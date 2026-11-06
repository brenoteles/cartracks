package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Where the car was at one instant of a recorded trip. */
data class ReplayFrame(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val altitudeM: Double,
    val timeMs: Long,
    /** Degrees clockwise from north, towards the next fix. */
    val bearing: Float,
    /** Driven distance from the start of the trip. */
    val distanceKm: Double,
    /** Index of the fix at or before this instant. */
    val index: Int
)

/**
 * Time-based playback of a recorded trace: positions, speed and altitude are interpolated
 * between fixes so the car glides instead of jumping. Fixes without usable timestamps
 * (all zero, or going backwards) are spaced one second apart.
 */
class TripReplay(private val points: List<TripPoint>) {

    private val times: LongArray
    private val cumulativeKm: DoubleArray

    init {
        val n = points.size
        val usable = n >= 2 && points.zipWithNext().all { (a, b) -> b.timeMs >= a.timeMs } && points.last().timeMs > points.first().timeMs
        times = LongArray(n) { i -> if (usable) points[i].timeMs - points[0].timeMs else i * 1000L }
        cumulativeKm = DoubleArray(n)
        for (i in 1 until n) {
            cumulativeKm[i] = cumulativeKm[i - 1] + RouteAnalysis.distanceM(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon) / 1000.0
        }
    }

    val isEmpty get() = points.size < 2
    val durationMs: Long get() = times.lastOrNull() ?: 0L
    val totalKm: Double get() = cumulativeKm.lastOrNull() ?: 0.0

    /** Playback offset (ms from the first fix) of fix [index]. */
    fun offsetOf(index: Int): Long = times[index.coerceIn(0, times.lastIndex)]

    /** The car at [offsetMs] after the first fix, clamped to the trip. */
    fun frameAt(offsetMs: Long): ReplayFrame? {
        if (isEmpty) return null
        val t = offsetMs.coerceIn(0L, durationMs)
        // last fix at or before t
        var lo = 0
        var hi = times.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (times[mid] <= t) lo = mid else hi = mid - 1
        }
        val i = lo.coerceAtMost(points.lastIndex - 1)
        val a = points[i]
        val b = points[i + 1]
        val span = (times[i + 1] - times[i]).coerceAtLeast(1)
        val f = ((t - times[i]).toDouble() / span).coerceIn(0.0, 1.0)
        return ReplayFrame(
            lat = a.lat + (b.lat - a.lat) * f,
            lon = a.lon + (b.lon - a.lon) * f,
            speedKmh = a.speedKmh + (b.speedKmh - a.speedKmh) * f,
            altitudeM = a.altitudeM + (b.altitudeM - a.altitudeM) * f,
            timeMs = points[0].timeMs + t,
            bearing = headingAround(i),
            distanceKm = cumulativeKm[i] + (cumulativeKm[i + 1] - cumulativeKm[i]) * f,
            index = if (f >= 1.0) i + 1 else i
        )
    }

    /** Heading of the first segment around [i] that actually moves (stopped cars keep the last heading). */
    private fun headingAround(i: Int): Float {
        for (k in i downTo 0) {
            val a = points[k]
            val b = points[k + 1]
            if (a.lat != b.lat || a.lon != b.lon) return bearing(a.lat, a.lon, b.lat, b.lon)
        }
        return 0f
    }

    companion object {
        /** Initial great-circle bearing from (lat1, lon1) to (lat2, lon2), 0..360. */
        fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)
            val y = sin(dLon) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
            return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
        }

        /** Angle between [from] and [to] taking the short way round, scaled by [f]. */
        fun lerpAngle(from: Float, to: Float, f: Float): Float {
            val delta = ((to - from + 540f) % 360f) - 180f
            return (from + delta * f + 360f) % 360f
        }
    }
}
