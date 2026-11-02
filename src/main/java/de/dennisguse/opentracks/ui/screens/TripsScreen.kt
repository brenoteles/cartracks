package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val filteredTrips = remember(trips, selectedFilter) {
        when (selectedFilter) {
            PeriodFilter.HOJE -> trips.filter { now - it.startTimeMs <= 24 * 3600 * 1000L }
            PeriodFilter.SETE_DIAS -> trips.filter { now - it.startTimeMs <= 7 * 24 * 3600 * 1000L }
            PeriodFilter.ESTE_MES -> trips.filter { now - it.startTimeMs <= 30 * 24 * 3600 * 1000L }
            PeriodFilter.TODAS -> trips
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
                    text = "Rotas, telemetria e consumo por período",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Period Filters (Cupertino Segmented Style)
        item {
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

        // Summary Card for Selected Period
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleCard)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Resumo do Período (${selectedFilter.label})",
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
                                text = "${filteredTrips.size} viagens",
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

                    // Fixed Button: Responsive, NEVER overflows text!
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
                            text = "Ver Todas as Rotas no Mapa",
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
                        text = "Nenhuma viagem encontrada neste período.",
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
