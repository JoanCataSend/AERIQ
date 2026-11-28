package com.aeriq.app.ble;
/*
 * Fichero: IBeaconParserTest.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/test/java/com/aeriq/app/ble.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public final class IBeaconParserTest {
    /**
     * Diseño lógico: type: Z, count: Z, minor: Z --> packet() --> N
     * Descripción: Describe la operación realizada y su resultado.
     */
    private byte[] packet(int type, int count, int minor) {
        byte[] packet = {2, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) type, (byte) count, (byte) (minor >>> 8), (byte) minor, -53};
        System.arraycopy(IBeaconParser.PROJECT_ID.getBytes(StandardCharsets.US_ASCII), 0, packet, 2, 16);
        return packet;
    }

    /**
     * Diseño lógico: ozonePpb()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void ozonePpb() {
        BeaconReading data = IBeaconParser.parse(packet(14, 7, 123));
        assertEquals(14, data.typeId);
        assertEquals(7, data.counter);
        assertEquals(123, data.value);
        assertEquals("ppb", data.unit());
    }

    /**
     * Diseño lógico: negativeTemperature()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void negativeTemperature() {
        BeaconReading data = IBeaconParser.parse(packet(12, 8, 0xfff4));
        assertEquals(-12, data.value);
        assertEquals("°C", data.unit());
    }

    /**
     * Diseño lógico: batteryIsNotAMeasurement()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void batteryIsNotAMeasurement() {
        assertNull(IBeaconParser.parse(packet(16, 3, 57)));
        assertNull(IBeaconParser.parseStatus(packet(16, 3, 57)));
        BeaconReading battery = IBeaconParser.parseBattery(packet(16, 3, 57));
        assertEquals(16, battery.typeId);
        assertEquals(57, battery.value);
        assertEquals(3, battery.counter);
        assertNull(IBeaconParser.parseBattery(packet(16, 3, 101)));
        assertNull(IBeaconParser.parseBattery(packet(14, 3, 57)));
    }

    /**
     * Diseño lógico: rejectsWrongUuid()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void rejectsWrongUuid() {
        byte[] data = packet(14, 2, 20);
        data[2] = 'X';
        assertNull(IBeaconParser.parse(data));
    }

    /**
     * Diseño lógico: rejectsTruncatedOrWrongType()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void rejectsTruncatedOrWrongType() {
        assertNull(IBeaconParser.parse(new byte[]{2, 21}));
        assertNull(IBeaconParser.parse(packet(99, 2, 20)));
    }

    /**
     * Diseño lógico: discoversStatusWithoutTreatingItAsMeasurement()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void discoversStatusWithoutTreatingItAsMeasurement() {
        byte[] data = packet(15, 255, 0x8018);
        assertNull(IBeaconParser.parse(data));
        BeaconReading status = IBeaconParser.parseStatus(data);
        assertNotNull(status);
        assertEquals(15, status.typeId);
        assertEquals(255, status.counter);
        assertEquals(0x8018, status.value);
    }

    /**
     * Diseño lógico: statusRequiresValidProjectAndCompleteFrame()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void statusRequiresValidProjectAndCompleteFrame() {
        assertNull(IBeaconParser.parseStatus(null));
        assertNull(IBeaconParser.parseStatus(new byte[]{2, 21}));
        assertNull(IBeaconParser.parseStatus(packet(14, 1, 10)));
        byte[] data = packet(15, 1, 8);
        data[2] = 'X';
        assertNull(IBeaconParser.parseStatus(data));
    }

    /**
     * Diseño lógico: diagnosticRequiresMatchingCycleAndVersion()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void diagnosticRequiresMatchingCycleAndVersion() {
        byte[] state = {1,7,1,2,3,4,5,6,7,8,0,3};
        assertEquals(3, IBeaconParser.parseDiagnostic(state,7));
        assertEquals(-1, IBeaconParser.parseDiagnostic(state,8));
        assertEquals(-1, IBeaconParser.parseDiagnostic(null,7));
        state[0]=2;
        assertEquals(-1, IBeaconParser.parseDiagnostic(state,7));
    }
}
