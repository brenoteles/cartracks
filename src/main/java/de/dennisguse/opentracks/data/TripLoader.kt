package de.dennisguse.opentracks.data

import android.content.Context
import de.dennisguse.opentracks.data.models.Track
import de.dennisguse.opentracks.data.models.TrackPoint
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.FavoritePlace
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.util.FavoritePlaces
import de.dennisguse.opentracks.util.FuelConsumptionModel
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads trips, fuel-ups and expenses into the UI models. Used by the activity and by code that
 * runs with the app closed (weekly summary), so it only depends on a Context.
 */
object TripLoader {

    /** Everything the analyses need, loaded from the databases and preferences. */
    data class All(val trips: List<TripItem>, val fills: List<FuelItem>, val expenses: List<Expense>, val model: FuelConsumptionModel)

    fun loadAll(context: Context): All {
        val fills = loadFills(context)
        val primary = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
            .getString("primary_fuel_type", "Gasolina Comum") ?: "Gasolina Comum"
        val model = FuelConsumptionModel.load(context, fills, primary)
        val (home, work) = places(context)
        val trips = loadTrips(context, ContentProviderUtils(context), model, home, work, FavoritePlaces.load(context))
        return All(trips, fills, ExpenseDatabaseHelper.get(context).all(), model)
    }

    fun loadFills(context: Context): List<FuelItem> = FuelDatabaseHelper.getInstance(context).allEntries.map { e ->
        FuelItem(
            id = e.id,
            dateTime = e.dateTime,
            stationName = e.stationName,
            liters = e.liters,
            pricePerLiter = e.pricePerLiter,
            totalCost = e.totalCost,
            odometerKm = e.odometerKm,
            notes = e.notes ?: "",
            isFullTank = e.isFullTank,
            fuelType = e.fuelType,
            latitude = e.latitude,
            longitude = e.longitude
        )
    }

    /** Casa and Trabalho from Ajustes; isSet is false until the user picked them. */
    fun places(context: Context): Pair<CustomLocation, CustomLocation> {
        val sp = context.getSharedPreferences("CarTracksLocations", Context.MODE_PRIVATE)
        val home = CustomLocation(
            lat = sp.getString("home_lat", "-23.6015")?.toDoubleOrNull() ?: -23.6015,
            lon = sp.getString("home_lon", "-46.6900")?.toDoubleOrNull() ?: -46.6900,
            label = sp.getString("home_label", "Casa") ?: "Casa",
            isSet = sp.contains("home_lat")
        )
        val work = CustomLocation(
            lat = sp.getString("work_lat", "-23.5615")?.toDoubleOrNull() ?: -23.5615,
            lon = sp.getString("work_lon", "-46.6559")?.toDoubleOrNull() ?: -46.6559,
            label = sp.getString("work_label", "Trabalho") ?: "Trabalho",
            isSet = sp.contains("work_lat")
        )
        return home to work
    }

