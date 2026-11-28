package com.aeriq.app.data;
/*
 * Fichero: CampaignTeam.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Diseño lógico: data: Text --> Operator() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
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

      /**
       * Diseño lógico: data: Text --> Device() --> Text
       * Descripción: Procesa device y devuelve el resultado correspondiente.
       */
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

  /**
   * Diseño lógico: data: Text --> CampaignTeam() --> Text
   * Descripción: Procesa campaign team y devuelve el resultado correspondiente.
   */
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

    /**
     * Diseño lógico: empty() --> Text
     * Descripción: Procesa empty y devuelve el resultado correspondiente.
     */
    public static CampaignTeam empty() {
        try { return new CampaignTeam(new JSONObject("{\"operators\":[],\"devices\":[]}")); }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * Diseño lógico: address: Text --> byAddress() --> Text
     * Descripción: Procesa by address y devuelve el resultado correspondiente.
     */
    public Device byAddress(String address) {
        for (Device device : devices) if (device.address.equalsIgnoreCase(address)) return device;
        return null;
    }

    /**
     * Diseño lógico: device: Text --> holder() --> Text
     * Descripción: Procesa holder y devuelve el resultado correspondiente.
     */
    public Operator holder(Device device) {
        if (device == null || device.workerId == null) return null;
        for (Operator operator : operators) if (operator.id == device.workerId) return operator;
        return null;
    }
}
