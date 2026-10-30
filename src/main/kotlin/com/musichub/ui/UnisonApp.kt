package com.musichub.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.animation.core.animateDpAsState
import kotlin.math.abs
import com.musichub.ui.liquid.rememberLiquidBackdrop
import com.musichub.ui.liquid.backdropSource
import com.musichub.ui.liquid.LocalLiquidGlass
import com.musichub.ui.liquid.LocalBackdrop
import com.musichub.ui.liquid.AmbientBackground
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.border
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import com.musichub.ui.liquid.panelSurface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musichub.desktop.AppGraph
import com.musichub.ui.screens.FavoritesScreen
import com.musichub.ui.screens.HomeScreen
import com.musichub.ui.screens.LibraryScreen
import com.musichub.ui.screens.SearchScreen
import com.musichub.ui.screens.SearchState
import com.musichub.ui.screens.SettingsScreen
import com.musichub.ui.screens.TrackListScreen
import com.musichub.ui.screens.TrackSource
import com.musichub.ui.screens.TransferScreen
import com.musichub.ui.theme.LocalPalette
import com.musichub.ui.theme.UnisonTheme
import com.musichub.update.Release
import kotlinx.coroutines.launch

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Главная", Icons.Rounded.Home),
    SEARCH("Поиск", Icons.Rounded.Search),
    LIBRARY("Медиатека", Icons.Rounded.LibraryMusic),
    FAVORITES("Любимое", Icons.Rounded.Favorite),
    TRANSFER("Перенос", Icons.Rounded.SwapHoriz),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}

@Composable
fun UnisonApp() {
    val engine = LocalEngine.current
    val state by engine.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    /** Открытый плейлист поверх вкладки «Медиатека». */
    var opened by remember { mutableStateOf<TrackSource?>(null) }
    var panel by remember { mutableStateOf(SidePanel.NONE) }
    val search = remember { SearchState() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        engine.messages.collect { snackbar.showSnackbar(it) }
    }

    val graph = LocalGraph.current
    val settings by graph.settings.flow.collectAsState()
    val liquid = settings.liquidGlass
    val backdrop = rememberLiquidBackdrop()

    UnisonTheme(coverUrl = state.track?.coverUrl) {
        Box(Modifier.fillMaxSize()) {
            // Фон окна; в теме Liquid Glass он же — то, что панели размывают и преломляют
            Box(Modifier.fillMaxSize().then(if (liquid) Modifier.backdropSource(backdrop) else Modifier)) {
                if (liquid) AmbientBackground()
                CoverBackdrop(state.track?.coverUrl, overAmbient = liquid)
            }
            CompositionLocalProvider(LocalLiquidGlass provides liquid, LocalBackdrop provides backdrop.takeIf { liquid }) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    Sidebar(
                        selected = tab,
                        collapsed = settings.sidebarCollapsed,
                        onToggle = { graph.scope.launch { graph.settings.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) } } },
                    ) {
                        tab = it
                        opened = null
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Crossfade(tab to opened) { (t, o) ->
                            when {
                                o != null -> TrackListScreen(o, onBack = { opened = null })
                                t == Tab.HOME -> HomeScreen(openSettings = { tab = Tab.SETTINGS })
                                t == Tab.SEARCH -> SearchScreen(search)
                                t == Tab.LIBRARY -> LibraryScreen(
                                    open = { opened = it },
                                    openFavorites = { tab = Tab.FAVORITES },
                                    openSettings = { tab = Tab.SETTINGS },
                                    openTransfer = { tab = Tab.TRANSFER },
                                )
                                t == Tab.FAVORITES -> FavoritesScreen(onBack = null)
                                t == Tab.TRANSFER -> TransferScreen()
                                else -> SettingsScreen()
                            }
                        }
                    }
                    AnimatedVisibility(panel != SidePanel.NONE) {
                        Row {
                            Spacer(Modifier.width(12.dp))
                            when (panel) {
                                SidePanel.LYRICS -> LyricsPanel(Modifier.width(380.dp))
                                else -> QueuePanel(Modifier.width(380.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PlayerBar(panel, onPanel = { panel = it })
            }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp)) { data ->
                Snackbar(data, containerColor = Color(0xFF26262E), contentColor = Color.White, shape = RoundedCornerShape(14.dp))
            }
        }
    }
}

