package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.FuelItem

data class FuelEfficiencyStats(
    val averageKmL: Double,
    val averageCostPerKm: Double,
    val lastRecordedKmL: Double?,
    val totalRecordedKm: Double,
    val totalFuelSpent: Double,
    val fullTankCyclesCount: Int,
    val partialFillsCount: Int
)

object FuelEfficiencyCalculator {

    /**
     * Calculates real fuel economy considering both Full Tank and Partial Fill-ups.
     * Full-tank to full-tank cycles give 100% exact consumption by summing intermediate partial liters.
     */
    fun calculateStats(entries: List<FuelItem>, tankCapacityLiters: Double = 50.0): FuelEfficiencyStats {
        val totalSpent = entries.sumOf { it.totalCost }
        val partialCount = entries.count { !it.isFullTank }

        if (entries.size < 2) {
            return FuelEfficiencyStats(
                averageKmL = 12.5,
                averageCostPerKm = 5.89 / 12.5,
                lastRecordedKmL = null,
                totalRecordedKm = 0.0,
                totalFuelSpent = totalSpent,
                fullTankCyclesCount = 0,
                partialFillsCount = partialCount
            )
        }

        // Sort ascending by odometer
        val sorted = entries.filter { it.odometerKm > 0 }.sortedBy { it.odometerKm }

        var totalCycleKm = 0.0
        var totalCycleLiters = 0.0
        var totalCycleCost = 0.0
        var lastCycleKmL: Double? = null
        var fullTankCycles = 0

        // Track accumulated fuel since previous full tank
        var lastFullTankIndex: Int? = null

        for (i in sorted.indices) {
            val curr = sorted[i]
            if (curr.isFullTank) {
                if (lastFullTankIndex != null) {
                    val prevFull = sorted[lastFullTankIndex]
                    val deltaKm = curr.odometerKm - prevFull.odometerKm

                    // Sum all liters added since previous full tank (including partials and this full tank)
                    var litersInCycle = 0.0
                    var costInCycle = 0.0
                    for (j in (lastFullTankIndex + 1)..i) {
                        litersInCycle += sorted[j].liters
                        costInCycle += sorted[j].totalCost
                    }

                    if (deltaKm > 0 && litersInCycle > 0) {
                        val kmL = deltaKm / litersInCycle
                        lastCycleKmL = kmL
                        totalCycleKm += deltaKm
                        totalCycleLiters += litersInCycle
                        totalCycleCost += costInCycle
                        fullTankCycles++
                    }
                }
                lastFullTankIndex = i
            }
        }

        // Fallback if no 2 full tanks exist yet
        val avgKmL = if (fullTankCycles > 0 && totalCycleLiters > 0) {
            totalCycleKm / totalCycleLiters
        } else {
            // General odometer span / total liters
            val first = sorted.first()
            val last = sorted.last()
            val delta = last.odometerKm - first.odometerKm
            val liters = entries.sumOf { it.liters }
            if (delta > 0 && liters > 0) delta / liters else 12.5
        }

        val avgCostKm = if (totalCycleKm > 0) totalCycleCost / totalCycleKm else 5.89 / avgKmL

        return FuelEfficiencyStats(
            averageKmL = avgKmL,
            averageCostPerKm = avgCostKm,
            lastRecordedKmL = lastCycleKmL,
            totalRecordedKm = if (totalCycleKm > 0) totalCycleKm else (sorted.last().odometerKm - sorted.first().odometerKm),
            totalFuelSpent = totalSpent,
            fullTankCyclesCount = fullTankCycles,
            partialFillsCount = partialCount
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
