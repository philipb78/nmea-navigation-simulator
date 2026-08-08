package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

class SimulationEngine(
    private val routeWaypoints: List<Waypoint> = defaultWaypoints(),
    private val random: Random = Random.Default
) {
    init {
        require(routeWaypoints.size >= 2) { "Simulation route requires at least two waypoints" }
    }

    private var routeIndex = 1
    private var vesselPosition = routeWaypoints.first().position
    private var headingTrue = GeoMath.bearingDegrees(routeWaypoints[0].position, routeWaypoints[1].position)
    private val vesselTrack = mutableListOf(vesselPosition)
    private var lastTickTimestampMillis: Long? = null

    private val speedOscillator = RangeOscillator(7.0, 9.0, 0.15, 20.0, 60.0, random = random)
    private val windDirectionOscillator = RangeOscillator(220.0, 260.0, 2.0, 45.0, 120.0, circular = true, random = random)
    private val windSpeedOscillator = RangeOscillator(10.0, 14.0, 0.4, 15.0, 40.0, random = random)
    private val depthOscillator = RangeOscillator(7.5, 8.5, 0.02, 60.0, 180.0, random = random)
    private val waterTemperatureOscillator = RangeOscillator(13.0, 15.0, 0.01, 120.0, 300.0, random = random)
    private val currentDirectionOscillator = RangeOscillator(80.0, 100.0, 1.0, 30.0, 90.0, circular = true, random = random)
    private val currentSpeedOscillator = RangeOscillator(0.3, 0.7, 0.05, 30.0, 90.0, random = random)

    fun reset(
        settings: SimulatorSettings = SimulatorSettings(),
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        routeIndex = 1
        vesselPosition = routeWaypoints.first().position
        headingTrue = GeoMath.bearingDegrees(routeWaypoints[0].position, routeWaypoints[1].position)
        vesselTrack.clear()
        vesselTrack += vesselPosition
        lastTickTimestampMillis = null
        resetOscillators(settings.sanitized(), timestampMillis)
    }

    fun currentRoute(): List<GeoPoint> = routeWaypoints.map { it.position }

    fun tick(
        settings: SimulatorSettings,
        timestampMillis: Long = System.currentTimeMillis(),
        previousTimestampMillis: Long? = lastTickTimestampMillis
    ): NavigationSnapshot {
        val sanitizedSettings = settings.sanitized()
        val deltaSeconds = calculateDeltaSeconds(sanitizedSettings, timestampMillis, previousTimestampMillis)
        lastTickTimestampMillis = timestampMillis

        updateOscillatorBounds(sanitizedSettings)
        val speedThroughWaterKnots = speedOscillator.tick(deltaSeconds, timestampMillis)
        val windDirectionTrue = windDirectionOscillator.tick(deltaSeconds, timestampMillis)
        val windSpeedKnots = windSpeedOscillator.tick(deltaSeconds, timestampMillis)
        val depthMeters = depthOscillator.tick(deltaSeconds, timestampMillis)
        val waterTemperatureCelsius = waterTemperatureOscillator.tick(deltaSeconds, timestampMillis)
        val currentDirectionTrue = currentDirectionOscillator.tick(deltaSeconds, timestampMillis)
        val currentSpeedKnots = currentSpeedOscillator.tick(deltaSeconds, timestampMillis)

        var fromWaypoint = routeWaypoints[routeIndex - 1]
        var toWaypoint = routeWaypoints[routeIndex]
        var trackBearing = GeoMath.bearingDegrees(fromWaypoint.position, toWaypoint.position)
        var signedXte = GeoMath.crossTrackErrorNm(vesselPosition, fromWaypoint.position, toWaypoint.position)

        val headingError = GeoMath.shortestSignedAngleDegrees(headingTrue, trackBearing)
        val xteCorrection = (-signedXte * 30.0).coerceIn(-25.0, 25.0)
        val headingCorrection = (headingError * 0.35).coerceIn(-10.0, 10.0)
        val targetHeading = GeoMath.normalizeDegrees(trackBearing + xteCorrection + headingCorrection)
        val maxTurnRateDegPerSecond = 8.0
        val maxHeadingStep = maxTurnRateDegPerSecond * deltaSeconds
        val turnStep = GeoMath.shortestSignedAngleDegrees(headingTrue, targetHeading)
            .coerceIn(-maxHeadingStep, maxHeadingStep)
        headingTrue = GeoMath.normalizeDegrees(headingTrue + turnStep)

        val waterEastKnots = eastComponent(speedThroughWaterKnots, headingTrue)
        val waterNorthKnots = northComponent(speedThroughWaterKnots, headingTrue)
        val currentEastKnots = eastComponent(currentSpeedKnots, currentDirectionTrue)
        val currentNorthKnots = northComponent(currentSpeedKnots, currentDirectionTrue)
        val sogEastKnots = waterEastKnots + currentEastKnots
        val sogNorthKnots = waterNorthKnots + currentNorthKnots
        val speedOverGroundKnots = hypot(sogEastKnots, sogNorthKnots)
        val courseOverGroundTrue = bearingFromComponents(sogEastKnots, sogNorthKnots, headingTrue)
        val distancePerTick = speedOverGroundKnots * deltaSeconds / 3600.0
        vesselPosition = GeoMath.move(vesselPosition, courseOverGroundTrue, distancePerTick)

        val disturbanceError = sanitizedSettings.injectedDeviationNm - signedXte
        if (disturbanceError.absoluteValue > 0.0001) {
            val lateralStep = disturbanceError * minOf(0.25 * deltaSeconds, 1.0)
            val routeNormalBearing = GeoMath.normalizeDegrees(
                trackBearing + if (lateralStep >= 0.0) 90.0 else -90.0
            )
            vesselPosition = GeoMath.move(vesselPosition, routeNormalBearing, lateralStep.absoluteValue)
            signedXte = GeoMath.crossTrackErrorNm(vesselPosition, fromWaypoint.position, toWaypoint.position)
        }

        val alongTrackRatio = GeoMath.alongTrackRatio(vesselPosition, fromWaypoint.position, toWaypoint.position)
        val distanceToWaypoint = GeoMath.distanceNm(vesselPosition, toWaypoint.position)
        if (routeIndex < routeWaypoints.lastIndex && (alongTrackRatio >= 0.995 || distanceToWaypoint < 0.03)) {
            routeIndex += 1
            fromWaypoint = routeWaypoints[routeIndex - 1]
            toWaypoint = routeWaypoints[routeIndex]
            trackBearing = GeoMath.bearingDegrees(fromWaypoint.position, toWaypoint.position)
            signedXte = GeoMath.crossTrackErrorNm(vesselPosition, fromWaypoint.position, toWaypoint.position)
        }

        vesselTrack += vesselPosition
        if (vesselTrack.size > 500) {
            vesselTrack.removeAt(0)
        }

        val activeWaypoint = routeWaypoints[routeIndex]
        return NavigationSnapshot(
            position = vesselPosition,
            headingTrue = headingTrue,
            trackBearingTrue = trackBearing,
            speedKnots = speedOverGroundKnots,
            speedThroughWaterKnots = speedThroughWaterKnots,
            speedOverGroundKnots = speedOverGroundKnots,
            courseOverGroundTrue = courseOverGroundTrue,
            crossTrackErrorNm = signedXte,
            bearingToWaypoint = GeoMath.bearingDegrees(vesselPosition, activeWaypoint.position),
            distanceToWaypointNm = GeoMath.distanceNm(vesselPosition, activeWaypoint.position),
            windDirectionTrue = windDirectionTrue,
            windSpeedKnots = windSpeedKnots,
            depthMeters = depthMeters,
            waterTemperatureCelsius = waterTemperatureCelsius,
            currentDirectionTrue = currentDirectionTrue,
            currentSpeedKnots = currentSpeedKnots,
            previousWaypoint = routeWaypoints[routeIndex - 1],
            currentWaypoint = activeWaypoint,
            route = currentRoute(),
            vesselTrack = vesselTrack.toList(),
            timestampMillis = timestampMillis
        )
    }

    private fun resetOscillators(settings: SimulatorSettings, timestampMillis: Long) {
        speedOscillator.reset(settings.speedKnotsMin, settings.speedKnotsMax, timestampMillis)
        windDirectionOscillator.reset(settings.windDirectionTrueMin, settings.windDirectionTrueMax, timestampMillis)
        windSpeedOscillator.reset(settings.windSpeedKnotsMin, settings.windSpeedKnotsMax, timestampMillis)
        depthOscillator.reset(settings.depthMetersMin, settings.depthMetersMax, timestampMillis)
        waterTemperatureOscillator.reset(
            settings.waterTemperatureCelsiusMin,
            settings.waterTemperatureCelsiusMax,
            timestampMillis
        )
        currentDirectionOscillator.reset(settings.currentDirectionTrueMin, settings.currentDirectionTrueMax, timestampMillis)
        currentSpeedOscillator.reset(settings.currentSpeedKnotsMin, settings.currentSpeedKnotsMax, timestampMillis)
    }

    private fun updateOscillatorBounds(settings: SimulatorSettings) {
        speedOscillator.updateBounds(settings.speedKnotsMin, settings.speedKnotsMax)
        windDirectionOscillator.updateBounds(settings.windDirectionTrueMin, settings.windDirectionTrueMax)
        windSpeedOscillator.updateBounds(settings.windSpeedKnotsMin, settings.windSpeedKnotsMax)
        depthOscillator.updateBounds(settings.depthMetersMin, settings.depthMetersMax)
        waterTemperatureOscillator.updateBounds(
            settings.waterTemperatureCelsiusMin,
            settings.waterTemperatureCelsiusMax
        )
        currentDirectionOscillator.updateBounds(settings.currentDirectionTrueMin, settings.currentDirectionTrueMax)
        currentSpeedOscillator.updateBounds(settings.currentSpeedKnotsMin, settings.currentSpeedKnotsMax)
    }

    private fun calculateDeltaSeconds(
        settings: SimulatorSettings,
        timestampMillis: Long,
        previousTimestampMillis: Long?
    ): Double {
        if (previousTimestampMillis == null || timestampMillis <= previousTimestampMillis) {
            return 1.0 / settings.updateRateHz.coerceAtLeast(1)
        }
        return ((timestampMillis - previousTimestampMillis) / 1000.0).coerceIn(0.05, 2.0)
    }

    private fun eastComponent(speedKnots: Double, bearingTrue: Double): Double {
        return speedKnots * kotlin.math.sin(Math.toRadians(bearingTrue))
    }

    private fun northComponent(speedKnots: Double, bearingTrue: Double): Double {
        return speedKnots * kotlin.math.cos(Math.toRadians(bearingTrue))
    }

    private fun bearingFromComponents(eastKnots: Double, northKnots: Double, fallback: Double): Double {
        if (hypot(eastKnots, northKnots) < 0.0001) return fallback
        return GeoMath.normalizeDegrees(Math.toDegrees(atan2(eastKnots, northKnots)))
    }

    companion object {
        private fun defaultWaypoints(): List<Waypoint> = listOf(
            Waypoint("OXFORD", GeoPoint(54.6190, -6.2480)),
            Waypoint("KINNEG", GeoPoint(54.5800, -6.3500)),
            Waypoint("MIDLOU", GeoPoint(54.6100, -6.4300)),
            Waypoint("TOOME", GeoPoint(54.6500, -6.5000)),
            Waypoint("PORTGL", GeoPoint(54.7000, -6.5200))
        )
    }
}

