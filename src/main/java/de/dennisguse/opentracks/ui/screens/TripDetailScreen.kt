package de.dennisguse.opentracks.ui.screens

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import de.dennisguse.opentracks.ui.components.EditTripDialog
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelEfficiencyCalculator
import de.dennisguse.opentracks.util.MapMatchingService
import de.dennisguse.opentracks.util.PlaceNames
import de.dennisguse.opentracks.util.RouteAnalysis
import de.dennisguse.opentracks.util.TripReplay
import de.dennisguse.opentracks.ui.model.CustomLocation
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
fun TripDetailScreen(
    trip: TripItem,
    points: List<TripPoint>,
    onBackClicked: () -> Unit,
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit,
    onUpdateTrip: ((Long, String, String) -> Unit)? = null,
    onDeleteTrip: ((Long) -> Unit)? = null
) {
    var isMapMaximized by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showMatchedRoute by remember { mutableStateOf(false) }
    var isMatchingInProgress by remember { mutableStateOf(false) }
    var matchedPoints by remember { mutableStateOf<List<TripPoint>?>(null) }

    // Sparse traces (few GPS fixes far apart) would be drawn as straight lines across the
    // city; fill the gaps with the road route between the recorded fixes when online.
    val isSparse = remember(points) { MapMatchingService.isSparse(points) }
    var reconstructedPoints by remember(points) { mutableStateOf<List<TripPoint>?>(null) }
    var showReconstructed by remember(points) { mutableStateOf(true) }
    var isReconstructing by remember(points) { mutableStateOf(isSparse) }
    LaunchedEffect(points) {
        if (isSparse) {
            reconstructedPoints = MapMatchingService.reconstructSparseRoute(points)
            isReconstructing = false
        }
    }

    val displayPoints = when {
        showMatchedRoute && matchedPoints != null -> matchedPoints!!
        isSparse && showReconstructed && reconstructedPoints != null -> reconstructedPoints!!
        else -> points
    }
    val tripKmL = trip.kmL

    // Replay (FR24-style playback) over the recorded fixes, which carry time, speed and altitude
    val replay = remember(points) { TripReplay(points) }
    var replayVisible by remember(points) { mutableStateOf(false) }
    var playing by remember(points) { mutableStateOf(false) }
    var replayMs by remember(points) { mutableLongStateOf(0L) }
    var replaySpeed by remember { mutableIntStateOf(30) }
    val frame = if (replayVisible) replay.frameAt(replayMs) else null
    LaunchedEffect(playing, replaySpeed) {
        if (!playing) return@LaunchedEffect
        if (replayMs >= replay.durationMs) replayMs = 0L
        var last = withFrameNanos { it }
        while (playing) {
            val now = withFrameNanos { it }
            replayMs = (replayMs + (now - last) / 1_000_000 * replaySpeed).coerceAtMost(replay.durationMs)
            last = now
            if (replayMs >= replay.durationMs) playing = false
        }
    }

    // Trip "ficha": the recorded fix closest to where the route was tapped
    var tappedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val onRouteTapped: (TripPoint) -> Unit = { p ->
        tappedIndex = points.indices.minByOrNull { RouteAnalysis.distanceM(points[it].lat, points[it].lon, p.lat, p.lon) }
    }
    val mapCar = frame?.let { it.lat to it.lon }

    if (showEditDialog) {
        EditTripDialog(
            trip = trip,
            onConfirm = { newName, newDesc ->
                onUpdateTrip?.invoke(trip.id, newName, newDesc)
                showEditDialog = false
            },
            onDelete = if (onDeleteTrip != null) {
                {
                    showEditDialog = false
                    onDeleteTrip(trip.id)
                    onBackClicked()
                }
            } else null,
            onDismiss = { showEditDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("EXCLUIR VIAGEM?", style = displayStyle(28), color = CyberRed) },
            text = {
                Text(
                    "O registro desta viagem será apagado. Esta ação não pode ser desfeita.",
                    color = TextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteTrip?.invoke(trip.id)
                        onBackClicked()
                    },
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed)
                ) {
                    Text("EXCLUIR", style = EyebrowStyle.copy(fontSize = 12.sp), color = CyberText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, shape = RectangleShape) {
                    Text("CANCELAR", style = EyebrowStyle.copy(fontSize = 12.sp), color = TextSecondary)
                }
            },
            shape = RectangleShape,
            containerColor = CyberPanelRaised
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberInk)
            .statusBarsPadding()
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClicked) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = CyberAcid)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = trip.name.ifEmpty { "Viagem" }.uppercase(),
                    style = displayStyle(26),
                    color = CyberText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(trip.dateFormatted, style = monoStyle(11, FontWeight.Normal), color = TextSecondary)
            }
            if (onUpdateTrip != null) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar viagem", tint = CyberText)
                }
            }
            if (onDeleteTrip != null) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir viagem", tint = CyberRed)
                }
            }
            IconButton(onClick = onExportGpx) {
                Icon(Icons.Default.Share, contentDescription = "Exportar trajeto", tint = CyberText)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyberAcid))

        if (isMapMaximized) {
            Box(Modifier.fillMaxSize()) {
                CarMapView(
                    points = displayPoints,
                    modifier = Modifier.fillMaxSize(),
                    currentLocation = mapCar,
                    vehicleBearing = frame?.bearing,
                    animateVehicle = false,
                    keepVehicleInView = playing,
                    onRoutePointTapped = onRouteTapped,
                    pinnedLocation = tappedIndex?.let { points[it].lat to points[it].lon },
                    pinnedTitle = "Ponto da viagem",
                    floatingControlsBottomPadding = 150.dp
                )
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(CyberInk.copy(alpha = 0.92f))) {
                    tappedIndex?.let { i -> TripPointCard(trip, points, replay, i, onClose = { tappedIndex = null }, onReplayFromHere = {
                        replayVisible = true
                        replayMs = replay.offsetOf(i)
                        playing = true
                        tappedIndex = null
                    }) }
                    ReplayBar(replay, frame, replayVisible, playing, replayMs, replaySpeed, tripKmL,
                        onToggle = { if (!replayVisible) replayVisible = true; playing = !playing },
                        onSeek = { replayVisible = true; replayMs = it },
                        onSpeed = { replaySpeed = it },
                        onClose = { playing = false; replayVisible = false })
                }
                MapSizeToggle(
                    maximized = true,
                    onClick = { isMapMaximized = false },
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(CyberPanel)
            ) {
                CarMapView(
                    points = displayPoints,
                    modifier = Modifier.fillMaxSize(),
                    currentLocation = mapCar,
                    vehicleBearing = frame?.bearing,
                    animateVehicle = false,
                    keepVehicleInView = playing,
                    onRoutePointTapped = onRouteTapped,
                    pinnedLocation = tappedIndex?.let { points[it].lat to points[it].lon },
                    pinnedTitle = "Ponto da viagem"
                )
                MapSizeToggle(
                    maximized = false,
                    onClick = { isMapMaximized = true },
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                )
            }
            tappedIndex?.let { i -> TripPointCard(trip, points, replay, i, onClose = { tappedIndex = null }, onReplayFromHere = {
                replayVisible = true
                replayMs = replay.offsetOf(i)
                playing = true
                tappedIndex = null
            }) }
            ReplayBar(replay, frame, replayVisible, playing, replayMs, replaySpeed, tripKmL,
                onToggle = { if (!replayVisible) replayVisible = true; playing = !playing },
                onSeek = { replayVisible = true; replayMs = it },
                onSpeed = { replaySpeed = it },
                onClose = { playing = false; replayVisible = false })

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isSparse) {
                    SparseTraceBanner(
                        pointCount = points.size,
                        isLoading = isReconstructing,
                        reconstructed = reconstructedPoints != null,
                        showingReconstructed = showReconstructed,
                        onToggle = { showReconstructed = !showReconstructed }
                    )
                }

                // Map matching
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cyberPanel(border = if (showMatchedRoute) CyberCyan else CyberLine)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AJUSTE ÀS RUAS", style = EyebrowStyle, color = CyberText)
                            if (showMatchedRoute) {
                                Spacer(Modifier.width(8.dp))
                                CyberChip("Ativo", CyberCyan, filled = true)
                            }
                        }
                        Text(
                            text = if (showMatchedRoute) "Traçado encaixado nas vias" else "Remove desvios do GPS e encaixa o trajeto nas vias",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .background(if (showMatchedRoute) CyberCyan else CyberAcid, RectangleShape)
                            .clickable(enabled = !isMatchingInProgress) {
                                if (showMatchedRoute) {
                                    showMatchedRoute = false
                                } else if (matchedPoints != null) {
                                    showMatchedRoute = true
                                } else {
                                    coroutineScope.launch {
                                        isMatchingInProgress = true
                                        val res = MapMatchingService.matchTrip(context, de.dennisguse.opentracks.data.models.Track.Id(trip.id), points)
                                        matchedPoints = res.matchedPoints
                                        showMatchedRoute = true
                                        isMatchingInProgress = false
                                        val typeMsg = if (res.isOnlineMatched) "vias OSM (online)" else "corredores viários (offline)"
                                        Toast.makeText(context, "Trajeto ajustado via $typeMsg", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (isMatchingInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyberOnAcid)
                        } else {
                            Text(
                                text = if (showMatchedRoute) "GPS BRUTO" else "AJUSTAR",
                                style = EyebrowStyle.copy(fontSize = 12.sp),
                                color = CyberOnAcid
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!trip.commuteBadge.isNullOrEmpty()) CyberChip(text = trip.commuteBadge, color = CyberCyan)
                    if (trip.trafficLabel.isNotEmpty()) CyberChip(text = "Trânsito: ${trip.trafficLabel}", color = CyberOrange)
                    if (trip.fuelLabel.isNotEmpty()) CyberChip(text = trip.fuelLabel, color = CyberGreen)
                }

                CyberSectionHeader(title = "Telemetria", eyebrow = "Desempenho")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Distância", String.format(java.util.Locale.getDefault(), "%.1f km", trip.distanceKm), Icons.Default.Route, CyberCyan, Modifier.weight(1f))
                    MetricCard("Duração", "${trip.durationMin} min", Icons.Default.Timer, CyberAcid, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Vel. média", "${trip.avgSpeedKmh.toInt()} km/h", Icons.Default.Speed, CyberPink, Modifier.weight(1f))
                    MetricCard("Vel. máxima", "${trip.maxSpeedKmh.toInt()} km/h", Icons.Default.TrendingUp, CyberRed, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Consumo · ${trip.fuelLabel}", String.format(java.util.Locale.getDefault(), "%.1f km/L", tripKmL), Icons.Default.LocalGasStation, CyberGreen, Modifier.weight(1f))
                    MetricCard("Gasto", String.format(java.util.Locale.getDefault(), "R$ %.2f", trip.fuelCost), Icons.Default.AttachMoney, CyberOrange, Modifier.weight(1f))
                }

                if (points.size >= 2) {
                    TripTelemetryProfileChart(points = points)
                }

                CyberSectionHeader(title = "Exportar", eyebrow = "Trajeto")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CyberGhostButton("GPX", onClick = onExportGpx, icon = Icons.Default.Share, accent = CyberAcid, modifier = Modifier.weight(1f))
                    CyberGhostButton("KML · Earth", onClick = onExportKml, icon = Icons.Default.Public, accent = CyberCyan, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MapSizeToggle(maximized: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(CyberInk.copy(alpha = 0.92f), RectangleShape)
            .border(1.dp, CyberAcid, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (maximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = null,
            tint = CyberAcid,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(if (maximized) "REDUZIR" else "TELA CHEIA", style = EyebrowStyle, color = CyberText)
    }
}

@Composable
private fun SparseTraceBanner(
    pointCount: Int,
    isLoading: Boolean,
    reconstructed: Boolean,
    showingReconstructed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cyberPanel(border = CyberOrange, shadow = false)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = CyberOrange)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("GPS ESPARSO · $pointCount PONTOS", style = EyebrowStyle, color = CyberOrange)
            Text(
                text = when {
                    isLoading -> "Reconstruindo o trajeto pelas ruas…"
                    reconstructed && showingReconstructed -> "Trechos sem sinal foram preenchidos com a rota pelas ruas (estimativa)."
                    reconstructed -> "Mostrando apenas os pontos gravados."
                    else -> "Sem conexão para reconstruir pelas ruas. Mostrando os pontos gravados."
                },
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = CyberOrange)
        } else if (reconstructed) {
            Text(
                if (showingReconstructed) "BRUTO" else "RUAS",
                style = EyebrowStyle,
                color = CyberAcid,
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .padding(8.dp)
            )
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
            .clip(RectangleShape)
            .background(CyberPanelRaised)
            .border(1.dp, CyberLine, RectangleShape),
        colors = CardDefaults.cardColors(containerColor = CyberPanelRaised)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // title on its own line and a full-width selector: nothing competes for the row width
            Text("PERFIL DA VIAGEM", style = EyebrowStyle.copy(fontSize = 10.sp), color = CyberAcid)
            Text(
                text = if (selectedMetric == 0) "Velocidade" else "Altitude",
                style = displayStyle(22),
                color = TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberInk)
                    .border(1.dp, CyberLine)
            ) {
                listOf("VELOCIDADE" to CyberAcid, "ALTITUDE" to CyberGreen).forEachIndexed { i, (label, accent) ->
                    val selected = selectedMetric == i
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (selected) accent else Color.Transparent)
                            .clickable { selectedMetric = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = EyebrowStyle.copy(fontSize = 11.sp), color = if (selected) CyberOnAcid else TextSecondary, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val primaryColor = if (selectedMetric == 0) CyberCyan else CyberGreen
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
                    modifier = Modifier.align(Alignment.TopStart).background(CyberPanelRaised.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 1.dp)
                )

                Text(
                    text = if (selectedMetric == 0) "0 km/h" else "${minVal.toInt()} m",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).background(CyberPanelRaised.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (selectedMetric == 0) {
                    ReplayStat("PICO", "${maxVal.toInt()} km/h", CyberAcid)
                    ReplayStat("MÉDIA", "${values.average().toInt()} km/h", CyberText)
                    ReplayStat("PONTOS", "${points.size}", CyberTextSecondary)
                } else {
                    ReplayStat("MÁXIMA", "${maxVal.toInt()} m", CyberGreen)
                    ReplayStat("MÍNIMA", "${minVal.toInt()} m", CyberText)
                    ReplayStat("DESNÍVEL", "${(maxVal - minVal).toInt()} m", CyberCyan)
                }
            }
        }
    }
}

private val REPLAY_SPEEDS = listOf(10, 30, 60, 120)

private fun clock(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(ms))

/** Play/pause, timeline slider, speed and a live readout of the replayed instant. */
@Composable
private fun ReplayBar(
    replay: TripReplay,
    frame: de.dennisguse.opentracks.util.ReplayFrame?,
    visible: Boolean,
    playing: Boolean,
    replayMs: Long,
    speed: Int,
    kmL: Double,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Int) -> Unit,
    onClose: () -> Unit
) {
    if (replay.isEmpty) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(CyberPanel)
            .border(1.dp, CyberLine)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(CyberAcid).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausar replay" else "Reproduzir viagem",
                    tint = CyberOnAcid
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("REPLAY DA VIAGEM", style = EyebrowStyle.copy(fontSize = 10.sp), color = CyberAcid)
                Text(
                    if (frame != null) clock(frame.timeMs) + "  ·  " + (replayMs / 60_000) + " min de " + (replay.durationMs / 60_000) + " min"
                    else "Toque ▶ ou no traçado",
                    style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary
                )
            }
            Box(
                Modifier.border(1.dp, CyberCyan).clickable { onSpeed(REPLAY_SPEEDS[(REPLAY_SPEEDS.indexOf(speed) + 1) % REPLAY_SPEEDS.size]) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text("${speed}×", style = monoStyle(12, FontWeight.Bold), color = CyberCyan) }
            if (visible) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar replay", tint = CyberTextSecondary) }
            }
        }
        Slider(
            value = replayMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..replay.durationMs.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(thumbColor = CyberAcid, activeTrackColor = CyberAcid, inactiveTrackColor = CyberPanelRaised),
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
        if (frame != null) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                ReplayStat("VEL.", String.format(java.util.Locale.US, "%.0f km/h", frame.speedKmh), CyberAcid)
                ReplayStat("ALT.", String.format(java.util.Locale.US, "%.0f m", frame.altitudeM), CyberCyan)
                ReplayStat("PERCORRIDO", String.format(java.util.Locale("pt", "BR"), "%.1f km", frame.distanceKm), CyberText)
                if (kmL > 0) ReplayStat("CONSUMO", String.format(java.util.Locale("pt", "BR"), "%.2f L", frame.distanceKm / kmL), CyberOrange)
            }
        }
    }
}

