package com.ozorhaar.yggdrasil.launcher.ui

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import com.ozorhaar.yggdrasil.launcher.REQ_CONFIGURE_WIDGET
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ozorhaar.yggdrasil.launcher.data.NovaImport
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.ozorhaar.yggdrasil.launcher.data.FOLDER_PREFIX
import com.ozorhaar.yggdrasil.launcher.data.Folder
import com.ozorhaar.yggdrasil.launcher.search.SearchOverlay
import com.ozorhaar.yggdrasil.launcher.security.Guard
import com.ozorhaar.yggdrasil.launcher.data.Loc
import com.ozorhaar.yggdrasil.launcher.data.isFolderRef
import kotlin.math.roundToInt
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozorhaar.yggdrasil.launcher.applyYggWallpaper
import com.ozorhaar.yggdrasil.launcher.badgeAccessGranted
import com.ozorhaar.yggdrasil.launcher.data.AppEntry
import com.ozorhaar.yggdrasil.launcher.data.LayoutMode
import com.ozorhaar.yggdrasil.launcher.data.LayoutStore
import com.ozorhaar.yggdrasil.launcher.expandNotifications
import com.ozorhaar.yggdrasil.launcher.icons.ShieldIcons
import com.ozorhaar.yggdrasil.launcher.openAssistant
import com.ozorhaar.yggdrasil.launcher.openSettings
import com.ozorhaar.yggdrasil.launcher.openUrl
import com.ozorhaar.yggdrasil.launcher.services.Badges
import com.ozorhaar.yggdrasil.launcher.services.LockService
import com.ozorhaar.yggdrasil.launcher.uninstall
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class From { HOME, DOCK, DRAWER, HALL }

private data class MenuTarget(val app: AppEntry, val from: From)

