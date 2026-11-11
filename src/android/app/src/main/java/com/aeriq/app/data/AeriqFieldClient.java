package com.aeriq.app.data;

import android.location.Location;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.aeriq.app.ble.LiveMeasurement;
import org.json.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

public final class AeriqFieldClient implements AutoCloseable {
    private final Supplier<String> token;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private final Supplier<Long> owner;
    private final ReadingOutbox outbox;
    private volatile boolean closed;

    public AeriqFieldClient(Context context, Supplier<String> token, Supplier<Long> owner) {
        this.token = token; this.owner = owner;
        outbox = new ReadingOutbox(context.getApplicationContext());
    }

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

    public void linkSensor(String address, String name, BiConsumer<List<OrbAssignment>, String> done) {
        String credential = token.get();
        if (credential == null) { done.accept(Collections.emptyList(), "Inicia sesión"); return; }
        worker.execute(() -> {
            List<OrbAssignment> rows = new ArrayList<>();
            String error = null;
            try {
                JsonHttp.Response response = JsonHttp.request(ApiRoutes.BASE + "sensor-link.php",
                    JsonHttp.object("address", address, "name", name), credential);
                if (!response.ok()) throw new IllegalStateException(response.message());
                JSONArray data = response.json.getJSONArray("assignments");
                for (int i = 0; i < data.length(); i++) rows.add(new OrbAssignment(data.getJSONObject(i)));
            } catch (Exception failure) { error = failure.getMessage(); }
            String message = error;
            main.post(() -> {
                if (!closed && credential.equals(token.get())) done.accept(rows, message);
            });
        });
    }

    public void upload(LiveMeasurement reading, OrbAssignment assignment, Location location, Consumer<String> done) {
        String credential = token.get();
        long account = owner.get();
        if (credential == null || account == 0) {
            done.accept("No guardado: inicia sesión");
            return;
        }
        if (assignment.simulated) {
            done.accept("No guardado: selecciona un sensor real");
            return;
        }
        // Persistir no espera a la red: un timeout HTTP no retrasa la captura BLE.
        writer.execute(() -> {
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
                outbox.add(account, event, body.toString());
                main.post(() -> {
                    if (!closed && credential.equals(token.get()) && account == owner.get()) {
                        done.accept("Lectura guardada en el móvil; sincronizando…");
                        flushPending(done);
                    }
                });
            } catch (Exception error) {
                postStatus(credential, account, "Error al guardar en el móvil: " + error.getMessage(), done);
            }
        });
    }

    /**
     * Envía la batería del Orb asignado. No pasa por la cola de lecturas: si falla, el Orb
     * la vuelve a anunciar en el siguiente ciclo y se reintenta más tarde.
     */
    public void battery(OrbAssignment assignment, String address, int percent, long capturedAtMillis) {
        String credential = token.get();
        if (credential == null || assignment == null || assignment.simulated) return;
        sender.execute(() -> {
            try {
                SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
                date.setTimeZone(TimeZone.getTimeZone("UTC"));
                JsonHttp.request(ApiRoutes.FIELD + "?action=battery", JsonHttp.object("deviceId", assignment.id,
                    "address", address, "percent", percent, "capturedAt", date.format(new Date(capturedAtMillis))), credential);
            } catch (Exception ignored) {
                // Dato de estado, no medición: no se guarda en la cola ni se avisa al usuario.
            }
        });
    }

    private void postStatus(String credential, long account, String status, Consumer<String> done) {
        main.post(() -> {
            if (!closed && credential.equals(token.get()) && account == owner.get()) done.accept(status);
        });
    }

    public void flushPending(Consumer<String> done) {
        String credential = token.get();
        long account = owner.get();
        if (closed || credential == null || account == 0 || !syncing.compareAndSet(false, true)) return;
        sender.execute(() -> {
            int saved = 0;
            String error = "";
            try {
                for (ReadingOutbox.Entry entry : outbox.next(account)) {
                    if (closed || !credential.equals(token.get()) || account != owner.get()) break;
                    try {
                        JsonHttp.Response response = JsonHttp.request(ApiRoutes.FIELD, new JSONObject(entry.payload), credential);
                        if (!credential.equals(token.get()) || account != owner.get()) break;
                        if (!response.ok()) {
                            error = "HTTP " + response.status + ": " + response.message();
                            // No bloquear toda la cola por una asignación borrada/revocada.
                            if (response.status == 403 || response.status == 409 || response.status == 422) {
                                outbox.block(account, entry.event, error);
                                continue;
                            }
                            break;
                        }
                        if (!entry.event.equals(response.json.optString("eventId"))) throw new IOException("Confirmación del servidor incorrecta");
                        outbox.acknowledge(account, entry.event);
                        ++saved;
                    } catch (Exception failure) { error = failure.getMessage(); break; }
                }
                int remaining = outbox.count(account, false), blocked = outbox.count(account, true);
                String status = (saved > 0 ? "Servidor: " + saved + " lecturas confirmadas. " : "")
                    + remaining + " pendientes · " + blocked + " requieren revisión";
                if (!error.isEmpty()) status += ". " + error;
                if (remaining == 0 && blocked == 0 && saved == 0) return;
                postStatus(credential, account, status, done);
            } catch (Exception failure) {
                postStatus(credential, account, "Error de sincronización: " + failure.getMessage(), done);
            } finally { syncing.set(false); }
        });
    }

    public void retryPending(Consumer<String> done) {
        long account = owner.get();
        if (closed || account == 0) return;
        writer.execute(() -> {
            outbox.retry(account);
            main.post(() -> { if (!closed && account == owner.get()) flushPending(done); });
        });
    }

    @Override public void close() {
        closed = true;
        worker.shutdownNow();
        // Las escrituras ya recibidas se completan incluso si se destruye la Activity.
        writer.shutdown();
        sender.shutdown();
        CompletableFuture.runAsync(() -> {
            try {
                if (writer.awaitTermination(60, TimeUnit.SECONDS) && sender.awaitTermination(60, TimeUnit.SECONDS)) outbox.close();
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
    }
}
