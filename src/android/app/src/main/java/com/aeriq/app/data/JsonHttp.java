package com.aeriq.app.data;
/*
 * Fichero: JsonHttp.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
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

      /**
       * Diseño lógico: status: Z, json: Text --> Response() --> Text
       * Descripción: Procesa response y devuelve el resultado correspondiente.
       */
        Response(int status, JSONObject json) {
            this.status = status;
            this.json = json;
        }

        /**
         * Diseño lógico: ok() --> B
         * Descripción: Procesa ok y devuelve el resultado correspondiente.
         */
        boolean ok() { return status >= 200 && status < 300; }

        /**
         * Diseño lógico: message() --> Text
         * Descripción: Procesa message y devuelve el resultado correspondiente.
         */
        String message() {
            return json.optString("error", json.optString("message", "HTTP " + status));
        }
    }

    /**
     * Diseño lógico: endpoint: Text, body: Text, token: Text --> request() --> Text
     * Descripción: Envía una solicitud al servicio y devuelve la respuesta validada.
     */
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

              /**
               * Diseño lógico: output: Text --> try() --> Text
               * Descripción: Procesa try y devuelve el resultado correspondiente.
               */
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IOException("Respuesta vacía (HTTP " + status + ")");

          /**
           * Diseño lógico: reader: Text, StandardCharsets.UTF_8): Text --> try() --> Text
           * Descripción: Procesa try y devuelve el resultado correspondiente.
           */
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

    /**
     * Diseño lógico: pairs: Text --> object() --> Text
     * Descripción: Procesa object y devuelve el resultado correspondiente.
     */
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
