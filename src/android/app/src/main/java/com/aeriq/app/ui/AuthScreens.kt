package com.aeriq.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.aeriq.app.Screen
import com.aeriq.app.data.AeriqAuthManager

@Composable
private fun AuthLayout(title: String, description: String, onBack: (() -> Unit)? = null,
                       content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(AeriqColors.background).verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            AeriqBrandLogo()
            if (onBack != null) {
                Box(Modifier.align(Alignment.CenterStart).size(44.dp).clickable { onBack() },
                    contentAlignment = Alignment.CenterStart) { AeriqIcon("back") }
            }
        }
        Spacer(Modifier.height(4.dp))
        AeriqText(title, 30, true, modifier = Modifier.fillMaxWidth(), align = TextAlign.Center)
        AeriqText(description, 14, false, AeriqColors.muted, Modifier.fillMaxWidth(), TextAlign.Center)
        content()
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun AuthSubmit(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth().height(53.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AeriqColors.forest)) {
        if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else AeriqText(label, 15, true, Color.White)
    }
}

@Composable
fun LoginScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf(auth.pendingEmail) }
    var password by rememberSaveable { mutableStateOf("") }
    AuthLayout("Iniciar sesión", "La misma cuenta de AERIQ para tu móvil y la web.") {
        Pane(padding = 20.dp) {
            InputField("Correo electrónico", email, { email = it }, "nombre@ejemplo.com")
            InputField("Contraseña", password, { password = it }, "Tu contraseña", true)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                LabeledLink("¿Has olvidado tu contraseña?", { onGo(Screen.FORGOT) })
            }
            AuthSubmit("Entrar", auth.busy) {
                when {
                    email.isBlank() || password.isBlank() -> onMessage("Introduce correo y contraseña.")
                    else -> auth.login(email, password) { result ->
                        password = ""
                        when {
                            result.success -> onGo(Screen.MAP)
                            result.needsVerification -> {
                                onMessage("Te falta confirmar el correo. Introduce el código recibido.")
                                onGo(Screen.VERIFY)
                            }
                            else -> onMessage(result.message)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            AeriqText("Tu organización crea tu cuenta de trabajador.", 13, false, AeriqColors.muted)
            Spacer(Modifier.width(6.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AeriqColors.soft)
            .clickable { onGo(Screen.MAP) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AeriqIcon("map", size = 24.dp, tint = AeriqColors.green)
            Column(Modifier.weight(1f)) {
                AeriqText("Explorar el mapa sin cuenta", 14, true)
                CaptionNote("El mapa público se puede consultar sin iniciar sesión.")
            }
            AeriqText("Abrir", 12, true, AeriqColors.green)
        }
        CaptionNote("Google, Apple y acceso con huella no están activados. En esta versión usa tu correo verificado.")
    }
}

@Composable
fun RegisterScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    var accepted by rememberSaveable { mutableStateOf(false) }
    AuthLayout("Crear cuenta", "Recibirás un código de seis cifras en tu correo.", onBack = { onGo(Screen.LOGIN) }) {
        Pane(padding = 20.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { InputField("Nombre", name, { name = it }, "Nombre") }
                Box(Modifier.weight(1f)) { InputField("Apellidos", last, { last = it }, "Apellidos") }
            }
            InputField("Correo electrónico", email, { email = it }, "nombre@ejemplo.com")
            InputField("Contraseña", pass, { pass = it }, "Mínimo 12 caracteres", true)
            InputField("Repetir contraseña", repeat, { repeat = it }, "Repite la contraseña", true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                Spacer(Modifier.width(5.dp))
                AeriqText("He leído y acepto el tratamiento de mis datos para crear la cuenta AERIQ.", 12)
            }
            AuthSubmit("Crear cuenta", auth.busy) {
                when {
                    name.isBlank() || last.isBlank() || !email.contains('@') -> onMessage("Completa nombre, apellidos y correo.")
                    pass.length < 12 -> onMessage("El servidor exige una contraseña de al menos 12 caracteres.")
                    pass != repeat -> onMessage("Las contraseñas no coinciden.")
                    !accepted -> onMessage("Acepta las condiciones para continuar.")
                    else -> auth.register(name, last, email, pass) { result ->
                        pass = ""; repeat = ""
                        if (result.success) { onMessage("Comprueba tu correo, también la carpeta spam."); onGo(Screen.VERIFY) }
                        else onMessage(result.message)
                    }
                }
            }
        }
        LabeledLink("¿Ya tienes cuenta? Inicia sesión", { onGo(Screen.LOGIN) })
    }
}

@Composable
fun VerifyEmailScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf(auth.pendingEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    AuthLayout("Verifica tu correo", "Introduce el código que hemos enviado. Caduca en 10 minutos.",
        onBack = { onGo(Screen.LOGIN) }) {
        Pane(padding = 20.dp) {
            InputField("Correo de la cuenta", email, { email = it }, "nombre@ejemplo.com")
            InputField("Código de 6 cifras", code, { code = it.filter(Char::isDigit).take(6) }, "000000")
            AuthSubmit("Confirmar y entrar", auth.busy) {
                if (code.length != 6 || !email.contains('@')) onMessage("Introduce el correo y el código completo.")
                else auth.verify(email, code) { result ->
                    code = ""
                    if (result.success) { onMessage("Correo verificado. Ya puedes utilizar AERIQ."); onGo(Screen.MAP) }
                    else onMessage(result.message)
                }
            }
            LabeledLink("Reenviar código", {
                if (!email.contains('@')) onMessage("Introduce un correo válido.")
                else auth.resend(email) { result -> onMessage(result.message) }
            })
        }
        CaptionNote("Los códigos son de un solo uso y tienen un límite de intentos.")
    }
}

@Composable
fun ForgotPasswordScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf(auth.pendingEmail) }
    AuthLayout("Recuperar contraseña", "Te enviaremos un código para establecer una nueva contraseña.",
        onBack = { onGo(Screen.LOGIN) }) {
        Pane(padding = 20.dp) {
            InputField("Correo electrónico", email, { email = it }, "nombre@ejemplo.com")
            AuthSubmit("Enviar código", auth.busy) {
                if (!email.contains('@')) onMessage("Introduce un correo válido.")
                else auth.forgot(email) { result ->
                    if (result.success) { onMessage(result.message); onGo(Screen.RESET) }
                    else onMessage(result.message)
                }
            }
        }
    }
}

