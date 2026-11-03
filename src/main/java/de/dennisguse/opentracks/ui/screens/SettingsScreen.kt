package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    vehicleName: String = "Toyota Etios",
    vehiclePlate: String = "",
    tankCapacityLiters: Double = 50.0,
    primaryFuelType: String = "Gasolina Comum",
    onSaveLocation: (key: String, lat: Double, lon: Double, label: String) -> Unit,
    onSaveVehicleConfig: (name: String, plate: String, tankCapacity: Double, primaryFuelType: String) -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    var vehicleNameStr by remember(vehicleName) { mutableStateOf(vehicleName) }
    var vehiclePlateStr by remember(vehiclePlate) { mutableStateOf(vehiclePlate) }
    var tankCapacityStr by remember(tankCapacityLiters) { mutableStateOf(tankCapacityLiters.toInt().toString()) }
    var selectedFuelType by remember(primaryFuelType) { mutableStateOf(primaryFuelType) }

    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "home" or "work"

    // Map Location Picker Modal (Full address search & GPS picker)
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
            .background(WandaBackground)
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
                    text = "Personalize seus dados do veículo e locais favoritos",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Section: Vehicle & Fuel Parameters (Custom Vehicle Name & Plate!)
        item {
            Text(
                text = "Identificação do Veículo",
                color = WandaCyan20,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WandaCard)
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Vehicle Name Input
                    OutlinedTextField(
                        value = vehicleNameStr,
                        onValueChange = {
                            vehicleNameStr = it
                            val cap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                            onSaveVehicleConfig(it, vehiclePlateStr.uppercase().trim(), cap, selectedFuelType)
                        },
                        label = { Text("Nome / Modelo do Veículo") },
                        placeholder = { Text("Ex: Toyota Etios 1.5, Honda Civic...") },
                        leadingIcon = {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = WandaInteractive)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Vehicle License Plate Input
                    OutlinedTextField(
                        value = vehiclePlateStr,
                        onValueChange = {
                            val upper = it.uppercase().trim()
                            vehiclePlateStr = upper
                            val cap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                            onSaveVehicleConfig(vehicleNameStr, upper, cap, selectedFuelType)
                        },
                        label = { Text("Placa do Veículo") },
                        placeholder = { Text("Ex: BRA2E19 ou ABC-1234") },
                        leadingIcon = {
                            Icon(Icons.Default.Pin, contentDescription = null, tint = WandaCyan20)
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Divider(color = WandaBorderSubtle, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Capacidade do Tanque",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${tankCapacityStr} Litros",
                            color = WandaCyan20,
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
                                    onSaveVehicleConfig(vehicleNameStr, vehiclePlateStr.uppercase().trim(), p.toDouble(), selectedFuelType)
                                },
                                label = { Text("${p}L", fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WandaInteractive,
                                    selectedLabelColor = Color.White,
                                    containerColor = WandaCardElevated,
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
                            onSaveVehicleConfig(vehicleNameStr, vehiclePlateStr.uppercase().trim(), cap, selectedFuelType)
                        },
                        label = { Text("Ou digite a capacidade exata (L)") },
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
                                    onSaveVehicleConfig(vehicleNameStr, vehiclePlateStr.uppercase().trim(), cap, fType)
                                },
                                label = { Text(fType, fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WandaInteractive,
                                    selectedLabelColor = Color.White,
                                    containerColor = WandaCardElevated,
                                    labelColor = TextSecondary
                                ),
                                border = null
                            )
                        }
                    }
                }
            }
        }

        // Section: Favorite Locations
        item {
            Text(
                text = "Locais Favoritos (Detecção Inteligente)",
                color = WandaCyan20,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Home Location Card (Removed "Usar Posição Atual" button - map picker handles everything!)
        item {
            LocationCard(
                title = "Casa",
                icon = "🏠",
                isConfigured = homeLoc.isSet,
                lat = if (homeLoc.isSet) homeLoc.lat else null,
                lon = if (homeLoc.isSet) homeLoc.lon else null,
                onOpenMapPicker = { mapPickerTarget = "home" }
            )
        }

        // Work Location Card
        item {
            LocationCard(
                title = "Trabalho",
                icon = "🏢",
                isConfigured = workLoc.isSet,
                lat = if (workLoc.isSet) workLoc.lat else null,
                lon = if (workLoc.isSet) workLoc.lon else null,
                onOpenMapPicker = { mapPickerTarget = "work" }
            )
        }

        // Section: System & Advanced
        item {
            Text(
                text = "Sistema e Sensores",
                color = WandaCyan20,
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
                colors = ButtonDefaults.buttonColors(containerColor = WandaCardElevated)
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
                    .background(WandaCard)
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(18.dp))
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
                        text = "Versão 1.0.11 • Wanda Design System • Mapa HD Nativo",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LocationCard(
    title: String,
    icon: String,
    isConfigured: Boolean,
    lat: Double? = null,
    lon: Double? = null,
    onOpenMapPicker: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WandaCard)
            .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isConfigured) WandaGreen30.copy(alpha = 0.15f) else WandaYellow20.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConfigured) "🟢 Configurado" else "⚪ Não Definido",
                        color = if (isConfigured) WandaGreen30 else WandaYellow20,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (isConfigured && lat != null && lon != null) {
                    String.format(java.util.Locale.US, "Identifica automaticamente viagens em $title (Lat: %.4f, Lon: %.4f).", lat, lon)
                } else {
                    "Identifica automaticamente viagens com partida ou destino em $title."
                },
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Single primary full-width button to open map picker: No clutter, seamless!
            Button(
                onClick = onOpenMapPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isConfigured) "Alterar Local no Mapa ou Buscar" else "Definir Local no Mapa ou Buscar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}
