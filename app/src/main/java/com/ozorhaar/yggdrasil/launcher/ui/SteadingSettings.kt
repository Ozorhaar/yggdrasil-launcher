package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.data.SteadingConfig
import com.ozorhaar.yggdrasil.launcher.data.SteadingConfig.Entities
import com.ozorhaar.yggdrasil.launcher.data.SteadingConfig.Touch

/**
 * The steading's own settings page: every living touch on or off, the horns' volume, and which
 * Home Assistant entities stand for what. Changes take effect within a couple of minutes (the
 * wallpaper reads the house every two).
 */
@Composable
internal fun SteadingSection() {
    val ctx = LocalContext.current
    Section("The steading")
    StatusLine("The living touches on the home screen and wallpaper.")
    Touch.entries.forEach { t ->
        var on by remember { mutableStateOf(SteadingConfig.on(ctx, t)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.label, color = Ygg.Bone, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = on,
                onCheckedChange = { on = it; SteadingConfig.set(ctx, t, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Ygg.Night, checkedTrackColor = Ygg.Gold),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    var vol by remember { mutableFloatStateOf(SteadingConfig.hornVolume(ctx)) }
    Text("Horn volume (homecoming, opening doors): ${(vol * 100).toInt()}%", color = Ygg.Bone, fontSize = 14.sp)
    Slider(
        value = vol,
        onValueChange = { vol = it },
        onValueChangeFinished = {
            SteadingConfig.setHornVolume(ctx, vol)
            Carved.play(ctx, Carved.Sound.HOME)
        },
        colors = SliderDefaults.colors(thumbColor = Ygg.Gold, activeTrackColor = Ygg.Gold),
    )
    Spacer(Modifier.height(6.dp))
    StatusLine("Home Assistant entities (comma-separated where there can be several):")
    Entities.entries.forEach { e ->
        var v by remember { mutableStateOf(SteadingConfig.text(ctx, e)) }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = v,
            onValueChange = { v = it; SteadingConfig.setText(ctx, e, it) },
            label = { Text(e.label, fontSize = 12.sp) },
            textStyle = TextStyle(color = Ygg.Bone, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Ygg.Gold, unfocusedBorderColor = Ygg.Gold.copy(alpha = 0.4f), cursorColor = Ygg.Gold,
                focusedLabelColor = Ygg.Frost, unfocusedLabelColor = Ygg.Ash,
            ),
            shape = CutCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
