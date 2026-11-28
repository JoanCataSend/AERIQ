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

    /**
     * Diseño lógico: context: Text, token: Text, owner: Text --> AeriqFieldClient() --> Text
     * Descripción: Procesa aeriq field client y devuelve el resultado correspondiente.
     */
    public AeriqFieldClient(Context context, Supplier<String> token, Supplier<Long> owner) {
        this.token = token; this.owner = owner;
        outbox = new ReadingOutbox(context.getApplicationContext());
    }

    /**
     * Diseño lógico: credential: Text, action: Text, body: Text --> request() --> Text
     * Descripción: Envía una solicitud al servicio y devuelve la respuesta validada.
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
     * Diseño lógico: action: Text, key: Text, decoder: Text, BiConsumer<List<T>: Text, done: Text --> list()
     * Descripción: Procesa list y devuelve el resultado correspondiente..
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
     * Diseño lógico: BiConsumer<List<OrbAssignment>: Text, done: Text --> assignments()
     * Descripción: Procesa assignments y devuelve el resultado correspondiente..
     */
    public void assignments(BiConsumer<List<OrbAssignment>, String> done) {
        list("", "assignments", OrbAssignment::new, done);
    }

    /**
     * Diseño lógico: BiConsumer<List<StoredReading>: Text, done: Text --> history()
     * Descripción: Procesa history y devuelve el resultado correspondiente..
     */
    public void history(BiConsumer<List<StoredReading>, String> done) {
        list("history", "readings", StoredReading::fromJson, done);
    }

    /**
     * Diseño lógico: reading: Text, assignment: Text, location: Text, done: Text --> upload()
     * Descripción: Procesa upload y devuelve el resultado correspondiente..
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

    /**
     * Diseño lógico: BiConsumer<CampaignTeam: Text, done: Text --> team()
     * Descripción: Procesa team y devuelve el resultado correspondiente..
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
     * Diseño lógico: action: Text, body: Text, parser: Text, fallback: Text, BiConsumer<T: Text, done: Text --> notices()
     * Descripción: Procesa notices y devuelve el resultado correspondiente..
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

    /**
     * Diseño lógico: BiConsumer<Notices.Inbox: Text, done: Text --> notices()
     * Descripción: Procesa notices y devuelve el resultado correspondiente..
     */
    public void notices(BiConsumer<Notices.Inbox, String> done) {
        notices("notices", null, Notices::inbox, Notices.Inbox.empty(), done);
    }

    /**
     * Diseño lógico: id: Z, BiConsumer<Notices.Conversation: Text, done: Text --> notice()
     * Descripción: Procesa notice y devuelve el resultado correspondiente..
     */
    public void notice(long id, BiConsumer<Notices.Conversation, String> done) {
        notices("notice&id=" + id, null, Notices::conversation, null, done);
    }

    /**
     * Diseño lógico: to: Z, subject: Text, text: Text, BiConsumer<String: Text, done: Text --> createNotice()
     * Descripción: Procesa create notice y devuelve el resultado correspondiente..
     */
    public void createNotice(long to, String subject, String text, BiConsumer<String, String> done) {
        notices("notice-create", JsonHttp.object("to", to, "subject", subject, "message", text),
            data -> data.optString("message", "Aviso enviado."), null, done);
    }

    /**
     * Diseño lógico: id: Z, text: Text, BiConsumer<String: Text, done: Text --> replyNotice()
     * Descripción: Procesa reply notice y devuelve el resultado correspondiente..
     */
    public void replyNotice(long id, String text, BiConsumer<String, String> done) {
        notices("notice-reply", JsonHttp.object("id", id, "message", text),
            data -> data.optString("message", "Respuesta enviada."), null, done);
    }

    /**
     * Diseño lógico: id: Z, BiConsumer<String: Text, done: Text --> closeNotice()
     * Descripción: Procesa close notice y devuelve el resultado correspondiente..
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
     * Diseño lógico: deviceId: Text, name: Text, address: Text, workerId: Z, simulated: B, done: Text --> assignOrb()
     * Descripción: Procesa assign orb y devuelve el resultado correspondiente..
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
     * Diseño lógico: assignment: Text, address: Text, percent: Z, capturedAtMillis: Z --> battery()
     * Descripción: Procesa battery y devuelve el resultado correspondiente..
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
     * Diseño lógico: credential: Text, account: Z, status: Text, done: Text --> postStatus()
     * Descripción: Procesa post status y devuelve el resultado correspondiente..
     */
    private void postStatus(String credential, long account, String status, Consumer<String> done) {
        main.post(() -> {
            if (!closed && credential.equals(token.get()) && account == owner.get()) done.accept(status);
        });
    }

    /**
     * Diseño lógico: done: Text --> flushPending()
     * Descripción: Procesa flush pending y devuelve el resultado correspondiente..
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
     * Diseño lógico: done: Text --> retryPending()
     * Descripción: Procesa retry pending y devuelve el resultado correspondiente..
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
     * Diseño lógico: close()
     * Descripción: Describe la operación realizada y su resultado.
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
