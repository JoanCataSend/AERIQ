package com.aeriq.app.ble

import org.junit.Assert.*
import org.junit.Test

class IBeaconParserTest {
    private fun packet(type: Int, count: Int, minor: Int): ByteArray {
        val id = IBeaconParser.PROJECT_ID.toByteArray(Charsets.US_ASCII)
        return byteArrayOf(0x02, 0x15) + id +
            byteArrayOf(type.toByte(), count.toByte(), (minor ushr 8).toByte(), minor.toByte(), (-53).toByte())
    }

    @Test fun ozonePpb() {
        val data = IBeaconParser.parse(packet(14, 7, 123))!!
        assertEquals(14, data.typeId)
        assertEquals(7, data.counter)
        assertEquals(123, data.value)
        assertEquals("ppb", data.unit)
    }

    @Test fun negativeTemperature() {
        val data = IBeaconParser.parse(packet(12, 8, 0xFFF4))!!
        assertEquals(-12, data.value)
        assertEquals("°C", data.unit)
    }

    @Test fun rejectsWrongUuid() {
        val data = packet(14, 2, 20)
        data[2] = 'X'.code.toByte()
        assertNull(IBeaconParser.parse(data))
    }

    @Test fun rejectsTruncatedOrWrongType() {
        assertNull(IBeaconParser.parse(byteArrayOf(0x02,0x15)))
        assertNull(IBeaconParser.parse(packet(99, 2, 20)))
    }
}
