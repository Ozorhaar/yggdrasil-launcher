package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.ozorhaar.yggdrasil.launcher.R
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The living fjord. The sky (time of day, stars, moon, northern lights, clouds,
 * rain, snow, lightning) is painted live; the mountains and the longship are
 * fixed layers cut from the static fjord wallpaper.
 */
const val DEMO_SECONDS = 80f
const val DEMO_PREF = "fjord_demo_start"

/** The longhouse's shelf on the right-hand hill, in the mountain art's coordinates. */
private const val SHELF_Y = 68
private const val SHELF_FROM = 1040
private const val SHELF_FULL = 1112
/** The longhouse's ends, in the art's coordinates. */
private const val HALL_L = 1118f
private const val HALL_R = 1402f
/** The sail on the longship bitmap (its own pixels). */
private const val SAIL_L = 448; private const val SAIL_T = 40; private const val SAIL_R = 766; private const val SAIL_B = 284
/** Solar output (W) at which the sail burns brightest. */
private const val SOLAR_FULL_W = 7000f

class FjordScene(ctx: Context) {

    companion object {
        /**
         * Where a raven perches on the longhouse's ridge (towards its left end, clear of the smoke
         * hole), in screen pixels for a w x h screen, plus the art's scale. Must match drawSteading.
         */
        fun ridgePerch(w: Int, h: Int, xOffset: Float): Triple<Float, Float, Float> {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val landX = ox - 1440 * s * 0.02f + par
            val landTop = oy + 1327 * s
            val ridge = landTop + 68 * s + 0.5f * s - 100 * s
            val l = landX + HALL_L * 1.04f * s; val r = landX + HALL_R * 1.04f * s
            return Triple(l + 40 * s + (r - l - 80 * s) * 0.18f, ridge, s)
        }
    }

