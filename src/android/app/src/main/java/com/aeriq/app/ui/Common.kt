package com.aeriq.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import com.aeriq.app.R
import com.aeriq.app.Screen

/** Figma Web / ANDORID (capa editable). Los colores del manual corporativo se
 *  documentan en LEEME, pero el prototipo de Figma define estos tokens UI. */
object AeriqColors {
    val background=Color(0xFFF4F7F6)
    val white=Color(0xFFFFFFFF)
    val dark=Color(0xFF17201D)
    val forest=Color(0xFF1C2522)
    val green=Color(0xFF0E6B5E)
    val muted=Color(0xFF5E6B66)
    val subtle=Color(0xFF8A9792)
    val soft=Color(0xFFE4F0ED)
    val border=Color(0xFFE3EAE7)
    val orange=Color(0xFFECA32A)
    val red=Color(0xFFC8433D)
    val good=Color(0xFF229D72)
    val inkLight=Color(0xFFB7C2BE)
}

@Composable
fun AeriqTheme(content:@Composable ()->Unit) {
    val scheme=lightColorScheme(primary=AeriqColors.green,background=AeriqColors.background,
        surface=AeriqColors.white,onPrimary=Color.White,onSurface=AeriqColors.dark,
        outline=AeriqColors.border,secondary=AeriqColors.soft)
    MaterialTheme(colorScheme=scheme,content=content)
}

object AeriqFonts {
    // Cambiar aquí al importar las dos fuentes del manual en res/font.
    val headings=FontFamily.SansSerif
    val body=FontFamily.SansSerif
}

/** El diseño editable de Figma emplea Inter; el manual describe Space Grotesk
 *  e IBM Plex Sans. La fuente instalada del sistema actúa como fallback hasta
 *  que se importen las fuentes licenciadas del proyecto (no se redistribuyen). */
@Composable
fun AeriqText(text:String,size:Int=14,bold:Boolean=false,color:Color=AeriqColors.dark,
              modifier:Modifier=Modifier,align:TextAlign?=null) {
    Text(text,modifier=modifier,color=color,fontSize=size.sp,
        fontWeight=if(bold) FontWeight.Bold else FontWeight.Normal,
        fontFamily=if(size>=17&&bold)AeriqFonts.headings else AeriqFonts.body,
        lineHeight=(size*1.38).sp,textAlign=align)
}

@DrawableRes
fun iconRes(name:String):Int=when(name){
    "map"->R.drawable.figma_nav_map
    "chip"->R.drawable.figma_nav_chip
    "book"->R.drawable.figma_nav_book
    "user"->R.drawable.figma_nav_user
    "bluetooth"->R.drawable.aeriq_bluetooth
    "battery"->R.drawable.aeriq_battery
    "location"->R.drawable.aeriq_location
    "community"->R.drawable.aeriq_community
    "wind"->R.drawable.aeriq_wind
    "ozone"->R.drawable.aeriq_ozone
    "temperature"->R.drawable.aeriq_temperature
    "humidity"->R.drawable.aeriq_humidity
    "camera"->R.drawable.aeriq_camera
    "mark"->R.drawable.aeriq_mark
    "back"->R.drawable.ic_back
    "bell"->R.drawable.ic_bell
    "search"->R.drawable.ic_search
    "settings"->R.drawable.ic_settings
    "fingerprint"->R.drawable.ic_fingerprint
    "layers"->R.drawable.ic_layers
    "target"->R.drawable.ic_target
    "sparkles"->R.drawable.ic_sparkles
    "check"->R.drawable.ic_check
    "chevron"->R.drawable.ic_chevron
    "download"->R.drawable.ic_download
    "refresh"->R.drawable.ic_refresh
    "alert"->R.drawable.ic_alert
    "external"->R.drawable.ic_external
    "heart"->R.drawable.ic_heart
    "shield"->R.drawable.ic_shield
    "lock"->R.drawable.ic_lock
    "logout"->R.drawable.ic_logout
    "calendar"->R.drawable.ic_calendar
    "plus"->R.drawable.ic_plus
    "chart"->R.drawable.ic_chart
    "air"->R.drawable.ic_air
    "globe"->R.drawable.ic_globe
    "info"->R.drawable.ic_info
    "package"->R.drawable.ic_package
    else->R.drawable.aeriq_ozone
}

@Composable
fun AeriqIcon(name:String,modifier:Modifier=Modifier,size:Dp=23.dp,tint:Color=AeriqColors.dark){
    Image(painter=painterResource(iconRes(name)),contentDescription=null,
        modifier=modifier.size(size),colorFilter=ColorFilter.tint(tint))
}

@Composable
fun AeriqBrandLogo(modifier:Modifier=Modifier){
    Image(painter=painterResource(R.drawable.aeriq_logo),contentDescription="AERIQ",
        modifier=modifier.width(99.dp).height(34.dp),colorFilter=ColorFilter.tint(AeriqColors.dark))
}

@Composable
fun Pane(modifier:Modifier=Modifier,padding:Dp=16.dp,content:@Composable ColumnScope.()->Unit){
    Column(modifier=modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(AeriqColors.white).border(1.dp,AeriqColors.border,RoundedCornerShape(22.dp))
        .padding(padding), verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}

@Composable
fun PrimaryButton(text:String,onClick:()->Unit,modifier:Modifier=Modifier,dark:Boolean=false){
    Button(onClick=onClick,modifier=modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),
        colors=ButtonDefaults.buttonColors(containerColor=if(dark)AeriqColors.forest else AeriqColors.green)) {
        AeriqText(text,15,true,Color.White)
    }
}

