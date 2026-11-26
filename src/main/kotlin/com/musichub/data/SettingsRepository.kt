package com.musichub.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
enum class Quality(val label: String) {
    HIGH("Высокое"),
    NORMAL("Обычное"),
    LOW("Экономия трафика"),
}

@Serializable
enum class LikeTarget(val label: String) { UNISON("Только Unison"), YANDEX("Яндекс"), YOUTUBE("YouTube Music"), VK("ВК · Моя музыка") }

@Serializable
enum class WaveSource(val label: String) {
    YANDEX("Яндекс"), YOUTUBE("YouTube"), VK("ВК"),
}

@Serializable
data class AppSettings(
    val yandexToken: String? = null,
    val yandexUid: Long? = null,
    val yandexLogin: String? = null,
    val vkToken: String? = null,
    val vkUserId: Long? = null,
    val vkName: String? = null,
    val searchYoutube: Boolean = true,
    val searchVk: Boolean = true,
    val searchSoundcloud: Boolean = true,
    /** Если трек не играет в своём сервисе — искать замену в других. */
    val fallbackToOthers: Boolean = true,
    /** Обычная очередь доиграла — продолжить похожими треками на последний. */
    val autoContinueSimilar: Boolean = false,
    val radioFromSearch: Boolean = true,
    val likeTarget: LikeTarget = LikeTarget.UNISON,
    val quality: Quality = Quality.HIGH,
    val waveMood: String = "all",
    val waveDiversity: String = "default",
    val waveSource: WaveSource = WaveSource.YANDEX,
    val waveLanguage: String = "any",
    /** Громкость 0..100 — запоминается между запусками. */
    val volume: Int = 80,
    /** Настройка эквалайзера на ПК; в резервной копии мобильное приложение её просто пропускает. */
    val equalizer: EqualizerConfig = EqualizerConfig(),
    /** Индекс палитры из ui.theme.Palettes. */
    val palette: Int = 0,
    /** Цвета экрана берутся из обложки текущего трека (поверх выбранной палитры). */
    val coverTheme: Boolean = true,
    /** Сфера Моей волны шевелится и пульсирует от басов (звук тогда идёт через приложение). */
    val sphereMotion: Boolean = true,
    /** Боковое меню свёрнуто до иконок. */
    val sidebarCollapsed: Boolean = false,
    /** Тема Liquid Glass: живой фон, стекло с преломлением, пузырь-индикатор, переключатели-капли. */
    val liquidGlass: Boolean = true,
    /** Подмешивать похожие треки с YouTube в Мою волну: каждый [mixEvery]-й трек. */
    val mixYoutubeInWave: Boolean = false,
    val mixEvery: Int = 4,
    // Настройки только Android-версии: на ПК не используются, но хранятся, чтобы не терялись в резервной копии
    val fadeSeconds: Int = 0,
    val autoCacheMb: Int = 4096,
    val ytProxyEnabled: Boolean = false,
    val ytProxyType: String = "SOCKS",
    val ytProxyHost: String = "127.0.0.1",
    val ytProxyPort: Int = 10808,
    /** Сессия YouTube Music (cookie после входа) и имя аккаунта. */
    val ytCookie: String? = null,
    val ytAccountName: String? = null,
    /** Private Unison API endpoint and revocable session token. */
    val cloudServer: String = "https://unison-api.vexorter.duckdns.org",
    val cloudUsername: String? = null,
    val cloudToken: String? = null,
) {
    val yandexLoggedIn: Boolean get() = yandexToken != null && yandexUid != null
    val vkLoggedIn: Boolean get() = vkToken != null && vkUserId != null
    val ytLoggedIn: Boolean get() = YtMusicSession.hasAuthCookie(ytCookie)
}

/** Папка данных приложения: %APPDATA%\Unison (на других ОС — ~/.unison). */
object AppDirs {
    val root: File by lazy {
        // -Dunison.dataDir — временная папка для проверок, чтобы не трогать настоящие настройки
        val base = System.getProperty("unison.dataDir")?.let(::File)
            ?: System.getenv("APPDATA")?.let { File(it, "Unison") }
            ?: File(System.getProperty("user.home"), ".unison")
        base.apply { mkdirs() }
    }
}

internal val StoreJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

/** Настройки в settings.json. Запись атомарная: сначала во временный файл, потом переименование. */
class SettingsRepository(private val file: File = File(AppDirs.root, "settings.json")) {

    private val state = MutableStateFlow(read())
    private val lock = Mutex()
    val flow: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    suspend fun update(block: (AppSettings) -> AppSettings) = lock.withLock {
        val next = block(state.value)
        state.value = next
        withContext(Dispatchers.IO) { writeAtomic(file, StoreJson.encodeToString(AppSettings.serializer(), next)) }
    }

    private fun read(): AppSettings = runCatching {
        val raw = file.readText()
        val loaded = StoreJson.decodeFromString(AppSettings.serializer(), raw)
        val hadNextcloudSecret = Regex("""\"cloudAppPassword\"\s*:""").containsMatchIn(raw)
        if (hadNextcloudSecret || loaded.cloudServer.contains("nextcloud.vexorter.duckdns.org", ignoreCase = true)) {
            loaded.copy(
                cloudServer = "https://unison-api.vexorter.duckdns.org",
                cloudUsername = null,
                cloudToken = null,
            ).also { migrated ->
                writeAtomic(file, StoreJson.encodeToString(AppSettings.serializer(), migrated))
            }
        } else loaded
    }.getOrDefault(AppSettings())
}

internal fun writeAtomic(file: File, text: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}
