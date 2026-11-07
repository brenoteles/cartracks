package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint

/**
 * Data shared by the weekly summary, records, insights, regularity, route variants and the
 * year recap. Computed by [DrivingInsights] / [RouteVariants] (pure Kotlin, unit-tested) and
 * rendered by the Início cards, the widget, the weekly notification and the recap screen.
 */

/** Totals of a time window [startMs, endMs). */
data class PeriodStats(
    val startMs: Long,
    val endMs: Long,
    val trips: Int,
    val km: Double,
    val drivingMin: Long,
    /** Estimated fuel burned by the trips of the window (sum of TripItem.fuelLiters). */
    val fuelLiters: Double,
    /** Money paid at the pump in the window (fills). */
    val fuelSpent: Double,
    /** Other expenses in the window. */
    val otherSpent: Double,
    /** km / fuelLiters, 0 when no fuel. */
    val avgKmL: Double,
    val longestTrip: TripItem?
) {
    val totalSpent get() = fuelSpent + otherSpent
}

/** This week against the one before (weeks start on Monday, local time). */
data class WeekSummary(val current: PeriodStats, val previous: PeriodStats)

enum class RecordKind(val title: String, val emoji: String) {
    BEST_TANK_KML("Melhor km/L num tanque", "⛽"),
    FASTEST_HOME_WORK("Casa → Trabalho mais rápido", "⚡"),
    LONGEST_TRIP("Viagem mais longa", "🛣️"),
    CHEAPEST_MONTH("Mês mais barato", "💰")
}

data class PersonalRecord(
    val kind: RecordKind,
    /** Raw value used to compare (km/L, minutes, km, R$). */
    val value: Double,
    /** Ready-to-show value: "14,2 km/L", "27 min", "312 km", "R$ 410,00". */
    val display: String,
    /** Context: date, place or month name. */
    val detail: String,
    val atMs: Long,
    val tripId: Long? = null
)

/** A short sentence for the Início. [id] is stable per kind of insight, so 👎 can hide that kind. */
data class Insight(
    val id: String,
    val emoji: String,
    val text: String,
    /** +1 good news, -1 bad news, 0 neutral. */
    val tone: Int = 0
)

/** When the user usually leaves [from] towards [to] on weekdays. Minutes are minutes of the day. */
data class DepartureWindow(
    val from: String,
    val to: String,
    val startMinute: Int,
    val endMinute: Int,
    /** Share of the departures inside the window (e.g. 0.8). */
    val share: Double,
    val days: Int,
    val medianMinute: Int,
    /** Departures per 10-minute bin, from [histogramStartMinute]. */
    val histogram: List<Int>,
    val histogramStartMinute: Int
)

/** One way of driving between the same two places. */
data class RouteVariant(
    /** Filled by the app after reverse-geocoding [distinctLat]/[distinctLon] (e.g. "Via Dutra"); "Caminho A" until then. */
    val name: String,
    val tripIds: List<Long>,
    val medianMin: Double,
    val avgKm: Double,
    val avgKmL: Double,
    /** A point of this variant far from every other variant: its road names the variant. */
    val distinctLat: Double,
    val distinctLon: Double,
    /** Points of the most typical trip of the variant, for a mini map. */
    val representative: List<TripPoint>
)

data class RouteComparison(val from: String, val to: String, val variants: List<RouteVariant>)

/** Everything the animated year recap shows. */
data class YearRecap(
    val year: Int,
    val stats: PeriodStats,
    val daysDriven: Int,
    /** km per month, index 0 = January. */
    val kmByMonth: List<Double>,
    val busiestMonth: Int,
    val topDestinations: List<Pair<String, Int>>,
    val records: List<PersonalRecord>,
    /** Filled by the app from Geocoder; empty from the pure computation. */
    val cities: List<String> = emptyList()
)
