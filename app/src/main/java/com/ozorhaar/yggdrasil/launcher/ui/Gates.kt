package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import com.ozorhaar.yggdrasil.launcher.search.HomeLink.Lookout
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
    /** When each door was first seen in its current state (for "open more than 10 minutes"). */
    private val since = HashMap<Gate, Long>()
    /** Bumped on every read, so the night-watch check re-runs. */
    var readAt by androidx.compose.runtime.mutableLongStateOf(0L)

    fun of(g: Gate): String? = state[g]

    /** Doors where Frigate sees someone right now (the raven on the roof). */
    val seen = mutableStateMapOf<Lookout, Boolean>()

    /** The smoker, the printer and the leak sensors (null until first read). */
    var steading by androidx.compose.runtime.mutableStateOf<com.ozorhaar.yggdrasil.launcher.search.HomeLink.Steading?>(null)

    /** Doors where Frigate sees a package waiting (the chest at the longhouse door). */
    val parcel = mutableStateMapOf<Lookout, Boolean>()

    /** The door whose package the chest stands for: the front first, then the side; null when none. */
    fun parcelAt(): Lookout? = listOf(Lookout.FRONT, Lookout.SIDE).firstOrNull { parcel[it] == true }

    /** The door the raven watches: the side door first, then the driveway, then the front; null when no one's about. */
    fun watching(): Lookout? = Lookout.entries.firstOrNull { seen[it] == true }

    fun put(g: Gate, s: String) {
        if (state[g] != s) since[g] = System.currentTimeMillis()
        state[g] = s
    }

    /**
     * Night watch: the garage open more than 10 minutes, or the side door unlocked between
     * 10 pm and 6 am. The mark pulses ember while this holds.
     */
    fun alert(g: Gate, now: Long = System.currentTimeMillis()): Boolean {
        val s = state[g] ?: return false
        if (!isOpen(s)) return false
        return when (g) {
            Gate.GARAGE -> now - (since[g] ?: now) >= 10 * 60 * 1000L
            Gate.SIDE_DOOR -> java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY).let { it >= 22 || it < 6 }
        }
    }
}

private fun Context.readGates(into: GateStates) {
    val link = ygg.homeLink
    if (!link.configured) return
    Gate.entries.forEach { g -> runCatching { link.gateState(g) }.getOrNull()?.let { into.put(g, it) } }
    runCatching { link.parcels() }.getOrNull()?.let { got -> Lookout.entries.forEach { into.parcel[it] = it in got } }
    runCatching { link.steading() }.getOrNull()?.let { into.steading = it }
    into.readAt = System.currentTimeMillis()
}

private fun Context.readWatchers(into: GateStates) {
    val link = ygg.homeLink
    if (!link.configured) return
    Lookout.entries.forEach { l -> runCatching { link.seen(l) }.getOrNull()?.let { into.seen[l] = it } }
}

/**
 * The doors' states, read when the home screen shows and every 30 seconds while it does; and
 * Frigate's person sensors at those doors every 6 seconds, for the raven.
 */
@Composable
fun rememberGates(): GateStates {
    val ctx = LocalContext.current
    val gates = remember { GateStates() }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                while (true) {
                    withContext(Dispatchers.IO) { ctx.readWatchers(gates) }
                    delay(6_000L)
                }
            }
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
            if (ok) Carved.play(ctx, if (open) Carved.Sound.OPEN else Carved.Sound.SECURE)
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

/** The side door's mark: an arched plank door, barred across when locked; unbarred, ring showing, when not. */
@Composable
fun SideDoorGem(state: String?, alert: Boolean = false, modifier: Modifier = Modifier) {
    val pulse = pulseAlpha(alert)
    DockGem(modifier) {
    val base = gateColor(state)
    val c = base.copy(alpha = base.alpha * pulse)
    val w = size.width; val h = size.height
    val sw = 1.4.dp.toPx()
    val dw = h * 0.62f; val l = w / 2 - dw / 2; val r = w / 2 + dw / 2; val top = h * 0.06f; val ar = dw / 2
    val door = Path().apply {
        moveTo(l, h); lineTo(l, top + ar)
        arcTo(androidx.compose.ui.geometry.Rect(l, top, r, top + dw), 180f, 180f, false)
        lineTo(r, h); close()
    }
    drawPath(door, c, style = Stroke(sw))
    for (f in listOf(1f / 3f, 2f / 3f)) {
        val x = l + dw * f
        drawLine(c, Offset(x, top + ar * 0.35f), Offset(x, h), sw * 0.6f)
    }
    if (isOpen(state)) {
        // unbarred: just the iron ring
        drawCircle(c, h * 0.09f, Offset(r - dw * 0.18f, h * 0.62f), style = Stroke(sw * 0.7f))
    } else {
        // the beam across the door, in its two brackets
        val y = h * 0.58f
        drawLine(c, Offset(l - h * 0.28f, y), Offset(r + h * 0.28f, y), sw * 1.5f, StrokeCap.Round)
        for (x in listOf(l - h * 0.12f, r + h * 0.12f))
            drawLine(c, Offset(x, y - h * 0.14f), Offset(x, y + h * 0.14f), sw * 0.8f, StrokeCap.Round)
    }
}
}

