package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.ozorhaar.yggdrasil.launcher.R

object Ygg {
    val Night = Color(0xFF0B1220)
    val Night2 = Color(0xFF121B2E)
    val Iron = Color(0xFF2A2F36)
    val Gold = Color(0xFFC8A25A)
    val GoldHi = Color(0xFFECD08F)
    val Frost = Color(0xFF8FC4E0)
    val Ember = Color(0xFFE0703A)
    val Bone = Color(0xFFE8DCC4)
    val Ash = Color(0xFF96918A)
    val Runic = FontFamily(Font(R.font.yggdrasil_runic))
}

@Composable
fun YggTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ygg.Gold,
            onPrimary = Ygg.Night,
            secondary = Ygg.Frost,
            tertiary = Ygg.Ember,
            background = Ygg.Night,
            surface = Ygg.Night2,
            surfaceVariant = Ygg.Iron,
            onSurface = Ygg.Bone,
            onSurfaceVariant = Ygg.Ash,
            onBackground = Ygg.Bone,
            outline = Ygg.Gold,
        ),
        content = content,
    )
}
