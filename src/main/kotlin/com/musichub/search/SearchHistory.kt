package com.musichub.search

import com.musichub.data.AppDirs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Recent desktop searches, persisted locally and capped to avoid unbounded growth. */
class SearchHistory(private val file: File = File(AppDirs.root, "search-history.json")) {
    private val lock = Any()
    private val json = Json
    private val mutableItems = MutableStateFlow(read())
    val items = mutableItems.asStateFlow()

    fun add(query: String) = update { current ->
        val normalized = query.trim()
        if (normalized.isEmpty()) current else (listOf(normalized) + current.filterNot { it.equals(normalized, true) }).take(MAX_ITEMS)
    }

    fun remove(query: String) = update { current -> current.filterNot { it == query } }

    fun clear() = update { emptyList() }

    private fun update(transform: (List<String>) -> List<String>) {
        synchronized(lock) {
            val next = transform(mutableItems.value)
            if (next == mutableItems.value) return@synchronized
            mutableItems.value = next
            runCatching { write(next) }
        }
    }

    private fun read(): List<String> = runCatching {
        json.decodeFromString<List<String>>(file.readText()).take(MAX_ITEMS)
    }.getOrDefault(emptyList())

    private fun write(items: List<String>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(json.encodeToString(items))
        runCatching {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val MAX_ITEMS = 20
    }
}
