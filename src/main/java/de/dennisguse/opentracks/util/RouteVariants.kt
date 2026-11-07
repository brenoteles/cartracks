package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.ui.model.TripPoint
import kotlin.math.cos
import kotlin.math.floor

/**
 * Splits the trips between the same two places into the different paths taken (Dutra ×
 * Ayrton Senna): trips sharing most of their ~200 m grid cells are the same variant.
 */
object RouteVariants {

    const val CELL_M = 200.0
    const val SAME_PATH_JACCARD = 0.55
    private const val M_PER_DEG = 111_320.0

    /**
     * Variants of the trips in [trips] (all of one direction), biggest first. Only variants
     * with at least [minTrips] trips are kept; null when fewer than two remain to compare.
     */
    fun compare(from: String, to: String, trips: List<TripItem>, points: Map<Long, List<TripPoint>>, minTrips: Int = 2): RouteComparison? {
        val withPts = trips.filter { (points[it.id]?.size ?: 0) >= 2 }
        if (withPts.size < 2 * minTrips) return null
        val lat0 = withPts.first().let { points.getValue(it.id).first().lat }
        val cells = withPts.associate { it.id to cellsOf(points.getValue(it.id), lat0) }

        val clusters = mutableListOf<MutableList<TripItem>>()
        for (t in withPts.sortedBy { it.startTimeMs }) {
            val best = clusters.maxByOrNull { c -> c.maxOf { jaccard(cells.getValue(it.id), cells.getValue(t.id)) } }
            if (best != null && best.maxOf { jaccard(cells.getValue(it.id), cells.getValue(t.id)) } >= SAME_PATH_JACCARD) best += t
            else clusters += mutableListOf(t)
        }
        val kept = clusters.filter { it.size >= minTrips }.sortedByDescending { it.size }
        if (kept.size < 2) return null

        val unionOf = kept.map { c -> c.flatMap { cells.getValue(it.id) }.toSet() }
        val variants = kept.mapIndexed { i, c ->
            val medoid = c.maxBy { a -> c.sumOf { b -> jaccard(cells.getValue(a.id), cells.getValue(b.id)) } }
            val others = unionOf.filterIndexed { j, _ -> j != i }.flatten().toSet()
            val (dLat, dLon) = distinctPoint(points.getValue(medoid.id), others, lat0)
            val liters = c.sumOf { it.fuelLiters }
            RouteVariant(
                name = "Caminho ${'A' + i}",
                tripIds = c.map { it.id },
                medianMin = RouteAnalysis.percentile(c.map { it.durationMin.toDouble() }.sorted(), 0.5),
                avgKm = c.map { it.distanceKm }.average(),
                avgKmL = if (liters > 0) c.sumOf { it.distanceKm } / liters else 0.0,
                distinctLat = dLat,
                distinctLon = dLon,
                representative = points.getValue(medoid.id)
            )
        }
        return RouteComparison(from, to, variants)
    }

    fun jaccard(a: Set<Long>, b: Set<Long>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    fun cellsOf(pts: List<TripPoint>, lat0: Double): Set<Long> =
        RouteAnalysis.densify(pts, CELL_M / 2).mapTo(HashSet()) { cell(it.lat, it.lon, lat0) }

    private fun cell(lat: Double, lon: Double, lat0: Double): Long {
        val y = floor(lat * M_PER_DEG / CELL_M).toLong()
        val x = floor(lon * M_PER_DEG * cos(Math.toRadians(lat0)) / CELL_M).toLong()
        return (y shl 32) xor (x and 0xffffffffL)
    }

    /** Middle of the longest run of points whose cells no other variant uses; the midpoint of the trip if none. */
    private fun distinctPoint(pts: List<TripPoint>, others: Set<Long>, lat0: Double): Pair<Double, Double> {
        var bestStart = -1
        var bestLen = 0
        var runStart = -1
        for (i in pts.indices) {
            val unique = cell(pts[i].lat, pts[i].lon, lat0) !in others
            if (unique && runStart < 0) runStart = i
            if ((!unique || i == pts.lastIndex) && runStart >= 0) {
                val end = if (unique) i + 1 else i
                if (end - runStart > bestLen) { bestLen = end - runStart; bestStart = runStart }
                runStart = -1
            }
        }
        val p = if (bestLen > 0) pts[bestStart + bestLen / 2] else pts[pts.size / 2]
        return p.lat to p.lon
    }
}
