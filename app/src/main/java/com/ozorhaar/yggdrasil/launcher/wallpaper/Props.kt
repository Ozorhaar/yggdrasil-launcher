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
    private fun stones(c: Canvas, cx: Float, gap: Float, r: Float) {
        fill(rock); for (k in -2..2) c.drawCircle(cx + k * gap, 0f, r, p)
    }

    /** Personal: a carved runestone on the ridge, its serpent band catching the light. */
    fun runestone(c: Canvas) {
        path.reset(); path.moveTo(-22f, 2f); path.lineTo(-26f, -58f); path.quadTo(-24f, -92f, 0f, -100f)
        path.quadTo(24f, -95f, 26f, -60f); path.lineTo(22f, 2f); path.close()
        fill(rock); c.drawPath(path, p)
        stroke(argb(0.35f + 0.35f * dark, GOLD), 2.6f)
        path.reset(); path.moveTo(-14f, -4f); path.lineTo(-17f, -56f); path.quadTo(-16f, -82f, 0f, -88f)
        path.quadTo(16f, -84f, 17f, -58f); path.lineTo(14f, -10f); path.quadTo(10f, -2f, 4f, -8f)
        c.drawPath(path, p)
        p.strokeWidth = 2f
        val runes = floatArrayOf(-5f, -70f, -5f, -50f, -5f, -70f, 2f, -64f, -5f, -63f, 2f, -57f,
            5f, -44f, 5f, -24f, 5f, -44f, -2f, -36f, -3f, -32f, 6f, -26f)
        c.drawLines(runes, p)
    }

    /** Finance: a cave in the mountain's face, a dragon's gold heaped inside, glinting. */
    fun cave(c: Canvas) {
        path.reset(); path.moveTo(-64f, 0f); path.quadTo(-62f, -72f, 0f, -84f); path.quadTo(60f, -74f, 66f, 0f); path.close()
        fill(mix(0x030406, 0x14131a, light)); c.drawPath(path, p)
        stroke(rock, 7f)
        path.reset(); path.moveTo(-70f, 3f); path.quadTo(-68f, -78f, 0f, -91f); path.quadTo(66f, -80f, 72f, 3f); c.drawPath(path, p)
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
    }

    /** Video: a skald by a little fire on the shore, his lyre on his knee. */
    fun skald(c: Canvas) {
        glow(c, 22f, -10f, 58f, 0.25f + 0.45f * dark)
        stones(c, 22f, 6f, 3.4f)
        flames(c, 22f, -1f, 5.5f, 16f)
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
    fun smithy(c: Canvas) {
        path.reset(); path.moveTo(-100f, 2f); path.lineTo(96f, 2f); path.lineTo(84f, 16f); path.quadTo(0f, 26f, -88f, 14f); path.close()
        fill(rock); c.drawPath(path, p)
        fill(mix(0x141820, 0x5a5658, light)); c.drawRect(-52f, -42f, 52f, 2f, p)
        path.reset(); path.moveTo(-66f, -38f); path.quadTo(-36f, -72f, 0f, -76f); path.quadTo(36f, -72f, 66f, -38f); path.close()
        fill(mix(0x0b0e15, 0x3a3638, light)); c.drawPath(path, p)
        fill(rock); c.drawRect(30f, -88f, 44f, -50f, p)
        val heat = 0.7f + 0.3f * sin(t * 0.004f) * (if (forge) 1.6f else 1f)
        glow(c, -8f, -16f, 46f, (0.25f + 0.45f * dark) * heat)
        fill(argb(0.55f + 0.4f * heat * (0.5f + 0.5f * dark), EMBER)); c.drawRect(-20f, -32f, 4f, 2f, p)
        fill(argb(0.35f * heat, GOLD)); c.drawRect(-16f, -12f, 0f, -2f, p)
        fill(mix(0x07080b, 0x26262a, light)); c.drawRect(-82f, -10f, -72f, 2f, p)
        path.reset(); path.moveTo(-92f, -10f); path.lineTo(-62f, -10f); path.quadTo(-58f, -15f, -64f, -18f); path.lineTo(-90f, -18f); path.close()
        c.drawPath(path, p)
        if (forge) for (i in 0 until 10) {
            val life = 1600f; val age = ((t + i * 397f) % life) / life
            val sx = 37f + sin(i * 2.3f + age * 5f) * 10f + (i % 3 - 1) * age * 14f; val sy = -90f - age * 70f
            fill(argb((1 - age) * 0.95f, if (i % 2 == 1) EMBER else GOLD)); c.drawCircle(sx, sy, (1.8f - age) * 1.4f, p)
        }
    }

    /** Food: a cookfire on the shore with a cauldron hung from a tripod, steam rising. */
    fun cookfire(c: Canvas) {
        glow(c, 0f, -10f, 56f, 0.25f + 0.45f * dark)
        stones(c, 0f, 7f, 3.6f)
        flames(c, 0f, -1f, 6f, 14f)
        stroke(wood, 3f)
        c.drawLines(floatArrayOf(-30f, 2f, 0f, -66f, 0f, -66f, 30f, 2f, 8f, 4f, 0f, -66f), p)
        p.strokeWidth = 1.5f; c.drawLine(0f, -66f, 0f, -44f, p)
        path.reset(); path.moveTo(-19f, -44f); path.lineTo(19f, -44f); path.quadTo(21f, -16f, 0f, -14f); path.quadTo(-21f, -16f, -19f, -44f); path.close()
        fill(mix(0x07080b, 0x2a2a2e, light)); c.drawPath(path, p)
        stroke(mix(0x2a2a2e, 0x7a7670, light), 2.5f); c.drawLine(-21f, -44f, 21f, -44f, p)
        for (i in 0 until 4) {
            val life = 3200f; val age = ((t + i * 800f) % life) / life
            fill(argb(0.22f * (1 - age), 0xE8ECF2)); c.drawCircle(sin(age * 4 + i) * 6, -48f - age * 46f, 4 + age * 9, p)
        }
    }

    private fun knarrShape(c: Canvas) {
        stroke(darkC, 4f); c.drawLine(0f, -12f, 0f, -150f, p)
        p.strokeWidth = 5f; c.drawLine(-62f, -142f, 62f, -142f, p)
        p.strokeWidth = 1.6f; c.drawLines(floatArrayOf(0f, -150f, -112f, -30f, 0f, -150f, 112f, -30f), p)
        val sl = 0.55f + 0.45f * light
        for (i in 0 until 6) { fill(mix(if (i % 2 == 1) BONE else FROST, 0x000000, 1 - sl)); c.drawRect(-56f + i * 18.6f, -139f, -56f + i * 18.6f + 18.8f, -69f, p) }
        fill(0x40000000); c.drawRect(-56f, -139f, 56f, -69f, p)
        fill(mix(0x2a2016, 0x8a6e4a, light))
        for (b in arrayOf(-82f to 26f, -54f to 22f, 48f to 24f, 74f to 22f)) { rf.set(b.first, -30f, b.first + b.second, -12f); c.drawRoundRect(rf, 5f, 5f, p) }
        path.reset(); path.moveTo(-118f, -36f); path.quadTo(-112f, -10f, -96f, -12f); path.lineTo(96f, -12f); path.quadTo(112f, -10f, 118f, -36f)
        path.quadTo(124f, -12f, 104f, 4f); path.quadTo(0f, 18f, -104f, 4f); path.quadTo(-124f, -12f, -118f, -36f); path.close()
        fill(darkC); c.drawPath(path, p)
        stroke(argb(0.25f + 0.2f * light, GOLD), 1.6f)
        path.reset(); path.moveTo(-98f, -8f); path.quadTo(0f, 2f, 98f, -8f); c.drawPath(path, p)
    }

    /** Shop: a knarr, the broad-bellied trader, riding at anchor with bales on deck, and its reflection. */
    fun knarr(c: Canvas) {
        val bob = sin(t * 0.0015f) * 3f; val roll = sin(t * 0.0011f) * 0.012f * 180f / PI.toFloat()
        val layer = c.saveLayerAlpha(-140f, -20f, 140f, 170f, (255 * 0.28f).toInt())
        c.scale(1f, -1f); c.translate(0f, -bob); c.rotate(-roll); knarrShape(c)
        c.restoreToCount(layer)
        c.save(); c.translate(0f, bob); c.rotate(roll); knarrShape(c); c.restore()
        stroke(argb(0.18f, 0xDDE6F5), 1.5f); c.drawLines(floatArrayOf(-120f, 6f, -90f, 6f, 92f, 6f, 124f, 6f), p)
    }

    companion object {
        const val EMBER = 0xE0703A; const val GOLD = 0xECD08F; const val FROST = 0x8FC4E0; const val BONE = 0xE8DCC4
    }
}
