package de.dennisguse.opentracks.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import de.dennisguse.opentracks.CarMainActivity
import de.dennisguse.opentracks.R
import de.dennisguse.opentracks.data.Expense
import de.dennisguse.opentracks.data.TripLoader
import de.dennisguse.opentracks.ui.model.FuelItem
import de.dennisguse.opentracks.ui.model.TripItem
import de.dennisguse.opentracks.util.DrivingInsights
import de.dennisguse.opentracks.util.FuelConsumptionModel
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * What the home-screen widgets show. The app publishes it whenever trips, fuel-ups or expenses
 * change ([publish]), and [CarWidgets.refreshFromDatabase] after a trip ends with the app closed;
 * widgets only read it.
 */
data class WidgetSnapshot(
    val lastTripId: Long = -1,
    val lastTripTitle: String = "",
    val lastTripBadge: String = "",
    val lastTripDate: String = "",
    val lastTripKm: Double = 0.0,
    val lastTripMin: Long = 0,
    val lastTripAvgKmh: Double = 0.0,
    val lastTripCost: Double = 0.0,
    val weekKm: Double = 0.0,
    val prevWeekKm: Double = 0.0,
    val weekTrips: Int = 0,
    val weekMin: Long = 0,
    val weekKmL: Double = 0.0,
    val weekSpent: Double = 0.0,
    val prevWeekSpent: Double = 0.0,
    val tankLiters: Double = 0.0,
    val tankCapacity: Double = 0.0,
    val rangeKm: Double = 0.0,
    val fuelType: String = ""
) {
    val tankPercent get() = if (tankCapacity > 0) (100 * tankLiters / tankCapacity).toInt().coerceIn(0, 100) else 0

    companion object {
        private const val PREFS = "CarTracksWidgets"
        private const val KEY = "snapshot"

        fun read(context: Context): WidgetSnapshot {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return WidgetSnapshot()
            return runCatching {
                val o = JSONObject(raw)
                WidgetSnapshot(
                    o.optLong("lastTripId", -1), o.optString("lastTripTitle"), o.optString("lastTripBadge"),
                    o.optString("lastTripDate"), o.optDouble("lastTripKm", 0.0), o.optLong("lastTripMin"),
                    o.optDouble("lastTripAvgKmh", 0.0), o.optDouble("lastTripCost", 0.0),
                    o.optDouble("weekKm", 0.0), o.optDouble("prevWeekKm", 0.0), o.optInt("weekTrips"), o.optLong("weekMin"),
                    o.optDouble("weekKmL", 0.0), o.optDouble("weekSpent", 0.0), o.optDouble("prevWeekSpent", 0.0),
                    o.optDouble("tankLiters", 0.0), o.optDouble("tankCapacity", 0.0), o.optDouble("rangeKm", 0.0), o.optString("fuelType")
                )
            }.getOrDefault(WidgetSnapshot())
        }

        /** Stores [s] and redraws every placed widget. */
        fun publish(context: Context, s: WidgetSnapshot) {
            val o = JSONObject()
                .put("lastTripId", s.lastTripId).put("lastTripTitle", s.lastTripTitle).put("lastTripBadge", s.lastTripBadge)
                .put("lastTripDate", s.lastTripDate).put("lastTripKm", s.lastTripKm).put("lastTripMin", s.lastTripMin)
                .put("lastTripAvgKmh", s.lastTripAvgKmh).put("lastTripCost", s.lastTripCost)
                .put("weekKm", s.weekKm).put("prevWeekKm", s.prevWeekKm).put("weekTrips", s.weekTrips).put("weekMin", s.weekMin)
                .put("weekKmL", s.weekKmL).put("weekSpent", s.weekSpent).put("prevWeekSpent", s.prevWeekSpent)
                .put("tankLiters", s.tankLiters).put("tankCapacity", s.tankCapacity).put("rangeKm", s.rangeKm).put("fuelType", s.fuelType)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
            CarWidgets.refreshAll(context)
        }

        /** What the widgets show for these trips, fuel-ups and expenses. */
        fun build(context: Context, trips: List<TripItem>, fills: List<FuelItem>, expenses: List<Expense>, model: FuelConsumptionModel): WidgetSnapshot {
            val last = trips.maxByOrNull { it.startTimeMs }
            val week = DrivingInsights.weekSummary(trips, fills, expenses, System.currentTimeMillis())
            val prefs = context.getSharedPreferences("CarTracksPrefs", Context.MODE_PRIVATE)
            val capacity = prefs.getFloat("tank_capacity_liters", 50f).toDouble()
            val liters = TripLoader.tankLiters(context, trips, fills, model)
            return WidgetSnapshot(
                lastTripId = last?.id ?: -1,
                // unnamed trips are "Viagem · <date>": the date is already on the widget, so the
                // place badge ("Destino: Casa") makes the better title
                lastTripTitle = last?.let { t -> if (t.name.startsWith("Viagem ·")) t.commuteBadge ?: "Viagem" else t.name } ?: "",
                lastTripBadge = last?.let { t -> if (t.name.startsWith("Viagem ·")) "" else t.commuteBadge } ?: "",
                lastTripDate = last?.dateFormatted ?: "",
                lastTripKm = last?.distanceKm ?: 0.0,
                lastTripMin = last?.durationMin ?: 0,
                lastTripAvgKmh = last?.avgSpeedKmh ?: 0.0,
                lastTripCost = last?.fuelCost ?: 0.0,
                weekKm = week.current.km,
                prevWeekKm = week.previous.km,
                weekTrips = week.current.trips,
                weekMin = week.current.drivingMin,
                weekKmL = week.current.avgKmL,
                weekSpent = week.current.totalSpent,
                prevWeekSpent = week.previous.totalSpent,
                tankLiters = liters,
                tankCapacity = capacity,
                rangeKm = liters * model.baseKmL(model.currentFamily()),
                fuelType = prefs.getString("primary_fuel_type", "Gasolina Comum") ?: "Gasolina Comum"
            )
        }
    }
}

