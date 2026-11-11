package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * A small carved plate set into the top of the dock's rune band (the same shape as the
 * Bifrost mark). Taps are handled by the home screen's gesture code via the plate's bounds.
 */
@Composable
fun DockGem(modifier: Modifier = Modifier, draw: DrawScope.() -> Unit) {
    Box(
        modifier
            .size(width = 46.dp, height = 24.dp)
            .background(Ygg.Night, RoundedCornerShape(12.dp))
            .border(1.dp, Dusk.gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 32.dp, height = 16.dp), onDraw = draw)
    }
}

/** Hafnir: ansuz, the rune of speech and of the god who listens, carved in frost. */
@Composable
fun HafnirGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val w = 2.4.dp.toPx()
    val cx = size.width / 2 - 3.dp.toPx()
    val top = 0.5.dp.toPx()
    val bottom = size.height - 0.5.dp.toPx()
    val twig = 5.dp.toPx()
    drawLine(Ygg.Frost, Offset(cx, top), Offset(cx, bottom), w, StrokeCap.Square)
    drawLine(Ygg.Frost, Offset(cx, top + 1.dp.toPx()), Offset(cx + twig + 1.dp.toPx(), top + twig), w, StrokeCap.Square)
    drawLine(Ygg.Frost, Offset(cx, top + 6.dp.toPx()), Offset(cx + twig + 1.dp.toPx(), top + 6.dp.toPx() + twig - 1.dp.toPx()), w, StrokeCap.Square)
}

/**
 * Launcher settings: a vegvisir-style wayfinder stave in frost (eight staves around a hub, each
 * ending in a crossbar), the old Norse sign for finding your way, carved like Bifrost's and Hafnir's marks.
 */
@Composable
fun SettingsGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = Offset(size.width / 2, size.height / 2)
    val w = 1.6.dp.toPx()
    val hub = 2.4.dp.toPx()
    val reach = size.height / 2 - 0.4.dp.toPx()
    val bar = 1.7.dp.toPx()
    drawCircle(Ygg.Frost, radius = hub, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0 - 90.0)
        val ux = kotlin.math.cos(a).toFloat(); val uy = kotlin.math.sin(a).toFloat()
        val from = Offset(c.x + ux * (hub + w / 2), c.y + uy * (hub + w / 2))
        val to = Offset(c.x + ux * reach, c.y + uy * reach)
        drawLine(Ygg.Frost, from, to, w, StrokeCap.Round)
        // the crossbar at each stave's end
        val bx = -uy * bar; val by = ux * bar
        drawLine(Ygg.Frost, Offset(to.x - bx, to.y - by), Offset(to.x + bx, to.y + by), w, StrokeCap.Round)
    }
}

/**
 * Heimdall's watch: Gjallarhorn, carved like the other marks and set into the foot of the band.
 * Frost while every post answers, ember when one has fallen, dim when none can be seen.
 */
@Composable
fun HeimdallGem(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) = DockGem(modifier) {
    val w = 2.dp.toPx()
    val h = size.height; val wd = size.width
    val horn = androidx.compose.ui.graphics.Path().apply {
        moveTo(wd * 0.12f, h * 0.30f)
        quadraticTo(wd * 0.45f, h * 1.05f, wd * 0.92f, h * 0.18f)
    }
    drawPath(horn, color, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = StrokeCap.Round))
    // the bell, and two bands on the horn
    drawLine(color, Offset(wd * 0.06f, h * 0.05f), Offset(wd * 0.16f, h * 0.62f), w, StrokeCap.Round)
    drawLine(color, Offset(wd * 0.36f, h * 0.48f), Offset(wd * 0.42f, h * 0.85f), w * 0.7f, StrokeCap.Round)
    drawLine(color, Offset(wd * 0.62f, h * 0.45f), Offset(wd * 0.72f, h * 0.78f), w * 0.7f, StrokeCap.Round)
}

// ------------------------------------------------------------------ the marks at the foot of the band

/**
 * Under Huginn: voicemail, as a rune-stick: the whittled stick Norse folk carved a message on and
 * left for someone (fehu, uruz and naudiz cut into it). Ember while a message waits.
 */
