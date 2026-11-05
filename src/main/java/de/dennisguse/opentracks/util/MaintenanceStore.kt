package de.dennisguse.opentracks.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A maintenance item due every [intervalKm] and/or every [intervalMonths], counted from the
 * last time it was done ([lastDoneKm] on the odometer, [lastDoneMs] date).
 */
data class MaintenanceItem(
    val id: Int,
    val name: String,
    val intervalKm: Int?,
    val intervalMonths: Int?,
    val lastDoneKm: Double?,
    val lastDoneMs: Long?,
    val notes: String = ""
)

enum class MaintenanceStatus { OK, SOON, DUE }

data class MaintenanceState(
    val item: MaintenanceItem,
    val status: MaintenanceStatus,
    val kmLeft: Double?,
    val daysLeft: Long?,
    /** 0..1 of the interval already used (km or time, whichever is further along). */
    val progress: Double
) {
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            val br = java.util.Locale("pt", "BR")
            kmLeft?.let { parts += if (it >= 0) String.format(br, "faltam %,d km", it.toInt()) else String.format(br, "atrasado %,d km", (-it).toInt()) }
            daysLeft?.let { parts += if (it >= 0) "faltam $it dias" else "atrasado ${-it} dias" }
            return parts.joinToString(" · ").ifEmpty { "sem registro da última vez" }
        }
}

object MaintenanceStore {

    private const val PREFS = "CarTracksMaintenance"
    private const val KEY_ITEMS = "items"
    private const val KEY_NOTIFIED = "notified"
    private const val SOON_KM = 1000.0
    private const val SOON_DAYS = 30L

    /** Common Brazilian car schedule; the user edits intervals and the last service. */
    private fun defaults(): List<MaintenanceItem> = listOf(
        MaintenanceItem(1, "Troca de óleo e filtro", 10_000, 12, null, null),
        MaintenanceItem(2, "Filtro de ar", 15_000, 12, null, null),
        MaintenanceItem(3, "Filtro de cabine (ar-condicionado)", 15_000, 12, null, null),
        MaintenanceItem(4, "Rodízio e calibragem dos pneus", 10_000, null, null, null),
        MaintenanceItem(5, "Alinhamento e balanceamento", 10_000, 12, null, null),
        MaintenanceItem(6, "Pastilhas de freio (revisão)", 20_000, null, null, null),
        MaintenanceItem(7, "Fluido de freio", null, 24, null, null),
        MaintenanceItem(8, "Velas de ignição", 40_000, null, null, null),
        MaintenanceItem(9, "Revisão geral", 10_000, 12, null, null),
        MaintenanceItem(10, "IPVA / Licenciamento", null, 12, null, null)
    )

    fun load(context: Context): List<MaintenanceItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
            ?: return defaults()
        return try {
            fromJson(JSONArray(raw))
        } catch (e: Exception) {
            defaults()
        }
    }

    fun save(context: Context, items: List<MaintenanceItem>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ITEMS, toJson(items).toString())
            .apply()
    }

    fun nextId(items: List<MaintenanceItem>) = (items.maxOfOrNull { it.id } ?: 0) + 1

    fun evaluate(item: MaintenanceItem, odometerKm: Double, nowMs: Long = System.currentTimeMillis()): MaintenanceState {
        var kmLeft: Double? = null
        var daysLeft: Long? = null
        var progress = 0.0
        if (item.intervalKm != null && item.lastDoneKm != null) {
            kmLeft = item.lastDoneKm + item.intervalKm - odometerKm
            progress = maxOf(progress, (odometerKm - item.lastDoneKm) / item.intervalKm)
        }
        if (item.intervalMonths != null && item.lastDoneMs != null) {
            val zone = ZoneId.systemDefault()
            val due = Instant.ofEpochMilli(item.lastDoneMs).atZone(zone).plusMonths(item.intervalMonths.toLong())
            daysLeft = ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), due.toLocalDate())
            val total = ChronoUnit.DAYS.between(Instant.ofEpochMilli(item.lastDoneMs).atZone(zone).toLocalDate(), due.toLocalDate()).coerceAtLeast(1)
            progress = maxOf(progress, 1.0 - daysLeft.toDouble() / total)
        }
        val status = when {
            (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0) -> MaintenanceStatus.DUE
            (kmLeft != null && kmLeft <= SOON_KM) || (daysLeft != null && daysLeft <= SOON_DAYS) -> MaintenanceStatus.SOON
            else -> MaintenanceStatus.OK
        }
        return MaintenanceState(item, status, kmLeft, daysLeft, progress.coerceIn(0.0, 1.0))
    }

    /** Notifies each item once per status change (SOON, then DUE); cleared when serviced. */
    fun notifyDue(context: Context, odometerKm: Double) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val notified = JSONObject(sp.getString(KEY_NOTIFIED, "{}") ?: "{}")
        load(context).forEach { item ->
            val st = evaluate(item, odometerKm)
            val key = item.id.toString()
            if (st.status == MaintenanceStatus.OK) {
                notified.remove(key)
            } else if (notified.optString(key) != st.status.name) {
                val title = if (st.status == MaintenanceStatus.DUE) "Manutenção vencida: ${item.name}" else "Manutenção em breve: ${item.name}"
                CarNotifications.maintenanceDue(context, item.id, title, st.summary.replaceFirstChar { it.uppercase() })
                notified.put(key, st.status.name)
            }
        }
        sp.edit().putString(KEY_NOTIFIED, notified.toString()).apply()
    }

    fun toJson(items: List<MaintenanceItem>): JSONArray = JSONArray().apply {
        items.forEach { m ->
            put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                m.intervalKm?.let { put("intervalKm", it) }
                m.intervalMonths?.let { put("intervalMonths", it) }
                m.lastDoneKm?.let { put("lastDoneKm", it) }
                m.lastDoneMs?.let { put("lastDoneMs", it) }
                put("notes", m.notes)
            })
        }
    }

    fun fromJson(arr: JSONArray): List<MaintenanceItem> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        MaintenanceItem(
            id = o.getInt("id"),
            name = o.getString("name"),
            intervalKm = if (o.has("intervalKm") && !o.isNull("intervalKm")) o.getInt("intervalKm") else null,
            intervalMonths = if (o.has("intervalMonths") && !o.isNull("intervalMonths")) o.getInt("intervalMonths") else null,
            lastDoneKm = if (o.has("lastDoneKm") && !o.isNull("lastDoneKm")) o.getDouble("lastDoneKm") else null,
            lastDoneMs = if (o.has("lastDoneMs") && !o.isNull("lastDoneMs")) o.getLong("lastDoneMs") else null,
            notes = o.optString("notes", "")
        )
    }
}
