package de.dennisguse.opentracks.ui.screens

import androidx.compose.ui.graphics.RectangleShape
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.ui.components.LocationPickerMapDialog
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.MapMarkerItem
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelEfficiencyCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelScreen(
    fuelEntries: List<FuelItem>,
    tankCapacityLiters: Double = 50.0,
    currentGps: Pair<Double, Double>? = null,
    currentVehicleOdometer: Double = 0.0,
    onAddFuel: (
        station: String,
        liters: Double,
        price: Double,
        total: Double,
        odo: Double,
        notes: String,
        isFullTank: Boolean,
        fuelType: String,
        latitude: Double?,
        longitude: Double?
    ) -> Unit,
    onUpdateFuelLocation: (entryId: Long, latitude: Double, longitude: Double) -> Unit = { _, _, _ -> },
    onUpdateFuel: ((id: Long, station: String, liters: Double, price: Double, total: Double, odo: Double, notes: String, isFull: Boolean, fuelType: String) -> Unit)? = null,
    onDeleteFuel: ((id: Long) -> Unit)? = null
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllStationsMap by remember { mutableStateOf(false) }

    // State for picking/editing location of an EXISTING fuel entry ("depois do abastecimento")
    var editingLocationEntry by remember { mutableStateOf<FuelItem?>(null) }
    var editingFuelEntry by remember { mutableStateOf<FuelItem?>(null) }
    var deletingFuelEntry by remember { mutableStateOf<FuelItem?>(null) }

    // Filter states
    var selectedFuelTypeFilter by remember { mutableStateOf("Todos") }
    var selectedTankStateFilter by remember { mutableStateOf("Todos") } // "Todos", "Cheio", "Parcial"

    val filteredEntries = remember(fuelEntries, selectedFuelTypeFilter, selectedTankStateFilter) {
        fuelEntries.filter { item ->
            val matchFuel = selectedFuelTypeFilter == "Todos" || item.fuelType.equals(selectedFuelTypeFilter, ignoreCase = true)
            val matchTank = when (selectedTankStateFilter) {
                "Cheio" -> item.isFullTank
                "Parcial" -> !item.isFullTank
                else -> true
            }
            matchFuel && matchTank
        }
    }

    val totalSpent = filteredEntries.sumOf { it.totalCost }
    val totalLiters = filteredEntries.sumOf { it.liters }
    val efficiencyStats = remember(fuelEntries, tankCapacityLiters) {
        FuelEfficiencyCalculator.calculateStats(fuelEntries, tankCapacityLiters)
    }

    val stationsWithLocation = remember(filteredEntries) {
        filteredEntries.filter { it.latitude != null && it.longitude != null }
    }

    // 1. Dialog to pick/edit location of an existing fuel entry ("Depois do abastecimento")
    editingLocationEntry?.let { entry ->
        LocationPickerMapDialog(
            title = entry.stationName.ifBlank { "Posto de Combustível" },
            initialLat = entry.latitude ?: currentGps?.first ?: -23.5672,
            initialLon = entry.longitude ?: currentGps?.second ?: -46.6685,
            currentGps = currentGps,
            onConfirm = { lat, lon ->
                onUpdateFuelLocation(entry.id, lat, lon)
                editingLocationEntry = null
            },
            onDismiss = { editingLocationEntry = null }
        )
    }

    // 2. Full-screen Map showing all fueled gas stations with coordinates
    if (showAllStationsMap) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val markers = remember(stationsWithLocation) {
            stationsWithLocation.map { item ->
                MapMarkerItem(
                    lat = item.latitude!!,
                    lon = item.longitude!!,
                    title = "${item.stationName} (${item.fuelType})",
                    snippet = String.format(
                        Locale.getDefault(),
                        "R$ %.2f/L • %.1f L • %s",
                        item.pricePerLiter,
                        item.liters,
                        sdf.format(Date(item.dateTime))
                    )
                )
            }
        }

        Dialog(
            onDismissRequest = { showAllStationsMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CyberInk)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                CarMapView(
                    extraMarkers = markers,
                    currentLocation = currentGps,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RectangleShape)
                        .background(CyberPanelRaised.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mapa de Postos Abastecidos",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${markers.size} abastecimento(s) com localização no mapa",
                            color = CyberCyan,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = { showAllStationsMap = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = TextPrimary)
                    }
                }
            }
        }
    }

    // 3. Dialog to add a new fuel entry (With Location Autofill & Smart Suggestions)
    if (showAddDialog) {
        val defaultOdo = remember(currentVehicleOdometer, fuelEntries) {
            if (currentVehicleOdometer > 0) {
                currentVehicleOdometer.toInt().toString()
            } else {
                val lastOdo = fuelEntries.map { it.odometerKm }.filter { it > 0 }.maxOrNull()
                if (lastOdo != null) (lastOdo + 320.0).toInt().toString() else ""
            }
        }

        var station by remember { mutableStateOf("") }
        var litersStr by remember { mutableStateOf("") }
        var priceStr by remember { mutableStateOf("5.89") }
        var odoStr by remember { mutableStateOf(defaultOdo) }
        var notes by remember { mutableStateOf("") }
        var isFullTank by remember { mutableStateOf(true) }
        var chosenFuelType by remember { mutableStateOf("Gasolina Comum") }
        var selectedLat by remember { mutableStateOf<Double?>(currentGps?.first) }
        var selectedLon by remember { mutableStateOf<Double?>(currentGps?.second) }
        var showNewEntryMapPicker by remember { mutableStateOf(false) }

        // Smart Geocoding / Location Autofill
        val activeCoord = if (selectedLat != null && selectedLon != null) Pair(selectedLat!!, selectedLon!!) else currentGps
        LaunchedEffect(activeCoord) {
            if (activeCoord != null && station.isBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(activeCoord.first, activeCoord.second, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            val street = addr.thoroughfare ?: addr.featureName
                            val sub = addr.subLocality ?: addr.locality
                            val autoName = when {
                                !street.isNullOrBlank() && !sub.isNullOrBlank() -> "Posto $street ($sub)"
                                !street.isNullOrBlank() -> "Posto $street"
                                !sub.isNullOrBlank() -> "Posto $sub"
                                else -> ""
                            }
                            if (autoName.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    if (station.isBlank()) {
                                        station = autoName
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        if (showNewEntryMapPicker) {
            LocationPickerMapDialog(
                title = station.ifBlank { "Posto de Abastecimento" },
                initialLat = selectedLat ?: currentGps?.first ?: -23.5672,
                initialLon = selectedLon ?: currentGps?.second ?: -46.6685,
                currentGps = currentGps,
                onConfirm = { lat, lon ->
                    selectedLat = lat
                    selectedLon = lon
                    showNewEntryMapPicker = false
                },
                onDismiss = { showNewEntryMapPicker = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = {
                    Text(
                        text = "Registrar Abastecimento",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Full Tank vs Partial Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RectangleShape)
                                .background(CyberPanel)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFullTank) "🟢 Tanque Cheio (100%)" else "🟡 Abastecimento Parcial",
                                color = if (isFullTank) CyberGreen else CyberOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Switch(
                                checked = isFullTank,
                                onCheckedChange = { isFullTank = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CyberGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = CyberOrange
                                )
                            )
                        }

                        // Station Name with Quick Suggestions
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = station,
                                onValueChange = { station = it },
                                label = { Text("Nome do Posto") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Quick Station Suggestions
                            val commonStations = listOf("Posto Shell", "Posto Ipiranga", "Posto BR Petrobras", "Posto Ipiranga Rebouças")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(commonStations) { sName ->
                                    AssistChip(
                                        onClick = { station = sName },
                                        label = { Text(sName, fontSize = 11.sp) },
                                        shape = RectangleShape,
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = CyberPanel,
                                            labelColor = CyberCyan
                                        ),
                                        border = null
                                    )
                                }
                            }
                        }

                        // Fuel Type Chips
                        Text("Tipo de Combustível:", fontSize = 12.sp, color = TextSecondary)
                        val fuelOptions = listOf("Gasolina Comum", "Gasolina Aditivada", "Etanol", "Diesel")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(fuelOptions) { ft ->
                                val sel = ft == chosenFuelType
                                FilterChip(
                                    selected = sel,
                                    onClick = { chosenFuelType = ft },
                                    label = { Text(ft, fontSize = 11.sp) },
                                    shape = RectangleShape,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberAcid,
                                        selectedLabelColor = CyberOnAcid,
                                selectedLeadingIconColor = CyberOnAcid,
                                        containerColor = CyberPanel,
                                        labelColor = TextSecondary
                                    ),
                                    border = null
                                )
                            }
                        }

                        // Liters & Price Inputs
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = litersStr,
                                onValueChange = { litersStr = it },
                                label = { Text("Litros") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = priceStr,
                                onValueChange = { priceStr = it },
                                label = { Text("Preço / L") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Quick Liters Shortcuts
                        val literPresets = listOf("20", "30", "40", "${tankCapacityLiters.toInt()}")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(literPresets) { l ->
                                AssistChip(
                                    onClick = { litersStr = l },
                                    label = { Text("${l}L", fontSize = 11.sp) },
                                    shape = RectangleShape,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = CyberPanel,
                                        labelColor = CyberGreen
                                    ),
                                    border = null
                                )
                            }
                        }

                        // Odometer Input (Pre-filled with estimated km!)
                        OutlinedTextField(
                            value = odoStr,
                            onValueChange = { odoStr = it },
                            label = { Text("Km no Odômetro (Automático)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Location Section ("Escolher no Mapa na hora ou depois")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RectangleShape)
                                .background(CyberPanel)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 Localização no Mapa (Opcional)",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (selectedLat != null && selectedLon != null) {
                                    TextButton(
                                        shape = RectangleShape,
                                        onClick = {
                                            selectedLat = null
                                            selectedLon = null
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Deixar p/ depois", fontSize = 11.sp, color = CyberOrange)
                                    }
                                }
                            }

                            if (selectedLat != null && selectedLon != null) {
                                Text(
                                    text = String.format(Locale.US, "Definido: %.5f, %.5f", selectedLat, selectedLon),
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Você pode escolher o local no mapa agora ou adicionar depois na lista.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showNewEntryMapPicker = true },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RectangleShape,
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedLat != null) "Alterar no Mapa" else "Definir no Mapa",
                                        maxLines = 1,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (currentGps != null) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedLat = currentGps.first
                                            selectedLon = currentGps.second
                                        },
                                        modifier = Modifier.height(38.dp),
                                        shape = RectangleShape,
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MyLocation,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = CyberCyan
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("GPS", fontSize = 11.sp, color = TextPrimary)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Observações (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        shape = RectangleShape,
                        onClick = {
                            val l = litersStr.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val p = priceStr.replace(',', '.').toDoubleOrNull() ?: 0.0
                            val o = odoStr.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (station.isNotBlank() && l > 0) {
                                val tot = l * p
                                onAddFuel(station, l, p, tot, o, notes, isFullTank, chosenFuelType, selectedLat, selectedLon)
                                showAddDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
                    ) {
                        Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(shape = RectangleShape, onClick = { showAddDialog = false }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                },
                containerColor = CyberPanelRaised
            )
        }
    }

    if (editingFuelEntry != null) {
        val entry = editingFuelEntry!!
        EditFuelEntryDialog(
            entry = entry,
            onConfirm = { newStation, newLiters, newPrice, newTotal, newOdo, newNotes, newIsFull, newType ->
                onUpdateFuel?.invoke(entry.id, newStation, newLiters, newPrice, newTotal, newOdo, newNotes, newIsFull, newType)
                editingFuelEntry = null
            },
            onDismiss = { editingFuelEntry = null }
        )
    }

    if (deletingFuelEntry != null) {
        val entry = deletingFuelEntry!!
        AlertDialog(
            onDismissRequest = { deletingFuelEntry = null },
            title = {
                Text("Excluir Abastecimento", fontWeight = FontWeight.Bold, color = CyberRed)
            },
            text = {
                Text("Deseja realmente excluir o abastecimento no ${entry.stationName}?", color = TextPrimary)
            },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    onClick = {
                        val id = entry.id
                        deletingFuelEntry = null
                        onDeleteFuel?.invoke(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = { deletingFuelEntry = null }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = CyberPanelRaised
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "ABASTECIMENTOS",
                    style = displayStyle(40),
                    color = CyberAcid
                )
                Text(
                    text = "// " + "Consumo real (tanque cheio e parcial) • Tanque máx: ${tankCapacityLiters.toInt()}L".uppercase(),
                    style = EyebrowStyle,
                    color = TextSecondary
                )
            }
        }

        // Efficiency Summary Card
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Consumo Real Médio",
                        value = if (efficiencyStats.fullTankCyclesCount > 0) String.format(Locale.getDefault(), "%.1f km/L", efficiencyStats.averageKmL) else "—",  // needs two full tanks
                        icon = Icons.Default.LocalGasStation,
                        tint = CyberGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Custo Médio / Km",
                        value = if (efficiencyStats.fullTankCyclesCount > 0) String.format(Locale.getDefault(), "R$ %.2f /km", efficiencyStats.averageCostPerKm) else "—",
                        icon = Icons.Default.AttachMoney,
                        tint = CyberAcid,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Gasto Filtrado",
                        value = String.format(Locale.getDefault(), "R$ %.2f", totalSpent),
                        icon = Icons.Default.AttachMoney,
                        tint = CyberOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Litros Filtrados",
                        value = String.format(Locale.getDefault(), "%.1f L", totalLiters),
                        icon = Icons.Default.Speed,
                        tint = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Explanation Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RectangleShape)
                    .background(CyberPanelRaised)
                    .border(1.dp, CyberLineSubtle, RectangleShape)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Abastecimentos parciais acumulam litros até o próximo tanque cheio. O app sugere automaticamente nomes de postos e odômetro!",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Filter 1: By Fuel Type
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Filtrar por Combustível:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val fuelFilters = listOf("Todos", "Gasolina Comum", "Gasolina Aditivada", "Etanol", "Diesel")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fuelFilters) { ft ->
                        val isSel = ft == selectedFuelTypeFilter
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedFuelTypeFilter = ft },
                            label = { Text(ft, fontSize = 12.sp) },
                            shape = RectangleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberAcid,
                                selectedLabelColor = CyberOnAcid,
                                selectedLeadingIconColor = CyberOnAcid,
                                containerColor = CyberPanel,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Filter 2: By Tank Fill Type
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Filtrar por Tipo de Tanque:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                val tankFilters = listOf("Todos" to "Todos", "Cheio" to "🟢 Tanque Cheio", "Parcial" to "🟡 Parcial")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tankFilters) { (key, label) ->
                        val isSel = key == selectedTankStateFilter
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedTankStateFilter = key },
                            label = { Text(label, fontSize = 12.sp) },
                            shape = RectangleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberAcid,
                                selectedLabelColor = CyberOnAcid,
                                selectedLeadingIconColor = CyberOnAcid,
                                containerColor = CyberPanel,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            }
        }

        // Action Buttons: Add Fuel + View Fuel Stations Map
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registrar Novo Abastecimento",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (stationsWithLocation.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showAllStationsMap = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ver Postos Abastecidos no Mapa (${stationsWithLocation.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }
                }
            }
        }

        // Entries List
        if (filteredEntries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .background(CyberPanel)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhum abastecimento encontrado para os filtros selecionados.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            itemsIndexed(filteredEntries) { _, entry ->
                val hasLoc = entry.latitude != null && entry.longitude != null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .background(CyberPanel)
                        .border(1.dp, CyberLineSubtle, RectangleShape)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = entry.stationName,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (onUpdateFuel != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { editingFuelEntry = entry },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Posto",
                                            tint = CyberAcid,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                if (onDeleteFuel != null) {
                                    IconButton(
                                        onClick = { deletingFuelEntry = entry },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Excluir Abastecimento",
                                            tint = CyberRed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = sdf.format(Date(entry.dateTime)),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Badges: Fuel Type and Full Tank vs Partial
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RectangleShape)
                                    .background(CyberPanelRaised)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = entry.fuelType,
                                    color = CyberCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RectangleShape)
                                    .background(if (entry.isFullTank) CyberGreen.copy(alpha = 0.15f) else CyberOrange.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (entry.isFullTank) "🟢 Tanque Cheio" else "🟡 Abastecimento Parcial",
                                    color = if (entry.isFullTank) CyberGreen else CyberOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${String.format(Locale.getDefault(), "%.1f", entry.liters)} L • R$ ${String.format(Locale.getDefault(), "%.2f", entry.pricePerLiter)}/L",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "R$ %.2f", entry.totalCost),
                                color = CyberGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        if (entry.odometerKm > 0) {
                            Text(
                                text = "Odômetro: ${String.format(Locale.getDefault(), "%.0f", entry.odometerKm)} km",
                                color = TextTertiary,
                                fontSize = 12.sp
                            )
                        }

                        // Map Location Row (View on Map if set, or Add Location Later if not set!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasLoc) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Local Salvo no Mapa",
                                        color = CyberCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { editingLocationEntry = entry },
                                    shape = RectangleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = CyberPanelRaised,
                                        contentColor = CyberAcid
                                    ),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Map,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ver no Mapa",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "Sem local definido",
                                    color = TextTertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = { editingLocationEntry = entry },
                                    shape = RectangleShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberOrange.copy(alpha = 0.2f),
                                        contentColor = CyberOrange
                                    ),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EditLocationAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Marcar Local no Mapa",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun EditFuelEntryDialog(
    entry: FuelItem,
    onConfirm: (
        station: String,
        liters: Double,
        price: Double,
        total: Double,
        odo: Double,
        notes: String,
        isFull: Boolean,
        fuelType: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var station by remember { mutableStateOf(entry.stationName) }
    var litersStr by remember { mutableStateOf(String.format(Locale.US, "%.1f", entry.liters)) }
    var priceStr by remember { mutableStateOf(String.format(Locale.US, "%.2f", entry.pricePerLiter)) }
    var odoStr by remember { mutableStateOf(if (entry.odometerKm > 0) String.format(Locale.US, "%.0f", entry.odometerKm) else "") }
    var notes by remember { mutableStateOf(entry.notes) }
    var isFullTank by remember { mutableStateOf(entry.isFullTank) }
    var chosenFuelType by remember { mutableStateOf(entry.fuelType) }

    val commonStations = listOf("Posto Shell", "Posto Ipiranga", "Posto BR Petrobras", "Posto Ale")
    val fuelTypes = listOf("Gasolina Comum", "Gasolina Aditivada", "Etanol", "Diesel S10", "GNV")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RectangleShape)
                        .background(CyberMagenta.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = CyberAcid,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text("Editar Abastecimento", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text("Nome do Posto") },
                    placeholder = { Text("Ex: Posto Shell Jardins") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(commonStations) { s ->
                        SuggestionChip(
                            onClick = { station = s },
                            label = { Text(s, fontSize = 11.sp) }
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = litersStr,
                        onValueChange = { litersStr = it },
                        label = { Text("Litros (L)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Preço / Litro") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = odoStr,
                    onValueChange = { odoStr = it },
                    label = { Text("Odômetro (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tipo de Combustível:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(fuelTypes) { fType ->
                        FilterChip(
                            selected = fType == chosenFuelType,
                            onClick = { chosenFuelType = fType },
                            label = { Text(fType, fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RectangleShape)
                        .background(CyberPanelRaised)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tanque Cheio?", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (isFullTank) "🟢 Sim (Calcula média km/L)" else "🟡 Abastecimento parcial", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isFullTank,
                        onCheckedChange = { isFullTank = it }
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                shape = RectangleShape,
                onClick = {
                    val l = litersStr.replace(',', '.').toDoubleOrNull() ?: entry.liters
                    val p = priceStr.replace(',', '.').toDoubleOrNull() ?: entry.pricePerLiter
                    val o = odoStr.replace(',', '.').toDoubleOrNull() ?: entry.odometerKm
                    val tot = l * p
                    onConfirm(
                        if (station.isBlank()) entry.stationName else station.trim(),
                        l, p, tot, o, notes.trim(), isFullTank, chosenFuelType
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberMagenta)
            ) {
                Text("Salvar Alterações", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(shape = RectangleShape, onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        },
        containerColor = CyberPanelRaised
    )
}
