package com.aeriq.app.data;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Busca cualquier ciudad, pueblo, calle o lugar del mundo.
 * Usa Photon (geocodificador abierto basado en OpenStreetMap, sin clave de API) y,
 * si no responde, el Geocoder nativo de Android.
 */
public final class PlaceSearch {
    public static final class Place {
        public final String name, detail;
        public final double latitude, longitude;
        /** Caja [norte, este, sur, oeste] o null si el servicio no la devuelve. */
        public final double[] box;
        public final String kind;

        Place(String name, String detail, double latitude, double longitude, double[] box, String kind) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
            this.box = box;
            this.kind = kind == null ? "" : kind;
        }

        public double zoom() {
            switch (kind) {
                case "country": return 6;
                case "state": case "region": return 8;
                case "county": case "district": return 10;
                case "city": case "town": return 13;
                case "village": case "suburb": case "locality": return 14;
                case "street": return 16;
                case "house": return 18;
                default: return 15;
            }
        }
    }

    private PlaceSearch() {}

    /** Ciudades ya resueltas, por coordenadas redondeadas a unos 100 m: así no se repite la consulta. */
    private static final Map<String, String> CITIES = new java.util.concurrent.ConcurrentHashMap<>();

    private static String cityKey(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude);
    }

    /** Ciudad ya conocida para esas coordenadas, sin consultar nada; null si aún no se ha resuelto. */
    public static String cachedCity(double latitude, double longitude) { return CITIES.get(cityKey(latitude, longitude)); }

    /**
     * Ciudad o pueblo de unas coordenadas (sin calle). Primero el geocodificador del sistema y, si no responde,
     * Photon. Devuelve null si ninguno lo sabe. Hace red: llamar fuera del hilo principal.
     */
    public static String city(Context context, double latitude, double longitude) {
        String key = cityKey(latitude, longitude);
        String known = CITIES.get(key);
        if (known != null) return known;
        String name = null;
        try { name = androidCity(context, latitude, longitude); } catch (Exception ignored) { }
        if (name == null) try { name = photonCity(latitude, longitude); } catch (Exception ignored) { }
        if (name != null) CITIES.put(key, name);
        return name;
    }

    @SuppressWarnings("deprecation")
    private static String androidCity(Context context, double latitude, double longitude) throws IOException {
        if (!Geocoder.isPresent()) return null;
        List<Address> found = new Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1);
        if (found == null || found.isEmpty()) return null;
        Address address = found.get(0);
        for (String value : new String[]{address.getLocality(), address.getSubAdminArea()}) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private static String photonCity(double latitude, double longitude) throws Exception {
        JSONArray features = new JSONObject(get(new URL(String.format(Locale.ROOT,
            "https://photon.komoot.io/reverse?limit=1&lat=%.5f&lon=%.5f", latitude, longitude)))).getJSONArray("features");
        if (features.length() == 0) return null;
        JSONObject properties = features.getJSONObject(0).getJSONObject("properties");
        String type = properties.optString("type", "");
        if (type.equals("city") || type.equals("town") || type.equals("village")) {
            String name = properties.optString("name", "");
            if (!name.isEmpty()) return name;
        }
        for (String key : new String[]{"city", "town", "village", "county"}) {
            String value = properties.optString(key, "");
            if (!value.isEmpty()) return value;
        }
        return null;
    }

    public static List<Place> search(Context context, String query, int limit) throws IOException {
        String term = query.trim();
        if (term.isEmpty()) return Collections.emptyList();
        try {
            List<Place> places = photon(term, limit);
            if (!places.isEmpty()) return places;
        } catch (Exception ignored) {
            // Sin conexión con Photon: se intenta con el geocodificador del sistema.
        }
        return android(context, term, limit);
    }

    private static String get(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AERIQ-Android/1.0");
            if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
            StringBuilder content = new StringBuilder();
            try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, count);
                    if (content.length() > 1_000_000) throw new IOException("Respuesta demasiado grande");
                }
            }
            return content.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static List<Place> photon(String term, int limit) throws Exception {
        URL url = new URL("https://photon.komoot.io/api/?limit=" + limit + "&q="
            + URLEncoder.encode(term, "UTF-8"));
        JSONArray features = new JSONObject(get(url)).getJSONArray("features");
        List<Place> places = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject properties = feature.getJSONObject("properties");
            JSONArray point = feature.getJSONObject("geometry").getJSONArray("coordinates");
            String name = properties.optString("name", properties.optString("street", ""));
            if (name.isEmpty()) continue;
            List<String> parts = new ArrayList<>();
            for (String key : new String[]{"city", "county", "state", "country"}) {
                String value = properties.optString(key, "");
                if (!value.isEmpty() && !value.equals(name) && !parts.contains(value)) parts.add(value);
            }
            String detail = String.join(", ", parts);
            if (!seen.add(name + "|" + detail)) continue;
            double[] box = null;
            JSONArray extent = properties.optJSONArray("extent");
            if (extent != null && extent.length() == 4) {
                // Photon: [oeste, norte, este, sur]
                box = new double[]{extent.getDouble(1), extent.getDouble(2), extent.getDouble(3), extent.getDouble(0)};
            }
            places.add(new Place(name, detail, point.getDouble(1), point.getDouble(0), box,
                properties.optString("type", properties.optString("osm_value", ""))));
        }
        return places;
    }

    @SuppressWarnings("deprecation")
    private static List<Place> android(Context context, String term, int limit) throws IOException {
        if (!Geocoder.isPresent()) throw new IOException("No hay servicio de búsqueda disponible");
        List<Address> addresses = new Geocoder(context, Locale.getDefault()).getFromLocationName(term, limit);
        List<Place> places = new ArrayList<>();
        if (addresses == null) return places;
        for (Address address : addresses) {
            if (!address.hasLatitude() || !address.hasLongitude()) continue;
            String name = address.getLocality() != null ? address.getLocality()
                : address.getFeatureName() != null ? address.getFeatureName() : term;
            List<String> parts = new ArrayList<>();
            for (String value : new String[]{address.getSubAdminArea(), address.getAdminArea(), address.getCountryName()}) {
                if (value != null && !value.equals(name) && !parts.contains(value)) parts.add(value);
            }
            places.add(new Place(name, String.join(", ", parts), address.getLatitude(), address.getLongitude(),
                null, address.getLocality() != null ? "city" : ""));
        }
        return places;
    }
}
