package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.PlaceNames
import de.dennisguse.opentracks.util.RouteAnalysis
import de.dennisguse.opentracks.util.RouteGroup
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
    var mode by remember { mutableStateOf(0) } // 0 frequency, 1 individual trips
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
    val focusRoutes = focused?.tripIds?.mapNotNull { pointsById[it] } ?: emptyList()

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
            listOf("Frequência", "Viagens").forEachIndexed { i, label ->
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
                focusRoutes = focusRoutes,
                floatingControlsBottomPadding = 12.dp
            )
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
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    itemsIndexed(gs) { _, (g, name) ->
                        RouteGroupRow(g, name, selected = focused == g) { focused = if (focused == g) null else g }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteGroupRow(g: RouteGroup, name: String, selected: Boolean, onClick: () -> Unit) {
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
        Spacer(Modifier.height(4.dp))
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
