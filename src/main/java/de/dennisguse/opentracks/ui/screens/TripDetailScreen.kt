package de.dennisguse.opentracks.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated)
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
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppleCardElevated.copy(alpha = 0.92f))
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
