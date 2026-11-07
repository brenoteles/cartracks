package de.dennisguse.opentracks.util

import de.dennisguse.opentracks.util.FuelAmounts.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelAmountsTest {

    @Test
    fun derivedIsTheFieldNotAmongTheLastTwoTyped() {
        var order = FuelAmounts.touch(emptyList(), Field.PRICE)
        assertNull(FuelAmounts.derived(order))

        order = FuelAmounts.touch(order, Field.TOTAL)
        assertEquals(Field.LITERS, FuelAmounts.derived(order))

        // typing liters afterwards makes price the computed one
        order = FuelAmounts.touch(order, Field.LITERS)
        assertEquals(listOf(Field.TOTAL, Field.LITERS), order)
        assertEquals(Field.PRICE, FuelAmounts.derived(order))

        // re-typing a field already in the pair keeps the pair
        order = FuelAmounts.touch(order, Field.TOTAL)
        assertEquals(Field.PRICE, FuelAmounts.derived(order))
    }

    @Test
    fun computesTheMissingValue() {
        assertEquals(25.47, FuelAmounts.compute(Field.LITERS, 150.0, 5.89, null)!!, 0.01)
        assertEquals(150.0, FuelAmounts.compute(Field.TOTAL, null, 5.0, 30.0)!!, 1e-9)
        assertEquals(5.0, FuelAmounts.compute(Field.PRICE, 150.0, null, 30.0)!!, 1e-9)
        assertNull(FuelAmounts.compute(Field.LITERS, 150.0, 0.0, null))
        assertNull(FuelAmounts.compute(Field.PRICE, null, null, 30.0))
    }

    @Test
    fun parsesBrazilianInput() {
        assertEquals(5.89, FuelAmounts.parse("5,89")!!, 1e-9)
        assertEquals(150.0, FuelAmounts.parse("R$ 150")!!, 1e-9)
        assertNull(FuelAmounts.parse("abc"))
    }

    @Test
    fun formatsWithComma() {
        assertEquals("150,00", FuelAmounts.format(Field.TOTAL, 150.0))
        assertEquals("25,47", FuelAmounts.format(Field.LITERS, 25.4669))
        assertEquals("5,89", FuelAmounts.format(Field.PRICE, 5.89))
        assertEquals("5,899", FuelAmounts.format(Field.PRICE, 5.899))
        assertEquals("6,00", FuelAmounts.format(Field.PRICE, 6.0))
    }
}
