package com.aeriq.app.data;
/*
 * Fichero: AeriqAuthManager.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class AeriqAuthManager implements AutoCloseable {
    public static final class User {
        /**
         * Diseño lógico: data: Text --> User() --> Text
         * Descripción: Procesa user y devuelve el resultado correspondiente.
         */
        User(JSONObject data) throws JSONException {
            id = data.getLong("id");
            name = data.optString("name");
            last = data.optString("last");
            email = data.getString("email");
            phone = data.optString("phone");
            role = data.optString("role", "visitante");
            roleName = data.optString("roleName", role);
            JSONArray granted = data.optJSONArray("permissions");
            Set<String> list = new HashSet<>();
            if (granted != null) for (int i = 0; i < granted.length(); i++) list.add(granted.optString(i));
            permissions = Collections.unmodifiableSet(list);
            permissionsFromServer = granted != null;
            organization = data.optString("organization");
            campaign = data.isNull("campaign") ? "" : data.optString("campaign");
            mustChangePassword = data.optBoolean("mustChangePassword");
        }

        /**
         * Diseño lógico: fullName() --> Text
         * Descripción: Procesa full name y devuelve el resultado correspondiente.
         */
        public String fullName() { return (name + " " + last).trim(); }

        /**
         * Diseño lógico: collects() --> B
         * Descripción: Procesa collects y devuelve el resultado correspondiente.
         */
        public boolean collects() { return can(PERMISSION_COLLECT); }

        /**
         * Diseño lógico: manages() --> B
         * Descripción: Procesa manages y devuelve el resultado correspondiente.
         */
        public boolean manages() { return can(PERMISSION_TEAM) || can(PERMISSION_CAMPAIGNS); }

        /**
         * Diseño lógico: permission: Text --> can() --> B
         * Descripción: Procesa can y devuelve el resultado correspondiente.
         */
        public boolean can(String permission) { return can(permissionsFromServer, permissions, role, permission); }

        /**
         * Diseño lógico: fromServer: B, granted: Text, role: Text, permission: Text --> can() --> B
         * Descripción: Procesa can y devuelve el resultado correspondiente.
         */
        static boolean can(boolean fromServer, Set<String> granted, String role, String permission) {
            if (fromServer) return granted.contains(permission);
            return PERMISSION_COLLECT.equals(permission) && ("trabajador".equals(role) || "admin".equals(role));
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;

        /**
         * Diseño lógico: success: B, message: Text --> Result() --> Text
         * Descripción: Procesa result y devuelve el resultado correspondiente.
         */
        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private final AeriqTokenStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    public volatile User user;
    public boolean busy;
    public String pendingEmail = "";

    /**
     * Diseño lógico: context: Text --> AeriqAuthManager() --> Text
     * Descripción: Procesa aeriq auth manager y devuelve el resultado correspondiente.
     */
    public AeriqAuthManager(Context context) { store = new AeriqTokenStore(context.getApplicationContext()); }

    /**
     * Diseño lógico: sessionToken() --> Text
     * Descripción: Procesa session token y devuelve el resultado correspondiente.
     */
    public String sessionToken() { return store.read(); }
    /**
     * Diseño lógico: savedUserId() --> Z
     * Descripción: Procesa saved user id y devuelve el resultado correspondiente.
     */
    public long savedUserId() { return store.ownerUserId(); }
    /**
     * Diseño lógico: getLastEmail() --> Text
     * Descripción: Procesa get last email y devuelve el resultado correspondiente.
     */
    public String getLastEmail() { return store.getLastEmail(); }
    /**
     * Diseño lógico: hasSavedToken() --> B
     * Descripción: Procesa has saved token y devuelve el resultado correspondiente.
     */
    public boolean hasSavedToken() { return store.read() != null; }

    /**
     * Diseño lógico: done: Text --> restore()
     * Descripción: Procesa restore y devuelve el resultado correspondiente..
     */
    public void restore(Consumer<Result> done) {
        String token = store.read();
        if (token == null) {
            done.accept(new Result(false, ""));
            return;
        }
        execute("session", null, true, done);
    }

    /**
     * Diseño lógico: action: Text, done: Text, fields: Text --> submit()
     * Descripción: Procesa submit y devuelve el resultado correspondiente..
     */
    public void submit(String action, Consumer<Result> done, Object... fields) {
        boolean authorized = "profile".equals(action) || "change-password".equals(action);
        for (int i = 0; i < fields.length; i += 2) {
            if ("email".equals(fields[i]) && fields[i + 1] != null) {
                pendingEmail = fields[i + 1].toString().trim().toLowerCase(Locale.ROOT);
                fields[i + 1] = pendingEmail;
            }
        }
        execute(action, JsonHttp.object(fields), authorized, done);
    }

    /**
     * Diseño lógico: action: Text, body: Text, authorized: B, done: Text --> execute()
     * Descripción: Procesa execute y devuelve el resultado correspondiente..
     */
    private void execute(String action, JSONObject body, boolean authorized, Consumer<Result> done) {
        if (busy || closed) return;
        busy = true;
        worker.execute(() -> {
            User returnedUser = null;
            boolean invalidSession = false;
            Result result;
            try {
                JsonHttp.Response response = JsonHttp.request(ApiRoutes.AUTH + "?action=" + action,
                    body, authorized ? store.read() : null);
                if (response.ok()) {
                    JSONObject userJson = response.json.optJSONObject("user");
                    if (userJson != null) returnedUser = new User(userJson);
                    String token = response.json.optString("token");
                    if (!token.isEmpty()) store.write(token, returnedUser == null ? 0L : returnedUser.id);
                    else if ("session".equals(action) && returnedUser != null) store.associateOwner(returnedUser.id);
                    result = new Result(true, response.json.optString("message", "Operación completada"));
                } else {
                    invalidSession = authorized && (response.status == 401 || "session".equals(action));
                    if (invalidSession) store.clear();
                    result = new Result(false, response.message());
                }
            } catch (Exception error) {
                result = new Result(false, "No se pudo conectar con AERIQ: " + error.getMessage());
            }
            User received = returnedUser;
            boolean clearUser = invalidSession;
            Result outcome = result;
            main.post(() -> {
                if (closed) return;
                busy = false;
                if (clearUser) user = null;
                if (received != null) {
                    user = received;
                    store.saveLastEmail(received.email);
                }
                done.accept(outcome);
            });
        });
    }

    /**
     * Diseño lógico: logout()
     * Descripción: Procesa logout y devuelve el resultado correspondiente..
     */
    public void logout() {
        if (busy || closed) return;
        String token = store.read();
        store.clear();
        user = null;
        pendingEmail = "";
        if (token != null) worker.execute(() -> {
            try { JsonHttp.request(ApiRoutes.AUTH + "?action=logout", new JSONObject(), token); }
            catch (Exception ignored) { /* La sesión local ya se ha cerrado. */ }
        });
    }

    /**
     * Diseño lógico: clearLocalSession()
     * Descripción: Procesa clear local session y devuelve el resultado correspondiente..
     */
    public void clearLocalSession() {
        store.clear();
        user = null;
        pendingEmail = store.getLastEmail();
    }

    /**
     * Diseño lógico: close()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Override public void close() {
        closed = true;
        worker.shutdown();
    }
}
