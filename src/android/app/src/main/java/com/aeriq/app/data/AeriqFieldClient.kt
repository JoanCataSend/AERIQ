package com.aeriq.app.data

import android.location.Location
import android.os.Handler
import android.os.Looper
import com.aeriq.app.ble.LiveMeasurement
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

data class OrbAssignment(val id: Long, val name: String, val address: String, val campaign: String,
                         val campaignId: Long, val simulated: Boolean, val assignmentId: Long,
                         val medium: Double = 60.0, val high: Double = 100.0) {
    companion object {
        fun fromJson(o: JSONObject) = OrbAssignment(o.getLong("id"), o.getString("nombre"),
            o.getString("direccion_ble"), o.getString("campana"), o.getLong("campana_id"), (o.optInt("simulado") == 1 || o.optBoolean("simulado",false)),
            o.getLong("asignacion_id"),o.optDouble("umbral_medio",60.0),o.optDouble("umbral_alto",100.0))
    }
}

/** Envía al mismo despliegue AERIQ que las cuentas; cada lectura tiene una clave estable. */
class AeriqFieldClient(private val token: () -> String?,
                      private val endpoint: String = ApiRoutes.FIELD) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicInteger(0)

    private fun request(credential: String, body: JSONObject? = null, action: String = ""): JSONObject {
        check(token() == credential) { "Sesión cambiada; envío cancelado" }
        val c = URL(endpoint + if (action.isBlank()) "" else "?action=$action").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10000; c.readTimeout = 10000; c.instanceFollowRedirects = false
            c.setRequestProperty("X-AERIQ-Token", credential)
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Cache-Control", "no-store")
            if (body != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = try { JSONObject(text) } catch (_: Exception) { error("El servidor no devolvió JSON (HTTP $code)") }
            check(code in 200..299) { result.optString("error", "HTTP $code") }
            return result
        } finally { c.disconnect() }
    }

    fun assignments(done: (List<OrbAssignment>, String?) -> Unit) {
        val credential = token() ?: run { done(emptyList(), "Inicia sesión"); return }
        try { worker.execute {
            val result = runCatching {
                val array = request(credential).getJSONArray("assignments")
                (0 until array.length()).map { OrbAssignment.fromJson(array.getJSONObject(it)) }
            }
            main.post { if (token() == credential) done(result.getOrDefault(emptyList()), result.exceptionOrNull()?.message) }
        } } catch (_: RejectedExecutionException) { done(emptyList(), "Cliente cerrado") }
    }

    fun upload(m: LiveMeasurement, orb: OrbAssignment, location: Location?, done: (String) -> Unit) {
        val credential = token() ?: run { done("No enviado: inicia sesión"); return }
        if (pending.incrementAndGet() > 30) { pending.decrementAndGet(); done("Error de envío: cola llena; lectura no guardada"); return }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val body = JSONObject().put("eventId", UUID.randomUUID().toString()).put("deviceId", orb.id)
            .put("campaignId", orb.campaignId).put("assignmentId", orb.assignmentId).put("unit", m.reading.unit)
            .put("address", m.address).put("type", m.reading.typeId).put("value", m.reading.value)
            .put("capturedAt", format.format(Date(m.receivedAtMillis))).put("counter", m.reading.counter).put("rssi", m.rssi)
        if (location != null) {
            body.put("latitude", location.latitude).put("longitude", location.longitude).put("accuracy", location.accuracy.toDouble())
        }
        try { worker.execute {
            val status = try {
                var failure: Exception? = null
                var saved: JSONObject? = null
                // Reutilizar exactamente el evento al reintentar: el servidor evita duplicados.
                for (attempt in 0..1) {
                    try { saved = request(credential, body); break }
                    catch (e: java.io.IOException) { failure = e }
                }
                check(saved?.optString("eventId") == body.getString("eventId")) { failure?.message ?: "Respuesta de guardado incorrecta" }
                "Servidor: lectura ${if (saved?.optBoolean("duplicate") == true) "ya registrada" else "guardada"} · ${format.format(Date())}"
            } catch (e: Exception) {
                if (e is java.io.IOException) "Servidor no alcanzable: revisa Internet o la disponibilidad del servidor. BLE puede seguir recibiendo."
                else "Error de envío · BLE puede seguir recibiendo: ${e.message}"
            }
            finally { pending.decrementAndGet() }
            main.post { if (token() == credential) done(status) }
        } } catch (_: RejectedExecutionException) { pending.decrementAndGet(); done("No enviado: cliente cerrado") }
    }
    fun history(done: (List<StoredReading>, String?) -> Unit) {
        val credential=token() ?: run { done(emptyList(),"Inicia sesión");return }
        try { worker.execute {
            val result=runCatching {
                val array=request(credential,action="history").getJSONArray("readings")
                (0 until array.length()).map { StoredReading.fromJson(array.getJSONObject(it)) }
            }
            main.post { if(token()==credential)done(result.getOrDefault(emptyList()),result.exceptionOrNull()?.message) }
        } } catch (_: RejectedExecutionException) { done(emptyList(),"Cliente cerrado") }
    }
    fun close() { worker.shutdownNow() }
}

data class StoredReading(val id: Long, val value: Double, val unit: String, val type: Int,
                         val date: String, val device: String, val campaign: String, val zone: String,
                         val latitude: Double?, val longitude: Double?, val simulated: Boolean, val deviceId: Long = 0) {
    companion object {
        fun fromJson(o: JSONObject)=StoredReading(o.getLong("id"),o.getDouble("valor"),o.getString("unidad"),
            o.getInt("tipo"),o.getString("capturada_en"),o.getString("dispositivo"),o.getString("campana"),o.getString("zona"),
            if(o.isNull("latitud"))null else o.getDouble("latitud"),if(o.isNull("longitud"))null else o.getDouble("longitud"),(o.optInt("simulado")==1 || o.optBoolean("simulado",false)),o.optLong("dispositivo_id"))
    }
}
