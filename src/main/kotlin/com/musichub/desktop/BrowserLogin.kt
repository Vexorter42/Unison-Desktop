package com.musichub.desktop

import com.musichub.data.AppDirs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Вход в сервис через отдельное окно Chrome / Edge с чистым профилем — замена WebView из Android-версии.
 * Пользователь входит сам; приложение только смотрит адреса открытых страниц и cookie по протоколу DevTools
 * (порт слушает лишь 127.0.0.1), забирает результат и закрывает окно. Профиль после входа удаляется.
 * Google в окно с DevTools не пускает — для него [loginAndReadCookies]: обычное окно, cookie читаются после.
 */
class BrowserLogin(http: OkHttpClient) {
    private val http = http.newBuilder().proxy(java.net.Proxy.NO_PROXY).build()
    private val json = Json { ignoreUnknownKeys = true }

    /** Есть ли на компьютере браузер для окна входа. */
    val available: Boolean get() = findBrowser() != null

    /** Открытое окно входа: адреса страниц, cookie. */
    inner class Session internal constructor(private val port: Int) {
        private var socket: WebSocket? = null
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        private val ids = AtomicInteger()

        /** Адреса открытых вкладок (с #фрагментом — в нём Яндекс и ВК отдают токен). */
        fun pageUrls(): List<String> = get("/json/list").jsonArray
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "page" }
            .mapNotNull { it["url"]?.jsonPrimitive?.content }

