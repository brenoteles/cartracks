package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.LocalGasStation
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
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelEfficiencyCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FuelScreen(
    fuelEntries: List<FuelItem>,
    onAddFuel: (station: String, liters: Double, price: Double, total: Double, odo: Double, notes: String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val totalSpent = fuelEntries.sumOf { it.totalCost }
    val totalLiters = fuelEntries.sumOf { it.liters }
    val efficiencyStats = remember(fuelEntries) {
        FuelEfficiencyCalculator.calculateStats(fuelEntries)
    }

    if (showDialog) {
        var station by remember { mutableStateOf("") }
        var litersStr by remember { mutableStateOf("") }
        var priceStr by remember { mutableStateOf("5.89") }
        var odoStr by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = "Registrar Abastecimento",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            onAddFuel(station, l, p, tot, o, notes)
                            showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppleBlue)
                ) {
                    Text("Salvar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            },
            containerColor = AppleCardElevated
        )
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
                    text = "Eficiência real (km/L) e despesas de combustível",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // Feature 3: Real km/L & Cost Metrics Grid
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
                        title = "Total Gasto",
                        value = String.format(Locale.getDefault(), "R$ %.2f", totalSpent),
                        icon = Icons.Default.AttachMoney,
                        tint = AppleOrange,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Litros",
                        value = String.format(Locale.getDefault(), "%.1f L", totalLiters),
                        icon = Icons.Default.Speed,
                        tint = AppleTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Action Button
        item {
            Button(
                onClick = { showDialog = true },
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
        }

        // Entries List with Individual km/L calculation
        if (fuelEntries.isEmpty()) {
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
                        text = "Nenhum abastecimento registrado.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            itemsIndexed(fuelEntries) { index, entry ->
                // Check if next entry in list (which is previous in time) allows delta calculation
                var itemKmL: Double? = null
                if (index < fuelEntries.size - 1) {
                    val prevInTime = fuelEntries[index + 1]
                    val delta = entry.odometerKm - prevInTime.odometerKm
                    if (delta > 0 && entry.liters > 0) {
                        itemKmL = delta / entry.liters
                    }
                }

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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (entry.odometerKm > 0) {
                                Text(
                                    text = "Odômetro: ${String.format(Locale.getDefault(), "%.0f", entry.odometerKm)} km",
                                    color = TextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                            if (itemKmL != null && itemKmL > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppleGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "Rendimento: ${String.format(Locale.getDefault(), "%.1f", itemKmL)} km/L",
                                        color = AppleGreen,
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
