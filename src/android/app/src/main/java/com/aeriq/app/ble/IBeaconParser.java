package com.aeriq.app.ble;

import java.nio.charset.StandardCharsets;

public final class IBeaconParser {
    public static final int APPLE_MANUFACTURER_ID = 0x004c, O3_ID = 14, TEMPERATURE_ID = 12;
    public static final String PROJECT_ID = "EPSG-GTI-PROY-3A";
    public static final int STATUS_ID = 15;
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

    private static BeaconReading parseType(byte[] data, boolean statusOnly) {
        if (data == null || data.length < 23 || data[0] != 2 || data[1] != 21) return null;
        String uuid = new String(data, 2, 16, StandardCharsets.ISO_8859_1);
        int type = data[18] & 255;
        if (!PROJECT_ID.equals(uuid) || (statusOnly ? type != STATUS_ID
            : (type != O3_ID && type != TEMPERATURE_ID))) return null;
        int raw = (data[20] & 255) << 8 | (data[21] & 255);
        return new BeaconReading(uuid, type, data[19] & 255, type == TEMPERATURE_ID ? (short) raw : raw, data[22]);
    }
}
