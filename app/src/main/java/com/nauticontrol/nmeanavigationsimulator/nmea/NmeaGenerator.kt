package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.simulation.GeoMath
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

class NmeaGenerator {
    private val utcTimeFormat = DateTimeFormatter.ofPattern("HHmmss.SS", Locale.US)
        .withZone(ZoneOffset.UTC)
    private val utcDateFormat = DateTimeFormatter.ofPattern("ddMMyy", Locale.US)
        .withZone(ZoneOffset.UTC)

    private var lastType5EmitMillis: Long = Long.MIN_VALUE
    private var aisSequentialMessageId: Int = 0

    fun generate(
        snapshot: NavigationSnapshot,
        settings: SimulatorSettings = SimulatorSettings()
    ): List<String> {
        val sanitized = settings.sanitizedForNmea()
        val sentences = mutableListOf(
            gpApb(snapshot),
            gpXte(snapshot),
            gpRmc(snapshot, sanitized),
            gpGga(snapshot, sanitized),
            gpGll(snapshot, sanitized),
            gpVtg(snapshot, sanitized),
            gpVhw(snapshot, sanitized),
            gpRmb(snapshot),
            wiMwv(snapshot, sanitized),
            sdDbt(snapshot, sanitized),
            sdDpt(snapshot, sanitized),
            iiVbw(snapshot),
            iiRsa(sanitized),
            hcHdt(snapshot),
            hcHdg(snapshot, sanitized),
            ycMtw(snapshot)
        )
        if (sanitized.emitAis) {
            sentences += aisSentences(snapshot, sanitized)
        }
        return sentences
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

    private fun gpRmc(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        val instant = Instant.ofEpochMilli(snapshot.timestampMillis)
        val status = if (settings.gpsFixInvalid) "V" else "A"
        val mode = if (settings.gpsFixInvalid) "N" else "A"
        return sentence(
            "GPRMC",
            utcTimeFormat.format(instant),
            status,
            latitude(snapshot.position),
            latitudeHemisphere(snapshot.position),
            longitude(snapshot.position),
            longitudeHemisphere(snapshot.position),
            "%.2f".format(Locale.US, snapshot.speedOverGroundKnots),
            "%.1f".format(Locale.US, snapshot.courseOverGroundTrue),
            utcDateFormat.format(instant),
            variationMagnitude(settings.magneticVariationDegrees),
            variationHemisphere(settings.magneticVariationDegrees),
            mode
        )
    }

    private fun gpGga(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        val instant = Instant.ofEpochMilli(snapshot.timestampMillis)
        val fixQuality = if (settings.gpsFixInvalid) "0" else "1"
        return sentence(
            "GPGGA",
            utcTimeFormat.format(instant),
            latitude(snapshot.position),
            latitudeHemisphere(snapshot.position),
            longitude(snapshot.position),
            longitudeHemisphere(snapshot.position),
            fixQuality,
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

    private fun gpGll(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        val instant = Instant.ofEpochMilli(snapshot.timestampMillis)
        val status = if (settings.gpsFixInvalid) "V" else "A"
        val mode = if (settings.gpsFixInvalid) "N" else "A"
        return sentence(
            "GPGLL",
            latitude(snapshot.position),
            latitudeHemisphere(snapshot.position),
            longitude(snapshot.position),
            longitudeHemisphere(snapshot.position),
            utcTimeFormat.format(instant),
            status,
            mode
        )
    }

    private fun gpVhw(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        val speedKmh = snapshot.speedThroughWaterKnots * 1.852
        return sentence(
            "GPVHW",
            "%.1f".format(Locale.US, snapshot.headingTrue), "T",
            "%.1f".format(Locale.US, magneticDegrees(snapshot.headingTrue, settings.magneticVariationDegrees)), "M",
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

    private fun gpVtg(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        val speedKmh = snapshot.speedOverGroundKnots * 1.852
        val mode = if (settings.gpsFixInvalid) "N" else "A"
        return sentence(
            "GPVTG",
            "%.1f".format(Locale.US, snapshot.courseOverGroundTrue),
            "T",
            "%.1f".format(Locale.US, magneticDegrees(snapshot.courseOverGroundTrue, settings.magneticVariationDegrees)),
            "M",
            "%.2f".format(Locale.US, snapshot.speedOverGroundKnots),
            "N",
            "%.2f".format(Locale.US, speedKmh),
            "K",
            mode
        )
    }

    private fun wiMwv(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        val twdRad = Math.toRadians(snapshot.windDirectionTrue)
        val hdgRad = Math.toRadians(snapshot.headingTrue)
        val awe = snapshot.windSpeedKnots * sin(twdRad) - snapshot.speedThroughWaterKnots * sin(hdgRad)
        val awn = snapshot.windSpeedKnots * cos(twdRad) - snapshot.speedThroughWaterKnots * cos(hdgRad)
        val aws = sqrt(awe * awe + awn * awn)
        val awdTrue = GeoMath.normalizeDegrees(Math.toDegrees(atan2(awe, awn)))
        val awa = GeoMath.normalizeDegrees(awdTrue - snapshot.headingTrue)
        val status = if (settings.mwvStatusInvalid) "V" else "A"
        return sentence(
            "WIMWV",
            "%.1f".format(Locale.US, awa),
            "R",
            "%.1f".format(Locale.US, aws),
            "N",
            status
        )
    }

    private fun sdDbt(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        if (settings.depthFieldsBlank) {
            return sentence("SDDBT", "", "f", "", "M", "", "F")
        }
        val depthFeet = snapshot.depthMeters * 3.28084
        val depthFathoms = snapshot.depthMeters * 0.546807
        return sentence(
            "SDDBT",
            "%.1f".format(Locale.US, depthFeet), "f",
            "%.1f".format(Locale.US, snapshot.depthMeters), "M",
            "%.1f".format(Locale.US, depthFathoms), "F"
        )
    }

    private fun sdDpt(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        if (settings.depthFieldsBlank) {
            return sentence("SDDPT", "", "0.0", "")
        }
        return sentence(
            "SDDPT",
            "%.1f".format(Locale.US, snapshot.depthMeters),
            "0.0",
            ""
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
            ""
        )
    }

    private fun iiRsa(settings: SimulatorSettings): String {
        val status = if (settings.rsaStatusInvalid) "V" else "A"
        return sentence(
            "IIRSA",
            "%.1f".format(Locale.US, settings.rudderAngleDegrees),
            status,
            "",
            ""
        )
    }

    private fun hcHdt(snapshot: NavigationSnapshot): String {
        return sentence("HCHDT", "%.1f".format(Locale.US, snapshot.headingTrue), "T")
    }

    private fun hcHdg(snapshot: NavigationSnapshot, settings: SimulatorSettings): String {
        return sentence(
            "HCHDG",
            "%.1f".format(Locale.US, magneticDegrees(snapshot.headingTrue, settings.magneticVariationDegrees)),
            "",
            "",
            variationMagnitude(settings.magneticVariationDegrees),
            variationHemisphere(settings.magneticVariationDegrees)
        )
    }

    private fun ycMtw(snapshot: NavigationSnapshot): String {
        return sentence("YCMTW", "%.1f".format(Locale.US, snapshot.waterTemperatureCelsius), "C")
    }

    private fun aisSentences(snapshot: NavigationSnapshot, settings: SimulatorSettings): List<String> {
        val sentences = mutableListOf<String>()
        val classAPosition = GeoMath.move(snapshot.position, snapshot.headingTrue, 0.3)
        val classBPosition = GeoMath.move(
            snapshot.position,
            GeoMath.normalizeDegrees(snapshot.headingTrue + 90.0),
            0.5
        )
        val secondOfMinute = ((snapshot.timestampMillis / 1000L) % 60L).toInt()

        sentences += aivdm(
            AisEncoder.encodeType1ClassA(
                mmsi = CLASS_A_MMSI,
                position = classAPosition,
                sogKnots = (snapshot.speedOverGroundKnots * 0.9).coerceAtLeast(0.0),
                cogDegrees = snapshot.courseOverGroundTrue,
                headingDegrees = snapshot.headingTrue,
                timestampSeconds = secondOfMinute
            )
        )

        sentences += aivdm(
            AisEncoder.encodeType18ClassB(
                mmsi = CLASS_B_MMSI,
                position = classBPosition,
                sogKnots = (snapshot.speedOverGroundKnots * 0.7).coerceAtLeast(0.0),
                cogDegrees = GeoMath.normalizeDegrees(snapshot.courseOverGroundTrue + 15.0),
                headingDegrees = GeoMath.normalizeDegrees(snapshot.headingTrue + 15.0),
                timestampSeconds = secondOfMinute
            )
        )

        if (snapshot.timestampMillis - lastType5EmitMillis >= TYPE5_INTERVAL_MS || lastType5EmitMillis == Long.MIN_VALUE) {
            lastType5EmitMillis = snapshot.timestampMillis
            sentences += aivdm(
                AisEncoder.encodeType5Static(
                    mmsi = CLASS_A_MMSI,
                    vesselName = "N2K CLASS A",
                    callSign = "LA1234",
                    shipType = 70,
                    destination = "NAUTIC"
                )
            )
        }

        if (settings.emitAivdo) {
            sentences += aivdo(
                AisEncoder.encodeType1ClassA(
                    mmsi = OWN_SHIP_MMSI,
                    position = snapshot.position,
                    sogKnots = snapshot.speedOverGroundKnots,
                    cogDegrees = snapshot.courseOverGroundTrue,
                    headingDegrees = snapshot.headingTrue,
                    timestampSeconds = secondOfMinute
                )
            )
        }

        return sentences
    }

    private fun aivdm(payloadBits: ByteArray): List<String> {
        return encapsulatedSentences("AIVDM", payloadBits)
    }

    private fun aivdo(payloadBits: ByteArray): List<String> {
        return encapsulatedSentences("AIVDO", payloadBits)
    }

    private fun encapsulatedSentences(talkerSentence: String, payloadBits: ByteArray): List<String> {
        val (payload, fillBits) = AisEncoder.toSixBitPayload(payloadBits)
        val fragments = AisEncoder.splitAivdmPayload(payload, fillBits)
        val sequentialId = if (fragments.size > 1) {
            aisSequentialMessageId = (aisSequentialMessageId + 1) % 10
            aisSequentialMessageId.toString()
        } else {
            ""
        }
        return fragments.mapIndexed { index, (fragmentPayload, fragmentFill) ->
            encapsulatedSentence(
                talkerSentence,
                fragments.size.toString(),
                (index + 1).toString(),
                sequentialId,
                "A",
                fragmentPayload,
                fragmentFill.toString()
            )
        }
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

    private fun magneticDegrees(trueDegrees: Double, variationEastPositive: Double): Double {
        return GeoMath.normalizeDegrees(trueDegrees - variationEastPositive)
    }

    private fun variationMagnitude(variationEastPositive: Double): String {
        return "%.1f".format(Locale.US, variationEastPositive.absoluteValue)
    }

    private fun variationHemisphere(variationEastPositive: Double): String {
        return if (variationEastPositive >= 0.0) "E" else "W"
    }

    private fun safeField(value: String): String {
        return value.filterNot { it == ',' || it == '*' || it == '$' || it.code < 32 }
    }

    private fun sentence(type: String, vararg fields: String): String {
        return framedSentence('$', type, *fields)
    }

    private fun encapsulatedSentence(type: String, vararg fields: String): String {
        return framedSentence('!', type, *fields)
    }

    private fun framedSentence(start: Char, type: String, vararg fields: String): String {
        val body = buildString {
            append(type)
            fields.forEach {
                append(',')
                append(it)
            }
        }
        val checksum = body.fold(0) { acc, c -> acc xor c.code }
        return "$start${body}*%02X".format(Locale.US, checksum)
    }

    companion object {
        private const val CLASS_A_MMSI = 257000001
        private const val CLASS_B_MMSI = 257000002
        private const val OWN_SHIP_MMSI = 257000000
        private const val TYPE5_INTERVAL_MS = 30_000L
    }
}

private fun SimulatorSettings.sanitizedForNmea(): SimulatorSettings {
    return copy(
        rudderAngleDegrees = rudderAngleDegrees.coerceIn(-40.0, 40.0),
        magneticVariationDegrees = magneticVariationDegrees.coerceIn(-30.0, 30.0)
    )
}
