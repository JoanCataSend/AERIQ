package com.aeriq.app.data;

import org.json.JSONObject;
import org.json.JSONException;

public final class OrbAssignment {
    public final long id, campaignId, assignmentId;
    public final String name, address, campaign;
    public final boolean simulated;
    public final double medium, high;

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
    }
}
