package com.musichub

import com.musichub.data.PlayEvent
import com.musichub.data.PlayStats
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.io.path.createTempDirectory

class PlayStatsTest {
    @Test
    fun historyKeepsRepeatedPlaysAndUpdatesCurrentOne() {
        val file = createTempDirectory("unison-history").resolve("stats.json").toFile()
        try {
            val stats = PlayStats(file)
            val first = event(1000, 5000)
            val second = event(2000, 3000)
            stats.record(first)
            stats.record(second)
            stats.record(first.copy(playedMs = 9000))
            assertEquals(2, stats.events.value.size)
            assertEquals(9000L, stats.events.value.first { it.startedAt == 1000L }.playedMs)
            assertEquals(2, PlayStats(file).all().size)
        } finally {
            file.delete()
            file.parentFile.delete()
        }
    }

    private fun event(started: Long, played: Long) = PlayEvent(
        trackKey = "yt:song", title = "Song", artists = "Artist", source = "YOUTUBE",
        startedAt = started, playedMs = played, durationMs = 120000,
        skipped = false, fromWave = false, trackJson = "{}",
    )
}
