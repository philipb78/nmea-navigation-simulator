package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.simulation.GeoMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.absoluteValue

class NmeaGenerator {
    private val utcTimeFormat = SimpleDateFormat("HHmmss.SS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val utcDateFormat = SimpleDateFormat("ddMMyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun generate(snapshot: NavigationSnapshot): List<String> {
        return listOf(
            gpApb(snapshot),
            gpXte(snapshot),
            gpRmc(snapshot),
            gpGga(snapshot),
            gpVtg(snapshot)
        )
    }

    private fun gpApb(snapshot: NavigationSnapshot): String {
        val xteMagnitude = "%.2f".format(Locale.US, snapshot.crossTrackErrorNm.absoluteValue)
        val steerDirection = if (snapshot.crossTrackErrorNm >= 0) "L" else "R"
        val bearing = "%.1f".format(Locale.US, snapshot.bearingToWaypoint)
        val heading = "%.1f".format(Locale.US, snapshot.headingTrue)
        val destination = snapshot.currentWaypoint.name
        return sentence(
            "GPAPB",
            "A",
            "A",
            xteMagnitude,
            steerDirection,
            "N",
            "V",
            "V",
            bearing,
            "T",
            destination,
            heading,
            "T",
            bearing,
            "T"
        )
    }

    private fun gpXte(snapshot: NavigationSnapshot): String {
        val xteMagnitude = "%.2f".format(Locale.US, snapshot.crossTrackErrorNm.absoluteValue)
        val steerDirection = if (snapshot.crossTrackErrorNm >= 0) "L" else "R"
        return sentence("GPXTE", "A", "A", xteMagnitude, steerDirection, "N")
    }

    private fun gpRmc(snapshot: NavigationSnapshot): String {
        val date = Date(snapshot.timestampMillis)
        return sentence(
            "GPRMC",
            utcTimeFormat.format(date),
            "A",
            latitude(snapshot.position),
            latitudeHemisphere(snapshot.position),
            longitude(snapshot.position),
            longitudeHemisphere(snapshot.position),
            "%.2f".format(Locale.US, snapshot.speedKnots),
            "%.1f".format(Locale.US, snapshot.headingTrue),
            utcDateFormat.format(date),
            "",
            "",
            "A"
        )
    }

    private fun gpGga(snapshot: NavigationSnapshot): String {
        val date = Date(snapshot.timestampMillis)
        return sentence(
            "GPGGA",
            utcTimeFormat.format(date),
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

    private fun gpVtg(snapshot: NavigationSnapshot): String {
        val speedKmh = snapshot.speedKnots * 1.852
        return sentence(
            "GPVTG",
            "%.1f".format(Locale.US, snapshot.headingTrue),
            "T",
            "",
            "M",
            "%.2f".format(Locale.US, snapshot.speedKnots),
            "N",
            "%.2f".format(Locale.US, speedKmh),
            "K",
            "A"
        )
    }

    private fun latitude(point: GeoPoint): String {
        val value = point.latitude.absoluteValue
        val degrees = value.toInt()
        val minutes = (value - degrees) * 60.0
        return "%02d%06.3f".format(Locale.US, degrees, minutes)
    }

    private fun longitude(point: GeoPoint): String {
        val value = point.longitude.absoluteValue
        val degrees = value.toInt()
        val minutes = (value - degrees) * 60.0
        return "%03d%06.3f".format(Locale.US, degrees, minutes)
    }

    private fun latitudeHemisphere(point: GeoPoint): String =
        GeoMath.directionLetter(point.latitude, "N", "S")

    private fun longitudeHemisphere(point: GeoPoint): String =
        GeoMath.directionLetter(-point.longitude, "W", "E")

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
