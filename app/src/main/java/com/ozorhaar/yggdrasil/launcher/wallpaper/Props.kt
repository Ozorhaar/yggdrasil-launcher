package com.ozorhaar.yggdrasil.launcher.wallpaper

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The steading's places: the folders that live in the scene each have a thing you can tap.
 * Each prop is drawn in its own units (1 = one art pixel at scale 1) from an anchor at its foot,
 * up is -y. The same drawing as yggdrasil-os windows/sky/web/props.js.
 */
internal class Props {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()

    var light = 1f; var dark = 0f; var t = 0f; var forge = false
    /** Tonight's moonlight, 0..1: Jörmungandr's scales shimmer with it, Ratatoskr's fur takes a gold edge. */
    var moon = 0f
    /** Snow lies on the roofs and stones. */
    var snow = false
    /** The print's progress, 0..100, while the printer works (-1 otherwise): the smithy's forge brightens with it. */
    var printPct = -1
    /** On call: Dagaz, the rune of day and waking, cut into the smithy's door in frost, breathing slowly. */
    var onCall = false
    private val snowC get() = mix(0xDDE4EE, 0xF4F7FA, light)
    /** Hoarfrost, 0..1: on freezing nights without snow, a thin white rime where the snow would lie, sparkling. */
    var frost = 0f
    /** The moon's light (set by the scene) also makes the rime sparkle. */
    private val capped get() = snow || frost > 0.05f
    private val capC get() = if (snow) snowC else argb(0.22f + 0.45f * frost, mix(0xB8C8DC, 0xEAF2FA, light))
    /** A few rime crystals twinkling along a line (only frost, not snow). */
    private fun rime(c: Canvas, x0: Float, x1: Float, y: Float, n: Int = 4) {
        if (snow || frost < 0.05f) return
        val shine = frost * max(0.3f + 0.7f * moon, light * 0.6f)
        for (i in 0 until n) {
            val u = (i + 0.5f) / n
            val tw = max(0f, sin(t * 0.0023f + i * 2.4f + x0 * 0.05f))
            if (tw > 0.1f) glint(c, x0 + (x1 - x0) * u, y, 2.5f + 3f * tw, 0.8f * shine * tw)
        }
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        fun ch(sh: Int) = (((a shr sh) and 255) + ((((b shr sh) and 255) - ((a shr sh) and 255)) * k)).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
    private fun argb(a: Float, c: Int) = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (c and 0xFFFFFF)
    private val rock get() = mix(0x15171d, 0x5c5862, light)
    private val darkC get() = mix(0x0b0d12, 0x2c2a2e, light)
    private val wood get() = mix(0x120e0b, 0x4a3828, light)

    private fun fill(c: Int) { p.shader = null; p.style = Paint.Style.FILL; p.color = c }
    private fun stroke(c: Int, w: Float) {
        p.shader = null; p.style = Paint.Style.STROKE; p.color = c; p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }
    private fun glow(c: Canvas, x: Float, y: Float, r: Float, a: Float, col: Int = EMBER) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(x, y, r, intArrayOf(argb(a, col), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p); p.shader = null
    }
    private fun flames(c: Canvas, x: Float, y: Float, w: Float, h: Float, n: Int = 3) {
        for (k in 0 until n) {
            val fl = 0.7f + 0.3f * sin(t * (0.013f + k * 0.004f) + k * 2f)
            val fh = h * (1 + 0.3f * (k % 2)) * fl; val bx = x + (k - (n - 1) / 2f) * w
            path.reset(); path.moveTo(bx - w * 0.85f, y)
            path.quadTo(bx - w * 0.7f, y - fh * 0.6f, bx + sin(t * 0.009f + k) * w * 0.35f, y - fh)
            path.quadTo(bx + w * 0.7f, y - fh * 0.6f, bx + w * 0.85f, y); path.close()
            fill(argb(0.92f, if (k == n / 2) GOLD else EMBER)); c.drawPath(path, p)
        }
    }
    private fun star(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
        path.reset(); path.moveTo(x, y - r); path.lineTo(x + r * 0.25f, y - r * 0.25f); path.lineTo(x + r, y)
        path.lineTo(x + r * 0.25f, y + r * 0.25f); path.lineTo(x, y + r); path.lineTo(x - r * 0.25f, y + r * 0.25f)
        path.lineTo(x - r, y); path.lineTo(x - r * 0.25f, y - r * 0.25f); path.close()
        fill(argb(a, 0xFFF4D6)); c.drawPath(path, p)
    }
    /** Sparks rising from x, y: n of them, each living [life] ms and climbing [rise]. */
    fun sparks(c: Canvas, x: Float, y: Float, n: Int, life: Float, rise: Float) {
        for (i in 0 until n) {
            val age = ((t + i * life / n) % life) / life
            val sx = x + sin(i * 2.3f + age * 5f) * 8f + (i % 3 - 1) * age * 12f
            fill(argb((1 - age) * 0.95f, if (i % 2 == 0) GOLD else EMBER))
            c.drawCircle(sx, y - age * rise, (1.8f - age) * 1.2f + 0.3f, p)
        }
    }

    /** A four-pointed glint (for frost on the World Tree). */
    fun glint(c: Canvas, x: Float, y: Float, r: Float, a: Float, col: Int = FROST) {
        path.reset(); path.moveTo(x, y - r); path.lineTo(x + r * 0.25f, y - r * 0.25f); path.lineTo(x + r, y)
        path.lineTo(x + r * 0.25f, y + r * 0.25f); path.lineTo(x, y + r); path.lineTo(x - r * 0.25f, y + r * 0.25f)
        path.lineTo(x - r, y); path.lineTo(x - r * 0.25f, y - r * 0.25f); path.close()
        fill(argb(a, col)); c.drawPath(path, p)
    }

    /** A soft light at x, y (for the shields' gleam). */
    fun gleam(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, a: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(0f, 0f, 1f, intArrayOf(argb(a, 0xFFF4D6), 0), null, Shader.TileMode.CLAMP)
        c.save(); c.translate(x, y); c.scale(rx, ry); c.drawCircle(0f, 0f, 1f, p); c.restore()
        p.shader = null
    }

    private fun stones(c: Canvas, cx: Float, gap: Float, r: Float) {
        fill(rock); for (k in -2..2) c.drawCircle(cx + k * gap, 0f, r, p)
    }

    /** Personal: a carved runestone on the ridge, its serpent band catching the light. */
    fun runestone(c: Canvas, stir: Boolean = false) {
        val pulse = 0.5f + 0.5f * sin(t * 0.004f)
        if (stir) glow(c, 0f, -50f, 64f, 0.18f + 0.22f * pulse)
        path.reset(); path.moveTo(-22f, 2f); path.lineTo(-26f, -58f); path.quadTo(-24f, -92f, 0f, -100f)
        path.quadTo(24f, -95f, 26f, -60f); path.lineTo(22f, 2f); path.close()
        fill(rock); c.drawPath(path, p)
        // something waits: the runes burn ember and slowly pulse
        stroke(if (stir) argb(0.6f + 0.4f * pulse, EMBER) else argb(0.35f + 0.35f * dark, GOLD), 2.6f)
        path.reset(); path.moveTo(-14f, -4f); path.lineTo(-17f, -56f); path.quadTo(-16f, -82f, 0f, -88f)
        path.quadTo(16f, -84f, 17f, -58f); path.lineTo(14f, -10f); path.quadTo(10f, -2f, 4f, -8f)
        c.drawPath(path, p)
        // the rune of the day, cut large in the middle of the band
        val r = RuneOfDay.today().strokes
        var minX = 1f; var maxX = 0f
        for (i in r.indices step 2) { minX = minOf(minX, r[i]); maxX = maxOf(maxX, r[i]) }
        val gw = 22f; val gh = 44f; val ox = -(minX + maxX) / 2f * gw; val oy = -72f
        p.strokeWidth = 2.6f
        for (i in r.indices step 4) c.drawLine(ox + r[i] * gw, oy + r[i + 1] * gh, ox + r[i + 2] * gw, oy + r[i + 3] * gh, p)
        if (capped) {
            path.reset(); path.moveTo(-25f, -78f); path.quadTo(-23f, -97f, 0f, -103f); path.quadTo(23f, -98f, 25f, -78f)
            path.lineTo(19f, -84f); path.quadTo(0f, -91f, -19f, -84f); path.close()
            fill(capC); c.drawPath(path, p)
        }
        rime(c, -20f, 20f, -96f, 3)
    }

    /** Finance: a cave in the mountain's face, a dragon's gold heaped inside, glinting. */
    fun cave(c: Canvas, stir: Boolean = false) {
        path.reset(); path.moveTo(-64f, 0f); path.quadTo(-62f, -72f, 0f, -84f); path.quadTo(60f, -74f, 66f, 0f); path.close()
        fill(mix(0x030406, 0x14131a, light)); c.drawPath(path, p)
        stroke(rock, 7f)
        path.reset(); path.moveTo(-70f, 3f); path.quadTo(-68f, -78f, 0f, -91f); path.quadTo(66f, -80f, 72f, 3f); c.drawPath(path, p)
        if (capped) { stroke(capC, 4f); path.reset(); path.moveTo(-58f, -48f); path.quadTo(-52f, -86f, 0f, -95f); path.quadTo(52f, -88f, 60f, -50f); c.drawPath(path, p) }
        rime(c, -50f, 50f, -86f, 4)
        glow(c, 0f, -12f, 70f, 0.18f + 0.32f * dark, GOLD)
        path.reset(); path.moveTo(-50f, 0f); path.quadTo(-36f, -24f, -12f, -30f); path.quadTo(10f, -36f, 30f, -22f)
        path.quadTo(44f, -12f, 52f, 0f); path.close()
        fill(mix(0x6b5320, 0xc8a040, 0.55f + 0.45f * max(light, dark * 0.8f))); c.drawPath(path, p)
        fill(mix(0x8a6a28, GOLD, 0.6f))
        for (q in arrayOf(-30f to -8f, -14f to -18f, 4f to -24f, 20f to -14f, 34f to -6f, -4f to -6f, 12f to -4f)) {
            rf.set(q.first - 4.5f, q.second - 2.2f, q.first + 4.5f, q.second + 2.2f); c.drawOval(rf, p)
        }
        // the dragon's tail curled over its hoard
        val tail = mix(0x07080b, 0x1d1b22, light)
        stroke(tail, 7f)
        path.reset(); path.moveTo(62f, -6f); path.quadTo(40f, -46f, 6f, -40f); path.quadTo(-18f, -36f, -20f, -50f); c.drawPath(path, p)
        fill(tail)
        for (q in arrayOf(44f to -32f, 26f to -42f, 8f to -44f)) {
            path.reset(); path.moveTo(q.first - 4, q.second); path.lineTo(q.first, q.second - 9); path.lineTo(q.first + 4, q.second); path.close()
            c.drawPath(path, p)
        }
        for (g in arrayOf(floatArrayOf(-22f, -20f, 0f), floatArrayOf(8f, -30f, 1.7f), floatArrayOf(28f, -16f, 3.1f), floatArrayOf(-38f, -6f, 4.4f))) {
            val a = max(0f, sin(t * 0.002f + g[2])); if (a > 0.05f) star(c, g[0], g[1], 5 * a + 1, 0.9f * a)
        }
        // something waits: the dragon wakes, two eyes open in the dark above its gold, blinking now and then
        if (stir && (t % 5200f) > 220f) for (ex in floatArrayOf(-13f, 13f)) {
            glow(c, ex, -60f, 11f, 0.55f, GOLD)
            fill(argb(0.95f, 0xFFE08A)); rf.set(ex - 5.5f, -62.6f, ex + 5.5f, -57.4f); c.drawOval(rf, p)
            fill(0xFF14100A.toInt()); c.drawRect(ex - 0.9f, -62.2f, ex + 0.9f, -57.8f, p)
        }
    }

    /** Video: a skald by a little fire on the shore, his lyre on his knee. */
    fun skald(c: Canvas, stir: Boolean = false) {
        // a low hump of the ridge itself under him and his fire, so they sit on any ground
        path.reset(); path.moveTo(-50f, 16f); path.quadTo(-40f, 1f, -20f, 0f); path.lineTo(44f, 0f); path.quadTo(60f, 1f, 68f, 16f); path.close()
        fill(mix(0x1a151f, 0x3b2f45, light)); c.drawPath(path, p)
        glow(c, 22f, -10f, if (stir) 80f else 58f, 0.25f + 0.45f * dark + (if (stir) 0.15f else 0f))
        stones(c, 22f, 6f, 3.4f)
        flames(c, 22f, -1f, 5.5f, if (stir) 28f else 16f)
        if (stir) sparks(c, 22f, -18f, 8, 2200f, 80f)              // something waits: the fire flares and throws sparks
        fill(darkC)
        path.reset(); path.moveTo(-26f, 0f); path.quadTo(-30f, -22f, -20f, -34f); path.quadTo(-12f, -44f, -6f, -36f)
        path.quadTo(-2f, -24f, 2f, -14f); path.lineTo(6f, 0f); path.close(); c.drawPath(path, p)
        c.drawCircle(-12f, -40f, 7f, p)
        path.reset(); path.moveTo(-20f, -42f); path.quadTo(-12f, -54f, -4f, -42f); path.close(); c.drawPath(path, p)
        stroke(argb(0.6f + 0.3f * dark, GOLD), 2f)
        path.reset(); path.moveTo(0f, -12f); path.quadTo(-2f, -30f, 4f, -32f); path.moveTo(0f, -12f); path.quadTo(12f, -26f, 10f, -32f)
        path.moveTo(3f, -30f); path.lineTo(10f, -30f); c.drawPath(path, p)
        p.strokeWidth = 0.8f
        c.drawLines(floatArrayOf(4f, -29f, 2f, -14f, 6f, -29f, 4f, -14f, 8f, -29f, 6f, -14f), p)
    }

    /** Work: a smithy on a ledge of the slope, its forge glowing through the door, an anvil outside.
     *  While the printer works, sparks fly from its chimney. */
    fun smithy(c: Canvas, stir: Boolean = false) {
        path.reset(); path.moveTo(-140f, 2f); path.lineTo(96f, 2f); path.lineTo(84f, 16f); path.quadTo(-10f, 26f, -128f, 14f); path.close()
        fill(rock); c.drawPath(path, p)
        fill(mix(0x141820, 0x5a5658, light)); c.drawRect(-52f, -42f, 52f, 2f, p)
        path.reset(); path.moveTo(-66f, -38f); path.quadTo(-36f, -72f, 0f, -76f); path.quadTo(36f, -72f, 66f, -38f); path.close()
        fill(mix(0x0b0e15, 0x3a3638, light)); c.drawPath(path, p)
        fill(rock); c.drawRect(30f, -88f, 44f, -50f, p)
        val prog = if (forge && printPct >= 0) printPct.coerceIn(0, 100) / 100f else -1f
        val heat = (0.7f + 0.3f * sin(t * 0.004f) * (if (forge) 1.6f else 1f)) * (if (prog >= 0f) 0.6f + 0.6f * prog else 1f)
        glow(c, -8f, -16f, 46f + (if (prog >= 0f) 20f * prog else 0f), (0.25f + 0.45f * dark) * heat)
        fill(argb(0.55f + 0.4f * heat * (0.5f + 0.5f * dark), EMBER)); c.drawRect(-20f, -32f, 4f, 2f, p)
        if (prog >= 0f) { fill(argb(0.9f, GOLD)); c.drawRect(-18f, 1f - 32f * prog, 2f, 1f, p) }      // the print, rising like a pour
        else { fill(argb(0.35f * heat, GOLD)); c.drawRect(-16f, -12f, 0f, -2f, p) }
        if (onCall) {
            val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 0.0029f))
            glow(c, -8f, -15f, 26f, 0.55f * a, FROST)
            stroke(argb(a, 0xDDEEFA), 2.2f)
            // Dagaz: two uprights joined by a cross, filling the doorway
            c.drawLines(floatArrayOf(-16f, -27f, -16f, -5f, 0f, -27f, 0f, -5f, -16f, -27f, 0f, -5f, 0f, -27f, -16f, -5f), p)
        }
        if (capped) {
            path.reset(); path.moveTo(-66f, -38f); path.quadTo(-36f, -72f, 0f, -76f); path.quadTo(36f, -72f, 66f, -38f)
            path.lineTo(60f, -42f); path.quadTo(34f, -65f, 0f, -69f); path.quadTo(-34f, -65f, -60f, -42f); path.close()
            fill(capC); c.drawPath(path, p)
            stroke(capC, 3f); c.drawLine(-96f, 1f, 92f, 1f, p)
        }
        rime(c, -50f, 50f, -64f, 4); rime(c, -120f, 80f, 0f, 5)
        c.save(); c.translate(3.5f, -1f); c.scale(1.5f, 1.5f)          // the anvil: x -77 -> -112, 1.5x, foot on the ledge
        fill(mix(0x07080b, 0x26262a, light)); c.drawRect(-82f, -10f, -72f, 2f, p)
        path.reset(); path.moveTo(-92f, -10f); path.lineTo(-62f, -10f); path.quadTo(-58f, -15f, -64f, -18f); path.lineTo(-90f, -18f); path.close()
        c.drawPath(path, p)
        // something waits, or the printer works: the hammer rings on the anvil, a burst of sparks every few seconds
        if (stir || prog >= 0f) {
            val ph = (t % 2400f) / 2400f
            if (ph < 0.32f) {
                val k = ph / 0.32f
                glow(c, -77f, -18f, 26f, 0.6f * (1 - k), GOLD)
                for (i in 0 until 12) {
                    val a = (-PI.toFloat() / 2) + (i - 5.5f) * 0.2f
                    val r = 6f + k * (24f + (i % 4) * 6f)
                    fill(argb(1 - k, if (i % 2 == 0) GOLD else EMBER))
                    c.drawCircle(-77f + kotlin.math.cos(a) * r, -18f + sin(a) * r + k * k * 10f, 1.6f * (1 - k) + 0.4f, p)
                }
            }
        }
        c.restore()
        if (forge) for (i in 0 until 10) {
            val life = 1600f; val age = ((t + i * 397f) % life) / life
            val sx = 37f + sin(i * 2.3f + age * 5f) * 10f + (i % 3 - 1) * age * 14f; val sy = -90f - age * 70f
            fill(argb((1 - age) * 0.95f, if (i % 2 == 1) EMBER else GOLD)); c.drawCircle(sx, sy, (1.8f - age) * 1.4f, p)
        }
    }

    /** Food: a cookfire on the shore with a cauldron hung from a tripod, steam rising. */
    fun cookfire(c: Canvas, stir: Boolean = false) {
        glow(c, 0f, -10f, 56f, 0.25f + 0.45f * dark)
        stones(c, 0f, 7f, 3.6f)
        flames(c, 0f, -1f, 6f, 14f)
        stroke(wood, 3f)
        c.drawLines(floatArrayOf(-30f, 2f, 0f, -66f, 0f, -66f, 30f, 2f, 8f, 4f, 0f, -66f), p)
        p.strokeWidth = 1.5f; c.drawLine(0f, -66f, 0f, -44f, p)
        path.reset(); path.moveTo(-19f, -44f); path.lineTo(19f, -44f); path.quadTo(21f, -16f, 0f, -14f); path.quadTo(-21f, -16f, -19f, -44f); path.close()
        fill(mix(0x07080b, 0x2a2a2e, light)); c.drawPath(path, p)
        stroke(mix(0x2a2a2e, 0x7a7670, light), 2.5f); c.drawLine(-21f, -44f, 21f, -44f, p)
        // steam; something waits: the cauldron boils over, steam pouring up
        val puffs = if (stir) 9 else 4; val life = if (stir) 2000f else 3200f
        for (i in 0 until puffs) {
            val age = ((t + i * life / puffs) % life) / life
            fill(argb((if (stir) 0.32f else 0.22f) * (1 - age), 0xE8ECF2))
            c.drawCircle(sin(age * 4 + i) * (if (stir) 10 else 6), -48f - age * (if (stir) 70f else 46f), (if (stir) 5f else 4f) + age * (if (stir) 13f else 9f), p)
        }
        if (stir) for (i in 0 until 4) {
            val ph = ((t * 0.003f + i * 1.7f) % 1f + 1f) % 1f
            fill(argb(0.7f * (1 - ph), 0xBFD3E6)); c.drawCircle(-12f + i * 8f, -45f - ph * 7f, 1.6f, p)
        }
    }

    private fun knarrShape(c: Canvas, lantern: Boolean, furled: Boolean = false) {
        stroke(darkC, 4f); c.drawLine(0f, -12f, 0f, -150f, p)
        p.strokeWidth = 5f; c.drawLine(-62f, -142f, 62f, -142f, p)
        p.strokeWidth = 1.6f; c.drawLines(floatArrayOf(0f, -150f, -112f, -30f, 0f, -150f, 112f, -30f), p)
        val sl = 0.55f + 0.45f * light
        if (furled) { stroke(mix(BONE, 0x000000, 1 - sl), 7f); c.drawLine(-58f, -137f, 58f, -137f, p) }
        else {
            for (i in 0 until 6) { fill(mix(if (i % 2 == 1) BONE else FROST, 0x000000, 1 - sl)); c.drawRect(-56f + i * 18.6f, -139f, -56f + i * 18.6f + 18.8f, -69f, p) }
            fill(0x40000000); c.drawRect(-56f, -139f, 56f, -69f, p)
        }
        fill(mix(0x2a2016, 0x8a6e4a, light))
        for (b in arrayOf(-82f to 26f, -54f to 22f, 48f to 24f, 74f to 22f)) { rf.set(b.first, -30f, b.first + b.second, -12f); c.drawRoundRect(rf, 5f, 5f, p) }
        path.reset(); path.moveTo(-118f, -36f); path.quadTo(-112f, -10f, -96f, -12f); path.lineTo(96f, -12f); path.quadTo(112f, -10f, 118f, -36f)
        path.quadTo(124f, -12f, 104f, 4f); path.quadTo(0f, 18f, -104f, 4f); path.quadTo(-124f, -12f, -118f, -36f); path.close()
        fill(darkC); c.drawPath(path, p)
        stroke(argb(0.25f + 0.2f * light, GOLD), 1.6f)
        path.reset(); path.moveTo(-98f, -8f); path.quadTo(0f, 2f, 98f, -8f); c.drawPath(path, p)
        if (lantern) {
            val fl = 0.8f + 0.2f * sin(t * 0.011f) * sin(t * 0.0067f + 1f)
            stroke(darkC, 2f); c.drawLine(110f, -36f, 110f, -58f, p)
            glow(c, 110f, -62f, 34f, (0.45f + 0.3f * dark) * fl, GOLD)
            fill(argb(0.95f * fl, GOLD)); rf.set(106f, -67f, 114f, -57f); c.drawRoundRect(rf, 2f, 2f, p)
        }
    }

    /** Shop: a knarr, the broad-bellied trader, riding at anchor with bales on deck, and its reflection. */
    fun knarr(c: Canvas, stir: Boolean = false) {
        val bob = sin(t * 0.0015f) * 3f; val roll = sin(t * 0.0011f) * 0.012f * 180f / PI.toFloat()
        val layer = c.saveLayerAlpha(-140f, -20f, 140f, 170f, (255 * 0.28f).toInt())
        c.scale(1f, -1f); c.translate(0f, -bob); c.rotate(-roll); knarrShape(c, stir)
        c.restoreToCount(layer)
        c.save(); c.translate(0f, bob); c.rotate(roll); knarrShape(c, stir); c.restore()
        stroke(argb(0.18f, 0xDDE6F5), 1.5f); c.drawLines(floatArrayOf(-120f, 6f, -90f, 6f, 92f, 6f, 124f, 6f), p)
    }

    /** The knarr drawn up on the shore for the cold, its sail furled (no reflection: it's on land). */
    fun knarrBeached(c: Canvas, stir: Boolean = false) {
        c.save(); c.rotate(-5f); knarrShape(c, stir, furled = true)
        if (capped) { stroke(capC, 3f); c.drawLine(-100f, -13f, 100f, -13f, p) }
        rime(c, -90f, 90f, -14f, 5)
        c.restore()
    }

    /** Claude: Mímir's Well under the World Tree, a ring of stones round a dark pool and a stone cut with Ansuz
     *  (Odin's word). While an alert waits, the pool glows frost and motes of light rise from it. */
    fun well(c: Canvas, lit: Boolean) {
        val k = if (lit) 0.6f + 0.4f * sin(t * 0.0025f) else 0f
        // a low hump of the ridge itself under the well and its stone, so they sit on the shoulder where it falls away
        path.reset(); path.moveTo(-60f, 16f); path.quadTo(-52f, 1f, -36f, 0f); path.lineTo(30f, 0f); path.quadTo(46f, 2f, 56f, 24f); path.close()
        fill(mix(0x1a151f, 0x3b2f45, light)); c.drawPath(path, p)
        if (lit) glow(c, 0f, -14f, 54f, 0.45f * k + 0.15f, FROST)
        // the back of the ring, the pool, the front of the ring
        fill(rock); rf.set(-31f, -20f, 31f, -6f); c.drawOval(rf, p)
        fill(if (lit) argb(0.75f + 0.25f * k, mix(0x2a4a5a, 0xBFE2F2, 0.4f + 0.5f * k)) else mix(0x05070b, 0x1a2a34, light))
        rf.set(-25f, -17f, 25f, -9f); c.drawOval(rf, p)
        if (!lit && moon > 0.05f) glint(c, 8f, -13f, 3f, 0.5f * moon)
        path.reset(); path.moveTo(-31f, -13f); path.lineTo(-30f, 0f); path.quadTo(0f, 9f, 30f, 0f); path.lineTo(31f, -13f)
        path.quadTo(0f, -4f, -31f, -13f); path.close(); fill(rock); c.drawPath(path, p)
        stroke(darkC, 1.4f); c.drawLines(floatArrayOf(-18f, -9f, -18f, 2f, 0f, -6f, 0f, 5f, 18f, -9f, 18f, 2f, -30f, -5f, 30f, -5f), p)
        // the standing stone beside it, Ansuz cut in gold
        path.reset(); path.moveTo(-44f, 0f); path.lineTo(-46f, -38f); path.quadTo(-42f, -50f, -36f, -40f); path.lineTo(-34f, 0f); path.close()
        fill(rock); c.drawPath(path, p)
        stroke(argb(0.45f + 0.35f * dark + 0.2f * k, if (lit) FROST else GOLD), 1.6f)
        c.drawLines(floatArrayOf(-42f, -36f, -42f, -8f, -42f, -36f, -36f, -30f, -42f, -28f, -36f, -22f), p)
        if (capped) { stroke(capC, 3f); c.drawLine(-28f, -18f, 28f, -18f, p) }
        if (lit) for (i in 0 until 7) {
            val life = 2600f; val age = ((t + i * life / 7) % life) / life
            glint(c, sin(i * 2.1f + age * 3f) * 12f, -14f - age * 70f, 2f + 3f * (1 - age), (1 - age) * 0.9f, 0xDDEEFA)
        }
    }

    /** Camera alerts: a beacon cairn on the ridge, an iron basket on a pole over a heap of stones. Unseen alerts:
     *  it burns, throwing sparks, its glow on the ridge. */
    fun beacon(c: Canvas, lit: Boolean) {
        if (lit) glow(c, 0f, -96f, 110f, 0.5f + 0.15f * sin(t * 0.007f))
        // the cairn
        path.reset(); path.moveTo(-34f, 2f); path.quadTo(-30f, -22f, -12f, -34f); path.lineTo(12f, -34f); path.quadTo(30f, -22f, 34f, 2f); path.close()
        fill(rock); c.drawPath(path, p)
        stroke(darkC, 1.4f)
        c.drawLines(floatArrayOf(-26f, -10f, 26f, -10f, -18f, -22f, 18f, -22f, -10f, 2f, -14f, -10f, 8f, -10f, 12f, -22f, 0f, -22f, -4f, -34f), p)
        if (capped) { path.reset(); path.moveTo(-14f, -33f); path.quadTo(0f, -38f, 14f, -33f); stroke(capC, 3f); c.drawPath(path, p) }
        // the pole and the basket
        stroke(darkC, 4f); c.drawLine(0f, -34f, 0f, -86f, p)
        path.reset(); path.moveTo(-15f, -100f); path.lineTo(15f, -100f); path.lineTo(10f, -86f); path.lineTo(-10f, -86f); path.close()
        fill(mix(0x07080b, 0x26262a, light)); c.drawPath(path, p)
        stroke(wood, 2.4f); c.drawLines(floatArrayOf(-12f, -101f, 6f, -107f, 12f, -101f, -6f, -108f), p)
        if (lit) {
            flames(c, 0f, -100f, 7f, 34f)
            sparks(c, 0f, -122f, 12, 1900f, 110f)
        } else if (moon > 0.05f) gleam(c, 0f, -100f, 10f, 3f, 0.25f * moon)
    }

    companion object {
        const val EMBER = 0xE0703A; const val GOLD = 0xECD08F; const val FROST = 0x8FC4E0; const val BONE = 0xE8DCC4
    }

    /** Mail: Ratatoskr clinging to the World Tree's limb, head up (the caller flips him to run down), tail plumed. */
    fun squirrel(c: Canvas) {
        val lit = max(light, 0.38f + 0.3f * moon)
        val fur = mix(0x3a1a0c, 0xa9532a, lit); val dk = mix(0x1c0d06, 0x5e2a12, lit)
        val sheen = moon * (0.75f + 0.25f * sin(t * 0.0021f)) * (1 - light)
        if (sheen > 0.03f) glow(c, 6f, -6f, 30f, 0.22f * sheen, GOLD)
        fill(fur)
        path.reset(); path.moveTo(3f, 10f); path.cubicTo(22f, 16f, 26f, -8f, 14f, -20f); path.cubicTo(10f, -25f, 4f, -22f, 6f, -16f)
        path.cubicTo(14f, -8f, 12f, 6f, 2f, 4f); path.close(); c.drawPath(path, p)
        if (sheen > 0.03f) {                                     // the tail's edge, catching the moon gold
            stroke(argb(0.75f * sheen, GOLD), 1.3f)
            path.reset(); path.moveTo(3f, 10f); path.cubicTo(22f, 16f, 26f, -8f, 14f, -20f); path.cubicTo(10f, -25f, 4f, -22f, 6f, -16f)
            c.drawPath(path, p); fill(fur)
        }
        rf.set(-7f, -13f, 7f, 13f); c.drawOval(rf, p)
        c.drawCircle(-1f, -16f, 5.6f, p)
        path.reset(); path.moveTo(-3f, -20f); path.lineTo(-1f, -27f); path.lineTo(2f, -20f); path.close(); c.drawPath(path, p)
        stroke(dk, 2.2f); c.drawLine(-5f, -6f, -10f, -9f, p); c.drawLine(-5f, 8f, -10f, 11f, p)
        if (sheen > 0.03f) { stroke(argb(0.55f * sheen, GOLD), 1.1f); c.drawArc(-6.6f, -21.6f, 4.6f, -10.4f, -70f, 120f, false, p) }
        fill(0xFF0A0604.toInt()); c.drawCircle(-3.2f, -17f, 1.2f, p)
    }

    /** Games: Jörmungandr's coils breaking the water (anchor on the waterline), the tail's tip at the left, his head at
     *  the right: low and sleepy, or risen with a burning eye and a flicking tongue when a game calls. */
    fun serpent(c: Canvas, stir: Boolean = false) {
        val lit = max(light, 0.38f * moon)
        val scale = mix(0x0c2224, 0x2e5f57, lit); val spine = mix(0x061314, 0x1d3a35, lit)
        val silver = moon * (1 - light)
        val humps = arrayOf(floatArrayOf(-430f, 150f, 0f), floatArrayOf(-210f, 170f, 1.3f), floatArrayOf(20f, 160f, 2.6f), floatArrayOf(240f, 140f, 3.9f))
        val band = 24f
        fun hump(x: Float, w: Float, ph: Float, refl: Boolean) {
            val hh = 44f + 6f * sin(t * 0.0012f + ph)
            fill(if (refl) argb(0.22f + 0.1f * silver, scale) else scale)
            path.reset(); path.moveTo(x - w / 2, 0f); path.cubicTo(x - w / 2, -hh * 1.33f, x + w / 2, -hh * 1.33f, x + w / 2, 0f)
            path.lineTo(x + w / 2 - band, 0f)
            path.cubicTo(x + w / 2 - band, -(hh - band) * 1.33f, x - w / 2 + band, -(hh - band) * 1.33f, x - w / 2 + band, 0f)
            path.close(); c.drawPath(path, p)
            if (refl) return
            fill(spine)
            for (k in 1 until 6) {
                val a = (PI * (1 - k / 6.0)).toFloat()
                val cx = x + kotlin.math.cos(a) * w / 2 * 0.98f; val cy = -sin(a) * hh
                path.reset(); path.moveTo(cx - 5f, cy + 2f); path.lineTo(cx + kotlin.math.cos(a) * 3f, cy - 11f); path.lineTo(cx + 5f, cy + 2f); path.close()
                c.drawPath(path, p)
            }
            if (silver > 0.03f) {
                // the moon on his scales: a cool edge along the crest, and glints sliding along it
                stroke(argb(0.35f * silver, 0xCFDBF0), 1.8f)
                path.reset(); path.moveTo(x - w / 2, 0f); path.cubicTo(x - w / 2, -hh * 1.33f, x + w / 2, -hh * 1.33f, x + w / 2, 0f)
                c.drawPath(path, p)
                for (j in 0 until 3) {
                    val u = ((t * 0.00018f + ph * 0.37f + j / 3f) % 1f)
                    val a = (PI * (1 - u)).toFloat()
                    val tw = sin(u * PI.toFloat())
                    glint(c, x + kotlin.math.cos(a) * w / 2, -sin(a) * hh * 1.0f, 4f + 4f * tw, 0.8f * silver * tw)
                }
            }
            stroke(argb(0.35f, mix(0x2a4a5a, 0xdde6f5, light * 0.6f)), 1.6f)
            rf.set(x - w / 2 + band / 2 - band, -2f, x - w / 2 + band / 2 + band, 6f); c.drawOval(rf, p)
            rf.set(x + w / 2 - band / 2 - band, -2f, x + w / 2 - band / 2 + band, 6f); c.drawOval(rf, p)
        }
        c.save(); c.scale(1f, -1f); for (hm in humps) hump(hm[0], hm[1], hm[2], true); c.restore()
        for (hm in humps) hump(hm[0], hm[1], hm[2], false)
        fill(scale)
        val tf = 6f * sin(t * 0.002f)
        path.reset(); path.moveTo(-560f, 0f); path.quadTo(-585f, -30f + tf, -606f, -22f + tf); path.quadTo(-588f, -14f + tf, -572f, 0f); path.close()
        c.drawPath(path, p)
        val hy = -(if (stir) 26f + 4f * sin(t * 0.003f) else 0f)
        path.reset(); path.moveTo(350f, 0f); path.quadTo(352f, hy - 30f, 386f, hy - 34f); path.quadTo(430f, hy - 38f, 452f, hy - 22f)
        path.quadTo(470f, hy - 14f, 468f, hy - 6f); path.quadTo(440f, hy - 4f, 420f, hy - 8f); path.quadTo(404f, hy + 4f, 392f, 0f); path.close()
        fill(scale); c.drawPath(path, p)
        fill(spine); path.reset(); path.moveTo(372f, hy - 32f); path.lineTo(380f, hy - 48f); path.lineTo(390f, hy - 34f); path.close(); c.drawPath(path, p)
        if (stir) {
            glow(c, 420f, hy - 24f, 26f, 0.7f, GOLD)
            fill(0xFFFFC860.toInt()); rf.set(415f, hy - 27f, 425f, hy - 21f); c.drawOval(rf, p)
            if (t % 1400f < 300f) {
                stroke(0xFFB0402A.toInt(), 1.6f)
                c.drawLine(468f, hy - 8f, 486f, hy - 6f, p); c.drawLine(486f, hy - 6f, 492f, hy - 10f, p); c.drawLine(486f, hy - 6f, 492f, hy - 2f, p)
            }
        } else { stroke(spine, 2f); c.drawLine(414f, hy - 24f, 426f, hy - 24f, p) }
    }
}
