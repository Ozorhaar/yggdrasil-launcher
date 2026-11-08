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