@Composable
fun LauncherRoot() {
    val ctx = LocalContext.current
    val ygg = ctx.ygg
    val layout = ygg.layout
    val apps by ygg.repo.apps.collectAsStateWithLifecycle()
    val badges by Badges.counts.collectAsStateWithLifecycle()
    val pinnedShortcuts by ygg.repo.shortcuts.collectAsStateWithLifecycle()
    val byKey = remember(apps, pinnedShortcuts) { (apps + pinnedShortcuts).associateBy { it.key } }
    val byKeyNow by rememberUpdatedState(byKey)

    val density = LocalDensity.current
    val icons = remember {
        val dpi = ctx.resources.displayMetrics.densityDpi
        ShieldIcons(with(density) { 64.dp.roundToPx() }) { ygg.repo.iconFor(it, dpi) }
    }
    val haptic = LocalHapticFeedback.current
    val topZonePx = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.toPx() }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var widgetMenu by remember { mutableStateOf<Int?>(null) }
    var pendingWidget by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }
    var pendingPage by remember { mutableStateOf(0) }
    val drag = remember { DragState() }
    var dockRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val rootSize = remember { IntArray(2) }

    val pager = rememberPagerState(pageCount = { layout.pages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) layout.prune(apps.map { it.key }.toSet(), apps.map { it.packageName }.toSet())
    }
    // a folder that dissolved (or was ungrouped) closes itself
    openFolder?.let { if (!layout.folders.containsKey(it)) openFolder = null }

    // Tell the wallpaper we're the home screen and where we are on it.
    val view = LocalView.current
    val wallpaper = remember { WallpaperManager.getInstance(ctx) }
    LaunchedEffect(view, layout.layoutMode) {
        snapshotFlow { Triple(pager.currentPage, pager.currentPageOffsetFraction, layout.pages.size) }
            .collect { (page, frac, count) ->
                val token = view.windowToken ?: return@collect
                val steps = if (count > 1) 1f / (count - 1) else 0f
                val x = if (count > 1 && layout.layoutMode == LayoutMode.GRID)
                    ((page + frac) / (count - 1)).coerceIn(0f, 1f) else 0.5f
                runCatching {
                    wallpaper.setWallpaperOffsetSteps(steps, 0f)
                    wallpaper.setWallpaperOffsets(token, x, 0.5f)
                }
            }
    }
    DisposableEffect(view) {
        val listener = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {
                runCatching { wallpaper.setWallpaperOffsets(v.windowToken, 0.5f, 0.5f) }
            }
            override fun onViewDetachedFromWindow(v: android.view.View) {}
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.windowToken != null) runCatching { wallpaper.setWallpaperOffsets(view.windowToken, 0.5f, 0.5f) }
        onDispose { view.removeOnAttachStateChangeListener(listener) }
    }
    LaunchedEffect(Unit) {
        ygg.homePressed.collect {
            drawerOpen = false; settingsOpen = false; searchOpen = false; menu = null; openFolder = null; drag.reset()
            homeMenu = false; widgetPicker = false; widgetMenu = null
            scope.launch { pager.animateScrollToPage(0) }
        }
    }

    BackHandler(enabled = true) {
        when {
            menu != null -> menu = null
            widgetMenu != null -> widgetMenu = null
            homeMenu -> homeMenu = false
            widgetPicker -> widgetPicker = false
            openFolder != null -> openFolder = null
            settingsOpen -> settingsOpen = false
            searchOpen -> searchOpen = false
            drawerOpen -> drawerOpen = false
        }
    }

    /** Opens an app, asking for your fingerprint first if you've locked it. */
    fun launch(app: AppEntry) {
        if (layout.isLocked(app.key) || (app.isShortcut && layout.isLocked(app.packageName)))
            Guard.check(ctx, "Open ${app.label}") { ygg.repo.launch(app) }
        else ygg.repo.launch(app)
    }

    /** Opens a folder, asking for your fingerprint first if it's locked. */
    fun showFolder(f: Folder) {
        if (f.locked) Guard.check(ctx, "Open ${f.name}") { openFolder = f.id } else openFolder = f.id
    }

    // ---------------------------------------------------------- widgets
    val widgetManager = ygg.widgetManager
    val widgetHost = ygg.widgetHost

    fun placePendingWidget() {
        val (id, info) = pendingWidget ?: return
        pendingWidget = null
        val (w, h) = cellsFor(ctx, info, layout.columns, layout.rows)
        val page = layout.addWidget(id, info.provider.flattenToString(), w, h, pendingPage)
        scope.launch { delay(50); pager.animateScrollToPage(page) }
    }

    fun cancelPendingWidget() {
        pendingWidget?.let { (id, _) -> runCatching { widgetHost.deleteAppWidgetId(id) } }
        pendingWidget = null
    }

    fun configureOrPlace() {
        val (id, info) = pendingWidget ?: return
        val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        val activity = ctx as? android.app.Activity
        if (info.configure != null && !optional && activity != null) {
            val started = runCatching {
                widgetHost.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE_WIDGET, null)
            }.isSuccess
            if (!started) placePendingWidget()
        } else placePendingWidget()
    }

    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) configureOrPlace() else cancelPendingWidget()
    }
    LaunchedEffect(Unit) {
        ygg.widgetConfigResult.collect { code ->
            if (code == android.app.Activity.RESULT_OK) placePendingWidget() else cancelPendingWidget()
        }
    }

    fun addWidget(info: AppWidgetProviderInfo) {
        widgetPicker = false
        val id = widgetHost.allocateAppWidgetId()
        pendingWidget = id to info
        pendingPage = pager.currentPage
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.provider)) configureOrPlace()
        else bindLauncher.launch(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
        )
    }

    // an app's shortcut entry from the widget list: run its setup screen, then pin what it hands back
    val shortcutSetup = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val key = ygg.repo.acceptPin(r.data)
        if (key != null) layout.addToHome(key, pendingPage)
        else Toast.makeText(ctx, "That app didn't hand back a shortcut", Toast.LENGTH_SHORT).show()
    }

    fun addShortcutFromList(info: android.content.pm.LauncherActivityInfo) {
        widgetPicker = false
        pendingPage = pager.currentPage
        val sender = ygg.repo.shortcutConfigIntent(info)
        if (sender == null) {
            Toast.makeText(ctx, "Couldn't open that shortcut", Toast.LENGTH_SHORT).show(); return
        }
        shortcutSetup.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
    }

    fun removeWidget(id: Int) {
        layout.removeWidget(id)
        ygg.widgetViews.remove(id)
        runCatching { widgetHost.deleteAppWidgetId(id) }
        widgetMenu = null
    }

    /** Where a dragged widget would land: the cell nearest its top-left corner. */
    fun updateWidgetTarget() {
        val spot = layout.widgets.firstOrNull { it.id == drag.widgetId } ?: return
        val page = pager.currentPage
        val r0 = drag.cells[Loc.Home(page, 0)] ?: return
        val tl = drag.pos - drag.grab
        val x = ((tl.x - r0.left) / r0.width).roundToInt()
        val y = ((tl.y - r0.top) / r0.height).roundToInt()
        drag.widgetTarget = Triple(page, x, y)
        drag.widgetTargetOk = layout.rectFree(page, x, y, spot.w, spot.h, ignoreWidget = spot.id)
    }

    // ---------------------------------------------------------- drag and drop
    fun updateHover() {
        if (drag.widgetId != null) { updateWidgetTarget(); return }
        val loc = drag.hit(drag.pos, pager.currentPage, null)
        drag.hover = loc
        val target = loc?.let { layout.itemAt(it) }
        drag.hoverInto = loc != null && loc != drag.src && target != null &&
            !isFolderRef(drag.item) && target != (drag.src as? Loc.InFolder)?.let { FOLDER_PREFIX + it.id } &&
            drag.overCentre(drag.pos, loc)
    }

    /** True only when an actual icon (not an empty slot) is under [p]. */
    fun iconAt(p: Offset): Boolean =
        drag.hit(p, pager.currentPage, null)?.let { layout.itemAt(it) } != null ||
            drag.widgetAt(p, pager.currentPage) != null

    fun tapItem(loc: Loc, item: String) {
        val folder = layout.folderOf(item)
        when {
            folder != null -> showFolder(folder)
            else -> byKeyNow[item]?.let { app ->
                if (loc is Loc.InFolder) openFolder = null
                launch(app)
            }
        }
    }

    fun finishDrag() {
        drag.widgetId?.let { id ->
            val moved = (drag.pos - drag.start).getDistance() > with(density) { 14.dp.toPx() } ||
                pager.currentPage != drag.startPage
            if (!moved) widgetMenu = id
            else {
                updateWidgetTarget()
                val t = drag.widgetTarget
                if (t != null && drag.widgetTargetOk) layout.moveWidget(id, t.first, t.second, t.third)
            }
            drag.reset()
            return
        }
        val src = drag.src ?: return drag.reset()
        val item = drag.item ?: return drag.reset()
        val moved = (drag.pos - drag.start).getDistance() > with(density) { 14.dp.toPx() } ||
            pager.currentPage != drag.startPage
        if (!moved) {
            // a long-press that stayed put: options for that icon
            val folder = layout.folderOf(item)
            if (folder != null) showFolder(folder)
            else byKeyNow[item]?.let { menu = MenuTarget(it, if (src is Loc.Dock) From.DOCK else From.HOME) }
        } else {
            updateHover()
            drag.hover?.let { dst -> layout.drop(src, dst, drag.hoverInto) }
        }
        drag.reset()
    }

    // hold at the left/right edge while dragging to turn the page
    LaunchedEffect(drag.active) {
        if (!drag.active) return@LaunchedEffect
        val edge = with(density) { 28.dp.toPx() }
        var since = 0L
        while (drag.active) {
            delay(80)
            updateHover()
            val x = drag.pos.x
            val dir = when {
                layout.layoutMode != LayoutMode.GRID -> 0
                x < edge -> -1
                x > rootSize[0] - edge -> 1
                else -> 0
            }
            if (dir == 0) { since = 0L; continue }
            val now = SystemClock.uptimeMillis()
            if (since == 0L) { since = now; continue }
            if (now - since < 600) continue
            since = 0L
            val to = pager.currentPage + dir
            if (to < 0) continue
            if (to >= layout.pages.size) {
                if (layout.pages.last().all { it == null } && layout.widgets.none { it.page == layout.pages.lastIndex }) continue
                layout.addPage()
                delay(32)
            }
            pager.animateScrollToPage(to)
            updateHover()
        }
    }

    val overlayOpen = drawerOpen || settingsOpen || searchOpen || menu != null || openFolder != null ||
        homeMenu || widgetPicker || widgetMenu != null

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootSize[0] = it.size.width; rootSize[1] = it.size.height }
            // Icons: tap to open, hold to pick up, drag to move, drop on an app to make a folder.
            // Watches the Initial pass so a drag in progress can keep the pager from scrolling.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    drag.ownedGesture = false
                    if (drawerOpen || settingsOpen || searchOpen || menu != null || homeMenu || widgetPicker || widgetMenu != null)
                        return@awaitEachGesture
                    val widget = if (openFolder == null) drag.widgetAt(down.position, pager.currentPage) else null
                    val loc = if (widget != null) null else drag.hit(down.position, pager.currentPage, openFolder)
                    val item = loc?.let { layout.itemAt(it) }
                    if (widget == null && item == null) return@awaitEachGesture

                    // 1 = lifted (tap), 2 = moved or cancelled, null = held long enough
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var r = 0
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id }
                            if (c == null) { r = 2; break }
                            if (!c.pressed) { r = 1; break }
                            if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) { r = 2; break }
                        }
                        r
                    }
                    // taps on a widget belong to the widget itself
                    if (outcome == 1) { if (loc != null && item != null) tapItem(loc, item); return@awaitEachGesture }
                    if (outcome == 2) return@awaitEachGesture

                    drag.ownedGesture = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (widget != null) {
                        val r = drag.widgetRects[widget]!!.second
                        drag.beginWidget(widget, down.position, down.position - r.topLeft, pager.currentPage)
                    } else {
                        drag.begin(loc!!, item!!, down.position, pager.currentPage)
                        if (loc is Loc.InFolder) openFolder = null
                    }
                    try {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                            c.consume()
                            if (!c.pressed) break
                            drag.pos = c.position
                            updateHover()
                        }
                    } finally {
                        if (drag.active) finishDrag()
                    }
                }
            }
            .pointerInput(overlayOpen) {
                if (overlayOpen) return@pointerInput
                detectTapGestures(
                    // a tap on the dock's rune band (not an icon) wakes Hafnir
                    onTap = { p ->
                        if (iconAt(p) || !dockRect.contains(p)) return@detectTapGestures
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ctx.openAssistant()
                    },
                    onDoubleTap = { p ->
                        if (iconAt(p)) return@detectTapGestures
                        if (!LockService.lockScreen()) {
                            val msg = if (LockService.isEnabledInSettings(ctx))
                                "Lock service is on but not running yet. Switch it off and on in Accessibility."
                            else "Turn on “Yggdrasil double-tap to lock” in Accessibility"
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onLongPress = { p ->
                        if (drag.ownedGesture || iconAt(p)) return@detectTapGestures
                        homeMenu = true
                    },
                )
            }
            .pointerInput(overlayOpen) {
                if (overlayOpen) return@pointerInput
                var total = 0f
                var fromTop = false
                // the top strip (status bar and just below it) pulls notifications; anywhere else opens search
                val topZone = topZonePx
                detectVerticalDragGestures(
                    onDragStart = { start -> total = 0f; fromTop = start.y < topZone },
                    onDragEnd = {
                        if (drag.ownedGesture) return@detectVerticalDragGestures
                        if (total < -140f) drawerOpen = true
                        else if (total > 140f) { if (fromTop) ctx.expandNotifications() else searchOpen = true }
                    },
                ) { _, dy -> total += dy }
            }
    ) {
        // wallpaper shade: a little darker at the top (clock/status bar) and bottom (dock)
        if (layout.wallShade > 0) {
            val a = layout.wallShade / 100f
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = (a * 1.3f).coerceAtMost(0.85f)),
                        0.35f to Color.Black.copy(alpha = a * 0.8f),
                        0.7f to Color.Black.copy(alpha = a * 0.9f),
                        1f to Color.Black.copy(alpha = (a * 1.4f).coerceAtMost(0.9f)),
                    )
                )
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (layout.layoutMode) {
                    LayoutMode.GRID -> HomeGrid(layout, byKey, icons, badges, pager, drag)
                    LayoutMode.LONGHALL -> Longhall(
                        layout, byKey, icons, badges,
                        onTap = { launch(it) },
                        onLong = { menu = MenuTarget(it, From.HALL) },
                    )
                }
            }
            if (layout.layoutMode == LayoutMode.GRID) PageMarks(layout.pages.size, pager.currentPage)
            Dock(layout, byKey, icons, badges, drag, onPlaced = { dockRect = it })
        }

        openFolder?.let { id -> layout.folders[id] }?.let { folder ->
            FolderPopup(folder, layout, byKey, icons, badges, drag, onClose = { openFolder = null })
        }

        // the icon riding under your finger
        if (drag.active) {
            val item = drag.item
            val size = 62.dp
            val half = with(density) { (size / 2).toPx() }
            Box(
                Modifier
                    .offset { IntOffset((drag.pos.x - half).roundToInt(), (drag.pos.y - half * 1.4f).roundToInt()) }
                    .size(size)
                    .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.92f }
            ) {
                val folder = layout.folderOf(item)
                if (folder != null) FolderIcon(folder, byKey, icons, size)
                else item?.let { byKey[it] }?.let { ShieldImage(it, icons, size) }
            }
        }

        AnimatedVisibility(
            drawerOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AppDrawer(
                apps = apps.filter { !layout.isHidden(it.key) }, icons = icons, badges = badges, runic = layout.runicLabels,
                onLaunch = { drawerOpen = false; launch(it) },
                onLong = { menu = MenuTarget(it, From.DRAWER) },
                onClose = { drawerOpen = false },
                onWidgets = { drawerOpen = false; widgetPicker = true },
                onSettings = { drawerOpen = false; settingsOpen = true },
            )
        }

        AnimatedVisibility(
            searchOpen,
            enter = slideInVertically { -it / 3 } + fadeIn(),
            exit = slideOutVertically { -it / 3 } + fadeOut(),
        ) {
            SearchOverlay(
                apps = apps.filter { !layout.isHidden(it.key) },
                icons = icons,
                home = ygg.homeLink,
                onLaunch = { launch(it) },
                onClose = { searchOpen = false },
            )
        }

        AnimatedVisibility(settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsPanel(layout, onClose = { settingsOpen = false })
        }

        if (homeMenu) HomeMenu(
            onWidgets = { homeMenu = false; widgetPicker = true },
            onSettings = { homeMenu = false; settingsOpen = true },
            onClose = { homeMenu = false },
        )
        AnimatedVisibility(widgetPicker, enter = fadeIn(), exit = fadeOut()) {
            WidgetPicker(
                layout.columns, layout.rows,
                onPick = { addWidget(it) },
                onPickShortcut = { addShortcutFromList(it) },
                onClose = { widgetPicker = false },
            )
        }
        widgetMenu?.let { id -> layout.widgets.firstOrNull { it.id == id } }?.let { spot ->
            WidgetSheet(spot, layout, onRemove = { removeWidget(spot.id) }, onClose = { widgetMenu = null })
        }

        menu?.let { target ->
            ActionSheet(
                target = target,
                layout = layout,
                icons = icons,
                onDismiss = { menu = null },
            )
        }
    }
}

