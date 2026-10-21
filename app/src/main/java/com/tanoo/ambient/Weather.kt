package com.tanoo.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class Weather(
    val temp: Double,
    val feelsLike: Double,
    val humidity: Int,
    val wind: Double,
    val sky: Sky,
    val isDay: Boolean,
    val high: Double,
    val low: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val sunriseTomorrow: LocalDateTime?,
    val aqi: Int?,
    val pm25: Double?,
    val uv: Double?,
    val fahrenheit: Boolean,
) {
    val condition: String
        get() = when (sky) {
            Sky.CLEAR -> if (isDay) "Sunny" else "Clear"
            Sky.PARTLY -> "Partly Cloudy"
            Sky.CLOUDY -> "Cloudy"
            Sky.FOG -> "Fog"
            Sky.DRIZZLE -> "Drizzle"
            Sky.RAIN -> "Rain"
            Sky.SNOW -> "Snow"
            Sky.STORM -> "Thunderstorms"
        }

    /** The next sunrise or sunset from [now], whichever comes first. */
    fun nextSunEvent(now: LocalDateTime): Pair<Boolean, LocalDateTime>? = when {
        sunrise != null && now.isBefore(sunrise) -> true to sunrise
        sunset != null && now.isBefore(sunset) -> false to sunset
        sunriseTomorrow != null -> true to sunriseTomorrow
        else -> null
    }
}

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Open-Meteo: free, no API key, covers forecast + air quality + geocoding. */
object WeatherApi {

    suspend fun fetch(lat: String, lon: String, fahrenheit: Boolean): Weather = coroutineScope {
        val units = if (fahrenheit) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else ""
        val forecastJob = async {
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&timezone=auto&forecast_days=2$units"
            )
        }
        val airJob = async {
            runCatching {
                get(
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                        "&current=us_aqi,pm2_5,uv_index&timezone=auto"
                )
            }.getOrNull()
        }
        val f = forecastJob.await()
        val air = airJob.await()?.optJSONObject("current")
        val cur = f.getJSONObject("current")
        val daily = f.getJSONObject("daily")

        fun dailyTime(key: String, i: Int) = daily.optJSONArray(key)?.optString(i)
            ?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

        Weather(
            temp = cur.getDouble("temperature_2m"),
            feelsLike = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            humidity = cur.optInt("relative_humidity_2m"),
            wind = cur.optDouble("wind_speed_10m"),
            sky = skyFor(cur.optInt("weather_code")),
            isDay = cur.optInt("is_day", 1) == 1,
            high = daily.getJSONArray("temperature_2m_max").getDouble(0),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0),
            sunrise = dailyTime("sunrise", 0),
            sunset = dailyTime("sunset", 0),
            sunriseTomorrow = dailyTime("sunrise", 1),
            aqi = air?.takeIf { it.has("us_aqi") && !it.isNull("us_aqi") }?.getInt("us_aqi"),
            pm25 = air?.takeIf { it.has("pm2_5") && !it.isNull("pm2_5") }?.getDouble("pm2_5"),
            uv = air?.takeIf { it.has("uv_index") && !it.isNull("uv_index") }?.getDouble("uv_index"),
            fahrenheit = fahrenheit,
        )
    }

    suspend fun search(query: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=6&language=en&format=json")
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            Place(
                name = r.getString("name"),
                detail = listOfNotNull(r.optString("admin1").ifBlank { null }, r.optString("country").ifBlank { null })
                    .joinToString(", "),
                lat = r.getDouble("latitude"),
                lon = r.getDouble("longitude"),
            )
        }
    }

    private fun skyFor(code: Int) = when (code) {
        0 -> Sky.CLEAR
        1, 2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        in 51..57 -> Sky.DRIZZLE
        in 61..67, in 80..82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        in 95..99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} for $url")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}

enum class AqiLevel(val label: String, val color: Long) {
    GOOD("Good", 0xFF34C759),
    MODERATE("Moderate", 0xFFFFCC00),
    SENSITIVE("Unhealthy for some", 0xFFFF9500),
    UNHEALTHY("Unhealthy", 0xFFFF3B30),
    VERY_UNHEALTHY("Very unhealthy", 0xFFAF52DE),
    HAZARDOUS("Hazardous", 0xFFA2384F);

    companion object {
        fun of(aqi: Int) = when {
            aqi <= 50 -> GOOD
            aqi <= 100 -> MODERATE
            aqi <= 150 -> SENSITIVE
            aqi <= 200 -> UNHEALTHY
            aqi <= 300 -> VERY_UNHEALTHY
            else -> HAZARDOUS
        }
    }
}
