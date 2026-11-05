package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.MaintenanceItem
import de.dennisguse.opentracks.util.MaintenanceState
import de.dennisguse.opentracks.util.MaintenanceStatus
import de.dennisguse.opentracks.util.MaintenanceStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val PT_BR = Locale("pt", "BR")

fun maintenanceColor(status: MaintenanceStatus): Color = when (status) {
    MaintenanceStatus.DUE -> CyberRed
    MaintenanceStatus.SOON -> CyberOrange
    MaintenanceStatus.OK -> CyberCyan
}

@Composable
fun MaintenanceScreen(
    items: List<MaintenanceItem>,
    odometerKm: Double,
    onSave: (List<MaintenanceItem>) -> Unit,
    onBack: () -> Unit
) {
    var editing by remember { mutableStateOf<MaintenanceItem?>(null) }
    var isNew by remember { mutableStateOf(false) }
    val states = items.map { MaintenanceStore.evaluate(it, odometerKm) }
        .sortedWith(compareBy<MaintenanceState> { it.status != MaintenanceStatus.DUE }
            .thenBy { it.status != MaintenanceStatus.SOON }
            .thenBy { it.item.lastDoneKm == null && it.item.lastDoneMs == null }
            .thenByDescending { it.progress })

    editing?.let { item ->
        MaintenanceEditDialog(
            item = item,
            isNew = isNew,
            odometerKm = odometerKm,
            onConfirm = { updated ->
                onSave(if (isNew) items + updated else items.map { if (it.id == updated.id) updated else it })
                editing = null
            },
            onDelete = if (isNew) null else ({
                onSave(items.filterNot { it.id == item.id })
                editing = null
            }),
            onDismiss = { editing = null }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = CyberAcid) }
            Column(Modifier.weight(1f)) {
                Text("MANUTENÇÃO", style = displayStyle(28), color = CyberText)
                Text(String.format(PT_BR, "Odômetro: %,.0f km", odometerKm), style = monoStyle(11, FontWeight.Normal), color = TextSecondary)
            }
            IconButton(onClick = {
                isNew = true
                editing = MaintenanceItem(MaintenanceStore.nextId(items), "", 10_000, 12, odometerKm, System.currentTimeMillis())
            }) { Icon(Icons.Default.Add, contentDescription = "Novo lembrete", tint = CyberAcid) }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyberAcid))

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            item {
                Text(
                    "Toque em um item para ajustar o intervalo e a última vez que foi feito. Você recebe uma notificação 1.000 km ou 30 dias antes e quando vencer.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            items(states, key = { it.item.id }) { st ->
                MaintenanceRow(
                    state = st,
                    onClick = {
                        isNew = false
                        editing = st.item
                    },
                    onDoneToday = {
                        onSave(items.map {
                            if (it.id == st.item.id) it.copy(lastDoneKm = odometerKm, lastDoneMs = System.currentTimeMillis()) else it
                        })
                    }
                )
            }
        }
    }
}

@Composable
private fun MaintenanceRow(state: MaintenanceState, onClick: () -> Unit, onDoneToday: () -> Unit) {
    val item = state.item
    val noHistory = item.lastDoneKm == null && item.lastDoneMs == null
    val color = if (noHistory) CyberTextTertiary else maintenanceColor(state.status)
    Row(
        Modifier
            .fillMaxWidth()
            .cyberPanel(border = if (state.status == MaintenanceStatus.OK || noHistory) CyberLine else color)
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, color = CyberText, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                CyberChip(
                    text = when {
                        noHistory -> "Configurar"
                        state.status == MaintenanceStatus.DUE -> "Vencido"
                        state.status == MaintenanceStatus.SOON -> "Em breve"
                        else -> "Em dia"
                    },
                    color = color,
                    filled = state.status == MaintenanceStatus.DUE && !noHistory
                )
            }
            Text(
                listOfNotNull(
                    item.intervalKm?.let { String.format(PT_BR, "a cada %,d km", it) },
                    item.intervalMonths?.let { "a cada $it ${if (it == 1) "mês" else "meses"}" }
                ).joinToString(" ou "),
                style = monoStyle(11, FontWeight.Normal),
                color = CyberTextSecondary
            )
            if (!noHistory) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).background(CyberPanelRaised)) {
                    Box(Modifier.fillMaxWidth(state.progress.toFloat().coerceAtLeast(0.02f)).fillMaxHeight().background(color))
                }
                Spacer(Modifier.height(4.dp))
                Text(state.summary.replaceFirstChar { it.uppercase() }, style = monoStyle(11, FontWeight.Medium), color = color)
            }
        }
        Box(
            Modifier
                .fillMaxHeight()
                .width(64.dp)
                .background(CyberPanelRaised)
                .clickable(onClick = onDoneToday),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Build, contentDescription = "Feito hoje", tint = CyberAcid, modifier = Modifier.size(20.dp))
                Text("FEITO\nHOJE", style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = CyberText, lineHeight = 11.sp)
            }
        }
    }
}

