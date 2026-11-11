package com.aeriq.app.data;

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
        /** Permiso que concede recibir asignaciones, vincular Orb y enviar lecturas. */
        public static final String PERMISSION_COLLECT = "mediciones.registrar";
        public final long id;
        public final String name, last, email, phone, role, roleName, organization;
        /** Permisos que el servidor calcula a partir del rol (tabla rol_permisos). */
        public final Set<String> permissions;
        public final boolean mustChangePassword;
        private final boolean permissionsFromServer;

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
            mustChangePassword = data.optBoolean("mustChangePassword");
        }

        public String fullName() { return (name + " " + last).trim(); }

        /** La app no decide por el nombre del rol: pregunta si la cuenta tiene el permiso. */
        public boolean can(String permission) { return can(permissionsFromServer, permissions, role, permission); }

        /** Un servidor anterior al esquema v2 solo envía el rol; entonces se usa la regla antigua. */
        static boolean can(boolean fromServer, Set<String> granted, String role, String permission) {
            if (fromServer) return granted.contains(permission);
            return PERMISSION_COLLECT.equals(permission) && ("trabajador".equals(role) || "admin".equals(role));
        }
    }

    public static final class Result {
        public final boolean success;
        public final String message;

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

    public AeriqAuthManager(Context context) { store = new AeriqTokenStore(context.getApplicationContext()); }

    public String sessionToken() { return store.read(); }
    public long savedUserId() { return store.ownerUserId(); }
    public String getLastEmail() { return store.getLastEmail(); }
    public boolean hasSavedToken() { return store.read() != null; }

    public void restore(Consumer<Result> done) {
        String token = store.read();
        if (token == null) {
            done.accept(new Result(false, ""));
            return;
        }
        execute("session", null, true, done);
    }

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

    /** Clears a locally unusable session without sending a remote logout request. */
    public void clearLocalSession() {
        store.clear();
        user = null;
        pendingEmail = store.getLastEmail();
    }

    @Override public void close() {
        closed = true;
        worker.shutdown();
    }
}
