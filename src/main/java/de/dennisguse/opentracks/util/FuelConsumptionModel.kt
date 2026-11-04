package de.dennisguse.opentracks.util

import android.content.Context
import de.dennisguse.opentracks.ui.model.FuelItem

/** Fuels that burn differently; both gasolines share one consumption figure. */
enum class FuelFamily(val label: String, val prefKey: String) {
    GASOLINA("Gasolina", "kml_gasolina"),
    ETANOL("Etanol", "kml_etanol"),
    DIESEL("Diesel", "kml_diesel");

    companion object {
        fun of(fuelType: String?): FuelFamily = when {
            fuelType == null -> GASOLINA
            fuelType.contains("Etanol", ignoreCase = true) -> ETANOL
            fuelType.contains("Diesel", ignoreCase = true) -> DIESEL
            else -> GASOLINA
        }
    }
}

/** km/L measured at the pump (full tank to full tank) for one fuel family. */
data class PumpMeasurement(val kmL: Double, val cycles: Int, val km: Double, val liters: Double)

/**
 * Consumption used for trip fuel, cost and tank estimates.
 *
 * Base km/L per fuel family comes from the pump (full-to-full cycles) when available and the
 * user did not force the manual value; otherwise from the value edited in Ajustes. Each trip
 * then gets a traffic factor from its average speed (total time includes stops, so stop-and-go
 * traffic lowers the average and the km/L), normalised so mixed driving equals the base value.
 */
