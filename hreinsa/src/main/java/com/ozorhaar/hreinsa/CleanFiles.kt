package com.ozorhaar.hreinsa

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the cleaned photos to the app you share them with, read-only and only by the one-time
 * grant the share carries. Serves nothing but files in its own clean/ folder, by plain name.
 */
class CleanFiles : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.ozorhaar.hreinsa.files"
        private val NAME = Regex("""^clean_[0-9a-f]{12}\.(jpg|png)$""")
        fun dir(ctx: android.content.Context) = File(ctx.cacheDir, "clean").apply { mkdirs() }
        fun uriFor(f: File): Uri = Uri.parse("content://$AUTHORITY/${f.name}")
    }

    private fun fileFor(uri: Uri): File {
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!NAME.matches(name) || uri.pathSegments.size != 1) throw FileNotFoundException()
        val f = File(dir(context!!), name)
        if (!f.isFile) throw FileNotFoundException()
        return f
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? =
        uri.lastPathSegment?.let { if (it.endsWith(".png")) "image/png" else if (it.endsWith(".jpg")) "image/jpeg" else null }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols)
        c.addRow(cols.map { when (it) { OpenableColumns.DISPLAY_NAME -> f.name; OpenableColumns.SIZE -> f.length(); else -> null } })
        return c
    }

    override fun onCreate() = true
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
