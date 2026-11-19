package com.aeriq.app.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Operadores y Orb de la campaña del admin, tal como los devuelve mobile-team.php (la misma lógica que el panel web). */
public final class CampaignTeam {
    public static final class Operator {
        public final long id;
        public final String name, orb;
        /** pending: invitación enviada, aún no ha activado la cuenta. Ya se le puede asignar un Orb. */
        public final boolean active, pending;

        Operator(JSONObject data) throws JSONException {
            id = data.getLong("id");
            name = data.optString("nombre");
            orb = data.isNull("orb") ? "" : data.optString("orb");
            active = data.optBoolean("activo");
            pending = data.optBoolean("pendiente");
        }
    }

    public static final class Device {
        public final long id;
        public final Long workerId;
        public final String name, address, worker, lastReading;
        public final boolean active, simulated;

        Device(JSONObject data) throws JSONException {
            id = data.getLong("id");
            name = data.optString("nombre");
            address = data.isNull("direccion_ble") ? "" : data.optString("direccion_ble");
            workerId = data.isNull("trabajador_id") ? null : data.getLong("trabajador_id");
            worker = data.isNull("trabajador") ? "" : data.optString("trabajador");
            active = data.optBoolean("activo");
            simulated = data.optBoolean("simulado");
            lastReading = data.isNull("ultima_medicion") ? "" : data.optString("ultima_medicion");
        }
    }

    public final String campaign;
    public final List<Operator> operators;
    public final List<Device> devices;

    CampaignTeam(JSONObject data) throws JSONException {
        JSONObject c = data.optJSONObject("campaign");
        campaign = c == null ? "" : c.optString("nombre");
        List<Operator> people = new ArrayList<>();
        JSONArray list = data.getJSONArray("operators");
        for (int i = 0; i < list.length(); i++) people.add(new Operator(list.getJSONObject(i)));
        operators = Collections.unmodifiableList(people);
        List<Device> orbs = new ArrayList<>();
        list = data.getJSONArray("devices");
        for (int i = 0; i < list.length(); i++) orbs.add(new Device(list.getJSONObject(i)));
        devices = Collections.unmodifiableList(orbs);
    }

    public static CampaignTeam empty() {
        try { return new CampaignTeam(new JSONObject("{\"operators\":[],\"devices\":[]}")); }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    /** El Orb registrado con esa dirección BLE, o null si aún no está en la campaña. */
    public Device byAddress(String address) {
        for (Device device : devices) if (device.address.equalsIgnoreCase(address)) return device;
        return null;
    }

    /** El operador que lleva ahora ese Orb, o null. */
    public Operator holder(Device device) {
        if (device == null || device.workerId == null) return null;
        for (Operator operator : operators) if (operator.id == device.workerId) return operator;
        return null;
    }
}
