package com.ozorhaar.yggdrasil.launcher.ui

import android.app.WallpaperManager
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
    val byKey = remember(apps) { apps.associateBy { it.key } }

    val density = LocalDensity.current
    val icons = remember { ShieldIcons(with(density) { 64.dp.roundToPx() }) }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var moving by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }

    val pager = rememberPagerState(pageCount = { layout.pages.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) layout.prune(apps.map { it.key }.toSet())
    }

    // Tell the wallpaper we're the home screen and where we are on it. Live
    // wallpapers (Samsung's layered engine included) stay in their dimmed /
    // blurred "behind an app" state until a launcher reports offsets.
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
            drawerOpen = false; settingsOpen = false; moving = null; menu = null
            scope.launch { pager.animateScrollToPage(0) }
        }
    }

    BackHandler(enabled = true) {
        when {
            menu != null -> menu = null
            moving != null -> moving = null
            settingsOpen -> settingsOpen = false
            drawerOpen -> drawerOpen = false
        }
    }

    fun launch(app: AppEntry) {
        if (moving != null) return
        ygg.repo.launch(app)
    }

    val overlayOpen = drawerOpen || settingsOpen || menu != null

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(overlayOpen, moving) {
                if (overlayOpen || moving != null) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        if (!LockService.lockScreen()) {
                            val msg = if (LockService.isEnabledInSettings(ctx))
                                "Lock service is on but not running yet. Switch it off and on in Accessibility."
                            else "Turn on “Yggdrasil double-tap to lock” in Accessibility"
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onLongPress = { settingsOpen = true },
                )
            }
            .pointerInput(overlayOpen, moving) {
                if (overlayOpen || moving != null) return@pointerInput
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -140f) drawerOpen = true
                        else if (total > 140f) ctx.expandNotifications()
                    },
                ) { _, dy -> total += dy }
            }
    ) {
        // backdrop painted by the launcher itself (instead of the system wallpaper layer)
        com.ozorhaar.yggdrasil.launcher.YggWallpaper.entries.firstOrNull { it.name == layout.backdrop }?.let { w ->
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(w.res),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
                    LayoutMode.GRID -> HomeGrid(
                        layout, byKey, icons, badges, pager, moving,
                        onTap = { key, page, slot ->
                            val m = moving
                            if (m != null) {
                                layout.moveTo(m, page, slot); moving = null
                            } else byKey[key]?.let { launch(it) }
                        },
                        onLong = { key -> byKey[key]?.let { menu = MenuTarget(it, From.HOME) } },
                        onEmpty = { page, slot -> moving?.let { layout.moveTo(it, page, slot) }; moving = null },
                    )
                    LayoutMode.LONGHALL -> Longhall(
                        layout, byKey, icons, badges,
                        onTap = { launch(it) },
                        onLong = { menu = MenuTarget(it, From.HALL) },
                    )
                }
            }
            if (layout.layoutMode == LayoutMode.GRID) PageMarks(layout.pages.size, pager.currentPage)
            Dock(layout, byKey, icons, badges, onTap = { launch(it) }, onLong = { menu = MenuTarget(it, From.DOCK) })
        }

        // move-mode banner
        AnimatedVisibility(moving != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            val name = byKey[moving]?.label ?: ""
            Row(
                Modifier.statusBarsPadding().padding(12.dp)
                    .background(Ygg.Night.copy(alpha = 0.92f), CutCornerShape(10.dp))
                    .border(1.dp, Ygg.Gold, CutCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RuneText("Place $name", 15.sp, Ygg.Bone)
                RuneButton("+ Page") { layout.addPage(); scope.launch { pager.animateScrollToPage(layout.pages.lastIndex) } }
                RuneButton("Cancel") { moving = null }
            }
        }

        AnimatedVisibility(
            drawerOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AppDrawer(
                apps = apps, icons = icons, badges = badges, runic = layout.runicLabels,
                onLaunch = { drawerOpen = false; launch(it) },
                onLong = { menu = MenuTarget(it, From.DRAWER) },
                onClose = { drawerOpen = false },
            )
        }

        AnimatedVisibility(settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsPanel(layout, onClose = { settingsOpen = false })
        }

        menu?.let { target ->
            ActionSheet(
                target = target,
                layout = layout,
                icons = icons,
                onDismiss = { menu = null },
                onMove = {
                    moving = target.app.key; menu = null; drawerOpen = false
                },
            )
        }
    }
}

// =================================================================== home grid

