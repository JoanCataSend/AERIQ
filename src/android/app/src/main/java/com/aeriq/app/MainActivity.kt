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
import com.aeriq.app.data.AeriqFieldClient
import com.aeriq.app.data.OrbAssignment
import com.aeriq.app.data.StoredReading
import com.aeriq.app.data.ForegroundCaptureLocation
import com.aeriq.app.data.AeriqAuthManager
import com.aeriq.app.ui.*

/** Pantallas AERIQ (Compose) + receptor del protocolo real PBIO (iBeacon, no GATT). */
class MainActivity : ComponentActivity() {
    private lateinit var ble: AeriqBleController
    private lateinit var api: AeriqFieldClient
    private lateinit var captureLocation: ForegroundCaptureLocation
    private var assignments by mutableStateOf<List<OrbAssignment>>(emptyList())
    private var history by mutableStateOf<List<StoredReading>>(emptyList())
    private var historyStatus by mutableStateOf("Consulta el historial de tu campaña")
    private var assignmentOwner: Long? = null
    private var assignmentStatus by mutableStateOf("Esperando cuenta autorizada")
    private lateinit var auth: AeriqAuthManager
    private var sharingAllowed = false
    private var serverStatus by mutableStateOf("Sin envíos al servidor · activa «Compartir con la red»")

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it } && ble.hasScanPermissions()) requestBluetoothOrScan()
        else ble.statusPermissionDenied()
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (sharingAllowed) captureLocation.start()
    }

    private val enableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { if (ble.bluetoothEnabled()) ble.startScan() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = AeriqAuthManager(this)
        api = AeriqFieldClient({ auth.sessionToken() })
        captureLocation = ForegroundCaptureLocation(this)
        auth.restore()
        ble = AeriqBleController(this) { measurement ->
            // No enviar nada por red sin activar expresamente "Compartir con la red".
            if (sharingAllowed) {
                serverStatus = "Enviando ${measurement.reading.label.lowercase()}…"
                val orb = assignments.firstOrNull { it.address.equals(measurement.address, true) }
                if (orb == null) serverStatus = "No enviado: Orb sin asignación autorizada"
                else api.upload(measurement, orb, captureLocation.current()) { serverStatus = it }
            }
        }
        setContent {
            AeriqTheme {
                Surface(Modifier.fillMaxSize(), color = AeriqColors.background) {
                    AeriqApplication(
                        ble = ble,
                        auth = auth,
                        serverStatus = serverStatus,
                        assignments = assignments, assignmentStatus = assignmentStatus,
                        history = history, historyStatus = historyStatus,
                        onRefreshHistory = { loadHistory() },
                        onRefreshAssignments = { loadAssignments() },
                        onStartScan = { startScannerWithPermission() },
                        onStopScan = { ble.stopScan() },
                        onSharingChanged = { enabled ->
                            sharingAllowed = enabled
                            serverStatus = if (enabled) "Envío activado: esperando lecturas de campaña" else "Envío desactivado"
                            if (!enabled) captureLocation.stop()
                            else if (captureLocation.allowed()) captureLocation.start()
                            else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    )
                }
            }
        }
    }

    private fun loadHistory() {
        if(auth.user?.role!="trabajador" || auth.user?.mustChangePassword==true){history=emptyList();return}
        historyStatus="Consultando historial…"
        api.history { rows,error ->
            if(error==null)history=rows
            historyStatus=error ?: "${rows.size} lecturas disponibles · máximo 1.000 recientes"
        }
    }

    private fun loadAssignments() {
        if(assignmentOwner!=auth.user?.id){assignments=emptyList();history=emptyList();assignmentOwner=auth.user?.id}
        if (auth.user?.role != "trabajador" || auth.user?.mustChangePassword == true) {
            assignments = emptyList(); assignmentStatus = "Accede con la cuenta de trabajador de tu organización"; return
        }
        assignmentStatus = "Consultando asignaciones…"
        api.assignments { list, error ->
            if(error!=null){assignmentStatus="No se pudo actualizar: $error. Se conservan las asignaciones consultadas.";return@assignments}
            assignments = list
            assignmentStatus = "${list.size} asignación(es) activas"
            if (list.none { it.address.equals(ble.selectedAddress, true) }) {
                ble.unlink(); sharingAllowed = false; captureLocation.stop()
                serverStatus = "Envío detenido: selecciona una asignación vigente"
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::captureLocation.isInitialized && sharingAllowed) captureLocation.start()
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
        if (::captureLocation.isInitialized) captureLocation.stop()
        super.onStop()
    }

    override fun onDestroy() {
        if (::api.isInitialized) api.close()
        if (::auth.isInitialized) auth.close()
        super.onDestroy()
    }
}

enum class Screen { LOGIN, REGISTER, VERIFY, FORGOT, RESET, MAP, LEARN, SENSOR, LINK, HISTORY, CONFIG, COMPARE, SERVICES, ACCOUNT, EDIT_PROFILE, CHANGE_PASSWORD }

@Composable
fun AeriqApplication(
    ble: AeriqBleController,
    auth: AeriqAuthManager,
    serverStatus: String,
    assignments: List<OrbAssignment>, assignmentStatus: String,
    history: List<StoredReading>, historyStatus: String, onRefreshHistory: () -> Unit,
    onRefreshAssignments: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onSharingChanged: (Boolean) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Screen.LOGIN.name) }
    var sensorName by rememberSaveable { mutableStateOf("Sensor PBIO") }
    var sharing by remember { mutableStateOf(false) }
    var trackRoute by rememberSaveable { mutableStateOf(false) }

    var notice by remember { mutableStateOf<String?>(null) }
    val publicScreens = setOf(Screen.LOGIN, Screen.REGISTER, Screen.VERIFY, Screen.FORGOT, Screen.RESET, Screen.MAP, Screen.LEARN)
    val go: (Screen) -> Unit = { destination ->
        if (auth.user?.mustChangePassword == true) {
            current = Screen.CHANGE_PASSWORD.name
        } else if (auth.user == null && destination !in publicScreens) {
            current = Screen.LOGIN.name
            notice = "Inicia sesión para acceder a tus datos y al sensor."
        } else {
            current = destination.name
            notice = null
        }
    }
    val message: (String) -> Unit = { notice = it }
    val linked = ble.selectedAddress != null
    LaunchedEffect(assignments, ble.selectedAddress) {
        if (assignments.none { it.address.equals(ble.selectedAddress, true) } && sharing) {
            sharing = false; onSharingChanged(false)
        }
    }
    LaunchedEffect(auth.user?.id, auth.user?.mustChangePassword) {
        sharing = false; onSharingChanged(false); ble.unlink()
        onRefreshAssignments(); onRefreshHistory()
    }
    LaunchedEffect(auth.restoring, auth.user) {
        if (!auth.restoring) {
            val screen = Screen.valueOf(current)
            if (auth.user?.mustChangePassword == true) current = Screen.CHANGE_PASSWORD.name
            else if (auth.user == null && screen !in publicScreens) current = Screen.LOGIN.name
            if (auth.user != null && auth.user?.mustChangePassword != true && screen in setOf(Screen.LOGIN, Screen.REGISTER, Screen.VERIFY, Screen.FORGOT, Screen.RESET)) {
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
        } else if (auth.user?.mustChangePassword == true) {
            ChangePasswordScreen(auth, onGo = go, onMessage = message, mandatory = true)
        } else when (Screen.valueOf(current)) {
            Screen.LOGIN -> LoginScreen(auth, onGo = go, onMessage = message)
            Screen.REGISTER -> RegisterScreen(auth, onGo = go, onMessage = message)
            Screen.VERIFY -> VerifyEmailScreen(auth, onGo = go, onMessage = message)
            Screen.FORGOT -> ForgotPasswordScreen(auth, onGo = go, onMessage = message)
            Screen.RESET -> ResetPasswordScreen(auth, onGo = go, onMessage = message)
            Screen.MAP -> PublicMapScreen(onGo = go)
            Screen.LEARN -> LearnScreen(onGo = go)
            Screen.SENSOR -> WorkScreen(ble, assignments, assignmentStatus, serverStatus, sharing,
                { sharing = it; onSharingChanged(it) }, onRefreshAssignments, onStartScan, onStopScan, go, history, historyStatus)
            Screen.LINK -> LinkScreen(onGo = go, devices = ble.devices.filter { d -> assignments.any { it.address.equals(d.address, true) } },
                status = ble.status, scanning = ble.scanning,
                selectedAddress = ble.selectedAddress,
                onScan = onStartScan, onStop = onStopScan,
                onSelect = { ble.select(it); go(Screen.SENSOR) })
            Screen.HISTORY -> WorkerHistoryScreen(history, historyStatus, onRefreshHistory, go, message)
            Screen.CONFIG -> WorkSettingsScreen(assignments.firstOrNull { it.address.equals(ble.selectedAddress,true) }, sharing,
                { sharing=it;onSharingChanged(it) }, { ble.unlink();sharing=false;onSharingChanged(false) }, go)
            Screen.COMPARE -> CompareScreen(onGo = go)
            Screen.SERVICES -> ServicesScreen(onGo = go)
            Screen.ACCOUNT -> {
                auth.user?.let { authenticated ->
                    AccountScreen(assignments.firstOrNull { it.address.equals(ble.selectedAddress,true) }?.name ?: "Sin Orb seleccionado", sharing, authenticated, onGo = go,
                        onMessage = message, onLogout = {
                            onSharingChanged(false)
                            sharing = false
                            ble.stopScan(); ble.unlink()
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
