package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.data.Expense
import de.dennisguse.opentracks.data.ExpenseCategory
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DrivingInsightsTest {

    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun ts(year: Int, month: Int, day: Int, hour: Int = 8, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun trip(
        id: Long,
        startTimeMs: Long,
        distanceKm: Double = 15.0,
        durationMin: Long = 30L,
        fuelLiters: Double = 1.5,
        fuelCost: Double = 9.0,
        commuteBadge: String? = null,
        dateFormatted: String = "01/11/2026"
    ) = TripItem(
        id = id,
        name = "Viagem $id",
        description = "",
        distanceKm = distanceKm,
        durationMin = durationMin,
        startTimeMs = startTimeMs,
        dateFormatted = dateFormatted,
        avgSpeedKmh = if (durationMin > 0) distanceKm / (durationMin / 60.0) else 0.0,
        maxSpeedKmh = 80.0,
        fuelLiters = fuelLiters,
        fuelCost = fuelCost,
        commuteBadge = commuteBadge
    )

    private fun fill(
        id: Long,
        dateTime: Long,
        odometerKm: Double,
        liters: Double,
        totalCost: Double = liters * 6.0,
        isFullTank: Boolean = true,
        stationName: String = "Posto Centro"
    ) = FuelItem(
        id = id,
        dateTime = dateTime,
        stationName = stationName,
        liters = liters,
        pricePerLiter = if (liters > 0) totalCost / liters else 6.0,
        totalCost = totalCost,
        odometerKm = odometerKm,
        notes = "",
        isFullTank = isFullTank
    )

    private fun expense(
        id: Long,
        dateTime: Long,
        amount: Double,
        category: ExpenseCategory = ExpenseCategory.MANUTENCAO
    ) = Expense(
        id = id,
        dateTime = dateTime,
        category = category,
        description = "Despesa $id",
        amount = amount,
        odometerKm = null,
        maintenanceId = null
    )

    @Test
    fun periodStats_respectsHalfOpenWindowAndComputesTotals() {
        val start = ts(2026, 11, 1, 0, 0)
        val end = ts(2026, 11, 8, 0, 0)

        val trips = listOf(
            trip(1L, start - 1, distanceKm = 99.0),
            trip(2L, start, distanceKm = 20.0, durationMin = 30L, fuelLiters = 2.0),
            trip(3L, ts(2026, 11, 4, 10, 0), distanceKm = 30.0, durationMin = 45L, fuelLiters = 2.0),
            trip(4L, end, distanceKm = 88.0)
        )
        val fills = listOf(
            fill(1L, ts(2026, 11, 3, 12, 0), odometerKm = 1000.0, liters = 30.0, totalCost = 180.0),
            fill(2L, end, odometerKm = 1300.0, liters = 20.0, totalCost = 120.0)
        )
        val expenses = listOf(
            expense(1L, start - 10, amount = 50.0),
            expense(2L, ts(2026, 11, 5, 9, 0), amount = 45.0, category = ExpenseCategory.PEDAGIO)
        )

        val stats = DrivingInsights.periodStats(trips, fills, expenses, start, end)

        assertEquals(2, stats.trips)
        assertEquals(50.0, stats.km, 1e-6)
        assertEquals(75L, stats.drivingMin)
        assertEquals(4.0, stats.fuelLiters, 1e-6)
        assertEquals(12.5, stats.avgKmL, 1e-6)
        assertEquals(180.0, stats.fuelSpent, 1e-6)
        assertEquals(45.0, stats.otherSpent, 1e-6)
        assertEquals(225.0, stats.totalSpent, 1e-6)
        assertEquals(3L, stats.longestTrip?.id)
    }

    @Test
    fun weekSummary_splitsCurrentAndPreviousRollingWindows() {
        val now = ts(2026, 11, 15, 12, 0)
        val trips = listOf(
            trip(1L, ts(2026, 11, 13, 9, 0), distanceKm = 40.0, fuelLiters = 4.0),
            trip(2L, ts(2026, 11, 5, 9, 0), distanceKm = 25.0, fuelLiters = 2.5),
            trip(3L, ts(2026, 10, 25, 9, 0), distanceKm = 100.0, fuelLiters = 10.0)
        )

        val summary = DrivingInsights.weekSummary(trips, emptyList(), emptyList(), now)

        assertEquals(1, summary.current.trips)
        assertEquals(40.0, summary.current.km, 1e-6)
        assertEquals(1, summary.previous.trips)
        assertEquals(25.0, summary.previous.km, 1e-6)
    }

    @Test
    fun records_computesAllFourKindsAndIgnoresCurrentMonthForCheapestMonth() {
        val now = ts(2026, 12, 10, 12, 0)

        val fills = listOf(
            fill(1L, ts(2026, 10, 1, 8, 0), odometerKm = 1000.0, liters = 40.0, isFullTank = true),
            fill(2L, ts(2026, 10, 10, 8, 0), odometerKm = 1200.0, liters = 10.0, isFullTank = false),
            fill(3L, ts(2026, 10, 20, 8, 0), odometerKm = 1568.0, liters = 30.0, isFullTank = true),
            fill(4L, ts(2026, 11, 5, 8, 0), odometerKm = 1928.0, liters = 30.0, isFullTank = true)
        )

        val trips = listOf(
            trip(
                id = 1L,
                startTimeMs = ts(2026, 10, 5, 8, 0),
                distanceKm = 45.6,
                durationMin = 35L,
                fuelCost = 350.0,
                commuteBadge = "🏠 Casa ➔ 🏢 Trabalho",
                dateFormatted = "05/10/2026"
            ),
            trip(
                id = 2L,
                startTimeMs = ts(2026, 11, 5, 8, 0),
                distanceKm = 18.0,
                durationMin = 27L,
                fuelCost = 60.0,
                commuteBadge = "🏠 Casa ➔ 🏢 Trabalho",
                dateFormatted = "05/11/2026"
            ),
            trip(
                id = 3L,
                startTimeMs = ts(2026, 11, 14, 7, 0),
                distanceKm = 312.4,
                durationMin = 240L,
                fuelCost = 300.0,
                commuteBadge = "Destino: ⭐ Litoral",
                dateFormatted = "14/11/2026"
            ),
            trip(
                id = 4L,
                startTimeMs = ts(2026, 12, 2, 18, 0),
                distanceKm = 12.0,
                durationMin = 20L,
                fuelCost = 20.0,
                commuteBadge = "🏢 Trabalho ➔ 🏠 Casa",
                dateFormatted = "02/12/2026"
            )
        )

        val expenses = listOf(
            expense(1L, ts(2026, 10, 12, 10, 0), amount = 200.0),
            expense(2L, ts(2026, 11, 18, 10, 0), amount = 50.0)
        )

        val recs = DrivingInsights.records(trips, fills, expenses, now, zone).associateBy { it.kind }
        assertEquals(4, recs.size)

        val bestTank = recs.getValue(RecordKind.BEST_TANK_KML)
        assertEquals(14.2, bestTank.value, 1e-3)
        assertEquals("14,2 km/L", bestTank.display)
        assertEquals(fills[2].dateTime, bestTank.atMs)

        val fastestHw = recs.getValue(RecordKind.FASTEST_HOME_WORK)
        assertEquals(27.0, fastestHw.value, 1e-6)
        assertEquals("27 min", fastestHw.display)
        assertEquals(2L, fastestHw.tripId)
        assertEquals("05/11/2026", fastestHw.detail)

        val longest = recs.getValue(RecordKind.LONGEST_TRIP)
        assertEquals(312.4, longest.value, 1e-6)
        assertEquals("312 km", longest.display)
        assertEquals(3L, longest.tripId)
        assertEquals("⭐ Litoral", longest.detail)

        val cheapest = recs.getValue(RecordKind.CHEAPEST_MONTH)
        assertEquals(410.0, cheapest.value, 1e-6)
        assertEquals("R$ 410,00", cheapest.display)
        assertEquals("novembro de 2026", cheapest.detail)
    }

    @Test
    fun brokenRecords_detectsImprovedAndIgnoresSameOrNewKinds() {
        val previous = listOf(
            PersonalRecord(RecordKind.BEST_TANK_KML, 13.0, "13,0 km/L", "Posto", 1000L),
            PersonalRecord(RecordKind.FASTEST_HOME_WORK, 30.0, "30 min", "01/10/2026", 2000L, 1L),
            PersonalRecord(RecordKind.LONGEST_TRIP, 200.0, "200 km", "02/10/2026", 3000L, 2L)
        )
        val current = listOf(
            PersonalRecord(RecordKind.BEST_TANK_KML, 14.2, "14,2 km/L", "Posto", 4000L),
            PersonalRecord(RecordKind.FASTEST_HOME_WORK, 27.0, "27 min", "05/11/2026", 5000L, 3L),
            PersonalRecord(RecordKind.LONGEST_TRIP, 200.0, "200 km", "02/10/2026", 3000L, 2L),
            PersonalRecord(RecordKind.CHEAPEST_MONTH, 410.0, "R$ 410,00", "novembro de 2026", 6000L)
        )

        val broken = DrivingInsights.brokenRecords(previous, current)
        assertEquals(
            listOf(RecordKind.BEST_TANK_KML, RecordKind.FASTEST_HOME_WORK),
            broken.map { it.kind }
        )
    }

    @Test
    fun departureWindow_finds7h40To7h55AndIgnoresWeekends() {
        val now = ts(2026, 11, 20, 18, 0)
        val trips = listOf(
            trip(1L, ts(2026, 11, 2, 7, 40), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(2L, ts(2026, 11, 3, 7, 42), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(3L, ts(2026, 11, 4, 7, 45), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(4L, ts(2026, 11, 5, 7, 48), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(5L, ts(2026, 11, 6, 7, 50), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(99L, ts(2026, 11, 6, 19, 30), commuteBadge = "Partida: 🏠 Casa"),
            trip(100L, ts(2026, 11, 7, 10, 15), commuteBadge = "🏠 Casa ➔ ⭐ Academia"),
            trip(101L, ts(2026, 11, 8, 11, 0), commuteBadge = "Partida: 🏠 Casa"),
            trip(6L, ts(2026, 11, 9, 7, 52), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(7L, ts(2026, 11, 10, 7, 55), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(8L, ts(2026, 11, 11, 9, 30), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho")
        )

        val window = DrivingInsights.departureWindow(trips, zone, now)
        assertNotNull(window)
        window!!

        assertEquals("Casa", window.from)
        assertEquals("Trabalho", window.to)
        assertEquals(7 * 60 + 40, window.startMinute)
        assertEquals(7 * 60 + 55, window.endMinute)
        assertEquals("7h40", DrivingInsights.formatMinuteOfDay(window.startMinute))
        assertEquals("7h55", DrivingInsights.formatMinuteOfDay(window.endMinute))
        assertEquals(8, window.days)
        assertTrue(window.share >= 0.8)
        assertEquals(6 * 60 + 30, window.histogramStartMinute)
        // the 9h30 outlier falls after the histogram range (window end + 30 min)
        assertEquals(7, window.histogram.sum())

        assertNull(DrivingInsights.departureWindow(trips.take(3), zone, now))
    }

    @Test
    fun insights_reportsWeekSpendCommuteWeekdayAndRegularity() {
        val now = ts(2026, 11, 20, 20, 0)

        val fills = listOf(
            fill(1L, ts(2026, 11, 10, 12, 0), odometerKm = 1000.0, liters = 33.0, totalCost = 200.0),
            fill(2L, ts(2026, 11, 18, 12, 0), odometerKm = 1400.0, liters = 27.0, totalCost = 164.0)
        )

        val morningTrips = listOf(
            trip(1L, ts(2026, 11, 2, 7, 40), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(2L, ts(2026, 11, 3, 7, 43), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(3L, ts(2026, 11, 4, 7, 45), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(4L, ts(2026, 11, 5, 7, 48), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(5L, ts(2026, 11, 6, 7, 50), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(6L, ts(2026, 11, 9, 7, 52), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(7L, ts(2026, 11, 10, 7, 54), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(8L, ts(2026, 11, 11, 7, 55), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(9L, ts(2026, 11, 12, 9, 30), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(10L, ts(2026, 11, 13, 9, 35), commuteBadge = "🏠 Casa ➔ 🏢 Trabalho")
        )

        val eveningTrips = listOf(
            trip(11L, ts(2026, 11, 2, 18, 0), durationMin = 30L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(12L, ts(2026, 11, 3, 18, 0), durationMin = 30L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(13L, ts(2026, 11, 4, 18, 0), durationMin = 30L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(14L, ts(2026, 11, 5, 18, 0), durationMin = 30L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(15L, ts(2026, 11, 6, 18, 0), durationMin = 39L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(16L, ts(2026, 11, 9, 18, 0), durationMin = 30L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(17L, ts(2026, 11, 13, 18, 0), durationMin = 39L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa"),
            trip(18L, ts(2026, 11, 20, 18, 0), durationMin = 39L, commuteBadge = "🏢 Trabalho ➔ 🏠 Casa")
        )

        val insights = DrivingInsights.insights(
            trips = morningTrips + eveningTrips,
            fills = fills,
            expenses = emptyList(),
            nowMs = now,
            zone = zone
        ).associateBy { it.id }

        val spend = insights["week_spend"]
        assertNotNull(spend)
        assertEquals("Você gastou 18% menos que na semana passada", spend!!.text)
        assertEquals(1, spend.tone)

        val commuteWh = insights["commute_day_wh"]
        assertNotNull(commuteWh)
        assertEquals("Trabalho→Casa levou 9 min a mais às sextas", commuteWh!!.text)
        assertEquals(-1, commuteWh.tone)

        val regularity = insights["regularity"]
        assertNotNull(regularity)
        assertEquals("Você sai de casa entre 7h40 e 7h55 em 80% dos dias úteis", regularity!!.text)
    }

    @Test
    fun yearRecap_aggregatesMonthlyMileageBusiestMonthAndTopDestinations() {
        val trips = listOf(
            trip(1L, ts(2025, 12, 31, 20, 0), distanceKm = 500.0, commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(2L, ts(2026, 1, 10, 8, 0), distanceKm = 40.0, commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(3L, ts(2026, 1, 10, 18, 0), distanceKm = 60.0, commuteBadge = "Destino: ⭐ Academia"),
            trip(4L, ts(2026, 7, 15, 7, 0), distanceKm = 300.0, commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(5L, ts(2026, 7, 20, 9, 0), distanceKm = 150.0, commuteBadge = "🏠 Casa ➔ 🏢 Trabalho"),
            trip(6L, ts(2026, 11, 5, 8, 0), distanceKm = 200.0, commuteBadge = "Destino: ⭐ Academia")
        )
        val records = listOf(
            PersonalRecord(RecordKind.LONGEST_TRIP, 300.0, "300 km", "🏢 Trabalho", ts(2026, 7, 15, 7, 0), 4L),
            PersonalRecord(RecordKind.BEST_TANK_KML, 15.0, "15,0 km/L", "Posto", ts(2025, 6, 1, 10, 0), null)
        )

        val recap = DrivingInsights.yearRecap(trips, emptyList(), emptyList(), 2026, zone, records)

        assertEquals(2026, recap.year)
        assertEquals(5, recap.stats.trips)
        assertEquals(750.0, recap.stats.km, 1e-6)
        assertEquals(4, recap.daysDriven)
        assertEquals(12, recap.kmByMonth.size)
        assertEquals(100.0, recap.kmByMonth[0], 1e-6)
        assertEquals(450.0, recap.kmByMonth[6], 1e-6)
        assertEquals(200.0, recap.kmByMonth[10], 1e-6)
        assertEquals(6, recap.busiestMonth)
        assertEquals(listOf("🏢 Trabalho" to 3, "⭐ Academia" to 2), recap.topDestinations)
        assertEquals(1, recap.records.size)
        assertEquals(RecordKind.LONGEST_TRIP, recap.records.first().kind)
        assertTrue(recap.cities.isEmpty())
    }
}
