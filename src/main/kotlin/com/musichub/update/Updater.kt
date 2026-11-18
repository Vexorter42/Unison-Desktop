package com.musichub.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Новая версия из GitHub Releases. */
data class Release(val version: String, val notes: String, val installerUrl: String, val extension: String, val size: Long)

data class UpdateState(
    val checking: Boolean = false,
    val available: Release? = null,
    /** 0..1, пока качается установщик. */
    val progress: Float? = null,
    /** На Mac диск открыт; остаётся перенести приложение в «Программы». */
    val openedDmg: Boolean = false,
    /** Проверка прошла, новее нет. */
    val upToDate: Boolean = false,
    val error: String? = null,
)

/**
 * Обновления через GitHub Releases: выбираем установщик текущей платформы.
 */
class Updater(private val http: OkHttpClient) {
    /** Версия из сборки (-Dunison.version); у запуска из исходников её нет — тогда не обновляемся. */
    val current: String? = System.getProperty("unison.version")

    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check() {
        if (_state.value.checking || _state.value.progress != null) return
        _state.update { it.copy(checking = true, error = null, upToDate = false, openedDmg = false) }
        val result = runCatching { withContext(Dispatchers.IO) { latest() } }
        _state.update { s ->
            result.fold(
                onSuccess = { r ->
                    val newer = r?.takeIf { current != null && isNewer(it.version, current) }
                    s.copy(checking = false, available = newer, upToDate = newer == null)
                },
                onFailure = { s.copy(checking = false, error = "Не удалось проверить обновления: ${it.message}") },
            )
        }
    }

    private fun latest(): Release? {
        val req = Request.Builder().url(LATEST_URL).header("Accept", "application/vnd.github+json").build()
        val root = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("GitHub ответил ${resp.code}")
            json.parseToJsonElement(resp.body!!.string()).jsonObject
        }
        val extension = if (isMac) ".dmg" else ".msi"
        val installer = root["assets"]?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(extension, ignoreCase = true) == true } ?: return null
        return Release(
            version = root["tag_name"]!!.jsonPrimitive.content.removePrefix("v"),
            notes = root["body"]?.jsonPrimitive?.content.orEmpty(),
            installerUrl = installer["browser_download_url"]!!.jsonPrimitive.content,
            extension = extension,
            size = installer["size"]?.jsonPrimitive?.long ?: 0,
        )
    }

    /** Качает установщик (с прогрессом в [state]) и возвращает файл. */
    suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        _state.update { it.copy(progress = 0f, error = null) }
        try {
            val file = File(System.getProperty("java.io.tmpdir"), "Unison-${release.version}${release.extension}")
            http.newCall(Request.Builder().url(release.installerUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("GitHub ответил ${resp.code}")
                val body = resp.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: release.size
                body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) _state.update { it.copy(progress = (done.toFloat() / total).coerceAtMost(1f)) }
                        }
                    }
                }
            }
            if (release.size > 0 && file.length() != release.size) throw IOException("Установщик скачался не полностью")
            file
        } catch (e: Exception) {
            _state.update { it.copy(progress = null, error = "Не удалось скачать обновление: ${e.message}") }
            throw e
        }
    }

    /**
     * Запускает установку и перезапуск отдельным скриптом: сам Unison должен сразу закрыться,
     * иначе установщик не сможет заменить его файлы.
     */
    fun installAndRestart(installer: File): Boolean {
        if (isMac) {
            // DMG открывается в Finder: пользователь переносит Unison в «Программы».
            ProcessBuilder("open", installer.absolutePath).start()
            _state.update { it.copy(progress = null, openedDmg = true) }
            return false
        }
        val app = System.getProperty("jpackage.app-path")
        val script = File(System.getProperty("java.io.tmpdir"), "unison-update.ps1")
        val msiPath = installer.absolutePath.replace("'", "''")
        val appPath = app?.replace("'", "''")
        script.writeText(
            "\uFEFF" + buildString {
                appendLine("Start-Sleep -Seconds 2")
                appendLine("${'$'}msi = '$msiPath'")
                appendLine("${'$'}arguments = '/i \"' + ${'$'}msi + '\" /passive /norestart'")
                appendLine("${'$'}process = Start-Process -FilePath msiexec.exe -ArgumentList ${'$'}arguments -PassThru -Wait")
                if (appPath != null) appendLine("if (${'$'}process.ExitCode -eq 0) { Start-Process -FilePath '$appPath' }")
                appendLine("Remove-Item -LiteralPath ${'$'}msi -ErrorAction SilentlyContinue")
                appendLine("Remove-Item -LiteralPath ${'$'}PSCommandPath -ErrorAction SilentlyContinue")
            },
            Charsets.UTF_8,
        )
        ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass", "-File", script.path).start()
        return true
    }

    companion object {
        private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac")
        const val REPO = "Vexorter42/Unison-Desktop"
        const val RELEASES_URL = "https://github.com/$REPO/releases"
        private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

        /** «0.1.10» новее «0.1.9»: сравниваем числа по частям. */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (d != 0) return d > 0
            }
            return false
        }
    }
}
