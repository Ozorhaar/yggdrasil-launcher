package com.ozorhaar.yggdrasil.launcher.ui

import com.ozorhaar.yggdrasil.launcher.search.looselyHas
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ozorhaar.yggdrasil.launcher.data.LayoutStore
import com.ozorhaar.yggdrasil.launcher.data.WidgetSpot
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/** How many grid cells a widget wants by default. */
fun cellsFor(ctx: Context, info: AppWidgetProviderInfo, cols: Int, rows: Int): Pair<Int, Int> {
    val d = ctx.resources.displayMetrics.density
    val w = if (info.targetCellWidth > 0) info.targetCellWidth else ceil((info.minWidth / d + 30f) / 70f).toInt()
    val h = if (info.targetCellHeight > 0) info.targetCellHeight else ceil((info.minHeight / d + 30f) / 70f).toInt()
    return w.coerceIn(1, cols) to h.coerceIn(1, rows)
}

/** Registers a widget's on-screen bounds so a long-press on it can be found. */
@Composable
fun Modifier.widgetCell(drag: DragState, id: Int, page: Int): Modifier {
    DisposableEffect(drag, id) { onDispose { drag.widgetRects.remove(id) } }
    return this.onGloballyPositioned { drag.widgetRects[id] = page to it.boundsInRoot() }
}

