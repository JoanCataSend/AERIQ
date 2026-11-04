package com.aeriq.app.ble;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public final class IBeaconParserTest {
    private byte[] packet(int type, int count, int minor) {
        byte[] packet = {2, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) type, (byte) count, (byte) (minor >>> 8), (byte) minor, -53};
        System.arraycopy(IBeaconParser.PROJECT_ID.getBytes(StandardCharsets.US_ASCII), 0, packet, 2, 16);
        return packet;
    }

    @Test public void ozonePpb() {
        BeaconReading data = IBeaconParser.parse(packet(14, 7, 123));
        assertEquals(14, data.typeId);
        assertEquals(7, data.counter);
        assertEquals(123, data.value);
        assertEquals("ppb", data.unit());
    }

    @Test public void negativeTemperature() {
        BeaconReading data = IBeaconParser.parse(packet(12, 8, 0xfff4));
        assertEquals(-12, data.value);
        assertEquals("°C", data.unit());
    }

    @Test public void rejectsWrongUuid() {
        byte[] data = packet(14, 2, 20);
        data[2] = 'X';
        assertNull(IBeaconParser.parse(data));
    }

    @Test public void rejectsTruncatedOrWrongType() {
        assertNull(IBeaconParser.parse(new byte[]{2, 21}));
        assertNull(IBeaconParser.parse(packet(99, 2, 20)));
    }
}
