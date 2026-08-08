package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class AisEncoderTest {
    @Test
    fun `type 1 payload is 168 bits and six-bit encodes cleanly`() {
        val bits = AisEncoder.encodeType1ClassA(
            mmsi = 257000001,
            position = GeoPoint(50.5, -1.25),
            sogKnots = 8.0,
            cogDegrees = 90.0,
            headingDegrees = 91.0
        )
        assertEquals(21, bits.size)

        val (payload, fillBits) = AisEncoder.toSixBitPayload(bits)
        assertEquals(0, fillBits)
        assertEquals(28, payload.length)
        assertEquals(1, decodeMessageType(payload))
    }

    @Test
    fun `type 18 payload is 168 bits`() {
        val bits = AisEncoder.encodeType18ClassB(
            mmsi = 257000002,
            position = GeoPoint(50.5, -1.25),
            sogKnots = 5.0,
            cogDegrees = 100.0,
            headingDegrees = 105.0
        )
        val (payload, fillBits) = AisEncoder.toSixBitPayload(bits)
        assertEquals(0, fillBits)
        assertEquals(28, payload.length)
        assertEquals(18, decodeMessageType(payload))
    }

    @Test
    fun `type 5 payload splits into two aivdm fragments`() {
        val bits = AisEncoder.encodeType5Static(
            mmsi = 257000001,
            vesselName = "N2K CLASS A"
        )
        assertEquals(53, bits.size)

        val (payload, fillBits) = AisEncoder.toSixBitPayload(bits)
        assertEquals(2, fillBits)
        assertEquals(71, payload.length)
        assertEquals(5, decodeMessageType(payload))

        val fragments = AisEncoder.splitAivdmPayload(payload, fillBits)
        assertEquals(2, fragments.size)
        assertEquals(60, fragments[0].first.length)
        assertEquals(0, fragments[0].second)
        assertEquals(11, fragments[1].first.length)
        assertEquals(2, fragments[1].second)
    }

    @Test
    fun `six bit ascii mapping matches ITU table`() {
        assertEquals('0', AisEncoder.sixBitToAscii(0))
        assertEquals('W', AisEncoder.sixBitToAscii(39))
        assertEquals('`', AisEncoder.sixBitToAscii(40))
        assertEquals('w', AisEncoder.sixBitToAscii(63))
    }

    private fun decodeMessageType(payload: String): Int {
        val first = payload.first().code
        val value = if (first > 88) first - 56 else first - 48
        return (value and 0x3F) shr 0 // top 6 bits of first char are message type for aligned payload
    }
}
