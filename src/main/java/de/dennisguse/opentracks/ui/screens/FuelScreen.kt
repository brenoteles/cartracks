package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelScreen(
    fuelEntries: List<FuelItem>,
    tankCapacityLiters: Double = 50.0,
    currentGps: Pair<Double, Double>? = null,
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
    onUpdateFuelLocation: (entryId: Long, latitude: Double, longitude: Double) -> Unit = { _, _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showAllStationsMap by remember { mutableStateOf(false) }

    // State for picking/editing location of an EXISTING fuel entry ("colocar depois")
    var editingLocationEntry by remember { mutableStateOf<FuelItem?>(null) }

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
                    .background(AppleBlack)
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppleCardElevated.copy(alpha = 0.95f))
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
                            color = AppleTeal,
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

    // 3. Dialog to add a new fuel entry ("Na hora do abastecimento", with optional map pin)
    if (showAddDialog) {
        var station by remember { mutableStateOf("") }
        var litersStr by remember { mutableStateOf("") }
        var priceStr by remember { mutableStateOf("5.89") }
        var odoStr by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var isFullTank by remember { mutableStateOf(true) }
        var chosenFuelType by remember { mutableStateOf("Gasolina Comum") }
        var selectedLat by remember { mutableStateOf<Double?>(null) }
        var selectedLon by remember { mutableStateOf<Double?>(null) }
        var showNewEntryMapPicker by remember { mutableStateOf(false) }

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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Full Tank vs Partial Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppleCard)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isFullTank) "🟢 Tanque Cheio (100%)" else "🟡 Abastecimento Parcial",
                                color = if (isFullTank) AppleGreen else AppleOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Switch(
                                checked = isFullTank,
                                onCheckedChange = { isFullTank = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppleGreen,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = AppleOrange
                                )
                            )
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
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppleBlue,
                                        selectedLabelColor = Color.White,
                                        containerColor = AppleCard,
                                        labelColor = TextSecondary
                                    ),
                                    border = null
                                )
                            }
                        }

                        OutlinedTextField(
                            value = station,
                            onValueChange = { station = it },
                            label = { Text("Nome do Posto") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

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

                        OutlinedTextField(
                            value = odoStr,
                            onValueChange = { odoStr = it },
                            label = { Text("Km no Odômetro") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Optional Location Section ("Na hora ou depois")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppleCard)
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
                                        onClick = {
                                            selectedLat = null
                                            selectedLon = null
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Deixar p/ depois", fontSize = 11.sp, color = AppleOrange)
                                    }
                                }
                            }

                            if (selectedLat != null && selectedLon != null) {
                                Text(
                                    text = String.format(Locale.US, "Fixado: %.5f, %.5f", selectedLat, selectedLon),
                                    color = AppleTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = "Você pode pinar o posto agora ou adicionar depois na lista.",
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
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectedLat != null) "Alterar no Mapa" else "Pinar no Mapa",
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
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MyLocation,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = AppleTeal
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
                        colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                    ) {
                        Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                },
                containerColor = AppleCardElevated
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleBlack)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Abastecimentos",
                    color = TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Consumo real (tanque cheio e parcial) • Tanque máx: ${tankCapacityLiters.toInt()}L",
                    color = TextSecondary,
                    fontSize = 14.sp
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
                        value = String.format(Locale.getDefault(), "%.1f km/L", efficiencyStats.averageKmL),
                        icon = Icons.Default.LocalGasStation,
                        tint = AppleGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Custo Médio / Km",
                        value = String.format(Locale.getDefault(), "R$ %.2f /km", efficiencyStats.averageCostPerKm),
                        icon = Icons.Default.AttachMoney,
                        tint = AppleBlue,
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
                        tint = AppleOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Litros Filtrados",
                        value = String.format(Locale.getDefault(), "%.1f L", totalLiters),
                        icon = Icons.Default.Speed,
                        tint = AppleTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Partial & Full Tank Explanation Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppleCardElevated)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Abastecimentos parciais acumulam litros até o próximo tanque cheio. Você pode pinar a localização do posto na hora ou depois!",
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
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppleBlue,
                                selectedLabelColor = Color.White,
                                containerColor = AppleCard,
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
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppleBlue,
                                selectedLabelColor = Color.White,
                                containerColor = AppleCard,
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
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
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
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleTeal)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AppleTeal
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ver Mapa dos Postos Abastecidos (${stationsWithLocation.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppleTeal
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleCard)
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
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppleCard)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.stationName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppleCardElevated)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = entry.fuelType,
                                    color = AppleTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (entry.isFullTank) AppleGreen.copy(alpha = 0.15f) else AppleOrange.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = if (entry.isFullTank) "🟢 Tanque Cheio" else "🟡 Abastecimento Parcial",
                                    color = if (entry.isFullTank) AppleGreen else AppleOrange,
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
                                color = AppleGreen,
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

                        // Map Location Row (View/Edit if set, or Add Location Later if not set!)
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
                                        tint = AppleTeal,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = String.format(Locale.US, "%.4f, %.4f", entry.latitude, entry.longitude),
                                        color = AppleTeal,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                FilledTonalButton(
                                    onClick = { editingLocationEntry = entry },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = AppleCardElevated,
                                        contentColor = AppleBlue
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
                                        text = "Ver / Editar no Mapa",
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
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AppleOrange.copy(alpha = 0.2f),
                                        contentColor = AppleOrange
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
                                        text = "Definir Local no Mapa",
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
