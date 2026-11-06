package com.musichub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.musichub.data.model.Source
import com.musichub.data.model.Track
import com.musichub.desktop.AppGraph
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.UnisonApp
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalTestApi::class)
class FullPlayerTest {
    @Test
    fun coverOpensAndClosesFullPlayer() {
        val dir = Files.createTempDirectory("unison-full-player-test").toFile()
        System.setProperty("unison.dataDir", dir.path)
        val graph = AppGraph()
        val engine = graph.engine ?: error("VLC не найден")
        kotlinx.coroutines.runBlocking { graph.settings.update { it.copy(sphereMotion = false) } }
        try {
            runDesktopComposeUiTest(width = 1200, height = 760) {
                setContent {
                    CompositionLocalProvider(LocalGraph provides graph, LocalEngine provides engine) { UnisonApp() }
                }
                engine.playTracks(listOf(Track(Source.LOCAL, "test-track", "Тестовый трек", "Тестовый артист")))
                waitUntil { engine.state.value.track != null }

                onNodeWithContentDescription("Открыть большой плеер").performClick()
                onNodeWithText("Сейчас играет").assertIsDisplayed()
                onNodeWithContentDescription("Свернуть плеер").performClick()
                onNodeWithText("Сейчас играет").assertDoesNotExist()
            }
        } finally {
            engine.release()
            dir.deleteRecursively()
            System.clearProperty("unison.dataDir")
        }
    }
}
