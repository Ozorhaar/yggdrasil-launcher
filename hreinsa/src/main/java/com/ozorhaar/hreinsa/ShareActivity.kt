package com.ozorhaar.hreinsa

import android.app.Activity
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File
import java.security.SecureRandom

/**
 * The share-sheet stop: cleans what you shared, then opens the share sheet again with the clean
 * version (Hreinsa itself left out of it). Links lose their trackers; photos are redrawn from
 * their pixels alone, so location, camera, time and every other hidden detail is gone.
 */
class ShareActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        try { handle() } catch (e: Exception) { toast("Couldn't clean that: ${e.javaClass.simpleName}") }
        finish()
    }

    private fun toast(s: String) = Toast.makeText(applicationContext, s, Toast.LENGTH_LONG).show()

    private fun handle() {
        val type = intent.type.orEmpty()
        if (type.startsWith("text/")) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return toast("Nothing to clean")
            val r = LinkCleaner.clean(text)
            toast(if (r.removed == 0) "Nothing to clean" else "Removed ${r.removed} tracker${if (r.removed == 1) "" else "s"}")
            share(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r.text)
                .also { i -> intent.getStringExtra(Intent.EXTRA_SUBJECT)?.let { i.putExtra(Intent.EXTRA_SUBJECT, it) } })
            return
        }
        // photos
        @Suppress("DEPRECATION")
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM))
        }
        if (uris.isEmpty()) return toast("Nothing to clean")
        tidy()
        val out = uris.mapNotNull { cleanPhoto(it) }
        if (out.isEmpty()) return toast("Couldn't read that photo")
        toast(if (out.size == 1) "Photo cleaned: location and camera details removed" else "${out.size} photos cleaned")
        val send = if (out.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, out[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(out))
        send.setType(if (out.all { it.toString().endsWith(".png") }) "image/png" else "image/jpeg")
        send.clipData = ClipData.newRawUri("", out[0]).also { c -> out.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share(send)
    }

    private fun share(send: Intent) {
        val chooser = Intent.createChooser(send, "Share clean")
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, ShareActivity::class.java)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(chooser)
    }

    /** Redraws the photo from its pixels (upright, at most 4096 px on its long side) into a fresh file. */
    private fun cleanPhoto(src: Uri): Uri? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(src)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val bmp = contentResolver.openInputStream(src)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rot = runCatching {
            contentResolver.openInputStream(src)!!.use { ExifInterface(it) }.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        }.getOrDefault(1)
        val m = Matrix().apply {
            when (rot) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        val upright = if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        val png = bounds.outMimeType == "image/png"
        val id = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val f = File(CleanFiles.dir(this), "clean_$id.${if (png) "png" else "jpg"}")
        f.outputStream().use { upright.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
        CleanFiles.uriFor(f)
    }.getOrNull()

    /** Cleaned copies are only kept for an hour. */
    private fun tidy() {
        val cut = System.currentTimeMillis() - 3600_000L
        CleanFiles.dir(this).listFiles()?.forEach { if (it.lastModified() < cut) it.delete() }
    }
}
