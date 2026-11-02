package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.FuelItem

data class FuelEfficiencyStats(
    val averageKmL: Double,
    val averageCostPerKm: Double,
    val lastRecordedKmL: Double?,
    val totalRecordedKm: Double,
    val totalFuelSpent: Double,
    val intervalsCount: Int
)

object FuelEfficiencyCalculator {

    fun calculateStats(entries: List<FuelItem>): FuelEfficiencyStats {
        if (entries.size < 2) {
            val totalSpent = entries.sumOf { it.totalCost }
            return FuelEfficiencyStats(
                averageKmL = 12.5, // Default automotive benchmark
                averageCostPerKm = 5.89 / 12.5,
                lastRecordedKmL = null,
                totalRecordedKm = 0.0,
                totalFuelSpent = totalSpent,
                intervalsCount = 0
            )
        }

        // Sort ascending by odometer
        val sorted = entries.filter { it.odometerKm > 0 }.sortedBy { it.odometerKm }
        var totalDeltaKm = 0.0
        var totalLiters = 0.0
        var totalCost = 0.0
        var lastKmL: Double? = null
        var intervals = 0

        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val deltaKm = curr.odometerKm - prev.odometerKm
            if (deltaKm > 0 && curr.liters > 0) {
                totalDeltaKm += deltaKm
                totalLiters += curr.liters
                totalCost += curr.totalCost
                lastKmL = deltaKm / curr.liters
                intervals++
            }
        }

        val avgKmL = if (totalLiters > 0 && totalDeltaKm > 0) totalDeltaKm / totalLiters else 12.5
        val avgCostKm = if (totalDeltaKm > 0) totalCost / totalDeltaKm else 5.89 / avgKmL

        return FuelEfficiencyStats(
            averageKmL = avgKmL,
            averageCostPerKm = avgCostKm,
            lastRecordedKmL = lastKmL,
            totalRecordedKm = totalDeltaKm,
            totalFuelSpent = entries.sumOf { it.totalCost },
            intervalsCount = intervals
        )
    }

    /**
     * Estimates km/L for an individual trip based on its average speed profile.
     */
    fun estimateTripKmL(avgSpeedKmh: Double): Double {
        return when {
            avgSpeedKmh <= 0 -> 12.0
            avgSpeedKmh < 22 -> 8.8  // Heavy urban stop-and-go
            avgSpeedKmh < 40 -> 11.2 // Moderate city traffic
            avgSpeedKmh < 65 -> 13.8 // Avenues and light flow
            avgSpeedKmh < 95 -> 15.6 // Highway optimal cruising speed
            avgSpeedKmh < 120 -> 13.5 // Fast highway (aerodynamic drag)
            else -> 11.0             // Very high speed
        }
    }
}
