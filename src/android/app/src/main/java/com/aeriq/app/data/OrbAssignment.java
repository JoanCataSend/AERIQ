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
    }
}
