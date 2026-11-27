package com.musichub

import com.musichub.data.model.Source
import com.musichub.data.model.Track
import com.musichub.desktop.AppGraph
import com.musichub.data.OfflineDownloads
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.net.InetSocketAddress
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/** A real VLC run against downloaded silent WAV files, with the server stopped before playback. */
fun main() {
    try { runBlocking { runCrossfadeCheck() } }
    catch (e: Throwable) { e.printStackTrace(); kotlin.system.exitProcess(1) }
    kotlin.system.exitProcess(0)
}

private suspend fun runCrossfadeCheck() = kotlinx.coroutines.coroutineScope {
    val dir = Files.createTempDirectory("unison-crossfade-").toFile()
    System.setProperty("unison.dataDir", dir.absolutePath)
    System.setProperty("compose.application.resources.dir", File("build/vlc-resources/windows").absolutePath)
    val graph = AppGraph()
    val checkBass = System.getProperty("unison.check.bass") == "true"
    graph.settings.update { it.copy(volume = 20, sphereMotion = checkBass, fadeSeconds = 2) }
    fun wav(name: String): File {
        val file = File(dir, name)
        val rate = 44100
        val samples = ByteArray(rate * 6 * 2)
        val format = AudioFormat(rate.toFloat(), 16, 1, true, false)
        AudioInputStream(ByteArrayInputStream(samples), format, (samples.size / 2).toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }
    val one = wav("one.wav")
    val two = wav("two.wav")
    val first = Track(Source.YOUTUBE, "test-one", "First", "Test", durationMs = 6000)
    val second = Track(Source.YOUTUBE, "test-two", "Second", "Test", durationMs = 6000)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/audio") { exchange ->
        val file = if (exchange.requestURI.query == "one") one else two
        exchange.sendResponseHeaders(200, file.length())
        file.inputStream().use { input -> exchange.responseBody.use { input.copyTo(it) } }
    }
    server.start()
    try {
        val store = OfflineDownloads(OkHttpClient(), { track -> "http://127.0.0.1:${server.address.port}/audio?${if (track.key == first.key) "one" else "two"}" }, File(dir, "downloads"))
        check(store.downloadPlaylist("test", "Test", listOf(first, second)).failed == 0)
    } finally { server.stop(0) }
    val engine = graph.engine ?: error("VLC не найден")
    if (checkBass) check(engine.bassAvailable) { "Нет доступного аудиоустройства для проверки сферы" }
    var released = false
    try {
        val observer = launch {
            while (true) {
                delay(1000)
                val s = engine.state.value
                val field = engine.javaClass.getDeclaredField("player").apply { isAccessible = true }
                val vlc = field.get(engine) as uk.co.caprica.vlcj.player.base.MediaPlayer
                println("state index=${s.index} playing=${s.isPlaying} buffering=${s.isBuffering} position=${s.positionMs} crossfade=${s.crossfading} error=${s.error} vlc=${vlc.status().state()} mrl=${vlc.media().info()?.mrl()}")
            }
        }
        engine.playTracks(listOf(first, second))
        withTimeout(20_000) { engine.state.first { it.index == 0 && it.crossfading } }
        delay(350)
        val gainField = engine.javaClass.getDeclaredField("fadeGain").apply { isAccessible = true }
        val gain = gainField.getFloat(engine)
        check(gain > 0f && gain < 1f) { "Fade gain did not change: $gain" }
        engine.togglePlay()
        withTimeout(3_000) { engine.state.first { !it.isPlaying } }
        delay(300)
        engine.togglePlay()
        val next = withTimeout(15_000) { engine.state.first { it.index == 1 && it.isPlaying && it.positionMs >= 1000 } }
        check(next.track?.key == second.key)
        println("Offline playback and crossfade worked; pause/resume continued the next track at ${next.positionMs} ms")
        observer.cancel()
        engine.togglePlay()
        withTimeout(3_000) { engine.state.first { !it.isPlaying } }
        engine.release()
        released = true
        graph.scope.cancel()
        val restarted = AppGraph()
        val restored = restarted.engine ?: error("VLC не найден после перезапуска")
        try {
            val snapshot = restored.state.value
            check(snapshot.track?.key == second.key && snapshot.queue.size == 2 && snapshot.index == 1 && !snapshot.isPlaying)
            check(snapshot.positionMs >= 1000) { "Позиция не восстановлена: ${snapshot.positionMs}" }
            restored.togglePlay()
            val resumed = withTimeout(12_000) { restored.state.first { it.isPlaying && it.positionMs >= snapshot.positionMs + 300 } }
            check(resumed.track?.key == second.key)
            println("Queue and position restored at ${snapshot.positionMs} ms; playback resumed at ${resumed.positionMs} ms")
        } finally { restored.release(); restarted.scope.cancel() }
    } finally {
        graph.scope.cancel()
        if (!released) engine.release()
        File(dir, "downloads").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        File(dir, "downloads").delete()
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        dir.delete()
    }
}
