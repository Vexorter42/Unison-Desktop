package com.musichub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.graphics.toPixelMap
import com.musichub.ui.WaveSphere
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class WaveSphereTest {
    @Test
    fun flowingLightKeepsTheTitleAreaDark() = runDesktopComposeUiTest(width = 400, height = 400) {
        setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                WaveSphere(
                    colorA = Color(0xFF16EFC2),
                    colorB = Color(0xFF087F69),
                    motion = false,
                    bass = { 0f },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()
        val center = onRoot().captureToImage().toPixelMap()[200, 200]
        assertTrue("Центр должен оставаться тёмным: $center", center.red < 0.05f && center.green < 0.05f && center.blue < 0.05f)
    }
}
