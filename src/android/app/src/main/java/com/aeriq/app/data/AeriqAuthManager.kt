package com.aeriq.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

/** Autenticación real contra mobile-auth.php de Plesk. Misma tabla de cuentas que la web. */
data class AeriqUser(val id: Long, val name: String, val last: String, val email: String, val phone: String,
                     val role: String = "visitante", val organization: String = "", val mustChangePassword: Boolean = false) {
    val fullName: String get() = "$name $last".trim()
    companion object {
        fun fromJson(o: JSONObject) = AeriqUser(
            o.getLong("id"), o.optString("name"), o.optString("last"),
            o.getString("email"), o.optString("phone"), o.optString("role", "visitante"),
            o.optString("organization", ""), o.optBoolean("mustChangePassword")
        )
    }
}

data class AuthResult(val success: Boolean, val message: String, val needsVerification: Boolean = false)

class AeriqAuthManager(context: Context) {
    /** URL pública de /httpdocs/AERIQ/mobile-auth.php en Plesk. */
    private val endpoint = ApiRoutes.AUTH
    private val tokenStore = AeriqTokenStore(context.applicationContext)
    internal fun sessionToken(): String? = tokenStore.read()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var user by mutableStateOf<AeriqUser?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var restoring by mutableStateOf(true)
        private set
    var pendingEmail by mutableStateOf("")
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private data class Response(val json: JSONObject, val code: Int) {
        val ok: Boolean get() = code in 200..299
        val message: String get() = json.optString("error", json.optString("message", "Error HTTP $code"))
        val needsVerification: Boolean get() = json.optBoolean("needsVerification")
    }

    /** Ninguna contraseña ni código se guarda en el teléfono. */
    private fun request(action: String, body: JSONObject? = null, token: String? = null): Response {
        val uri = URL("$endpoint?action=$action")
        check(uri.protocol == "https") { "La autenticación requiere HTTPS" }
        val connection = uri.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (!token.isNullOrBlank()) connection.setRequestProperty("X-AERIQ-Token", token)
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val contents = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText().take(20000) }.orEmpty()
            val json = try { JSONObject(contents) } catch (_: Exception) {
                throw IOException("El servidor no devolvió JSON. Revisa la ruta mobile-auth.php (HTTP $code).")
            }
            return Response(json, code)
        } finally {
            connection.disconnect()
        }
    }

    fun restore() {
        restoring = true
        worker.execute {
            val token = tokenStore.read()
            if (token.isNullOrBlank()) {
                main.post { user = null; restoring = false }
                return@execute
            }
            try {
                val response = request("session", token = token)
                main.post {
                    if (response.ok) user = AeriqUser.fromJson(response.json.getJSONObject("user"))
                    else {
                        tokenStore.clear()
                        user = null
                        lastError = response.message
                    }
                    restoring = false
                }
            } catch (e: Exception) {
                main.post { user = null; restoring = false; lastError = "No se ha podido comprobar tu sesión. Comprueba Internet y el servidor." }
            }
        }
    }

    private fun execute(action: String, data: JSONObject, requiresToken: Boolean = false, callback: (AuthResult) -> Unit) {
        if (busy) return
        busy = true
        worker.execute {
            val result = try {
                val r = request(action, data, if (requiresToken) tokenStore.read() else null)
                if (r.ok) {
                    val freshToken = r.json.optString("token")
                    if (freshToken.isNotBlank()) tokenStore.write(freshToken)
                    val u = r.json.optJSONObject("user")?.let { AeriqUser.fromJson(it) }
                    main.post { if (u != null) user = u }
                    AuthResult(true, r.json.optString("message", "Operación completada"))
                } else {
                    if (r.code == 401 && requiresToken) {
                        tokenStore.clear()
                        main.post { user = null }
                    }
                    AuthResult(false, r.message, r.needsVerification)
                }
            } catch (e: Exception) {
                AuthResult(false, "No se pudo conectar con AERIQ: ${e.message ?: "red no disponible"}")
            }
            main.post { busy = false; callback(result) }
        }
    }

    fun login(email: String, password: String, done: (AuthResult) -> Unit) {
        pendingEmail = email.trim().lowercase()
        execute("login", JSONObject().put("email", pendingEmail).put("password", password), callback = done)
    }

    fun register(name: String, last: String, email: String, password: String, done: (AuthResult) -> Unit) {
        pendingEmail = email.trim().lowercase()
        execute("register", JSONObject().put("name", name.trim()).put("last", last.trim())
            .put("email", pendingEmail).put("password", password), callback = done)
    }

    fun verify(email: String, code: String, done: (AuthResult) -> Unit) {
        pendingEmail = email.trim().lowercase()
        execute("verify", JSONObject().put("email", pendingEmail).put("code", code.trim()), callback = done)
    }

    fun resend(email: String, done: (AuthResult) -> Unit) {
        execute("resend", JSONObject().put("email", email.trim().lowercase()), callback = done)
    }

    fun forgot(email: String, done: (AuthResult) -> Unit) {
        pendingEmail = email.trim().lowercase()
        execute("forgot", JSONObject().put("email", pendingEmail), callback = done)
    }

    fun resetPassword(email: String, code: String, password: String, done: (AuthResult) -> Unit) {
        pendingEmail = email.trim().lowercase()
        execute("reset-password", JSONObject().put("email", pendingEmail)
            .put("code", code.trim()).put("password", password), callback = done)
    }

    fun updateProfile(name: String, last: String, phone: String, done: (AuthResult) -> Unit) {
        execute("profile", JSONObject().put("name", name.trim()).put("last", last.trim())
            .put("phone", phone.trim()), requiresToken = true, callback = done)
    }

    fun changePassword(current: String, password: String, done: (AuthResult) -> Unit) {
        execute("change-password", JSONObject().put("current", current).put("password", password),
            requiresToken = true, callback = done)
    }

    fun logout(done: () -> Unit) {
        // Revocar en servidor; limpiar siempre en local incluso si no hay conexión.
        val oldToken = tokenStore.read()
        tokenStore.clear()
        user = null
        pendingEmail = ""
        worker.execute {
            if (!oldToken.isNullOrBlank()) {
                try { request("logout", JSONObject(), oldToken) } catch (_: Exception) { /* sin conexión */ }
            }
            main.post(done)
        }
    }

    fun close() { worker.shutdown() }
}
