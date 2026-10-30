package com.musichub.transfer

import com.musichub.desktop.AppGraph
import com.musichub.ui.screens.TrackSource
import com.musichub.util.tracksCount
import com.musichub.vk.VkPlaylist
import java.net.URI
import java.net.URLDecoder

/** Списки треков, которые можно перенести: из аккаунтов, «Любимое», свои плейлисты — и по ссылке. */
class Sources(private val graph: AppGraph) {

    /** Все списки, доступные по входам в аккаунты. Ходит в сеть — вызывать из корутины. */
    suspend fun available(): List<TrackSource> {
        val s = graph.settings.current
        val list = mutableListOf<TrackSource>()
        list += TrackSource("favorites", "Любимое Unison", tracksCount(graph.favorites.items.value.size), null) {
            graph.favorites.items.value.map { it.track }
        }
        graph.myPlaylists.items.value.forEach { p ->
            list += TrackSource("my:${p.id}", p.name, "Мой плейлист · ${tracksCount(p.tracks.size)}", p.tracks.firstOrNull()?.coverUrl) {
                graph.myPlaylists.get(p.id)?.tracks.orEmpty()
            }
        }
        if (s.yandexLoggedIn) {
            list += TrackSource("ya:liked", "Мне нравится", "Яндекс Музыка", null) { graph.yandex.likedTracks() }
            runCatching { graph.yandex.playlists() }.getOrDefault(emptyList()).forEach { p ->
                list += TrackSource("ya:${p.ownerUid}:${p.kind}", p.title, "Плейлист Яндекса · ${tracksCount(p.trackCount)}", p.coverUrl) {
                    graph.yandex.playlistTracks(p.ownerUid.toString(), p.kind)
                }
            }
        }
        if (s.ytLoggedIn) {
            list += TrackSource("yt:liked", "Понравившиеся", "YouTube Music", null) { graph.ytMusic.liked() }
            runCatching { graph.ytMusic.playlists() }.getOrDefault(emptyList()).forEach { p ->
                list += TrackSource("yt:${p.browseId}", p.title, "Плейлист YouTube Music", p.coverUrl) { graph.ytMusic.playlistTracks(p.browseId) }
            }
        }
        if (s.vkLoggedIn) {
            list += TrackSource("vk:my", "Моя музыка", "ВКонтакте", null) { graph.vk.myAudios() }
            runCatching { graph.vk.playlists() }.getOrDefault(emptyList()).forEach { p ->
                list += TrackSource("vk:${p.ownerId}_${p.id}", p.title, "Плейлист ВК · ${tracksCount(p.count)}", p.coverUrl) { graph.vk.playlistTracks(p) }
            }
        }
        return list
    }

    /**
     * Плейлист или альбом по ссылке Яндекс Музыки, YouTube (Music), SoundCloud или ВКонтакте.
     * Название настоящего плейлиста подставляется при загрузке, если сервис его отдаёт.
     */
    fun fromLink(text: String): TrackSource? {
        val url = Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', '»', '"') ?: return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val p = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
            val k = it.substringBefore('=', "")
            if (k.isEmpty()) null else k to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
        }.toMap()
        return when {
            host.startsWith("music.yandex.") -> when {
                p.size >= 4 && p[0] == "users" && p[2] == "playlists" ->
                    TrackSource("ya:${p[1]}:${p[3]}", "Плейлист Яндекса", "по ссылке", null) { graph.yandex.playlistTracks(p[1], p[3].toLong()) }
                p.size >= 2 && p[0] == "album" ->
                    TrackSource("ya:album:${p[1]}", "Альбом Яндекса", "по ссылке", null) { graph.yandex.albumTracks(p[1]) }
                p.size >= 2 && p[0] == "playlists" ->
                    TrackSource("ya:uuid:${p[1]}", "Плейлист Яндекса", "по ссылке", null) { graph.yandex.playlistByUuid(p[1]) }
                else -> null
            }
            host == "youtube.com" || host == "music.youtube.com" -> query["list"]?.takeIf { it.isNotBlank() && !it.startsWith("RD") }?.let { id ->
                TrackSource("yt:link:$id", "Плейлист YouTube", "по ссылке", null) {
                    graph.youtube.playlist("https://www.youtube.com/playlist?list=$id").second
                }
            }
            host == "soundcloud.com" && p.size >= 3 && p[1] == "sets" -> "${p[0]}/sets/${p[2]}".let { path ->
                TrackSource("sc:$path", "Плейлист SoundCloud", "по ссылке", null) { graph.soundcloud.playlist(path).second }
            }
            host == "vk.com" || host == "vk.ru" -> vkPlaylist(p, query)?.let { pl ->
                TrackSource("vk:${pl.ownerId}_${pl.id}", "Плейлист ВК", "по ссылке", null) { graph.vk.playlistTracks(pl) }
            }
            else -> null
        }
    }

    /** vk.com/music/playlist/<owner>_<id>_<key> или vk.com/audio?act=audio_playlist<owner>_<id>&access_hash=<key> */
    private fun vkPlaylist(p: List<String>, query: Map<String, String>): VkPlaylist? {
        if (p.size >= 3 && p[0] == "music" && (p[1] == "playlist" || p[1] == "album")) {
            val parts = p[2].split('_')
            val owner = parts.getOrNull(0)?.toLongOrNull()
            val id = parts.getOrNull(1)?.toLongOrNull()
            if (owner != null && id != null) return VkPlaylist(owner, id, parts.getOrNull(2), "", 0, null)
        }
        val ids = query["act"]?.removePrefix("audio_playlist")?.split('_')?.mapNotNull { it.toLongOrNull() }
        if (ids != null && ids.size == 2) return VkPlaylist(ids[0], ids[1], query["access_hash"], "", 0, null)
        return null
    }
}
