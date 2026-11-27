package com.musichub.data

import com.musichub.data.model.Source
import com.musichub.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

@Serializable
data class OfflineFile(val key: String, val name: String, val bytes: Long)

@Serializable
data class OfflinePlaylist(val id: String, val title: String, val tracks: List<Track>)

@Serializable
private data class OfflineIndex(val files: List<OfflineFile> = emptyList(), val playlists: List<OfflinePlaylist> = emptyList())

data class OfflineProgress(val title: String, val done: Int, val total: Int, val failed: Int = 0)

/** Downloads complete audio files; playlist membership is kept separately from shared files. */
class OfflineDownloads(private val http: OkHttpClient, private val resolve: (Track) -> String,
    private val folder: File = File(AppDirs.root, "downloads")) {
    init { folder.mkdirs() }
    private val indexFile = File(folder, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    @Volatile private var index = runCatching { json.decodeFromString(OfflineIndex.serializer(), indexFile.readText()) }.getOrDefault(OfflineIndex())
    private val _playlists = MutableStateFlow(index.playlists)
    val playlists = _playlists.asStateFlow()
    private val _progress = MutableStateFlow<OfflineProgress?>(null)
    val progress = _progress.asStateFlow()

    fun pathFor(key: String): File? = index.files.firstOrNull { it.key == key }?.let { item ->
        // Names are generated hashes; never trust a path stored in a potentially damaged index.
        if (!item.name.matches(Regex("[a-f0-9]{64}\\.audio"))) null else File(folder, item.name).takeIf { it.isFile && it.length() == item.bytes && item.bytes > 0 }
    }

    fun sizeOf(playlist: OfflinePlaylist): Long = playlist.tracks.distinctBy { it.key }.sumOf { track ->
        index.files.firstOrNull { it.key == track.key }?.takeIf { pathFor(track.key) != null }?.bytes ?: 0L
    }

    fun tracksOf(playlist: OfflinePlaylist): List<Track> = playlist.tracks.filter { pathFor(it.key) != null }

    suspend fun downloadPlaylist(id: String, title: String, tracks: List<Track>): OfflineProgress = lock.withLock {
        withContext(Dispatchers.IO) {
            val unique = tracks.distinctBy { it.key }
            var done = 0
            var failed = 0
            _progress.value = OfflineProgress(title, done, unique.size)
            try {
                val previous = index.playlists.firstOrNull { it.id == id }
                val combined = (previous?.tracks.orEmpty() + unique).distinctBy { it.key }
                save(index.copy(playlists = index.playlists.filterNot { it.id == id } + OfflinePlaylist(id, title, combined)))
                for (track in unique) {
                    if (pathFor(track.key) == null) {
                        val ok = try { downloadFile(track); true } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                        if (!ok) failed++
                    }
                    done++
                    _progress.value = OfflineProgress(title, done, unique.size, failed)
                }
                OfflineProgress(title, done, unique.size, failed)
            } finally {
                _progress.value = null
            }
        }
    }

    private fun downloadFile(track: Track) {
        require(track.source != Source.LOCAL && track.source != Source.SOUNDCLOUD) { "Этот источник не поддерживает скачивание" }
        val url = resolve(track)
        require(url.startsWith("https://") || url.startsWith("http://")) { "Неподдерживаемый источник" }
        val name = MessageDigest.getInstance("SHA-256").digest(track.key.toByteArray()).joinToString("") { "%02x".format(it) } + ".audio"
        val tmp = File.createTempFile("download-", ".part", folder)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "Ошибка загрузки: ${response.code}" }
                val type = response.header("Content-Type").orEmpty().lowercase()
                check("mpegurl" !in type && "text/html" !in type && "application/json" !in type && "dash+xml" !in type && !url.substringBefore('?').endsWith(".m3u8", true)) { "Этот поток нельзя сохранить как аудиофайл" }
                val body = response.body ?: error("Пустой ответ")
                body.byteStream().use { input ->
                    val prefix = ByteArray(32)
                    val read = input.read(prefix)
                    check(read > 0) { "Пустой ответ" }
                    val header = String(prefix, 0, read, Charsets.US_ASCII).trimStart().lowercase()
                    check(!header.startsWith("#extm3u") && !header.startsWith("<html") && !header.startsWith("<!doctype html")) { "Сервис вернул страницу вместо аудио" }
                    tmp.outputStream().use { output -> output.write(prefix, 0, read); input.copyTo(output) }
                }
                check(tmp.length() > 0 && (body.contentLength() < 0 || body.contentLength() == tmp.length())) { "Загрузка не завершена" }
            }
            val target = File(folder, name)
            java.nio.file.Files.move(tmp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            save(index.copy(files = index.files.filterNot { it.key == track.key } + OfflineFile(track.key, name, target.length())))
        } finally { tmp.delete() }
    }

    suspend fun removePlaylist(id: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            val remaining = index.playlists.filterNot { it.id == id }
            val used = remaining.flatMap { it.tracks }.mapTo(HashSet()) { it.key }
            val removed = index.playlists.firstOrNull { it.id == id }?.tracks.orEmpty().map { it.key }.toSet() - used
            // Persist the new index before deleting files; a crash can at worst leave unused files.
            val files = index.files.filterNot { it.key in removed }
            val deleted = index.files.filter { it.key in removed }.mapNotNull { pathFor(it.key) }
            save(index.copy(files = files, playlists = remaining))
            deleted.forEach { it.delete() }
        }
    }

    private fun save(next: OfflineIndex) {
        writeAtomic(indexFile, json.encodeToString(OfflineIndex.serializer(), next))
        index = next
        _playlists.value = next.playlists
    }
}
