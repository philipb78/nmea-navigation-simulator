package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
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
}
