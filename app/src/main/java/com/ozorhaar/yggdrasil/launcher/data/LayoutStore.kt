package com.ozorhaar.yggdrasil.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

enum class LayoutMode { GRID, LONGHALL }

/** A place an item can sit: a home-page slot, a dock slot, or a position inside a folder. */
sealed interface Loc {
    data class Home(val page: Int, val slot: Int) : Loc
    data class Dock(val slot: Int) : Loc
    data class InFolder(val id: String, val index: Int) : Loc
}

/** A folder of apps. It sits in a home or dock slot as the reference "folder:<id>". */
class Folder(val id: String, name: String, apps: List<String>, emblem: Int = -2, locked: Boolean = false) {
    var name by mutableStateOf(name)
    /** Needs your fingerprint (or screen lock) to open. */
    var locked by mutableStateOf(locked)
    /** Rune index for the folder's emblem; -2 = pick from the name, -1 = none. */
    var emblem by mutableStateOf(emblem)
    val apps: SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(apps) }
    val ref: String get() = FOLDER_PREFIX + id
}

/** A widget on a home page, covering [w] x [h] cells from column [x], row [y]. */
class WidgetSpot(val id: Int, val provider: String, page: Int, x: Int, y: Int, w: Int, h: Int) {
    var page by mutableStateOf(page)
    var x by mutableStateOf(x)
    var y by mutableStateOf(y)
    var w by mutableStateOf(w)
    var h by mutableStateOf(h)

    fun overlaps(p: Int, ox: Int, oy: Int, ow: Int, oh: Int) =
        p == page && ox < x + w && x < ox + ow && oy < y + h && y < oy + oh
}

const val FOLDER_PREFIX = "folder:"
const val DEFAULT_FOLDER_NAME = "Hoard"
const val BACKUP_FORMAT = "yggdrasil-launcher-layout"
fun isFolderRef(v: String?): Boolean = v?.startsWith(FOLDER_PREFIX) == true

