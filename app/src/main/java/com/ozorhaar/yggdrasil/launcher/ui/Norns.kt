package com.ozorhaar.yggdrasil.launcher.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Norns: Yggdrasil's own calendar card, a built-in widget showing what's coming from your Nine
 * (Exchange) calendar, read from the phone's own calendar store where Nine syncs it. It needs
 * calendar access (read only); nothing leaves the phone.
 */
object NornsCard {
    const val PROVIDER = "ygg:norns"
    const val ID = -3
    const val WIDTH = 3
    const val HEIGHT = 1
    /** Nine's account type in the calendar store: only its calendars are shown. */
    const val NINE = "com.ninefolders.hd3"
}

private class Thread(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

private fun granted(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** The next events from Nine's visible calendars over the coming week (ones already over are skipped). */
private fun upcoming(ctx: Context, max: Int = 2): List<Thread> {
    if (!granted(ctx)) return emptyList()
    val now = System.currentTimeMillis()
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, now - 12 * 3600 * 1000L)
        ContentUris.appendId(it, now + 7 * 24 * 3600 * 1000L)
    }.build()
    val out = mutableListOf<Thread>()
    runCatching {
        ctx.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
            ),
            "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.END} > ? AND " +
                "${CalendarContract.Instances.CALENDAR_ID} IN (" + nineCalendars(ctx).joinToString(",").ifEmpty { "-1" } + ")",
            arrayOf(now.toString()),
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < max) {
                val allDay = c.getInt(4) == 1
                out += Thread(c.getLong(0), c.getString(1)?.takeIf { it.isNotBlank() } ?: "(busy)", c.getLong(2), c.getLong(3), allDay)
            }
        }
    }
    return out
}

private fun nineCalendars(ctx: Context): List<Long> {
    val ids = mutableListOf<Long>()
    runCatching {
        ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
            arrayOf(NornsCard.NINE), null,
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
    }
    return ids
}

/** "Now", "3:00 PM", "Tomorrow 9:30 AM", "Wed 2:00 PM", "Fri" (all day). */
private fun whenText(t: Thread, now: Long): String {
    if (t.begin <= now && now < t.end && !t.allDay) return "Now"
    val tf = DateFormat.getTimeInstance(DateFormat.SHORT)
    val cal = Calendar.getInstance()
    val today = cal.apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    // all-day events are stored in UTC midnight; read them as that calendar day
    val begin = if (t.allDay) t.begin - java.util.TimeZone.getDefault().getOffset(t.begin) else t.begin
    val dayMs = 24 * 3600 * 1000L
    val day = when (Math.floorDiv(begin - today, dayMs)) {
        0L -> if (t.allDay) "Today" else ""
        1L -> "Tomorrow"
        else -> DateUtils.formatDateTime(null, begin, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
    }
    if (t.allDay) return day
    return listOf(day, tf.format(Date(t.begin))).filter { it.isNotEmpty() }.joinToString(" ")
}

@Composable
fun NornsCardView() {
    val ctx = LocalContext.current
    var events by remember { mutableStateOf<List<Thread>>(emptyList()) }
    var allowed by remember { mutableStateOf(granted(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> allowed = ok; refresh++ }

    LaunchedEffect(allowed, refresh) {
        while (true) {
            events = withContext(Dispatchers.IO) { upcoming(ctx) }
            delay(5 * 60 * 1000L)
        }
    }
    // re-read as soon as Nine syncs a change
    DisposableEffect(allowed) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { refresh++ }
        }
        if (allowed) runCatching { ctx.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, obs) }
        onDispose { runCatching { ctx.contentResolver.unregisterContentObserver(obs) } }
    }

    val shape = CutCornerShape(14.dp)
    Row(
        Modifier.fillMaxSize()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Ygg.Night2, Ygg.Night)), shape)
            .border(1.5.dp, Dusk.gold.copy(alpha = 0.85f), shape)
            .clickable {
                if (!allowed) ask.launch(Manifest.permission.READ_CALENDAR) else openCalendar(ctx, events.firstOrNull())
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape)
                .background(Dusk.gold, CircleShape).border(2.dp, Ygg.Night, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.glyph_calendar), contentDescription = null,
                colorFilter = ColorFilter.tint(Ygg.Night), modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            val now = System.currentTimeMillis()
            when {
                !allowed -> {
                    Line("Allow calendar access", Ygg.Bone, 15)
                    Line("Tap to let the Norns read Nine", Ygg.Ash, 12)
                }
                events.isEmpty() -> {
                    Line("Nothing woven", Ygg.Bone, 15)
                    Line("for the coming week", Ygg.Ash, 12)
                }
                else -> events.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            whenText(t, now), maxLines = 1,
                            style = TextStyle(color = if (i == 0) Ygg.GoldHi else Ygg.Ash, fontSize = if (i == 0) 14.sp else 12.sp, fontFamily = Ygg.Runic),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = if (i == 0) Ygg.Bone else Ygg.Ash, fontSize = if (i == 0) 14.sp else 12.sp, fontFamily = Ygg.Runic),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(text: String, color: androidx.compose.ui.graphics.Color, size: Int) = Text(
    text, maxLines = 1, overflow = TextOverflow.Ellipsis,
    style = TextStyle(color = color, fontSize = size.sp, fontFamily = Ygg.Runic),
)

/** Opens the event in Nine (or whatever handles calendar events), else Nine's calendar. */
private fun openCalendar(ctx: Context, t: Thread?) {
    if (t != null) {
        val i = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, t.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, t.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, t.end)
            .setPackage(NornsCard.NINE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return
    }
    val cal = Intent().setClassName(NornsCard.NINE, "com.ninefolders.hd3.activity.CalendarAliasActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(cal) }.isSuccess) return
    ctx.packageManager.getLaunchIntentForPackage(NornsCard.NINE)?.let { runCatching { ctx.startActivity(it) } }
}
