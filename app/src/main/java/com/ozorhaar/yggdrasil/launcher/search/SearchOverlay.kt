package com.ozorhaar.yggdrasil.launcher.search

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.data.AppEntry
import com.ozorhaar.yggdrasil.launcher.icons.ShieldIcons
import com.ozorhaar.yggdrasil.launcher.ui.RuneButton
import com.ozorhaar.yggdrasil.launcher.ui.RuneText
import com.ozorhaar.yggdrasil.launcher.ui.ShieldImage
import com.ozorhaar.yggdrasil.launcher.ui.Ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Search everything: apps, sums, contacts, phone settings, your HA devices, and the web. */
@Composable
fun SearchOverlay(
    apps: List<AppEntry>,
    icons: ShieldIcons,
    home: HomeLink,
    onLaunch: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val q = query.trim()

    var contactsAllowed by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }

    val appHits = remember(apps, q) {
        if (q.isEmpty()) emptyList()
        else apps.filter { it.label.contains(q, ignoreCase = true) }
            .sortedBy { if (it.label.startsWith(q, ignoreCase = true)) 0 else 1 }.take(8)
    }
    val sum = remember(q) { Calc.evaluate(q) }
    val settingHits = remember(q) { if (q.length < 2) emptyList() else SETTINGS.filter { s -> s.words.any { it.contains(q.lowercase()) } }.take(4) }
    var contactHits by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var deviceHits by remember { mutableStateOf<List<HomeLink.Device>>(emptyList()) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var asked by remember { mutableStateOf<String?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    var thinking by remember { mutableStateOf(false) }

    fun askHafnir(text: String) {
        if (thinking || text.isBlank()) return
        keyboard?.hide()
        thinking = true; asked = text; answer = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { home.ask(text) } }
            thinking = false
            answer = r.getOrElse { "Couldn't reach Hafnir: ${it.message}" }
        }
    }

    LaunchedEffect(q, contactsAllowed) {
        if (q.length < 2) { contactHits = emptyList(); deviceHits = emptyList(); return@LaunchedEffect }
        delay(150) // let typing settle
        contactHits = if (contactsAllowed) withContext(Dispatchers.IO) { runCatching { findContacts(ctx, q) }.getOrDefault(emptyList()) } else emptyList()
        if (home.configured) {
            val r = withContext(Dispatchers.IO) { runCatching { home.find(q) } }
            deviceHits = r.getOrDefault(emptyList())
            homeError = r.exceptionOrNull()?.message
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun go(action: () -> Unit) {
        keyboard?.hide(); action(); onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ygg.Night.copy(alpha = 0.95f), Ygg.Night2.copy(alpha = 0.97f))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { RuneText("Seek…", 16.sp, Ygg.Ash) },
                textStyle = TextStyle(color = Ygg.Bone, fontSize = 17.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    when {
                        appHits.isNotEmpty() -> go { onLaunch(appHits.first()) }
                        q.isNotEmpty() && home.configured -> askHafnir(q)
                        q.isNotEmpty() -> go { webSearch(ctx, q) }
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ygg.Gold, unfocusedBorderColor = Ygg.Gold.copy(alpha = 0.4f), cursorColor = Ygg.Gold,
                ),
                shape = CutCornerShape(10.dp),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Spacer(Modifier.width(12.dp))
            RuneButton("Close") { keyboard?.hide(); onClose() }
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
            if (asked != null) {
                Header("Hafnir")
                RuneText("“$asked”", 14.sp, Ygg.Ash)
                if (thinking) RuneText("Thinking…", 18.sp, Ygg.Frost)
                answer?.let { androidx.compose.material3.Text(it, color = Ygg.Bone, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            if (sum != null) {
                Header("Reckoning")
                Hit("= $sum", "Tap to copy") {
                    ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("sum", sum))
                    Toast.makeText(ctx, "Copied $sum", Toast.LENGTH_SHORT).show()
                }
            }
            if (appHits.isNotEmpty()) {
                Header("Apps")
                appHits.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable { go { onLaunch(app) } }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShieldImage(app, icons, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        RuneText(app.label, 18.sp, Ygg.Bone)
                    }
                }
            }
            if (q.length >= 2) {
                if (contactsAllowed) {
                    if (contactHits.isNotEmpty()) Header("People")
                    contactHits.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { go { openContact(ctx, c) } }) {
                                RuneText(c.name, 18.sp, Ygg.Bone)
                                if (c.number != null) RuneText(c.number, 13.sp, Ygg.Ash)
                            }
                            if (c.number != null) {
                                RuneButton("Call") { go { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                                Spacer(Modifier.width(8.dp))
                                RuneButton("Text") { go { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", c.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                            }
                        }
                    }
                } else {
                    Hit("Search people too", "Lets the launcher read your contacts on this phone (they never leave it)") {
                        askContacts.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            }
            if (deviceHits.isNotEmpty() || homeError != null) {
                Header("The hall")
                homeError?.let { RuneText(it, 13.sp, Ygg.Ember) }
                deviceHits.forEach { d ->
                    var state by remember(d.entityId) { mutableStateOf(d.state) }
                    var busy by remember(d.entityId) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            RuneText(d.name, 18.sp, Ygg.Bone)
                            RuneText(state, 13.sp, Ygg.Ash)
                        }
                        d.copy(state = state).action?.let { (label, _) ->
                            RuneButton(if (busy) "…" else label) {
                                if (busy) return@RuneButton
                                busy = true
                                scope.launch {
                                    val r = withContext(Dispatchers.IO) { runCatching { home.act(d.copy(state = state)) } }
                                    busy = false
                                    r.onSuccess { state = it }.onFailure { Toast.makeText(ctx, it.message ?: "Failed", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        }
                        ?: d.copy(state = state).oneWayHint?.let { RuneText(it, 12.sp, Ygg.Ash.copy(alpha = 0.6f)) }
                    }
                }
            }
            if (settingHits.isNotEmpty()) {
                Header("Phone settings")
                settingHits.forEach { s -> Hit(s.label, null) { go { openSetting(ctx, s.action) } } }
            }
            if (q.isNotEmpty() && home.configured && q != asked) {
                Header("Ask Hafnir")
                Hit("“$q”", "He answers here; follow-ups for two minutes keep the thread") { askHafnir(q) }
            }
            if (q.isNotEmpty()) {
                Header("Beyond the walls")
                Hit("Search the web for “$q”", null) { go { webSearch(ctx, q) } }
            }
            Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
private fun Header(text: String) {
    Row(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).rotate(45f).background(Ygg.Gold))
        Spacer(Modifier.width(10.dp))
        RuneText(text, 15.sp, Ygg.Gold)
    }
}

@Composable
private fun Hit(title: String, sub: String?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .border(1.dp, Color.Transparent)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RuneText(title, 18.sp, Ygg.Bone)
        if (sub != null) RuneText(sub, 13.sp, Ygg.Ash)
    }
}

// ------------------------------------------------------------------ people
data class Contact(val id: Long, val lookup: String, val name: String, val number: String?)

private fun findContacts(ctx: Context, q: String): List<Contact> {
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
    val out = mutableListOf<Contact>()
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.HAS_PHONE_NUMBER),
        null, null, ContactsContract.Contacts.TIMES_CONTACTED + " DESC",
    )?.use { c ->
        while (c.moveToNext() && out.size < 5) {
            val id = c.getLong(0)
            val number = if (c.getInt(3) > 0) firstNumber(ctx, id) else null
            out += Contact(id, c.getString(1) ?: "", c.getString(2) ?: "?", number)
        }
    }
    return out
}

private fun firstNumber(ctx: Context, contactId: Long): String? =
    ctx.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY),
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(contactId.toString()),
        ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY + " DESC",
    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

private fun openContact(ctx: Context, c: Contact) {
    val uri = ContactsContract.Contacts.getLookupUri(c.id, c.lookup) ?: ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, c.id)
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ------------------------------------------------------------------ web and settings
private fun webSearch(ctx: Context, q: String) {
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(search) }.onFailure {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun openSetting(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(ctx, "Couldn't open that setting", Toast.LENGTH_SHORT).show() }
}

private class SettingEntry(val label: String, val action: String, vararg words: String) {
    val words: List<String> = (listOf(label.lowercase()) + words).map { it.lowercase() }
}

private val SETTINGS = listOf(
    SettingEntry("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi", "wireless", "network", "internet"),
    SettingEntry("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "pair", "headphones", "earbuds", "car"),
    SettingEntry("Mobile data and SIM", Settings.ACTION_DATA_ROAMING_SETTINGS, "cellular", "data", "sim", "roaming", "mobile"),
    SettingEntry("Hotspot and tethering", "android.settings.TETHER_SETTINGS", "hotspot", "tether"),
    SettingEntry("Display and brightness", Settings.ACTION_DISPLAY_SETTINGS, "screen", "brightness", "dark mode", "font", "refresh"),
    SettingEntry("Sounds and vibration", Settings.ACTION_SOUND_SETTINGS, "sound", "ringtone", "volume", "vibrat", "notification sound"),
    SettingEntry("Notifications", Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, "alerts", "do not disturb"),
    SettingEntry("Do not disturb", "android.settings.ZEN_MODE_SETTINGS", "dnd", "quiet", "silence"),
    SettingEntry("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS, "power", "saver", "charge"),
    SettingEntry("Apps", Settings.ACTION_APPLICATION_SETTINGS, "applications", "default apps", "permissions"),
    SettingEntry("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, "home app", "browser", "assistant"),
    SettingEntry("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "gps"),
    SettingEntry("Security and privacy", Settings.ACTION_SECURITY_SETTINGS, "security", "privacy", "lock screen", "fingerprint", "biometric", "password"),
    SettingEntry("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "talkback", "double-tap"),
    SettingEntry("Date and time", Settings.ACTION_DATE_SETTINGS, "clock", "timezone"),
    SettingEntry("Languages and keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, "keyboard", "language", "input", "yggdrasil keys"),
    SettingEntry("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "space", "files", "memory"),
    SettingEntry("NFC and payments", Settings.ACTION_NFC_SETTINGS, "nfc", "tap to pay"),
    SettingEntry("Wallpaper", Intent.ACTION_SET_WALLPAPER, "background"),
    SettingEntry("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "flight"),
    SettingEntry("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "developer", "adb", "wireless debugging", "usb debugging"),
    SettingEntry("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, "version", "model", "software update"),
    SettingEntry("Accounts", Settings.ACTION_SYNC_SETTINGS, "sync", "google", "samsung account"),
    SettingEntry("VPN", Settings.ACTION_VPN_SETTINGS, "proxy"),
)
