package com.ozorhaar.yggdrasil.launcher.ui

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
    val ctx = LocalContext.current
    val ygg = ctx.ygg
    val info = remember(spot.id) { ygg.widgetManager.getAppWidgetInfo(spot.id) }
    if (info == null) {
        Box(
            Modifier.fillMaxSize()
                .background(Ygg.Night.copy(alpha = 0.6f), CutCornerShape(10.dp))
                .border(1.dp, Ygg.Ember.copy(alpha = 0.7f), CutCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { RuneText("Widget unavailable. Hold to remove.", 13.sp, Ygg.Ash) }
        return
    }
    AndroidView(
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
    )
}

private class ProviderRow(val info: AppWidgetProviderInfo, val app: String, val label: String)

/** Every widget on the phone, grouped by app, with previews. */
@Composable
fun WidgetPicker(cols: Int, rows: Int, onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val all by produceState<List<ProviderRow>?>(null) {
        value = withContext(Dispatchers.IO) {
            ctx.ygg.widgetManager.getInstalledProvidersForProfile(Process.myUserHandle())
                .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
                .map { info ->
                    val app = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
                    }.getOrDefault(info.provider.packageName)
                    ProviderRow(info, app, info.loadLabel(pm) ?: app)
                }
                .sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        }
    }
    var query by remember { mutableStateOf("") }
    val shown = all?.let { list ->
        val q = query.trim()
        if (q.isEmpty()) list else list.filter { it.app.contains(q, true) || it.label.contains(q, true) }
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
            var lastApp = ""
            shown.forEach { row ->
                if (row.app != lastApp) {
                    lastApp = row.app
                    item(key = "h:" + row.app + row.info.provider.packageName) {
                        Box(Modifier.padding(top = 16.dp, bottom = 4.dp)) { RuneText(row.app, 19.sp, Ygg.Frost) }
                    }
                }
                item(key = row.info.provider.flattenToString()) {
                    val (w, h) = cellsFor(ctx, row.info, cols, rows)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { onPick(row.info) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetPreview(row.info, Modifier.width(120.dp).heightIn(min = 48.dp, max = 110.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.label, style = TextStyle(color = Ygg.Bone, fontSize = 16.sp))
                            Text("$w × $h", style = TextStyle(color = Ygg.Ash, fontSize = 13.sp))
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
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
fun WidgetSheet(spot: WidgetSpot, layout: LayoutStore, onRemove: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val info = remember(spot.id) { ctx.ygg.widgetManager.getAppWidgetInfo(spot.id) }
    val name = remember(info) { info?.loadLabel(ctx.packageManager) ?: "Widget" }
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
fun HomeMenu(onWidgets: () -> Unit, onSettings: () -> Unit, onClose: () -> Unit) {
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
