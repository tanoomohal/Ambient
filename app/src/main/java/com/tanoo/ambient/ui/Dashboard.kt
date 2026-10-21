package com.tanoo.ambient.ui

import android.text.format.DateFormat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import com.tanoo.ambient.AmbientApp
import com.tanoo.ambient.AqiLevel
import com.tanoo.ambient.ClockStyle
import com.tanoo.ambient.HdmiSource
import com.tanoo.ambient.NowPlaying
import com.tanoo.ambient.Sky
import com.tanoo.ambient.Stat
import com.tanoo.ambient.Track
import com.tanoo.ambient.Weather
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun Dashboard(
    now: LocalDateTime,
    weather: Weather?,
    weatherError: Boolean,
    hdmi: HdmiSource?,
    onHdmi: () -> Unit,
    onSettings: () -> Unit,
    onApps: () -> Unit,
    onWeatherClick: () -> Unit,
    initialFocus: FocusRequester,
) {
    val prefs = AmbientApp.prefs
    val track = if (prefs.showNowPlaying) NowPlaying.track else null

    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, top = 36.dp, bottom = 30.dp)
    ) {
        TopBar(now, hdmi, onHdmi, onSettings, initialFocus)
        Spacer(Modifier.height(26.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            if (prefs.showWeather) {
                WeatherCard(
                    now = now,
                    weather = weather,
                    error = weatherError,
                    hasLocation = prefs.latitude.isNotBlank(),
                    onClick = onWeatherClick,
                    modifier = Modifier
                        .width(250.dp)
                        .fillMaxHeight(),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    ClockBlock(now, prefs.clockStyle)
                }
                val stats = prefs.statList.take(4)
                if (stats.isNotEmpty()) StatsRow(stats, weather)
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = track != null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    track?.let { NowPlayingBar(it) }
                }
            }
            Spacer(Modifier.width(24.dp))
            AppsChip(onApps)
        }
    }
}

@Composable
private fun TopBar(
    now: LocalDateTime,
    hdmi: HdmiSource?,
    onHdmi: () -> Unit,
    onSettings: () -> Unit,
    initialFocus: FocusRequester,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(
                greeting(now).uppercase(), size = 13.sp, weight = FontWeight.SemiBold,
                color = P.text2, letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.height(4.dp))
            Txt(
                now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                size = 22.sp, weight = FontWeight.Medium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(
                onClick = onHdmi,
                modifier = Modifier.focusRequester(initialFocus),
                label = hdmi?.label ?: "HDMI",
                icon = {
                    val connected = hdmi?.connected == true && hdmi.hasCecDevice
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (connected) Color(0xFF30D158) else P.text3)
                    )
                },
            )
            Pill(onClick = onSettings, icon = {
                Glyph(20.dp, LocalContentColorOnPill()) { gearGlyph(it) }
            })
        }
    }
}

private fun greeting(now: LocalDateTime) = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

// ——— Weather ————————————————————————————————————————————————————————————————————

private fun skyGradient(sky: Sky, isDay: Boolean): List<Color> = when {
    sky == Sky.CLEAR && isDay -> listOf(Color(0xFF6E8BF0), Color(0xFF9DB4FA))
    sky == Sky.CLEAR -> listOf(Color(0xFF161D38), Color(0xFF2C3966))
    sky == Sky.PARTLY && isDay -> listOf(Color(0xFF6A84C8), Color(0xFF9EB0D8))
    sky == Sky.PARTLY -> listOf(Color(0xFF1E2540), Color(0xFF3A4462))
    sky == Sky.SNOW -> listOf(Color(0xFF8FA3BF), Color(0xFFC9D5E6))
    sky == Sky.FOG -> listOf(Color(0xFF7D8794), Color(0xFFA9B1BC))
    sky == Sky.STORM -> listOf(Color(0xFF262A40), Color(0xFF474B6C))
    sky == Sky.RAIN || sky == Sky.DRIZZLE -> if (isDay) listOf(Color(0xFF4A5F7E), Color(0xFF7489A8))
    else listOf(Color(0xFF1C2433), Color(0xFF364256))
    else -> if (isDay) listOf(Color(0xFF6F7C92), Color(0xFF9EABC0)) else listOf(Color(0xFF1F2330), Color(0xFF383E50))
}

