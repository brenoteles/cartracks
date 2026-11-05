package de.dennisguse.opentracks.services

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ParkedDetectorTest {

    private val lat0 = -23.44201
    private val lon0 = -46.38211
    private val mPerDegLat = 111_320.0
    private val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat0))

    /** Feeds one fix every 2 s; [eastMeters] gives the car position at second t. Returns the stop second or null. */
    private fun drive(seconds: Int, detector: ParkedDetector = ParkedDetector(), startS: Int = 0, eastMeters: (Int) -> Double): Int? {
        for (t in startS until startS + seconds step 2) {
            val jitter = 4 * sin(t * 0.7)
            val parked = detector.onFix(t * 1000L, lat0 + jitter / mPerDegLat, lon0 + (eastMeters(t) + jitter) / mPerDegLon)
            if (parked) return t
        }
        return null
    }

    @Test
    fun heavyTrafficKeepsRecording() {
        // ~300 m in 11 min, i.e. 1.6 km/h average: worst stop-and-go
        assertTrue(drive(11 * 60) { t -> 300.0 * t / 660 } == null)
    }

    @Test
    fun longRedLightsAndCreepingKeepRecording() {
        // 4 min stopped, creep 250 m in 2 min, 6 min stopped: never 10 min inside 200 m
        val stop = drive(12 * 60) { t ->
            when {
                t < 240 -> 0.0
                t < 360 -> 250.0 * (t - 240) / 120
                else -> 250.0
            }
        }
        assertTrue(stop == null)
    }

    @Test
    fun parkedCarEndsAfterTenMinutes() {
        val stop = drive(15 * 60) { 0.0 }
        assertTrue("should stop", stop != null && stop in 600..620)
    }

    @Test
    fun drivingThenParkingEndsTenMinutesAfterParking() {
        val stop = drive(30 * 60) { t -> if (t < 600) 15.0 * t else 9000.0 }
        assertTrue("stopped at $stop", stop != null && stop in 1170..1230)
    }

    @Test
    fun gapInsideWindowIsNotParked() {
        // Gemini's case: 1 fix, 9 min without GPS, then 20 fixes at the same spot
        val d = ParkedDetector()
        assertFalse(d.onFix(0, lat0, lon0))
        var parked = false
        for (t in 570..608 step 2) parked = d.onFix(t * 1000L, lat0, lon0)
        assertFalse(parked)
    }

    @Test
    fun singleFixAfterTunnelBlackoutIsNotParked() {
        val d = ParkedDetector()
        drive(5 * 60, d) { t -> 15.0 * t }
        // 12 min without signal, then the car comes out and gets one fix
        assertFalse(d.onFix(17 * 60_000L, lat0, lon0 + 4500 / mPerDegLon))
    }
}
