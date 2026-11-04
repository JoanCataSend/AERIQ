package com.aeriq.app.ble;

import java.nio.charset.StandardCharsets;

public final class IBeaconParser {
    public static final int APPLE_MANUFACTURER_ID = 0x004c, O3_ID = 14, TEMPERATURE_ID = 12;
    public static final String PROJECT_ID = "EPSG-GTI-PROY-3A";

    private IBeaconParser() {}

    public static BeaconReading parse(byte[] data) {
        if (data == null || data.length < 23 || data[0] != 2 || data[1] != 21) return null;
        String uuid = new String(data, 2, 16, StandardCharsets.ISO_8859_1);
        int type = data[18] & 255;
        if (!PROJECT_ID.equals(uuid) || (type != O3_ID && type != TEMPERATURE_ID)) return null;
        int raw = (data[20] & 255) << 8 | (data[21] & 255);
        return new BeaconReading(uuid, type, data[19] & 255, type == O3_ID ? raw : (short) raw, data[22]);
    }
}
