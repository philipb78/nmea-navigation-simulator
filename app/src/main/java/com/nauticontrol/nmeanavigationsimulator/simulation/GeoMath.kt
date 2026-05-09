package com.nauticontrol.nmeanavigationsimulator.simulation

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EarthRadiusNm = 3440.065
    private const val MetersPerNm = 1852.0

    fun distanceNm(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude.toRadians()
        val lon1 = a.longitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val lon2 = b.longitude.toRadians()
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EarthRadiusNm * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val dLon = (b.longitude - a.longitude).toRadians()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun move(point: GeoPoint, bearingDegrees: Double, distanceNm: Double): GeoPoint {
        val angularDistance = distanceNm / EarthRadiusNm
        val bearing = bearingDegrees.toRadians()
        val lat1 = point.latitude.toRadians()
        val lon1 = point.longitude.toRadians()
        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(angularDistance) +
                cos(lat1) * sin(angularDistance) * cos(bearing)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2)
        )
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    fun crossTrackErrorNm(position: GeoPoint, segmentStart: GeoPoint, segmentEnd: GeoPoint): Double {
        val distance13 = distanceNm(segmentStart, position) / EarthRadiusNm
        val bearing13 = bearingDegrees(segmentStart, position).toRadians()
        val bearing12 = bearingDegrees(segmentStart, segmentEnd).toRadians()
        return kotlin.math.asin(sin(distance13) * sin(bearing13 - bearing12)) * EarthRadiusNm
    }

    fun alongTrackRatio(position: GeoPoint, segmentStart: GeoPoint, segmentEnd: GeoPoint): Double {
        val sx = 0.0
        val sy = 0.0
        val ex = distanceEastNm(segmentStart, segmentEnd)
        val ey = distanceNorthNm(segmentStart, segmentEnd)
        val px = distanceEastNm(segmentStart, position)
        val py = distanceNorthNm(segmentStart, position)
        val dx = ex - sx
        val dy = ey - sy
        val denom = dx * dx + dy * dy
        if (denom == 0.0) return 0.0
        return min(1.0, maxOf(0.0, ((px - sx) * dx + (py - sy) * dy) / denom))
    }

    fun projectToSegment(position: GeoPoint, segmentStart: GeoPoint, segmentEnd: GeoPoint): GeoPoint {
        val ratio = alongTrackRatio(position, segmentStart, segmentEnd)
        val east = distanceEastNm(segmentStart, segmentEnd) * ratio
        val north = distanceNorthNm(segmentStart, segmentEnd) * ratio
        return offset(segmentStart, east, north)
    }

    fun offset(origin: GeoPoint, eastNm: Double, northNm: Double): GeoPoint {
        val lat = origin.latitude + Math.toDegrees((northNm * MetersPerNm) / 6_378_137.0)
        val lon = origin.longitude + Math.toDegrees(
            (eastNm * MetersPerNm) / (6_378_137.0 * cos(origin.latitude.toRadians()))
        )
        return GeoPoint(lat, lon)
    }

    fun distanceEastNm(origin: GeoPoint, point: GeoPoint): Double {
        val meanLat = ((origin.latitude + point.latitude) / 2.0).toRadians()
        return (point.longitude - origin.longitude).toRadians() * cos(meanLat) * EarthRadiusNm
    }

    fun distanceNorthNm(origin: GeoPoint, point: GeoPoint): Double {
        return (point.latitude - origin.latitude).toRadians() * EarthRadiusNm
    }

    fun normalizeDegrees(value: Double): Double {
        var normalized = value % 360.0
        if (normalized < 0) {
            normalized += 360.0
        }
        return normalized
    }

    fun shortestSignedAngleDegrees(from: Double, to: Double): Double {
        var delta = normalizeDegrees(to) - normalizeDegrees(from)
        if (delta > 180.0) {
            delta -= 360.0
        } else if (delta < -180.0) {
            delta += 360.0
        }
        return delta
    }

    fun round(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return round(value * factor) / factor
    }

    fun directionLetter(value: Double, positive: String, negative: String): String {
        return if (value >= 0) positive else negative
    }

    fun absRounded(value: Double, decimals: Int): Double = round(abs(value), decimals)

    private fun Double.toRadians(): Double = this / 180.0 * PI
}
