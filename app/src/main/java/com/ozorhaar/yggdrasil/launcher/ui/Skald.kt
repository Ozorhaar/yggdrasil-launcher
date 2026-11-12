package com.ozorhaar.yggdrasil.launcher.ui

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.services.BadgeService

/**
 * Skald: Yggdrasil's own now-playing card, a built-in widget that replaces music apps' widgets.
 * It reads and controls whatever is playing through the notification access the launcher already
 * holds for unread badges (no extra permission, no network).
 */
object Skald {
    /** The layout stores it like any widget, under this provider and this (negative, never an Android widget) id. */
    const val PROVIDER = "ygg:skald"
    const val ID = -1
    const val WIDTH = 5
    const val HEIGHT = 1

    fun isBuiltin(provider: String) = provider.startsWith("ygg:")
}

private class NowPlaying(
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val playing: Boolean,
    val controller: MediaController?,
)

@Composable
fun SkaldCard() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(NowPlaying(null, null, null, false, null)) }
    var allowed by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val listener = ComponentName(ctx, BadgeService::class.java)
        val main = Handler(Looper.getMainLooper())
        var current: MediaController? = null
        lateinit var choose: (List<MediaController>) -> Unit
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) { current?.let { now = read(it) } }
            override fun onPlaybackStateChanged(state: PlaybackState?) { current?.let { now = read(it) } }
            override fun onSessionDestroyed() { choose(runCatching { msm.getActiveSessions(listener) }.getOrDefault(emptyList())) }
        }

        choose = { list ->
            val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            if (best?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(callback)
                current = best
                best?.registerCallback(callback, main)
            }
            now = best?.let { read(it) } ?: NowPlaying(null, null, null, false, null)
        }

        val onChange = MediaSessionManager.OnActiveSessionsChangedListener { choose(it ?: emptyList()) }
        try {
            choose(msm.getActiveSessions(listener))
            msm.addOnActiveSessionsChangedListener(onChange, listener, main)
        } catch (_: SecurityException) {
            allowed = false
        }
        onDispose {
            runCatching { msm.removeOnActiveSessionsChangedListener(onChange) }
            current?.unregisterCallback(callback)
        }
    }

    val shape = CutCornerShape(14.dp)
    Row(
        Modifier.fillMaxSize()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Ygg.Night2, Ygg.Night)), shape)
            .border(1.5.dp, Dusk.gold.copy(alpha = 0.85f), shape)
            .clickable { open(ctx, now.controller) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the album art set in a gold-rimmed shield
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f)
                .clip(CircleShape)
                .background(Ygg.Iron, CircleShape)
                .border(2.5.dp, Dusk.gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val art = now.art
            if (art != null) {
                Image(
                    art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().padding(2.5.dp).clip(CircleShape),
                )
            } else {
                Text("ᛚ", style = TextStyle(color = Dusk.gold, fontSize = 26.sp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            val title = when {
                !allowed -> "Allow notification access"
                now.title != null -> now.title!!
                else -> "The skald is silent"
            }
            androidx.compose.runtime.key(title) {      // a new song rolls again
                Text(
                    title, maxLines = 1,
                    style = TextStyle(color = Ygg.Bone, fontSize = 18.sp, fontFamily = Ygg.Runic),
                    modifier = Modifier.rollIfLong(),
                )
            }
            val sub = if (!allowed) "for the launcher, in Settings" else now.artist ?: if (now.title == null) "Tap play to resume" else ""
            if (sub.isNotEmpty()) androidx.compose.runtime.key(title, sub) {
                Text(
                    sub, maxLines = 1,
                    style = TextStyle(color = Ygg.Ash, fontSize = 14.sp, fontFamily = Ygg.Runic),
                    modifier = Modifier.rollIfLong(),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Control(Glyph.PREV) { transport(ctx, now.controller, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        Control(if (now.playing) Glyph.PAUSE else Glyph.PLAY, big = true) {
            transport(ctx, now.controller, if (now.playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
        }
        Control(Glyph.NEXT) { transport(ctx, now.controller, KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

private fun read(c: MediaController): NowPlaying {
    val m = c.metadata
    val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
    val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
    return NowPlaying(title, artist, art, c.playbackState?.state == PlaybackState.STATE_PLAYING, c)
}

/** Play/pause/skip on the current player, or a media key (resumes the last player) when none is active. */
private fun transport(ctx: Context, c: MediaController?, key: Int) {
    val t = c?.transportControls
    if (t != null) {
        when (key) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> t.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> t.pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
        }
        return
    }
    val am = ctx.getSystemService(AudioManager::class.java)
    val now = SystemClock.uptimeMillis()
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0))
    am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0))
}

/** Opens the player's own screen. */
private fun open(ctx: Context, c: MediaController?) {
    val pi = c?.sessionActivity
    if (pi != null) {
        val opts = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        if (runCatching { pi.send(ctx, 0, null, null, null, null, opts.toBundle()) }.isSuccess) return
    }
    val pkg = c?.packageName ?: return
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { ctx.startActivity(it) } }
}

private enum class Glyph { PREV, PLAY, PAUSE, NEXT }

/** A gold, hand-drawn transport button. */
@Composable
private fun Control(g: Glyph, big: Boolean = false, onClick: () -> Unit) {
    val d = if (big) 44.dp else 36.dp
    Box(
        Modifier.size(d).clip(CircleShape)
            .then(if (big) Modifier.border(1.5.dp, Ygg.Gold, CircleShape) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (big) 18.dp else 16.dp)) {
            val w = size.width; val h = size.height
            fun tri(x0: Float, x1: Float): Path = Path().apply {
                moveTo(x0, 0f); lineTo(x1, h / 2); lineTo(x0, h); close()
            }
            val gold = Ygg.Gold
            when (g) {
                Glyph.PLAY -> drawPath(tri(w * 0.12f, w), gold)
                Glyph.PAUSE -> {
                    drawRect(gold, Offset(w * 0.12f, 0f), androidx.compose.ui.geometry.Size(w * 0.26f, h))
                    drawRect(gold, Offset(w * 0.62f, 0f), androidx.compose.ui.geometry.Size(w * 0.26f, h))
                }
                Glyph.NEXT -> {
                    drawPath(tri(0f, w * 0.78f), gold)
                    drawRect(gold, Offset(w * 0.8f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
                }
                Glyph.PREV -> {
                    drawRect(gold, Offset(0f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
                    drawPath(Path().apply { moveTo(w, 0f); lineTo(w * 0.22f, h / 2); lineTo(w, h); close() }, gold)
                }
            }
        }
    }
}


/** Whether any music app is playing right now: the Saga turns to the Skald. */
internal fun skaldPlaying(ctx: Context): Boolean = runCatching {
    val msm = ctx.getSystemService(MediaSessionManager::class.java)
    msm.getActiveSessions(ComponentName(ctx, BadgeService::class.java)).any { it.playbackState?.state == PlaybackState.STATE_PLAYING }
}.getOrDefault(false)
