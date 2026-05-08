package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import kotlin.math.absoluteValue

class SimulationEngine(
    private val routeWaypoints: List<Waypoint> = defaultWaypoints()
) {
    private var routeIndex = 1
    private var vesselPosition = routeWaypoints.first().position
    private var headingTrue = GeoMath.bearingDegrees(routeWaypoints[0].position, routeWaypoints[1].position)
    private val vesselTrack = mutableListOf(vesselPosition)

    fun reset() {
        routeIndex = 1
        vesselPosition = routeWaypoints.first().position
        headingTrue = GeoMath.bearingDegrees(routeWaypoints[0].position, routeWaypoints[1].position)
        vesselTrack.clear()
        vesselTrack += vesselPosition
    }

    fun currentRoute(): List<GeoPoint> = routeWaypoints.map { it.position }

    fun tick(settings: SimulatorSettings, timestampMillis: Long = System.currentTimeMillis()): NavigationSnapshot {
        val fromWaypoint = routeWaypoints[routeIndex - 1]
        val toWaypoint = routeWaypoints[routeIndex]
        val trackBearing = GeoMath.bearingDegrees(fromWaypoint.position, toWaypoint.position)
        val projected = GeoMath.projectToSegment(vesselPosition, fromWaypoint.position, toWaypoint.position)
        val signedXte = GeoMath.crossTrackErrorNm(vesselPosition, fromWaypoint.position, toWaypoint.position)
        val correctedXte = signedXte + settings.injectedDeviationNm

        val correctionAngle = (-correctedXte * 22.0).coerceIn(-12.0, 12.0)
        headingTrue = GeoMath.normalizeDegrees(trackBearing + correctionAngle)

        val secondsPerTick = 1.0 / settings.updateRateHz
        val distancePerTick = settings.speedKnots * secondsPerTick / 3600.0
        val basePosition = GeoMath.move(projected, headingTrue, distancePerTick)
        vesselPosition = if (settings.injectedDeviationNm.absoluteValue > 0.001) {
            val routeNormalBearing = GeoMath.normalizeDegrees(trackBearing + if (settings.injectedDeviationNm >= 0) 90.0 else -90.0)
            GeoMath.move(basePosition, routeNormalBearing, settings.injectedDeviationNm * 0.08)
        } else {
            basePosition
        }

        val distanceToWaypoint = GeoMath.distanceNm(vesselPosition, toWaypoint.position)
        if (distanceToWaypoint < 0.03 && routeIndex < routeWaypoints.lastIndex) {
            routeIndex += 1
        }

        vesselTrack += vesselPosition
        if (vesselTrack.size > 500) {
            vesselTrack.removeAt(0)
        }

        val activeWaypoint = routeWaypoints[routeIndex]
        return NavigationSnapshot(
            position = vesselPosition,
            headingTrue = headingTrue,
            speedKnots = settings.speedKnots,
            crossTrackErrorNm = GeoMath.crossTrackErrorNm(vesselPosition, fromWaypoint.position, toWaypoint.position),
            bearingToWaypoint = GeoMath.bearingDegrees(vesselPosition, activeWaypoint.position),
            distanceToWaypointNm = GeoMath.distanceNm(vesselPosition, activeWaypoint.position),
            currentWaypoint = activeWaypoint,
            route = currentRoute(),
            vesselTrack = vesselTrack.toList(),
            timestampMillis = timestampMillis
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
