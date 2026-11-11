package com.ozorhaar.yggdrasil.launcher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates the launcher from its own GitHub releases.
 *
 * Every update must pass, in order:
 *  1. come from github.com/Ozorhaar/yggdrasil-launcher over HTTPS,
 *  2. match the SHA-256 published next to it,
 *  3. be this app (same package), newer than what is installed,
 *  4. be signed with the same key as the installed launcher.
 * Then Android's own installer asks you to confirm. Nothing installs silently.
 */
object Updater {
    private const val API = "https://api.github.com/repos/Ozorhaar/yggdrasil-launcher/releases/latest"
    private val ALLOWED_HOSTS = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    data class Release(val versionName: String, val versionCode: Long, val apkUrl: String, val shaUrl: String?)

    fun installedCode(ctx: Context): Long =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode

    fun installedName(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** The newest release on GitHub. Call off the main thread. */
    fun latest(): Release {
        val j = JSONObject(get(API).decodeToString())
        val tag = j.getString("tag_name")                       // v1.0.N (build N), or v2.0.P (build 88 + P)
        val parts = tag.removePrefix("v").split('.')
        val last = parts.lastOrNull()?.toLongOrNull() ?: error("Unexpected release tag $tag")
        val code = when (parts.firstOrNull()) {
            "1" -> last
            "2" -> 88 + last                                    // 2.0.0 was CI run 88 (see app/build.gradle.kts)
            else -> error("Unexpected release tag $tag")
        }
        val assets = j.getJSONArray("assets")
        var apk: String? = null
        var sha: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.getString("name")
            val url = a.getString("browser_download_url")
            if (name.endsWith(".apk")) apk = url
            if (name.endsWith(".apk.sha256")) sha = url
        }
        return Release(tag.removePrefix("v"), code, apk ?: error("Release $tag has no APK"), sha)
    }

    /** Downloads and checks the update; returns the verified file. Call off the main thread. */
    fun downloadAndVerify(ctx: Context, r: Release, progress: (String) -> Unit): File {
        val shaUrl = r.shaUrl ?: error("Release has no checksum, so it won't be installed")
        progress("Downloading ${r.versionName}…")
        val expected = get(shaUrl).decodeToString().trim().split(Regex("\\s+")).first().lowercase()
        if (!expected.matches(Regex("[0-9a-f]{64}"))) error("The published checksum is malformed")

        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "update.apk")
        apk.writeBytes(get(r.apkUrl))

        progress("Checking the download…")
        val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes()).joinToString("") { "%02x".format(it) }
        if (actual != expected) { apk.delete(); error("Checksum doesn't match. The download was not installed.") }

        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: run { apk.delete(); error("That file isn't a valid app") }
        if (archive.packageName != ctx.packageName) { apk.delete(); error("That file is a different app (${archive.packageName})") }
        if (archive.longVersionCode <= installedCode(ctx)) { apk.delete(); error("That version isn't newer than this one") }

        val mine = pm.getPackageInfo(ctx.packageName, flags).signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet()
        val theirs = archive.signingInfo?.apkContentsSigners?.map { it.toByteArray().toList() }?.toSet()
        if (mine.isNullOrEmpty() || mine != theirs) { apk.delete(); error("It isn't signed with your Yggdrasil key. Not installed.") }
        return apk
    }

    /** True if Android lets the launcher offer installs (asked once per phone). */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** Hands the verified file to Android's installer, which asks you to confirm. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("yggdrasil-launcher.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }

    private fun get(url: String): ByteArray {
        var target = URL(url)
        repeat(5) {
            if (target.protocol != "https" || target.host !in ALLOWED_HOSTS) error("Refusing to download from ${target.host}")
            val c = target.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            c.setRequestProperty("User-Agent", "YggdrasilLauncher")
            when (val code = c.responseCode) {
                in 200..299 -> return c.inputStream.use { it.readBytes() }.also { c.disconnect() }
                301, 302, 303, 307, 308 -> {
                    target = URL(target, c.getHeaderField("Location") ?: error("Redirect without a location"))
                    c.disconnect()
                }
                else -> { c.disconnect(); error("GitHub answered $code") }
            }
        }
        error("Too many redirects")
    }
}

/** Gets the installer's answer: shows Android's confirmation screen, or reports a failure. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // the new launcher takes over
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown reason"
                Toast.makeText(ctx, "Update not installed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