/** The live widget itself (a classic Android view inside Compose). */
@Composable
fun WidgetView(spot: WidgetSpot, width: Dp, height: Dp) {
    if (spot.provider == Skald.PROVIDER) { SkaldCard(); return }
    if (spot.provider == SkyCard.PROVIDER) { SkyCardView(); return }
    if (spot.provider == NornsCard.PROVIDER) { NornsCardView(); return }
    if (spot.provider == SagaCard.PROVIDER) { SagaCardView(); return }
    val ctx = LocalContext.current
    val ygg = ctx.ygg
    val info = remember(spot.id) { runCatching { ygg.widgetManager.getAppWidgetInfo(spot.id) }.getOrNull() }
    if (info == null) {
        Box(
            Modifier.fillMaxSize()
                .background(Ygg.Night.copy(alpha = 0.6f), CutCornerShape(10.dp))
                .border(1.dp, Ygg.Ember.copy(alpha = 0.7f), CutCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { RuneText("Widget unavailable. Hold to remove.", 13.sp, Ygg.Ash) }
        return
    }
    // every app widget sits in a carved gold frame on night, so its own colours stay inside the theme
    val frame = CutCornerShape(14.dp)
    Box(
        Modifier.fillMaxSize()
            .clip(frame)
            .background(Ygg.Night, frame)
            .border(1.5.dp, Dusk.gold.copy(alpha = 0.85f), frame)
            .padding(3.dp)
            .clip(CutCornerShape(12.dp)),
    ) { AndroidView(
        factory = { c ->
            val v = ygg.widgetViews.getOrPut(spot.id) { ygg.widgetHost.createView(c, spot.id, info) }
            (v.parent as? ViewGroup)?.removeView(v)
            v
        },
        modifier = Modifier.fillMaxSize(),
        update = { v: AppWidgetHostView ->
            val size = SizeF(width.value, height.value)
            if (v.tag != size) {
                v.tag = size
                runCatching { v.updateAppWidgetSize(Bundle(), listOf(size)) }
            }
        },
    ) }
}

/** A row in the picker: a widget, or an app's 1x1 shortcut entry (like Home Assistant's Assist). */
private class ProviderRow(
    val info: AppWidgetProviderInfo?,
    val shortcut: android.content.pm.LauncherActivityInfo?,
    val app: String,
    val label: String,
) {
    val id: String get() = info?.provider?.flattenToString() ?: ("sc:" + shortcut!!.componentName.flattenToString())
    val pkg: String get() = info?.provider?.packageName ?: shortcut!!.componentName.packageName
}

/** Every widget on the phone, grouped by app, with previews. */
@Composable
fun WidgetPicker(
    cols: Int,
    rows: Int,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickShortcut: (android.content.pm.LauncherActivityInfo) -> Unit,
    onPickSkald: () -> Unit,
    onPickSky: () -> Unit,
    onPickNorns: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val all by produceState<List<ProviderRow>?>(null) {
        value = withContext(Dispatchers.IO) {
            fun appName(pkg: String) = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val widgets = ctx.ygg.widgetManager.getInstalledProvidersForProfile(Process.myUserHandle())
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .map { info ->
                    val app = appName(info.provider.packageName)
                    ProviderRow(info, null, app, info.loadLabel(pm) ?: app)
                }
            val shortcuts = ctx.ygg.repo.shortcutConfigs().map { sc ->
                ProviderRow(null, sc, appName(sc.componentName.packageName), sc.label?.toString() ?: "Shortcut")
            }
            (widgets + shortcuts).sortedWith(compareBy({ it.app.lowercase() }, { it.shortcut != null }, { it.label.lowercase() }))
        }
    }
    var query by remember { mutableStateOf("") }
    val shown = all?.let { list ->
        val q = query.trim()
        if (q.isEmpty()) list else list.filter { it.app.looselyHas(q) || it.label.looselyHas(q) }
    }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ygg.Night.copy(alpha = 0.95f), Ygg.Night2.copy(alpha = 0.98f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RuneText("Widgets", 30.sp, Ygg.Gold)
            Spacer(Modifier.weight(1f))
            RuneButton("Close") { onClose() }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { RuneText("Search widgets", 16.sp, Ygg.Ash) },
            textStyle = TextStyle(color = Ygg.Bone, fontSize = 17.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ygg.Gold, unfocusedBorderColor = Ygg.Gold.copy(alpha = 0.4f), cursorColor = Ygg.Gold,
            ),
            shape = CutCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (shown == null) {
            RuneText("Gathering widgets…", 16.sp, Ygg.Ash); return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            // Yggdrasil's own widgets first
            if (query.isBlank() || "skald now playing music yggdrasil".looselyHas(query)) {
                item(key = "h:yggdrasil") {
                    Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) { RuneText("Yggdrasil", 19.sp, Ygg.Frost) }
                }
                item(key = Skald.PROVIDER) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickSkald() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(120.dp).height(52.dp)) {
                            SkaldCard()
                            Box(Modifier.matchParentSize().clickable { onPickSkald() }) // a tap picks it, never plays
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Skald: now playing", style = TextStyle(color = Ygg.Bone, fontSize = 16.sp))
                            Text("${Skald.WIDTH} × ${Skald.HEIGHT} · any music app", style = TextStyle(color = Ygg.Ash, fontSize = 13.sp))
                        }
                    }
                }
            }
            if (query.isBlank() || "sky weather sun forecast yggdrasil".looselyHas(query)) {
                item(key = SkyCard.PROVIDER) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickSky() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(120.dp).height(52.dp)) {
                            SkyCardView()
                            Box(Modifier.matchParentSize().clickable { onPickSky() })
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sky: weather", style = TextStyle(color = Ygg.Bone, fontSize = 16.sp))
                            Text("${SkyCard.WIDTH} × ${SkyCard.HEIGHT} · temperature, sky, sunrise and sunset", style = TextStyle(color = Ygg.Ash, fontSize = 13.sp))
                        }
                    }
                }
            }
            if (query.isBlank() || "norns calendar events nine agenda yggdrasil".looselyHas(query)) {
                item(key = NornsCard.PROVIDER) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickNorns() }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(120.dp).height(52.dp)) {
                            NornsCardView()
                            Box(Modifier.matchParentSize().clickable { onPickNorns() })
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Norns: calendar", style = TextStyle(color = Ygg.Bone, fontSize = 16.sp))
                            Text("${NornsCard.WIDTH} × ${NornsCard.HEIGHT} · what's next in your Nine calendar", style = TextStyle(color = Ygg.Ash, fontSize = 13.sp))
                        }
                    }
                }
            }
            var lastApp = ""
            shown.forEach { row ->
                if (row.app != lastApp) {
                    lastApp = row.app
                    item(key = "h:" + row.app + row.pkg) {
                        Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) { RuneText(row.app, 19.sp, Ygg.Frost) }
                    }
                }
                item(key = row.id) {
                    val info = row.info
                    val sc = row.shortcut
                    val size = if (info != null) cellsFor(ctx, info, cols, rows).let { (w, h) -> "$w × $h" } else "Shortcut · 1 × 1"
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { if (info != null) onPick(info) else if (sc != null) onPickShortcut(sc) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val box = Modifier.width(120.dp).heightIn(min = 48.dp, max = 110.dp)
                        if (info != null) WidgetPreview(info, box) else if (sc != null) ShortcutPreview(sc, box)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.label, style = TextStyle(color = Ygg.Bone, fontSize = 16.sp))
                            Text(size, style = TextStyle(color = Ygg.Ash, fontSize = 13.sp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@Composable
private fun ShortcutPreview(sc: android.content.pm.LauncherActivityInfo, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, sc.componentName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d = sc.getIcon(ctx.resources.displayMetrics.densityDpi) ?: return@runCatching null
                val bm = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 144, 144)
                d.draw(Canvas(bm))
                bm.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, contentDescription = null, modifier = Modifier.width(48.dp).heightIn(max = 48.dp)) }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val dpi = ctx.resources.displayMetrics.densityDpi
                val d = info.loadPreviewImage(ctx, dpi) ?: info.loadIcon(ctx, dpi) ?: return@runCatching null
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
                val scale = minOf(1f, 480f / maxOf(w, h))
                val bm = Bitmap.createBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, bm.width, bm.height)
                d.draw(Canvas(bm))
                bm.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Options for one widget: resize or remove. */
