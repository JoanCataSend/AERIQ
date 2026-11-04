package com.aeriq.app.data;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import com.aeriq.app.ble.LiveMeasurement;
import org.json.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public final class AeriqFieldClient implements AutoCloseable {
    private final Supplier<String> token;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger pending = new AtomicInteger();
    private volatile boolean closed;

    public AeriqFieldClient(Supplier<String> token) { this.token = token; }

    private JSONObject request(String credential, String action, JSONObject body) throws Exception {
        if (!credential.equals(token.get())) throw new IllegalStateException("La sesión ha cambiado");
        String endpoint = ApiRoutes.FIELD + (action.isEmpty() ? "" : "?action=" + action);
        JsonHttp.Response response = JsonHttp.request(endpoint, body, credential);
        if (!response.ok()) throw new IllegalStateException(response.message());
        return response.json;
    }

    private interface Decoder<T> { T decode(JSONObject row) throws JSONException; }

    private <T> void list(String action, String key, Decoder<T> decoder, BiConsumer<List<T>, String> done) {
        String credential = token.get();
        if (credential == null) {
            done.accept(Collections.emptyList(), "Inicia sesión");
            return;
        }
        worker.execute(() -> {
            List<T> rows = new ArrayList<>();
            String error = null;
            try {
                JSONArray data = request(credential, action, null).getJSONArray(key);
                for (int i = 0; i < data.length(); i++) rows.add(decoder.decode(data.getJSONObject(i)));
            } catch (Exception failure) { error = failure.getMessage(); }
            String message = error;
            main.post(() -> {
                if (!closed && credential.equals(token.get())) done.accept(rows, message);
            });
        });
    }

    public void assignments(BiConsumer<List<OrbAssignment>, String> done) {
        list("", "assignments", OrbAssignment::new, done);
    }

    public void history(BiConsumer<List<StoredReading>, String> done) {
        list("history", "readings", StoredReading::fromJson, done);
    }

    public void upload(LiveMeasurement reading, OrbAssignment assignment, Location location, Consumer<String> done) {
        String credential = token.get();
        if (credential == null) {
            done.accept("No enviado: inicia sesión");
            return;
        }
        if (pending.incrementAndGet() > 30) {
            pending.decrementAndGet();
            done.accept("Cola llena: lectura no guardada");
            return;
        }
        worker.execute(() -> {
            String status;
            try {
                SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
                date.setTimeZone(TimeZone.getTimeZone("UTC"));
                String event = UUID.randomUUID().toString();
                JSONObject body = JsonHttp.object("eventId", event, "deviceId", assignment.id,
                    "campaignId", assignment.campaignId, "assignmentId", assignment.assignmentId,
                    "unit", reading.reading.unit(), "address", reading.address, "type", reading.reading.typeId,
                    "value", reading.reading.value, "capturedAt", date.format(new Date(reading.receivedAtMillis)),
                    "counter", reading.reading.counter, "rssi", reading.rssi);
                if (location != null) {
                    body.put("latitude", location.getLatitude());
                    body.put("longitude", location.getLongitude());
                    body.put("accuracy", location.getAccuracy());
                }
                JSONObject saved;
                try { saved = request(credential, "", body); }
                catch (IOException error) { saved = request(credential, "", body); }
                if (!event.equals(saved.optString("eventId"))) throw new IllegalStateException("Respuesta de guardado incorrecta");
                status = saved.optBoolean("duplicate") ? "Servidor: lectura ya registrada" : "Servidor: lectura guardada";
            } catch (Exception error) {
                status = "No se pudo guardar la lectura: " + error.getMessage();
            } finally {
                pending.decrementAndGet();
            }
            String message = status;
            main.post(() -> {
                if (!closed && credential.equals(token.get())) done.accept(message);
            });
        });
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
    }
}
