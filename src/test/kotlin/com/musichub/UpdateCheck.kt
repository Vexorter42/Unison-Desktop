package com.musichub

import com.musichub.desktop.AppGraph
import com.musichub.update.Updater
import kotlinx.coroutines.runBlocking

/**
 * Проверка обновления на настоящем GitHub Releases: «установлена» версия из аргумента (по умолчанию 0.0.1),
 * последний релиз должен найтись и установщик — скачаться целиком. Сама установка не запускается.
 * Запуск: ./gradlew updateCheck -PfakeVersion=0.1.0
 */
fun main(args: Array<String>): Unit = runBlocking {
    check(Updater.isNewer("0.1.10", "0.1.9") && !Updater.isNewer("0.1.1", "0.1.1") && !Updater.isNewer("0.1", "0.1.0"))
    System.setProperty("unison.version", args.firstOrNull() ?: "0.0.1")
    val updater = AppGraph().updater
    updater.check()
    val st = updater.state.value
    println("Установлена ${updater.current}, на GitHub: ${st.available?.version ?: "новее нет"}${st.error?.let { " — $it" } ?: ""}")
    val release = st.available ?: kotlin.system.exitProcess(if (st.upToDate) 0 else 1)
    val msi = updater.download(release)
    println("Скачано: ${msi.name}, ${msi.length()} из ${release.size} байт — ${if (msi.length() == release.size) "ок" else "НЕ совпадает"}")
    msi.delete()
    kotlin.system.exitProcess(0)
}
