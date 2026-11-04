package com.ozorhaar.yggdrasil.launcher.bifrost

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ozorhaar.yggdrasil.launcher.ui.Ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bifrost (the house VPN) as seen from the launcher. Talks to Bifrost's provider, which only
 * answers apps signed with the Yggdrasil key. If Bifrost isn't installed, everything here is null.
 */
object Bridge {
    const val PACKAGE = "com.ozorhaar.bifrost"
    private val URI: Uri = Uri.parse("content://com.ozorhaar.bifrost.bridge/state")

    data class Info(val mode: String, val full: Boolean, val wanted: Boolean, val detail: String)

    /** Blocking. */
    fun read(ctx: Context): Info? = runCatching {
        ctx.contentResolver.query(URI, null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) null
            else Info(c.getString(0), c.getInt(1) == 1, c.getInt(2) == 1, c.getString(3) ?: "")
        }
    }.getOrNull()

    /** Blocking. "lower" or "raise"; false means Bifrost needs to be opened. */
    fun call(ctx: Context, method: String): Boolean =
        runCatching { ctx.contentResolver.call(URI, method, null, null)?.getBoolean("ok") == true }.getOrDefault(false)

    fun open(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(PACKAGE)?.let {
            ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun observe(ctx: Context, onChange: () -> Unit): ContentObserver? {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        return runCatching { ctx.contentResolver.registerContentObserver(URI, false, obs); obs }.getOrNull()
    }
}

/** Bifrost's current state, kept fresh while the home screen is showing. */
@Composable
fun rememberBridge(): State<Bridge.Info?> {
    val ctx = LocalContext.current
    return produceState<Bridge.Info?>(initialValue = null) {
        suspend fun load() { value = withContext(Dispatchers.IO) { Bridge.read(ctx) } }
        load()
        val obs = Bridge.observe(ctx) { launch { load() } }
        try {
            while (true) { delay(60_000); load() }      // in case Bifrost restarted without saying
        } finally {
            obs?.let { runCatching { ctx.contentResolver.unregisterContentObserver(it) } }
        }
    }
}

/** The bridge mark set into the dock band: three arcs lit by state. */
@Composable
fun BridgeGem(info: Bridge.Info, modifier: Modifier = Modifier) {
    val iron = Ygg.Iron
    val arcs = when (info.mode) {
        "AWAY" -> if (info.full) listOf(Ygg.Ember, Ygg.Gold, Ygg.Frost) else listOf(iron, Ygg.Gold, Ygg.Frost)
        "HOME" -> listOf(iron, iron, Ygg.Frost)
        "UNREACHABLE", "ERROR" -> listOf(Ygg.Ember, iron, iron)
        else -> listOf(iron, iron, iron)
    }
    Box(
        modifier
            .size(width = 46.dp, height = 24.dp)
            .background(Ygg.Night, RoundedCornerShape(12.dp))
            .border(1.dp, Ygg.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp)
    ) {
        Canvas(Modifier.size(width = 32.dp, height = 16.dp)) {
            val stroke = 2.4.dp.toPx()
            val cx = size.width / 2
            val baseY = size.height
            listOf(1f, 0.72f, 0.44f).forEachIndexed { i, f ->
                val r = (size.width / 2 - stroke / 2) * f
                drawArc(
                    arcs[i], 180f, 180f, false,
                    topLeft = Offset(cx - r, baseY - r), size = Size(r * 2, r * 2), style = Stroke(stroke),
                )
            }
        }
    }
}
