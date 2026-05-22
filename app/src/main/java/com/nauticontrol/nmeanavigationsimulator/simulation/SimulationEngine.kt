package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import kotlin.math.absoluteValue

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

        val distancePerTick = sanitizedSettings.speedKnots * deltaSeconds / 3600.0
        vesselPosition = GeoMath.move(vesselPosition, headingTrue, distancePerTick)

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
            speedKnots = sanitizedSettings.speedKnots,
            crossTrackErrorNm = signedXte,
            bearingToWaypoint = GeoMath.bearingDegrees(vesselPosition, activeWaypoint.position),
            distanceToWaypointNm = GeoMath.distanceNm(vesselPosition, activeWaypoint.position),
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
            injectedDeviationNm = injectedDeviationNm.coerceIn(-5.0, 5.0)
        )
    }

    companion object {
        private fun defaultWaypoints(): List<Waypoint> = listOf(
            Waypoint("WP01", GeoPoint(50.7050, -1.2980)),
            Waypoint("WP02", GeoPoint(50.7090, -1.2860)),
            Waypoint("WP03", GeoPoint(50.7145, -1.2725)),
            Waypoint("WP04", GeoPoint(50.7190, -1.2580))
        )
    }
}
