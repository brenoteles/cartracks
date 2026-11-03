package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.ui.model.LiveCockpitData
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelEfficiencyCalculator

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
        String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    val currentKmL = remember(liveData.currentSpeedKmh) {
        FuelEfficiencyCalculator.estimateTripKmL(liveData.currentSpeedKmh)
    }

    val speedColor = when {
        liveData.currentSpeedKmh > 120 -> WandaRed30
        liveData.currentSpeedKmh > 90 -> WandaYellow20
        liveData.currentSpeedKmh > 50 -> WandaGreen30
        else -> WandaCyan20
    }

    if (showConfirmStop) {
        AlertDialog(
            onDismissRequest = { showConfirmStop = false },
            title = {
                Text(
                    text = "Encerrar Viagem?",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "A gravação do percurso será finalizada e os dados de telemetria serão salvos no histórico.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmStop = false
                        onStopTripClicked()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WandaRed30)
                ) {
                    Text("Sim, Encerrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmStop = false }) {
                    Text("Continuar Gravando", color = WandaInteractive)
                }
            },
            containerColor = WandaCardElevated
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WandaBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header (Live Cockpit)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(WandaRed30)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Cockpit",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onMinimizeClicked,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(WandaCardElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimizar",
                    tint = TextPrimary
                )
            }
        }

        if (!isMapExpanded) {
            // Speedometer Display (Pure High-Contrast Automotive Telemetry)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${liveData.currentSpeedKmh.toInt()}",
                    color = TextPrimary,
                    fontSize = 82.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 84.sp
                )
                Text(
                    text = "KM/H",
                    color = speedColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            // High Readability 2x2 Telemetry Grid (Eliminates squished cards)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "Distância",
                        value = String.format(java.util.Locale.getDefault(), "%.2f km", liveData.distanceKm),
                        icon = Icons.Default.Speed,
                        tint = WandaCyan20,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Tempo",
                        value = durationText,
                        icon = Icons.Default.Timer,
                        tint = WandaYellow20,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "Vel. Máx",
                        value = "${liveData.maxSpeedKmh.toInt()} km/h",
                        icon = Icons.Default.TrendingUp,
                        tint = WandaRed30,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Consumo Est.",
                        value = String.format(java.util.Locale.getDefault(), "%.1f km/L", currentKmL),
                        icon = Icons.Default.LocalGasStation,
                        tint = WandaGreen30,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Live HD Map View (Expandable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WandaCard)
                .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp))
        ) {
            val curLoc = if (liveData.currentLat != null && liveData.currentLon != null) {
                Pair(liveData.currentLat, liveData.currentLon)
            } else null

            CarMapView(
                points = liveData.breadcrumbPoints,
                currentLocation = curLoc,
                modifier = Modifier.fillMaxSize()
            )

            // Prominent Maximize / Reduce Toggle on Map (Positioned TopEnd, away from TopStart layer switcher!)
            Button(
                onClick = { isMapExpanded = !isMapExpanded },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WandaCardElevated.copy(alpha = 0.94f))
            ) {
                Icon(
                    imageVector = if (isMapExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = WandaInteractive,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMapExpanded) "Mostrar Cockpit" else "Maximizar Mapa",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isMapExpanded) {
                // Floating Speed Badge when map is fullscreen
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WandaCardElevated.copy(alpha = 0.94f))
                        .border(1.dp, WandaBorderSubtle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${liveData.currentSpeedKmh.toInt()} km/h",
                        color = speedColor,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Finish Trip Action Button
        Button(
            onClick = { showConfirmStop = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WandaRed30)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Encerrar e Salvar Viagem",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
