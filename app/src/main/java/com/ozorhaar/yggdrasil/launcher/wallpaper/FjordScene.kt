package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
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
import kotlin.random.Random

/**
 * The living fjord. The sky (time of day, stars, moon, northern lights, clouds,
 * rain, snow, lightning) is painted live; the mountains and the longship are
 * fixed layers cut from the static fjord wallpaper.
 */
const val DEMO_SECONDS = 80f
const val DEMO_PREF = "fjord_demo_start"

class FjordScene(ctx: Context) {

    var weather: Weather.Now? = Weather.cached(ctx)

    /** What the house is doing, from Home Assistant (null = unknown: the steading is drawn dark). */
    data class House(val lightsOn: Boolean, val garageOpen: Boolean, val doorUnlocked: Boolean)
    var house: House? = null

    /** Set to start the ~80 s demo that runs through a day and every kind of weather. 0 = off. */
    var demoStart = 0L

    /** True while something moves (northern lights, rain, snow, storm): the engine then draws ~15 fps. */
    var animating = false
        private set

    private val land: Bitmap = decode(ctx, R.drawable.fjord_land)
    private val ship: Bitmap = decode(ctx, R.drawable.fjord_ship)
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
        val moonA = (p.dark - 0.5f).coerceAtLeast(0f) * 2 * (1 - cloud)
        if (moonA > 0.02f) {
            val mx = w * 0.78f + skyPar; val my = h * 0.13f; val r = 34 * s * 1.6f
            paint.shader = RadialGradient(mx, my, r * 5, intArrayOf(argb(0.35f * moonA, 0xFFE8F0FF.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(mx, my, r * 5, paint); paint.shader = null
            paint.color = argb(0.9f * moonA, 0xFFEDEFF5.toInt()); c.drawCircle(mx, my, r, paint)
            paint.color = argb(0.85f * moonA, top); c.drawCircle(mx + r * 0.45f, my - r * 0.15f, r * 0.92f, paint) // crescent
        }
        val sunA = light * (1 - p.glow) * (1 - cloud) * 0.6f
        if (sunA > 0.02f) {
            val sx = w * 0.72f + skyPar; val sy = h * 0.15f
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
        val glowA = p.glow * (1 - cloud * 0.7f)
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

        // the steading on the far shore: a longhouse (windows lit while the house lights are on,
        // its door glowing ember while the side door is unlocked) and a boathouse with a lantern
        // that burns while the garage is open
        drawSteading(c, ox, oy, s, par, hz, light, p.dark)

        // longship, its reflection and the ripples
        val sl = lerp(1.15f, 0.65f, p.dark)
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(sl, sl, sl, 1f) })
        rect.set(ox + 13 * s * 1.04f - 1440 * s * 0.02f + par, oy + 1598 * s, ox + (13 + 1427) * s * 1.04f - 1440 * s * 0.02f + par, oy + (1598 + 1519) * s)
        c.drawBitmap(ship, null, rect, paint)
        paint.colorFilter = null

        // rain / snow
        val precip = when (skyKind) {
            Weather.Sky.DRIZZLE -> 90; Weather.Sky.RAIN -> 200; Weather.Sky.THUNDER -> 240; Weather.Sky.SNOW -> 170; else -> 0
        }
        if (precip > 0) drawPrecip(c, w, h, dt, t, precip, skyKind == Weather.Sky.SNOW, light)

        animating = demo != null || auroraA > 0.03f || precip > 0 || skyKind == Weather.Sky.THUNDER
    }

    private fun drawSteading(c: Canvas, ox: Float, oy: Float, s: Float, par: Float, hz: Float, light: Float, dark: Float) {
        val h = house
        fun ax(x: Float) = ox + x * s * 1.04f - 1440 * s * 0.02f + par
        val base = hz + 1 * s
        val wall = mix(0xFF141820.toInt(), 0xFF4B4A4C.toInt(), light)
        val roof = mix(0xFF0E1118.toInt(), 0xFF3A3A3E.toInt(), light)
        paint.reset(); paint.isAntiAlias = true
        val path = android.graphics.Path()

        // longhouse: long low walls, a deep turf roof and crossed gable boards at each end
        val l = ax(1170f); val r = ax(1330f); val wallTop = base - 22 * s; val ridge = base - 52 * s
        paint.color = wall; c.drawRect(l, wallTop, r, base, paint)
        path.reset(); path.moveTo(l - 10 * s, wallTop + 2 * s); path.lineTo(l + 24 * s, ridge); path.lineTo(r - 24 * s, ridge)
        path.lineTo(r + 10 * s, wallTop + 2 * s); path.close()
        paint.color = roof; c.drawPath(path, paint)
        paint.strokeWidth = 3.2f * s; paint.strokeCap = Paint.Cap.ROUND; paint.style = Paint.Style.STROKE
        for (x in listOf(l + 24 * s, r - 24 * s)) {
            c.drawLine(x - 10 * s, ridge + 9 * s, x + 7 * s, ridge - 10 * s, paint)
            c.drawLine(x + 10 * s, ridge + 9 * s, x - 7 * s, ridge - 10 * s, paint)
        }
        paint.style = Paint.Style.FILL

        // boathouse (naust) down by the water, a little in front
        val bl = ax(1352f); val br = ax(1420f); val bTop = base - 18 * s; val bRidge = base - 40 * s
        path.reset(); path.moveTo(bl, base + 2 * s); path.lineTo(bl, bTop); path.lineTo((bl + br) / 2, bRidge); path.lineTo(br, bTop); path.lineTo(br, base + 2 * s); path.close()
        paint.color = roof; c.drawPath(path, paint)

        // the lights
        val night = 0.35f + 0.65f * dark   // lights read stronger after dark
        if (h?.lightsOn == true) {
            val warm = 0xFFF0C070.toInt()
            val wy = base - 13 * s
            for (i in 0 until 4) {
                val wx = l + (24 + i * 34) * s
                paint.shader = RadialGradient(wx, wy, 16 * s, intArrayOf(argb(0.55f * night, warm), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(wx, wy, 16 * s, paint); paint.shader = null
                paint.color = argb(0.95f * night, warm)
                c.drawRect(wx - 3.5f * s, wy - 4 * s, wx + 3.5f * s, wy + 4 * s, paint)
            }
        }
        // the side door, mid-wall: dark when barred, ember when unlocked
        val dx = (l + r) / 2
        paint.color = if (h?.doorUnlocked == true) argb(0.9f * night + 0.1f, 0xFFE0703A.toInt()) else mix(roof, 0xFF000000.toInt(), 0.4f)
        c.drawRect(dx - 5 * s, base - 16 * s, dx + 5 * s, base, paint)
        // the garage lantern at the boathouse
        if (h?.garageOpen == true) {
            val lx = br + 6 * s; val ly = base - 14 * s
            paint.shader = RadialGradient(lx, ly, 26 * s, intArrayOf(argb(0.7f * night + 0.2f, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(lx, ly, 26 * s, paint); paint.shader = null
            paint.color = argb(1f, 0xFFECD08F.toInt()); c.drawCircle(lx, ly, 3.2f * s, paint)
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

    fun release() {
        land.recycle(); ship.recycle(); ray.recycle(); puff.recycle()
    }

    // ------------------------------------------------------------------ helpers
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
