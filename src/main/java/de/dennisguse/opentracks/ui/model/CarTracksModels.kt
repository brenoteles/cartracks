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
    val commuteBadge: String? = null
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
    val notes: String
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

data class LiveCockpitData(
    val isRecording: Boolean = false,
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