// =================================================================== home grid

/** Shows what a slot holds, plus the drag feedback (lifted, drop here, make a folder). */
@Composable
private fun SlotContent(
    loc: Loc,
    item: String?,
    layout: LayoutStore,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    drag: DragState,
    iconSize: Dp,
    showLabel: Boolean,
) {
    if (loc is Loc.Home && layout.isCovered(loc.page, loc.slot)) return
    val lifted = drag.active && drag.src == loc
    val hovered = drag.active && drag.widgetId == null && drag.hover == loc && !lifted
    Box(contentAlignment = Alignment.TopCenter) {
        if (hovered && drag.hoverInto) {
            // drop here to gather into a folder
            Box(
                Modifier.padding(top = 4.dp).size(iconSize)
                    .graphicsLayer { scaleX = 1.22f; scaleY = 1.22f }
                    .background(Ygg.Gold.copy(alpha = 0.22f), CutCornerShape(iconSize * 0.18f))
                    .border(2.dp, Ygg.GoldHi, CutCornerShape(iconSize * 0.18f))
            )
        } else if (hovered || (drag.active && drag.widgetId == null && item == null)) {
            Box(
                Modifier.padding(top = 4.dp).size(iconSize)
                    .border(
                        1.5.dp,
                        if (hovered) Ygg.GoldHi else Ygg.Gold.copy(alpha = 0.3f),
                        CircleShape,
                    )
            )
        }
        Box(Modifier.graphicsLayer { alpha = if (lifted) 0.25f else 1f }) {
            val folder = layout.folderOf(item)
            val app = item?.let { byKey[it] }
            when {
                folder != null -> FolderTile(folder, byKey, icons, badges, layout.runicLabels, iconSize, showLabel)
                app != null -> AppTile(app, icons, badges[app.packageName] ?: 0, layout.runicLabels, iconSize, showLabel = showLabel)
            }
        }
    }
}