/**
 * Home-screen layout and launcher settings, persisted in SharedPreferences.
 * Pages are flat lists of [columns] x [rows] slots. A slot holds an app key,
 * a folder reference, or null.
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
    var dockRunes by mutableStateOf(prefs.getBoolean("dock_runes", true))
        private set
    /** How much to darken the wallpaper, 0-60 (percent). */
    var wallShade by mutableStateOf(prefs.getInt("wall_shade", 0))
        private set

    val pages: SnapshotStateList<SnapshotStateList<String?>> = mutableStateListOf()
    val dock: SnapshotStateList<String?> = mutableStateListOf()
    val folders = mutableStateMapOf<String, Folder>()
    val widgets: SnapshotStateList<WidgetSpot> = mutableStateListOf()
    /** Apps kept out of the drawer and search. */
    val hidden: SnapshotStateList<String> = mutableStateListOf()
    /** Apps that need your fingerprint to open from the launcher. */
    val locked: SnapshotStateList<String> = mutableStateListOf()

    val initialized: Boolean get() = prefs.getBoolean("initialized", false)

    init {
        load()
    }

    private fun load() {
        folders.clear()
        prefs.getString("folders", null)?.let { raw ->
            val obj = JSONObject(raw)
            obj.keys().forEach { id ->
                val f = obj.getJSONObject(id)
                val list = f.optJSONArray("apps") ?: JSONArray()
                folders[id] = Folder(id, f.optString("name", DEFAULT_FOLDER_NAME), (0 until list.length()).map { list.getString(it) }, f.optInt("emblem", -2), f.optBoolean("locked", false))
            }
        }
        fun strings(key: String) = prefs.getString(key, null)?.let { raw ->
            JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } }
        } ?: emptyList()
        hidden.clear(); hidden.addAll(strings("hidden"))
        locked.clear(); locked.addAll(strings("locked"))
        widgets.clear()
        prefs.getString("widgets", null)?.let { raw ->
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                widgets += WidgetSpot(
                    o.getInt("id"), o.optString("provider"), o.getInt("page"),
                    o.getInt("x"), o.getInt("y"), o.getInt("w"), o.getInt("h"),
                )
            }
        }
        fun clean(v: String?): String? = if (isFolderRef(v) && folderOf(v) == null) null else v

        pages.clear()
        val raw = prefs.getString("pages", null)
        if (raw != null) {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val p = arr.getJSONArray(i)
                val page = mutableStateListOf<String?>()
                for (j in 0 until slotsPerPage) page.add(clean(if (j < p.length() && !p.isNull(j)) p.getString(j) else null))
                pages.add(page)
            }
        }
        if (pages.isEmpty()) pages.add(emptyPage())
        dock.clear()
        val d = prefs.getString("dock", null)?.let { JSONArray(it) }
        for (j in 0 until dockSize) dock.add(clean(if (d != null && j < d.length() && !d.isNull(j)) d.getString(j) else null))
    }

    val slotsPerPage: Int get() = columns * rows

    private fun emptyPage() = mutableStateListOf<String?>().apply { repeat(slotsPerPage) { add(null) } }

    fun save() {
        val arr = JSONArray()
        pages.forEach { page -> arr.put(JSONArray().apply { page.forEach { put(it ?: JSONObject.NULL) } }) }
        val d = JSONArray().apply { dock.forEach { put(it ?: JSONObject.NULL) } }
        val f = JSONObject()
        folders.values.forEach { folder ->
            f.put(folder.id, JSONObject().put("name", folder.name).put("apps", JSONArray(folder.apps.toList())).put("emblem", folder.emblem).put("locked", folder.locked))
        }
        val wj = JSONArray()
        widgets.forEach { s ->
            wj.put(
                JSONObject().put("id", s.id).put("provider", s.provider).put("page", s.page)
                    .put("x", s.x).put("y", s.y).put("w", s.w).put("h", s.h)
            )
        }
        prefs.edit()
            .putString("widgets", wj.toString())
            .putString("pages", arr.toString())
            .putString("dock", d.toString())
            .putString("folders", f.toString())
            .putString("hidden", JSONArray(hidden.toList()).toString())
            .putString("locked", JSONArray(locked.toList()).toString())
            .putInt("columns", columns)
            .putString("mode", layoutMode.name)
            .putBoolean("runic_labels", runicLabels)
            .putBoolean("dock_runes", dockRunes)
            .putInt("wall_shade", wallShade)
            .remove("backdrop")   // the old in-launcher backdrop (fog testing) is gone
            .putBoolean("initialized", true)
            .apply()
    }

    // ------------------------------------------------------------ queries
    fun folderOf(ref: String?): Folder? =
        if (isFolderRef(ref)) folders[ref!!.removePrefix(FOLDER_PREFIX)] else null

    fun itemAt(loc: Loc): String? = when (loc) {
        is Loc.Home -> pages.getOrNull(loc.page)?.getOrNull(loc.slot)
        is Loc.Dock -> dock.getOrNull(loc.slot)
        is Loc.InFolder -> folders[loc.id]?.apps?.getOrNull(loc.index)
    }

    private fun setAt(loc: Loc, v: String?) {
        when (loc) {
            is Loc.Home -> pages[loc.page][loc.slot] = v
            is Loc.Dock -> dock[loc.slot] = v
            is Loc.InFolder -> Unit
        }
    }

    /** True when a widget sits over this home slot. */
    fun isCovered(page: Int, slot: Int): Boolean =
        widgets.any { it.overlaps(page, slot % columns, slot / columns, 1, 1) }

    /** True when the w x h block at x,y on [page] has no icons and no other widget. */
    fun rectFree(page: Int, x: Int, y: Int, w: Int, h: Int, ignoreWidget: Int? = null): Boolean {
        if (page !in pages.indices || x < 0 || y < 0 || x + w > columns || y + h > rows) return false
        for (cy in y until y + h) for (cx in x until x + w) if (pages[page][cy * columns + cx] != null) return false
        return widgets.none { it.id != ignoreWidget && it.overlaps(page, x, y, w, h) }
    }

    private fun holds(ref: String?, key: String) = ref == key || folderOf(ref)?.apps?.contains(key) == true

    /** True if the app is on a home page, directly or inside a folder there. */
    fun isOnHome(key: String) = pages.any { p -> p.any { holds(it, key) } }

    fun isPlaced(key: String) = isOnHome(key) || dock.any { holds(it, key) }

    /** Every app on the home pages in reading order, folders opened up (used by the Longhall view). Locked folders stay shut. */
    fun pinnedInOrder(): List<String> = pages.flatMap { p ->
        p.filterNotNull().flatMap { ref ->
            val f = folderOf(ref)
            when {
                f != null -> if (f.locked) emptyList() else f.apps.toList()
                isFolderRef(ref) -> emptyList()
                else -> listOf(ref)
            }
        }
    }

    fun isLocked(key: String) = key in locked

    /** True if [key] sits in a locked folder: such apps ask for a fingerprint wherever they're opened. */
    fun inLockedFolder(key: String) = folders.values.any { it.locked && key in it.apps }
    fun isHidden(key: String) = key in hidden

    fun setLocked(key: String, on: Boolean) {
        if (on) { if (key !in locked) locked.add(key) } else locked.remove(key)
        save()
    }

    fun setHidden(key: String, on: Boolean) {
        if (on) { if (key !in hidden) hidden.add(key) } else hidden.remove(key)
        save()
    }

    fun setFolderLocked(id: String, on: Boolean) {
        folders[id]?.locked = on
        save()
    }

    // ------------------------------------------------------------ backup
    /** Everything about the layout and launcher look, as a file you can keep. No secrets go in here. */
    fun exportJson(): JSONObject {
        val f = JSONObject()
        folders.values.forEach { folder ->
            f.put(folder.id, JSONObject().put("name", folder.name).put("apps", JSONArray(folder.apps.toList()))
                .put("emblem", folder.emblem).put("locked", folder.locked))
        }
        return JSONObject()
            .put("format", BACKUP_FORMAT).put("version", 1)
            .put("columns", columns).put("mode", layoutMode.name)
            .put("runic_labels", runicLabels).put("dock_runes", dockRunes).put("wall_shade", wallShade)
            .put("pages", JSONArray().apply { pages.forEach { page -> put(JSONArray().apply { page.forEach { put(it ?: JSONObject.NULL) } }) } })
            .put("dock", JSONArray().apply { dock.forEach { put(it ?: JSONObject.NULL) } })
            .put("folders", f)
            .put("widgets", JSONArray().apply {
                widgets.forEach { s ->
                    put(JSONObject().put("provider", s.provider).put("page", s.page).put("x", s.x).put("y", s.y).put("w", s.w).put("h", s.h))
                }
            })
            .put("hidden", JSONArray(hidden.toList()))
            .put("locked", JSONArray(locked.toList()))
    }

    /**
     * Replaces the layout with a backup from [exportJson]. [bindWidget] gets a fresh widget id for a
     * provider (or null if it can't be restored). Returns the old widget ids to delete and the
     * providers that couldn't come back.
     */
    fun importJson(o: JSONObject, bindWidget: (provider: String) -> Int?): Pair<List<Int>, List<String>> {
        require(o.optString("format") == BACKUP_FORMAT) { "Not a Yggdrasil layout backup" }
        val oldWidgets = widgets.map { it.id }
        columns = o.optInt("columns", columns).coerceIn(4, 5)
        layoutMode = runCatching { LayoutMode.valueOf(o.optString("mode", layoutMode.name)) }.getOrDefault(layoutMode)
        runicLabels = o.optBoolean("runic_labels", runicLabels)
        dockRunes = o.optBoolean("dock_runes", dockRunes)
        wallShade = o.optInt("wall_shade", wallShade).coerceIn(0, 60)

        folders.clear()
        o.optJSONObject("folders")?.let { fj ->
            fj.keys().forEach { id ->
                val x = fj.getJSONObject(id)
                val list = x.optJSONArray("apps") ?: JSONArray()
                folders[id] = Folder(id, x.optString("name", DEFAULT_FOLDER_NAME), (0 until list.length()).map { list.getString(it) },
                    x.optInt("emblem", -2), x.optBoolean("locked", false))
            }
        }
        fun ref(a: JSONArray, i: Int): String? =
            if (i < a.length() && !a.isNull(i)) a.getString(i).takeUnless { isFolderRef(it) && folderOf(it) == null } else null
        pages.clear()
        o.optJSONArray("pages")?.let { pa ->
            for (i in 0 until pa.length()) {
                val pj = pa.getJSONArray(i)
                pages.add(mutableStateListOf<String?>().apply { for (j in 0 until slotsPerPage) add(ref(pj, j)) })
            }
        }
        if (pages.isEmpty()) pages.add(emptyPage())
        val dj = o.optJSONArray("dock") ?: JSONArray()
        dock.indices.forEach { dock[it] = ref(dj, it) }

        fun strings(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        hidden.clear(); hidden.addAll(strings("hidden"))
        locked.clear(); locked.addAll(strings("locked"))

        widgets.clear()
        val missing = mutableListOf<String>()
        o.optJSONArray("widgets")?.let { wa ->
            for (i in 0 until wa.length()) {
                val w = wa.getJSONObject(i)
                val provider = w.optString("provider")
                val id = bindWidget(provider)
                if (id == null) { missing += provider; continue }
                widgets += WidgetSpot(id, provider, w.getInt("page"), w.getInt("x"), w.getInt("y"), w.getInt("w"), w.getInt("h"))
                while (pages.size <= w.getInt("page")) pages.add(emptyPage())
            }
        }
        save()
        return oldWidgets to missing
    }

    // ------------------------------------------------------------ edits
    /** Puts an item (app key or folder ref) in the first free slot, preferring [preferredPage]. */
    private fun placeAnywhere(item: String, preferredPage: Int = 0) {
        val start = preferredPage.coerceIn(0, pages.lastIndex)
        val order = (start until pages.size) + (0 until start)
        for (p in order) {
            val idx = pages[p].indices.firstOrNull { pages[p][it] == null && !isCovered(p, it) } ?: -1
            if (idx >= 0) {
                pages[p][idx] = item; return
            }
        }
        pages.add(emptyPage().also { it[0] = item })
    }

    fun addToHome(key: String, preferredPage: Int = 0): Boolean {
        if (isOnHome(key)) return false
        placeAnywhere(key, preferredPage)
        save()
        return true
    }

    fun removeFromHome(key: String) {
        pages.forEach { page -> page.indices.filter { page[it] == key }.forEach { page[it] = null } }
        folders.values.toList().filter { key in it.apps }.forEach { it.apps.remove(key); dissolveIfSmall(it) }
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

    fun addPage() {
        pages.add(emptyPage()); save()
    }

    private fun trimEmptyPages() {
        while (pages.size > 1 && pages.last().all { it == null } && widgets.none { it.page == pages.lastIndex })
            pages.removeAt(pages.lastIndex)
    }

    /**
     * Drag-and-drop. Moves the item at [src] to [dst] (a home or dock slot).
     * If [intoFolder], an app dropped on an app makes a folder, and an app
     * dropped on a folder joins it. Otherwise the two items trade places.
     */
    fun drop(src: Loc, dst: Loc, intoFolder: Boolean) {
        if (src == dst || dst is Loc.InFolder) return
        if (dst is Loc.Home && isCovered(dst.page, dst.slot)) return
        val dragged = itemAt(src) ?: return
        val target = itemAt(dst)
        val srcFolder = (src as? Loc.InFolder)?.let { folders[it.id] }
        if (srcFolder != null && target == srcFolder.ref) return // dropped back on its own folder
        val draggedIsFolder = isFolderRef(dragged)

        // lift it out of where it was
        if (srcFolder != null) srcFolder.apps.removeAt((src as Loc.InFolder).index) else setAt(src, null)

        val targetFolder = folderOf(target)
        when {
            target == null -> setAt(dst, dragged)
            intoFolder && !draggedIsFolder && targetFolder != null -> {
                if (dragged !in targetFolder.apps) targetFolder.apps.add(dragged)
            }
            intoFolder && !draggedIsFolder && !isFolderRef(target) -> {
                val f = Folder(newId(), DEFAULT_FOLDER_NAME, listOf(target, dragged))
                folders[f.id] = f
                setAt(dst, f.ref)
            }
            else -> {
                setAt(dst, dragged)
                if (srcFolder == null) setAt(src, target)
                else placeAnywhere(target, (dst as? Loc.Home)?.page ?: 0)
            }
        }
        srcFolder?.let { dissolveIfSmall(it) }
        trimEmptyPages()
        save()
    }

    // ------------------------------------------------------------ widgets
    /** Puts a widget in the first block of free cells, preferring [preferredPage]. Returns its page. */
    fun addWidget(id: Int, provider: String, w: Int, h: Int, preferredPage: Int): Int {
        val ww = w.coerceIn(1, columns)
        val hh = h.coerceIn(1, rows)
        val start = preferredPage.coerceIn(0, pages.lastIndex)
        for (p in (start until pages.size) + (0 until start)) {
            for (y in 0..rows - hh) for (x in 0..columns - ww) {
                if (rectFree(p, x, y, ww, hh)) {
                    widgets += WidgetSpot(id, provider, p, x, y, ww, hh); save(); return p
                }
            }
        }
        pages.add(emptyPage())
        widgets += WidgetSpot(id, provider, pages.lastIndex, 0, 0, ww, hh)
        save()
        return pages.lastIndex
    }

    fun moveWidget(id: Int, page: Int, x: Int, y: Int): Boolean {
        val s = widgets.firstOrNull { it.id == id } ?: return false
        if (!rectFree(page, x, y, s.w, s.h, ignoreWidget = id)) return false
        s.page = page; s.x = x; s.y = y
        trimEmptyPages(); save()
        return true
    }

    fun resizeWidget(id: Int, w: Int, h: Int): Boolean {
        val s = widgets.firstOrNull { it.id == id } ?: return false
        if (w < 1 || h < 1 || !rectFree(s.page, s.x, s.y, w, h, ignoreWidget = id)) return false
        s.w = w; s.h = h
        save()
        return true
    }

    fun removeWidget(id: Int) {
        widgets.removeAll { it.id == id }
        trimEmptyPages(); save()
    }

    fun setEmblem(id: String, emblem: Int) {
        folders[id]?.emblem = emblem
        save()
    }

    fun renameFolder(id: String, name: String) {
        folders[id]?.name = name.trim().ifBlank { DEFAULT_FOLDER_NAME }
        save()
    }

    /** Takes the apps back out: the first one takes the folder's place, the rest go to free slots. */
    fun ungroup(id: String) {
        val f = folders.remove(id) ?: return
        val apps = f.apps.toList()
        var page = 0
        pages.forEachIndexed { p, slots -> if (f.ref in slots) page = p }
        replaceRef(f.ref, apps.firstOrNull())
        apps.drop(1).forEach { placeAnywhere(it, page) }
        trimEmptyPages()
        save()
    }

    /** A folder with fewer than two apps turns back into its last app (or nothing). */
    private fun dissolveIfSmall(f: Folder) {
        if (f.apps.size >= 2) return
        replaceRef(f.ref, f.apps.firstOrNull())
        folders.remove(f.id)
    }

    private fun replaceRef(ref: String, with: String?) {
        pages.forEach { p -> p.indices.forEach { if (p[it] == ref) p[it] = with } }
        dock.indices.forEach { if (dock[it] == ref) dock[it] = with }
    }

    private fun newId(): String {
        var id = java.lang.Long.toString(System.currentTimeMillis(), 36)
        while (id in folders) id += "x"
        return id
    }

    /** Drops apps that were uninstalled (and shortcuts whose app was). */
    fun prune(valid: Set<String>, packages: Set<String>) {
        fun gone(v: String) = if (isShortcutRef(v)) shortcutPackage(v) !in packages else v !in valid
        var changed = false
        folders.values.toList().forEach { f ->
            if (f.apps.removeAll { gone(it) }) changed = true
            if (f.apps.size < 2) { dissolveIfSmall(f); changed = true }
        }
        fun bad(v: String?) = v != null && !isFolderRef(v) && gone(v)
        pages.forEach { page -> page.indices.forEach { i -> if (bad(page[i])) { page[i] = null; changed = true } } }
        dock.indices.forEach { i -> if (bad(dock[i])) { dock[i] = null; changed = true } }
        if (changed) { trimEmptyPages(); save() }
    }

    // ------------------------------------------------------------ settings
    fun updateLayoutMode(m: LayoutMode) { layoutMode = m; save() }
    fun updateRunicLabels(on: Boolean) { runicLabels = on; save() }
    fun updateDockRunes(on: Boolean) { dockRunes = on; save() }
    fun updateWallShade(pct: Int) { wallShade = pct.coerceIn(0, 60); save() }

    fun updateColumns(c: Int) {
        if (c == columns) return
        val items = pages.flatMap { it.filterNotNull() }
        columns = c
        widgets.forEach { s -> s.w = s.w.coerceAtMost(c); s.x = s.x.coerceAtMost(c - s.w) }
        pages.clear()
        repeat((widgets.maxOfOrNull { it.page } ?: 0) + 1) { pages.add(emptyPage()) }
        items.forEach { placeAnywhere(it) }
        save()
    }

    /** Replaces the whole home layout (used by the Nova import). */
    fun replaceAll(
        newColumns: Int,
        newPages: List<Map<Int, String>>,
        overflow: List<Pair<Int, String>>,
        newDock: List<String?>,
        newFolders: List<Folder>,
    ): List<Int> {
        val dropped = widgets.map { it.id }
        widgets.clear()
        columns = newColumns
        folders.clear()
        newFolders.forEach { folders[it.id] = it }
        pages.clear()
        newPages.forEach { m -> pages.add(emptyPage().also { p -> m.forEach { (slot, item) -> if (slot in p.indices) p[slot] = item } }) }
        if (pages.isEmpty()) pages.add(emptyPage())
        overflow.forEach { (page, item) -> placeAnywhere(item, page) }
        dock.indices.forEach { dock[it] = newDock.getOrNull(it) }
        trimEmptyPages()
        save()
        return dropped
    }

    fun seedDefaults(home: List<String>, dockApps: List<String>) {
        dockApps.take(dockSize).forEachIndexed { i, k -> dock[i] = k }
        home.forEach { addToHome(it) }
        save()
    }
}
