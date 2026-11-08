package com.ozorhaar.yggdrasil.launcher.hoard

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Base64
import com.ozorhaar.yggdrasil.launcher.R
import com.ozorhaar.yggdrasil.launcher.YggApp
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.concurrent.thread

/**
 * The Hoard: every night the launcher sends an encrypted copy of your home-screen layout (pages,
 * dock, folders, cards, the hidden watch widget) to the Mac mini, so a reset or new phone is one
 * restore away. It's encrypted on the phone to the Mac's own public key (res/raw/hoard_pub.der);
 * only the Mac's private key, which never leaves it, can open it. No Home Assistant token, no
 * passwords and nothing from your apps is in it: only the layout.
 */
object Hoard {
    private const val URL_UPLOAD = "https://homelab.ozorhaar.com/hoard/upload"
    private const val JOB_ID = 4711
    private const val DAY = 24 * 3600 * 1000L

    /** Schedules the nightly backup (once; Android keeps it across restarts). */
    fun schedule(ctx: Context) = runCatching {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) != null) return@runCatching
        js.schedule(
            JobInfo.Builder(JOB_ID, ComponentName(ctx, HoardJob::class.java))
                .setPeriodic(DAY, 6 * 3600 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setRequiresBatteryNotLow(true)
                .setPersisted(true)
                .build()
        )
    }.let { }

    /** Encrypts and sends the layout now. Returns a short result for the settings screen. Blocking. */
    fun sendNow(ctx: Context): String = runCatching {
        val app = ctx.applicationContext as YggApp
        val layout = app.layout.exportJson()
        if (app.watch.widgetId >= 0) layout.put("watch", app.watch.provider)
        val sealed = seal(ctx, layout.toString().toByteArray())
        val c = URL(URL_UPLOAD).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 6000; c.readTimeout = 10000; c.doOutput = true
            c.instanceFollowRedirects = false
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.outputStream.use { it.write(sealed) }
            val code = c.responseCode
            if (code !in 200..299) error("the Mac answered $code")
        } finally {
            c.disconnect()
        }
        ctx.getSharedPreferences("ygg_hoard", Context.MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).apply()
        "Layout saved to the Mac"
    }.getOrElse { "Backup failed: ${it.message ?: it.javaClass.simpleName}" }

    /** When the last backup reached the Mac (0 = never). */
    fun last(ctx: Context): Long = ctx.getSharedPreferences("ygg_hoard", Context.MODE_PRIVATE).getLong("last", 0L)

    /** "YGGHOARD1\n" + b64(RSA-OAEP-SHA256(aesKey)) + "\n" + b64(nonce) + "\n" + b64(AES-256-GCM(data)) */
    private fun seal(ctx: Context, data: ByteArray): ByteArray {
        val pub = KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(ctx.resources.openRawResource(R.raw.hoard_pub).use { it.readBytes() })
        )
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ct = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, nonce)); doFinal(data)
        }
        val wrapped = Cipher.getInstance("RSA/ECB/OAEPPadding").run {
            init(Cipher.ENCRYPT_MODE, pub, OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT))
            doFinal(aes.encoded)
        }
        fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
        return ("YGGHOARD1\n" + b64(wrapped) + "\n" + b64(nonce) + "\n" + b64(ct)).toByteArray()
    }
}

/** Android runs this about once a day when there's a network and the battery isn't low. */
class HoardJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread {
            val r = Hoard.sendNow(applicationContext)
            jobFinished(params, r.startsWith("Backup failed"))
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
