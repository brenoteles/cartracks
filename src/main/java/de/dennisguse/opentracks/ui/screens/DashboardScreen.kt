package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.LiveCockpitData
import de.dennisguse.opentracks.ui.model.ParkingInfo
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.theme.*

@Composable
fun DashboardScreen(
    trips: List<TripItem>,
    fuelEntries: List<FuelItem>,
    liveData: LiveCockpitData,
    vehicleName: String = "Toyota Etios",
    vehiclePlate: String = "",
    tankCapacityLiters: Double = 50.0,
    primaryFuelType: String = "Gasolina Comum",
    parkingInfo: ParkingInfo? = null,
    onNavigateToParking: ((Double, Double) -> Unit)? = null,
    onClearParking: (() -> Unit)? = null,
    onStartTripClicked: () -> Unit,
    onOpenCockpitClicked: () -> Unit,
    onAddFuelClicked: () -> Unit,
    onTripClicked: (TripItem) -> Unit,
    onViewAllTripsClicked: () -> Unit
) {
    val totalKm = trips.sumOf { it.distanceKm }
    val totalFuel = trips.sumOf { it.fuelLiters }
    val totalCost = trips.sumOf { it.fuelCost }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(WandaBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Dynamic Island Banner if recording
        if (liveData.isRecording) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(WandaCardElevated)
                        .border(1.dp, WandaRed30.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .clickable { onOpenCockpitClicked() }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(WandaRed30)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Viagem em Gravação",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${liveData.currentSpeedKmh.toInt()} km/h • ${String.format(java.util.Locale.getDefault(), "%.1f", liveData.distanceKm)} km",
                                color = WandaCyan20,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = "Abrir Cockpit ➔",
                        color = WandaInteractive,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Parking Spot Card ("Onde Estacionei")
        if (parkingInfo != null) {
            item {
                val elapsedMs = System.currentTimeMillis() - parkingInfo.timeMs
                val elapsedMinutes = (elapsedMs / (1000 * 60)).coerceAtLeast(0)
                val elapsedHours = elapsedMinutes / 60
                val timeText = if (elapsedHours > 0) {
                    "Estacionado há ${elapsedHours}h ${elapsedMinutes % 60}m"
                } else {
                    "Estacionado há ${elapsedMinutes} min"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(WandaCardElevated)
                        .border(1.dp, WandaCyan20.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(WandaCyan20.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Onde Estacionei",
                                tint = WandaCyan20,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Onde Estacionei",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = timeText,
                                color = WandaCyan20,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format(java.util.Locale.getDefault(), "Lat: %.4f, Lon: %.4f", parkingInfo.lat, parkingInfo.lon),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { onNavigateToParking?.invoke(parkingInfo.lat, parkingInfo.lon) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WandaPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "Ir até o carro",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Navegar",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { onClearParking?.invoke() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Liberar vaga",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Header & Vehicle Hero Status Card (Vibrant & High-Tech)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CarTracks",
                            color = TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Painel Veicular Inteligente",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    // Active GPS Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(WandaGreen30.copy(alpha = 0.15f))
                            .border(1.dp, WandaGreen30.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(WandaGreen30)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GPS ATIVO",
                                color = WandaGreen30,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // Vibrant Hero Vehicle Cockpit Card
                HeroVehicleStatusCard(
                    vehicleName = vehicleName,
                    vehiclePlate = vehiclePlate,
                    tankCapacityLiters = tankCapacityLiters,
                    primaryFuelType = primaryFuelType,
                    currentFuelLiters = (tankCapacityLiters * 0.82),
                    odometerKm = 148520.0 + totalKm
                )
            }
        }

        // 3. Metric Cards Grid (2x2 with Vibrant Top Accent Lines)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Distância Total",
                        value = String.format(java.util.Locale.getDefault(), "%.1f km", totalKm),
                        icon = Icons.Default.Speed,
                        tint = WandaCyan20,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Combustível",
                        value = String.format(java.util.Locale.getDefault(), "%.1f L", totalFuel),
                        icon = Icons.Default.LocalGasStation,
                        tint = WandaYellow20,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Custo Estimado",
                        value = String.format(java.util.Locale.getDefault(), "R$ %.2f", totalCost),
                        icon = Icons.Default.AttachMoney,
                        tint = WandaGreen30,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total de Viagens",
                        value = "${trips.size} viagens",
                        icon = Icons.Default.DirectionsCar,
                        tint = WandaPurple30,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Primary Actions (Vibrant High-Contrast Buttons)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartTripClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (liveData.isRecording) "Continuar no Cockpit" else "Iniciar Nova Viagem",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onAddFuelClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = WandaCard),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(WandaBorderSubtle))
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = WandaYellow20
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registrar Abastecimento",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }

        // 5. Recent Trips Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Viagens Recentes",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Ver Todas (${trips.size})",
                    color = WandaInteractive,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onViewAllTripsClicked() }
                )
            }
        }

        if (trips.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(WandaCard)
                        .border(1.dp, WandaBorderSubtle, RoundedCornerShape(18.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma viagem registrada ainda.\nToque em Iniciar Nova Viagem!",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(trips.take(4)) { trip ->
                TripRowCard(trip = trip, onClick = { onTripClicked(trip) })
            }
        }
    }
}

@Composable
fun HeroVehicleStatusCard(
    vehicleName: String,
    vehiclePlate: String,
    tankCapacityLiters: Double,
    primaryFuelType: String,
    currentFuelLiters: Double,
    odometerKm: Double
) {
    val tankPercent = (currentFuelLiters / tankCapacityLiters).coerceIn(0.0, 1.0)
    val estimatedRangeKm = (currentFuelLiters * 13.8).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF121624),
                        Color(0xFF1B2338)
                    )
                )
            )
            .border(1.dp, Color(0x334589FF), RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Vehicle Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(WandaInteractive.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = WandaInteractive,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = vehicleName.ifBlank { "Meu Veículo" },
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (vehiclePlate.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFF003399), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = vehiclePlate.uppercase(),
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Tanque: ${tankCapacityLiters.toInt()}L • $primaryFuelType",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WandaGreen30.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "82% Tanque",
                        color = WandaGreen30,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Fuel Level Gauge Bar (Gradient Cyan -> Green)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Nível de Combustível",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "%.1f L restantes".format(java.util.Locale.US, currentFuelLiters),
                        color = WandaCyan20,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF232838))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = tankPercent.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(WandaCyan20, WandaGreen30)
                                )
                            )
                    )
                }
            }

            // Bottom Vehicle Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WandaCardElevated.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text("ODÔMETRO", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${odometerKm.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+$)"), "$1.")} km",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WandaCardElevated.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text("AUTONOMIA EST.", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = "~$estimatedRangeKm km",
                            fontSize = 14.sp,
                            color = WandaGreen30,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WandaCardElevated.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text("MÉDIA GERAL", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = "13.8 km/L",
                            fontSize = 14.sp,
                            color = WandaYellow20,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(WandaCard)
            .border(1.dp, WandaBorderSubtle, RoundedCornerShape(18.dp))
    ) {
        Column {
            // Vibrant Top Accent Stripe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(tint)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TripRowCard(
    trip: TripItem,
    onClick: () -> Unit
) {
    val commuteColor = when {
        trip.commuteBadge?.contains("Casa", ignoreCase = true) == true -> WandaCyan20
        trip.commuteBadge?.contains("Trabalho", ignoreCase = true) == true -> WandaPurple30
        else -> WandaInteractive
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(WandaCard)
            .border(1.dp, WandaBorderSubtle, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!trip.commuteBadge.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(commuteColor.copy(alpha = 0.16f))
                                .border(1.dp, commuteColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = trip.commuteBadge,
                                color = commuteColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = trip.name.ifEmpty { "Viagem Automotiva" },
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Text(
                    text = trip.dateFormatted,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(java.util.Locale.getDefault(), "%.1f km", trip.distanceKm),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${trip.durationMin} min",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = "${trip.avgSpeedKmh.toInt()} km/h méd.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = String.format(java.util.Locale.getDefault(), "R$ %.2f", trip.fuelCost),
                    color = WandaGreen30,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
