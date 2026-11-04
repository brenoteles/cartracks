package de.dennisguse.opentracks.ui.screens

import androidx.compose.ui.graphics.RectangleShape
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.model.DestinationFilter
import de.dennisguse.opentracks.ui.model.PeriodFilter
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TripsScreen(
    trips: List<TripItem>,
    selectedFilter: PeriodFilter,
    onFilterSelected: (PeriodFilter) -> Unit,
    onTripClicked: (TripItem) -> Unit,
    onPeriodMapClicked: () -> Unit,
    onDeleteTrip: ((Long) -> Unit)? = null,
    onUpdateTrip: ((Long, String, String) -> Unit)? = null
) {
    val now = System.currentTimeMillis()
    var selectedDestination by remember { mutableStateOf(DestinationFilter.TODOS) }
    var showMonthlyReport by remember { mutableStateOf(false) }

    val filteredTrips = remember(trips, selectedFilter, selectedDestination) {
        val byPeriod = when (selectedFilter) {
            PeriodFilter.HOJE -> trips.filter { now - it.startTimeMs <= 24 * 3600 * 1000L }
            PeriodFilter.SETE_DIAS -> trips.filter { now - it.startTimeMs <= 7 * 24 * 3600 * 1000L }
            PeriodFilter.ESTE_MES -> trips.filter { now - it.startTimeMs <= 30 * 24 * 3600 * 1000L }
            PeriodFilter.TODAS -> trips
        }

        when (selectedDestination) {
            DestinationFilter.TODOS -> byPeriod
            DestinationFilter.CASA -> byPeriod.filter {
                it.commuteBadge?.contains("Casa") == true ||
                it.name.contains("Casa", ignoreCase = true) ||
                it.description.contains("Casa", ignoreCase = true)
            }
            DestinationFilter.TRABALHO -> byPeriod.filter {
                it.commuteBadge?.contains("Trabalho") == true ||
                it.name.contains("Trabalho", ignoreCase = true) ||
                it.description.contains("Trabalho", ignoreCase = true)
            }
            DestinationFilter.OUTROS -> byPeriod.filter {
                it.commuteBadge == null &&
                !it.name.contains("Casa", ignoreCase = true) &&
                !it.name.contains("Trabalho", ignoreCase = true)
            }
        }
    }

    val periodKm = filteredTrips.sumOf { it.distanceKm }
    val periodFuel = filteredTrips.sumOf { it.fuelLiters }
    val periodCost = filteredTrips.sumOf { it.fuelCost }

    if (showMonthlyReport) {
        MonthlyReportDialog(
            trips = trips,
            onDismiss = { showMonthlyReport = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header with Monthly Report Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HISTÓRICO DE VIAGENS",
                        style = displayStyle(40),
                        color = CyberAcid
                    )
                    Text(
                        text = "// " + "Filtros por período e destino com mapa HD".uppercase(),
                        style = EyebrowStyle,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = { showMonthlyReport = true },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPanelRaised),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Relatórios",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Relatório",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Filter 1: Period Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Período:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PeriodFilter.values()) { filter ->
                        val isSelected = filter == selectedFilter
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterSelected(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            shape = RectangleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberAcid,
                                selectedLabelColor = TextPrimary,
                                containerColor = CyberPanel,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Filter 2: Destination Filters
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Destino do Trajeto:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(DestinationFilter.values()) { dest ->
                        val isSelected = dest == selectedDestination
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDestination = dest },
                            label = {
                                Text(
                                    text = dest.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            shape = RectangleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberAcid,
                                selectedLabelColor = TextPrimary,
                                containerColor = CyberPanel,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resumo (${selectedFilter.label})",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${filteredTrips.size} viagens",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Distância", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.1f km", periodKm),
                                color = CyberAcid,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column {
                            Text("Consumo", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.1f L", periodFuel),
                                color = CyberGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                        Column {
                            Text("Custo Estimado", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "R$ %.2f", periodCost),
                                color = CyberAcid,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    }

                    // Period Map Preview Action
                    Button(
                        onClick = onPeriodMapClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPanelRaised)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = CyberAcid,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ver Rotas Filtradas no Mapa HD",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Trips List
        if (filteredTrips.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .background(CyberPanel)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🛣️", fontSize = 32.sp)
                        Text(
                            text = "Nenhuma viagem registrada",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Toque em 'Iniciar Viagem' no Início para começar a gravar seus trajetos reais de carro!",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredTrips) { trip ->
                TripRowCard(trip = trip, onClick = { onTripClicked(trip) })
            }
        }
    }
}

@Composable
fun MonthlyReportDialog(
    trips: List<TripItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("pt", "BR")) }

    // Group trips by Month Year
    val monthGroups = remember(trips) {
        trips.groupBy { trip ->
            monthFormat.format(Date(trip.startTimeMs)).replaceFirstChar { it.uppercase() }
        }
    }

    val availableMonths = remember(monthGroups) {
        if (monthGroups.isEmpty()) listOf("Mês Atual") else monthGroups.keys.toList()
    }

    var selectedMonth by remember { mutableStateOf(availableMonths.first()) }
    val monthTrips = monthGroups[selectedMonth] ?: emptyList()

    val totalKm = monthTrips.sumOf { it.distanceKm }
    val totalLiters = monthTrips.sumOf { it.fuelLiters }
    val totalCost = monthTrips.sumOf { it.fuelCost }
    val totalHours = monthTrips.sumOf { it.durationMin } / 60.0
    val costPerKm = if (totalKm > 0) totalCost / totalKm else 0.0
    val commuteCount = monthTrips.count { it.commuteBadge != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Relatório Consolidado",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Month selector chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(availableMonths) { m ->
                        val isSel = m == selectedMonth
                        Box(
                            modifier = Modifier
                                .clip(RectangleShape)
                                .background(if (isSel) CyberAcid else CyberPanelRaised)
                                .clickable { selectedMonth = m }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = m,
                                color = if (isSel) CyberOnAcid else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Metrics Grid
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = CardDefaults.cardColors(containerColor = CyberInk)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total de Viagens:", color = TextSecondary, fontSize = 13.sp)
                            Text("${monthTrips.size} viagens", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Quilometragem Total:", color = TextSecondary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "%.1f km", totalKm), color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tempo ao Volante:", color = TextSecondary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "%.1f horas", totalHours), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Consumo Estimado:", color = TextSecondary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "%.1f Litros", totalLiters), color = CyberGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gasto em Viagens:", color = TextSecondary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "R$ %.2f", totalCost), color = CyberOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Custo Médio / km:", color = TextSecondary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "R$ %.2f / km", costPerKm), color = CyberAcid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trajetos Casa ↔ Trab.:", color = TextSecondary, fontSize = 13.sp)
                            Text("$commuteCount viagens", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RectangleShape,
                onClick = {
                    val summaryText = buildString {
                        appendLine("📊 RELATÓRIO MENSAL - CARTRACKS ($selectedMonth)")
                        appendLine("• Viagens Registradas: ${monthTrips.size}")
                        appendLine("• Distância Total: ${String.format(Locale.getDefault(), "%.1f km", totalKm)}")
                        appendLine("• Tempo ao Volante: ${String.format(Locale.getDefault(), "%.1f h", totalHours)}")
                        appendLine("• Combustível Estimado: ${String.format(Locale.getDefault(), "%.1f L", totalLiters)}")
                        appendLine("• Custo Total Estimado: R$ ${String.format(Locale.getDefault(), "%.2f", totalCost)}")
                        appendLine("• Custo Médio: R$ ${String.format(Locale.getDefault(), "%.2f/km", costPerKm)}")
                        appendLine("• Trajetos Casa/Trabalho: $commuteCount")
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Relatório Mensal CarTracks - $selectedMonth")
                        putExtra(Intent.EXTRA_TEXT, summaryText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Compartilhar Relatório"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Compartilhar Resumo", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = CyberPanelRaised
    )
}
