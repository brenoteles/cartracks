package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos

class RouteAnalysisTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val lat0 = -23.44
    private val lon0 = -46.38
    private val mLon = 111_320.0 * cos(Math.toRadians(lat0))

    /** Straight east line of [meters], one point every 20 m, starting [northM] north of the origin. */
    private fun line(meters: Int, northM: Double = 0.0, reverse: Boolean = false): List<TripPoint> {
        val pts = (0..meters step 20).map { e -> TripPoint(lat0 + northM / 111_320.0, lon0 + e / mLon, 40.0, 0.0, 0) }
        return if (reverse) pts.reversed() else pts
    }

    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, 3, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun repeatedRouteIsStrongerThanSingleDetour() {
        val heat = RouteAnalysis.heatSegments(listOf(line(2000), line(2000), line(2000, northM = 1500.0)))
        assertEquals(2, heat.filter { it.points.first().lat == lat0 }.maxOf { it.trips })
        assertEquals(1, heat.filter { it.points.first().lat != lat0 }.maxOf { it.trips })
    }

    @Test
    fun partiallySharedRouteSplitsIntoLevels() {
        // second trip shares the first 1 km, then branches 600 m north
        val branch = line(1000) + (1..30).map { TripPoint(lat0 + it * 20 / 111_320.0, lon0 + 1000 / mLon, 40.0, 0.0, 0) }
        val heat = RouteAnalysis.heatSegments(listOf(line(2000), branch))
        assertTrue(heat.any { it.trips == 2 })
        assertTrue(heat.any { it.trips == 1 })
    }

    @Test
    fun sparseHighwayFixesStillOverlap() {
        // same road sampled 55 m apart with different phases: densify makes them share cells
        val a = (0..3000 step 55).map { TripPoint(lat0, lon0 + it / mLon, 90.0, 0.0, 0) }
        val b = (27..3000 step 55).map { TripPoint(lat0, lon0 + it / mLon, 90.0, 0.0, 0) }
        val heat = RouteAnalysis.heatSegments(listOf(a, b))
        val shared = heat.filter { it.trips == 2 }.sumOf { it.points.size }
        val single = heat.filter { it.trips == 1 }.sumOf { it.points.size }
        assertTrue("shared=$shared single=$single", shared > single * 5)
    }

    @Test
    fun gpsDriftBetweenTripsStaysOneSharedLevel() {
        // same road, second trip drifting 0..30 m to the side: must not stripe between 1 and 2
        val a = line(3000)
        val b = (0..3000 step 20).map { e -> TripPoint(lat0 + ((e / 20) % 7) * 5 / 111_320.0, lon0 + e / mLon, 40.0, 0.0, 0) }
        val heat = RouteAnalysis.heatSegments(listOf(a, b))
        assertEquals(setOf(2), heat.map { it.trips }.toSet())
        assertEquals(2, heat.size) // one run per trip
    }

    @Test
    fun clusteringDoesNotDependOnOrder() {
        fun at(e: Int) = TripPoint(lat0, lon0 + e / mLon, 0.0, 0.0, 0)
        val dest = (0..10).map { TripPoint(lat0 + 0.05, lon0 + it * 0.0001, 0.0, 0.0, 0) }
        val trips = listOf(0, 350, 700).mapIndexed { i, e -> TripTrace(i.toLong(), at(7, i), 30, 6.0, listOf(at(e)) + dest) }
        assertEquals(RouteAnalysis.groupRoutes(trips, zone).map { it.count }, RouteAnalysis.groupRoutes(trips.reversed(), zone).map { it.count })
    }

    @Test
    fun percentiles() {
        assertEquals(34.0, RouteAnalysis.percentile(listOf(30.0, 34.0, 50.0), 0.5), 0.001)
        assertEquals(46.8, RouteAnalysis.percentile(listOf(30.0, 34.0, 50.0), 0.9), 0.001)
    }

    @Test
    fun groupsByOriginAndDestinationWithDirection() {
        val trips = listOf(
            TripTrace(1, at(7), 30, 2.0, line(2000)),
            TripTrace(2, at(7, 20), 34, 2.0, line(2000)),
            TripTrace(3, at(8, 10), 50, 2.0, line(2000)),
            TripTrace(4, at(18), 40, 2.0, line(2000, reverse = true))
        )
        val groups = RouteAnalysis.groupRoutes(trips, zone)
        assertEquals(2, groups.size)
        val commute = groups.first()
        assertEquals(listOf(1L, 2L, 3L), commute.tripIds)
        assertEquals(38.0, commute.avgDurationMin, 0.001)
        assertEquals(7, commute.bestHour)
        assertEquals(32.0, commute.bestHourAvgMin!!, 0.001)
        assertEquals(8, commute.worstHour)
        assertEquals(listOf(4L), groups[1].tripIds)
        assertNull(groups[1].bestHour)
    }
}
