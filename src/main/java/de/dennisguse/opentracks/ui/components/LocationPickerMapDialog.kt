package de.dennisguse.opentracks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.dennisguse.opentracks.ui.theme.*

@Composable
fun LocationPickerMapDialog(
    title: String,
    initialLat: Double,
    initialLon: Double,
    currentGps: Pair<Double, Double>?,
    onConfirm: (lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var pinnedLat by remember { mutableStateOf(initialLat) }
    var pinnedLon by remember { mutableStateOf(initialLon) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppleBlack)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. HD Interactive Map with Tap-to-Pin
            CarMapView(
                pinnedLocation = Pair(pinnedLat, pinnedLon),
                pinnedTitle = title,
                onMapTapped = { lat, lon ->
                    pinnedLat = lat
                    pinnedLon = lon
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Top Header Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppleCardElevated.copy(alpha = 0.95f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pinar no Mapa: $title",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Toque no mapa para posicionar o marcador",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                }
            }

            // 3. Bottom Confirmation Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppleCard.copy(alpha = 0.96f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Coordenadas Selecionadas:",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.5f, %.5f", pinnedLat, pinnedLon),
                            color = AppleTeal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (currentGps != null) {
                        OutlinedButton(
                            onClick = {
                                pinnedLat = currentGps.first
                                pinnedLon = currentGps.second
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AppleBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GPS Atual", fontSize = 12.sp, color = TextPrimary)
                        }
                    }
                }

                Button(
                    onClick = {
                        onConfirm(pinnedLat, pinnedLon)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text(
                        text = "Confirmar Localização de $title",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
