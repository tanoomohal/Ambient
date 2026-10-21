package com.tanoo.ambient.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import com.tanoo.ambient.AmbientApp
import com.tanoo.ambient.Apps
import com.tanoo.ambient.ClockStyle
import com.tanoo.ambient.Corners
import com.tanoo.ambient.HdmiSource
import com.tanoo.ambient.Inputs
import com.tanoo.ambient.NowPlaying
import com.tanoo.ambient.Place
import com.tanoo.ambient.Stat
import com.tanoo.ambient.ThemeMode
import com.tanoo.ambient.WeatherApi
import kotlinx.coroutines.launch

private fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1).mod(size)]

@Composable
fun SettingsPage(
    onEditApps: () -> Unit,
    onLocation: () -> Unit,
    initialFocus: FocusRequester,
) {
    val prefs = AmbientApp.prefs
    val context = LocalContext.current
    val sources = remember { Inputs.list(context) }
    val auto = Inputs.preferred(context)

    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 36.dp)) {
        Column(Modifier.width(250.dp)) {
            Txt("Settings", size = 34.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(8.dp))
            Txt("Changes apply instantly.\nPress Back to return home.", size = 15.sp, color = P.text2, maxLines = 3, lineHeight = 22.sp)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 60.dp, start = 12.dp, end = 12.dp),
        ) {
            item { Section("Appearance") }
            item {
                Setting("Theme", prefs.theme.label, "Automatic follows sunrise and sunset", Modifier.focusRequester(initialFocus)) {
                    prefs.theme = ThemeMode.entries.after(prefs.theme)
                }
            }

            item {
                Setting("Screen corners", prefs.corners.label, "Rounds the edges of the picture on your wall") {
                    prefs.corners = Corners.entries.after(prefs.corners)
                }
            }

            item { Section("Power & HDMI") }
            item {
                Setting(
                    "When the projector turns on",
                    if (prefs.startOnHdmi) "Open ${auto?.label ?: "HDMI"}" else "Show Home",
                    "Opens HDMI only when HDMI-CEC sees a device connected to it",
                ) { prefs.startOnHdmi = !prefs.startOnHdmi }
            }
            item {
                val current = sources.firstOrNull { it.id == prefs.hdmiInputId }
                Setting(
                    "HDMI source",
                    current?.let { "${it.label} · ${it.portLabel}" } ?: "Automatic${auto?.let { " (${it.label})" } ?: ""}",
                    "Used by the HDMI button and power-on",
                ) {
                    val options: List<HdmiSource?> = listOf<HdmiSource?>(null) + sources
                    prefs.hdmiInputId = options.after(current)?.id ?: ""
                }
            }

            item { Section("Home") }
            item {
                Setting("Clock", prefs.clockStyle.label) { prefs.clockStyle = ClockStyle.entries.after(prefs.clockStyle) }
            }
            item { Setting("Weather", onOff(prefs.showWeather)) { prefs.showWeather = !prefs.showWeather } }
            item {
                Setting(
                    "Now Playing", onOff(prefs.showNowPlaying),
                    if (NowPlaying.hasAccess) "Shows what's playing in apps on the projector"
                    else "Needs media access; grant it with the ADB command in the README",
                ) { prefs.showNowPlaying = !prefs.showNowPlaying }
            }
            items(Stat.entries) { stat ->
                val on = stat in prefs.statList
                Setting(stat.label, onOff(on), if (stat == Stat.entries.first()) "Up to four stats show under the clock" else null) {
                    val set = prefs.statList.toMutableSet()
                    if (on) set.remove(stat) else set.add(stat)
                    prefs.stats = set.sortedBy { it.ordinal }.joinToString(",") { it.name }
                }
            }

            item { Section("Weather") }
            item { Setting("Location", prefs.city.ifBlank { "Not set" }, onClick = onLocation) }
            item { Setting("Units", if (prefs.fahrenheit) "°F" else "°C") { prefs.fahrenheit = !prefs.fahrenheit } }

            item { Section("Apps & system") }
            item { Setting("Choose apps", "${prefs.appList.size} selected", onClick = onEditApps) }
            item {
                Setting("Keep screen awake on Home", onOff(prefs.keepAwake), "Stops the screensaver while the dashboard is showing") {
                    prefs.keepAwake = !prefs.keepAwake
                }
            }
            item { Setting("Android settings", "") { Apps.openSystemSettings(context) } }
        }
    }
}

private fun onOff(b: Boolean) = if (b) "On" else "Off"

@Composable
private fun Section(title: String) {
    Txt(
        title.uppercase(), size = 13.sp, weight = FontWeight.SemiBold, color = P.text3, letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 2.dp),
    )
}

@Composable
private fun Setting(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        focusedScale = 1.02f,
        focusedContainer = if (P.isDark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F),
        focusedContentColor = if (P.isDark) Color(0xFF1D1D1F) else Color(0xFFF5F5F7),
        showBorder = false,
    ) {
        val ink = LocalContentColor.current
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = if (subtitle == null) 15.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Txt(title, size = 17.sp, weight = FontWeight.Medium, color = ink)
                if (subtitle != null) Txt(subtitle, size = 13.sp, color = ink.copy(alpha = 0.6f))
            }
            if (value.isNotEmpty()) Txt(value, size = 17.sp, color = ink.copy(alpha = 0.7f))
        }
    }
}

// ——— Location ————————————————————————————————————————————————————————————————————

@Composable
fun LocationPage(onDone: () -> Unit, initialFocus: FocusRequester) {
    val prefs = AmbientApp.prefs
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(prefs.city) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf("Type a city, then press Search") }
    var fieldFocused by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank()) return
        status = "Searching…"
        scope.launch {
            results = runCatching { WeatherApi.search(query) }.getOrElse { emptyList() }
            status = if (results.isEmpty()) "No matches" else "Choose your city"
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 36.dp)) {
        Column(Modifier.width(250.dp)) {
            Txt("Location", size = 34.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(8.dp))
            Txt("Used for weather, air quality\nand sunrise times.", size = 15.sp, color = P.text2, maxLines = 3, lineHeight = 22.sp)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 20.sp, color = P.text),
                    cursorBrush = SolidColor(P.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(initialFocus)
                        .onFocusChanged { fieldFocused = it.isFocused }
                        .background(P.card, RoundedCornerShape(14.dp))
                        .border(
                            BorderStroke(2.dp, if (fieldFocused) P.focusBorder else P.cardBorder),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                )
                Spacer(Modifier.width(14.dp))
                Pill(onClick = { search() }, label = "Search")
            }
            Spacer(Modifier.height(14.dp))
            Txt(status, size = 14.sp, color = P.text2, modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                items(results) { place ->
                    Setting(place.name, "", place.detail) {
                        prefs.city = place.name
                        prefs.latitude = "%.4f".format(java.util.Locale.US, place.lat)
                        prefs.longitude = "%.4f".format(java.util.Locale.US, place.lon)
                        onDone()
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (query.isNotBlank()) search() }
}
