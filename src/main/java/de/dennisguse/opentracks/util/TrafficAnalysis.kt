package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import java.time.Instant
import java.time.ZoneId
import kotlin.math.*

/**
 * Classes de trânsito estimadas com base na velocidade mediana histórica.
 *
 * Cores ARGB sugeridas alinhadas ao design system Cyberpunk:
 * - LIVRE: 0xFF00B9B0 (CyberCyan)
 * - MODERADO: 0xFFE8FF3A (CyberAcid)
 * - LENTO: 0xFFE3881E (CyberOrange)
 * - PARADO: 0xFFE14822 (CyberRed)
 */
enum class TrafficClass(val colorArgb: Int, val label: String) {
    LIVRE(0xFF00B9B0.toInt(), "Livre"),
    MODERADO(0xFFE8FF3A.toInt(), "Moderado"),
    LENTO(0xFFE3881E.toInt(), "Lento"),
    PARADO(0xFFE14822.toInt(), "Parado");

    companion object {
        fun fromSpeed(speedKmh: Double): TrafficClass = when {
            speedKmh >= 40.0 -> LIVRE
            speedKmh >= 20.0 -> MODERADO
            speedKmh >= 8.0 -> LENTO
            else -> PARADO
        }
    }
}

/**
 * Faixas horárias padrão para visualização de tráfego histórico.
 * A faixa NOITE trata a travessia da meia-noite (20h até 6h).
 */
enum class TrafficTimeBand(val label: String, val hours: Set<Int>) {
    MANHA("Manhã 6–10h", (6 until 10).toSet()),
    MEIO_DIA("Meio-dia 10–16h", (10 until 16).toSet()),
    TARDE("Tarde 16–20h", (16 until 20).toSet()),
    NOITE("Noite 20–6h", (20..23).toSet() + (0 until 6).toSet()),
    DIA_TODO("Dia todo", (0..23).toSet());

    fun containsHour(hour: Int): Boolean = hour in hours
}

/** Segmento contínuo de trajeto associado a uma classe de tráfego. */
data class TrafficSegment(
    val points: List<TripPoint>,
    val trafficClass: TrafficClass
)

/**
 * Análise de tráfego histórico a partir das viagens gravadas.
 *
 * Grade e agregação:
 * - Grade espacial de 40 m (RouteAnalysis.CELL_METERS) com densificação a cada 20 m (CELL_METERS / 2).
 * - Células agregam pontos com velocidade válida (speedKmh >= 0) cujo horário local cai na faixa selecionada.
 * - Células com menos de 2 viagens distintas são descartadas (dados insuficientes).
 * - A velocidade da célula é calculada pela mediana dos pontos.
 *
 * Suavização e continuidade:
 * - Trechos curtos (< 6 passos densificados) são suavizados para a classe vizinha (evitando efeito "listrado").
 * - Decisão de sobreposição: Aceitamos sobreposição contínua por viagem (como em RouteAnalysis.heatSegments),
 *   garantindo traçados contínuos e sem micro-fragmentos gerados por pequenas variações de GPS entre viagens.
 *   Como o CarMapView renderiza polylines sólidas de largura fixa com contorno escuro, sobreposições da mesma
 *   classe visualmente coincidem sem artefatos. Opcionalmente, suporta [deduplicate] para omitir células já cobertas.
 */
object TrafficAnalysis {

    const val CELL_METERS = RouteAnalysis.CELL_METERS
    const val MIN_RUN_STEPS = 6

