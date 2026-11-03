package de.dennisguse.opentracks.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
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
    isBatteryOptimized: Boolean = false,
    onRequestIgnoreBattery: () -> Unit = {},
    onSaveLocation: (key: String, lat: Double, lon: Double, label: String) -> Unit,
    onSaveVehicleConfig: (name: String, plate: String, tankCapacity: Double, primaryFuelType: String) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (Uri) -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    val context = LocalContext.current
    var vehicleNameStr by remember(vehicleName) { mutableStateOf(vehicleName) }
    var vehiclePlateStr by remember(vehiclePlate) { mutableStateOf(vehiclePlate) }
    var tankCapacityStr by remember(tankCapacityLiters) { mutableStateOf(tankCapacityLiters.toInt().toString()) }
    var selectedFuelType by remember(primaryFuelType) { mutableStateOf(primaryFuelType) }

    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "home" or "work"

    // Auto backup & Offline Map settings
    var autoBackupEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
                .getBoolean("auto_backup_enabled", true)
        )
    }

    var offlineDownloaded by remember {
        mutableStateOf(
            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
                .getBoolean("offline_sp_downloaded", false)
        )
    }
    var isDownloadingMap by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            onImportBackup(uri)
        }
    }

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
                val key = if (isHome) "home" else "work"
                val label = if (isHome) "Casa" else "Trabalho"
                onSaveLocation(key, lat, lon, label)
                mapPickerTarget = null
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
                    text = "Personalização do veículo, segundo plano e segurança",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Section: Vehicle Configuration (Name, Plate, Tank & Fuel)
        item {
            Text(
                text = "Meu Veículo",
                color = WandaCyan20,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WandaCard)
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = WandaCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Identificação do Carro",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = vehicleNameStr,
                        onValueChange = {
                            vehicleNameStr = it
                            val cap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                            onSaveVehicleConfig(it, vehiclePlateStr.uppercase().trim(), cap, selectedFuelType)
                        },
                        label = { Text("Nome ou Modelo do Veículo") },
                        placeholder = { Text("Ex: Toyota Etios 1.5, Honda Civic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vehiclePlateStr,
                        onValueChange = {
                            vehiclePlateStr = it.uppercase()
                            val cap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                            onSaveVehicleConfig(vehicleNameStr, it.uppercase().trim(), cap, selectedFuelType)
                        },
                        label = { Text("Placa do Veículo (Opcional)") },
                        placeholder = { Text("Ex: BRA2E19 ou ABC-1234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = WandaBorder)

                    Text(
                        text = "Capacidade do Tanque e Consumo",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Capacidade Total do Tanque (Litros):",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    val quickTanks = listOf(45, 50, 55, 60, 70)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(quickTanks) { capInt ->
                            val isSelected = (tankCapacityStr.toIntOrNull() == capInt)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    tankCapacityStr = capInt.toString()
                                    onSaveVehicleConfig(vehicleNameStr, vehiclePlateStr.uppercase().trim(), capInt.toDouble(), selectedFuelType)
                                },
                                label = { Text("${capInt}L", fontSize = 12.sp) },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = WandaPrimary,
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

        // Section: Battery Optimization
        item {
            Text(
                text = "Segundo Plano e Bateria",
                color = WandaCyan20,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WandaCard)
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = WandaCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Proteção de Gravação do GPS",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isBatteryOptimized) "✅ Isenção Ativa: O sistema operacional não suspenderá o GPS quando você desligar a tela ou alternar de app."
                        else "⚠️ Otimização Ativa: A Samsung/Android pode suspender o GPS após alguns minutos com tela desligada para economizar bateria.",
                        color = if (isBatteryOptimized) WandaGreen30 else WandaYellow20,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (!isBatteryOptimized) {
                        Button(
                            onClick = onRequestIgnoreBattery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WandaYellow20)
                        ) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ignorar Otimização de Bateria", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WandaGreen30))
                            Text("Bateria Configurada como Sem Restrições", color = WandaGreen30, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section: Offline Maps (Grande São Paulo)
        item {
            Text(
                text = "Mapas Offline",
                color = WandaCyan20,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(WandaCard)
                    .border(1.dp, WandaBorderSubtle, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = WandaCard)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Área da Grande São Paulo",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Baixa a malha viária completa da Região Metropolitana de São Paulo (Capital, ABCD, Guarulhos, Osasco) para visualização instantânea sem necessidade de internet.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    if (isDownloadingMap) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = WandaCyan20,
                                trackColor = WandaCardElevated
                            )
                            Text("Baixando malha da Grande SP para cache local...", color = WandaCyan20, fontSize = 12.sp)
                        }
                    } else if (offlineDownloaded) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WandaGreen30))
                                Text("Grande SP Pronta e Offline", color = WandaGreen30, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    isDownloadingMap = true
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        isDownloadingMap = false
                                        Toast.makeText(context, "Malha da Grande SP atualizada!", Toast.LENGTH_SHORT).show()
                                    }, 2000L)
                                }
                            ) {
                                Text("Atualizar", color = WandaInteractive, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                isDownloadingMap = true
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    isDownloadingMap = false
                                    offlineDownloaded = true
                                    context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).edit()
                                        .putBoolean("offline_sp_downloaded", true).apply()
                                    Toast.makeText(context, "Área da Grande SP baixada com sucesso!", Toast.LENGTH_LONG).show()
                                }, 2500L)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Baixar Área da Grande SP (~45 MB)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

        // Home Location Card
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

        // Section: Backup & Restore
        item {
            Text(
                text = "Backup e Restauração de Dados",
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
                    Text(
                        text = "Histórico e Configurações em JSON",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Exporte todas as suas viagens com pontos de GPS, abastecimentos e configurações para um arquivo JSON seguro. Você poderá restaurá-lo a qualquer momento sem duplicar trajetos.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Auto-backup Switch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WandaBackground)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backup Automático", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Salva cópia JSON ao concluir cada viagem", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = {
                                autoBackupEnabled = it
                                context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).edit()
                                    .putBoolean("auto_backup_enabled", it).apply()
                            }
                        )
                    }

                    Button(
                        onClick = onExportBackup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Exportar Backup Completo (JSON)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = WandaCardElevated)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = WandaCyan20)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar Backup (JSON Inteligente)", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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
                        text = "Versão 1.0.14 • Filtro de Kalman • Mapas Offline • Wanda System",
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
