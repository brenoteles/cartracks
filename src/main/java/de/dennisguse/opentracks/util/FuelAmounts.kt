package de.dennisguse.opentracks.util

import java.util.Locale

/**
 * The three numbers of a fuel-up: any two given, the third follows. The driver types whatever the
 * pump or receipt shows (total paid, price per liter, liters), and [derived] is the field that was
 * not among the two most recently typed.
 */
object FuelAmounts {

    enum class Field { TOTAL, PRICE, LITERS }

    /** Fields typed by the driver, oldest first; at most two are kept. */
    fun touch(order: List<Field>, edited: Field): List<Field> = (order - edited + edited).takeLast(2)

    /** The field to compute, or null while fewer than two were typed. */
    fun derived(order: List<Field>): Field? =
        if (order.size < 2) null else Field.values().first { it !in order }

    /** Value of [field] from the other two; null when they are missing or not positive. */
    fun compute(field: Field, total: Double?, price: Double?, liters: Double?): Double? {
        fun ok(v: Double?) = v != null && v > 0
        return when (field) {
            Field.TOTAL -> if (ok(price) && ok(liters)) price!! * liters!! else null
            Field.PRICE -> if (ok(total) && ok(liters)) total!! / liters!! else null
            Field.LITERS -> if (ok(total) && ok(price)) total!! / price!! else null
        }
    }

    /** Accepts "5,89", "5.89" and "R$ 150"; null for anything else. */
    fun parse(text: String): Double? =
        text.replace("R$", "").trim().replace(',', '.').toDoubleOrNull()

    private val BR = Locale("pt", "BR")

    fun format(field: Field, value: Double): String = when (field) {
        Field.TOTAL -> String.format(BR, "%.2f", value)
        // pumps show three decimals ("5,899"); drop a trailing zero so "5,890" reads "5,89"
        Field.PRICE -> String.format(BR, "%.3f", value).let { if (it.endsWith('0')) it.dropLast(1) else it }
        Field.LITERS -> String.format(BR, "%.2f", value)
    }
}
