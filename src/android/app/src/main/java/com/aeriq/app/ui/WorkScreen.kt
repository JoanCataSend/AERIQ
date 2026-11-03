package com.aeriq.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aeriq.app.R
import com.aeriq.app.Screen
import com.aeriq.app.ble.AeriqBleController
import com.aeriq.app.data.OrbAssignment
import com.aeriq.app.data.StoredReading
import com.aeriq.app.data.measurementCsv
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
internal fun MeasurementGraph(values: List<Double>, modifier: Modifier = Modifier) {
    if(values.isEmpty()) { Box(modifier.fillMaxWidth().height(135.dp),contentAlignment=Alignment.Center){CaptionNote("El gráfico aparecerá cuando haya mediciones guardadas.")};return }
    Canvas(modifier.fillMaxWidth().height(145.dp)) {
        val low=minOf(0.0,values.minOrNull() ?: 0.0);val high=maxOf(low+1,values.maxOrNull() ?: 1.0)
        repeat(4){i->drawLine(AeriqColors.border,Offset(0f,size.height*i/3),Offset(size.width,size.height*i/3),1.dp.toPx())}
        val pts=values.mapIndexed{i,v->Offset(if(values.size==1)size.width/2 else size.width*i/(values.size-1),((1-(v-low)/(high-low))*size.height).toFloat())}
        val line=Path().apply{moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach{lineTo(it.x,it.y)}}
        val area=Path().apply{addPath(line);lineTo(pts.last().x,size.height);lineTo(pts.first().x,size.height);close()}
        drawPath(area,AeriqColors.soft);drawPath(line,AeriqColors.green,style=androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
        drawCircle(AeriqColors.green,4.dp.toPx(),pts.last())
    }
}

@Composable
fun WorkScreen(ble: AeriqBleController, assignments: List<OrbAssignment>, assignmentStatus: String,
               serverStatus: String, sending: Boolean, onSending: (Boolean) -> Unit,
               onRefresh: () -> Unit, onScan: () -> Unit, onStop: () -> Unit, onGo: (Screen) -> Unit,
               history: List<StoredReading>, historyStatus: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true){now=System.currentTimeMillis();delay(1000)} }
    val selected=assignments.firstOrNull { it.address.equals(ble.selectedAddress,true) }
    val values=history.filter { it.type==14 && it.deviceId==selected?.id && it.simulated==selected?.simulated }.sortedBy { it.date }
    val age=now-ble.lastAdvertisementAt
    val receiving=selected!=null&&ble.scanning&&ble.lastAdvertisementAt>0&&age<=15000
    val reception=when {
        selected==null->"Selecciona tu Orb asignado"
        !ble.scanning->"Recepción detenida"
        ble.lastAdvertisementAt==0L->"Esperando el primer anuncio"
        !receiving->"Sin anuncios desde hace ${age/1000} s"
        else->"Anuncios recibidos hace ${age/1000} s"
    }
    AeriqPage("Mi sensor",Screen.SENSOR,onGo,trailing="settings",onTrailing={onGo(Screen.CONFIG)}) {
        Pane(padding=14.dp) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(56.dp).background(AeriqColors.soft,RoundedCornerShape(14.dp)),contentAlignment=Alignment.Center){Image(painterResource(R.drawable.aeriq_mark),"Orb",Modifier.size(31.dp))}
                Column(Modifier.weight(1f)) {AeriqText(selected?.name ?: "Tu sensor Orb",17,true);CaptionNote(selected?.campaign ?: "Sin Orb seleccionado")}
                AeriqIcon("bluetooth",tint=if(receiving)AeriqColors.green else AeriqColors.muted)
            }
            CaptionNote(reception)
        }
        Pane(padding=18.dp) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {AeriqText("O₃ ahora",14,true);AeriqText(if(selected?.simulated==true)"SIMULADO" else if(selected==null)"Sin lectura" else "Real",11,true,AeriqColors.green)}
            Row(verticalAlignment=Alignment.Bottom) {AeriqText(ble.lastO3?.reading?.value?.toString() ?: "—",46,true);Spacer(Modifier.width(8.dp));AeriqText("ppb",14,color=AeriqColors.muted,modifier=Modifier.padding(bottom=8.dp))}
            MeasurementGraph(values.map{it.value})
            CaptionNote(if(values.isEmpty())historyStatus else "Histórico guardado · ${values.first().date.take(16)} — ${values.last().date.take(16)} UTC")
            OutlineAction("Ver historial y exportar  →",{onGo(Screen.HISTORY)})
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)){Pane{IconTile("bluetooth");AeriqText("Recepción BLE",13,true);CaptionNote(reception)}}
            Box(Modifier.weight(1f)){Pane{IconTile("globe");AeriqText("Servidor",13,true);CaptionNote(serverStatus)}}
        }
        Pane {
            AeriqText("Tu campaña",19,true)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)){CaptionNote("Lecturas guardadas");AeriqText(values.size.toString(),23,true)}
                Column(Modifier.weight(1f)){CaptionNote("Media disponible");AeriqText(if(values.isEmpty())"—" else "%.1f ppb".format(values.map{it.value}.average()),21,true)}
            }
            CaptionNote("Temperatura BLE: ${ble.lastTemperature?.reading?.value ?: "—"} °C · Batería: no transmitida")
            CaptionNote("La batería no se estima. Los tres LED necesitan el firmware y el circuito del Orb.")
        }
        Pane {
            AeriqText("Orb asignados",18,true);CaptionNote(assignmentStatus)
            if(assignments.isEmpty())CaptionNote("Tu organización debe asignarte un dispositivo y una campaña vigente.")
            assignments.forEach { orb ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){AeriqText(orb.name,14,true);CaptionNote(orb.campaign);CaptionNote(if(orb.simulated)"Datos simulados" else "Datos reales")}
                    val detected=ble.devices.any{it.address.equals(orb.address,true)&&now-it.lastSeen<15000}
                    Button(onClick={ble.select(orb.address)},enabled=detected&&selected?.id!=orb.id){Text(if(selected?.id==orb.id)"Seleccionado" else "Seleccionar")}
                }
                HorizontalDivider(color=AeriqColors.border)
            }
            PrimaryButton(if(ble.scanning)"Detener búsqueda / recepción" else "Buscar Orb por Bluetooth",if(ble.scanning)onStop else onScan)
            OutlineAction("Actualizar asignaciones",onRefresh)
            CaptionNote("iBeacon: recibir anuncios no implica una conexión Bluetooth permanente.")
        }
        Row(Modifier.fillMaxWidth().background(AeriqColors.soft,RoundedCornerShape(19.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            AeriqIcon("community",tint=AeriqColors.green)
            Column(Modifier.weight(1f)){AeriqText("Enviar a la campaña",14,true);CaptionNote("GPS solo con permiso. Publicación según autorización de tu organización.")}
            Switch(sending,onSending,enabled=selected!=null)
        }
    }
}

