package com.ozorhaar.yggdrasil.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Process
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Rebuilds the home screen from a Nova Launcher backup (.novabackup, a zip
 * holding nova.db). Folders keep their names and order; widgets, shortcuts
 * and Nova's own drawer button are skipped.
 */
object NovaImport {
    data class Result(val apps: Int, val folders: Int, val skipped: List<String>)

    private const val DESKTOP = -100L
    private const val HOTSEAT = -101L
    private const val TYPE_APP = 0
    private const val TYPE_SHORTCUT = 1
    private const val TYPE_FOLDER = 2

    private class Row(
        val id: Long, val title: String, val intent: String?, val container: Long,
        val screen: Long, val x: Int, val y: Int, val type: Int, val rank: Int,
    )

    fun run(ctx: Context, uri: Uri, layout: LayoutStore, apps: List<AppEntry>): Result {
        val db = File(ctx.cacheDir, "nova_import.db")
        extractDb(ctx, uri, db)
        val rows = mutableListOf<Row>()
        SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY).use { sql ->
            sql.rawQuery(
                "SELECT _id,title,intent,container,screen,cellX,cellY,itemType,rank FROM favorites", null,
            ).use { c ->
                while (c.moveToNext()) rows += Row(
                    c.getLong(0), c.getString(1) ?: "", c.getString(2), c.getLong(3), c.getLong(4),
                    c.getDouble(5).coerceAtLeast(0.0).toInt(), c.getDouble(6).coerceAtLeast(0.0).toInt(),
                    c.getInt(7), c.getInt(8),
                )
            }
        }
        db.delete()

        val me = Process.myUserHandle()
        val byComponent = apps.filter { it.user == me }.associateBy { it.component }
        val byPackage = apps.filter { it.user == me }.groupBy { it.packageName }
        val skipped = mutableListOf<String>()

        fun appKey(r: Row): String? {
            val comp: ComponentName? = r.intent?.let { runCatching { Intent.parseUri(it, 0).component }.getOrNull() }
            if (comp == null || comp.packageName == "com.teslacoilsw.launcher") return null
            return (byComponent[comp] ?: byPackage[comp.packageName]?.firstOrNull())?.key
        }

        // folders first: folder id -> its apps in Nova's order
        val folderMap = mutableMapOf<Long, Folder>()
        val loneApp = mutableMapOf<Long, String?>()
        rows.filter { it.type == TYPE_FOLDER }.forEach { f ->
            val kids = rows.filter { it.container == f.id }.sortedWith(compareBy({ it.rank }, { it.y }, { it.x }))
            val keys = kids.mapNotNull { k ->
                val key = if (k.type == TYPE_APP || k.type == TYPE_SHORTCUT) appKey(k) else null
                if (key == null && k.title.isNotBlank()) skipped += k.title
                key
            }.distinct()
            when {
                keys.size >= 2 -> folderMap[f.id] = Folder("n${f.id}", f.title.ifBlank { DEFAULT_FOLDER_NAME }, keys)
                else -> loneApp[f.id] = keys.firstOrNull()
            }
        }

        fun itemFor(r: Row): String? = when (r.type) {
            TYPE_FOLDER -> folderMap[r.id]?.ref ?: loneApp[r.id]
            TYPE_APP, TYPE_SHORTCUT -> appKey(r).also { if (it == null && r.title.isNotBlank() && r.title != "Apps") skipped += r.title }
            else -> { if (r.title.isNotBlank()) skipped += r.title; null } // widgets, deep shortcuts
        }

        // home pages: one per Nova screen, positions kept, shifted up into our 6 rows
        val desktop = rows.filter { it.container == DESKTOP }
        val iconRows = desktop.filter { it.type == TYPE_APP || it.type == TYPE_SHORTCUT || it.type == TYPE_FOLDER }
        val columns = if ((iconRows.maxOfOrNull { it.x } ?: 3) >= 4) 5 else 4
        val pages = mutableListOf<Map<Int, String>>()
        val overflow = mutableListOf<Pair<Int, String>>()
        desktop.map { it.screen }.distinct().sorted().forEach { screen ->
            val onScreen = desktop.filter { it.screen == screen }.sortedWith(compareBy({ it.y }, { it.x }))
            val icons = onScreen.mapNotNull { r -> itemFor(r)?.let { r to it } }
            if (icons.isEmpty()) return@forEach
            val minY = icons.minOf { it.first.y }
            val used = icons.maxOf { it.first.y } - minY + 1
            val top = minY - (if (used < layout.rows) 1 else 0)
            val page = mutableMapOf<Int, String>()
            val pageIdx = pages.size
            icons.forEach { (r, item) ->
                val y = r.y - top
                val slot = y * columns + r.x
                if (r.x < columns && y in 0 until layout.rows && slot !in page) page[slot] = item
                else overflow += pageIdx to item
            }
            pages += page
        }

        // dock: Nova's first dock page; later dock pages go on a home page of their own
        val dockRows = rows.filter { it.container == HOTSEAT }
        val dockPages = dockRows.map { it.screen }.distinct().sorted()
        val dock = arrayOfNulls<String>(layout.dockSize)
        dockPages.firstOrNull()?.let { first ->
            dockRows.filter { it.screen == first }.forEach { r ->
                val item = itemFor(r) ?: return@forEach
                if (r.x < dock.size && dock[r.x] == null) dock[r.x] = item else overflow += 0 to item
            }
        }
        val extraDock = dockPages.drop(1).flatMap { s ->
            dockRows.filter { it.screen == s }.sortedBy { it.x }.mapNotNull { itemFor(it) }
        }
        val already = (pages.flatMap { it.values } + dock.filterNotNull() + overflow.map { it.second }).toSet() +
            folderMap.values.flatMap { it.apps }
        val extra = extraDock.filter { it !in already }.distinct()
        if (extra.isNotEmpty()) {
            val idx = pages.size
            pages += emptyMap()
            extra.forEach { overflow += idx to it }
        }

        layout.replaceAll(columns, pages, overflow, dock.toList(), folderMap.values.toList())
        val placed = (pages.flatMap { it.values } + dock.filterNotNull() + overflow.map { it.second })
        return Result(
            apps = placed.count { !isFolderRef(it) } + folderMap.values.sumOf { it.apps.size },
            folders = folderMap.size,
            skipped = skipped.distinct(),
        )
    }

    private fun extractDb(ctx: Context, uri: Uri, out: File) {
        ctx.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Couldn't open the backup" }
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.name.substringAfterLast('/') == "nova.db") {
                        out.outputStream().use { zip.copyTo(it) }
                        return
                    }
                }
            }
        }
        error("That file isn't a Nova backup (no nova.db inside)")
    }
}
