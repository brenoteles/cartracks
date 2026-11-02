package de.dennisguse.opentracks

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import de.dennisguse.opentracks.data.ContentProviderUtils
import de.dennisguse.opentracks.data.FuelDatabaseHelper
import de.dennisguse.opentracks.data.TrackListIterator
import de.dennisguse.opentracks.data.TrackPointIterator
import de.dennisguse.opentracks.data.models.Track
import de.dennisguse.opentracks.data.models.TrackPoint
import de.dennisguse.opentracks.io.TrackExportHelper
import de.dennisguse.opentracks.services.RecordingData
import de.dennisguse.opentracks.services.TrackRecordingService
import de.dennisguse.opentracks.services.TrackRecordingServiceConnection
import de.dennisguse.opentracks.settings.SettingsActivity
import de.dennisguse.opentracks.ui.model.*
import de.dennisguse.opentracks.ui.screens.*
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelEfficiencyCalculator
import de.dennisguse.opentracks.util.SpeedAlertManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

class CarMainActivity : ComponentActivity() {

    private lateinit var providerUtils: ContentProviderUtils
    private lateinit var fuelDb: FuelDatabaseHelper
    private lateinit var speedAlertManager: SpeedAlertManager
    private var serviceConnection: TrackRecordingServiceConnection? = null

