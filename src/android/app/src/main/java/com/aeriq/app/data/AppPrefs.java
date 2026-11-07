package com.aeriq.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/** Ajustes locales del móvil (no se envían al servidor). */
public final class AppPrefs {
    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("aeriq_ajustes", Context.MODE_PRIVATE);
    }

    /** Nombre que el usuario da a su sensor en este móvil; null si no ha puesto ninguno. */
    public String alias(String address) {
        if (address == null || address.isEmpty()) return null;
        String value = prefs.getString("alias_" + address.toUpperCase(Locale.ROOT), null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    public void setAlias(String address, String name) {
        if (address == null || address.isEmpty()) return;
        prefs.edit().putString("alias_" + address.toUpperCase(Locale.ROOT), name == null ? "" : name.trim()).apply();
    }

    /** Pedir la huella para abrir la sesión guardada en lugar de entrar automáticamente. */
    public boolean fingerprint() { return prefs.getBoolean("huella", false); }
    public void setFingerprint(boolean enabled) { prefs.edit().putBoolean("huella", enabled).apply(); }

    /** Avisos cuando el ozono de tu zona pasa a moderado o alto. */
    public boolean ozoneAlerts() { return prefs.getBoolean("avisos", true); }
    public void setOzoneAlerts(boolean enabled) { prefs.edit().putBoolean("avisos", enabled).apply(); }

    /** Adjuntar la posición GPS a tus lecturas (recorrido y distancia, solo visibles para ti). */
    public boolean saveRoute() { return prefs.getBoolean("recorrido", true); }
    public void setSaveRoute(boolean enabled) { prefs.edit().putBoolean("recorrido", enabled).apply(); }
}
