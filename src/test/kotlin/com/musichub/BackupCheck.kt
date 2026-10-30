package com.musichub

import com.musichub.data.BackupFile
import com.musichub.desktop.AppGraph
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.nio.file.Files

/**
 * Резервная копия на временной папке данных (настоящие не трогаются): копия в формате телефона
 * восстанавливается (дважды — без дублей), затем копия с ПК читается по той же схеме, что у телефона.
 */
fun main(): Unit = runBlocking {
    val dir = Files.createTempDirectory("unison-backup-check").toFile()
    System.setProperty("unison.dataDir", dir.path)
    val graph = AppGraph()
    val track1 = """{"source":"YANDEX","sourceId":"33311009:3998592","title":"Группа крови","artists":"КИНО","durationMs":285000}"""
    val track2 = """{"source":"LOCAL","sourceId":"12345","title":"Демо","artists":"Я","durationMs":120000}"""
    fun q(s: String) = JsonPrimitive(s).toString()
    val phone = """
        {"app":"Unison","version":1,"createdAt":1761800000000,
         "settings":{"searchYoutube":true,"searchVk":false,"fallbackToOthers":true,"mixYoutubeInWave":true,"mixEvery":3,
           "quality":"NORMAL","fadeSeconds":6,"autoCacheMb":2048,"waveMood":"calm","waveDiversity":"discover",
           "ytProxyEnabled":true,"ytProxyType":"SOCKS","ytProxyHost":"127.0.0.1","ytProxyPort":10808,
           "liquidGlass":true,"palette":3,"coverTheme":false},
         "playlists":[{"name":"С телефона","createdAt":1761700000000,"tracks":[
           {"playlistId":7,"trackKey":"ya:33311009:3998592","position":0,"trackJson":${q(track1)}},
           {"playlistId":7,"trackKey":"lc:12345","position":1,"trackJson":${q(track2)}}]}],
         "favorites":[{"trackKey":"ya:33311009:3998592","trackJson":${q(track1)},"addedAt":1761750000000,"sentYandex":true,"sentYoutube":false}],
         "stats":[{"id":1,"trackKey":"ya:33311009:3998592","title":"Группа крови","artists":"КИНО","source":"YANDEX",
           "startedAt":1761760000000,"playedMs":200000,"durationMs":285000,"skipped":false,"fromWave":true,"trackJson":${q(track1)}}],
         "newFieldFromFuture":42}
    """.trimIndent()
    val phoneFile = File(dir, "phone.json").apply { writeText(phone) }

    val r1 = graph.backup.restore(phoneFile)
    val r2 = graph.backup.restore(phoneFile)
    val s = graph.settings.current
    println("Восстановление: $r1; повторно: $r2")
    println("Настройки: mood=${s.waveMood} diversity=${s.waveDiversity} quality=${s.quality} palette=${s.palette} fade=${s.fadeSeconds} proxy=${s.ytProxyEnabled} mix=${s.mixYoutubeInWave}/${s.mixEvery}")
    println("Плейлисты: ${graph.myPlaylists.items.value.map { "${it.name} (${it.tracks.size})" }}, «Любимое»: ${graph.favorites.items.value.size}, прослушиваний: ${graph.stats.all().size}")

    val out = File(dir, "desktop.json")
    graph.backup.export(out)
    // Телефон читает без ignoreUnknownKeys-послаблений по обязательным полям: проверяем строгим парсером
    val strict = Json { ignoreUnknownKeys = false }
    val back = strict.decodeFromString(BackupFile.serializer(), out.readText())
    println("Копия с ПК читается строго: плейлистов ${back.playlists.size}, «Любимое» ${back.favorites.size}, прослушиваний ${back.stats.size}, fade=${back.settings.fadeSeconds}")
    dir.deleteRecursively()
    kotlin.system.exitProcess(0)
}

