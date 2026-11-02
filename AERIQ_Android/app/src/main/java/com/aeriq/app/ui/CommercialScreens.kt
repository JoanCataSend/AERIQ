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
import androidx.compose.ui.unit.*
import com.aeriq.app.Screen
import com.aeriq.app.data.AeriqUser

@Composable
private fun CompareLines(){
    Canvas(Modifier.fillMaxWidth().height(185.dp)){
        val w=size.width;val h=size.height
        listOf(.15f,.55f,.95f).forEach { v -> drawLine(AeriqColors.border,Offset(0f,h*v),Offset(w,h*v),1.dp.toPx()) }
        val top=listOf(.52f,.50f,.49f,.51f,.58f,.69f,.78f,.86f,.90f,.88f,.83f,.75f)
        val low=listOf(.45f,.42f,.40f,.43f,.49f,.57f,.65f,.70f,.75f,.72f,.66f,.61f)
        fun draw(values:List<Float>,color:Color){
            val pts=values.mapIndexed{i,v->Offset(w*i/(values.size-1),h*(1-v))}
            val path=Path().apply{moveTo(pts[0].x,pts[0].y);pts.drop(1).forEach{lineTo(it.x,it.y)}}
            drawPath(path,color,style=androidx.compose.ui.graphics.drawscope.Stroke(2.3.dp.toPx()))
        }
        draw(top,AeriqColors.orange)
        draw(low,AeriqColors.green)
    }
}

