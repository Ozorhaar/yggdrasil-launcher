package com.ozorhaar.yggdrasil.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One launchable thing: an app's main activity, or (when [shortcut] is set) an
 * app shortcut pinned to the home screen, such as Home Assistant's "Assist".
 */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val info: LauncherActivityInfo?,
    val shortcut: ShortcutInfo? = null,
) {
    val isShortcut: Boolean get() = shortcut != null
}

const val SHORTCUT_PREFIX = "shortcut:"
fun isShortcutRef(v: String?): Boolean = v?.startsWith(SHORTCUT_PREFIX) == true
/** The app package a "shortcut:<pkg>/<id>#<user>" reference belongs to. */
fun shortcutPackage(ref: String): String = ref.removePrefix(SHORTCUT_PREFIX).substringBefore('/')

/** Keeps the list of launchable apps current via LauncherApps callbacks. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _shortcuts = MutableStateFlow<List<AppEntry>>(emptyList())
    /** Shortcuts this launcher has pinned to the home screen. */
    val shortcuts: StateFlow<List<AppEntry>> = _shortcuts

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = reload()
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) =
            reloadShortcuts()
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
        reloadShortcuts()
    }

    // ------------------------------------------------------------ shortcuts
    private fun canUseShortcuts() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutKey(pkg: String, id: String, user: UserHandle) =
        "$SHORTCUT_PREFIX$pkg/$id#${userManager.getSerialNumberForUser(user)}"

    private fun entryFor(s: ShortcutInfo): AppEntry {
        val comp = s.activity
            ?: _apps.value.firstOrNull { it.packageName == s.`package` }?.component
            ?: ComponentName(s.`package`, "")
        return AppEntry(
            key = shortcutKey(s.`package`, s.id, s.userHandle),
            label = (s.shortLabel ?: s.longLabel ?: s.id).toString(),
            packageName = s.`package`,
            component = comp,
            user = s.userHandle,
            info = null,
            shortcut = s,
        )
    }

    fun reloadShortcuts() {
        if (!canUseShortcuts()) return
        _shortcuts.value = runCatching {
            userManager.userProfiles.flatMap { user ->
                launcherApps.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), user)
                    .orEmpty().map { entryFor(it) }
            }
        }.getOrDefault(emptyList())
    }

    /** The shortcuts an app offers (what a long-press on its icon shows elsewhere). */
    fun shortcutsFor(app: AppEntry): List<ShortcutInfo> {
        if (!canUseShortcuts() || app.isShortcut) return emptyList()
        return runCatching {
            launcherApps.getShortcuts(
                LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                ),
                app.user,
            ).orEmpty().filter { it.isEnabled }.sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
        }.getOrDefault(emptyList())
    }

    /** Finds one shortcut by package and id (used by the Nova import). */
    fun findShortcut(pkg: String, id: String, user: UserHandle = android.os.Process.myUserHandle()): ShortcutInfo? {
        if (!canUseShortcuts()) return null
        return runCatching {
            launcherApps.getShortcuts(
                LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(id)).setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                ),
                user,
            )?.firstOrNull()
        }.getOrNull()
    }

    private fun pinnedIds(pkg: String, user: UserHandle): List<String> = runCatching {
        launcherApps.getShortcuts(
            LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), user,
        ).orEmpty().map { it.id }
    }.getOrDefault(emptyList())

    /** Pins a shortcut so it keeps working even if the app drops it from its menu. Returns its key. */
    fun pin(s: ShortcutInfo): String? = runCatching {
        launcherApps.pinShortcuts(s.`package`, (pinnedIds(s.`package`, s.userHandle) + s.id).distinct(), s.userHandle)
        reloadShortcuts()
        shortcutKey(s.`package`, s.id, s.userHandle)
    }.getOrNull()

    fun unpin(key: String) {
        val s = _shortcuts.value.firstOrNull { it.key == key }?.shortcut ?: return
        runCatching {
            launcherApps.pinShortcuts(s.`package`, pinnedIds(s.`package`, s.userHandle) - s.id, s.userHandle)
            reloadShortcuts()
        }
    }

    fun launchShortcut(s: ShortcutInfo) {
        runCatching { launcherApps.startShortcut(s, null, null) }
    }

    /** The drawable to carve into a shield. */
    fun iconFor(app: AppEntry, dpi: Int): Drawable? = runCatching {
        app.shortcut?.let { launcherApps.getShortcutIconDrawable(it, dpi) } ?: app.info?.getIcon(0)
    }.getOrNull()

    fun keyFor(component: ComponentName, user: UserHandle): String =
        "${component.flattenToString()}#${userManager.getSerialNumberForUser(user)}"

    fun byKey(key: String?): AppEntry? = key?.let { k -> _apps.value.firstOrNull { it.key == k } }

    fun firstForPackage(pkg: String): AppEntry? = _apps.value.firstOrNull { it.packageName == pkg }

    fun launch(app: AppEntry, bounds: Rect? = null) {
        app.shortcut?.let { launchShortcut(it); return }
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (_: Exception) {
        }
    }

    fun openAppInfo(app: AppEntry) {
        try {
            val comp = if (app.isShortcut) firstForPackage(app.packageName)?.component ?: app.component else app.component
            launcherApps.startAppDetailsActivity(comp, app.user, null, null)
        } catch (_: Exception) {
        }
    }
}
