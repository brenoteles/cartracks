package de.dennisguse.opentracks.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What the Início remembers about insights and records: 👍/👎 answers and the last known records. */
object InsightStore {
    private const val PREFS = "CarTracksInsights"
    private const val KEY_HIDDEN = "hidden_ids"
    private const val KEY_LIKED = "liked_ids"
    private const val KEY_RECORDS = "records_json"
    private const val KEY_BROKEN_PREFIX = "broken_at_"

    /** How long the "NOVO RECORDE" seal stays on a record. */
    const val SEAL_MS = 7L * 24 * 3600 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hidden(context: Context): Set<String> = prefs(context).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
    fun liked(context: Context): Set<String> = prefs(context).getStringSet(KEY_LIKED, emptySet()) ?: emptySet()

    fun feedback(context: Context, id: String, useful: Boolean) {
        val key = if (useful) KEY_LIKED else KEY_HIDDEN
        prefs(context).edit().putStringSet(key, (prefs(context).getStringSet(key, emptySet()) ?: emptySet()) + id).apply()
    }

    fun unhideAll(context: Context) = prefs(context).edit().remove(KEY_HIDDEN).apply()

    /**
     * Compares [current] with the records saved last time, stamps the broken ones and saves
     * [current]. Returns the kinds whose seal is still showing.
     */
    fun updateRecords(context: Context, current: List<PersonalRecord>, nowMs: Long): Set<RecordKind> {
        val p = prefs(context)
        val previous = load(p.getString(KEY_RECORDS, null))
        val edit = p.edit()
        if (previous.isNotEmpty()) {
            DrivingInsights.brokenRecords(previous, current).forEach { edit.putLong(KEY_BROKEN_PREFIX + it.kind.name, nowMs) }
        }
        edit.putString(KEY_RECORDS, save(current)).apply()
        return RecordKind.values().filter { k ->
            val at = p.getLong(KEY_BROKEN_PREFIX + k.name, 0L)
            at > 0 && nowMs - at < SEAL_MS && current.any { it.kind == k }
        }.toSet()
    }

    private fun save(records: List<PersonalRecord>): String = JSONArray().apply {
        records.forEach { put(JSONObject().put("kind", it.kind.name).put("value", it.value).put("atMs", it.atMs)) }
    }.toString()

    /** Only kind, value and time matter for the comparison. */
    private fun load(json: String?): List<PersonalRecord> = runCatching {
        val arr = JSONArray(json ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val kind = runCatching { RecordKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
            PersonalRecord(kind, o.getDouble("value"), "", "", o.optLong("atMs"))
        }
    }.getOrDefault(emptyList())
}
