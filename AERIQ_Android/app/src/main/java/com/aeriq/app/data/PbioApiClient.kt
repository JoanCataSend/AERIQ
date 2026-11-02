package com.aeriq.app.data

import android.os.Handler
import android.os.Looper
import com.aeriq.app.ble.LiveMeasurement
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** Contrato HTTP de src/communication/api.php (Sprint 0, sin autenticación).
 * La cola serial conserva el orden O3/temperatura mientras la aplicación está abierta.
 * No reintenta automáticamente escrituras: el backend de Sprint 0 carece de idempotencia.
 */
class PbioApiClient(
    private val endpoint: String = "https://jcatsen.upv.edu.es/biometria/api.php"
) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val pending = AtomicInteger(0)
    private val maxPending = 30

    fun upload(measurement: LiveMeasurement, done: (String) -> Unit) {
        // No bloquear el hilo de la UI; mantener una cola acotada.
        if (pending.incrementAndGet() > maxPending) {
            pending.decrementAndGet()
            main.post { done("Cola de envío llena: esta lectura NO se ha guardado. Comprueba la conexión.") }
            return
        }
        val payload = JSONObject().apply {
            put("uuid", measurement.reading.uuid)
            put("tipoMedidaId", measurement.reading.typeId)
            put("valor", measurement.reading.value)
            put("contador", measurement.reading.counter)
            put("rssi", measurement.rssi)
        }.toString()
        try {
            worker.execute {
                var conn: HttpURLConnection? = null
                val status = try {
                    conn = URL(endpoint).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 10000
                    conn.readTimeout = 10000
                    conn.doOutput = true
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("Accept", "application/json")
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    val bytes = payload.toByteArray(StandardCharsets.UTF_8)
                    conn.setFixedLengthStreamingMode(bytes.size)
                    conn.outputStream.use { it.write(bytes) }
                    val code = conn.responseCode
                    val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.use { it.readText().take(300) }.orEmpty()
                    if (code == 201) {
                        // Contrato del backend: debe devolver MedidaVista.
                        val record = JSONObject(responseText)
                        if (record.optString("dispositivoId") == measurement.reading.uuid &&
                            record.optInt("tipoMedidaId") == measurement.reading.typeId &&
                            record.optInt("valor") == measurement.reading.value) {
                            "Guardado en MariaDB: ${measurement.reading.label} ${measurement.reading.value} ${measurement.reading.unit}"
                        } else "La API respondió, pero la lectura no coincide; revisar servidor"
                    } else "No guardado: HTTP $code ${responseText.take(120)}"
                } catch (e: Exception) {
                    "No guardado: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    conn?.disconnect()
                    pending.decrementAndGet()
                }
                main.post { done(status) }
            }
        } catch (_: RejectedExecutionException) {
            pending.decrementAndGet()
            main.post { done("No guardado: cliente HTTP cerrado") }
        }
    }

    fun close() { worker.shutdown() }
}
