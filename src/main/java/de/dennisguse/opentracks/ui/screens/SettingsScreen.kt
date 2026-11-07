package de.dennisguse.opentracks.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.BuildConfig
import de.dennisguse.opentracks.ui.components.ConsumptionSettingsSection
import de.dennisguse.opentracks.ui.components.LocationPickerMapDialog
import de.dennisguse.opentracks.ui.model.CustomLocation
import de.dennisguse.opentracks.ui.model.FavoritePlace
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.CarBluetooth
import de.dennisguse.opentracks.util.FavoritePlaces
import de.dennisguse.opentracks.util.FuelConsumptionModel
import de.dennisguse.opentracks.util.FuelFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
    favorites: List<FavoritePlace> = emptyList(),
    onSaveFavorite: (FavoritePlace) -> Unit = {},
    onDeleteFavorite: (String) -> Unit = {},
    onSaveVehicleConfig: (name: String, plate: String, tankCapacity: Double, primaryFuelType: String, odometer: Double, estimatedKmL: Double, kmSinceLastFull: Double) -> Unit,
    onPurgeDemoData: () -> Unit = {},
    consumption: FuelConsumptionModel? = null,
    onBackupToDrive: () -> Unit = {},
    onSaveConsumption: (Map<FuelFamily, Double>, Boolean) -> Unit = { _, _ -> },
    onExportBackup: () -> Unit,
    onImportBackup: (Uri) -> Unit,
    reminderEnabled: Boolean = true,
    reminderItems: List<String> = listOf("Celular", "Carteira", "Porta-malas"),
    onSaveReminder: (enabled: Boolean, items: List<String>) -> Unit = { _, _ -> },
    weeklySummaryEnabled: Boolean = true,
    onWeeklySummaryChange: (Boolean) -> Unit = {},
    onTestWeeklySummary: () -> Unit = {},
    hiddenInsightsCount: Int = 0,
    onRestoreInsights: () -> Unit = {}
) {
    val context = LocalContext.current

    // Dialog & picker states
    var showVehicleDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }
    var mapPickerTarget by remember { mutableStateOf<String?>(null) } // "home", "work" or "fav"
    var favDraft by remember { mutableStateOf<FavoritePlace?>(null) }
    var showFavDialog by remember { mutableStateOf(false) }
    var favToDelete by remember { mutableStateOf<FavoritePlace?>(null) }

    // Bluetooth state
    var carName by remember {
        mutableStateOf(CarBluetooth.address(context)?.let { CarBluetooth.name(context) })
    }
    var devices by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            devices = CarBluetooth.pairedDevices(context)
        } else {
            Toast.makeText(context, "Sem a permissão de Bluetooth o app não reconhece o som do carro.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto backup, Smart Pause & Map Matching (SharedPreferences)
    var smartPauseEnabled by remember {
        mutableStateOf(context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getBoolean("auto_stop_parked_enabled", true))
    }
    var autoMapMatchEnabled by remember {
        mutableStateOf(context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getBoolean("auto_map_match_enabled", true))
    }
    var autoBackupEnabled by remember {
        mutableStateOf(context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getBoolean("auto_backup_enabled", true))
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            onImportBackup(uri)
        }
    }

    // --- Modal Dialogs ---

    // 1. Map Location Picker (Home, Work, or Favorite spot)
    val currentDraft = favDraft
    if (mapPickerTarget == "fav" && currentDraft != null) {
        LocationPickerMapDialog(
            title = "${currentDraft.emoji} ${currentDraft.name}",
            initialLat = currentDraft.lat,
            initialLon = currentDraft.lon,
            currentGps = currentGps,
            onConfirm = { lat, lon ->
                onSaveFavorite(currentDraft.copy(lat = lat, lon = lon))
                mapPickerTarget = null
                favDraft = null
            },
            onDismiss = {
                mapPickerTarget = null
                favDraft = null
            }
        )
    } else if (mapPickerTarget != null) {
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

    // 2. Favorite Place Name & Emoji Dialog
    if (showFavDialog && currentDraft != null) {
        var name by remember(currentDraft.id) { mutableStateOf(currentDraft.name) }
        var emoji by remember(currentDraft.id) { mutableStateOf(currentDraft.emoji) }
        AlertDialog(
            onDismissRequest = {
                showFavDialog = false
                favDraft = null
            },
            containerColor = CyberPanelRaised,
            shape = RectangleShape,
            title = {
                Text(
                    text = if (favorites.any { it.id == currentDraft.id }) "Editar Local Favorito" else "Novo Local Favorito",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("Nome (ex: Academia, Faculdade)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Escolha um ícone:", color = TextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(FavoritePlaces.EMOJIS) { e ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (e == emoji) CyberAcid else CyberPanel)
                                    .border(1.dp, if (e == emoji) CyberAcid else CyberLine)
                                    .clickable { emoji = e },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(e, fontSize = 20.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAcid, contentColor = CyberOnAcid),
                    onClick = {
                        favDraft = currentDraft.copy(name = name.trim(), emoji = emoji)
                        showFavDialog = false
                        mapPickerTarget = "fav"
                    }
                ) {
                    Text("Escolher no mapa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    shape = RectangleShape,
                    onClick = {
                        showFavDialog = false
                        favDraft = null
                    }
                ) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // 3. Confirm Delete Favorite
    favToDelete?.let { place ->
        AlertDialog(
            onDismissRequest = { favToDelete = null },
            containerColor = CyberPanelRaised,
            shape = RectangleShape,
            title = { Text("Remover ${place.emoji} ${place.name}?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("As viagens continuam salvas; apenas deixam de receber esta etiqueta automática.", color = TextSecondary) },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                    onClick = {
                        onDeleteFavorite(place.id)
                        favToDelete = null
                    }
                ) {
                    Text("Remover", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = { favToDelete = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // 4. Bluetooth Device Picker Dialog
    devices?.let { list ->
        AlertDialog(
            onDismissRequest = { devices = null },
            containerColor = CyberPanelRaised,
            shape = RectangleShape,
            title = { Text("Selecione o Bluetooth do Carro", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                if (list.isEmpty()) {
                    Text("Nenhum dispositivo pareado encontrado. Pareie o som do carro nas configurações de Bluetooth do celular.", color = TextSecondary)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 340.dp)) {
                        items(list) { (address, name) ->
                            Text(
                                text = name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CyberPanel)
                                    .border(1.dp, CyberLine)
                                    .clickable {
                                        CarBluetooth.save(context, address, name)
                                        carName = name
                                        devices = null
                                    }
                                    .padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(shape = RectangleShape, onClick = { devices = null }) {
                    Text("Fechar", color = TextSecondary)
                }
            }
        )
    }

    // 5. Purge Demo Data Confirmation Dialog
    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            containerColor = CyberPanelRaised,
            shape = RectangleShape,
            title = { Text("Limpar Dados de Demonstração", fontWeight = FontWeight.Bold, color = CyberRed) },
            text = { Text("Isso removerá todas as viagens simuladas e abastecimentos fictícios criados para teste. Viagens reais gravadas por você serão mantidas.", color = TextPrimary) },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                    onClick = {
                        showPurgeConfirm = false
                        onPurgeDemoData()
                    }
                ) {
                    Text("Limpar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = { showPurgeConfirm = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // 6. Vehicle Edit Dialog
    if (showVehicleDialog) {
        VehicleEditDialog(
            initialName = vehicleName,
            initialPlate = vehiclePlate,
            initialTankCapacity = tankCapacityLiters,
            initialFuelType = primaryFuelType,
            initialOdometer = currentOdometer,
            initialEstimatedKmL = estimatedKmL,
            initialKmSinceLastFull = kmSinceLastFull,
            onConfirm = { name, plate, cap, fuel, odo, kml, kmSince ->
                onSaveVehicleConfig(name, plate, cap, fuel, odo, kml, kmSince)
                showVehicleDialog = false
            },
            onDismiss = { showVehicleDialog = false }
        )
    }

    // 7. Checklist Reminder Dialog ("Ao desligar o carro")
    if (showReminderDialog) {
        ReminderChecklistDialog(
            initialItems = reminderItems,
            onConfirm = { updatedItems ->
                onSaveReminder(reminderEnabled, updatedItems)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    // --- Main Screen Content ---
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
                    text = "// " + "Personalização, automação e segurança".uppercase(),
                    style = EyebrowStyle,
                    color = TextSecondary
                )
            }
        }

        // ==========================================
        // 1. Veículo
        // ==========================================
        item {
            CyberSectionHeader(title = "Veículo", eyebrow = "Configuração do carro")
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = CyberLineSubtle, shadow = true),
                colors = CardDefaults.cardColors(containerColor = CyberPanel),
                shape = RectangleShape
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(CyberAcid.copy(alpha = 0.15f), RectangleShape)
                                    .border(1.dp, CyberAcid, RectangleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = CyberAcid,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = vehicleName.ifBlank { "Meu Carro" }.uppercase(),
                                    style = displayStyle(22),
                                    color = CyberText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (vehiclePlate.isNotBlank()) "PLACA: ${vehiclePlate.uppercase()}" else "SEM PLACA DEFINIDA",
                                    style = EyebrowStyle.copy(fontSize = 10.sp),
                                    color = if (vehiclePlate.isNotBlank()) CyberCyan else TextTertiary
                                )
                            }
                        }
                        CyberGhostButton(
                            text = "Editar",
                            icon = Icons.Default.Edit,
                            accent = CyberAcid,
                            height = 36.dp,
                            onClick = { showVehicleDialog = true }
                        )
                    }

                    HorizontalDivider(color = CyberLineSubtle)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsTelemetryBadge(
                            label = "Odômetro",
                            value = if (currentOdometer > 0) String.format(Locale("pt", "BR"), "%,.0f km", currentOdometer) else "—",
                            accent = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsTelemetryBadge(
                            label = "Tanque",
                            value = "${tankCapacityLiters.toInt()} L",
                            accent = CyberOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SettingsTelemetryBadge(
                            label = "Combustível",
                            value = primaryFuelType,
                            accent = CyberPink,
                            modifier = Modifier.weight(1f)
                        )
                        SettingsTelemetryBadge(
                            label = "Consumo em uso",
                            value = String.format(Locale("pt", "BR"), "%.1f km/L", consumption?.let { it.baseKmL(it.currentFamily()) } ?: estimatedKmL),
                            accent = CyberAcid,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. Consumo
        // ==========================================
        if (consumption != null) {
            item {
                CyberSectionHeader(title = "Consumo", eyebrow = "km/L por combustível")
            }
            item {
                ConsumptionSettingsSection(
                    model = consumption,
                    manualValues = consumption.manualKmL,
                    onSave = onSaveConsumption
                )
            }
        }

        // ==========================================
        // 3. Lugares
        // ==========================================
        item {
            CyberSectionHeader(title = "Lugares", eyebrow = "Casa, trabalho e favoritos")
        }

        item {
            SettingsGroup {
                // Casa
                SettingsRow(
                    title = "Casa",
                    secondary = if (homeLoc.isSet) {
                        String.format(Locale.US, "Definido · Lat: %.4f, Lon: %.4f", homeLoc.lat, homeLoc.lon)
                    } else {
                        "Não definido · Toque para escolher no mapa"
                    },
                    iconEmoji = "🏠",
                    onClick = { mapPickerTarget = "home" },
                    trailing = {
                        CyberChip(
                            text = if (homeLoc.isSet) "Definido" else "Pendente",
                            color = if (homeLoc.isSet) CyberGreen else CyberOrange
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Trabalho
                SettingsRow(
                    title = "Trabalho",
                    secondary = if (workLoc.isSet) {
                        String.format(Locale.US, "Definido · Lat: %.4f, Lon: %.4f", workLoc.lat, workLoc.lon)
                    } else {
                        "Não definido · Toque para escolher no mapa"
                    },
                    iconEmoji = "🏢",
                    onClick = { mapPickerTarget = "work" },
                    trailing = {
                        CyberChip(
                            text = if (workLoc.isSet) "Definido" else "Pendente",
                            color = if (workLoc.isSet) CyberGreen else CyberOrange
                        )
                    }
                )

                // Favoritos cadastrados
                favorites.forEach { place ->
                    HorizontalDivider(color = CyberLineSubtle)
                    SettingsRow(
                        title = place.name,
                        secondary = String.format(Locale.US, "Lat: %.4f, Lon: %.4f", place.lat, place.lon),
                        iconEmoji = place.emoji,
                        onClick = {
                            favDraft = place
                            mapPickerTarget = "fav"
                        },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    onClick = {
                                        favDraft = place
                                        showFavDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    onClick = { favToDelete = place }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remover", tint = CyberRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(color = CyberLineSubtle)

                // Adicionar favorito
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val (lat, lon) = currentGps ?: (if (homeLoc.isSet) homeLoc.lat to homeLoc.lon else -23.5505 to -46.6333)
                            favDraft = FavoritePlace(UUID.randomUUID().toString(), "", "⭐", lat, lon)
                            showFavDialog = true
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = CyberAcid, modifier = Modifier.size(18.dp))
                        Text("ADICIONAR LOCAL FAVORITO", style = EyebrowStyle.copy(fontSize = 11.sp), color = CyberAcid)
                    }
                }
            }
        }

        // ==========================================
        // 4. Carro e automação
        // ==========================================
        item {
            CyberSectionHeader(title = "Carro e automação", eyebrow = "Bluetooth, sensores e segundo plano")
        }

        item {
            val hasCarBt = carName != null
            SettingsGroup {
                // Bluetooth do carro
                SettingsRow(
                    title = "Bluetooth do carro",
                    secondary = if (hasCarBt) {
                        "Conectado a $carName · Gravação automática"
                    } else {
                        "Nenhum som selecionado · Inicia ao conectar"
                    },
                    icon = Icons.Default.Bluetooth,
                    iconTint = if (hasCarBt) CyberGreen else CyberCyan,
                    onClick = {
                        if (CarBluetooth.hasPermission(context)) devices = CarBluetooth.pairedDevices(context)
                        else permissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                    },
                    trailing = {
                        if (hasCarBt) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CyberGhostButton(
                                    text = "Trocar",
                                    accent = CyberCyan,
                                    height = 30.dp,
                                    onClick = {
                                        if (CarBluetooth.hasPermission(context)) devices = CarBluetooth.pairedDevices(context)
                                        else permissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                                    }
                                )
                                IconButton(
                                    modifier = Modifier.size(30.dp),
                                    onClick = {
                                        CarBluetooth.save(context, null, null)
                                        carName = null
                                    }
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Desativar", tint = CyberRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        } else {
                            CyberGhostButton(
                                text = "Escolher",
                                accent = CyberAcid,
                                height = 30.dp,
                                onClick = {
                                    if (CarBluetooth.hasPermission(context)) devices = CarBluetooth.pairedDevices(context)
                                    else permissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            )
                        }
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Segundo plano e bateria
                SettingsRow(
                    title = "Segundo plano",
                    secondary = if (isBatteryOptimized) {
                        "Sem restrições · GPS protegido com tela desligada"
                    } else {
                        "Restrito · Toque para isentar economia de bateria"
                    },
                    icon = if (isBatteryOptimized) Icons.Default.BatteryChargingFull else Icons.Default.BatteryAlert,
                    iconTint = if (isBatteryOptimized) CyberGreen else CyberOrange,
                    onClick = if (!isBatteryOptimized) onRequestIgnoreBattery else null,
                    trailing = {
                        if (!isBatteryOptimized) {
                            CyberGhostButton(
                                text = "Ajustar",
                                accent = CyberOrange,
                                height = 30.dp,
                                onClick = onRequestIgnoreBattery
                            )
                        } else {
                            CyberChip("Isenção ativa", CyberGreen)
                        }
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // NEW: "Ao desligar o carro"
                SettingsRow(
                    title = "Ao desligar o carro",
                    secondary = if (hasCarBt) {
                        formatReminderSecondary(reminderItems)
                    } else {
                        "${formatReminderSecondary(reminderItems)} · Requer Bluetooth do carro"
                    },
                    icon = Icons.Default.PlaylistAddCheck,
                    iconTint = if (hasCarBt) CyberAcid else TextTertiary,
                    modifier = if (!hasCarBt) Modifier.alpha(0.6f) else Modifier,
                    onClick = { showReminderDialog = true },
                    trailing = {
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { onSaveReminder(it, reminderItems) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAcid,
                                checkedTrackColor = CyberAcid.copy(alpha = 0.3f)
                            )
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Encerrar ao estacionar
                SettingsRow(
                    title = "Encerrar ao estacionar",
                    secondary = "Encerra viagem se ficar 10 min parado num raio de 200m",
                    icon = Icons.Default.LocalParking,
                    iconTint = CyberCyan,
                    trailing = {
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
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Map matching automático
                SettingsRow(
                    title = "Map matching automático",
                    secondary = "Ajusta o traçado às vias oficiais ao concluir percurso",
                    icon = Icons.Default.AltRoute,
                    iconTint = CyberMagenta,
                    trailing = {
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
                )
            }
        }

        // ==========================================
        // 5. Notificações
        // ==========================================
        item {
            CyberSectionHeader(title = "Notificações", eyebrow = "Alertas e relatórios")
        }

        item {
            SettingsGroup {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(CyberCyan.copy(alpha = 0.12f), RectangleShape)
                                .border(1.dp, CyberCyan.copy(alpha = 0.35f), RectangleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Resumo semanal",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Domingo às 20h · km, horas, gasto, km/L e a viagem mais longa",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = weeklySummaryEnabled,
                            onCheckedChange = onWeeklySummaryChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberAcid,
                                checkedTrackColor = CyberAcid.copy(alpha = 0.3f)
                            )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    CyberGhostButton(
                        text = "Ver exemplo agora",
                        icon = Icons.Default.Visibility,
                        accent = CyberCyan,
                        height = 32.dp,
                        modifier = Modifier.padding(start = 48.dp),
                        onClick = onTestWeeklySummary
                    )
                    if (hiddenInsightsCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        CyberGhostButton(
                            text = if (hiddenInsightsCount == 1) "Mostrar 1 insight oculto" else "Mostrar $hiddenInsightsCount insights ocultos",
                            icon = Icons.Default.Visibility,
                            accent = CyberAcid,
                            height = 32.dp,
                            modifier = Modifier.padding(start = 48.dp),
                            onClick = onRestoreInsights
                        )
                    }
                }
            }
        }

        // ==========================================
        // 6. Dados e backup
        // ==========================================
        item {
            CyberSectionHeader(title = "Dados e backup", eyebrow = "Nuvem e exportação")
        }

        item {
            val lastDrive = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).getLong("last_drive_backup_ms", 0L)
            SettingsGroup {
                // Google Drive
                SettingsRow(
                    title = "Google Drive",
                    secondary = if (lastDrive > 0) {
                        "Última cópia: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(lastDrive))
                    } else {
                        "Salvar cópia completa das viagens na sua nuvem"
                    },
                    icon = Icons.Default.CloudSync,
                    iconTint = CyberCyan,
                    trailing = {
                        CyberGhostButton(
                            text = "Salvar",
                            icon = Icons.Default.CloudUpload,
                            accent = CyberCyan,
                            height = 30.dp,
                            onClick = onBackupToDrive
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Backup do Google (Android)
                SettingsRow(
                    title = "Backup do Android",
                    secondary = "Viagens e custos inclusos no backup Google da conta",
                    icon = Icons.Default.Cloud,
                    iconTint = CyberAcid,
                    trailing = {
                        CyberGhostButton(
                            text = "Abrir",
                            accent = CyberTextSecondary,
                            height = 30.dp,
                            onClick = {
                                val intents = listOf(
                                    Intent().setClassName("com.google.android.gms", "com.google.android.gms.backup.component.BackupSettingsActivity"),
                                    Intent("android.settings.PRIVACY_SETTINGS"),
                                    Intent(android.provider.Settings.ACTION_SETTINGS)
                                )
                                intents.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                            }
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Backup automático pós-viagem
                SettingsRow(
                    title = "Backup pós-viagem",
                    secondary = "Salva arquivo JSON ao concluir cada percurso",
                    icon = Icons.Default.Save,
                    iconTint = CyberPink,
                    trailing = {
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = {
                                autoBackupEnabled = it
                                context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE).edit()
                                    .putBoolean("auto_backup_enabled", it).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberPink,
                                checkedTrackColor = CyberPink.copy(alpha = 0.3f)
                            )
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Exportar JSON
                SettingsRow(
                    title = "Exportar backup",
                    secondary = "Histórico completo com trajetos GPS e abastecimentos",
                    icon = Icons.Default.UploadFile,
                    iconTint = CyberMagenta,
                    trailing = {
                        CyberGhostButton(
                            text = "Exportar",
                            accent = CyberMagenta,
                            height = 30.dp,
                            onClick = onExportBackup
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Importar JSON
                SettingsRow(
                    title = "Importar backup",
                    secondary = "Restaura histórico de arquivo sem duplicar registros",
                    icon = Icons.Default.FileDownload,
                    iconTint = CyberAcid,
                    trailing = {
                        CyberGhostButton(
                            text = "Importar",
                            accent = CyberAcid,
                            height = 30.dp,
                            onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                        )
                    }
                )

                HorizontalDivider(color = CyberLineSubtle)

                // Limpar dados de demonstração (Muted danger style at bottom)
                SettingsRow(
                    title = "Dados de demonstração",
                    secondary = "Apagar viagens fictícias e postos simulados de teste",
                    icon = Icons.Default.DeleteSweep,
                    iconTint = CyberRed,
                    trailing = {
                        CyberGhostButton(
                            text = "Limpar",
                            accent = CyberRed,
                            height = 30.dp,
                            onClick = { showPurgeConfirm = true }
                        )
                    }
                )
            }
        }

        // ==========================================
        // 7. Sobre
        // ==========================================
        item {
            CyberSectionHeader(title = "Sobre", eyebrow = "Informações do sistema")
        }

        item {
            SettingsGroup {
                SettingsRow(
                    title = "CarTracks",
                    secondary = "Versão ${BuildConfig.VERSION_NAME_FULL.removePrefix("v")} (build ${BuildConfig.VERSION_CODE})",
                    icon = Icons.Default.Info,
                    iconTint = CyberAcid,
                    trailing = {
                        CyberChip("v${BuildConfig.VERSION_NAME_FULL.removePrefix("v")}", CyberAcid)
                    }
                )
            }
        }
    }
}

// --- Supporting Components ---

/** Container for grouped settings rows with cyberpunk borders. */
@Composable
private fun SettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberPanel(border = CyberLineSubtle, shadow = true),
        content = content
    )
}

/** Standard settings row with icon, title, single-line secondary text, and trailing control. */
@Composable
private fun SettingsRow(
    title: String,
    secondary: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconEmoji: String? = null,
    iconTint: Color = CyberAcid,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (enabled) iconTint.copy(alpha = 0.12f) else CyberPanelRaised, RectangleShape)
                    .border(1.dp, if (enabled) iconTint.copy(alpha = 0.35f) else CyberLineSubtle, RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) iconTint else TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        } else if (iconEmoji != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(CyberPanelRaised, RectangleShape)
                    .border(1.dp, CyberLineSubtle, RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(iconEmoji, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = secondary,
                color = if (enabled) TextSecondary else TextTertiary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Telemetry readout badge for vehicle summary card. */
@Composable
private fun SettingsTelemetryBadge(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(CyberPanelRaised, RectangleShape)
            .border(1.dp, CyberLineSubtle, RectangleShape)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = label.uppercase(),
                style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                color = TextSecondary,
                maxLines = 1
            )
            Text(
                text = value,
                style = monoStyle(13, FontWeight.SemiBold),
                color = CyberText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Formats the reminder items list into "Viagem salva · confira celular, carteira e porta-malas". */
private fun formatReminderSecondary(items: List<String>): String {
    val clean = items.map { it.trim().lowercase(Locale.getDefault()) }.filter { it.isNotBlank() }
    val joined = when (clean.size) {
        0 -> "nenhum item cadastrado"
        1 -> clean[0]
        2 -> "${clean[0]} e ${clean[1]}"
        else -> clean.dropLast(1).joinToString(", ") + " e " + clean.last()
    }
    return "Viagem salva · confira $joined"
}

/** Dialog for editing the checklist of items to check upon parking. */
@Composable
private fun ReminderChecklistDialog(
    initialItems: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var draftItems by remember { mutableStateOf(initialItems) }
    var newItemText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberPanelRaised,
        shape = RectangleShape,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, tint = CyberAcid)
                Text("Ao Desligar o Carro", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dispara uma notificação para não esquecer seus pertences toda vez que o som Bluetooth do carro desconectar e a viagem for encerrada.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )

                Text(
                    text = "ITENS DO LEMBRETE (${draftItems.size}/6)",
                    style = EyebrowStyle.copy(fontSize = 10.sp),
                    color = CyberAcid
                )

                if (draftItems.isEmpty()) {
                    Text(
                        text = "Nenhum item na lista. Adicione abaixo.",
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberPanel)
                            .border(1.dp, CyberLine, RectangleShape)
                    ) {
                        draftItems.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = CyberLineSubtle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(CyberAcid, RectangleShape)
                                    )
                                    Text(
                                        text = item,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    modifier = Modifier.size(28.dp),
                                    onClick = {
                                        draftItems = draftItems.toMutableList().also { it.removeAt(index) }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remover",
                                        tint = CyberRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (draftItems.size < 6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newItemText,
                            onValueChange = { newItemText = it.take(24) },
                            placeholder = { Text("Novo item (ex: Óculos)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.weight(1f)
                        )
                        CyberGhostButton(
                            text = "Adicionar",
                            accent = CyberAcid,
                            height = 54.dp,
                            onClick = {
                                val trimmed = newItemText.trim()
                                if (trimmed.isNotBlank() && draftItems.size < 6) {
                                    draftItems = draftItems + trimmed
                                    newItemText = ""
                                }
                            }
                        )
                    }
                } else {
                    Text(
                        text = "Limite máximo de 6 itens atingido.",
                        style = EyebrowStyle.copy(fontSize = 10.sp),
                        color = CyberOrange
                    )
                }
            }
        },
        confirmButton = {
            Button(
                shape = RectangleShape,
                onClick = { onConfirm(draftItems) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(shape = RectangleShape, onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

/** Dialog for configuring vehicle name, plate, tank, fuel and odometer. */
@Composable
private fun VehicleEditDialog(
    initialName: String,
    initialPlate: String,
    initialTankCapacity: Double,
    initialFuelType: String,
    initialOdometer: Double,
    initialEstimatedKmL: Double,
    initialKmSinceLastFull: Double,
    onConfirm: (
        name: String,
        plate: String,
        tankCapacity: Double,
        fuelType: String,
        odometer: Double,
        estimatedKmL: Double,
        kmSinceLastFull: Double
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var nameStr by remember { mutableStateOf(initialName) }
    var plateStr by remember { mutableStateOf(initialPlate) }
    var tankStr by remember { mutableStateOf(initialTankCapacity.toInt().toString()) }
    var fuelTypeStr by remember { mutableStateOf(initialFuelType) }
    var odoStr by remember {
        mutableStateOf(if (initialOdometer > 0) String.format(Locale.US, "%.0f", initialOdometer) else "")
    }
    var estKmLStr by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", initialEstimatedKmL))
    }
    var kmSinceStr by remember {
        mutableStateOf(if (initialKmSinceLastFull > 0) String.format(Locale.US, "%.0f", initialKmSinceLastFull) else "0")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberPanelRaised,
        shape = RectangleShape,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyberAcid)
                Text("Configuração do Veículo", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameStr,
                    onValueChange = { nameStr = it },
                    label = { Text("Nome ou Modelo do Veículo") },
                    placeholder = { Text("Ex: Toyota Etios 1.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = plateStr,
                    onValueChange = { plateStr = it.uppercase() },
                    label = { Text("Placa do Veículo (Opcional)") },
                    placeholder = { Text("Ex: BRA2E19") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = odoStr,
                    onValueChange = { odoStr = it },
                    label = { Text("Odômetro do Painel (km)") },
                    placeholder = { Text("Ex: 85200") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tankStr,
                    onValueChange = { tankStr = it },
                    label = { Text("Capacidade Total do Tanque (Litros)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = estKmLStr,
                    onValueChange = { estKmLStr = it.replace(',', '.') },
                    label = { Text("Consumo Médio Estimado (km/L)") },
                    placeholder = { Text("Ex: 12.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = kmSinceStr,
                    onValueChange = { kmSinceStr = it },
                    label = { Text("Km desde o último tanque cheio") },
                    placeholder = { Text("Ex: 0 se acabou de encher") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Autonomy live preview
                val calcCap = tankStr.toDoubleOrNull() ?: 50.0
                val calcKmL = estKmLStr.toDoubleOrNull() ?: 12.0
                val calcKmSince = kmSinceStr.toDoubleOrNull() ?: 0.0
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
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("No tanque:", fontSize = 10.sp, color = TextMuted)
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f L (%d%%)", calcLitersLeft, calcPercent),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Autonomia estimada:", fontSize = 10.sp, color = TextMuted)
                            Text(
                                text = "~$calcRange km",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberGreen
                            )
                        }
                    }
                }

                Text("Combustível Principal:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

                val fuelTypes = listOf("Gasolina Comum", "Gasolina Aditivada", "Etanol", "Diesel")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(fuelTypes) { fType ->
                        val isSelected = fType == fuelTypeStr
                        FilterChip(
                            selected = isSelected,
                            onClick = { fuelTypeStr = fType },
                            label = { Text(fType, fontSize = 11.sp) },
                            shape = RectangleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberAcid,
                                selectedLabelColor = CyberOnAcid,
                                containerColor = CyberPanel,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RectangleShape,
                onClick = {
                    val cap = tankStr.toDoubleOrNull() ?: 50.0
                    val odo = odoStr.toDoubleOrNull() ?: initialOdometer
                    val kml = estKmLStr.toDoubleOrNull() ?: 12.0
                    val kmSince = kmSinceStr.toDoubleOrNull() ?: 0.0
                    onConfirm(
                        nameStr.trim().ifBlank { "Meu Carro" },
                        plateStr.uppercase().trim(),
                        cap,
                        fuelTypeStr,
                        odo,
                        kml,
                        kmSince
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(shape = RectangleShape, onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

/** Legacy LocationCard retained for external compatibility. */
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
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(icon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RectangleShape)
                        .background(if (isConfigured) CyberGreen.copy(alpha = 0.15f) else CyberOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isConfigured) "🟢 Configurado" else "⚪ Não Definido",
                        color = if (isConfigured) CyberGreen else CyberOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (isConfigured && lat != null && lon != null) {
                    String.format(Locale.US, "Identifica viagens em $title (Lat: %.4f, Lon: %.4f).", lat, lon)
                } else {
                    "Identifica viagens com partida ou destino em $title."
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Button(
                onClick = onOpenMapPicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isConfigured) "Alterar Local no Mapa" else "Definir Local no Mapa",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