@Composable
private fun HomeGrid(
    layout: LayoutStore,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    pager: androidx.compose.foundation.pager.PagerState,
    drag: DragState,
) {
    val cols = layout.columns
    val rows = layout.rows
    HorizontalPager(state = pager, userScrollEnabled = !drag.active, modifier = Modifier.fillMaxSize()) { pageIdx ->
        val page = layout.pages.getOrNull(pageIdx) ?: return@HorizontalPager
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val iconSize = minOf(maxWidth / cols * 0.62f, 60.dp)
            val cellW = maxWidth / cols
            val cellH = maxHeight / rows
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until cols) {
                            val slot = r * cols + c
                            val loc = Loc.Home(pageIdx, slot)
                            Box(
                                Modifier.weight(1f).fillMaxHeight().dropCell(drag, loc),
                                contentAlignment = Alignment.Center,
                            ) {
                                SlotContent(loc, page.getOrNull(slot), layout, byKey, icons, badges, drag, iconSize, true)
                            }
                        }
                    }
                }
            }
            // widgets, laid over the cells they cover
            layout.widgets.filter { it.page == pageIdx }.forEach { spot ->
                key(spot.id) {
                    val lifted = drag.active && drag.widgetId == spot.id
                    Box(
                        Modifier
                            .offset(cellW * spot.x, cellH * spot.y)
                            .size(cellW * spot.w, cellH * spot.h)
                            .widgetCell(drag, spot.id, pageIdx)
                            .padding(4.dp)
                            .graphicsLayer { alpha = if (lifted) 0.35f else 1f }
                    ) {
                        WidgetView(spot, cellW * spot.w - 8.dp, cellH * spot.h - 8.dp)
                    }
                }
            }
            // where a dragged widget would land
            val t = drag.widgetTarget
            val moving = layout.widgets.firstOrNull { it.id == drag.widgetId }
            if (drag.active && moving != null && t != null && t.first == pageIdx) {
                Box(
                    Modifier
                        .offset(cellW * t.second, cellH * t.third)
                        .size(cellW * moving.w, cellH * moving.h)
                        .padding(4.dp)
                        .background((if (drag.widgetTargetOk) Ygg.Gold else Ygg.Ember).copy(alpha = 0.15f), CutCornerShape(12.dp))
                        .border(2.dp, if (drag.widgetTargetOk) Ygg.GoldHi else Ygg.Ember, CutCornerShape(12.dp))
                )
            }
        }
    }
}