@Composable
fun WorkerHistoryScreen(history: List<StoredReading>, status: String, onRefresh: () -> Unit,
                        onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var period by remember { mutableStateOf("Semana") }
    val days=when(period){"Día"->1;"Semana"->7;"Mes"->30;else->365}
    val cutoff=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date(System.currentTimeMillis()-days*86400000L))
    val rows=history.filter{it.type==14&&it.date>=cutoff}.sortedBy{it.date}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {uri->
        if(uri!=null)runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {out->
                out.write(measurementCsv(rows))
            } ?: error("No se pudo abrir el destino")
        }.onSuccess{onMessage("CSV guardado.")}.onFailure{onMessage("No se pudo exportar: ${it.message}")}
    }
    LaunchedEffect(Unit){onRefresh()}
    AeriqPage("Historial",Screen.SENSOR,onGo,back=true,backTo=Screen.SENSOR) {
        FigmaSegmented(listOf("Día","Semana","Mes","Año"),period,{period=it});CaptionNote(status)
        Pane{AeriqText("O₃ · últimos $days días",18,true);CaptionNote("UTC · Máximo 1.000 lecturas recientes de tus asignaciones.")}
        rows.groupBy{it.simulated}.forEach{(simulated,group)->Pane{AeriqText(if(simulated)"Serie simulada" else "Serie real",16,true);MeasurementGraph(group.map{it.value});InfoRow("Media de esta serie","%.1f ppb".format(group.map{it.value}.average()))}}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)){Pane{CaptionNote("Mínimo");AeriqText(rows.minOfOrNull{it.value}?.toString() ?: "—",22,true)}}
            Box(Modifier.weight(1f)){Pane{CaptionNote("Máximo");AeriqText(rows.maxOfOrNull{it.value}?.toString() ?: "—",22,true)}}
        }
        Pane{InfoRow("Mediciones",rows.size.toString());CaptionNote("Las medias y gráficas se calculan por separado para datos reales y simulados.")}
        Button(onClick={export.launch("AERIQ-historial.csv")},enabled=rows.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("Exportar mediciones CSV")}
        OutlineAction("Actualizar",onRefresh)
        rows.asReversed().take(80).forEach{r->Pane(padding=12.dp){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){AeriqText(r.device,14,true);AeriqText("${r.value} ${r.unit}",16,true)};CaptionNote("${r.date} UTC · ${r.campaign}");CaptionNote("${if(r.simulated)"Simulado" else "Real"} · ${r.latitude?.let{"$it, ${r.longitude}"} ?: "Sin GPS autorizado"}")}}
        if(rows.size>80)CaptionNote("La pantalla resume las últimas 80; el CSV incluye el resultado completo del filtro.")
    }
}

@Composable
fun WorkSettingsScreen(orb: OrbAssignment?,sending: Boolean,onSending: (Boolean)->Unit,onUnlink: ()->Unit,onGo: (Screen)->Unit) {
    AeriqPage("Configurar sensor",Screen.SENSOR,onGo,back=true,backTo=Screen.SENSOR) {
        Pane {AeriqText("Tu dispositivo",19,true);InfoRow("Nombre",orb?.name ?: "Sin selección");InfoRow("Campaña",orb?.campaign ?: "Sin asignación");InfoRow("Recepción","Anuncios iBeacon");InfoRow("Batería","No transmitida");CaptionNote("El administrador gestiona el nombre, el alta y la asignación desde la web.")}
        Pane {Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){AeriqText("Enviar lecturas",15,true);CaptionNote("GPS opcional y solo en primer plano")};Switch(sending,onSending,enabled=orb!=null)};CaptionNote("La publicación pública se autoriza en la campaña; enviar al servidor no publica automáticamente tu ubicación.")}
        OutlineAction("Buscar / cambiar Orb",{onGo(Screen.LINK)})
        OutlineAction("Deseleccionar este Orb",onUnlink)
        CaptionNote("AERIQ · Orb · Desarrollado por Código Norte")
    }
}
