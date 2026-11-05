package de.dennisguse.opentracks

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import de.dennisguse.opentracks.util.GpsKalmanFilter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import de.dennisguse.opentracks.data.MockDataGenerator
import de.dennisguse.opentracks.data.TrackListIterator
import de.dennisguse.opentracks.data.TrackPointIterator
import de.dennisguse.opentracks.data.models.Track
import de.dennisguse.opentracks.data.models.TrackPoint
import de.dennisguse.opentracks.io.CarTracksBackupHelper
import de.dennisguse.opentracks.io.TrackExportHelper
import de.dennisguse.opentracks.services.RecordingData
import de.dennisguse.opentracks.services.TrackRecordingService
import de.dennisguse.opentracks.services.TrackRecordingServiceConnection
import de.dennisguse.opentracks.ui.components.QuickEditOdometerDialog
import de.dennisguse.opentracks.ui.components.VehicleSetupDialog
import de.dennisguse.opentracks.ui.model.*
import de.dennisguse.opentracks.ui.screens.*
import de.dennisguse.opentracks.ui.theme.*
import de.dennisguse.opentracks.util.FuelConsumptionModel
import de.dennisguse.opentracks.util.FuelFamily
import de.dennisguse.opentracks.util.MaintenanceItem
import de.dennisguse.opentracks.util.MaintenanceStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

class CarMainActivity : ComponentActivity() {

    private lateinit var providerUtils: ContentProviderUtils
    private lateinit var fuelDb: FuelDatabaseHelper
    private var serviceConnection: TrackRecordingServiceConnection? = null

    // Compose State Holders
    private val tripsState = mutableStateOf<List<TripItem>>(emptyList())
    private val fuelState = mutableStateOf<List<FuelItem>>(emptyList())
    private val liveCockpitState = mutableStateOf(LiveCockpitData())
    private val homeLocationState = mutableStateOf(CustomLocation(-23.6015, -46.6900, "Casa", false))
    private val workLocationState = mutableStateOf(CustomLocation(-23.5615, -46.6559, "Trabalho", false))
    private val currentGpsState = mutableStateOf<Pair<Double, Double>?>(null)
    private val liveKalmanFilter = GpsKalmanFilter(processNoiseSigma = 2.5)
    private val parkingInfoState = mutableStateOf<ParkingInfo?>(null)
    private val isBatteryOptimizedState = mutableStateOf(false)

