package de.dennisguse.opentracks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
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
import de.dennisguse.opentracks.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSetupDialog(
    isInitialOnboarding: Boolean = false,
    initialName: String = "Meu Carro",
    initialPlate: String = "",
    initialTankCapacity: Double = 50.0,
    initialFuelType: String = "Gasolina Comum",
    initialOdometer: Double = 0.0,
    initialEstimatedKmL: Double = 12.0,
    initialKmSinceLastFull: Double = 0.0,
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
    var name by remember { mutableStateOf(initialName) }
    var plate by remember { mutableStateOf(initialPlate) }
    var tankCapacityStr by remember { mutableStateOf(if (initialTankCapacity > 0) initialTankCapacity.toInt().toString() else "50") }
    var fuelType by remember { mutableStateOf(initialFuelType) }
    var odometerStr by remember { mutableStateOf(if (initialOdometer > 0) String.format(Locale.US, "%.0f", initialOdometer) else "") }
    var estimatedKmLStr by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialEstimatedKmL)) }
    var kmSinceLastFullStr by remember { mutableStateOf(if (initialKmSinceLastFull > 0) String.format(Locale.US, "%.0f", initialKmSinceLastFull) else "0") }

    val tankCap = tankCapacityStr.toDoubleOrNull() ?: 50.0
    val kmL = estimatedKmLStr.toDoubleOrNull() ?: 12.0
    val kmSince = kmSinceLastFullStr.toDoubleOrNull() ?: 0.0

    val litersUsed = if (kmL > 0) kmSince / kmL else 0.0
    val estimatedCurrentLiters = (tankCap - litersUsed).coerceIn(0.0, tankCap)
    val estimatedRange = (estimatedCurrentLiters * kmL).toInt()
    val tankPercent = if (tankCap > 0) (estimatedCurrentLiters / tankCap * 100).toInt() else 0

    AlertDialog(
        onDismissRequest = {
            if (!isInitialOnboarding) onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WandaInteractive.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = WandaInteractive,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = if (isInitialOnboarding) "Configurar meu Carro" else "Dados do Veículo",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isInitialOnboarding) "Para estimativas precisas de tanque e autonomia" else "Ajuste odômetro, tanque e consumo",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info banner explaining the necessity of initial input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WandaInteractive.copy(alpha = 0.10f))
                        .border(1.dp, WandaInteractive.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = WandaInteractive,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Antes das primeiras viagens, o CarTracks precisa do odômetro e de uma estimativa do tanque para calcular sua autonomia com precisão real.",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }

                // 1. Odômetro Atual
                OutlinedTextField(
                    value = odometerStr,
                    onValueChange = { odometerStr = it },
                    label = { Text("Odômetro Atual do Carro (km)") },
                    placeholder = { Text("Ex: 85200") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 2. Capacidade do Tanque (L)
                OutlinedTextField(
                    value = tankCapacityStr,
                    onValueChange = { tankCapacityStr = it },
                    label = { Text("Capacidade do Tanque (Litros)") },
                    placeholder = { Text("Ex: 50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Média Estimada de Consumo (km/L)
                OutlinedTextField(
                    value = estimatedKmLStr,
                    onValueChange = { estimatedKmLStr = it },
                    label = { Text("Consumo Médio Estimado (km/L)") },
                    placeholder = { Text("Ex: 11.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Km Rodados desde o último tanque cheio
                OutlinedTextField(
                    value = kmSinceLastFullStr,
                    onValueChange = { kmSinceLastFullStr = it },
                    label = { Text("Km rodados desde que encheu o tanque") },
                    placeholder = { Text("Ex: 80 (ou 0 se acabou de encher)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Estimation Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141926))
                        .border(1.dp, Color(0x334589FF), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ESTIMATIVA RESULTANTE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WandaCyan20,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("No Tanque:", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f L (%d%%)", estimatedCurrentLiters, tankPercent),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Autonomia:", fontSize = 11.sp, color = TextMuted)
                                Text(
                                    text = "~$estimatedRange km",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WandaGreen30
                                )
                            }
                        }
                    }
                }

                // 5. Nome do Carro (Opcional)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome ou Modelo do Carro") },
                    placeholder = { Text("Ex: Toyota Etios, Honda Civic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 6. Placa (Opcional)
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase() },
                    label = { Text("Placa (Opcional)") },
                    placeholder = { Text("Ex: ABC-1234") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odo = odometerStr.toDoubleOrNull() ?: 0.0
                    onConfirm(
                        if (name.isBlank()) "Meu Carro" else name.trim(),
                        plate.trim().uppercase(),
                        tankCap,
                        fuelType,
                        odo,
                        kmL,
                        kmSince
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
            ) {
                Text(
                    text = if (isInitialOnboarding) "Salvar e Começar" else "Salvar Configurações",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            if (!isInitialOnboarding) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        },
        containerColor = WandaCardElevated
    )
}

@Composable
fun QuickEditOdometerDialog(
    currentOdometer: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var odoStr by remember {
        mutableStateOf(if (currentOdometer > 0) String.format(Locale.US, "%.0f", currentOdometer) else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "✏️ Ajustar Odômetro",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Digite o valor atual marcado no hodômetro do painel do seu carro:",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = odoStr,
                    onValueChange = { odoStr = it },
                    label = { Text("Odômetro Real (km)") },
                    placeholder = { Text("Ex: 85450") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val odo = odoStr.toDoubleOrNull() ?: currentOdometer
                    onConfirm(odo)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WandaInteractive)
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        },
        containerColor = WandaCardElevated
    )
}
