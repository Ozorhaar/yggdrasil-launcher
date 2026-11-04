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

const val WIDGET_HOST_ID = 0x7997
const val REQ_CONFIGURE_WIDGET = 4101

class YggApp : Application() {
    lateinit var repo: AppRepository
        private set
    lateinit var layout: LayoutStore
        private set

    /** Hosts the home-screen widgets. */
    lateinit var widgetHost: android.appwidget.AppWidgetHost
        private set
    val widgetManager: android.appwidget.AppWidgetManager by lazy { android.appwidget.AppWidgetManager.getInstance(this) }
    /** Live widget views, reused when a page scrolls back into view. */
    val widgetViews = HashMap<Int, android.appwidget.AppWidgetHostView>()
    /** (resultCode) from a widget's configure screen. */
    val widgetConfigResult = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Home Assistant devices in search (token kept in the hardware keystore). */
    val homeLink by lazy { com.ozorhaar.yggdrasil.launcher.search.HomeLink(this) }

    /** Fires when the Home button is pressed while the launcher is already showing. */
    val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(this)
        widgetHost = android.appwidget.AppWidgetHost(this, WIDGET_HOST_ID)
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

/**
 * Wakes Hafnir straight into listening (same as the assist gesture). Falls back to whatever
 * assistant the phone is set to use if Hafnir isn't installed or is too old to have the listen door.
 */
fun Context.openAssistant() {
    val tries = listOf(
        Intent(HAFNIR_LISTEN).setPackage(HAFNIR_PACKAGE),
        Intent(Intent.ACTION_VOICE_COMMAND),
        Intent(Intent.ACTION_ASSIST),
    )
    for (intent in tries) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        } catch (_: Exception) {
        }
    }
}

const val HAFNIR_PACKAGE = "com.ozorhaar.hafnir"
const val HAFNIR_LISTEN = "com.ozorhaar.hafnir.action.LISTEN"
