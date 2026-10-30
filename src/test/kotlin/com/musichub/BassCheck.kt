package com.musichub

import com.musichub.desktop.AppGraph
import com.musichub.youtube.NewPipeDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.nio.file.Files

/**
 * Звук через приложение (для сферы): трек играет, позиция идёт, пауза и перемотка работают, уровень басов меняется.
 * На временной папке данных и тихо (громкость 10) — настоящие настройки не трогаются.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val dir = Files.createTempDirectory("unison-bass-check").toFile()
    System.setProperty("unison.dataDir", dir.path)
    val graph = AppGraph()
    NewPipe.init(NewPipeDownloader(graph.http), Localization("ru", "RU"), ContentCountry("RU"))
    val engine = graph.engine ?: error("VLC не найден")
    println("Звук через приложение: ${engine.bassAvailable}")
    engine.setVolume(args.getOrNull(1)?.toInt() ?: 10)
    val track = graph.youtube.search(args.firstOrNull() ?: "Daft Punk Around the World").first()
    engine.playTracks(listOf(track))
    val ok = withTimeoutOrNull(25_000) { engine.state.first { it.isPlaying && it.positionMs > 1000 } }
    println("Играет: ${ok != null} — ${track.artists} — ${track.title}")
    val levels = mutableListOf<Float>()
    var outPeak = 0
    repeat(40) {
        delay(100)
        levels += engine.bassLevel
        outPeak = maxOf(outPeak, engine.audioOutputPeak)
    }
    println("Басы за 4 с: мин ${"%.2f".format(levels.min())}, макс ${"%.2f".format(levels.max())}, " +
        "разных значений ${levels.map { (it * 20).toInt() }.toSet().size}; блоков звука: ${engine.audioBlocks}; пик на выходе: $outPeak")
    val before = engine.state.value.positionMs
    engine.togglePlay()
    delay(1500)
    val paused = engine.state.value.let { !it.isPlaying } && engine.bassLevel == 0f
    engine.seekTo(60_000)
    engine.togglePlay()
    delay(2500)
    println("Пауза: ${if (paused) "ок" else "НЕТ"}; перемотка: ${engine.state.value.positionMs} мс (было $before)")
    engine.release()
    dir.deleteRecursively()
    kotlin.system.exitProcess(0)
}
