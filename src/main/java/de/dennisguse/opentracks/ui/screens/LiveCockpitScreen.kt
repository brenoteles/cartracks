package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.ui.components.SpeedGauge
import de.dennisguse.opentracks.ui.model.LiveCockpitData
import de.dennisguse.opentracks.ui.theme.*
import java.util.Locale

@Composable
fun LiveCockpitScreen(
    liveData: LiveCockpitData,
    onStopTripClicked: () -> Unit,
    onMinimizeClicked: () -> Unit
) {
    var showConfirmStop by remember { mutableStateOf(false) }
    var isMapExpanded by remember { mutableStateOf(false) }

    val hours = liveData.durationSec / 3600
    val minutes = (liveData.durationSec % 3600) / 60
    val seconds = liveData.durationSec % 60
    val durationText = if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    val currentKmL = if (liveData.fuelLiters > 0) liveData.distanceKm / liveData.fuelLiters else 0.0

    val speedColor = when {
        liveData.currentSpeedKmh > 120 -> CyberRed
        liveData.currentSpeedKmh > 90 -> CyberOrange
        liveData.currentSpeedKmh > 50 -> CyberAcid
        else -> CyberCyan
    }
    val curLoc = if (liveData.currentLat != null && liveData.currentLon != null) {
        Pair(liveData.currentLat, liveData.currentLon)
    } else null

    // Heading from the last two distinct trace points; kept while stopped at a light
    val vehicleBearing = remember(liveData.breadcrumbPoints) {
        val pts = liveData.breadcrumbPoints
        val last = pts.lastOrNull()
        val prev = last?.let { l -> pts.takeLast(10).dropLast(1).lastOrNull { it.lat != l.lat || it.lon != l.lon } }
        if (last == null || prev == null) null else {
            val lat1 = Math.toRadians(prev.lat)
            val lat2 = Math.toRadians(last.lat)
            val dLon = Math.toRadians(last.lon - prev.lon)
            val y = Math.sin(dLon) * Math.cos(lat2)
            val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
            ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
        }
    }

    if (showConfirmStop) {
        AlertDialog(
            onDismissRequest = { showConfirmStop = false },
            title = { Text("ENCERRAR VIAGEM?", style = displayStyle(28), color = CyberText) },
            text = {
                Text(
                    "A gravação do percurso será finalizada e a telemetria salva no histórico.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmStop = false
                        onStopTripClicked()
                    },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("SIM, ENCERRAR", style = EyebrowStyle.copy(fontSize = 12.sp), color = CyberText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmStop = false }, shape = RectangleShape) {
                    Text("CONTINUAR", style = EyebrowStyle.copy(fontSize = 12.sp), color = CyberAcid)
                }
            },
            shape = RectangleShape,
            containerColor = CyberPanelRaised
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CyberChip(text = "● REC", color = CyberRed, filled = true)
            Spacer(Modifier.width(8.dp))
            CyberChip(
                text = if (curLoc != null) "GNSS LOCK" else "BUSCANDO SATÉLITES",
                color = if (curLoc != null) CyberCyan else CyberOrange
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, CyberLine, RectangleShape)
                    .clickable { onMinimizeClicked() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Minimizar", tint = CyberText)
            }
        }


        if (!isMapExpanded) {
            // Instrument cluster speedometer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = speedColor),
                contentAlignment = Alignment.Center
            ) {
                SpeedGauge(
                    speedKmh = liveData.currentSpeedKmh,
                    maxSpeedKmh = liveData.maxSpeedKmh,
                    color = speedColor,
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .heightIn(max = 260.dp)  // landscape: keep the dial inside its panel
                        .padding(vertical = 6.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Distância", String.format(Locale.getDefault(), "%.2f km", liveData.distanceKm), Icons.Default.Route, CyberCyan, Modifier.weight(1f))
                    MetricCard("Tempo", durationText, Icons.Default.Timer, CyberAcid, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Vel. máx", "${liveData.maxSpeedKmh.toInt()} km/h", Icons.Default.TrendingUp, CyberRed, Modifier.weight(1f))
                    MetricCard("Consumo est.", String.format(Locale.getDefault(), "%.1f km/L", currentKmL), Icons.Default.LocalGasStation, CyberGreen, Modifier.weight(1f))
                }
            }
        }

        // Live map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, CyberLine, RectangleShape)
        ) {
            CarMapView(
                points = liveData.breadcrumbPoints,
                currentLocation = curLoc,
                followVehicle = true,
                vehicleBearing = vehicleBearing,
                onCenterGpsClicked = {},
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(CyberInk.copy(alpha = 0.92f), RectangleShape)
                    .border(1.dp, CyberAcid, RectangleShape)
                    .clickable { isMapExpanded = !isMapExpanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isMapExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = CyberAcid,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isMapExpanded) "COCKPIT" else "MAPA",
                    style = EyebrowStyle,
                    color = CyberText
                )
                if (isMapExpanded) {
                    Spacer(Modifier.width(10.dp))
                    Text("${liveData.currentSpeedKmh.toInt()} KM/H", style = monoStyle(16, FontWeight.Bold), color = speedColor)
                }
            }
        }

        CyberActionButton(
            text = "Encerrar e salvar",
            icon = Icons.Default.Stop,
            fill = CyberRed,
            onClick = { showConfirmStop = true }
        )
    }
}
