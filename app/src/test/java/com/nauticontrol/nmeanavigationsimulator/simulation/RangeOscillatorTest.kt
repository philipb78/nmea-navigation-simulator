package com.nauticontrol.nmeanavigationsimulator.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class RangeOscillatorTest {
    @Test
    fun `values stay within linear bounds over many ticks`() {
        val random = Random(42)
        val oscillator = RangeOscillator(10.0, 14.0, 0.4, 1.0, 2.0, random = random)
        oscillator.reset(10.0, 14.0, 0L)

        var timestamp = 0L
        repeat(200) {
            timestamp += 500L
            val value = oscillator.tick(0.5, timestamp)
            assertTrue("value $value out of range at $timestamp", value in 10.0..14.0)
        }
    }

    @Test
    fun `circular oscillator stays within max step per tick`() {
        val random = Random(7)
        val maxRate = 2.0
        val oscillator = RangeOscillator(220.0, 260.0, maxRate, 1.0, 2.0, circular = true, random = random)
        oscillator.reset(220.0, 260.0, 0L)

        var previous = oscillator.currentValue()
        var timestamp = 0L
        repeat(100) {
            timestamp += 200L
            val value = oscillator.tick(0.2, timestamp)
            val step = abs(GeoMath.shortestSignedAngleDegrees(previous, value))
            assertTrue("step $step exceeded max rate", step <= maxRate * 0.2 + 0.001)
            previous = value
        }
    }

    @Test
    fun `circular range wrapping through north stays on the short arc`() {
        val oscillator = RangeOscillator(350.0, 10.0, 5.0, 1.0, 2.0, circular = true, random = Random(3))
        oscillator.reset(350.0, 10.0, 0L)
        var timestamp = 0L
        repeat(80) {
            timestamp += 250L
            val value = oscillator.tick(0.25, timestamp)
            val onArc = value >= 350.0 || value <= 10.0
            assertTrue("value $value left the 350-10 arc", onArc)
        }
        assertTrue(oscillator.currentValue() >= 350.0 || oscillator.currentValue() <= 10.0)
    }

    @Test
    fun `static range returns fixed value`() {
        val oscillator = RangeOscillator(12.3, 12.3, 1.0, 1.0, 2.0)
        oscillator.reset(12.3, 12.3, 0L)

        assertEquals(12.3, oscillator.tick(1.0, 5_000L), 0.0)
        assertEquals(12.3, oscillator.tick(1.0, 10_000L), 0.0)
    }
}
