package de.dennisguse.opentracks.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.data.Expense
import de.dennisguse.opentracks.io.ReportImageExporter
import de.dennisguse.opentracks.util.PlaceNames
import de.dennisguse.opentracks.util.RouteAnalysis
import de.dennisguse.opentracks.util.RouteGroup
import de.dennisguse.opentracks.util.TripTrace
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlinx.coroutines.launch
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelConsumptionModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private enum class ReportPeriod(val label: String) {
    SEVEN("7 dias"), MONTH("Este mês"), LAST_MONTH("Mês passado"), QUARTER("90 dias"), ALL("Tudo"), CUSTOM("Escolher datas")
}

private data class Destination(val lat: Double, val lon: Double, var count: Int, var label: String)

/**
 * Infographic report for a date range: map of every trip, KPIs, estimated vs pump consumption,
 * fuels used, most frequent destinations, traffic profile, weekday rhythm and stations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    trips: List<TripItem>,
    fills: List<FuelItem>,
    home: CustomLocation,
    work: CustomLocation,
    loadPoints: (Long) -> List<TripPoint>,
    onBack: () -> Unit,
    expenses: List<Expense> = emptyList(),
    /** Opened from the weekly summary: start on the last 7 days instead of this month. */
    startOnLastWeek: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val bodyLayer = rememberGraphicsLayer()
    var mapSnapshot by remember { mutableStateOf<(suspend () -> android.graphics.Bitmap?)?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var period by remember { mutableStateOf(if (startOnLastWeek) ReportPeriod.SEVEN else ReportPeriod.MONTH) }
    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    val today = LocalDate.now(zone)
    val (fromMs, toMs) = when (period) {
        ReportPeriod.SEVEN -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli() to Long.MAX_VALUE
        ReportPeriod.MONTH -> today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() to Long.MAX_VALUE
        ReportPeriod.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() to
            today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        ReportPeriod.QUARTER -> today.minusDays(89).atStartOfDay(zone).toInstant().toEpochMilli() to Long.MAX_VALUE
        ReportPeriod.ALL -> 0L to Long.MAX_VALUE
        ReportPeriod.CUSTOM -> (customStart ?: 0L) to ((customEnd ?: customStart)?.plus(86_400_000L - 1) ?: Long.MAX_VALUE)
    }
    val dayFmt = DateTimeFormatter.ofPattern("dd/MM/yy")
    val rangeLabel = when (period) {
        ReportPeriod.ALL -> "Todo o histórico"
        else -> {
            val from = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
            val to = if (toMs == Long.MAX_VALUE) today else Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
            "${from.format(dayFmt)} — ${to.format(dayFmt)}"
        }
    }

    val pTrips = remember(trips, fromMs, toMs) { trips.filter { it.startTimeMs in fromMs..toMs }.sortedBy { it.startTimeMs } }
    val pFills = remember(fills, fromMs, toMs) { fills.filter { it.dateTime in fromMs..toMs } }
    val pExpenses = remember(expenses, fromMs, toMs) { expenses.filter { it.dateTime in fromMs..toMs } }

    val routes by produceState<List<List<TripPoint>>?>(null, pTrips) {
        value = null
        value = withContext(Dispatchers.IO) { pTrips.map { loadPoints(it.id) } }
    }

    val topRoutes by produceState<List<Pair<RouteGroup, String>>?>(null, routes) {
        val r = routes ?: return@produceState
        value = withContext(Dispatchers.IO) {
            RouteAnalysis.groupRoutes(pTrips.zip(r).map { (t, pts) -> TripTrace(t.id, t.startTimeMs, t.durationMin, t.distanceKm, pts) })
                .filter { it.count > 1 }.take(3)
                .map { g -> g to (PlaceNames.label(context, g.startLat, g.startLon, home, work) + " → " + PlaceNames.label(context, g.endLat, g.endLon, home, work)) }
        }
    }

    // Most frequent destinations: cluster trip end points within 400 m
    val destinations by produceState<List<Destination>?>(null, routes) {
        val r = routes ?: return@produceState
        value = withContext(Dispatchers.IO) {
            val clusters = mutableListOf<Destination>()
            r.mapNotNull { it.lastOrNull() }.forEach { end ->
                val hit = clusters.firstOrNull { distM(it.lat, it.lon, end.lat, end.lon) < 400 }
                if (hit != null) hit.count++ else clusters.add(Destination(end.lat, end.lon, 1, ""))
            }
            val top = clusters.sortedByDescending { it.count }.take(5)
            val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale("pt", "BR")) else null
            top.forEach { d ->
                d.label = when {
                    home.isSet && distM(d.lat, d.lon, home.lat, home.lon) < 600 -> "🏠 ${home.label}"
                    work.isSet && distM(d.lat, d.lon, work.lat, work.lon) < 600 -> "🏢 ${work.label}"
                    else -> try {
                        @Suppress("DEPRECATION")
                        geocoder?.getFromLocation(d.lat, d.lon, 1)?.firstOrNull()?.let { a ->
                            listOfNotNull(a.thoroughfare, a.subLocality ?: a.locality).joinToString(", ").ifBlank { null }
                        }
                    } catch (e: Exception) {
                        null
                    } ?: String.format(Locale.US, "%.4f, %.4f", d.lat, d.lon)
                }
            }
            top
        }
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    shape = RectangleShape,
                    onClick = {
                        // Picker returns UTC midnight; convert to the local day.
                        fun local(utc: Long) = Instant.ofEpochMilli(utc).atZone(ZoneId.of("UTC")).toLocalDate()
                            .atStartOfDay(zone).toInstant().toEpochMilli()
                        state.selectedStartDateMillis?.let { customStart = local(it) }
                        customEnd = state.selectedEndDateMillis?.let { local(it) }
                        if (customStart != null) period = ReportPeriod.CUSTOM
                        showPicker = false
                    }
                ) { Text("APLICAR", style = EyebrowStyle, color = CyberAcid) }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = { showPicker = false }) {
                    Text("CANCELAR", style = EyebrowStyle, color = TextSecondary)
                }
            },
            shape = RectangleShape,
            colors = DatePickerDefaults.colors(containerColor = CyberPanelRaised)
        ) {
            DateRangePicker(
                state = state,
                modifier = Modifier.height(480.dp),
                colors = DatePickerDefaults.colors(
                    containerColor = CyberPanelRaised,
                    selectedDayContainerColor = CyberAcid,
                    selectedDayContentColor = CyberOnAcid,
                    dayInSelectionRangeContainerColor = CyberAcid.copy(alpha = 0.25f),
                    dayInSelectionRangeContentColor = CyberText,
                    todayDateBorderColor = CyberAcid,
                    todayContentColor = CyberAcid
                )
            )
        }
    }

    // --- Aggregates ---
    val km = pTrips.sumOf { it.distanceKm }
    val hours = pTrips.sumOf { it.durationMin } / 60.0
    val avgSpeed = if (hours > 0) km / hours else 0.0
    val maxSpeed = pTrips.maxOfOrNull { it.maxSpeedKmh } ?: 0.0
    val estLiters = pTrips.sumOf { it.fuelLiters }
    val estCost = pTrips.sumOf { it.fuelCost }
    val estKmL = if (estLiters > 0) km / estLiters else 0.0
    val pumpLiters = pFills.sumOf { it.liters }
    val pumpCost = pFills.sumOf { it.totalCost }
    val pumpMeasure = remember(pFills) { FuelConsumptionModel.measureAtPump(pFills) }
    val pumpKm = pumpMeasure.values.sumOf { it.km }
    val pumpKmL = if (pumpKm > 0) pumpKm / pumpMeasure.values.sumOf { it.liters } else null
    val otherCost = pExpenses.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = CyberAcid) }
            Column(Modifier.weight(1f)) {
                Text("RELATÓRIO", style = displayStyle(28), color = CyberText)
                Text(rangeLabel, style = monoStyle(11, FontWeight.Normal), color = TextSecondary)
            }
            if (exporting) {
                CircularProgressIndicator(color = CyberAcid, strokeWidth = 2.dp, modifier = Modifier.size(24.dp).padding(end = 4.dp))
            } else {
                IconButton(onClick = {
                    exporting = true
                    scope.launch {
                        try {
                            val map = mapSnapshot?.invoke()
                            val body = bodyLayer.toImageBitmap().asAndroidBitmap()
                            val img = ReportImageExporter.compose(context, "CARTRACKS · RELATÓRIO", rangeLabel, map, body)
                            ReportImageExporter.share(context, img, "CarTracks_relatorio_" + System.currentTimeMillis() + ".png")
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Falha ao gerar imagem: " + e.message, android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            exporting = false
                        }
                    }
                }) { Icon(Icons.Default.Share, contentDescription = "Exportar como imagem", tint = CyberAcid) }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyberAcid))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ReportPeriod.values().toList()) { p ->
                    val sel = p == period
                    Box(
                        modifier = Modifier
                            .background(if (sel) CyberAcid else CyberPanel, RectangleShape)
                            .border(1.dp, if (sel) CyberAcid else CyberLine, RectangleShape)
                            .clickable { if (p == ReportPeriod.CUSTOM) showPicker = true else period = p }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(p.label.uppercase(), style = EyebrowStyle, color = if (sel) CyberOnAcid else CyberText)
                    }
                }
            }

            // Map of every trip in the period
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(CyberPanel)
            ) {
                val r = routes
                if (r == null) {
                    CircularProgressIndicator(color = CyberAcid, modifier = Modifier.align(Alignment.Center))
                } else {
                    CarMapView(
                        heatRoutes = r,
                        modifier = Modifier.fillMaxSize(),
                        showZoomControls = true,
                        onSnapshotProvider = { mapSnapshot = it }
                    )
                }
                CyberChip("${pTrips.size} viagens no mapa", CyberAcid, Modifier.align(Alignment.BottomStart).padding(10.dp), filled = true)
            }

            Column(
                Modifier
                    .background(CyberInk)
                    .drawWithContent {
                        bodyLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(bodyLayer)
                    }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pTrips.isEmpty() && pFills.isEmpty()) {
                    Text("Nenhuma viagem ou abastecimento neste período.", color = TextSecondary)
                    return@Column
                }

                // Hero number
                Column(
                    Modifier
                        .fillMaxWidth()
                        .hardShadow(color = CyberMagenta, offset = 6.dp)
                        .background(CyberAcid)
                        .border(2.dp, CyberOnAcid)
                        .padding(16.dp)
                ) {
                    Text("DISTÂNCIA RODADA", style = EyebrowStyle, color = CyberOnAcidSecondary)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(String.format(Locale.getDefault(), "%.1f", km), style = monoStyle(48, FontWeight.Bold), color = CyberOnAcid)
                        Spacer(Modifier.width(6.dp))
                        Text("KM", style = displayStyle(26), color = CyberOnAcid, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Text(
                        String.format(Locale.getDefault(), "%d viagens · %.1f h ao volante · R$ %.2f estimados", pTrips.size, hours, estCost),
                        style = monoStyle(12, FontWeight.Medium),
                        color = CyberOnAcid
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Vel. média", "${avgSpeed.toInt()} km/h", Icons.Default.Speed, CyberCyan, Modifier.weight(1f))
                    MetricCard("Vel. máxima", "${maxSpeed.toInt()} km/h", Icons.Default.TrendingUp, CyberRed, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Outros custos", brl(otherCost), Icons.Default.Build, CyberPink, Modifier.weight(1f))
                    MetricCard("Custo total/km", if (km > 0) brl((estCost + otherCost) / km) else "—", Icons.Default.AttachMoney, CyberGreen, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Combustível/km", if (km > 0) String.format(Locale.getDefault(), "R$ %.2f", estCost / km) else "—", Icons.Default.AttachMoney, CyberGreen, Modifier.weight(1f))
                    MetricCard("Viagem média", if (pTrips.isNotEmpty()) String.format(Locale.getDefault(), "%.1f km", km / pTrips.size) else "—", Icons.Default.Route, CyberPink, Modifier.weight(1f))
                }

                // Consumption: estimated vs pump
                CyberSectionHeader("Consumo", eyebrow = "Estimado × bomba")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Estimado", if (estKmL > 0) String.format(Locale.getDefault(), "%.1f km/L", estKmL) else "—", Icons.Default.Calculate, CyberAcid, Modifier.weight(1f))
                    MetricCard("Na bomba", pumpKmL?.let { String.format(Locale.getDefault(), "%.1f km/L", it) } ?: "—", Icons.Default.LocalGasStation, CyberOrange, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Litros estimados", String.format(Locale.getDefault(), "%.1f L", estLiters), Icons.Default.WaterDrop, CyberAcid, Modifier.weight(1f))
                    MetricCard("Litros abastecidos", String.format(Locale.getDefault(), "%.1f L", pumpLiters), Icons.Default.WaterDrop, CyberOrange, Modifier.weight(1f))
                }
                if (pumpKmL == null) {
                    Text("A média na bomba aparece quando o período tem dois tanques cheios.", color = TextTertiary, fontSize = 12.sp)
                }

                // Fuels used
                if (pFills.isNotEmpty()) {
                    CyberSectionHeader("Combustíveis", eyebrow = String.format(Locale.getDefault(), "R$ %.2f na bomba", pumpCost))
                    val byFuel = pFills.groupBy { it.fuelType }.map { (type, list) ->
                        Triple(type, list.sumOf { it.liters }, list.sumOf { it.totalCost })
                    }.sortedByDescending { it.second }
                    BarList(
                        rows = byFuel.map { (type, liters, cost) ->
                            BarRow(type, liters, String.format(Locale.getDefault(), "%.1f L · R$ %.2f · R$ %.2f/L", liters, cost, if (liters > 0) cost / liters else 0.0))
                        },
                        color = CyberOrange
                    )
                }

                // Other costs by category
                if (pExpenses.isNotEmpty()) {
                    CyberSectionHeader("Outros custos", eyebrow = brl(otherCost) + " no período")
                    BarList(
                        pExpenses.groupBy { it.category }
                            .map { (cat, l) -> BarRow(cat.emoji + " " + cat.label, l.sumOf { it.amount }, "" + l.size + "× · " + brl(l.sumOf { it.amount })) }
                            .sortedByDescending { it.value },
                        CyberPink
                    )
                }

                // Repeated routes with timing insight
                val tr = topRoutes
                if (!tr.isNullOrEmpty()) {
                    CyberSectionHeader("Rotas repetidas", eyebrow = "Tempo típico e horário")
                    BarList(
                        tr.map { (g, name) ->
                            BarRow(
                                name, g.count.toDouble(),
                                String.format(Locale("pt", "BR"), "%d× · %.0f–%.0f min", g.count, g.medianDurationMin, g.p90DurationMin) +
                                    (if (g.bestHour != null) " · melhor " + g.bestHour + "h" else "")
                            )
                        },
                        CyberCyan
                    )
                }

                // Destinations
                CyberSectionHeader("Destinos frequentes", eyebrow = "Onde as viagens terminam")
                val dest = destinations
                if (dest == null) {
                    LinearProgressIndicator(color = CyberAcid, trackColor = CyberPanelRaised, modifier = Modifier.fillMaxWidth())
                } else if (dest.isEmpty()) {
                    Text("Sem pontos de chegada no período.", color = TextTertiary, fontSize = 12.sp)
                } else {
                    BarList(dest.map { BarRow(it.label, it.count.toDouble(), "${it.count}×") }, CyberAcid)
                }

                // Traffic profile
                if (pTrips.isNotEmpty()) {
                    CyberSectionHeader("Perfil de trânsito", eyebrow = "Pela velocidade média")
                    val order = listOf("Anda e para", "Urbano", "Misto", "Estrada", "Estrada rápida")
                    val byTraffic = pTrips.groupBy { FuelConsumptionModel.trafficLabel(it.avgSpeedKmh) }
                    BarList(
                        rows = order.filter { byTraffic.containsKey(it) }.map { label ->
                            val list = byTraffic.getValue(label)
                            val k = list.sumOf { it.distanceKm }
                            BarRow(label, k, String.format(Locale.getDefault(), "%d viagens · %.0f km", list.size, k))
                        },
                        color = CyberCyan
                    )

                    // Weekday rhythm
                    CyberSectionHeader("Dias da semana", eyebrow = "Viagens por dia")
                    val counts = DayOfWeek.values().associateWith { d ->
                        pTrips.count { Instant.ofEpochMilli(it.startTimeMs).atZone(zone).dayOfWeek == d }
                    }
                    WeekdayBars(counts)
                }

                // Stations
                if (pFills.isNotEmpty()) {
                    CyberSectionHeader("Postos", eyebrow = "Mais usados")
                    BarList(
                        pFills.groupBy { it.stationName.ifBlank { "Sem nome" } }
                            .map { (name, list) -> BarRow(name, list.size.toDouble(), String.format(Locale.getDefault(), "%d× · R$ %.2f", list.size, list.sumOf { it.totalCost })) }
                            .sortedByDescending { it.value }
                            .take(5),
                        CyberPink
                    )
                }
            }
        }
    }
}

private data class BarRow(val label: String, val value: Double, val caption: String)

/** Horizontal bars, one hue (magnitude), label and value as text so identity never relies on color. */
@Composable
private fun BarList(rows: List<BarRow>, color: Color) {
    val max = rows.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    Column(
        Modifier
            .fillMaxWidth()
            .cyberPanel()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Column {
                Row(Modifier.fillMaxWidth()) {
                    Text(row.label, color = CyberText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(row.caption, style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(CyberPanelRaised)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((row.value / max).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayBars(counts: Map<DayOfWeek, Int>) {
    val names = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
    val max = (counts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(
        Modifier
            .fillMaxWidth()
            .cyberPanel()
            .padding(12.dp)
            .height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        DayOfWeek.values().forEachIndexed { i, d ->
            val c = counts[d] ?: 0
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$c", style = monoStyle(11), color = CyberText)
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((90 * c / max).coerceAtLeast(2).dp)
                        .background(if (c == 0) CyberLine else CyberPink)
                )
                Spacer(Modifier.height(4.dp))
                Text(names[i], style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = CyberTextSecondary)
            }
        }
    }
}

private fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
