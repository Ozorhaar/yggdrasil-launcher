package com.ozorhaar.yggdrasil.launcher.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * The steading's settings: which of the living touches are on, how loud the horns blow, and which
 * Home Assistant entities stand for the household, the leak sensors, solar, the smoker and the
 * printer. Kept on the phone and carried in the Hoard backup.
 */
object SteadingConfig {
    enum class Touch(val key: String, val label: String) {
        RAVEN("raven", "Raven on the roof (someone at a door)"),
        CHEST("chest", "Chest at the door (a package)"),
        LONGSHIP("longship", "Longship leaves when no one's home"),
        OARS("oars", "Oars row while sending power to the grid"),
        SAIL("sail", "Sail glows with the solar panels"),
        WIND("wind", "Sail fills with the wind"),
        HEARTH("hearth", "Hearth smoke while someone's home"),
        FIRE("fire", "Surtur's fire while the smoker's lit"),
        FORGE("forge", "Forge sparks while printing"),
        LEAK("leak", "Water and ember for a leak"),
        HOMECOMING("homecoming", "Homecoming horn"),
        SAGA("saga", "Morning saga"),
        HEIMDALL("heimdall", "Heimdall's watch on the homelab"),
        REAL_AURORA("real_aurora", "Northern lights follow NOAA (off: some every clear night)"),
        YULE("yule", "Yule greenery and lanterns in December"),
        WORLD_TREE("world_tree", "The World Tree on clear nights"),
    }

    enum class Entities(val key: String, val label: String, val default: String) {
        PEOPLE("people", "Household (person entities)", "person.ozorhaar, person.leah"),
        DARK_LIGHTS("dark_lights", "Lights that don't light the hall's windows (outdoor, dusk-to-dawn, gadgets)",
            "light.smart_curtain_lights, light.front_porch_bulb, light.back_patio, light.drivedoor_light, light.permanent_lights, " +
                "light.smart_ice_maker, light.water_heater, light.yggdrasil_chamber_light"),
        LEAKS("leaks", "Leak sensors", "binary_sensor.bathroomsls_water_leak, binary_sensor.kitchenls_water_leak, binary_sensor.upstairstls_water_leak"),
        SOLAR("solar", "Solar power now (W)", "sensor.solar_power"),
        SOLAR_TODAY("solar_today", "Solar made today (kWh)", "sensor.solar_generation"),
        HOUSE_USE("house_use", "House using now (W)", "sensor.sense_monitor_total_power"),
        SMOKER("smoker", "Smoker (climate)", "climate.surtur_climate"),
        PROBE("probe", "Smoker probe (climate)", "climate.back_patio_smoker_probe"),
        PRINTER("printer", "Printer status", "sensor.yggdrasil_print_status"),
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("ygg_steading", Context.MODE_PRIVATE)

    fun on(ctx: Context, t: Touch): Boolean = prefs(ctx).getBoolean(t.key, true)
    fun set(ctx: Context, t: Touch, on: Boolean) = prefs(ctx).edit().putBoolean(t.key, on).apply()

    /** Horn volume, 0..1. */
    fun hornVolume(ctx: Context): Float = prefs(ctx).getFloat("horn_volume", 0.9f)
    fun setHornVolume(ctx: Context, v: Float) = prefs(ctx).edit().putFloat("horn_volume", v.coerceIn(0f, 1f)).apply()

    fun text(ctx: Context, e: Entities): String = prefs(ctx).getString(e.key, null)?.takeIf { it.isNotBlank() } ?: e.default
    fun setText(ctx: Context, e: Entities, v: String) = prefs(ctx).edit().putString(e.key, v.trim()).apply()

    /** The entity ids in a setting (comma or space separated, only well-formed ids). */
    fun ids(ctx: Context, e: Entities): List<String> =
        text(ctx, e).split(',', ' ', '\n').map { it.trim() }.filter { ID.matches(it) }

    fun id(ctx: Context, e: Entities): String = ids(ctx, e).firstOrNull() ?: e.default

    private val ID = Regex("^[a-z_]+\\.[a-z0-9_]+$")

    fun export(ctx: Context): JSONObject {
        val p = prefs(ctx)
        val o = JSONObject()
        Touch.entries.forEach { o.put(it.key, p.getBoolean(it.key, true)) }
        Entities.entries.forEach { o.put(it.key, text(ctx, it)) }
        o.put("horn_volume", hornVolume(ctx).toDouble())
        return o
    }

    fun import(ctx: Context, o: JSONObject) {
        val ed = prefs(ctx).edit()
        Touch.entries.forEach { if (o.has(it.key)) ed.putBoolean(it.key, o.optBoolean(it.key, true)) }
        Entities.entries.forEach { if (o.has(it.key)) ed.putString(it.key, o.optString(it.key)) }
        if (o.has("horn_volume")) ed.putFloat("horn_volume", o.optDouble("horn_volume", 0.9).toFloat())
        ed.apply()
    }
}
