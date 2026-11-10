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

/** Under Huginn: voicemail, the old tape sign (two reels and the tape between); ember while a message waits. */
@Composable
fun VoicemailGem(waiting: Boolean, modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = if (waiting) Ygg.Ember else Ygg.Frost
    val w = 1.8.dp.toPx()
    val r = size.height * 0.27f
    val cy = size.height * 0.46f
    val lx = size.width * 0.5f - r * 1.75f; val rx = size.width * 0.5f + r * 1.75f
    drawCircle(c, r, Offset(lx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    drawCircle(c, r, Offset(rx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    drawLine(c, Offset(lx, cy + r), Offset(rx, cy + r), w, StrokeCap.Round)
}

/** Under Mimir: the camera, as the eye Odin gave to Mimir's well. */
@Composable
fun EyeGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = Ygg.Frost
    val w = 1.7.dp.toPx()
    val cx = size.width / 2; val cy = size.height / 2
    val hw = size.width * 0.40f; val hh = size.height * 0.42f
    val lid = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - hw, cy)
        quadraticTo(cx, cy - hh * 2, cx + hw, cy)
        quadraticTo(cx, cy + hh * 2, cx - hw, cy)
        close()
    }
    drawPath(lid, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = StrokeCap.Round))
    drawCircle(c, hh * 0.55f, Offset(cx, cy))
}

/** Under Muninn (Memory): the gallery, a framed picture of peaks under the sun. */
@Composable
fun GalleryGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = Ygg.Frost
    val w = 1.6.dp.toPx()
    val wd = size.width; val h = size.height
    drawRect(c, Offset(wd * 0.2f, h * 0.06f), androidx.compose.ui.geometry.Size(wd * 0.6f, h * 0.88f), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    val peaks = androidx.compose.ui.graphics.Path().apply {
        moveTo(wd * 0.24f, h * 0.84f); lineTo(wd * 0.42f, h * 0.42f); lineTo(wd * 0.54f, h * 0.66f)
        lineTo(wd * 0.64f, h * 0.52f); lineTo(wd * 0.76f, h * 0.84f)
    }
    drawPath(peaks, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = StrokeCap.Round))
    drawCircle(c, h * 0.09f, Offset(wd * 0.66f, h * 0.28f))
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
