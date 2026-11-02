package com.aeriq.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import com.aeriq.app.R
import com.aeriq.app.Screen
import com.aeriq.app.ble.AeriqBleController
import com.aeriq.app.ble.LiveMeasurement

@Composable
fun LineGraph(){
    Canvas(Modifier.fillMaxWidth().height(140.dp)){
        val values=listOf(.35f,.32f,.29f,.30f,.33f,.37f,.42f,.46f,.50f,.54f,.57f,.61f,.65f,.70f,.75f,.80f,.62f)
        val points=values.mapIndexed{i,v->Offset(size.width*i/(values.size-1),size.height*(1-v))}
        val p=Path().apply{moveTo(points.first().x,points.first().y);points.drop(1).forEach{lineTo(it.x,it.y)}}
        val area=Path().apply{addPath(p);lineTo(size.width,size.height);lineTo(0f,size.height);close()}
        drawPath(area,Color(0xFFE2F2EC))
        drawPath(p,Color(0xFF16A374),style=androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
        drawCircle(AeriqColors.green,5.dp.toPx(),points.last())
        drawCircle(Color.White,3.dp.toPx(),points.last())
    }
}

@Composable
private fun SensorStatusCard(name:String,linked:Boolean,status:String,onGo:(Screen)->Unit){
    Pane(padding=14.dp){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Box(Modifier.size(55.dp).clip(RoundedCornerShape(12.dp)).background(AeriqColors.background),
                contentAlignment=Alignment.Center){
                Image(painterResource(R.drawable.aeriq_mark),null,Modifier.size(32.dp))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                AeriqText(name,16,true)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(8.dp).background(if(linked)AeriqColors.good else AeriqColors.orange,RoundedCornerShape(50)))
                    Spacer(Modifier.width(6.dp))
                    AeriqText(if(linked)"Recibiendo anuncios BLE" else "Sensor no vinculado",11,true,
                        if(linked)AeriqColors.green else AeriqColors.muted)
                }
            }
            AeriqIcon("bluetooth",size=19.dp,tint=AeriqColors.muted)
        }
        if(!linked)OutlineAction("Vincular sensor",{onGo(Screen.LINK)})
        else if(status.isNotBlank())CaptionNote(status)
    }
}

