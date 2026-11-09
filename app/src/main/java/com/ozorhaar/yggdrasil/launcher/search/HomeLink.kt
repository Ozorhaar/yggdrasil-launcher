package com.ozorhaar.yggdrasil.launcher.search

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Home Assistant devices in search. Talks to HA over HTTPS only, with an access token
 * that's encrypted by a key that lives in the phone's hardware keystore (it can't be
 * read out, backed up or copied to another phone).
 *
 * Only devices you've ticked in the launcher's settings show up (an allowlist, so a
 * relay that opens something can't sneak in), and it only does things that are
 * safe from an unlocked home screen: switch lights, plugs and fans, lock a lock, close
 * a cover (garage) and run a scene. It never unlocks, opens, or disarms anything.
 */
class HomeLink(context: Context) {
    private val prefs = context.getSharedPreferences("ygg_home_link", Context.MODE_PRIVATE)

    data class Device(val entityId: String, val name: String, val state: String) {
        val domain: String get() = entityId.substringBefore('.')

        /** The one safe action for this device right now, or null. */
        val action: Pair<String, String>?
            get() = when (domain) {
                "light", "switch", "fan", "input_boolean" ->
                    if (state == "on") "Turn off" to "turn_off" else "Turn on" to "turn_on"
                "lock" -> if (state == "unlocked" || state == "open") "Lock" to "lock" else null
                "cover" -> if (state == "open" || state == "opening") "Close" to "close_cover" else null
                "scene" -> "Activate" to "turn_on"
                else -> null
            }

        /** Why there's no button, for devices search will only ever move one way. */
        val oneWayHint: String?
            get() = if (action != null) null else when (domain) {
                "lock" -> "Can only lock from here"
                "cover" -> "Can only close from here"
                else -> null
            }
    }

    val url: String get() = prefs.getString("url", DEFAULT_URL) ?: DEFAULT_URL
    val configured: Boolean get() = prefs.contains("token")

    private var cache: List<Device> = emptyList()
    private var cachedAt = 0L

    /** Saves the address and token after checking HA accepts them. Throws with a reason if not. */
    fun connect(rawUrl: String, token: String) {
        val u = rawUrl.trim().trimEnd('/')
        require(u.startsWith("https://")) { "Use the https:// address" }
        require(token.isNotBlank()) { "Paste a token first" }
        val reply = request(u, token.trim(), "GET", "/api/", null)
        require(reply.contains("API running")) { "Home Assistant didn't answer as expected" }
        prefs.edit().putString("url", u).putString("token", encrypt(token.trim())).apply()
        cache = emptyList()
    }

    fun forget() {
        prefs.edit().remove("token").apply()
        cache = emptyList()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    /** Devices matching [query] by name or id (blocking; call off the main thread). */
    fun find(query: String): List<Device> {
        val q = query.trim().lowercase()
        if (q.length < 2 || !configured) return emptyList()
        if (System.currentTimeMillis() - cachedAt > 60_000 || cache.isEmpty()) {
            cache = allDevices()
            cachedAt = System.currentTimeMillis()
        }
        val ok = allowed
        return cache.filter { it.entityId in ok && (q in it.name.lowercase() || q in it.entityId) }.take(6)
    }

    /** Every device HA has in the domains search can handle (blocking), for choosing which to allow. */
    fun allDevices(): List<Device> {
        val arr = JSONArray(request(url, token(), "GET", "/api/states", null))
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.getString("entity_id").substringBefore('.') in DOMAINS }
            .map {
                Device(
                    it.getString("entity_id"),
                    it.optJSONObject("attributes")?.optString("friendly_name")?.takeIf { n -> n.isNotBlank() } ?: it.getString("entity_id"),
                    it.optString("state"),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    /** Entity ids you've allowed in search. */
    val allowed: Set<String> get() = prefs.getStringSet("allowed", emptySet()) ?: emptySet()

    fun setAllowed(entityId: String, on: Boolean) {
        val now = allowed.toMutableSet()
        if (on) now += entityId else now -= entityId
        prefs.edit().putStringSet("allowed", now).apply()
    }

    private var chatId: String? = null
    private var chatAt = 0L

    /**
     * Asks Hafnir (HA's conversation agent) a typed question and returns what he'd say.
     * Follow-ups within two minutes stay in the same conversation. Blocking.
     */
    fun ask(text: String): String {
        // Same guard rail as Hafnir's own: voice/typing never unlocks, opens a door/garage/gate or disarms.
        if (Backstop.isForbidden(text)) return Backstop.REPLY
        val body = JSONObject().put("text", text).put("language", "en").put("agent_id", AGENT)
        chatId?.takeIf { System.currentTimeMillis() - chatAt < 120_000 }?.let { body.put("conversation_id", it) }
        val r = JSONObject(request(url, token(), "POST", "/api/conversation/process", body.toString()))
        chatId = r.optString("conversation_id").takeIf { it.isNotBlank() }
        chatAt = System.currentTimeMillis()
        return r.optJSONObject("response")?.optJSONObject("speech")?.optJSONObject("plain")?.optString("speech")
            ?.takeIf { it.isNotBlank() } ?: "No answer came back."
    }

    /** Runs the device's safe action. Returns its new state as HA reports it. */
    fun act(d: Device): String {
        val (_, service) = d.action ?: error("Nothing safe to do with ${d.name}")
        require(service in SAFE_SERVICES && d.entityId in allowed) // belt and braces: never anything else
        request(url, token(), "POST", "/api/services/${d.domain}/$service", JSONObject().put("entity_id", d.entityId).toString())
        cachedAt = 0L
        return runCatching {
            JSONObject(request(url, token(), "GET", "/api/states/${d.entityId}", null)).optString("state")
        }.getOrDefault("?")
    }

    /**
     * The two doors the dock's marks work: the side door lock and the garage door. These are the
     * only things on the phone that may ever unlock or open, and the dock asks for your fingerprint
     * (always fresh) before it calls [setGate] with open = true. Hafnir and search stay one-way.
     */
    enum class Gate(val entityId: String, val label: String, val camera: String) {
        SIDE_DOOR("lock.side_door_2", "Side door", "camera.frigate_side_doorbell"),
        GARAGE("cover.garage_rat_door", "Garage door", "camera.frigate_driveway"),
    }

    /**
     * The doors the raven watches: Frigate's person sensor and camera at each, and the gate (if
     * any) the camera peek can work from there. The front door has none: its peek only looks.
     */
    enum class Lookout(val label: String, val camera: String, val watcher: String, val gate: Gate?) {
        SIDE("Side door", Gate.SIDE_DOOR.camera, "binary_sensor.side_doorbell_person_occupancy_2", Gate.SIDE_DOOR),
        DRIVEWAY("Garage door", Gate.GARAGE.camera, "binary_sensor.driveway_person_occupancy", Gate.GARAGE),
        FRONT("Front door", "camera.frigate_front_doorbell", "binary_sensor.front_doorbell_person_occupancy_2", null);

        companion object {
            fun of(g: Gate): Lookout = entries.first { it.gate == g }
        }
    }

    /** What's going on around the steading, for the longhouse's sheet (all read only). */
    data class Steading(
        /** Leak sensors that are wet, by name. */
        val leaks: List<String> = emptyList(),
        /** Surtur the smoker: running, its temperature and target, the meat probe and its target, pellets. */
        val smokerOn: Boolean = false,
        val smokerTemp: String? = null, val smokerTarget: String? = null,
        val probeTemp: String? = null, val probeTarget: String? = null,
        val pellets: String? = null,
        /** The probe alarm or the cook timer has gone off. */
        val smokerAlarm: Boolean = false,
        /** The Bambu printer (named Yggdrasil): printing, progress %, layer of layers. */
        val printing: Boolean = false, val printProgress: Int = 0,
        val layer: String? = null, val layers: String? = null,
    ) {
        val busy: Boolean get() = leaks.isNotEmpty() || smokerOn || printing
    }

    private fun stateOf(id: String): JSONObject? =
        runCatching { JSONObject(request(url, token(), "GET", "/api/states/$id", null)) }.getOrNull()

    private fun JSONObject.attr(k: String): String? =
        optJSONObject("attributes")?.opt(k)?.takeIf { it != JSONObject.NULL }?.toString()

    /** One read of the smoker, the printer and the leak sensors. Blocking. */
    fun steading(): Steading {
        val leaks = LEAKS.mapNotNull { id -> stateOf(id)?.takeIf { it.optString("state") == "on" }?.let { it.attr("friendly_name") ?: id } }
        val smoker = stateOf("climate.surtur_climate")
        val smokerOn = smoker?.optString("state")?.let { it !in setOf("off", "unavailable", "unknown", "") } == true
        val probe = if (smokerOn) stateOf("climate.back_patio_smoker_probe") else null
        val alarm = smokerOn && (stateOf("binary_sensor.surtur_probe_alarm_fired")?.optString("state") == "on" ||
            stateOf("binary_sensor.surtur_cook_timer_complete")?.optString("state") == "on")
        val print = stateOf("sensor.yggdrasil_print_status")?.optString("state")
        val printing = print == "running" || print == "prepare" || print == "pause"
        return Steading(
            leaks = leaks,
            smokerOn = smokerOn,
            smokerTemp = smoker?.attr("current_temperature"), smokerTarget = smoker?.attr("temperature"),
            probeTemp = probe?.attr("current_temperature"), probeTarget = probe?.attr("temperature"),
            pellets = if (smokerOn) stateOf("sensor.surtur_pellet_level")?.optString("state") else null,
            smokerAlarm = alarm,
            printing = printing,
            printProgress = if (printing) stateOf("sensor.yggdrasil_print_progress")?.optString("state")?.toFloatOrNull()?.toInt() ?: 0 else 0,
            layer = if (printing) stateOf("sensor.yggdrasil_current_layer")?.optString("state") else null,
            layers = if (printing) stateOf("sensor.yggdrasil_total_layer_count")?.optString("state") else null,
        )
    }

    /** Yesterday's solar generation in kWh (the most the daily counter reached), or null. Blocking. */
    fun solarYesterday(): Float? = runCatching {
        val tz = java.util.TimeZone.getDefault()
        val cal = java.util.Calendar.getInstance(tz).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val end = cal.timeInMillis; val start = end - 24 * 3600_000L
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
        fun enc(ms: Long) = java.net.URLEncoder.encode(fmt.format(java.util.Date(ms)), "UTF-8")
        val body = request(url, token(), "GET",
            "/api/history/period/${enc(start)}?end_time=${enc(end)}&filter_entity_id=$SOLAR_TODAY&minimal_response&no_attributes", null)
        val rows = JSONArray(body).optJSONArray(0) ?: return null
        (0 until rows.length()).mapNotNull { rows.getJSONObject(it).optString("state").toFloatOrNull() }.maxOrNull()
    }.getOrNull()

    /**
     * Hafnir's voice: asks Home Assistant to speak [text] with the same Piper voice his pipeline
     * uses, and returns a URL to the audio. Blocking.
     */
    fun speechUrl(text: String): String {
        val body = JSONObject().put("engine_id", "tts.piper_2").put("message", text).put("language", "en_GB")
            .put("options", JSONObject().put("voice", "en_GB-northern_english_male-medium"))
        val r = JSONObject(request(url, token(), "POST", "/api/tts_get_url", body.toString()))
        val path = r.optString("path").ifBlank { java.net.URL(r.getString("url")).path }
        return url + path
    }

    private var parcelIds: Map<Lookout, List<String>> = emptyMap()
    private var parcelIdsAt = 0L

    /**
     * Where Frigate sees a package waiting: front or side doorbell. Finds the package sensors by
     * name (Frigate names them like binary_sensor.front_doorbell_package_occupancy) every 10
     * minutes, then reads just those. Read only. Blocking.
     */
    fun parcels(): Set<Lookout> {
        if (System.currentTimeMillis() - parcelIdsAt > 10 * 60_000L) {
            val arr = JSONArray(request(url, token(), "GET", "/api/states", null))
            val ids = (0 until arr.length()).map { arr.getJSONObject(it).optString("entity_id") }
                .filter { it.startsWith("binary_sensor.") && it.contains("package_occupancy") }
            parcelIds = mapOf(
                Lookout.FRONT to ids.filter { it.contains("front_doorbell") },
                Lookout.SIDE to ids.filter { it.contains("side_doorbell") },
            )
            parcelIdsAt = System.currentTimeMillis()
        }
        return parcelIds.filter { (_, ids) ->
            ids.any { JSONObject(request(url, token(), "GET", "/api/states/$it", null)).optString("state") == "on" }
        }.keys
    }

    /** Whether Frigate sees someone at this door right now (its person sensor). Read only. Blocking. */
    fun seen(l: Lookout): Boolean =
        JSONObject(request(url, token(), "GET", "/api/states/${l.watcher}", null)).optString("state") == "on"

    /** A still from the door's camera (JPEG bytes), through Home Assistant's camera proxy. Blocking. */
    fun snapshot(camera: String): ByteArray {
        require(camera in CAMERAS)
        val c = URL(url + "/api/camera_proxy/" + camera).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 4000; c.readTimeout = 8000; c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer ${token()}")
            val code = c.responseCode
            if (code !in 200..299) error("Camera answered $code")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    /** The door's state as HA reports it (locked/unlocked/open/closed/opening/...). Blocking. */
    fun gateState(g: Gate): String =
        JSONObject(request(url, token(), "GET", "/api/states/${g.entityId}", null)).optString("state")

    /**
     * One read of the house for the wallpaper: any light on, garage open, side door unlocked,
     * whether Jason or Leah is home (the hearth), and what the solar panels are making. Blocking.
     */
    fun glance(): com.ozorhaar.yggdrasil.launcher.wallpaper.FjordScene.House {
        val arr = JSONArray(request(url, token(), "GET", "/api/states", null))
        var lights = false; var garage = false; var door = false; var hearth = false; var solar = 0f; var use = -1f
        var leak = false; var fire = false; var forge = false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("entity_id"); val st = o.optString("state")
            when {
                id.startsWith("light.") && st == "on" -> lights = true
                id == Gate.GARAGE.entityId -> garage = st == "open" || st == "opening"
                id == Gate.SIDE_DOOR.entityId -> door = st == "unlocked" || st == "open"
                id in HEARTH_KEEPERS && st == "home" -> hearth = true
                // a smaller zone inside home (like Driveway Exit) shows as the person's state,
                // but Home Assistant still lists them in the home zone
                id == "zone.home" -> o.optJSONObject("attributes")?.optJSONArray("persons")?.let { ps ->
                    for (j in 0 until ps.length()) if (ps.optString(j) in HEARTH_KEEPERS) hearth = true
                }
                id == SOLAR -> solar = st.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
                id == HOUSE_USE -> use = st.toFloatOrNull() ?: -1f
                id in LEAKS && st == "on" -> leak = true
                id == "climate.surtur_climate" -> fire = st !in setOf("off", "unavailable", "unknown", "")
                id == "sensor.yggdrasil_print_status" -> forge = st == "running" || st == "prepare" || st == "pause"
            }
        }
        // sending power to the grid when the panels make more than the house uses (unknown use: no)
        val export = if (use >= 0f) solar - use else 0f
        return com.ozorhaar.yggdrasil.launcher.wallpaper.FjordScene.House(lights, garage, door, hearth, solar, export, leak, fire, forge)
    }

    /** Secures ([open] = false: lock/close) or opens ([open] = true: unlock/open) one of the two doors. Blocking. */
    fun setGate(g: Gate, open: Boolean) {
        val domain = g.entityId.substringBefore('.')
        val service = when (domain) {
            "lock" -> if (open) "unlock" else "lock"
            "cover" -> if (open) "open_cover" else "close_cover"
            else -> error("Not a door")
        }
        request(url, token(), "POST", "/api/services/$domain/$service", JSONObject().put("entity_id", g.entityId).toString())
        cachedAt = 0L
    }

    /** The house's latitude/longitude from Home Assistant's config, or null if not connected. Blocking. */
    fun homeLocation(): Pair<Double, Double>? = runCatching {
        if (!configured) return null
        val j = org.json.JSONObject(request(url, token(), "GET", "/api/config", null))
        j.getDouble("latitude") to j.getDouble("longitude")
    }.getOrNull()

    private fun request(base: String, token: String, method: String, path: String, body: String?): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 4000
            c.readTimeout = if (path.startsWith("/api/conversation")) 30000 else 6000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            if (code == 401) error("Home Assistant refused the token")
            if (code !in 200..299) error("Home Assistant answered $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- token at rest
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun token(): String {
        val raw = Base64.decode(prefs.getString("token", null) ?: error("Not connected"), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        return String(c.doFinal(raw, 12, raw.size - 12))
    }

    companion object {
        const val DEFAULT_URL = ""
        const val AGENT = "conversation.hafnir_mac"
        /** The hearth burns while either of them is home. */
        private val HEARTH_KEEPERS = setOf("person.ozorhaar", "person.leah")
        /** Solar output in watts (lights the longship's sail). */
        private const val SOLAR = "sensor.solar_power"
        /** What the house is using, in watts (Sense). */
        private const val HOUSE_USE = "sensor.sense_monitor_total_power"
        /** The water-leak sensors (a wet one floods the longhouse's footing). */
        private val LEAKS = listOf("binary_sensor.bathroomsls_water_leak", "binary_sensor.kitchenls_water_leak", "binary_sensor.upstairstls_water_leak")
        /** Today's solar generation, kWh (resets at midnight). */
        private const val SOLAR_TODAY = "sensor.solar_generation"
        /** The only cameras the launcher may ask Home Assistant for. */
        private val CAMERAS = Lookout.entries.map { it.camera }.toSet()
        private const val KEY_ALIAS = "ygg_home_link"
        private val DOMAINS = setOf("light", "switch", "fan", "input_boolean", "lock", "cover", "scene")
        private val SAFE_SERVICES = setOf("turn_on", "turn_off", "lock", "close_cover")
    }
}
