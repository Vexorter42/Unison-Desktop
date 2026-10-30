package com.musichub.soundcloud

import com.musichub.data.Quality
import com.musichub.data.model.Source
import com.musichub.data.model.Track
import com.musichub.search.TrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException
import org.schabi.newpipe.extractor.services.soundcloud.linkHandler.SoundcloudSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

/** SoundCloud через NewPipe Extractor (без аккаунта: client_id NewPipe находит сам). */
class SoundCloudApi {

    private val service get() = ServiceList.SoundCloud

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    suspend fun search(query: String): List<Track> = io { searchBlocking(query) }

    fun searchBlocking(query: String): List<Track> {
        val extractor = service.getSearchExtractor(query, listOf(SoundcloudSearchQueryHandlerFactory.TRACKS), "")
        extractor.fetchPage()
        return extractor.initialPage.items.mapNotNull { it.toTrack() }
    }

    /** Ищет на SoundCloud тот же трек, что и [target] (для замены). */
    fun findMatchBlocking(target: Track): Track? {
        val q = TrackMatcher.splitYoutube(target)
        val candidates = runCatching { searchBlocking("${q.artists} ${q.title}") }.getOrDefault(emptyList())
        return TrackMatcher.best(q, candidates, threshold = 0.65)
    }

    /** [path] — «автор/трек» из адреса soundcloud.com. Треки Go+ без подписки отдают только 30-секундный отрывок — их не играем. */
    fun streamUrlBlocking(path: String, quality: Quality): String {
        val info = try {
            StreamInfo.getInfo(service, "$BASE/$path")
        } catch (e: SoundCloudGoPlusContentException) {
            throw IOException("SoundCloud: трек доступен только по подписке Go+", e)
        }
        val streams = info.audioStreams.filter { it.isUrl }
        // Цельный файл перематывается надёжнее, HLS — запасной вариант (VLC умеет оба)
        val pick = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.ifEmpty { streams }
            .sortedBy { it.averageBitrate }
            .let { if (quality == Quality.LOW) it.firstOrNull() else it.lastOrNull() }
            ?: throw IOException("SoundCloud: нет аудиопотока")
        return pick.content
    }

    /** Плейлист («автор/sets/название»): название и треки. */
    suspend fun playlist(path: String, limit: Int = 1000): Pair<String, List<Track>> = io {
        val url = "$BASE/$path"
        val info = PlaylistInfo.getInfo(service, url)
        val items = info.relatedItems.toMutableList<InfoItem>()
        var next: Page? = info.nextPage
        while (Page.isValid(next) && items.size < limit) {
            val page = PlaylistInfo.getMoreItems(service, url, next)
            items += page.items
            next = page.nextPage
        }
        info.name to items.mapNotNull { it.toTrack() }
    }

    private fun InfoItem.toTrack(): Track? {
        if (this !is StreamInfoItem) return null
        val path = url.substringAfter("soundcloud.com/", "").substringBefore('?').trim('/')
        if (path.isEmpty()) return null
        return Track(
            source = Source.SOUNDCLOUD,
            sourceId = path,
            title = name,
            artists = uploaderName?.ifBlank { null } ?: "SoundCloud",
            durationMs = duration.coerceAtLeast(0) * 1000,
            coverUrl = thumbnails.maxByOrNull { it.height }?.url,
        )
    }

    private companion object {
        const val BASE = "https://soundcloud.com"
    }
}
