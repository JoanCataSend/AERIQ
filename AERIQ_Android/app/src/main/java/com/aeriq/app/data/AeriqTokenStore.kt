package com.aeriq.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Token de sesión (NO contraseña) protegido con AES-GCM y Android Keystore. */
internal class AeriqTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("aeriq_mobile_auth_private", Context.MODE_PRIVATE)
    private val keyAlias = "aeriq_mobile_token_aes_gcm_v1"

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    @Synchronized fun write(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString("session", "$iv:$data").apply()
    }

    @Synchronized fun read(): String? {
        val saved = prefs.getString("session", null) ?: return null
        return try {
            val parts = saved.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized fun clear() { prefs.edit().remove("session").apply() }
}
