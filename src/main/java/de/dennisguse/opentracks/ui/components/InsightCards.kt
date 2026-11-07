package de.dennisguse.opentracks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.DepartureWindow
import de.dennisguse.opentracks.util.DrivingInsights
import de.dennisguse.opentracks.util.Insight
import de.dennisguse.opentracks.util.PersonalRecord
import de.dennisguse.opentracks.util.RecordKind
import de.dennisguse.opentracks.util.RouteComparison
import java.util.Locale

/** Short insight cards with 👍/👎; 👎 hides that kind of insight for good. */
@Composable
fun InsightsCard(insights: List<Insight>, onFeedback: (id: String, useful: Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        insights.forEach { insight ->
            val accent = when (insight.tone) {
                1 -> CyberGreen
                -1 -> CyberOrange
                else -> CyberCyan
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = CyberLine)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
                Text(insight.emoji, fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
                Text(
                    insight.text,
                    style = monoStyle(13, FontWeight.Medium).copy(lineHeight = 18.sp),
                    color = CyberText,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp)
                )
                FeedbackButton("👍") { onFeedback(insight.id, true) }
                FeedbackButton("👎") { onFeedback(insight.id, false) }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun FeedbackButton(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(36.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(emoji, fontSize = 15.sp) }
}

/** Personal records; a freshly broken one carries the "NOVO RECORDE" seal. */
@Composable
fun RecordsCard(records: List<PersonalRecord>, newKinds: Set<RecordKind>, onRecordClick: (PersonalRecord) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        records.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { r ->
                    val isNew = r.kind in newKinds
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .cyberPanel(border = if (isNew) CyberAcid else CyberLine, background = if (isNew) CyberPanelRaised else CyberPanel)
                            .clickable { onRecordClick(r) }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.kind.emoji, fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                r.kind.title.uppercase(),
                                style = EyebrowStyle.copy(fontSize = 9.sp, lineHeight = 12.sp),
                                color = CyberTextSecondary,
                                maxLines = 2
                            )
                        }
                        Text(r.display, style = displayStyle(26), color = if (isNew) CyberAcid else CyberText, maxLines = 1)
                        Text(r.detail, style = monoStyle(10, FontWeight.Normal), color = CyberTextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AnimatedVisibility(isNew) {
                            CyberChip("🏆 Novo recorde", CyberAcid, Modifier.padding(top = 6.dp), filled = true)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** "Você sai de casa entre 7h40 e 7h55 em 80% dos dias úteis" with the histogram and the band. */
@Composable
fun RegularityCard(w: DepartureWindow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberPanel(border = CyberLine)
            .padding(14.dp)
    ) {
        Text("SAÍDA DE CASA · DIAS ÚTEIS", style = EyebrowStyle, color = CyberCyan)
        Spacer(Modifier.height(4.dp))
        Text(
            "${DrivingInsights.formatMinuteOfDay(w.startMinute)} – ${DrivingInsights.formatMinuteOfDay(w.endMinute)}",
            style = displayStyle(34),
            color = CyberAcid
        )
        Text(
            "em ${(w.share * 100).toInt()}% de ${w.days} dias" + if (w.to.isNotBlank()) " · rumo a ${w.to}" else "",
            style = monoStyle(12, FontWeight.Normal),
            color = CyberTextSecondary
        )
        Spacer(Modifier.height(12.dp))
        val bins = w.histogram
        val maxBin = (bins.maxOrNull() ?: 1).coerceAtLeast(1)
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            if (bins.isEmpty()) return@Canvas
            val bw = size.width / bins.size
            // the band holding most departures
            val a = (w.startMinute - w.histogramStartMinute) / 10f * bw
            val b = (w.endMinute - w.histogramStartMinute) / 10f * bw
            drawRect(CyberAcid.copy(alpha = 0.12f), Offset(a, 0f), Size((b - a).coerceAtLeast(2f), size.height))
            bins.forEachIndexed { i, n ->
                val h = size.height * n / maxBin
                val start = w.histogramStartMinute + i * 10
                val inBand = start + 10 > w.startMinute && start < w.endMinute
                drawRect(
                    if (inBand) CyberAcid else CyberLine,
                    Offset(i * bw + bw * 0.15f, size.height - h),
                    Size(bw * 0.7f, h)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(DrivingInsights.formatMinuteOfDay(w.histogramStartMinute), style = monoStyle(10, FontWeight.Normal), color = CyberTextTertiary)
            Text("mediana ${DrivingInsights.formatMinuteOfDay(w.medianMinute)}", style = monoStyle(10, FontWeight.Normal), color = CyberTextTertiary)
            Text(
                DrivingInsights.formatMinuteOfDay(w.histogramStartMinute + w.histogram.size * 10),
                style = monoStyle(10, FontWeight.Normal),
                color = CyberTextTertiary
            )
        }
    }
}

/** The paths already taken between the same two places, with median time and km/L of each. */
@Composable
fun RouteVariantsCard(cmp: RouteComparison, onShowOnMap: (List<Long>, String) -> Unit) {
    val fastest = cmp.variants.minByOrNull { it.medianMin }
    val thriftiest = cmp.variants.filter { it.avgKmL > 0 }.maxByOrNull { it.avgKmL }
    val br = Locale("pt", "BR")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cyberPanel(border = CyberLine)
            .padding(14.dp)
    ) {
        Text("${cmp.from.uppercase()} → ${cmp.to.uppercase()}", style = EyebrowStyle, color = CyberPink)
        Spacer(Modifier.height(8.dp))
        cmp.variants.forEach { v ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowOnMap(v.tripIds, "${cmp.from} → ${cmp.to} · ${v.name}") }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = displayStyle(20), color = CyberText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${v.tripIds.size} viagens · ${String.format(br, "%.1f", v.avgKm)} km",
                        style = monoStyle(11, FontWeight.Normal),
                        color = CyberTextTertiary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        if (v === fastest) CyberChip("Mais rápido", CyberAcid)
                        if (v === thriftiest) CyberChip("Mais econômico", CyberGreen)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${v.medianMin.toInt()} min", style = displayStyle(24), color = if (v === fastest) CyberAcid else CyberText)
                    Text(
                        if (v.avgKmL > 0) String.format(br, "%.1f km/L", v.avgKmL) else "—",
                        style = monoStyle(11),
                        color = if (v === thriftiest) CyberGreen else CyberTextSecondary
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Ver no mapa", tint = CyberTextTertiary)
            }
            if (v !== cmp.variants.last()) Box(Modifier.fillMaxWidth().height(1.dp).background(CyberLineSubtle))
        }
        Text("tempo mediano · toque para ver no mapa", style = monoStyle(10, FontWeight.Normal), color = CyberTextTertiary)
    }
}

/** Entry to the animated year recap. */
@Composable
fun YearRecapBanner(year: Int, km: Double, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberMagenta, RectangleShape)
            .border(1.dp, CyberPink, RectangleShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberAcid, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("RETROSPECTIVA $year", style = displayStyle(24), color = CyberOnMagenta)
            Text(
                String.format(Locale("pt", "BR"), "%,.0f km até agora · animada e compartilhável", km),
                style = monoStyle(11, FontWeight.Normal),
                color = CyberOnMagenta.copy(alpha = 0.85f)
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CyberAcid)
    }
}
