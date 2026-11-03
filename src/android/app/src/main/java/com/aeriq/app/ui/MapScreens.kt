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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun locationAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Localización exclusivamente en primer plano, mientras se visita la pestaña Mapa. */
@SuppressLint("MissingPermission")
@Composable
internal fun rememberForegroundLocation(allowed: Boolean): Location? {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }
    var lastPosition by remember { mutableStateOf<Location?>(null) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(allowed, manager, owner) {
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

            fun startLocation() {
                runCatching { manager.removeUpdates(listener) }
                providers.forEach { provider ->
                    runCatching {
                        val cached = manager.getLastKnownLocation(provider)
                        if (cached != null && System.currentTimeMillis() - cached.time in 0..600000L &&
                            (lastPosition == null || cached.time > (lastPosition?.time ?: 0))) lastPosition = cached
                        if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, 2500L, 3f, listener, Looper.getMainLooper())
                    }
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) startLocation()
                if (event == Lifecycle.Event.ON_PAUSE) runCatching { manager.removeUpdates(listener) }
            }
            owner.lifecycle.addObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startLocation()
            onDispose {
                owner.lifecycle.removeObserver(observer)
                runCatching { manager.removeUpdates(listener) }
            }
        }
    }
    return lastPosition
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
