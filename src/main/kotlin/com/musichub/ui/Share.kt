package com.musichub.ui

import com.musichub.data.model.Source
import com.musichub.data.model.Track
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Ссылка для отправки трека из любого поддерживаемого сервиса. */
internal fun trackShareUrl(track: Track): String? = when (track.source) {
    Source.YANDEX -> {
        val id = track.sourceId.substringBefore(':')
        val album = track.sourceId.substringAfter(':', "")
        if (album.isEmpty()) "https://music.yandex.ru/track/$id" else "https://music.yandex.ru/album/$album/track/$id"
    }
    Source.YOUTUBE -> "https://www.youtube.com/watch?v=${track.sourceId}"
    Source.VK -> "https://vk.com/audio${track.sourceId}"
    Source.SOUNDCLOUD -> "https://soundcloud.com/${track.sourceId.trimStart('/')}"
    Source.LOCAL -> null
}

internal fun copyTrackForSharing(track: Track): Boolean = runCatching {
    val text = listOfNotNull("${track.artists} — ${track.title}", trackShareUrl(track)).joinToString("\n")
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}.isSuccess