object CarWidgets {
    const val ACTION_OPEN_TRIP = "de.dennisguse.cartracks.action.OPEN_TRIP"
    const val EXTRA_TRIP_ID = "trip_id"

    private val BR = Locale("pt", "BR")
    fun money(v: Double): String = NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ')
    fun km(v: Double): String = if (v >= 100) String.format(BR, "%.0f", v) else String.format(BR, "%.1f", v)

    /** Rebuilds the snapshot from the databases, off the main thread (a trip ended with the app closed). */
    fun refreshFromDatabase(context: Context) {
        val app = context.applicationContext
        Thread {
            runCatching {
                val all = TripLoader.loadAll(app)
                WidgetSnapshot.publish(app, WidgetSnapshot.build(app, all.trips, all.fills, all.expenses, all.model))
            }
        }.start()
    }

    fun refreshAll(context: Context) {
        RecordWidget.refresh(context)
        refresh(context, LastTripWidget::class.java) { LastTripWidget.views(context) }
        refresh(context, MonthCostWidget::class.java) { MonthCostWidget.views(context) }
    }

    private fun refresh(context: Context, cls: Class<*>, views: () -> RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, views())
    }

    fun openApp(context: Context, requestCode: Int, action: String, tripId: Long = -1): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, CarMainActivity::class.java)
            .setAction(action)
            .putExtra(EXTRA_TRIP_ID, tripId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Last trip: where, when, distance, time, average speed and cost. Tap opens the trip. */
class LastTripWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun views(context: Context): RemoteViews {
            val s = WidgetSnapshot.read(context)
            return RemoteViews(context.packageName, R.layout.widget_last_trip).apply {
                if (s.lastTripId < 0) {
                    setTextViewText(R.id.wt_title, "Nenhuma viagem ainda")
                    setTextViewText(R.id.wt_date, "")
                    setViewVisibility(R.id.wt_badge, View.GONE)
                    setViewVisibility(R.id.wt_stats, View.GONE)
                } else {
                    setTextViewText(R.id.wt_title, s.lastTripTitle)
                    setTextViewText(R.id.wt_date, s.lastTripDate)
                    setTextViewText(R.id.wt_badge, s.lastTripBadge)
                    setViewVisibility(R.id.wt_badge, if (s.lastTripBadge.isBlank()) View.GONE else View.VISIBLE)
                    setViewVisibility(R.id.wt_stats, View.VISIBLE)
                    setTextViewText(R.id.wt_km, CarWidgets.km(s.lastTripKm))
                    setTextViewText(R.id.wt_min, s.lastTripMin.toString())
                    setTextViewText(R.id.wt_speed, String.format(Locale.US, "%.0f", s.lastTripAvgKmh))
                    setTextViewText(R.id.wt_cost, CarWidgets.money(s.lastTripCost).removePrefix("R$ "))
                }
                setOnClickPendingIntent(R.id.wt_root, CarWidgets.openApp(context, 44, CarWidgets.ACTION_OPEN_TRIP, s.lastTripId))
            }
        }
    }
}

/** Last 7 days: km against the week before, km/L, time at the wheel, trips and spending. Tap opens the week report. */
class MonthCostWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun views(context: Context): RemoteViews {
            val s = WidgetSnapshot.read(context)
            val br = Locale("pt", "BR")
            return RemoteViews(context.packageName, R.layout.widget_month).apply {
                setTextViewText(R.id.wm_km, CarWidgets.km(s.weekKm))
                setTextViewText(R.id.wm_delta, if (s.prevWeekKm <= 0) "" else {
                    val pct = Math.round((s.weekKm - s.prevWeekKm) / s.prevWeekKm * 100)
                    (if (pct > 0) "+$pct%" else "$pct%") + " vs semana anterior"
                })
                setTextViewText(R.id.wm_kml, if (s.weekKmL > 0) String.format(br, "%.1f", s.weekKmL) else "—")
                setTextViewText(R.id.wm_time, if (s.weekMin >= 60) "${s.weekMin / 60}h${String.format("%02d", s.weekMin % 60)}" else "${s.weekMin}min")
                setTextViewText(R.id.wm_trips, s.weekTrips.toString())
                setTextViewText(R.id.wm_spent, "Gasto: " + CarWidgets.money(s.weekSpent) +
                    if (s.prevWeekSpent > 0) " · antes " + CarWidgets.money(s.prevWeekSpent) else "")
                setOnClickPendingIntent(R.id.wm_root, CarWidgets.openApp(context, 45, WeeklySummary.ACTION_OPEN_WEEK))
            }
        }
    }
}
