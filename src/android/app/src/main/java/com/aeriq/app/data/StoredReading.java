package com.aeriq.app.data;

import org.json.JSONObject;
import org.json.JSONException;

public final class StoredReading {
    public final long id, deviceId;
    public final double value;
    public final int type;
    public final String unit, date, device, campaign, zone;
    public final Double latitude, longitude;
    public final boolean simulated;

    public StoredReading(long id, double value, String unit, int type, String date, String device,
                         String campaign, String zone, Double latitude, Double longitude,
                         boolean simulated, long deviceId) {
        this.id = id;
        this.value = value;
        this.unit = unit;
        this.type = type;
        this.date = date;
        this.device = device;
        this.campaign = campaign;
        this.zone = zone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.simulated = simulated;
        this.deviceId = deviceId;
    }

    static StoredReading fromJson(JSONObject data) throws JSONException {
        return new StoredReading(data.getLong("id"), data.getDouble("valor"), data.getString("unidad"),
            data.getInt("tipo"), data.getString("capturada_en"), data.getString("dispositivo"),
            data.getString("campana"), data.getString("zona"),
            data.isNull("latitud") ? null : data.getDouble("latitud"),
            data.isNull("longitud") ? null : data.getDouble("longitud"),
            data.optInt("simulado") == 1 || data.optBoolean("simulado"), data.optLong("dispositivo_id"));
    }
}