@Composable
fun OutlineAction(text:String,onClick:()->Unit,modifier:Modifier=Modifier){
    OutlinedButton(onClick=onClick,modifier=modifier.fillMaxWidth().height(49.dp),shape=RoundedCornerShape(14.dp),
        border=BorderStroke(1.dp,Color(0xFFD1DDD8))){AeriqText(text,14,true)}
}

@Composable
fun InputField(label:String,value:String,onChange:(String)->Unit,placeholder:String="",password:Boolean=false){
    var reveal by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        AeriqText(label,13,true)
        OutlinedTextField(value=value,onValueChange=onChange,modifier=Modifier.fillMaxWidth().height(55.dp),
            singleLine=true,placeholder={AeriqText(placeholder,14,false,AeriqColors.subtle)},
            textStyle=androidx.compose.ui.text.TextStyle(fontSize=14.sp,color=AeriqColors.dark),
            visualTransformation=if(password&&!reveal)PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon=if(password) {{TextButton(onClick={reveal=!reveal}){AeriqText(if(reveal)"Ocultar" else "Mostrar",12,true,AeriqColors.green)}}} else null,
            shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(
                focusedContainerColor=AeriqColors.background,unfocusedContainerColor=AeriqColors.background,
                focusedBorderColor=AeriqColors.green,unfocusedBorderColor=AeriqColors.border))
    }
}

@Composable
fun Header(title:String,back:Boolean=false,onBack:()->Unit={},trailing:String?=null,onTrailing:()->Unit={}){
    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal=16.dp),
        horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(36.dp).clickable {if(back)onBack()},contentAlignment=Alignment.CenterStart){
            if(back)AeriqIcon("back",size=24.dp)
            else Image(painterResource(R.drawable.aeriq_mark),null,Modifier.size(27.dp),
                colorFilter=ColorFilter.tint(AeriqColors.dark))
        }
        AeriqText(title,17,true)
        Box(Modifier.size(36.dp).clickable {onTrailing()},contentAlignment=Alignment.CenterEnd){
            if(trailing!=null) AeriqIcon(trailing,size=24.dp)
        }
    }
}

@Composable
fun BottomNavigation(selected:Screen,onGo:(Screen)->Unit){
    val tabs=listOf(Triple(Screen.MAP,"Mapa","map"),Triple(Screen.SENSOR,"Mi sensor","chip"),
        Triple(Screen.LEARN,"Aprende","book"),Triple(Screen.ACCOUNT,"Cuenta","user"))
    Row(Modifier.fillMaxWidth().height(80.dp).background(AeriqColors.white)
        .border(0.5.dp,AeriqColors.border),verticalAlignment=Alignment.CenterVertically){
        tabs.forEach{(route,label,icon)->
            val active=selected==route||(selected in listOf(Screen.LINK,Screen.HISTORY,Screen.CONFIG)&&route==Screen.SENSOR)
                ||(selected==Screen.COMPARE&&route==Screen.MAP)||(selected==Screen.PREMIUM&&route==Screen.ACCOUNT)
            Column(Modifier.weight(1f).fillMaxHeight().clickable {onGo(route)}.padding(top=8.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                Box(Modifier.width(56.dp).height(31.dp).background(if(active)AeriqColors.soft else Color.Transparent,RoundedCornerShape(18.dp)),
                    contentAlignment=Alignment.Center){AeriqIcon(icon,size=21.dp,tint=if(active)AeriqColors.green else AeriqColors.muted)}
                AeriqText(label,12,active,if(active)AeriqColors.green else AeriqColors.muted)
            }
        }
    }
}

@Composable
fun AeriqPage(title:String,active:Screen,onGo:(Screen)->Unit,back:Boolean=false,backTo:Screen=Screen.MAP,
              trailing:String?=null,onTrailing:()->Unit={},body:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().background(AeriqColors.background)){
        Header(title,back,{onGo(backTo)},trailing,onTrailing)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(horizontal=16.dp).padding(bottom=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=body)
        BottomNavigation(active,onGo)
    }
}

@Composable
fun InfoRow(label:String,value:String,accent:Boolean=false){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween){
        AeriqText(label,13,color=AeriqColors.muted)
        AeriqText(value,13,true,if(accent)AeriqColors.green else AeriqColors.dark)
    }
}

@Composable fun SectionHeading(text:String){AeriqText(text,19,true)}
@Composable fun CaptionNote(text:String){AeriqText(text,11,false,AeriqColors.muted)}
@Composable fun DemoChip(){
    Box(Modifier.background(AeriqColors.soft,RoundedCornerShape(40.dp)).padding(horizontal=9.dp,vertical=4.dp)){
        AeriqText("Datos de ejemplo",10,true,AeriqColors.green)
    }
}

@Composable
fun FigmaSegmented(options:List<String>,selected:String,onSelected:(String)->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEAF0ED)).padding(4.dp)){
        options.forEach{opt->
            Box(Modifier.weight(1f).height(39.dp).clip(RoundedCornerShape(10.dp))
                .background(if(opt==selected)Color.White else Color.Transparent)
                .clickable{onSelected(opt)},contentAlignment=Alignment.Center){
                AeriqText(opt,12,true,if(opt==selected)AeriqColors.dark else AeriqColors.muted)
            }
        }
    }
}

@Composable
fun IconTile(name:String,tint:Color=AeriqColors.green){
    Box(Modifier.size(38.dp).background(AeriqColors.soft,RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){
        AeriqIcon(name,size=20.dp,tint=tint)
    }
}

@Composable
fun LabeledLink(text:String,onClick:()->Unit){
    TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)){
        AeriqText(text,13,true,AeriqColors.green)
    }
}