@Composable
private fun MaintenanceEditDialog(
    item: MaintenanceItem,
    isNew: Boolean,
    odometerKm: Double,
    onConfirm: (MaintenanceItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    var name by remember { mutableStateOf(item.name) }
    var km by remember { mutableStateOf(item.intervalKm?.toString() ?: "") }
    var months by remember { mutableStateOf(item.intervalMonths?.toString() ?: "") }
    var lastKm by remember { mutableStateOf(item.lastDoneKm?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var lastDate by remember {
        mutableStateOf(item.lastDoneMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(DATE_FMT) } ?: "")
    }
    val dateValid = lastDate.isBlank() || runCatching { LocalDate.parse(lastDate, DATE_FMT) }.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = CyberPanelRaised,
        title = { Text(if (isNew) "NOVO LEMBRETE" else "EDITAR LEMBRETE", style = displayStyle(26), color = CyberText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Item", name, { name = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("A cada (km)", km, { km = it.filter(Char::isDigit) }, KeyboardType.Number, Modifier.weight(1f))
                    Field("A cada (meses)", months, { months = it.filter(Char::isDigit) }, KeyboardType.Number, Modifier.weight(1f))
                }
                Text("Última vez feito", style = EyebrowStyle, color = CyberAcid)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Odômetro (km)", lastKm, { lastKm = it.filter(Char::isDigit) }, KeyboardType.Number, Modifier.weight(1f))
                    Field("Data (dd/mm/aaaa)", lastDate, { lastDate = it }, KeyboardType.Number, Modifier.weight(1f), isError = !dateValid)
                }
                TextButton(shape = RectangleShape, onClick = {
                    lastKm = String.format(Locale.US, "%.0f", odometerKm)
                    lastDate = LocalDate.now(zone).format(DATE_FMT)
                }) { Text("USAR HOJE E O ODÔMETRO ATUAL", style = EyebrowStyle, color = CyberAcid) }
                if (onDelete != null) {
                    TextButton(shape = RectangleShape, onClick = onDelete) {
                        Text("EXCLUIR LEMBRETE", style = EyebrowStyle, color = CyberRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RectangleShape,
                enabled = name.isNotBlank() && dateValid && (km.isNotBlank() || months.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = CyberAcid, contentColor = CyberOnAcid),
                onClick = {
                    onConfirm(
                        item.copy(
                            name = name.trim(),
                            intervalKm = km.toIntOrNull()?.takeIf { it > 0 },
                            intervalMonths = months.toIntOrNull()?.takeIf { it > 0 },
                            lastDoneKm = lastKm.toDoubleOrNull(),
                            lastDoneMs = lastDate.takeIf { it.isNotBlank() }?.let {
                                LocalDate.parse(it, DATE_FMT).atStartOfDay(zone).toInstant().toEpochMilli()
                            }
                        )
                    )
                }
            ) { Text("SALVAR", style = EyebrowStyle) }
        },
        dismissButton = {
            TextButton(shape = RectangleShape, onClick = onDismiss) { Text("CANCELAR", style = EyebrowStyle, color = TextSecondary) }
        }
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        isError = isError,
        shape = RectangleShape,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CyberAcid,
            unfocusedBorderColor = CyberLine,
            focusedLabelColor = CyberAcid,
            unfocusedLabelColor = TextSecondary,
            focusedTextColor = CyberText,
            unfocusedTextColor = CyberText,
            cursorColor = CyberAcid
        )
    )
}

/** Dashboard card: the most urgent reminder, opens the maintenance screen. */
@Composable
fun MaintenanceSummaryCard(items: List<MaintenanceItem>, odometerKm: Double, onClick: () -> Unit) {
    val configured = items.filter { it.lastDoneKm != null || it.lastDoneMs != null }
    val states = configured.map { MaintenanceStore.evaluate(it, odometerKm) }
    val due = states.count { it.status == MaintenanceStatus.DUE }
    val soon = states.count { it.status == MaintenanceStatus.SOON }
    val top = states.maxByOrNull { it.progress }
    val color = when {
        due > 0 -> CyberRed
        soon > 0 -> CyberOrange
        configured.isEmpty() -> CyberTextTertiary
        else -> CyberCyan
    }
    Row(
        Modifier
            .fillMaxWidth()
            .cyberPanel(border = color)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(color), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Build, contentDescription = null, tint = CyberOnAcid, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("MANUTENÇÃO", style = displayStyle(20), color = CyberText)
            Text(
                when {
                    configured.isEmpty() -> "Configure óleo, pneus, revisão e IPVA"
                    due > 0 -> "$due vencido(s)" + if (soon > 0) " · $soon em breve" else ""
                    soon > 0 -> "$soon em breve"
                    else -> "Tudo em dia"
                },
                style = EyebrowStyle,
                color = color
            )
            if (top != null) {
                Text("${top.item.name}: ${top.summary}", color = TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CyberAcid)
    }
}
