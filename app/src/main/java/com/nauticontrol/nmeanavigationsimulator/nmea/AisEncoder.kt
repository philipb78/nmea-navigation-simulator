package com.nauticontrol.nmeanavigationsimulator.nmea

import com.nauticontrol.nmeanavigationsimulator.model.GeoPoint
import kotlin.math.roundToInt

/**
 * Minimal AIS 6-bit payload encoder for message types 1, 5, and 18.
 */
object AisEncoder {
    private const val MaxPayloadCharsPerSentence = 60

    fun encodeType1ClassA(
        mmsi: Int,
        position: GeoPoint,
        sogKnots: Double,
        cogDegrees: Double,
        headingDegrees: Double,
        navigationStatus: Int = 0,
        timestampSeconds: Int = 60
    ): ByteArray {
        val bits = BitWriter(168)
        bits.writeUnsigned(1, 6)
        bits.writeUnsigned(0, 2)
        bits.writeUnsigned(mmsi, 30)
        bits.writeUnsigned(navigationStatus, 4)
        bits.writeUnsigned(0x80, 8) // ROT not available
        bits.writeUnsigned(encodeSog(sogKnots), 10)
        bits.writeUnsigned(0, 1) // position accuracy
        bits.writeSigned(encodeLongitude(position.longitude), 28)
        bits.writeSigned(encodeLatitude(position.latitude), 27)
        bits.writeUnsigned(encodeCog(cogDegrees), 12)
        bits.writeUnsigned(encodeHeading(headingDegrees), 9)
        bits.writeUnsigned(timestampSeconds.coerceIn(0, 63), 6)
        bits.writeUnsigned(0, 2) // maneuver
        bits.writeUnsigned(0, 3) // spare
        bits.writeUnsigned(0, 1) // RAIM
        bits.writeUnsigned(0, 19) // radio status
        return bits.toByteArray()
    }

    fun encodeType18ClassB(
        mmsi: Int,
        position: GeoPoint,
        sogKnots: Double,
        cogDegrees: Double,
        headingDegrees: Double,
        timestampSeconds: Int = 60
    ): ByteArray {
        val bits = BitWriter(168)
        bits.writeUnsigned(18, 6)
        bits.writeUnsigned(0, 2)
        bits.writeUnsigned(mmsi, 30)
        bits.writeUnsigned(0, 8) // spare
        bits.writeUnsigned(encodeSog(sogKnots), 10)
        bits.writeUnsigned(0, 1) // position accuracy
        bits.writeSigned(encodeLongitude(position.longitude), 28)
        bits.writeSigned(encodeLatitude(position.latitude), 27)
        bits.writeUnsigned(encodeCog(cogDegrees), 12)
        bits.writeUnsigned(encodeHeading(headingDegrees), 9)
        bits.writeUnsigned(timestampSeconds.coerceIn(0, 63), 6)
        bits.writeUnsigned(0, 2) // spare
        bits.writeUnsigned(1, 1) // Class B CS unit
        bits.writeUnsigned(0, 1) // display flag
        bits.writeUnsigned(0, 1) // DSC flag
        bits.writeUnsigned(0, 1) // band flag
        bits.writeUnsigned(0, 1) // message 22 flag
        bits.writeUnsigned(0, 1) // mode flag
        bits.writeUnsigned(0, 1) // RAIM
        bits.writeUnsigned(0, 1) // communication state selector
        bits.writeUnsigned(0, 19) // radio status
        return bits.toByteArray()
    }

    fun encodeType5Static(
        mmsi: Int,
        vesselName: String,
        callSign: String = "TEST1",
        shipType: Int = 37,
        draughtMeters: Double = 2.5,
        destination: String = "SIMPORT"
    ): ByteArray {
        val bits = BitWriter(424)
        bits.writeUnsigned(5, 6)
        bits.writeUnsigned(0, 2)
        bits.writeUnsigned(mmsi, 30)
        bits.writeUnsigned(0, 2) // AIS version
        bits.writeUnsigned(0, 30) // IMO
        bits.writeSixBitString(callSign, 7)
        bits.writeSixBitString(vesselName, 20)
        bits.writeUnsigned(shipType, 8)
        bits.writeUnsigned(20, 9) // to bow
        bits.writeUnsigned(10, 9) // to stern
        bits.writeUnsigned(4, 6) // to port
        bits.writeUnsigned(4, 6) // to starboard
        bits.writeUnsigned(1, 4) // position fix type GPS
        bits.writeUnsigned(0, 4) // ETA month
        bits.writeUnsigned(0, 5) // ETA day
        bits.writeUnsigned(24, 5) // ETA hour unavailable
        bits.writeUnsigned(60, 6) // ETA minute unavailable
        bits.writeUnsigned((draughtMeters * 10.0).roundToInt().coerceIn(0, 255), 8)
        bits.writeSixBitString(destination, 20)
        bits.writeUnsigned(0, 1) // DTE
        bits.writeUnsigned(0, 1) // spare
        return bits.toByteArray()
    }

