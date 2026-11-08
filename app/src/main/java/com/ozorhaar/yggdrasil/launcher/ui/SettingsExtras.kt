package com.ozorhaar.yggdrasil.launcher.ui

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.data.LayoutStore
import com.ozorhaar.yggdrasil.launcher.search.HomeLink
import com.ozorhaar.yggdrasil.launcher.security.Guard
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Hidden apps (behind your fingerprint) and what's locked. */
@Composable
internal fun PrivacySection(layout: LayoutStore) {
    val ctx = LocalContext.current
    val apps = ctx.ygg.repo.apps.value
    var reveal by remember { mutableStateOf(false) }
    Section("Privacy")
    val lockedFolders = layout.folders.values.count { it.locked }
    StatusLine("Locked: ${layout.locked.size} apps, $lockedFolders folders. Hidden: ${layout.hidden.size} apps.")
    StatusLine("Hold an app to lock or hide it; open a folder to lock it. Locks guard opening from this launcher, so keep each app's own fingerprint lock on too.")
    if (!reveal) {
        RuneButton("Show hidden apps") { Guard.check(ctx, "Show hidden apps", fresh = true) { reveal = true } }
    } else {
        if (layout.hidden.isEmpty()) StatusLine("Nothing hidden.")
        layout.hidden.toList().forEach { key ->
            val label = apps.firstOrNull { it.key == key }?.label ?: key.substringBefore('/')
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RuneText(label, 17.sp, Ygg.Bone) }
                val app = apps.firstOrNull { it.key == key }
                if (app != null) {
                    RuneButton("Open") { ctx.ygg.repo.launch(app) }
                    Spacer(Modifier.width(8.dp))
                }
                RuneButton("Unhide") { layout.setHidden(key, false) }
            }
        }
        RuneButton("Hide the list again") { reveal = false }
    }
}

/** People and Home Assistant in search. */
@Composable
internal fun SearchSection() {
    val ctx = LocalContext.current
    val home = ctx.ygg.homeLink
    val scope = rememberCoroutineScope()
    Section("Search")
    StatusLine("Swipe down anywhere below the status bar to search apps, sums, people, phone settings, your devices and the web.")
    val contacts = ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    StatusLine(if (contacts) "People: on (contacts stay on this phone)" else "People: off. Search offers to turn it on.")

    var connected by remember { mutableStateOf(home.configured) }
    var url by remember { mutableStateOf(home.url) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<HomeLink.Device>>(emptyList()) }
    var allowed by remember { mutableStateOf(home.allowed) }

    Spacer(Modifier.height(8.dp))
    RuneText("Home Assistant devices", 17.sp, Ygg.Bone)
    if (!connected) {
        StatusLine("Make a long-lived access token in HA (your profile → Security), ideally for a non-admin user, and paste it here. It's stored encrypted by the phone's keystore.")
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ygg.Gold, unfocusedBorderColor = Ygg.Gold.copy(alpha = 0.4f), cursorColor = Ygg.Gold,
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true,
            textStyle = TextStyle(color = Ygg.Bone, fontSize = 15.sp), colors = fieldColors, shape = CutCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it }, singleLine = true,
            placeholder = { RuneText("Access token", 15.sp, Ygg.Ash) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            textStyle = TextStyle(color = Ygg.Bone, fontSize = 15.sp), colors = fieldColors, shape = CutCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        RuneButton(if (busy) "Checking…" else "Connect") {
            if (busy) return@RuneButton
            busy = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { home.connect(url, token) } }
                busy = false
                r.onSuccess { token = ""; connected = true; choosing = true }
                    .onFailure { Toast.makeText(ctx, it.message ?: "Couldn't connect", Toast.LENGTH_LONG).show() }
            }
        }
    } else {
        StatusLine("Connected to ${home.url}. ${allowed.size} devices allowed in search. Search can switch lights and plugs, lock locks, close the garage and run scenes. It never unlocks or opens anything.")
        Row {
            RuneButton(if (choosing) "Done choosing" else "Choose devices") {
                if (choosing) { choosing = false; return@RuneButton }
                Guard.check(ctx, "Choose devices") { choosing = true }
            }
            Spacer(Modifier.width(8.dp))
            RuneButton("Disconnect") { home.forget(); connected = false; choosing = false; allowed = emptySet() }
        }
    }
    LaunchedEffect(connected, choosing) {
        if (!connected || !choosing || devices.isNotEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { home.allDevices() } }
            .onSuccess { devices = it }
            .onFailure { Toast.makeText(ctx, it.message ?: "Couldn't load devices", Toast.LENGTH_LONG).show(); choosing = false }
    }
    if (connected && choosing) {
        if (devices.isEmpty()) StatusLine("Loading devices…")
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            devices.forEach { d ->
                val on = d.entityId in allowed
                Row(
                    Modifier.fillMaxWidth().clickable {
                        home.setAllowed(d.entityId, !on); allowed = home.allowed
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = on,
                        onCheckedChange = { home.setAllowed(d.entityId, it); allowed = home.allowed },
                        colors = CheckboxDefaults.colors(checkedColor = Ygg.Gold, uncheckedColor = Ygg.Ash, checkmarkColor = Ygg.Night),
                    )
                    Column {
                        RuneText(d.name, 16.sp, Ygg.Bone)
                        RuneText(d.entityId, 12.sp, Ygg.Ash)
                    }
                }
            }
        }
    }
}

/** Save the whole layout to a file, or bring one back. */
@Composable
internal fun BackupSection(layout: LayoutStore, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val ygg = ctx.ygg
    val scope = rememberCoroutineScope()
    Section("Back up the layout")
    StatusLine("Saves pages, folders (names, emblems, locks), dock, widgets' places, hidden and locked apps, and the look. No passwords or tokens go in the file.")

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(layout.exportJson().toString(2).toByteArray()) }
                }
            }
            Toast.makeText(ctx, if (r.isSuccess) "Layout saved" else "Couldn't save: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            }.getOrElse { Toast.makeText(ctx, "Couldn't read that file", Toast.LENGTH_LONG).show(); return@launch }
            val msg = runCatching {
                val (oldIds, missing) = layout.importJson(JSONObject(text)) { provider ->
                    if (provider == Skald.PROVIDER) return@importJson Skald.ID
                    if (provider == SkyCard.PROVIDER) return@importJson SkyCard.ID
                    val cn = ComponentName.unflattenFromString(provider) ?: return@importJson null
                    val id = ygg.widgetHost.allocateAppWidgetId()
                    if (ygg.widgetManager.bindAppWidgetIdIfAllowed(id, cn)) id
                    else { runCatching { ygg.widgetHost.deleteAppWidgetId(id) }; null }
                }
                oldIds.forEach { id -> ygg.widgetViews.remove(id); if (id >= 0) runCatching { ygg.widgetHost.deleteAppWidgetId(id) } }
                if (missing.isEmpty()) "Layout restored"
                else "Layout restored. Add these widgets again: " + missing.joinToString { it.substringBefore('/').substringAfterLast('.') }
            }.getOrElse { "Restore failed: ${it.message}" }
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            onDone()
        }
    }
    Row {
        RuneButton("Save to a file") {
            Guard.check(ctx, "Save the layout", fresh = true) { saver.launch("yggdrasil-layout-${LocalDate.now()}.json") }
        }
        Spacer(Modifier.width(8.dp))
        RuneButton("Restore from a file") {
            Guard.check(ctx, "Restore a layout", fresh = true) { opener.launch(arrayOf("application/json", "text/plain", "*/*")) }
        }
    }
}
