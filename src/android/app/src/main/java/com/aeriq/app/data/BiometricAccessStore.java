package com.aeriq.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Per-account biometric gate. The biometric itself remains managed by Android. */
public final class BiometricAccessStore {
    private static final String KEY_PREFIX = "aeriq_biometric_user_";
    private static final String RECORD_PREFIX = "biometric_access_user_";
    private static final String STORE_NAME = "aeriq_biometric_access";
    private static final String LAST_USER = "biometric_last_user";
    private static final String EMAIL_PREFIX = "biometric_email_user_";
    private static final String PROOF_PREFIX = "AERIQ_BIOMETRIC_ACCESS_V1:";
    private final SharedPreferences prefs;

    public static final class Credential {
        public final String email;
        public final String password;
        Credential(String email, String password) { this.email = email; this.password = password; }
    }

    public BiometricAccessStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(STORE_NAME, Context.MODE_PRIVATE);
    }

    private static String alias(long userId) { return KEY_PREFIX + userId; }
    private static String recordKey(long userId) { return RECORD_PREFIX + userId; }

    public synchronized boolean hasEnrollment(long userId) {
        return userId > 0 && prefs.contains(recordKey(userId));
    }

    public synchronized long enrolledUserId() {
        long last = prefs.getLong(LAST_USER, 0L);
        if (hasEnrollment(last)) return last;
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith(RECORD_PREFIX)) continue;
            try {
                long id = Long.parseLong(key.substring(RECORD_PREFIX.length()));
                if (hasEnrollment(id)) return id;
            } catch (NumberFormatException ignored) { }
        }
        return 0L;
    }

    public synchronized String enrolledEmail() {
        long userId = enrolledUserId();
        return userId > 0 ? prefs.getString(EMAIL_PREFIX + userId, "") : "";
    }

    /** Creates a per-use, biometric-authenticated cipher for the enrollment prompt. */
    public synchronized Cipher beginEnrollment(long userId) throws Exception {
        requireUser(userId);
        deleteKey(userId);
        prefs.edit().remove(recordKey(userId)).apply();

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(alias(userId),
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
        } else {
            spec.setUserAuthenticationValidityDurationSeconds(-1);
        }
        generator.init(spec.build());
        generator.generateKey();

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey(userId));
        return cipher;
    }

    /** Saves account credentials only after the prompt authorizes the cipher. */
    public synchronized void finishEnrollment(long userId, Cipher authorizedCipher, String email, String password) throws Exception {
        requireUser(userId);
        if (authorizedCipher == null || authorizedCipher.getIV() == null || email == null || email.trim().isEmpty()
            || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Falta la operación biométrica autorizada");
        }
        JSONObject payload = new JSONObject();
        payload.put("proof", PROOF_PREFIX + userId);
        payload.put("email", email.trim());
        payload.put("password", password);
        byte[] ciphertext = authorizedCipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
        String value = encode(authorizedCipher.getIV()) + ":" + encode(ciphertext);
        if (!prefs.edit().putString(recordKey(userId), value).putLong(LAST_USER, userId)
            .putString(EMAIL_PREFIX + userId, email.trim()).commit()) {
            throw new IllegalStateException("No se pudo guardar el acceso biométrico");
        }
    }

    /** Prepares the stored account credentials for biometric-gated decryption. */
    public synchronized Cipher beginUnlock(long userId) throws Exception {
        requireUser(userId);
        String value = prefs.getString(recordKey(userId), null);
        if (value == null) throw new IllegalStateException("No hay acceso biométrico activado para esta cuenta");
        String[] parts = value.split(":", 2);
        if (parts.length != 2) throw new IllegalStateException("El registro biométrico no es válido");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getKey(userId), new GCMParameterSpec(128, decode(parts[0])));
        return cipher;
    }

    /** Verifies both the cryptographic proof and the account ID after authentication. */
    public synchronized Credential finishUnlock(long userId, Cipher authorizedCipher) throws Exception {
        requireUser(userId);
        String value = prefs.getString(recordKey(userId), null);
        if (value == null) return null;
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || authorizedCipher == null) return null;
        JSONObject payload = new JSONObject(new String(authorizedCipher.doFinal(decode(parts[1])), StandardCharsets.UTF_8));
        byte[] expected = proof(userId);
        byte[] actual = payload.optString("proof").getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) return null;
        String email = payload.optString("email");
        String password = payload.optString("password");
        return email.isEmpty() || password.isEmpty() ? null : new Credential(email, password);
    }

    public synchronized void disable(long userId) {
        if (userId <= 0) return;
        SharedPreferences.Editor editor = prefs.edit().remove(recordKey(userId)).remove(EMAIL_PREFIX + userId);
        if (prefs.getLong(LAST_USER, 0L) == userId) editor.remove(LAST_USER);
        editor.apply();
        deleteKey(userId);
    }

    private SecretKey getKey(long userId) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(alias(userId))) {
            throw new IllegalStateException("La clave biométrica de esta cuenta ya no está disponible");
        }
        return (SecretKey) keyStore.getKey(alias(userId), null);
    }

    private void deleteKey(long userId) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(alias(userId))) keyStore.deleteEntry(alias(userId));
        } catch (Exception ignored) {
            // The preference record is also removed, so a stale key cannot unlock an account.
        }
    }

    private static byte[] proof(long userId) {
        return (PROOF_PREFIX + userId).getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] value) { return Base64.encodeToString(value, Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value, Base64.NO_WRAP); }

    private static void requireUser(long userId) {
        if (userId <= 0) throw new IllegalArgumentException("La cuenta no tiene un ID válido");
    }
}