@Composable
private fun WeatherCard(
    now: LocalDateTime,
    weather: Weather?,
    error: Boolean,
    hasLocation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val gradient = weather?.let { skyGradient(it.sky, it.isDay) }
        ?: listOf(P.card, P.card)
    val ink = if (weather == null) P.text else Color.White
    val prefs = AmbientApp.prefs
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        container = Color.Transparent,
        focusedScale = 1.03f,
        showBorder = weather == null,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradient))
                .padding(24.dp)
        ) {
            if (weather == null) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    Txt(
                        when {
                            !hasLocation -> "Add your city"
                            error -> "Weather unavailable"
                            else -> "Loading…"
                        },
                        size = 22.sp, weight = FontWeight.Medium, color = ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    Txt(
                        if (!hasLocation) "Select to choose a location" else prefs.city,
                        size = 15.sp, color = P.text2, maxLines = 2,
                    )
                }
                return@Box
            }
            weather.nextSunEvent(now)?.let { (rising, at) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(22.dp, ink) { sunEventGlyph(it, rising) }
                    Spacer(Modifier.width(8.dp))
                    Txt(formatTime(at), size = 18.sp, weight = FontWeight.Medium, color = ink)
                }
            }
            Glyph(64.dp, ink.copy(alpha = 0.92f), Modifier.align(Alignment.TopEnd)) {
                weatherGlyph(it, weather.sky, weather.isDay)
            }
            Column(Modifier.align(Alignment.BottomStart)) {
                Txt("${weather.temp.roundToInt()}°", size = 84.sp, weight = FontWeight.Light, color = ink, letterSpacing = (-3).sp)
                Txt(weather.condition, size = 18.sp, weight = FontWeight.Medium, color = ink)
                Spacer(Modifier.height(2.dp))
                Txt(
                    "${prefs.city} · H ${weather.high.roundToInt()}°  L ${weather.low.roundToInt()}°",
                    size = 14.sp, color = ink.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun formatTime(t: LocalDateTime): String {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    return t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
}

// ——— Clock ——————————————————————————————————————————————————————————————————————

@Composable
private fun ClockBlock(now: LocalDateTime, style: ClockStyle) {
    if (style == ClockStyle.OFF) return
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (style == ClockStyle.BOTH || style == ClockStyle.ANALOG) {
            AnalogClock(now, if (style == ClockStyle.ANALOG) 230 else 196)
        }
        if (style == ClockStyle.BOTH) Spacer(Modifier.width(44.dp))
        if (style == ClockStyle.BOTH || style == ClockStyle.DIGITAL) {
            val hour = if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
            val big = if (style == ClockStyle.DIGITAL) 120.sp else 98.sp
            Row(verticalAlignment = Alignment.Bottom) {
                Column {
                    Txt(
                        if (is24) "%02d".format(hour) else "%02d".format(hour), size = big,
                        weight = FontWeight.Light, letterSpacing = (-2).sp, lineHeight = big,
                    )
                    Txt(
                        "%02d".format(now.minute), size = big, weight = FontWeight.Light,
                        color = P.text2, letterSpacing = (-2).sp, lineHeight = big,
                    )
                }
                if (!is24) {
                    Spacer(Modifier.width(12.dp))
                    Txt(
                        if (now.hour < 12) "AM" else "PM", size = 20.sp, weight = FontWeight.Medium,
                        color = P.text2, modifier = Modifier.padding(bottom = 18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalogClock(now: LocalDateTime, sizeDp: Int) {
    val face = P.text
    val tick = P.text3
    val second = P.clockSecond
    Canvas(Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        fun at(angleDeg: Double, len: Float) = Offset(
            c.x + (cos((angleDeg - 90) * PI / 180) * len).toFloat(),
            c.y + (sin((angleDeg - 90) * PI / 180) * len).toFloat(),
        )
        for (i in 0 until 12) {
            val a = i * 30.0
            val major = i % 3 == 0
            drawLine(
                if (major) face else tick, at(a, r * (if (major) 0.78f else 0.84f)), at(a, r * 0.95f),
                strokeWidth = r * (if (major) 0.035f else 0.025f), cap = StrokeCap.Round,
            )
        }
        val sec = now.second
        val min = now.minute + sec / 60.0
        val hr = (now.hour % 12) + min / 60.0
        drawLine(face, c, at(hr * 30, r * 0.5f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
        drawLine(face, c, at(min * 6, r * 0.76f), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
        drawLine(second, at(sec * 6.0 + 180, r * 0.14f), at(sec * 6.0, r * 0.86f), strokeWidth = r * 0.018f, cap = StrokeCap.Round)
        drawCircle(face, r * 0.06f, c)
        drawCircle(second, r * 0.03f, c)
    }
}

// ——— Stats ——————————————————————————————————————————————————————————————————————

@Composable
private fun StatsRow(stats: List<Stat>, weather: Weather?) {
    Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
        stats.forEach { stat ->
            val (value, unit, label, color) = statDisplay(stat, weather)
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(value, size = 44.sp, weight = FontWeight.Light, color = color, letterSpacing = (-1).sp)
                    if (unit.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Txt(unit, size = 14.sp, color = P.text2, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Txt(label, size = 14.sp, weight = FontWeight.Medium, color = P.text2)
            }
        }
    }
}

private data class StatView(val value: String, val unit: String, val label: String, val color: Color)

@Composable
private fun statDisplay(stat: Stat, w: Weather?): StatView {
    val dash = "–"
    val text = P.text
    return when (stat) {
        Stat.AQI -> {
            val aqi = w?.aqi
            StatView(aqi?.toString() ?: dash, "", aqi?.let { "AQI · ${AqiLevel.of(it).label}" } ?: "AQI",
                aqi?.let { Color(AqiLevel.of(it).color) } ?: text)
        }
        Stat.PM25 -> StatView(w?.pm25?.roundToInt()?.toString() ?: dash, "µg/m³", "PM2.5", text)
        Stat.UV -> StatView(w?.uv?.roundToInt()?.toString() ?: dash, "", "UV Index", text)
        Stat.FEELS -> StatView(w?.feelsLike?.let { "${it.roundToInt()}°" } ?: dash, "", "Feels like", text)
        Stat.HUMIDITY -> StatView(w?.humidity?.toString() ?: dash, "%", "Humidity", text)
        Stat.WIND -> StatView(w?.wind?.roundToInt()?.toString() ?: dash, if (w?.fahrenheit == true) "mph" else "km/h", "Wind", text)
    }
}

// ——— Now Playing ————————————————————————————————————————————————————————————————

@Composable
private fun NowPlayingBar(track: Track) {
    val context = LocalContext.current
    var position by remember { mutableLongStateOf(track.positionNow()) }
    LaunchedEffect(track) {
        while (true) {
            position = track.positionNow()
            delay(500)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FocusSurface(
            onClick = { NowPlaying.open(context) },
            modifier = Modifier
                .width(420.dp)
                .height(72.dp),
            shape = RoundedCornerShape(20.dp),
            focusedScale = 1.04f,
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(P.fill)
                ) {
                    track.art?.let {
                        Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Txt(track.title, size = 16.sp, weight = FontWeight.SemiBold, color = LocalContentColor.current)
                    Txt(
                        listOf(track.artist, track.appLabel).filter { it.isNotBlank() }.joinToString(" · "),
                        size = 13.sp, color = P.text2,
                    )
                    if (track.durationMs > 0) {
                        Spacer(Modifier.height(7.dp))
                        val frac = (position.toFloat() / track.durationMs).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(0.95f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(P.fill)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(frac)
                                    .fillMaxHeight()
                                    .background(P.text2)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        TransportButton({ NowPlaying.previous() }) { skipGlyph(it, forward = false) }
        Spacer(Modifier.width(10.dp))
        TransportButton({ NowPlaying.playPause() }, big = true) { if (track.playing) pauseGlyph(it) else playGlyph(it) }
        Spacer(Modifier.width(10.dp))
        TransportButton({ NowPlaying.next() }) { skipGlyph(it, forward = true) }
    }
}

@Composable
private fun TransportButton(
    onClick: () -> Unit,
    big: Boolean = false,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier.size(if (big) 56.dp else 46.dp),
        shape = CircleShape,
        container = P.fill.copy(alpha = if (P.isDark) 0.7f else 1f),
        focusedContainer = if (P.isDark) Color(0xFFF5F5F7) else Color(0xFF1D1D1F),
        focusedContentColor = if (P.isDark) Color(0xFF1D1D1F) else Color(0xFFF5F5F7),
        showBorder = false,
        focusedScale = 1.12f,
    ) {
        Glyph(if (big) 22.dp else 18.dp, LocalContentColor.current, Modifier.align(Alignment.Center), draw)
    }
}

// ——— Apps affordance ————————————————————————————————————————————————————————————

@Composable
private fun AppsChip(onApps: () -> Unit) {
    Pill(
        onClick = onApps,
        modifier = Modifier.onPreviewKeyEvent {
            // Pressing down past the last row moves to the Apps page, like scrolling.
            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionDown) { onApps(); true } else false
        },
        label = "Apps",
        icon = { Glyph(18.dp, LocalContentColorOnPill()) { gridGlyph(it) } },
    )
}
