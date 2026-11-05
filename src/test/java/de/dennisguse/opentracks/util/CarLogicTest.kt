package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.FuelItem
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CarLogicTest {

    private fun fill(odo: Double, liters: Double, full: Boolean, type: String = "Gasolina Comum", day: Long = 0) =
        FuelItem(0, day * 86_400_000L, "Posto", liters, 6.0, liters * 6.0, odo, "", full, type)

    @Test
    fun pumpMeasurementUsesFullToFullCycle() {
        val m = FuelConsumptionModel.measureAtPump(listOf(fill(1000.0, 40.0, true), fill(1260.0, 20.0, true)))
        assertEquals(13.0, m.getValue(FuelFamily.GASOLINA).kmL, 0.001)
        assertNull(m[FuelFamily.ETANOL])
    }

    @Test
    fun partialFillsCountInTheCycle() {
        val m = FuelConsumptionModel.measureAtPump(
            listOf(fill(1000.0, 40.0, true), fill(1150.0, 10.0, false), fill(1300.0, 15.0, true))
        )
        assertEquals(300.0 / 25.0, m.getValue(FuelFamily.GASOLINA).kmL, 0.001)
    }

    @Test
    fun cycleIsAttributedToTheFuelThatWasBurned() {
        // Tank filled with ethanol, then refilled with gasoline: the 9 km/L belongs to ethanol.
        val m = FuelConsumptionModel.measureAtPump(
            listOf(fill(1000.0, 40.0, true, "Etanol"), fill(1270.0, 30.0, true, "Gasolina Comum"))
        )
        assertEquals(9.0, m.getValue(FuelFamily.ETANOL).kmL, 0.001)
        assertNull(m[FuelFamily.GASOLINA])
    }

    @Test
    fun manualValueIsUsedWithoutPumpCycles() {
        val model = FuelConsumptionModel(
            mapOf(FuelFamily.GASOLINA to 13.0, FuelFamily.ETANOL to 9.1, FuelFamily.DIESEL to 15.0),
            usePumpMeasurement = true, pump = emptyMap(),
            fills = listOf(fill(1000.0, 40.0, true, "Etanol", day = 10)), primaryFuelType = "Gasolina Comum"
        )
        // Before the ethanol fill the car ran on its primary fuel; after it, on ethanol.
        assertEquals(FuelFamily.GASOLINA, model.familyAt(5 * 86_400_000L))
        assertEquals(FuelFamily.ETANOL, model.currentFamily())
        assertEquals(9.1, model.baseKmL(model.currentFamily()), 0.001)
    }

    @Test
    fun stopAndGoBurnsMoreThanHighway() {
        val jam = FuelConsumptionModel.trafficFactor(12.0)
        val highway = FuelConsumptionModel.trafficFactor(85.0)
        assertTrue(jam < 1.0)
        assertTrue(highway > 1.0)
        assertEquals("Anda e para", FuelConsumptionModel.trafficLabel(12.0))
    }

    @Test
    fun maintenanceSoonAndDueByKm() {
        val oil = MaintenanceItem(1, "Óleo", 10_000, null, 110_500.0, null)
        assertEquals(MaintenanceStatus.SOON, MaintenanceStore.evaluate(oil, 120_078.0).status)
        assertEquals(422.0, MaintenanceStore.evaluate(oil, 120_078.0).kmLeft!!, 0.001)
        assertEquals(MaintenanceStatus.DUE, MaintenanceStore.evaluate(oil, 120_600.0).status)
        assertEquals(MaintenanceStatus.OK, MaintenanceStore.evaluate(oil, 112_000.0).status)
    }

    @Test
    fun maintenanceDueByDate() {
        val zone = ZoneId.systemDefault()
        val lastYear = LocalDate.now(zone).minusMonths(13).atStartOfDay(zone).toInstant().toEpochMilli()
        val ipva = MaintenanceItem(10, "IPVA", null, 12, null, lastYear)
        val st = MaintenanceStore.evaluate(ipva, 0.0)
        assertEquals(MaintenanceStatus.DUE, st.status)
        assertTrue(st.daysLeft!! < 0)
    }

    @Test
    fun zeroPriceFillsFallBackToDefaultPrice() {
        val model = FuelConsumptionModel(emptyMap(), true, emptyMap(),
            listOf(FuelItem(0, 0, "Posto", 10.0, 0.0, 0.0, 1000.0, "", true)), "Gasolina Comum")
        assertEquals(FuelConsumptionModel.DEFAULT_PRICE, model.pricePerLiter(FuelFamily.GASOLINA), 0.001)
    }

    @Test
    fun maintenanceJsonToleratesNulls() {
        val arr = org.json.JSONArray("""[{"id":1,"name":"Óleo","intervalKm":10000,"intervalMonths":null,"lastDoneKm":null}]""")
        val item = MaintenanceStore.fromJson(arr).single()
        assertEquals(10_000, item.intervalKm)
        assertNull(item.intervalMonths)
    }

    @Test
    fun maintenanceJsonRoundTrip() {
        val items = listOf(
            MaintenanceItem(1, "Óleo", 10_000, 12, 110_500.0, 1_700_000_000_000L, "5W30"),
            MaintenanceItem(2, "IPVA", null, 12, null, null)
        )
        assertEquals(items, MaintenanceStore.fromJson(MaintenanceStore.toJson(items)))
    }
}
