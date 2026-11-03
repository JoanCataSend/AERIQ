package com.aeriq.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as NativeColor
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aeriq.app.Screen
import com.aeriq.app.data.ApiRoutes
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.*

internal data class PublicReading(val id: String, val campaign: String, val zone: String,
    val latitude: Double, val longitude: Double, val value: Double, val unit: String,
    val date: String, val simulated: Boolean, val radius: Int, val medium: Double, val high: Double) {
    val position get() = GeoPoint(latitude, longitude)
    val color get() = colorFor(value)
    fun colorFor(v: Double) = when { v >= high -> NativeColor.rgb(227,91,93); v >= medium -> NativeColor.rgb(238,162,45); else -> NativeColor.rgb(33,156,114) }
}

internal fun interpolatedOzone(latitude: Double, longitude: Double, rows: List<PublicReading>, radius: Int): Double? {
    if (radius !in 20..50) return null
    val center = GeoPoint(latitude, longitude)
    val near = rows.distinctBy { Pair(it.latitude, it.longitude) }.map { it to center.distanceToAsDouble(it.position) }.filter { it.second <= radius }
    if (near.size < 3) return null
    val first = near[0].first
    if (near.any { it.first.campaign != first.campaign || it.first.unit != first.unit || it.first.simulated != first.simulated }) return null
    val weights = near.map { 1.0 / max(1.0, it.second).pow(2) }
    return near.mapIndexed { i, point -> point.first.value * weights[i] }.sum() / weights.sum()
}

internal fun fetchPublicReadings(mode: String): Pair<List<PublicReading>,Boolean> {
    val c=URL(ApiRoutes.PUBLIC_MAP+"?action=map&mode=$mode").openConnection() as HttpURLConnection
    try {
        c.connectTimeout=10000;c.readTimeout=10000;c.instanceFollowRedirects=false
        check(c.responseCode==200){"Servidor no disponible (HTTP ${c.responseCode})"}
        val data=JSONObject(c.inputStream.bufferedReader().use{it.readText()});val array=data.getJSONArray("measurements")
        val readings=(0 until array.length()).map{i->val o=array.getJSONObject(i)
            PublicReading(o.getString("id"),o.getString("campaignId"),o.getString("zona"),o.getDouble("latitud"),o.getDouble("longitud"),o.getDouble("valor"),o.getString("unidad"),o.getString("capturada_en"),o.getBoolean("simulado"),o.getInt("radio_m"),o.getDouble("umbral_medio"),o.getDouble("umbral_alto"))}
        return readings to data.optBoolean("truncated")
    } finally {c.disconnect()}
}

