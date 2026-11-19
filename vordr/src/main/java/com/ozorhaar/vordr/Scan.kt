package com.ozorhaar.vordr

import android.Manifest
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One look over the phone's security: what's switched on that shouldn't be, which apps hold the
 * powers that matter, and what changed since the last look. Reads settings and the package list
 * only; it changes nothing and sends nothing anywhere.
 */
object Scan {
    enum class Level { OK, NOTE, WARN }

    /** [id] is stable across scans, so a finding can be told apart as new. */
    class Finding(val id: String, val level: Level, val title: String, val detail: String, val fix: Intent? = null, val trusted: Boolean = false) {
        /** Things you can't wave off: they're either always worth fixing or a sign of tampering. */
        val trustable get() = level == Level.WARN && listOf("adb_wifi", "lock", "patch", "signer:").none { id == it || id.startsWith(it) && it.endsWith(":") }
        fun asTrusted() = Finding(id, Level.NOTE, title, "You marked this as trusted. Long-press to undo.", fix, trusted = true)
    }

    /** Your own signing keys (SHA-256 of the certificate): Yggdrasil's, and Hafnir's (also BMO's). */
    private val OWN_KEYS = mapOf(
        "3bacbf5cc5eb7449280fde62f43536060ef4fa6dc664384f77986844eb2d9900" to "Yggdrasil key",
        "c7f2dbb41e318f9835f3282ceefe23d73f2e6f63874fb5c8c075e425698a998b" to "Hafnir key",
    )
    private val STORES = setOf("com.android.vending", "com.sec.android.app.samsungapps", "com.google.android.packageinstaller")

    /** The powers worth knowing who holds, with what they let an app do. */
    private val SENSITIVE = linkedMapOf(
        Manifest.permission.READ_SMS to "read your texts", Manifest.permission.RECEIVE_SMS to "see texts as they arrive",
        Manifest.permission.SEND_SMS to "send texts", Manifest.permission.READ_CALL_LOG to "read your call history",
        Manifest.permission.RECORD_AUDIO to "use the microphone", Manifest.permission.CAMERA to "use the camera",
        Manifest.permission.ACCESS_BACKGROUND_LOCATION to "know where you are, even when closed",
        Manifest.permission.READ_CONTACTS to "read your contacts", Manifest.permission.BODY_SENSORS to "read body sensors",
    )

    class Report(val findings: List<Finding>, val grants: Map<String, Set<String>>, val signers: Map<String, String>, val at: Long)

