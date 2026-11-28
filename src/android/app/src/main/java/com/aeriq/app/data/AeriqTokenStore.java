package com.aeriq.app.data;
/*
 * Fichero: AeriqTokenStore.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

public final class AeriqTokenStore {
    private static final String ALIAS = "aeriq_mobile_token_aes_gcm_v1";
    private final SharedPreferences prefs;

  /**
   * Diseño lógico: context: Text --> AeriqTokenStore() --> Text
   * Descripción: Procesa aeriq token store y devuelve el resultado correspondiente.
   */
    AeriqTokenStore(Context context) {
        prefs = context.getSharedPreferences("aeriq_mobile_auth_private", Context.MODE_PRIVATE);
    }

    /**
     * Diseño lógico: key() --> Text
     * Descripción: Procesa key y devuelve el resultado correspondiente.
     */
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return generator.generateKey();
    }

    /**
     * Diseño lógico: token: Text, userId: Z --> write()
     * Descripción: Escribe el contenido recibido en el archivo y normaliza sus finales de línea.
     */
    synchronized void write(String token, long userId) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
        String data = Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        prefs.edit().putString("session", iv + ":" + data)
            .putLong("session_user_id", userId).apply();
    }

    /**
     * Diseño lógico: userId: Z --> associateOwner()
     * Descripción: Procesa associate owner y devuelve el resultado correspondiente..
     */
    synchronized void associateOwner(long userId) {
        if (userId > 0 && prefs.getString("session", null) != null) {
            prefs.edit().putLong("session_user_id", userId).apply();
        }
    }

    /**
     * Diseño lógico: ownerUserId() --> Z
     * Descripción: Procesa owner user id y devuelve el resultado correspondiente.
     */
    public synchronized long ownerUserId() { return prefs.getLong("session_user_id", 0L); }

    /**
     * Diseño lógico: read() --> Text
     * Descripción: Procesa read y devuelve el resultado correspondiente.
     */
    public synchronized String read() {
        String saved = prefs.getString("session", null);
        if (saved == null) return null;
        try {
            String[] parts = saved.split(":", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    /**
     * Diseño lógico: email: Text --> saveLastEmail()
     * Descripción: Procesa save last email y devuelve el resultado correspondiente..
     */
    public synchronized void saveLastEmail(String email) {
        if (email != null && !email.trim().isEmpty()) {
            prefs.edit().putString("last_email", email.trim()).apply();
        }
    }

    /**
     * Diseño lógico: getLastEmail() --> Text
     * Descripción: Procesa get last email y devuelve el resultado correspondiente.
     */
    public synchronized String getLastEmail() {
        return prefs.getString("last_email", "");
    }

    /**
     * Diseño lógico: clear()
     * Descripción: Procesa clear y devuelve el resultado correspondiente..
     */
    public synchronized void clear() {
        prefs.edit().remove("session").remove("session_user_id").apply();
    }
}