        /** Все cookie браузера, включая HttpOnly. */
        suspend fun cookies(): List<Pair<String, String>> =
            (call("Storage.getCookies")["cookies"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
                it["domain"]!!.jsonPrimitive.content to "${it["name"]!!.jsonPrimitive.content}=${it["value"]!!.jsonPrimitive.content}"
            }

        private fun get(path: String) = http.newCall(Request.Builder().url("http://127.0.0.1:$port$path").build()).execute().use {
            json.parseToJsonElement(it.body!!.string())
        }

        // К вкладкам не подключаемся (Google не пускает в «управляемый» браузер) — только к самому браузеру
        private suspend fun call(method: String): JsonObject {
            val ws = socket ?: connect().also { socket = it }
            val id = ids.incrementAndGet()
            val answer = CompletableDeferred<JsonObject>().also { pending[id] = it }
            ws.send(buildJsonObject { put("id", id); put("method", method) }.toString())
            return withTimeout(10_000) { answer.await() }
        }

        private fun connect(): WebSocket {
            val url = get("/json/version").jsonObject["webSocketDebuggerUrl"]!!.jsonPrimitive.content
            return http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val msg = json.parseToJsonElement(text).jsonObject
                    val id = msg["id"]?.jsonPrimitive?.int ?: return
                    pending.remove(id)?.complete(msg["result"]?.jsonObject ?: JsonObject(emptyMap()))
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    pending.values.forEach { it.completeExceptionally(t) }
                }
            })
        }

        internal fun close() {
            runCatching { socket?.send("""{"id":0,"method":"Browser.close"}""") }
            socket?.close(1000, null)
        }
    }

    /**
     * Открывает [startUrl] в окне входа и раз в секунду вызывает [check], пока тот не вернёт результат.
     * Закрытое пользователем окно — ошибка; отмена корутины закрывает окно.
     */
    suspend fun <T : Any> login(startUrl: String, timeoutMs: Long = 10 * 60_000L, check: suspend Session.() -> T?): T =
        withContext(Dispatchers.IO) {
            val profile = freshProfile()
            val process = startBrowser(profile, "--remote-debugging-port=0", "--window-size=560,780", startUrl)
            var session: Session? = null
            try {
                withTimeout(timeoutMs) {
                    val s = openSession(profile, process).also { session = it }
                    var result: T? = null
                    while (result == null) {
                        if (!process.isAlive) throw IOException("Окно входа закрыто")
                        result = runCatching { s.check() }.getOrNull()
                        if (result == null) delay(1000)
                    }
                    result
                }
            } finally {
                session?.close()
                stop(process)
                deleteProfile(profile)
            }
        }

    /**
     * Вход в Google. Браузер с открытым портом DevTools Google отклоняет («этот браузер небезопасен»),
     * поэтому окно входа — самое обычное, без отладки. Когда в заголовке окна появляется [doneTitle]
     * (или пользователь сам закрыл окно), окно закрывается штатно — так браузер сохраняет cookie на диск, —
     * а cookie читаются из того же профиля браузером без окна.
     */
    suspend fun loginAndReadCookies(startUrl: String, doneTitle: String, timeoutMs: Long = 10 * 60_000L): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val profile = freshProfile()
            try {
                val window = startBrowser(profile, "--window-size=560,780", startUrl)
                try {
                    withTimeout(timeoutMs) {
                        while (window.isAlive) {
                            if (BrowserWindows.titles(window.pid()).any { doneTitle in it }) {
                                delay(2000) // сайт дописывает cookie после перехода
                                BrowserWindows.close(window.pid())
                                break
                            }
                            delay(700)
                        }
                    }
                } finally {
                    if (window.isAlive) BrowserWindows.close(window.pid())
                    stop(window)
                }
                val headless = startBrowser(profile, "--headless=new", "--remote-debugging-port=0", "about:blank")
                var session: Session? = null
                try {
                    withTimeout(30_000) { openSession(profile, headless).also { session = it }.cookies() }
                } finally {
                    session?.close()
                    stop(headless)
                }
            } finally {
                deleteProfile(profile)
            }
        }

    /**
     * Свой профиль на каждый вход: если прошлое окно входа ещё открыто, новый запуск с тем же профилем
     * передал бы адрес ему и сразу завершился. Профили от прошлых входов удаляются, если уже не заняты.
     */
    private fun freshProfile(): File {
        val dir = File(AppDirs.root, "login-browser").apply { mkdirs() }
        // Открытый браузер держит lockfile своего профиля: не удаляется — профиль занят, не трогаем
        dir.listFiles { f -> f.isDirectory }?.forEach { p ->
            val lock = File(p, "lockfile")
            if (!lock.exists() || lock.delete()) p.deleteRecursively()
        }
        return File(dir, java.util.UUID.randomUUID().toString().take(8))
    }

    private fun startBrowser(profile: File, vararg args: String): Process {
        val browser = findBrowser() ?: throw IOException("Не найден Chrome или Edge для окна входа — войдите вручную")
        return ProcessBuilder(
            listOf(
                browser.path,
                "--user-data-dir=${profile.path}",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-features=msEdgeSyncConsentDialog,msImplicitSignin,msEdgeWelcomePage",
            ) + args,
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    }

    private suspend fun openSession(profile: File, process: Process): Session {
        val portFile = File(profile, "DevToolsActivePort")
        while (!portFile.exists() || portFile.length() == 0L) {
            if (!process.isAlive) throw IOException("Окно входа не открылось")
            delay(200)
        }
        return Session(portFile.readLines().first().trim().toInt())
    }

    /** Ждём штатного выхода браузера (на нём сохраняются cookie), иначе завершаем. */
    private fun stop(process: Process) {
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
        process.waitFor(5, TimeUnit.SECONDS)
    }

    private fun deleteProfile(profile: File) {
        // Файлы профиля отпускаются не сразу после выхода браузера
        for (attempt in 1..10) {
            if (profile.deleteRecursively()) break
            Thread.sleep(300)
        }
    }

    private fun findBrowser(): File? {
        val pf = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val pf86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
        val local = System.getenv("LOCALAPPDATA").orEmpty()
        // Chrome первым: у Edge с чистым профилем вылезают окна приветствия и синхронизации
        return listOf(
            "$pf\\Google\\Chrome\\Application\\chrome.exe",
            "$pf86\\Google\\Chrome\\Application\\chrome.exe",
            "$local\\Google\\Chrome\\Application\\chrome.exe",
            "$pf86\\Microsoft\\Edge\\Application\\msedge.exe",
            "$pf\\Microsoft\\Edge\\Application\\msedge.exe",
            "$local\\Yandex\\YandexBrowser\\Application\\browser.exe",
        ).map(::File).firstOrNull { it.isFile }
    }

    companion object {
        /** Вход в Google с возвратом на YouTube Music — после него у .youtube.com появляется SAPISID. */
        const val YOUTUBE_LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&hl=ru" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

        /** Cookie для music.youtube.com из всех cookie браузера. */
        fun youtubeCookie(all: List<Pair<String, String>>): String =
            all.filter { (domain, _) -> domain == ".youtube.com" || domain == "youtube.com" || domain == "music.youtube.com" }
                .map { it.second }
                .distinctBy { it.substringBefore('=') }
                .joinToString("; ")

        /** access_token из адреса вида …#access_token=XXX&… */
        fun tokenFromUrl(url: String): String? =
            Regex("""[#&?]access_token=([^&\s]+)""").find(url)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
    }
}

/** Окна браузера по id процесса (WinAPI): заголовок показывает, какая страница открыта, — без DevTools. */
private object BrowserWindows {
    private const val WM_CLOSE = 0x0010

    fun titles(pid: Long): List<String> = windows(pid).mapNotNull { hwnd ->
        val buf = CharArray(512)
        val n = User32.INSTANCE.GetWindowText(hwnd, buf, buf.size)
        if (n > 0) String(buf, 0, n) else null
    }

    /** Штатное закрытие, как крестиком: браузер успевает сохранить профиль. */
    fun close(pid: Long) = windows(pid).forEach { User32.INSTANCE.PostMessage(it, WM_CLOSE, null, null) }

    private fun windows(pid: Long): List<WinDef.HWND> {
        val out = mutableListOf<WinDef.HWND>()
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            val owner = IntByReference()
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner)
            if (owner.value.toLong() == pid && User32.INSTANCE.IsWindowVisible(hwnd)) out += hwnd
            true
        }, null)
        return out
    }
}
