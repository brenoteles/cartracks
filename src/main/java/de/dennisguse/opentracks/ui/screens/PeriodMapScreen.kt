package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.ui.components.MapHotspot
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.PlaceNames
import de.dennisguse.opentracks.util.RouteAnalysis
import de.dennisguse.opentracks.util.RouteGroup
import de.dennisguse.opentracks.util.TrafficAnalysis
import de.dennisguse.opentracks.util.TrafficTimeBand
import de.dennisguse.opentracks.util.TripTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * All trips of a period. "Frequência" draws shared stretches as one stronger line instead of
 * stacking identical commutes; the list below groups trips by origin and destination with
 * timing insights, and tapping a group highlights its trips.
 */
@Composable
fun PeriodMapScreen(
    trips: List<TripItem>,
    routes: List<List<TripPoint>>,
    periodName: String,
    home: CustomLocation,
    work: CustomLocation,
    onBackClicked: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) } // 0 frequency, 1 individual trips, 2 traffic
    var selectedTimeBand by remember { mutableStateOf(TrafficTimeBand.MANHA) }
    var focused by remember { mutableStateOf<RouteGroup?>(null) }
    val pointsById = remember(trips, routes) { trips.map { it.id }.zip(routes).toMap() }

    val groups by produceState<List<Pair<RouteGroup, String>>?>(null, trips, routes) {
        value = withContext(Dispatchers.Default) {
            val traces = trips.zip(routes).map { (t, pts) -> TripTrace(t.id, t.startTimeMs, t.durationMin, t.distanceKm, pts) }
            RouteAnalysis.groupRoutes(traces)
        }.let { gs ->
            withContext(Dispatchers.IO) {
                gs.map { g ->
                    g to "${PlaceNames.label(context, g.startLat, g.startLon, home, work)}  →  ${PlaceNames.label(context, g.endLat, g.endLon, home, work)}"
                }
            }
        }
    }

    val trafficSegments by produceState<List<Pair<List<TripPoint>, Int>>>(emptyList(), routes, selectedTimeBand, mode) {
        if (mode != 2) {
            value = emptyList()
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            TrafficAnalysis.coloredSegments(routes, selectedTimeBand.hours)
        }
    }

    val focusRoutes = focused?.tripIds?.mapNotNull { pointsById[it] } ?: emptyList()
    val hotspots = remember(groups) { groups?.let { destinationHotspots(it) } ?: emptyList() }

    Column(
        Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBackClicked) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = CyberAcid) }
            Column(Modifier.weight(1f)) {
                Text("MAPA DE ROTAS", style = displayStyle(26), color = CyberText)
                Text("$periodName · ${routes.count { it.size >= 2 }} viagens", style = monoStyle(11, FontWeight.Normal), color = TextSecondary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("Frequência", "Viagens", "Trânsito").forEachIndexed { i, label ->
                val sel = i == mode
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (sel) CyberAcid else CyberPanel)
                        .border(1.dp, if (sel) CyberAcid else CyberLine)
                        .clickable { mode = i }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label.uppercase(), style = EyebrowStyle, color = if (sel) CyberOnAcid else CyberText) }
            }
        }
        if (mode == 2) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrafficTimeBand.values().forEach { band ->
                    val sel = band == selectedTimeBand
                    Box(
                        Modifier
                            .background(if (sel) CyberAcid else CyberPanel)
                            .border(1.dp, if (sel) CyberAcid else CyberLine)
                            .clickable { selectedTimeBand = band }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            band.label.uppercase(),
                            style = EyebrowStyle.copy(fontSize = 10.sp),
                            color = if (sel) CyberOnAcid else CyberText
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, CyberLine)
        ) {
            CarMapView(
                modifier = Modifier.fillMaxSize(),
                heatRoutes = if (mode == 0) routes else emptyList(),
                multiRoutes = if (mode == 1) routes else emptyList(),
                coloredSegments = if (mode == 2) trafficSegments else emptyList(),
                focusRoutes = focusRoutes,
                hotspots = if (mode == 0) hotspots else emptyList(),
                floatingControlsBottomPadding = 12.dp
            )

            if (mode == 2) {
                // bottom-left: the layer switcher and its legends take the top of the map
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .background(CyberInk.copy(alpha = 0.92f), RectangleShape)
                        .border(1.dp, CyberLine, RectangleShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("TRÂNSITO HISTÓRICO", style = EyebrowStyle.copy(fontSize = 9.sp), color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            CyberCyan to "LIVRE",
                            CyberAcid to "MODERADO",
                            CyberOrange to "LENTO",
                            CyberRed to "PARADO"
                        ).forEach { (color, label) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(Modifier.size(width = 10.dp, height = 10.dp).background(color))
                                Text(label, style = monoStyle(9, FontWeight.Medium), color = TextPrimary)
                            }
                        }
                    }
                    if (trafficSegments.isEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Poucas viagens nesse horário",
                            style = monoStyle(10, FontWeight.Normal),
                            color = CyberTextSecondary
                        )
                    }
                }
            }
        }

        // Insights list below the map (nothing overlays the map controls)
        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ROTAS FREQUENTES", style = EyebrowStyle, color = CyberAcid, modifier = Modifier.weight(1f))
                if (focused != null) {
                    Text("LIMPAR DESTAQUE", style = EyebrowStyle, color = CyberText, modifier = Modifier.clickable { focused = null })
                }
            }
            val gs = groups
            if (gs == null) {
                LinearProgressIndicator(color = CyberAcid, trackColor = CyberPanelRaised, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            } else {
                val total = gs.sumOf { it.first.count }.coerceAtLeast(1)
                val repeated = gs.filter { it.first.count > 1 }.sumOf { it.first.count }
                Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryTile("${gs.size}", "rotas distintas", CyberText, Modifier.weight(1f))
                    SummaryTile("${repeated * 100 / total}%", "viagens repetidas", CyberAcid, Modifier.weight(1f))
                    SummaryTile("${hotspots.firstOrNull()?.count ?: 0}×", "destino nº 1", CyberPink, Modifier.weight(1f))
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    itemsIndexed(gs) { _, (g, name) ->
                        RouteGroupRow(g, name, share = g.count.toFloat() / total, selected = focused == g) { focused = if (focused == g) null else g }
                    }
                }
            }
        }
    }
}