@Composable
private fun ReplayStat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = EyebrowStyle.copy(fontSize = 9.sp), color = CyberTextTertiary)
        Text(value, style = monoStyle(13, FontWeight.Bold), color = color)
    }
}

/** Flight-radar style card for one recorded fix: time, speed, altitude and where in the trip it is. */
@Composable
private fun TripPointCard(
    trip: TripItem,
    points: List<TripPoint>,
    replay: TripReplay,
    index: Int,
    onClose: () -> Unit,
    onReplayFromHere: () -> Unit
) {
    val context = LocalContext.current
    val p = points[index]
    val at = replay.frameAt(replay.offsetOf(index))
    val unset = CustomLocation(0.0, 0.0, "", false)
    val place by produceState<String?>(null, p) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PlaceNames.label(context, p.lat, p.lon, unset, unset) }
    }
    val br = java.util.Locale("pt", "BR")
    Column(
        Modifier
            .fillMaxWidth()
            .background(CyberPanelRaised)
            .border(1.dp, CyberPink)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PONTO DA VIAGEM", style = EyebrowStyle.copy(fontSize = 10.sp), color = CyberPink)
                Text(place ?: "…", color = CyberText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = CyberTextSecondary) }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            ReplayStat("HORA", if (p.timeMs > 0) clock(p.timeMs) else "--", CyberText)
            ReplayStat("VEL.", String.format(java.util.Locale.US, "%.0f km/h", p.speedKmh), CyberAcid)
            ReplayStat("ALT.", String.format(java.util.Locale.US, "%.0f m", p.altitudeM), CyberCyan)
        }
        if (at != null) {
            Text(
                String.format(br, "Trecho: km %.1f de %.1f · %d min após a partida", at.distanceKm, replay.totalKm, replay.offsetOf(index) / 60_000) +
                    (if (trip.kmL > 0) String.format(br, " · ≈ %.2f L até aqui", at.distanceKm / trip.kmL) else ""),
                style = monoStyle(11, FontWeight.Normal), color = CyberTextSecondary
            )
        }
        Text(
            "▶ REPLAY DAQUI",
            style = EyebrowStyle, color = CyberAcid,
            modifier = Modifier.clickable(onClick = onReplayFromHere).padding(vertical = 4.dp)
        )
    }
}
