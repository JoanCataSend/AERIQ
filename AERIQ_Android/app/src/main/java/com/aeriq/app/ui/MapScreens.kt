package com.aeriq.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aeriq.app.Screen
import org.osmdroid.util.GeoPoint

private fun locationAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Localización exclusivamente en primer plano, mientras se visita la pestaña Mapa. */
@SuppressLint("MissingPermission")
@Composable
private fun rememberForegroundLocation(allowed: Boolean): Location? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var lastPosition by remember { mutableStateOf<Location?>(null) }

    DisposableEffect(allowed, manager) {
        if (!allowed) {
            lastPosition = null
            onDispose { }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lastPosition = location
                }
            }
            val fineAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val providers = buildList {
                // Fused es un proveedor del sistema en versiones modernas; no usa API key.
                if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
                if (fineAllowed) add(LocationManager.GPS_PROVIDER)
                add(LocationManager.NETWORK_PROVIDER)
            }

            // Se admite una posición en caché reciente mientras llega una lectura nueva.
            providers.forEach { provider ->
                runCatching {
                    val cached = manager.getLastKnownLocation(provider)
                    if (cached != null && System.currentTimeMillis() - cached.time < 10 * 60 * 1000L &&
                        (lastPosition == null || cached.time > (lastPosition?.time ?: 0))) {
                        lastPosition = cached
                    }
                    if (manager.isProviderEnabled(provider)) {
                        manager.requestLocationUpdates(provider, 2500L, 3f, listener, Looper.getMainLooper())
                    }
                }
            }
            onDispose { runCatching { manager.removeUpdates(listener) } }
        }
    }
    return lastPosition
}

