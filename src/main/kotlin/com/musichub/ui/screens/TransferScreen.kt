package com.musichub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.musichub.transfer.TransferState
import com.musichub.transfer.TransferTarget
import com.musichub.ui.Cover
import com.musichub.ui.GeneratedCover
import com.musichub.ui.GlassButton
import com.musichub.ui.LocalGraph
import com.musichub.ui.PrimaryPill
import com.musichub.ui.glassFill
import com.musichub.ui.theme.LocalPalette

/** Перенос плейлиста из любого сервиса (или по ссылке) в Яндекс, YouTube Music или «Мои плейлисты». */
@Composable
fun TransferScreen() {
    val graph = LocalGraph.current
    val repo = graph.transfer
    val state by repo.state.collectAsState()
    val settings by graph.settings.flow.collectAsState()
    val palette = LocalPalette.current
    var picked by remember { mutableStateOf<TrackSource?>(null) }
    var link by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(TransferTarget.LOCAL) }
    val fromLink = remember(link) { if (link.isBlank()) null else graph.sources.fromLink(link) }
    val source = fromLink ?: picked
    val working = state is TransferState.Working
    val sources by produceState<List<TrackSource>?>(null, settings.yandexUid, settings.ytCookie, settings.vkUserId) {
        value = graph.sources.available()
    }

    fun loggedIn(t: TransferTarget) = when (t) {
        TransferTarget.YANDEX -> settings.yandexLoggedIn
        TransferTarget.YOUTUBE -> settings.ytLoggedIn
        TransferTarget.LOCAL -> true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp)) {
        Text("Перенос плейлистов", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(
            "Плейлист из любого сервиса — в Яндекс Музыку, YouTube Music или в свои плейлисты Unison.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(20.dp))

        Column(Modifier.widthIn(max = 860.dp)) {
            Step("1", "Откуда")
            Field(link, placeholder = "Ссылка на плейлист Яндекса, YouTube, SoundCloud или ВК") {
                link = it
                graph.sources.fromLink(it)?.let { s -> name = s.title }
            }
            if (link.isNotBlank() && fromLink == null) {
                Text("Не узнаю эту ссылку", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Text("…или выберите из своих:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(glassFill())) {
                val list = sources
                if (list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).padding(24.dp).size(28.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
                } else {
                    LazyColumn(Modifier.padding(6.dp)) {
                        items(list, key = { it.id }) { s ->
                            val selected = fromLink == null && picked?.id == s.id
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) glassFill(strong = true) else Color.Transparent)
                                    .clickable(enabled = !working) {
                                        picked = s
                                        link = ""
                                        name = s.title
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(40.dp)) {
                                    if (s.coverUrl != null) Cover(s.coverUrl, size = 40.dp, corner = 8.dp)
                                    else GeneratedCover(Icons.Rounded.LibraryMusic, palette.b, Modifier.fillMaxSize())
                                }
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(s.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(palette.accent))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Step("2", "Куда")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TransferTarget.entries.forEach { t ->
                    val enabled = loggedIn(t)
                    val selected = target == t
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) glassFill(strong = true) else glassFill())
                            .then(if (selected) Modifier.border(1.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp)) else Modifier)
                            .clickable(enabled = enabled && !working) { target = t }
                            .padding(14.dp),
                    ) {
                        Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when {
                                !enabled -> "Нужен вход в настройках"
                                t == TransferTarget.LOCAL -> "Треки копируются как есть"
                                else -> "Каждый трек ищется там"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Step("3", "Название")
            Field(name, placeholder = "Название нового плейлиста") { name = it }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryPill(
                    "Перенести",
                    Icons.Rounded.SwapHoriz,
                    onClick = { source?.let { s -> repo.start(target, name.ifBlank { s.title }, s.load) } },
                    enabled = source != null && !working && loggedIn(target),
                )
                Text(
                    "В Яндекс и YouTube каждый трек ищется по названию, исполнителю и длительности.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (val s = state) {
                TransferState.Idle -> Unit
                is TransferState.Working -> Column(Modifier.padding(top = 20.dp)) {
                    Text(if (s.total > 0) "${s.step}: ${s.done} из ${s.total}" else s.step, fontSize = 14.sp)
                    Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(glassFill(strong = true))) {
                        if (s.total > 0) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(s.done.toFloat() / s.total).clip(CircleShape).background(palette.accent))
                        }
                    }
                    GlassButton("Отменить", onClick = repo::reset)
                }
                is TransferState.Done -> Column(
                    Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(glassFill()).padding(18.dp),
                ) {
                    Text("Готово: «${s.title}» → ${s.target.service}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Перенесено: ${s.found} из ${s.total}", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    if (s.missing.isNotEmpty()) {
                        Text("Не нашлись:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                        s.missing.take(50).forEach { Text("• ${it.artists} — ${it.title}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (s.missing.size > 50) Text("…и ещё ${s.missing.size - 50}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassButton("Ок", onClick = repo::reset)
                }
                is TransferState.Failed -> Column(Modifier.padding(top = 20.dp)) {
                    Text(s.message, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    GlassButton("Ок", onClick = repo::reset)
                }
            }
        }
    }
}

@Composable
private fun Step(n: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(glassFill(strong = true)), contentAlignment = Alignment.Center) {
            Text(n, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Field(value: String, placeholder: String, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(glassFill(strong = true)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
