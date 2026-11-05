package de.dennisguse.opentracks.ui.screens

import de.dennisguse.opentracks.ui.components.ConsumptionSettingsSection
import de.dennisguse.opentracks.util.FuelConsumptionModel
import de.dennisguse.opentracks.util.FuelFamily

import androidx.compose.ui.graphics.RectangleShape
import android.content.Context
import android.content.Intent
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
    currentOdometer: Double = 0.0,
    estimatedKmL: Double = 12.0,
    kmSinceLastFull: Double = 0.0,
    isBatteryOptimized: Boolean = false,
    onRequestIgnoreBattery: () -> Unit = {},
    onSaveLocation: (key: String, lat: Double, lon: Double, label: String) -> Unit,
    onSaveVehicleConfig: (name: String, plate: String, tankCapacity: Double, primaryFuelType: String, odometer: Double, estimatedKmL: Double, kmSinceLastFull: Double) -> Unit,
    onPurgeDemoData: () -> Unit = {},
    consumption: FuelConsumptionModel? = null,
    onBackupToDrive: () -> Unit = {},
    onSaveConsumption: (Map<FuelFamily, Double>, Boolean) -> Unit = { _, _ -> },
    onExportBackup: () -> Unit,
    onImportBackup: (Uri) -> Unit
) {
    val context = LocalContext.current
    var vehicleNameStr by remember(vehicleName) { mutableStateOf(vehicleName) }
    var vehiclePlateStr by remember(vehiclePlate) { mutableStateOf(vehiclePlate) }
    var tankCapacityStr by remember(tankCapacityLiters) { mutableStateOf(tankCapacityLiters.toInt().toString()) }
    var selectedFuelType by remember(primaryFuelType) { mutableStateOf(primaryFuelType) }
    var odometerStr by remember(currentOdometer) { mutableStateOf(if (currentOdometer > 0) String.format(java.util.Locale.US, "%.0f", currentOdometer) else "") }
    var estimatedKmLStr by remember(estimatedKmL) { mutableStateOf(String.format(java.util.Locale.US, "%.1f", estimatedKmL)) }
    var kmSinceLastFullStr by remember(kmSinceLastFull) { mutableStateOf(if (kmSinceLastFull > 0) String.format(java.util.Locale.US, "%.0f", kmSinceLastFull) else "0") }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "home" or "work"

    // Auto backup, Smart Pause & Map Matching
    var smartPauseEnabled by remember {
        mutableStateOf(context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getBoolean("auto_stop_parked_enabled", true))
    }
    var autoMapMatchEnabled by remember {
        mutableStateOf(context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getBoolean("auto_map_match_enabled", true))
    }
    var autoBackupEnabled by remember {
        mutableStateOf(
            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
                .getBoolean("auto_backup_enabled", true)
        )
    }


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
            .background(CyberInk)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "AJUSTES",
                    style = displayStyle(40),
                    color = CyberAcid
                )
                Text(
                    text = "// " + "Personalização do veículo, segundo plano e segurança".uppercase(),
                    style = EyebrowStyle,
                    color = TextSecondary
                )
            }
        }

        // Section: Vehicle Configuration (Name, Plate, Tank & Fuel)
        item {
            CyberSectionHeader(title = "Meu Veículo")
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel)
                    .border(1.dp, CyberLineSubtle, RectangleShape),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
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
                        onValueChange = { vehicleNameStr = it },
                        label = { Text("Nome ou Modelo do Veículo") },
                        placeholder = { Text("Ex: Toyota Etios 1.5, Honda Civic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = vehiclePlateStr,
                        onValueChange = { vehiclePlateStr = it.uppercase() },
                        label = { Text("Placa do Veículo (Opcional)") },
                        placeholder = { Text("Ex: BRA2E19 ou ABC-1234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 1. Odômetro Real
                    OutlinedTextField(
                        value = odometerStr,
                        onValueChange = { odometerStr = it },
                        label = { Text("Odômetro Real do Painel (km)") },
                        placeholder = { Text("Ex: 85200") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = CyberLine)

                    Text(
                        text = "Capacidade do Tanque e Autonomia",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = tankCapacityStr,
                        onValueChange = { tankCapacityStr = it },
                        label = { Text("Capacidade Total do Tanque (Litros)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = estimatedKmLStr,
                        onValueChange = { estimatedKmLStr = it },
                        label = { Text("Consumo Médio Estimado (km/L)") },
                        placeholder = { Text("Ex: 11.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = kmSinceLastFullStr,
                        onValueChange = { kmSinceLastFullStr = it },
                        label = { Text("Km rodados desde o último tanque cheio") },
                        placeholder = { Text("Ex: 60 (ou 0 se encheu agora)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Autonomy Calculation Preview
                    val calcCap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                    val calcKmL = estimatedKmLStr.toDoubleOrNull() ?: 12.0
                    val calcKmSince = kmSinceLastFullStr.toDoubleOrNull() ?: 0.0
                    val calcLitersUsed = if (calcKmL > 0) calcKmSince / calcKmL else 0.0
                    val calcLitersLeft = (calcCap - calcLitersUsed).coerceIn(0.0, calcCap)
                    val calcRange = (calcLitersLeft * calcKmL).toInt()
                    val calcPercent = if (calcCap > 0) (calcLitersLeft / calcCap * 100).toInt() else 0

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RectangleShape)
                            .background(CyberPanel)
                            .border(1.dp, CyberLine, RectangleShape)
                            .padding(12.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Estimativa no Tanque:", fontSize = 11.sp, color = TextMuted)
                                Text(String.format(java.util.Locale.getDefault(), "%.1f L (%d%%)", calcLitersLeft, calcPercent), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Autonomia Estimada:", fontSize = 11.sp, color = TextMuted)
                                Text("~$calcRange km", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CyberGreen)
                            }
                        }
                    }

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
                                onClick = { selectedFuelType = fType },
                                label = { Text(fType, fontSize = 12.sp) },
                                shape = RectangleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberAcid,
                                    selectedLabelColor = CyberOnAcid,
                                selectedLeadingIconColor = CyberOnAcid,
                                    containerColor = CyberPanelRaised,
                                    labelColor = TextSecondary
                                ),
                                border = null
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val cap = tankCapacityStr.toDoubleOrNull() ?: 50.0
                            val odo = odometerStr.toDoubleOrNull() ?: currentOdometer
                            val kml = estimatedKmLStr.toDoubleOrNull() ?: 12.0
                            val kmSince = kmSinceLastFullStr.toDoubleOrNull() ?: 0.0
                            onSaveVehicleConfig(
                                vehicleNameStr.trim(),
                                vehiclePlateStr.uppercase().trim(),
                                cap,
                                selectedFuelType,
                                odo,
                                kml,
                                kmSince
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
                    ) {
                        Text("Salvar Dados do Veículo e Odômetro", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Battery Optimization
        if (consumption != null) {
            item {
                CyberSectionHeader(title = "Consumo médio", eyebrow = "km/L por combustível")
            }
            item {
                ConsumptionSettingsSection(
                    model = consumption,
                    manualValues = consumption.manualKmL,
                    onSave = onSaveConsumption
                )
            }
        }

        item {
            CyberSectionHeader(title = "Segundo Plano e Bateria")
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel)
                    .border(1.dp, CyberLineSubtle, RectangleShape),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
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
                        color = if (isBatteryOptimized) CyberGreen else CyberOrange,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (!isBatteryOptimized) {
                        Button(
                            onClick = onRequestIgnoreBattery,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberOrange)
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
                            Box(modifier = Modifier.size(8.dp).clip(RectangleShape).background(CyberGreen))
                            Text("Bateria Configurada como Sem Restrições", color = CyberGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


        // Section: Favorite Locations
        item {
            CyberSectionHeader(title = "Locais Favoritos (Detecção Inteligente)")
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
            CyberSectionHeader(title = "Backup no Google Drive", eyebrow = "Nuvem")
        }
        item {
            val lastDrive = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getLong("last_drive_backup_ms", 0L)
            Column(
                Modifier
                    .fillMaxWidth()
                    .cyberPanel()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "1. Backup automático do Android: viagens, abastecimentos, manutenção e ajustes entram no backup do Google (Drive / Google One) do aparelho, feito pelo sistema com Wi-Fi e carregador. Confira se está ativo:",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                )
                CyberGhostButton(
                    text = "Abrir backup do Google",
                    icon = Icons.Default.CloudSync,
                    accent = CyberCyan,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intents = listOf(
                            Intent().setClassName("com.google.android.gms", "com.google.android.gms.backup.component.BackupSettingsActivity"),
                            Intent("android.settings.PRIVACY_SETTINGS"),
                            Intent(android.provider.Settings.ACTION_SETTINGS)
                        )
                        intents.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                    }
                )
                Text(
                    "2. Cópia manual: salva o arquivo JSON completo direto numa pasta do Google Drive (escolha \"Drive\" no seletor). Serve para restaurar em outro celular pelo botão Importar abaixo.",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp
                )
                CyberActionButton(
                    text = "Salvar cópia no Drive",
                    icon = Icons.Default.CloudUpload,
                    height = 48.dp,
                    onClick = onBackupToDrive
                )
                if (lastDrive > 0) {
                    Text(
                        "ÚLTIMA CÓPIA: " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastDrive)),
                        style = EyebrowStyle, color = CyberGreen
                    )
                }
            }
        }

        item {
            CyberSectionHeader(title = "Backup e Restauração de Dados")
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel)
                    .border(1.dp, CyberLineSubtle, RectangleShape)
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
                            .clip(RectangleShape)
                            .background(CyberInk)
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
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
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
                        shape = RectangleShape,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = CyberPanelRaised)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp), tint = CyberCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Importar Backup (JSON Inteligente)", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Section: Limpeza de Dados de Demonstração
        item {
            CyberSectionHeader(title = "Manutenção e Limpeza")
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel)
                    .border(1.dp, CyberLineSubtle, RectangleShape),
                colors = CardDefaults.cardColors(containerColor = CyberPanel)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Dados Falsos / Demonstração",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Se ainda houver viagens fictícias de teste ou postos falsos gerados anteriormente, você pode apagá-los para começar do zero.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    OutlinedButton(
                        onClick = { showPurgeConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberRed),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apagar Viagens e Postos de Demonstração", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Section: Automação Inteligente de Gravação
        item {
            CyberSectionHeader(title = "Automação e Inteligência Veicular")
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel)
                    .border(1.dp, CyberLineSubtle, RectangleShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Encerramento automático ao estacionar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Encerrar ao Estacionar",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Encerra a viagem se o carro ficar 10 min sem sair de um raio de 200 m. Trânsito lento continua gravando.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = smartPauseEnabled,
                        onCheckedChange = {
                            smartPauseEnabled = it
                            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("auto_stop_parked_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberGreen,
                            checkedTrackColor = CyberGreen.copy(alpha = 0.3f)
                        )
                    )
                }

                Divider(color = CyberLineSubtle)

                // Map Matching pós-viagem automático
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Map Matching Automático",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Ajusta o traçado às vias oficiais ao concluir o percurso",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = autoMapMatchEnabled,
                        onCheckedChange = {
                            autoMapMatchEnabled = it
                            context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("auto_map_match_enabled", it).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberCyan,
                            checkedTrackColor = CyberCyan.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        // App Info
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanel)
                    .border(1.dp, CyberLineSubtle, RectangleShape)
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
                        text = "Versão 1.0.15 • Odômetro Real • Autonomia Estimada • Wanda System",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }

    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = {
                Text("Limpar Dados de Demonstração", fontWeight = FontWeight.Bold, color = CyberRed)
            },
            text = {
                Text("Isso removerá todas as viagens e abastecimentos simulados. Deseja continuar?", color = TextPrimary)
            },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    onClick = {
                        showPurgeConfirm = false
                        onPurgeDemoData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Limpar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = { showPurgeConfirm = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = CyberPanelRaised
        )
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
            .clip(RectangleShape)
            .background(CyberPanel)
            .border(1.dp, CyberLineSubtle, RectangleShape)
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
                        .clip(RectangleShape)
                        .background(if (isConfigured) CyberGreen.copy(alpha = 0.15f) else CyberOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isConfigured) "🟢 Configurado" else "⚪ Não Definido",
                        color = if (isConfigured) CyberGreen else CyberOrange,
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
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
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
