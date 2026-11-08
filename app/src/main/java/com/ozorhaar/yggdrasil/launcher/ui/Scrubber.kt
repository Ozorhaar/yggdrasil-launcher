package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.data.AppEntry
import com.ozorhaar.yggdrasil.launcher.icons.Runes
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The rune scrubber: a carved strip down the drawer's edge, one rune for each letter your apps
 * start with. Slide along it to jump through the drawer; a large rune with its letter shows
 * beside your thumb, and each new letter clicks.
 */
@Composable
fun RuneScrubber(apps: List<AppEntry>, grid: LazyGridState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // letter -> index of its first app, in drawer order
    val sections = remember(apps) {
        val m = LinkedHashMap<Char, Int>()
        apps.forEachIndexed { i, a -> m.putIfAbsent(Runes.letterOf(a.label), i) }
        m.entries.map { it.key to it.value }
    }
    if (sections.size < 2) return
    var stripHeight by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf<Int?>(null) }
    var thumbY by remember { mutableFloatStateOf(0f) }

    fun pick(y: Float) {
        thumbY = y.coerceIn(0f, stripHeight)
        val i = ((thumbY / stripHeight) * sections.size).toInt().coerceIn(0, sections.lastIndex)
        if (i != active) {
            active = i
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            Carved.play(ctx, Carved.Sound.FOLDER)
            scope.launch { grid.scrollToItem(sections[i].second) }
        }
    }

    Box(modifier.fillMaxHeight().width(30.dp)) {
        Column(
            Modifier.fillMaxHeight().fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(Ygg.Night.copy(alpha = 0.55f), RoundedCornerShape(15.dp))
                .border(1.dp, Dusk.gold.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
                .onSizeChanged { stripHeight = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(sections) {
                    detectTapGestures(onPress = { o -> pick(o.y); tryAwaitRelease(); active = null })
                }
                .pointerInput(sections) {
                    detectVerticalDragGestures(
                        onDragStart = { o -> pick(o.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ -> pick(change.position.y) }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sections.forEachIndexed { i, (letter, _) ->
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        Runes.forLetter(letter),
                        style = TextStyle(
                            color = if (i == active) Ygg.GoldHi else Dusk.gold,
                            fontSize = if (sections.size > 20) 11.sp else 13.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
        // the big rune by your thumb, with its letter
        active?.let { i ->
            val (letter, _) = sections[i]
            val bubble = 64.dp
            val yPx = with(density) { (thumbY + 6.dp.toPx() - bubble.toPx() / 2) }
            Column(
                Modifier
                    .offset { IntOffset(with(density) { -(bubble + 14.dp).roundToPx() }, yPx.roundToInt()) }
                    .size(bubble)
                    .background(Ygg.Night2, CutCornerShape(14.dp))
                    .border(1.5.dp, Dusk.gold, CutCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Runes.forLetter(letter), style = TextStyle(color = Ygg.GoldHi, fontSize = 30.sp), modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (letter == '#') "#" else letter.uppercaseChar().toString(),
                    style = TextStyle(color = Ygg.Bone, fontSize = 13.sp, fontFamily = Ygg.Runic),
                )
            }
        }
    }
}
