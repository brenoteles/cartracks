package de.dennisguse.opentracks.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.io.ReportImageExporter
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.PersonalRecord
import de.dennisguse.opentracks.util.RecordKind
import de.dennisguse.opentracks.util.YearRecap
import java.text.NumberFormat
import java.time.Year
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PtBrLocale = Locale("pt", "BR")
private const val STORY_PAGE_DURATION_MS = 5000
private const val SP_RIO_DISTANCE_KM = 430.0

private val MonthNamesShort = listOf(
    "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
    "JUL", "AGO", "SET", "OUT", "NOV", "DEZ"
)

private val MonthNamesFull = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

private enum class RecapStoryPage {
    INTRO,
    KM_OF_YEAR,
    HOURS_AT_WHEEL,
    FUEL_AND_SPENT,
    KM_BY_MONTH,
    PLACES,
    RECORDS,
    SUMMARY
}

/**
 * Full-screen animated "Retrospectiva do ano" story with ~8 pages, auto-advancing every ~5s,
 * tap navigation (left = previous, right = next, long-press = pause), year switcher chips,
 * and one-tap PNG export via [ReportImageExporter].
 */
@Composable
fun YearRecapScreen(
    recap: YearRecap,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summaryLayer = rememberGraphicsLayer()

    // Empty year state: single page with back and optional year chips.
    if (recap.stats.trips == 0) {
        EmptyYearRecapContent(
            year = recap.year,
            availableYears = availableYears,
            onYearSelected = onYearSelected,
            onBack = onBack
        )
        return
    }

    val pages = remember(recap) {
        buildList {
            add(RecapStoryPage.INTRO)
            add(RecapStoryPage.KM_OF_YEAR)
            add(RecapStoryPage.HOURS_AT_WHEEL)
            add(RecapStoryPage.FUEL_AND_SPENT)
            add(RecapStoryPage.KM_BY_MONTH)
            add(RecapStoryPage.PLACES)
            if (recap.records.isNotEmpty()) {
                add(RecapStoryPage.RECORDS)
            }
            add(RecapStoryPage.SUMMARY)
        }
    }

    var pageIndex by remember(recap.year, pages.size) { mutableStateOf(0) }
    var restartTick by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val safeIndex = pageIndex.coerceIn(0, pages.lastIndex)
    val currentPage = pages[safeIndex]

    val pageProgress = remember { Animatable(0f) }

    // Reset progress whenever the active page or year changes.
    LaunchedEffect(safeIndex, recap.year, restartTick) {
        pageProgress.snapTo(0f)
    }

    // Auto-advance every ~5 seconds unless paused or exporting.
    LaunchedEffect(safeIndex, recap.year, restartTick, isPaused, exporting, pages.size) {
        if (isPaused || exporting) return@LaunchedEffect
        val currentFraction = pageProgress.value.coerceIn(0f, 1f)
        if (currentFraction < 1f) {
            val remainingMs = ((1f - currentFraction) * STORY_PAGE_DURATION_MS).toInt().coerceAtLeast(40)
            pageProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing)
            )
            if (safeIndex < pages.lastIndex) {
                pageIndex = safeIndex + 1
            }
        }
    }

    fun triggerShare() {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                if (pageIndex != pages.lastIndex) {
                    pageIndex = pages.lastIndex
                }
                // Wait for the summary page composition, layout, and drawLayer recording.
                withFrameNanos { }
                delay(350)
                withFrameNanos { }
                val bodyBitmap = summaryLayer.toImageBitmap().asAndroidBitmap()
                val subtitle = String.format(
                    PtBrLocale,
                    "%d viagens · %d dias ao volante · %s km",
                    recap.stats.trips,
                    recap.daysDriven,
                    formatNumber(recap.stats.km, decimals = 1)
                )
                val composed = ReportImageExporter.compose(
                    context = context,
                    title = "CARTRACKS · RETROSPECTIVA ${recap.year}",
                    subtitle = subtitle,
                    mapIn = null,
                    bodyIn = bodyBitmap
                )
                ReportImageExporter.share(
                    context = context,
                    image = composed,
                    fileName = "CarTracks_Retrospectiva_${recap.year}.png"
                )
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Falha ao gerar imagem: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                exporting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Segmented story progress bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            pages.forEachIndexed { idx, _ ->
                val fillFraction = when {
                    idx < safeIndex -> 1f
                    idx == safeIndex -> pageProgress.value.coerceIn(0f, 1f)
                    else -> 0f
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(CyberLine, RectangleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .fillMaxHeight()
                            .background(
                                if (idx == safeIndex) CyberAcid else CyberAcidMuted,
                                RectangleShape
                            )
                    )
                }
            }
        }

        // Top Bar: Back arrow, title + page indicator, Share button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Voltar",
                    tint = CyberAcid
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "RETROSPECTIVA ${recap.year}",
                        style = displayStyle(24),
                        color = CyberText
                    )
                    if (isPaused) {
                        CyberChip(text = "PAUSADO", color = CyberPink, filled = true)
                    }
                }
                Text(
                    text = String.format(PtBrLocale, "ETAPA %02d DE %02d", safeIndex + 1, pages.size),
                    style = EyebrowStyle.copy(fontSize = 10.sp),
                    color = CyberTextSecondary
                )
            }
            if (exporting) {
                CircularProgressIndicator(
                    color = CyberAcid,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(22.dp)
                )
            } else {
                IconButton(onClick = { triggerShare() }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartilhar retrospectiva",
                        tint = CyberAcid
                    )
                }
            }
        }

        // Year switcher chips when availableYears has more than one year
        if (availableYears.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableYears) { yr ->
                    val selected = yr == recap.year
                    Box(
                        modifier = Modifier
                            .background(if (selected) CyberAcid else CyberPanel, RectangleShape)
                            .border(1.dp, if (selected) CyberAcid else CyberLine, RectangleShape)
                            .clickable {
                                if (yr != recap.year) onYearSelected(yr)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = yr.toString(),
                            style = EyebrowStyle,
                            color = if (selected) CyberOnAcid else CyberText
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(CyberAcid)
        )

        // Interactive story viewport: tap left = previous, tap right = next, long-press = pause
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(pages.size, recap.year) {
                    detectTapGestures(
                        onPress = {
                            try {
                                tryAwaitRelease()
                            } finally {
                                isPaused = false
                            }
                        },
                        onLongPress = {
                            isPaused = true
                        },
                        onTap = { offset ->
                            val isRightHalf = offset.x >= size.width / 2f
                            if (isRightHalf) {
                                if (pageIndex < pages.lastIndex) {
                                    pageIndex += 1
                                }
                            } else {
                                if (pageIndex > 0) {
                                    pageIndex -= 1
                                } else {
                                    restartTick += 1
                                }
                            }
                        }
                    )
                }
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    fadeIn(animationSpec = tween(260)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "YearRecapPageTransition",
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    when (page) {
                        RecapStoryPage.INTRO -> IntroStoryPage(recap = recap)
                        RecapStoryPage.KM_OF_YEAR -> KmOfYearStoryPage(recap = recap)
                        RecapStoryPage.HOURS_AT_WHEEL -> HoursAtWheelStoryPage(recap = recap)
                        RecapStoryPage.FUEL_AND_SPENT -> FuelAndSpentStoryPage(recap = recap)
                        RecapStoryPage.KM_BY_MONTH -> KmByMonthStoryPage(recap = recap)
                        RecapStoryPage.PLACES -> PlacesStoryPage(recap = recap)
                        RecapStoryPage.RECORDS -> RecordsStoryPage(recap = recap)
                        RecapStoryPage.SUMMARY -> SummaryStoryPage(
                            recap = recap,
                            exporting = exporting,
                            onShareClick = { triggerShare() },
                            captureModifier = Modifier
                                .background(CyberInk)
                                .drawWithContent {
                                    summaryLayer.record { this@drawWithContent.drawContent() }
                                    drawLayer(summaryLayer)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyYearRecapContent(
    year: Int,
    availableYears: List<Int>,
    onYearSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Voltar",
                    tint = CyberAcid
                )
            }
            Text(
                text = "RETROSPECTIVA $year",
                style = displayStyle(26),
                color = CyberText
            )
        }

        if (availableYears.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableYears) { yr ->
                    val selected = yr == year
                    Box(
                        modifier = Modifier
                            .background(if (selected) CyberAcid else CyberPanel, RectangleShape)
                            .border(1.dp, if (selected) CyberAcid else CyberLine, RectangleShape)
                            .clickable {
                                if (yr != year) onYearSelected(yr)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = yr.toString(),
                            style = EyebrowStyle,
                            color = if (selected) CyberOnAcid else CyberText
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(CyberAcid)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = CyberAcid)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = CyberAcid,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Nenhuma viagem em $year",
                    style = displayStyle(34),
                    color = CyberText,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Registre suas viagens no CarTracks para desbloquear sua retrospectiva anual.",
                    style = monoStyle(12, FontWeight.Normal),
                    color = CyberTextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                CyberGhostButton(
                    text = "VOLTAR",
                    icon = Icons.Default.ArrowBack,
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Page 1: Intro "SUA RETROSPECTIVA <ano>"
 */
@Composable
private fun IntroStoryPage(recap: YearRecap) {
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(recap.year) {
        enterAnim.snapTo(0f)
        enterAnim.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enterAnim.value
                translationY = (1f - enterAnim.value) * 36f
            },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        StoryEyebrowHeader(
            eyebrow = "CARTRACKS // TELEMETRIA ANUAL",
            title = "O SEU ANO NA ESTRADA"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(color = CyberMagenta, offset = 8.dp)
                .background(CyberAcid, RectangleShape)
                .border(2.dp, CyberOnAcid, RectangleShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 4.dp)
                        .background(CyberMagenta)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "RELATÓRIO DE BORDO",
                    style = EyebrowStyle,
                    color = CyberOnAcidSecondary
                )
            }
            Text(
                text = "SUA RETROSPECTIVA\n${recap.year}",
                style = displayStyle(54),
                color = CyberOnAcid
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(CyberOnAcid)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = String.format(
                    PtBrLocale,
                    "%d VIAGENS · %d DIAS AO VOLANTE",
                    recap.stats.trips,
                    recap.daysDriven
                ),
                style = monoStyle(14, FontWeight.Bold),
                color = CyberOnAcid
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryReadout(
                    label = "Viagens",
                    value = "${recap.stats.trips}",
                    accent = CyberCyan,
                    icon = Icons.Default.Route,
                    modifier = Modifier.weight(1f)
                )
                TelemetryReadout(
                    label = "Dias ativos",
                    value = "${recap.daysDriven}",
                    accent = CyberPink,
                    icon = Icons.Default.CalendarToday,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "TOQUE À DIREITA PARA AVANÇAR · À ESQUERDA PARA VOLTAR · SEGURE PARA PAUSAR",
                style = EyebrowStyle.copy(fontSize = 10.sp),
                color = CyberTextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Page 2: Km do ano with a count-up animation and a fun comparison (São Paulo → Rio, ~430 km).
 */
@Composable
private fun KmOfYearStoryPage(recap: YearRecap) {
    val countAnim = remember { Animatable(0f) }
    LaunchedEffect(recap.year, recap.stats.km) {
        countAnim.snapTo(0f)
        countAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val animatedKm = recap.stats.km * countAnim.value
    val spRioTimes = recap.stats.km / SP_RIO_DISTANCE_KM
    val avgTripKm = if (recap.stats.trips > 0) recap.stats.km / recap.stats.trips else 0.0

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        StoryEyebrowHeader(
            eyebrow = "ODÔMETRO DO ANO",
            title = "QUILÔMETROS RODADOS"
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Big count-up hero box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow(color = CyberMagenta, offset = 6.dp)
                    .background(CyberAcid, RectangleShape)
                    .border(2.dp, CyberOnAcid, RectangleShape)
                    .padding(20.dp)
            ) {
                Text(
                    text = "DISTÂNCIA TOTAL EM ${recap.year}",
                    style = EyebrowStyle,
                    color = CyberOnAcidSecondary
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatNumber(animatedKm, decimals = if (recap.stats.km < 100) 1 else 0),
                        style = monoStyle(52, FontWeight.Bold),
                        color = CyberOnAcid
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "KM",
                        style = displayStyle(32),
                        color = CyberOnAcid,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format(
                        PtBrLocale,
                        "Média de %s km por viagem em %d viagens",
                        formatNumber(avgTripKm, decimals = 1),
                        recap.stats.trips
                    ),
                    style = monoStyle(12, FontWeight.Medium),
                    color = CyberOnAcidSecondary
                )
            }

            // Fun comparison card: São Paulo -> Rio (~430 km)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = CyberCyan)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CyberChip(text = "COMPARAÇÃO", color = CyberCyan, filled = true)
                    Text(
                        text = "SÃO PAULO → RIO DE JANEIRO",
                        style = EyebrowStyle,
                        color = CyberCyan
                    )
                }
                Text(
                    text = if (spRioTimes >= 1.0) {
                        String.format(
                            PtBrLocale,
                            "Isso equivale a %s vezes a viagem São Paulo → Rio (~430 km)!",
                            formatNumber(spRioTimes, decimals = 1)
                        )
                    } else {
                        String.format(
                            PtBrLocale,
                            "Você percorreu %d%% da distância entre São Paulo → Rio (~430 km).",
                            (spRioTimes * 100.0).roundToInt()
                        )
                    },
                    style = displayStyle(28),
                    color = CyberText
                )
                Text(
                    text = String.format(
                        PtBrLocale,
                        "Referência Via Dutra: ~430 km por trecho · Total de %s km no ano",
                        formatNumber(recap.stats.km, decimals = 1)
                    ),
                    style = monoStyle(11, FontWeight.Normal),
                    color = CyberTextSecondary
                )
            }
        }

        val longest = recap.stats.longestTrip
        if (longest != null) {
            TelemetryReadout(
                label = "Maior viagem do ano",
                value = String.format(PtBrLocale, "%s km (%d min)", formatNumber(longest.distanceKm, 1), longest.durationMin),
                accent = CyberPink,
                icon = Icons.Default.Route,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Page 3: Horas ao volante (count-up) and days driven.
 */
@Composable
private fun HoursAtWheelStoryPage(recap: YearRecap) {
    val countAnim = remember { Animatable(0f) }
    LaunchedEffect(recap.year, recap.stats.drivingMin, recap.daysDriven) {
        countAnim.snapTo(0f)
        countAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val totalHours = recap.stats.drivingMin / 60.0
    val animatedHours = totalHours * countAnim.value
    val animatedDays = (recap.daysDriven * countAnim.value).roundToInt()
    val daysInYear = if (Year.isLeap(recap.year.toLong())) 366 else 365
    val yearSharePct = (recap.daysDriven * 100.0 / daysInYear).coerceIn(0.0, 100.0)
    val minPerActiveDay = if (recap.daysDriven > 0) recap.stats.drivingMin.toDouble() / recap.daysDriven else 0.0

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        StoryEyebrowHeader(
            eyebrow = "CRONÔMETRO DE BORDO",
            title = "TEMPO AO VOLANTE"
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Hours count-up hero card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow(color = CyberCyan, offset = 6.dp)
                    .background(CyberPanelRaised, RectangleShape)
                    .border(2.dp, CyberAcid, RectangleShape)
                    .padding(20.dp)
            ) {
                Text(
                    text = "HORAS AO VOLANTE",
                    style = EyebrowStyle,
                    color = CyberAcid
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatNumber(animatedHours, decimals = 1),
                        style = monoStyle(54, FontWeight.Bold),
                        color = CyberText
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "HORAS",
                        style = displayStyle(32),
                        color = CyberAcid,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format(
                        PtBrLocale,
                        "%s minutos acumulados em trânsito",
                        formatNumber(recap.stats.drivingMin.toDouble(), decimals = 0)
                    ),
                    style = monoStyle(12, FontWeight.Normal),
                    color = CyberTextSecondary
                )
            }

            // Days driven card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = CyberPink)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DIAS DIRIGIDOS NO ANO",
                            style = EyebrowStyle,
                            color = CyberPink
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "$animatedDays",
                                style = monoStyle(44, FontWeight.Bold),
                                color = CyberText
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (recap.daysDriven == 1) "DIA" else "DIAS",
                                style = displayStyle(28),
                                color = CyberPink,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    CyberChip(
                        text = String.format(PtBrLocale, "%.0f%% DO ANO", yearSharePct),
                        color = CyberPink,
                        filled = true
                    )
                }

                // Visual year bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(CyberInk, RectangleShape)
                        .border(1.dp, CyberLine, RectangleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(((yearSharePct / 100.0) * countAnim.value).toFloat().coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(CyberPink, RectangleShape)
                    )
                }

                Text(
                    text = String.format(
                        PtBrLocale,
                        "Média de %.0f min por dia dirigido (%d de %d dias de %d)",
                        minPerActiveDay,
                        recap.daysDriven,
                        daysInYear,
                        recap.year
                    ),
                    style = monoStyle(12, FontWeight.Normal),
                    color = CyberTextSecondary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val avgSpeed = if (totalHours > 0) recap.stats.km / totalHours else 0.0
            TelemetryReadout(
                label = "Velocidade média",
                value = String.format(PtBrLocale, "%.0f km/h", avgSpeed),
                accent = CyberCyan,
                icon = Icons.Default.DirectionsCar,
                modifier = Modifier.weight(1f)
            )
            val avgDuration = if (recap.stats.trips > 0) recap.stats.drivingMin.toDouble() / recap.stats.trips else 0.0
            TelemetryReadout(
                label = "Duração por viagem",
                value = String.format(PtBrLocale, "%.0f min", avgDuration),
                accent = CyberAcid,
                icon = Icons.Default.Schedule,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Page 4: Combustível: litros, gasto (stats.totalSpent, R$ pt-BR), avg km/L.
 */
@Composable
private fun FuelAndSpentStoryPage(recap: YearRecap) {
    val countAnim = remember { Animatable(0f) }
    LaunchedEffect(recap.year, recap.stats.fuelLiters, recap.stats.totalSpent) {
        countAnim.snapTo(0f)
        countAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    val p = countAnim.value
    val liters = recap.stats.fuelLiters * p
    val totalSpent = recap.stats.totalSpent * p
    val avgKmL = recap.stats.avgKmL * p

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        StoryEyebrowHeader(
            eyebrow = "TANQUE & CUSTOS",
            title = "COMBUSTÍVEL E GASTOS"
        )

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Total spent hero panel (stats.totalSpent in R$ pt-BR)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow(color = CyberMagenta, offset = 6.dp)
                    .background(CyberAcid, RectangleShape)
                    .border(2.dp, CyberOnAcid, RectangleShape)
                    .padding(20.dp)
            ) {
                Text(
                    text = "GASTO TOTAL EM ${recap.year}",
                    style = EyebrowStyle,
                    color = CyberOnAcidSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = formatBrl(totalSpent),
                    style = monoStyle(38, FontWeight.Bold),
                    color = CyberOnAcid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (recap.stats.otherSpent > 0.0 || recap.stats.fuelSpent > 0.0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = String.format(
                            PtBrLocale,
                            "Abastecimentos: %s · Outros gastos: %s",
                            formatBrl(recap.stats.fuelSpent),
                            formatBrl(recap.stats.otherSpent)
                        ),
                        style = monoStyle(11, FontWeight.Medium),
                        color = CyberOnAcidSecondary
                    )
                }
            }

            // Liters and Avg km/L side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .cyberPanel(border = CyberOrange)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = CyberOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "LITROS CONSUMIDOS",
                        style = EyebrowStyle.copy(fontSize = 10.sp),
                        color = CyberTextSecondary
                    )
                    Text(
                        text = String.format(PtBrLocale, "%s L", formatNumber(liters, decimals = 1)),
                        style = monoStyle(28, FontWeight.Bold),
                        color = CyberText
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .cyberPanel(border = CyberCyan)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "MÉDIA DE CONSUMO",
                        style = EyebrowStyle.copy(fontSize = 10.sp),
                        color = CyberTextSecondary
                    )
                    Text(
                        text = if (recap.stats.avgKmL > 0.0) {
                            String.format(PtBrLocale, "%s km/L", formatNumber(avgKmL, decimals = 1))
                        } else {
                            "—"
                        },
                        style = monoStyle(28, FontWeight.Bold),
                        color = CyberCyan
                    )
                }
            }
        }

        val costPerKm = if (recap.stats.km > 0.0) recap.stats.totalSpent / recap.stats.km else 0.0
        TelemetryReadout(
            label = "Custo médio por quilômetro",
            value = if (costPerKm > 0.0) "${formatBrl(costPerKm)} / km" else "—",
            accent = CyberPink,
            icon = Icons.Default.LocalGasStation,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Page 5: Km por mês as an animated bar chart (Canvas, bars growing), highlighting busiestMonth.
 */
@Composable
private fun KmByMonthStoryPage(recap: YearRecap) {
    val barGrowth = remember { Animatable(0f) }
    LaunchedEffect(recap.year, recap.kmByMonth) {
        barGrowth.snapTo(0f)
        barGrowth.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
    }

    val monthsKm = remember(recap.kmByMonth) {
        List(12) { idx -> recap.kmByMonth.getOrElse(idx) { 0.0 }.coerceAtLeast(0.0) }
    }
    val busiestIdx = remember(monthsKm, recap.busiestMonth) {
        resolveBusiestMonthIndex(monthsKm, recap.busiestMonth)
    }
    val busiestKm = monthsKm.getOrElse(busiestIdx) { 0.0 }
    val maxKm = (monthsKm.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        StoryEyebrowHeader(
            eyebrow = "SAZONALIDADE ANUAL",
            title = "QUILÔMETROS POR MÊS"
        )

        // Highlight card for busiestMonth
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(color = CyberMagenta, offset = 5.dp)
                .background(CyberAcid, RectangleShape)
                .border(2.dp, CyberOnAcid, RectangleShape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MÊS MAIS MOVIMENTADO",
                    style = EyebrowStyle,
                    color = CyberOnAcidSecondary
                )
                Text(
                    text = MonthNamesFull[busiestIdx].uppercase(PtBrLocale),
                    style = displayStyle(34),
                    color = CyberOnAcid
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(PtBrLocale, "%s KM", formatNumber(busiestKm * barGrowth.value, decimals = 0)),
                    style = monoStyle(26, FontWeight.Bold),
                    color = CyberOnAcid
                )
                val share = if (recap.stats.km > 0) (busiestKm * 100.0 / recap.stats.km) else 0.0
                Text(
                    text = String.format(PtBrLocale, "%.0f%% do total anual", share),
                    style = monoStyle(11, FontWeight.Medium),
                    color = CyberOnAcidSecondary
                )
            }
        }

        // Animated Canvas Bar Chart
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cyberPanel(border = CyberLine)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val count = 12
                    val spacingPx = 6.dp.toPx()
                    val totalSpacing = spacingPx * (count - 1)
                    val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(4f)
                    val chartHeight = size.height

                    // Horizontal subtle reference lines (25%, 50%, 75%, 100%)
                    for (step in 1..4) {
                        val y = chartHeight * (1f - step / 4f)
                        drawLine(
                            color = CyberLineSubtle,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    monthsKm.forEachIndexed { idx, km ->
                        val x = idx * (barWidth + spacingPx)
                        val normalized = (km / maxKm).toFloat().coerceIn(0f, 1f)
                        val animatedFraction = normalized * barGrowth.value
                        val minBarH = if (km > 0.0) 4.dp.toPx() else 2.dp.toPx()
                        val barH = (chartHeight * animatedFraction).coerceAtLeast(minBarH)
                        val topY = chartHeight - barH
                        val isBusiest = idx == busiestIdx && km > 0.0

                        if (isBusiest) {
                            // Hard magenta offset shadow behind busiest month bar
                            val shadowOffset = 3.dp.toPx()
                            drawRect(
                                color = CyberMagenta,
                                topLeft = Offset(x + shadowOffset, topY + shadowOffset),
                                size = Size(barWidth, barH)
                            )
                        }

                        val barColor = when {
                            isBusiest -> CyberAcid
                            km > 0.0 -> CyberCyan
                            else -> CyberLine
                        }

                        drawRect(
                            color = barColor,
                            topLeft = Offset(x, topY),
                            size = Size(barWidth, barH)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Month labels and values aligned with the 12 bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                monthsKm.forEachIndexed { idx, km ->
                    val isBusiest = idx == busiestIdx && km > 0.0
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = MonthNamesShort[idx],
                            style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
                            color = if (isBusiest) CyberAcid else CyberTextSecondary,
                            maxLines = 1
                        )
                        Text(
                            text = if (km >= 1000) {
                                String.format(PtBrLocale, "%.1fk", km / 1000.0)
                            } else {
                                String.format(PtBrLocale, "%.0f", km)
                            },
                            style = monoStyle(9, if (isBusiest) FontWeight.Bold else FontWeight.Normal),
                            color = if (isBusiest) CyberAcid else CyberTextTertiary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        val activeMonths = monthsKm.count { it > 0.0 }
        val monthlyAvg = if (activeMonths > 0) recap.stats.km / activeMonths else 0.0
        TelemetryReadout(
            label = "Média nos meses ativos ($activeMonths/12)",
            value = String.format(PtBrLocale, "%s km / mês", formatNumber(monthlyAvg, decimals = 0)),
            accent = CyberCyan,
            icon = Icons.Default.CalendarToday,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Page 6: Lugares: cities list (may be empty -> hide that block) and topDestinations.
 */
@Composable
private fun PlacesStoryPage(recap: YearRecap) {
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(recap.year, recap.cities, recap.topDestinations) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StoryEyebrowHeader(
            eyebrow = "RADAR DE ROTAS",
            title = "LUGARES E DESTINOS"
        )

        // Cities block: hidden when cities is empty
        if (recap.cities.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .cyberPanel(border = CyberCyan)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "CIDADES VISITADAS",
                            style = EyebrowStyle,
                            color = CyberCyan
                        )
                    }
                    CyberChip(
                        text = "${recap.cities.size} CIDADES",
                        color = CyberCyan,
                        filled = true
                    )
                }

                recap.cities.chunked(2).forEach { rowCities ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCities.forEach { city ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(CyberPanelRaised, RectangleShape)
                                    .border(1.dp, CyberCyan.copy(alpha = 0.7f), RectangleShape)
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = city.uppercase(PtBrLocale),
                                    style = monoStyle(12, FontWeight.SemiBold),
                                    color = CyberText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (rowCities.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Top destinations block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cyberPanel(border = CyberAcid)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRINCIPAIS DESTINOS",
                    style = EyebrowStyle,
                    color = CyberAcid
                )
                if (recap.topDestinations.isNotEmpty()) {
                    CyberChip(
                        text = "TOP ${recap.topDestinations.size}",
                        color = CyberAcid
                    )
                }
            }

            if (recap.topDestinations.isEmpty()) {
                Text(
                    text = "Sem destinos recorrentes suficientes em ${recap.year}.",
                    style = monoStyle(12, FontWeight.Normal),
                    color = CyberTextSecondary
                )
            } else {
                val maxVisits = (recap.topDestinations.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                recap.topDestinations.forEachIndexed { idx, (place, count) ->
                    val barColor = when (idx) {
                        0 -> CyberAcid
                        1 -> CyberPink
                        else -> CyberCyan
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(PtBrLocale, "#%02d", idx + 1),
                                style = monoStyle(12, FontWeight.Bold),
                                color = barColor
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = place,
                                style = monoStyle(14, FontWeight.SemiBold),
                                color = CyberText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${count}×",
                                style = monoStyle(13, FontWeight.Bold),
                                color = barColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(CyberPanelRaised, RectangleShape)
                        ) {
                            val ratio = ((count.toFloat() / maxVisits) * progressAnim.value).coerceIn(0.03f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .background(barColor, RectangleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Page 7: Recordes: each PersonalRecord with emoji, kind.title, display and detail,
 * appearing one after another (page is hidden when records is empty).
 */
@Composable
private fun RecordsStoryPage(recap: YearRecap) {
    var visible by remember(recap.year) { mutableStateOf(false) }
    LaunchedEffect(recap.year) {
        visible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StoryEyebrowHeader(
            eyebrow = "HALL DA FAMA",
            title = "RECORDES PESSOAIS"
        )

        recap.records.forEachIndexed { index, record ->
            val itemAlpha by animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 450,
                    delayMillis = index * 240,
                    easing = FastOutSlowInEasing
                ),
                label = "RecordAlpha_$index"
            )
            PersonalRecordCard(
                record = record,
                index = index,
                progress = itemAlpha
            )
        }
    }
}

@Composable
private fun PersonalRecordCard(
    record: PersonalRecord,
    index: Int,
    progress: Float
) {
    val accent = when (record.kind) {
        RecordKind.BEST_TANK_KML -> CyberAcid
        RecordKind.FASTEST_HOME_WORK -> CyberCyan
        RecordKind.LONGEST_TRIP -> CyberPink
        RecordKind.CHEAPEST_MONTH -> CyberGreen
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 28f
            }
            .cyberPanel(border = accent)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(CyberPanelRaised, RectangleShape)
                    .border(1.dp, accent, RectangleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = record.kind.emoji,
                    fontSize = 24.sp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.kind.title.uppercase(PtBrLocale),
                    style = EyebrowStyle,
                    color = accent
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = record.display,
                    style = monoStyle(24, FontWeight.Bold),
                    color = CyberText
                )
                if (record.detail.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = record.detail,
                        style = monoStyle(12, FontWeight.Normal),
                        color = CyberTextSecondary
                    )
                }
            }
            CyberChip(
                text = String.format(PtBrLocale, "#%02d", index + 1),
                color = accent
            )
        }
    }
}

/**
 * Page 8: Final summary card with the main numbers in a compact grid — captured via
 * rememberGraphicsLayer() and shared via ReportImageExporter ("CarTracks_Retrospectiva_<year>.png").
 */
@Composable
private fun SummaryStoryPage(
    recap: YearRecap,
    exporting: Boolean,
    onShareClick: () -> Unit,
    captureModifier: Modifier
) {
    val monthsKm = remember(recap.kmByMonth) {
        List(12) { idx -> recap.kmByMonth.getOrElse(idx) { 0.0 }.coerceAtLeast(0.0) }
    }
    val busiestIdx = remember(monthsKm, recap.busiestMonth) {
        resolveBusiestMonthIndex(monthsKm, recap.busiestMonth)
    }
    val busiestKm = monthsKm.getOrElse(busiestIdx) { 0.0 }
    val totalHours = recap.stats.drivingMin / 60.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Captured block for ReportImageExporter
        Column(
            modifier = captureModifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Summary hero banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow(color = CyberMagenta, offset = 5.dp)
                    .background(CyberAcid, RectangleShape)
                    .border(2.dp, CyberOnAcid, RectangleShape)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RESUMO ${recap.year}",
                        style = EyebrowStyle,
                        color = CyberOnAcidSecondary
                    )
                    Text(
                        text = "${recap.stats.trips} VIAGENS",
                        style = EyebrowStyle,
                        color = CyberOnAcid
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatNumber(recap.stats.km, decimals = if (recap.stats.km < 100) 1 else 0),
                        style = monoStyle(42, FontWeight.Bold),
                        color = CyberOnAcid
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "KM RODADOS",
                        style = displayStyle(26),
                        color = CyberOnAcid,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }

            // Compact 2x3 grid of main numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryGridCell(
                    label = "Horas ao volante",
                    value = String.format(PtBrLocale, "%s h", formatNumber(totalHours, decimals = 1)),
                    accent = CyberCyan,
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                SummaryGridCell(
                    label = "Dias dirigidos",
                    value = "${recap.daysDriven} dias",
                    accent = CyberPink,
                    icon = Icons.Default.CalendarToday,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryGridCell(
                    label = "Combustível",
                    value = String.format(PtBrLocale, "%s L", formatNumber(recap.stats.fuelLiters, decimals = 1)),
                    accent = CyberOrange,
                    icon = Icons.Default.LocalGasStation,
                    modifier = Modifier.weight(1f)
                )
                SummaryGridCell(
                    label = "Consumo médio",
                    value = if (recap.stats.avgKmL > 0.0) {
                        String.format(PtBrLocale, "%s km/L", formatNumber(recap.stats.avgKmL, decimals = 1))
                    } else {
                        "—"
                    },
                    accent = CyberAcid,
                    icon = Icons.Default.DirectionsCar,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryGridCell(
                    label = "Gasto total",
                    value = formatBrl(recap.stats.totalSpent),
                    accent = CyberGreen,
                    icon = Icons.Default.LocalGasStation,
                    modifier = Modifier.weight(1f)
                )
                SummaryGridCell(
                    label = "Mês mais ativo",
                    value = "${MonthNamesShort[busiestIdx]} · ${formatNumber(busiestKm, 0)} km",
                    accent = CyberCyan,
                    icon = Icons.Default.Route,
                    modifier = Modifier.weight(1f)
                )
            }

            // Highlight strip for top destination and top record if available
            val topDestination = recap.topDestinations.firstOrNull()
            val topRecord = recap.records.firstOrNull()
            if (topDestination != null || topRecord != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberPanel(border = CyberLine)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (topDestination != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DESTINO #1",
                                style = EyebrowStyle.copy(fontSize = 10.sp),
                                color = CyberAcid
                            )
                            Text(
                                text = "${topDestination.first} (${topDestination.second}×)",
                                style = monoStyle(12, FontWeight.SemiBold),
                                color = CyberText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (topRecord != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${topRecord.kind.emoji} ${topRecord.kind.title.uppercase(PtBrLocale)}",
                                style = EyebrowStyle.copy(fontSize = 10.sp),
                                color = CyberPink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = topRecord.display,
                                style = monoStyle(12, FontWeight.Bold),
                                color = CyberText
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        CyberActionButton(
            text = if (exporting) "GERANDO IMAGEM..." else "COMPARTILHAR",
            icon = Icons.Default.Share,
            enabled = !exporting,
            onClick = onShareClick
        )
    }
}

@Composable
private fun SummaryGridCell(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .cyberPanel(border = CyberLine)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent)
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = label.uppercase(PtBrLocale),
                    style = EyebrowStyle.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                    color = CyberTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = monoStyle(17, FontWeight.Bold),
                color = CyberText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StoryEyebrowHeader(
    eyebrow: String,
    title: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 14.dp, height = 3.dp)
                    .background(CyberAcid)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = eyebrow.uppercase(PtBrLocale),
                style = EyebrowStyle,
                color = CyberAcid
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = title.uppercase(PtBrLocale),
            style = displayStyle(34),
            color = CyberText
        )
    }
}

/**
 * Resolves [YearRecap.busiestMonth] to a 0-based index (0 = January .. 11 = December),
 * supporting both 0-based (0..11, matching kmByMonth index 0 = January) and 1-based (1..12) inputs.
 */
private fun resolveBusiestMonthIndex(monthsKm: List<Double>, busiestMonth: Int): Int {
    val maxIdx = monthsKm.indices.maxByOrNull { monthsKm[it] } ?: 0
    val maxVal = monthsKm.getOrElse(maxIdx) { 0.0 }
    if (maxVal > 0.0) {
        if (busiestMonth in 0..11 && monthsKm[busiestMonth] == maxVal) return busiestMonth
        if (busiestMonth in 1..12 && monthsKm[busiestMonth - 1] == maxVal) return busiestMonth - 1
    }
    return when {
        busiestMonth in 0..11 -> busiestMonth
        busiestMonth in 1..12 -> busiestMonth - 1
        else -> maxIdx
    }
}

private fun formatNumber(value: Double, decimals: Int): String {
    val fmt = NumberFormat.getNumberInstance(PtBrLocale).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }
    return fmt.format(value)
}

private fun formatBrl(amount: Double): String {
    val fmt = NumberFormat.getNumberInstance(PtBrLocale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return "R$ ${fmt.format(amount)}"
}
