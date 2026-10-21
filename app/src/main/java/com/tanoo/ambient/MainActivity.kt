package com.tanoo.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tanoo.ambient.ui.AmbientTheme
import com.tanoo.ambient.ui.AppPicker
import com.tanoo.ambient.ui.AppsPage
import com.tanoo.ambient.ui.Dashboard
import com.tanoo.ambient.ui.LocationPage
import com.tanoo.ambient.ui.P
import com.tanoo.ambient.ui.SettingsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

enum class Screen(val depth: Int) { Home(0), Apps(1), Settings(1), Picker(2), Location(2) }

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf(Screen.Home)
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var hdmi by mutableStateOf<HdmiSource?>(null)
    private var resumeTick by mutableStateOf(0)

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = maybeOpenHdmiOnPowerOn()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Apps.ensureDefaults(this)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (!bootHandled && SystemClock.elapsedRealtime() < 3 * 60_000) {
            bootHandled = true
            maybeOpenHdmiOnPowerOn()
        }
        setContent { Root() }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOn)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // HOME pressed while we're already in front: go back to the dashboard.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.Home
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        NowPlaying.start(this)
        hdmi = Inputs.preferred(this)
        lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { Apps.loadAll(this@MainActivity) }
        }
    }

    /**
     * On boot and on wake: if the user wants it, jump to HDMI, but only once CEC reports a
     * device on that input. CEC discovery can lag boot by several seconds, so poll briefly.
     */
    private fun maybeOpenHdmiOnPowerOn() {
        if (!AmbientApp.prefs.startOnHdmi) return
        lifecycleScope.launch {
            repeat(15) {
                val src = Inputs.preferred(this@MainActivity)
                if (src != null && src.hasCecDevice && src.connected) {
                    Inputs.switchTo(this@MainActivity, src)
                    return@launch
                }
                delay(1000)
            }
        }
    }

    private fun openHdmi() {
        val src = Inputs.preferred(this) ?: Inputs.list(this).firstOrNull()
        if (src == null || !Inputs.switchTo(this, src)) {
            Toast.makeText(this, "Couldn't open HDMI on this device", Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun Root() {
        val prefs = AmbientApp.prefs
        val now by produceState(LocalDateTime.now()) {
            while (true) {
                value = LocalDateTime.now()
                delay(1000 - System.currentTimeMillis() % 1000)
            }
        }

        var weather by remember { mutableStateOf<Weather?>(null) }
        var weatherError by remember { mutableStateOf(false) }
        LaunchedEffect(prefs.latitude, prefs.longitude, prefs.fahrenheit, resumeTick) {
            if (prefs.latitude.isBlank()) { weather = null; return@LaunchedEffect }
            while (true) {
                val ok = runCatching { WeatherApi.fetch(prefs.latitude, prefs.longitude, prefs.fahrenheit) }
                    .onSuccess { weather = it; weatherError = false }
                    .onFailure {
                        // Cancellation means the effect restarted (e.g. on resume), not a failure.
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        Log.w("Weather", "fetch failed", it)
                        weatherError = weather == null
                    }
                    .isSuccess
                // Wi-Fi is often still coming up after wake/boot, so retry failures quickly.
                delay(if (ok) 15 * 60_000L else 30_000L)
            }
        }

        // Keep HDMI/CEC status fresh while visible (devices power on/off).
        LaunchedEffect(resumeTick) {
            while (true) {
                delay(10_000)
                hdmi = Inputs.preferred(this@MainActivity)
            }
        }

        val dark = when (prefs.theme) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> {
                val w = weather
                if (w?.sunrise != null && w.sunset != null) now.isBefore(w.sunrise) || now.isAfter(w.sunset)
                else now.hour !in 7..18
            }
        }

        AmbientTheme(dark) {
            val top by animateColorAsState(P.bgTop, tween(600), label = "bgTop")
            val bottom by animateColorAsState(P.bgBottom, tween(600), label = "bgBottom")
            val view = LocalView.current
            view.keepScreenOn = prefs.keepAwake && screen == Screen.Home

            BackHandler(enabled = screen != Screen.Home) {
                screen = when (screen) {
                    Screen.Picker -> if (lastDepth1 == Screen.Settings) Screen.Settings else Screen.Apps
                    Screen.Location -> Screen.Settings
                    else -> Screen.Home
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(top, bottom)))
            ) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val down = targetState.depth > initialState.depth ||
                            (initialState == Screen.Home && targetState == Screen.Apps)
                        if (initialState == Screen.Home && targetState == Screen.Apps ||
                            initialState == Screen.Apps && targetState == Screen.Home
                        ) {
                            (slideInVertically(tween(450)) { if (down) it / 3 else -it / 3 } + fadeIn(tween(350)))
                                .togetherWith(slideOutVertically(tween(450)) { if (down) -it / 3 else it / 3 } + fadeOut(tween(250)))
                        } else {
                            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = if (down) 0.96f else 1.04f))
                                .togetherWith(fadeOut(tween(200)))
                        }
                    },
                    label = "screens",
                ) { target ->
                    val focus = remember { FocusRequester() }
                    when (target) {
                        Screen.Home -> Dashboard(
                            now = now,
                            weather = weather,
                            weatherError = weatherError,
                            hdmi = hdmi,
                            onHdmi = ::openHdmi,
                            onSettings = { lastDepth1 = Screen.Settings; screen = Screen.Settings },
                            onApps = { lastDepth1 = Screen.Apps; screen = Screen.Apps },
                            onWeatherClick = {
                                if (prefs.latitude.isBlank()) { lastDepth1 = Screen.Settings; screen = Screen.Location }
                                else resumeTick++
                            },
                            initialFocus = focus,
                        )
                        Screen.Apps -> AppsPage(
                            apps = apps,
                            hdmi = hdmi,
                            onHdmi = ::openHdmi,
                            onLaunch = { Apps.launch(this@MainActivity, it.packageName) },
                            onEdit = { screen = Screen.Picker },
                            onBackHome = { screen = Screen.Home },
                            initialFocus = focus,
                        )
                        Screen.Settings -> SettingsPage(
                            onEditApps = { screen = Screen.Picker },
                            onLocation = { screen = Screen.Location },
                            initialFocus = focus,
                        )
                        Screen.Picker -> AppPicker(
                            apps = apps,
                            onDone = { screen = if (lastDepth1 == Screen.Settings) Screen.Settings else Screen.Apps },
                            initialFocus = focus,
                        )
                        Screen.Location -> LocationPage(onDone = { screen = Screen.Settings }, initialFocus = focus)
                    }
                    LaunchedEffect(Unit) {
                        // Give the first frame a moment to lay out before claiming focus.
                        // Retry: on a cold start the target may not be attached yet.
                        repeat(6) {
                            delay(80)
                            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
                        }
                    }
                }
                ScreenCorners(prefs.corners.fraction)
            }
        }
    }

    private var lastDepth1 = Screen.Apps

    /** Black mask outside a rounded rect, so the projected image reads as a rounded screen. */
    @Composable
    private fun ScreenCorners(fraction: Float) {
        if (fraction <= 0f) return
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * fraction
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(r, r)))
            }
            drawPath(mask, Color.Black)
        }
    }

    companion object {
        private var bootHandled = false
    }
}