private fun SimulatorSettings.sanitized(): SimulatorSettings {
    val speedPair = orderedPair(speedKnotsMin, speedKnotsMax)
    val speedMin = speedPair.first.coerceAtLeast(0.0)
    val speedMax = speedPair.second.coerceAtLeast(speedMin)
    val windDirPair = orderedPair(windDirectionTrueMin, windDirectionTrueMax)
    val windDirMin = GeoMath.normalizeDegrees(windDirPair.first)
    val windDirMax = GeoMath.normalizeDegrees(windDirPair.second)
    val windSpeedPair = orderedPair(windSpeedKnotsMin, windSpeedKnotsMax)
    val windSpeedMin = windSpeedPair.first.coerceIn(0.0, 80.0)
    val windSpeedMax = windSpeedPair.second.coerceIn(windSpeedMin, 80.0)
    val depthPair = orderedPair(depthMetersMin, depthMetersMax)
    val depthMin = depthPair.first.coerceIn(0.0, 200.0)
    val depthMax = depthPair.second.coerceIn(depthMin, 200.0)
    val tempPair = orderedPair(waterTemperatureCelsiusMin, waterTemperatureCelsiusMax)
    val tempMin = tempPair.first.coerceIn(-2.0, 40.0)
    val tempMax = tempPair.second.coerceIn(tempMin, 40.0)
    val currentDirPair = orderedPair(currentDirectionTrueMin, currentDirectionTrueMax)
    val currentDirMin = GeoMath.normalizeDegrees(currentDirPair.first)
    val currentDirMax = GeoMath.normalizeDegrees(currentDirPair.second)
    val currentSpeedPair = orderedPair(currentSpeedKnotsMin, currentSpeedKnotsMax)
    val currentSpeedMin = currentSpeedPair.first.coerceIn(0.0, 10.0)
    val currentSpeedMax = currentSpeedPair.second.coerceIn(currentSpeedMin, 10.0)

    return copy(
        speedKnotsMin = speedMin,
        speedKnotsMax = speedMax,
        updateRateHz = updateRateHz.coerceAtLeast(1),
        injectedDeviationNm = injectedDeviationNm.coerceIn(-5.0, 5.0),
        rudderAngleDegrees = rudderAngleDegrees.coerceIn(-40.0, 40.0),
        windDirectionTrueMin = windDirMin,
        windDirectionTrueMax = windDirMax,
        windSpeedKnotsMin = windSpeedMin,
        windSpeedKnotsMax = windSpeedMax,
        depthMetersMin = depthMin,
        depthMetersMax = depthMax,
        waterTemperatureCelsiusMin = tempMin,
        waterTemperatureCelsiusMax = tempMax,
        currentDirectionTrueMin = currentDirMin,
        currentDirectionTrueMax = currentDirMax,
        currentSpeedKnotsMin = currentSpeedMin,
        currentSpeedKnotsMax = currentSpeedMax
    )
}

private fun orderedPair(min: Double, max: Double): Pair<Double, Double> {
    return if (min <= max) min to max else max to min
}
