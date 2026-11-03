package com.ozorhaar.yggdrasil.launcher

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.ozorhaar.yggdrasil.launcher.data.AppRepository
import com.ozorhaar.yggdrasil.launcher.data.LayoutStore
import com.ozorhaar.yggdrasil.launcher.services.BadgeService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class YggApp : Application() {
    lateinit var repo: AppRepository
        private set
    lateinit var layout: LayoutStore
        private set

    /** Fires when the Home button is pressed while the launcher is already showing. */
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(this)
        layout = LayoutStore(this)
        repo.start()
        if (!layout.initialized) seedDefaultLayout()
        seedWallpapersOnce()
    }

    /** First run: Fjord on the home screen, World Tree on the lock screen. */
    private fun seedWallpapersOnce() {
        val prefs = getSharedPreferences("yggdrasil_layout", MODE_PRIVATE)
        if (prefs.getBoolean("wallpapers_seeded", false)) return
        prefs.edit().putBoolean("wallpapers_seeded", true).apply()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            applyYggWallpaper(YggWallpaper.FJORD, WallTarget.HOME)
            applyYggWallpaper(YggWallpaper.WORLD_TREE, WallTarget.LOCK)
        }
    }

    /** First run: phone, messages, browser, camera in the dock; a few staples on page one. */
    private fun seedDefaultLayout() {
        fun defaultFor(intent: Intent): String? {
            val ri = packageManager.resolveActivity(intent, 0) ?: return null
            val pkg = ri.activityInfo?.packageName ?: return null
            if (pkg == "android") return null // the chooser, i.e. no default
            return repo.firstForPackage(pkg)?.key
        }
        val dock = listOfNotNull(
            defaultFor(Intent(Intent.ACTION_DIAL)),
            defaultFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            defaultFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))),
            defaultFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        ).distinct()
        val staples = listOf(
            "com.android.settings", "com.android.vending", "com.sec.android.gallery3d",
            "com.google.android.apps.photos", "com.samsung.android.calendar",
            "com.google.android.calendar", "com.google.android.youtube", "com.google.android.gm",
            "com.valvesoftware.android.steam.community", "io.homeassistant.companion.android",
        ).mapNotNull { repo.firstForPackage(it)?.key }.filter { it !in dock }
        layout.seedDefaults(staples, dock)
    }
}

val Context.ygg: YggApp get() = applicationContext as YggApp

// ---------------------------------------------------------------- system helpers

/** Pulls down the notification shade (works on One UI via the hidden StatusBarManager API). */
@SuppressLint("WrongConstant")
fun Context.expandNotifications() {
    try {
        val sbm = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
    } catch (_: Throwable) {
    }
}

fun Context.badgeAccessGranted(): Boolean {
    val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
    val me = ComponentName(this, BadgeService::class.java).flattenToString()
    return flat.split(":").any { it.equals(me, ignoreCase = true) }
}

fun Context.openSettings(action: String) {
    try {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

fun Context.uninstall(packageName: String) {
    try {
        startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}

/** Opens whatever assistant the phone is set to use (Hafnir, once it exists). */
fun Context.openAssistant() {
    for (action in listOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST)) {
        try {
            startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {
        }
    }
}
