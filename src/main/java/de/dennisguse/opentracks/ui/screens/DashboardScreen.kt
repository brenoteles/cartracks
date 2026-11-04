package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.MercosulPlate
import de.dennisguse.opentracks.ui.components.OdometerWheels
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.LiveCockpitData
import de.dennisguse.opentracks.ui.model.ParkingInfo
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.theme.*
import java.util.Locale

@Composable
fun DashboardScreen(
    trips: List<TripItem>,
    fuelEntries: List<FuelItem>,
    liveData: LiveCockpitData,
    vehicleName: String = "Meu Carro",
    vehiclePlate: String = "",
    tankCapacityLiters: Double = 50.0,
    primaryFuelType: String = "Gasolina Comum",
    currentFuelLiters: Double = 35.0,
    estimatedKmL: Double = 12.0,
    odometerKm: Double = 0.0,
    parkingInfo: ParkingInfo? = null,
    onNavigateToParking: ((Double, Double) -> Unit)? = null,
    onClearParking: (() -> Unit)? = null,
    onEditOdometerClicked: (() -> Unit)? = null,
    onConfigureVehicleClicked: (() -> Unit)? = null,
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
            .background(CyberInk)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Brand strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("CARTRACKS", style = displayStyle(40), color = CyberAcid)
                    Text("// TELEMETRIA VEICULAR", style = EyebrowStyle, color = CyberTextSecondary)
                }
                CyberChip(
                    text = if (liveData.isRecording) "● REC" else "GPS ONLINE",
                    color = if (liveData.isRecording) CyberRed else CyberCyan,
                    filled = liveData.isRecording
                )
            }
        }

        // Recording banner
        if (liveData.isRecording) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberPanel(border = CyberRed, background = CyberPanelRaised)
                        .clickable { onOpenCockpitClicked() }
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(6.dp).fillMaxHeight().background(CyberRed))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text("GRAVANDO VIAGEM", style = EyebrowStyle, color = CyberRed)
                        Text(
                            text = "${liveData.currentSpeedKmh.toInt()} KM/H  ·  ${String.format(Locale.getDefault(), "%.1f", liveData.distanceKm)} KM",
                            style = monoStyle(18),
                            color = CyberText
                        )
                    }
                    Text(
                        "COCKPIT ▸",
                        style = EyebrowStyle.copy(fontSize = 12.sp),
                        color = CyberAcid,
                        modifier = Modifier.padding(end = 14.dp)
                    )
                }
            }
        }

        // Vehicle hero (acid field)
        item {
            HeroVehicleStatusCard(
                vehicleName = vehicleName,
                vehiclePlate = vehiclePlate,
                tankCapacityLiters = tankCapacityLiters,
                primaryFuelType = primaryFuelType,
                currentFuelLiters = currentFuelLiters,
                estimatedKmL = estimatedKmL,
                odometerKm = odometerKm,
                onEditOdometer = onEditOdometerClicked,
                onConfigureVehicle = onConfigureVehicleClicked
            )
        }

        // Primary actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CyberActionButton(
                    text = if (liveData.isRecording) "Voltar ao Cockpit" else "Iniciar Viagem",
                    icon = Icons.Default.PlayArrow,
                    onClick = onStartTripClicked,
                    fill = if (liveData.isRecording) CyberRed else CyberAcid
                )
                CyberGhostButton(
                    text = "Registrar Abastecimento",
                    icon = Icons.Default.LocalGasStation,
                    accent = CyberOrange,
                    onClick = onAddFuelClicked,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Parking spot
        if (parkingInfo != null) {
            item {
                val elapsedMinutes = ((System.currentTimeMillis() - parkingInfo.timeMs) / 60000).coerceAtLeast(0)
                val elapsedHours = elapsedMinutes / 60
                val timeText = if (elapsedHours > 0) "HÁ ${elapsedHours}H ${elapsedMinutes % 60}MIN" else "HÁ ${elapsedMinutes} MIN"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberPanel(border = CyberCyan)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CyberCyan, RectangleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocalParking, contentDescription = null, tint = CyberOnAcid, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ONDE ESTACIONEI", style = displayStyle(20), color = CyberText)
                        Text(timeText, style = EyebrowStyle, color = CyberCyan)
                        Text(
                            String.format(Locale.US, "%.5f, %.5f", parkingInfo.lat, parkingInfo.lon),
                            style = monoStyle(11, FontWeight.Normal),
                            color = CyberTextTertiary
                        )
                    }
                    IconButton(onClick = { onNavigateToParking?.invoke(parkingInfo.lat, parkingInfo.lon) }) {
                        Icon(Icons.Default.Navigation, contentDescription = "Navegar até o carro", tint = CyberAcid)
                    }
                    IconButton(onClick = { onClearParking?.invoke() }) {
                        Icon(Icons.Default.Close, contentDescription = "Liberar vaga", tint = CyberTextSecondary)
                    }
                }
            }
        }

        // Totals grid
        item {
            CyberSectionHeader(title = "Resumo", eyebrow = "Todas as viagens")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Distância", String.format(Locale.getDefault(), "%.1f km", totalKm), Icons.Default.Route, CyberCyan, Modifier.weight(1f))
                    MetricCard("Combustível", String.format(Locale.getDefault(), "%.1f L", totalFuel), Icons.Default.LocalGasStation, CyberOrange, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Custo est.", String.format(Locale.getDefault(), "R$ %.2f", totalCost), Icons.Default.AttachMoney, CyberGreen, Modifier.weight(1f))
                    MetricCard("Viagens", "${trips.size}", Icons.Default.DirectionsCar, CyberPink, Modifier.weight(1f))
                }
            }
        }

        // Recent trips
        item {
            CyberSectionHeader(
                title = "Viagens recentes",
                eyebrow = "Histórico",
                trailing = {
                    Text(
                        "VER TODAS (${trips.size}) ▸",
                        style = EyebrowStyle,
                        color = CyberAcid,
                        modifier = Modifier
                            .clickable { onViewAllTripsClicked() }
                            .padding(vertical = 4.dp)
                    )
                }
            )
        }

        if (trips.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberPanel(border = CyberLine)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NENHUMA VIAGEM REGISTRADA.\nTOQUE EM INICIAR VIAGEM.",
                        style = EyebrowStyle.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = CyberTextSecondary,
                        textAlign = TextAlign.Center
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
    estimatedKmL: Double = 12.0,
    odometerKm: Double,
    onEditOdometer: (() -> Unit)? = null,
    onConfigureVehicle: (() -> Unit)? = null
) {
    val tankPercent = if (tankCapacityLiters > 0) (currentFuelLiters / tankCapacityLiters).coerceIn(0.0, 1.0) else 0.0
    val estimatedRangeKm = (currentFuelLiters * estimatedKmL).toInt()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(color = CyberMagenta, offset = 6.dp)
            .background(CyberAcid, RectangleShape)
            .border(2.dp, CyberOnAcid, RectangleShape)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("VEÍCULO", style = EyebrowStyle, color = CyberOnAcidSecondary)
                Text(
                    vehicleName.ifBlank { "Meu Carro" }.uppercase(),
                    style = displayStyle(34),
                    color = CyberOnAcid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (vehiclePlate.isNotBlank()) {
                MercosulPlate(vehiclePlate)
            }
            if (onConfigureVehicle != null) {
                IconButton(onClick = onConfigureVehicle) {
                    Icon(Icons.Default.Tune, contentDescription = "Configurar veículo", tint = CyberOnAcid)
                }
            }
        }

        // Odometer readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onEditOdometer != null) { onEditOdometer?.invoke() }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text("ODÔMETRO", style = EyebrowStyle.copy(fontSize = 10.sp), color = CyberOnAcidSecondary)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    OdometerWheels(km = odometerKm)
                    Spacer(Modifier.width(6.dp))
                    Text("KM", style = displayStyle(22), color = CyberOnAcid)
                }
            }
            Spacer(Modifier.weight(1f))
            if (onEditOdometer != null) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Editar odômetro",
                    tint = CyberOnAcid,
                    modifier = Modifier
                        .padding(bottom = 10.dp)
                        .size(18.dp)
                )
            }
        }

        // Segmented fuel gauge
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TANQUE · ${primaryFuelType.uppercase()}", style = EyebrowStyle, color = CyberOnAcidSecondary, maxLines = 1)
                Text(
                    String.format(Locale.getDefault(), "%.1f / %.0f L", currentFuelLiters, tankCapacityLiters),
                    style = monoStyle(12, FontWeight.Bold),
                    color = CyberOnAcid
                )
            }
            Spacer(Modifier.height(6.dp))
            val segments = 20
            Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = CyberOnAcid, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("E", style = monoStyle(12, FontWeight.Bold), color = CyberRed)
            Spacer(Modifier.width(6.dp))
            val lit = (tankPercent * segments).toInt()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(segments) { i ->
                    val color = when {
                        i >= lit -> CyberOnAcid.copy(alpha = 0.12f)
                        tankPercent < 0.2 -> CyberRed
                        else -> CyberOnAcid
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text("F", style = monoStyle(12, FontWeight.Bold), color = CyberOnAcid)
            }
        }

        // Ink telemetry strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberOnAcid)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeroStat("Nível", "${(tankPercent * 100).toInt()}%", CyberAcid)
            HeroStat("Autonomia", "~$estimatedRangeKm km", CyberCyan)
            HeroStat("Média", String.format(Locale.getDefault(), "%.1f km/L", estimatedKmL), CyberPink)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color) {
    Column {
        Text(label.uppercase(), style = EyebrowStyle.copy(fontSize = 10.sp), color = CyberTextSecondary)
        Text(value, style = monoStyle(15, FontWeight.Bold), color = color, maxLines = 1)
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
    TelemetryReadout(
        label = title,
        value = value,
        accent = tint,
        icon = icon,
        modifier = modifier
    )
}

@Composable
fun TripRowCard(
    trip: TripItem,
    onClick: () -> Unit
) {
    val commuteColor = when {
        trip.commuteBadge?.contains("Casa", ignoreCase = true) == true -> CyberCyan
        trip.commuteBadge?.contains("Trabalho", ignoreCase = true) == true -> CyberPink
        else -> CyberAcid
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cyberPanel()
            .clickable { onClick() }
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(commuteColor))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = trip.name.ifEmpty { "Viagem" },
                    color = CyberText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = trip.dateFormatted,
                    style = monoStyle(11, FontWeight.Normal),
                    color = CyberTextTertiary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (!trip.commuteBadge.isNullOrEmpty()) {
                Spacer(Modifier.height(6.dp))
                CyberChip(text = trip.commuteBadge, color = commuteColor)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(String.format(Locale.getDefault(), "%.1f km", trip.distanceKm), style = monoStyle(18, FontWeight.Bold), color = CyberAcid)
                Text("${trip.durationMin} min", style = monoStyle(12, FontWeight.Normal), color = CyberTextSecondary)
                Text("${trip.avgSpeedKmh.toInt()} km/h", style = monoStyle(12, FontWeight.Normal), color = CyberTextSecondary)
                Text(String.format(Locale.getDefault(), "R$ %.2f", trip.fuelCost), style = monoStyle(12, FontWeight.Bold), color = CyberGreen)
            }
        }
    }
}
