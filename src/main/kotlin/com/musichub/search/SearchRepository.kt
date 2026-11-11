package com.musichub.search

import com.musichub.data.SettingsRepository
import com.musichub.data.model.Source
import com.musichub.data.model.Track
import com.musichub.data.model.TrackRef
import com.musichub.soundcloud.SoundCloudApi
import com.musichub.vk.VkApi
import com.musichub.yandex.YandexApi
import com.musichub.youtube.YouTubeApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Единый поиск: Яндекс + ВК + YouTube + SoundCloud, одинаковые треки склеиваются в один. */
class SearchRepository(
    private val yandex: YandexApi,
    private val vk: VkApi,
    private val youtube: YouTubeApi,
    private val soundcloud: SoundCloudApi,
    private val settings: SettingsRepository,
) {
    suspend fun search(query: String, category: SearchCategory = SearchCategory.TRACKS): SearchResult = coroutineScope {
        val s = settings.current
        when (category) {
            SearchCategory.TRACKS -> searchTracks(query, s)
            SearchCategory.ARTISTS -> {
                val ya = async { runCatching { yandex.searchArtists(query) } }
                val yt = async { if (s.searchYoutube) runCatching { youtube.searchArtists(query) } else Result.success(emptyList()) }
                val results = listOf("Яндекс" to ya.await(), "YouTube" to yt.await())
                SearchResult(
                    artists = results.flatMap { it.second.getOrDefault(emptyList()) }.distinctBy { "${it.source}:${it.id}" },
                    errors = results.mapNotNull { (name, r) -> r.exceptionOrNull()?.let { "$name: ${it.message}" } },
                )
            }
            SearchCategory.ALBUMS -> {
                val ya = async { runCatching { yandex.searchAlbums(query) } }
                val yt = async { if (s.searchYoutube) runCatching { youtube.searchAlbums(query) } else Result.success(emptyList()) }
                val results = listOf("Яндекс" to ya.await(), "YouTube" to yt.await())
                SearchResult(
                    albums = results.flatMap { it.second.getOrDefault(emptyList()) }.distinctBy { "${it.source}:${it.id}" },
                    errors = results.mapNotNull { (name, r) -> r.exceptionOrNull()?.let { "$name: ${it.message}" } },
                )
            }
            SearchCategory.PLAYLISTS -> {
                val ya = async { runCatching { yandex.searchPlaylists(query) } }
                val yt = async { if (s.searchYoutube) runCatching { youtube.searchPlaylists(query) } else Result.success(emptyList()) }
                val results = listOf("Яндекс" to ya.await(), "YouTube" to yt.await())
                SearchResult(
                    playlists = results.flatMap { it.second.getOrDefault(emptyList()) }.distinctBy { "${it.source}:${it.id}" },
                    errors = results.mapNotNull { (name, r) -> r.exceptionOrNull()?.let { "$name: ${it.message}" } },
                )
            }
        }
    }

    private suspend fun searchTracks(query: String, s: com.musichub.data.AppSettings): SearchResult = coroutineScope {
        val ya = async { runCatching { yandex.search(query) } }
        val vkRes = async { if (s.searchVk && s.vkLoggedIn) runCatching { vk.search(query) } else Result.success(emptyList()) }
        val yt = async { if (s.searchYoutube) runCatching { youtube.search(query) } else Result.success(emptyList()) }
        val sc = async { if (s.searchSoundcloud) runCatching { soundcloud.search(query) } else Result.success(emptyList()) }
        val results = listOf("Яндекс" to ya.await(), "ВК" to vkRes.await(), "YouTube" to yt.await(), "SoundCloud" to sc.await())
        var merged = emptyList<Track>()
        for ((_, r) in results) merged = merge(merged, r.getOrDefault(emptyList()))
        SearchResult(
            tracks = merged,
            errors = results.mapNotNull { (name, r) -> r.exceptionOrNull()?.let { "$name: ${it.message}" } },
        )
    }

    suspend fun loadCollection(item: CollectionSearchResult): List<Track> = when (item.source) {
        Source.YANDEX -> when (item.kind) {
            CollectionKind.ALBUM -> yandex.albumTracks(item.id)
            CollectionKind.PLAYLIST -> item.id.split(':').let { yandex.playlistTracks(it[0], it[1].toLong()) }
        }
        Source.YOUTUBE -> youtube.playlist(item.url ?: item.id).second
        else -> emptyList()
    }

    fun merge(base: List<Track>, extra: List<Track>): List<Track> {
        val result = base.toMutableList()
        val added = mutableListOf<Track>()
        for (t in extra) {
            val idx = result.indexOfFirst { existing ->
                existing.alternates.none { it.source == t.source } && TrackMatcher.score(existing, t) >= 0.78
            }
            if (idx >= 0) {
                val e = result[idx]
                result[idx] = e.copy(alternates = e.alternates + TrackRef(t.source, t.sourceId))
            } else {
                added += t
            }
        }
        return result + added
    }

    /**
     * Похожие треки: станция Яндекса «радио по треку» (если есть вход) + похожие с YouTube.
     * Трек из любого сервиса сначала сопоставляется с Яндексом/YouTube.
     */
    suspend fun similar(seed: Track): List<Track> = coroutineScope {
        val ya = async {
            runCatching {
                if (!settings.current.yandexLoggedIn) return@runCatching emptyList()
                val yandexId = when (seed.source) {
                    Source.YANDEX -> seed.yandexId
                    else -> seed.alternates.firstOrNull { it.source == Source.YANDEX }?.sourceId?.substringBefore(':')
                        ?: findOnYandex(seed)?.yandexId
                } ?: return@runCatching emptyList()
                yandex.stationTracks("track:$yandexId", seed.yandexId).tracks
            }.getOrDefault(emptyList())
        }
        val yt = async {
            runCatching {
                val videoId = when (seed.source) {
                    Source.YOUTUBE -> seed.sourceId
                    else -> seed.alternates.firstOrNull { it.source == Source.YOUTUBE }?.sourceId
                        ?: youtube.findMatch(seed)?.sourceId
                } ?: return@runCatching emptyList()
                youtube.related(videoId)
            }.getOrDefault(emptyList())
        }
        (ya.await() + yt.await()).filter { it.key != seed.key }.distinctBy { it.key }
    }

    private suspend fun findOnYandex(t: Track): Track? {
        val q = TrackMatcher.splitYoutube(t)
        val results = runCatching { yandex.search("${q.artists} ${q.title}") }.getOrNull() ?: return null
        return TrackMatcher.best(q, results, threshold = 0.65)
    }
}
