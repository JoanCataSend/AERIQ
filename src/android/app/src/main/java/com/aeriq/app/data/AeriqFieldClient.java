package com.aeriq.app.data;
/*
 * Fichero: AeriqFieldClient.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
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

    /**
     * Gestiona petición.
     * Diseño lógico: (String credential, String action, JSONObject body) → request() → JSONObject; según doc/android_design.md.
     */
    private JSONObject request(String credential, String action, JSONObject body) throws Exception {
        if (!credential.equals(token.get())) throw new IllegalStateException("La sesión ha cambiado");
        String endpoint = ApiRoutes.FIELD + (action.isEmpty() ? "" : "?action=" + action);
        JsonHttp.Response response = JsonHttp.request(endpoint, body, credential);
        if (!response.ok()) throw new IllegalStateException(response.message());
        return response.json;
    }

    private interface Decoder<T> { T decode(JSONObject row) throws JSONException; }

    /**
     * Gestiona list.
     * Diseño lógico: (String action, String key, Decoder<T> decoder, BiConsumer<List<T>, String> done) → list() → <T> void; según doc/android_design.md.
     */
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

    /**
     * Gestiona asignaciones.
     * Diseño lógico: (BiConsumer<List<OrbAssignment>, String> done) → assignments() → void; según doc/android_design.md.
     */
    public void assignments(BiConsumer<List<OrbAssignment>, String> done) {
        list("", "assignments", OrbAssignment::new, done);
    }

    /**
     * Gestiona historial.
     * Diseño lógico: (BiConsumer<List<StoredReading>, String> done) → history() → void; según doc/android_design.md.
     */
    public void history(BiConsumer<List<StoredReading>, String> done) {
        list("history", "readings", StoredReading::fromJson, done);
    }

    /**
     * Gestiona upload.
     * Diseño lógico: (LiveMeasurement reading, OrbAssignment assignment, Location location, Consumer<String> done) → upload() → void; según doc/android_design.md.
     */
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

    /** Admin de campaña: operadores y Orb de su campaña (mobile-team.php, mismas reglas que el panel web). */
    /**
     * Diseño lógico: (BiConsumer<CampaignTeam, String> done) → team() → void; según doc/android_design.md.
     */
    public void team(BiConsumer<CampaignTeam, String> done) {
        String credential = token.get();
        if (credential == null) { done.accept(CampaignTeam.empty(), "Inicia sesión"); return; }
        worker.execute(() -> {
            CampaignTeam team = CampaignTeam.empty();
            String error = null;
            try {
                JsonHttp.Response response = JsonHttp.request(ApiRoutes.TEAM, null, credential);
                if (!response.ok()) throw new IllegalStateException(response.message());
                team = new CampaignTeam(response.json);
            } catch (Exception failure) { error = failure.getMessage(); }
            CampaignTeam result = team;
            String message = error;
            main.post(() -> { if (!closed && credential.equals(token.get())) done.accept(result, message); });
        });
    }

    // ------------------------------------------------------------ Avisos (HU36, mobile-avisos.php)

    private interface Parser<T> { T parse(JSONObject data) throws JSONException; }

    /**
     * Gestiona avisos.
     * Diseño lógico: (String action, JSONObject body, Parser<T> parser, T fallback, BiConsumer<T, String> done) → notices() → <T> void; según doc/android_design.md.
     */
    private <T> void notices(String action, JSONObject body, Parser<T> parser, T fallback, BiConsumer<T, String> done) {
        String credential = token.get();
        if (credential == null) { done.accept(fallback, "Inicia sesión"); return; }
        worker.execute(() -> {
            T result = fallback;
            String error = null;
            try {
                JsonHttp.Response response = JsonHttp.request(ApiRoutes.NOTICES + "?action=" + action, body, credential);
                if (!response.ok()) throw new IllegalStateException(response.message());
                result = parser.parse(response.json);
            } catch (Exception failure) { error = failure.getMessage(); }
            T value = result;
            String message = error;
            main.post(() -> { if (!closed && credential.equals(token.get())) done.accept(value, message); });
        });
    }

    /** Bandeja: tus avisos, a quién puedes escribir y cuántos tienen algo nuevo. */
    /**
     * Diseño lógico: (BiConsumer<Notices.Inbox, String> done) → notices() → void; según doc/android_design.md.
     */
    public void notices(BiConsumer<Notices.Inbox, String> done) {
        notices("notices", null, Notices::inbox, Notices.Inbox.empty(), done);
    }

    /** Un aviso con sus mensajes; abrirlo lo marca como visto. */
    /**
     * Diseño lógico: (long id, BiConsumer<Notices.Conversation, String> done) → notice() → void; según doc/android_design.md.
     */
    public void notice(long id, BiConsumer<Notices.Conversation, String> done) {
        notices("notice&id=" + id, null, Notices::conversation, null, done);
    }

    /** Crea un aviso. El servidor comprueba que puedes escribir a esa persona. Devuelve el mensaje del servidor. */
    /**
     * Diseño lógico: (long to, String subject, String text, BiConsumer<String, String> done) → createNotice() → void; según doc/android_design.md.
     */
    public void createNotice(long to, String subject, String text, BiConsumer<String, String> done) {
        notices("notice-create", JsonHttp.object("to", to, "subject", subject, "message", text),
            data -> data.optString("message", "Aviso enviado."), null, done);
    }

    /** Responde a un aviso (si estaba cerrado, se vuelve a abrir). */
    /**
     * Diseño lógico: (long id, String text, BiConsumer<String, String> done) → replyNotice() → void; según doc/android_design.md.
     */
    public void replyNotice(long id, String text, BiConsumer<String, String> done) {
        notices("notice-reply", JsonHttp.object("id", id, "message", text),
            data -> data.optString("message", "Respuesta enviada."), null, done);
    }

    /**
     * Cierra aviso.
     * Diseño lógico: (long id, BiConsumer<String, String> done) → closeNotice() → void; según doc/android_design.md.
     */
    public void closeNotice(long id, BiConsumer<String, String> done) {
        notices("notice-close", JsonHttp.object("id", id),
            data -> data.optString("message", "Aviso cerrado."), null, done);
    }

    /**
     * Admin de campaña: registra el Orb (deviceId null) o lo reasigna, siempre a un operador de su campaña.
     * El servidor aplica las reglas del panel web: un Orb por operador y la asignación anterior se cierra.
     */
    /**
     * Diseño lógico: (Long deviceId, String name, String address, long workerId, boolean simulated, Consumer<String> done) → assignOrb() → void; según doc/android_design.md.
     */
    public void assignOrb(Long deviceId, String name, String address, long workerId, boolean simulated, Consumer<String> done) {
        String credential = token.get();
        if (credential == null) { done.accept("Inicia sesión"); return; }
        worker.execute(() -> {
            String error = null;
            try {
                JSONObject body = JsonHttp.object("name", name, "address", address, "worker", workerId,
                    "active", true, "simulated", simulated);
                if (deviceId != null) body.put("id", deviceId);
                JsonHttp.Response response = JsonHttp.request(ApiRoutes.TEAM, body, credential);
                if (!response.ok()) throw new IllegalStateException(response.message());
            } catch (Exception failure) { error = failure.getMessage(); }
            String message = error;
            main.post(() -> { if (!closed && credential.equals(token.get())) done.accept(message); });
        });
    }

    /**
     * Envía la batería del Orb asignado. No pasa por la cola de lecturas: si falla, el Orb
     * la vuelve a anunciar en el siguiente ciclo y se reintenta más tarde.
     */
    /**
     * Diseño lógico: (OrbAssignment assignment, String address, int percent, long capturedAtMillis) → battery() → void; según doc/android_design.md.
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

    /**
     * Envía estado.
     * Diseño lógico: (String credential, long account, String status, Consumer<String> done) → postStatus() → void; según doc/android_design.md.
     */
    private void postStatus(String credential, long account, String status, Consumer<String> done) {
        main.post(() -> {
            if (!closed && credential.equals(token.get()) && account == owner.get()) done.accept(status);
        });
    }

    /**
     * Gestiona flush pending.
     * Diseño lógico: (Consumer<String> done) → flushPending() → void; según doc/android_design.md.
     */
    public void flushPending(Consumer<String> done) {
        String credential = token.get();
        long account = owner.get();
        if (closed || credential == null || account == 0 || !syncing.compareAndSet(false, true)) return;
        sender.execute(() -> {
            int saved = 0, discarded = 0;
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
                        // HU35: el servidor la guarda, pero como no válida (fuera de rango o anómala).
                        if (!response.json.optBoolean("valid", true)) ++discarded;
                    } catch (Exception failure) { error = failure.getMessage(); break; }
                }
                int remaining = outbox.count(account, false), blocked = outbox.count(account, true);
                String status = (saved > 0 ? "Servidor: " + saved + " lecturas confirmadas"
                    + (discarded > 0 ? " (" + discarded + " descartadas por estar fuera de rango o ser anómalas)" : "") + ". " : "")
                    + remaining + " pendientes · " + blocked + " requieren revisión";
                if (!error.isEmpty()) status += ". " + error;
                if (remaining == 0 && blocked == 0 && saved == 0) return;
                postStatus(credential, account, status, done);
            } catch (Exception failure) {
                postStatus(credential, account, "Error de sincronización: " + failure.getMessage(), done);
            } finally { syncing.set(false); }
        });
    }

    /**
     * Gestiona retry pending.
     * Diseño lógico: (Consumer<String> done) → retryPending() → void; según doc/android_design.md.
     */
    public void retryPending(Consumer<String> done) {
        long account = owner.get();
        if (closed || account == 0) return;
        writer.execute(() -> {
            outbox.retry(account);
            main.post(() -> { if (!closed && account == owner.get()) flushPending(done); });
        });
    }

    /**
     * Cierra.
     * Diseño lógico: (sin parámetros) → close() → void; según doc/android_design.md.
     */
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
