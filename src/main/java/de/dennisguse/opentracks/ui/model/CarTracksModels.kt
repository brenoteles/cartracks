package de.dennisguse.opentracks.ui.model

data class TripItem(
    val id: Long,
    val name: String,
    val description: String,
    val distanceKm: Double,
    val durationMin: Long,
    val startTimeMs: Long,
    val dateFormatted: String,
    val avgSpeedKmh: Double,
    val maxSpeedKmh: Double,
    val fuelLiters: Double,
    val fuelCost: Double,
    val commuteBadge: String? = null,
    val kmL: Double = 0.0,
    val fuelLabel: String = "",
    val trafficLabel: String = ""
)

data class TripPoint(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val altitudeM: Double,
    val timeMs: Long
)

data class FuelItem(
    val id: Long,
    val dateTime: Long,
    val stationName: String,
    val liters: Double,
    val pricePerLiter: Double,
    val totalCost: Double,
    val odometerKm: Double,
    val notes: String,
    val isFullTank: Boolean = true,
    val fuelType: String = "Gasolina Comum",
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class MapMarkerItem(
    val lat: Double,
    val lon: Double,
    val title: String,
    val snippet: String = ""
)

data class CustomLocation(
    val lat: Double,
    val lon: Double,
    val label: String,
    val isSet: Boolean
)

enum class PeriodFilter(val label: String) {
    HOJE("Hoje"),
    SETE_DIAS("7 Dias"),
    ESTE_MES("Este Mês"),
    TODAS("Todas")
}

enum class DestinationFilter(val label: String) {
    TODOS("Todos Destinos"),
    CASA("🏠 Para Casa"),
    TRABALHO("🏢 Para o Trabalho"),
    OUTROS("🚗 Outros Trajetos")
}

enum class FuelTypeFilter(val label: String) {
    TODOS("Todos"),
    GASOLINA_COMUM("Gasolina Comum"),
    GASOLINA_ADITIVADA("Gasolina Aditivada"),
    ETANOL("Etanol"),
    DIESEL("Diesel")
}

data class LiveCockpitData(
    val isRecording: Boolean = false,
    val isSmartPaused: Boolean = false,
    val trackId: Long = 0,
    val currentSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0,
    val fuelLiters: Double = 0.0,
    val fuelCost: Double = 0.0,
    val currentLat: Double? = null,
    val currentLon: Double? = null,
    val currentAlt: Double? = null,
    val breadcrumbPoints: List<TripPoint> = emptyList()
)

data class ParkingInfo(
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val address: String = "Último local de estacionamento"
)

data class MonthlyReportSummary(
    val monthYear: String, // e.g. "Novembro 2026"
    val totalTrips: Int,
    val totalDistanceKm: Double,
    val totalDurationHours: Double,
    val totalFuelLiters: Double,
    val totalTripCost: Double,
    val totalFuelExpense: Double,
    val costPerKm: Double,
    val commuteTripsCount: Int
)