/** Arrivals per destination: route groups ending within the clustering radius are merged. */
private fun destinationHotspots(groups: List<Pair<RouteGroup, String>>): List<MapHotspot> {
    val spots = mutableListOf<MapHotspot>()
    groups.forEach { (g, name) ->
        val i = spots.indexOfFirst { RouteAnalysis.distanceM(it.lat, it.lon, g.endLat, g.endLon) < RouteAnalysis.ENDPOINT_RADIUS_M }
        if (i >= 0) spots[i] = spots[i].copy(count = spots[i].count + g.count)
        else spots += MapHotspot(g.endLat, g.endLon, g.count, name.substringAfterLast("→").trim())
    }
    return spots.filter { it.count >= 2 }.sortedByDescending { it.count }.take(8)
}

@Composable
private fun SummaryTile(value: String, label: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.cyberPanel(border = CyberLine, shadow = false).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value, style = displayStyle(20), color = color, maxLines = 1)
        Text(label.uppercase(), style = EyebrowStyle.copy(fontSize = 9.sp, lineHeight = 11.sp), color = CyberTextSecondary, minLines = 2, maxLines = 2)
    }
}

@Composable
private fun RouteGroupRow(g: RouteGroup, name: String, share: Float, selected: Boolean, onClick: () -> Unit) {
    val br = Locale("pt", "BR")
    Column(
        Modifier
            .fillMaxWidth()
            .cyberPanel(border = if (selected) CyberPink else CyberLine, shadow = false)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = CyberText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2)
            CyberChip("${g.count}×", if (g.count > 1) CyberAcid else CyberTextTertiary, filled = g.count > 1)
        }
        Spacer(Modifier.height(6.dp))
        // share of the period's trips on this route
        Box(Modifier.fillMaxWidth().height(4.dp).background(CyberPanelRaised)) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(if (selected) CyberPink else CyberAcid))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            String.format(br, "%.1f km · mediana %.0f min · 90%% até %.0f min · %.0f km/h", g.avgDistanceKm, g.medianDurationMin, g.p90DurationMin, g.avgSpeedKmh),
            style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary
        )
        if (g.bestHour != null && g.worstHour != null && g.bestHour != g.worstHour) {
            Text(
                String.format(br, "Saindo às %dh: %.0f min · às %dh: %.0f min", g.bestHour, g.bestHourAvgMin, g.worstHour, g.worstHourAvgMin),
                style = monoStyle(11, FontWeight.Medium), color = CyberCyan
            )
        } else if (g.count > 1 && g.maxDurationMin > g.minDurationMin) {
            Text(
                "Variação: ${g.minDurationMin}–${g.maxDurationMin} min",
                style = monoStyle(11, FontWeight.Medium), color = CyberCyan
            )
        }
    }
}