@Composable
fun ResetPasswordScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf(auth.pendingEmail) }
    var code by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    AuthLayout("Nueva contraseña", "Introduce el código recibido y tu nueva contraseña.",
        onBack = { onGo(Screen.FORGOT) }) {
        Pane(padding = 20.dp) {
            InputField("Correo electrónico", email, { email = it }, "nombre@ejemplo.com")
            InputField("Código", code, { code = it.filter(Char::isDigit).take(6) }, "000000")
            InputField("Nueva contraseña", pass, { pass = it }, "Mínimo 12 caracteres", true)
            InputField("Repetir contraseña", repeat, { repeat = it }, "Repite la contraseña", true)
            AuthSubmit("Cambiar contraseña", auth.busy) {
                when {
                    !email.contains('@') || code.length != 6 -> onMessage("Revisa el correo y el código de seis cifras.")
                    pass.length < 12 || pass != repeat -> onMessage("La contraseña debe tener 12 caracteres o más y coincidir.")
                    else -> auth.resetPassword(email, code, pass) { result ->
                        code = ""; pass = ""; repeat = ""
                        if (result.success) { onMessage("Contraseña actualizada."); onGo(Screen.MAP) }
                        else onMessage(result.message)
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit) {
    val user = auth.user
    var name by remember(user?.id) { mutableStateOf(user?.name.orEmpty()) }
    var last by remember(user?.id) { mutableStateOf(user?.last.orEmpty()) }
    var phone by remember(user?.id) { mutableStateOf(user?.phone.orEmpty()) }
    AuthLayout("Datos personales", "Tu perfil se guarda en la misma base de datos que la web.",
        onBack = { onGo(Screen.ACCOUNT) }) {
        Pane(padding = 20.dp) {
            InputField("Nombre", name, { name = it }, "Nombre")
            InputField("Apellidos", last, { last = it }, "Apellidos")
            InputField("Teléfono (opcional)", phone, { phone = it }, "Teléfono")
            CaptionNote("Correo de la cuenta: ${user?.email.orEmpty()}")
            AuthSubmit("Guardar cambios", auth.busy) {
                if (name.isBlank() || last.isBlank()) onMessage("Introduce nombre y apellidos.")
                else auth.updateProfile(name, last, phone) { result ->
                    if (result.success) { onMessage("Datos actualizados en MariaDB."); onGo(Screen.ACCOUNT) }
                    else onMessage(result.message)
                }
            }
        }
    }
}

@Composable
fun ChangePasswordScreen(auth: AeriqAuthManager, onGo: (Screen) -> Unit, onMessage: (String) -> Unit, mandatory: Boolean = false) {
    var current by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    AuthLayout(if (mandatory) "Sustituye tu contraseña temporal" else "Cambiar contraseña", "Elige una contraseña diferente de al menos 12 caracteres.", onBack = if (mandatory) null else ({ onGo(Screen.ACCOUNT) })) {
        if (mandatory) LabeledLink("Cerrar sesión", { auth.logout { onGo(Screen.LOGIN) } })
        Pane(padding = 20.dp) {
            InputField("Contraseña actual", current, { current = it }, "Contraseña actual", true)
            InputField("Nueva contraseña", password, { password = it }, "Mínimo 12 caracteres", true)
            InputField("Confirmar nueva contraseña", confirm, { confirm = it }, "Repetir", true)
            AuthSubmit("Actualizar contraseña", auth.busy) {
                if (password.length < 12 || password != confirm) onMessage("Introduce una contraseña de al menos 12 caracteres y repítela.")
                else auth.changePassword(current, password) { result ->
                    current = ""; password = ""; confirm = ""
                    if (result.success) { onMessage("Contraseña actualizada."); onGo(Screen.MAP) }
                    else onMessage(result.message)
                }
            }
        }
    }
}
