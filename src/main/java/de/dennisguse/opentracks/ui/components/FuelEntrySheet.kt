package de.dennisguse.opentracks.ui.components

import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelAmounts
import de.dennisguse.opentracks.util.FuelAmounts.Field
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val FUEL_TYPES = listOf("Gasolina Comum", "Gasolina Aditivada", "Etanol", "Diesel")

/**
 * Full-screen fuel-up form. The driver fills any two of total paid, price per liter and liters
 * (whatever the pump or receipt shows); the third is computed and marked as such.
 */
@Composable
fun FuelEntrySheet(
    history: List<FuelItem>,
    defaultFuelType: String,
    defaultOdometer: String,
    /** Liters that would fill the tank now, for the "Completar" shortcut; null when unknown. */
    litersToFill: Double?,
    currentGps: Pair<Double, Double>?,
    onSave: (station: String, liters: Double, price: Double, total: Double, odo: Double, notes: String,
             isFullTank: Boolean, fuelType: String, lat: Double?, lon: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fuelType by remember { mutableStateOf(defaultFuelType.takeIf { it in FUEL_TYPES } ?: FUEL_TYPES[0]) }
    fun lastPrice(type: String) = history.filter { it.fuelType == type && it.pricePerLiter > 0 }
        .maxByOrNull { it.dateTime }?.pricePerLiter

    var totalText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf(lastPrice(fuelType)?.let { FuelAmounts.format(Field.PRICE, it) } ?: "") }
    var litersText by remember { mutableStateOf("") }
    // A remembered price counts as typed: entering the total alone is enough.
    var order by remember { mutableStateOf(if (priceText.isNotEmpty()) listOf(Field.PRICE) else emptyList()) }
    var priceTypedByUser by remember { mutableStateOf(false) }

    var isFullTank by remember { mutableStateOf(true) }
    var station by remember { mutableStateOf("") }
    var odoText by remember { mutableStateOf(defaultOdometer) }
    var notes by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(currentGps?.first) }
    var lon by remember { mutableStateOf(currentGps?.second) }
    var pickOnMap by remember { mutableStateOf(false) }

    val derived = FuelAmounts.derived(order)
    fun valueOf(f: Field) = FuelAmounts.parse(when (f) { Field.TOTAL -> totalText; Field.PRICE -> priceText; Field.LITERS -> litersText })
    fun setText(f: Field, t: String) = when (f) { Field.TOTAL -> totalText = t; Field.PRICE -> priceText = t; Field.LITERS -> litersText = t }
    fun recompute() {
        val d = FuelAmounts.derived(order) ?: return
        val v = FuelAmounts.compute(d, valueOf(Field.TOTAL), valueOf(Field.PRICE), valueOf(Field.LITERS))
        setText(d, v?.let { FuelAmounts.format(d, it) } ?: "")
    }
    fun type(f: Field, t: String) {
        setText(f, t)
        if (f == Field.PRICE) priceTypedByUser = true
        order = FuelAmounts.touch(order, f)
        recompute()
    }

    // Station name from the address when the form opens at the pump.
    LaunchedEffect(lat, lon) {
        val la = lat ?: return@LaunchedEffect
        val lo = lon ?: return@LaunchedEffect
        if (station.isNotBlank()) return@LaunchedEffect
        val name = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(la, lo, 1)?.firstOrNull()?.let { a ->
                    val street = a.thoroughfare ?: a.featureName
                    val sub = a.subLocality ?: a.locality
                    when {
                        !street.isNullOrBlank() && !sub.isNullOrBlank() -> "Posto $street ($sub)"
                        !street.isNullOrBlank() -> "Posto $street"
                        !sub.isNullOrBlank() -> "Posto $sub"
                        else -> null
                    }
                }
            }.getOrNull()
        }
        if (name != null && station.isBlank()) station = name
    }

    if (pickOnMap) {
        LocationPickerMapDialog(
            title = station.ifBlank { "Posto de abastecimento" },
            initialLat = lat ?: currentGps?.first ?: -23.5672,
            initialLon = lon ?: currentGps?.second ?: -46.6685,
            currentGps = currentGps,
            onConfirm = { la, lo -> lat = la; lon = lo; pickOnMap = false },
            onDismiss = { pickOnMap = false }
        )
        return
    }

    val total = valueOf(Field.TOTAL)
    val price = valueOf(Field.PRICE)
    val liters = valueOf(Field.LITERS)
    val canSave = total != null && total > 0 && price != null && price > 0 && liters != null && liters > 0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CyberInk)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(CyberOrange), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = CyberOnAcid, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ABASTECIMENTO", style = EyebrowStyle, color = CyberOrange)
                    Text("REGISTRAR", style = displayStyle(30), color = CyberText)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = CyberText) }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(CyberOrange))

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Fuel type
                Section("Combustível") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FUEL_TYPES.forEach { t ->
                            SelectBox(t, selected = t == fuelType, accent = CyberAcid) {
                                fuelType = t
                                // follow the price last paid for this fuel unless the driver typed one
                                // or it is the computed field
                                if (!priceTypedByUser && derived != Field.PRICE) lastPrice(t)?.let { p ->
                                    priceText = FuelAmounts.format(Field.PRICE, p)
                                    if (Field.PRICE !in order) order = FuelAmounts.touch(order, Field.PRICE)
                                    recompute()
                                }
                            }
                        }
                    }
                }

                // Amounts
                Section("Valores", hint = "Preencha dois: o terceiro é calculado") {
                    AmountField("Total pago", "R$", totalText, derived == Field.TOTAL, big = true) { type(Field.TOTAL, it) }
                    QuickRow(listOf(50, 100, 150, 200).map { "R$ $it" to { type(Field.TOTAL, "$it,00") } })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AmountField("Preço/L", "R$", priceText, derived == Field.PRICE, Modifier.weight(1f)) { type(Field.PRICE, it) }
                        AmountField("Litros", "L", litersText, derived == Field.LITERS, Modifier.weight(1f)) { type(Field.LITERS, it) }
                    }
                    QuickRow(
                        listOfNotNull(
                            litersToFill?.takeIf { it >= 1 }?.let { l -> "Completar ~${l.toInt()} L" to { isFullTank = true; type(Field.LITERS, FuelAmounts.format(Field.LITERS, l)) } }
                        ) + listOf(20, 30, 40).map { "$it L" to { type(Field.LITERS, "$it,00") } }
                    )
                    SummaryBand(total, price, liters)
                }

                // Tank state
                Section("Tanque") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectBox("Tanque cheio", isFullTank, CyberGreen, Modifier.weight(1f)) { isFullTank = true }
                        SelectBox("Parcial", !isFullTank, CyberOrange, Modifier.weight(1f)) { isFullTank = false }
                    }
                    Text(
                        if (isFullTank) "Tanque cheio fecha a medição: o consumo real é calculado a partir daqui."
                        else "Parciais somam litros até o próximo tanque cheio.",
                        style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary
                    )
                }

                // Station
                Section("Posto") {
                    CyberField(station, { station = it }, "Nome do posto")
                    val recent = history.sortedByDescending { it.dateTime }.map { it.stationName }.filter { it.isNotBlank() }.distinct().take(6)
                    if (recent.isNotEmpty()) QuickRow(recent.map { it to { station = it } }, color = CyberCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CyberGhostButton(if (lat != null) "Mudar no mapa" else "Marcar no mapa", { pickOnMap = true }, Modifier.weight(1f), Icons.Default.Map, CyberMagenta, 42.dp)
                        if (currentGps != null) CyberGhostButton("Aqui", { lat = currentGps.first; lon = currentGps.second }, icon = Icons.Default.MyLocation, accent = CyberCyan, height = 42.dp)
                    }
                    Text(
                        if (lat != null && lon != null) String.format(Locale.US, "Local: %.5f, %.5f", lat, lon) else "Sem local: dá para marcar depois na lista.",
                        style = monoStyle(11, FontWeight.Normal), color = if (lat != null) CyberCyan else CyberTextTertiary
                    )
                }

                Section("Odômetro e observações") {
                    CyberField(odoText, { odoText = it }, "Km no painel", KeyboardType.Number, suffix = "km")
                    CyberField(notes, { notes = it }, "Observações (opcional)")
                }
            }

            Box(Modifier.padding(16.dp)) {
                CyberActionButton(
                    text = if (canSave) "Salvar · R$ ${FuelAmounts.format(Field.TOTAL, total!!)}" else "Preencha os valores",
                    icon = Icons.Default.LocalGasStation,
                    fill = CyberAcid,
                    enabled = canSave,
                    onClick = {
                        onSave(
                            station.ifBlank { "Posto" }, liters!!, price!!, total!!,
                            FuelAmounts.parse(odoText) ?: 0.0, notes, isFullTank, fuelType, lat, lon
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 14.dp, height = 3.dp).background(CyberAcid))
            Spacer(Modifier.width(6.dp))
            Text(title.uppercase(), style = EyebrowStyle, color = CyberAcid)
        }
        if (hint != null) Text(hint, style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary)
        content()
    }
}

