package com.musichub.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Одно прослушивание — те же поля, что у статистики Android-версии (так оно и попадает в резервную копию). */
@Serializable
data class PlayEvent(
    val id: Long = 0,
    val trackKey: String,
    val title: String,
    val artists: String,
    val source: String,
    val startedAt: Long,
    val playedMs: Long,
    val durationMs: Long,
    val skipped: Boolean,
    val fromWave: Boolean,
    val trackJson: String,
)

/**
 * История прослушиваний (stats.json). Экрана статистики на ПК пока нет, но история уходит в резервную копию:
 * восстановленная на телефоне, она появится в его статистике, а история с телефона не теряется при переносе через ПК.
 */
class PlayStats(private val file: File = File(AppDirs.root, "stats.json")) {
    private val serializer = ListSerializer(PlayEvent.serializer())
    private var items: List<PlayEvent> = runCatching { StoreJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun all(): List<PlayEvent> = items

    /** Записать прослушивание (повторная запись того же — по треку и времени начала — заменяет прежнюю). */
    @Synchronized
    fun record(e: PlayEvent) {
        items = items.filterNot { it.trackKey == e.trackKey && it.startedAt == e.startedAt } + e
        save()
    }

    /** Добавить из резервной копии, пропуская уже известные. Возвращает, сколько добавлено. */
    @Synchronized
    fun addAll(events: List<PlayEvent>): Int {
        val known = items.mapTo(HashSet()) { it.trackKey to it.startedAt }
        val fresh = events.filter { known.add(it.trackKey to it.startedAt) }
        if (fresh.isEmpty()) return 0
        items = (items + fresh).sortedBy { it.startedAt }
        save()
        return fresh.size
    }

    private fun save() = writeAtomic(file, StoreJson.encodeToString(serializer, items))
}
