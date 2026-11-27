package com.musichub

import com.musichub.desktop.AppGraph
import com.musichub.search.SearchCategory
import com.musichub.search.SearchRelevance
import com.musichub.youtube.NewPipeDownloader
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.nio.file.Files

/** Network check uses a new profile and does not touch the user's accounts or library. */
fun main() {
    val directory = Files.createTempDirectory("unison-search-check-").toFile()
    System.setProperty("unison.dataDir", directory.absolutePath)
    try {
        runBlocking {
            val graph = AppGraph()
            NewPipe.init(NewPipeDownloader(graph.http), Localization("ru", "RU"), ContentCountry("RU"))
            try {
                withTimeout(60_000) {
                    for ((query, category) in listOf("Кино группа крови" to SearchCategory.TRACKS, "Цой" to SearchCategory.ARTISTS)) {
                        val result = graph.search.search(query, category)
                        println("$query: " + if (category == SearchCategory.TRACKS) result.tracks.take(5).joinToString { "${it.artists} — ${it.title}" }
                            else result.artists.take(5).joinToString { it.name })
                        println("Ошибки сервисов: ${result.errors.size}")
                        check(if (category == SearchCategory.TRACKS) result.tracks.isNotEmpty() else result.artists.isNotEmpty()) { "Нет результатов: ${result.errors}" }
                        if (category == SearchCategory.TRACKS) {
                            val originalInTop = result.tracks.take(3).any { SearchRelevance.score("Кино", it.artists) > 0 }
                            println("Artist match in top three: $originalInTop")
                            check(originalInTop) { "Первые результаты не содержат указанного исполнителя" }
                        }
                    }
                }
            } finally { graph.scope.cancel() }
        }
    } catch (e: Throwable) { e.printStackTrace(); kotlin.system.exitProcess(1) }
    finally { directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }; directory.delete() }
    kotlin.system.exitProcess(0)
}
