package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.ozorhaar.yggdrasil.launcher.R

/**
 * Carved sounds for the launcher's own actions, cut from the Yggdrasil sound set
 * (yggdrasil-os phone-sounds): a rune-stone knock when a door is barred or the garage shut,
 * a short war-horn note when one opens, a stone tap when a folder opens.
 * They play on the system-sounds volume and stay silent when the phone is on silent or vibrate.
 */
object Carved {
    enum class Sound(val res: Int) {
        SECURE(R.raw.ygg_secure), OPEN(R.raw.ygg_open), FOLDER(R.raw.ygg_folder),
        /** Someone's come home: Heimdall's horn, softened. */
        HOME(R.raw.ygg_home),
        /** Someone's gone out: a short, low note on the war horn. */
        AWAY(R.raw.ygg_away),
    }

    private var pool: SoundPool? = null
    private val ids = HashMap<Sound, Int>()

    private fun ready(ctx: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        Sound.entries.forEach { s -> ids[s] = p.load(ctx.applicationContext, s.res, 1) }
        pool = p
        return p
    }

    /** Loads the sounds ahead of the first tap. */
    fun warm(ctx: Context) { ready(ctx) }

    fun play(ctx: Context, s: Sound) {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val p = ready(ctx)
        val v = if (s == Sound.HOME || s == Sound.AWAY || s == Sound.OPEN) com.ozorhaar.yggdrasil.launcher.data.SteadingConfig.hornVolume(ctx) else 0.9f
        if (v <= 0.01f) return
        ids[s]?.let { p.play(it, v, v, 1, 0, 1f) }
    }
}