@Composable
fun PublicMapScreen(onGo: (Screen) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var mode by remember { mutableStateOf("real") }
    var nonce by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<PublicReading>>(emptyList()) }
    var status by remember { mutableStateOf("Consultando mediciones autorizadas…") }
    var selected by remember { mutableStateOf<PublicReading?>(null) }
    var zones by remember { mutableStateOf(true) }
    var markers by remember { mutableStateOf(true) }
    var dark by remember { mutableStateOf(false) }
    var layers by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf("") }
    var locationAllowed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        locationAllowed = it.values.any { granted -> granted }
    }
    LaunchedEffect(Unit) {
        locationAllowed = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val location = rememberForegroundLocation(locationAllowed)
    val map = remember(context) {
        Configuration.getInstance().userAgentValue = "AERIQ-Android/1.0 (${context.packageName})"
        MapView(context).apply { setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(16.0); controller.setCenter(GANDIA_CAMPUS) }
    }
    DisposableEffect(map, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) map.onResume()
            if (event == Lifecycle.Event.ON_PAUSE) map.onPause()
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose { lifecycle.lifecycle.removeObserver(observer); map.onPause(); map.onDetach() }
    }
    DisposableEffect(mode, nonce) {
        var active = true
        rows = emptyList(); selected = null; status = "Consultando mediciones autorizadas…"
        val worker = Executors.newSingleThreadExecutor()
        worker.execute {
            val result = runCatching { fetchPublicReadings(mode) }
            Handler(Looper.getMainLooper()).post {
                if (active) {
                    rows = result.getOrNull()?.first ?: emptyList()
                    status = result.exceptionOrNull()?.message ?: "${rows.size} lecturas publicadas${if (result.getOrNull()?.second == true) " · las 2.000 más recientes" else ""}. Sin color = sin datos suficientes."
                    rows.firstOrNull()?.let { map.controller.setCenter(it.position) }
                }
            }
        }
        onDispose { active = false; worker.shutdownNow() }
    }
    fun locate() {
        if(!locationAllowed)permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        else if(location!=null)map.controller.animateTo(GeoPoint(location.latitude,location.longitude))
        else status="Esperando ubicación del dispositivo…"
    }
    fun search() {
        val match=rows.firstOrNull{it.zone.contains(term,true)}
        when {
            match!=null->map.controller.animateTo(match.position)
            term.contains("upv",true)||term.contains("campus",true)->map.controller.animateTo(GANDIA_CAMPUS)
            term.contains("playa",true)||term.contains("platja",true)->map.controller.animateTo(GeoPoint(38.999,-.1589))
            term.contains("grau",true)||term.contains("grao",true)->map.controller.animateTo(GeoPoint(38.9922,-.1656))
            else->status="Busca una zona publicada, UPV, playa o Grau de Gandía."
        }
    }
    AeriqPage("Mapa de ozono", Screen.MAP, onGo) {
        OutlinedTextField(term,{term=it},modifier=Modifier.fillMaxWidth(),placeholder={AeriqText("Buscar zona: UPV, playa…",13)},singleLine=true,
            leadingIcon={AeriqIcon("search",size=20.dp)},trailingIcon={TextButton(onClick={search()}){Text("Ir")}},shape=RoundedCornerShape(16.dp),
            keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()}))
        FigmaSegmented(listOf("Reales", "Simulados"), if (mode == "real") "Reales" else "Simulados", { mode = if (it == "Reales") "real" else "simulated" })
        CaptionNote(status)
        Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(18.dp)).border(1.dp,AeriqColors.border,RoundedCornerShape(18.dp))) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { view ->
            view.overlays.clear()
            if (zones) {
                var drawn = 0
                var examined = 0
                rows.groupBy { it.campaign }.values.forEach { group ->
                    val first = group.first()
                    val latStep = 10.0 / 111320.0
                    val lonStep = 10.0 / (111320.0 * cos(first.latitude * PI / 180.0))
                    val bucketLat = latStep * 5
                    val bucketLon = lonStep * 5
                    val buckets = group.groupBy { Pair(floor(it.latitude / bucketLat).toLong(), floor(it.longitude / bucketLon).toLong()) }
                    val cells = mutableSetOf<Pair<Long, Long>>()
                    group.forEach { r ->
                        val a = (r.latitude / latStep).roundToLong(); val b = (r.longitude / lonStep).roundToLong()
                        for (i in -5..5) for (j in -5..5) cells.add(Pair(a + i, b + j))
                    }
                    for ((a, b) in cells) {
                        if (drawn >= 1500 || examined++ >= 5000) break
                        val lat = a * latStep; val lon = b * lonStep
                        val x = floor(lat / bucketLat).toLong(); val y = floor(lon / bucketLon).toLong()
                        val nearby = mutableListOf<PublicReading>()
                        for (i in -1..1) for (j in -1..1) nearby.addAll(buckets[Pair(x+i,y+j)] ?: emptyList())
                        val value = interpolatedOzone(lat, lon, nearby, first.radius) ?: continue
                        view.overlays.add(Polygon(view).apply {
                            points = listOf(GeoPoint(lat, lon), GeoPoint(lat + latStep, lon), GeoPoint(lat + latStep, lon + lonStep), GeoPoint(lat, lon + lonStep))
                            fillColor = (first.colorFor(value) and 0x00ffffff) or (60 shl 24); strokeWidth = 0f
                        }); drawn++
                    }
                }
            }
            if(markers) for (r in rows) view.overlays.add(Marker(view).apply {
                position = r.position; title = "${r.value} ${r.unit} · ${r.zone}"
                snippet = "${r.date} UTC · ${if (r.simulated) "SIMULADO" else "Real"}"
                icon = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(r.color); setStroke(3, NativeColor.WHITE); setSize(25, 25) }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> selected = r; true }
            })
            location?.let { point -> view.overlays.add(Marker(view).apply { position = GeoPoint(point.latitude, point.longitude); title = "Mi ubicación"; icon = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(NativeColor.BLUE); setSize(22,22) } }) }
            view.overlayManager.tilesOverlay.setColorFilter(if(dark)darkTileMatrix else null)
            view.invalidate()
        })
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            PublicMapControl("target", "Mi ubicación", {locate()})
            PublicMapControl("layers", "Capas", {layers=!layers})
            PublicMapControl("location", "Volver a UPV", {map.controller.animateTo(GANDIA_CAMPUS)})
        }
        if(layers)Column(Modifier.align(Alignment.TopStart).padding(10.dp).background(Color.White,RoundedCornerShape(14.dp)).padding(10.dp)) {
            AeriqText("Capas del mapa",12,true)
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(zones,{zones=it});AeriqText("Interpolación 20–50 m",11)}
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(markers,{markers=it});AeriqText("Mediciones",11)}
            TextButton(onClick={dark=!dark}){Text(if(dark)"Modo claro" else "Modo oscuro")}
        }
        Row(Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.White.copy(alpha=.95f),RoundedCornerShape(18.dp)).padding(9.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            AeriqText("● Bajo",10,color=AeriqColors.green);AeriqText("● Medio",10,color=AeriqColors.orange);AeriqText("● Alto",10,color=AeriqColors.red)
        }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { map.controller.zoomIn() }) { Text("Acercar") }
            TextButton(onClick = { map.controller.zoomOut() }) { Text("Alejar") }
            TextButton(onClick = { map.controller.setCenter(GANDIA_CAMPUS) }) { Text("UPV") }
            TextButton(onClick = { nonce++ }) { Text("Actualizar") }
        }
        OutlineAction("Mostrar mi ubicación", {
            if (!locationAllowed) permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            else location?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
        })
        selected?.let { r -> Pane {
            AeriqText("${r.value} ${r.unit}", 26, true); AeriqText(r.zone, 16, true)
            CaptionNote("${r.date} UTC · ${r.latitude}, ${r.longitude}")
            CaptionNote(if (r.simulated) "Medición simulada" else "Medición real; no sustituye a las estaciones oficiales")
        } }
        OutlineAction("Comparar zonas publicadas",{onGo(Screen.COMPARE)})
        CaptionNote("© OpenStreetMap contributors. Verde / naranja / rojo según los umbrales de cada campaña. Las áreas sin al menos tres ubicaciones próximas permanecen sin color.")
    }
}

@Composable
private fun PublicMapControl(icon: String,label: String,onClick: ()->Unit) {
    Box(Modifier.size(42.dp).background(Color.White,RoundedCornerShape(13.dp)).border(1.dp,AeriqColors.border,RoundedCornerShape(13.dp)).clickable(onClick=onClick),contentAlignment=Alignment.Center){AeriqIcon(icon,size=21.dp)}
}
