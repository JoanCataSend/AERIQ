package com.aeriq.app.data;

import org.json.*;
import org.osmdroid.util.GeoPoint;
import java.util.*;

public final class PublicReadings {
    public static final class Reading {
        public final String id, campaign, zone, unit, date;
        public final double latitude, longitude, value, medium, high;
        public final boolean simulated;
        public final int radius;

        public Reading(String id, String campaign, String zone, double latitude, double longitude,
                       double value, String unit, String date, boolean simulated, int radius,
                       double medium, double high) {
            this.id = id;
            this.campaign = campaign;
            this.zone = zone;
            this.latitude = latitude;
            this.longitude = longitude;
            this.value = value;
            this.unit = unit;
            this.date = date;
            this.simulated = simulated;
            this.radius = radius;
            this.medium = medium;
            this.high = high;
        }

        public GeoPoint position() { return new GeoPoint(latitude, longitude); }
        public int color(double level) { return level >= high ? 0xffe35b5d : level >= medium ? 0xffeea22d : 0xff219c72; }
    }

    public static final class Result {
        public final List<Reading> rows;
        public final boolean truncated;

        Result(List<Reading> rows, boolean truncated) {
            this.rows = rows;
            this.truncated = truncated;
        }
    }

    public static Result fetch(boolean simulated) throws Exception {
        JsonHttp.Response response = JsonHttp.request(ApiRoutes.PUBLIC_MAP + "?action=map&mode="
            + (simulated ? "simulated" : "real"), null, null);
        if (!response.ok()) throw new IllegalStateException(response.message());
        JSONArray array = response.json.getJSONArray("measurements");
        List<Reading> rows = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject row = array.getJSONObject(index);
            rows.add(new Reading(row.getString("id"), row.getString("campaignId"), row.getString("zona"),
                row.getDouble("latitud"), row.getDouble("longitud"), row.getDouble("valor"),
                row.getString("unidad"), row.getString("capturada_en"), row.getBoolean("simulado"),
                row.getInt("radio_m"), row.getDouble("umbral_medio"), row.getDouble("umbral_alto")));
        }
        return new Result(rows, response.json.optBoolean("truncated"));
    }

    public static Double interpolate(double latitude, double longitude, List<Reading> rows, int radius) {
        if (radius < 20 || radius > 50) return null;
        GeoPoint center = new GeoPoint(latitude, longitude);
        Set<String> locations = new HashSet<>();
        Reading first = null;
        double sum = 0, weights = 0;
        for (Reading reading : rows) {
            double distance = center.distanceToAsDouble(reading.position());
            if (distance > radius || !locations.add(reading.latitude + "/" + reading.longitude)) continue;
            if (first == null) first = reading;
            else if (!reading.campaign.equals(first.campaign) || !reading.unit.equals(first.unit)
                    || reading.simulated != first.simulated) return null;
            double weight = 1 / Math.pow(Math.max(1, distance), 2);
            sum += reading.value * weight;
            weights += weight;
        }
        return locations.size() < 3 ? null : sum / weights;
    }
}
