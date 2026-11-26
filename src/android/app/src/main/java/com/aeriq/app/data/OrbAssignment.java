package com.aeriq.app.data;

import org.json.JSONObject;
import org.json.JSONException;

public final class OrbAssignment {
    public final long id, campaignId, assignmentId;
    public final String name, address, campaign;
    public final boolean simulated;
    public final double medium, high;
    /** Última batería guardada en el servidor (-1 si el Orb nunca la ha enviado) y su fecha UTC. */
    public final int batteryPercent;
    public final String batteryAt;
    /** Rango admitido de ozono (ppb) y temperatura (°C) según el servidor (HU35). */
    public final double o3Min, o3Max, temperatureMin, temperatureMax;

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
    public boolean inRange(int typeId, double value) {
        if (typeId == 14) return value >= o3Min && value <= o3Max;
        if (typeId == 12) return value >= temperatureMin && value <= temperatureMax;
        return true;
    }

    /** Texto del rango admitido para una magnitud, p. ej. «0–500 ppb». */
    public String range(int typeId) {
        if (typeId == 12) return number(temperatureMin) + "–" + number(temperatureMax) + " °C";
        return number(o3Min) + "–" + number(o3Max) + " ppb";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