/** Page indicator: small carved diamonds. */
@Composable
private fun PageMarks(count: Int, current: Int) {
    if (count <= 1) {
        Spacer(Modifier.height(10.dp)); return
    }
    Row(
        Modifier.fillMaxWidth().height(18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 5.dp).size(if (i == current) 9.dp else 7.dp).rotate(45f)
                    .background(if (i == current) Ygg.Gold else Ygg.Bone.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun Dock(
    layout: LayoutStore,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    drag: DragState,
    onPlaced: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
            .fillMaxWidth()
            .background(Ygg.Night.copy(alpha = 0.55f), RoundedCornerShape(34.dp))
            .then(
                if (layout.dockRunes) Modifier.runeBorder(34.dp, 14.dp, Ygg.Gold).padding(horizontal = 12.dp, vertical = 14.dp)
                else Modifier.border(1.dp, Ygg.Gold.copy(alpha = 0.35f), RoundedCornerShape(34.dp)).padding(vertical = 6.dp)
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        layout.dock.forEachIndexed { i, key ->
            val loc = Loc.Dock(i)
            Box(Modifier.size(64.dp).dropCell(drag, loc), contentAlignment = Alignment.Center) {
                SlotContent(loc, key, layout, byKey, icons, badges, drag, 52.dp, false)
            }
        }
    }
}

// =================================================================== folder

@Composable
private fun FolderPopup(
    folder: Folder,
    layout: LayoutStore,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    drag: DragState,
    onClose: () -> Unit,
) {
    var editing by remember(folder.id) { mutableStateOf(false) }
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    var picking by remember(folder.id) { mutableStateOf(false) }
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun commit() {
        layout.renameFolder(folder.id, name); name = folder.name; editing = false; keyboard?.hide()
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xAA000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (editing) commit()
                onClose()
            }
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(Ygg.Night2.copy(alpha = 0.97f), CutCornerShape(18.dp))
                .border(1.5.dp, Ygg.Gold.copy(alpha = 0.7f), CutCornerShape(18.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        singleLine = true,
                        textStyle = TextStyle(color = Ygg.Bone, fontSize = 18.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { commit() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ygg.Gold, unfocusedBorderColor = Ygg.Gold.copy(alpha = 0.4f), cursorColor = Ygg.Gold,
                        ),
                        shape = CutCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    RuneButton("Save") { commit() }
                } else {
                    Box(Modifier.weight(1f).clickable { name = folder.name; editing = true }) {
                        RuneText(folder.name, 26.sp, Ygg.Gold)
                    }
                    RuneButton("Rename") { name = folder.name; editing = true }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                val shown = emblemFor(folder.emblem, folder.name, folder.id)
                Box(
                    Modifier.size(34.dp).background(Ygg.Night, CutCornerShape(10.dp))
                        .border(1.dp, Ygg.Gold, CutCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) { if (shown >= 0) RuneGlyph(shown, 21.dp, Ygg.GoldHi) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    RuneText(
                        when {
                            shown < 0 -> "No emblem"
                            folder.emblem == EMBLEM_AUTO -> RUNE_NAMES[shown] + " (from the name)"
                            else -> RUNE_NAMES[shown]
                        },
                        14.sp, Ygg.Bone,
                    )
                }
                RuneButton(if (picking) "Done" else "Emblem") { picking = !picking }
            }
            if (picking) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    @Composable
                    fun choice(selected: Boolean, onPick: () -> Unit, content: @Composable () -> Unit) = Box(
                        Modifier.size(42.dp)
                            .background(if (selected) Ygg.Gold.copy(alpha = 0.25f) else Ygg.Night, CutCornerShape(10.dp))
                            .border(1.dp, Ygg.Gold.copy(alpha = if (selected) 1f else 0.4f), CutCornerShape(10.dp))
                            .clickable { onPick() },
                        contentAlignment = Alignment.Center,
                    ) { content() }
                    choice(folder.emblem == EMBLEM_AUTO, { layout.setEmblem(folder.id, EMBLEM_AUTO) }) { RuneText("Auto", 11.sp, Ygg.Bone) }
                    choice(folder.emblem == EMBLEM_NONE, { layout.setEmblem(folder.id, EMBLEM_NONE) }) { RuneText("None", 11.sp, Ygg.Bone) }
                    RUNE_NAMES.indices.forEach { i ->
                        choice(folder.emblem == i, { layout.setEmblem(folder.id, i) }) { RuneGlyph(i, 24.dp, Ygg.GoldHi) }
                    }
                }
            }
            Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp)) {
                drawLine(Ygg.Gold.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
            }
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                folder.apps.toList().chunked(4).forEachIndexed { r, rowKeys ->
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 4) {
                            val idx = r * 4 + c
                            val key = rowKeys.getOrNull(c)
                            val cell = Modifier.weight(1f).height(92.dp)
                            if (key == null) Spacer(cell)
                            else Box(cell.dropCell(drag, Loc.InFolder(folder.id, idx)), contentAlignment = Alignment.Center) {
                                byKey[key]?.let { app ->
                                    AppTile(app, icons, badges[app.packageName] ?: 0, layout.runicLabels, 52.dp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RuneText("Hold and drag an app out to take it out", 13.sp, Ygg.Ash) }
                if (folder.locked) RuneButton("Unlock") {
                    Guard.check(ctx, "Unlock ${folder.name}", fresh = true) { layout.setFolderLocked(folder.id, false) }
                } else RuneButton("Lock") {
                    if (Guard.available(ctx)) layout.setFolderLocked(folder.id, true)
                    else Toast.makeText(ctx, "Set up a fingerprint or screen lock first", Toast.LENGTH_LONG).show()
                }
                Spacer(Modifier.width(8.dp))
                RuneButton("Ungroup") {
                    if (folder.locked) Guard.check(ctx, "Ungroup ${folder.name}", fresh = true) { layout.ungroup(folder.id); onClose() }
                    else { layout.ungroup(folder.id); onClose() }
                }
            }
        }
    }
}

// =================================================================== longhall

@Composable
private fun Longhall(
    layout: LayoutStore,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    onTap: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val time by produceState(initialValue = "") {
        while (true) {
            value = android.text.format.DateFormat.getTimeFormat(ctx).format(Date())
            delay(1000)
        }
    }
    val date by produceState(initialValue = "") {
        while (true) {
            value = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }
    val pinned = layout.pinnedInOrder().mapNotNull { byKey[it] }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        // the runestone
        Column(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Ygg.Iron.copy(alpha = 0.85f), Ygg.Night.copy(alpha = 0.85f))),
                    CutCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                )
                .border(
                    1.5.dp, Ygg.Gold.copy(alpha = 0.7f),
                    CutCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
                )
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RuneText(time, 58.sp, Ygg.Gold)
            RuneText(date, 18.sp, Ygg.Bone)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .clickable { ctx.openAssistant() }
                    .border(1.dp, Ygg.Frost.copy(alpha = 0.6f), CutCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).rotate(45f).background(Ygg.Frost))
                Spacer(Modifier.width(10.dp))
                RuneText("Hafnir", 18.sp, Ygg.Frost)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(pinned, key = { it.key }) { app ->
                HallRow(app, icons, badges[app.packageName] ?: 0, layout.runicLabels, onTap, onLong)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HallRow(
    app: AppEntry,
    icons: ShieldIcons,
    badge: Int,
    runic: Boolean,
    onTap: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = { onTap(app) }, onLongClick = { onLong(app) })
    ) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                ShieldImage(app, icons, 40.dp)
                Badge(badge, Modifier.align(Alignment.TopEnd))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                app.label,
                style = TextStyle(
                    color = Ygg.Bone, fontSize = 22.sp, fontFamily = if (runic) Ygg.Runic else null, shadow = labelShadow,
                ),
            )
        }
        // a carved, slightly slanted divider
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(Ygg.Gold.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
        }
    }
}

