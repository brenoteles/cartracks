package de.dennisguse.opentracks.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.dennisguse.opentracks.data.TripLoader
import de.dennisguse.opentracks.util.CarNotifications
import de.dennisguse.opentracks.util.DrivingInsights
import de.dennisguse.opentracks.util.WeekSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sunday-evening notification with the week's km, time at the wheel, spending, km/L and longest
 * trip, compared with the week before. An inexact alarm fires the receiver, which reloads the
 * data itself (the app may be closed) and schedules the next Sunday.
 */
object WeeklySummary {
    private const val PREFS = "CarTracksPrefs"
    private const val KEY_ENABLED = "weekly_summary_enabled"
    const val ACTION_FIRE = "de.dennisguse.cartracks.action.WEEKLY_SUMMARY"
    const val ACTION_OPEN_WEEK = "de.dennisguse.cartracks.action.OPEN_WEEK_REPORT"
    private val SEND_AT = LocalTime.of(20, 0)

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        schedule(context)
    }

    /** Next Sunday 20:00 (today if it is Sunday before 20:00). */
    fun nextFire(now: ZonedDateTime): ZonedDateTime {
        val today = now.toLocalDate().atTime(SEND_AT).atZone(now.zone)
        if (now.dayOfWeek == DayOfWeek.SUNDAY && now.isBefore(today)) return today
        return now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.SUNDAY)).atTime(SEND_AT).atZone(now.zone)
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 47, Intent(context, WeeklySummaryReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.cancel(pi)
        if (!isEnabled(context)) return
        val at = nextFire(ZonedDateTime.now(ZoneId.systemDefault())).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    /** Computes this week's summary and posts it; silent when there were no trips in either week. */
    fun post(context: Context) {
        val all = TripLoader.loadAll(context)
        val week = DrivingInsights.weekSummary(all.trips, all.fills, all.expenses, System.currentTimeMillis())
        if (week.current.trips == 0 && week.previous.trips == 0) return
        val (title, text) = format(week)
        CarNotifications.weeklySummary(context, title, text)
    }

    /** (title, body) of the notification. */
    fun format(week: WeekSummary): Pair<String, String> {
        val c = week.current
        val p = week.previous
        val br = Locale("pt", "BR")
        val title = if (c.trips == 0) "Semana sem viagens" else "Sua semana: ${km(c.km)} km em ${c.trips} viagens"
        val lines = mutableListOf<String>()
        lines += "🛣️ ${km(c.km)} km${delta(c.km, p.km)}"
        lines += "⏱️ ${hours(c.drivingMin)} ao volante${delta(c.drivingMin.toDouble(), p.drivingMin.toDouble())}"
        lines += "💸 ${money(c.totalSpent)} gastos${delta(c.totalSpent, p.totalSpent)}"
        if (c.avgKmL > 0) lines += "⛽ ${String.format(br, "%.1f", c.avgKmL)} km/L${delta(c.avgKmL, p.avgKmL)}"
        c.longestTrip?.let { t ->
            val where = t.commuteBadge?.substringAfter("➔ ")?.substringAfter("Destino: ") ?: t.dateFormatted
            lines += "🏁 Mais longa: ${km(t.distanceKm)} km · $where"
        }
        return title to lines.joinToString("\n")
    }

    private fun delta(now: Double, before: Double): String {
        if (before <= 0) return ""
        val pct = ((now - before) / before * 100).roundToInt()
        if (abs(pct) < 1) return " (=)"
        return if (pct > 0) " (+$pct%)" else " ($pct%)"
    }

    private fun km(v: Double) = if (v >= 100) String.format(Locale("pt", "BR"), "%.0f", v) else String.format(Locale("pt", "BR"), "%.1f", v)
    private fun hours(min: Long) = if (min >= 60) "${min / 60}h${String.format("%02d", min % 60)}" else "$min min"
    private fun money(v: Double) = CarWidgets.money(v)
}

class WeeklySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            WeeklySummary.ACTION_FIRE -> {
                val pending = goAsync()
                Thread {
                    try {
                        WeeklySummary.post(context)
                    } catch (e: Exception) {
                        android.util.Log.w("WeeklySummary", "Weekly summary failed", e)
                    } finally {
                        WeeklySummary.schedule(context)
                        pending.finish()
                    }
                }.start()
            }
            Intent.ACTION_BOOT_COMPLETED -> WeeklySummary.schedule(context)
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WeeklySummary.schedule(context)
                // the stored widget snapshot may still have an older app version's shape
                CarWidgets.refreshFromDatabase(context)
            }
        }
    }
}
