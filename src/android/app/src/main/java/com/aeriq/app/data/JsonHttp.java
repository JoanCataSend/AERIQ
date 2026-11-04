package com.aeriq.app.data;

import org.json.JSONObject;
import org.json.JSONException;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class JsonHttp {
    static final class Response {
        final int status;
        final JSONObject json;

        Response(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }

        boolean ok() { return status >= 200 && status < 300; }
        String message() {
            return json.optString("error", json.optString("message", "HTTP " + status));
        }
    }

    static Response request(String endpoint, JSONObject body, String token) throws IOException {
        URL url = new URL(endpoint);
        if (!"https".equals(url.getProtocol())) throw new IOException("La conexión requiere HTTPS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            if (token != null) connection.setRequestProperty("X-AERIQ-Token", token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IOException("Respuesta vacía (HTTP " + status + ")");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                StringBuilder content = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, count);
                    if (content.length() > 8_000_000) throw new IOException("Respuesta demasiado grande");
                }
                return new Response(status, new JSONObject(content.toString()));
            } catch (JSONException error) {
                throw new IOException("Respuesta inválida (HTTP " + status + ")", error);
            }
        } finally {
            connection.disconnect();
        }
    }

    static JSONObject object(Object... pairs) {
        JSONObject object = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2) object.put((String) pairs[i], pairs[i + 1]);
            return object;
        } catch (JSONException error) {
            throw new IllegalArgumentException(error);
        }
    }
}
