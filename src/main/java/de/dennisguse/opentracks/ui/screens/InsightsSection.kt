package de.dennisguse.opentracks.ui.screens

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.dennisguse.opentracks.data.Expense
import de.dennisguse.opentracks.ui.components.InsightsCard
import de.dennisguse.opentracks.ui.components.RecordsCard
import de.dennisguse.opentracks.ui.components.RegularityCard
import de.dennisguse.opentracks.ui.components.RouteVariantsCard
import de.dennisguse.opentracks.ui.components.YearRecapBanner
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.CyberSectionHeader
import de.dennisguse.opentracks.util.DrivingInsights
import de.dennisguse.opentracks.util.InsightStore
import de.dennisguse.opentracks.util.PersonalRecord
import de.dennisguse.opentracks.util.RecordKind
import de.dennisguse.opentracks.util.RouteComparison
import de.dennisguse.opentracks.util.RouteVariants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Início block: week insights, records, departure regularity, route variants and the year recap entry. */
@Composable
fun InsightsSection(
    trips: List<TripItem>,
    fills: List<FuelItem>,
    expenses: List<Expense>,
    loadPoints: (Long) -> List<TripPoint>,
    onOpenTrip: (Long) -> Unit,
    onShowTripsOnMap: (List<Long>, String) -> Unit,
    onOpenRecap: (year: Int) -> Unit
) {
    if (trips.isEmpty()) return
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var feedbackVersion by remember { mutableIntStateOf(0) }

    val now = remember(trips, fills, expenses) { System.currentTimeMillis() }
    val insights = remember(trips, fills, expenses, feedbackVersion) {
        val hidden = InsightStore.hidden(context)
        val liked = InsightStore.liked(context)
        runCatching { DrivingInsights.insights(trips, fills, expenses, now, zone) }.getOrDefault(emptyList())
            .filter { it.id !in hidden }
            .sortedByDescending { it.id in liked }
            .take(4)
    }
    val records = remember(trips, fills, expenses) {
        runCatching { DrivingInsights.records(trips, fills, expenses, now, zone) }.getOrDefault(emptyList())
    }
    var sealed by remember { mutableStateOf(emptySet<RecordKind>()) }
    LaunchedEffect(records) { sealed = InsightStore.updateRecords(context, records, now) }
    val window = remember(trips) { runCatching { DrivingInsights.departureWindow(trips, zone, now) }.getOrNull() }
    val variants by produceState<List<RouteComparison>>(emptyList(), trips) {
        value = withContext(Dispatchers.IO) { routeComparisons(context, trips, loadPoints) }
    }
    // the recap is an end-of-year thing: December shows this year, January the year that just ended
    val year = remember { recapYear(java.time.LocalDate.now(zone)) }
    val yearKm = remember(trips, year) {
        if (year == null) 0.0 else trips.filter { Instant.ofEpochMilli(it.startTimeMs).atZone(zone).year == year }.sumOf { it.distanceKm }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (insights.isNotEmpty()) {
            CyberSectionHeader(title = "Insights", eyebrow = "Esta semana")
            InsightsCard(insights) { id, useful ->
                InsightStore.feedback(context, id, useful)
                if (!useful) feedbackVersion++
            }
        }
        if (records.isNotEmpty()) {
            CyberSectionHeader(title = "Recordes", eyebrow = "Pessoais")
            RecordsCard(records, sealed) { r: PersonalRecord -> r.tripId?.let(onOpenTrip) }
        }
        if (window != null) {
            CyberSectionHeader(title = "Regularidade", eyebrow = "Horário de saída")
            RegularityCard(window)
        }
        if (variants.isNotEmpty()) {
            CyberSectionHeader(title = "Seus caminhos", eyebrow = "Rota habitual")
            variants.forEach { RouteVariantsCard(it, onShowTripsOnMap) }
        }
        if (year != null && yearKm > 0) YearRecapBanner(year, yearKm) { onOpenRecap(year) }
    }
}

/** Year whose recap is offered on [today]: December → this year, January → last year, otherwise none. */
fun recapYear(today: java.time.LocalDate): Int? = when (today.monthValue) {
    12 -> today.year
    1 -> today.year - 1
    else -> null
}

/** Casa→Trabalho and Trabalho→Casa variants, named after the road of each one's distinctive stretch. */
private fun routeComparisons(context: Context, trips: List<TripItem>, loadPoints: (Long) -> List<TripPoint>): List<RouteComparison> {
    val dirs = listOf(
        Triple("🏠 Casa ➔ 🏢 Trabalho", "Casa", "Trabalho"),
        Triple("🏢 Trabalho ➔ 🏠 Casa", "Trabalho", "Casa")
    )
    return dirs.mapNotNull { (badge, from, to) ->
        val list = trips.filter { it.commuteBadge == badge }.sortedByDescending { it.startTimeMs }.take(60)
        if (list.size < 4) return@mapNotNull null
        val cmp = RouteVariants.compare(from, to, list, list.associate { it.id to loadPoints(it.id) }) ?: return@mapNotNull null
        val used = mutableSetOf<String>()
        cmp.copy(variants = cmp.variants.map { v ->
            val road = roadAt(context, v.distinctLat, v.distinctLon)
            if (road != null && used.add(road)) v.copy(name = "Via $road") else v
        })
    }
}

private fun roadAt(context: Context, lat: Double, lon: Double): String? = try {
    if (!Geocoder.isPresent()) null
    else {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale("pt", "BR")).getFromLocation(lat, lon, 1)?.firstOrNull()?.thoroughfare
            ?.replace("Rodovia ", "")
            ?.replace("Avenida ", "Av. ")
            ?.takeIf { it.isNotBlank() }
    }
} catch (e: Exception) {
    null
}
