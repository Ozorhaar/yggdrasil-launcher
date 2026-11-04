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
            .border(1.dp, Ygg.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
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

/** Launcher settings: three carved slider bars with gold notches. */
@Composable
fun SettingsGem(modifier: Modifier = Modifier) = DockGem(modifier) {
    val w = 1.8.dp.toPx()
    val left = 4.dp.toPx()
    val right = size.width - 4.dp.toPx()
    val notches = listOf(0.30f, 0.68f, 0.45f)
    listOf(0.18f, 0.5f, 0.82f).forEachIndexed { i, f ->
        val y = size.height * f
        drawLine(Ygg.Bone.copy(alpha = 0.75f), Offset(left, y), Offset(right, y), w, StrokeCap.Round)
        val x = left + (right - left) * notches[i]
        drawLine(Ygg.Gold, Offset(x, y - 2.6.dp.toPx()), Offset(x, y + 2.6.dp.toPx()), 2.6.dp.toPx(), StrokeCap.Round)
    }
}
