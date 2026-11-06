package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.ui.model.TripPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.cos

class TrafficAnalysisTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val lat0 = -23.44
    private val lon0 = -46.38
    private val mLon = 111_320.0 * cos(Math.toRadians(lat0))

    /** Converte hora e minuto para timestamp em milissegundos na zona local. */
    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 11, 3, hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Rota retilínea para leste com pontos espaçados a cada 20 m. */
    private fun line(
        meters: Int,
        speedKmh: Double = 60.0,
        timeMs: Long = at(8, 0),
        northM: Double = 0.0,
        reverse: Boolean = false
    ): List<TripPoint> {
        val pts = (0..meters step 20).map { e ->
            TripPoint(lat0 + northM / 111_320.0, lon0 + e / mLon, speedKmh, 0.0, timeMs)
        }
        return if (reverse) pts.reversed() else pts
    }

    @Test
    fun fastRoadBecomesLivre() {
        val trip1 = line(2000, speedKmh = 80.0, timeMs = at(8, 0))
        val trip2 = line(2000, speedKmh = 70.0, timeMs = at(8, 15))

        val segments = TrafficAnalysis.trafficSegments(listOf(trip1, trip2), 6..10, zone)

        assertTrue("Deveria conter segmentos de tráfego", segments.isNotEmpty())
        assertTrue(
            "Todos os trechos rápidos (>= 40 km/h) devem ser classificados como LIVRE",
            segments.all { it.trafficClass == TrafficClass.LIVRE }
        )
    }

    @Test
    fun stopAndGoBecomesLentoOrParado() {
        // Trecho com velocidade < 8 km/h vira PARADO
        val stopped1 = line(2000, speedKmh = 5.0, timeMs = at(8, 0))
        val stopped2 = line(2000, speedKmh = 6.0, timeMs = at(8, 20))
        val stoppedSegments = TrafficAnalysis.trafficSegments(listOf(stopped1, stopped2), 6..10, zone)

        assertTrue(stoppedSegments.isNotEmpty())
        assertTrue(
            "Trecho < 8 km/h deve ser classificado como PARADO",
            stoppedSegments.all { it.trafficClass == TrafficClass.PARADO }
        )

        // Trecho entre 8 e 20 km/h vira LENTO
        val slow1 = line(2000, speedKmh = 14.0, timeMs = at(8, 0))
        val slow2 = line(2000, speedKmh = 16.0, timeMs = at(8, 20))
        val slowSegments = TrafficAnalysis.trafficSegments(listOf(slow1, slow2), 6..10, zone)

        assertTrue(slowSegments.isNotEmpty())
        assertTrue(
            "Trecho de 8–20 km/h deve ser classificado como LENTO",
            slowSegments.all { it.trafficClass == TrafficClass.LENTO }
        )
    }

    @Test
    fun hourFilterExcludesTripsOutsideRange() {
        // trip1 é pela manhã (8h), trip2 é à noite (22h)
        val trip1 = line(2000, speedKmh = 60.0, timeMs = at(8, 0))
        val trip2 = line(2000, speedKmh = 60.0, timeMs = at(22, 0))

        // Filtrando para manhã (6..10h): apenas 1 viagem cai na faixa -> insuficiente (< 2 viagens)
        val morningSegments = TrafficAnalysis.trafficSegments(listOf(trip1, trip2), 6..10, zone)
        assertTrue(
            "Com apenas uma viagem no horário, o resultado deve ser vazio por dados insuficientes",
            morningSegments.isEmpty()
        )

        // Ao adicionar uma segunda viagem matutina, os trechos da manhã passam a aparecer
        val trip3 = line(2000, speedKmh = 60.0, timeMs = at(8, 30))
        val withTwoMorning = TrafficAnalysis.trafficSegments(listOf(trip1, trip2, trip3), 6..10, zone)
        assertTrue("Com 2 viagens no horário da manhã, deve gerar segmentos", withTwoMorning.isNotEmpty())
    }

    @Test
    fun cellWithOnlyOneTripDoesNotAppear() {
        // trip1 tem 2000 m; trip2 tem apenas 1000 m. Ambas às 8h.
        val trip1 = line(2000, speedKmh = 60.0, timeMs = at(8, 0))
        val trip2 = line(1000, speedKmh = 60.0, timeMs = at(8, 15))

        val segments = TrafficAnalysis.trafficSegments(listOf(trip1, trip2), 6..10, zone)
        assertTrue(segments.isNotEmpty())

        // ~1000 m shared, plus up to two cells: the trip's own cell and the neighbour ring that
        // absorbs GPS drift between trips
        val maxLon = lon0 + (1000.0 + 2 * TrafficAnalysis.CELL_METERS) / mLon
        // and the second kilometre, driven by trip1 alone, must stay off the map
        assertTrue(segments.flatMap { it.points }.none { it.lon > lon0 + 1200.0 / mLon })
        for (seg in segments) {
            for (p in seg.points) {
                assertTrue(
                    "Ponto com longitude ${p.lon} excede o limite compartilhado ($maxLon). Células com 1 viagem só não devem aparecer.",
                    p.lon <= maxLon
                )
            }
        }
    }

    @Test
    fun overnightHourRangeWrapsAroundMidnight() {
        // Faixa noturna: 20h às 6h (atravessando meia-noite)
        val trip1 = line(2000, speedKmh = 50.0, timeMs = at(23, 0))
        val trip2 = line(2000, speedKmh = 55.0, timeMs = at(1, 0))

        val nightSegments = TrafficAnalysis.trafficSegments(
            listOf(trip1, trip2),
            TrafficTimeBand.NOITE.hours,
            zone
        )
        assertTrue("Viagens às 23h e às 1h devem ser consideradas na faixa NOITE", nightSegments.isNotEmpty())
        assertTrue(nightSegments.all { it.trafficClass == TrafficClass.LIVRE })
    }

    @Test
    fun shortRunsAreSmoothedToNeighbourClass() {
        // Linha rápida (60 km/h), com um pequeno jitter de 2 pontos (40 m) a 5 km/h
        val pts1 = (0..2000 step 20).map { e ->
            val spd = if (e in 600..640) 5.0 else 60.0
            TripPoint(lat0, lon0 + e / mLon, spd, 0.0, at(8, 0))
        }
        val pts2 = (0..2000 step 20).map { e ->
            val spd = if (e in 600..640) 5.0 else 60.0
            TripPoint(lat0, lon0 + e / mLon, spd, 0.0, at(8, 10))
        }

        val segments = TrafficAnalysis.trafficSegments(listOf(pts1, pts2), 6..10, zone)
        // O trecho de < 6 passos densificados deve ser suavizado para LIVRE
        assertEquals(listOf(TrafficClass.LIVRE), segments.map { it.trafficClass }.distinct())
    }

    @Test
    fun colorsMatchCyberpunkPalette() {
        assertEquals(0xFF00B9B0.toInt(), TrafficClass.LIVRE.colorArgb)
        assertEquals(0xFFE8FF3A.toInt(), TrafficClass.MODERADO.colorArgb)
        assertEquals(0xFFE3881E.toInt(), TrafficClass.LENTO.colorArgb)
        assertEquals(0xFFE14822.toInt(), TrafficClass.PARADO.colorArgb)
    }
}
