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

    private static List<Place> photon(String term, int limit) throws Exception {
        URL url = new URL("https://photon.komoot.io/api/?limit=" + limit + "&q="
            + URLEncoder.encode(term, "UTF-8"));
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
            JSONArray features = new JSONObject(content.toString()).getJSONArray("features");
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
        } finally {
            connection.disconnect();
        }
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
