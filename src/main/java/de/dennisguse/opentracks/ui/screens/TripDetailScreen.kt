package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.ui.components.CarMapView
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelEfficiencyCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    trip: TripItem,
    points: List<TripPoint>,
    onBackClicked: () -> Unit,
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit
) {
    var isMapMaximized by remember { mutableStateOf(false) }
    val tripKmL = remember(trip.avgSpeedKmh) {
        FuelEfficiencyCalculator.estimateTripKmL(trip.avgSpeedKmh)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = trip.name.ifEmpty { "Detalhes da Viagem" },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = trip.dateFormatted,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Voltar",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onExportGpx) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Exportar Trajeto",
                            tint = AppleBlue
                        )
                    }
                    IconButton(onClick = { isMapMaximized = !isMapMaximized }) {
                        Icon(
                            imageVector = if (isMapMaximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Alternar Tamanho do Mapa",
                            tint = AppleBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppleBlack)
            )
        },
        containerColor = AppleBlack
    ) { innerPadding ->
        if (isMapMaximized) {
            // Fullscreen Map Mode
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                CarMapView(
                    points = points,
                    modifier = Modifier.fillMaxSize()
                )

                // Large Prominent Toggle Button Floating on Map
                Button(
                    onClick = { isMapMaximized = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WandaCardElevated.copy(alpha = 0.94f))
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = null,
                        tint = AppleBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reduzir Mapa",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            // Standard Mode: Map + Telemetry + Exports
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp)
            ) {
                // Map Container with Visible Floating Maximize Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(AppleCard)
                ) {
                    CarMapView(
                        points = points,
                        modifier = Modifier.fillMaxSize()
                    )

                    // VERY PROMINENT MAXIMIZE BUTTON (Directly on Map)
                    Button(
                        onClick = { isMapMaximized = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WandaCardElevated.copy(alpha = 0.94f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = AppleBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Maximizar Mapa",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Telemetry & Details Section
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Commute badge
                    if (!trip.commuteBadge.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppleBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = trip.commuteBadge,
                                color = AppleBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Text(
                        text = "Telemetria e Desempenho",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Metric Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Distância",
                            value = String.format(java.util.Locale.getDefault(), "%.1f km", trip.distanceKm),
                            icon = Icons.Default.Speed,
                            tint = AppleBlue,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Duração",
                            value = "${trip.durationMin} min",
                            icon = Icons.Default.Timer,
                            tint = AppleOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Velocidade Média",
                            value = "${trip.avgSpeedKmh.toInt()} km/h",
                            icon = Icons.Default.Speed,
                            tint = AppleTeal,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Velocidade Máxima",
                            value = "${trip.maxSpeedKmh.toInt()} km/h",
                            icon = Icons.Default.TrendingUp,
                            tint = AppleRed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Feature 3: Real & Estimated Fuel Efficiency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Consumo Real Est.",
                            value = String.format(java.util.Locale.getDefault(), "%.1f km/L", tripKmL),
                            icon = Icons.Default.LocalGasStation,
                            tint = AppleGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Gasto na Viagem",
                            value = String.format(java.util.Locale.getDefault(), "R$ %.2f", trip.fuelCost),
                            icon = Icons.Default.AttachMoney,
                            tint = AppleYellow,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Interactive Telemetry Profile (OsmAnd Style Speed & Altitude Graph)
                    if (points.size >= 2) {
                        TripTelemetryProfileChart(points = points)
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Feature 6: Route Export Buttons (GPX & KML)
                    Text(
                        text = "Exportar Trajeto",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportGpx,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = AppleBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Exportar GPX",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = onExportKml,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = AppleGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Google Earth (KML)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripTelemetryProfileChart(points: List<TripPoint>) {
    if (points.size < 2) return

    var selectedMetric by remember { mutableStateOf(0) } // 0: Velocidade, 1: Altitude

    val values = remember(points, selectedMetric) {
        if (selectedMetric == 0) {
            points.map { it.speedKmh.toFloat().coerceAtLeast(0f) }
        } else {
            points.map { it.altitudeM.toFloat() }
        }
    }

    val maxVal = remember(values) { (values.maxOrNull() ?: 100f).coerceAtLeast(10f) }
    val minVal = remember(values, selectedMetric) {
        if (selectedMetric == 0) 0f else (values.minOrNull() ?: 0f)
    }
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WandaCardElevated)
            .border(1.dp, WandaBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = WandaCardElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedMetric == 0) "Perfil de Velocidade" else "Perfil Altimétrico",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(WandaBackground)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedMetric == 0) WandaPrimary else Color.Transparent)
                            .clickable { selectedMetric = 0 }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "⚡ Velocidade",
                            color = if (selectedMetric == 0) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedMetric == 1) WandaGreen30 else Color.Transparent)
                            .clickable { selectedMetric = 1 }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "⛰️ Altitude",
                            color = if (selectedMetric == 1) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val primaryColor = if (selectedMetric == 0) WandaCyan20 else WandaGreen30
            val gradientBrush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.45f), primaryColor.copy(alpha = 0.02f))
            )

            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val n = values.size
                    val dx = w / (n - 1).toFloat()

                    // Guide Lines
                    val steps = 3
                    for (s in 0..steps) {
                        val y = h - (h / steps) * s
                        drawLine(
                            color = Color(0x22FFFFFF),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }

                    val linePath = Path()
                    val areaPath = Path()

                    values.forEachIndexed { i, v ->
                        val x = i * dx
                        val norm = (v - minVal) / range
                        val y = h - (norm * (h - 16f)) - 8f

                        if (i == 0) {
                            linePath.moveTo(x, y)
                            areaPath.moveTo(x, h)
                            areaPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            areaPath.lineTo(x, y)
                        }

                        if (i == n - 1) {
                            areaPath.lineTo(x, h)
                            areaPath.close()
                        }
                    }

                    drawPath(areaPath, brush = gradientBrush)
                    drawPath(linePath, color = primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                }

                Text(
                    text = if (selectedMetric == 0) "${maxVal.toInt()} km/h" else "${maxVal.toInt()} m",
                    color = primaryColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 2.dp)
                )

                Text(
                    text = if (selectedMetric == 0) "0 km/h" else "${minVal.toInt()} m",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedMetric == 0) {
                    val avg = values.average()
                    Text("Pico: ${maxVal.toInt()} km/h", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Média: ${avg.toInt()} km/h", color = TextSecondary, fontSize = 12.sp)
                    Text("${points.size} pontos", color = TextSecondary, fontSize = 12.sp)
                } else {
                    val gain = (maxVal - minVal).toInt()
                    Text("Ponto Mais Alto: ${maxVal.toInt()} m", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Desnível: ${gain} m", color = WandaGreen30, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
