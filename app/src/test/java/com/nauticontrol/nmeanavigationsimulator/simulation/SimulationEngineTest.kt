package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationEngineTest {
    private val route = listOf(
        Waypoint("A", GeoPoint(50.7050, -1.2980)),
        Waypoint("B", GeoPoint(50.7090, -1.2860)),
        Waypoint("C", GeoPoint(50.7145, -1.2725))
    )

    @Test
    fun `simulation distance is approximately frame rate independent`() {
        val settings1Hz = SimulatorSettings(speedKnots = 12.0, updateRateHz = 1, injectedDeviationNm = 0.0)
        val oneStepEngine = SimulationEngine(route)
        val oneStep = oneStepEngine.tick(settings1Hz, timestampMillis = 1_000L, previousTimestampMillis = 0L)

        val settings2Hz = settings1Hz.copy(updateRateHz = 2)
        val twoStepEngine = SimulationEngine(route)
        twoStepEngine.tick(settings2Hz, timestampMillis = 500L, previousTimestampMillis = 0L)
        val twoStep = twoStepEngine.tick(settings2Hz, timestampMillis = 1_000L, previousTimestampMillis = 500L)

        assertTrue(GeoMath.distanceNm(oneStep.position, twoStep.position) < 0.01)
    }

    @Test
    fun `route requires at least two waypoints`() {
        assertThrows(IllegalArgumentException::class.java) {
            SimulationEngine(listOf(Waypoint("A", GeoPoint(50.7050, -1.2980))))
        }
    }

    @Test
    fun `invalid update rate is clamped for simulation tick`() {
        val engine = SimulationEngine(route)
        val snapshot = engine.tick(
            SimulatorSettings(speedKnots = 12.0, updateRateHz = 0, injectedDeviationNm = 0.0),
            timestampMillis = 1_000L,
            previousTimestampMillis = null
        )

        assertTrue(snapshot.speedKnots > 0.0)
    }

    @Test
    fun `current changes speed over ground while preserving speed through water`() {
        val engine = SimulationEngine(route)
        val snapshot = engine.tick(
            SimulatorSettings(
                speedKnots = 10.0,
                updateRateHz = 1,
                injectedDeviationNm = 0.0,
                currentDirectionTrue = 90.0,
                currentSpeedKnots = 2.0
            ),
            timestampMillis = 1_000L,
            previousTimestampMillis = 0L
        )

        assertTrue(snapshot.speedThroughWaterKnots == 10.0)
        assertTrue(snapshot.speedOverGroundKnots > 10.0)
        assertTrue(snapshot.courseOverGroundTrue != snapshot.headingTrue)
    }

    @Test
    fun `depth setting flows into navigation snapshot unchanged`() {
        val engine = SimulationEngine(route)
        val snapshot = engine.tick(
            SimulatorSettings(depthMeters = 12.3),
            timestampMillis = 1_000L,
            previousTimestampMillis = 0L
        )

        assertEquals(12.3, snapshot.depthMeters, 0.0)
    }

    @Test
    fun `default route follows Lough Neagh demo waypoints`() {
        val route = SimulationEngine().currentRoute()

        assertEquals(5, route.size)
        assertEquals(54.6190, route[0].latitude, 0.0001)
        assertEquals(-6.2480, route[0].longitude, 0.0001)
        assertEquals(54.5800, route[1].latitude, 0.0001)
        assertEquals(-6.3500, route[1].longitude, 0.0001)
        assertEquals(54.6500, route[3].latitude, 0.0001)
        assertEquals(-6.5000, route[3].longitude, 0.0001)
    }
}
