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
internal val RUNES: List<List<FloatArray>> = listOf(
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
 * The battery, read into the dock's rune band: [level] 0..1, [charging], and while charging
 * [sweep] 0..1, where the running light is.
 */
class BandGauge(val level: Float, val charging: Boolean, val sweep: Float)

/**
 * A carved band around a rounded shape: an outer and inner line with runes
 * running between them, tops facing outward like an inscription on a ring.
 * With a [gauge], the runes light gold from left to right as far as the battery's level
 * (ember below 20%), the rest dim, and a frost light runs along the lit runes while charging.
 */
fun Modifier.runeBorder(
    radius: Dp, band: Dp, color: Color,
    gauge: (() -> BandGauge?)? = null,
    /** Gjallarhorn, carved at the foot of the band in this colour (Heimdall's watch); null for none. */
    horn: (() -> Color?)? = null,
): Modifier = drawBehind {
    val g = gauge?.invoke()
    val hornColor = horn?.invoke()
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
    val span = (size.width - 2 * mid).coerceAtLeast(1f)
    val litStroke = Stroke(width = 1.5f * line, cap = StrokeCap.Round)
    for (i in 0 until count) {
        val (pos, angle) = path.at(i * gap)
        // leave the foot of the band clear for the horn
        if (hornColor != null && kotlin.math.abs(angle - 180f) < 1f && kotlin.math.abs(pos.x - size.width / 2) < glyphH * 2.2f) continue
        // with the battery gauge: lit up to the level, dim beyond; a frost light runs while charging
        var c = color.copy(alpha = 0.8f); var dot = color.copy(alpha = 0.7f); var st = stroke
        if (g != null) {
            val x = (pos.x - mid) / span
            if (x <= g.level) {
                val base = if (g.level < 0.2f && !g.charging) Ygg.Ember else Ygg.GoldHi
                val run = if (g.charging) (1f - kotlin.math.abs(x - g.sweep * g.level) / 0.07f).coerceIn(0f, 1f) else 0f
                c = androidx.compose.ui.graphics.lerp(base, Ygg.Frost, run)
                dot = c; st = litStroke
            } else {
                c = color.copy(alpha = 0.3f); dot = color.copy(alpha = 0.25f)
            }
        }
        translate(pos.x, pos.y) {
            rotate(angle, pivot = Offset.Zero) {
                if (i % 6 == 5) {
                    // word break: two carved dots
                    drawCircle(dot, radius = line * 0.9f, center = Offset(0f, -glyphH * 0.2f))
                    drawCircle(dot, radius = line * 0.9f, center = Offset(0f, glyphH * 0.2f))
                } else {
                    drawRune(RUNES[(i - i / 6) % RUNES.size], glyphW, glyphH, c, st)
                }
            }
        }
    }
    if (hornColor != null) drawHorn(size.width / 2, size.height - mid, glyphH, hornColor)
}

/** Gjallarhorn lying along the band's foot, centred on (cx, y), mouth to the left, glyph height [g]. */
private fun DrawScope.drawHorn(cx: Float, y: Float, g: Float, color: Color) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx - 1.7f * g, y - 0.15f * g)
        quadraticTo(cx - 0.2f * g, y + 0.75f * g, cx + 1.6f * g, y - 0.35f * g)
    }
    drawPath(path, color, style = Stroke(width = 0.22f * g, cap = StrokeCap.Round))
    // the bell and two bands of the horn
    drawLine(color, Offset(cx - 1.75f * g, y - 0.5f * g), Offset(cx - 1.6f * g, y + 0.25f * g), 0.2f * g, StrokeCap.Round)
    drawLine(color, Offset(cx - 0.6f * g, y + 0.05f * g), Offset(cx - 0.5f * g, y + 0.45f * g), 0.12f * g, StrokeCap.Round)
    drawLine(color, Offset(cx + 0.6f * g, y - 0.05f * g), Offset(cx + 0.75f * g, y + 0.3f * g), 0.12f * g, StrokeCap.Round)
}

internal fun DrawScope.drawRune(segs: List<FloatArray>, w: Float, h: Float, color: Color, stroke: Stroke) {
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

/** Names and meanings for the runes above, same order (used for folder emblems). */
internal val RUNE_NAMES = listOf(
    "Fehu · wealth", "Uruz · strength", "Thurisaz · thorn", "Ansuz · speech", "Raido · journey",
    "Kenaz · torch, craft", "Gebo · gift", "Wunjo · joy", "Hagalaz · hail", "Naudiz · need",
    "Isa · ice", "Eihwaz · yew, guard", "Perthro · lot-cup, chance", "Berkano · birch, growth", "Ehwaz · horse",
    "Mannaz · people", "Laguz · water", "Ingwaz · hearth, home", "Dagaz · day",
)

/** Folder emblem settings: [EMBLEM_AUTO] picks from the folder's name, [EMBLEM_NONE] hides it. */
const val EMBLEM_AUTO = -2
const val EMBLEM_NONE = -1

private val EMBLEM_WORDS: List<Pair<Int, List<String>>> = listOf(
    0 to listOf("money", "bank", "financ", "pay", "wallet", "crypto", "budget", "stock", "invest"),
    1 to listOf("fit", "gym", "health", "workout", "sport", "run"),
    11 to listOf("secur", "vpn", "password", "auth", "privacy", "guard", "camera", "nvr"),
    3 to listOf("chat", "messag", "social", "talk", "mail", "comm", "news"),
    4 to listOf("travel", "map", "nav", "car", "drive", "trip", "flight", "transit"),
    5 to listOf("tool", "util", "work", "office", "create", "photo", "edit", "dev", "code", "make"),
    6 to listOf("shop", "store", "gift", "buy", "amazon", "deal"),
    7 to listOf("game", "play", "fun", "music", "media", "video", "tv", "stream", "watch", "listen"),
    8 to listOf("weather", "storm"),
    9 to listOf("system", "setting", "need", "admin"),
    13 to listOf("grow", "garden", "learn", "book", "read", "school", "kid", "family"),
    15 to listOf("people", "contact", "friend"),
    16 to listOf("water", "sea", "fish", "boat", "drink", "food", "cook", "recipe"),
    17 to listOf("home", "house", "hearth", "smart", "hass", "assistant", "longhouse"),
    18 to listOf("day", "calendar", "plan", "task", "todo", "note", "time", "clock"),
)

/** The rune a folder shows: its own choice, or one that suits its name. */
fun emblemFor(choice: Int, name: String, id: String): Int {
    if (choice >= 0) return choice.coerceAtMost(RUNES.size - 1)
    if (choice == EMBLEM_NONE) return EMBLEM_NONE
    val n = name.lowercase()
    EMBLEM_WORDS.firstOrNull { (_, words) -> words.any { it in n } }?.let { return it.first }
    return Math.floorMod(id.hashCode(), RUNES.size)
}


/** The phone's battery (level 0..1, charging), kept up to date from the system's battery broadcast. */
@androidx.compose.runtime.Composable
fun rememberBattery(): androidx.compose.runtime.State<Pair<Float, Boolean>> {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1f to false) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        fun read(i: android.content.Intent?) {
            if (i == null) return
            val level = i.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            val plugged = i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0
            if (level >= 0 && scale > 0) state.value = (level / scale.toFloat()) to plugged
        }
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: android.content.Intent) = read(i)
        }
        read(ctx.registerReceiver(rx, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)))
        onDispose { runCatching { ctx.unregisterReceiver(rx) } }
    }
    return state
}
