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

    /** Consumes the old boolean-only biometric flag; it is replaced by a Keystore enrollment. */
    public boolean consumeLegacyFingerprint(long userId) {
        if (userId <= 0) return false;
        String key = "huella_usuario_" + userId;
        boolean enabled = prefs.getBoolean(key, false);
        if (prefs.contains(key)) prefs.edit().remove(key).apply();
        return enabled;
    }

    /** Avisos cuando el ozono de tu zona pasa a moderado o alto. */
    public boolean ozoneAlerts() { return prefs.getBoolean("avisos", true); }
    public void setOzoneAlerts(boolean enabled) { prefs.edit().putBoolean("avisos", enabled).apply(); }

    /**
     * Tutorial de la primera vez (HU33): true cuando ya se ha terminado o saltado. Vive en los datos de la app,
     * así que solo vuelve a salir si se borran los datos o se reinstala.
     */
    public boolean tutorialSeen() { return prefs.getBoolean("tutorial_visto", false); }
    public void setTutorialSeen() { prefs.edit().putBoolean("tutorial_visto", true).apply(); }

    /** Adjuntar la posición GPS a tus lecturas (recorrido y distancia, solo visibles para ti). */
    public boolean saveRoute() { return prefs.getBoolean("recorrido", true); }
    public void setSaveRoute(boolean enabled) { prefs.edit().putBoolean("recorrido", enabled).apply(); }
}
