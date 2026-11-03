package com.ozorhaar.yggdrasil.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray

enum class LayoutMode { GRID, LONGHALL }

/**
 * Home-screen layout and launcher settings, persisted in SharedPreferences.
 * Pages are flat lists of [columns] x [rows] slots holding app keys (or null).
 */
class LayoutStore(context: Context) {
    private val prefs = context.getSharedPreferences("yggdrasil_layout", Context.MODE_PRIVATE)

    var columns by mutableStateOf(prefs.getInt("columns", 4))
        private set
    val rows = 6
    val dockSize = 5

    var layoutMode by mutableStateOf(
        runCatching { LayoutMode.valueOf(prefs.getString("mode", "GRID")!!) }.getOrDefault(LayoutMode.GRID)
    )
        private set
    var runicLabels by mutableStateOf(prefs.getBoolean("runic_labels", true))
        private set
    /** How much to darken the wallpaper, 0-60 (percent). One UI and Nova dim it too. */
    var wallShade by mutableStateOf(prefs.getInt("wall_shade", 30))
        private set

    val pages: SnapshotStateList<SnapshotStateList<String?>> = mutableStateListOf()
    val dock: SnapshotStateList<String?> = mutableStateListOf()

    val initialized: Boolean get() = prefs.getBoolean("initialized", false)

    init {
        load()
    }

    private fun load() {
        pages.clear()
        val raw = prefs.getString("pages", null)
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                val page = mutableStateListOf<String?>()
                for (j in 0 until slotsPerPage) page.add(if (j < p.length() && !p.isNull(j)) p.getString(j) else null)
                pages.add(page)
            }
        }
        if (pages.isEmpty()) pages.add(emptyPage())
        dock.clear()
        val d = prefs.getString("dock", null)?.let { JSONArray(it) }
        for (j in 0 until dockSize) dock.add(if (d != null && j < d.length() && !d.isNull(j)) d.getString(j) else null)
    }

    val slotsPerPage: Int get() = columns * rows

    private fun emptyPage() = mutableStateListOf<String?>().apply { repeat(slotsPerPage) { add(null) } }

    fun save() {
        val arr = JSONArray()
        pages.forEach { page -> arr.put(JSONArray().apply { page.forEach { put(it ?: JSONObjectNull) } }) }
        val d = JSONArray().apply { dock.forEach { put(it ?: JSONObjectNull) } }
        prefs.edit()
            .putString("pages", arr.toString())
            .putString("dock", d.toString())
            .putInt("columns", columns)
            .putString("mode", layoutMode.name)
            .putBoolean("runic_labels", runicLabels)
            .putInt("wall_shade", wallShade)
            .putBoolean("initialized", true)
            .apply()
    }

    // ------------------------------------------------------------ queries
    fun isPlaced(key: String) = pages.any { key in it } || key in dock

    /** Every app on the home pages, in reading order (used by the Longhall view). */
    fun pinnedInOrder(): List<String> = pages.flatMap { it.filterNotNull() }

    // ------------------------------------------------------------ edits
    fun addToHome(key: String, preferredPage: Int = 0): Boolean {
        if (pages.any { key in it }) return false
        val order = (preferredPage until pages.size) + (0 until preferredPage)
        for (p in order) {
            val idx = pages[p].indexOfFirst { it == null }
            if (idx >= 0) {
                pages[p][idx] = key; save(); return true
            }
        }
        pages.add(emptyPage().also { it[0] = key })
        save()
        return true
    }

    fun removeFromHome(key: String) {
        pages.forEach { page -> page.indices.filter { page[it] == key }.forEach { page[it] = null } }
        trimEmptyPages()
        save()
    }

    fun pinToDock(key: String): Boolean {
        if (key in dock) return false
        val idx = dock.indexOfFirst { it == null }
        if (idx < 0) return false
        dock[idx] = key
        save()
        return true
    }

    fun unpinFromDock(key: String) {
        dock.indices.filter { dock[it] == key }.forEach { dock[it] = null }
        save()
    }

    /** Moves (or swaps) an app already on a page to [page]/[slot]. */
    fun moveTo(key: String, page: Int, slot: Int) {
        val fromPage = pages.indexOfFirst { key in it }
        if (fromPage < 0 || page !in pages.indices || slot !in 0 until slotsPerPage) return
        val fromSlot = pages[fromPage].indexOf(key)
        val occupant = pages[page][slot]
        pages[page][slot] = key
        pages[fromPage][fromSlot] = occupant
        save()
    }

    fun addPage() {
        pages.add(emptyPage()); save()
    }

    private fun trimEmptyPages() {
        while (pages.size > 1 && pages.last().all { it == null }) pages.removeAt(pages.lastIndex)
    }

    /** Drops apps that were uninstalled. */
    fun prune(valid: Set<String>) {
        var changed = false
        pages.forEach { page -> page.indices.forEach { i -> if (page[i] != null && page[i] !in valid) { page[i] = null; changed = true } } }
        dock.indices.forEach { i -> if (dock[i] != null && dock[i] !in valid) { dock[i] = null; changed = true } }
        if (changed) { trimEmptyPages(); save() }
    }

    // ------------------------------------------------------------ settings
    fun updateLayoutMode(m: LayoutMode) { layoutMode = m; save() }
    fun updateRunicLabels(on: Boolean) { runicLabels = on; save() }
    fun updateWallShade(pct: Int) { wallShade = pct.coerceIn(0, 60); save() }

    fun updateColumns(c: Int) {
        if (c == columns) return
        val apps = pinnedInOrder()
        columns = c
        pages.clear()
        pages.add(emptyPage())
        apps.forEach { addToHome(it) }
        save()
    }

    fun seedDefaults(home: List<String>, dockApps: List<String>) {
        dockApps.take(dockSize).forEachIndexed { i, k -> dock[i] = k }
        home.forEach { addToHome(it) }
        save()
    }
}

private val JSONObjectNull: Any = org.json.JSONObject.NULL
