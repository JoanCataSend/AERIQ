package com.aeriq.app.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Escaneo de ADVERTISEMENTS iBeacon: el firmware antiguo NO implementa GATT conectable. */
class AeriqBleController(
    private val context: Context,
    private val onNewMeasurement: (LiveMeasurement) -> Unit
) {
    data class FoundDevice(val address: String, val name: String, val rssi: Int, val lastSeen: Long)

    var status by mutableStateOf("Pulsa Buscar para detectar el sensor")
        private set
    var scanning by mutableStateOf(false)
        private set
    var devices by mutableStateOf<List<FoundDevice>>(emptyList())
        private set
    var selectedAddress by mutableStateOf<String?>(null)
        private set
    var lastO3 by mutableStateOf<LiveMeasurement?>(null)
        private set
    var lastTemperature by mutableStateOf<LiveMeasurement?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private val lastSeenCounters = mutableMapOf<String, Int>()

    fun statusPermissionDenied() { status = "No se han concedido los permisos Bluetooth" }

    fun requiredScanPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasScanPermissions(): Boolean = requiredScanPermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun bluetoothEnabled(): Boolean = try {
        bluetoothManager.adapter?.isEnabled == true
    } catch (_: SecurityException) { false }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasScanPermissions()) { status = "Permiso Bluetooth pendiente"; return }
        if (!bluetoothEnabled()) { status = "Activa Bluetooth"; return }
        if (scanning) return
        val localScanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (localScanner == null) { status = "Escáner BLE no disponible"; return }
        scanner = localScanner
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val payload = result.scanRecord?.getManufacturerSpecificData(IBeaconParser.APPLE_MANUFACTURER_ID)
                val parsed = IBeaconParser.parse(payload) ?: return
                val addr = result.device?.address ?: return
                val now = System.currentTimeMillis()
                val name = result.scanRecord?.deviceName ?: "Sensor AERIQ / PBIO"
                main.post {
                    val candidate = FoundDevice(addr, name, result.rssi, now)
                    devices = (devices.filterNot { it.address == addr } + candidate)
                        .sortedByDescending { it.lastSeen }
                    if (selectedAddress != addr) return@post
                    val key = "$addr/${parsed.typeId}"
                    // El mismo anuncio se repite muchas veces durante 1200 ms. El contador
                    // no cambia hasta el siguiente ciclo, así que no se publica dos veces.
                    // Si el contador da la vuelta de 255 a 0, el valor anterior ya será 255.
                    if (lastSeenCounters[key] == parsed.counter) return@post
                    lastSeenCounters[key] = parsed.counter
                    val live = LiveMeasurement(addr, parsed, result.rssi, now)
                    if (parsed.typeId == IBeaconParser.O3_ID) lastO3 = live else lastTemperature = live
                    status = "Recibiendo mediciones del sensor seleccionado"
                    onNewMeasurement(live)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                main.post { status = "Error al buscar sensores BLE: $errorCode"; scanning = false; callback = null }
            }
        }
        callback = scanCallback
        // Filtramos por fabricante y prefijo; el nombre puede venir en scan-response separado.
        val filter = ScanFilter.Builder()
            .setManufacturerData(IBeaconParser.APPLE_MANUFACTURER_ID, byteArrayOf(0x02, 0x15))
            .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            localScanner.startScan(listOf(filter), settings, scanCallback)
            scanning = true
            status = "Buscando sensores PBIO por BLE…"
        } catch (_: SecurityException) {
            callback = null
            status = "Sin permiso para buscar sensores"
        } catch (e: IllegalStateException) {
            callback = null
            status = "No se puede iniciar el escaneo: ${e.message ?: "Bluetooth"}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val c = callback
        if (c != null && hasScanPermissions()) {
            try { scanner?.stopScan(c) } catch (_: SecurityException) { /* permiso revocado */ }
        }
        callback = null
        scanning = false
        status = "Escaneo detenido"
    }

    fun select(address: String) {
        if (devices.none { it.address == address }) { status = "Sensor no disponible"; return }
        selectedAddress = address
        lastO3 = null
        lastTemperature = null
        lastSeenCounters.clear()
        status = "Sensor seleccionado. Esperando nueva medición…"
    }

    fun unlink() {
        selectedAddress = null
        lastO3 = null
        lastTemperature = null
        lastSeenCounters.clear()
        status = "Sensor desvinculado de esta sesión"
    }
}
