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

        /**
         * Gestiona posición.
         * Diseño lógico: (sin parámetros) → position() → GeoPoint; según doc/android_design.md.
         */
        public GeoPoint position() { return new GeoPoint(latitude, longitude); }
        /**
         * Gestiona color.
         * Diseño lógico: (double level) → color() → int; según doc/android_design.md.
         */
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

    /**
     * Obtiene.
     * Diseño lógico: (boolean simulated) → fetch() → Result; según doc/android_design.md.
     */
    public static Result fetch(boolean simulated) throws Exception { return fetch(simulated, 24); }

    /**
     * hours: 24 (último día), 168 (última semana) o 0 (histórico, las 10.000 más recientes). Sin este parámetro el
     * servidor devuelve solo las últimas 24 h, y una comparación de 7 o 30 días quedaba vacía.
     */
    /**
     * Diseño lógico: (boolean simulated, int hours) → fetch() → Result; según doc/android_design.md.
     */
    public static Result fetch(boolean simulated, int hours) throws Exception {
        if (hours != 0 && hours != 24 && hours != 168) throw new IllegalArgumentException("Periodo no admitido: " + hours);
        JsonHttp.Response response = JsonHttp.request(ApiRoutes.PUBLIC_MAP + "?action=map&mode="
            + (simulated ? "simulated" : "real") + "&hours=" + hours, null, null);
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
    /** Exponente del peso 1/d^p (3: manda la medición más cercana, T46) y suelo de distancia (m) para no dividir entre cero. */
    public static final double POWER = 3, MIN_DISTANCE = 1;
    /**
     * Zona propia (T46): alrededor de cada posición, hasta esta fracción de la distancia a su vecina más cercana y
     * como mucho OWN_MAX metros, la zona toma el valor medido en ese punto.
     */
    public static final double OWN_FRACTION = 0.5, OWN_MAX = 15;
    /** Lado (m) de la rejilla de validación de las pruebas. */
    public static final double CELL = 10;
    /** Puntos que {@link #raster} evalúa como máximo por pintado; si se alcanza, se avisa. */
    public static final int MAX_EVALUATIONS = 250_000;
    /** Estado de cada punto de {@link #raster}: sin datos, datos insuficientes y nivel bajo, moderado o alto. */
    public static final byte ZONE_NONE = 0, ZONE_INSUFFICIENT = 1, ZONE_LOW = 2, ZONE_MEDIUM = 3, ZONE_HIGH = 4;
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
        /** Suma de pesos IDW de las posiciones dentro del radio (para combinar campañas que se solapan, T47). */
        public final double weight;
        /** true si el punto cae en la zona propia de la posición más cercana y el valor es el medido (T46). */
        public final boolean own;

        Estimate(Double value, int positions, double nearest, Reason reason) { this(value, positions, nearest, reason, 0, false); }

        Estimate(Double value, int positions, double nearest, Reason reason, double weight, boolean own) {
            this.value = value;
            this.positions = positions;
            this.nearest = nearest;
            this.reason = reason;
            this.weight = weight;
            this.own = own;
        }
    }

    /** Solo se interpolan lecturas de la misma campaña, unidad y origen (real/simulado). */
    /**
     * Diseño lógico: (Reading reading) → groupKey() → String; según doc/android_design.md.
     */
    public static String groupKey(Reading reading) {
        return reading.campaign + "|" + reading.unit + "|" + (reading.simulated ? 1 : 0);
    }

    /** Distancia haversine en metros, con el mismo radio terrestre que la web. */
    /**
     * Diseño lógico: (double lat1, double lon1, double lat2, double lon2) → distance() → double; según doc/android_design.md.
     */
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double rad = Math.PI / 180, dlat = (lat1 - lat2) * rad, dlon = (lon1 - lon2) * rad;
        double h = Math.pow(Math.sin(dlat / 2), 2)
            + Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.pow(Math.sin(dlon / 2), 2);
        return 6371000 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /**
     * Gestiona support.
     * Diseño lógico: (List<Reading> rows) → support() → List<Reading>; según doc/android_design.md.
     */
    public static List<Reading> support(List<Reading> rows) { return support(rows, MIN_SEPARATION); }

    /**
     * Posiciones de soporte: una lectura (la más reciente) por cada posición separada al menos
     * {@code separation} metros de las demás de su grupo.
     */
    /**
     * Diseño lógico: (List<Reading> rows, double separation) → support() → List<Reading>; según doc/android_design.md.
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

    /**
     * Zona propia de cada posición de soporte (T46): la mitad de la distancia a su vecina más cercana del mismo grupo,
     * como mucho {@link #OWN_MAX} metros. Mismo cálculo que ownRadii() de aeriq-domain.js.
     */
    /**
     * Diseño lógico: (List<Reading> samples) → ownRadii() → Map<Reading, Double>; según doc/android_design.md.
     */
    public static Map<Reading, Double> ownRadii(List<Reading> samples) {
        Map<Reading, Double> out = new java.util.IdentityHashMap<>();
        if (samples.isEmpty()) return out;
        double reach = OWN_MAX / OWN_FRACTION, latStep = reach / METERS_PER_DEGREE;
        double lonStep = latStep / Math.max(0.01, Math.cos(samples.get(0).latitude * Math.PI / 180));
        Map<String, List<Reading>> cells = new HashMap<>();
        for (Reading r : samples) {
            cells.computeIfAbsent((long) Math.floor(r.latitude / latStep) + ":" + (long) Math.floor(r.longitude / lonStep), k -> new ArrayList<>()).add(r);
        }
        for (Reading r : samples) {
            long x = (long) Math.floor(r.latitude / latStep), y = (long) Math.floor(r.longitude / lonStep);
            double nn = Double.POSITIVE_INFINITY;
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
                List<Reading> cell = cells.get((x + a) + ":" + (y + b));
                if (cell == null) continue;
                for (Reading other : cell) {
                    if (other == r || !groupKey(other).equals(groupKey(r))) continue;
                    nn = Math.min(nn, distance(r.latitude, r.longitude, other.latitude, other.longitude));
                }
            }
            out.put(r, Math.min(OWN_MAX, nn * OWN_FRACTION));
        }
        return out;
    }

    /** Estimación en un punto a partir de posiciones de soporte (resultado de {@link #support}). */
    /**
     * Diseño lógico: (double latitude, double longitude, List<Reading> samples, int radius) → estimate() → Estimate; según doc/android_design.md.
     */
    public static Estimate estimate(double latitude, double longitude, List<Reading> samples, int radius) {
        return estimate(latitude, longitude, samples, radius, ownRadii(samples));
    }

    /** Igual, con las zonas propias ya calculadas para el grupo (raster no las repite en cada punto). */
    static Estimate estimate(double latitude, double longitude, List<Reading> samples, int radius, Map<Reading, Double> own) {
        if (radius < RADIUS_MIN || radius > RADIUS_MAX) return new Estimate(null, 0, Double.POSITIVE_INFINITY, Reason.RADIUS);
        Reading first = null, closest = null;
        boolean mixed = false;
        double sum = 0, weights = 0, nearest = Double.POSITIVE_INFINITY;
        int count = 0;
        for (Reading reading : samples) {
            double distance = distance(latitude, longitude, reading.latitude, reading.longitude);
            if (distance < nearest) { nearest = distance; closest = reading; }
            if (distance > radius) continue;
            if (first == null) first = reading;
            else if (!groupKey(reading).equals(groupKey(first))) mixed = true;
            double weight = 1 / Math.pow(Math.max(MIN_DISTANCE, distance), POWER);
            sum += reading.value * weight;
            weights += weight;
            count++;
        }
        if (count == 0) return new Estimate(null, 0, nearest, Reason.FAR, 0, false);
        if (mixed) return new Estimate(null, count, nearest, Reason.MIXED, weights, false);
        if (count < MIN_POSITIONS) return new Estimate(null, count, nearest, Reason.INSUFFICIENT, weights, false);
        // Dentro de la zona propia de la posición más cercana manda su valor medido (T46).
        Double ownRadius = own.get(closest);
        if (ownRadius != null && nearest <= ownRadius) return new Estimate(closest.value, count, nearest, Reason.NONE, weights, true);
        return new Estimate(sum / weights, count, nearest, Reason.NONE, weights, false);
    }

    /** Valor interpolado o null si el punto queda sin datos. Acepta lecturas en bruto. */
    /**
     * Diseño lógico: (double latitude, double longitude, List<Reading> rows, int radius) → interpolate() → Double; según doc/android_design.md.
     */
    public static Double interpolate(double latitude, double longitude, List<Reading> rows, int radius) {
        return estimate(latitude, longitude, support(rows), radius).value;
    }

    /** Resultado de {@link #raster}: un estado ZONE_* por punto, fila a fila. */
    public static final class Raster {
        public final int rows, columns, evaluated;
        public final byte[] states;
        public final boolean clipped;

        Raster(int rows, int columns, byte[] states, int evaluated, boolean clipped) {
            this.rows = rows;
            this.columns = columns;
            this.states = states;
            this.evaluated = evaluated;
            this.clipped = clipped;
        }
    }

    /** Índices {desde, hasta} de los valores de una lista ordenada (creciente o decreciente) que caen en [min, max]. */
    static int[] span(double[] values, double min, double max) {
        int n = values.length;
        boolean ascending = n < 2 || values[0] <= values[n - 1];
        int lo = 0, hi = n;
        while (lo < hi) { int m = (lo + hi) >>> 1; if (at(values, ascending, m) < min) lo = m + 1; else hi = m; }
        int a = lo;
        hi = n;
        while (lo < hi) { int m = (lo + hi) >>> 1; if (at(values, ascending, m) <= max) lo = m + 1; else hi = m; }
        int b = lo - 1;
        return ascending ? new int[]{a, b} : new int[]{n - 1 - b, n - 1 - a};
    }

    /**
     * Gestiona at.
     * Diseño lógico: (double[] values, boolean ascending, int index) → at() → double; según doc/android_design.md.
     */
    private static double at(double[] values, boolean ascending, int index) {
        return ascending ? values[index] : values[values.length - 1 - index];
    }

    /**
     * Rejilla para pintar las zonas sin celdas cuadradas: evalúa {@link #estimate} en cada punto de una rejilla
     * fina (la pantalla, cada pocos píxeles), así el borde de cada zona sigue el radio y sale redondo.
     * Mismo cálculo que raster() de aeriq-domain.js. groups: posiciones de soporte por grupo; radii: radio de
     * cada grupo; lats/lons: latitud de cada fila y longitud de cada columna (ordenadas). Solo se evalúan los
     * puntos cercanos a alguna posición. Si varios grupos alcanzan un punto, sus estimaciones se combinan (T47) y un
     * valor estimado gana a «datos insuficientes».
     */
    /**
     * Diseño lógico: (List<List<Reading>> groups, int[] radii, double[] lats, double[] lons, int maxEvaluations) → raster() → Raster; según doc/android_design.md.
     */
    public static Raster raster(List<List<Reading>> groups, int[] radii, double[] lats, double[] lons, int maxEvaluations) {
        int rows = lats.length, columns = lons.length, evaluated = 0, cells = rows * columns;
        byte[] states = new byte[cells];
        int[] seen = new int[cells];
        // Campañas que se solapan (T47): la zona común se interpola con todas (media de cada estimación ponderada por
        // su peso IDW) y el nivel se juzga con los umbrales de la que más pesa. En la zona propia de una posición
        // manda su valor medido, el de la más cercana si hay varias. Mismo cálculo que raster() de aeriq-domain.js.
        double[] sumV = new double[cells], sumW = new double[cells], topW = new double[cells], exactV = new double[cells], exactD = new double[cells];
        int[] owner = new int[cells], exactG = new int[cells];
        boolean[] insufficient = new boolean[cells];
        java.util.Arrays.fill(owner, -1);
        java.util.Arrays.fill(exactG, -1);
        java.util.Arrays.fill(exactD, Double.POSITIVE_INFINITY);
        boolean clipped = false;
        double bucketLat = RADIUS_MAX * 1.1 / METERS_PER_DEGREE;
        scan:
        for (int g = 0; g < groups.size(); g++) {
            List<Reading> group = groups.get(g);
            int radius = radii[g], pass = g + 1;
            if (group.isEmpty() || radius < RADIUS_MIN || radius > RADIUS_MAX) continue;
            Reading first = group.get(0);
            Map<Reading, Double> own = ownRadii(group);
            // Cubos de ~radio máximo para comparar cada punto solo con las posiciones vecinas.
            double bucketLon = bucketLat / Math.max(0.01, Math.cos(first.latitude * Math.PI / 180));
            Map<String, List<Reading>> buckets = new HashMap<>();
            for (Reading reading : group) {
                buckets.computeIfAbsent((long) Math.floor(reading.latitude / bucketLat) + ":"
                    + (long) Math.floor(reading.longitude / bucketLon), key -> new ArrayList<>()).add(reading);
            }
            double dLat = radius / METERS_PER_DEGREE;
            for (Reading sample : group) {
                double dLon = dLat / Math.max(0.01, Math.cos(sample.latitude * Math.PI / 180));
                int[] r = span(lats, sample.latitude - dLat, sample.latitude + dLat);
                int[] c = span(lons, sample.longitude - dLon, sample.longitude + dLon);
                for (int i = r[0]; i <= r[1]; i++) for (int j = c[0]; j <= c[1]; j++) {
                    int k = i * columns + j;
                    if (seen[k] == pass) continue;
                    seen[k] = pass;
                    // El tope de rendimiento se avisa: una zona recortada no debe confundirse con una zona sin datos.
                    if (evaluated >= maxEvaluations) { clipped = true; break scan; }
                    evaluated++;
                    long x = (long) Math.floor(lats[i] / bucketLat), y = (long) Math.floor(lons[j] / bucketLon);
                    List<Reading> near = new ArrayList<>();
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                        List<Reading> bucket = buckets.get((x + dx) + ":" + (y + dy));
                        if (bucket != null) near.addAll(bucket);
                    }
                    Estimate estimate = estimate(lats[i], lons[j], near, radius, own);
                    if (estimate.value != null) {
                        if (estimate.own) {
                            if (estimate.nearest < exactD[k]) { exactD[k] = estimate.nearest; exactV[k] = estimate.value; exactG[k] = g; }
                        } else {
                            sumV[k] += estimate.value * estimate.weight;
                            sumW[k] += estimate.weight;
                            if (estimate.weight > topW[k]) { topW[k] = estimate.weight; owner[k] = g; }
                        }
                    } else if (estimate.reason == Reason.INSUFFICIENT) insufficient[k] = true;
                }
            }
        }
        for (int k = 0; k < cells; k++) {
            double value;
            Reading thresholds;
            if (exactG[k] >= 0) { value = exactV[k]; thresholds = groups.get(exactG[k]).get(0); }
            else if (sumW[k] > 0) { value = sumV[k] / sumW[k]; thresholds = groups.get(owner[k]).get(0); }
            else { if (insufficient[k]) states[k] = ZONE_INSUFFICIENT; continue; }
            states[k] = value >= thresholds.high ? ZONE_HIGH : value >= thresholds.medium ? ZONE_MEDIUM : ZONE_LOW;
        }
        return new Raster(rows, columns, states, evaluated, clipped);
    }

    /**
     * Texto de la consulta de un punto del mapa: título y detalle. Con valor indica que es interpolado;
     * sin él, por qué no se asigna nivel. El mismo criterio que public-map.js.
     */
    /**
     * Diseño lógico: (Estimate estimate, int radius, String unit, double medium, double high, boolean simulated) → describe() → String[]; según doc/android_design.md.
     */
    public static String[] describe(Estimate estimate, int radius, String unit, double medium, double high, boolean simulated) {
        String positions = estimate.positions + (estimate.positions == 1 ? " posición" : " posiciones") + " en " + radius + " m";
        if (estimate.value != null) {
            double v = estimate.value;
            String level = v >= high ? "alto" : v >= medium ? "moderado" : "bajo";
            // Con un decimal si al redondear cruzaría un umbral: 59,6 no se enseña como «60 · bajo».
            long rounded = Math.round(v);
            String roundedLevel = rounded >= high ? "alto" : rounded >= medium ? "moderado" : "bajo";
            String shown = roundedLevel.equals(level) ? String.valueOf(rounded)
                : String.format(java.util.Locale.ROOT, "%.1f", v).replace('.', ',');
            return new String[]{"Estimación: " + shown + " " + unit + " · " + level,
                positions + " · la más cercana a " + Math.round(estimate.nearest) + " m. "
                    + (estimate.own ? "Junto a esa medición se muestra su valor medido" : "Valor interpolado, no medido")
                    + (simulated ? " · datos simulados." : ".")};
        }
        if (estimate.reason == Reason.INSUFFICIENT) {
            return new String[]{"Datos insuficientes", positions + "; hacen falta " + MIN_POSITIONS + ". No se asigna nivel de ozono."};
        }
        return new String[]{"Sin datos", (Double.isInfinite(estimate.nearest) ? "No hay mediciones publicadas."
            : "La medición más cercana está a " + Math.round(estimate.nearest) + " m (radio de " + radius + " m).")
            + " Sin datos no significa aire limpio."};
    }

    /**
     * Comprueba si finito.
     * Diseño lógico: (double value) → isFinite() → boolean; según doc/android_design.md.
     */
    private static boolean isFinite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
