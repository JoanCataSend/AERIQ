/*
 * Fichero: StoredReading.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import org.json.JSONObject;
import org.json.JSONException;

public final class StoredReading {
    public final long id, deviceId;
    public final double value;
    public final int type;
    public final String unit, date, device, campaign, zone;
    public final Double latitude, longitude;
    public final boolean simulated;

    /**

     * StoredReading(long id, double value, String unit, int type, String date, String device,
                         String campaign, String zone, Double latitude, Double longitude,
                         boolean simulated, long deviceId) → Implementa la operación StoredReading del módulo.

     */
    /**
     * Diseño lógico: id: Z, value: R, unit: Text, type: Z, date: Text, device: Text, campaign: Text, zone: Text, latitude: Text, longitude: Text, simulated: B, deviceId: Z --> StoredReading() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
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

    /**
     * Diseño lógico: data: Text --> fromJson() --> Text
     * Descripción: Procesa from json y devuelve el resultado correspondiente.
     */
    static StoredReading fromJson(JSONObject data) throws JSONException {
        return new StoredReading(data.getLong("id"), data.getDouble("valor"), data.getString("unidad"),
            data.getInt("tipo"), data.getString("capturada_en"), data.getString("dispositivo"),
            data.getString("campana"), data.getString("zona"),
            data.isNull("latitud") ? null : data.getDouble("latitud"),
            data.isNull("longitud") ? null : data.getDouble("longitud"),
            data.optInt("simulado") == 1 || data.optBoolean("simulado"), data.optLong("dispositivo_id"));
    }
}
