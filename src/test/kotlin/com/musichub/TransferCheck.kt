package com.musichub

import com.musichub.desktop.AppGraph
import com.musichub.transfer.TransferState
import com.musichub.transfer.TransferTarget
import com.musichub.youtube.NewPipeDownloader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Перенос без записи в сервисы: разбор ссылок и перенос первого доступного плейлиста в «Мои плейлисты»
 * (созданный плейлист потом удаляется). В Яндекс и YouTube ничего не создаётся.
 */
fun main(): Unit = runBlocking {
    val graph = AppGraph()
    NewPipe.init(NewPipeDownloader(graph.http), Localization("ru", "RU"), ContentCountry("RU"))
    val links = listOf(
        "https://music.yandex.ru/users/yamusic-daily/playlists/12345",
        "https://music.yandex.ru/album/3423/track/123",
        "https://music.yandex.ru/playlists/ar.a2b6f1c4-1111",
        "Послушай: https://music.youtube.com/playlist?list=PLabcdef123&si=xyz",
        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ",
        "https://soundcloud.com/artist/sets/best-of",
        "https://vk.com/music/playlist/-147845620_2949_9e1f2a",
        "https://vk.com/audio?act=audio_playlist123_45&access_hash=abc",
        "https://example.com/playlist",
    )
    links.forEach { println("Ссылка → ${graph.sources.fromLink(it)?.id ?: "не узнана"}  ($it)") }

    val sources = graph.sources.available()
    println("Источников: ${sources.size}: ${sources.take(6).joinToString { it.title }}")
    val src = sources.firstOrNull { it.id.startsWith("ya:") && it.id != "ya:liked" } ?: sources.first()
    val before = graph.myPlaylists.items.value.map { it.id }.toSet()
    graph.transfer.start(TransferTarget.LOCAL, "Проверка переноса", src.load)
    val done = withTimeout(60_000) { graph.transfer.state.first { it is TransferState.Done || it is TransferState.Failed } }
    println("Перенос «${src.title}» → Мои плейлисты: $done".take(300))
    graph.myPlaylists.items.value.filter { it.id !in before }.forEach {
        println("Создан: ${it.name}, ${it.tracks.size} треков — удаляю")
        graph.myPlaylists.delete(it.id)
    }
    kotlin.system.exitProcess(0)
}
