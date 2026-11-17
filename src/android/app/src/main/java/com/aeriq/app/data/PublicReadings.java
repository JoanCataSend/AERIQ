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

    // ------------------------------------------------------------------ interpolación
    // IDW local acotada (T25/HU28). Mismos parámetros y pasos que aeriq-domain.js; ver doc/interpolacion.md.

    /** Rango admitido del radio (m); fuera de él no se estima. Cada campaña guarda el suyo en radio_m. */
    public static final int RADIUS_MIN = 20, RADIUS_MAX = 50, RADIUS_DEFAULT = 30;
    /** Posiciones de soporte distintas exigidas dentro del radio. */
    public static final int MIN_POSITIONS = 3;
    /** Lecturas más juntas (m) cuentan como una sola posición: deriva del GPS. */
    public static final double MIN_SEPARATION = 5;
    /** Exponente del peso 1/d^p y suelo de distancia (m) para no dividir entre cero. */
    public static final double POWER = 2, MIN_DISTANCE = 1;
    /** Lado de la celda de pintado (m). */
    public static final double CELL = 10;
    public static final double METERS_PER_DEGREE = 111320;

    public enum Reason {
        /** Hay valor estimado. */ NONE,
        /** Radio fuera de 20–50 m. */ RADIUS,
        /** Ninguna posición dentro del radio: sin datos. */ FAR,
        /** Menos de MIN_POSITIONS posiciones. */ INSUFFICIENT,
        /** Campañas, unidades u orígenes distintos. */ MIXED
    }

    public static final class Estimate {
        /** Valor interpolado, o null si el punto queda sin datos. */
        public final Double value;
        /** Posiciones de soporte dentro del radio. */
        public final int positions;
        /** Distancia (m) a la posición más cercana; infinito si no hay ninguna. */
        public final double nearest;
        public final Reason reason;

        Estimate(Double value, int positions, double nearest, Reason reason) {
            this.value = value;
            this.positions = positions;
            this.nearest = nearest;
            this.reason = reason;
        }
    }

    /** Solo se interpolan lecturas de la misma campaña, unidad y origen (real/simulado). */
    public static String groupKey(Reading reading) {
        return reading.campaign + "|" + reading.unit + "|" + (reading.simulated ? 1 : 0);
    }

    /** Distancia haversine en metros, con el mismo radio terrestre que la web. */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double rad = Math.PI / 180, dlat = (lat1 - lat2) * rad, dlon = (lon1 - lon2) * rad;
        double h = Math.pow(Math.sin(dlat / 2), 2)
            + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371000 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    public static List<Reading> support(List<Reading> rows) { return support(rows, MIN_SEPARATION); }

    /**
     * Posiciones de soporte: una lectura (la más reciente) por cada posición separada al menos
     * {@code separation} metros de las demás de su grupo.
     */
    public static List<Reading> support(List<Reading> rows, double separation) {
        List<Reading> sorted = new ArrayList<>(), out = new ArrayList<>();
        for (Reading reading : rows) {
            if (isFinite(reading.latitude) && isFinite(reading.longitude) && isFinite(reading.value)) sorted.add(reading);
        }
        if (sorted.isEmpty()) return out;
        // Orden propio (fecha descendente y desempates fijos): el resultado no depende del orden de entrada.
        Collections.sort(sorted, (a, b) -> {
            int order = (b.date == null ? "" : b.date).compareTo(a.date == null ? "" : a.date);
            if (order == 0) order = Double.compare(a.latitude, b.latitude);
            if (order == 0) order = Double.compare(a.longitude, b.longitude);
            return order != 0 ? order : Double.compare(a.value, b.value);
        });
        // Rejilla de «separation» para comparar solo con vecinas; ±2 celdas cubre el estrechamiento en longitud.
        double latStep = separation / METERS_PER_DEGREE;
        double lonStep = latStep / Math.max(0.01, Math.cos(sorted.get(0).latitude * Math.PI / 180));
        Map<String, List<Reading>> cells = new HashMap<>();
        for (Reading reading : sorted) {
            long x = (long) Math.floor(reading.latitude / latStep), y = (long) Math.floor(reading.longitude / lonStep);
            String key = groupKey(reading);
            boolean repeated = false;
            for (int i = -2; i <= 2 && !repeated; i++) for (int j = -2; j <= 2 && !repeated; j++) {
                List<Reading> cell = cells.get(key + "|" + (x + i) + "|" + (y + j));
                if (cell == null) continue;
                for (Reading other : cell) {
                    if (distance(reading.latitude, reading.longitude, other.latitude, other.longitude) < separation) {
                        repeated = true;
                        break;
                    }
                }
            }
            if (repeated) continue;
            cells.computeIfAbsent(key + "|" + x + "|" + y, k -> new ArrayList<>()).add(reading);
            out.add(reading);
        }
        return out;
    }

    /** Estimación en un punto a partir de posiciones de soporte (resultado de {@link #support}). */
    public static Estimate estimate(double latitude, double longitude, List<Reading> samples, int radius) {
        if (radius < RADIUS_MIN || radius > RADIUS_MAX) return new Estimate(null, 0, Double.POSITIVE_INFINITY, Reason.RADIUS);
        Reading first = null;
        boolean mixed = false;
        double sum = 0, weights = 0, nearest = Double.POSITIVE_INFINITY;
        int count = 0;
        for (Reading reading : samples) {
            double distance = distance(latitude, longitude, reading.latitude, reading.longitude);
            if (distance < nearest) nearest = distance;
            if (distance > radius) continue;
            if (first == null) first = reading;
            else if (!groupKey(reading).equals(groupKey(first))) mixed = true;
            double weight = 1 / Math.pow(Math.max(MIN_DISTANCE, distance), POWER);
            sum += reading.value * weight;
            weights += weight;
            count++;
        }
        if (count == 0) return new Estimate(null, 0, nearest, Reason.FAR);
        if (mixed) return new Estimate(null, count, nearest, Reason.MIXED);
        if (count < MIN_POSITIONS) return new Estimate(null, count, nearest, Reason.INSUFFICIENT);
        return new Estimate(sum / weights, count, nearest, Reason.NONE);
    }

    /** Valor interpolado o null si el punto queda sin datos. Acepta lecturas en bruto. */
    public static Double interpolate(double latitude, double longitude, List<Reading> rows, int radius) {
        return estimate(latitude, longitude, support(rows), radius).value;
    }

    /**
     * Texto de la consulta de un punto del mapa: título y detalle. Con valor indica que es interpolado;
     * sin él, por qué no se asigna nivel. El mismo criterio que public-map.js.
     */
    public static String[] describe(Estimate estimate, int radius, String unit, double medium, double high, boolean simulated) {
        String positions = estimate.positions + (estimate.positions == 1 ? " posición" : " posiciones") + " en " + radius + " m";
        if (estimate.value != null) {
            String level = estimate.value >= high ? "alto" : estimate.value >= medium ? "moderado" : "bajo";
            return new String[]{"Estimación: " + Math.round(estimate.value) + " " + unit + " · " + level,
                positions + " · la más cercana a " + Math.round(estimate.nearest) + " m. Valor interpolado, no medido"
                    + (simulated ? " · datos simulados." : ".")};
        }
        if (estimate.reason == Reason.INSUFFICIENT) {
            return new String[]{"Datos insuficientes", positions + "; hacen falta " + MIN_POSITIONS + ". No se asigna nivel de ozono."};
        }
        return new String[]{"Sin datos", (Double.isInfinite(estimate.nearest) ? "No hay mediciones publicadas."
            : "La medición más cercana está a " + Math.round(estimate.nearest) + " m (radio de " + radius + " m).")
            + " Sin datos no significa aire limpio."};
    }

    private static boolean isFinite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
