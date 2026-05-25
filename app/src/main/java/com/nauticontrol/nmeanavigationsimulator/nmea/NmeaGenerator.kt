package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.simulation.GeoMath
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

class NmeaGenerator {
    private val utcTimeFormat = DateTimeFormatter.ofPattern("HHmmss.SS", Locale.US)
        .withZone(ZoneOffset.UTC)
    private val utcDateFormat = DateTimeFormatter.ofPattern("ddMMyy", Locale.US)
        .withZone(ZoneOffset.UTC)

    fun generate(snapshot: NavigationSnapshot): List<String> {
        return listOf(
            gpApb(snapshot),
            gpXte(snapshot),
            gpRmc(snapshot),
            gpGga(snapshot),
            gpVtg(snapshot),
            gpVhw(snapshot),
            gpRmb(snapshot),
            wiMwv(snapshot),
            wiMwd(snapshot),
            sdDbd(snapshot),
            sdDpt(snapshot),
            iiVbw(snapshot),
            hcHdt(snapshot),
            hcHdg(snapshot),
            ycMtw(snapshot)
        )
    }

    private fun gpApb(snapshot: NavigationSnapshot): String {
        val xteMagnitude = "%.2f".format(Locale.US, snapshot.crossTrackErrorNm.absoluteValue)
        val steerDirection = if (snapshot.crossTrackErrorNm >= 0) "L" else "R"
        val bearingToWaypoint = "%.1f".format(Locale.US, snapshot.bearingToWaypoint)
        val bearingOriginToDestination = "%.1f".format(Locale.US, snapshot.trackBearingTrue)
        val headingToSteer = "%.1f".format(Locale.US, snapshot.trackBearingTrue)
        val destination = safeField(snapshot.currentWaypoint.name).take(6)
        val arrivalStatus = if (snapshot.distanceToWaypointNm <= 0.02) "A" else "V"
        return sentence(
            "GPAPB",
            "A",
            "A",
            xteMagnitude,
            steerDirection,
            "N",
            arrivalStatus,
            arrivalStatus,
            bearingOriginToDestination,
            "T",
            destination,
            bearingToWaypoint,
            "T",
            headingToSteer,
            "T",
            "A"
        )
    }

    private fun gpXte(snapshot: NavigationSnapshot): String {
        val xteMagnitude = "%.2f".format(Locale.US, snapshot.crossTrackErrorNm.absoluteValue)
        val steerDirection = if (snapshot.crossTrackErrorNm >= 0) "L" else "R"
        return sentence("GPXTE", "A", "A", xteMagnitude, steerDirection, "N")
    }

    private fun gpRmc(snapshot: NavigationSnapshot): String {
        val instant = Instant.ofEpochMilli(snapshot.timestampMillis)
        return sentence(
            "GPRMC",
            utcTimeFormat.format(instant),
            "A",
            latitude(snapshot.position),
            latitudeHemisphere(snapshot.position),
            longitude(snapshot.position),
            longitudeHemisphere(snapshot.position),
            "%.2f".format(Locale.US, snapshot.speedOverGroundKnots),
            "%.1f".format(Locale.US, snapshot.courseOverGroundTrue),
            utcDateFormat.format(instant),
            "",
            "",
            "A"
        )
    }

    private fun gpGga(snapshot: NavigationSnapshot): String {
        val instant = Instant.ofEpochMilli(snapshot.timestampMillis)
        return sentence(
            "GPGGA",
            utcTimeFormat.format(instant),
            latitude(snapshot.position),
            latitudeHemisphere(snapshot.position),
            longitude(snapshot.position),
            longitudeHemisphere(snapshot.position),
            "1",
            "10",
            "0.9",
            "5.0",
            "M",
            "47.0",
            "M",
            "",
            ""
        )
    }

    private fun gpVhw(snapshot: NavigationSnapshot): String {
        val speedKmh = snapshot.speedThroughWaterKnots * 1.852
        return sentence(
            "GPVHW",
            "%.1f".format(Locale.US, snapshot.headingTrue), "T",
            "", "M",
            "%.2f".format(Locale.US, snapshot.speedThroughWaterKnots), "N",
            "%.2f".format(Locale.US, speedKmh), "K"
        )
    }

    private fun gpRmb(snapshot: NavigationSnapshot): String {
        val xteMagnitude = "%.2f".format(Locale.US, snapshot.crossTrackErrorNm.absoluteValue)
        val steerDirection = if (snapshot.crossTrackErrorNm >= 0) "L" else "R"
        val origin = safeField(snapshot.previousWaypoint.name).take(6)
        val destination = safeField(snapshot.currentWaypoint.name).take(6)
        val range = "%.3f".format(Locale.US, snapshot.distanceToWaypointNm)
        val bearing = "%.1f".format(Locale.US, snapshot.bearingToWaypoint)
        val closingVelocity = "%.1f".format(Locale.US, snapshot.speedOverGroundKnots)
        val arrivalStatus = if (snapshot.distanceToWaypointNm <= 0.02) "A" else "V"
        return sentence(
            "GPRMB",
            "A",
            xteMagnitude, steerDirection,
            origin, destination,
            latitude(snapshot.currentWaypoint.position),
            latitudeHemisphere(snapshot.currentWaypoint.position),
            longitude(snapshot.currentWaypoint.position),
            longitudeHemisphere(snapshot.currentWaypoint.position),
            range, bearing,
            closingVelocity,
            arrivalStatus
        )
    }

