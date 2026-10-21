package com.tanoo.ambient.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanoo.ambient.AmbientApp
import com.tanoo.ambient.AppEntry
import com.tanoo.ambient.HdmiSource

private const val COLUMNS = 4

@Composable
fun AppsPage(
    apps: List<AppEntry>,
    hdmi: HdmiSource?,
    onHdmi: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onEdit: () -> Unit,
    onBackHome: () -> Unit,
    initialFocus: FocusRequester,
) {
    val prefs = AmbientApp.prefs
    val byPkg = apps.associateBy { it.packageName }
    val chosen = prefs.appList.mapNotNull { byPkg[it] }
    var moving by remember { mutableStateOf<String?>(null) }

    fun move(pkg: String, delta: Int) {
        val list = prefs.appList.toMutableList()
        val from = list.indexOf(pkg)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.removeAt(from)
        list.add(to, pkg)
        prefs.setAppList(list)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, top = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Apps", size = 34.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(4.dp))
                Txt(
                    if (moving != null) "Use the arrows to move · press OK when done"
                    else "Hold OK on an app to rearrange",
                    size = 15.sp, color = P.text2,
                )
            }
        }
        Spacer(Modifier.height(18.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hdmi") {
                Tile(
                    label = hdmi?.let { "${it.label} · ${it.portLabel}" } ?: "HDMI",
                    onClick = onHdmi,
                    modifier = Modifier
                        .focusRequester(initialFocus)
                        .upGoesHome(true, onBackHome),
                ) { HdmiArt(hdmi) }
            }
            itemsIndexed(chosen, key = { _, a -> a.packageName }) { index, app ->
                val isMoving = moving == app.packageName
                Tile(
                    label = app.label,
                    onClick = { if (isMoving) moving = null else onLaunch(app) },
                    onLongClick = { moving = app.packageName },
                    lifted = isMoving,
                    modifier = Modifier
                        .animateItem()
                        .upGoesHome(moving == null && index + 1 < COLUMNS, onBackHome)
                        .onPreviewKeyEvent { e ->
                            if (!isMoving || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionLeft -> { move(app.packageName, -1); true }
                                Key.DirectionRight -> { move(app.packageName, 1); true }
                                Key.DirectionUp -> { move(app.packageName, -COLUMNS); true }
                                Key.DirectionDown -> { move(app.packageName, COLUMNS); true }
                                Key.Back, Key.Escape -> { moving = null; true }
                                else -> false
                            }
                        },
                ) { AppArt(app) }
            }
            item(key = "edit") {
                Tile(
                    label = "Choose apps",
                    onClick = onEdit,
                    modifier = Modifier.upGoesHome(chosen.size + 2 <= COLUMNS, onBackHome),
                ) {
                    Box(Modifier.fillMaxSize().background(P.fill), contentAlignment = Alignment.Center) {
                        Glyph(34.dp, P.text2) { plusGlyph(it) }
                    }
                }
            }
        }
    }
}

private fun Modifier.upGoesHome(enabled: Boolean, onBackHome: () -> Unit) =
    if (!enabled) this else onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) { onBackHome(); true } else false
    }

/** tvOS-style tile: 16:9 artwork, title appears under it only while focused. */
@Composable
fun Tile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    lifted: Boolean = false,
    alwaysShowLabel: Boolean = false,
    art: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused || alwaysShowLabel || lifted) 1f else 0f, label = "label")
    Column(modifier.onFocusChanged { focused = it.hasFocus }) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(16.dp),
            focusedScale = if (lifted) 1.16f else 1.1f,
        ) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) { art() }
            if (lifted) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000))))
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Txt(
            label, size = 15.sp, weight = FontWeight.Medium,
            color = if (focused) P.text else P.text2,
            modifier = Modifier
                .alpha(labelAlpha)
                .align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun AppArt(app: AppEntry) {
    if (app.banner != null) {
        Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        // No TV banner (phone apps like Netflix here): big icon on its own background colour.
        val bg = app.tint?.let { Color(it) } ?: P.fill
        Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
            if (app.icon != null) {
                Image(app.icon, app.label, Modifier.fillMaxSize(0.62f).aspectRatio(1f))
            } else {
                Txt(app.label, size = 18.sp, weight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HdmiArt(hdmi: HdmiSource?) {
    val live = hdmi?.connected == true && hdmi.hasCecDevice
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF3A3A3C)))),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(46.dp, Color.White) { hdmiGlyph(it) }
            Txt(hdmi?.label ?: "HDMI", size = 17.sp, weight = FontWeight.SemiBold, color = Color.White)
        }
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) Color(0xFF30D158) else Color(0xFF8E8E93)))
            Spacer(Modifier.width(6.dp))
            Txt(if (live) "Connected" else "No device", size = 12.sp, color = Color(0xCCFFFFFF))
        }
    }
}

// ——— Picker ——————————————————————————————————————————————————————————————————————

@Composable
fun AppPicker(apps: List<AppEntry>, onDone: () -> Unit, initialFocus: FocusRequester) {
    val prefs = AmbientApp.prefs
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, top = 36.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Choose apps", size = 34.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(4.dp))
                Txt("Selected apps appear on your Apps page in the order you pick them", size = 15.sp, color = P.text2)
            }
            Pill(onClick = onDone, label = "Done", icon = { Glyph(18.dp, LocalContentColorOnPill()) { checkGlyph(it) } })
        }
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp, start = 6.dp, end = 6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.packageName }) { app ->
                val selected = app.packageName in prefs.appList
                val first = app == apps.firstOrNull()
                Tile(
                    label = app.label,
                    alwaysShowLabel = true,
                    onClick = {
                        val list = prefs.appList.toMutableList()
                        if (selected) list.remove(app.packageName) else list.add(app.packageName)
                        prefs.setAppList(list)
                    },
                    modifier = if (first) Modifier.focusRequester(initialFocus) else Modifier,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AppArt(app)
                        if (!selected) Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(0xFF0A84FF) else Color(0x55FFFFFF)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Glyph(16.dp, Color.White) { checkGlyph(it) }
                        }
                    }
                }
            }
        }
    }
}
