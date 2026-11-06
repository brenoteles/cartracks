package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

/** One trip as the analysis sees it. */
data class TripTrace(
    val id: Long,
    val startMs: Long,
    val durationMin: Long,
    val distanceKm: Double,
    val points: List<TripPoint>
)

/** A run of consecutive points travelled by [trips] distinct trips. */
data class HeatSegment(val points: List<TripPoint>, val trips: Int)

/** Trips sharing the same origin and destination area (direction matters). */
data class RouteGroup(
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val tripIds: List<Long>,
    val avgDurationMin: Double,
    val minDurationMin: Long,
    val maxDurationMin: Long,
    val avgDistanceKm: Double,
    val avgSpeedKmh: Double,
    /** Departure hour with the shortest average duration, when trips left at 2+ different hours. */
    val bestHour: Int?,
    val bestHourAvgMin: Double?,
    val worstHour: Int?,
    val worstHourAvgMin: Double?,
    val medianDurationMin: Double,
    /** 90% of the trips took at most this long: plan the departure with it, not with the mean. */
    val p90DurationMin: Double
) {
    val count get() = tripIds.size
}

object RouteAnalysis {

    const val CELL_METERS = 40.0
    const val ENDPOINT_RADIUS_M = 400.0

    /** Runs shorter than this (in densified steps of CELL_METERS / 2) take the level of the run before them. */
    const val MIN_RUN_STEPS = 6

    /**
     * Splits every trip into runs whose frequency is the number of distinct trips that crossed
     * the same ~40 m grid cells. Repeated commutes then render as one strong line instead of
     * several overlapping ones; single detours stay faint.
     */
    fun heatSegments(trips: List<List<TripPoint>>): List<HeatSegment> {
        if (trips.isEmpty()) return emptyList()
        val lat0 = trips.flatten().firstOrNull()?.lat ?: return emptyList()
        // fixes are up to ~60 m apart at highway speed; densify so every crossed cell is counted
        val dense = trips.map { densify(it, CELL_METERS / 2) }
        // a trip also counts for the 8 surrounding cells: the same road drifts a few metres between
        // trips (GPS, other lane) and would otherwise flicker between shared and unshared cells
        val counts = HashMap<Long, Int>()
        dense.forEach { pts ->
            val cells = HashSet<Long>()
            pts.forEach { p ->
                val (y, x) = cellYX(p, lat0)
                for (dy in -1..1) for (dx in -1..1) cells += key(y + dy, x + dx)
            }
            cells.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }

        val out = mutableListOf<HeatSegment>()
        dense.forEach { pts ->
            if (pts.size < 2) return@forEach
            // a segment takes the weaker of its two ends, so a shared road only lights up where both ends are shared
            val levels = IntArray(pts.size - 1) { i -> min(counts[cell(pts[i], lat0)] ?: 1, counts[cell(pts[i + 1], lat0)] ?: 1) }
            smoothRuns(levels)
            var start = 0
            for (i in 1..levels.size) {
                if (i == levels.size || levels[i] != levels[start]) {
                    out += HeatSegment(pts.subList(start, i + 1).toList(), levels[start])
                    start = i
                }
            }
        }
        return out
    }

    /** Short runs (crossings, GPS jitter) take the level of the run before them, or after for the first run. */
    private fun smoothRuns(levels: IntArray) {
        var changed = true
        while (changed) {
            changed = false
            var start = 0
            var prev = -1
            while (start < levels.size) {
                var end = start
                while (end < levels.size && levels[end] == levels[start]) end++
                val neighbour = if (prev >= 0) prev else levels.getOrNull(end)
                if (end - start < MIN_RUN_STEPS && neighbour != null && neighbour != levels[start] && end - start < levels.size) {
                    for (k in start until end) levels[k] = neighbour
                    changed = true
                    break
                }
                prev = levels[start]
                start = end
            }
        }
    }

