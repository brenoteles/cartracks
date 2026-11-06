package de.dennisguse.opentracks.ui.components

import de.dennisguse.opentracks.ui.theme.CyberPink
import org.junit.Assert.*
import org.junit.Test

class HeatBinsTest {

    @Test
    fun `few trips get one bin per count`() {
        val bins = heatBins(3)
        assertEquals(listOf("1×", "2×", "3×"), bins.map { it.label })
        assertEquals(CyberPink, bins.last().color)
    }

    @Test
    fun `many trips split into four contiguous bins ending at the maximum`() {
        val bins = heatBins(20)
        assertEquals(4, bins.size)
        assertEquals(1, bins.first().min)
        assertEquals(20, bins.last().max)
        bins.zipWithNext().forEach { (a, b) -> assertEquals(a.max + 1, b.min) }
        assertTrue(bins.zipWithNext().all { (a, b) -> a.width < b.width })
    }

    @Test
    fun `single trip period still renders`() {
        assertEquals(listOf("1×"), heatBins(1).map { it.label })
        assertEquals(listOf("1×"), heatBins(0).map { it.label })
    }
}
