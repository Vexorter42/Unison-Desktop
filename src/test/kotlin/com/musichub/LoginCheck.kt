package com.musichub

import com.musichub.desktop.AppGraph
import com.musichub.desktop.BrowserLogin
import kotlinx.coroutines.runBlocking

/**
 * Проверка окна входа без настоящего аккаунта: токен из #фрагмента адреса (как у Яндекса и ВК)
 * и cookie youtube.com (анонимные — SAPISID появится только после входа). Окно должно закрыться само.
 */
fun main(): Unit = runBlocking {
    val login = AppGraph().browserLogin
    println("Браузер для входа: ${if (login.available) "есть" else "НЕТ"}")
    val token = login.login("https://example.com/#access_token=test-token-42&expires_in=1") {
        pageUrls().firstNotNullOfOrNull(BrowserLogin::tokenFromUrl)
    }
    println("Токен из адреса: ${if (token == "test-token-42") "ок" else "НЕ тот: $token"}")
    val cookie = login.login("https://music.youtube.com/") {
        if (pageUrls().none { "music.youtube.com" in it }) return@login null
        BrowserLogin.youtubeCookie(cookies()).takeIf { it.isNotEmpty() }
    }
    println("Cookie youtube.com: ${cookie.split("; ").map { it.substringBefore('=') }}")
    kotlin.system.exitProcess(0)
}
