package de.dennisguse.opentracks.util

import android.content.Context
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.FavoritePlace
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-defined places besides Casa and Trabalho (gym, school, parents...). Stored as one JSON
 * array next to the home/work keys so existing installs need no migration.
 */
object FavoritePlaces {
    const val PREFS = "CarTracksLocations"
    const val KEY = "favorites_json"

    /** Same radius used for Casa/Trabalho detection. */
    const val RADIUS_M = 600.0

    val EMOJIS = listOf("⭐", "🏋️", "🎓", "🛒", "👪", "🏥", "⛪", "🍽️", "⚽", "🅿️")

    fun load(context: Context): List<FavoritePlace> =
        fromJson(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, places: List<FavoritePlace>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, toJson(places).toString()).apply()
    }

    fun toJson(places: List<FavoritePlace>): JSONArray = JSONArray().apply {
        places.forEach { p ->
            put(JSONObject().put("id", p.id).put("name", p.name).put("emoji", p.emoji).put("lat", p.lat).put("lon", p.lon))
        }
    }

    fun fromJson(json: String?): List<FavoritePlace> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FavoritePlace(o.getString("id"), o.getString("name"), o.optString("emoji", "⭐"), o.getDouble("lat"), o.getDouble("lon"))
            }
        }.getOrDefault(emptyList())
    }

    /** "🏠 Casa", "🏢 Trabalho" or "⭐ Academia" when the point is within [RADIUS_M] of a known place. */
    fun placeAt(lat: Double, lon: Double, home: CustomLocation, work: CustomLocation, favorites: List<FavoritePlace>): String? {
        if (home.isSet && RouteAnalysis.distanceM(lat, lon, home.lat, home.lon) < RADIUS_M) return "🏠 Casa"
        if (work.isSet && RouteAnalysis.distanceM(lat, lon, work.lat, work.lon) < RADIUS_M) return "🏢 Trabalho"
        return favorites.minByOrNull { RouteAnalysis.distanceM(lat, lon, it.lat, it.lon) }
            ?.takeIf { RouteAnalysis.distanceM(lat, lon, it.lat, it.lon) < RADIUS_M }
            ?.let { "${it.emoji} ${it.name}" }
    }

    /** Trip badge from its two ends, keeping the existing Casa/Trabalho wording. */
    fun badge(start: String?, end: String?): String? = when {
        start != null && end != null && start != end -> "$start ➔ $end"
        start != null -> "Partida: $start"
        end != null -> "Destino: $end"
        else -> null
    }
}
