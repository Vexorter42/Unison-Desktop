package com.musichub.data

import com.musichub.data.model.Source
import com.musichub.data.model.Track
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.io.path.createTempDirectory

class OfflineDownloadsTest {
    @Test fun downloadsSharedFilesOnceAndDeletesAfterLastPlaylist() = runBlocking {
        val folder = createTempDirectory("unison-offline").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var requests = 0
        server.createContext("/audio") { exchange ->
            requests++
            val bytes = "example-audio-data".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val track = Track(Source.YOUTUBE, "video", "Song", "Artist")
            val store = OfflineDownloads(OkHttpClient(), { "http://127.0.0.1:${server.address.port}/audio" }, folder)
            assertEquals(0, store.downloadPlaylist("a", "First", listOf(track)).failed)
            assertEquals(0, store.downloadPlaylist("b", "Second", listOf(track)).failed)
            assertEquals(1, requests)
            assertNotNull(store.pathFor(track.key))
            store.removePlaylist("a")
            assertNotNull(store.pathFor(track.key))
            val reopened = OfflineDownloads(OkHttpClient(), { error("not needed") }, folder)
            assertNotNull(reopened.pathFor(track.key))
            reopened.removePlaylist("b")
            assertNull(reopened.pathFor(track.key))
        } finally {
            server.stop(0)
            folder.listFiles()?.forEach { it.delete() }
            folder.delete()
        }
    }

    @Test fun rejectsPlaylistResponses() = runBlocking {
        val folder = createTempDirectory("unison-hls").toFile()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/audio") { exchange ->
            val bytes = "#EXTM3U\n#EXT-X-VERSION:3".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val track = Track(Source.YOUTUBE, "video", "Song", "Artist")
            val store = OfflineDownloads(OkHttpClient(), { "http://127.0.0.1:${server.address.port}/audio" }, folder)
            assertEquals(1, store.downloadPlaylist("a", "First", listOf(track)).failed)
            assertNull(store.pathFor(track.key))
        } finally {
            server.stop(0)
            folder.listFiles()?.forEach { it.delete() }
            folder.delete()
        }
    }
}