// =================================================================== drawer

@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    runic: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
    onClose: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f) {
                    pull += available.y
                    if (pull > 260f) {
                        pull = 0f; onClose()
                    }
                } else if (available.y < 0f) pull = 0f
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pull = 0f
                return Velocity.Zero
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ygg.Night.copy(alpha = 0.94f), Ygg.Night2.copy(alpha = 0.97f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { RuneText("Search the hoard", 16.sp, Ygg.Ash) },
                textStyle = TextStyle(color = Ygg.Bone, fontSize = 17.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    shown.firstOrNull()?.let { keyboard?.hide(); onLaunch(it) }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ygg.Gold, unfocusedBorderColor = Ygg.Gold.copy(alpha = 0.4f), cursorColor = Ygg.Gold,
                ),
                shape = CutCornerShape(10.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            RuneButton("Close") { keyboard?.hide(); onClose() }
        }
        // always reachable, even when the home screen has no empty space to hold
        Row(Modifier.padding(start = 16.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuneButton("Widgets") { keyboard?.hide(); onWidgets() }
            RuneButton("Settings") { keyboard?.hide(); onSettings() }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().nestedScroll(pullToClose).padding(horizontal = 8.dp),
        ) {
            items(shown, key = { it.key }) { app ->
                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    AppTile(
                        app, icons, badges[app.packageName] ?: 0, runic, 54.dp,
                        onClick = { keyboard?.hide(); onLaunch(app) },
                        onLongClick = { onLong(app) },
                    )
                }
            }
        }
    }
}

// =================================================================== action sheet

private fun repo0(ctx: android.content.Context) = ctx.ygg.repo

