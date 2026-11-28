package com.aeriq.app.data;
/*
 * Fichero: AppPrefs.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

/**
 * Diseño lógico: context: Text --> AppPrefs() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
    public AppPrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("aeriq_ajustes", Context.MODE_PRIVATE);
    }

    /**
     * Diseño lógico: address: Text --> alias() --> Text
     * Descripción: Procesa alias y devuelve el resultado correspondiente.
     */
    public String alias(String address) {
        if (address == null || address.isEmpty()) return null;
        String value = prefs.getString("alias_" + address.toUpperCase(Locale.ROOT), null);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    /**
     * Diseño lógico: address: Text, name: Text --> setAlias()
     * Descripción: Procesa set alias y devuelve el resultado correspondiente..
     */
    public void setAlias(String address, String name) {
        if (address == null || address.isEmpty()) return;
        prefs.edit().putString("alias_" + address.toUpperCase(Locale.ROOT), name == null ? "" : name.trim()).apply();
    }

    /**
     * Diseño lógico: userId: Z --> consumeLegacyFingerprint() --> B
     * Descripción: Procesa consume legacy fingerprint y devuelve el resultado correspondiente.
     */
    public boolean consumeLegacyFingerprint(long userId) {
        if (userId <= 0) return false;
        String key = "huella_usuario_" + userId;
        boolean enabled = prefs.getBoolean(key, false);
        if (prefs.contains(key)) prefs.edit().remove(key).apply();
        return enabled;
    }

    /**
     * Diseño lógico: ozoneAlerts() --> B
     * Descripción: Procesa ozone alerts y devuelve el resultado correspondiente.
     */
    public boolean ozoneAlerts() { return prefs.getBoolean("avisos", true); }
    /**
     * Diseño lógico: enabled: B --> setOzoneAlerts()
     * Descripción: Procesa set ozone alerts y devuelve el resultado correspondiente..
     */
    public void setOzoneAlerts(boolean enabled) { prefs.edit().putBoolean("avisos", enabled).apply(); }

    /**
     * Tutorial de la primera vez (HU33): true cuando ya se ha terminado o saltado. Vive en los datos de la app,
     * así que solo vuelve a salir si se borran los datos o se reinstala.
     */
    /**
     * Diseño lógico: tutorialSeen() --> B
     * Descripción: Procesa tutorial seen y devuelve el resultado correspondiente.
     */
    public boolean tutorialSeen() { return prefs.getBoolean("tutorial_visto", false); }
    /**
     * Diseño lógico: setTutorialSeen()
     * Descripción: Procesa set tutorial seen y devuelve el resultado correspondiente..
     */
    public void setTutorialSeen() { prefs.edit().putBoolean("tutorial_visto", true).apply(); }

    /**
     * Diseño lógico: saveRoute() --> B
     * Descripción: Procesa save route y devuelve el resultado correspondiente.
     */
    public boolean saveRoute() { return prefs.getBoolean("recorrido", true); }
    /**
     * Diseño lógico: enabled: B --> setSaveRoute()
     * Descripción: Procesa set save route y devuelve el resultado correspondiente..
     */
    public void setSaveRoute(boolean enabled) { prefs.edit().putBoolean("recorrido", enabled).apply(); }
}