    fun trafficSegments(
        trips: List<List<TripPoint>>,
        hours: Set<Int>,
        zone: ZoneId = ZoneId.systemDefault(),
        deduplicate: Boolean = false
    ): List<TrafficSegment> {
        if (trips.isEmpty()) return emptyList()
        val lat0 = trips.firstOrNull { it.isNotEmpty() }?.firstOrNull()?.lat ?: return emptyList()

        val stepM = CELL_METERS / 2.0
        val denseTrips = trips.map { RouteAnalysis.densify(it, stepM) }

        val cellSpeeds = HashMap<Long, MutableList<Double>>()
        val cellTrips = HashMap<Long, MutableSet<Int>>()

        denseTrips.forEachIndexed { tripIdx, pts ->
            pts.forEach { p ->
                if (p.speedKmh < 0.0) return@forEach
                val hour = Instant.ofEpochMilli(p.timeMs).atZone(zone).hour
                if (hour !in hours) return@forEach

                // a fix also counts for the 8 surrounding cells: the same road drifts a few metres
                // between trips (GPS, other lane) and would otherwise look like single-trip cells
                val (y, x) = cellYX(p, lat0)
                for (dy in -1..1) for (dx in -1..1) {
                    val c = key(y + dy, x + dx)
                    cellSpeeds.getOrPut(c) { mutableListOf() }.add(p.speedKmh)
                    cellTrips.getOrPut(c) { mutableSetOf() }.add(tripIdx)
                }
            }
        }

        // Células com menos de 2 viagens distintas têm dado insuficiente
        val validCells = HashMap<Long, Double>()
        val cellClass = HashMap<Long, TrafficClass>()
        cellTrips.forEach { (c, set) ->
            if (set.size >= 2) {
                val speeds = cellSpeeds[c]
                if (!speeds.isNullOrEmpty()) {
                    val median = RouteAnalysis.percentile(speeds.sorted(), 0.5)
                    validCells[c] = median
                    cellClass[c] = TrafficClass.fromSpeed(median)
                }
            }
        }

        if (validCells.isEmpty()) return emptyList()

        val out = mutableListOf<TrafficSegment>()
        val drawnCells = if (deduplicate) HashSet<Long>() else null

        denseTrips.forEach { pts ->
            if (pts.size < 2) return@forEach

            val stepClasses = arrayOfNulls<TrafficClass>(pts.size - 1)

            for (i in 0 until pts.size - 1) {
                val p1 = pts[i]
                val p2 = pts[i + 1]

                val h1 = Instant.ofEpochMilli(p1.timeMs).atZone(zone).hour
                val h2 = Instant.ofEpochMilli(p2.timeMs).atZone(zone).hour
                if (h1 !in hours && h2 !in hours) continue

                val c1 = cell(p1, lat0)
                val c2 = cell(p2, lat0)

                val v1 = validCells[c1]
                val v2 = validCells[c2]
                if (v1 != null && v2 != null) {
                    if (deduplicate && drawnCells != null && drawnCells.contains(c1) && drawnCells.contains(c2)) {
                        continue
                    }
                    val speed = (v1 + v2) / 2.0
                    stepClasses[i] = TrafficClass.fromSpeed(speed)
                }
            }

            // Suavizar trechos curtos de cada sequência contínua válida
            var idx = 0
            while (idx < stepClasses.size) {
                if (stepClasses[idx] == null) {
                    idx++
                    continue
                }
                val runStart = idx
                while (idx < stepClasses.size && stepClasses[idx] != null) {
                    idx++
                }
                val runEnd = idx
                val levels = IntArray(runEnd - runStart) { k -> stepClasses[runStart + k]!!.ordinal }
                smoothRuns(levels)
                val values = TrafficClass.values()
                for (k in levels.indices) {
                    stepClasses[runStart + k] = values[levels[k]]
                }
            }

            // Agrupar em segmentos contínuos da mesma classe de tráfego
            var segStart = 0
            while (segStart < stepClasses.size) {
                val cClass = stepClasses[segStart]
                if (cClass == null) {
                    segStart++
                    continue
                }
                var segEnd = segStart
                while (segEnd < stepClasses.size && stepClasses[segEnd] == cClass) {
                    segEnd++
                }
                val segmentPts = pts.subList(segStart, segEnd + 1).toList()
                out += TrafficSegment(segmentPts, cClass)

                if (deduplicate && drawnCells != null) {
                    for (k in segStart..segEnd) {
                        drawnCells.add(cell(pts[k], lat0))
                    }
                }

                segStart = segEnd
            }
        }

        return out
    }

    fun trafficSegments(
        trips: List<List<TripPoint>>,
        hours: IntRange = 0..23,
        zone: ZoneId = ZoneId.systemDefault(),
        deduplicate: Boolean = false
    ): List<TrafficSegment> = trafficSegments(trips, hours.toHourSet(), zone, deduplicate)

    fun coloredSegments(
        trips: List<List<TripPoint>>,
        hours: Set<Int>,
        zone: ZoneId = ZoneId.systemDefault(),
        deduplicate: Boolean = false
    ): List<Pair<List<TripPoint>, Int>> =
        trafficSegments(trips, hours, zone, deduplicate).map { it.points to it.trafficClass.colorArgb }

    fun coloredSegments(
        trips: List<List<TripPoint>>,
        hours: IntRange = 0..23,
        zone: ZoneId = ZoneId.systemDefault(),
        deduplicate: Boolean = false
    ): List<Pair<List<TripPoint>, Int>> =
        coloredSegments(trips, hours.toHourSet(), zone, deduplicate)

    fun analyze(
        trips: List<List<TripPoint>>,
        hours: Set<Int>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TrafficSegment> = trafficSegments(trips, hours, zone)

    fun analyze(
        trips: List<List<TripPoint>>,
        hours: IntRange = 0..23,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TrafficSegment> = trafficSegments(trips, hours.toHourSet(), zone)

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

    private fun cellYX(p: TripPoint, lat0: Double): Pair<Long, Long> =
        floor(p.lat * 111_320.0 / CELL_METERS).toLong() to
            floor(p.lon * 111_320.0 * cos(Math.toRadians(lat0)) / CELL_METERS).toLong()

    private fun key(y: Long, x: Long): Long = (y shl 32) xor (x and 0xffffffffL)

    private fun cell(p: TripPoint, lat0: Double): Long = cellYX(p, lat0).let { (y, x) -> key(y, x) }

    fun IntRange.toHourSet(): Set<Int> =
        if (first <= last) toSet() else (first..23).toSet() + (0..last).toSet()
}
