package com.aeriq.app.ble;

import java.nio.charset.StandardCharsets;

public final class IBeaconParser {
    public static final int APPLE_MANUFACTURER_ID = 0x004c, O3_ID = 14, TEMPERATURE_ID = 12;
    public static final String PROJECT_ID = "EPSG-GTI-PROY-3A";
    public static final int STATUS_ID = 15;
    /** Batería del Orb en % (0..100), medida por su MAX17048. No es una medición ambiental. */
    public static final int BATTERY_ID = 16;
    public static final String STATUS_UUID = "e3b44c20-3f02-4d76-9cb2-bdcb3105a001";

    public static int parseDiagnostic(byte[] data, int counter) {
        if (data == null || data.length != 12 || data[0] != 1 || (data[1] & 255) != counter) return -1;
        return (data[10] & 255) << 8 | (data[11] & 255);
    }

    private IBeaconParser() {}

    public static BeaconReading parse(byte[] data) {
        return parseType(data, false);
    }

    /** Estado del firmware: sirve para descubrir/diagnosticar, nunca para publicar medidas. */
    public static BeaconReading parseStatus(byte[] data) {
        return parseType(data, true);
    }

    /** Nivel de batería del Orb; null si no es ese anuncio o el valor está fuera de 0..100. */
    public static BeaconReading parseBattery(byte[] data) {
        BeaconReading reading = parseType(data, BATTERY_ID);
        return reading != null && reading.value <= 100 ? reading : null;
    }

    private static BeaconReading parseType(byte[] data, boolean statusOnly) {
        return statusOnly ? parseType(data, STATUS_ID) : parseType(data, -1);
    }

    /** wanted = -1 acepta las medidas (O3 y temperatura); otro valor, solo ese tipo. */
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