/** Боковое меню; свёрнутое — только иконки (состояние запоминается). */
@Composable
private fun Sidebar(selected: Tab, collapsed: Boolean, onToggle: () -> Unit, onSelect: (Tab) -> Unit) {
    val palette = LocalPalette.current
    val width by animateDpAsState(if (collapsed) 72.dp else 220.dp, spring(dampingRatio = 0.85f, stiffness = 500f), label = "sidebar")
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .panelSurface(0.55f)
            .padding(horizontal = if (collapsed) 12.dp else 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = if (collapsed) 8.dp else 6.dp, top = 6.dp, bottom = 18.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(palette.gradient))
            if (!collapsed) {
                Spacer(Modifier.width(10.dp))
                Text("Unison", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        val liquid = LocalLiquidGlass.current
        var rowHeight by remember { mutableStateOf(0) }
        Box {
        if (liquid && rowHeight > 0) SelectionBubble(Tab.entries.indexOf(selected), rowHeight)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Tab.entries.forEach { t ->
            val hover = remember { MutableInteractionSource() }
            val hovered by hover.collectIsHoveredAsState()
            val isSelected = t == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowHeight = it.height }
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isSelected && !liquid -> glassFill(strong = true)
                            hovered && !isSelected -> glassFill()
                            else -> Color.Transparent
                        },
                    )
                    .hoverable(hover)
                    .clickable { onSelect(t) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    t.icon, t.title,
                    tint = if (isSelected) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                if (!collapsed) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        t.title,
                        fontSize = 14.sp,
                        maxLines = 1,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
        }
        Spacer(Modifier.weight(1f))
        UpdateCard(collapsed)
        // Свернуть / развернуть меню
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (hovered) glassFill() else Color.Transparent)
                .hoverable(hover)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.MenuOpen,
                if (collapsed) "Развернуть меню" else "Свернуть меню",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { scaleX = if (collapsed) -1f else 1f },
            )
            if (!collapsed) {
                Spacer(Modifier.width(12.dp))
                Text("Свернуть", fontSize = 14.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Пузырь выделения в теме Liquid Glass: перетекает к выбранной вкладке на пружине,
 * в полёте вытягивается по ходу движения и сужается, на месте «оседает».
 */
@Composable
private fun SelectionBubble(index: Int, rowHeight: Int) {
    val density = LocalDensity.current
    val gap = with(density) { 4.dp.toPx() }
    val target = index * (rowHeight + gap)
    val y = remember { Animatable(target) }
    LaunchedEffect(target) { y.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 320f)) }
    val speed = (abs(y.velocity) / 3000f).coerceAtMost(0.35f)
    val palette = LocalPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { rowHeight.toDp() })
            .graphicsLayer {
                translationY = y.value
                scaleY = 1f + speed
                scaleX = 1f - speed * 0.25f
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f))))
            .background(palette.accent.copy(alpha = 0.08f))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))), RoundedCornerShape(14.dp)),
    )
}

/** Карточка «Доступна новая версия» внизу боковой панели. */
@Composable
private fun UpdateCard(collapsed: Boolean) {
    val graph = LocalGraph.current
    val update by graph.updater.state.collectAsState()
    val release = update.available ?: return
    if (collapsed) {
        // В свёрнутом меню — одна кнопка-значок
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(glassFill(strong = true))
                .clickable(enabled = update.progress == null) { installUpdate(graph, release) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SystemUpdateAlt, "Обновить до ${release.version}", tint = LocalPalette.current.accent, modifier = Modifier.size(20.dp))
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(glassFill(strong = true)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Доступна версия ${release.version}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val progress = update.progress
        if (progress == null) {
            PrimaryPill("Обновить", Icons.Rounded.SystemUpdateAlt, onClick = { installUpdate(graph, release) })
        } else {
            Text("Скачиваю… ${(progress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        update.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
    }
}

/** Скачать установщик, запустить его и закрыться: после установки Unison откроется сам. */
fun installUpdate(graph: AppGraph, release: Release) {
    graph.scope.launch {
        val msi = runCatching { graph.updater.download(release) }.getOrNull() ?: return@launch
        graph.updater.installAndRestart(msi)
        graph.requestExit()
    }
}
