package com.musichub.data

import com.musichub.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/*
 * Резервная копия в формате Android-версии (data/Backup.kt там): тот же JSON, поэтому копия с телефона
 * восстанавливается на ПК и наоборот. Имена полей менять нельзя.
 */

/** Настройки без входов в аккаунты: токены и cookie в файл копии не попадают. Все поля обязательны — их ждёт телефон. */
@Serializable
data class BackupSettings(
    val searchYoutube: Boolean,
    val searchVk: Boolean,
    val fallbackToOthers: Boolean,
    val mixYoutubeInWave: Boolean,
    val mixEvery: Int,
    val quality: Quality,
    val fadeSeconds: Int,
    val autoCacheMb: Int,
    val waveMood: String,
    val waveDiversity: String,
    val ytProxyEnabled: Boolean,
    val ytProxyType: String,
    val ytProxyHost: String,
    val ytProxyPort: Int,
    val liquidGlass: Boolean,
    val palette: Int,
    val coverTheme: Boolean,
)

@Serializable
data class BackupPlaylistTrack(val playlistId: Long, val trackKey: String, val position: Int, val trackJson: String)

@Serializable
data class BackupPlaylist(val name: String, val createdAt: Long, val tracks: List<BackupPlaylistTrack>)

@Serializable
data class BackupFavorite(
    val trackKey: String,
    val trackJson: String,
    val addedAt: Long,
    val sentYandex: Boolean = false,
    val sentYoutube: Boolean = false,
)

@Serializable
data class BackupFile(
    val app: String = "Unison",
    val version: Int = 1,
    val createdAt: Long,
    val settings: BackupSettings,
    val playlists: List<BackupPlaylist>,
    val favorites: List<BackupFavorite>,
    val stats: List<PlayEvent>,
)

data class RestoreResult(val playlists: Int, val favorites: Int, val stats: Int)

/** Резервная копия в один JSON-файл: свои плейлисты, «Любимое», статистика, настройки. */
class Backup(
    private val settings: SettingsRepository,
    private val favorites: Favorites,
    private val playlists: MyPlaylists,
    private val stats: PlayStats,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Возвращает, сколько плейлистов и треков «Любимого» записано. */
    suspend fun export(file: File): Int = withContext(Dispatchers.IO) {
        val s = settings.current
        val lists = playlists.items.value.map { p ->
            BackupPlaylist(p.name, p.createdAt, p.tracks.mapIndexed { i, t -> BackupPlaylistTrack(0, t.key, i, t.toJson()) })
        }
        val backup = BackupFile(
            createdAt = System.currentTimeMillis(),
            settings = BackupSettings(
                s.searchYoutube, s.searchVk, s.fallbackToOthers, s.mixYoutubeInWave, s.mixEvery, s.quality, s.fadeSeconds,
                s.autoCacheMb, s.waveMood, s.waveDiversity, s.ytProxyEnabled, s.ytProxyType, s.ytProxyHost, s.ytProxyPort,
                s.liquidGlass, s.palette, s.coverTheme,
            ),
            playlists = lists,
            favorites = favorites.items.value.map { BackupFavorite(it.key, it.track.toJson(), it.addedAt, it.sentYandex, it.sentYoutube) },
            stats = stats.all(),
        )
        writeAtomic(file, json.encodeToString(BackupFile.serializer(), backup))
        lists.size + backup.favorites.size
    }

    /** Восстановление добавляет к текущему, ничего не удаляя: плейлисты — новыми, дубли плейлистов, «Любимого» и статистики пропускаются. */
    suspend fun restore(file: File): RestoreResult = withContext(Dispatchers.IO) {
        val backup = runCatching { json.decodeFromString(BackupFile.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { it.app == "Unison" } ?: error("Это не резервная копия Unison")
        // Тот же плейлист (имя и время создания) при повторном восстановлении не дублируем
        val known = playlists.items.value.mapTo(HashSet()) { it.name to it.createdAt }
        val fresh = backup.playlists.filter { known.add(it.name to it.createdAt) }
        fresh.forEach { p ->
            val tracks = p.tracks.sortedBy { it.position }.mapNotNull { runCatching { Track.fromJson(it.trackJson) }.getOrNull() }
            playlists.create(p.name, tracks, createdAt = p.createdAt)
        }
        val favs = backup.favorites.mapNotNull { f ->
            runCatching { Track.fromJson(f.trackJson) }.getOrNull()?.let { FavoriteTrack(it, f.addedAt, f.sentYandex, f.sentYoutube) }
        }
        val addedFavs = favorites.restore(favs)
        val addedStats = stats.addAll(backup.stats)
        val b = backup.settings
        settings.update {
            it.copy(
                searchYoutube = b.searchYoutube, searchVk = b.searchVk, fallbackToOthers = b.fallbackToOthers,
                mixYoutubeInWave = b.mixYoutubeInWave, mixEvery = b.mixEvery, quality = b.quality, fadeSeconds = b.fadeSeconds,
                autoCacheMb = b.autoCacheMb, waveMood = b.waveMood, waveDiversity = b.waveDiversity,
                ytProxyEnabled = b.ytProxyEnabled, ytProxyType = b.ytProxyType, ytProxyHost = b.ytProxyHost, ytProxyPort = b.ytProxyPort,
                liquidGlass = b.liquidGlass, palette = b.palette, coverTheme = b.coverTheme,
            )
        }
        RestoreResult(fresh.size, addedFavs, addedStats)
    }
}