@Composable
fun CompareScreen(onGo:(Screen)->Unit){
    var period by remember{mutableStateOf("7 días")}
    AeriqPage("Comparar zonas",Screen.MAP,onGo,back=true,backTo=Screen.MAP){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
            Row(Modifier.background(AeriqColors.forest,RoundedCornerShape(18.dp))
                .padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
                AeriqIcon("sparkles",size=17.dp,tint=Color.White)
                Spacer(Modifier.width(5.dp));AeriqText("Premium",12,true,Color.White)
            }
            AeriqText("Análisis ampliado de la red",12,false,AeriqColors.muted)
        }
        FigmaSegmented(listOf("24 h","7 días","30 días"),period,{period=it})
        Pane(padding=18.dp){
            Row(horizontalArrangement=Arrangement.spacedBy(15.dp),verticalAlignment=Alignment.CenterVertically){
                listOf("Ruzafa" to AeriqColors.orange,"Benimaclet" to AeriqColors.green).forEach{(label,c)->
                    Box(Modifier.size(8.dp).background(c,RoundedCornerShape(50)))
                    AeriqText(label,11,true)
                }
            }
            HorizontalDivider(color=AeriqColors.border)
            CompareLines()
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                listOf("Lun","Mié","Vie","Dom").forEach{CaptionNote(it)}
            }
        }
        Pane{
            SectionHeading("Zonas comparadas")
            listOf(Triple("Ruzafa","12 sensores",72),Triple("Benimaclet","7 sensores",54),
                Triple("El Cabanyal","5 sensores",66),Triple("Campanar","2 sensores",-1)).forEachIndexed{index,v->
                val ok=v.third>=0
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Column(Modifier.weight(1f)){
                        AeriqText(v.first,14,true,if(ok)AeriqColors.dark else AeriqColors.subtle)
                        CaptionNote(v.second)
                    }
                    AeriqText(if(ok)"${v.third}" else "—",17,true)
                    Box(Modifier.background(if(ok)AeriqColors.soft else AeriqColors.background,RoundedCornerShape(18.dp))
                        .padding(horizontal=8.dp,vertical=6.dp)){
                        AeriqText(if(ok)"Datos suficientes" else "Sin datos suficientes",10,true,
                            if(ok)AeriqColors.green else AeriqColors.muted)
                    }
                }
                if(index!=3)HorizontalDivider(color=AeriqColors.border)
            }
        }
        AeriqText("Solo se comparan zonas con datos suficientes. Datos de ejemplo.",11,false,AeriqColors.muted,
            modifier=Modifier.fillMaxWidth(),align=androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun PremiumScreen(yearly:Boolean,onYearly:(Boolean)->Unit,onGo:(Screen)->Unit,onMessage:(String)->Unit){
    AeriqPage("Premium",Screen.ACCOUNT,onGo,back=true,backTo=Screen.ACCOUNT){
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(AeriqColors.forest).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.background(Color(0xFF3E4845),RoundedCornerShape(18.dp))
                .padding(horizontal=11.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                AeriqIcon("sparkles",size=17.dp,tint=Color.White)
                Spacer(Modifier.width(7.dp));AeriqText("Premium",12,true,Color.White)
            }
            AeriqText("Entiende el ozono de toda tu ciudad.",28,true,Color.White)
            AeriqText("Funciones de análisis para quien quiere ir más allá de sus propias mediciones.",13,false,AeriqColors.inkLight)
        }
        Pane{
            listOf("Comparación de zonas de la red","Históricos ampliados de la red","Informes automáticos",
                "Una cuenta para la app y la web").forEach{label->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    IconTile("check")
                    AeriqText(label,14)
                }
            }
        }
        listOf(true,false).forEach { isYearly ->
            val active=yearly==isYearly
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                .background(if(active)AeriqColors.soft else Color.White)
                .border(if(active)2.dp else 1.dp,if(active)AeriqColors.green else AeriqColors.border,RoundedCornerShape(17.dp))
                .clickable{onYearly(isYearly)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                RadioButton(selected=active,onClick={onYearly(isYearly)})
                Column(Modifier.weight(1f)){
                    AeriqText(if(isYearly)"Anual" else "Mensual",16,true)
                    CaptionNote(if(isYearly)"Equivale a 10 mensualidades" else "Cancela cuando quieras")
                }
                AeriqText(if(isYearly)"39,90 €" else "3,99 €",23,true)
                AeriqText(if(isYearly)"/año" else "/mes",11,false,AeriqColors.muted)
            }
        }
        Pane{
            AeriqText("Renovación y cancelación.",13,true)
            AeriqText("Se cobra al inicio de cada periodo. Si cancelas, Premium sigue activo hasta el final del periodo pagado.",12)
            AeriqText("Pago seguro.",13,true)
            AeriqText("Lo gestiona un proveedor de pagos externo: AERIQ no guarda los datos de tu tarjeta.",12)
        }
        PrimaryButton("Continuar al pago seguro",{
            onMessage("La pasarela de pago aún no está integrada; no se realizará ningún cobro.")
        })
        AeriqText("El mapa público, los avisos y las funciones esenciales de tu sensor siguen gratis, con o sin Premium.",
            11,false,AeriqColors.muted,Modifier.fillMaxWidth(),androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AccountRow(icon:String,title:String,subtitle:String="",onClick:()->Unit,showSwitch:Boolean=false,
    isOn:Boolean=false,onToggle:((Boolean)->Unit)?=null,critical:Boolean=false){
    Row(Modifier.fillMaxWidth().clickable{if(!showSwitch)onClick()}.padding(vertical=3.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
        Box(Modifier.size(35.dp).background(if(critical)Color(0xFFFCE9E6) else AeriqColors.soft,
            RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){
            AeriqIcon(icon,size=19.dp,tint=if(critical)AeriqColors.red else AeriqColors.green)
        }
        Column(Modifier.weight(1f)){
            AeriqText(title,13,true,if(critical)AeriqColors.red else AeriqColors.dark)
            if(subtitle.isNotBlank())CaptionNote(subtitle)
        }
        if(showSwitch)Switch(checked=isOn,onCheckedChange={onToggle?.invoke(it)})
        else if(!critical)AeriqIcon("chevron",size=18.dp,tint=AeriqColors.subtle)
    }
}

@Composable
fun AccountScreen(name:String,sharing:Boolean,user:AeriqUser,onGo:(Screen)->Unit,onMessage:(String)->Unit,onLogout:()->Unit){
    var fingerprint by remember{mutableStateOf(false)}
    var alerts by remember{mutableStateOf(false)}
    AeriqPage("Cuenta",Screen.ACCOUNT,onGo){
        Pane{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Box(Modifier.size(55.dp).background(AeriqColors.soft,RoundedCornerShape(50)),contentAlignment=Alignment.Center){
                    AeriqText(user.name.take(1).uppercase() + user.last.take(1).uppercase(),20,true,AeriqColors.green)
                }
                Column{
                    AeriqText(user.fullName,18,true)
                    CaptionNote(user.email)
                    Box(Modifier.background(AeriqColors.background,RoundedCornerShape(12.dp)).padding(horizontal=8.dp,vertical=4.dp)){
                        AeriqText("Plan gratuito",10,true,AeriqColors.muted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AeriqColors.forest)
            .clickable{onGo(Screen.PREMIUM)}.padding(15.dp),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Box(Modifier.size(36.dp).background(Color(0xFF414C49),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){
                AeriqIcon("sparkles",size=19.dp,tint=Color.White)
            }
            Column(Modifier.weight(1f)){
                AeriqText("Hazte Premium",14,true,Color.White)
                AeriqText("Compara zonas y consulta históricos de la red",11,false,AeriqColors.inkLight)
            }
            AeriqIcon("chevron",size=18.dp,tint=Color.White)
        }
        Pane{
            AccountRow("user","Datos personales",onClick={onGo(Screen.EDIT_PROFILE)})
            HorizontalDivider(color=AeriqColors.border)
            AccountRow("chip","Mi sensor","$name · ${"Vinculado o demo"}",onClick={onGo(Screen.SENSOR)})
        }
        Pane{
            AccountRow("lock","Cambiar contraseña",onClick={onGo(Screen.CHANGE_PASSWORD)})
            HorizontalDivider(color=AeriqColors.border)
            AccountRow("fingerprint","Acceso con huella",showSwitch=true,isOn=fingerprint,
                onClick={},onToggle={fingerprint=it;onMessage("Preferencia visual; la biometría aún no está integrada.")})
            HorizontalDivider(color=AeriqColors.border)
            AccountRow("bell","Avisos de ozono","Cuando suba el nivel en tu zona",showSwitch=true,isOn=alerts,
                onClick={},onToggle={alerts=it;onMessage("Avisos simulados; todavía no se envían notificaciones.")})
        }
        Pane{
            AccountRow("community","Compartir con la red",if(sharing)"Opcional · Activado" else "Opcional · Desactivado",onClick={onGo(Screen.CONFIG)})
            HorizontalDivider(color=AeriqColors.border)
            AccountRow("download","Exportar mis datos",onClick={onGo(Screen.HISTORY)})
            HorizontalDivider(color=AeriqColors.border)
            AccountRow("shield","Privacidad y ubicación",onClick={onGo(Screen.CONFIG)})
        }
        Pane{
            AccountRow("package","Mis pedidos","Se gestionan en la web",onClick={onMessage("Pedidos disponibles en la futura web.")})
            HorizontalDivider(color=AeriqColors.border)
            AccountRow("info","Ayuda y contacto",onClick={onMessage("El canal de soporte todavía no está configurado.")})
        }
        Pane{
            AccountRow("logout","Cerrar sesión",critical=true,onClick=onLogout)
        }
    }
}
