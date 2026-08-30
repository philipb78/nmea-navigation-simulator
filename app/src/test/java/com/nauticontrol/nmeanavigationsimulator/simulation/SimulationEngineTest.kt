package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import org.junit.Assert.assertThrows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.random.Random

class SimulationEngineTest {
    private val route = listOf(
        Waypoint("A", GeoPoint(50.7050, -1.2980)),
        Waypoint("B", GeoPoint(50.7090, -1.2860)),
        Waypoint("C", GeoPoint(50.7145, -1.2725))
    )

    @Test
    fun `simulation distance is approximately frame rate independent`() {
        val settings1Hz = SimulatorSettings(speedKnotsMin = 12.0, speedKnotsMax = 12.0, updateRateHz = 1, injectedDeviationNm = 0.0)
        val oneStepEngine = SimulationEngine(route, Random(1))
        val oneStep = oneStepEngine.tick(settings1Hz, timestampMillis = 1_000L, previousTimestampMillis = 0L)

        val settings2Hz = settings1Hz.copy(updateRateHz = 2)
        val twoStepEngine = SimulationEngine(route, Random(1))
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
        val engine = SimulationEngine(route, Random(2))
        val snapshot = engine.tick(
            SimulatorSettings(speedKnotsMin = 12.0, speedKnotsMax = 12.0, updateRateHz = 0, injectedDeviationNm = 0.0),
            timestampMillis = 1_000L,
            previousTimestampMillis = null
        )

        assertTrue(snapshot.speedKnots > 0.0)
    }

    @Test
    fun `current changes speed over ground while preserving speed through water`() {
        val engine = SimulationEngine(route, Random(3))
        val snapshot = engine.tick(
            SimulatorSettings(
                speedKnotsMin = 10.0,
                speedKnotsMax = 10.0,
                updateRateHz = 1,
                injectedDeviationNm = 0.0,
                currentDirectionTrueMin = 90.0,
                currentDirectionTrueMax = 90.0,
                currentSpeedKnotsMin = 2.0,
                currentSpeedKnotsMax = 2.0
            ),
            timestampMillis = 1_000L,
            previousTimestampMillis = 0L
        )

        assertTrue(snapshot.speedThroughWaterKnots == 10.0)
        assertTrue(snapshot.speedOverGroundKnots > 10.0)
        assertTrue(snapshot.courseOverGroundTrue != snapshot.headingTrue)
    }

    @Test
    fun `static depth range flows into navigation snapshot unchanged`() {
        val engine = SimulationEngine(route, Random(4))
        val snapshot = engine.tick(
            SimulatorSettings(depthMetersMin = 12.3, depthMetersMax = 12.3),
            timestampMillis = 1_000L,
            previousTimestampMillis = null
        )

        assertEquals(12.3, snapshot.depthMeters, 0.0)
    }

    @Test
    fun `environmental values fluctuate within configured ranges`() {
        val settings = SimulatorSettings(
            speedKnotsMin = 7.0,
            speedKnotsMax = 9.0,
            windDirectionTrueMin = 220.0,
            windDirectionTrueMax = 260.0,
            windSpeedKnotsMin = 10.0,
            windSpeedKnotsMax = 14.0,
            depthMetersMin = 7.5,
            depthMetersMax = 8.5,
            waterTemperatureCelsiusMin = 13.0,
            waterTemperatureCelsiusMax = 15.0,
            currentDirectionTrueMin = 80.0,
            currentDirectionTrueMax = 100.0,
            currentSpeedKnotsMin = 0.3,
            currentSpeedKnotsMax = 0.7
        )
        val engine = SimulationEngine(route, Random(99))
        engine.reset(settings, 0L)
        val first = engine.tick(settings, 1_000L, 0L)
        val second = engine.tick(settings, 60_000L, 1_000L)

        assertTrue(first.depthMeters in 7.5..8.5)
        assertTrue(second.depthMeters in 7.5..8.5)
        assertTrue(first.windSpeedKnots in 10.0..14.0)
        assertTrue(second.windSpeedKnots in 10.0..14.0)
        assertTrue(
            first.depthMeters != second.depthMeters ||
                first.windSpeedKnots != second.windSpeedKnots ||
                first.speedThroughWaterKnots != second.speedThroughWaterKnots
        )
    }

    @Test
    fun `default lough current keeps heading and course within a few degrees`() {
        val settings = SimulatorSettings(
            speedKnotsMin = 8.0,
            speedKnotsMax = 8.0,
            updateRateHz = 2,
            injectedDeviationNm = 0.0
        )
        val engine = SimulationEngine(random = Random(7))
        engine.reset(settings, 0L)
        var snapshot = engine.tick(settings, 500L, 0L)
        repeat(40) { i ->
            snapshot = engine.tick(settings, 500L + (i + 1) * 500L, 500L + i * 500L)
        }
        val set = GeoMath.shortestSignedAngleDegrees(snapshot.headingTrue, snapshot.courseOverGroundTrue)
        assertTrue("HDG vs COG was $set", set.absoluteValue < 5.0)
        val headingToTrack = GeoMath.shortestSignedAngleDegrees(snapshot.headingTrue, snapshot.trackBearingTrue)
        assertTrue("HDG should stay on the track, error was $headingToTrack", headingToTrack.absoluteValue < 2.0)
    }

    @Test
    fun `heading stays on the track and only current offsets course`() {
        val settings = SimulatorSettings(
            speedKnotsMin = 8.0,
            speedKnotsMax = 8.0,
            updateRateHz = 2,
            injectedDeviationNm = 0.0,
            currentDirectionTrueMin = 90.0,
            currentDirectionTrueMax = 90.0,
            currentSpeedKnotsMin = 2.0,
            currentSpeedKnotsMax = 2.0
        )
        val engine = SimulationEngine(route, Random(8))
        engine.reset(settings, 0L)
        var snapshot = engine.tick(settings, 500L, 0L)
        repeat(40) { i ->
            snapshot = engine.tick(settings, 500L + (i + 1) * 500L, 500L + i * 500L)
        }
        val headingToTrack = GeoMath.shortestSignedAngleDegrees(snapshot.headingTrue, snapshot.trackBearingTrue)
        val set = GeoMath.shortestSignedAngleDegrees(snapshot.headingTrue, snapshot.courseOverGroundTrue)
        assertTrue("HDG left the track ($headingToTrack)", headingToTrack.absoluteValue < 2.0)
        assertTrue("current should offset COG, set was $set", set.absoluteValue in 5.0..25.0)
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