    private fun gpVtg(snapshot: NavigationSnapshot): String {
        val speedKmh = snapshot.speedOverGroundKnots * 1.852
        return sentence(
            "GPVTG",
            "%.1f".format(Locale.US, snapshot.courseOverGroundTrue),
            "T",
            "",
            "M",
            "%.2f".format(Locale.US, snapshot.speedOverGroundKnots),
            "N",
            "%.2f".format(Locale.US, speedKmh),
            "K",
            "A"
        )
    }

    private fun wiMwv(snapshot: NavigationSnapshot): String {
        val relativeWindAngle = GeoMath.normalizeDegrees(snapshot.windDirectionTrue - snapshot.headingTrue)
        return sentence(
            "WIMWV",
            "%.1f".format(Locale.US, relativeWindAngle),
            "R",
            "%.1f".format(Locale.US, snapshot.windSpeedKnots),
            "N",
            "A"
        )
    }

    private fun wiMwd(snapshot: NavigationSnapshot): String {
        val speedMetersPerSecond = snapshot.windSpeedKnots * 0.514444
        return sentence(
            "WIMWD",
            "%.1f".format(Locale.US, snapshot.windDirectionTrue),
            "T",
            "%.1f".format(Locale.US, snapshot.windDirectionTrue),
            "M",
            "%.1f".format(Locale.US, snapshot.windSpeedKnots),
            "N",
            "%.1f".format(Locale.US, speedMetersPerSecond),
            "M"
        )
    }

    private fun sdDbd(snapshot: NavigationSnapshot): String {
        val depthFeet = snapshot.depthMeters * 3.28084
        val depthFathoms = snapshot.depthMeters * 0.546807
        return sentence(
            "SDDBD",
            "%.1f".format(Locale.US, depthFeet), "f",
            "%.1f".format(Locale.US, snapshot.depthMeters), "M",
            "%.1f".format(Locale.US, depthFathoms), "F"
        )
    }

    private fun sdDpt(snapshot: NavigationSnapshot): String {
        return sentence(
            "SDDPT",
            "%.1f".format(Locale.US, snapshot.depthMeters),
            "0.0",
            "%.1f".format(Locale.US, snapshot.depthMeters)
        )
    }

    private fun iiVbw(snapshot: NavigationSnapshot): String {
        return sentence(
            "IIVBW",
            "%.2f".format(Locale.US, snapshot.speedThroughWaterKnots),
            "0.00",
            "A",
            "%.2f".format(Locale.US, snapshot.speedOverGroundKnots),
            "0.00",
            "A",
            "",
            "",
            "",
            "",
            "",
            ""
        )
    }

    private fun hcHdt(snapshot: NavigationSnapshot): String {
        return sentence("HCHDT", "%.1f".format(Locale.US, snapshot.headingTrue), "T")
    }

    private fun hcHdg(snapshot: NavigationSnapshot): String {
        return sentence(
            "HCHDG",
            "%.1f".format(Locale.US, snapshot.headingTrue),
            "0.0",
            "E",
            "0.0",
            "E"
        )
    }

    private fun ycMtw(snapshot: NavigationSnapshot): String {
        return sentence("YCMTW", "%.1f".format(Locale.US, snapshot.waterTemperatureCelsius), "C")
    }

    private fun latitude(point: GeoPoint): String {
        return coordinate(point.latitude.coerceIn(-90.0, 90.0).absoluteValue, degreeWidth = 2)
    }

    private fun longitude(point: GeoPoint): String {
        return coordinate(GeoMath.normalizeLongitude(point.longitude).absoluteValue, degreeWidth = 3)
    }

    private fun latitudeHemisphere(point: GeoPoint): String =
        GeoMath.directionLetter(point.latitude, "N", "S")

    private fun longitudeHemisphere(point: GeoPoint): String =
        if (GeoMath.normalizeLongitude(point.longitude) >= 0) "E" else "W"

    private fun coordinate(value: Double, degreeWidth: Int): String {
        val totalMinuteThousandths = (value * 60_000.0).roundToLong()
        val degrees = totalMinuteThousandths / 60_000
        val minutes = (totalMinuteThousandths % 60_000) / 1000.0
        return "%0${degreeWidth}d%06.3f".format(Locale.US, degrees, minutes)
    }

    private fun safeField(value: String): String {
        return value.filterNot { it == ',' || it == '*' || it == '$' || it.code < 32 }
    }

    private fun sentence(type: String, vararg fields: String): String {
        val body = buildString {
            append(type)
            fields.forEach {
                append(',')
                append(it)
            }
        }
        val checksum = body.fold(0) { acc, c -> acc xor c.code }
        return "\$${body}*%02X".format(Locale.US, checksum)
    }
}