    /** Greedy clustering of starts and ends; trips with the same (start, end) cluster pair form a route. */
    fun groupRoutes(trips: List<TripTrace>, zone: ZoneId = ZoneId.systemDefault()): List<RouteGroup> {
        // chronological order keeps the result stable; centers move to the running mean of their members
        val valid = trips.filter { it.points.size >= 2 }.sortedBy { it.startMs }
        val starts = mutableListOf<Cluster>()
        val ends = mutableListOf<Cluster>()
        fun clusterOf(list: MutableList<Cluster>, p: TripPoint): Int {
            val idx = list.withIndex()
                .filter { distanceM(it.value.lat, it.value.lon, p.lat, p.lon) < ENDPOINT_RADIUS_M }
                .minByOrNull { distanceM(it.value.lat, it.value.lon, p.lat, p.lon) }?.index
            if (idx != null) {
                list[idx].add(p.lat, p.lon)
                return idx
            }
            list += Cluster(p.lat, p.lon, 1)
            return list.lastIndex
        }
        val keyed = valid.groupBy { clusterOf(starts, it.points.first()) to clusterOf(ends, it.points.last()) }
        return keyed.map { (key, group) ->
            val byHour = group.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).hour }
                .mapValues { (_, l) -> l.map { it.durationMin }.average() }
            val best = byHour.minByOrNull { it.value }.takeIf { byHour.size >= 2 }
            val worst = byHour.maxByOrNull { it.value }.takeIf { byHour.size >= 2 }
            val totalH = group.sumOf { it.durationMin } / 60.0
            val sorted = group.map { it.durationMin.toDouble() }.sorted()
            RouteGroup(
                startLat = starts[key.first].lat, startLon = starts[key.first].lon,
                endLat = ends[key.second].lat, endLon = ends[key.second].lon,
                tripIds = group.map { it.id },
                avgDurationMin = group.map { it.durationMin }.average(),
                minDurationMin = group.minOf { it.durationMin },
                maxDurationMin = group.maxOf { it.durationMin },
                avgDistanceKm = group.map { it.distanceKm }.average(),
                avgSpeedKmh = if (totalH > 0) group.sumOf { it.distanceKm } / totalH else 0.0,
                bestHour = best?.key, bestHourAvgMin = best?.value,
                worstHour = worst?.key, worstHourAvgMin = worst?.value,
                medianDurationMin = percentile(sorted, 0.5),
                p90DurationMin = percentile(sorted, 0.9)
            )
        }.sortedWith(compareByDescending<RouteGroup> { it.count }.thenByDescending { it.avgDistanceKm })
    }

    private class Cluster(var lat: Double, var lon: Double, var n: Int) {
        fun add(la: Double, lo: Double) {
            n++
            lat += (la - lat) / n
            lon += (lo - lon) / n
        }
    }

    /** Linear interpolation on sorted values (p in 0..1). */
    fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = p * (sorted.size - 1)
        val lo = floor(pos).toInt()
        val hi = ceil(pos).toInt()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
    }

    /** Inserts points so consecutive points are at most [stepM] apart. */
    fun densify(pts: List<TripPoint>, stepM: Double): List<TripPoint> {
        if (pts.size < 2) return pts
        val out = ArrayList<TripPoint>(pts.size * 2)
        out += pts[0]
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val n = (distanceM(a.lat, a.lon, b.lat, b.lon) / stepM).toInt()
            for (k in 1..n) {
                val t = k.toDouble() / (n + 1)
                out += a.copy(lat = a.lat + (b.lat - a.lat) * t, lon = a.lon + (b.lon - a.lon) * t)
            }
            out += b
        }
        return out
    }

    private fun cellYX(p: TripPoint, lat0: Double): Pair<Long, Long> =
        floor(p.lat * 111_320.0 / CELL_METERS).toLong() to floor(p.lon * 111_320.0 * cos(Math.toRadians(lat0)) / CELL_METERS).toLong()

    private fun key(y: Long, x: Long): Long = (y shl 32) xor (x and 0xffffffffL)

    private fun cell(p: TripPoint, lat0: Double): Long = cellYX(p, lat0).let { (y, x) -> key(y, x) }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
