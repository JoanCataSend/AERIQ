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
     * Prueba el comportamiento de packet con los casos descritos en el cuerpo.
     * Diseño lógico: (int type, int count, int minor) → packet() → byte[]; según doc/android_design.md.
     */
    private byte[] packet(int type, int count, int minor) {
        byte[] packet = {2, 21, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) type, (byte) count, (byte) (minor >>> 8), (byte) minor, -53};
        System.arraycopy(IBeaconParser.PROJECT_ID.getBytes(StandardCharsets.US_ASCII), 0, packet, 2, 16);
        return packet;
    }

    /**
     * Prueba el comportamiento de ozonePpb con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → ozonePpb() → void; según doc/android_design.md.
     */
    @Test public void ozonePpb() {
        BeaconReading data = IBeaconParser.parse(packet(14, 7, 123));
        assertEquals(14, data.typeId);
        assertEquals(7, data.counter);
        assertEquals(123, data.value);
        assertEquals("ppb", data.unit());
    }

    /**
     * Prueba el comportamiento de negativeTemperature con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → negativeTemperature() → void; según doc/android_design.md.
     */
    @Test public void negativeTemperature() {
        BeaconReading data = IBeaconParser.parse(packet(12, 8, 0xfff4));
        assertEquals(-12, data.value);
        assertEquals("°C", data.unit());
    }

    /**
     * Prueba el comportamiento de batteryIsNotAMeasurement con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → batteryIsNotAMeasurement() → void; según doc/android_design.md.
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
     * Prueba el comportamiento de rejectsWrongUuid con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → rejectsWrongUuid() → void; según doc/android_design.md.
     */
    @Test public void rejectsWrongUuid() {
        byte[] data = packet(14, 2, 20);
        data[2] = 'X';
        assertNull(IBeaconParser.parse(data));
    }

    /**
     * Prueba el comportamiento de rejectsTruncatedOrWrongType con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → rejectsTruncatedOrWrongType() → void; según doc/android_design.md.
     */
    @Test public void rejectsTruncatedOrWrongType() {
        assertNull(IBeaconParser.parse(new byte[]{2, 21}));
        assertNull(IBeaconParser.parse(packet(99, 2, 20)));
    }

    /**
     * Prueba el comportamiento de discoversStatusWithoutTreatingItAsMeasurement con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → discoversStatusWithoutTreatingItAsMeasurement() → void; según doc/android_design.md.
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
     * Prueba el comportamiento de statusRequiresValidProjectAndCompleteFrame con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → statusRequiresValidProjectAndCompleteFrame() → void; según doc/android_design.md.
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
     * Prueba el comportamiento de diagnosticRequiresMatchingCycleAndVersion con los casos descritos en el cuerpo.
     * Diseño lógico: (sin parámetros) → diagnosticRequiresMatchingCycleAndVersion() → void; según doc/android_design.md.
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