@Composable
private fun HomeGrid(
    layout: LayoutStore,
    byKey: Map<String, AppEntry>,
    icons: ShieldIcons,
    badges: Map<String, Int>,
    pager: androidx.compose.foundation.pager.PagerState,
    moving: String?,
    onTap: (String, Int, Int) -> Unit,
    onLong: (String) -> Unit,
    onEmpty: (Int, Int) -> Unit,
) {
    val cols = layout.columns
    val rows = layout.rows
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { pageIdx ->
        val page = layout.pages.getOrNull(pageIdx) ?: return@HorizontalPager
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val iconSize = minOf(maxWidth / cols * 0.62f, 60.dp)
            Column(Modifier.fillMaxSize()) {
                for (r in 0 until rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until cols) {
                            val slot = r * cols + c
                            val key = page.getOrNull(slot)
                            val app = key?.let { byKey[it] }
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                if (app != null) {
                                    AppTile(
                                        app, icons, badges[app.packageName] ?: 0, layout.runicLabels, iconSize,
                                        highlighted = moving == app.key,
                                        onClick = { onTap(app.key, pageIdx, slot) },
                                        onLongClick = { onLong(app.key) },
                                    )
                                } else if (moving != null) {
                                    Box(
                                        Modifier.size(iconSize)
                                            .border(1.5.dp, Ygg.Gold.copy(alpha = 0.55f), CircleShape)
                                            .clickable { onEmpty(pageIdx, slot) }
                                    )
                                }
                            }
                        }
                    }
                }
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
    onTap: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(Ygg.Night.copy(alpha = 0.55f), RoundedCornerShape(30.dp))
            .border(1.dp, Ygg.Gold.copy(alpha = 0.35f), RoundedCornerShape(30.dp))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        layout.dock.forEach { key ->
            val app = key?.let { byKey[it] }
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                if (app != null) AppTile(
                    app, icons, badges[app.packageName] ?: 0, layout.runicLabels, 52.dp,
                    showLabel = false, onClick = { onTap(app) }, onLongClick = { onLong(app) },
                )
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

@Composable
private fun ActionSheet(
    target: MenuTarget,
    layout: LayoutStore,
    icons: ShieldIcons,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = target.app
    val onHome = layout.pages.any { app.key in it }
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
            if (onHome && target.from == From.HOME) SheetItem("Move") { onMove() }
            if (onHome) SheetItem("Remove from home") { layout.removeFromHome(app.key); onDismiss() }
            else SheetItem("Add to home") { layout.addToHome(app.key); onDismiss() }
            if (inDock) SheetItem("Unpin from dock") { layout.unpinFromDock(app.key); onDismiss() }
            else if (layout.dock.any { it == null }) SheetItem("Pin to dock") { layout.pinToDock(app.key); onDismiss() }
            SheetItem("App info") { ctx.ygg.repo.openAppInfo(app); onDismiss() }
            SheetItem("Uninstall", Ygg.Ember) { ctx.uninstall(app.packageName); onDismiss() }
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

        Section("Yggdrasil wallpapers")
        var target by remember { mutableStateOf(com.ozorhaar.yggdrasil.launcher.WallTarget.HOME) }
        val scope = rememberCoroutineScope()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ozorhaar.yggdrasil.launcher.WallTarget.entries.forEach { t ->
                Choice(t.label, target == t) { target = t }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ozorhaar.yggdrasil.launcher.YggWallpaper.entries.forEach { w ->
                WallThumb(w) {
                    scope.launch {
                        val ok = ctx.applyYggWallpaper(w, target)
                        Toast.makeText(ctx, if (ok) "${w.label} set (${target.label.lowercase()})" else "Couldn't set wallpaper", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        Section("Backdrop")
        StatusLine("Paint the art inside the launcher instead of using the system wallpaper")
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("System", layout.backdrop == "") { layout.updateBackdrop("") }
            com.ozorhaar.yggdrasil.launcher.YggWallpaper.entries.forEach { w ->
                Choice(w.label, layout.backdrop == w.name) { layout.updateBackdrop(w.name) }
            }
        }

        Section("Wallpaper shade")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0, 15, 30, 45, 60).forEach { pct ->
                Choice(if (pct == 0) "Off" else "$pct%", layout.wallShade == pct) { layout.updateWallShade(pct) }
            }
        }

        Section("Runic labels")
        Switch(
            checked = layout.runicLabels,
            onCheckedChange = { layout.updateRunicLabels(it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Ygg.Night, checkedTrackColor = Ygg.Gold),
        )

        Section("Unread badges")
        StatusLine(if (ctx.badgeAccessGranted()) "On" else "Needs notification access")
        RuneButton("Open notification access") { ctx.openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }

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
        RuneButton("Get the latest release") { ctx.openUrl("https://github.com/Ozorhaar/yggdrasil-launcher/releases/latest") }

        Section("Gestures")
        RuneText(
            "Swipe up: all apps\nSwipe down: notifications\nDouble-tap: lock\nHold empty space: this page\nHold an app: options",
            16.sp, Ygg.Bone,
        )
        Spacer(Modifier.height(30.dp))
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
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    RuneText(title, 20.sp, Ygg.Frost)
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawLine(Ygg.Gold.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width * 0.6f, 0f), strokeWidth = 2f)
    }
}

@Composable
private fun StatusLine(text: String) = RuneText(text, 15.sp, Ygg.Ash)

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
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
