package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.data.AppEntry
import com.ozorhaar.yggdrasil.launcher.icons.ShieldIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ShieldImage(app: AppEntry, icons: ShieldIcons, size: Dp) {
    val bmp by produceState<ImageBitmap?>(initialValue = null, app.key) {
        value = withContext(Dispatchers.Default) { icons.get(app) }
    }
    Box(Modifier.size(size)) {
        bmp?.let { Image(bitmap = it, contentDescription = app.label, modifier = Modifier.size(size)) }
    }
}

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Ygg.Ember, CircleShape)
            .border(1.5.dp, Ygg.Night, CircleShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp),
        )
    }
}

val labelShadow = Shadow(color = Color(0xCC000000), blurRadius = 6f)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    icons: ShieldIcons,
    badge: Int,
    runic: Boolean,
    iconSize: Dp,
    showLabel: Boolean = true,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .padding(4.dp)
            .then(if (highlighted) Modifier.border(2.dp, Ygg.GoldHi, CircleShape) else Modifier),
    ) {
        Box {
            ShieldImage(app, icons, iconSize)
            Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp))
        }
        if (showLabel) TileLabel(app.label, runic)
    }
}

@Composable
fun TileLabel(text: String, runic: Boolean) {
    // Drawn twice so names stay readable on any wallpaper: a dark carved outline with a soft
    // dark glow underneath, then the bone-coloured letters on top.
    val base = TextStyle(
        fontSize = if (runic) 12.sp else 11.sp,
        fontFamily = if (runic) Ygg.Runic else null,
    )
    Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = base.copy(
                color = Color(0xF20B1220),
                drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 5f, join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
                shadow = Shadow(color = Color(0xE6000000), blurRadius = 10f),
            ),
        )
        Text(
            text = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = base.copy(color = Ygg.Bone),
        )
    }
}
