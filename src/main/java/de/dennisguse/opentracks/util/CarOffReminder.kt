package de.dennisguse.opentracks.util

import android.content.Context

/** "Confira celular, carteira e porta-malas" when the car Bluetooth disconnects. Items are configurable in Ajustes. */
object CarOffReminder {
    private const val PREFS = "CarTracksPrefs"
    private const val KEY_ENABLED = "car_off_reminder_enabled"
    private const val KEY_ITEMS = "car_off_reminder_items"
    val DEFAULT_ITEMS = listOf("Celular", "Carteira", "Porta-malas")

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun items(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_ITEMS

    fun save(context: Context, enabled: Boolean, items: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_ITEMS, items.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
            .apply()
    }

    /** "celular, carteira e porta-malas". */
    fun checklist(items: List<String>): String {
        val low = items.map { it.lowercase(java.util.Locale("pt", "BR")) }
        return when (low.size) {
            0 -> ""
            1 -> low[0]
            else -> low.dropLast(1).joinToString(", ") + " e " + low.last()
        }
    }
}