    private fun appInfo(pkg: String) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    fun run(ctx: Context, before: Saved?): Report {
        val pm = ctx.packageManager
        val f = ArrayList<Finding>()
        fun label(pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

        // ---- the phone itself
        val patch = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull()
        if (patch != null) {
            val days = ChronoUnit.DAYS.between(patch, LocalDate.now())
            f += when {
                days > 90 -> Finding("patch", Level.WARN, "Security update is $days days old", "Patch level ${Build.VERSION.SECURITY_PATCH}. Check Settings → Software update.", Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                days > 45 -> Finding("patch", Level.NOTE, "Security update is $days days old", "Patch level ${Build.VERSION.SECURITY_PATCH}. Samsung usually ships one a month.")
                else -> Finding("patch", Level.OK, "Security update is current", "Patch level ${Build.VERSION.SECURITY_PATCH}.")
            }
        }
        val secure = ctx.getSystemService(KeyguardManager::class.java).isDeviceSecure
        f += if (secure) Finding("lock", Level.OK, "Screen lock is on", "PIN, pattern or password, with your fingerprint.")
             else Finding("lock", Level.WARN, "No screen lock", "Anyone holding the phone can open it.", Intent(Settings.ACTION_SECURITY_SETTINGS))

        fun global(k: String) = runCatching { Settings.Global.getInt(ctx.contentResolver, k, 0) }.getOrDefault(0)
        val dev = global(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1
        val adbWifi = global("adb_wifi_enabled") == 1
        val adbUsb = global(Settings.Global.ADB_ENABLED) == 1
        f += when {
            adbWifi -> Finding("adb_wifi", Level.WARN, "Wireless debugging is on", "Anything paired on this Wi-Fi can install apps and read data. Switch it off when you're done.", Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            adbUsb -> Finding("adb_usb", Level.NOTE, "USB debugging is on", "A computer you've allowed can control the phone over a cable.", Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            dev -> Finding("dev", Level.NOTE, "Developer options are on", "Debugging is off, which is what matters.")
            else -> Finding("dev", Level.OK, "Debugging is off", "")
        }

        // your own apps (signed with your Yggdrasil or Hafnir key) get their powers listed as yours, not warned about;
        // if one's key ever changed it would stop counting as yours and the warnings come back
        val flagsS = PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        val own = HashMap<String, String>()
        for (p in pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flagsS))) {
            val c = p.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray() ?: continue
            val h = MessageDigest.getInstance("SHA-256").digest(c).joinToString("") { b -> "%02x".format(b) }
            OWN_KEYS[h]?.let { own[p.packageName] = it }
        }
        fun mine(pkgs: List<String>, what: String) {
            val m = pkgs.filter { it in own }
            if (m.isNotEmpty()) f += Finding("own:$what:" + m.sorted().joinToString(","), Level.OK,
                "Yours: ${m.joinToString { label(it) }} ($what)", "Signed with your own key, so it's expected.")
        }

        // ---- the way out: Bifrost should always be up, and nothing should steer DNS around the house
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        @Suppress("DEPRECATION") val nets = runCatching { cm.allNetworks.toList() }.getOrDefault(emptyList())
        val vpnUp = nets.any { cm.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true }
        val online = nets.any { cm.getNetworkCapabilities(it)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true }
        f += when {
            vpnUp -> Finding("vpn", Level.OK, "VPN is up", "Traffic goes through Bifrost.")
            online -> Finding("vpn_off", Level.WARN, "No VPN running", "Bifrost is off, so traffic skips the house's protection. Turn it back on.",
                Intent(Settings.ACTION_VPN_SETTINGS))
            else -> Finding("vpn", Level.OK, "Offline", "")
        }
        fun secureStr(k: String) = runCatching { Settings.Secure.getString(ctx.contentResolver, k) }.getOrNull()
        secureStr("always_on_vpn_app")?.let { app ->
            f += if (app != "com.ozorhaar.bifrost") Finding("alwayson:$app", Level.WARN, "Always-on VPN isn't Bifrost",
                "Set to ${label(app)}.", Intent(Settings.ACTION_VPN_SETTINGS))
            else if (secureStr("always_on_vpn_lockdown") == "1") Finding("lockdown", Level.OK, "Bifrost is always on, and blocks traffic without it", "")
            else Finding("lockdown_off", Level.WARN, "\"Block connections without VPN\" is off",
                "If Bifrost drops, apps carry on unprotected. Turn it on in Bifrost's VPN settings.", Intent(Settings.ACTION_VPN_SETTINGS))
        }
        val dot = nets.mapNotNull { runCatching { cm.getLinkProperties(it)?.privateDnsServerName }.getOrNull() }.firstOrNull()
        if (dot != null) f += Finding("dot:$dot", Level.WARN, "Private DNS is set to $dot",
            "Lookups go there instead of the house's DNS, past its ad, tracker and malware blocking. Set Private DNS to Off.",
            Intent("android.settings.WIRELESS_SETTINGS"))

        // ---- standing powers: accessibility, notification access, device admin
        fun secureList(k: String) = runCatching { Settings.Secure.getString(ctx.contentResolver, k) }.getOrNull()
            ?.split(':')?.mapNotNull { ComponentName.unflattenFromString(it)?.packageName }?.distinct().orEmpty()
        val a11yAll = secureList(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        mine(a11yAll, "accessibility"); val a11y = a11yAll.filter { it !in own }
        f += if (a11y.isEmpty()) Finding("a11y", Level.OK, "No accessibility services", "Nothing can read or tap your screen for you.")
             else Finding("a11y:" + a11y.sorted().joinToString(","), Level.WARN, "Accessibility: ${a11y.joinToString { label(it) }}",
                 "These can read everything on screen and tap for you. Keep only ones you trust.", Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        val listenersAll = secureList("enabled_notification_listeners")
        mine(listenersAll, "notification access"); val listeners = listenersAll.filter { it !in own }
        if (listeners.isNotEmpty()) f += Finding("listen:" + listeners.sorted().joinToString(","), Level.NOTE,
            "Notification access: ${listeners.joinToString { label(it) }}", "These can read every notification, including codes and messages.",
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        val adminsAll = ctx.getSystemService(DevicePolicyManager::class.java).activeAdmins.orEmpty().map { it.packageName }.distinct()
        mine(adminsAll, "device admin"); val admins = adminsAll.filter { it !in own }
        if (admins.isNotEmpty()) f += Finding("admin:" + admins.sorted().joinToString(","), Level.NOTE,
            "Device admin: ${admins.joinToString { label(it) }}", "These can lock or wipe the phone.", Intent().setClassName("com.android.settings", "com.android.settings.DeviceAdminSettings"))

        // ---- apps: sensitive grants, who may install apps, where they came from, who signed them
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES
        val pkgs: List<PackageInfo> = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        val grants = HashMap<String, Set<String>>(); val signers = HashMap<String, String>()
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val installers = ArrayList<String>(); val stale = ArrayList<String>(); val outside = ArrayList<String>()
        for (p in pkgs) {
            val ai = p.applicationInfo ?: continue
            val system = ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val req = p.requestedPermissions.orEmpty(); val rf = p.requestedPermissionsFlags ?: IntArray(0)
            val held = req.indices.filter { req[it] in SENSITIVE && rf[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }.map { req[it] }.toSet()
            if (!system && held.isNotEmpty()) grants[p.packageName] = held
            val cert = p.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            val sha = cert?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }
            if (!system && sha != null) signers[p.packageName] = sha
            if (!system && Manifest.permission.REQUEST_INSTALL_PACKAGES in req) {
                val mode = runCatching { ops.unsafeCheckOpNoThrow("android:request_install_packages", ai.uid, p.packageName) }.getOrNull()
                if (mode == AppOpsManager.MODE_ALLOWED) installers += p.packageName
            }
            if (!system) {
                val from = runCatching { pm.getInstallSourceInfo(p.packageName).installingPackageName }.getOrNull()
                if (from == null || from !in STORES) outside += p.packageName
                if (from in STORES && System.currentTimeMillis() - p.lastUpdateTime > 365L * 86_400_000) stale += p.packageName
            }
        }
        mine(installers, "may install its own updates"); installers.removeAll(own.keys)
        f += if (installers.isEmpty()) Finding("installers", Level.OK, "No app may install other apps", "")
             else Finding("installers:" + installers.sorted().joinToString(","), Level.WARN, "May install apps: ${installers.joinToString { label(it) }}",
                 "These can put new apps on the phone. Allow it only while you need it.", Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))

        // signers that changed since the last look: the strongest sign something was swapped
        before?.signers?.forEach { (pkg, was) ->
            val now = signers[pkg]
            if (now != null && now != was) f += Finding("signer:$pkg:$now", Level.WARN, "${label(pkg)} is signed by a different key",
                "Same app name, different maker's key since the last check. If you didn't reinstall it from somewhere else, remove it.", appInfo(pkg))
        }
        // apps from outside the stores, and whose key they carry
        for (pkg in outside.sortedBy { label(it).lowercase() }) {
            val own = signers[pkg]?.let { OWN_KEYS[it] }
            f += if (own != null) Finding("side:$pkg", Level.OK, "${label(pkg)}: yours", "Signed with your $own.")
                 else Finding("side:$pkg:${signers[pkg]?.take(12)}", Level.NOTE, "${label(pkg)}: from outside the stores",
                     "Installed directly, signed by a key that isn't yours (${signers[pkg]?.take(12) ?: "unknown"}…).", appInfo(pkg))
        }
        // new sensitive powers since the last look
        for ((pkg, held) in grants) {
            val was = before?.grants?.get(pkg).orEmpty()
            val added = held - was
            if (before != null && added.isNotEmpty()) f += Finding("grant:$pkg:" + added.sorted().joinToString(","), Level.WARN,
                "${label(pkg)} can now ${added.joinToString(", ") { SENSITIVE[it]!! }}", "Granted since the last check.", appInfo(pkg))
        }
        if (stale.isNotEmpty()) f += Finding("stale:" + stale.sorted().joinToString(","), Level.NOTE,
            "Not updated in a year: ${stale.joinToString { label(it) }}", "Abandoned apps stop getting security fixes. Remove any you don't use.")
        return Report(f, grants, signers, System.currentTimeMillis())
    }

    /** What the last scan saw, to tell what's new. */
    class Saved(val grants: Map<String, Set<String>>, val signers: Map<String, String>, val ids: Set<String>, val at: Long) {
        fun toJson(): String = JSONObject().put("at", at)
            .put("grants", JSONObject(grants.mapValues { org.json.JSONArray(it.value.toList()) }))
            .put("signers", JSONObject(signers)).put("ids", org.json.JSONArray(ids.toList())).toString()

        companion object {
            fun from(r: Report) = Saved(r.grants, r.signers, r.findings.filter { it.level != Level.OK }.map { it.id }.toSet(), r.at)
            fun parse(s: String?): Saved? = runCatching {
                val o = JSONObject(s!!)
                val g = o.getJSONObject("grants"); val sg = o.getJSONObject("signers"); val ids = o.getJSONArray("ids")
                Saved(g.keys().asSequence().associateWith { k -> g.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } },
                    sg.keys().asSequence().associateWith { sg.getString(it) },
                    (0 until ids.length()).map { ids.getString(it) }.toSet(), o.getLong("at"))
            }.getOrNull()
        }
    }
}
