package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.hypot

class SimulationEngine(
    private val routeWaypoints: List<Waypoint> = defaultWaypoints()
) {
    init {
        require(routeWaypoints.size >= 2) { "Simulation route requires at least two waypoints" }
    }

    private var routeIndex = 1
    private var vesselPosition = routeWaypoints.first().position
    private var headingTrue = GeoMath.bearingDegrees(routeWaypoints[0].position, routeWaypoints[1].position)
    private val vesselTrack = mutableListOf(vesselPosition)
    private var lastTickTimestampMillis: Long? = null

    fun reset() {
        routeIndex = 1
        vesselPosition = routeWaypoints.first().position
        headingTrue = GeoMath.bearingDegrees(routeWaypoints[0].position, routeWaypoints[1].position)
        vesselTrack.clear()
        vesselTrack += vesselPosition
        lastTickTimestampMillis = null
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

        val waterEastKnots = eastComponent(sanitizedSettings.speedKnots, headingTrue)
        val waterNorthKnots = northComponent(sanitizedSettings.speedKnots, headingTrue)
        val currentEastKnots = eastComponent(
            sanitizedSettings.currentSpeedKnots,
            sanitizedSettings.currentDirectionTrue
        )
        val currentNorthKnots = northComponent(
            sanitizedSettings.currentSpeedKnots,
            sanitizedSettings.currentDirectionTrue
        )
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
            speedThroughWaterKnots = sanitizedSettings.speedKnots,
            speedOverGroundKnots = speedOverGroundKnots,
            courseOverGroundTrue = courseOverGroundTrue,
            crossTrackErrorNm = signedXte,
            bearingToWaypoint = GeoMath.bearingDegrees(vesselPosition, activeWaypoint.position),
            distanceToWaypointNm = GeoMath.distanceNm(vesselPosition, activeWaypoint.position),
            windDirectionTrue = sanitizedSettings.windDirectionTrue,
            windSpeedKnots = sanitizedSettings.windSpeedKnots,
            depthMeters = sanitizedSettings.depthMeters,
            waterTemperatureCelsius = sanitizedSettings.waterTemperatureCelsius,
            currentDirectionTrue = sanitizedSettings.currentDirectionTrue,
            currentSpeedKnots = sanitizedSettings.currentSpeedKnots,
            previousWaypoint = routeWaypoints[routeIndex - 1],
            currentWaypoint = activeWaypoint,
            route = currentRoute(),
            vesselTrack = vesselTrack.toList(),
            timestampMillis = timestampMillis
        )
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

    private fun SimulatorSettings.sanitized(): SimulatorSettings {
        return copy(
            speedKnots = speedKnots.coerceAtLeast(0.0),
            updateRateHz = updateRateHz.coerceAtLeast(1),
            injectedDeviationNm = injectedDeviationNm.coerceIn(-5.0, 5.0),
            windDirectionTrue = GeoMath.normalizeDegrees(windDirectionTrue),
            windSpeedKnots = windSpeedKnots.coerceIn(0.0, 80.0),
            depthMeters = depthMeters.coerceIn(0.0, 200.0),
            waterTemperatureCelsius = waterTemperatureCelsius.coerceIn(-2.0, 40.0),
            currentDirectionTrue = GeoMath.normalizeDegrees(currentDirectionTrue),
            currentSpeedKnots = currentSpeedKnots.coerceIn(0.0, 10.0)
        )
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
