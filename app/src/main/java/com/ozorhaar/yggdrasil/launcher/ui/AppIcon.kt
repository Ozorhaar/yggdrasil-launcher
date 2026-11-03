package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    Box(
        modifier
            .size(14.dp)
            .background(Ygg.Ember, CircleShape)
            .border(2.dp, Ygg.Night, CircleShape)
    )
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp)
            .then(if (highlighted) Modifier.border(2.dp, Ygg.GoldHi, CircleShape) else Modifier),
    ) {
        Box {
            ShieldImage(app, icons, iconSize)
            Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
        }
        if (showLabel) {
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = Ygg.Bone,
                    fontSize = if (runic) 12.sp else 11.sp,
                    fontFamily = if (runic) Ygg.Runic else null,
                    shadow = labelShadow,
                ),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