    // Compose State Holders
    private val tripsState = mutableStateOf<List<TripItem>>(emptyList())
    private val fuelState = mutableStateOf<List<FuelItem>>(emptyList())
    private val liveCockpitState = mutableStateOf(LiveCockpitData())
    private val homeLocationState = mutableStateOf(CustomLocation(-23.6015, -46.6900, "Casa", false))
    private val workLocationState = mutableStateOf(CustomLocation(-23.5615, -46.6559, "Trabalho", false))
    private val currentGpsState = mutableStateOf<Pair<Double, Double>?>(null)
    private val speedLimitState = mutableStateOf(100)
    private val speedAlertEnabledState = mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            doStartTrip()
        } else {
            Toast.makeText(this, "Permissão de GPS necessária para gravar o trajeto.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        providerUtils = ContentProviderUtils(this)
        fuelDb = FuelDatabaseHelper.getInstance(this)
        speedAlertManager = SpeedAlertManager(this)

        loadPreferences()
        loadCustomLocations()
        loadTrips()
        loadFuelEntries()

        // Service connection for live cockpit
        serviceConnection = TrackRecordingServiceConnection { _, _ ->
            updateLiveRecordingStatus()
        }

        // Coroutine loop for live cockpit telemetry updates
        lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                updateLiveRecordingStatus()
            }
        }

        setContent {
            CarTracksTheme {
                MainAppNavigation()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        serviceConnection?.bind(this)
        loadTrips()
        loadFuelEntries()
    }

    override fun onStop() {
        super.onStop()
        serviceConnection?.unbind(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        speedAlertManager.release()
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE)
        speedLimitState.value = sp.getInt("speed_limit_kmh", 100)
        speedAlertEnabledState.value = sp.getBoolean("speed_alert_enabled", true)
    }

    private fun saveSpeedLimit(limit: Int, enabled: Boolean) {
        getSharedPreferences("CarTracksPrefs", MODE_PRIVATE).edit()
            .putInt("speed_limit_kmh", limit)
            .putBoolean("speed_alert_enabled", enabled)
            .apply()
        speedLimitState.value = limit
        speedAlertEnabledState.value = enabled
        Toast.makeText(this, "Alerta de velocidade atualizado: $limit km/h", Toast.LENGTH_SHORT).show()
    }

    private fun loadCustomLocations() {
        val sp = getSharedPreferences("CarTracksLocations", MODE_PRIVATE)
        homeLocationState.value = CustomLocation(
            lat = sp.getString("home_lat", "-23.6015")?.toDoubleOrNull() ?: -23.6015,
            lon = sp.getString("home_lon", "-46.6900")?.toDoubleOrNull() ?: -46.6900,
            label = sp.getString("home_label", "Casa") ?: "Casa",
            isSet = sp.contains("home_lat")
        )
        workLocationState.value = CustomLocation(
            lat = sp.getString("work_lat", "-23.5615")?.toDoubleOrNull() ?: -23.5615,
            lon = sp.getString("work_lon", "-46.6559")?.toDoubleOrNull() ?: -46.6559,
            label = sp.getString("work_label", "Trabalho") ?: "Trabalho",
            isSet = sp.contains("work_lat")
        )
    }

    private fun saveCustomLocation(key: String, lat: Double, lon: Double, label: String) {
        getSharedPreferences("CarTracksLocations", MODE_PRIVATE).edit()
            .putString("${key}_lat", lat.toString())
            .putString("${key}_lon", lon.toString())
            .putString("${key}_label", label)
            .apply()
        loadCustomLocations()
        loadTrips()
        Toast.makeText(this, "Local '$label' salvo!", Toast.LENGTH_SHORT).show()
    }

    private fun loadTrips() {
        lifecycleScope.launch {
            val list = mutableListOf<TripItem>()
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            try {
                providerUtils.searchTracks(null)?.use { it ->
                    while (it.hasNext()) {
                        val item = it.next()
                        val km = item.totalDistance().toKM()
                        val minutes = item.totalTime().toMinutes()
                        val track = providerUtils.getTrack(item.id())

                        var maxSpd = 0.0
                        var avgSpd = 0.0
                        if (track != null && track.statistics() != null) {
                            if (track.statistics().maxSpeed() != null) {
                                maxSpd = track.statistics().maxSpeed().toKMH()
                            }
                            if (track.statistics().getAverageSpeed() != null) {
                                avgSpd = track.statistics().getAverageSpeed().toKMH()
                            }
                        }
                        if (avgSpd <= 0.0) {
                            val hours = item.totalTime().seconds / 3600.0
                            avgSpd = if (hours > 0) (km / hours) else 42.0
                        }
                        if (maxSpd <= 0.0) {
                            maxSpd = avgSpd * 1.35
                        }

                        val tripKmL = FuelEfficiencyCalculator.estimateTripKmL(avgSpd)
                        val fuel = if (tripKmL > 0) km / tripKmL else km * 0.08
                        val cost = fuel * 5.89

                        // Compute Commute Badge if start/end matches Home or Work
                        val badge = detectCommuteBadge(item.id().id())

                        list.add(
                            TripItem(
                                id = item.id().id(),
                                name = item.name(),
                                description = item.description(),
                                distanceKm = km,
                                durationMin = minutes,
                                startTimeMs = item.startTime().toEpochMilli(),
                                dateFormatted = item.startTime().atOffset(item.zoneOffset()).format(formatter),
                                avgSpeedKmh = avgSpd,
                                maxSpeedKmh = maxSpd,
                                fuelLiters = fuel,
                                fuelCost = cost,
                                commuteBadge = badge
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tripsState.value = list
        }
    }

    private fun detectCommuteBadge(trackId: Long): String? {
        val home = homeLocationState.value
        val work = workLocationState.value
        if (!home.isSet && !work.isSet) return null

        try {
            providerUtils.getTrackPointIterator(Track.Id(trackId), null)?.use { it ->
                var firstPt: TrackPoint? = null
                var lastPt: TrackPoint? = null
                while (it.hasNext()) {
                    val tp = it.next()
                    if (tp != null && tp.position().hasLocation()) {
                        if (firstPt == null) firstPt = tp
                        lastPt = tp
                    }
                }

                if (firstPt != null && lastPt != null) {
                    val startLat = firstPt.position().latitude()
                    val startLon = firstPt.position().longitude()
                    val endLat = lastPt.position().latitude()
                    val endLon = lastPt.position().longitude()

                    val isStartHome = distKm(startLat, startLon, home.lat, home.lon) < 0.6
                    val isEndHome = distKm(endLat, endLon, home.lat, home.lon) < 0.6
                    val isStartWork = distKm(startLat, startLon, work.lat, work.lon) < 0.6
                    val isEndWork = distKm(endLat, endLon, work.lat, work.lon) < 0.6

                    return when {
                        isStartHome && isEndWork -> "🏠 Casa ➔ 🏢 Trabalho"
                        isStartWork && isEndHome -> "🏢 Trabalho ➔ 🏠 Casa"
                        isStartHome -> "Partida: 🏠 Casa"
                        isEndHome -> "Destino: 🏠 Casa"
                        isStartWork -> "Partida: 🏢 Trabalho"
                        isEndWork -> "Destino: 🏢 Trabalho"
                        else -> null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun distKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * asin(sqrt(a))
    }

    private fun loadFuelEntries() {
        val entries = fuelDb.allEntries.map { e ->
            FuelItem(
                id = e.id,
                dateTime = e.dateTime,
                stationName = e.stationName,
                liters = e.liters,
                pricePerLiter = e.pricePerLiter,
                totalCost = e.totalCost,
                odometerKm = e.odometerKm,
                notes = e.notes ?: ""
            )
        }
        fuelState.value = entries
    }

    private fun getTripPoints(trackId: Long): List<TripPoint> {
        val list = mutableListOf<TripPoint>()
        try {
            providerUtils.getTrackPointIterator(Track.Id(trackId), null)?.use { it ->
                while (it.hasNext()) {
                    val tp = it.next()
                    if (tp != null && tp.position().hasLocation()) {
                        val lat = tp.position().latitude()
                        val lon = tp.position().longitude()
                        val spd = if (tp.position().hasSpeed() && tp.position().speed() != null) {
                            tp.position().speed().toKMH()
                        } else 0.0
                        val alt = if (tp.position().hasAltitude() && tp.position().altitude() != null) {
                            tp.position().altitude().toM()
                        } else 0.0
                        val time = tp.time?.toEpochMilli() ?: 0L
                        list.add(TripPoint(lat, lon, spd, alt, time))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun updateLiveRecordingStatus() {
        val conn = serviceConnection ?: return
        val service = conn.trackRecordingService ?: return

        val isRec = service.isRecording
        if (!isRec) {
            if (liveCockpitState.value.isRecording) {
                liveCockpitState.value = LiveCockpitData(isRecording = false)
                loadTrips()
            }
            return
        }

        val recData = service.recordingDataObservable?.value
        var km = 0.0
        var sec = 0L
        var maxSpd = 0.0
        var curSpd = 0.0
        var curLat: Double? = null
        var curLon: Double? = null
        var curAlt: Double? = null

        if (recData != null && recData.track() != null) {
            val stats = recData.track().statistics()
            if (stats != null) {
                km = stats.totalDistance().toKM()
                sec = stats.totalDuration().seconds
                if (stats.maxSpeed() != null) {
                    maxSpd = stats.maxSpeed().toKMH()
                }
            }
            val tp = recData.latestTrackPoint()
            if (tp != null && tp.position() != null) {
                if (tp.position().hasSpeed() && tp.position().speed() != null) {
                    curSpd = tp.position().speed().toKMH()
                }
                curLat = tp.position().latitude()
                curLon = tp.position().longitude()
                if (tp.position().hasAltitude() && tp.position().altitude() != null) {
                    curAlt = tp.position().altitude().toM()
                }
                if (curLat != null && curLon != null) {
                    currentGpsState.value = Pair(curLat, curLon)
                }
            }
        }

        // Feature 5: Speed Alert Check & Trigger
        speedAlertManager.checkSpeed(curSpd, speedLimitState.value, speedAlertEnabledState.value)

        val fuel = km * 0.08
        val cost = fuel * 5.89

        // Breadcrumbs
        val currentBreadcrumbs = liveCockpitState.value.breadcrumbPoints.toMutableList()
        if (curLat != null && curLon != null) {
            if (currentBreadcrumbs.isEmpty() ||
                distKm(currentBreadcrumbs.last().lat, currentBreadcrumbs.last().lon, curLat, curLon) > 0.015) {
                currentBreadcrumbs.add(TripPoint(curLat, curLon, curSpd, curAlt ?: 0.0, System.currentTimeMillis()))
            }
        }

        liveCockpitState.value = LiveCockpitData(
            isRecording = true,
            trackId = 0,
            currentSpeedKmh = curSpd,
            maxSpeedKmh = maxSpd,
            distanceKm = km,
            durationSec = sec,
            fuelLiters = fuel,
            fuelCost = cost,
            currentLat = curLat,
            currentLon = curLon,
            currentAlt = curAlt,
            breadcrumbPoints = currentBreadcrumbs
        )
    }

    private fun checkPermissionsAndStartTrip() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            doStartTrip()
        }
    }

    private fun doStartTrip() {
        try {
            TrackRecordingServiceConnection.executeForeground(this) { service, _ ->
                if (!service.isRecording) {
                    val trackId = service.startNewTrack()
                    Toast.makeText(this, "Viagem iniciada no Apple Live Cockpit!", Toast.LENGTH_SHORT).show()
                    liveCockpitState.value = LiveCockpitData(isRecording = true, trackId = trackId.id())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao iniciar gravação: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTrip() {
        val conn = serviceConnection ?: return
        val service = conn.trackRecordingService ?: return
        if (service.isRecording) {
            service.endCurrentTrack()
            Toast.makeText(this, "Viagem concluída e gravada com sucesso!", Toast.LENGTH_SHORT).show()
            liveCockpitState.value = LiveCockpitData(isRecording = false)
            loadTrips()
        }
    }

    @Composable
    private fun MainAppNavigation() {
        var selectedTab by remember { mutableStateOf(0) }
        var activeDetailTrip by remember { mutableStateOf<TripItem?>(null) }
        var isPeriodMapOpen by remember { mutableStateOf(false) }
        var isCockpitFullscreen by remember { mutableStateOf(false) }
        var selectedPeriod by remember { mutableStateOf(PeriodFilter.TODAS) }

        val trips by tripsState
        val fuelEntries by fuelState
        val liveData by liveCockpitState
        val homeLoc by homeLocationState
        val workLoc by workLocationState
        val currentGps by currentGpsState
        val speedLimit by speedLimitState
        val isSpeedAlertEnabled by speedAlertEnabledState

        // Auto-open cockpit if user just started a trip
        LaunchedEffect(liveData.isRecording) {
            if (liveData.isRecording && !isCockpitFullscreen && activeDetailTrip == null && !isPeriodMapOpen) {
                isCockpitFullscreen = true
            }
        }

        // Fullscreen Overlays First
        if (isCockpitFullscreen && liveData.isRecording) {
            LiveCockpitScreen(
                liveData = liveData,
                speedLimitKmh = speedLimit,
                isSpeedAlertEnabled = isSpeedAlertEnabled,
                onUpdateSpeedLimit = { saveSpeedLimit(it, isSpeedAlertEnabled) },
                onStopTripClicked = {
                    stopTrip()
                    isCockpitFullscreen = false
                },
                onMinimizeClicked = {
                    isCockpitFullscreen = false
                }
            )
            return
        }

        if (activeDetailTrip != null) {
            val points = remember(activeDetailTrip) {
                getTripPoints(activeDetailTrip!!.id)
            }
            TripDetailScreen(
                trip = activeDetailTrip!!,
                points = points,
                onBackClicked = { activeDetailTrip = null },
                onExportGpx = {
                    TrackExportHelper.exportAndShareGPX(this@CarMainActivity, activeDetailTrip!!, points)
                },
                onExportKml = {
                    TrackExportHelper.exportAndShareKML(this@CarMainActivity, activeDetailTrip!!, points)
                }
            )
            return
        }

        if (isPeriodMapOpen) {
            val now = System.currentTimeMillis()
            val filtered = remember(trips, selectedPeriod) {
                when (selectedPeriod) {
                    PeriodFilter.HOJE -> trips.filter { now - it.startTimeMs <= 24 * 3600 * 1000L }
                    PeriodFilter.SETE_DIAS -> trips.filter { now - it.startTimeMs <= 7 * 24 * 3600 * 1000L }
                    PeriodFilter.ESTE_MES -> trips.filter { now - it.startTimeMs <= 30 * 24 * 3600 * 1000L }
                    PeriodFilter.TODAS -> trips
                }
            }
            val multiRoutes = remember(filtered) {
                filtered.map { getTripPoints(it.id) }
            }
            PeriodMapScreen(
                routes = multiRoutes,
                periodName = selectedPeriod.label,
                onBackClicked = { isPeriodMapOpen = false }
            )
            return
        }

        // Main Tab Scaffold
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = AppleDarkSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.navigationBarsPadding() // Safe insets for gesture navigation!
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Início") },
                        label = { Text("Início", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppleBlue,
                            selectedTextColor = AppleBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.DirectionsCar, contentDescription = "Viagens") },
                        label = { Text("Viagens", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppleBlue,
                            selectedTextColor = AppleBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.LocalGasStation, contentDescription = "Abastecimento") },
                        label = { Text("Abastecimento", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppleBlue,
                            selectedTextColor = AppleBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                        label = { Text("Ajustes", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppleBlue,
                            selectedTextColor = AppleBlue,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            },
            containerColor = AppleBlack
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        trips = trips,
                        fuelEntries = fuelEntries,
                        liveData = liveData,
                        onStartTripClicked = {
                            if (liveData.isRecording) {
                                isCockpitFullscreen = true
                            } else {
                                checkPermissionsAndStartTrip()
                            }
                        },
                        onOpenCockpitClicked = { isCockpitFullscreen = true },
                        onAddFuelClicked = { selectedTab = 2 },
                        onTripClicked = { activeDetailTrip = it },
                        onViewAllTripsClicked = { selectedTab = 1 }
                    )
                    1 -> TripsScreen(
                        trips = trips,
                        selectedFilter = selectedPeriod,
                        onFilterSelected = { selectedPeriod = it },
                        onTripClicked = { activeDetailTrip = it },
                        onPeriodMapClicked = { isPeriodMapOpen = true }
                    )
                    2 -> FuelScreen(
                        fuelEntries = fuelEntries,
                        onAddFuel = { station, liters, price, total, odo, notes ->
                            fuelDb.addEntry(station, liters, price, total, odo, notes)
                            loadFuelEntries()
                            Toast.makeText(this@CarMainActivity, "Abastecimento registrado!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    3 -> SettingsScreen(
                        homeLoc = homeLoc,
                        workLoc = workLoc,
                        currentGps = currentGps,
                        speedLimitKmh = speedLimit,
                        isSpeedAlertEnabled = isSpeedAlertEnabled,
                        onSaveLocation = { key, lat, lon, label ->
                            saveCustomLocation(key, lat, lon, label)
                        },
                        onSaveSpeedAlert = { limit, enabled ->
                            saveSpeedLimit(limit, enabled)
                        },
                        onOpenSystemSettings = {
                            startActivity(Intent(this@CarMainActivity, SettingsActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}
