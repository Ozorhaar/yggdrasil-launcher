package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Heimdall's watch: the guardian of the Bifrost keeps an eye on the homelab. Every two minutes
 * while the home screen shows, the phone asks each service whether it answers (any reply counts,
 * even a login page; nothing is sent but the request itself). Gjallarhorn, carved at the foot of
 * the dock's rune band, shows frost while all is well, ember when something has fallen, and dims
 * when none of it can be seen (away from home, where nothing on ozorhaar.com is reachable).
 */
object Heimdall {
    data class Post(val name: String, val up: Boolean)

    enum class Mood { WELL, FALLEN, DARK, UNKNOWN }

    data class Report(val posts: List<Post>, val at: Long) {
        val mood: Mood
            get() {
                val services = posts.filter { it.name != INTERNET }
                return when {
                    posts.isEmpty() -> Mood.UNKNOWN
                    services.none { it.up } -> Mood.DARK
                    posts.all { it.up } -> Mood.WELL
                    else -> Mood.FALLEN
                }
            }
        val fallen: List<String> get() = posts.filter { !it.up }.map { it.name }
    }

    private const val INTERNET = "Internet"

    private fun answers(url: String): Boolean = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4000; c.readTimeout = 5000; c.instanceFollowRedirects = false
            c.requestMethod = "GET"
            c.responseCode in 100..499
        } finally { c.disconnect() }
    }.getOrDefault(false)

    private fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Asks every post at once. Call off the main thread. */
    suspend fun look(ctx: Context): Report = coroutineScope {
        val ha = ctx.ygg.homeLink.url.takeIf { it.startsWith("https://") }
        val posts = listOfNotNull(
            ha?.let { "Home Assistant" to "$it/api/" },
            "Frigate" to "https://frigate.ozorhaar.com/api/version",
            "Proxmox" to "https://prox.ozorhaar.com/",
            "NAS" to "https://nas.ozorhaar.com/",
            "Jellyfin" to "https://media.ozorhaar.com/health",
        )
        val checks = posts.map { (name, url) -> async(Dispatchers.IO) { Post(name, answers(url)) } }
        val internet = Post(INTERNET, online(ctx))
        Report(listOf(internet) + checks.awaitAll(), System.currentTimeMillis())
    }
}

/** Heimdall's latest report, renewed every two minutes while the home screen shows. */
@Composable
fun rememberHeimdall(): Heimdall.Report? {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf<Heimdall.Report?>(null) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                report = withContext(Dispatchers.IO) { Heimdall.look(ctx) }
                delay(2 * 60_000L)
            }
        }
    }
    return report
}

/** The horn's colour for the rune band: frost when well, ember when something's fallen, dim when unseen. */
fun Heimdall.Report?.hornColor(): Color = when (this?.mood) {
    Heimdall.Mood.WELL -> Ygg.Frost
    Heimdall.Mood.FALLEN -> Ygg.Ember
    Heimdall.Mood.DARK -> Ygg.Ash.copy(alpha = 0.5f)
    else -> Ygg.Frost.copy(alpha = 0.4f)
}

/** What Heimdall sees, post by post. */
@Composable
fun HeimdallSheet(report: Heimdall.Report?, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        val shape = CutCornerShape(16.dp)
        Column(
            Modifier.fillMaxWidth()
                .background(Ygg.Night2, shape)
                .border(1.5.dp, report.hornColor(), shape)
                .padding(16.dp),
        ) {
            RuneText("Heimdall's watch", 22.sp, Ygg.GoldHi)
            Spacer(Modifier.height(4.dp))
            val words = when (report?.mood) {
                Heimdall.Mood.WELL -> "All the posts answer."
                Heimdall.Mood.FALLEN -> "Fallen: " + report.fallen.joinToString(", ")
                Heimdall.Mood.DARK -> "The homelab can't be seen from here (away from home?)."
                else -> "Looking…"
            }
            androidx.compose.material3.Text(words, color = Ygg.Bone, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            report?.posts?.forEach { p ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(10.dp).background(if (p.up) Ygg.Frost else Ygg.Ember, CircleShape),
                    )
                    RuneText(p.name, 16.sp, if (p.up) Ygg.Bone else Ygg.Ember)
                    Spacer(Modifier.weight(1f))
                    RuneText(if (p.up) "answers" else "silent", 14.sp, Ygg.Ash)
                }
            }
            report?.let {
                Spacer(Modifier.height(8.dp))
                val t = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.at))
                androidx.compose.material3.Text("Looked at $t", color = Ygg.Ash, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            RuneButton("Done") { onClose() }
        }
    }
}
