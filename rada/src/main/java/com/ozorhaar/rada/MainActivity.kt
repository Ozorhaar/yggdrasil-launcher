package com.ozorhaar.rada

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Point the camera at a QR code (or share a screenshot of one to Ráða): it shows what the code
 * really holds and, for a link, which site it belongs to and any scam signs, with trackers already
 * removed. Nothing opens until you tap.
 */
class MainActivity : ComponentActivity() {
    private val night = Color.rgb(11, 14, 21); private val gold = Color.rgb(236, 208, 143)
    private val bone = Color.rgb(232, 220, 196); private val ash = Color.rgb(150, 150, 160)
    private val ember = Color.rgb(224, 112, 58); private val frost = Color.rgb(143, 196, 224); private val moss = Color.rgb(140, 190, 130)

    private lateinit var root: FrameLayout
    private val analysis = Executors.newSingleThreadExecutor()
    @Volatile private var found = false
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = night; window.navigationBarColor = night
        root = FrameLayout(this).apply { setBackgroundColor(night) }
        setContentView(root)
        val shared = sharedImage(intent)
        if (shared != null) decodeImage(shared) else startScanning()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedImage(intent)?.let { decodeImage(it) }
    }

    private fun sharedImage(i: Intent?): Uri? =
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("image/") == true)
            @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM) else null

    // ------------------------------------------------------------------ camera
    private fun startScanning() {
        found = false
        root.removeAllViews()
        if (checkSelfPermission(Manifest.permission.CAMERA) != 0) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
            showMessage("Ráða needs the camera to read QR codes.\n\nYou can also share a screenshot of a QR code to Ráða.")
            return
        }
        val preview = PreviewView(this)
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(this).apply {
            text = "Point at a QR code"; textSize = 16f; setTextColor(gold); gravity = Gravity.CENTER
            setBackgroundColor(Color.argb(170, 11, 14, 21)); setPadding(dp(16), dp(14), dp(16), dp(14))
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val a = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            a.setAnalyzer(analysis) { img -> analyze(img) }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, p, a)
        }, mainExecutor)
    }

    private fun analyze(img: ImageProxy) {
        try {
            if (found) return
            val y = img.planes[0]
            val data = ByteArray(y.buffer.remaining()).also { y.buffer.get(it) }
            val source = PlanarYUVLuminanceSource(data, y.rowStride, img.height, 0, 0, img.width, img.height, false)
            decode(source)?.let { text ->
                found = true
                runOnUiThread {
                    ProcessCameraProvider.getInstance(this).get().unbindAll()
                    show(text)
                }
            }
        } finally { img.close() }
    }

    private fun decode(source: LuminanceSource): String? =
        runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()
            ?: runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert()))).text }.getOrNull()
            .also { reader.reset() }

    private fun decodeImage(uri: Uri) {
        val text = runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 2000) sample *= 2
            val bmp: Bitmap = contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }!!
            val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
            decode(RGBLuminanceSource(bmp.width, bmp.height, px))
        }.getOrNull()
        if (text != null) show(text) else {
            root.removeAllViews()
            showMessage("No QR code found in that picture.")
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (results.firstOrNull() == 0) startScanning()
    }

    // ------------------------------------------------------------------ results
    private fun page(): LinearLayout {
        root.removeAllViews()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)) }
        root.addView(ScrollView(this).apply { addView(col) }, FrameLayout.LayoutParams(-1, -1))
        return col
    }

    private fun LinearLayout.text(s: String, size: Float, color: Int, bold: Boolean = false, top: Int = 8) = addView(TextView(context).apply {
        text = s; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(top), 0, 0); setTextIsSelectable(true)
    })

    private fun LinearLayout.button(s: String, filled: Boolean = true, go: () -> Unit) = addView(Button(context).apply {
        text = s; isAllCaps = false
        setTextColor(if (filled) night else gold)
        background = GradientDrawable().apply { setColor(if (filled) gold else night); cornerRadius = dp(8).toFloat(); setStroke(dp(1), gold) }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
        setOnClickListener { go() }
    })

    private fun showMessage(s: String) {
        val col = page()
        col.text("Ráða", 28f, gold, bold = true, top = 0)
        col.text(s, 15f, bone, top = 14)
        col.button("Scan") { startScanning() }
    }

    private fun copy(label: String, s: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, s))
    }

    private fun show(raw: String) {
        val col = page()
        col.text("What the code holds", 22f, gold, bold = true, top = 0)
        val text = raw.trim()
        val clean = if (LinkCheck.looksLikeLink(text)) LinkCleaner.clean(text) else null
        val report = LinkCheck.check(clean?.text ?: text)
        when {
            report != null -> showLink(col, text, clean!!, report)
            text.startsWith("WIFI:", ignoreCase = true) -> showWifi(col, text)
            else -> {
                val scheme = text.substringBefore(':', "").lowercase()
                val kind = when (scheme) { "tel" -> "A phone number to call"; "sms", "smsto" -> "A text message to send"
                    "mailto" -> "An email to send"; "geo" -> "A place on a map"; "upi", "bitcoin", "ethereum" -> "A payment request"; else -> "Text" }
                col.text(kind, 15f, bone, top = 14)
                col.text(text, 15f, if (scheme in setOf("bitcoin", "ethereum", "upi")) ember else bone)
                if (scheme in setOf("bitcoin", "ethereum", "upi")) col.text("A QR code asking you to send money is a classic scam. Only pay if you know exactly who made this code.", 14f, ember)
                col.button("Copy") { copy("QR code", text) }
                if (scheme in setOf("tel", "sms", "smsto", "mailto", "geo"))
                    col.button("Open in its app", filled = false) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(text))) } }
            }
        }
        col.button("Scan another", filled = false) { startScanning() }
    }

    private fun showLink(col: LinearLayout, original: String, clean: LinkCleaner.Result, r: LinkCheck.Report) {
        val (verdict, color) = when (r.level) {
            LinkCheck.Level.OK -> "No warning signs" to moss
            LinkCheck.Level.CAUTION -> "Be careful" to frost
            LinkCheck.Level.DANGER -> "Looks like a scam" to ember
        }
        col.text("A link to", 14f, ash, top = 14)
        col.text(r.site, 26f, color, bold = true, top = 2)
        if (r.host != r.site) col.text(r.host, 14f, ash, top = 2)
        col.addView(TextView(this).apply {
            text = verdict; textSize = 16f; setTextColor(night); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply { setColor(color); cornerRadius = dp(6).toFloat() }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) }
        })
        r.signs.forEach { col.text("•  " + it.text, 14f, if (it.level == LinkCheck.Level.DANGER) ember else bone, top = 8) }
        if (clean.removed > 0) col.text("Removed ${clean.removed} tracker${if (clean.removed == 1) "" else "s"}.", 13f, ash, top = 12)
        col.text("Full link", 14f, ash, top = 16)
        col.text(r.url, 13f, bone, top = 2)
        if (clean.text != original) { col.text("As scanned", 14f, ash, top = 10); col.text(original, 12f, ash, top = 2) }
        val open = { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addCategory(Intent.CATEGORY_BROWSABLE)) } }
        if (r.level == LinkCheck.Level.DANGER) {
            col.button("Copy the link") { copy("link", r.url) }
            col.button("Open anyway", filled = false) { open() }
        } else {
            col.button("Open the link") { open() }
            col.button("Copy the link", filled = false) { copy("link", r.url) }
        }
    }

    private fun showWifi(col: LinearLayout, text: String) {
        fun field(k: String) = Regex("""(?:^WIFI:|;)$k:((?:\\.|[^;])*)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.replace(Regex("""\\(.)"""), "$1")
        val name = field("S").orEmpty(); val sec = field("T").orEmpty().ifBlank { "nopass" }; val pass = field("P")
        col.text("A Wi-Fi network", 15f, bone, top = 14)
        col.text(name.ifBlank { "(no name)" }, 24f, gold, bold = true, top = 4)
        col.text(if (sec.equals("nopass", true)) "Open network: no password, anyone nearby can see unencrypted traffic." else "Security: $sec", 14f,
            if (sec.equals("nopass", true) || sec.equals("WEP", true)) ember else ash)
        if (!pass.isNullOrEmpty()) col.button("Copy the password") { copy("Wi-Fi password", pass) }
        col.button("Open Wi-Fi settings", filled = false) { startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
    }

    override fun onDestroy() { super.onDestroy(); analysis.shutdown() }
}