@Composable
fun MapScreen(onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var term by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var dark by rememberSaveable { mutableStateOf(false) }
    var layersVisible by rememberSaveable { mutableStateOf(false) }
    var zonesEnabled by rememberSaveable { mutableStateOf(true) }
    var markersEnabled by rememberSaveable { mutableStateOf(true) }
    var selectedId by rememberSaveable { mutableStateOf("017") }
    var permissionGranted by remember { mutableStateOf(locationAllowed(context)) }
    var focusPoint by remember { mutableStateOf(GANDIA_CAMPUS) }
    var focusNonce by remember { mutableIntStateOf(0) }
    var recenterRequested by remember { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!permissionGranted) {
            onMessage("Puedes explorar el mapa sin dar permiso. Para ver tu ubicación, pulsa el icono de la diana.")
        }
    }
    // El diálogo de Android aparece al abrir Mapa, nunca al iniciar sesión.
    LaunchedEffect(Unit) {
        if (!locationAllowed(context)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else permissionGranted = true
    }
    val deviceLocation = rememberForegroundLocation(permissionGranted)
    val myGeoPoint = deviceLocation?.let { GeoPoint(it.latitude, it.longitude) }
    val selected = ozoneDemoSensors.firstOrNull { it.id == selectedId } ?: ozoneDemoSensors[1]

    fun focus(point: GeoPoint) {
        recenterRequested = false
        focusPoint = point
        focusNonce++
    }
    fun search() {
        when {
            term.contains("campus", true) || term.contains("universidad", true) ||
                term.contains("universitat", true) || term.contains("upv", true) -> focus(GANDIA_CAMPUS)
            term.contains("playa", true) || term.contains("platja", true) -> focus(GeoPoint(38.9990, -0.1589))
            term.contains("grau", true) || term.contains("grao", true) ||
                term.contains("gandía", true) || term.contains("gandia", true) -> focus(GeoPoint(38.995, -0.163))
            else -> onMessage("En este prototipo busca UPV, Gandía, Grau o playa. La búsqueda de cualquier dirección necesita un geocodificador.")
        }
    }

    AeriqPage("Mapa de ozono", Screen.MAP, onGo, trailing = "bell", onTrailing = {
        onMessage("Las alertas ambientales todavía no están conectadas a datos oficiales.")
    }) {
        OutlinedTextField(
            value = term, onValueChange = { term = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { AeriqText("Buscar zona: UPV, playa de Gandía…", 13) },
            singleLine = true,
            leadingIcon = { AeriqIcon("search", size = 20.dp, tint = AeriqColors.muted) },
            trailingIcon = { TextButton(onClick = { search() }) { AeriqText("Ir", 12, true, AeriqColors.green) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                unfocusedBorderColor = AeriqColors.border, focusedBorderColor = AeriqColors.green
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Todos", "Red colaborativa", "Estaciones oficiales").forEach { choice ->
                val active = filter == choice
                Box(
                    Modifier.clip(RoundedCornerShape(13.dp))
                        .background(if (active) AeriqColors.forest else Color.White)
                        .clickable { filter = choice }
                        .padding(horizontal = 9.dp, vertical = 12.dp)
                ) { AeriqText(choice, 10, true, if (active) Color.White else AeriqColors.dark) }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFE8F1EE), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AeriqIcon("info", size = 18.dp, tint = AeriqColors.green)
            AeriqText("Mapa real de calles · sensores y lecturas de ozono ficticios.", 11,
                color = AeriqColors.green, modifier = Modifier.weight(1f))
        }
        Box(
            Modifier.fillMaxWidth().height(365.dp).clip(RoundedCornerShape(18.dp))
                .border(1.dp, AeriqColors.border, RoundedCornerShape(18.dp))
        ) {
            RealOzoneMap(
                selectedId = selectedId,
                visibleSensors = markersEnabled && filter != "Estaciones oficiales",
                visibleZones = zonesEnabled && filter != "Estaciones oficiales",
                darkMode = dark,
                location = myGeoPoint,
                centerMyLocation = recenterRequested,
                focusPoint = focusPoint,
                focusNonce = focusNonce,
                onSelectSensor = { selectedId = it },
                modifier = Modifier.fillMaxSize()
            )
            // Los controles son Compose nativos y se superponen al mapa de calles.
            Column(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControl("target", "Ir a mi ubicación") {
                    if (!permissionGranted) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else if (myGeoPoint == null) {
                        onMessage("Esperando una ubicación del dispositivo. Comprueba que la localización del móvil esté activada.")
                    } else {
                        focus(myGeoPoint)
                    }
                }
                MapControl("layers", "Mostrar capas") { layersVisible = !layersVisible }
                MapControl("location", "Volver al campus UPV") { focus(GANDIA_CAMPUS) }
            }
            if (layersVisible) {
                Column(
                    Modifier.align(Alignment.TopStart).padding(10.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.dp, AeriqColors.border, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AeriqText("Capas de la maqueta", 11, true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(zonesEnabled, { zonesEnabled = it }, modifier = Modifier.size(34.dp))
                        AeriqText("Zonas O₃", 11)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(markersEnabled, { markersEnabled = it }, modifier = Modifier.size(34.dp))
                        AeriqText("Sensores", 11)
                    }
                    TextButton(onClick = { dark = !dark }) {
                        AeriqText(if (dark) "☀ Modo claro" else "☾ Modo oscuro", 11, true, AeriqColors.green)
                    }
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AeriqText("Leyenda", 10, true)
                listOf(
                    "Bajo" to AeriqColors.good,
                    "Moderado" to AeriqColors.orange,
                    "Alto" to AeriqColors.red
                ).forEach { (name, color) ->
                    Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
                    AeriqText(name, 9, color = AeriqColors.muted)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(Color(0xFF1E79DF), RoundedCornerShape(50)))
            AeriqText(
                if (!permissionGranted) "Ubicación sin permiso · mapa disponible"
                else if (deviceLocation == null) "Buscando ubicación del móvil…"
                else "Punto azul: tu ubicación real (${if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) "precisa" else "aproximada"})",
                11, color = AeriqColors.muted, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { focus(GANDIA_CAMPUS) }) { AeriqText("Ver UPV", 11, true, AeriqColors.green) }
        }
        if (filter == "Estaciones oficiales") {
            Pane {
                AeriqText("Estaciones oficiales", 17, true)
                CaptionNote("Todavía no hemos conectado una fuente pública de estaciones oficiales. No presentamos estos datos como mediciones reales.")
            }
        } else {
            Pane(padding = 15.dp) {
                DemoChip()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(
                        if (selected.level == "Alto") AeriqColors.red else if (selected.level == "Moderado") AeriqColors.orange else AeriqColors.good,
                        RoundedCornerShape(50)))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        AeriqText(selected.name, 15, true)
                        CaptionNote("Red colaborativa · sensor #${selected.id} (demo)")
                    }
                    AeriqText("${selected.value}", 29, true)
                    AeriqText("µg/m³", 11, color = AeriqColors.muted)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.background(Color(0xFFFFF2DD), RoundedCornerShape(16.dp))
                        .padding(horizontal = 11.dp, vertical = 6.dp)) {
                        AeriqText("Nivel: ${selected.level}", 11, true, Color(0xFFA36C17))
                    }
                    CaptionNote("Lectura ficticia · sin actualización real")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { OutlineAction("Ver historial", { onGo(Screen.HISTORY) }) }
                    Box(Modifier.weight(1f)) { PrimaryButton("Comparar · Premium", { onGo(Screen.COMPARE) }, dark = true) }
                }
                CaptionNote("El mapa de calles y la ubicación del móvil son reales. Los puntos y colores de ozono son ilustrativos; no sustituyen a las estaciones oficiales.")
            }
        }
        CaptionNote("Cartografía © OpenStreetMap contributors · Se necesita Internet para descargar las calles.")
    }
}

@Composable
private fun MapControl(icon: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color.White)
            .border(1.dp, AeriqColors.border, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        AeriqIcon(icon, size = 21.dp)
    }
}

