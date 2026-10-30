package com.musichub.data

import com.musichub.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

@Serializable
data class MyPlaylist(val id: String, val name: String, val tracks: List<Track>, val createdAt: Long)

/** «Мои плейлисты» — треки из любых сервисов в одном списке (playlists.json). Новые сверху. */
class MyPlaylists(private val file: File = File(AppDirs.root, "playlists.json")) {
    private val serializer = ListSerializer(MyPlaylist.serializer())
    private val lock = Mutex()

    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<MyPlaylist>> = _items.asStateFlow()

    fun get(id: String): MyPlaylist? = _items.value.firstOrNull { it.id == id }

    suspend fun create(name: String, tracks: List<Track>): MyPlaylist {
        val p = MyPlaylist(UUID.randomUUID().toString(), name, tracks.map { it.copy(mixed = false) }, System.currentTimeMillis())
        edit { listOf(p) + it }
        return p
    }

    suspend fun delete(id: String) = edit { list -> list.filterNot { it.id == id } }

    private suspend fun edit(block: (List<MyPlaylist>) -> List<MyPlaylist>) = lock.withLock {
        val next = block(_items.value)
        _items.value = next
        withContext(Dispatchers.IO) { writeAtomic(file, StoreJson.encodeToString(serializer, next)) }
    }

    private fun read(): List<MyPlaylist> = runCatching {
        StoreJson.decodeFromString(serializer, file.readText())
    }.getOrDefault(emptyList())
}
