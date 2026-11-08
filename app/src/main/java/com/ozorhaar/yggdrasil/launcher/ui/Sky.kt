package com.ozorhaar.yggdrasil.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozorhaar.yggdrasil.launcher.weather.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sky: Yggdrasil's own weather card, a built-in widget. It shows the same reading the runestone
 * clock and the living wallpaper use (Open-Meteo, cached 30 minutes), carved in the launcher's style.
 */
object SkyCard {
    const val PROVIDER = "ygg:sky"
    const val ID = -2
    const val WIDTH = 3
    const val HEIGHT = 1
}

@Composable
fun SkyCardView() {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Weather.cached(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = withContext(Dispatchers.IO) { Weather.get(ctx) } ?: now
            delay(15 * 60 * 1000L)
        }
    }
    val shape = CutCornerShape(14.dp)
    Row(
        Modifier.fillMaxSize()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Ygg.Night2, Ygg.Night)), shape)
            .border(1.5.dp, Ygg.Gold.copy(alpha = 0.85f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val n = now
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f).clip(CircleShape)
                .background(Ygg.Iron, CircleShape).border(2.dp, Ygg.Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(9.dp)) { drawSky(n?.sky ?: Weather.Sky.CLOUDY, n?.isDay ?: true) }
        }
        Spacer(Modifier.width(10.dp))
        if (n == null) {
            Text("Reading the sky…", style = TextStyle(color = Ygg.Ash, fontSize = 15.sp, fontFamily = Ygg.Runic))
            return@Row
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${n.tempF}°", style = TextStyle(color = Ygg.GoldHi, fontSize = 24.sp, fontFamily = Ygg.Runic))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${n.highF}° / ${n.lowF}°", maxLines = 1,
                    style = TextStyle(color = Ygg.Ash, fontSize = 13.sp, fontFamily = Ygg.Runic),
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            val t = DateFormat.getTimeInstance(DateFormat.SHORT)
            val sun = if (System.currentTimeMillis() < n.sunset) "Sunset ${t.format(Date(n.sunset))}" else "Sunrise ${t.format(Date(n.sunrise + 24 * 3600 * 1000L))}"
            Text(
                "${n.words} · $sun", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Ygg.Bone, fontSize = 13.sp, fontFamily = Ygg.Runic),
            )
        }
    }
}

/** Carved sky signs: sun, moon, cloud, rain, snow, fog, Thor's lightning. */
private fun DrawScope.drawSky(sky: Weather.Sky, day: Boolean) {
    val w = size.width; val h = size.height
    val stroke = w * 0.08f
    fun sun(c: Offset, r: Float) {
        drawCircle(Ygg.GoldHi, r, c)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
            drawLine(Ygg.GoldHi, Offset(c.x + ux * r * 1.45f, c.y + uy * r * 1.45f), Offset(c.x + ux * r * 1.95f, c.y + uy * r * 1.95f), stroke * 0.8f, StrokeCap.Round)
        }
    }
    fun moon(c: Offset, r: Float) {
        drawCircle(Ygg.Bone, r, c)
        drawCircle(Ygg.Iron, r * 0.86f, Offset(c.x + r * 0.45f, c.y - r * 0.2f))
    }
    fun cloud(y: Float, color: Color = Ygg.Bone) {
        drawCircle(color, w * 0.17f, Offset(w * 0.36f, y))
        drawCircle(color, w * 0.22f, Offset(w * 0.56f, y - h * 0.07f))
        drawCircle(color, w * 0.15f, Offset(w * 0.74f, y + h * 0.02f))
        drawRect(color, Offset(w * 0.2f, y), Size(w * 0.68f, h * 0.15f))
    }
    fun drops(color: Color, flakes: Boolean) {
        for (i in 0 until 3) {
            val x = w * (0.32f + i * 0.2f); val y = h * 0.78f
            if (flakes) {
                for (k in 0 until 3) {
                    val a = Math.toRadians(k * 60.0)
                    val dx = cos(a).toFloat() * w * 0.06f; val dy = sin(a).toFloat() * w * 0.06f
                    drawLine(color, Offset(x - dx, y - dy), Offset(x + dx, y + dy), stroke * 0.5f, StrokeCap.Round)
                }
            } else drawLine(color, Offset(x, y - h * 0.06f), Offset(x - w * 0.05f, y + h * 0.1f), stroke * 0.7f, StrokeCap.Round)
        }
    }
    when (sky) {
        Weather.Sky.CLEAR -> if (day) sun(Offset(w / 2, h / 2), w * 0.22f) else moon(Offset(w / 2, h / 2), w * 0.34f)
        Weather.Sky.PARTLY -> {
            if (day) sun(Offset(w * 0.64f, h * 0.36f), w * 0.15f) else moon(Offset(w * 0.64f, h * 0.34f), w * 0.2f)
            cloud(h * 0.62f)
        }
        Weather.Sky.CLOUDY -> cloud(h * 0.55f)
        Weather.Sky.FOG -> for (i in 0 until 4) {
            val y = h * (0.28f + i * 0.15f)
            drawLine(Ygg.Bone, Offset(w * (0.12f + (i % 2) * 0.08f), y), Offset(w * (0.88f - (i % 2) * 0.08f), y), stroke, StrokeCap.Round)
        }
        Weather.Sky.DRIZZLE, Weather.Sky.RAIN -> { cloud(h * 0.42f); drops(Ygg.Frost, false) }
        Weather.Sky.SNOW -> { cloud(h * 0.42f); drops(Ygg.Frost, true) }
        Weather.Sky.THUNDER -> {
            cloud(h * 0.38f)
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.56f, h * 0.5f); lineTo(w * 0.4f, h * 0.76f); lineTo(w * 0.52f, h * 0.76f)
                lineTo(w * 0.44f, h * 0.98f); lineTo(w * 0.66f, h * 0.68f); lineTo(w * 0.54f, h * 0.68f); close()
            }
            drawPath(p, Ygg.GoldHi)
        }
    }
}
