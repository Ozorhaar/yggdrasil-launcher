package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozorhaar.yggdrasil.launcher.data.AppEntry
import com.ozorhaar.yggdrasil.launcher.data.Folder
import com.ozorhaar.yggdrasil.launcher.data.Loc
import com.ozorhaar.yggdrasil.launcher.icons.ShieldIcons
import com.ozorhaar.yggdrasil.launcher.ygg

/** What is being dragged, where the finger is, and what it is hovering over. */
class DragState {
    var active by mutableStateOf(false)
    var src by mutableStateOf<Loc?>(null)
    var item by mutableStateOf<String?>(null)
    var pos by mutableStateOf(Offset.Zero)
    var start = Offset.Zero
    var startPage = 0
    var hover by mutableStateOf<Loc?>(null)
    var hoverInto by mutableStateOf(false)
    /** The scene place a dragged app has rested over (Place name, or CAR); dropping puts it in that folder. */
    var placeHover by mutableStateOf<String?>(null)
    var placeCand: String? = null
    var placeSince = 0L

    /** Set while a long-press on an icon owns the current touch, so other gestures stand down. */
    var ownedGesture = false

    /** Screen bounds of every slot currently on screen. */
    val cells = HashMap<Loc, Rect>()

    /** Widget id -> (page, screen bounds). */
    val widgetRects = HashMap<Int, Pair<Int, Rect>>()
    /** Set while a widget (not an icon) is being dragged. */
    var widgetId by mutableStateOf<Int?>(null)
    /** Finger position relative to the widget's top-left corner when it was picked up. */
    var grab = Offset.Zero
    /** Where the widget would land: page, column, row. */
    var widgetTarget by mutableStateOf<Triple<Int, Int, Int>?>(null)
    var widgetTargetOk by mutableStateOf(false)

    fun widgetAt(p: Offset, page: Int): Int? =
        widgetRects.entries.firstOrNull { (_, v) -> v.first == page && v.second.contains(p) }?.key

    fun beginWidget(id: Int, at: Offset, grabOffset: Offset, page: Int) {
        src = null; item = null; widgetId = id; grab = grabOffset
        pos = at; start = at; startPage = page
        hover = null; hoverInto = false; placeHover = null; placeCand = null; widgetTarget = null; widgetTargetOk = false
        active = true
    }

    fun begin(loc: Loc, what: String, at: Offset, page: Int) {
        src = loc; item = what; pos = at; start = at; startPage = page
        hover = null; hoverInto = false; placeHover = null; placeCand = null; active = true
    }

    fun reset() {
        active = false; src = null; item = null; hover = null; hoverInto = false; placeHover = null; placeCand = null
        widgetId = null; widgetTarget = null; widgetTargetOk = false
    }

    /** The slot under [p]. With a folder open only its slots count; otherwise the current page and the dock. */
    fun hit(p: Offset, page: Int, openFolder: String?): Loc? {
        val found = cells.entries.firstOrNull { (loc, r) ->
            r.contains(p) && when (loc) {
                is Loc.InFolder -> openFolder != null && loc.id == openFolder
                is Loc.Home -> openFolder == null && loc.page == page
                is Loc.Dock -> openFolder == null
            }
        }
        return found?.key
    }

    /** True when [p] is over the middle of the icon in slot [loc] (drop there = folder). */
    fun overCentre(p: Offset, loc: Loc): Boolean {
        val r = cells[loc] ?: return false
        val c = Offset(r.center.x, r.center.y - r.height * 0.08f)
        return (p - c).getDistance() < minOf(r.width, r.height) * 0.3f
    }
}

/** Registers this slot's on-screen bounds so drops and touches can find it. */
@Composable
fun Modifier.dropCell(drag: DragState, loc: Loc): Modifier {
    DisposableEffect(drag, loc) { onDispose { drag.cells.remove(loc) } }
    return this.onGloballyPositioned { drag.cells[loc] = it.boundsInRoot() }
}

/** A carved frame holding up to four tiny shields. */
@Composable
fun FolderIcon(folder: Folder, byKey: Map<String, AppEntry>, icons: ShieldIcons, size: Dp) {
    val shape = CutCornerShape(size * 0.18f)
    val mini = size * 0.38f
    val apps = folder.apps.mapNotNull { byKey[it] }.take(4)
    Box(
        Modifier.size(size)
            .background(Ygg.Night.copy(alpha = 0.72f), shape)
            .border(1.5.dp, Dusk.gold.copy(alpha = 0.85f), shape)
            .padding(size * 0.08f),
        contentAlignment = Alignment.Center,
    ) {
        if (folder.locked) {
            // locked: show nothing of what's inside, just its rune and a padlock
            val rune = emblemFor(folder.emblem, folder.name, folder.id)
            if (rune >= 0) RuneGlyph(rune, size * 0.5f, Ygg.GoldHi)
            Padlock(Modifier.align(Alignment.BottomEnd).offset(x = size * 0.1f, y = size * 0.1f), size * 0.3f)
            return@Box
        }
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
            for (r in 0 until 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                    for (c in 0 until 2) {
                        val app = apps.getOrNull(r * 2 + c)
                        if (app != null) ShieldImage(app, icons, mini) else Box(Modifier.size(mini))
                    }
                }
            }
        }
        val rune = emblemFor(folder.emblem, folder.name, folder.id)
        if (rune >= 0) {
            val m = size * 0.34f
            Box(
                Modifier.align(Alignment.BottomStart).offset(x = -(size * 0.16f), y = size * 0.16f)
                    .size(m)
                    .background(Ygg.Night, CutCornerShape(m * 0.3f))
                    .border(1.dp, Dusk.gold, CutCornerShape(m * 0.3f)),
                contentAlignment = Alignment.Center,
            ) { RuneGlyph(rune, m * 0.62f, Ygg.GoldHi) }
        }
    }
}

