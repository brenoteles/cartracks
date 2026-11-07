package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class RouteVariantsTest {

    private val lat0 = -23.44
    private val lon0 = -46.38
    private val mLon = 111_320.0 * cos(Math.toRadians(lat0))

    private fun p(eastM: Double, northM: Double) = TripPoint(lat0 + northM / 111_320.0, lon0 + eastM / mLon, 40.0, 0.0, 0)

    /** 10 km east; the "north" path bulges 3 km north in the middle, like taking another highway. */
    private fun path(north: Boolean): List<TripPoint> = (0..100).map { i ->
        val e = i * 100.0
        val bulge = if (north && e in 2000.0..8000.0) 3000.0 else 0.0
        p(e, bulge)
    }

    private fun trip(id: Long, min: Long, km: Double, liters: Double) =
        TripItem(id, "t$id", "", km, min, id * 86_400_000L, "", 40.0, 80.0, liters, liters * 6, "🏠 Casa ➔ 🏢 Trabalho")

    @Test
    fun twoPathsBecomeTwoVariantsWithTheirOwnNumbers() {
        val trips = listOf(trip(1, 30, 10.0, 1.0), trip(2, 34, 10.0, 1.0), trip(3, 32, 10.0, 1.0),
            trip(4, 25, 16.0, 1.0), trip(5, 27, 16.0, 1.0))
        val pts = mapOf(1L to path(false), 2L to path(false), 3L to path(false), 4L to path(true), 5L to path(true))
        val cmp = RouteVariants.compare("Casa", "Trabalho", trips, pts)!!
        assertEquals(2, cmp.variants.size)
        val (a, b) = cmp.variants
        assertEquals(listOf(1L, 2L, 3L), a.tripIds)
        assertEquals(32.0, a.medianMin, 1e-9)
        assertEquals(10.0, a.avgKmL, 1e-9)
        assertEquals(listOf(4L, 5L), b.tripIds)
        assertEquals(26.0, b.medianMin, 1e-9)
        // the distinctive point of the northern variant lies on its bulge
        assertTrue((b.distinctLat - lat0) * 111_320.0 > 2500)
        assertTrue((a.distinctLat - lat0) * 111_320.0 < 500)
    }

    @Test
    fun singlePathHasNothingToCompare() {
        val trips = (1L..4L).map { trip(it, 30, 10.0, 1.0) }
        assertNull(RouteVariants.compare("Casa", "Trabalho", trips, trips.associate { it.id to path(false) }))
    }

    @Test
    fun oneOffDetourIsNotAVariant() {
        val trips = (1L..4L).map { trip(it, 30, 10.0, 1.0) } + trip(5, 40, 16.0, 1.0)
        val pts = trips.associate { it.id to path(it.id == 5L) }
        assertNull(RouteVariants.compare("Casa", "Trabalho", trips, pts))
    }
}
