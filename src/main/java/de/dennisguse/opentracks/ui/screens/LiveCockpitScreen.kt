package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
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
                    colors = ButtonDefaults.buttonColors(containerColor = AppleRed)
                ) {
                    Text("Sim, Encerrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmStop = false }) {
                    Text("Continuar Gravando", color = AppleBlue)
                }
            },
            containerColor = AppleCardElevated
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
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
                        .background(AppleRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Apple Live Cockpit",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = onMinimizeClicked,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppleCardElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimizar",
                    tint = TextPrimary
                )
            }
        }

        if (!isMapExpanded) {
            // Huge Speedometer Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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
                    color = AppleTeal,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }

            // Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "Distância",
                    value = String.format(java.util.Locale.getDefault(), "%.2f km", liveData.distanceKm),
                    icon = Icons.Default.Speed,
                    tint = AppleBlue,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Tempo",
                    value = durationText,
                    icon = Icons.Default.Timer,
                    tint = AppleOrange,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Vel. Máx",
                    value = "${liveData.maxSpeedKmh.toInt()} km/h",
                    icon = Icons.Default.TrendingUp,
                    tint = AppleRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Live Map View (Expandable)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(AppleCard)
        ) {
            val curLoc = if (liveData.currentLat != null && liveData.currentLon != null) {
                Pair(liveData.currentLat, liveData.currentLon)
            } else null

            CarMapView(
                points = liveData.breadcrumbPoints,
                currentLocation = curLoc,
                modifier = Modifier.fillMaxSize()
            )

            // Prominent Maximize / Reduce Toggle on Map
            Button(
                onClick = { isMapExpanded = !isMapExpanded },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated.copy(alpha = 0.92f))
            ) {
                Icon(
                    imageVector = if (isMapExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = AppleBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMapExpanded) "Mostrar Velocímetro" else "Maximizar Mapa",
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
                        .background(AppleCardElevated.copy(alpha = 0.92f))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "${liveData.currentSpeedKmh.toInt()} km/h",
                        color = TextPrimary,
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
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppleRed)
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
