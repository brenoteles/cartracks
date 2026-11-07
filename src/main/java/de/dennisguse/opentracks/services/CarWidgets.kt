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
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * What the home-screen widgets show. The app publishes it whenever trips, fuel-ups or expenses
 * change ([publish]); widgets only read it, so they never touch the databases themselves.
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
    val monthLabel: String = "",
    val monthFuel: Double = 0.0,
    val monthOther: Double = 0.0,
    val monthKm: Double = 0.0,
    val monthTrips: Int = 0,
    val tankLiters: Double = 0.0,
    val tankCapacity: Double = 0.0,
    val rangeKm: Double = 0.0,
    val fuelType: String = ""
) {
    val monthTotal get() = monthFuel + monthOther
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
                    o.optString("lastTripDate"), o.optDouble("lastTripKm"), o.optLong("lastTripMin"),
                    o.optDouble("lastTripAvgKmh"), o.optDouble("lastTripCost"), o.optString("monthLabel"),
                    o.optDouble("monthFuel"), o.optDouble("monthOther"), o.optDouble("monthKm"), o.optInt("monthTrips"),
                    o.optDouble("tankLiters"), o.optDouble("tankCapacity"), o.optDouble("rangeKm"), o.optString("fuelType")
                )
            }.getOrDefault(WidgetSnapshot())
        }

        /** Stores [s] and redraws every placed widget. */
        fun publish(context: Context, s: WidgetSnapshot) {
            val o = JSONObject()
                .put("lastTripId", s.lastTripId).put("lastTripTitle", s.lastTripTitle).put("lastTripBadge", s.lastTripBadge)
                .put("lastTripDate", s.lastTripDate).put("lastTripKm", s.lastTripKm).put("lastTripMin", s.lastTripMin)
                .put("lastTripAvgKmh", s.lastTripAvgKmh).put("lastTripCost", s.lastTripCost).put("monthLabel", s.monthLabel)
                .put("monthFuel", s.monthFuel).put("monthOther", s.monthOther).put("monthKm", s.monthKm).put("monthTrips", s.monthTrips)
                .put("tankLiters", s.tankLiters).put("tankCapacity", s.tankCapacity).put("rangeKm", s.rangeKm).put("fuelType", s.fuelType)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()
            CarWidgets.refreshAll(context)
        }
    }
}

object CarWidgets {
    const val ACTION_OPEN_TRIP = "de.dennisguse.cartracks.action.OPEN_TRIP"
    const val ACTION_OPEN_COSTS = "de.dennisguse.cartracks.action.OPEN_COSTS"
    const val EXTRA_TRIP_ID = "trip_id"

    private val BR = Locale("pt", "BR")
    fun money(v: Double): String = NumberFormat.getCurrencyInstance(BR).format(v).replace(' ', ' ')
    fun km(v: Double): String = if (v >= 100) String.format(BR, "%.0f", v) else String.format(BR, "%.1f", v)

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

/** Spending of the current month: fuel, other costs, total and cost per km. Tap opens Custos. */
class MonthCostWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun views(context: Context): RemoteViews {
            val s = WidgetSnapshot.read(context)
            return RemoteViews(context.packageName, R.layout.widget_month).apply {
                setTextViewText(R.id.wm_month, ("GASTOS · " + s.monthLabel).uppercase(Locale("pt", "BR")))
                setTextViewText(R.id.wm_total, CarWidgets.money(s.monthTotal))
                setTextViewText(R.id.wm_fuel, CarWidgets.money(s.monthFuel))
                setTextViewText(R.id.wm_other, CarWidgets.money(s.monthOther))
                val perKm = if (s.monthKm > 0) CarWidgets.money(s.monthTotal / s.monthKm) + "/km" else "—"
                setTextViewText(R.id.wm_km, "${CarWidgets.km(s.monthKm)} km · ${s.monthTrips} viagens · $perKm")
                setOnClickPendingIntent(R.id.wm_root, CarWidgets.openApp(context, 45, CarWidgets.ACTION_OPEN_COSTS))
            }
        }
    }
}
