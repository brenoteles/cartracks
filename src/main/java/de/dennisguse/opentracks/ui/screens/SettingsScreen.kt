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
    var homeLat by remember(homeLoc) { mutableStateOf(homeLoc.lat.toString()) }
    var homeLon by remember(homeLoc) { mutableStateOf(homeLoc.lon.toString()) }

    var workLat by remember(workLoc) { mutableStateOf(workLoc.lat.toString()) }
    var workLon by remember(workLoc) { mutableStateOf(workLoc.lon.toString()) }

    var tankCapacityStr by remember(tankCapacityLiters) { mutableStateOf(tankCapacityLiters.toInt().toString()) }
    var selectedFuelType by remember(primaryFuelType) { mutableStateOf(primaryFuelType) }

    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "home" or "work"

    // Map Location Picker Modal
    if (mapPickerTarget != null) {
        val isHome = mapPickerTarget == "home"
        val curLat = if (isHome) homeLat.toDoubleOrNull() ?: -23.6015 else workLat.toDoubleOrNull() ?: -23.5615
        val curLon = if (isHome) homeLon.toDoubleOrNull() ?: -46.6900 else workLon.toDoubleOrNull() ?: -46.6559

        LocationPickerMapDialog(
            title = if (isHome) "Casa" else "Trabalho",
            initialLat = curLat,
            initialLon = curLon,
            currentGps = currentGps,
            onConfirm = { lat, lon ->
                if (isHome) {
                    homeLat = lat.toString()
                    homeLon = lon.toString()
                    onSaveLocation("home", lat, lon, "Casa")
                } else {
                    workLat = lat.toString()
                    workLon = lon.toString()
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
                    text = "Locais favoritos no mapa e parâmetros do veículo",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
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
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppleCard)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Capacidade do Tanque e Combustível",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = tankCapacityStr,
                        onValueChange = {
                            tankCapacityStr = it
                            val cap = it.toDoubleOrNull() ?: 50.0
                            onSaveVehicleConfig(cap, selectedFuelType)
                        },
                        label = { Text("Capacidade Máxima do Tanque (Litros)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                                shape = RoundedCornerShape(14.dp),
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

        // Section: Favorite Locations with Map Pinning
        item {
            Text(
                text = "Locais Favoritos (Pinar no Mapa)",
                color = AppleBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Home Location Card
        item {
            LocationCardWithMapPicker(
                title = "🏠 Casa",
                lat = homeLat,
                lon = homeLon,
                onLatChange = { homeLat = it },
                onLonChange = { homeLon = it },
                onOpenMapPicker = { mapPickerTarget = "home" },
                onUseGps = {
                    currentGps?.let { (lat, lon) ->
                        homeLat = lat.toString()
                        homeLon = lon.toString()
                        onSaveLocation("home", lat, lon, "Casa")
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
            LocationCardWithMapPicker(
                title = "🏢 Trabalho",
                lat = workLat,
                lon = workLon,
                onLatChange = { workLat = it },
                onLonChange = { workLon = it },
                onOpenMapPicker = { mapPickerTarget = "work" },
                onUseGps = {
                    currentGps?.let { (lat, lon) ->
                        workLat = lat.toString()
                        workLon = lon.toString()
                        onSaveLocation("work", lat, lon, "Trabalho")
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
                    text = "Configurações Avançadas do Sistema",
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
                        text = "Versão 1.0.5 • Mapa HD • Tanque Parcial / Cheio • Pinar no Mapa",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LocationCardWithMapPicker(
    title: String,
    lat: String,
    lon: String,
    onLatChange: (String) -> Unit,
    onLonChange: (String) -> Unit,
    onOpenMapPicker: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                // Prominent Map Picker Button!
                Button(
                    onClick = onOpenMapPicker,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppleBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pinar no Mapa",
                        color = AppleBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

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
                    Text("Usar GPS", fontSize = 12.sp, color = TextPrimary)
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
