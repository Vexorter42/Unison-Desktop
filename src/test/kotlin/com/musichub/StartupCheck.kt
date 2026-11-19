package com.musichub

import com.musichub.desktop.AppGraph
import com.musichub.playback.Vlc
import kotlin.system.measureTimeMillis

/**
 * Сколько стоит запуск до окна: VLC из папки ресурсов установщика (-Dcompose.application.resources.dir)
 * и остальной AppGraph. Запуск: ./gradlew startupCheck
 */
fun main() {
    println("VLC из: ${System.getProperty("compose.application.resources.dir")}")
    val vlc = measureTimeMillis { Vlc.createFactory()?.release() ?: error("VLC не найден") }
    println("VLC (первый раз): $vlc мс")
    val vlc2 = measureTimeMillis { Vlc.createFactory()?.release() }
    println("VLC (повторно): $vlc2 мс")
    val factory = Vlc.createFactory() ?: error("VLC не найден")
    val equalizer = factory.equalizer()
    check(equalizer.bands().size == 10) { "Ожидалось 10 полос эквалайзера, получено ${equalizer.bands().size}" }
    val player = factory.mediaPlayers().newMediaPlayer()
    val preset = equalizer.newEqualizer()
    preset.setPreamp(-6f)
    preset.setAmp(0, 6f)
    player.audio().setEqualizer(preset)
    player.audio().setEqualizer(null)
    player.release()
    factory.release()
    println("Эквалайзер libVLC: 10 полос, включение и выключение работают")
    val graph = measureTimeMillis { AppGraph().engine?.release() }
    println("AppGraph целиком: $graph мс")
    kotlin.system.exitProcess(0)
}
