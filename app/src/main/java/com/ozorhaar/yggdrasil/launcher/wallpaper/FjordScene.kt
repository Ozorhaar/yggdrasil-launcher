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
import com.ozorhaar.yggdrasil.launcher.data.SteadingConfig.Touch
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
/** The launcher writes the time someone came home here: the hall's windows flare for a while. */
const val FLARE_PREF = "fjord_flare_at"

/** The longhouse's shelf on the right-hand hill, in the mountain art's coordinates. */
private const val SHELF_Y = 68
private const val SHELF_FROM = 1015
private const val SHELF_FULL = 1084
/** The longhouse's ends, in the art's coordinates. */
private const val HALL_L = 1118f
private const val HALL_R = 1402f
/**
 * The longship layer: yggdrasil-os tools/art.py fjord_layers(1440, 3120, afloat=True, live=True),
 * the same ship as Windows and Yggdrasil OS (Geri at the bow, Freki at the stern), cropped to rows
 * [SHIP_TOP, SHIP_TOP + SHIP_WL) of the 1440 x 3120 art. The hull sits in the water at the bitmap's
 * foot (the horizon line); the scene draws its reflection and oars.
 */
private const val SHIP_TOP = 1560
private const val SHIP_WL = 375
/** The oar ports along the hull (bitmap row, first x and spacing), and how far in front of the hull the blades meet the water. */
private const val OAR_PORT = 356; private const val OAR_X0 = 335.5f; private const val OAR_DX = 62.9f
private const val OAR_REACH = 42
/** The sail's mesh for the wind. */
private const val SAIL_COLS = 10; private const val SAIL_ROWS = 8
/** The sail on the longship bitmap (its own pixels). */
private const val SAIL_L = 389; private const val SAIL_T = 34; private const val SAIL_R = 763; private const val SAIL_B = 312
/** Solar output (W) at which the sail burns brightest. */
private const val SOLAR_FULL_W = 7000f

