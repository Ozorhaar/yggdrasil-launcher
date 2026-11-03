package com.ozorhaar.yggdrasil.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One launchable activity (an "app" as the user sees it). */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val info: LauncherActivityInfo,
)

/** Keeps the list of launchable apps current via LauncherApps callbacks. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        val list = userManager.userProfiles.flatMap { user ->
            launcherApps.getActivityList(null, user).map { info ->
                AppEntry(
                    key = keyFor(info.componentName, user),
                    label = info.label?.toString() ?: info.componentName.packageName,
                    packageName = info.componentName.packageName,
                    component = info.componentName,
                    user = user,
                    info = info,
                )
            }
        }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
        _apps.value = list
    }

    fun keyFor(component: ComponentName, user: UserHandle): String =
        "${component.flattenToString()}#${userManager.getSerialNumberForUser(user)}"

    fun byKey(key: String?): AppEntry? = key?.let { k -> _apps.value.firstOrNull { it.key == k } }

    fun firstForPackage(pkg: String): AppEntry? = _apps.value.firstOrNull { it.packageName == pkg }

    fun launch(app: AppEntry, bounds: Rect? = null) {
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (_: Exception) {
        }
    }

    fun openAppInfo(app: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Exception) {
        }
    }
}
