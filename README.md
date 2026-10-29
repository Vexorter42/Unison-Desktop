# Unison для Windows

Десктопный клиент Unison на Compose Multiplatform (JVM). Логика сервисов скопирована из Android-версии (`app/`):
`YandexApi`, `YouTubeApi` / `YtMusicApi` / `NewPipeDownloader`, `VkApi`, `SearchRepository` + `TrackMatcher`,
`LyricsRepository`, `FallbackDns`, модель `Track`. Позже общее можно вынести в shared-модуль.
Только на десктопе: SoundCloud (`SoundCloudApi`, тоже через NewPipe Extractor).

Чем заменены Android-зависимости:

| Android | Десктоп |
|---|---|
| Media3 / ExoPlayer | libVLC через vlcj; потоки идут через локальный прокси `StreamProxy` (OkHttp: DoH, User-Agent для googlevideo, Range) |
| DataStore, Room | JSON в `%APPDATA%\Unison` (`settings.json`, `favorites.json`) |
| Coil | `ImageLoader` на OkHttp + Skia |
| Palette | `CoverColors` (гистограмма оттенков обложки) |
| MediaSession | медиаклавиши Windows через `RegisterHotKey` (JNA) |
| WebView для входа | `BrowserLogin`: окно Chrome/Edge с чистым профилем, токен/cookie читаются по DevTools-протоколу |

## Запуск и сборка

```bash
./gradlew :desktop:run
./gradlew :desktop:packageMsi :desktop:packageExe   # build/compose/binaries/main/{msi,exe}
```

VLC едет внутри установщика: libvlc и аудио-плагины копируются из установленного VLC
(`C:/Program Files/VideoLAN/VLC`, другой путь — `-PvlcDir=...`). Для `run` и сборки VLC 3.x (64-bit) должен быть установлен.

## Проверки

```bash
./gradlew :desktop:smokeCheck   # поиск, склейка, YouTube/Яндекс, перемотка, медиаклавиша, текст, Моя волна (если есть вход)
./gradlew :desktop:loginCheck   # окно входа открывается, токен и cookie читаются, окно закрывается само
./gradlew :desktop:uiCheck      # настоящий интерфейс без окна: поиск → клик → играет; скриншоты в desktop/build/ui-check
```
