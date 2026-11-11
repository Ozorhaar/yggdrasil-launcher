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
internal fun SteadingSection(layout: com.ozorhaar.yggdrasil.launcher.data.LayoutStore) {
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
    Spacer(Modifier.height(10.dp))
    StatusLine("The scene's places: the World Tree, the runestone, the dragon's cave, the skald's fire, the smithy, the cookfire and the knarr each open a folder (the longship's hull opens Car; hold the longhouse for Smart Home). Which folder each opens is set below.")
    Spacer(Modifier.height(4.dp))
    var askMove by remember { mutableStateOf(false) }
    RuneButton("Move the folders into the scene") { askMove = true }
    if (askMove) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askMove = false },
        title = { Text("Move the folders into the scene?") },
        text = {
            Text("Their tiles leave the home screen (" +
                SteadingConfig.PLACE_FOLDERS.joinToString(", ") { SteadingConfig.text(ctx, it) } +
                "); they open from the scene instead. The Google and Proton folders go (their apps stay in the app drawer), " +
                "and so does the Visual Voicemail icon (it's on the rune band now). Nine and Proton Mail go to Ratatoskr's Mail folder, " +
                "Monopoly GO! to Jörmungandr's Games folder, and Bambu Handy's icon goes (the anvil opens it).")
        },
        confirmButton = {
            androidx.compose.material3.TextButton({
                askMove = false
                android.widget.Toast.makeText(ctx, moveIntoScene(ctx, layout), android.widget.Toast.LENGTH_LONG).show()
            }) { Text("Move them") }
        },
        dismissButton = { androidx.compose.material3.TextButton({ askMove = false }) { Text("Not now") } },
    )
    Spacer(Modifier.height(10.dp))
    StatusLine("The name-day feast comes by itself on 1 January.")
    Spacer(Modifier.height(4.dp))
    RuneButton("Preview the birthday") {
        com.ozorhaar.yggdrasil.launcher.wallpaper.Birthday.preview(ctx)
        android.widget.Toast.makeText(ctx, "Go to the home screen: the feast lasts about a minute", android.widget.Toast.LENGTH_LONG).show()
    }
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


/**
 * Takes the scene's folders off the home grid (they open from the scene), drops the Google and
 * Proton folders (their apps stay in the drawer) and the Visual Voicemail icon. Says what it did.
 */
internal fun moveIntoScene(ctx: android.content.Context, layout: com.ozorhaar.yggdrasil.launcher.data.LayoutStore): String {
    val names = SteadingConfig.PLACE_FOLDERS.map { SteadingConfig.text(ctx, it).trim().lowercase() }.toSet()
    val moved = layout.folders.values.filter { it.name.trim().lowercase() in names }.map { it.id }
    layout.takeOffGrid(moved)
    val dropped = layout.folders.values.filter { it.name.trim().lowercase() in setOf("google", "proton") && it.name.trim().lowercase() !in names }.map { it.id }
    dropped.forEach { layout.dropFolder(it) }
    val vvm = layout.pages.flatten().filterNotNull().filter { k -> listOf("com.tmobile.vvm.application", "com.samsung.vvm").any { k.contains(it) } }.distinct()
    vvm.forEach { layout.removeFromGrid(it) }
    // Ratatoskr carries the mail, Jörmungandr keeps the games; the anvil opens Bambu Handy itself
    fun keysFor(vararg pkgs: String): List<String> {
        val la = ctx.getSystemService(android.content.pm.LauncherApps::class.java)
        val me = android.os.Process.myUserHandle()
        val serial = ctx.getSystemService(android.os.UserManager::class.java).getSerialNumberForUser(me)
        return pkgs.flatMap { pk -> runCatching { la.getActivityList(pk, me) }.getOrDefault(emptyList()) }
            .map { "${it.componentName.flattenToString()}#$serial" }
    }
    val mail = layout.gatherInto(SteadingConfig.text(ctx, SteadingConfig.Entities.F_MAIL), keysFor("com.ninefolders.hd3", "ch.protonmail.android"))
    val games = layout.gatherInto(SteadingConfig.text(ctx, SteadingConfig.Entities.F_GAMES), keysFor("com.scopely.monopolygo"))
    layout.takeOffGrid(layout.folders.values.filter { it.name.trim().lowercase() in names }.map { it.id })
    val bambu = layout.pages.flatten().filterNotNull().filter { it.startsWith("bbl.intl.bambulab.com/") }.distinct()
    bambu.forEach { layout.removeFromGrid(it) }
    val icons = vvm.size + bambu.size
    return "Moved ${moved.size} folders into the scene" + (if (mail + games > 0) ", gathered $mail into Mail and $games into Games" else "") +
        "; removed ${dropped.size} folders and $icons icon" + (if (icons == 1) "" else "s")
}
