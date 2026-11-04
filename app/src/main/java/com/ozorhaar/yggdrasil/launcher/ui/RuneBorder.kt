package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Elder Futhark runes as carved strokes, in a unit box (x 0..0.6, y 0 = top .. 1 = bottom).
 * Sowilo, tiwaz, algiz and othala are left out on purpose: they're the ones that
 * hate groups have taken over. Jera too, since at this size it reads like sowilo.
 */
private val RUNES: List<List<FloatArray>> = listOf(
    /* fehu     */ listOf(f(0f, 0f, 0f, 1f), f(0f, .35f, .5f, .05f), f(0f, .62f, .5f, .32f)),
    /* uruz     */ listOf(f(0f, 0f, 0f, 1f), f(0f, 0f, .5f, .3f), f(.5f, .3f, .5f, 1f)),
    /* thurisaz */ listOf(f(0f, 0f, 0f, 1f), f(0f, .28f, .45f, .5f), f(.45f, .5f, 0f, .72f)),
    /* ansuz    */ listOf(f(0f, 0f, 0f, 1f), f(0f, .05f, .45f, .3f), f(0f, .35f, .45f, .6f)),
    /* raido    */ listOf(f(0f, 0f, 0f, 1f), f(0f, 0f, .45f, .25f), f(.45f, .25f, 0f, .5f), f(0f, .5f, .45f, 1f)),
    /* kenaz    */ listOf(f(.45f, .2f, .05f, .5f), f(.05f, .5f, .45f, .8f)),
    /* gebo     */ listOf(f(0f, .1f, .55f, .9f), f(.55f, .1f, 0f, .9f)),
    /* wunjo    */ listOf(f(0f, 0f, 0f, 1f), f(0f, 0f, .45f, .2f), f(.45f, .2f, 0f, .4f)),
    /* hagalaz  */ listOf(f(0f, 0f, 0f, 1f), f(.5f, 0f, .5f, 1f), f(0f, .35f, .5f, .65f)),
    /* naudiz   */ listOf(f(.27f, 0f, .27f, 1f), f(.05f, .35f, .5f, .65f)),
    /* isa      */ listOf(f(.27f, 0f, .27f, 1f)),
    /* eihwaz   */ listOf(f(.27f, 0f, .27f, 1f), f(.27f, 0f, .52f, .2f), f(.27f, 1f, .02f, .8f)),
    /* perthro  */ listOf(f(0f, 0f, 0f, 1f), f(0f, 0f, .45f, .25f), f(.45f, .25f, .45f, .08f), f(0f, 1f, .45f, .75f), f(.45f, .75f, .45f, .92f)),
    /* berkano  */ listOf(f(0f, 0f, 0f, 1f), f(0f, 0f, .45f, .25f), f(.45f, .25f, 0f, .5f), f(0f, .5f, .45f, .75f), f(.45f, .75f, 0f, 1f)),
    /* ehwaz    */ listOf(f(0f, 0f, 0f, 1f), f(.55f, 0f, .55f, 1f), f(0f, 0f, .27f, .3f), f(.27f, .3f, .55f, 0f)),
    /* mannaz   */ listOf(f(0f, 0f, 0f, 1f), f(.55f, 0f, .55f, 1f), f(0f, 0f, .55f, .42f), f(.55f, 0f, 0f, .42f)),
    /* laguz    */ listOf(f(0f, 0f, 0f, 1f), f(0f, 0f, .45f, .3f)),
    /* ingwaz   */ listOf(f(.27f, .2f, .54f, .5f), f(.54f, .5f, .27f, .8f), f(.27f, .8f, 0f, .5f), f(0f, .5f, .27f, .2f)),
    /* dagaz    */ listOf(f(0f, 0f, 0f, 1f), f(.6f, 0f, .6f, 1f), f(0f, 0f, .6f, 1f), f(.6f, 0f, 0f, 1f)),
)

private fun f(x1: Float, y1: Float, x2: Float, y2: Float) = floatArrayOf(x1, y1, x2, y2)