@Composable
fun VoicemailGem(waiting: Boolean, modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = if (waiting) Ygg.Ember else Ygg.Frost
    val w = size.width; val h = size.height
    val stick = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.04f, h * 0.40f); lineTo(w * 0.80f, h * 0.30f); lineTo(w * 0.97f, h * 0.52f)
        lineTo(w * 0.80f, h * 0.72f); lineTo(w * 0.04f, h * 0.64f); close()
    }
    drawPath(stick, c)
    val cut = 1.3.dp.toPx()
    fun l(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(Ygg.Night, Offset(w * x1, h * y1), Offset(w * x2, h * y2), cut, StrokeCap.Round)
    l(0.18f, 0.62f, 0.18f, 0.40f); l(0.18f, 0.44f, 0.25f, 0.38f); l(0.18f, 0.52f, 0.25f, 0.46f)   // fehu
    l(0.36f, 0.62f, 0.36f, 0.38f); l(0.36f, 0.38f, 0.45f, 0.46f); l(0.45f, 0.46f, 0.45f, 0.62f)   // uruz
    l(0.58f, 0.62f, 0.58f, 0.36f); l(0.53f, 0.44f, 0.63f, 0.54f)                                    // naudiz
}

/** Under Mimir: the camera, as a holed "Odin stone", the kind you look through to see what's hidden. */
@Composable
fun EyeGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = Ygg.Frost
    val w = size.width; val h = size.height
    val cx = w / 2; val cy = h / 2
    val sw = 1.6.dp.toPx()
    val pts = floatArrayOf(-0.30f, 0.40f, -0.34f, 0.02f, -0.24f, -0.34f, -0.02f, -0.46f, 0.20f, -0.40f, 0.32f, -0.10f, 0.30f, 0.26f, 0.14f, 0.44f)
    val stone = androidx.compose.ui.graphics.Path().apply {
        for (i in pts.indices step 2) {
            val x = cx + pts[i] * w; val y = cy + pts[i + 1] * h * 1.05f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(stone, c, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    val hx = cx - w * 0.03f; val hy = cy - h * 0.04f
    rotate(17f, Offset(hx, hy)) {
        drawOval(c, Offset(hx - h * 0.15f, hy - h * 0.13f), androidx.compose.ui.geometry.Size(h * 0.30f, h * 0.26f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
    }
    drawLine(c, Offset(cx + w * 0.12f, cy + h * 0.22f), Offset(cx + w * 0.20f, cy + h * 0.10f), 1.dp.toPx(), StrokeCap.Round)
}

/** Under Muninn (Memory): the gallery, as a Gotland picture stone: a sun-whirl above a ship. */
@Composable
fun GalleryGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = Ygg.Frost
    val w = size.width; val h = size.height
    val stone = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.27f, h); lineTo(w * 0.29f, h * 0.40f)
        cubicTo(w * 0.30f, h * -0.08f, w * 0.70f, h * -0.08f, w * 0.71f, h * 0.40f); lineTo(w * 0.73f, h)
    }
    drawPath(stone, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    val r = h * 0.09f
    drawArc(c, 17f, 292f, false, Offset(w * 0.5f - r, h * 0.22f - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
    // the ship: a broad checked sail over a hull with curled stems
    drawRect(c, Offset(w * 0.395f, h * 0.40f), androidx.compose.ui.geometry.Size(w * 0.21f, h * 0.27f))
    val lw = 0.7.dp.toPx()
    drawLine(Ygg.Night, Offset(w * 0.395f, h * 0.40f), Offset(w * 0.605f, h * 0.67f), lw)
    drawLine(Ygg.Night, Offset(w * 0.605f, h * 0.40f), Offset(w * 0.395f, h * 0.67f), lw)
    val hull = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.34f, h * 0.66f); quadraticTo(w * 0.36f, h * 0.80f, w * 0.5f, h * 0.80f); quadraticTo(w * 0.64f, h * 0.80f, w * 0.66f, h * 0.66f)
    }
    drawPath(hull, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
    val stems = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.34f, h * 0.66f); quadraticTo(w * 0.33f, h * 0.58f, w * 0.37f, h * 0.60f)
        moveTo(w * 0.66f, h * 0.66f); quadraticTo(w * 0.67f, h * 0.58f, w * 0.63f, h * 0.60f)
    }
    drawPath(stems, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.1.dp.toPx(), cap = StrokeCap.Round))
}

/** Under Brave's helm: a round shield, for Proton Pass (hold it for Proton Authenticator). */
@Composable
fun ShieldGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = Ygg.Frost
    val w = 1.6.dp.toPx()
    val cx = size.width / 2; val cy = size.height / 2
    val r = size.height * 0.48f
    drawCircle(c, r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    for (i in 0 until 4) {
        val a = Math.toRadians(45.0 + i * 90.0)
        val ux = kotlin.math.cos(a).toFloat(); val uy = kotlin.math.sin(a).toFloat()
        drawLine(c, Offset(cx + ux * r * 0.32f, cy + uy * r * 0.32f), Offset(cx + ux * r * 0.92f, cy + uy * r * 0.92f), w * 0.8f, StrokeCap.Round)
    }
    drawCircle(c, r * 0.28f, Offset(cx, cy))
}