@Composable
fun LearnScreen(onGo:(Screen)->Unit){
    var topic by remember{mutableStateOf("Ozono")}
    AeriqPage("Aprende",Screen.LEARN,onGo){
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
            .background(AeriqColors.green).padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
            AeriqText("Aprende",11,false,Color(0xFFB9DED4))
            AeriqText("Un aire limpio empieza por conocerlo.",26,true,Color.White)
            AeriqText("Qué contamina, cómo afecta a tu salud y qué dice la normativa.",13,false,Color(0xFFD4EAE4))
        }
        FigmaSegmented(listOf("Ozono","Salud","Normativa"),topic,{topic=it})
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                IconTile("air"); AeriqText("¿Qué es el ozono troposférico?",16,true)
            }
            AeriqText("Es un gas que se forma cerca del suelo cuando la luz del sol reacciona con contaminantes del tráfico y la industria. Sube en las horas centrales del día y en verano.",13,false,AeriqColors.muted)
        }
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                IconTile("heart");AeriqText("Efectos en la salud",16,true)
            }
            listOf("Irritación de ojos, nariz y garganta","Tos y dificultad para respirar",
                "Puede empeorar el asma y otras enfermedades respiratorias").forEach{
                AeriqText("•   $it",13)
            }
        }
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                IconTile("shield");AeriqText("Cómo protegerte",16,true)
            }
            listOf("Consulta el mapa antes de hacer deporte al aire libre",
                "Evita el ejercicio intenso en las horas de más sol",
                "Cuando el nivel sea alto, elige zonas verdes y primeras o últimas horas").forEach{
                AeriqText("•   $it",13)
            }
        }
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                IconTile("shield");AeriqText("Normativa",16,true)
            }
            AeriqText("La Directiva europea 2008/50/CE fija umbrales de información y de alerta para el ozono. Consulta los valores vigentes en las fuentes oficiales.",13,false,AeriqColors.muted)
            AeriqText("Ver la normativa ↗",13,true,AeriqColors.green)
        }
        Pane{
            AeriqText("Otros contaminantes",17,true)
            listOf("Ozono (O₃)" to "Lo mide tu sensor AERIQ",
                "Dióxido de nitrógeno (NO₂)" to "Procede sobre todo del tráfico",
                "Partículas (PM10 y PM2,5)" to "Se pueden inhalar en profundidad",
                "Monóxido de carbono (CO)" to "Combustión incompleta").forEachIndexed{index,pair->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        AeriqText(pair.first,13,true);CaptionNote(pair.second)
                    }
                    AeriqIcon("chevron",size=16.dp,tint=AeriqColors.subtle)
                }
                if(index!=3)HorizontalDivider(color=AeriqColors.border)
            }
        }
        CaptionNote("AERIQ mide ozono (O₃). Sus mediciones son complementarias; no sustituyen a las estaciones oficiales ni constituyen un diagnóstico de exposición personal.")
    }
}
