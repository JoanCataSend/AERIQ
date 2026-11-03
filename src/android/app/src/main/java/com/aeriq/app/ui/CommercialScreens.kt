package com.aeriq.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aeriq.app.Screen
import com.aeriq.app.data.AeriqUser
import com.aeriq.app.data.ApiRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CompareScreen(onGo: (Screen)->Unit) {
    var mode by remember{mutableStateOf("Simulados")}
    var rows by remember{mutableStateOf<List<PublicReading>>(emptyList())}
    var status by remember{mutableStateOf("Consultando mapa público…")}
    LaunchedEffect(mode){
        rows=emptyList()
        val result=withContext(Dispatchers.IO){runCatching{fetchPublicReadings(if(mode=="Reales")"real" else "simulated")}}
        rows=result.getOrNull()?.first ?: emptyList();status=result.exceptionOrNull()?.message ?: "${rows.size} mediciones públicas · últimas 24 h"
    }
    AeriqPage("Comparar zonas",Screen.MAP,onGo,back=true,backTo=Screen.MAP) {
        Pane {AeriqText("Conoce las zonas medidas",22,true);CaptionNote("Compara datos publicados sin suscripciones. Las zonas sin mediciones no reciben un valor estimado.")}
        FigmaSegmented(listOf("Reales","Simulados"),mode,{mode=it});CaptionNote(status)
        rows.groupBy{it.zone+" · "+it.unit}.forEach{(name,readings)->
            Pane {AeriqText(name,17,true);MeasurementGraph(readings.sortedBy{it.date}.map{it.value});InfoRow("Mediciones",readings.size.toString());InfoRow("Media","%.1f".format(readings.map{it.value}.average()));InfoRow("Mínimo / máximo","${readings.minOf{it.value}} / ${readings.maxOf{it.value}}");CaptionNote("Origen: $mode · Datos disponibles, no cobertura de toda la zona.")}
        }
        if(rows.isEmpty())Pane{CaptionNote("No hay datos publicados de este origen para comparar.")}
    }
}

@Composable
fun ServicesScreen(onGo: (Screen)->Unit) {
    val context=LocalContext.current
    AeriqPage("Soluciones AERIQ",Screen.ACCOUNT,onGo,back=true,backTo=Screen.ACCOUNT) {
        Pane {AeriqText("Tu campaña, a tu medida.",27,true);CaptionNote("Código Norte desarrolla AERIQ y el sensor Orb. La consulta del mapa público es gratuita.")}
        listOf(Triple("administracion","Kits para administraciones","Para campañas municipales y estudios del territorio."),Triple("empresa","Kits para empresas","Sensores, trabajadores y campañas de tu organización."),Triple("estudio","Estudios ambientales","Código Norte realiza el estudio sin que tengas que comprar sensores.")).forEach{(service,title,description)->
            Pane {IconTile("package");AeriqText(title,19,true);AeriqText(description,14,color=AeriqColors.muted);PrimaryButton("Solicitar información ↗",{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(ApiRoutes.BASE+"solicitar.html?service=$service")))})}
        }
        CaptionNote("Los presupuestos se preparan al revisar las necesidades. Las mediciones no sustituyen a las estaciones oficiales.")
    }
}

@Composable
private fun AccountRow(icon: String,title: String,subtitle: String="",onClick: ()->Unit,critical: Boolean=false) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconTile(icon,tint=if(critical)AeriqColors.red else AeriqColors.green)
        Column(Modifier.weight(1f)){AeriqText(title,14,true,if(critical)AeriqColors.red else AeriqColors.dark);if(subtitle.isNotBlank())CaptionNote(subtitle)}
        AeriqIcon("chevron",size=17.dp,tint=AeriqColors.subtle)
    }
}

@Composable
fun AccountScreen(name: String,sharing: Boolean,user: AeriqUser,onGo: (Screen)->Unit,onMessage: (String)->Unit,onLogout: ()->Unit) {
    val context=LocalContext.current
    AeriqPage("Cuenta",Screen.ACCOUNT,onGo) {
        Pane {Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(58.dp).background(AeriqColors.soft,RoundedCornerShape(50)),contentAlignment=Alignment.Center){AeriqText(user.name.take(1)+user.last.take(1),21,true,AeriqColors.green)}
            Column {AeriqText(user.fullName,18,true);CaptionNote(user.email);CaptionNote(user.organization.ifBlank{"Consulta pública"});AeriqText(user.role.replaceFirstChar{it.uppercase()},11,true,AeriqColors.green)}
        }}
        Pane {AccountRow("user","Datos personales",onClick={onGo(Screen.EDIT_PROFILE)});HorizontalDivider(color=AeriqColors.border);AccountRow("chip","Mi sensor",name,onClick={onGo(Screen.SENSOR)})}
        Pane {AccountRow("lock","Cambiar contraseña",onClick={onGo(Screen.CHANGE_PASSWORD)});HorizontalDivider(color=AeriqColors.border);AccountRow("fingerprint","Acceso biométrico","Mejora prevista",onClick={onMessage("El acceso biométrico todavía no está implementado. Tu sesión utiliza el token protegido por Android Keystore.")})}
        Pane {AccountRow("community","Envío de campaña",if(sharing)"Activado" else "Desactivado",onClick={onGo(Screen.CONFIG)});HorizontalDivider(color=AeriqColors.border);AccountRow("download","Historial y exportación",onClick={onGo(Screen.HISTORY)});HorizontalDivider(color=AeriqColors.border);AccountRow("shield","Privacidad y ubicación",onClick={onGo(Screen.CONFIG)})}
        Row(Modifier.fillMaxWidth().background(AeriqColors.forest,RoundedCornerShape(20.dp)).clickable{onGo(Screen.SERVICES)}.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){AeriqText("Soluciones para organizaciones",16,true,Color.White);AeriqText("Kits Orb y estudios de Código Norte",12,color=AeriqColors.inkLight)};AeriqIcon("chevron",tint=Color.White)
        }
        Pane {AccountRow("globe","Panel de organización","Trabajadores, campañas e informes en la web",onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(ApiRoutes.BASE+"portal.html")))});AccountRow("info","Ayuda y contacto",onClick={onGo(Screen.SERVICES)})}
        Pane {AccountRow("logout","Cerrar sesión",onClick=onLogout,critical=true)}
        CaptionNote("AERIQ · Orb · Desarrollado por Código Norte")
    }
}
