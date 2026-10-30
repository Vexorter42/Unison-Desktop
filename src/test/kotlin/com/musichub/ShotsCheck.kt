package com.musichub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.musichub.desktop.AppGraph
import com.musichub.ui.LocalEngine
import com.musichub.ui.LocalGraph
import com.musichub.ui.UnisonApp
import com.musichub.youtube.NewPipeDownloader
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.io.File

/**
 * Скриншоты экранов без звука и без изменения настроек: главная, медиатека, перенос, настройки.
 * Запуск: ./gradlew shotsCheck [-Ppalette=0] — картинки в build/shots. С палитрой работает на временной
 * копии настроек (вход в аккаунты тот же), настоящие настройки не трогаются.
 */
@OptIn(ExperimentalTestApi::class)
fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "shots" }).apply { mkdirs() }
    val tempData = args.getOrNull(1)?.toIntOrNull()?.let { palette ->
        val real = File(System.getenv("APPDATA"), "Unison/settings.json")
        File(out, "data").apply {
            mkdirs()
            val json = real.takeIf { it.exists() }?.readText() ?: "{}"
            File(this, "settings.json").writeText(
                json.replace(Regex(""""palette":\s*\d+"""), """"palette": $palette""")
                    .replace(Regex(""""coverTheme":\s*\w+"""), """"coverTheme": false""")
                    // Покадровая анимация сферы не даёт тесту дождаться «покоя» — снимаем неподвижную
                    .replace(Regex(""""sphereMotion":\s*\w+,?\s*"""), "")
                    .replaceFirst("{", """{"sphereMotion": false, """),
            )
            System.setProperty("unison.dataDir", path)
        }
    }
    val graph = AppGraph()
    NewPipe.init(NewPipeDownloader(graph.http), Localization("ru", "RU"), ContentCountry("RU"))
    val engine = graph.engine ?: error("VLC не найден")

    // Копия настроек (с токенами входа) удаляется при любом исходе
    try {
    runDesktopComposeUiTest(width = 1320, height = 820) {
        setContent {
            CompositionLocalProvider(LocalGraph provides graph, LocalEngine provides engine) { UnisonApp() }
        }
        fun SemanticsNodeInteractionsProvider.shot(name: String) {
            val png = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!
            File(out, "$name.png").writeBytes(png.bytes)
            println("Скриншот: ${File(out, "$name.png")}")
        }
        mainClock.advanceTimeBy(500)
        shot("1-home")
        onNodeWithText("Перенос").performClick()
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithText("Любимое Unison").fetchSemanticsNodes().isNotEmpty() }
        shot("2-transfer")
        onNodeWithText("Медиатека").performClick()
        waitForIdle()
        shot("3-library")
        onNodeWithText("Настройки").performClick()
        onNodeWithText("Liquid Glass").performScrollTo()
        waitForIdle()
        shot("4-settings")
        if (tempData != null) {
            // Сворачивание меню меняет настройку — только на временной копии. Не на главной:
            // сфера в тесте рисуется на процессоре, и кадры анимации меню заняли бы минуты
            onNodeWithText("Свернуть").performClick()
            mainClock.advanceTimeBy(800)
            waitForIdle()
            shot("5-collapsed")
        }
    }
    engine.release()
    } finally {
        tempData?.deleteRecursively()
    }
    kotlin.system.exitProcess(0)
}