@Composable
private fun SelectBox(text: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .background(if (selected) accent else CyberPanel, RectangleShape)
            .border(1.dp, if (selected) accent else CyberLine, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), style = EyebrowStyle.copy(fontSize = 12.sp), color = if (selected) contentOn(accent) else CyberTextSecondary, maxLines = 1)
    }
}

@Composable
private fun QuickRow(items: List<Pair<String, () -> Unit>>, color: Color = CyberGreen) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (label, action) ->
            Box(
                Modifier
                    .background(color.copy(alpha = 0.12f), RectangleShape)
                    .border(1.dp, color.copy(alpha = 0.6f), RectangleShape)
                    .clickable(onClick = action)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(label, style = monoStyle(12), color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AmountField(
    label: String,
    unit: String,
    value: String,
    computed: Boolean,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    onChange: (String) -> Unit
) {
    val accent = if (computed) CyberCyan else CyberAcid
    OutlinedTextField(
        value = value,
        onValueChange = { t -> onChange(t.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(if (computed) "$label (auto)" else label) },
        prefix = { Text("$unit ", style = monoStyle(if (big) 18 else 14), color = CyberTextSecondary) },
        textStyle = monoStyle(if (big) 28 else 18, FontWeight.Bold).copy(color = if (computed) CyberCyan else CyberText),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = if (computed) CyberCyan.copy(alpha = 0.6f) else CyberLine,
            focusedLabelColor = accent,
            unfocusedLabelColor = if (computed) CyberCyan else CyberTextSecondary,
            cursorColor = CyberAcid,
            focusedContainerColor = CyberPanel,
            unfocusedContainerColor = CyberPanel
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun CyberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType = KeyboardType.Text,
    suffix: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it, color = CyberTextSecondary) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberAcid,
            unfocusedBorderColor = CyberLine,
            focusedLabelColor = CyberAcid,
            cursorColor = CyberAcid,
            focusedContainerColor = CyberPanel,
            unfocusedContainerColor = CyberPanel
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Receipt-style line confirming what will be saved. */
@Composable
private fun SummaryBand(total: Double?, price: Double?, liters: Double?) {
    val ready = total != null && price != null && liters != null && total > 0 && price > 0 && liters > 0
    Row(
        Modifier
            .fillMaxWidth()
            .cyberPanel(border = if (ready) CyberAcid else CyberLine, background = if (ready) CyberAcid else CyberPanel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ready) {
            Column(Modifier.weight(1f)) {
                Text("VOCÊ ABASTECEU", style = EyebrowStyle, color = CyberOnAcidSecondary)
                Text("${FuelAmounts.format(Field.LITERS, liters!!)} L × R$ ${FuelAmounts.format(Field.PRICE, price!!)}", style = monoStyle(14), color = CyberOnAcid)
            }
            Text("R$ ${FuelAmounts.format(Field.TOTAL, total!!)}", style = displayStyle(32), color = CyberOnAcid)
        } else {
            Text("Ex.: total R$ 150 e preço R$ 5,89 → 25,47 L", style = monoStyle(12, FontWeight.Normal), color = CyberTextSecondary)
        }
    }
}
