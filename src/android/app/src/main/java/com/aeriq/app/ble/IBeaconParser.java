package com.aeriq.app.ble;
/*
 * Fichero: IBeaconParser.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ble.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import java.nio.charset.StandardCharsets;

public final class IBeaconParser {
    public static final int APPLE_MANUFACTURER_ID = 0x004c, O3_ID = 14, TEMPERATURE_ID = 12;
    public static final String PROJECT_ID = "EPSG-GTI-PROY-3A";
    public static final int STATUS_ID = 15;
    /**
     * Diseño lógico: data: N, counter: Z --> parseDiagnostic() --> Z
     * Descripción: Describe la operación realizada y su resultado.
     */
    public static int parseDiagnostic(byte[] data, int counter) {
        if (data == null || data.length != 12 || data[0] != 1 || (data[1] & 255) != counter) return -1;
        return (data[10] & 255) << 8 | (data[11] & 255);
    }

    /**
     * Diseño lógico: IBeaconParser() --> Text
     * Descripción: Procesa ibeacon parser y devuelve el resultado correspondiente.
     */
    private IBeaconParser() {}

    /**
     * Diseño lógico: data: N --> parse() --> Text
     * Descripción: Interpreta los datos recibidos y devuelve su representación lógica.
     */
    public static BeaconReading parse(byte[] data) {
        return parseType(data, false);
    }

    /**
     * Diseño lógico: data: N --> parseStatus() --> Text
     * Descripción: Procesa parse status y devuelve el resultado correspondiente.
     */
    public static BeaconReading parseStatus(byte[] data) {
        return parseType(data, true);
    }

    /**
     * Diseño lógico: data: N --> parseBattery() --> Text
     * Descripción: Procesa parse battery y devuelve el resultado correspondiente.
     */
    public static BeaconReading parseBattery(byte[] data) {
        BeaconReading reading = parseType(data, BATTERY_ID);
        return reading != null && reading.value <= 100 ? reading : null;
    }

    /**
     * Diseño lógico: data: N, statusOnly: B --> parseType() --> Text
     * Descripción: Procesa parse type y devuelve el resultado correspondiente.
     */
    private static BeaconReading parseType(byte[] data, boolean statusOnly) {
        return statusOnly ? parseType(data, STATUS_ID) : parseType(data, -1);
    }

    /**
     * Diseño lógico: data: N, wanted: Z --> parseType() --> Text
     * Descripción: Procesa parse type y devuelve el resultado correspondiente.
     */
    private static BeaconReading parseType(byte[] data, int wanted) {
        if (data == null || data.length < 23 || data[0] != 2 || data[1] != 21) return null;
        String uuid = new String(data, 2, 16, StandardCharsets.ISO_8859_1);
        int type = data[18] & 255;
        if (!PROJECT_ID.equals(uuid) || (wanted < 0 ? (type != O3_ID && type != TEMPERATURE_ID)
            : type != wanted)) return null;
        int raw = (data[20] & 255) << 8 | (data[21] & 255);
        return new BeaconReading(uuid, type, data[19] & 255, type == TEMPERATURE_ID ? (short) raw : raw, data[22]);
    }
}