    private val prefs = ctx.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)
    /** When snow was last seen falling (it then lies on the roofs while it stays at freezing). */
    private var snowAt = prefs.getLong("snow_at", 0L)
    /** 0 = the longship in its bay, 1 = gone out of the fjord; -1 until the house is first read. */
    private var shipOut = -1f

    var weather: Weather.Now? = Weather.cached(ctx)

    /** What the house is doing, from Home Assistant (null = unknown: the steading is drawn dark). */
    data class House(
        val lightsOn: Boolean,
        val garageOpen: Boolean,
        val doorUnlocked: Boolean,
        /** Jason or Leah is home: the hearth burns and smoke rises from the roof. */
        val hearth: Boolean = false,
        /** What the solar panels are making, in watts: the longship's sail glows with it. */
        val solarW: Float = 0f,
        /** Power going out to the grid (solar beyond what the house uses), watts; the oars row while it flows. */
        val exportW: Float = 0f,
    ) {
        /** Neither Jason nor Leah is home: the longship is away. */
        val away: Boolean get() = !hearth
    }
    var house: House? = null

    /** Set to start the ~80 s demo that runs through a day and every kind of weather. 0 = off. */
    var demoStart = 0L

    /** True while something moves (northern lights, rain, snow, storm, hearth smoke). */
    var animating = false
        private set

    /** How long until the next frame: ~15 fps for weather and lights, ~8 fps for smoke alone, else a minute. */
    var frameMs = 60_000L
        private set

    private val land: Bitmap = levelHilltop(decode(ctx, R.drawable.fjord_land))
    private val ship: Bitmap = decode(ctx, R.drawable.fjord_ship)
    private val sail: Bitmap? = sailMask(ship)
    private val ray: Bitmap = auroraRay()
    private val puff: Bitmap = cloudPuff()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()

    // fixed random features
    private val rnd = Random(11)
    private val stars = Array(260) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * rnd.nextFloat() * 0.5f, 0.8f + rnd.nextFloat() * 1.8f, 0.3f + rnd.nextFloat() * 0.7f, rnd.nextFloat() * 6.3f) }
    private val glints = Array(80) { floatArrayOf(rnd.nextGaussian(), abs(rnd.nextGaussian()), 0.4f + rnd.nextFloat(), rnd.nextFloat() * 6.3f) }
    private val clouds = Array(14) { floatArrayOf(rnd.nextFloat(), 0.04f + rnd.nextFloat() * 0.42f, 0.55f + rnd.nextFloat() * 0.5f, 0.6f + rnd.nextFloat() * 0.8f) }
    private val drops = Array(240) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.7f + rnd.nextFloat() * 0.6f) }
    private var lastT = 0L
    private var flashAt = 0L
    private var nextFlash = 0L

    // ------------------------------------------------------------------ sky timeline
    private class Pal(val top: Int, val mid: Int, val hor: Int, val dark: Float, val glow: Float)

    private val NIGHT = Pal(0xFF04060D.toInt(), 0xFF0B1430.toInt(), 0xFF1A2342.toInt(), 1f, 0f)
    private val DAWN = Pal(0xFF1A2342.toInt(), 0xFF5B3A6A.toInt(), 0xFFE8946A.toInt(), 0.55f, 1f)
    private val DAY = Pal(0xFF2F6FB3.toInt(), 0xFF6AA6D8.toInt(), 0xFFBFE0F0.toInt(), 0f, 0f)
    private val GOLDEN = Pal(0xFF2D5A9A.toInt(), 0xFF8AA8C8.toInt(), 0xFFF0C070.toInt(), 0.05f, 0.6f)
    private val DUSK = Pal(0xFF141A3A.toInt(), 0xFF5B3A4A.toInt(), 0xFFE0703A.toInt(), 0.45f, 1f)

    private fun sunTimes(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        fun today(ms: Long): Long {
            val c = Calendar.getInstance().apply { timeInMillis = ms }
            c.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            return c.timeInMillis
        }
        val w = weather
        if (w != null) return today(w.sunrise) to today(w.sunset)
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.SECOND, 0) }
        c.set(Calendar.HOUR_OF_DAY, 7); c.set(Calendar.MINUTE, 15); val rise = c.timeInMillis
        c.set(Calendar.HOUR_OF_DAY, 17); c.set(Calendar.MINUTE, 45); val set = c.timeInMillis
        return rise to set
    }

    private fun skyAt(now: Long): Pal {
        val (rise, set) = sunTimes(now)
        val m = 60_000L
        val frames = listOf(
            rise - 70 * m to NIGHT, rise - 15 * m to DAWN, rise + 50 * m to DAY,
            set - 90 * m to DAY, set - 25 * m to GOLDEN, set + 5 * m to DUSK, set + 55 * m to NIGHT,
        )
        if (now <= frames.first().first || now >= frames.last().first) return NIGHT
        for (i in 0 until frames.size - 1) {
            val (t0, a) = frames[i]
            val (t1, b) = frames[i + 1]
            if (now in t0..t1) {
                val f = ((now - t0).toFloat() / (t1 - t0)).coerceIn(0f, 1f)
                val s = f * f * (3 - 2 * f)
                return Pal(mix(a.top, b.top, s), mix(a.mid, b.mid, s), mix(a.hor, b.hor, s), lerp(a.dark, b.dark, s), lerp(a.glow, b.glow, s))
            }
        }
        return NIGHT
    }

    // ------------------------------------------------------------------ drawing
    /** Demo script: (seconds from start, what to show). Returns fake clock time, weather code, aurora strength. */
    private fun demoState(realNow: Long): Triple<Long, Int, Float>? {
        if (demoStart == 0L) return null
        val sec = (realNow - demoStart) / 1000f
        if (sec < 0 || sec > DEMO_SECONDS) { demoStart = 0L; return null }
        val saved = weather
        weather = null                                   // use the fixed 7:15 / 17:45 day for the demo
        val (rise, set) = sunTimes(realNow)
        weather = saved
        val m = 60_000L
        fun at(t: Long, code: Int, aurora: Float = 0f) = Triple(t, code, aurora)
        return when {
            sec < 30 -> at(rise - 80 * m + ((set + 70 * m - (rise - 80 * m)) * (sec / 30f)).toLong(), 0)   // a whole clear day
            sec < 44 -> at(set + 180 * m, 0, 1f)          // a clear night: full northern lights
            sec < 54 -> at(rise + 240 * m, 63)            // rain
            sec < 64 -> at(set + 20 * m, 95)              // storm at dusk
            sec < 74 -> at(rise + 200 * m, 73)            // snow
            else -> at(rise + 10 * m, 45)                 // foggy dawn
        }
    }

    fun draw(c: Canvas, w: Int, h: Int, xOffset: Float, uptime: Long) {
        val realNow = System.currentTimeMillis()
        val demo = demoState(realNow)
        val now = demo?.first ?: realNow
        val dt = if (lastT == 0L) 0f else ((uptime - lastT).coerceIn(0, 100)) / 1000f
        lastT = uptime
        val t = uptime.toFloat()

        val wx = if (demo != null) Weather.Now(40, 45, 35, demo.second, true, 0, 0, 0) else weather
        val skyKind = wx?.sky ?: Weather.Sky.CLEAR
        val cloud = when (skyKind) {
            Weather.Sky.CLEAR -> 0f; Weather.Sky.PARTLY -> 0.3f; Weather.Sky.CLOUDY -> 0.75f
            Weather.Sky.FOG -> 0.6f; Weather.Sky.DRIZZLE -> 0.7f; Weather.Sky.RAIN -> 0.8f
            Weather.Sky.SNOW -> 0.75f; Weather.Sky.THUNDER -> 0.9f
        }
        val p = skyAt(now)
        // the demo also shows the steading at its liveliest: hearth lit, sail full of sun by day
        val realHouse = house
        if (demo != null) house = (realHouse ?: House(false, false, false)).copy(
            lightsOn = true, hearth = true, solarW = if (p.dark < 0.4f) SOLAR_FULL_W else 0f,
        )
        if (demo == null && skyKind == Weather.Sky.SNOW && realNow - snowAt > 10 * 60_000L) {
            snowAt = realNow; prefs.edit().putLong("snow_at", snowAt).apply()
        }
        // snow lies on the steading while it falls, and for a day and a half after while it stays at freezing
        val snowCover = skyKind == Weather.Sky.SNOW ||
            (demo == null && realNow - snowAt < 36 * 3600_000L && (wx?.tempF ?: 99) <= 33)
        val light = 1f - p.dark
        val grey = mix(0xFF2A2E36.toInt(), 0xFF8A919C.toInt(), light)
        val top = mix(p.top, grey, cloud * 0.8f)
        val mid = mix(p.mid, grey, cloud * 0.85f)
        val hor = mix(p.hor, grey, cloud * 0.75f)

        // layout: the art is 1440 x 3120, centre-cropped to the screen
        val s = max(w / 1440f, h / 3120f)
        val ox = (w - 1440 * s) / 2
        val oy = (h - 3120 * s) / 2
        val hz = oy + 3120 * s * 0.62f
        val par = (xOffset - 0.5f) * -0.03f * w
        val skyPar = par * 0.4f

        // sky
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true
        paint.shader = LinearGradient(0f, 0f, 0f, hz, intArrayOf(top, mid, hor), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), hz + 2, paint)
        paint.shader = null

        // stars
        val starA = p.dark * (1 - cloud * 1.2f).coerceAtLeast(0f)
        if (starA > 0.02f) {
            paint.color = Color.WHITE
            for (st in stars) {
                val tw = 0.7f + 0.3f * sin(t * 0.0021f * (0.5f + st[3]) + st[4])
                paint.alpha = (255 * starA * st[3] * tw).toInt().coerceIn(0, 255)
                c.drawCircle(st[0] * w + skyPar, st[1] * hz * 1.4f, st[2] * s * 1.6f, paint)
            }
        }

        // moon / sun
        val moonAge = Moon.age(now)
        val moonLit = Moon.lit(moonAge)
        val moonA = (p.dark - 0.5f).coerceAtLeast(0f) * 2 * (1 - cloud) * (0.25f + 0.75f * moonLit)
        if (moonA > 0.02f) {
            // tonight's real phase: a glow that grows with it, the dark face faintly, the lit part bright
            val mx = w * 0.78f + skyPar; val my = h * 0.13f; val r = 34 * s * 1.6f
            paint.shader = RadialGradient(mx, my, r * 5, intArrayOf(argb(0.35f * moonA * moonLit, 0xFFE8F0FF.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(mx, my, r * 5, paint); paint.shader = null
            paint.color = argb(0.85f * moonA, mix(top, 0xFF2A3348.toInt(), 0.5f)); c.drawCircle(mx, my, r, paint)
            paint.color = argb(0.92f * moonA, 0xFFEDEFF5.toInt())
            c.drawPath(Moon.litPath(mx, my, r, moonAge), paint)
        }
        // midwinter: on the solstice the sun keeps low in the fjord's mouth all day
        val solstice = isSolstice(now) && light > 0.3f
        val sunA = if (solstice) light * (1 - cloud) * 0.7f else light * (1 - p.glow) * (1 - cloud) * 0.6f
        if (sunA > 0.02f) {
            val sx = if (solstice) ox + 1440 * s * 0.62f + skyPar else w * 0.72f + skyPar
            val sy = if (solstice) oy + 3120 * s * 0.62f - h * 0.035f else h * 0.15f
            paint.shader = RadialGradient(sx, sy, w * 0.35f, intArrayOf(argb(sunA, 0xFFFFF4D6.toInt()), argb(sunA * 0.25f, 0xFFFFE9A8.toInt()), 0), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, w * 0.35f, paint); paint.shader = null
        }

        // northern lights
        val night = floor((now - 12 * 3600_000L) / 86_400_000.0)
        val auroraNight = if (demo != null) demo.third else 0.35f + 0.65f * frac(sin(night * 12.9898) * 43758.5453).toFloat()
        val auroraA = auroraNight * ((p.dark - 0.65f) / 0.35f).coerceIn(0f, 1f) * (1 - cloud * 2.2f).coerceIn(0f, 1f)
        if (auroraA > 0.03f) drawAurora(c, w, h, t, auroraA, skyPar)

        // clouds
        if (cloud > 0.15f) {
            val tint = mix(0xFF30343C.toInt(), 0xFFDDE2EA.toInt(), light * 0.85f + p.glow * 0.1f)
            paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
            val n = (4 + cloud * 10).toInt().coerceAtMost(clouds.size)
            for (i in 0 until n) {
                val cl = clouds[i]
                val span = w * 1.6f
                val x = ((cl[0] * span + t * 0.006f * cl[3] * s) % span) - w * 0.3f
                val cw = w * cl[2]; val ch = cw * 0.38f
                rect.set(x - cw / 2, cl[1] * hz - ch / 2, x + cw / 2, cl[1] * hz + ch / 2)
                paint.alpha = (255 * (0.18f + 0.35f * cloud)).toInt()
                c.drawBitmap(puff, null, rect, paint)
            }
            paint.colorFilter = null
            paint.color = argb(cloud * 0.3f, tint)
            c.drawRect(0f, 0f, w.toFloat(), hz, paint)
        }

        // the glow in the fjord's mouth at dawn and dusk
        val valleyX = ox + 1440 * s * 0.62f + skyPar
        val glowA = (if (solstice) max(p.glow, 0.75f) else p.glow) * (1 - cloud * 0.7f)
        if (glowA > 0.02f) {
            paint.shader = RadialGradient(valleyX, hz, w * 0.55f, intArrayOf(argb(0.85f * glowA, 0xFFECD08F.toInt()), argb(0.4f * glowA, 0xFFE0703A.toInt()), 0), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, hz - w * 0.55f, w.toFloat(), hz, paint); paint.shader = null
        }

        // lightning
        if (skyKind == Weather.Sky.THUNDER) {
            if (nextFlash == 0L || uptime > nextFlash) { flashAt = uptime; nextFlash = uptime + 9000 + Random.nextLong(25000) }
            val age = uptime - flashAt
            if (age < 600) {
                val f = (1 - age / 600f) * (if (age in 120..200) 0.3f else 1f)
                paint.color = argb(0.55f * f, 0xFFE8F0FF.toInt())
                c.drawRect(0f, 0f, w.toFloat(), hz, paint)
            }
        }

        // fog band over the water line
        if (skyKind == Weather.Sky.FOG) {
            paint.shader = LinearGradient(0f, hz - h * 0.2f, 0f, hz + h * 0.08f, intArrayOf(0, argb(0.55f, grey), 0), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, hz - h * 0.2f, w.toFloat(), hz + h * 0.08f, paint); paint.shader = null
        }

        // water
        val deep = 0xFF060912.toInt()
        paint.shader = LinearGradient(0f, hz, 0f, h.toFloat(), intArrayOf(mix(hor, deep, 0.45f), mix(mid, deep, 0.7f), deep), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, hz, w.toFloat(), h.toFloat(), paint); paint.shader = null

        // land tint follows the light (warm at dawn/dusk, dim at night)
        val l = lerp(1.3f, 0.5f, p.dark)
        val cm = ColorMatrix().apply { setScale(l * (1 + 0.15f * p.glow), l, l * (1 - 0.1f * p.glow), 1f) }
        val landFilter = ColorMatrixColorFilter(cm)
        val landW = 1440 * s * 1.04f
        val landX = ox - 1440 * s * 0.02f + par
        val landTop = oy + 1327 * s
        val landBot = oy + 1935 * s

        // reflection of the mountains
        c.save()
        c.scale(1f, -1f, 0f, hz)
        paint.colorFilter = landFilter; paint.alpha = 80
        rect.set(landX, landTop, landX + landW, landBot)
        c.drawBitmap(land, null, rect, paint)
        c.restore()

        // glints on the water under the sun, glow or moon
        val glintA = max(p.glow, max(light * 0.35f, moonA * 0.6f)) * (1 - cloud * 0.85f)
        if (glintA > 0.03f) {
            val gx = if (moonA > p.glow && moonA > light) w * 0.78f + skyPar else valleyX
            val gc = if (moonA > p.glow && moonA > light) 0xFFDDE6F5.toInt() else 0xFFECD08F.toInt()
            paint.colorFilter = null
            for (g in glints) {
                val tw = 0.5f + 0.5f * sin(t * 0.004f * g[2] + g[3])
                paint.color = argb(glintA * tw * 0.6f, gc)
                val x = gx + g[0] * w * 0.08f
                val y = hz + g[1] * h * 0.07f + 4 * s
                val len = (24 + 80 * g[2]) * s
                c.drawRect(x, y, x + len, y + 3 * s, paint)
            }
        }

        // mountains
        paint.colorFilter = landFilter; paint.alpha = 255
        rect.set(landX, landTop, landX + landW, landBot)
        c.drawBitmap(land, null, rect, paint)

        // the steading on the right-hand hill: a longhouse that follows the house (see drawSteading)
        drawSteading(c, landX, landTop, s, light, p.dark, snowCover, t)

        // longship, its reflection and the ripples. It sails out of the fjord when no one's home
        // and back in when someone is, and rows while the house sends power to the grid.
        val h0 = house
        val outTarget = if (h0?.away == true) 1f else 0f
        if (shipOut < 0f) shipOut = if (h0 == null) 0f else outTarget
        if (shipOut != outTarget) {
            val step = dt / 16f                                   // ~16 s to cross the fjord
            shipOut = if (shipOut < outTarget) min(outTarget, shipOut + step) else max(outTarget, shipOut - step)
        }
        val sailing = shipOut != outTarget
        val rowing = sailing || (h0?.exportW ?: 0f) > 150f
        if (shipOut < 0.995f) {
            val sl = lerp(1.15f, 0.65f, p.dark)
            val shipL = ox + 13 * s * 1.04f - 1440 * s * 0.02f + par; val shipT = oy + 1598 * s
            // the hull's waterline glides to the fjord's mouth, shrinking with the distance
            val pivX = shipL + 540 * 1.04f * s; val pivY = shipT + 352 * s
            val u = shipOut * shipOut * (3 - 2 * shipOut)
            val k = 1f - 0.9f * u
            val fade = 1f - u * u * u
            c.save()
            c.translate(lerp(pivX, valleyX, u), lerp(pivY, hz, u))
            c.scale(k, k)
            c.translate(-pivX, -pivY)
            val sun = sqrt(((h0?.solarW ?: 0f) / SOLAR_FULL_W).coerceIn(0f, 1f))
            val sailRect = RectF(shipL + SAIL_L * 1.04f * s, shipT + SAIL_T * s, shipL + SAIL_R * 1.04f * s, shipT + SAIL_B * s)
            if (sun > 0.02f) {
                // the panels' power, caught in the sail: a warm halo behind it...
                val r = sailRect.height() * 1.1f
                paint.shader = RadialGradient(sailRect.centerX(), sailRect.centerY(), r, intArrayOf(argb(0.32f * sun * fade, 0xFFECD08F.toInt()), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(sailRect.centerX(), sailRect.centerY(), r, paint); paint.shader = null
            }
            if (rowing) drawOars(c, shipL, shipT, s, t, fade, p.dark)
            paint.isFilterBitmap = true
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(sl, sl, sl, 1f) })
            paint.alpha = (255 * fade).toInt()
            rect.set(shipL, shipT, ox + (13 + 1427) * s * 1.04f - 1440 * s * 0.02f + par, oy + (1598 + 1519) * s)
            c.drawBitmap(ship, null, rect, paint)
            paint.colorFilter = null; paint.alpha = 255
            val sm = sail
            if (sun > 0.02f && sm != null) {
                // ...and gold light in the cloth itself
                paint.colorFilter = PorterDuffColorFilter(0xFFECD08F.toInt(), PorterDuff.Mode.SRC_IN)
                paint.blendMode = BlendMode.PLUS
                paint.alpha = (150 * sun * fade).toInt()
                c.drawBitmap(sm, null, sailRect, paint)
                paint.blendMode = null; paint.colorFilter = null; paint.alpha = 255
            }
            c.restore()
        }

        // rain / snow
        val precip = when (skyKind) {
            Weather.Sky.DRIZZLE -> 90; Weather.Sky.RAIN -> 200; Weather.Sky.THUNDER -> 240; Weather.Sky.SNOW -> 170; else -> 0
        }
        if (precip > 0) drawPrecip(c, w, h, dt, t, precip, skyKind == Weather.Sky.SNOW, light)

        animating = demo != null || auroraA > 0.03f || precip > 0 || skyKind == Weather.Sky.THUNDER
        val smoke = house?.hearth == true
        frameMs = when {
            animating || sailing -> 66L
            smoke || rowing -> 125L
            else -> 60_000L
        }
        animating = animating || smoke || sailing || rowing
        house = realHouse
    }


    /**
     * The steading on the right-hand hill, filling the gap under the top rows of the home screen:
     * the hilltop is levelled into a shelf and a longhouse spans it. Its windows light while the
     * house lights are on, its door glows ember while the side door is unlocked, and a lantern on
     * a post at its end burns while the garage is open. Positions are in the mountain art's own
     * coordinates, so it moves with the hills.
     */
    private fun drawSteading(c: Canvas, landX: Float, landTop: Float, s: Float, light: Float, dark: Float, snow: Boolean, t: Float) {
        val h = house
        val k = 1.04f * s
        fun x(a: Float) = landX + a * k
        fun y(a: Float) = landTop + a * s
        val path = android.graphics.Path()
        paint.reset(); paint.isAntiAlias = true

        val base = y(68f) + 0.5f * s
        val wall = mix(0xFF141820.toInt(), 0xFF5A5658.toInt(), light)
        val roof = mix(0xFF0B0E15.toInt(), 0xFF3A3638.toInt(), light)

        // the longhouse: low walls under a deep, curved turf roof, crossed gable boards at both ends
        val l = x(HALL_L); val r = x(HALL_R); val wallTop = base - 40 * s; val ridge = base - 100 * s
        paint.color = wall; c.drawRect(l, wallTop, r, base, paint)
        path.reset()
        path.moveTo(l - 16 * s, wallTop + 4 * s)
        path.quadTo(l + 10 * s, ridge + 18 * s, l + 40 * s, ridge)
        path.lineTo(r - 40 * s, ridge)
        path.quadTo(r - 10 * s, ridge + 18 * s, r + 16 * s, wallTop + 4 * s)
        path.close()
        paint.color = roof; c.drawPath(path, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f * s; paint.strokeCap = Paint.Cap.ROUND
        for (gx in listOf(l + 40 * s, r - 40 * s)) {
            c.drawLine(gx - 13 * s, ridge + 12 * s, gx + 10 * s, ridge - 15 * s, paint)
            c.drawLine(gx + 13 * s, ridge + 12 * s, gx - 10 * s, ridge - 15 * s, paint)
        }
        // carved beam along the eaves
        paint.strokeWidth = 2.5f * s
        c.drawLine(l - 6 * s, wallTop + 2 * s, r + 6 * s, wallTop + 2 * s, paint)
        paint.style = Paint.Style.FILL

        val glow = 0.35f + 0.65f * dark
        val mid = (l + r) / 2
        // windows either side of the door, lit while the house lights are on
        if (h?.lightsOn == true) {
            val warm = 0xFFF0C070.toInt()
            val wy = base - 21 * s
            for (wx in listOf(l + 34 * s, l + 82 * s, mid - 46 * s, mid + 46 * s, r - 82 * s, r - 34 * s)) {
                paint.shader = RadialGradient(wx, wy, 22 * s, intArrayOf(argb(0.6f * glow, warm), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(wx, wy, 22 * s, paint); paint.shader = null
                paint.color = argb(0.95f * glow, warm)
                c.drawRect(wx - 5 * s, wy - 6 * s, wx + 5 * s, wy + 6 * s, paint)
            }
        }
        // the side door, in the middle: dark when barred, ember when unlocked
        paint.color = if (h?.doorUnlocked == true) argb(0.9f * glow + 0.1f, 0xFFE0703A.toInt()) else mix(roof, 0xFF000000.toInt(), 0.4f)
        c.drawRect(mid - 9 * s, base - 30 * s, mid + 9 * s, base, paint)
        // the garage lantern, on a post at the hall's left end
        val px = l - 22 * s
        paint.color = roof
        c.drawRect(px - 2 * s, base - 46 * s, px + 2 * s, base, paint)
        if (h?.garageOpen == true) {
            val ly = base - 48 * s
            paint.shader = RadialGradient(px, ly, 34 * s, intArrayOf(argb(0.75f * glow + 0.2f, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(px, ly, 34 * s, paint); paint.shader = null
            paint.color = 0xFFECD08F.toInt(); c.drawCircle(px, ly, 4.5f * s, paint)
        } else {
            paint.color = roof; c.drawCircle(px, base - 48 * s, 4f * s, paint)
        }

        // Yule: an evergreen swag under the eaves hung with lanterns, and a wreath on the door
        if (isYule(System.currentTimeMillis())) {
            val green = mix(0xFF16261B.toInt(), 0xFF2F5A3A.toInt(), light * 0.8f + 0.2f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f * s; paint.strokeCap = Paint.Cap.ROUND
            paint.color = green
            val swags = 6
            val ey = wallTop + 4 * s
            for (i in 0 until swags) {
                val x0 = l - 6 * s + (r - l + 12 * s) * i / swags
                val x1 = l - 6 * s + (r - l + 12 * s) * (i + 1) / swags
                path.reset(); path.moveTo(x0, ey); path.quadTo((x0 + x1) / 2, ey + 16 * s, x1, ey)
                c.drawPath(path, paint)
            }
            paint.strokeWidth = 3.5f * s
            c.drawCircle(mid, base - 38 * s, 7 * s, paint)               // the wreath
            paint.style = Paint.Style.FILL
            paint.color = argb(0.9f, 0xFFB0402A.toInt()); c.drawCircle(mid, base - 31.5f * s, 2.2f * s, paint)
            // lanterns where the swags meet, twinkling a little, brighter at night
            for (i in 0..swags) {
                val lx = l - 6 * s + (r - l + 12 * s) * i / swags
                val tw = 0.75f + 0.25f * sin(t * 0.003f + i * 1.7f)
                val col = if (i % 2 == 0) 0xFFECD08F.toInt() else 0xFFE0703A.toInt()
                val a = (0.45f + 0.55f * dark) * tw
                paint.shader = RadialGradient(lx, ey + 6 * s, 14 * s, intArrayOf(argb(0.6f * a, col), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(lx, ey + 6 * s, 14 * s, paint); paint.shader = null
                paint.color = argb(0.95f * a + 0.05f, col)
                c.drawRect(lx - 3 * s, ey + 2 * s, lx + 3 * s, ey + 10 * s, paint)
            }
        }

        // snow on the turf roof and the shelf it stands on
        if (snow) {
            val white = mix(0xFF8A93A6.toInt(), 0xFFEEF2F8.toInt(), light * 0.8f + 0.2f)
            paint.color = white
            path.reset()
            path.moveTo(l - 16 * s, wallTop + 4 * s)
            path.quadTo(l + 10 * s, ridge + 18 * s, l + 40 * s, ridge)
            path.lineTo(r - 40 * s, ridge)
            path.quadTo(r - 10 * s, ridge + 18 * s, r + 16 * s, wallTop + 4 * s)
            path.lineTo(r + 10 * s, wallTop + 2 * s)
            path.quadTo(r - 14 * s, ridge + 26 * s, r - 42 * s, ridge + 9 * s)
            path.lineTo(l + 42 * s, ridge + 9 * s)
            path.quadTo(l + 14 * s, ridge + 26 * s, l - 10 * s, wallTop + 2 * s)
            path.close()
            c.drawPath(path, paint)
            c.drawRect(x(SHELF_FROM + 30f), base - 3 * s, landX + 1440 * k, base + 3 * s, paint)
        }

        // the hearth: while Jason or Leah is home, embers glow in the smoke hole and smoke rises
        if (h?.hearth == true) {
            val hx = mid + 40 * s; val hy = ridge + 3 * s
            val flick = 0.8f + 0.2f * sin(t * 0.009f) * sin(t * 0.0047f + 1.3f)
            paint.shader = RadialGradient(hx, hy, 18 * s, intArrayOf(argb((0.35f + 0.5f * dark) * flick, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(hx, hy, 18 * s, paint); paint.shader = null
            paint.color = argb(0.9f * flick, 0xFFECD08F.toInt()); c.drawRect(hx - 6 * s, hy - 1.5f * s, hx + 6 * s, hy + 1.5f * s, paint)
            // by day grey smoke; by night pale, starlit smoke, warmed by the fire where it leaves the roof
            val pale = mix(0xFF7C8496.toInt(), 0xFFB4B9C2.toInt(), light)
            val strength = 0.42f + 0.28f * dark
            val life = 7000f; val n = 16
            for (i in 0 until n) {
                val age = (((t + i * life / n) % life) / life)
                val rise = age * 230 * s
                val drift = -age * age * 110 * s + sin(age * 7f + i) * 7 * s
                val rad = (10 + 34 * age) * s
                val warm = ((1 - age * 3).coerceAtLeast(0f)) * (0.3f + 0.5f * dark)
                paint.colorFilter = PorterDuffColorFilter(mix(pale, 0xFFE0703A.toInt(), warm), PorterDuff.Mode.SRC_IN)
                paint.alpha = (255 * strength * (1 - age) * (age * 6).coerceAtMost(1f)).toInt()
                rect.set(hx + drift - rad, hy - rise - rad * 0.6f, hx + drift + rad, hy - rise + rad * 0.6f)
                c.drawBitmap(puff, null, rect, paint)
            }
            paint.colorFilter = null; paint.alpha = 255
        }
    }

    private fun drawAurora(c: Canvas, w: Int, h: Int, t: Float, a: Float, par: Float) {
        val n = 90
        val colW = w / n * 1.7f
        for (b in 0 until 3) {
            val base = h * (0.17f + b * 0.055f)
            val amp = h * 0.03f
            val tall = h * (0.11f + 0.035f * b)
            for (i in 0 until n) {
                val u = i / (n - 1f)
                val x = u * w * 1.1f - w * 0.05f + par * (1 + b * 0.3f)
                val y = base + amp * sin(u * 2 * PI.toFloat() * 1.3f + t * 0.00022f * (1 + b * 0.3f) + b) +
                    amp * 0.5f * sin(u * 2 * PI.toFloat() * 3.1f - t * 0.00037f)
                val shimmer = 0.5f + 0.5f * sin(i * 0.7f + t * 0.0011f * (b + 1)) * sin(i * 0.23f - t * 0.0007f)
                paint.alpha = (255 * a * (0.12f + 0.32f * shimmer) * (1f - b * 0.22f)).toInt().coerceIn(0, 255)
                rect.set(x - colW / 2, y - tall, x + colW / 2, y + h * 0.012f)
                c.drawBitmap(ray, null, rect, paint)
            }
        }
        paint.alpha = 255
    }

    private fun drawPrecip(c: Canvas, w: Int, h: Int, dt: Float, t: Float, count: Int, snow: Boolean, light: Float) {
        val n = min(count, drops.size)
        if (snow) {
            paint.color = argb(0.75f + 0.2f * light, Color.WHITE)
            for (i in 0 until n) {
                val d = drops[i]
                d[1] += dt * 0.06f * d[2]
                d[0] += dt * 0.01f * sin(t * 0.001f + i)
                if (d[1] > 1f) { d[1] -= 1f; d[0] = Random.nextFloat() }
                c.drawCircle(((d[0] % 1f) + 1f) % 1f * w, d[1] * h, (1.5f + 2.5f * d[2]) * w / 1440f * 1.6f, paint)
            }
        } else {
            paint.color = argb(0.28f + 0.15f * light, 0xFF8FC4E0.toInt())
            paint.strokeWidth = 2.2f * w / 1440f * 1.4f
            val len = h * 0.028f
            for (i in 0 until n) {
                val d = drops[i]
                d[1] += dt * 1.15f * d[2]
                d[0] -= dt * 0.12f * d[2]
                if (d[1] > 1f) { d[1] -= 1f; d[0] = Random.nextFloat() * 1.2f }
                val x = (((d[0] % 1f) + 1f) % 1f) * w
                val y = d[1] * h
                c.drawLine(x, y, x + len * 0.18f * d[2], y - len * d[2], paint)
            }
        }
    }

    /**
     * Oars out of the ports along the hull, sweeping aft through the water in a steady stroke and
     * lifting back; drawn behind the hull so they come out from under the shields.
     */
    private fun drawOars(c: Canvas, shipL: Float, shipT: Float, s: Float, t: Float, fade: Float, dark: Float) {
        val k = 1.04f * s
        paint.reset(); paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        val wood = argb(fade, mix(0xFF5A4630.toInt(), 0xFF1C150F.toInt(), dark))
        val phase = t * 0.0026f                                    // about one stroke every 2.4 s
        for (i in 0 until 9) {
            val px = shipL + (250 + i * 55) * k; val py = shipT + 350 * s
            val a = (PI / 2 + 0.42 * sin(phase.toDouble())).toFloat()   // swing fore and aft
            val len = (118 + 16 * kotlin.math.cos(phase.toDouble()).toFloat()) * s  // dips in, lifts out
            val ex = px + kotlin.math.cos(a.toDouble()).toFloat() * len
            val ey = py + sin(a.toDouble()).toFloat() * len
            paint.color = wood; paint.strokeWidth = 3.2f * s
            c.drawLine(px, py, ex, ey, paint)
            paint.strokeWidth = 7f * s
            val bx = px + kotlin.math.cos(a.toDouble()).toFloat() * len * 0.82f
            val by = py + sin(a.toDouble()).toFloat() * len * 0.82f
            c.drawLine(bx, by, ex, ey, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** 21 December, the midwinter solstice (as it falls in North America's eastern time). */
    private fun isSolstice(now: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.get(Calendar.MONTH) == Calendar.DECEMBER && cal.get(Calendar.DAY_OF_MONTH) == 21
    }

    /** Yule: all of December and the first days of January, until the twelfth night (6 January). */
    private fun isYule(now: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val m = cal.get(Calendar.MONTH); val d = cal.get(Calendar.DAY_OF_MONTH)
        return m == Calendar.DECEMBER || (m == Calendar.JANUARY && d <= 6)
    }

    fun release() {
        land.recycle(); ship.recycle(); ray.recycle(); puff.recycle(); sail?.recycle()
    }

    /** Just the sail's cloth from the longship (the rigging and hull left out), for lighting it. */
    private fun sailMask(src: Bitmap): Bitmap? = runCatching {
        val w = SAIL_R - SAIL_L; val h = SAIL_B - SAIL_T
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, SAIL_L, SAIL_T, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            val bright = maxOf(Color.red(c), Color.green(c), Color.blue(c))
            px[i] = if (a > 100 && bright > 60) (0xFF shl 24) or (c and 0xFFFFFF) else 0
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    // ------------------------------------------------------------------ helpers
    /**
     * Levels the right-hand hilltop into a shelf for the longhouse, in the mountain art itself so it
     * stays one mountain: peaks above the shelf are cut away, and where the hill is lower the
     * ground is raised with that column's own surface colour (keeping the lit and shaded faces),
     * easing in from the natural ridge on the left.
     */
    private fun levelHilltop(src: Bitmap): Bitmap {
        val b = runCatching { src.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull() ?: return src
        src.recycle()
        val h = b.height
        val col = IntArray(h)
        val from = SHELF_FROM; val full = SHELF_FULL
        for (x in from until b.width) {
            b.getPixels(col, 0, 1, x, 0, 1, h)
            var top = 0
            while (top < h && (col[top] ushr 24) < 128) top++
            if (top >= h) continue
            val target = if (x >= full) SHELF_Y.toFloat() else {
                val f = (x - from) / (full - from).toFloat()
                val e = f * f * (3 - 2 * f)
                top + (SHELF_Y - top) * e
            }.toFloat()
            val t = target.toInt().coerceIn(0, h - 1)
            if (top < t) {
                for (y in 0 until t) col[y] = 0
            } else if (top > t) {
                val surface = col[(top + 8).coerceAtMost(h - 1)]
                for (y in t until top) col[y] = surface
            }
            b.setPixels(col, 0, 1, x, 0, 1, h)
        }
        return b
    }

    private fun decode(ctx: Context, res: Int): Bitmap =
        BitmapFactory.decodeResource(ctx.resources, res, BitmapFactory.Options().apply { inScaled = false })

    /** One vertical aurora ray: transparent violet at the top, green at the foot. */
    private fun auroraRay(): Bitmap {
        val hgt = 256
        val bm = Bitmap.createBitmap(4, hgt, Bitmap.Config.ARGB_8888)
        for (y in 0 until hgt) {
            val u = y / (hgt - 1f)
            val col = if (u < 0.6f) mix(0xFF9A7FE0.toInt(), 0xFF8FC4E0.toInt(), u / 0.6f) else mix(0xFF8FC4E0.toInt(), 0xFF3FE0A0.toInt(), (u - 0.6f) / 0.4f)
            val alpha = when {
                u < 0.85f -> u / 0.85f
                else -> 1f - (u - 0.85f) / 0.15f * 0.7f
            }
            for (x in 0 until 4) bm.setPixel(x, y, argb(alpha * alpha, col))
        }
        return bm
    }

    /** A soft white cloud puff, tinted when drawn. */
    private fun cloudPuff(): Bitmap {
        val bm = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Random(5)
        repeat(9) {
            val x = 40f + r.nextFloat() * 176f
            val y = 54f + r.nextFloat() * 30f
            val rad = 30f + r.nextFloat() * 34f
            pp.shader = RadialGradient(x, y, rad, intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            cv.drawCircle(x, y, rad, pp)
        }
        return bm
    }

    private fun Random.nextGaussian(): Float {
        var s: Double; var v1: Double; var v2: Double
        do { v1 = 2 * nextDouble() - 1; v2 = 2 * nextDouble() - 1; s = v1 * v1 + v2 * v2 } while (s >= 1 || s == 0.0)
        return (v1 * Math.sqrt(-2 * Math.log(s) / s)).toFloat()
    }

    private fun frac(x: Double) = x - floor(x)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun mix(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
    )
}

private fun argb(alpha: Float, rgb: Int): Int =
    Color.argb((alpha.coerceIn(0f, 1f) * 255).toInt(), Color.red(rgb), Color.green(rgb), Color.blue(rgb))
