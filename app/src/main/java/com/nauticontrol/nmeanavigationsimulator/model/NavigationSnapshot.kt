package com.nauticontrol.nmeanavigationsimulator.model

data class NavigationSnapshot(
    val position: GeoPoint,
    val headingTrue: Double,
    val trackBearingTrue: Double,
    val speedKnots: Double,
    val speedThroughWaterKnots: Double,
    val speedOverGroundKnots: Double,
    val courseOverGroundTrue: Double,
    val crossTrackErrorNm: Double,
    val bearingToWaypoint: Double,
    val distanceToWaypointNm: Double,
    val windDirectionTrue: Double,
    val windSpeedKnots: Double,
    val depthMeters: Double,
    val waterTemperatureCelsius: Double,
    val currentDirectionTrue: Double,
    val currentSpeedKnots: Double,
    val previousWaypoint: Waypoint,
    val currentWaypoint: Waypoint,
    val route: List<GeoPoint>,
    val vesselTrack: List<GeoPoint>,
    val timestampMillis: Long
)