class FuelConsumptionModel(
    val manualKmL: Map<FuelFamily, Double>,
    val usePumpMeasurement: Boolean,
    val pump: Map<FuelFamily, PumpMeasurement>,
    private val fills: List<FuelItem>,
    private val primaryFuelType: String
) {

    fun baseKmL(family: FuelFamily): Double {
        val measured = pump[family]
        if (usePumpMeasurement && measured != null) return measured.kmL
        return manualKmL[family] ?: DEFAULT_KML
    }

    fun isFromPump(family: FuelFamily): Boolean = usePumpMeasurement && pump[family] != null

    /** Fuel in the tank at a moment: the last fill before it, else the vehicle's primary fuel. */
    fun familyAt(timeMs: Long): FuelFamily {
        val last = fills.filter { it.dateTime <= timeMs }.maxByOrNull { it.dateTime }
        return FuelFamily.of(last?.fuelType ?: primaryFuelType)
    }

    fun currentFamily(): FuelFamily = familyAt(Long.MAX_VALUE)

    fun tripKmL(avgSpeedKmh: Double, startTimeMs: Long): Double =
        baseKmL(familyAt(startTimeMs)) * trafficFactor(avgSpeedKmh)

    /** R$/L of the last fill of that family, else the last fill, else a default. */
    fun pricePerLiter(family: FuelFamily, atMs: Long = Long.MAX_VALUE): Double {
        val sorted = fills.filter { it.dateTime <= atMs && it.pricePerLiter > 0 }.sortedByDescending { it.dateTime }
        return sorted.firstOrNull { FuelFamily.of(it.fuelType) == family }?.pricePerLiter
            ?: sorted.firstOrNull()?.pricePerLiter
            ?: fills.maxByOrNull { it.dateTime }?.pricePerLiter
            ?: DEFAULT_PRICE
    }

    companion object {
        const val DEFAULT_KML = 12.0
        const val DEFAULT_PRICE = 5.89
        private const val PREFS = "CarTracksPrefs"
        const val KEY_USE_PUMP = "consumption_use_pump"

        /** 1.0 = mixed city/road driving. Stop-and-go burns more, steady 60-100 km/h burns less. */
        fun trafficFactor(avgSpeedKmh: Double): Double = when {
            avgSpeedKmh <= 0 -> 1.0
            avgSpeedKmh < 15 -> 0.70
            avgSpeedKmh < 25 -> 0.80
            avgSpeedKmh < 40 -> 0.92
            avgSpeedKmh < 60 -> 1.02
            avgSpeedKmh < 95 -> 1.12
            avgSpeedKmh < 115 -> 1.02
            else -> 0.90
        }

        fun trafficLabel(avgSpeedKmh: Double): String = when {
            avgSpeedKmh <= 0 -> "—"
            avgSpeedKmh < 25 -> "Anda e para"
            avgSpeedKmh < 40 -> "Urbano"
            avgSpeedKmh < 60 -> "Misto"
            avgSpeedKmh < 115 -> "Estrada"
            else -> "Estrada rápida"
        }

        /**
         * Full-to-full cycles per family. The liters refilled at the closing full tank (plus
         * partials in between) were burned over the odometer delta; the fuel burned is the one
         * that was in the tank, i.e. the majority family of the fills that opened the cycle.
         */
        fun measureAtPump(fills: List<FuelItem>): Map<FuelFamily, PumpMeasurement> {
            val sorted = fills.filter { it.odometerKm > 0 }.sortedBy { it.odometerKm }
            val km = mutableMapOf<FuelFamily, Double>()
            val liters = mutableMapOf<FuelFamily, Double>()
            val cycles = mutableMapOf<FuelFamily, Int>()
            var lastFull = -1
            for (i in sorted.indices) {
                if (!sorted[i].isFullTank) continue
                if (lastFull >= 0) {
                    val deltaKm = sorted[i].odometerKm - sorted[lastFull].odometerKm
                    val refill = (lastFull + 1..i).sumOf { sorted[it].liters }
                    val burned = (lastFull until i).groupBy { FuelFamily.of(sorted[it].fuelType) }
                        .mapValues { (_, idx) -> idx.sumOf { sorted[it].liters } }
                    val family = burned.maxByOrNull { it.value }?.key ?: FuelFamily.of(sorted[lastFull].fuelType)
                    val kmL = if (refill > 0) deltaKm / refill else 0.0
                    if (deltaKm > 0 && kmL in 3.0..35.0) {
                        km[family] = (km[family] ?: 0.0) + deltaKm
                        liters[family] = (liters[family] ?: 0.0) + refill
                        cycles[family] = (cycles[family] ?: 0) + 1
                    }
                }
                lastFull = i
            }
            return km.keys.associateWith { f ->
                PumpMeasurement(km[f]!! / liters[f]!!, cycles[f]!!, km[f]!!, liters[f]!!)
            }
        }

        /** Manual km/L per family. Before v1.0.18 there was one value (estimated_km_per_liter). */
        fun loadManual(context: Context, primaryFuelType: String): Map<FuelFamily, Double> {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val legacy = sp.getFloat("estimated_km_per_liter", DEFAULT_KML.toFloat()).toDouble()
            val legacyFamily = FuelFamily.of(primaryFuelType)
            // Ethanol yields ~70% of gasoline per liter; diesel ~115%.
            val gasolineRef = when (legacyFamily) {
                FuelFamily.GASOLINA -> legacy
                FuelFamily.ETANOL -> legacy / 0.7
                FuelFamily.DIESEL -> legacy / 1.15
            }
            val defaults = mapOf(
                FuelFamily.GASOLINA to gasolineRef,
                FuelFamily.ETANOL to gasolineRef * 0.7,
                FuelFamily.DIESEL to gasolineRef * 1.15
            )
            return FuelFamily.values().associateWith { f ->
                if (sp.contains(f.prefKey)) sp.getFloat(f.prefKey, 0f).toDouble() else defaults.getValue(f)
            }
        }

        fun saveManual(context: Context, values: Map<FuelFamily, Double>, usePump: Boolean) {
            val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            values.forEach { (f, v) -> edit.putFloat(f.prefKey, v.toFloat()) }
            edit.putBoolean(KEY_USE_PUMP, usePump).apply()
        }

        fun load(context: Context, fills: List<FuelItem>, primaryFuelType: String): FuelConsumptionModel {
            val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return FuelConsumptionModel(
                manualKmL = loadManual(context, primaryFuelType),
                usePumpMeasurement = sp.getBoolean(KEY_USE_PUMP, true),
                pump = measureAtPump(fills),
                fills = fills,
                primaryFuelType = primaryFuelType
            )
        }
    }
}