/** The garage's mark: a boathouse (naust) with a horned gable; plank doors when closed, a dragon prow when open. */
@Composable
fun GarageGem(state: String?, alert: Boolean = false, modifier: Modifier = Modifier) {
    val pulse = pulseAlpha(alert)
    DockGem(modifier) {
    val base = gateColor(state)
    val c = base.copy(alpha = base.alpha * pulse)
    val w = size.width; val h = size.height
    val sw = 1.4.dp.toPx()
    val half = h * 0.62f; val l = w / 2 - half; val r = w / 2 + half
    val eave = h * 0.42f; val peak = h * 0.12f
    val house = Path().apply { moveTo(l, h); lineTo(l, eave); lineTo(w / 2, peak); lineTo(r, eave); lineTo(r, h) }
    drawPath(house, c, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // crossed bargeboards at the peak
    drawLine(c, Offset(w / 2 - h * 0.16f, peak + h * 0.1f), Offset(w / 2 + h * 0.12f, -h * 0.06f), sw, StrokeCap.Round)
    drawLine(c, Offset(w / 2 + h * 0.16f, peak + h * 0.1f), Offset(w / 2 - h * 0.12f, -h * 0.06f), sw, StrokeCap.Round)
    val dl = l + h * 0.18f; val dr = r - h * 0.18f; val dt = eave + h * 0.06f
    if (isOpen(state) || state == "opening") {
        drawLine(c, Offset(dl, dt), Offset(dr, dt), sw * 0.9f, StrokeCap.Round)
        val px = w / 2 - h * 0.05f
        val prow = Path().apply {
            moveTo(px, h); lineTo(px, h * 0.66f)
            cubicTo(px, h * 0.54f, px + h * 0.2f, h * 0.52f, px + h * 0.2f, h * 0.64f)
            cubicTo(px + h * 0.2f, h * 0.72f, px + h * 0.09f, h * 0.72f, px + h * 0.09f, h * 0.66f)
        }
        drawPath(prow, c, style = Stroke(sw * 0.9f, cap = StrokeCap.Round))
    } else {
        for (i in 0 until 5) {
            val x = dl + (dr - dl) * i / 4f
            drawLine(c, Offset(x, dt), Offset(x, h), sw * 0.75f, StrokeCap.Round)
        }
    }
}
}

private fun gateColor(state: String?): Color = when {
    isSecure(state) -> Ygg.Frost
    isOpen(state) -> Ygg.Ember
    state == null -> Ygg.Frost.copy(alpha = 0.45f)
    else -> Ygg.Ash // moving, jammed or unknown
}

/** 1 when calm; a slow ember breath (0.35..1) while the night watch has something to say. */
@Composable
private fun pulseAlpha(alert: Boolean): Float {
    if (!alert) return 1f
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "watch")
    val a by t.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween<Float>(1100), androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "watch-alpha",
    )
    return a
}

/**
 * Look before you open: a long press on a door's mark shows a live still from that door's camera
 * (refreshed every two seconds), with the same button the mark has: open behind your fingerprint,
 * or secure straight away.
 */
@Composable
fun CameraPeek(look: Lookout, gates: GateStates, onClose: () -> Unit) {
    val g = look.gate
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var frame by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var trouble by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(look) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val bytes = withContext(Dispatchers.IO) { runCatching { ctx.ygg.homeLink.snapshot(look.camera) } }
                bytes.onSuccess { b ->
                    android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.let { frame = it.asImageBitmap(); trouble = null }
                }.onFailure { trouble = "Couldn't reach the camera" }
                withContext(Dispatchers.IO) { ctx.readGates(gates) }
                delay(2000L)
            }
        }
    }
    val state = g?.let { gates.of(it) }
    val action = if (g == null) null else when {
        isSecure(state) -> if (g == Gate.SIDE_DOOR) "Unlock" else "Open"
        isOpen(state) -> if (g == Gate.SIDE_DOOR) "Lock" else "Close"
        else -> null
    }
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        val shape = androidx.compose.foundation.shape.CutCornerShape(16.dp)
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.fillMaxWidth().padding(16.dp)
                .background(Ygg.Night2, shape)
                .border(1.5.dp, Dusk.gold, shape)
                .clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .padding(14.dp),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                RuneText(look.label, 22.sp, Ygg.GoldHi)
                androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.weight(1f))
                if (g != null) RuneText(state ?: "…", 16.sp, gateColor(state))
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(10.dp))
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(androidx.compose.foundation.shape.CutCornerShape(10.dp))
                    .background(Ygg.Night, androidx.compose.foundation.shape.CutCornerShape(10.dp))
                    .border(1.dp, Dusk.gold.copy(alpha = 0.6f), androidx.compose.foundation.shape.CutCornerShape(10.dp)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                val f = frame
                if (f != null) androidx.compose.foundation.Image(
                    f, contentDescription = "${look.label} camera",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                ) else RuneText(trouble ?: "Opening the camera…", 15.sp, Ygg.Ash)
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(12.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                if (action != null && g != null) RuneButton(action) { gateTap(ctx, scope, gates, g) }
                RuneButton("Done") { onClose() }
            }
        }
    }
}
