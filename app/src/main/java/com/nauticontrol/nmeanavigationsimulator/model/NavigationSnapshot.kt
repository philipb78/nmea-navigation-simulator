package com.nauticontrol.nmeanavigationsimulator.model

data class NavigationSnapshot(
    val position: GeoPoint,
    val headingTrue: Double,
    val speedKnots: Double,
    val crossTrackErrorNm: Double,
    val bearingToWaypoint: Double,
    val distanceToWaypointNm: Double,
    val currentWaypoint: Waypoint,
    val route: List<GeoPoint>,
    val vesselTrack: List<GeoPoint>,
    val timestampMillis: Long
)
