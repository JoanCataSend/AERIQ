package com.aeriq.app.data;
/*
 * Fichero: OrbAssignment.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import org.json.JSONObject;
import org.json.JSONException;

public final class OrbAssignment {
    public final long id, campaignId, assignmentId;
    public final String name, address, campaign;
    public final boolean simulated;
    public final double medium, high;
    /**
     * Diseño lógico: data: Text --> OrbAssignment() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    OrbAssignment(JSONObject data) throws JSONException {
        id = data.getLong("id");
        name = data.getString("nombre");
        address = data.isNull("direccion_ble") ? "" : data.getString("direccion_ble");
        campaign = data.getString("campana");
        campaignId = data.getLong("campana_id");
        assignmentId = data.getLong("asignacion_id");
        simulated = data.optInt("simulado") == 1 || data.optBoolean("simulado");
        medium = data.optDouble("umbral_medio", 60);
        high = data.optDouble("umbral_alto", 100);
        batteryPercent = data.isNull("bateria_pct") ? -1 : data.optInt("bateria_pct", -1);
        batteryAt = data.isNull("bateria_en") ? "" : data.optString("bateria_en", "");
        o3Min = data.optDouble("o3_minimo", 0);
        o3Max = data.optDouble("o3_maximo", 500);
        temperatureMin = data.optDouble("temperatura_minima", -20);
        temperatureMax = data.optDouble("temperatura_maxima", 70);
    }

    /**
     * HU35: true si el valor está dentro del rango admitido para su magnitud (14 = O3, 12 = temperatura).
     * La app lo comprueba antes de enviar para avisar al momento; el servidor vuelve a comprobarlo y guarda la
     * lectura como no válida si está fuera, para dejar constancia.
     */
    /**
     * Diseño lógico: typeId: Z, value: R --> inRange() --> B
     * Descripción: Procesa in range y devuelve el resultado correspondiente.
     */
    public boolean inRange(int typeId, double value) {
        if (typeId == 14) return value >= o3Min && value <= o3Max;
        if (typeId == 12) return value >= temperatureMin && value <= temperatureMax;
        return true;
    }

    /**
     * Diseño lógico: typeId: Z --> range() --> Text
     * Descripción: Procesa range y devuelve el resultado correspondiente.
     */
    public String range(int typeId) {
        if (typeId == 12) return number(temperatureMin) + "–" + number(temperatureMax) + " °C";
        return number(o3Min) + "–" + number(o3Max) + " ppb";
    }

    /**
     * Diseño lógico: value: R --> number() --> Text
     * Descripción: Procesa number y devuelve el resultado correspondiente.
     */
    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