@Composable
fun WidgetSheet(spot: WidgetSpot, layout: LayoutStore, onRemove: () -> Unit, onWatch: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val builtin = Skald.isBuiltin(spot.provider)
    val info = remember(spot.id) { if (builtin) null else runCatching { ctx.ygg.widgetManager.getAppWidgetInfo(spot.id) }.getOrNull() }
    val name = remember(info) {
        when {
            spot.provider == Skald.PROVIDER -> "Skald (now playing)"
            spot.provider == SkyCard.PROVIDER -> "Sky (weather)"
            spot.provider == NornsCard.PROVIDER -> "Norns (calendar)"
            spot.provider == SagaCard.PROVIDER -> "Saga (Skald, Norns and Sky)"
            else -> info?.loadLabel(ctx.packageManager) ?: "Widget"
        }
    }
    val resizable = info == null || info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .background(Ygg.Night2, CutCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .border(1.dp, Ygg.Gold.copy(alpha = 0.6f), CutCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(18.dp),
        ) {
            RuneText(name, 22.sp, Ygg.Gold)
            Spacer(Modifier.height(12.dp))
            if (resizable) {
                Stepper("Width", spot.w) { layout.resizeWidget(spot.id, spot.w + it, spot.h) }
                Stepper("Height", spot.h) { layout.resizeWidget(spot.id, spot.w, spot.h + it) }
                RuneText("To move it: hold the widget and drag", 13.sp, Ygg.Ash)
            } else {
                RuneText("This widget has a fixed size. Hold and drag it to move it.", 13.sp, Ygg.Ash)
            }
            Spacer(Modifier.height(8.dp))
            if (!builtin) {
                Box(Modifier.fillMaxWidth().clickable { onWatch() }.padding(vertical = 12.dp)) {
                    Column {
                        RuneText("Hide it and keep watch", 18.sp, Ygg.Frost)
                        RuneText("Shows a frost rune on its app's folder while you're on call", 13.sp, Ygg.Ash)
                    }
                }
            }
            val parts = listOf(Skald.PROVIDER, NornsCard.PROVIDER, SkyCard.PROVIDER)
            if (spot.provider in parts && layout.widgets.count { it.provider in parts } >= 2) {
                Box(Modifier.fillMaxWidth().clickable {
                    layout.combineCards(SagaCard.ID, SagaCard.PROVIDER, parts); onClose()
                }.padding(vertical = 12.dp)) {
                    Column {
                        RuneText("Combine the cards", 18.sp, Ygg.Frost)
                        RuneText("The Skald, the Norns and the Sky in one card: swipe between them", 13.sp, Ygg.Ash)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().clickable { onRemove() }.padding(vertical = 12.dp)) {
                RuneText("Remove widget", 18.sp, Ygg.Ember)
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onStep: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { RuneText(label, 18.sp, Ygg.Bone) }
        RuneButton("−") { onStep(-1) }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { RuneText("$value", 20.sp, Ygg.Gold) }
        RuneButton("+") { onStep(1) }
    }
}

/** What a hold on empty home space offers. */
@Composable
fun HomeMenu(onWidgets: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit, onPlaces: (() -> Unit)? = null) {
    Box(
        Modifier.fillMaxSize().background(Color(0x88000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            Modifier.navigationBarsPadding().padding(bottom = 120.dp)
                .background(Ygg.Night2.copy(alpha = 0.96f), CutCornerShape(14.dp))
                .border(1.dp, Ygg.Gold.copy(alpha = 0.6f), CutCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MenuChoice("Widgets", onWidgets)
            if (onPlaces != null) MenuChoice("Places", onPlaces)
            MenuChoice("Settings", onSettings)
        }
    }
}

@Composable
private fun MenuChoice(text: String, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        RuneText(text, 19.sp, Ygg.Gold)
    }
}


/** The Saga: the Skald, the Norns and the Sky in one card where the Skald sat; swipe between them. */
object SagaCard {
    const val PROVIDER = "ygg:saga"
    const val ID = -4
    const val WIDTH = 5
    const val HEIGHT = 1
}

/** The Saga card: one page each for the Skald, the Norns and the Sky, dots to show which; keeps the last one shown. */
@Composable
fun SagaCardView() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ygg_saga", android.content.Context.MODE_PRIVATE) }
    val state = androidx.compose.foundation.pager.rememberPagerState(initialPage = prefs.getInt("page", 0).coerceIn(0, 2)) { 3 }
    androidx.compose.runtime.LaunchedEffect(state.currentPage) { prefs.edit().putInt("page", state.currentPage).apply() }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.pager.HorizontalPager(state, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
            Box(Modifier.fillMaxSize()) {
                when (p) {
                    0 -> SkaldCard()
                    1 -> NornsCardView()
                    else -> SkyCardView()
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier.size(6.dp).background(
                        if (i == state.currentPage) Ygg.Gold else Ygg.Gold.copy(alpha = 0.3f),
                        androidx.compose.foundation.shape.CircleShape,
                    )
                )
            }
        }
    }
}
