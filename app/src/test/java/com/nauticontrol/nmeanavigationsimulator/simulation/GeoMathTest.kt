package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun `move preserves commanded distance approximately`() {
        val start = GeoPoint(50.7050, -1.2980)
        val end = GeoMath.move(start, 90.0, 1.0)

        assertEquals(1.0, GeoMath.distanceNm(start, end), 0.01)
    }

    @Test
    fun `shortestSignedAngleDegrees wraps through north correctly`() {
        assertEquals(20.0, GeoMath.shortestSignedAngleDegrees(350.0, 10.0), 0.0001)
        assertEquals(-20.0, GeoMath.shortestSignedAngleDegrees(10.0, 350.0), 0.0001)
    }

    @Test
    fun `positive cross track error represents starboard side of track`() {
        val start = GeoPoint(0.0, 0.0)
        val end = GeoPoint(0.0, 1.0)
        val pointSouth = GeoPoint(-0.1, 0.5)

        assertTrue(GeoMath.crossTrackErrorNm(pointSouth, start, end) > 0.0)
    }

    @Test
    fun `move normalizes longitude across anti meridian`() {
        val start = GeoPoint(0.0, 179.9)
        val end = GeoMath.move(start, 90.0, 20.0)

        assertTrue(end.longitude in -180.0..180.0)
        assertTrue(end.longitude < -179.0)
    }
}
