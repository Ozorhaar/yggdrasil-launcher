package com.ozorhaar.yggdrasil.launcher.ui

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ozorhaar.yggdrasil.launcher.search.HomeLink.Lookout
import com.ozorhaar.yggdrasil.launcher.wallpaper.FjordScene
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val RAVEN = Color(0xFF05070C)
private val RIM = Ygg.Frost.copy(alpha = 0.45f)

/**
 * One of Odin's ravens keeps watch over the steading. When Frigate sees someone at the side door,
 * in the driveway or at the front door, a raven flies in from the right and perches on the longhouse ridge; a tap on
 * it opens that door's camera peek. When the person has gone, it flies off over the fjord.
 *
 * Drawn above the wallpaper and below the icons, in the same coordinates as the wallpaper's
 * longhouse ([FjordScene.ridgePerch]). It only animates while it's on screen.
 */
@Composable
fun RavenWatch(watching: Lookout?, wallX: () -> Float, onPerch: (Rect?) -> Unit) {
    val flight = remember { Animatable(0f) }       // 0 = away, 1 = on the ridge
    var present by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var clock by remember { mutableLongStateOf(0L) }

    LaunchedEffect(watching != null) {
        if (watching != null) {
            leaving = false
            present = true
            flight.animateTo(1f, tween(1800, easing = LinearOutSlowInEasing))
        } else if (present) {
            leaving = true
            flight.animateTo(0f, tween(1500, easing = FastOutLinearInEasing))
            present = false
            leaving = false
        }
    }
    val perched by remember { derivedStateOf { present && !leaving && flight.value > 0.985f } }

    // frames only while it's here: every frame in flight, ten a second while it sits
    LaunchedEffect(present, perched) {
        if (!present) return@LaunchedEffect
        while (true) {
            if (perched) { delay(100); clock = SystemClock.uptimeMillis() }
            else withFrameMillis { clock = SystemClock.uptimeMillis() }
        }
    }

    // the whole longhouse is the raven's tap target while it's here (not just the bird)
    val here = present && !leaving
    LaunchedEffect(here, size) {
        onPerch(if (here && size.width > 0) steadingRect(size, wallX()) else null)
    }

    Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        if (!present || size.width == 0) return@Canvas
        val w = this.size.width; val h = this.size.height
        val (px, py, s) = FjordScene.ridgePerch(w.toInt(), h.toInt(), wallX())
        val l = 64 * s
        val t = clock / 1000f
        if (perched) {
            // a slow look about: the head turns a little now and then
            val bob = 7f * sin(t * 1.3f) * sin(t * 0.37f + 0.6f)
            perchedRaven(px, py, l, bob)
            return@Canvas
        }
        val u = flight.value
        val (a, b, c) = if (!leaving)
            Triple(Offset(w * 1.1f, py - h * 0.16f), Offset(px + w * 0.12f, py - h * 0.13f), Offset(px, py))
        else
            Triple(Offset(px, py), Offset(px - w * 0.15f, py - h * 0.02f), Offset(-w * 0.15f, py - h * 0.32f))
        val k = if (leaving) 1 - u else u
        val pos = bezier(a, b, c, k)
        // flapping; braking with wings raised over the last stretch before landing
        val beat = if (!leaving && k > 0.88f) 0.85f else sin(t * 2 * PI.toFloat() * 4.5f)
        flyingRaven(pos.x, pos.y - 0.32f * l, l, beat)
    }
}

private fun steadingRect(size: IntSize, wallX: Float): Rect =
    FjordScene.steadingArea(size.width, size.height, wallX).let { Rect(it.left, it.top, it.right, it.bottom) }

private fun bezier(a: Offset, b: Offset, c: Offset, t: Float): Offset {
    val m = 1 - t
    return Offset(m * m * a.x + 2 * m * t * b.x + t * t * c.x, m * m * a.y + 2 * m * t * b.y + t * t * c.y)
}

/** Builds a path from points given in body lengths, relative to an origin (facing left). */
private class Shape(val ox: Float, val oy: Float, val l: Float) {
    fun at(x: Float, y: Float) = Offset(ox + x * l, oy + y * l)
    fun poly(vararg pts: Pair<Float, Float>, turn: ((Pair<Float, Float>) -> Pair<Float, Float>)? = null): Path {
        val p = Path()
        pts.forEachIndexed { i, q0 ->
            val q = turn?.invoke(q0) ?: q0
            val o = at(q.first, q.second)
            if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
        }
        p.close(); return p
    }
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, turn: ((Pair<Float, Float>) -> Pair<Float, Float>)? = null): Path {
        val r = Math.toRadians(deg.toDouble())
        val pts = (0 until 28).map {
            val a = it / 28.0 * 2 * PI
            val x = rx * cos(a); val y = ry * sin(a)
            (cx + (x * cos(r) - y * sin(r))).toFloat() to (cy + (x * sin(r) + y * cos(r))).toFloat()
        }
        return poly(*pts.toTypedArray(), turn = turn)
    }
}

private fun turnAbout(px: Float, py: Float, deg: Float): (Pair<Float, Float>) -> Pair<Float, Float> {
    val r = Math.toRadians(deg.toDouble())
    return { (x, y) ->
        val dx = x - px; val dy = y - py
        (px + dx * cos(r) - dy * sin(r)).toFloat() to (py + dx * sin(r) + dy * cos(r)).toFloat()
    }
}

