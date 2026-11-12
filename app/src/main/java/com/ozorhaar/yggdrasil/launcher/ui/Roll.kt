package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Text that doesn't fit its line rolls slowly sideways, like a scroll unwinding, twice, then rests
 * at its start (it rolls again when the text changes); text that fits stays still. Rolling redraws
 * the screen at its full refresh rate, so it mustn't run forever.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rollIfLong(): Modifier = this.basicMarquee(
    iterations = 2,
    initialDelayMillis = 1500,
    repeatDelayMillis = 2500,
    spacing = MarqueeSpacing(40.dp),
    velocity = 28.dp,
)
