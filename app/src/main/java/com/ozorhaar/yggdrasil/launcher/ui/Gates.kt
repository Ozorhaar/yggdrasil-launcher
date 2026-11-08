package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ozorhaar.yggdrasil.launcher.search.HomeLink.Gate
import com.ozorhaar.yggdrasil.launcher.security.Guard
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the dock knows about the two doors (null = not read yet / HA unreachable). */
class GateStates {
    val state = mutableStateMapOf<Gate, String>()
    fun of(g: Gate): String? = state[g]
}

private fun Context.readGates(into: GateStates) {
    val link = ygg.homeLink
    if (!link.configured) return
    Gate.entries.forEach { g -> runCatching { link.gateState(g) }.getOrNull()?.let { into.state[g] = it } }
}

/** The doors' states, read when the home screen shows and every 30 seconds while it does. */
@Composable
fun rememberGates(): GateStates {
    val ctx = LocalContext.current
    val gates = remember { GateStates() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                withContext(Dispatchers.IO) { ctx.readGates(gates) }
                delay(30_000L)
            }
        }
    }
    return gates
}

private fun isSecure(s: String?) = s == "locked" || s == "closed"
private fun isOpen(s: String?) = s == "unlocked" || s == "open"

/**
 * A tap on a door's mark: locking or closing goes straight through; unlocking or opening asks for
 * your fingerprint first, every time.
 */
fun gateTap(ctx: Context, scope: CoroutineScope, gates: GateStates, g: Gate) {
    val link = ctx.ygg.homeLink
    if (!link.configured) {
        Toast.makeText(ctx, "Connect Home Assistant in launcher settings first", Toast.LENGTH_LONG).show(); return
    }
    val now = gates.of(g)
    fun go(open: Boolean) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { link.setGate(g, open) }.isSuccess }
            val verb = when {
                g == Gate.SIDE_DOOR && open -> "unlocking"
                g == Gate.SIDE_DOOR -> "locking"
                open -> "opening"
                else -> "closing"
            }
            Toast.makeText(ctx, if (ok) "${g.label}: $verb" else "Couldn't reach Home Assistant", Toast.LENGTH_SHORT).show()
            // follow the door until it settles
            repeat(8) {
                delay(1500L)
                withContext(Dispatchers.IO) { ctx.readGates(gates) }
                val s = gates.of(g)
                if (isSecure(s) == !open && (isSecure(s) || isOpen(s))) return@launch
            }
        }
    }
    when {
        isSecure(now) -> {
            val title = if (g == Gate.SIDE_DOOR) "Unlock the side door" else "Open the garage door"
            Guard.check(ctx, title, fresh = true) { go(open = true) }
        }
        isOpen(now) -> go(open = false)
        else -> {
            Toast.makeText(ctx, "${g.label}: ${now ?: "checking…"}", Toast.LENGTH_SHORT).show()
            scope.launch { withContext(Dispatchers.IO) { ctx.readGates(gates) } }
        }
    }
}

/** The side door's mark: a padlock, frost when locked, ember and open when unlocked. */
@Composable
fun SideDoorGem(state: String?, modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = gateColor(state)
    val w = size.width; val h = size.height
    val bw = h * 0.78f; val bh = h * 0.52f
    val left = w / 2 - bw / 2; val top = h - bh
    drawRoundRect(c, Offset(left, top), Size(bw, bh), CornerRadius(h * 0.08f))
    drawCircle(Ygg.Night, h * 0.08f, Offset(w / 2, top + bh * 0.45f))
    val sw = 1.6.dp.toPx()
    val r = bw * 0.3f
    // the shackle: seated when locked, lifted and swung aside when unlocked
    val lift = if (isOpen(state)) h * 0.16f else 0f
    val cx = if (isOpen(state)) w / 2 + r * 0.5f else w / 2
    val p = Path().apply {
        moveTo(cx - r, top - lift + (if (isOpen(state)) -h * 0.02f else 0f) + 0.5f)
        lineTo(cx - r, top - r * 0.7f - lift)
        arcTo(androidx.compose.ui.geometry.Rect(cx - r, top - r * 1.7f - lift, cx + r, top + r * 0.3f - lift), 180f, 180f, false)
        lineTo(cx + r, top - lift + 0.5f)
    }
    drawPath(p, c, style = Stroke(sw, cap = StrokeCap.Round))
}

/** The garage's mark: the door in its frame, frost when closed, ember and raised when open. */
@Composable
fun GarageGem(state: String?, modifier: Modifier = Modifier) = DockGem(modifier) {
    val c = gateColor(state)
    val w = size.width; val h = size.height
    val sw = 1.5.dp.toPx()
    val half = h * 0.62f
    val l = w / 2 - half; val r = w / 2 + half
    val eave = h * 0.36f
    // roof and walls
    val house = Path().apply {
        moveTo(l, h); lineTo(l, eave); lineTo(w / 2, 0.5f); lineTo(r, eave); lineTo(r, h)
    }
    drawPath(house, c, style = Stroke(sw, cap = StrokeCap.Round))
    // the door's slats: all four when closed, one tucked up top when open
    val slats = if (isOpen(state) || state == "opening") 1 else 4
    val dl = l + h * 0.16f; val dr = r - h * 0.16f
    for (i in 0 until slats) {
        val y = eave + h * 0.1f + i * h * 0.14f
        drawLine(c, Offset(dl, y), Offset(dr, y), sw, StrokeCap.Round)
    }
}

private fun gateColor(state: String?): Color = when {
    isSecure(state) -> Ygg.Frost
    isOpen(state) -> Ygg.Ember
    state == null -> Ygg.Frost.copy(alpha = 0.45f)
    else -> Ygg.Ash // moving, jammed or unknown
}