/** A small iron padlock badge. */
@Composable
fun Padlock(modifier: Modifier, size: Dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.09f
        drawCircle(Ygg.Night, radius = w / 2f)
        drawCircle(Ygg.Gold, radius = w / 2f - stroke / 2f, style = Stroke(width = stroke * 0.7f))
        // shackle
        drawArc(
            Ygg.GoldHi, startAngle = 180f, sweepAngle = 180f, useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.33f, w * 0.2f),
            size = androidx.compose.ui.geometry.Size(w * 0.34f, w * 0.34f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // body
        drawRect(
            Ygg.GoldHi,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.27f, w * 0.42f),
            size = androidx.compose.ui.geometry.Size(w * 0.46f, w * 0.34f),
        )
    }
}

/** One rune drawn as carved strokes, [height] tall. */
@Composable
fun RuneGlyph(index: Int, height: Dp, color: Color) {
    Canvas(Modifier.size(height * 0.7f, height)) {
        val h = size.height
        val w = h * 0.6f
        translate(size.width / 2f, h / 2f) {
            drawRune(RUNES[index], w, h, color, Stroke(width = (h * 0.11f).coerceAtLeast(1.5f), cap = StrokeCap.Round))
        }
    }
}

@Composable
fun FolderTile(
    folder: Folder,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    runic: Boolean,
    iconSize: Dp,
    showLabel: Boolean = true,
) {
    val badge = folder.apps.sumOf { key -> byKey[key]?.let { badges[it.packageName] } ?: 0 }
    val onWatch = rememberOnCall(folder, byKey)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(4.dp)) {
        Box {
            FolderIcon(folder, byKey, icons, iconSize)
            if (onWatch) WatchRune(Modifier.align(Alignment.TopEnd).offset(x = iconSize * 0.1f, y = -(iconSize * 0.1f)), iconSize * 0.3f)
            // with the watch rune in the top corner, the unread count moves to the other one
            Badge(badge, Modifier.align(if (onWatch) Alignment.TopStart else Alignment.TopEnd).offset(x = if (onWatch) (-6).dp else 6.dp, y = (-4).dp))
        }
        if (showLabel) TileLabel(folder.name, runic)
    }
}


/**
 * True while you're on call and this is the watch's folder: the one holding the watched widget's
 * app, or else the folder named Work. Re-checked every minute, so a shift that starts while the
 * widget hasn't refreshed still lights the rune.
 */
@Composable
fun rememberOnCall(folder: Folder, byKey: Map<String, AppEntry>): Boolean {
    val ygg = androidx.compose.ui.platform.LocalContext.current.ygg
    val texts by ygg.watch.texts.collectAsState()
    if (ygg.watch.widgetId < 0) return false
    val pkg = ygg.watch.providerPackage
    val mine = folder.apps.any { byKey[it]?.packageName == pkg } || folder.name.trim().equals("work", ignoreCase = true)
    if (!mine) return false
    var tick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000L); tick = System.currentTimeMillis() } }
    return com.ozorhaar.yggdrasil.launcher.watch.Watch.isOnCall(texts, tick)
}

/** On call: dagaz, the rune of day and waking, in frost on a night disc (the padlock's size). */
@Composable
fun WatchRune(modifier: Modifier, size: Dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(Ygg.Night, radius = w / 2f)
        drawCircle(Ygg.Frost, radius = w / 2f - w * 0.04f, style = Stroke(width = w * 0.07f))
        val s = w * 0.09f
        val l = w * 0.27f; val r = w * 0.73f; val t = w * 0.3f; val b = w * 0.7f
        drawLine(Ygg.Frost, Offset(l, t), Offset(l, b), s, StrokeCap.Round)
        drawLine(Ygg.Frost, Offset(r, t), Offset(r, b), s, StrokeCap.Round)
        drawLine(Ygg.Frost, Offset(l, t), Offset(r, b), s, StrokeCap.Round)
        drawLine(Ygg.Frost, Offset(l, b), Offset(r, t), s, StrokeCap.Round)
    }
}