/** A point on a rounded rectangle's outline, walked clockwise, with the direction of travel (degrees). */
private class Outline(val l: Float, val t: Float, val r: Float, val b: Float, radius: Float) {
    val rad = min(radius, min(r - l, b - t) / 2f)
    private val w = r - l - 2 * rad
    private val h = b - t - 2 * rad
    private val arc = (PI / 2 * rad).toFloat()
    val length = 2 * w + 2 * h + 4 * arc

    fun at(dist: Float): Pair<Offset, Float> {
        var d = ((dist % length) + length) % length
        fun arcAt(cx: Float, cy: Float, startDeg: Float, s: Float): Pair<Offset, Float> {
            val a = (startDeg + s / arc * 90f) * (PI / 180).toFloat()
            return Offset(cx + rad * cos(a), cy + rad * sin(a)) to (startDeg + s / arc * 90f + 90f)
        }
        if (d < w) return Offset(l + rad + d, t) to 0f; d -= w
        if (d < arc) return arcAt(r - rad, t + rad, -90f, d); d -= arc
        if (d < h) return Offset(r, t + rad + d) to 90f; d -= h
        if (d < arc) return arcAt(r - rad, b - rad, 0f, d); d -= arc
        if (d < w) return Offset(r - rad - d, b) to 180f; d -= w
        if (d < arc) return arcAt(l + rad, b - rad, 90f, d); d -= arc
        if (d < h) return Offset(l, b - rad - d) to 270f; d -= h
        return arcAt(l + rad, t + rad, 180f, d)
    }
}

/**
 * A carved band around a rounded shape: an outer and inner line with runes
 * running between them, tops facing outward like an inscription on a ring.
 */
fun Modifier.runeBorder(radius: Dp, band: Dp, color: Color): Modifier = drawBehind {
    val bandPx = band.toPx()
    val line = 1.dp(this)
    val outer = line
    val inner = bandPx - line
    val rr = radius.toPx()
    // the two lines
    fun ring(inset: Float, alpha: Float) = drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius((rr - inset).coerceAtLeast(0f)),
        style = Stroke(width = line),
    )
    ring(outer, 0.55f)
    ring(inner, 0.4f)

    // runes along the middle of the band
    val mid = bandPx / 2f
    val path = Outline(mid, mid, size.width - mid, size.height - mid, rr - mid)
    val glyphH = bandPx - 4.5f * line
    val glyphW = glyphH * 0.6f
    val step = glyphW + glyphH * 0.75f
    val count = (path.length / step).toInt().coerceAtLeast(1)
    val gap = path.length / count
    val stroke = Stroke(width = 1.1f * line, cap = StrokeCap.Round)
    for (i in 0 until count) {
        val (pos, angle) = path.at(i * gap)
        translate(pos.x, pos.y) {
            rotate(angle, pivot = Offset.Zero) {
                if (i % 6 == 5) {
                    // word break: two carved dots
                    drawCircle(color.copy(alpha = 0.7f), radius = line * 0.9f, center = Offset(0f, -glyphH * 0.2f))
                    drawCircle(color.copy(alpha = 0.7f), radius = line * 0.9f, center = Offset(0f, glyphH * 0.2f))
                } else {
                    drawRune(RUNES[(i - i / 6) % RUNES.size], glyphW, glyphH, color.copy(alpha = 0.8f), stroke)
                }
            }
        }
    }
}

private fun DrawScope.drawRune(segs: List<FloatArray>, w: Float, h: Float, color: Color, stroke: Stroke) {
    val ox = -w / 2f
    val oy = -h / 2f
    val sx = w / 0.6f
    segs.forEach { s ->
        drawLine(
            color,
            Offset(ox + s[0] * sx, oy + s[1] * h),
            Offset(ox + s[2] * sx, oy + s[3] * h),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
    }
}

private fun Int.dp(scope: DrawScope): Float = with(scope) { this@dp.toFloat() * density }