    private fun checkBatteryOptimization() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryOptimizedState.value = pm?.isIgnoringBatteryOptimizations(packageName) == true
    }

    private fun requestIgnoreBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: Exception) {
                Toast.makeText(this, "Abra Configurações > Bateria e desative a restrição.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadParkingInfo() {
        val sp = getSharedPreferences("CarTracksParking", Context.MODE_PRIVATE)
        if (sp.getBoolean("parking_active", false)) {
            val lat = sp.getString("parking_lat", null)?.toDoubleOrNull()
            val lon = sp.getString("parking_lon", null)?.toDoubleOrNull()
            val time = sp.getLong("parking_time_ms", 0L)
            val addr = sp.getString("parking_address", "Local de Estacionamento") ?: "Local de Estacionamento"
            if (lat != null && lon != null) {
                parkingInfoState.value = ParkingInfo(lat, lon, time, addr)
                return
            }
        }
        parkingInfoState.value = null
    }

    private fun saveParkingInfo(lat: Double, lon: Double) {
        getSharedPreferences("CarTracksParking", Context.MODE_PRIVATE).edit()
            .putBoolean("parking_active", true)
            .putString("parking_lat", lat.toString())
            .putString("parking_lon", lon.toString())
            .putLong("parking_time_ms", System.currentTimeMillis())
            .putString("parking_address", "Local de parada do veículo")
            .apply()
        loadParkingInfo()
    }

    private fun clearParkingInfo() {
        getSharedPreferences("CarTracksParking", Context.MODE_PRIVATE).edit().clear().apply()
        parkingInfoState.value = null
        Toast.makeText(this, "Vaga de estacionamento liberada!", Toast.LENGTH_SHORT).show()
    }

    // Vehicle & Fuel Config
    private val vehicleNameState = mutableStateOf("Meu Carro")
    private val vehiclePlateState = mutableStateOf("")
    private val tankCapacityState = mutableStateOf(50.0)
    private val primaryFuelTypeState = mutableStateOf("Gasolina Comum")
    private val odometerBaseState = mutableStateOf(0.0)
    private val estimatedKmLState = mutableStateOf(12.0)
    private val kmSinceLastFullState = mutableStateOf(0.0)
    private val initialSetupDoneState = mutableStateOf(false)
    private val showInitialSetupDialogState = mutableStateOf(false)
    private val showEditOdometerDialogState = mutableStateOf(false)
    private val consumptionState = mutableStateOf(FuelConsumptionModel(emptyMap(), true, emptyMap(), emptyList(), "Gasolina Comum"))

    private val appLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val acc = if (location.hasAccuracy()) location.accuracy else 8.0f
            val (fLat, fLon) = liveKalmanFilter.filter(location.latitude, location.longitude, acc, location.time)
            currentGpsState.value = Pair(fLat, fLon)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun startLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val locManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            try {
                val lastGps = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = locManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val bestLast = lastGps ?: lastNet
                if (bestLast != null) {
                    currentGpsState.value = Pair(bestLast.latitude, bestLast.longitude)
                }
                locManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1.0f,
                    appLocationListener
                )
                locManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2.0f,
                    appLocationListener
                )
            } catch (e: SecurityException) {
                // Ignore if not permitted yet
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopLocationUpdates() {
        val locManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        try {
            locManager.removeUpdates(appLocationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val maintenanceState = mutableStateOf<List<MaintenanceItem>>(emptyList())

    private fun saveMaintenance(items: List<MaintenanceItem>) {
        MaintenanceStore.save(this, items)
        maintenanceState.value = items
        MaintenanceStore.notifyDue(this, getCurrentOdometer())
        android.app.backup.BackupManager(this).dataChanged()
    }

    private val driveBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            CarTracksBackupHelper.writeBackupTo(this, uri)
            Toast.makeText(this, "Backup salvo no destino escolhido.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao salvar backup: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            startLocationUpdates()
            doStartTrip()
        } else {
            Toast.makeText(this, "Permissão de GPS necessária para gravar o trajeto.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        providerUtils = ContentProviderUtils(this)
        fuelDb = FuelDatabaseHelper.getInstance(this)

        // Clean fake demo trips and mock fuel entries for real-life usage
        cleanDemoData()

        loadPreferences()
        loadCustomLocations()
        loadFuelEntries()
        maintenanceState.value = MaintenanceStore.load(this)
        checkBatteryOptimization()
        loadParkingInfo()
        try {
            org.maplibre.android.MapLibre.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
        loadFuelEntries()
        checkBatteryOptimization()
        loadParkingInfo()
        try {
            org.maplibre.android.MapLibre.getInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        startLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        MaintenanceStore.notifyDue(this, getCurrentOdometer())
        startLocationUpdates()
        checkBatteryOptimization()
        loadParkingInfo()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
        serviceConnection?.unbind(this)
    }

    private fun cleanDemoData(showToast: Boolean = false) {
        if (!::fuelDb.isInitialized) {
            fuelDb = FuelDatabaseHelper.getInstance(this)
        }
        if (!::providerUtils.isInitialized) {
            providerUtils = ContentProviderUtils(this)
        }
        val prefs = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE)
        val alreadyCleaned = prefs.getBoolean("demo_data_purged_v15", false)
        if (!alreadyCleaned || showToast) {
            MockDataGenerator.purgeAllMockTrips(this)
            fuelDb.deleteMockEntries()
            prefs.edit().putBoolean("demo_data_purged_v15", true).apply()
            loadTrips()
            loadFuelEntries()
            if (showToast) {
                Toast.makeText(this, "Viagens e postos de demonstração apagados!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPreferences() {
        val sp = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE)
        vehicleNameState.value = sp.getString("vehicle_name", "Meu Carro") ?: "Meu Carro"
        vehiclePlateState.value = sp.getString("vehicle_plate", "") ?: ""
        tankCapacityState.value = sp.getFloat("tank_capacity_liters", 50f).toDouble()
        primaryFuelTypeState.value = sp.getString("primary_fuel_type", "Gasolina Comum") ?: "Gasolina Comum"
        odometerBaseState.value = sp.getFloat("odometer_base_km", 0f).toDouble()
        estimatedKmLState.value = sp.getFloat("estimated_km_per_liter", 12f).toDouble()
        kmSinceLastFullState.value = sp.getFloat("km_since_last_full", 0f).toDouble()

        val setupDone = sp.getBoolean("initial_setup_completed", false)
        initialSetupDoneState.value = setupDone
        if (!setupDone) {
            showInitialSetupDialogState.value = true
        }
    }

    private fun getCurrentOdometer(): Double {
        val totalRecordedKm = tripsState.value.sumOf { it.distanceKm }
        val base = odometerBaseState.value
        return if (base > 0) base + totalRecordedKm else totalRecordedKm
    }

    /**
     * Liters in the tank: start from the last full fill (or from the setup answer "km since
     * the tank was full"), add partial fills after it and subtract the fuel of later trips.
     */
    private fun getCurrentFuelLiters(): Double {
        val cap = tankCapacityState.value
        val model = consumptionState.value
        val fills = fuelState.value
        val setupRefMs = getSharedPreferences("CarTracksPrefs", MODE_PRIVATE).getLong("tank_ref_time_ms", 0L)
        val lastFull = fills.filter { it.isFullTank }.maxByOrNull { it.dateTime }
        val (refMs, startLiters) = if (lastFull != null && lastFull.dateTime >= setupRefMs) {
            lastFull.dateTime to cap
        } else {
            setupRefMs to cap - kmSinceLastFullState.value / model.baseKmL(model.familyAt(setupRefMs))
        }
        val added = fills.filter { !it.isFullTank && it.dateTime > refMs }.sumOf { it.liters }
        val burned = tripsState.value.filter { it.startTimeMs > refMs }.sumOf { it.fuelLiters }
        return (startLiters + added - burned).coerceIn(0.0, cap)
    }

    private fun saveOdometer(newOdometer: Double) {
        val totalRecordedKm = tripsState.value.sumOf { it.distanceKm }
        val base = (newOdometer - totalRecordedKm).coerceAtLeast(0.0)
        getSharedPreferences("CarTracksPrefs", MODE_PRIVATE).edit()
            .putFloat("odometer_base_km", base.toFloat())
            .apply()
        odometerBaseState.value = base
        showEditOdometerDialogState.value = false
        Toast.makeText(this, "Odômetro ajustado para ${newOdometer.toInt()} km", Toast.LENGTH_SHORT).show()
    }

    private fun saveFullVehicleConfig(
        name: String,
        plate: String,
        tankCapacity: Double,
        primaryFuelType: String,
        odometer: Double,
        estimatedKmL: Double,
        kmSinceLastFull: Double
    ) {
        val totalRecordedKm = tripsState.value.sumOf { it.distanceKm }
        val baseOdo = (odometer - totalRecordedKm).coerceAtLeast(0.0)
        getSharedPreferences("CarTracksPrefs", MODE_PRIVATE).edit()
            .putString("vehicle_name", name)
            .putString("vehicle_plate", plate)
            .putFloat("tank_capacity_liters", tankCapacity.toFloat())
            .putString("primary_fuel_type", primaryFuelType)
            .putFloat("odometer_base_km", baseOdo.toFloat())
            .putFloat("estimated_km_per_liter", estimatedKmL.toFloat())
            .putFloat("km_since_last_full", kmSinceLastFull.toFloat())
            .putFloat(FuelFamily.of(primaryFuelType).prefKey, estimatedKmL.toFloat())
            .putLong("tank_ref_time_ms", System.currentTimeMillis())
            .putBoolean("initial_setup_completed", true)
            .apply()

        vehicleNameState.value = name
        vehiclePlateState.value = plate
        tankCapacityState.value = tankCapacity
        primaryFuelTypeState.value = primaryFuelType
        odometerBaseState.value = baseOdo
        estimatedKmLState.value = estimatedKmL
        kmSinceLastFullState.value = kmSinceLastFull
        initialSetupDoneState.value = true
        showInitialSetupDialogState.value = false
        reloadConsumption()
        Toast.makeText(this, "Dados do veículo salvos!", Toast.LENGTH_SHORT).show()
    }

    private fun updateTrip(tripId: Long, newName: String, newDescription: String) {
        try {
            val track = providerUtils.getTrack(Track.Id(tripId))
            if (track != null) {
                val updated = Track(
                    track.id(),
                    track.uuid(),
                    newName,
                    newDescription,
                    track.activityTypeLocalized(),
                    track.activityType(),
                    track.zoneOffset(),
                    track.statistics()
                )
                providerUtils.updateTrack(updated)
                loadTrips()
                Toast.makeText(this, "Viagem atualizada com sucesso!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteTrip(tripId: Long) {
        try {
            providerUtils.deleteTrack(this, Track.Id(tripId))
            loadTrips()
            Toast.makeText(this, "Viagem excluída com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateFuelEntry(
        id: Long,
        stationName: String,
        liters: Double,
        price: Double,
        total: Double,
        odo: Double,
        notes: String,
        isFull: Boolean,
        fuelType: String
    ) {
        try {
            fuelDb.updateEntry(id, stationName, liters, price, total, odo, notes, isFull, fuelType)
            loadFuelEntries()
            Toast.makeText(this, "Abastecimento atualizado!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao atualizar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFuelEntry(id: Long) {
        try {
            fuelDb.deleteEntry(id)
            loadFuelEntries()
            Toast.makeText(this, "Abastecimento excluído!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Erro ao excluir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
            val model = consumptionState.value
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

                        val startMs = item.startTime().toEpochMilli()
                        val family = model.familyAt(startMs)
                        val tripKmL = model.tripKmL(avgSpd, startMs)
                        val fuel = if (tripKmL > 0) km / tripKmL else 0.0
                        val cost = fuel * model.pricePerLiter(family, startMs)

                        // Compute Commute Badge if start/end matches Home or Work
                        val badge = detectCommuteBadge(item.id().id())

                        list.add(
                            TripItem(
                                id = item.id().id(),
                                name = friendlyTripName(item.name(), item.startTime().atOffset(item.zoneOffset())),
                                description = item.description(),
                                distanceKm = km,
                                durationMin = minutes,
                                startTimeMs = item.startTime().toEpochMilli(),
                                dateFormatted = item.startTime().atOffset(item.zoneOffset()).format(formatter),
                                avgSpeedKmh = avgSpd,
                                maxSpeedKmh = maxSpd,
                                fuelLiters = fuel,
                                fuelCost = cost,
                                commuteBadge = badge,
                                kmL = tripKmL,
                                fuelLabel = family.label,
                                trafficLabel = FuelConsumptionModel.trafficLabel(avgSpd)
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

    /** Default track names are ISO timestamps ("2026-11-04T18:06-03"); show a readable one. */
    private fun friendlyTripName(name: String?, start: java.time.OffsetDateTime): String {
        if (name != null && name.isNotBlank() && !Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}.*""").matches(name)) return name
        return "Viagem · " + start.format(DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", Locale("pt", "BR")))
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
                notes = e.notes ?: "",
                isFullTank = e.isFullTank,
                fuelType = e.fuelType,
                latitude = e.latitude,
                longitude = e.longitude
            )
        }
        fuelState.value = entries
        reloadConsumption()
    }

    /** Rebuilds the consumption model (pump cycles + Ajustes) and re-prices every trip. */
    private fun reloadConsumption() {
        consumptionState.value = FuelConsumptionModel.load(this, fuelState.value, primaryFuelTypeState.value)
        loadTrips()
    }

    private fun saveConsumption(values: Map<FuelFamily, Double>, usePump: Boolean) {
        FuelConsumptionModel.saveManual(this, values, usePump)
        reloadConsumption()
        Toast.makeText(this, "Consumo atualizado. Viagens recalculadas.", Toast.LENGTH_SHORT).show()
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

        val model = consumptionState.value
        val family = model.currentFamily()
        val avgSoFar = if (sec > 0) km / (sec / 3600.0) else 0.0
        val liveKmL = model.baseKmL(family) * FuelConsumptionModel.trafficFactor(avgSoFar)
        val fuel = if (liveKmL > 0) km / liveKmL else 0.0
        val cost = fuel * model.pricePerLiter(family)

        // Breadcrumbs
        val currentBreadcrumbs = liveCockpitState.value.breadcrumbPoints.toMutableList()
        if (curLat != null && curLon != null) {
            if (currentBreadcrumbs.isEmpty() ||
                distKm(currentBreadcrumbs.last().lat, currentBreadcrumbs.last().lon, curLat, curLon) > 0.015) {
                currentBreadcrumbs.add(TripPoint(curLat, curLon, curSpd, curAlt ?: 0.0, System.currentTimeMillis()))
            }
        }

        val isSmartPaused = service.isSmartPaused
        liveCockpitState.value = LiveCockpitData(
            isRecording = true,
            isSmartPaused = isSmartPaused,
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
                    Toast.makeText(this, "Viagem iniciada no Live Cockpit!", Toast.LENGTH_SHORT).show()
                    val currentLoc = currentGpsState.value
                    liveCockpitState.value = LiveCockpitData(
                        isRecording = true,
                        trackId = trackId.id(),
                        currentLat = currentLoc?.first,
                        currentLon = currentLoc?.second,
                        breadcrumbPoints = if (currentLoc != null) {
                            listOf(TripPoint(currentLoc.first, currentLoc.second, 0.0, 0.0, System.currentTimeMillis()))
                        } else emptyList()
                    )
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
            val lastLoc = currentGpsState.value
            if (lastLoc != null) {
                saveParkingInfo(lastLoc.first, lastLoc.second)
            }
            service.endCurrentTrack()
            val autoFile = CarTracksBackupHelper.saveAutoBackup(this)
            android.app.backup.BackupManager(this).dataChanged()
            MaintenanceStore.notifyDue(this, getCurrentOdometer())
            val msg = if (autoFile != null) {
                "Viagem salva! Local de parada e backup automático registrados."
            } else {
                "Viagem concluída e gravada com sucesso!"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            liveCockpitState.value = LiveCockpitData(isRecording = false)
            loadTrips()
        }
    }

    @Composable
    private fun MainAppNavigation() {
        var selectedTab by remember { mutableStateOf(0) }
        var activeDetailTrip by remember { mutableStateOf<TripItem?>(null) }
        var isPeriodMapOpen by remember { mutableStateOf(false) }
        var isReportOpen by remember { mutableStateOf(false) }
        var isMaintenanceOpen by remember { mutableStateOf(false) }
        var isCockpitFullscreen by remember { mutableStateOf(false) }
        var selectedPeriod by remember { mutableStateOf(PeriodFilter.TODAS) }

        val trips by tripsState
        val fuelEntries by fuelState
        val liveData by liveCockpitState
        val homeLoc by homeLocationState
        val workLoc by workLocationState
        val currentGps by currentGpsState
        val vehicleName by vehicleNameState
        val vehiclePlate by vehiclePlateState
        val tankCapacity by tankCapacityState
        val primaryFuelType by primaryFuelTypeState

        // System Back Gesture Handlers - Never accidentally closes the app!
        BackHandler(enabled = isCockpitFullscreen) {
            isCockpitFullscreen = false
        }

        BackHandler(enabled = activeDetailTrip != null) {
            activeDetailTrip = null
        }

        BackHandler(enabled = isPeriodMapOpen) {
            isPeriodMapOpen = false
        }

        BackHandler(enabled = isReportOpen && activeDetailTrip == null) {
            isReportOpen = false
        }

        BackHandler(enabled = isMaintenanceOpen) {
            isMaintenanceOpen = false
        }

        BackHandler(enabled = !isCockpitFullscreen && activeDetailTrip == null && !isPeriodMapOpen && !isReportOpen && !isMaintenanceOpen && selectedTab != 0) {
            selectedTab = 0
        }

        // Auto-open cockpit if user just started a trip
        LaunchedEffect(liveData.isRecording) {
            if (liveData.isRecording && !isCockpitFullscreen && activeDetailTrip == null && !isPeriodMapOpen) {
                isCockpitFullscreen = true
            }
        }

        // Vehicle dialogs (Início: botão de ajustes e lápis do odômetro; também o onboarding)
        if (showInitialSetupDialogState.value) {
            VehicleSetupDialog(
                isInitialOnboarding = !initialSetupDoneState.value,
                initialName = vehicleName,
                initialPlate = vehiclePlate,
                initialTankCapacity = tankCapacity,
                initialFuelType = primaryFuelType,
                initialOdometer = getCurrentOdometer(),
                initialEstimatedKmL = estimatedKmLState.value,
                initialKmSinceLastFull = kmSinceLastFullState.value,
                onConfirm = { name, plate, cap, fType, odo, estKmL, kmSince ->
                    saveFullVehicleConfig(name, plate, cap, fType, odo, estKmL, kmSince)
                },
                onDismiss = { showInitialSetupDialogState.value = false }
            )
        }
        if (showEditOdometerDialogState.value) {
            QuickEditOdometerDialog(
                currentOdometer = getCurrentOdometer(),
                onConfirm = { saveOdometer(it) },
                onDismiss = { showEditOdometerDialogState.value = false }
            )
        }

        // Fullscreen Overlays First
        if (isCockpitFullscreen && liveData.isRecording) {
            LiveCockpitScreen(
                liveData = liveData,
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
                },
                onUpdateTrip = { tripId, newName, newDesc ->
                    updateTrip(tripId, newName, newDesc)
                    activeDetailTrip = activeDetailTrip?.copy(name = newName, description = newDesc)
                },
                onDeleteTrip = { tripId ->
                    deleteTrip(tripId)
                    activeDetailTrip = null
                }
            )
            return
        }

        if (isMaintenanceOpen) {
            MaintenanceScreen(
                items = maintenanceState.value,
                odometerKm = getCurrentOdometer(),
                onSave = { saveMaintenance(it) },
                onBack = { isMaintenanceOpen = false }
            )
            return
        }

        if (isReportOpen) {
            ReportScreen(
                trips = trips,
                fills = fuelEntries,
                home = homeLoc,
                work = workLoc,
                loadPoints = { getTripPoints(it) },
                onBack = { isReportOpen = false }
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
                CyberNavBar(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    items = listOf(
                        Icons.Default.Speed to "Início",
                        Icons.Default.Route to "Viagens",
                        Icons.Default.LocalGasStation to "Posto",
                        Icons.Default.Settings to "Ajustes"
                    )
                )
            },
            containerColor = CyberInk
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
                        vehicleName = vehicleName,
                        vehiclePlate = vehiclePlate,
                        tankCapacityLiters = tankCapacity,
                        primaryFuelType = primaryFuelType,
                        currentFuelLiters = getCurrentFuelLiters(),
                        estimatedKmL = consumptionState.value.let { it.baseKmL(it.currentFamily()) },
                        odometerKm = getCurrentOdometer(),
                        parkingInfo = parkingInfoState.value,
                        onNavigateToParking = { lat, lon ->
                            try {
                                val uri = Uri.parse("google.navigation:q=$lat,$lon&mode=w")
                                startActivity(Intent(Intent.ACTION_VIEW, uri))
                            } catch (e: Exception) {
                                val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(Meu+Carro)")
                                startActivity(Intent(Intent.ACTION_VIEW, geo))
                            }
                        },
                        onClearParking = { clearParkingInfo() },
                        onEditOdometerClicked = { showEditOdometerDialogState.value = true },
                        onConfigureVehicleClicked = { showInitialSetupDialogState.value = true },
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
                        onViewAllTripsClicked = { selectedTab = 1 },
                        maintenanceCard = {
                            MaintenanceSummaryCard(
                                items = maintenanceState.value,
                                odometerKm = getCurrentOdometer(),
                                onClick = { isMaintenanceOpen = true }
                            )
                        }
                    )
                    1 -> TripsScreen(
                        trips = trips,
                        selectedFilter = selectedPeriod,
                        onFilterSelected = { selectedPeriod = it },
                        onTripClicked = { activeDetailTrip = it },
                        onPeriodMapClicked = { isPeriodMapOpen = true },
                        onDeleteTrip = { tripId -> deleteTrip(tripId) },
                        onUpdateTrip = { tripId, name, desc -> updateTrip(tripId, name, desc) },
                        onOpenReport = { isReportOpen = true }
                    )
                    2 -> FuelScreen(
                        fuelEntries = fuelEntries,
                        tankCapacityLiters = tankCapacity,
                        currentGps = currentGps,
                        currentVehicleOdometer = getCurrentOdometer(),
                        onAddFuel = { station, liters, price, total, odo, notes, isFull, fuelType, lat, lon ->
                            fuelDb.addEntry(station, liters, price, total, odo, notes, isFull, fuelType, lat, lon)
                            loadFuelEntries()
                            Toast.makeText(this@CarMainActivity, "Abastecimento registrado!", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateFuelLocation = { entryId, lat, lon ->
                            fuelDb.updateEntryLocation(entryId, lat, lon)
                            loadFuelEntries()
                            Toast.makeText(this@CarMainActivity, "Localização do posto salva no mapa!", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateFuel = { entryId, station, liters, price, total, odo, notes, isFull, fuelType ->
                            updateFuelEntry(entryId, station, liters, price, total, odo, notes, isFull, fuelType)
                        },
                        onDeleteFuel = { entryId ->
                            deleteFuelEntry(entryId)
                        }
                    )
                    3 -> SettingsScreen(
                        homeLoc = homeLoc,
                        workLoc = workLoc,
                        currentGps = currentGps,
                        vehicleName = vehicleName,
                        vehiclePlate = vehiclePlate,
                        tankCapacityLiters = tankCapacity,
                        primaryFuelType = primaryFuelType,
                        currentOdometer = getCurrentOdometer(),
                        estimatedKmL = estimatedKmLState.value,
                        kmSinceLastFull = kmSinceLastFullState.value,
                        isBatteryOptimized = isBatteryOptimizedState.value,
                        onRequestIgnoreBattery = { requestIgnoreBatteryOptimization() },
                        onSaveLocation = { key, lat, lon, label ->
                            saveCustomLocation(key, lat, lon, label)
                        },
                        onSaveVehicleConfig = { name, plate, cap, fType, odo, estKmL, kmSince ->
                            saveFullVehicleConfig(name, plate, cap, fType, odo, estKmL, kmSince)
                        },
                        onPurgeDemoData = {
                            cleanDemoData(showToast = true)
                        },
                        consumption = consumptionState.value,
                        onBackupToDrive = {
                            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(java.util.Date())
                            driveBackupLauncher.launch("CarTracks_backup_$stamp.json")
                        },
                        onSaveConsumption = { values, usePump -> saveConsumption(values, usePump) },
                        onExportBackup = {
                            CarTracksBackupHelper.exportBackup(this@CarMainActivity)
                        },
                        onImportBackup = { uri ->
                            try {
                                val result = CarTracksBackupHelper.importBackup(this@CarMainActivity, uri)
                                loadTrips()
                                loadFuelEntries()
                                loadPreferences()
                                loadCustomLocations()
                                maintenanceState.value = MaintenanceStore.load(this@CarMainActivity)
                                val msg = if (result.skippedCount > 0) {
                                    "Backup importado: ${result.tripsCount} viagens novas (${result.skippedCount} já existentes ignoradas), ${result.fuelCount} abastecimentos."
                                } else {
                                    "Backup importado com sucesso! (${result.tripsCount} viagens, ${result.fuelCount} abastecimentos)"
                                }
                                Toast.makeText(this@CarMainActivity, msg, Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(this@CarMainActivity, "Erro ao importar backup: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
        }
    }
}
