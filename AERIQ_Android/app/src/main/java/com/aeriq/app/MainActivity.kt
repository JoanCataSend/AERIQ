package com.aeriq.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeriq.app.ble.AeriqBleController
import com.aeriq.app.data.PbioApiClient
import com.aeriq.app.data.AeriqAuthManager
import com.aeriq.app.ui.*

/** Pantallas AERIQ (Compose) + receptor del protocolo real PBIO (iBeacon, no GATT). */
class MainActivity : ComponentActivity() {
    private lateinit var ble: AeriqBleController
    private lateinit var api: PbioApiClient
    private lateinit var auth: AeriqAuthManager
    private var sharingAllowed = false
    private var serverStatus by mutableStateOf("Sin envíos al servidor · activa «Compartir con la red»")

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it } && ble.hasScanPermissions()) requestBluetoothOrScan()
        else ble.statusPermissionDenied()
    }

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (ble.bluetoothEnabled()) ble.startScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = PbioApiClient()
        auth = AeriqAuthManager(this)
        auth.restore()
        ble = AeriqBleController(this) { measurement ->
            // No enviar nada por red sin activar expresamente "Compartir con la red".
            if (sharingAllowed) {
                serverStatus = "Enviando ${measurement.reading.label.lowercase()}…"
                api.upload(measurement) { serverStatus = it }
            }
        }
        setContent {
            AeriqTheme {
                Surface(Modifier.fillMaxSize(), color = AeriqColors.background) {
                    AeriqApplication(
                        ble = ble,
                        auth = auth,
                        serverStatus = serverStatus,
                        onStartScan = { startScannerWithPermission() },
                        onStopScan = { ble.stopScan() },
                        onSharingChanged = { sharingAllowed = it; serverStatus = if (it) "Publicación activada: esperando nuevas mediciones BLE…" else "Publicación desactivada: nada se enviará al servidor" }
                    )
                }
            }
        }
    }

    private fun startScannerWithPermission() {
        if (!ble.hasScanPermissions()) {
            permissionRequest.launch(ble.requiredScanPermissions())
        } else requestBluetoothOrScan()
    }

    private fun requestBluetoothOrScan() {
        if (!ble.bluetoothEnabled()) {
            enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else ble.startScan()
    }

    override fun onStop() {
        // No escanear continuamente cuando la actividad no está en primer plano.
        if (::ble.isInitialized) ble.stopScan()
        super.onStop()
    }

    override fun onDestroy() {
        if (::api.isInitialized) api.close()
        if (::auth.isInitialized) auth.close()
        super.onDestroy()
    }
}

enum class Screen { LOGIN, REGISTER, VERIFY, FORGOT, RESET, MAP, LEARN, SENSOR, LINK, HISTORY, CONFIG, COMPARE, PREMIUM, ACCOUNT, EDIT_PROFILE, CHANGE_PASSWORD }

@Composable
fun AeriqApplication(
    ble: AeriqBleController,
    auth: AeriqAuthManager,
    serverStatus: String,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSharingChanged: (Boolean) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Screen.LOGIN.name) }
    var sensorName by rememberSaveable { mutableStateOf("Sensor PBIO") }
    var sharing by remember { mutableStateOf(false) }
    var trackRoute by rememberSaveable { mutableStateOf(false) }
    var premiumYearly by rememberSaveable { mutableStateOf(true) }
    var notice by remember { mutableStateOf<String?>(null) }
    val publicScreens = setOf(Screen.LOGIN, Screen.REGISTER, Screen.VERIFY, Screen.FORGOT, Screen.RESET, Screen.MAP, Screen.LEARN)
    val go: (Screen) -> Unit = { destination ->
        if (auth.user == null && destination !in publicScreens) {
            current = Screen.LOGIN.name
            notice = "Inicia sesión para acceder a tus datos y al sensor."
        } else {
            current = destination.name
            notice = null
        }
    }
    val message: (String) -> Unit = { notice = it }
    val linked = ble.selectedAddress != null
    LaunchedEffect(auth.restoring, auth.user) {
        if (!auth.restoring) {
            val screen = Screen.valueOf(current)
            if (auth.user == null && screen !in publicScreens) current = Screen.LOGIN.name
            if (auth.user != null && screen in setOf(Screen.LOGIN, Screen.REGISTER, Screen.VERIFY, Screen.FORGOT, Screen.RESET)) {
                current = Screen.MAP.name
            }
        }
    }

    LaunchedEffect(auth.restoring, auth.lastError) {
        if (!auth.restoring && auth.lastError != null) {
            notice = auth.lastError
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (auth.restoring) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AeriqColors.green)
            }
        } else when (Screen.valueOf(current)) {
            Screen.LOGIN -> LoginScreen(auth, onGo = go, onMessage = message)
            Screen.REGISTER -> RegisterScreen(auth, onGo = go, onMessage = message)
            Screen.VERIFY -> VerifyEmailScreen(auth, onGo = go, onMessage = message)
            Screen.FORGOT -> ForgotPasswordScreen(auth, onGo = go, onMessage = message)
            Screen.RESET -> ResetPasswordScreen(auth, onGo = go, onMessage = message)
            Screen.MAP -> MapScreen(onGo = go, onMessage = message)
            Screen.LEARN -> LearnScreen(onGo = go)
            Screen.SENSOR -> SensorScreen(sensorName, linked, sharing,
                { sharing = it; onSharingChanged(it) }, onGo = go,
                liveO3 = ble.lastO3, liveTemperature = ble.lastTemperature,
                status = ble.status, serverStatus = serverStatus,
                scanning = ble.scanning, onResumeScan = onStartScan)
            Screen.LINK -> LinkScreen(onGo = go, devices = ble.devices,
                status = ble.status, scanning = ble.scanning,
                selectedAddress = ble.selectedAddress,
                onScan = onStartScan, onStop = onStopScan,
                onSelect = { ble.select(it); go(Screen.SENSOR) })
            Screen.HISTORY -> HistoryScreen(onGo = go, onMessage = message)
            Screen.CONFIG -> ConfigScreen(sensorName, { sensorName = it },
                sharing, { sharing = it; onSharingChanged(it) },
                trackRoute, { trackRoute = it }, linked,
                onUnlink = { ble.unlink() },
                onGo = go, onMessage = message,
                lastO3 = ble.lastO3, scanning = ble.scanning)
            Screen.COMPARE -> CompareScreen(onGo = go)
            Screen.PREMIUM -> PremiumScreen(premiumYearly, { premiumYearly = it }, onGo = go, onMessage = message)
            Screen.ACCOUNT -> {
                auth.user?.let { authenticated ->
                    AccountScreen(sensorName, sharing, authenticated, onGo = go,
                        onMessage = message, onLogout = {
                            onSharingChanged(false)
                            sharing = false
                            ble.stopScan()
                            auth.logout { message("Has cerrado sesión.") }
                            go(Screen.LOGIN)
                        })
                }
            }
            Screen.EDIT_PROFILE -> EditProfileScreen(auth, onGo = go, onMessage = message)
            Screen.CHANGE_PASSWORD -> ChangePasswordScreen(auth, onGo = go, onMessage = message)
        }
        notice?.let { text ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter),
                action = { TextButton(onClick = { notice = null }) { Text("Cerrar") } }
            ) { Text(text) }
        }
    }
}
