package de.dennisguse.opentracks.util

import android.content.Context
import android.location.Geocoder
import de.dennisguse.opentracks.ui.model.CustomLocation
import java.util.Locale

/** Human name for a coordinate: Casa/Trabalho when close, else street + neighbourhood, else coordinates. */
object PlaceNames {
    private val cache = HashMap<String, String>()

    fun label(context: Context, lat: Double, lon: Double, home: CustomLocation, work: CustomLocation): String {
        if (home.isSet && RouteAnalysis.distanceM(lat, lon, home.lat, home.lon) < 600) return "🏠 ${home.label}"
        if (work.isSet && RouteAnalysis.distanceM(lat, lon, work.lat, work.lon) < 600) return "🏢 ${work.label}"
        val key = String.format(Locale.US, "%.3f,%.3f", lat, lon)
        cache[key]?.let { return it }
        val name = try {
            if (!Geocoder.isPresent()) null
            else {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { a ->
                    listOfNotNull(a.thoroughfare, a.subLocality ?: a.locality).joinToString(", ").ifBlank { null }
                }
            }
        } catch (e: Exception) {
            null
        } ?: String.format(Locale.US, "%.4f, %.4f", lat, lon)
        cache[key] = name
        return name
    }
}
