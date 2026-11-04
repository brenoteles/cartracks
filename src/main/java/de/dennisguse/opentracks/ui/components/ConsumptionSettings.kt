package de.dennisguse.opentracks.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelConsumptionModel
import de.dennisguse.opentracks.util.FuelFamily
import java.util.Locale

/**
 * Ajustes > Consumo médio: km/L editável por combustível, valor medido na bomba e a escolha
 * de qual dos dois entra no cálculo das viagens.
 */
@Composable
fun ConsumptionSettingsSection(
    model: FuelConsumptionModel,
    manualValues: Map<FuelFamily, Double>,
    onSave: (Map<FuelFamily, Double>, Boolean) -> Unit
) {
    var usePump by remember(model.usePumpMeasurement) { mutableStateOf(model.usePumpMeasurement) }
    val inputs = remember(manualValues) {
        FuelFamily.values().associateWith { f ->
            mutableStateOf(String.format(Locale.US, "%.1f", manualValues[f] ?: FuelConsumptionModel.DEFAULT_KML))
        }
    }
    val current = model.currentFamily()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberPanel()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Combustível atual: ${current.label}. O km/L abaixo é a média em uso misto; cada viagem é " +
                "ajustada pelo trânsito (anda e para consome mais, estrada consome menos).",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        FuelFamily.values().forEach { family ->
            val measured = model.pump[family]
            val inUse = model.baseKmL(family)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (family == current) CyberPanelRaised else CyberPanel)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(family.label.uppercase(), style = EyebrowStyle, color = if (family == current) CyberAcid else CyberText)
                        if (family == current) {
                            Spacer(Modifier.width(6.dp))
                            CyberChip("No tanque", CyberAcid)
                        }
                    }
                    Text(
                        text = if (measured != null) {
                            String.format(Locale.getDefault(), "Bomba: %.1f km/L (%d ciclo%s, %.0f km)", measured.kmL, measured.cycles, if (measured.cycles > 1) "s" else "", measured.km)
                        } else "Bomba: sem 2 tanques cheios ainda",
                        style = monoStyle(11, FontWeight.Normal),
                        color = if (measured != null) CyberCyan else CyberTextTertiary
                    )
                    Text(
                        String.format(Locale.getDefault(), "Em uso: %.1f km/L %s", inUse, if (model.isFromPump(family)) "(bomba)" else "(manual)"),
                        style = monoStyle(11, FontWeight.Normal),
                        color = CyberTextSecondary
                    )
                }
                OutlinedTextField(
                    value = inputs.getValue(family).value,
                    onValueChange = { inputs.getValue(family).value = it.replace(',', '.') },
                    modifier = Modifier.width(96.dp),
                    singleLine = true,
                    suffix = { Text("km/L", fontSize = 10.sp, color = TextTertiary) },
                    textStyle = monoStyle(14),
                    shape = RectangleShape,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberAcid,
                        unfocusedBorderColor = CyberLine,
                        focusedTextColor = CyberText,
                        unfocusedTextColor = CyberText,
                        cursorColor = CyberAcid
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("USAR CONSUMO MEDIDO NA BOMBA", style = EyebrowStyle, color = CyberText)
                Text(
                    "Quando houver dois tanques cheios do mesmo combustível, o valor medido substitui o manual.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = usePump,
                onCheckedChange = { usePump = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberOnAcid,
                    checkedTrackColor = CyberAcid,
                    uncheckedThumbColor = CyberTextSecondary,
                    uncheckedTrackColor = CyberPanelRaised,
                    uncheckedBorderColor = CyberLine
                )
            )
        }

        CyberActionButton(
            text = "Salvar consumo",
            height = 48.dp,
            onClick = {
                val values = FuelFamily.values().associateWith { f ->
                    inputs.getValue(f).value.toDoubleOrNull()?.takeIf { it in 2.0..40.0 }
                        ?: manualValues[f] ?: FuelConsumptionModel.DEFAULT_KML
                }
                onSave(values, usePump)
            }
        )
    }
}