/** Frost rim first, the black body over it, so only the outer edge of the rim shows. */
private fun DrawScope.carve(l: Float, parts: List<Path>) {
    parts.forEach { drawPath(it, RIM, style = Stroke(0.06f * l, join = StrokeJoin.Round)) }
    parts.forEach { drawPath(it, RAVEN) }
}

/** Sitting on the ridge, feet at (x, y), facing out over the fjord; [bob] turns the head (degrees). */
private fun DrawScope.perchedRaven(x: Float, y: Float, l: Float, bob: Float) {
    val s = Shape(x, y, l)
    val head = turnAbout(-0.18f, -0.46f, bob)
    val parts = listOf(
        s.poly(0.18f to -0.30f, 0.64f to -0.13f, 0.61f to -0.05f, 0.10f to -0.17f),          // tail
        s.ellipse(0.02f, -0.32f, 0.32f, 0.17f, 20f),                                         // body
        s.poly(-0.24f to -0.47f, -0.37f to -0.38f, -0.14f to -0.38f, turn = head),          // hackles
        s.ellipse(-0.30f, -0.56f, 0.13f, 0.12f, 0f, turn = head),                            // head
        s.poly(-0.38f to -0.62f, -0.55f to -0.60f, -0.63f to -0.54f, -0.40f to -0.49f, turn = head), // beak
    )
    // legs and feet on the ridge
    listOf(-0.03f, 0.07f).forEach { lx ->
        drawLine(RAVEN, s.at(lx, -0.17f), s.at(lx - 0.01f, 0f), 0.04f * l, StrokeCap.Round)
        drawLine(RAVEN, s.at(lx - 0.08f, 0f), s.at(lx + 0.05f, 0f), 0.03f * l, StrokeCap.Round)
    }
    carve(l, parts)
    val eye = head(-0.34f to -0.58f)
    drawCircle(Ygg.Frost, 0.026f * l, s.at(eye.first, eye.second))
}

/** On the wing, body centre at (x, y), facing left; [beat] -1..1 is the wing stroke. */
private fun DrawScope.flyingRaven(x: Float, y: Float, l: Float, beat: Float) {
    val s = Shape(x, y, l)
    val up = beat.coerceIn(-0.7f, 1f)
    val parts = listOf(
        s.poly(0.28f to -0.04f, 0.62f to -0.11f, 0.65f to 0.09f, 0.28f to 0.05f),          // wedge tail
        s.ellipse(0f, 0f, 0.36f, 0.12f, 0f),                                                // body
        s.ellipse(-0.38f, -0.04f, 0.11f, 0.1f, 0f),                                         // head
        s.poly(-0.45f to -0.08f, -0.66f to -0.03f, -0.45f to 0.01f),                       // beak
        s.poly(-0.13f to -0.02f, -0.08f to -0.04f - 0.62f * up, 0.05f to -0.06f - 0.86f * up,
            0.27f to -0.05f - 0.72f * up, 0.19f to -0.02f),                                  // wing
    )
    carve(l, parts)
    drawCircle(Ygg.Frost, 0.022f * l, s.at(-0.41f, -0.06f))
}

/**
 * A delivery waiting: when Frigate sees a package at the front or side door, an iron-bound chest
 * stands on the ground by the longhouse door until it's brought in. A tap opens that door's camera.
 */
@Composable
fun ChestAtDoor(look: Lookout?, wallX: () -> Float, onPlaced: (Rect?) -> Unit) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val shown by androidx.compose.animation.core.animateFloatAsState(if (look != null) 1f else 0f, tween(700), label = "chest")
    LaunchedEffect(look != null, size) {
        onPlaced(
            if (look != null && size.width > 0) steadingRect(size, wallX()) else null
        )
    }
    Canvas(Modifier.fillMaxSize().onSizeChanged { size = it }) {
        if (shown <= 0.01f || size.width == 0) return@Canvas
        val (x, y, s) = FjordScene.doorStep(size.width, size.height, wallX())
        val a = shown
        val wood = Color(0xFF5A3A22).copy(alpha = a)
        val woodDark = Color(0xFF3A2414).copy(alpha = a)
        val iron = Ygg.GoldHi.copy(alpha = 0.9f * a)
        val w = 42 * s; val h = 26 * s
        val left = x; val top = y - h
        // body and a rounded lid
        drawRect(woodDark, Offset(left - 1.5f * s, top - 1.5f * s), androidx.compose.ui.geometry.Size(w + 3 * s, h + 1.5f * s))
        drawRect(wood, Offset(left, top), androidx.compose.ui.geometry.Size(w, h))
        drawArc(wood, 180f, 180f, true, Offset(left, top - h * 0.45f), androidx.compose.ui.geometry.Size(w, h * 0.9f))
        drawLine(woodDark, Offset(left, top), Offset(left + w, top), 1.5f * s)
        // iron bands, a lock plate and a frost glint
        listOf(0.18f, 0.82f).forEach { f ->
            drawLine(iron, Offset(left + w * f, top - h * 0.42f), Offset(left + w * f, y), 2.4f * s)
        }
        drawLine(iron, Offset(left, top + h * 0.55f), Offset(left + w, top + h * 0.55f), 2f * s)
        drawRect(iron, Offset(left + w / 2 - 3.5f * s, top - 2 * s), androidx.compose.ui.geometry.Size(7 * s, 9 * s))
        drawCircle(Ygg.Frost.copy(alpha = 0.8f * a), 1.6f * s, Offset(left + w * 0.82f, top - h * 0.3f))
    }
}
