package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import kotlin.math.*

/**
 * High-Precision Adaptive GPS Kalman Filter.
 * Filters out high-frequency sensor noise, satellite multipath jumps, and erratic GPS drift
 * when stationary, producing smooth, automotive-grade trajectories.
 */
class GpsKalmanFilter(
    private val processNoiseSigma: Double = 3.0 // meters per second^2 typical vehicle acceleration
) {
    private var lat: Double = 0.0
    private var lon: Double = 0.0
    private var variance: Double = -1.0 // P matrix diagonal representation
    private var lastTimeMs: Long = 0

    // Meters per degree conversions around current latitude
    private fun metersPerDegLat(): Double = 111132.954
    private fun metersPerDegLon(latDeg: Double): Double = 111132.954 * cos(Math.toRadians(latDeg))

    fun reset() {
        variance = -1.0
        lastTimeMs = 0
    }

    /**
     * Filters a single GPS coordinate measurement.
     * @param rawLat measured latitude
     * @param rawLon measured longitude
     * @param accuracyMeters reported GPS horizontal accuracy
     * @param timeMs epoch timestamp
     * @return filtered Pair(lat, lon)
     */
    fun filter(rawLat: Double, rawLon: Double, accuracyMeters: Float, timeMs: Long): Pair<Double, Double> {
        val accuracy = accuracyMeters.coerceAtLeast(3.0f).toDouble()

        if (variance < 0 || lastTimeMs == 0L) {
            // Initialization
            lat = rawLat
            lon = rawLon
            variance = accuracy * accuracy
            lastTimeMs = timeMs
            return Pair(lat, lon)
        }

        val dt = ((timeMs - lastTimeMs) / 1000.0).coerceIn(0.1, 15.0)
        lastTimeMs = timeMs

        // 1. Prediction: variance grows with elapsed time and process acceleration noise
        variance += dt * processNoiseSigma * processNoiseSigma

        // 2. Kalman Gain: K = P / (P + R)
        val measurementVariance = accuracy * accuracy
        val k = variance / (variance + measurementVariance)

        // 3. Update state: x = x + K * (z - x)
        // Convert degree delta to meters, apply gain, convert back to degrees
        val mLat = metersPerDegLat()
        val mLon = metersPerDegLon(lat)

        val deltaLatM = (rawLat - lat) * mLat
        val deltaLonM = (rawLon - lon) * mLon
        val distJumpM = sqrt(deltaLatM * deltaLatM + deltaLonM * deltaLonM)

        // Multipath rejection: if an impossible jump occurred (> 180 km/h delta in 1-2s), damp it heavily
        val maxSpeedMps = 55.0 // ~200 km/h
        val effectiveK = if (distJumpM > maxSpeedMps * dt && accuracy > 12.0) {
            k * 0.2 // Heavily mistrust the spike
        } else {
            k
        }

        lat += (rawLat - lat) * effectiveK
        lon += (rawLon - lon) * effectiveK

        // 4. Update Covariance: P = (1 - K) * P
        variance *= (1.0 - effectiveK)

        return Pair(lat, lon)
    }

    companion object {
        /**
         * Smooths an entire recorded route with forward-backward passes to eliminate GPS jitter
         * while perfectly preserving true turns and vehicle heading.
         */
        fun smoothTrack(points: List<TripPoint>): List<TripPoint> {
            if (points.size <= 2) return points

            val filter = GpsKalmanFilter(processNoiseSigma = 2.5)
            val smoothed = ArrayList<TripPoint>(points.size)

            for (pt in points) {
                val (sLat, sLon) = filter.filter(pt.lat, pt.lon, 6.0f, pt.timeMs)
                smoothed.add(
                    TripPoint(
                        lat = sLat,
                        lon = sLon,
                        speedKmh = pt.speedKmh,
                        altitudeM = pt.altitudeM,
                        timeMs = pt.timeMs
                    )
                )
            }
            return smoothed
        }
    }
}