@Composable
fun SensorScreen(name:String,linked:Boolean,sharing:Boolean,onSharing:(Boolean)->Unit,onGo:(Screen)->Unit,
    liveO3:LiveMeasurement?=null,liveTemperature:LiveMeasurement?=null,status:String="",serverStatus:String="",
    scanning:Boolean=false,onResumeScan:()->Unit={}){
    val measured=liveO3?.reading?.value
    AeriqPage("Mi sensor",Screen.SENSOR,onGo,trailing="settings",onTrailing={onGo(Screen.CONFIG)}){
        SensorStatusCard(name,linked,status,onGo)
        Pane(padding=17.dp){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween){
                AeriqText("O₃ ahora",13,true)
                Box(Modifier.background(AeriqColors.soft,RoundedCornerShape(18.dp)).padding(horizontal=10.dp,vertical=6.dp)){
                    AeriqText(if(measured==null)"Calidad: Buena" else "BLE recibido",11,true,AeriqColors.green)
                }
            }
            Row(verticalAlignment=Alignment.Bottom){
                AeriqText("${measured ?: 68}",46,true)
                Spacer(Modifier.width(4.dp))
                AeriqText(if(measured==null)"µg/m³" else "ppb",13,false,AeriqColors.muted,
                    Modifier.padding(bottom=7.dp))
            }
            LineGraph()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                listOf("00:00","06:00","12:00").forEach{CaptionNote(it)}
            }
            CaptionNote(if(measured==null)"Última medición: hoy, 12:45 · Ejemplo" else
                "Lectura BLE real, sin conversión a µg/m³. Gráfico ilustrativo.")
            OutlineAction("Ver historial    →",{onGo(Screen.HISTORY)})
        }
        Pane{
            AeriqText("Tu día",19,true)
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                Column(Modifier.weight(1f).background(AeriqColors.background,RoundedCornerShape(14.dp)).padding(12.dp)){
                    CaptionNote("Distancia · Ejemplo");AeriqText("6,4 km",19,true)
                }
                Column(Modifier.weight(1f).background(AeriqColors.background,RoundedCornerShape(14.dp)).padding(12.dp)){
                    CaptionNote("O₃ medio · Ejemplo");AeriqText("60 µg/m³",19,true)
                }
            }
            CaptionNote("Calidad del aire que has respirado hoy · Ejemplo")
            Row(Modifier.fillMaxWidth().height(15.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                repeat(14){i->Box(Modifier.weight(1f).fillMaxHeight().background(if(i in 9..11)AeriqColors.orange else Color(0xFF38AA7A)))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){CaptionNote("00:00");CaptionNote("Ahora")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Box(Modifier.weight(1f)){
                Pane(modifier=Modifier.clickable{onGo(Screen.CONFIG)}){
                    IconTile("chart")
                    AeriqText("Estado del sensor",14,true)
                }
            }
            Box(Modifier.weight(1f)){
                Pane(modifier=Modifier.clickable{onGo(Screen.CONFIG)}){
                    IconTile("settings")
                    AeriqText("Configurar sensor",14,true)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(AeriqColors.soft,RoundedCornerShape(19.dp)).padding(14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            AeriqIcon("community",size=22.dp,tint=AeriqColors.green)
            Column(Modifier.weight(1f)){
                AeriqText("Compartiendo con la red",14,true)
                CaptionNote("Opcional. Se envían lecturas sin GPS en esta versión.")
            }
            Switch(checked=sharing,onCheckedChange=onSharing)
        }
        if(linked&&!scanning)OutlineAction("Reanudar recepción BLE",onResumeScan)
        if(liveTemperature!=null)CaptionNote("Temperatura BLE: ${liveTemperature.reading.value} °C")
        if(serverStatus.isNotBlank())CaptionNote(serverStatus)
    }
}

@Composable
fun LinkScreen(onGo:(Screen)->Unit,devices:List<AeriqBleController.FoundDevice>,status:String,scanning:Boolean,
               selectedAddress:String?,onScan:()->Unit,onStop:()->Unit,onSelect:(String)->Unit){
    AeriqPage("Vincular sensor",Screen.SENSOR,onGo,back=true,backTo=Screen.SENSOR){
        Box(Modifier.fillMaxWidth().height(166.dp).clip(RoundedCornerShape(16.dp))
            .background(AeriqColors.soft),contentAlignment=Alignment.Center){
            Image(painterResource(R.drawable.aeriq_orb_hero),null,
                modifier=Modifier.fillMaxHeight().fillMaxWidth())
        }
        AeriqText("Conecta tu sensor",27,true)
        AeriqText("Enciende el sensor y acércalo al móvil. AERIQ lo encontrará por Bluetooth.",14,false,AeriqColors.muted)
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                IconTile("bluetooth");Column(Modifier.weight(1f)){
                    AeriqText("Bluetooth",15,true);CaptionNote("Necesario para leer el sensor")
                }
                Switch(checked=scanning,onCheckedChange={if(it)onScan()else onStop()})
            }
            HorizontalDivider(color=AeriqColors.border)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                IconTile("location");Column(Modifier.weight(1f)){
                    AeriqText("Ubicación",15,true);CaptionNote("Para situar cada medición en el mapa")
                }
                AeriqIcon("info",size=20.dp,tint=AeriqColors.muted)
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            AeriqText("Sensores cercanos",17,true)
            CaptionNote(if(scanning)"Buscando…" else "Búsqueda detenida")
        }
        if(devices.isEmpty()){
            Pane{
                CaptionNote("Todavía no hay sensores detectados. Activa Bluetooth y enciende la placa PBIO.")
                PrimaryButton(if(scanning)"Detener búsqueda" else "Buscar sensores",if(scanning)onStop else onScan)
            }
        }else devices.forEach{device->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                .background(AeriqColors.white).border(if(selectedAddress==device.address)2.dp else 1.dp,
                if(selectedAddress==device.address)AeriqColors.green else AeriqColors.border,RoundedCornerShape(17.dp))
                .padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                IconTile("chip")
                Column(Modifier.weight(1f)){
                    AeriqText(device.name,15,true)
                    CaptionNote("${device.address.takeLast(8)} · ${device.rssi} dBm")
                }
                Button(onClick={onSelect(device.address)},shape=RoundedCornerShape(12.dp)){
                    AeriqText("Vincular",12,true,Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(AeriqColors.soft,RoundedCornerShape(18.dp)).padding(14.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
            IconTile("package")
            Column(Modifier.weight(1f)){
                AeriqText("¿Aún no tienes sensor?",14,true)
                CaptionNote("Puedes comprarlo en la web de AERIQ. El mapa público sigue siendo gratis.")
            }
            AeriqIcon("external",size=18.dp,tint=AeriqColors.green)
        }
        CaptionNote("Este firmware emite anuncios iBeacon; vincular significa seleccionar el emisor, no conectarse por GATT. $status")
    }
}

@Composable
fun HistoryScreen(onGo:(Screen)->Unit,onMessage:(String)->Unit){
    var period by remember{mutableStateOf("Día")}
    AeriqPage("Historial",Screen.SENSOR,onGo,back=true,backTo=Screen.SENSOR,
        trailing="settings",onTrailing={onGo(Screen.CONFIG)}){
        FigmaSegmented(listOf("Día","Semana","Mes","Año"),period,{period=it})
        Pane(padding=18.dp){
            CaptionNote("Hoy, 29 de octubre · Ejemplo")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment=Alignment.Bottom){
                AeriqText("O₃",20,true);Spacer(Modifier.width(10.dp))
                AeriqText("68",42,true);AeriqText("µg/m³",12,false,AeriqColors.muted,Modifier.padding(bottom=8.dp))
            }
            CaptionNote("Media del día: 60 µg/m³")
            HorizontalDivider(color=AeriqColors.border)
            Row(Modifier.fillMaxWidth().height(135.dp),verticalAlignment=Alignment.Bottom,
                horizontalArrangement=Arrangement.spacedBy(3.dp)){
                listOf(40,36,33,35,39,44,49,54,62,70,76,84,89,63).forEachIndexed{idx,n->
                    Box(Modifier.weight(1f).fillMaxHeight(n/100f)
                        .background(if(idx in 10..12)AeriqColors.orange else Color(0xFF219F72),
                            RoundedCornerShape(topStart=4.dp,topEnd=4.dp)))
                }
                repeat(9){Box(Modifier.weight(.6f).height(9.dp).background(AeriqColors.border,RoundedCornerShape(50)))}
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                listOf("00","06","12","18","24").forEach{CaptionNote(it)}
            }
            CaptionNote("● Bajo      ● Moderado")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            Box(Modifier.weight(1f)){Pane{CaptionNote("Máximo");AeriqText("92 µg/m³",19,true)}}
            Box(Modifier.weight(1f)){Pane{CaptionNote("Mínimo");AeriqText("38 µg/m³",19,true)}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            Box(Modifier.weight(1f)){Pane{CaptionNote("Media");AeriqText("60 µg/m³",19,true)}}
            Box(Modifier.weight(1f)){Pane{CaptionNote("Mediciones");AeriqText("13",19,true)}}
        }
        PrimaryButton("↓  Exportar datos (CSV o PDF)",{
            onMessage("Exportación pendiente de datos reales. No se genera un informe simulado.")
        })
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AeriqColors.forest)
            .clickable{onGo(Screen.COMPARE)}.padding(16.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)){
            IconTile("sparkles")
            Column(Modifier.weight(1f)){
                AeriqText("Compara tu zona con el resto de la red",14,true,Color.White)
                AeriqText("Históricos ampliados con Premium",11,false,AeriqColors.inkLight)
            }
            AeriqIcon("chevron",size=18.dp,tint=Color.White)
        }
    }
}

@Composable
fun ConfigScreen(name:String,onName:(String)->Unit,sharing:Boolean,onSharing:(Boolean)->Unit,
    saveRoute:Boolean,onSaveRoute:(Boolean)->Unit,linked:Boolean,onUnlink:()->Unit,onGo:(Screen)->Unit,
    onMessage:(String)->Unit,lastO3:LiveMeasurement?=null,scanning:Boolean=false){
    var edited by remember(name){mutableStateOf(name)}
    AeriqPage("Configurar sensor",Screen.SENSOR,onGo,back=true,backTo=Screen.SENSOR){
        Pane{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                AeriqText("Estado del sensor",17,true)
                Box(Modifier.background(AeriqColors.soft,RoundedCornerShape(20.dp)).padding(horizontal=9.dp,vertical=5.dp)){
                    AeriqText(if(linked)"●  Seleccionado" else "No vinculado",11,true,AeriqColors.green)
                }
            }
            listOf("Conexión" to if(linked)"Bluetooth LE" else "Sin conexión",
                "Batería" to "No transmitida", "Señal" to (lastO3?.let{"${it.rssi} dBm"}?:"Sin medición"),
                "Última sincronización" to if(lastO3!=null)"Lectura BLE reciente" else "Pendiente",
                "Sensor" to "SPEC O₃").forEachIndexed{index,p->
                InfoRow(p.first,p.second)
                if(index!=4)HorizontalDivider(color=AeriqColors.border)
            }
        }
        Pane{
            AeriqText("Nombre del sensor",18,true)
            InputField("Nombre",edited,{edited=it},"Sensor PBIO #001")
            OutlineAction("Guardar nombre",{
                if(edited.isBlank())onMessage("El nombre no puede estar vacío.")
                else {onName(edited);onMessage("Nombre guardado en esta sesión.")}
            })
        }
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    AeriqText("Compartir con la red",15,true)
                    CaptionNote("Opcional")
                }
                Switch(checked=sharing,onCheckedChange=onSharing)
            }
            AeriqText("Tus mediciones ayudan a mejorar el mapa público. En esta versión se envían lecturas BLE al servidor sin GPS.",12)
            HorizontalDivider(color=AeriqColors.border)
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    AeriqText("Guardar mi recorrido y distancia",13,true)
                    CaptionNote("Solo lo ves tú. No se publica. Pendiente de integrar.")
                }
                Switch(checked=false,onCheckedChange={onMessage("El registro de recorridos todavía no está implementado.")})
            }
        }
        OutlineAction("Reconectar sensor",{onGo(Screen.LINK)})
        OutlinedButton(onClick={onUnlink();onMessage("Emisor BLE deseleccionado.")},
            modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,Color(0xFFF0BDB6))){
            AeriqIcon("logout",size=18.dp,tint=AeriqColors.red)
            Spacer(Modifier.width(8.dp))
            AeriqText("Desvincular sensor",14,true,AeriqColors.red)
        }
        AeriqText("Puedes cambiar estas opciones cuando quieras. Las funciones esenciales del sensor no se bloquean.",11,
            color=AeriqColors.muted,modifier=Modifier.fillMaxWidth(),align=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
