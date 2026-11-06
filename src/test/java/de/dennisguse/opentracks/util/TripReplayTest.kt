package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import org.junit.Assert.*
import org.junit.Test

class TripReplayTest {

    private val t0 = 1_800_000_000_000L

    /** Due east, 0.001° of longitude every 10 s, speeding up from 0 to 40 km/h. */
    private val east = (0..4).map { i -> TripPoint(-23.44, -46.38 + i * 0.001, i * 10.0, 700.0 + i, t0 + i * 10_000L) }

    @Test
    fun interpolatesBetweenFixesByTime() {
        val f = TripReplay(east).frameAt(15_000)!!
        assertEquals(-46.3785, f.lon, 1e-9)
        assertEquals(15.0, f.speedKmh, 1e-9)
        assertEquals(701.5, f.altitudeM, 1e-9)
        assertEquals(t0 + 15_000, f.timeMs)
        assertEquals(1, f.index)
        assertEquals(90f, f.bearing, 0.5f)
    }

    @Test
    fun clampsToTheTripAndMeasuresDistance() {
        val r = TripReplay(east)
        assertEquals(40_000L, r.durationMs)
        assertEquals(east.last().lon, r.frameAt(99_999)!!.lon, 1e-12)
        assertEquals(east.first().lon, r.frameAt(-5)!!.lon, 1e-12)
        // 0.004° of longitude at -23.44° ≈ 408 m
        assertEquals(0.408, r.totalKm, 0.01)
    }

    @Test
    fun missingTimestampsFallBackToOneSecondPerFix() {
        val r = TripReplay(east.map { it.copy(timeMs = 0) })
        assertEquals(4_000L, r.durationMs)
        assertEquals(2, r.frameAt(2_000)!!.index)
    }

    @Test
    fun stoppedCarKeepsItsLastHeading() {
        val north = listOf(
            TripPoint(-23.44, -46.38, 20.0, 0.0, t0),
            TripPoint(-23.439, -46.38, 0.0, 0.0, t0 + 10_000),
            TripPoint(-23.439, -46.38, 0.0, 0.0, t0 + 20_000)
        )
        assertEquals(0f, TripReplay(north).frameAt(15_000)!!.bearing, 0.5f)
    }

    @Test
    fun angleInterpolationTakesTheShortWay() {
        assertEquals(0f, TripReplay.lerpAngle(350f, 10f, 0.5f), 0.01f)
        assertEquals(355f, TripReplay.lerpAngle(10f, 340f, 0.5f), 0.01f)
    }
}
