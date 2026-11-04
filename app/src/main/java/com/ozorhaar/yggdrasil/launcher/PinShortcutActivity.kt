package com.ozorhaar.yggdrasil.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast

/**
 * Handles an app's "Add to home screen" request (requestPinShortcut), such as
 * Home Assistant's Assist shortcut. Asks once, then pins it on the home screen.
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val req = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        val info = req?.shortcutInfo
        if (req == null || req.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || info == null || !req.isValid) {
            finish(); return
        }
        val label = (info.shortLabel ?: info.longLabel ?: info.id).toString()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Add to Yggdrasil home")
            .setMessage("Add “$label” to the home screen?")
            .setPositiveButton("Add") { _, _ ->
                if (runCatching { req.accept() }.getOrDefault(false)) {
                    val repo = ygg.repo
                    repo.reloadShortcuts()
                    ygg.layout.addToHome(repo.shortcutKey(info.`package`, info.id, info.userHandle))
                    Toast.makeText(this, "$label added", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
