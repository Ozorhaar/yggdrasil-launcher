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
    // Where to ask about: the house location from Home Assistant (cached on the phone only),
    // else the middle of town. Sent to Open-Meteo rounded to ~1 km.
    private const val TOWN_LAT = 41.13   // Cuyahoga Falls, Ohio
    private const val TOWN_LON = -81.48
    private const val LOC_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L
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

    /** House location from Home Assistant, refreshed weekly; falls back to the town. Off the main thread. */
    private fun place(ctx: Context): Pair<Double, Double> {
        val p = ctx.getSharedPreferences("ygg_weather_place", Context.MODE_PRIVATE)
        val at = p.getLong("at", 0L)
        if (System.currentTimeMillis() - at > LOC_MAX_AGE_MS) {
            com.ozorhaar.yggdrasil.launcher.search.HomeLink(ctx).homeLocation()?.let { (la, lo) ->
                p.edit().putFloat("lat", la.toFloat()).putFloat("lon", lo.toFloat()).putLong("at", System.currentTimeMillis()).apply()
            }
        }
        val lat = if (p.contains("lat")) p.getFloat("lat", 0f).toDouble() else TOWN_LAT
        val lon = if (p.contains("lon")) p.getFloat("lon", 0f).toDouble() else TOWN_LON
        fun r2(v: Double) = Math.round(v * 100) / 100.0
        return r2(lat) to r2(lon)
    }

    private fun fetch(ctx: Context): Now {
        val (lat, lon) = place(ctx)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
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

    /** A warning from the National Weather Service for the house's spot. */
    data class Alert(val event: String, val headline: String, val severity: String, val description: String, val instruction: String)

    private var alertsAt = 0L
    private var alertsCache: List<Alert> = emptyList()

    /**
     * Active National Weather Service alerts (wind, winter storm, ice, flood...) for the house's
     * spot, from api.weather.gov (free, no account), checked at most every 10 minutes. Only the
     * rounded location is sent. Call off the main thread; empty when there are none or it's unreachable.
     */
    fun alerts(ctx: Context): List<Alert> {
        if (System.currentTimeMillis() - alertsAt < 10 * 60_000L) return alertsCache
        val (lat, lon) = place(ctx)
        val fresh = runCatching {
            val conn = URL("https://api.weather.gov/alerts/active?point=$lat,$lon").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "YggdrasilLauncher/1.0 (personal use)")
            conn.setRequestProperty("Accept", "application/geo+json")
            val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
            val feats = JSONObject(body).optJSONArray("features") ?: org.json.JSONArray()
            (0 until feats.length()).mapNotNull { feats.getJSONObject(it).optJSONObject("properties") }
                .filter { it.optString("status") == "Actual" && it.optString("messageType") != "Cancel" }
                .map {
                    Alert(
                        it.optString("event"), it.optString("headline"), it.optString("severity"),
                        it.optString("description").trim(), it.optString("instruction").trim(),
                    )
                }
                .distinctBy { it.event + it.headline }
        }.getOrNull() ?: return alertsCache
        alertsCache = fresh
        alertsAt = System.currentTimeMillis()
        return fresh
    }

    /**
     * Tonight's geomagnetic activity: the highest planetary Kp NOAA's Space Weather Prediction
     * Center forecasts from three hours ago to twelve hours ahead (free, no account, nothing about
     * the phone is sent). Cached for 30 minutes; the last value, however old, if unreachable.
     * From Ohio, Kp 5 can show low in the north, 6 to 7 is a real chance, 8 or more overhead.
     */
    fun kp(ctx: Context): Float {
        val p = prefs(ctx)
        val at = p.getLong("kp_at", 0L)
        if (System.currentTimeMillis() - at < 30 * 60_000L) return p.getFloat("kp", 2f)
        val fresh = runCatching {
            val conn = URL("https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 8000; conn.instanceFollowRedirects = false
            val body = try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
            val arr = org.json.JSONArray(body)
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val now = System.currentTimeMillis()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val t = runCatching { fmt.parse(o.optString("time_tag"))?.time }.getOrNull() ?: return@mapNotNull null
                if (t in (now - 3 * 3600_000L)..(now + 12 * 3600_000L)) o.optDouble("kp", Double.NaN).takeIf { !it.isNaN() } else null
            }.maxOrNull()?.toFloat()
        }.getOrNull()
        if (fresh != null) p.edit().putFloat("kp", fresh).putLong("kp_at", System.currentTimeMillis()).apply()
        return fresh ?: p.getFloat("kp", 2f)
    }

    /** The last Kp read, without fetching (for drawing). */
    fun kpCached(ctx: Context): Float = prefs(ctx).getFloat("kp", 2f)

    private fun parse(o: JSONObject) = Now(
        tempF = o.getInt("t"), highF = o.getInt("hi"), lowF = o.getInt("lo"), code = o.getInt("code"),
        isDay = o.getBoolean("day"), sunrise = o.getLong("rise"), sunset = o.getLong("set"), fetchedAt = o.getLong("at"),
    )
}