@Composable
private fun ActionSheet(
    target: MenuTarget,
    layout: LayoutStore,
    icons: ShieldIcons,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = target.app
    val onHome = layout.isOnHome(app.key)
    val inDock = app.key in layout.dock
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp)
                .background(Ygg.Night2, CutCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .border(1.dp, Ygg.Gold.copy(alpha = 0.6f), CutCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldImage(app, icons, 44.dp)
                Spacer(Modifier.width(14.dp))
                RuneText(app.label, 22.sp, Ygg.Gold)
            }
            Spacer(Modifier.height(10.dp))
            // its waiting notifications, unless it's locked or tucked in a locked folder
            val guarded = layout.isLocked(app.key) || layout.folders.values.any { it.locked && app.key in it.apps }
            val previews = Badges.previews.collectAsStateWithLifecycle().value[app.packageName].orEmpty()
            if (previews.isNotEmpty() && !app.isShortcut) {
                if (guarded) RuneText("${previews.size} waiting (locked: open the app to read)", 14.sp, Ygg.Ash)
                else previews.forEach { pv ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            runCatching {
                                val opts = if (android.os.Build.VERSION.SDK_INT >= 34)
                                    android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                                        android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
                                else null
                                if (pv.open != null) pv.open.send(ctx, 0, null, null, null, null, opts)
                                else repo0(ctx).launch(app)
                            }
                            onDismiss()
                        }.padding(vertical = 5.dp),
                    ) {
                        if (pv.title.isNotBlank()) RuneText(pv.title, 15.sp, Ygg.Gold)
                        if (pv.text.isNotBlank()) Text(pv.text, color = Ygg.Bone, fontSize = 14.sp, maxLines = 3)
                    }
                }
                Canvas(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp)) {
                    drawLine(Ygg.Gold.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
                }
            }
            val repo = ctx.ygg.repo
            // the app's own shortcuts (Home Assistant's Assist, a contact, a new message...)
            val shortcuts = remember(app.key) { repo.shortcutsFor(app) }
            if (shortcuts.isNotEmpty()) {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    shortcuts.forEach { sc ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (layout.isLocked(app.key)) Guard.check(ctx, "Open ${app.label}") { repo.launchShortcut(sc) }
                                else repo.launchShortcut(sc)
                                onDismiss()
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).rotate(45f).background(Ygg.Frost))
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.weight(1f)) { RuneText((sc.shortLabel ?: sc.id).toString(), 17.sp, Ygg.Frost) }
                            RuneButton("+ Home") {
                                repo.pin(sc)?.let { key ->
                                    layout.addToHome(key)
                                    Toast.makeText(ctx, "${sc.shortLabel ?: sc.id} added to home", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            }
                        }
                    }
                }
                Canvas(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp)) {
                    drawLine(Ygg.Gold.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
                }
            }
            fun dropIfUnused() { if (app.isShortcut && !layout.isPlaced(app.key)) repo.unpin(app.key) }
            if (onHome) SheetItem("Remove from home") { layout.removeFromHome(app.key); dropIfUnused(); onDismiss() }
            else if (!app.isShortcut) SheetItem("Add to home") { layout.addToHome(app.key); onDismiss() }
            if (inDock) SheetItem("Unpin from dock") { layout.unpinFromDock(app.key); dropIfUnused(); onDismiss() }
            else if (layout.dock.any { it == null }) SheetItem("Pin to dock") { layout.pinToDock(app.key); onDismiss() }
            if (!app.isShortcut) {
                if (layout.isLocked(app.key)) SheetItem("Unlock (no fingerprint to open)") {
                    Guard.check(ctx, "Unlock ${app.label}", fresh = true) { layout.setLocked(app.key, false) }; onDismiss()
                } else SheetItem("Lock with fingerprint") {
                    if (Guard.available(ctx)) layout.setLocked(app.key, true)
                    else Toast.makeText(ctx, "Set up a fingerprint or screen lock first", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
                if (!layout.isHidden(app.key)) SheetItem("Hide from apps and search") {
                    layout.setHidden(app.key, true)
                    Toast.makeText(ctx, "${app.label} hidden. Settings → Privacy shows hidden apps.", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }
            SheetItem("App info") { repo.openAppInfo(app); onDismiss() }
            if (!app.isShortcut) SheetItem("Uninstall", Ygg.Ember) { ctx.uninstall(app.packageName); onDismiss() }
        }
    }
}

@Composable
private fun SheetItem(text: String, color: Color = Ygg.Bone, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        RuneText(text, 18.sp, color)
    }
}

// =================================================================== settings

