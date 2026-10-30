package com.musichub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.data.AppDirs
import com.musichub.data.AppSettings
import com.musichub.data.Quality
import com.musichub.desktop.BrowserLogin
import com.musichub.ui.GlassButton
import com.musichub.ui.GlassChip
import com.musichub.ui.LocalGraph
import com.musichub.ui.PrimaryPill
import com.musichub.ui.glassFill
import com.musichub.ui.liquid.LiquidSwitch
import com.musichub.ui.liquid.LocalLiquidGlass
import com.musichub.ui.installUpdate
import com.musichub.update.Updater
import com.musichub.ui.onEnter
import com.musichub.ui.theme.LocalPalette
import com.musichub.ui.theme.Palettes
import com.musichub.vk.VkApi
import com.musichub.yandex.YandexApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.URI
import java.time.LocalDate
import java.io.File
import java.awt.Frame
import java.awt.FileDialog
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Restore

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val graph = LocalGraph.current
    val s by graph.settings.flow.collectAsState()
    fun set(block: (AppSettings) -> AppSettings) = graph.scope.launch { graph.settings.update(block) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Аккаунты и настройки", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        AccountCard(
            title = "Яндекс Музыка",
            loggedInAs = s.yandexLogin?.let { "Вход выполнен: $it" }.takeIf { s.yandexLoggedIn },
            hint = "Откроется окно браузера: войдите в Яндекс и нажмите «Разрешить». Окно закроется само.",
            browserLogin = {
                graph.browserLogin.login(YandexApi.OAUTH_URL) { pageUrls().firstNotNullOfOrNull(BrowserLogin::tokenFromUrl) }
            },
            manualHint = "Откройте вход в своём браузере, войдите и разрешите доступ. Браузер перейдёт на страницу, " +
                "в адресе которой есть access_token=… — скопируйте этот адрес целиком (или только токен) и вставьте сюда.",
            manualUrl = YandexApi.OAUTH_URL,
            placeholder = "Адрес страницы после входа или токен",
            onSubmit = { input ->
                val token = BrowserLogin.tokenFromUrl(input) ?: input
                val (uid, login) = graph.yandex.account(token)
                graph.settings.update { it.copy(yandexToken = token, yandexUid = uid, yandexLogin = login) }
                runCatching { graph.yandex.refreshLikes() }
            },
            onLogout = { set { it.copy(yandexToken = null, yandexUid = null, yandexLogin = null) } },
        )
        Spacer(Modifier.height(14.dp))
        AccountCard(
            title = "ВКонтакте",
            loggedInAs = s.vkName?.let { "Вход выполнен: $it" }.takeIf { s.vkLoggedIn },
            hint = "Вход через Kate Mobile в окне браузера. ВК иногда ограничивает доступ к музыке — тогда поиск в ВК просто пропускается.",
            browserLogin = {
                graph.browserLogin.login(VkApi.OAUTH_URL) { pageUrls().firstNotNullOfOrNull(BrowserLogin::tokenFromUrl) }
            },
            manualHint = "Откройте вход в своём браузере, после входа скопируйте адрес страницы oauth.vk.com/blank.html#access_token=… и вставьте сюда.",
            manualUrl = VkApi.OAUTH_URL,
            placeholder = "Адрес blank.html#access_token=… или токен",
            onSubmit = { input ->
                val token = BrowserLogin.tokenFromUrl(input) ?: input
                val (id, name) = graph.vk.profile(token)
                graph.settings.update { it.copy(vkToken = token, vkUserId = id, vkName = name) }
            },
            onLogout = { set { it.copy(vkToken = null, vkUserId = null, vkName = null) } },
        )
        Spacer(Modifier.height(14.dp))
        AccountCard(
            title = "YouTube Music",
            loggedInAs = s.ytAccountName?.let { "Вход выполнен: $it" }.takeIf { s.ytLoggedIn },
            hint = "Необязательно: поиск и музыка YouTube работают и без входа. Вход нужен для ваших лайков и плейлистов. " +
                "Откроется обычное окно браузера: войдите в Google. Когда откроется YouTube Music, окно закроется само.",
            browserLogin = {
                val all = graph.browserLogin.loginAndReadCookies(BrowserLogin.YOUTUBE_LOGIN_URL, doneTitle = "YouTube Music")
                BrowserLogin.youtubeCookie(all).also { if ("SAPISID=" !in it) error("Вход в Google не завершён — попробуйте ещё раз") }
            },
            manualHint = "1. Откройте music.youtube.com в своём браузере и войдите в аккаунт.\n" +
                "2. Нажмите F12 → вкладка «Сеть» (Network) → обновите страницу (F5).\n" +
                "3. Правой кнопкой по первому запросу к music.youtube.com → Копировать → «Копировать как cURL (bash)».\n" +
                "4. Вставьте сюда всё целиком — Unison сам найдёт cookie. Подойдёт и просто значение заголовка Cookie.",
            manualUrl = "https://music.youtube.com",
            placeholder = "cURL-запрос или заголовок Cookie",
            onSubmit = { input ->
                val cookie = cookieFromInput(input)
                if ("SAPISID=" !in cookie) error("В cookie нет SAPISID — похоже, скопирован запрос без входа в аккаунт")
                val name = graph.ytMusic.accountName(cookie)
                graph.settings.update { it.copy(ytCookie = cookie, ytAccountName = name) }
            },
            onLogout = { set { it.copy(ytCookie = null, ytAccountName = null) } },
        )

        Spacer(Modifier.height(28.dp))
        Text("Поиск и воспроизведение", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Toggle("Искать на YouTube", s.searchYoutube) { v -> set { it.copy(searchYoutube = v) } }
        Toggle("Искать во ВКонтакте", s.searchVk) { v -> set { it.copy(searchVk = v) } }
        Toggle("Искать на SoundCloud", s.searchSoundcloud, note = "Ремиксы, демо и треки, которых нет в других сервисах. Без входа") { v ->
            set { it.copy(searchSoundcloud = v) }
        }
        Toggle(
            "Подмешивать YouTube в Мою волну",
            s.mixYoutubeInWave,
            note = "Похожие треки с YouTube между треками волны — как в Android-версии",
        ) { v -> set { it.copy(mixYoutubeInWave = v) } }
        if (s.mixYoutubeInWave) {
            Row(
                Modifier.padding(start = 4.dp, top = 2.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Каждый", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(2, 3, 4, 5, 6, 8, 10).forEach { n -> GlassChip("$n-й", s.mixEvery == n) { set { it.copy(mixEvery = n) } } }
                Text("трек", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Toggle(
            "Замена, если трек не играет",
            s.fallbackToOthers,
            note = "Ищет тот же трек в другом сервисе: например, YouTube просит «подтвердить, что вы не бот» — играет из Яндекса",
        ) { v -> set { it.copy(fallbackToOthers = v) } }
        Spacer(Modifier.height(10.dp))
        Text("Качество", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Quality.entries.forEach { q -> GlassChip(q.label, s.quality == q) { set { it.copy(quality = q) } } }
        }

        Spacer(Modifier.height(28.dp))
        Text("Оформление", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Toggle("Liquid Glass", s.liquidGlass, note = "Стекло с преломлением, живой фон и пузырь-индикатор — как в Android-версии") { v ->
            set { it.copy(liquidGlass = v) }
        }
        Toggle(
            "Живая сфера в Моей волне",
            s.sphereMotion,
            note = "Сфера за надписью шевелится и пульсирует от басов. Выключите, если отвлекает — останется неподвижной " +
                "(звук после перезапуска снова пойдёт напрямую через VLC)",
        ) { v -> set { it.copy(sphereMotion = v) } }
        Toggle("Цвета из обложки", s.coverTheme, note = "Палитра окна берётся из обложки текущего трека") { v -> set { it.copy(coverTheme = v) } }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.forEachIndexed { i, p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { set { it.copy(palette = i) } }) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(p.gradient)
                            .then(if (s.palette == i) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                    )
                    Text(p.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text("Резервная копия", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        BackupSection()

        Spacer(Modifier.height(28.dp))
        Text("Обновления", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        UpdatesSection()

        Spacer(Modifier.height(28.dp))
        Text(
            "Данные приложения: ${AppDirs.root.absolutePath}\nUnison — свободная программа (GPL-3.0), использует NewPipe Extractor и libVLC.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountCard(
    title: String,
    loggedInAs: String?,
    hint: String,
    /** Вход в окне браузера: возвращает то же, что пользователь вставил бы вручную. */
    browserLogin: suspend () -> String,
    manualHint: String,
    manualUrl: String,
    placeholder: String,
    onSubmit: suspend (String) -> Unit,
    onLogout: () -> Unit,
) {
    val graph = LocalGraph.current
    val canUseBrowser = remember { graph.browserLogin.available }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf<Job?>(null) }
    var manual by remember { mutableStateOf(!canUseBrowser) }
    var error by remember { mutableStateOf<String?>(null) }
    val palette = LocalPalette.current

    fun submit() {
        val value = input.trim()
        if (value.isEmpty() || busy) return
        busy = true
        error = null
        graph.scope.launch {
            runCatching { onSubmit(value) }
                .onSuccess { input = "" }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    fun loginInBrowser() {
        error = null
        waiting = graph.scope.launch {
            runCatching { onSubmit(browserLogin()) }
                .onFailure { if (it !is CancellationException) error = it.message ?: it.javaClass.simpleName }
            waiting = null
        }
    }

    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glassFill()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (loggedInAs != null) GlassButton("Выйти", Icons.AutoMirrored.Rounded.Logout, onClick = onLogout)
        }
        if (loggedInAs != null) {
            Text(loggedInAs, color = palette.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            return@Column
        }
        if (canUseBrowser) {
            Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val job = waiting
                if (job == null) {
                    PrimaryPill("Войти через браузер", Icons.AutoMirrored.Rounded.Login, onClick = { loginInBrowser() })
                } else {
                    Text("Окно входа открыто — войдите в аккаунт…", fontSize = 14.sp, color = palette.accent)
                    GlassButton("Отмена", onClick = { job.cancel() })
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (manual) "Скрыть ручной вход" else "Ввести вручную",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { manual = !manual }.padding(6.dp),
                )
            }
        }
        if (manual) {
            Text(manualHint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Открыть", Icons.AutoMirrored.Rounded.OpenInNew, onClick = { openInBrowser(manualUrl) })
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(glassFill(strong = true)).padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    if (input.isEmpty()) {
                        Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().onEnter { submit() },
                    )
                }
                PrimaryPill(if (busy) "Проверяю…" else "Войти", null, onClick = { submit() }, enabled = input.isNotBlank() && !busy)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun BackupSection() {
    val graph = LocalGraph.current
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> String) {
        busy = true
        status = null
        error = null
        graph.scope.launch {
            runCatching { block() }.onSuccess { status = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
            busy = false
        }
    }

    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glassFill()).padding(18.dp)) {
        Text(
            "Свои плейлисты, «Любимое», статистика прослушиваний и настройки — в один файл. Формат тот же, что у Unison " +
                "на телефоне: копию можно перенести в обе стороны. Входы в аккаунты в копию не попадают.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Сохранить копию", Icons.Rounded.Save, enabled = !busy, onClick = {
                pickBackupFile(save = true)?.let { file ->
                    run {
                        val n = graph.backup.export(file)
                        "Сохранено: ${file.name} ($n плейлистов и треков «Любимого»)"
                    }
                }
            })
            GlassButton("Восстановить", Icons.Rounded.Restore, enabled = !busy, onClick = {
                pickBackupFile(save = false)?.let { file ->
                    run {
                        val r = graph.backup.restore(file)
                        "Восстановлено: плейлистов ${r.playlists}, в «Любимое» добавлено ${r.favorites}, прослушиваний ${r.stats}"
                    }
                }
            })
        }
        status?.let { Text(it, color = LocalPalette.current.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

/** Системный диалог выбора файла; null — отменили. Имя по умолчанию — как у Android-версии. */
private fun pickBackupFile(save: Boolean): File? {
    val dialog = FileDialog(
        null as Frame?,
        if (save) "Сохранить резервную копию Unison" else "Открыть резервную копию Unison",
        if (save) FileDialog.SAVE else FileDialog.LOAD,
    )
    dialog.file = if (save) "unison-backup-${LocalDate.now()}.json" else "*.json"
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, if (save && !name.endsWith(".json")) "$name.json" else name)
}

@Composable
private fun UpdatesSection() {
    val graph = LocalGraph.current
    val update by graph.updater.state.collectAsState()
    val current = graph.updater.current
    Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glassFill()).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if (current != null) "Unison $current" else "Unison (запуск из исходников)", fontSize = 15.sp)
                val status = when {
                    current == null -> "Обновления работают только в установленной версии"
                    update.checking -> "Проверяю…"
                    update.progress != null -> "Скачиваю обновление… ${(update.progress!! * 100).toInt()}%"
                    update.available != null -> "Доступна версия ${update.available!!.version}"
                    update.upToDate -> "Установлена последняя версия"
                    else -> "Проверяется сама при запуске и раз в 12 часов"
                }
                Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val release = update.available
            if (release != null && update.progress == null) {
                PrimaryPill("Обновить", null, onClick = { installUpdate(graph, release) })
            }
            GlassButton("Проверить", onClick = { graph.scope.launch { graph.updater.check() } }, enabled = !update.checking && current != null)
            GlassButton("Релизы", Icons.AutoMirrored.Rounded.OpenInNew, onClick = { openInBrowser(Updater.RELEASES_URL) })
        }
        update.available?.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
        }
        update.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.widthIn(max = 760.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            if (note != null) Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (LocalLiquidGlass.current) {
            LiquidSwitch(checked, onChange)
            return@Row
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LocalPalette.current.b,
                checkedThumbColor = Color.White,
                // По умолчанию кружок выключенного берёт outline темы — на стеклянной дорожке его не видно
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = glassFill(strong = true),
                uncheckedBorderColor = Color.White.copy(alpha = 0.22f),
            ),
        )
    }
}

/** Cookie из того, что вставил пользователь: «Копировать как cURL», строка заголовка или само значение. */
private fun cookieFromInput(input: String): String {
    val text = input.trim()
    Regex("""(?i)cookie:\s*([^'"\r\n]+)""").find(text)?.let { return it.groupValues[1].trim() }
    Regex("""(?:^|\s)-b\s+['"]([^'"]+)['"]""").find(text)?.let { return it.groupValues[1].trim() }
    return text
}

fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
        .recoverCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start() }
}
