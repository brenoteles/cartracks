package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.theme.*

@Composable
fun SettingsScreen(
    homeLoc: CustomLocation,
    workLoc: CustomLocation,
    currentGps: Pair<Double, Double>?,
    speedLimitKmh: Int,
    isSpeedAlertEnabled: Boolean,
    onSaveLocation: (key: String, lat: Double, lon: Double, label: String) -> Unit,
    onSaveSpeedAlert: (limitKmh: Int, enabled: Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    var homeLat by remember(homeLoc) { mutableStateOf(homeLoc.lat.toString()) }
    var homeLon by remember(homeLoc) { mutableStateOf(homeLoc.lon.toString()) }

    var workLat by remember(workLoc) { mutableStateOf(workLoc.lat.toString()) }
    var workLon by remember(workLoc) { mutableStateOf(workLoc.lon.toString()) }

    var currentLimit by remember(speedLimitKmh) { mutableStateOf(speedLimitKmh) }
    var alertEnabled by remember(isSpeedAlertEnabled) { mutableStateOf(isSpeedAlertEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Ajustes",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Alertas de velocidade, rotas e preferências do veículo",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Feature 5: Speed Limit Alert Settings Card
        item {
            Text(
                text = "Alerta de Limite de Velocidade",
                color = AppleBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleCard)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aviso Sonoro e Visual",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Beep suave e aviso ao ultrapassar a velocidade máxima",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = alertEnabled,
                            onCheckedChange = {
                                alertEnabled = it
                                onSaveSpeedAlert(currentLimit, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppleBlue
                            )
                        )
                    }

                    if (alertEnabled) {
                        Text(
                            text = "Limite Atual: $currentLimit km/h",
                            color = AppleTeal,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        val presetLimits = listOf(60, 80, 100, 110, 120, 140)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presetLimits) { limit ->
                                val isSelected = limit == currentLimit
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        currentLimit = limit
                                        onSaveSpeedAlert(limit, alertEnabled)
                                    },
                                    label = {
                                        Text(
                                            text = "$limit km/h",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppleBlue,
                                        selectedLabelColor = Color.White,
                                        containerColor = AppleCardElevated,
                                        labelColor = TextSecondary
                                    ),
                                    border = null
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Favorite Locations
        item {
            Text(
                text = "Locais Favoritos (Detecção de Trajetos)",
                color = AppleBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Home Location Card
        item {
            LocationCard(
                title = "🏠 Casa",
                lat = homeLat,
                lon = homeLon,
                onLatChange = { homeLat = it },
                onLonChange = { homeLon = it },
                onUseGps = {
                    currentGps?.let { (lat, lon) ->
                        homeLat = lat.toString()
                        homeLon = lon.toString()
                    }
                },
                onSave = {
                    val lat = homeLat.toDoubleOrNull() ?: 0.0
                    val lon = homeLon.toDoubleOrNull() ?: 0.0
                    onSaveLocation("home", lat, lon, "Casa")
                }
            )
        }

        // Work Location Card
        item {
            LocationCard(
                title = "🏢 Trabalho",
                lat = workLat,
                lon = workLon,
                onLatChange = { workLat = it },
                onLonChange = { workLon = it },
                onUseGps = {
                    currentGps?.let { (lat, lon) ->
                        workLat = lat.toString()
                        workLon = lon.toString()
                    }
                },
                onSave = {
                    val lat = workLat.toDoubleOrNull() ?: 0.0
                    val lon = workLon.toDoubleOrNull() ?: 0.0
                    onSaveLocation("work", lat, lon, "Trabalho")
                }
            )
        }

        // Section: System & Sensors
        item {
            Text(
                text = "Sistema e Sensores",
                color = AppleBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Button(
                onClick = onOpenSystemSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configurações Avançadas de Sensores",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // App Info
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppleCard)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CarTracks • Jetpack Compose UI",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Versão 1.0.4 • Samsung Galaxy S24 Ultra Optimized",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Exportação GPX / KML • Alerta de Velocidade • Consumo Real km/L",
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LocationCard(
    title: String,
    lat: String,
    lon: String,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    onUseGps: () -> Unit,
    onSave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppleCard)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = onLatChange,
                    label = { Text("Latitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = onLonChange,
                    label = { Text("Longitude") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onUseGps,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppleBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Usar GPS Atual", fontSize = 12.sp, color = TextPrimary)
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("Salvar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
