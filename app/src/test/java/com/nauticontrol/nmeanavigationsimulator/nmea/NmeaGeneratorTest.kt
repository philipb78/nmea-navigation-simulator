package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import com.nauticontrol.nmeanavigationsimulator.model.NavigationSnapshot
import com.nauticontrol.nmeanavigationsimulator.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaGeneratorTest {
    private val generator = NmeaGenerator()

    @Test
    fun `generated sentences have valid checksums`() {
        val sentences = generator.generate(snapshot())

        assertEquals(15, sentences.size)
        sentences.forEach { sentence ->
            assertTrue(sentence.startsWith("\$"))
            val body = sentence.substringAfter("\$").substringBefore("*")
            val expectedChecksum = body.fold(0) { acc, c -> acc xor c.code }
            val actualChecksum = sentence.substringAfter("*")
            assertEquals("%02X".format(expectedChecksum), actualChecksum)
        }
    }

    @Test
    fun `apb sentence sanitizes waypoint ids and uses track bearing`() {
        val apb = generator.generate(snapshot(waypointName = "WP,*\$7"))[0]

        assertTrue(apb.contains(",090.0,T,WP7,093.4,T,090.0,T,A*"))
        assertFalse(apb.contains("*\$7"))
    }

    @Test
    fun `rmc sentence uses west hemisphere for negative longitude`() {
        val rmc = generator.generate(snapshot(position = GeoPoint(50.5, -1.25)))[2]

        assertTrue(rmc.contains(",N,00115.000,W,"))
    }

    @Test
    fun `vhw sentence contains true heading and speed`() {
        val vhw = generator.generate(snapshot())[5]

        assertTrue(vhw.startsWith("\$GPVHW,"))
        assertTrue(vhw.contains(",91.2,T,"))
        assertTrue(vhw.contains(",12.50,N,"))
    }

    @Test
    fun `rmb sentence contains xte steer direction origin dest waypoints bearing and arrival status`() {
        val rmb = generator.generate(snapshot())[6]

        assertTrue(rmb.startsWith("\$GPRMB,"))
        assertTrue(rmb.contains(",A,0.15,L,"))   // status A, XTE 0.15 steer L
        assertTrue(rmb.contains(",WP00,WP01,"))  // origin WP00 → destination WP01
        assertTrue(rmb.contains(",93.4,"))        // bearing to waypoint
        assertTrue(rmb.contains(",V*"))           // not yet arrived
    }

    @Test
    fun `rmb sentence uses destination waypoint coordinates`() {
        val rmb = generator.generate(snapshot())[6]

        // Destination is GeoPoint(50.51, -1.20) → 5030.600,N,00112.000,W
        assertTrue(rmb.contains(",5030.600,N,00112.000,W,"))
    }

    @Test
    fun `rmb arrival flag is A when within 0_02nm of waypoint`() {
        val rmb = generator.generate(snapshot(distanceToWaypoint = 0.01))[6]

        assertTrue(rmb.contains(",A*"))
    }

    @Test
    fun `marine sensor sentences include wind depth speed heading and temperature`() {
        val sentences = generator.generate(snapshot())

        assertTrue(sentences[7].startsWith("\$WIMWV,"))
        assertTrue(sentences[7].contains(",12.0,N,A*"))
        assertEquals(listOf("240.0", "T", "", "M", "12.0", "N", "6.2", "M"), parsedFields(sentences[8]))
        assertTrue(sentences[9].startsWith("\$SDDBD,26.2,f,8.0,M,4.4,F*"))
        assertEquals(listOf("8.0", "0.0", ""), parsedFields(sentences[10]))
        assertEquals(listOf("12.50", "0.00", "A", "13.10", "0.00", "A", "", "", "", ""), parsedFields(sentences[11]))
        assertTrue(sentences[12].startsWith("\$HCHDT,91.2,T*"))
        assertEquals(listOf("91.2", "", "", "", ""), parsedFields(sentences[13]))
        assertTrue(sentences[14].startsWith("\$YCMTW,14.0,C*"))
    }

    @Test
    fun `depth sentences use snapshot depth in meters feet and fathoms`() {
        val sentences = generator.generate(snapshot(depthMeters = 12.3))

        assertTrue(sentences[9].startsWith("\$SDDBD,40.4,f,12.3,M,6.7,F*"))
        assertEquals(listOf("12.3", "0.0", ""), parsedFields(sentences[10]))
    }

    @Test
    fun `generated sentences use expected field counts`() {
        val sentences = generator.generate(snapshot())
        val expectedFieldCounts = mapOf(
            "GPAPB" to 15,
            "GPXTE" to 5,
            "GPRMC" to 12,
            "GPGGA" to 14,
            "GPVTG" to 9,
            "GPVHW" to 8,
            "GPRMB" to 13,
            "WIMWV" to 5,
            "WIMWD" to 8,
            "SDDBD" to 6,
            "SDDPT" to 3,
            "IIVBW" to 10,
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
        val sentences = generator.generate(snapshot())

        assertTrue(sentences[2].contains(",13.10,094.0,"))
        assertTrue(sentences[4].contains("\$GPVTG,094.0,T,,M,13.10,N,24.26,K,A*"))
    }

    @Test
    fun `coordinate rounding rolls minutes into degrees`() {
        val rmc = generator.generate(snapshot(position = GeoPoint(12.9999999, 179.9999999)))[2]

        assertTrue(rmc.contains(",1300.000,N,18000.000,E,"))
        assertFalse(rmc.contains("1260.000"))
        assertFalse(rmc.contains("17960.000"))
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

    private fun parsedType(sentence: String): String {
        return sentence.substringAfter("\$").substringBefore("*").substringBefore(",")
    }

    private fun parsedFields(sentence: String): List<String> {
        val body = sentence.substringAfter("\$").substringBefore("*")
        return body.substringAfter(",").split(",")
    }
}
