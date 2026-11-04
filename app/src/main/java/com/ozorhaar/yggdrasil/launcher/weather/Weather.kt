package com.ozorhaar.yggdrasil.launcher.weather

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Current weather from Open-Meteo (free, no account), cached for 30 minutes and
 * shared by the runestone clock and the living wallpaper.
 */
object Weather {
    // Cuyahoga Falls, Ohio
    const val LAT = 41.13
    const val LON = -81.48
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

    data class Now(
        val tempF: Int,
        val highF: Int,
        val lowF: Int,
        val code: Int,
        val isDay: Boolean,
        val sunrise: Long,   // epoch ms, today
        val sunset: Long,
        val fetchedAt: Long,
    ) {
        val sky: Sky
            get() = when (code) {
                0 -> Sky.CLEAR
                1, 2 -> Sky.PARTLY
                3 -> Sky.CLOUDY
                45, 48 -> Sky.FOG
                51, 53, 55, 56, 57 -> Sky.DRIZZLE
                61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
                71, 73, 75, 77, 85, 86 -> Sky.SNOW
                95, 96, 99 -> Sky.THUNDER
                else -> Sky.CLOUDY
            }

        val words: String
            get() = when (sky) {
                Sky.CLEAR -> if (isDay) "Clear" else "Clear night"
                Sky.PARTLY -> "Partly cloudy"
                Sky.CLOUDY -> "Overcast"
                Sky.FOG -> "Fog"
                Sky.DRIZZLE -> "Drizzle"
                Sky.RAIN -> "Rain"
                Sky.SNOW -> "Snow"
                Sky.THUNDER -> "Thor rides"
            }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** The last good reading, however old (null before the first fetch). */
    fun cached(ctx: Context): Now? = prefs(ctx).getString("now", null)?.let { runCatching { parse(JSONObject(it)) }.getOrNull() }

    /** Fresh-enough weather; fetches when the cache is older than 30 minutes. Call off the main thread. */
    fun get(ctx: Context, force: Boolean = false): Now? {
        val c = cached(ctx)
        if (!force && c != null && System.currentTimeMillis() - c.fetchedAt < MAX_AGE_MS) return c
        return runCatching { fetch(ctx) }.getOrNull() ?: c
    }

    private fun fetch(ctx: Context): Now {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                "&temperature_unit=fahrenheit&timezone=auto&forecast_days=1&timeformat=unixtime"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val j = JSONObject(body)
        val cur = j.getJSONObject("current")
        val daily = j.getJSONObject("daily")
        val out = JSONObject()
            .put("t", Math.round(cur.getDouble("temperature_2m")).toInt())
            .put("hi", Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt())
            .put("lo", Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt())
            .put("code", cur.getInt("weather_code"))
            .put("day", cur.getInt("is_day") == 1)
            .put("rise", daily.getJSONArray("sunrise").getLong(0) * 1000)
            .put("set", daily.getJSONArray("sunset").getLong(0) * 1000)
            .put("at", System.currentTimeMillis())
        prefs(ctx).edit().putString("now", out.toString()).apply()
        return parse(out)
    }

    private fun parse(o: JSONObject) = Now(
        tempF = o.getInt("t"), highF = o.getInt("hi"), lowF = o.getInt("lo"), code = o.getInt("code"),
        isDay = o.getBoolean("day"), sunrise = o.getLong("rise"), sunset = o.getLong("set"), fetchedAt = o.getLong("at"),
    )
}
