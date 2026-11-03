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
