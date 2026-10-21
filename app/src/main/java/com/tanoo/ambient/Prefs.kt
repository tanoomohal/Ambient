package com.tanoo.ambient

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class ThemeMode(val label: String) { AUTO("Automatic"), LIGHT("Light"), DARK("Dark") }

enum class ClockStyle(val label: String) {
    BOTH("Analog + digital"), DIGITAL("Digital"), ANALOG("Analog"), OFF("Off")
}

/** Rounds the projected picture itself; fraction of screen height. */
enum class Corners(val label: String, val fraction: Float) {
    OFF("Square", 0f), SUBTLE("Subtle", 0.03f), PRONOUNCED("Pronounced", 0.06f)
}

/** Small stats shown under the clock. Order here is the display order. */
enum class Stat(val label: String) {
    AQI("Air quality"), PM25("PM2.5"), UV("UV index"), FEELS("Feels like"), HUMIDITY("Humidity"), WIND("Wind")
}

/**
 * Every setting is backed by Compose state so the UI recomposes the moment it changes,
 * and written straight through to SharedPreferences.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("ambient", Context.MODE_PRIVATE)

    var theme by enumPref("theme", ThemeMode.AUTO)
    var clockStyle by enumPref("clock_style", ClockStyle.BOTH)
    var corners by enumPref("corners", Corners.SUBTLE)
    var showWeather by boolPref("show_weather", true)
    var showNowPlaying by boolPref("show_now_playing", true)
    var stats by stringPref("stats", "AQI,PM25,UV")
    var fahrenheit by boolPref("fahrenheit", false)

    var city by stringPref("city", "")
    var latitude by stringPref("lat", "")
    var longitude by stringPref("lon", "")

    /** Wake/boot straight into the HDMI source, but only when a CEC device is detected on it. */
    var startOnHdmi by boolPref("start_on_hdmi", false)
    /** Empty = automatic (the first HDMI input with a CEC device attached). */
    var hdmiInputId by stringPref("hdmi_input", "")

    /** Ordered, comma-separated package names. Empty until the user (or defaults) pick some. */
    var apps by stringPref("apps", "")
    var keepAwake by boolPref("keep_awake", false)

    val statList: List<Stat>
        get() = stats.split(',').mapNotNull { s -> Stat.entries.firstOrNull { it.name == s } }
            .sortedBy { it.ordinal }

    val appList: List<String>
        get() = apps.split(',').filter { it.isNotBlank() }

    fun setAppList(list: List<String>) {
        apps = list.joinToString(",")
    }

    private fun boolPref(key: String, def: Boolean) =
        Stored(sp.getBoolean(key, def)) { sp.edit().putBoolean(key, it).apply() }

    private fun stringPref(key: String, def: String) =
        Stored(sp.getString(key, def) ?: def) { sp.edit().putString(key, it).apply() }

    private inline fun <reified E : Enum<E>> enumPref(key: String, def: E): Stored<E> {
        val stored = sp.getString(key, null)
        val initial = enumValues<E>().firstOrNull { it.name == stored } ?: def
        return Stored(initial) { sp.edit().putString(key, it.name).apply() }
    }
}

class Stored<T>(initial: T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
    private val state = mutableStateOf(initial)
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        save(value)
    }
}
