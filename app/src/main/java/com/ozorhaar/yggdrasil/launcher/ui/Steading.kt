package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.search.HomeLink
import com.ozorhaar.yggdrasil.launcher.search.HomeLink.Lookout
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens an app by package, if it's installed. */
internal fun Context.openPackage(pkg: String, missing: String) {
    val i = packageManager.getLaunchIntentForPackage(pkg)
    if (i != null) runCatching { startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    else Toast.makeText(this, missing, Toast.LENGTH_SHORT).show()
}

private const val BAMBU = "bbl.intl.bambulab.com"

@Composable
private fun Sheet(border: Color, content: @Composable () -> Unit) {
    val shape = CutCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = 620.dp)
            .background(Ygg.Night2, shape)
            .border(1.5.dp, border, shape)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) { content() }
}

@Composable
private fun Item(title: String, color: Color, line: String? = null) {
    Spacer(Modifier.height(10.dp))
    RuneText(title, 18.sp, color)
    if (line != null) Text(line, color = Ygg.Bone, fontSize = 14.sp)
}

/**
 * Everything afoot around the longhouse at once: a leak, the raven's or the chest's door, Surtur
 * the smoker and the forge (printer), each with what it's doing and a way to look closer.
 */
@Composable
fun SteadingSheet(
    st: HomeLink.Steading?,
    raven: Lookout?,
    chest: Lookout?,
    onLook: (Lookout) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Sheet(if (st?.leaks?.isNotEmpty() == true || st?.smokerAlarm == true) Ygg.Ember else Dusk.gold) {
            RuneText("The steading", 22.sp, Ygg.GoldHi)
            st?.leaks?.takeIf { it.isNotEmpty() }?.let { Item("Water!", Ygg.Ember, "Wet: " + it.joinToString(", ")) }
            raven?.let { l ->
                Item("A raven at the ${l.label.lowercase()}", Ygg.Frost, "Frigate sees someone there.")
                Spacer(Modifier.height(6.dp)); RuneButton("Look") { onClose(); onLook(l) }
            }
            chest?.let { l ->
                Item("A delivery at the ${l.label.lowercase()}", Ygg.Frost, "Frigate sees a package waiting.")
                Spacer(Modifier.height(6.dp)); RuneButton("Look") { onClose(); onLook(l) }
            }
            if (st?.smokerOn == true) {
                val parts = listOfNotNull(
                    st.smokerTemp?.let { "Smoker ${deg(it)}" + (st.smokerTarget?.let { t -> " of ${deg(t)}" } ?: "") },
                    st.probeTemp?.let { "probe ${deg(it)}" + (st.probeTarget?.let { t -> " of ${deg(t)}" } ?: "") },
                    st.pellets?.takeIf { it.isNotBlank() && it != "unknown" }?.let { "pellets $it%" },
                )
                Item(if (st.smokerAlarm) "Surtur calls!" else "Surtur burns", if (st.smokerAlarm) Ygg.Ember else Ygg.GoldHi,
                    (if (st.smokerAlarm) "The probe alarm or cook timer has gone off. " else "") + parts.joinToString(" · "))
            }
            if (st?.printing == true) {
                val layers = if (st.layer != null && st.layers != null) " · layer ${st.layer} of ${st.layers}" else ""
                Item("The forge is hot", Ygg.GoldHi, "Printing: ${st.printProgress}%$layers")
                Spacer(Modifier.height(6.dp)); RuneButton("Open Bambu Handy") { onClose(); ctx.openPackage(BAMBU, "Bambu Handy isn't installed") }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RuneButton("Home Assistant") {
                    onClose()
                    ctx.openPackage("io.homeassistant.companion.android", "Home Assistant app isn't installed")
                }
                RuneButton("Done") { onClose() }
            }
        }
    }
}

private fun deg(v: String) = (v.toFloatOrNull()?.let { Math.round(it).toString() } ?: v) + "°"

/**
 * The morning saga: the first time the home screen shows each morning (5 to 11), a short scroll
 * of the day ahead. "Hear it" has Hafnir read it in his own voice (Piper, through Home Assistant).
 */
@Composable
fun MorningSaga(gates: GateStates, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    LaunchedEffect(Unit) {
        lines = withContext(Dispatchers.IO) { saga(ctx, gates) }
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Sheet(Dusk.gold) {
            val day = android.text.format.DateUtils.formatDateTime(ctx, System.currentTimeMillis(),
                android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_NO_YEAR)
            RuneText("The day's saga", 22.sp, Ygg.GoldHi)
            Text(day, color = Ygg.Ash, fontSize = 13.sp)
            if (lines.isEmpty()) Item("Reading the threads…", Ygg.Ash)
            lines.forEach { (head, body) -> Item(head, if (head.startsWith("Storm")) Ygg.Ember else Ygg.GoldHi, body) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (lines.isNotEmpty() && ctx.ygg.homeLink.configured) RuneButton(if (player != null) "Hush" else "Hear it") {
                    player?.let { it.release(); player = null; return@RuneButton }
                    val text = lines.joinToString(". ") { (h, b) -> "$h. $b" }.replace("°", " degrees")
                    scope.launch {
                        val url = withContext(Dispatchers.IO) { runCatching { ctx.ygg.homeLink.speechUrl(text) }.getOrNull() }
                        if (url == null) { Toast.makeText(ctx, "Hafnir couldn't be reached", Toast.LENGTH_SHORT).show(); return@launch }
                        player = MediaPlayer().apply {
                            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                            setDataSource(url)
                            setOnPreparedListener { it.start() }
                            setOnCompletionListener { it.release(); player = null }
                            setOnErrorListener { mp, _, _ -> mp.release(); player = null; true }
                            prepareAsync()
                        }
                    }
                }
                RuneButton("Onward") { onClose() }
            }
        }
    }
}

/** The saga's lines: (heading, words). Blocking. */
private fun saga(ctx: Context, gates: GateStates): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    Weather.get(ctx)?.let { w -> out += "The sky" to "${w.tempF}°, ${w.words.lowercase()}; a high of ${w.highF}° and a low of ${w.lowF}°." }
    Weather.alerts(ctx).takeIf { it.isNotEmpty() }?.let { a -> out += "Storm warning" to a.joinToString("; ") { it.event } }
    val today = nornsToday(ctx)
    out += "The Norns weave" to (if (today.isEmpty()) "Nothing on the calendar today." else today.take(4).joinToString("; ") { (t, n) -> "$t $n" })
    val watch = ctx.ygg.watch
    if (watch.widgetId >= 0) {
        val on = com.ozorhaar.yggdrasil.launcher.watch.Watch.isOnCall(watch.texts.value, System.currentTimeMillis())
        out += "The watch" to (if (on) "You're on call." else "You're off call.")
    }
    val link = ctx.ygg.homeLink
    if (link.configured) {
        link.solarYesterday()?.let { out += "The sun's gift" to "Yesterday the panels made ${"%.1f".format(it)} kWh." }
        runCatching { link.parcels() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { p ->
            out += "At the door" to "A package waits at the " + p.joinToString(" and ") { it.label.lowercase() } + "."
        }
        gates.steading?.takeIf { it.printing }?.let { out += "The forge" to "The printer is at ${it.printProgress}%." }
    }
    if (Weather.kp(ctx) >= 6f) out += "Northern lights" to "The sky may dance tonight (Kp ${"%.0f".format(Weather.kpCached(ctx))})."
    val (n, verse) = Hovamol.today()
    out += "Hovamol $n" to verse
    return out
}
