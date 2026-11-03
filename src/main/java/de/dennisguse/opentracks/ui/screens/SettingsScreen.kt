package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.LocationPickerMapDialog
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.theme.*

@Composable
fun SettingsScreen(
    homeLoc: CustomLocation,
    workLoc: CustomLocation,
    currentGps: Pair<Double, Double>?,
    tankCapacityLiters: Double,
    primaryFuelType: String,
    onSaveLocation: (key: String, lat: Double, lon: Double, label: String) -> Unit,
    onSaveVehicleConfig: (tankCapacity: Double, primaryFuelType: String) -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    var tankCapacityStr by remember(tankCapacityLiters) { mutableStateOf(tankCapacityLiters.toInt().toString()) }
    var selectedFuelType by remember(primaryFuelType) { mutableStateOf(primaryFuelType) }

    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "home" or "work"

    // Map Location Picker Modal (No manual typing of coordinates!)
    if (mapPickerTarget != null) {
        val isHome = mapPickerTarget == "home"
        val curLat = if (isHome) homeLoc.lat else workLoc.lat
        val curLon = if (isHome) homeLoc.lon else workLoc.lon

        LocationPickerMapDialog(
            title = if (isHome) "Casa" else "Trabalho",
            initialLat = curLat,
            initialLon = curLon,
            currentGps = currentGps,
            onConfirm = { lat, lon ->
                if (isHome) {
                    onSaveLocation("home", lat, lon, "Casa")
                } else {
                    onSaveLocation("work", lat, lon, "Trabalho")
                }
            },
            onDismiss = { mapPickerTarget = null }
        )
    }

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
                    text = "Personalize seus locais favoritos e consumo do veículo",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Section: Favorite Locations (Apple Style - Zero Manual Coordinates)
        item {
            Text(
                text = "Locais Favoritos (Detecção Inteligente)",
                color = AppleBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Home Location Card
        item {
            AppleLocationCard(
                title = "Casa",
                icon = "🏠",
                isConfigured = homeLoc.isSet,
                onOpenMapPicker = { mapPickerTarget = "home" },
                onUseGps = {
                    currentGps?.let { (lat, lon) ->
                        onSaveLocation("home", lat, lon, "Casa")
                    }
                }
            )
        }

        // Work Location Card
        item {
            AppleLocationCard(
                title = "Trabalho",
                icon = "🏢",
                isConfigured = workLoc.isSet,
                onOpenMapPicker = { mapPickerTarget = "work" },
                onUseGps = {
                    currentGps?.let { (lat, lon) ->
                        onSaveLocation("work", lat, lon, "Trabalho")
                    }
                }
            )
        }

        // Section: Vehicle & Fuel Parameters
        item {
            Text(
                text = "Parâmetros do Veículo",
                color = AppleBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppleCard)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Capacidade do Tanque",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tankCapacityStr} Litros",
                            color = AppleTeal,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Quick Tank Presets
                    val presets = listOf("45", "50", "55", "60", "70")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { p ->
                            val sel = tankCapacityStr == p
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    tankCapacityStr = p
                                    onSaveVehicleConfig(p.toDouble(), selectedFuelType)
                                },
                                label = { Text("${p}L", fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
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

                    OutlinedTextField(
                        value = tankCapacityStr,
                        onValueChange = {
                            tankCapacityStr = it
                            val cap = it.toDoubleOrNull() ?: 50.0
                            onSaveVehicleConfig(cap, selectedFuelType)
                        },
                        label = { Text("Ou digite a capacidade exata (L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Combustível Principal:",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    val fuelTypes = listOf("Gasolina Comum", "Gasolina Aditivada", "Etanol", "Diesel")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fuelTypes) { fType ->
                            val isSelected = fType == selectedFuelType
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedFuelType = fType
                                    val cap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                                    onSaveVehicleConfig(cap, fType)
                                },
                                label = { Text(fType, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
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

        // Section: System & Advanced
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
                    text = "Configurações Avançadas de Gravação",
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
                    .clip(RoundedCornerShape(18.dp))
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
                        text = "Versão 1.0.6 • Mapa HD Nativo • Apple HIG Dark Design",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AppleLocationCard(
    title: String,
    icon: String,
    isConfigured: Boolean,
    onOpenMapPicker: () -> Unit,
    onUseGps: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppleCard)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isConfigured) AppleGreen.copy(alpha = 0.15f) else AppleOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConfigured) "🟢 Configurado no Mapa" else "⚪ Não Definido",
                        color = if (isConfigured) AppleGreen else AppleOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Identifica automaticamente viagens com partida ou destino em $title.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenMapPicker,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConfigured) "Alterar no Mapa" else "Escolher no Mapa",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onUseGps,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppleTeal
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Usar GPS", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}
