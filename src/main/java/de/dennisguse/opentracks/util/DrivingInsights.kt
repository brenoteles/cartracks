package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.data.Expense
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin computations for period statistics, rolling week summaries, personal records,
 * departure regularity windows, short pt-BR insights, and the annual recap.
 */
object DrivingInsights {

    private val PT_BR: Locale = Locale("pt", "BR")
    private const val DAY_MS = 86_400_000L
    private const val WEEK_MS = 7 * DAY_MS
    private const val RECORD_EPSILON = 1e-6

    private val MONTH_NAMES_PT = arrayOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    )

    /** Aggregates trips, pump fills and expenses inside the half-open window [startMs, endMs). */
    fun periodStats(
        trips: List<TripItem>,
        fills: List<FuelItem>,
        expenses: List<Expense>,
        startMs: Long,
        endMs: Long
    ): PeriodStats {
        val windowTrips = trips.filter { it.startTimeMs in startMs until endMs }
        val windowFills = fills.filter { it.dateTime in startMs until endMs }
        val windowExpenses = expenses.filter { it.dateTime in startMs until endMs }

        val km = windowTrips.sumOf { it.distanceKm }
        val drivingMin = windowTrips.sumOf { it.durationMin }
        val fuelLiters = windowTrips.sumOf { it.fuelLiters }
        val fuelSpent = windowFills.sumOf { it.totalCost }
        val otherSpent = windowExpenses.sumOf { it.amount }
        val avgKmL = if (fuelLiters > 0.0) km / fuelLiters else 0.0
        val longestTrip = windowTrips.maxByOrNull { it.distanceKm }

        return PeriodStats(
            startMs = startMs,
            endMs = endMs,
            trips = windowTrips.size,
            km = km,
            drivingMin = drivingMin,
            fuelLiters = fuelLiters,
            fuelSpent = fuelSpent,
            otherSpent = otherSpent,
            avgKmL = avgKmL,
            longestTrip = longestTrip
        )
    }

    /** Compares the rolling last 7 days [nowMs - 7d, nowMs) against the preceding 7 days [nowMs - 14d, nowMs - 7d). */
    fun weekSummary(
        trips: List<TripItem>,
        fills: List<FuelItem>,
        expenses: List<Expense>,
        nowMs: Long
    ): WeekSummary {
        val currentStart = nowMs - WEEK_MS
        val previousStart = nowMs - 2 * WEEK_MS
        return WeekSummary(
            current = periodStats(trips, fills, expenses, currentStart, nowMs),
            previous = periodStats(trips, fills, expenses, previousStart, currentStart)
        )
    }

    /** Computes all available personal records up to [nowMs], omitting kinds without valid data. */
    fun records(
        trips: List<TripItem>,
        fills: List<FuelItem>,
        expenses: List<Expense>,
        nowMs: Long,
        zone: ZoneId
    ): List<PersonalRecord> {
        val out = mutableListOf<PersonalRecord>()

        bestTankRecord(fills)?.let { out += it }
        fastestHomeWorkRecord(trips)?.let { out += it }
        longestTripRecord(trips)?.let { out += it }
        cheapestMonthRecord(trips, expenses, nowMs, zone)?.let { out += it }

        return out
    }

    /** Returns true when [candidate] is strictly better than [current] for the given [kind]. */
    fun isBetter(kind: RecordKind, candidate: Double, current: Double): Boolean = when (kind) {
        RecordKind.BEST_TANK_KML, RecordKind.LONGEST_TRIP -> candidate > current
        RecordKind.FASTEST_HOME_WORK, RecordKind.CHEAPEST_MONTH -> candidate < current
    }

    /** Returns records in [current] whose kind already existed in [previous] and improved beyond epsilon. */
    fun brokenRecords(
        previous: List<PersonalRecord>,
        current: List<PersonalRecord>
    ): List<PersonalRecord> {
        val prevByKind = previous.associateBy { it.kind }
        return current.filter { curr ->
            val prev = prevByKind[curr.kind] ?: return@filter false
            abs(curr.value - prev.value) > RECORD_EPSILON && isBetter(curr.kind, curr.value, prev.value)
        }
    }

    /** Generates short pt-BR insights ordered by relevance, gracefully returning empty when data is scarce. */
    fun insights(
        trips: List<TripItem>,
        fills: List<FuelItem>,
        expenses: List<Expense>,
        nowMs: Long,
        zone: ZoneId
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        val week = weekSummary(trips, fills, expenses, nowMs)

        val prevSpent = week.previous.totalSpent
        val currSpent = week.current.totalSpent
        if (prevSpent > 0.0) {
            val change = (currSpent - prevSpent) / prevSpent
            val pct = abs(change * 100.0).roundToInt()
            if (abs(change) >= 0.05 && pct >= 5) {
                val less = currSpent < prevSpent
                val word = if (less) "menos" else "mais"
                out += Insight(
                    id = "week_spend",
                    emoji = if (less) "💰" else "💸",
                    text = "Você gastou ${pct}% $word que na semana passada",
                    tone = if (less) 1 else -1
                )
            }
        }

        commuteWeekdayInsight(
            trips = trips,
            nowMs = nowMs,
            zone = zone,
            badge = "🏠 Casa ➔ 🏢 Trabalho",
            dirLabel = "Casa→Trabalho",
            id = "commute_day_hw"
        )?.let { out += it }

        commuteWeekdayInsight(
            trips = trips,
            nowMs = nowMs,
            zone = zone,
            badge = "🏢 Trabalho ➔ 🏠 Casa",
            dirLabel = "Trabalho→Casa",
            id = "commute_day_wh"
        )?.let { out += it }

        if (week.current.km >= 20.0 && week.previous.km >= 20.0 &&
            week.current.avgKmL > 0.0 && week.previous.avgKmL > 0.0
        ) {
            val change = (week.current.avgKmL - week.previous.avgKmL) / week.previous.avgKmL
            val pct = abs(change * 100.0).roundToInt()
            if (abs(change) >= 0.05 && pct >= 5) {
                val better = week.current.avgKmL > week.previous.avgKmL
                val verb = if (better) "subiu" else "caiu"
                val kmLFormatted = String.format(PT_BR, "%.1f", week.current.avgKmL)
                out += Insight(
                    id = "week_kml",
                    emoji = "⛽",
                    text = "Consumo médio $verb para $kmLFormatted km/L (${pct}%)",
                    tone = if (better) 1 else -1
                )
            }
        }

        if (week.previous.km > 0.0) {
            val change = (week.current.km - week.previous.km) / week.previous.km
            val pct = abs(change * 100.0).roundToInt()
            if (abs(change) >= 0.05 && pct >= 5) {
                val word = if (week.current.km >= week.previous.km) "mais" else "menos"
                out += Insight(
                    id = "week_km",
                    emoji = "🛣️",
                    text = "Você rodou ${pct}% $word que na semana passada",
                    tone = 0
                )
            }
        }

        departureWindow(trips, zone, nowMs)?.let { dw ->
            val pct = (dw.share * 100.0).roundToInt()
            val startStr = formatMinuteOfDay(dw.startMinute)
            val endStr = formatMinuteOfDay(dw.endMinute)
            out += Insight(
                id = "regularity",
                emoji = "⏰",
                text = "Você sai de casa entre $startStr e $endStr em ${pct}% dos dias úteis",
                tone = 0
            )
        }

        val recentTrips = trips.filter { it.startTimeMs in (nowMs - 30 * DAY_MS)..nowMs && it.distanceKm > 0.0 }
        val recentKm = recentTrips.sumOf { it.distanceKm }
        if (recentKm >= 20.0) {
            val nightKm = recentTrips.filter {
                val hour = Instant.ofEpochMilli(it.startTimeMs).atZone(zone).hour
                hour >= 20 || hour < 5
            }.sumOf { it.distanceKm }
            val nightShare = nightKm / recentKm
            if (nightShare >= 0.25) {
                val pct = (nightShare * 100.0).roundToInt()
                out += Insight(
                    id = "night_driving",
                    emoji = "🌙",
                    text = "${pct}% dos km nos últimos 30 dias foram após as 20h",
                    tone = 0
                )
            }
        }

        val longestWeek = week.current.longestTrip
        if (longestWeek != null && longestWeek.distanceKm >= 15.0) {
            val distStr = formatDistanceKm(longestWeek.distanceKm)
            out += Insight(
                id = "longest_week",
                emoji = "📍",
                text = "Maior viagem da semana teve $distStr",
                tone = 0
            )
        }

        return out
    }

    /** Finds the narrowest weekday morning/daily departure window from Casa over the last 90 days. */
    fun departureWindow(
        trips: List<TripItem>,
        zone: ZoneId,
        nowMs: Long,
        coverage: Double = 0.8,
        minDays: Int = 5
    ): DepartureWindow? {
        val windowStart = nowMs - 90 * DAY_MS
        val casaWeekdayTrips = trips.filter { trip ->
            if (trip.startTimeMs !in windowStart..nowMs) return@filter false
            val badge = trip.commuteBadge ?: return@filter false
            if (!badge.startsWith("🏠 Casa ➔") && badge != "Partida: 🏠 Casa") return@filter false
            val dow = Instant.ofEpochMilli(trip.startTimeMs).atZone(zone).dayOfWeek
            dow.value in DayOfWeek.MONDAY.value..DayOfWeek.FRIDAY.value
        }

        val firstPerDay = casaWeekdayTrips
            .groupBy { Instant.ofEpochMilli(it.startTimeMs).atZone(zone).toLocalDate() }
            .values
            .map { dayTrips -> dayTrips.minByOrNull { it.startTimeMs }!! }

        val n = firstPerDay.size
        if (n < minDays) return null

        val minutes = firstPerDay.map { trip ->
            val zdt = Instant.ofEpochMilli(trip.startTimeMs).atZone(zone)
            zdt.hour * 60 + zdt.minute
        }.sorted()

        val k = ceil(coverage * n).toInt().coerceIn(1, n)
        var bestIdx = 0
        var bestSpan = Int.MAX_VALUE
        for (i in 0..(n - k)) {
            val span = minutes[i + k - 1] - minutes[i]
            if (span < bestSpan) {
                bestSpan = span
                bestIdx = i
            }
        }

        val rawA = minutes[bestIdx]
        val rawB = minutes[bestIdx + k - 1]
        val a = (rawA / 5) * 5
        val b = ((rawB + 4) / 5) * 5

        val insideCount = minutes.count { it in a..b }
        val share = insideCount.toDouble() / n
        val medianMinute = RouteAnalysis.percentile(minutes.map { it.toDouble() }, 0.5).roundToInt()

        val toLabel = firstPerDay
            .mapNotNull { trip ->
                val badge = trip.commuteBadge ?: return@mapNotNull null
                if (!badge.contains("➔")) return@mapNotNull null
                stripLeadingEmoji(badge.substringAfter("➔")).takeIf { it.isNotEmpty() }
            }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: ""

        val histStart = ((a / 60) * 60 - 30).coerceAtLeast(0)
        val histEnd = (b + 30).coerceAtMost(24 * 60)
        val numBins = max(1, ceil((histEnd - histStart) / 10.0).toInt())
        val bins = IntArray(numBins)
        for (m in minutes) {
            if (m >= histStart) {
                val idx = (m - histStart) / 10
                if (idx in 0 until numBins) {
                    bins[idx]++
                }
            }
        }

        return DepartureWindow(
            from = "Casa",
            to = toLabel,
            startMinute = a,
            endMinute = b,
            share = share,
            days = n,
            medianMinute = medianMinute,
            histogram = bins.toList(),
            histogramStartMinute = histStart
        )
    }

    /** Formats a minute of the day (0..1439) as "7h40" or "18h05". */
    fun formatMinuteOfDay(min: Int): String {
        val normalized = ((min % 1440) + 1440) % 1440
        val h = normalized / 60
        val m = normalized % 60
        return String.format(Locale.ROOT, "%dh%02d", h, m)
    }

    /** Builds the annual recap for [year] in [zone], including monthly mileage and top destinations. */
    fun yearRecap(
        trips: List<TripItem>,
        fills: List<FuelItem>,
        expenses: List<Expense>,
        year: Int,
        zone: ZoneId,
        records: List<PersonalRecord>
    ): YearRecap {
        val startMs = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()

        val stats = periodStats(trips, fills, expenses, startMs, endMs)
        val yearTrips = trips.filter { it.startTimeMs in startMs until endMs }

        val daysDriven = yearTrips
            .map { Instant.ofEpochMilli(it.startTimeMs).atZone(zone).toLocalDate() }
            .distinct()
            .size

        val kmByMonth = MutableList(12) { 0.0 }
        for (trip in yearTrips) {
            val monthIdx = Instant.ofEpochMilli(trip.startTimeMs).atZone(zone).monthValue - 1
            kmByMonth[monthIdx] = kmByMonth[monthIdx] + trip.distanceKm
        }

        val busiestMonth = kmByMonth.indices.maxByOrNull { kmByMonth[it] } ?: 0

        val topDestinations = yearTrips
            .mapNotNull { extractDestinationLabel(it.commuteBadge) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(5)
            .map { it.key to it.value }

        val yearRecords = records.filter { it.atMs in startMs until endMs }

        return YearRecap(
            year = year,
            stats = stats,
            daysDriven = daysDriven,
            kmByMonth = kmByMonth,
            busiestMonth = busiestMonth,
            topDestinations = topDestinations,
            records = yearRecords,
            cities = emptyList()
        )
    }

    private fun bestTankRecord(fills: List<FuelItem>): PersonalRecord? {
        val sorted = fills.filter { it.odometerKm > 0.0 }.sortedBy { it.odometerKm }
        var lastFull = -1
        var bestKmL = -1.0
        var bestClosing: FuelItem? = null

        for (i in sorted.indices) {
            if (!sorted[i].isFullTank) continue
            if (lastFull >= 0) {
                val deltaKm = sorted[i].odometerKm - sorted[lastFull].odometerKm
                val refill = (lastFull + 1..i).sumOf { sorted[it].liters }
                val kmL = if (refill > 0.0) deltaKm / refill else 0.0
                if (deltaKm > 0.0 && kmL in 3.0..35.0 && kmL > bestKmL) {
                    bestKmL = kmL
                    bestClosing = sorted[i]
                }
            }
            lastFull = i
        }

        val closing = bestClosing ?: return null
        return PersonalRecord(
            kind = RecordKind.BEST_TANK_KML,
            value = bestKmL,
            display = String.format(PT_BR, "%.1f km/L", bestKmL),
            detail = closing.stationName.ifBlank { closing.fuelType },
            atMs = closing.dateTime,
            tripId = null
        )
    }

    private fun fastestHomeWorkRecord(trips: List<TripItem>): PersonalRecord? {
        val best = trips
            .filter { it.commuteBadge == "🏠 Casa ➔ 🏢 Trabalho" && it.durationMin > 0L }
            .minByOrNull { it.durationMin }
            ?: return null

        return PersonalRecord(
            kind = RecordKind.FASTEST_HOME_WORK,
            value = best.durationMin.toDouble(),
            display = "${best.durationMin} min",
            detail = best.dateFormatted,
            atMs = best.startTimeMs,
            tripId = best.id
        )
    }

    private fun longestTripRecord(trips: List<TripItem>): PersonalRecord? {
        val best = trips
            .filter { it.distanceKm > 0.0 }
            .maxByOrNull { it.distanceKm }
            ?: return null

        val detail = extractDestinationLabel(best.commuteBadge) ?: best.dateFormatted
        return PersonalRecord(
            kind = RecordKind.LONGEST_TRIP,
            value = best.distanceKm,
            display = formatDistanceKm(best.distanceKm),
            detail = detail,
            atMs = best.startTimeMs,
            tripId = best.id
        )
    }

    private fun cheapestMonthRecord(
        trips: List<TripItem>,
        expenses: List<Expense>,
        nowMs: Long,
        zone: ZoneId
    ): PersonalRecord? {
        if (trips.isEmpty()) return null
        val currentMonth = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        val tripsByMonth = trips.groupBy { YearMonth.from(Instant.ofEpochMilli(it.startTimeMs).atZone(zone)) }
        val firstTripMonth = tripsByMonth.keys.minOrNull() ?: return null
        val expensesByMonth = expenses.groupBy { YearMonth.from(Instant.ofEpochMilli(it.dateTime).atZone(zone)) }

        val candidateMonths = tripsByMonth.keys.filter { ym ->
            ym >= firstTripMonth && ym < currentMonth && !tripsByMonth[ym].isNullOrEmpty()
        }
        if (candidateMonths.isEmpty()) return null

        var bestMonth: YearMonth? = null
        var minCost = Double.POSITIVE_INFINITY
        for (ym in candidateMonths.sorted()) {
            val tripCost = tripsByMonth[ym].orEmpty().sumOf { it.fuelCost }
            val expenseCost = expensesByMonth[ym].orEmpty().sumOf { it.amount }
            val totalCost = tripCost + expenseCost
            if (totalCost < minCost) {
                minCost = totalCost
                bestMonth = ym
            }
        }

        val ym = bestMonth ?: return null
        val monthName = MONTH_NAMES_PT[ym.monthValue - 1]
        val atMs = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return PersonalRecord(
            kind = RecordKind.CHEAPEST_MONTH,
            value = minCost,
            display = String.format(PT_BR, "R$ %,.2f", minCost),
            detail = "$monthName de ${ym.year}",
            atMs = atMs,
            tripId = null
        )
    }

    private fun commuteWeekdayInsight(
        trips: List<TripItem>,
        nowMs: Long,
        zone: ZoneId,
        badge: String,
        dirLabel: String,
        id: String
    ): Insight? {
        val windowStart = nowMs - 56 * DAY_MS
        val dirTrips = trips.filter { trip ->
            trip.commuteBadge == badge &&
                trip.durationMin > 0L &&
                trip.startTimeMs in windowStart..nowMs &&
                Instant.ofEpochMilli(trip.startTimeMs).atZone(zone).dayOfWeek.value in
                DayOfWeek.MONDAY.value..DayOfWeek.FRIDAY.value
        }
        if (dirTrips.size < 2) return null

        val overallMedian = RouteAnalysis.percentile(
            dirTrips.map { it.durationMin.toDouble() }.sorted(),
            0.5
        )
        val byWeekday = dirTrips.groupBy { Instant.ofEpochMilli(it.startTimeMs).atZone(zone).dayOfWeek }

        var worstDay: DayOfWeek? = null
        var maxExcess = 0.0
        for ((dow, dayTrips) in byWeekday) {
            if (dayTrips.size < 2) continue
            val dayMedian = RouteAnalysis.percentile(
                dayTrips.map { it.durationMin.toDouble() }.sorted(),
                0.5
            )
            val excess = dayMedian - overallMedian
            if (excess >= 5.0 && excess > maxExcess) {
                maxExcess = excess
                worstDay = dow
            }
        }

        val dow = worstDay ?: return null
        val dayPlural = weekdayPluralPt(dow)
        val extraMin = maxExcess.roundToInt()
        return Insight(
            id = id,
            emoji = "🚦",
            text = "$dirLabel levou $extraMin min a mais às $dayPlural",
            tone = -1
        )
    }

    private fun weekdayPluralPt(dow: DayOfWeek): String = when (dow) {
        DayOfWeek.MONDAY -> "segundas"
        DayOfWeek.TUESDAY -> "terças"
        DayOfWeek.WEDNESDAY -> "quartas"
        DayOfWeek.THURSDAY -> "quintas"
        DayOfWeek.FRIDAY -> "sextas"
        DayOfWeek.SATURDAY -> "sábados"
        DayOfWeek.SUNDAY -> "domingos"
    }

    private fun formatDistanceKm(km: Double): String =
        if (km >= 100.0) String.format(PT_BR, "%.0f km", km)
        else String.format(PT_BR, "%.1f km", km)

    private fun extractDestinationLabel(commuteBadge: String?): String? {
        if (commuteBadge.isNullOrBlank()) return null
        return when {
            commuteBadge.contains("➔ ") -> commuteBadge.substringAfter("➔ ").trim().takeIf { it.isNotEmpty() }
            commuteBadge.startsWith("Destino: ") -> commuteBadge.substringAfter("Destino: ").trim().takeIf { it.isNotEmpty() }
            else -> null
        }
    }

    private fun stripLeadingEmoji(label: String): String {
        val trimmed = label.trim()
        val spaceIdx = trimmed.indexOf(' ')
        if (spaceIdx > 0 && trimmed.substring(0, spaceIdx).none { it.isLetterOrDigit() }) {
            return trimmed.substring(spaceIdx + 1).trim()
        }
        return trimmed.dropWhile { !it.isLetterOrDigit() }.trim()
    }
}