    fun toSixBitPayload(payloadBits: ByteArray): Pair<String, Int> {
        val bitCount = payloadBits.size * 8
        // Trim to the intended message length by finding last used bit boundary is caller's responsibility;
        // callers pass exact-length arrays from BitWriter.
        val fillBits = (6 - (bitCount % 6)) % 6
        val totalBits = bitCount + fillBits
        val chars = StringBuilder(totalBits / 6)
        var bitIndex = 0
        while (bitIndex < totalBits) {
            var value = 0
            for (i in 0 until 6) {
                value = value shl 1
                val srcBit = bitIndex + i
                if (srcBit < bitCount) {
                    val byteIndex = srcBit / 8
                    val bitInByte = 7 - (srcBit % 8)
                    if (((payloadBits[byteIndex].toInt() ushr bitInByte) and 1) == 1) {
                        value = value or 1
                    }
                }
            }
            chars.append(sixBitToAscii(value))
            bitIndex += 6
        }
        return chars.toString() to fillBits
    }

    fun splitAivdmPayload(payload: String, fillBits: Int): List<Pair<String, Int>> {
        if (payload.length <= MaxPayloadCharsPerSentence) {
            return listOf(payload to fillBits)
        }
        val first = payload.substring(0, MaxPayloadCharsPerSentence)
        val second = payload.substring(MaxPayloadCharsPerSentence)
        return listOf(first to 0, second to fillBits)
    }

    fun sixBitToAscii(value: Int): Char {
        val v = value and 0x3F
        return if (v < 40) (v + 48).toChar() else (v + 56).toChar()
    }

    private fun encodeSog(sogKnots: Double): Int =
        (sogKnots * 10.0).roundToInt().coerceIn(0, 1023)

    private fun encodeCog(cogDegrees: Double): Int =
        (cogDegrees * 10.0).roundToInt().coerceIn(0, 3600)

    private fun encodeHeading(headingDegrees: Double): Int =
        headingDegrees.roundToInt().coerceIn(0, 359)

    private fun encodeLongitude(longitude: Double): Int =
        (longitude * 600_000.0).roundToInt()

    private fun encodeLatitude(latitude: Double): Int =
        (latitude * 600_000.0).roundToInt()

    private class BitWriter(private val bitCapacity: Int) {
        private val bytes = ByteArray((bitCapacity + 7) / 8)
        private var bitPosition = 0

        fun writeUnsigned(value: Int, bits: Int) {
            writeBits(value.toLong() and ((1L shl bits) - 1), bits)
        }

        fun writeSigned(value: Int, bits: Int) {
            val mask = (1L shl bits) - 1
            writeBits(value.toLong() and mask, bits)
        }

        fun writeSixBitString(text: String, charCount: Int) {
            val padded = text.uppercase().padEnd(charCount, '@').take(charCount)
            padded.forEach { writeUnsigned(encodeAisChar(it), 6) }
        }

        fun toByteArray(): ByteArray {
            require(bitPosition == bitCapacity) {
                "AIS payload expected $bitCapacity bits but wrote $bitPosition"
            }
            return bytes.copyOf()
        }

        private fun writeBits(value: Long, bits: Int) {
            require(bitPosition + bits <= bitCapacity) { "AIS bit writer overflow" }
            for (i in bits - 1 downTo 0) {
                val bit = ((value ushr i) and 1L).toInt()
                if (bit == 1) {
                    val byteIndex = bitPosition / 8
                    val bitInByte = 7 - (bitPosition % 8)
                    bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitInByte)).toByte()
                }
                bitPosition += 1
            }
        }

        private fun encodeAisChar(c: Char): Int {
            return when (c) {
                in 'A'..'Z' -> c - 'A' + 1
                in '0'..'9' -> c - '0' + 48
                ' ' -> 32
                '@' -> 0
                else -> 0
            }
        }
    }
}
