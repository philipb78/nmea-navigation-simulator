package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.SimulatorSettings
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaGeneratorTest {
    private val generator = NmeaGenerator()
    private val noAis = SimulatorSettings(emitAis = false)

    @Test
    fun `generated sentences have valid checksums`() {
        val sentences = generator.generate(snapshot(), noAis)

        assertEquals(16, sentences.size)
        sentences.forEach { sentence ->
            assertTrue(sentence.startsWith("\$") || sentence.startsWith("!"))
            val body = sentence.substring(1).substringBefore("*")
            val expectedChecksum = body.fold(0) { acc, c -> acc xor c.code }
            val actualChecksum = sentence.substringAfter("*")
            assertEquals("%02X".format(expectedChecksum), actualChecksum)
        }
    }

    @Test
    fun `apb sentence sanitizes waypoint ids and uses track bearing`() {
        val apb = generator.generate(snapshot(waypointName = "WP,*\$7"), noAis)[0]

        assertTrue(apb.contains(",090.0,T,WP7,093.4,T,090.0,T,A*"))
        assertFalse(apb.contains("*\$7"))
    }

    @Test
    fun `rmc sentence uses west hemisphere for negative longitude`() {
        val rmc = generator.generate(snapshot(position = GeoPoint(50.5, -1.25)), noAis)[2]

        assertTrue(rmc.contains(",N,00115.000,W,"))
    }

    @Test
    fun `vhw sentence contains true heading and speed`() {
        val vhw = sentenceOf(generator.generate(snapshot(), noAis), "GPVHW")

        assertTrue(vhw.contains(",91.2,T,95.2,M,"))
        assertTrue(vhw.contains(",12.50,N,"))
    }

    @Test
    fun `rmb sentence contains xte steer direction origin dest waypoints bearing and arrival status`() {
        val rmb = sentenceOf(generator.generate(snapshot(), noAis), "GPRMB")

        assertTrue(rmb.startsWith("\$GPRMB,"))
        assertTrue(rmb.contains(",A,0.15,L,"))   // status A, XTE 0.15 steer L
        assertTrue(rmb.contains(",WP00,WP01,"))  // origin WP00 → destination WP01
        assertTrue(rmb.contains(",93.4,"))        // bearing to waypoint
        assertTrue(rmb.contains(",V*"))           // not yet arrived
    }

    @Test
    fun `rmb sentence uses destination waypoint coordinates`() {
        val rmb = sentenceOf(generator.generate(snapshot(), noAis), "GPRMB")

        // Destination is GeoPoint(50.51, -1.20) → 5030.600,N,00112.000,W
        assertTrue(rmb.contains(",5030.600,N,00112.000,W,"))
    }

    @Test
    fun `rmb arrival flag is A when within 0_02nm of waypoint`() {
        val rmb = sentenceOf(generator.generate(snapshot(distanceToWaypoint = 0.01), noAis), "GPRMB")

        assertTrue(rmb.contains(",A*"))
    }

    @Test
    fun `marine sensor sentences include wind depth speed rudder heading and temperature`() {
        val sentences = generator.generate(snapshot(), noAis)

        assertTrue(sentenceOf(sentences, "WIMWV").contains(",164.6,R,23.6,N,A*"))
        assertTrue(sentenceOf(sentences, "SDDBT").startsWith("\$SDDBT,26.2,f,8.0,M,4.4,F*"))
        assertEquals(listOf("8.0", "0.0", ""), parsedFields(sentenceOf(sentences, "SDDPT")))
        assertEquals(listOf("12.50", "0.00", "A", "13.10", "0.00", "A", "", "", "", ""), parsedFields(sentenceOf(sentences, "IIVBW")))
        assertTrue(sentenceOf(sentences, "IIRSA").startsWith("\$IIRSA,0.0,A,,*"))
        assertTrue(sentenceOf(sentences, "HCHDT").startsWith("\$HCHDT,91.2,T*"))
        assertEquals(listOf("95.2", "", "", "4.0", "W"), parsedFields(sentenceOf(sentences, "HCHDG")))
        assertTrue(sentenceOf(sentences, "YCMTW").startsWith("\$YCMTW,14.0,C*"))
        assertTrue(sentenceOf(sentences, "GPGLL").contains(",A,A*"))
    }

    @Test
    fun `depth sentences use snapshot depth in meters feet and fathoms as DBT`() {
        val sentences = generator.generate(snapshot(depthMeters = 12.3), noAis)

        assertTrue(sentenceOf(sentences, "SDDBT").startsWith("\$SDDBT,40.4,f,12.3,M,6.7,F*"))
        assertEquals(listOf("12.3", "0.0", ""), parsedFields(sentenceOf(sentences, "SDDPT")))
    }

    @Test
    fun `rsa uses rudder angle and can emit status V`() {
        val valid = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = false, rudderAngleDegrees = -12.5)
        )
        val invalid = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = false, rudderAngleDegrees = 8.0, rsaStatusInvalid = true)
        )

        assertTrue(sentenceOf(valid, "IIRSA").startsWith("\$IIRSA,-12.5,A,,*"))
        assertTrue(sentenceOf(invalid, "IIRSA").startsWith("\$IIRSA,8.0,V,,*"))
    }

    @Test
    fun `mwv can emit status V`() {
        val sentences = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = false, mwvStatusInvalid = true)
        )

        assertTrue(sentenceOf(sentences, "WIMWV").contains(",164.6,R,23.6,N,V*"))
    }

    @Test
    fun `generated sentences use expected field counts`() {
        val sentences = generator.generate(snapshot(), noAis)
        val expectedFieldCounts = mapOf(
            "GPAPB" to 15,
            "GPXTE" to 5,
            "GPRMC" to 12,
            "GPGGA" to 14,
            "GPGLL" to 7,
            "GPVTG" to 9,
            "GPVHW" to 8,
            "GPRMB" to 13,
            "WIMWV" to 5,
            "SDDBT" to 6,
            "SDDPT" to 3,
            "IIVBW" to 10,
            "IIRSA" to 4,
            "HCHDT" to 2,
            "HCHDG" to 5,
            "YCMTW" to 2
        )

        sentences.forEach { sentence ->
            val type = parsedType(sentence)
            assertEquals("Unexpected field count for $type", expectedFieldCounts.getValue(type), parsedFields(sentence).size)
        }
    }

    @Test
    fun `rmc and vtg use course and speed over ground`() {
        val sentences = generator.generate(snapshot(), noAis)

        assertTrue(sentenceOf(sentences, "GPRMC").contains(",13.10,094.0,"))
        assertTrue(sentenceOf(sentences, "GPVTG").contains("\$GPVTG,094.0,T,098.0,M,13.10,N,24.26,K,A*"))
    }

    @Test
    fun `coordinate rounding rolls minutes into degrees`() {
        val rmc = generator.generate(snapshot(position = GeoPoint(12.9999999, 179.9999999)), noAis)[2]

        assertTrue(rmc.contains(",1300.000,N,18000.000,E,"))
        assertFalse(rmc.contains("1260.000"))
        assertFalse(rmc.contains("17960.000"))
    }

    @Test
    fun `ais emission includes class A class B and type 5 fragments`() {
        val sentences = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = true, emitAivdo = false)
        )

        val aivdm = sentences.filter { it.startsWith("!AIVDM,") }
        assertTrue(aivdm.size >= 3)
        assertTrue(aivdm.any { it.startsWith("!AIVDM,1,1,,A,") })
        assertTrue(aivdm.any { it.startsWith("!AIVDM,2,1,") })
        assertTrue(aivdm.any { it.startsWith("!AIVDM,2,2,") })
        aivdm.forEach { sentence ->
            val body = sentence.substring(1).substringBefore("*")
            val expectedChecksum = body.fold(0) { acc, c -> acc xor c.code }
            assertEquals("%02X".format(expectedChecksum), sentence.substringAfter("*"))
        }
    }

    @Test
    fun `optional aivdo emits own ship type 1`() {
        val sentences = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = true, emitAivdo = true)
        )

        assertTrue(sentences.any { it.startsWith("!AIVDO,1,1,,A,") })
    }

    @Test
    fun `ais can be disabled`() {
        val sentences = generator.generate(snapshot(), noAis)

        assertFalse(sentences.any { it.startsWith("!AIVDM") || it.startsWith("!AIVDO") })
    }

    @Test
    fun `hdg uses magnetic heading and variation so it differs from hdt`() {
        val sentences = generator.generate(snapshot(), noAis)

        assertEquals(listOf("91.2", "T"), parsedFields(sentenceOf(sentences, "HCHDT")))
        assertEquals(listOf("95.2", "", "", "4.0", "W"), parsedFields(sentenceOf(sentences, "HCHDG")))
        assertTrue(sentenceOf(sentences, "GPRMC").contains(",4.0,W,A*"))
    }

    @Test
    fun `gps loss voids rmc gga and gll`() {
        val sentences = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = false, gpsFixInvalid = true)
        )

        assertEquals("V", parsedFields(sentenceOf(sentences, "GPRMC"))[1])
        assertEquals("N", parsedFields(sentenceOf(sentences, "GPRMC")).last())
        assertEquals("0", parsedFields(sentenceOf(sentences, "GPGGA"))[5])
        assertEquals(listOf("V", "N"), parsedFields(sentenceOf(sentences, "GPGLL")).takeLast(2))
        assertEquals("N", parsedFields(sentenceOf(sentences, "GPVTG")).last())
    }

    @Test
    fun `blank depth emits empty dbt and dpt values`() {
        val sentences = generator.generate(
            snapshot(),
            SimulatorSettings(emitAis = false, depthFieldsBlank = true)
        )

        assertEquals(listOf("", "f", "", "M", "", "F"), parsedFields(sentenceOf(sentences, "SDDBT")))
        assertEquals(listOf("", "0.0", ""), parsedFields(sentenceOf(sentences, "SDDPT")))
    }

    private fun snapshot(
        position: GeoPoint = GeoPoint(50.5, -1.25),
        waypointName: String = "WP01",
        distanceToWaypoint: Double = 1.2,
        depthMeters: Double = 8.0
    ): NavigationSnapshot {
        return NavigationSnapshot(
            position = position,
            headingTrue = 91.2,
            trackBearingTrue = 90.0,
            speedKnots = 12.5,
            speedThroughWaterKnots = 12.5,
            speedOverGroundKnots = 13.1,
            courseOverGroundTrue = 94.0,
            crossTrackErrorNm = 0.15,
            bearingToWaypoint = 93.4,
            distanceToWaypointNm = distanceToWaypoint,
            windDirectionTrue = 240.0,
            windSpeedKnots = 12.0,
            depthMeters = depthMeters,
            waterTemperatureCelsius = 14.0,
            currentDirectionTrue = 90.0,
            currentSpeedKnots = 0.6,
            previousWaypoint = Waypoint("WP00", GeoPoint(50.49, -1.30)),
            currentWaypoint = Waypoint(waypointName, GeoPoint(50.51, -1.20)),
            route = listOf(position, GeoPoint(50.51, -1.20)),
            vesselTrack = listOf(position),
            timestampMillis = 1_720_000_000_000L
        )
    }

    private fun sentenceOf(sentences: List<String>, type: String): String {
        return sentences.first { parsedType(it) == type }
    }

    private fun parsedType(sentence: String): String {
        return sentence.substring(1).substringBefore("*").substringBefore(",")
    }

    private fun parsedFields(sentence: String): List<String> {
        val body = sentence.substring(1).substringBefore("*")
        return body.substringAfter(",").split(",")
    }
}
