package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.data.Expense
import de.dennisguse.opentracks.data.ExpenseCategory
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.MaintenanceItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BR = Locale("pt", "BR")
private val DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun brl(v: Double): String = String.format(BR, "R$ %,.2f", v)

/** Costs tab: fuel (existing screen) plus every other car expense, with a monthly summary. */
@Composable
fun CostsScreen(
    fills: List<FuelItem>,
    expenses: List<Expense>,
    trips: List<TripItem>,
    maintenance: List<MaintenanceItem>,
    currentOdometer: Double,
    onSaveExpense: (Expense, markDoneId: Int?) -> Unit,
    onDeleteExpense: (Long) -> Unit,
    fuelContent: @Composable () -> Unit
) {
    var tab by remember { mutableStateOf(0) }
    val zone = ZoneId.systemDefault()
    val monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val fuelMonth = fills.filter { it.dateTime >= monthStart }.sumOf { it.totalCost }
    val otherMonth = expenses.filter { it.dateTime >= monthStart }.sumOf { it.amount }
    val kmMonth = trips.filter { it.startTimeMs >= monthStart }.sumOf { it.distanceKm }

    Column(Modifier.fillMaxSize().background(CyberInk)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
            Text("CUSTOS", style = displayStyle(40), color = CyberAcid)
            Text("// COMBUSTÍVEL, MANUTENÇÃO E DESPESAS", style = EyebrowStyle, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CyberOnAcid)
                    .border(1.dp, CyberAcid)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryStat("Mês · combustível", brl(fuelMonth), CyberOrange)
                SummaryStat("Mês · outros", brl(otherMonth), CyberPink)
                SummaryStat("Custo/km", if (kmMonth > 0) brl((fuelMonth + otherMonth) / kmMonth) else "—", CyberAcid)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("Combustível", "Outros custos").forEachIndexed { i, label ->
                    val sel = i == tab
                    Box(
                        Modifier
                            .weight(1f)
                            .background(if (sel) CyberAcid else CyberPanel)
                            .border(1.dp, if (sel) CyberAcid else CyberLine)
                            .clickable { tab = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label.uppercase(), style = EyebrowStyle, color = if (sel) CyberOnAcid else CyberText)
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (tab == 0) fuelContent() else ExpensesTab(expenses, maintenance, currentOdometer, onSaveExpense, onDeleteExpense)
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(label.uppercase(), style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.8.sp), color = TextSecondary)
        Text(value, style = monoStyle(14, FontWeight.Bold), color = color, maxLines = 1)
    }
}

@Composable
private fun ExpensesTab(
    expenses: List<Expense>,
    maintenance: List<MaintenanceItem>,
    currentOdometer: Double,
    onSave: (Expense, Int?) -> Unit,
    onDelete: (Long) -> Unit
) {
    var editing by remember { mutableStateOf<Expense?>(null) }
    editing?.let { e ->
        ExpenseDialog(
            expense = e,
            maintenance = maintenance,
            onConfirm = { saved, markDone ->
                onSave(saved, markDone)
                editing = null
            },
            onDelete = if (e.id == 0L) null else ({ onDelete(e.id); editing = null }),
            onDismiss = { editing = null }
        )
    }
    val byCategory = expenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
        .entries.sortedByDescending { it.value }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CyberActionButton(
                text = "Adicionar custo",
                icon = Icons.Default.Add,
                height = 52.dp,
                onClick = {
                    editing = Expense(0, System.currentTimeMillis(), ExpenseCategory.MANUTENCAO, "", 0.0, currentOdometer, null)
                }
            )
        }
        if (byCategory.isNotEmpty()) {
            item { CyberSectionHeader("Por categoria", eyebrow = "Total " + brl(expenses.sumOf { it.amount })) }
            item {
                val max = byCategory.first().value.coerceAtLeast(0.01)
                Column(
                    Modifier.fillMaxWidth().cyberPanel().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    byCategory.forEach { (cat, total) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${cat.emoji} ${cat.label}", color = CyberText, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(brl(total), style = monoStyle(12), color = CyberTextSecondary)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).background(CyberPanelRaised)) {
                            Box(Modifier.fillMaxWidth((total / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(CyberPink))
                        }
                    }
                }
            }
            item { CyberSectionHeader("Lançamentos", eyebrow = "${expenses.size} registros") }
        } else {
            item {
                Text(
                    "Registre troca de óleo, amortecedores, pneus, pedágios, estacionamento, lavagem, seguro, IPVA e multas. " +
                        "Eles entram no custo por km e no relatório.",
                    color = TextSecondary, fontSize = 13.sp
                )
            }
        }
        items(expenses, key = { it.id }) { e ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .cyberPanel()
                    .clickable { editing = e }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(e.category.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.description.ifBlank { e.category.label },
                        color = CyberText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            Instant.ofEpochMilli(e.dateTime).atZone(ZoneId.systemDefault()).toLocalDate().format(DAY),
                            e.category.label.substringBefore(" /"),
                            e.odometerKm?.let { String.format(BR, "%,.0f km", it) }
                        ).joinToString(" · "),
                        style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary, maxLines = 1
                    )
                }
                Text(brl(e.amount), style = monoStyle(14, FontWeight.Bold), color = CyberPink)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(
    expense: Expense,
    maintenance: List<MaintenanceItem>,
    onConfirm: (Expense, Int?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val zone = ZoneId.systemDefault()
    var category by remember { mutableStateOf(expense.category) }
    var description by remember { mutableStateOf(expense.description) }
    var amount by remember { mutableStateOf(if (expense.amount > 0) String.format(Locale.US, "%.2f", expense.amount) else "") }
    var date by remember { mutableStateOf(Instant.ofEpochMilli(expense.dateTime).atZone(zone).toLocalDate().format(DAY)) }
    var odometer by remember { mutableStateOf(expense.odometerKm?.let { String.format(Locale.US, "%.0f", it) } ?: "") }
    var markDone by remember { mutableStateOf(expense.maintenanceId) }
    val dateOk = runCatching { LocalDate.parse(date, DAY) }.isSuccess
    val amountValue = amount.replace(',', '.').toDoubleOrNull()
    val canLink = category in setOf(ExpenseCategory.MANUTENCAO, ExpenseCategory.PECAS, ExpenseCategory.PNEUS, ExpenseCategory.IPVA)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = CyberPanelRaised,
        title = { Text(if (expense.id == 0L) "NOVO CUSTO" else "EDITAR CUSTO", style = displayStyle(26), color = CyberText) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.values().forEach { c ->
                        val sel = c == category
                        Box(
                            Modifier
                                .background(if (sel) CyberAcid else CyberPanel)
                                .border(1.dp, if (sel) CyberAcid else CyberLine)
                                .clickable { category = c }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("${c.emoji} ${c.label}", fontSize = 12.sp, color = if (sel) CyberOnAcid else CyberText)
                        }
                    }
                }
                CostField("Descrição (ex.: troca de amortecedores)", description, { description = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CostField("Valor (R$)", amount, { amount = it }, KeyboardType.Decimal, Modifier.weight(1f), isError = amount.isNotBlank() && amountValue == null)
                    CostField("Data", date, { date = it }, KeyboardType.Number, Modifier.weight(1f), isError = !dateOk)
                }
                CostField("Odômetro (km)", odometer, { odometer = it.filter(Char::isDigit) }, KeyboardType.Number)
                if (canLink && maintenance.isNotEmpty()) {
                    Text("MARCAR LEMBRETE COMO FEITO", style = EyebrowStyle, color = CyberAcid)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf<MaintenanceItem?>(null) + maintenance).forEach { m ->
                            val sel = markDone == m?.id
                            Box(
                                Modifier
                                    .background(if (sel) CyberCyan else CyberPanel)
                                    .border(1.dp, if (sel) CyberCyan else CyberLine)
                                    .clickable { markDone = m?.id }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(m?.name ?: "Nenhum", fontSize = 12.sp, color = if (sel) CyberOnAcid else CyberText)
                            }
                        }
                    }
                }
                if (onDelete != null) {
                    TextButton(shape = RectangleShape, onClick = onDelete) { Text("EXCLUIR CUSTO", style = EyebrowStyle, color = CyberRed) }
                }
            }
        },
        confirmButton = {
            Button(
                shape = RectangleShape,
                enabled = amountValue != null && amountValue > 0 && dateOk,
                colors = ButtonDefaults.buttonColors(containerColor = CyberAcid, contentColor = CyberOnAcid),
                onClick = {
                    val day = LocalDate.parse(date, DAY)
                    val original = Instant.ofEpochMilli(expense.dateTime).atZone(zone)
                    // keep the time of day when the date is unchanged, so ordering stays natural
                    val ms = if (original.toLocalDate() == day) expense.dateTime
                    else day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                    onConfirm(
                        expense.copy(
                            dateTime = ms,
                            category = category,
                            description = description.trim(),
                            amount = amountValue!!,
                            odometerKm = odometer.toDoubleOrNull(),
                            maintenanceId = if (canLink) markDone else null
                        ),
                        if (canLink) markDone else null
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
private fun CostField(
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