    fun loadTrips(
        context: Context,
        providerUtils: ContentProviderUtils,
        model: FuelConsumptionModel,
        home: CustomLocation,
        work: CustomLocation,
        favorites: List<FavoritePlace>
    ): List<TripItem> {
        val list = mutableListOf<TripItem>()
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        try {
            providerUtils.searchTracks(null)?.use {
                while (it.hasNext()) {
                    val item = it.next()
                    val km = item.totalDistance().toKM()
                    val minutes = item.totalTime().toMinutes()
                    val track = providerUtils.getTrack(item.id())

                    var maxSpd = 0.0
                    var avgSpd = 0.0
                    if (track != null && track.statistics() != null) {
                        if (track.statistics().maxSpeed() != null) {
                            maxSpd = track.statistics().maxSpeed().toKMH()
                        }
                        if (track.statistics().getAverageSpeed() != null) {
                            avgSpd = track.statistics().getAverageSpeed().toKMH()
                        }
                    }
                    if (avgSpd <= 0.0) {
                        val hours = item.totalTime().seconds / 3600.0
                        avgSpd = if (hours > 0) (km / hours) else 42.0
                    }
                    if (maxSpd <= 0.0) {
                        maxSpd = avgSpd * 1.35
                    }

                    val startMs = item.startTime().toEpochMilli()
                    val family = model.familyAt(startMs)
                    val tripKmL = model.tripKmL(avgSpd, startMs)
                    val fuel = if (tripKmL > 0) km / tripKmL else 0.0
                    val cost = fuel * model.pricePerLiter(family, startMs)

                    val ends = endpoints(providerUtils, item.id().id())
                    val badge = ends?.let { (s, e) ->
                        FavoritePlaces.badge(
                            FavoritePlaces.placeAt(s.first, s.second, home, work, favorites),
                            FavoritePlaces.placeAt(e.first, e.second, home, work, favorites)
                        )
                    }

                    list.add(
                        TripItem(
                            id = item.id().id(),
                            name = friendlyTripName(item.name(), item.startTime().atOffset(item.zoneOffset())),
                            description = item.description(),
                            distanceKm = km,
                            durationMin = minutes,
                            startTimeMs = startMs,
                            dateFormatted = item.startTime().atOffset(item.zoneOffset()).format(formatter),
                            avgSpeedKmh = avgSpd,
                            maxSpeedKmh = maxSpd,
                            fuelLiters = fuel,
                            fuelCost = cost,
                            commuteBadge = badge,
                            kmL = tripKmL,
                            fuelLabel = family.label,
                            trafficLabel = FuelConsumptionModel.trafficLabel(avgSpd),
                            startLat = ends?.first?.first,
                            startLon = ends?.first?.second,
                            endLat = ends?.second?.first,
                            endLon = ends?.second?.second
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Liters in the tank: start from the last full fill (or from the setup answer "km since
     * the tank was full"), add partial fills after it and subtract the fuel of later trips.
     */
    fun tankLiters(context: Context, trips: List<TripItem>, fills: List<FuelItem>, model: FuelConsumptionModel): Double {
        val sp = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
        val cap = sp.getFloat("tank_capacity_liters", 50f).toDouble()
        val setupRefMs = sp.getLong("tank_ref_time_ms", 0L)
        val kmSinceLastFull = sp.getFloat("km_since_last_full", 0f).toDouble()
        val lastFull = fills.filter { it.isFullTank }.maxByOrNull { it.dateTime }
        val (refMs, startLiters) = if (lastFull != null && lastFull.dateTime >= setupRefMs) {
            lastFull.dateTime to cap
        } else {
            setupRefMs to cap - kmSinceLastFull / model.baseKmL(model.familyAt(setupRefMs))
        }
        val added = fills.filter { !it.isFullTank && it.dateTime > refMs }.sumOf { it.liters }
        val burned = trips.filter { it.startTimeMs > refMs }.sumOf { it.fuelLiters }
        return (startLiters + added - burned).coerceIn(0.0, cap)
    }

    /** Default track names are ISO timestamps ("2026-11-04T18:06-03"); show a readable one. */
    private fun friendlyTripName(name: String?, start: java.time.OffsetDateTime): String {
        if (name != null && name.isNotBlank() && !Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}.*""").matches(name)) return name
        return "Viagem · " + start.format(DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", Locale("pt", "BR")))
    }

    /** First and last fix of a trip as ((lat, lon), (lat, lon)). */
    private fun endpoints(providerUtils: ContentProviderUtils, trackId: Long): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
        try {
            providerUtils.getTrackPointIterator(Track.Id(trackId), null)?.use {
                var firstPt: TrackPoint? = null
                var lastPt: TrackPoint? = null
                while (it.hasNext()) {
                    val tp = it.next()
                    if (tp != null && tp.position().hasLocation()) {
                        if (firstPt == null) firstPt = tp
                        lastPt = tp
                    }
                }
                if (firstPt != null && lastPt != null) {
                    return (firstPt.position().latitude() to firstPt.position().longitude()) to
                        (lastPt.position().latitude() to lastPt.position().longitude())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
