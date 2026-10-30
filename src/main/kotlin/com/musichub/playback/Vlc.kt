package com.musichub.playback

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.provider.DiscoveryDirectoryProvider
import java.io.File

/**
 * VLC, который едет в установщике (папка ресурсов приложения), находится раньше установленного в систему.
 * Регистрируется через META-INF/services.
 */
class BundledVlcDirectoryProvider : DiscoveryDirectoryProvider {
    override fun priority(): Int = 10

    override fun directories(): Array<String> = listOfNotNull(System.getProperty("compose.application.resources.dir"))
        .filter { File(it, "libvlc.dll").exists() }
        .toTypedArray()

    override fun supported(): Boolean = true
}

object Vlc {
    /**
     * Строит кеш плагинов (plugins.dat) у VLC из установщика, если его нет или он от другой версии.
     * Без кеша libVLC при каждом запуске открывает все ~200 DLL плагинов. Собрать кеш заранее нельзя:
     * VLC сверяет его с датами файлов, а установщик их меняет — поэтому строим уже на этом компьютере.
     */
    fun ensurePluginCache(appVersion: String?) {
        val dir = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return
        val gen = File(dir, "vlc-cache-gen.exe")
        val plugins = File(dir, "plugins")
        val marker = File(plugins, ".unison-cache")
        if (appVersion == null || !gen.exists() || !plugins.canWrite()) return
        if (File(plugins, "plugins.dat").exists() && marker.takeIf { it.exists() }?.readText() == appVersion) return
        runCatching {
            val p = ProcessBuilder(gen.absolutePath, plugins.absolutePath).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) marker.writeText(appVersion)
        }
    }

    /** null — VLC не найден ни в приложении, ни в системе. */
    fun createFactory(): MediaPlayerFactory? {
        if (!NativeDiscovery().discover()) return null
        return runCatching {
            MediaPlayerFactory(
                "--no-video",
                "--quiet",
                "--intf=dummy",
                "--network-caching=1500",
                "--no-metadata-network-access",
                "--no-media-library",
            )
        }.getOrNull()
    }
}