@Composable
private fun SettingsPanel(layout: LayoutStore, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ygg.Night.copy(alpha = 0.97f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuneText("Yggdrasil", 36.sp, Ygg.Gold)
            Spacer(Modifier.weight(1f))
            RuneButton("Done") { onClose() }
        }
        RuneText("Launcher $version", 14.sp, Ygg.Ash)

        Section("Hall layout")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("Grid", layout.layoutMode == LayoutMode.GRID) { layout.updateLayoutMode(LayoutMode.GRID) }
            Choice("Longhall", layout.layoutMode == LayoutMode.LONGHALL) { layout.updateLayoutMode(LayoutMode.LONGHALL) }
        }

        Section("Columns")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice("4", layout.columns == 4) { layout.updateColumns(4) }
            Choice("5", layout.columns == 5) { layout.updateColumns(5) }
        }

        Section("Import from Nova")
        StatusLine("Rebuilds this home screen, folders and dock from a Nova backup (.novabackup). Replaces the current layout.")
        val importScope = rememberCoroutineScope()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            importScope.launch {
                val msg = runCatching {
                    val r = NovaImport.run(ctx, uri, layout, ctx.ygg.repo.apps.value)
                    r.droppedWidgets.forEach { id ->
                        ctx.ygg.widgetViews.remove(id)
                        runCatching { ctx.ygg.widgetHost.deleteAppWidgetId(id) }
                    }
                    buildString {
                        append("Imported ${r.apps} apps in ${r.folders} folders and loose")
                        if (r.skipped.isNotEmpty()) append(". Skipped (widgets/shortcuts/not installed): ${r.skipped.joinToString()}")
                    }
                }.getOrElse { "Import failed: ${it.message}" }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                onClose()
            }
        }
        RuneButton("Choose Nova backup") { picker.launch(arrayOf("*/*")) }

        Section("Yggdrasil wallpapers")
        var target by remember { mutableStateOf(com.ozorhaar.yggdrasil.launcher.WallTarget.HOME) }
        val scope = rememberCoroutineScope()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ozorhaar.yggdrasil.launcher.WallTarget.entries.forEach { t ->
                Choice(t.label, target == t) { target = t }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ozorhaar.yggdrasil.launcher.YggWallpaper.entries.forEach { w ->
                WallThumb(w) {
                    scope.launch {
                        val ok = ctx.applyYggWallpaper(w, target)
                        Toast.makeText(ctx, if (ok) "${w.label} set (${target.label.lowercase()})" else "Couldn't set wallpaper", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        RuneButton("Living fjord (moves with the real sky)") {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                        .putExtra(
                            android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            android.content.ComponentName(ctx, com.ozorhaar.yggdrasil.launcher.wallpaper.FjordWallpaper::class.java),
                        )
                )
            }.onFailure { Toast.makeText(ctx, "Couldn't open the wallpaper preview", Toast.LENGTH_SHORT).show() }
        }

        RuneButton("Show me the sky (80-second demo)") {
            ctx.getSharedPreferences("weather", android.content.Context.MODE_PRIVATE).edit()
                .putLong(com.ozorhaar.yggdrasil.launcher.wallpaper.DEMO_PREF, System.currentTimeMillis()).apply()
            onClose()
        }
        StatusLine("A whole day, a night of northern lights, rain, a storm, snow and fog. Needs the living fjord as your wallpaper.")

        Section("Wallpaper shade")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0, 15, 30, 45, 60).forEach { pct ->
                Choice(if (pct == 0) "Off" else "$pct%", layout.wallShade == pct) { layout.updateWallShade(pct) }
            }
        }

        Section("Rune band on the dock")
        Switch(
            checked = layout.dockRunes,
            onCheckedChange = { layout.updateDockRunes(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Ygg.Night, checkedTrackColor = Ygg.Gold),
        )

        Section("Runic labels")
        Switch(
            checked = layout.runicLabels,
            onCheckedChange = { layout.updateRunicLabels(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Ygg.Night, checkedTrackColor = Ygg.Gold),
        )

        Section("Unread badges")
        StatusLine(if (ctx.badgeAccessGranted()) "On" else "Needs notification access")
        RuneButton("Open notification access") { ctx.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

        PrivacySection(layout)
        SearchSection()
        BackupSection(layout, onDone = onClose)

        Section("Double-tap to lock")
        StatusLine(
            when {
                LockService.running -> "On"
                LockService.isEnabledInSettings(ctx) -> "Switched on, waiting for Android to start it"
                else -> "Turn on “Yggdrasil double-tap to lock”"
            }
        )
        RuneButton("Open accessibility") { ctx.openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }

        Section("Default home app")
        RuneButton("Choose home app") { ctx.openSettings(Settings.ACTION_HOME_SETTINGS) }

        Section("Updates")
        UpdatesSection()

        Section("Gestures")
        RuneText(
            "Swipe up: all apps\nSwipe down from the top: notifications\nSwipe down anywhere else: search\nTap the dock's rune band: Hafnir\nDouble-tap: lock\nHold empty space: widgets and settings\nHold an app: options\nHold and drag an app: move it\nDrop it on another app: make a folder\nHold it at the edge: turn the page",
            16.sp, Ygg.Bone,
        )
        Spacer(Modifier.height(30.dp))
    }
}

/** Check GitHub for a newer launcher, verify it, and hand it to Android's installer. */
@Composable
private fun UpdatesSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Installed: " + com.ozorhaar.yggdrasil.launcher.update.Updater.installedName(ctx)) }
    var release by remember { mutableStateOf<com.ozorhaar.yggdrasil.launcher.update.Updater.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    val U = com.ozorhaar.yggdrasil.launcher.update.Updater
    StatusLine(status)
    val r = release
    if (r == null) {
        RuneButton(if (busy) "Checking…" else "Check for updates") {
            if (busy) return@RuneButton
            busy = true
            scope.launch {
                runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { U.latest() } }
                    .onSuccess {
                        if (it.versionCode > U.installedCode(ctx)) { release = it; status = "Version ${it.versionName} is ready" }
                        else status = "Up to date (${U.installedName(ctx)})"
                    }
                    .onFailure { status = "Couldn't check: ${it.message}" }
                busy = false
            }
        }
    } else {
        RuneButton(if (busy) "Working…" else "Install ${r.versionName}") {
            if (busy) return@RuneButton
            if (!U.canInstall(ctx)) {
                status = "Allow Yggdrasil to install updates, then tap Install again"
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return@RuneButton
            }
            busy = true
            scope.launch {
                runCatching {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        U.downloadAndVerify(ctx, r) { msg -> scope.launch { status = msg } }
                    }
                }.onSuccess { apk ->
                    status = "Verified: checksum and signature match. Confirm on the next screen."
                    runCatching { U.install(ctx, apk) }.onFailure { status = "Couldn't start the install: ${it.message}" }
                }.onFailure { status = it.message ?: "Update failed" }
                busy = false
            }
        }
    }
}

@Composable
private fun WallThumb(w: com.ozorhaar.yggdrasil.launcher.YggWallpaper, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(w.res),
            contentDescription = w.label,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.width(84.dp).height(170.dp)
                .border(1.dp, Ygg.Gold.copy(alpha = 0.7f), CutCornerShape(8.dp)),
        )
        RuneText(w.label, 14.sp, Ygg.Bone)
    }
}

@Composable
internal fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    RuneText(title, 20.sp, Ygg.Frost)
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawLine(Ygg.Gold.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width * 0.6f, 0f), strokeWidth = 2f)
    }
}

@Composable
internal fun StatusLine(text: String) = RuneText(text, 15.sp, Ygg.Ash)

@Composable
internal fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .background(if (selected) Ygg.Gold else Color.Transparent, CutCornerShape(8.dp))
            .border(1.dp, Ygg.Gold, CutCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        RuneText(text, 17.sp, if (selected) Ygg.Night else Ygg.Gold)
    }
}

// =================================================================== bits

@Composable
fun RuneText(text: String, size: TextUnit, color: Color) {
    Text(text, style = TextStyle(color = color, fontSize = size, fontFamily = Ygg.Runic, shadow = labelShadow))
}

@Composable
fun RuneButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .clickable(onClick = onClick)
            .border(1.dp, Ygg.Gold.copy(alpha = 0.7f), CutCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        RuneText(text, 16.sp, Ygg.Gold)
    }
}
