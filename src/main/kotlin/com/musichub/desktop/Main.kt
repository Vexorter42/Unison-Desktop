package com.musichub.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.musichub.playback.PlaybackEngine
import com.musichub.playback.Vlc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.musichub.ui.GlassButton
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.UnisonApp
import com.musichub.ui.screens.openInBrowser
import com.musichub.youtube.NewPipeDownloader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

fun main() {
    val graph = AppGraph()
    NewPipe.init(NewPipeDownloader(graph.http), Localization("ru", "RU"), ContentCountry("RU"))
    if (graph.settings.current.yandexLoggedIn) {
        graph.scope.launch { runCatching { graph.yandex.refreshLikes() } }
    }
    // Как в Android-версии: проверка обновлений при запуске и раз в 12 часов
    graph.scope.launch {
        delay(5_000)
        while (true) {
            graph.updater.check()
            delay(12 * 60 * 60_000L)
        }
    }

    val icon = Thread.currentThread().contextClassLoader.getResourceAsStream("unison.png")?.use {
        BitmapPainter(Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap())
    }

    application {
        val windowState = rememberWindowState(size = DpSize(1320.dp, 860.dp))
        // Окно показывается сразу, а VLC (до секунды и больше на холодном диске) запускается в фоне
        val load by produceState<EngineLoad>(EngineLoad.Loading) {
            value = withContext(Dispatchers.IO) { graph.engine }?.let { EngineLoad.Ready(it) } ?: EngineLoad.Missing
        }
        val engine = (load as? EngineLoad.Ready)?.engine
        var mediaKeys by remember { mutableStateOf<MediaKeys?>(null) }
        LaunchedEffect(engine) {
            if (engine == null) return@LaunchedEffect
            mediaKeys = MediaKeys(onPlayPause = engine::togglePlay, onNext = engine::next, onPrevious = engine::previous, onStop = { engine.togglePlay() })
                .also { it.start() }
            // Кеш плагинов VLC строится на этом компьютере — следующие запуски не перебирают все DLL
            withContext(Dispatchers.IO) { Vlc.ensurePluginCache(graph.updater.current) }
        }
        val exit = {
            mediaKeys?.stop()
            engine?.release()
            exitApplication()
        }
        graph.requestExit = exit
        Window(
            onCloseRequest = exit,
            title = "Unison",
            icon = icon,
            state = windowState,
        ) {
            window.minimumSize = java.awt.Dimension(1000, 640)
            LaunchedEffect(Unit) { WindowsTitleBar.useDarkMode(window) }
            CompositionLocalProvider(LocalGraph provides graph) {
                when (val l = load) {
                    EngineLoad.Loading -> Splash()
                    EngineLoad.Missing -> NoVlcScreen()
                    is EngineLoad.Ready -> CompositionLocalProvider(LocalEngine provides l.engine) { UnisonApp() }
                }
            }
        }
    }
}

private sealed interface EngineLoad {
    data object Loading : EngineLoad
    data object Missing : EngineLoad
    data class Ready(val engine: PlaybackEngine) : EngineLoad
}

/** Пока запускается VLC: тот же тёмный фон, что у приложения, и название. */
@androidx.compose.runtime.Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D10)), contentAlignment = Alignment.Center) {
        Text("Unison", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.85f))
    }
}

@androidx.compose.runtime.Composable
private fun NoVlcScreen() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0D0D10)).padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Не найден VLC", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "Unison играет музыку через libVLC. Установите VLC media player (64-bit) и перезапустите приложение.",
                color = Color(0xFF9A9AA6),
            )
            GlassButton("Скачать VLC", onClick = { openInBrowser("https://www.videolan.org/vlc/") })
        }
    }
}
