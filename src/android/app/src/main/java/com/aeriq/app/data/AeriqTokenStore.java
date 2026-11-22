package com.aeriq.app.data;

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

    AeriqTokenStore(Context context) {
        prefs = context.getSharedPreferences("aeriq_mobile_auth_private", Context.MODE_PRIVATE);
    }

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

    synchronized void write(String token, long userId) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
        String data = Base64.encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        prefs.edit().putString("session", iv + ":" + data)
            .putLong("session_user_id", userId).apply();
    }

    synchronized void associateOwner(long userId) {
        if (userId > 0 && prefs.getString("session", null) != null) {
            prefs.edit().putLong("session_user_id", userId).apply();
        }
    }

    public synchronized long ownerUserId() { return prefs.getLong("session_user_id", 0L); }

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

    public synchronized void saveLastEmail(String email) {
        if (email != null && !email.trim().isEmpty()) {
            prefs.edit().putString("last_email", email.trim()).apply();
        }
    }

    public synchronized String getLastEmail() {
        return prefs.getString("last_email", "");
    }

    public synchronized void clear() {
        prefs.edit().remove("session").remove("session_user_id").apply();
    }
}