class FjordScene(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private fun cfg(t: Touch) =
        com.ozorhaar.yggdrasil.launcher.data.SteadingConfig.on(appCtx, t)
    // which living touches are on (read from the steading settings each frame)
    private var showHearth = true; private var showFire = true; private var showForge = true
    private var showLeak = true; private var showYule = true; private var showPlaces = true
    private val props = Props()
    /** Tonight's moonlight on the scene, 0..1 (0 by day). */
    private var moonLight = 0f
    /** Tonight's hoarfrost, 0..1. */
    private var frostK = 0f
    /** The name-day feast is on (1 January, or a preview). */
    private var feast = false

    companion object {
        /** Wakes the wallpaper to draw now (set by its engine). */
        @Volatile var wake: (() -> Unit)? = null

        /**
         * The places whose folders have something waiting (Place names, and CAR, SMART_HOME); the
         * home screen sets it, and each stirs in its own way until they're read.
         */
        @Volatile var stirring: Set<String> = emptySet()
            set(v) { if (v != field) { field = v; wake?.invoke() } }
        /** On call (from the watch widget): Dagaz is cut into the smithy's door, glowing frost. */
        @Volatile var onCall = false
            set(v) { if (v != field) { field = v; wake?.invoke() } }
        /** A Claude alert waits: Mímir's Well glows. Camera alerts wait: the beacon burns. */
        @Volatile var mimir = false
            set(v) { if (v != field) { field = v; wake?.invoke() } }
        @Volatile var beaconLit = false
            set(v) { if (v != field) { field = v; wake?.invoke() } }
        /** Mímir's Well on the ridge where the World Tree stands, and the beacon cairn on the far-left ridge, in land art
         *  coordinates, drawn at 1.3; their tap boxes are around them in screen art pixels. */
        const val WELL_X = 610f; const val WELL_Y = 1497f   // the whole ring on the flat of the ridge, set a little into it
        const val BEACON_X = 120f; const val BEACON_Y = 1470f   // the lowest ground under the cairn, so no edge hangs
        private fun landRect(w: Int, h: Int, xOffset: Float, ax: Float, ay: Float, l: Float, t: Float, r: Float, b: Float): android.graphics.RectF {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val landX = ox - 1440 * s * 0.02f + (xOffset - 0.5f) * -0.03f * w
            val x = landX + ax * 1.04f * s; val y = oy + ay * s
            return android.graphics.RectF(x + l * s, y + t * s, x + r * s, y + b * s)
        }
        fun wellRect(w: Int, h: Int, xOffset: Float) = landRect(w, h, xOffset, WELL_X, WELL_Y, -40f, -82f, 56f, 20f)
        fun beaconRect(w: Int, h: Int, xOffset: Float) = landRect(w, h, xOffset, BEACON_X, BEACON_Y, -62f, -175f, 62f, 18f)
        /** Where the moon hangs: below the Saga card, above and left of the longhouse. */
        const val MOON_X = 0.74f; const val MOON_Y = 0.31f

        /** The fjord has iced over in a long cold spell: the knarr is drawn up on the shore. */
        @Volatile var beached = false

        /** Whether the longship is in its bay (not sailed out of the fjord); the scene writes it each frame. */
        @Volatile var shipHome = true

        /**
         * The scene's places, each opening a folder: its anchor in the mountain art's coordinates
         * (where the prop stands) and its tap box around the anchor. The World Tree stands in the sky,
         * so its box is in the screen's own art coordinates.
         */
        enum class Place(val ax: Float, val ay: Float, val box: FloatArray, val sky: Boolean = false, val tree: Boolean = false) {
            NETWORK(0f, 0f, floatArrayOf(40f, 1000f, 330f, 1330f), sky = true),
            PERSONAL(300f, 1466f, floatArrayOf(-34f, -108f, 34f, 8f)),
            FINANCE(150f, 1600f, floatArrayOf(-74f, -96f, 76f, 8f)),
            VIDEO(440f, 1466f, floatArrayOf(-70f, -100f, 96f, 22f)),     // the skald on the ridge, drawn at 1.4: easy to find and tap
            WORK(1312f, 1640f, floatArrayOf(-205f, -134f, 118f, 25f)),   // the smithy at 1.4: forge side opens Bambu Handy, the rest Work
            FOOD(1300f, 1934f, floatArrayOf(-40f, -100f, 40f, 8f)),
            SHOP(1190f, 2120f, floatArrayOf(-106f, -133f, 108f, 12f)),   // the knarr at 0.85: its mast clears the cookfire on the shore
            MAIL(0f, 0f, floatArrayOf(), tree = true),                     // Ratatoskr, on the World Tree's middle limb
            GAMES(690f, 2395f, floatArrayOf(-612f, -92f, 500f, 30f)),     // Jörmungandr's coils in the water above the dock
        }

        /** Ratatoskr's branch: the long limb reaching right over the fjord, as points along it (fractions of the
         *  World Tree's drawn box, x then y), from where it leaves the trunk to its tip. Measured on the phone. */
        private val BRANCH = floatArrayOf(0.61983f, 0.58808f, 0.70530f, 0.56314f, 0.81085f, 0.53603f)

        /** A point along the branch (u = 0 at the trunk, 1 at the tip) on screen: x, y, its angle, and its half-thickness. */
        fun limbAt(w: Int, h: Int, xOffset: Float, u: Float): FloatArray {
            val s = max(w / 1440f, h / 3120f)
            val oy = (h - 3120 * s) / 2
            val skyPar = (xOffset - 0.5f) * -0.03f * w * 0.4f
            val th = h * 0.5f; val tw = th * 0.9f
            val l = w * 0.34f + skyPar * 0.7f - tw / 2; val tp = oy + 3120 * s * 0.62f - th
            val n = BRANCH.size / 2
            val px = FloatArray(n) { l + BRANCH[it * 2] * tw }; val py = FloatArray(n) { tp + BRANCH[it * 2 + 1] * th }
            val seg = FloatArray(n - 1) { kotlin.math.hypot(px[it + 1] - px[it], py[it + 1] - py[it]) }
            var d = u.coerceIn(0f, 1f) * seg.sum(); var i = 0
            while (i < n - 2 && d > seg[i]) { d -= seg[i]; i++ }
            val f = (d / seg[i]).coerceIn(0f, 1f)
            return floatArrayOf(px[i] + (px[i + 1] - px[i]) * f, py[i] + (py[i + 1] - py[i]) * f,
                kotlin.math.atan2(py[i + 1] - py[i], px[i + 1] - px[i]), (14f - 5f * u) * s)
        }

        /** The smithy's forge side (the anvil and the glowing door): it opens Bambu Handy. Must match Props.smithy at 1.4. */
        fun anvilRect(w: Int, h: Int, xOffset: Float): android.graphics.RectF {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val landX = ox - 1440 * s * 0.02f + (xOffset - 0.5f) * -0.03f * w
            val x = landX + Place.WORK.ax * 1.04f * s; val y = oy + Place.WORK.ay * s
            // the smithy's left half: the anvil and the glowing forge door (drawn at 1.4)
            return android.graphics.RectF(x - 205f * s, y - 134f * s, x + 8f * s, y + 25f * s)
        }

        /** A place's tap box in screen pixels. Must match drawPlaces. */
        fun placeRect(w: Int, h: Int, xOffset: Float, pl: Place): android.graphics.RectF {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val landX = ox - 1440 * s * 0.02f + par
            if (pl.tree) {
                val s0 = max(w / 1440f, h / 3120f)
                val a = limbAt(w, h, xOffset, 0f); val b2 = limbAt(w, h, xOffset, 1f)
                return android.graphics.RectF(min(a[0], b2[0]) - 30f * s0, min(a[1], b2[1]) - 70f * s0, max(a[0], b2[0]) + 30f * s0, max(a[1], b2[1]) + 30f * s0)
            }
            val b = if (pl == Place.SHOP && beached) floatArrayOf(-80f, -110f, 85f, 12f) else pl.box
            val ax = if (pl == Place.SHOP && beached) 1150f else pl.ax
            val ay = if (pl == Place.SHOP && beached) 1938f else pl.ay
            return if (pl.sky) android.graphics.RectF(ox + b[0] * s, oy + b[1] * s, ox + b[2] * s, oy + b[3] * s)
            else {
                val x = landX + ax * 1.04f * s; val y = oy + ay * s
                android.graphics.RectF(x + b[0] * s, y + b[1] * s, x + b[2] * s, y + b[3] * s)
            }
        }

        /** The longship's parts that answer a tap: Freki at the stern, Geri at the bow, and the hull. */
        enum class ShipPart { FREKI, GERI, HULL }

        /** Which part of the longship (in its bay) is at screen point x, y, if any. Must match the ship's drawing. */
        fun shipPart(w: Int, h: Int, xOffset: Float, x: Float, y: Float): ShipPart? {
            if (!shipHome) return null
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val shipL = ox - 1440 * s * 0.02f + par; val shipT = oy + SHIP_TOP * s
            fun r(l: Float, t: Float, rr: Float, b: Float) =
                android.graphics.RectF(shipL + l * 1.04f * s, shipT + t * s, shipL + rr * 1.04f * s, shipT + b * s)
            return when {
                r(58f, 92f, 216f, 234f).contains(x, y) -> ShipPart.FREKI
                r(944f, 92f, 1094f, 234f).contains(x, y) -> ShipPart.GERI
                r(228f, 314f, 932f, 378f).contains(x, y) -> ShipPart.HULL
                else -> null
            }
        }

        /** A part of the longship as a box in screen pixels (for marking it). */
        fun shipPartRect(w: Int, h: Int, xOffset: Float, part: ShipPart): android.graphics.RectF {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val shipL = ox - 1440 * s * 0.02f + par; val shipT = oy + SHIP_TOP * s
            val b = when (part) {
                ShipPart.FREKI -> floatArrayOf(58f, 92f, 216f, 234f)
                ShipPart.GERI -> floatArrayOf(944f, 92f, 1094f, 234f)
                ShipPart.HULL -> floatArrayOf(228f, 314f, 932f, 378f)
            }
            return android.graphics.RectF(shipL + b[0] * 1.04f * s, shipT + b[1] * s, shipL + b[2] * 1.04f * s, shipT + b[3] * s)
        }

        /** The sail (and a little room for the wind's belly) as a tap target. Must match the ship's drawing. */
        fun sailArea(w: Int, h: Int, xOffset: Float): android.graphics.RectF {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val shipL = ox - 1440 * s * 0.02f + par; val shipT = oy + SHIP_TOP * s
            return android.graphics.RectF(shipL + (SAIL_L - 16) * 1.04f * s, shipT + 6 * s,
                shipL + (SAIL_R + 16) * 1.04f * s, shipT + (SAIL_B + 10) * s)
        }

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

        /**
         * The whole steading as one tap target: from the lantern post to the screen's edge, from a
         * little sky above the ridge down to the hall's foot. Must match drawSteading.
         */
        fun steadingArea(w: Int, h: Int, xOffset: Float): android.graphics.RectF {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val landX = ox - 1440 * s * 0.02f + par
            val base = oy + 1327 * s + 68 * s + 0.5f * s
            val l = landX + HALL_L * 1.04f * s
            return android.graphics.RectF(l - 44 * s, base - 100 * s - 35 * s, w.toFloat(), base + 3 * s)
        }

        /** The ground just right of the longhouse's door, where a delivered chest sits. Must match drawSteading. */
        fun doorStep(w: Int, h: Int, xOffset: Float): Triple<Float, Float, Float> {
            val s = max(w / 1440f, h / 3120f)
            val ox = (w - 1440 * s) / 2; val oy = (h - 3120 * s) / 2
            val par = (xOffset - 0.5f) * -0.03f * w
            val landX = ox - 1440 * s * 0.02f + par
            val base = oy + 1327 * s + 68 * s + 0.5f * s
            val mid = landX + (HALL_L + HALL_R) / 2 * 1.04f * s
            return Triple(mid + 30 * s, base, s)
        }
    }

    private val prefs = ctx.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)
    /** When snow was last seen falling (it then lies on the roofs while it stays at freezing). */
    private var snowAt = prefs.getLong("snow_at", 0L)
    /** Since when it's stayed at 20 °F or below (0 = not cold): after two days the fjord's edges ice over. */
    private var coldSince = prefs.getLong("cold_since", 0L)
    /** When rain last fell: for a while after, with the sun out, a rainbow (Bifröst) stands over the fjord. */
    private var rainAt = prefs.getLong("rain_at", 0L)
    /** 0 = the longship in its bay, 1 = gone out of the fjord; -1 until the house is first read. */
    private var shipOut = -1f

    var weather: Weather.Now? = Weather.cached(ctx)

    /** Tonight's planetary Kp from NOAA: the northern lights follow it (none on quiet nights). */
    var kp: Float = Weather.kpCached(ctx)

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
        /** A leak sensor is wet: water rises around the longhouse and the hall glows ember. */
        val leak: Boolean = false,
        /** Surtur the smoker is lit: a cooking fire burns on the hillside. */
        val fire: Boolean = false,
        /** The Bambu printer is working: sparks fly from the forge chimney. */
        val forge: Boolean = false,
        /** The print's progress (0..100) while the printer works, else -1: the smithy's forge brightens with it. */
        val printPct: Int = -1,
        /** Who's on the move (driving): FREKI for the first of the household, GERI for the second; their eyes glow. */
        val movers: Set<String> = emptySet(),
    ) {
        /** Neither Jason nor Leah is home: the longship is away. */
        val away: Boolean get() = !hearth
    }
    var house: House? = null

    /** When someone last came home (the windows flare for 20 s after). */
    var flareAt = 0L

    /** Set to start the ~80 s demo that runs through a day and every kind of weather. 0 = off. */
    var demoStart = 0L

    /** True while something moves (northern lights, rain, snow, storm, hearth smoke). */
    var animating = false
        private set

    /** How long until the next frame: ~15 fps for weather and lights, ~8 fps for smoke alone, else a minute. */
    var frameMs = 60_000L
        private set

    private val land: Bitmap = levelHilltop(decode(ctx, R.drawable.fjord_land))
    /** The top of the ground in each column of the mountain art (after levelling). */
    private val groundTops: IntArray = IntArray(land.width).also { tops ->
        val col = IntArray(land.height)
        for (x in 0 until land.width) {
            land.getPixels(col, 0, 1, x, 0, 1, land.height)
            var y = 0
            while (y < land.height && (col[y] ushr 24) < 128) y++
            tops[x] = y
        }
    }
    /** The longship cut at its waterline (the art's own baked reflection sat too high), and its true mirror image. */
    private val ship: Bitmap
    private val sail: Bitmap?
    private val shipRefl: Bitmap
    init {
        val art = decode(ctx, R.drawable.fjord_ship)
        sail = sailMask(art)
        shipRefl = mirrorShip(art)
        // the cloth is lifted out of the ship and drawn on its own, so the wind can shape it
        ship = cutSail(art, sail)
    }
    private val ray: Bitmap = auroraRay()
    private val treeLazy = lazy { worldTree() }
    private val tree: Bitmap by treeLazy
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
        showHearth = cfg(Touch.HEARTH); showFire = cfg(Touch.FIRE); showForge = cfg(Touch.FORGE); showPlaces = cfg(Touch.PLACES)
        showLeak = cfg(Touch.LEAK); showYule = cfg(Touch.YULE)
        // Jason's name-day: the hall keeps a feast all day, fireworks after midnight
        feast = Birthday.today(appCtx, realNow)
        val fireworks = Birthday.fireworks(appCtx, realNow)
        if (feast) house = (house ?: House(false, false, false)).copy(lightsOn = true, hearth = true)
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
        // the seasons: a long cold spell ices the fjord's edges (and the knarr is drawn up on the shore);
        // after rain, with the sun out, Bifröst stands over the fjord for a while
        val tempF = wx?.tempF
        if (demo == null && tempF != null) {
            if (tempF <= 20 && coldSince == 0L) { coldSince = realNow; prefs.edit().putLong("cold_since", realNow).apply() }
            if (tempF >= 28 && coldSince != 0L) { coldSince = 0L; prefs.edit().putLong("cold_since", 0L).apply() }
        }
        val iced = coldSince != 0L && realNow - coldSince > 48 * 3600_000L
        beached = iced && cfg(Touch.PLACES)
        if (demo == null && (skyKind == Weather.Sky.RAIN || skyKind == Weather.Sky.DRIZZLE || skyKind == Weather.Sky.THUNDER) && realNow - rainAt > 60_000L) {
            rainAt = realNow; prefs.edit().putLong("rain_at", realNow).apply()
        }
        val sinceRain = realNow - rainAt
        val bowA = if ((skyKind == Weather.Sky.CLEAR || skyKind == Weather.Sky.PARTLY) && sinceRain in 0..45 * 60_000L)
            (1f - sinceRain / (45f * 60_000f)) * (1f - p.dark * 2f).coerceIn(0f, 1f) else 0f
        props.snow = snowCover
        // hoarfrost: at or below freezing with no snow lying, more the colder it is; it goes as the day warms
        frostK = if (!snowCover && demo == null && tempF != null && tempF <= 32) ((33f - tempF) / 6f).coerceIn(0.15f, 1f) else 0f
        props.frost = frostK
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
        val nightK = ((p.dark - 0.5f) * 2).coerceIn(0f, 1f)
        val moonA = nightK * (1 - cloud) * (0.25f + 0.75f * moonLit)
        // the moonlight on the scene: the phase, thinned by cloud, never below a little starlight
        moonLight = nightK * max(0.22f, moonLit * (1 - 0.65f * cloud))
        val mx = w * MOON_X + skyPar; val my = h * MOON_Y; val r = 34 * s * 1.6f
        // behind cloud: no disc, only the moon's light diffused through it
        val veiled = nightK * cloud * moonLit
        if (veiled > 0.02f) {
            paint.shader = RadialGradient(mx, my, r * 9, intArrayOf(argb(0.28f * veiled, 0xFFDCE4F2.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(mx, my, r * 9, paint); paint.shader = null
        }
        if (moonA > 0.02f) {
            // tonight's real phase: a glow that grows with it, the dark face faintly, the lit part bright
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
        // real space weather: nothing below Kp 4, faint at 4, a fair chance from Ohio at 6, full from 8
        val auroraNight = when {
            demo != null -> demo.third
            !cfg(Touch.REAL_AURORA) ->
                0.35f + 0.65f * frac(sin(night * 12.9898) * 43758.5453).toFloat()   // some, every clear night
            kp < 4f -> 0f
            else -> min(1f, 0.15f + (kp - 4f) * 0.25f)
        }
        val auroraA = auroraNight * ((p.dark - 0.65f) / 0.35f).coerceIn(0f, 1f) * (1 - cloud * 2.2f).coerceIn(0f, 1f)
        if (auroraA > 0.03f) drawAurora(c, w, h, t, auroraA, skyPar)

        // Yggdrasil itself: on clear nights the World Tree's bare crown rises behind the mountains,
        // a darker shape against the stars, its branches edged green and frost while the lights dance
        // (while the folders live in the scene it stands faintly at every hour: it opens Network)
        val treeA = (((p.dark - 0.6f) / 0.4f).coerceIn(0f, 1f) * (1 - cloud * 2f).coerceIn(0f, 1f))
            .let { if (showPlaces) max(it, 0.22f) else it }
        if (treeA > 0.03f && cfg(Touch.WORLD_TREE)) {
            val tb = tree
            val th = h * 0.5f; val tw = th * tb.width / tb.height
            val cx = w * 0.34f + skyPar * 0.7f
            rect.set(cx - tw / 2, hz - th, cx + tw / 2, hz + 2)
            paint.colorFilter = PorterDuffColorFilter(mix(top, 0xFF000000.toInt(), 0.55f), PorterDuff.Mode.SRC_IN)
            paint.alpha = (235 * treeA).toInt()
            c.drawBitmap(tb, null, rect, paint)
            if (frostK > 0.05f) {
                paint.colorFilter = PorterDuffColorFilter(0xFFDCE8F4.toInt(), PorterDuff.Mode.SRC_IN)
                paint.alpha = (110 * frostK * treeA.coerceAtLeast(0.4f) * (0.6f + 0.4f * max(moonLight, light))).toInt()
                rect.offset(0f, -1.5f * s); c.drawBitmap(tb, null, rect, paint); rect.offset(0f, 1.5f * s)
                paint.colorFilter = null; paint.alpha = 255
            }
            if (auroraA > 0.03f) {
                paint.colorFilter = PorterDuffColorFilter(0xFF3FE0A0.toInt(), PorterDuff.Mode.SRC_IN)
                paint.blendMode = BlendMode.PLUS
                paint.alpha = (70 * auroraA * treeA).toInt()
                rect.offset(0f, -2f * s)
                c.drawBitmap(tb, null, rect, paint)
                paint.blendMode = null
            }
            paint.colorFilter = null; paint.alpha = 255
            if (showPlaces) drawRatatoskr(c, w, h, xOffset, t, light, treeA)
            // something waits in Network: frost glints drift along the crown's branch tips
            if (showPlaces && Place.NETWORK.name in stirring) {
                props.t = t
                for (i in 0 until 16) {
                    val fx = ((i * 0.618034f) % 1f); val fy = ((i * 0.381966f + 0.13f) % 1f)
                    val gx = rect.left + rect.width() * (0.12f + 0.76f * fx) + sin(t * 0.0006f + i) * 6 * s
                    val gy = rect.top + rect.height() * (0.06f + 0.42f * fy)
                    val a = max(0f, sin(t * 0.0026f + i * 1.9f))
                    if (a > 0.05f) props.glint(c, gx, gy, (3f + 5f * a) * s, 0.85f * a)
                }
            }
        }

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

        // Bifröst: on the name-day, the rainbow bridge arcs across the daytime sky (its feet behind the hills)
        if (feast && light > 0.3f) {
            val bands = intArrayOf(0xFFB0402A.toInt(), 0xFFE0703A.toInt(), 0xFFECD08F.toInt(), 0xFF6FA86A.toInt(), 0xFF8FC4E0.toInt(), 0xFF5A6AB0.toInt(), 0xFF8A6AB0.toInt())
            val bw = h * 0.011f
            val r0 = w * 0.62f; val bcx = w * 0.5f + skyPar; val bcy = hz + h * 0.08f
            paint.style = Paint.Style.STROKE; paint.strokeWidth = bw * 1.05f
            for ((i, col) in bands.withIndex()) {
                val rr = r0 - i * bw
                paint.color = argb(0.2f * light * (1 - cloud * 0.6f), col)
                c.drawArc(bcx - rr, bcy - rr, bcx + rr, bcy + rr, 180f, 180f, false, paint)
            }
            paint.style = Paint.Style.FILL
        }

        if (fireworks) drawFireworks(c, w, hz, s, t, false)

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

        if (fireworks) drawFireworks(c, w, hz, s, t, true)

        if (bowA > 0.02f) drawRainbow(c, w, h, hz, s, bowA)

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
            val gx = if (moonA > p.glow && moonA > light) w * MOON_X + skyPar else valleyX
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
        if (iced) drawIce(c, w, hz, s, light, t)
        paint.colorFilter = landFilter; paint.alpha = 255
        rect.set(landX, landTop, landX + landW, landBot)
        c.drawBitmap(land, null, rect, paint)

        // the steading on the right-hand hill: a longhouse that follows the house (see drawSteading)
        drawSteading(c, landX, landTop, s, light, p.dark, snowCover, t)
        // the places where the folders live: runestone, cave, skald, smithy, cookfire, knarr
        if (showPlaces) drawPlaces(c, landX, oy, s, light, p.dark, t)

        // longship, its reflection and the ripples. It sails out of the fjord when no one's home
        // and back in when someone is, and rows while the house sends power to the grid.
        val h0 = house
        val outTarget = if (h0?.away == true && cfg(Touch.LONGSHIP)) 1f else 0f
        if (shipOut < 0f) shipOut = if (h0 == null) 0f else outTarget
        if (shipOut != outTarget) {
            val step = dt / 16f                                   // ~16 s to cross the fjord
            shipOut = if (shipOut < outTarget) min(outTarget, shipOut + step) else max(outTarget, shipOut - step)
        }
        val sailing = shipOut != outTarget
        shipHome = shipOut < 0.1f
        val rowing = sailing || ((h0?.exportW ?: 0f) > 150f && cfg(Touch.OARS))
        if (shipOut < 0.995f) {
            val sl = lerp(1.15f, 0.65f, p.dark)
            val shipL = ox - 1440 * s * 0.02f + par; val shipT = oy + SHIP_TOP * s
            // the hull's waterline glides to the fjord's mouth, shrinking with the distance
            val pivX = shipL + 598 * 1.04f * s; val pivY = shipT + SHIP_WL * s
            val u = shipOut * shipOut * (3 - 2 * shipOut)
            val k = 1f - 0.9f * u
            val fade = 1f - u * u * u
            c.save()
            c.translate(lerp(pivX, valleyX, u), lerp(pivY, hz, u))
            c.scale(k, k)
            c.translate(-pivX, -pivY)
            val sun = if (!cfg(Touch.SAIL)) 0f
                else sqrt(((h0?.solarW ?: 0f) / SOLAR_FULL_W).coerceIn(0f, 1f))
            val sailRect = RectF(shipL + SAIL_L * 1.04f * s, shipT + SAIL_T * s, shipL + SAIL_R * 1.04f * s, shipT + SAIL_B * s)
            if (sun > 0.02f) {
                // the panels' power, caught in the sail: a warm halo behind it...
                val r = sailRect.height() * 1.1f
                paint.shader = RadialGradient(sailRect.centerX(), sailRect.centerY(), r, intArrayOf(argb(0.32f * sun * fade, 0xFFECD08F.toInt()), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(sailRect.centerX(), sailRect.centerY(), r, paint); paint.shader = null
            }
            val shipR = shipL + 1440 * 1.04f * s; val wl = shipT + SHIP_WL * s
            paint.isFilterBitmap = true
            paint.alpha = (255 * fade).toInt()
            // its reflection: hull and sail mirrored at the waterline, dimmer, cooler and rippled
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(sl * 0.8f, sl * 0.86f, sl * 0.95f, 1f) })
            rect.set(shipL, wl, shipR, wl + shipRefl.height * s)
            c.drawBitmap(shipRefl, null, rect, paint)
            paint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(sl, sl, sl, 1f) })
            rect.set(shipL, shipT, shipR, wl)
            c.drawBitmap(ship, null, rect, paint)
            // the sail, filled by the real wind: slack in a calm, bellied out in a breeze, straining
            // and shuddering in a gale
            val windNow = if (cfg(Touch.WIND)) wx?.windMph ?: 0f else 6f
            val gustNow = if (cfg(Touch.WIND)) wx?.gustMph ?: 0f else 0f
            val fillTarget = ((windNow - 3f) / 22f).coerceIn(0f, 1f)
            sailFill += (fillTarget - sailFill) * min(1f, dt / 3f)
            val gale = ((maxOf(gustNow, windNow * 1.3f) - 28f) / 20f).coerceIn(0f, 1f)
            sailMesh(sailRect, sailFill, gale, t)
            sail?.let { c.drawBitmapMesh(it, SAIL_COLS, SAIL_ROWS, meshV, 0, meshC, 0, paint) }
            paint.colorFilter = null; paint.alpha = 255
            if (rowing) drawOars(c, shipL, shipT, s, t, fade, p.dark)
            if (feast) dressShip(c, shipL, shipT, s, t, fade)
            // the wolves' eyes glow while one of the household is on the move: Freki's for the first, Geri's for the second
            val mv = h0?.movers ?: emptySet()
            if (showPlaces && mv.isNotEmpty()) for ((key, ex) in listOf("FREKI" to 128.3f, "GERI" to 1023.7f)) if (key in mv) {
                val x = shipL + ex * 1.04f * s; val y = shipT + 156.8f * s
                val pulse = 0.6f + 0.4f * sin(t * 0.005f)
                paint.shader = RadialGradient(x, y, 18 * s, intArrayOf(argb(0.8f * pulse * fade, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(x, y, 18 * s, paint); paint.shader = null
                paint.color = argb(fade, 0xFFFFC27A.toInt()); c.drawCircle(x, y, 3.4f * s, paint)
            }
            // something waits in Car: a gleam runs along the shields
            if (showPlaces && "CAR" in stirring) {
                val ph = (t % 2600f) / 2600f
                props.gleam(c, shipL + (300f + ph * 560f) * 1.04f * s, shipT + 336f * s, 46f * s, 18f * s, 0.55f * fade * sin(ph * PI.toFloat()))
            }
            paint.colorFilter = null; paint.alpha = 255
            val sm = sail
            if (sun > 0.02f && sm != null) {
                // ...and gold light in the cloth itself
                paint.colorFilter = PorterDuffColorFilter(0xFFECD08F.toInt(), PorterDuff.Mode.SRC_IN)
                paint.blendMode = BlendMode.PLUS
                paint.alpha = (150 * sun * fade).toInt()
                c.drawBitmapMesh(sm, SAIL_COLS, SAIL_ROWS, meshV, 0, null, 0, paint)
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
        val smoke = house?.hearth == true && showHearth
        val busy = (house?.leak == true && showLeak) || (house?.fire == true && showFire) || (house?.forge == true && showForge)
        frameMs = when {
            animating || sailing -> 66L
            smoke || rowing || System.currentTimeMillis() - flareAt in 0..20_000 || busy || (showPlaces && stirring.isNotEmpty()) ||
                (showPlaces && house?.movers?.isNotEmpty() == true) -> 125L
            showPlaces && (mimir || beaconLit) -> 125L          // the well's rising light, the beacon's fire
            showPlaces && onCall -> 250L                       // the on-call rune's slow breath
            else -> 60_000L
        }
        animating = animating || smoke || sailing || rowing || System.currentTimeMillis() - flareAt in 0..20_000 || busy ||
            (showPlaces && stirring.isNotEmpty())
        if (fireworks) { animating = true; frameMs = 66L }
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
        // windows either side of the door, lit while the house lights are on; a homecoming makes them flare
        val since = System.currentTimeMillis() - flareAt
        val flare = if (since in 0..20_000) (1f - since / 20_000f) * (0.6f + 0.4f * sin(t * 0.01f)) else 0f
        if (h?.lightsOn == true || flare > 0f) {
            val warm = 0xFFF0C070.toInt()
            val wy = base - 21 * s
            val gr = 22 * s * (1 + 1.6f * flare)
            for (wx in listOf(l + 34 * s, l + 82 * s, mid - 46 * s, mid + 46 * s, r - 82 * s, r - 34 * s)) {
                paint.shader = RadialGradient(wx, wy, gr, intArrayOf(argb((0.6f * glow + 0.4f * flare).coerceAtMost(1f), warm), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(wx, wy, gr, paint); paint.shader = null
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

        // the feast: long banners stream from poles at both gable ends
        if (feast) {
            for ((i, gx) in listOf(l + 40 * s, r - 40 * s).withIndex()) {
                val top = ridge - 34 * s
                paint.color = roof; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f * s
                c.drawLine(gx, ridge - 10 * s, gx, top, paint)
                paint.style = Paint.Style.FILL
                path.reset()
                path.moveTo(gx, top)
                val wave = sin(t * 0.004f + i * 1.7f) * 4 * s
                path.quadTo(gx - 20 * s, top + 2 * s + wave, gx - 42 * s, top + 6 * s + wave * 1.5f)
                path.quadTo(gx - 20 * s, top + 10 * s + wave, gx, top + 14 * s)
                path.close()
                paint.color = argb(0.95f, 0xFFB0402A.toInt()); c.drawPath(path, paint)
                paint.color = argb(0.95f, 0xFFECD08F.toInt())
                c.drawRect(gx - 3 * s, top + 1 * s, gx, top + 13 * s, paint)
            }
        }

        // Yule: an evergreen swag under the eaves hung with lanterns, and a wreath on the door
        if (showYule && isYule(System.currentTimeMillis())) {
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
        if (snow || frostK > 0.05f) {
            val white = mix(0xFF8A93A6.toInt(), 0xFFEEF2F8.toInt(), light * 0.8f + 0.2f)
            paint.color = if (snow) white else argb(0.18f + 0.4f * frostK, mix(0xFFB8C8DC.toInt(), 0xFFEAF2FA.toInt(), light))
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

        // something waits in Smart Home: sparks rise from the smoke hole
        if (showPlaces && "SMART_HOME" in stirring) {
            props.t = t
            c.save(); c.translate(mid + 40 * s, ridge + 2 * s); c.scale(s, s)
            props.sparks(c, 0f, 0f, 9, 2000f, 70f)
            c.restore()
        }

        // the hearth: while Jason or Leah is home, embers glow in the smoke hole and smoke rises
        if (h?.hearth == true && showHearth) {
            val hx = mid + 40 * s; val hy = ridge + 3 * s
            val flick = 0.8f + 0.2f * sin(t * 0.009f) * sin(t * 0.0047f + 1.3f)
            paint.shader = RadialGradient(hx, hy, 18 * s, intArrayOf(argb((0.35f + 0.5f * dark) * flick, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(hx, hy, 18 * s, paint); paint.shader = null
            paint.color = argb(0.9f * flick, 0xFFECD08F.toInt()); c.drawRect(hx - 6 * s, hy - 1.5f * s, hx + 6 * s, hy + 1.5f * s, paint)
            // by day grey smoke; by night pale, starlit smoke, warmed by the fire where it leaves the roof
            val pale = mix(0xFF7C8496.toInt(), 0xFFB4B9C2.toInt(), light)
            val strength = (0.42f + 0.28f * dark) * (if (feast) 1.35f else 1f)
            val life = 7000f; val n = if (feast) 24 else 16
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

        // the forge: while the printer works, a stone chimney near the hall's right end throws sparks
        if (h?.forge == true && showForge && !showPlaces) {          // (with the places, the smithy's chimney sparks instead)
            val cx = r - 72 * s; val ctop = ridge + 6 * s
            paint.color = mix(0xFF2A2C30.toInt(), 0xFF6A6660.toInt(), light)
            c.drawRect(cx - 7 * s, ctop - 18 * s, cx + 7 * s, ctop + 14 * s, paint)
            val heat = 0.75f + 0.25f * sin(t * 0.011f)
            paint.shader = RadialGradient(cx, ctop - 18 * s, 22 * s, intArrayOf(argb((0.4f + 0.4f * dark) * heat, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, ctop - 18 * s, 22 * s, paint); paint.shader = null
            for (i in 0 until 10) {
                val life = 1600f
                val age = ((t + i * 397f) % life) / life
                val sx = cx + sin(i * 2.3f + age * 5f) * 10 * s + (i % 3 - 1) * age * 14 * s
                val sy = ctop - 20 * s - age * 70 * s
                paint.color = argb((1 - age) * 0.95f, if (i % 2 == 0) 0xFFECD08F.toInt() else 0xFFE0703A.toInt())
                c.drawCircle(sx, sy, (1.8f - age) * s * 1.4f, paint)
            }
        }

        // Surtur's fire: while the smoker is lit, a cooking fire with a spit on the hillside below the lantern
        if (h?.fire == true && showFire) {
            val artX = (HALL_L - 70f).toInt().coerceIn(0, groundTops.size - 1)
            val fx = x(artX.toFloat()); val fy = y(groundTops[artX].toFloat()) + 2 * s
            paint.shader = RadialGradient(fx, fy - 8 * s, 40 * s, intArrayOf(argb(0.35f + 0.45f * dark, 0xFFE0703A.toInt()), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(fx, fy - 8 * s, 40 * s, paint); paint.shader = null
            // stones of the pit
            paint.color = mix(0xFF1A1C20.toInt(), 0xFF4A4846.toInt(), light)
            for (k in -2..2) c.drawCircle(fx + k * 6 * s, fy, 3.2f * s, paint)
            // three flickering tongues of flame
            for (k in 0 until 3) {
                val fl = 0.7f + 0.3f * sin(t * (0.013f + k * 0.004f) + k * 2f)
                val fh = (16 + 6 * (k % 2)) * s * fl
                val bx = fx + (k - 1) * 6 * s
                path.reset()
                path.moveTo(bx - 5 * s, fy - 1 * s)
                path.quadTo(bx - 4 * s, fy - fh * 0.6f, bx + sin(t * 0.009f + k) * 2 * s, fy - fh)
                path.quadTo(bx + 4 * s, fy - fh * 0.6f, bx + 5 * s, fy - 1 * s)
                path.close()
                paint.color = argb(0.9f, if (k == 1) 0xFFECD08F.toInt() else 0xFFE0703A.toInt())
                c.drawPath(path, paint)
            }
            // the spit: two forked posts and a bar with the roast
            paint.color = mix(0xFF120E0B.toInt(), 0xFF3A2C20.toInt(), light)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f * s; paint.strokeCap = Paint.Cap.ROUND
            for (side in listOf(-1, 1)) {
                val sx = fx + side * 18 * s
                c.drawLine(sx, fy, sx, fy - 24 * s, paint)
                c.drawLine(sx, fy - 24 * s, sx - 3 * s, fy - 28 * s, paint)
                c.drawLine(sx, fy - 24 * s, sx + 3 * s, fy - 28 * s, paint)
            }
            c.drawLine(fx - 22 * s, fy - 25 * s, fx + 22 * s, fy - 25 * s, paint)
            paint.style = Paint.Style.FILL
            c.drawOval(fx - 8 * s, fy - 30 * s, fx + 8 * s, fy - 20 * s, paint)
        }

        // a leak: water rises about the hall's footing, and the hall glows ember
        if (h?.leak == true && showLeak) {
            val beat = 0.5f + 0.5f * sin(t * 0.006f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * s
            paint.color = argb(0.45f + 0.45f * beat, 0xFFE0703A.toInt())
            c.drawRect(l, wallTop, r, base, paint)
            paint.style = Paint.Style.FILL
            path.reset()
            val wl = l - 40 * s; val wr = landX + 1440 * k
            path.moveTo(wl, base + 10 * s)
            var xx = wl
            while (xx <= wr) {
                path.lineTo(xx, base - 8 * s + sin(xx / (14 * s) + t * 0.004f) * 3 * s)
                xx += 6 * s
            }
            path.lineTo(wr, base + 10 * s); path.close()
            paint.color = argb(0.55f, 0xFF4F86A8.toInt())
            c.drawPath(path, paint)
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
     * Oars out of the ports along the hull, slanting down to meet the water a little in front of
     * the ship (lower on screen), sweeping fore and aft: the blades bite on the pull, with a ripple,
     * and lift clear on the return. Each has its reflection, rising from the blade back toward the hull.
     */
    private fun drawOars(c: Canvas, shipL: Float, shipT: Float, s: Float, t: Float, fade: Float, dark: Float) {
        val k = 1.04f * s
        val wl = shipT + SHIP_WL * s
        val hgt = (SHIP_WL - OAR_PORT) * s; val reach = OAR_REACH * s
        paint.reset(); paint.isAntiAlias = true
        paint.strokeCap = Paint.Cap.ROUND
        val wood = mix(0xFF7A5E3C.toInt(), 0xFF2C2219.toInt(), dark)
        val phase = (t * 0.0026f).toDouble()                       // about one stroke every 2.4 s
        // the ship heads right (bow to the right): blades catch forward and pull aft while in the water
        val sweep = -26f * s * sin(phase).toFloat()
        val lift = maxOf(0f, -kotlin.math.cos(phase).toFloat()) * 9f * s
        fun y(t0: Float, refl: Boolean): Float {
            val water = wl + reach * t0; val above = hgt * (1 - t0) + lift * t0
            return if (refl) water + above else water - above
        }
        for (refl in listOf(true, false)) {
            paint.color = argb(fade * (if (refl) 0.32f else 1f), wood)
            for (i in 0 until 9) {
                val px = shipL + (OAR_X0 + i * OAR_DX) * k; val tx = px - 12 * s + sweep
                paint.strokeWidth = 3.2f * s
                c.drawLine(px, y(0f, refl), tx, y(1f, refl), paint)
                paint.strokeWidth = 7f * s
                c.drawLine(px + (tx - px) * 0.7f, y(0.7f, refl), tx, y(1f, refl), paint)
            }
        }
        paint.color = argb(fade, mix(0xFF000000.toInt(), wood, 0.25f))
        for (i in 0 until 9) c.drawCircle(shipL + (OAR_X0 + i * OAR_DX) * k, wl - hgt, 3f * s, paint)
        val bite = kotlin.math.cos(phase).toFloat()
        if (bite > 0f) {
            paint.color = argb(fade * 0.55f * minOf(1f, bite * 2f), 0xFFDDE6F5.toInt())
            paint.strokeWidth = 1.6f * s
            for (i in 0 until 9) {
                val tx = shipL + (OAR_X0 + i * OAR_DX) * k - 12 * s + sweep
                c.drawLine(tx - 10 * s, wl + reach + s, tx + 10 * s, wl + reach + s, paint)
            }
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    private var sailFill = 0.3f
    private val meshV = FloatArray((SAIL_COLS + 1) * (SAIL_ROWS + 1) * 2)
    private val meshC = IntArray((SAIL_COLS + 1) * (SAIL_ROWS + 1))

    /**
     * The sail's shape for this frame, as a mesh over [r] (the yard along the top stays put).
     * [fill] 0 hangs slack and wrinkled, 1 bellies out (fatter mid-height, the foot curving down,
     * the edges in shadow); [gale] adds a fast shudder along the free edge.
     */
    private fun sailMesh(r: RectF, fill: Float, gale: Float, t: Float) {
        val w = r.width(); val h = r.height()
        val slack = 1f - fill
        var n = 0
        for (j in 0..SAIL_ROWS) {
            val v = j / SAIL_ROWS.toFloat()
            for (i in 0..SAIL_COLS) {
                val u = i / SAIL_COLS.toFloat()
                val arc = sin(PI.toFloat() * u)
                var dx = (u - 0.5f) * w * 0.10f * fill * sin(PI.toFloat() * v)
                var dy = h * (0.07f * fill * v * v + 0.03f * slack * v) * arc
                dx += w * 0.008f * slack * v * sin(v * 9f + u * 2f + t * 0.0015f)
                dx += w * (0.004f + 0.014f * gale) * fill * v * u * sin(t * (0.012f + 0.02f * gale) - u * 7f + v * 3f)
                if (gale > 0f) dy += h * 0.01f * gale * v * sin(t * 0.03f + u * 11f)
                meshV[n * 2] = r.left + u * w + dx
                meshV[n * 2 + 1] = r.top + v * h + dy
                val edge = abs(u - 0.5f) * 2f
                val lum = (1f - 0.28f * fill * edge * edge + 0.06f * fill * (1f - edge) - 0.05f * slack * v)
                val g = (255 * lum.coerceIn(0f, 1f)).toInt()
                meshC[n] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                n++
            }
        }
    }

    /** The ship with the sail's cloth taken out (it's drawn on its own, shaped by the wind). */
    private fun cutSail(src: Bitmap, cloth: Bitmap?): Bitmap {
        if (cloth == null) return src
        val b = runCatching { src.copy(Bitmap.Config.ARGB_8888, true) }.getOrNull() ?: return src
        val w = SAIL_R - SAIL_L; val h = SAIL_B - SAIL_T
        val px = IntArray(w * h); val m = IntArray(w * h)
        b.getPixels(px, 0, w, SAIL_L, SAIL_T, w, h)
        cloth.getPixels(m, 0, w, 0, 0, w, h)
        for (i in px.indices) if ((m[i] ushr 24) != 0) px[i] = 0
        b.setPixels(px, 0, w, SAIL_L, SAIL_T, w, h)
        src.recycle()
        return b
    }


    /** The ship mirrored about the waterline: fading with depth, cooler, broken by a gentle ripple. */
    private fun mirrorShip(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val row = IntArray(w); val o = IntArray(w)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (d in 0 until h) {
            src.getPixels(row, 0, w, 0, h - 1 - d, w, 1)
            val f = 1f - d / h.toFloat()
            val shift = Math.round(sin((h + d) * 0.23f) * (1f + 4f * d / h))
            val al = 0.5f * f * f * (0.9f + 0.1f * sin(d * 0.9f))
            for (x in 0 until w) {
                val c = row[(x - shift).coerceIn(0, w - 1)]
                o[x] = (((c ushr 24) * al).toInt() shl 24) or (c and 0xFFFFFF)
            }
            out.setPixels(o, 0, w, 0, d, w, 1)
        }
        return out
    }

    /**
     * Fireworks over the fjord: a burst of gold, ember and frost sparks every 1.4 s, falling as
     * they fade. With [mirror], their reflection in the water below the horizon.
     */
    private fun drawFireworks(c: Canvas, w: Int, hz: Float, s: Float, t: Float, mirror: Boolean) {
        val period = 1400L; val life = 2000f
        val tl = t.toLong()
        paint.style = Paint.Style.FILL
        for (k in 0..2) {
            val b = tl / period - k
            val age = (tl - b * period).toFloat()
            if (age !in 0f..life) continue
            val rnd = Random(b * 7919L)
            val x = w * (0.12f + 0.76f * rnd.nextFloat())
            val y = hz * (0.2f + 0.3f * rnd.nextFloat())
            val col = intArrayOf(0xFFECD08F.toInt(), 0xFFE0703A.toInt(), 0xFF8FC4E0.toInt(), 0xFFF0C070.toInt())[rnd.nextInt(4)]
            val speed = w * (0.1f + 0.06f * rnd.nextFloat())
            val a = age / 1000f
            val fade = Math.pow((1 - age / life).toDouble(), 1.5).toFloat()
            val g = w * 0.12f
            fun ys(v: Float) = if (mirror) 2 * hz - v else v
            val aMul = if (mirror) 0.25f else 1f
            if (age < 160 && !mirror) {
                paint.shader = RadialGradient(x, y, 60 * s, intArrayOf(argb(0.6f * (1 - age / 160f), col), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(x, y, 60 * s, paint); paint.shader = null
            }
            for (i in 0 until 48) {
                val ang = (i / 48f) * 2 * PI.toFloat() + rnd.nextFloat() * 0.12f
                val v = speed * (0.75f + 0.25f * rnd.nextFloat())
                val px = x + kotlin.math.cos(ang) * v * a
                val py = y + sin(ang) * v * a + 0.5f * g * a * a
                paint.color = argb(fade * aMul, col)
                c.drawCircle(px, ys(py), 2.6f * s, paint)
            }
        }
    }

    /** The longship dressed for the feast: a long pennant from the masthead and flags along the stay. */
    private fun dressShip(c: Canvas, shipL: Float, shipT: Float, s: Float, t: Float, fade: Float) {
        val k = 1.04f * s
        fun x(a: Float) = shipL + a * k
        fun y(a: Float) = shipT + a * s
        val p = android.graphics.Path()
        paint.reset(); paint.isAntiAlias = true
        val wave = sin(t * 0.004f) * 6 * s
        p.moveTo(x(578f), y(10f))
        p.quadTo(x(633f), y(14f) + wave, x(693f), y(20f) + wave * 1.4f)
        p.quadTo(x(633f), y(28f) + wave, x(578f), y(34f))
        p.close()
        paint.color = argb(0.95f * fade, 0xFFB0402A.toInt()); c.drawPath(p, paint)
        val cols = intArrayOf(0xFFECD08F.toInt(), 0xFFE0703A.toInt(), 0xFF8FC4E0.toInt())
        for (i in 0 until 7) {
            val f = (i + 0.5f) / 7f
            val fx = x(600f + (900f - 600f) * f); val fy = y(28f + (180f - 28f) * f)
            p.reset()
            p.moveTo(fx - 6 * s, fy); p.lineTo(fx + 6 * s, fy); p.lineTo(fx, fy + 13 * s); p.close()
            paint.color = argb(0.95f * fade, cols[i % 3]); c.drawPath(p, paint)
        }
        paint.color = argb(0.6f * fade, 0xFF1C150F.toInt()); paint.strokeWidth = 1.2f * s
        c.drawLine(x(600f), y(28f), x(900f), y(180f), paint)
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
        land.recycle(); ship.recycle(); shipRefl.recycle(); ray.recycle(); puff.recycle(); sail?.recycle()
        if (treeLazy.isInitialized()) tree.recycle()
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

    /** The places where the folders live, each standing on its spot in the mountain art (see [Place]). */
    /** Ratatoskr on his branch: sitting on top of it, or running up and down it while mail waits. */
    private fun drawRatatoskr(c: Canvas, w: Int, h: Int, xOffset: Float, t: Float, light: Float, treeA: Float) {
        val s = max(w / 1440f, h / 3120f)
        val stir = Place.MAIL.name in stirring
        // running: out to the tip and back, a little pause at each end
        val ph = t * 0.0009f
        val u = if (stir) (0.5f - 0.58f * kotlin.math.cos(ph)).coerceIn(0.02f, 0.98f) else 0.42f
        val back = stir && sin(ph) < 0f
        val pt = limbAt(w, h, xOffset, u)
        // on top of the branch: step out from its middle along the side facing the sky
        val nx = sin(pt[2]); val ny = -kotlin.math.cos(pt[2])
        val lift = pt[3] + 9f * s
        props.light = light; props.t = t; props.moon = moonLight
        if (stir) for (i in 0 until 5) {
            val tr = limbAt(w, h, xOffset, (u + (if (back) 1 else -1) * (0.06f + i * 0.05f)).coerceIn(0f, 1f))
            val a = (1f - i / 5f) * (0.45f + 0.55f * moonLight) * (0.6f + 0.4f * sin(t * 0.01f + i))
            props.glint(c, tr[0] + nx * (tr[3] + 6f * s), tr[1] + ny * (tr[3] + 6f * s) + sin(t * 0.004f + i * 2f) * 4f * s,
                (2.5f + 1.5f * (1 - i / 5f)) * s, a, 0xFFECD08F.toInt())
        }
        c.save()
        c.translate(pt[0] + nx * lift, pt[1] + ny * lift + (if (stir) -3f * kotlin.math.abs(sin(t * 0.02f)) * s else 0f))
        // his head points along the branch (toward the tip, or back toward the trunk), paws down on the bark
        c.rotate(Math.toDegrees(pt[2].toDouble()).toFloat() + 90f)
        c.scale(-2.1f * s, (if (back) -2.1f else 2.1f) * s)
        props.squirrel(c)
        c.restore()
    }

    private fun drawPlaces(c: Canvas, landX: Float, oy: Float, s: Float, light: Float, dark: Float, t: Float) {
        props.light = light; props.dark = dark; props.t = t; props.moon = moonLight
        props.forge = house?.forge == true && showForge
        props.printPct = house?.printPct ?: -1
        props.onCall = onCall
        val st = stirring
        for (pl in Place.entries) {
            if (pl.sky || pl.tree) continue
            val stir = pl.name in st
            c.save()
            if (pl == Place.SHOP && beached) {
                c.translate(landX + 1150f * 1.04f * s, oy + 1938f * s); c.scale(s * 0.7f, s * 0.7f)
                props.knarrBeached(c, stir); c.restore(); continue
            }
            c.translate(landX + pl.ax * 1.04f * s, oy + pl.ay * s)
            if (moonLight > 0.03f && pl != Place.GAMES) {
                val b = pl.box; val k = if (pl == Place.SHOP) 0.85f else 1f
                val cx = (b[0] + b[2]) / 2 * k * s; val cy = (b[1] + b[3]) / 2 * k * s
                val rr = max(b[2] - b[0], b[3] - b[1]) * 0.75f * k * s
                paint.shader = RadialGradient(cx + rr * 0.15f, cy - rr * 0.15f, rr, intArrayOf(argb(0.20f * moonLight, 0xFFCFDBF0.toInt()), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(cx + rr * 0.15f, cy - rr * 0.15f, rr, paint); paint.shader = null
            }
            when (pl) {
                Place.SHOP -> c.scale(s * 0.85f, s * 0.85f)
                Place.VIDEO, Place.WORK -> c.scale(s * 1.4f, s * 1.4f)
                else -> c.scale(s, s)
            }
            when (pl) {
                Place.PERSONAL -> props.runestone(c, stir)
                Place.FINANCE -> props.cave(c, stir)
                Place.VIDEO -> props.skald(c, stir)
                Place.WORK -> props.smithy(c, stir)
                Place.FOOD -> props.cookfire(c, stir)
                Place.SHOP -> props.knarr(c, stir)
                Place.GAMES -> props.serpent(c, stir)
                Place.NETWORK, Place.MAIL -> Unit
            }
            c.restore()
        }
        // Mímir's Well and the beacon cairn (they open apps, not folders)
        for ((ax, ay) in arrayOf(WELL_X to WELL_Y, BEACON_X to BEACON_Y)) {
            c.save(); c.translate(landX + ax * 1.04f * s, oy + ay * s); c.scale(s * 1.3f, s * 1.3f)
            if (ax == WELL_X) props.well(c, mimir) else props.beacon(c, beaconLit)
            c.restore()
        }
    }

    /** Bifröst after rain: a faint rainbow arched over the fjord, behind the mountains. */
    private fun drawRainbow(c: Canvas, w: Int, h: Int, hz: Float, s: Float, a: Float) {
        val cols = intArrayOf(0xFFE05050.toInt(), 0xFFE0A040.toInt(), 0xFFE8D860.toInt(), 0xFF70C070.toInt(), 0xFF6090E0.toInt(), 0xFF8A70D0.toInt())
        c.save(); c.clipRect(0f, 0f, w.toFloat(), hz)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 10f * s
        val cx = w * 0.62f; val cy = hz + h * 0.06f; val r0 = w * 0.78f
        for ((i, col) in cols.withIndex()) {
            val r = r0 - i * 10f * s
            paint.color = argb(0.16f * a, col)
            c.drawCircle(cx, cy, r, paint)
        }
        paint.style = Paint.Style.FILL
        c.restore()
    }

    /** A long cold spell: shelves of ice along the fjord's edges, cracked, with the light on them. */
    private fun drawIce(c: Canvas, w: Int, hz: Float, s: Float, light: Float, t: Float) {
        val ice = mix(0xFF8FA6BC.toInt(), 0xFFE6EEF4.toInt(), light)
        val path = android.graphics.Path()
        for (side in 0..1) {
            val x0 = if (side == 0) 0f else w * 0.70f; val x1 = if (side == 0) w * 0.30f else w.toFloat()
            path.reset(); path.moveTo(x0, hz)
            var x = x0
            while (x <= x1) {
                val edge = if (side == 0) (x1 - x) / (x1 - x0) else (x - x0) / (x1 - x0)
                path.lineTo(x, hz + (8f + 52f * edge + 6f * sin(x / (23f * s))) * s)
                x += 8f * s
            }
            path.lineTo(x1, hz); path.close()
            paint.color = argb(0.55f, ice); c.drawPath(path, paint)
        }
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f * s
        paint.color = argb(0.35f, mix(0xFF3A4A5C.toInt(), 0xFF7E93A8.toInt(), light))
        for (k in 0 until 7) {
            val xx = if (k < 4) w * (0.03f + k * 0.06f) else w * (0.74f + (k - 4) * 0.07f)
            c.drawLine(xx, hz + 6 * s, xx + 18 * s, hz + 26 * s, paint)
            c.drawLine(xx + 18 * s, hz + 26 * s, xx + 9 * s, hz + 40 * s, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun decode(ctx: Context, res: Int): Bitmap =
        BitmapFactory.decodeResource(ctx.resources, res, BitmapFactory.Options().apply { inScaled = false })

    /**
     * The World Tree's silhouette, drawn once: a great trunk that splits again and again into a
     * broad, bare ash crown (white; tinted when drawn).
     */
    private fun worldTree(): Bitmap {
        val wd = 900; val ht = 1000
        val r = Random(7)
        val segs = ArrayList<FloatArray>(9000)          // x1, y1, x2, y2, width (trunk foot at 0,0; up is -y)
        fun branch(x: Float, y: Float, ang: Float, len: Float, sw: Float, depth: Int) {
            val x2 = x + kotlin.math.cos(ang) * len; val y2 = y + sin(ang) * len
            segs += floatArrayOf(x, y, x2, y2, sw)
            if (depth == 0) return
            val kids = if (depth >= 5 && r.nextFloat() < 0.45f) 3 else 2
            for (k in 0 until kids) {
                val side = if (kids == 3) floatArrayOf(-1f, 1f, 0f)[k] else if (k == 0) -1f else 1f
                val spread = if (side == 0f) 0.05f else 0.28f + 0.32f * r.nextFloat()
                val lean = (ang + PI.toFloat() / 2) * 0.10f            // older limbs lean outward
                val a = ang + side * spread + lean + (r.nextFloat() - 0.5f) * 0.22f
                branch(x2, y2, a, len * (0.66f + 0.12f * r.nextFloat()), (sw * 0.66f).coerceAtLeast(1f), depth - 1)
            }
        }
        // a thick, slightly wandering trunk, then three great limbs
        var x = 0f; var y = 0f; var sw = 60f
        repeat(3) {
            val a = -PI.toFloat() / 2 + (r.nextFloat() - 0.5f) * 0.12f
            val x2 = x + kotlin.math.cos(a) * 90; val y2 = y + sin(a) * 90
            segs += floatArrayOf(x, y, x2, y2, sw); x = x2; y = y2; sw *= 0.92f
        }
        for (a in floatArrayOf(-PI.toFloat() / 2 - 0.7f, -PI.toFloat() / 2 - 0.15f, -PI.toFloat() / 2 + 0.5f)) branch(x, y, a, 150f, sw * 0.7f, 9)
        // fit the whole tree into the bitmap, foot at the bottom centre
        var minX = 0f; var maxX = 0f; var minY = 0f
        for (g in segs) { minX = min(minX, min(g[0], g[2])); maxX = max(maxX, max(g[0], g[2])); minY = min(minY, min(g[1], g[3])) }
        val sc = min((wd - 20) / (maxX - minX), (ht - 10) / -minY)
        val cx = wd / 2f - (minX + maxX) / 2 * sc
        val bm = Bitmap.createBitmap(wd, ht, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        for (g in segs) {
            pp.strokeWidth = max(1f, g[4] * sc)
            cv.drawLine(cx + g[0] * sc, ht + g[1] * sc, cx + g[2] * sc, ht + g[3] * sc, pp)
        }
        return bm
    }

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
