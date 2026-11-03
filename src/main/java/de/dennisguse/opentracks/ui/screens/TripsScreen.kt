package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.model.DestinationFilter
import de.dennisguse.opentracks.ui.model.PeriodFilter
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.theme.*

@Composable
fun TripsScreen(
    trips: List<TripItem>,
    selectedFilter: PeriodFilter,
    onFilterSelected: (PeriodFilter) -> Unit,
    onTripClicked: (TripItem) -> Unit,
    onPeriodMapClicked: () -> Unit
) {
    val now = System.currentTimeMillis()
    var selectedDestination by remember { mutableStateOf(DestinationFilter.TODOS) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Histórico de Viagens",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Filtros por período e destino com mapa HD",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
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
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppleBlue,
                                selectedLabelColor = TextPrimary,
                                containerColor = AppleCard,
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
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ApplePurple,
                                selectedLabelColor = Color.White,
                                containerColor = AppleCard,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Summary Card for Selected Filters
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(WandaCard)
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Resumo: ${selectedFilter.label} • ${selectedDestination.label}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.1f km", periodKm),
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${filteredTrips.size} viagens encontradas",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "R$ %.2f", periodCost),
                                color = AppleGreen,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "%.1f L consumidos", periodFuel),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Button to view all filtered routes on HD Map
                    Button(
                        onClick = onPeriodMapClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            tint = AppleBlue,
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleCard)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma viagem encontrada para os filtros selecionados.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filteredTrips) { trip ->
                TripRowCard(trip = trip, onClick = { onTripClicked(trip) })
            }
        }
    }
}
