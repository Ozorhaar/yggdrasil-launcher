package com.ozorhaar.yggdrasil.launcher.ui

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.ygg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The horn call: hold Hafnir's mark, type (or speak) a few words, and Jarvis announces them
 * through the house on SatOne, SatTwo or both. Nothing is sent until "Sound the horn".
 */
@Composable
fun HornCallSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val link = ctx.ygg.homeLink
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(link.satellites.map { it.first }.toSet()) }
    var sending by remember { mutableStateOf(false) }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { words = it }
    }
    val canSpeak = remember {
        ctx.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        val shape = CutCornerShape(16.dp)
        Column(
            Modifier.fillMaxWidth().background(Ygg.Night2, shape).border(1.5.dp, Ygg.Frost, shape).padding(16.dp),
        ) {
            RuneText("Horn call", 22.sp, Ygg.GoldHi)
            androidx.compose.material3.Text("Jarvis will say it through the house.", color = Ygg.Ash, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = words, onValueChange = { if (it.length <= 400) words = it },
                placeholder = { RuneText("Dinner's ready…", 16.sp, Ygg.Ash) },
                textStyle = TextStyle(color = Ygg.Bone, fontSize = 17.sp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ygg.Frost, unfocusedBorderColor = Ygg.Frost.copy(alpha = 0.4f), cursorColor = Ygg.Frost),
                shape = CutCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                link.satellites.forEach { (id, name) ->
                    val on = id in picked
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .background(if (on) Ygg.Frost else Ygg.Night, CutCornerShape(8.dp))
                            .border(1.dp, Ygg.Frost, CutCornerShape(8.dp))
                            .clickable { picked = if (on) picked - id else picked + id }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) { RuneText(name, 15.sp, if (on) Ygg.Night else Ygg.Frost) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canSpeak) RuneButton("Speak") {
                    speak.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should Jarvis say?"))
                }
                RuneButton(if (sending) "Sounding…" else "Sound the horn") {
                    if (sending) return@RuneButton
                    if (words.isBlank() || picked.isEmpty()) {
                        Toast.makeText(ctx, if (words.isBlank()) "Write what to say first" else "Pick a satellite", Toast.LENGTH_SHORT).show()
                        return@RuneButton
                    }
                    sending = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { link.announce(picked.toList(), words) }.isSuccess }
                        sending = false
                        Toast.makeText(ctx, if (ok) "The horn sounds" else "Couldn't reach Home Assistant", Toast.LENGTH_SHORT).show()
                        if (ok) onClose()
                    }
                }
                RuneButton("Close") { onClose() }
            }
        }
    }
}
