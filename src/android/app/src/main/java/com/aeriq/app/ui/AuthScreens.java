package com.aeriq.app.ui;

import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import com.aeriq.app.MainActivity;
import com.aeriq.app.MainActivity.Screen;
import com.aeriq.app.R;
import com.aeriq.app.data.AeriqAuthManager;

import static com.aeriq.app.ui.Views.*;

public final class AuthScreens {
    private static final int TEXT = InputType.TYPE_CLASS_TEXT;
    private static final int EMAIL = TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    private static final int PASSWORD = TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    private final MainActivity activity;
    private final Views ui;
    private final AeriqAuthManager auth;

    public AuthScreens(MainActivity activity, Views ui, AeriqAuthManager auth) {
        this.activity = activity;
        this.ui = ui;
        this.auth = auth;
    }

    public void show(Screen screen, LinearLayout body) {
        ImageView logo = ui.image(R.drawable.aeriq_logo, 112, 42);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(ui.dp(112), ui.dp(42));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.setMargins(0, ui.dp(20), 0, ui.dp(22));
        body.addView(logo, logoParams);
        String title;
        String description;
        switch (screen) {
            case REGISTER: title = "Crear cuenta"; description = "Recibirás un código de seis cifras en tu correo."; break;
            case VERIFY: title = "Verifica tu correo"; description = "El código caduca en 10 minutos."; break;
            case FORGOT: title = "Recuperar contraseña"; description = "Te enviaremos un código para establecer una nueva contraseña."; break;
            case RESET: title = "Nueva contraseña"; description = "Introduce el código recibido y tu nueva contraseña."; break;
            case EDIT_PROFILE: title = "Datos personales"; description = "Tu perfil se comparte con la web de AERIQ."; break;
            case CHANGE_PASSWORD:
                title = auth.user.mustChangePassword ? "Sustituye tu contraseña temporal" : "Cambiar contraseña";
                description = "Elige una contraseña diferente de al menos 12 caracteres.";
                break;
            default: title = "Iniciar sesión"; description = "La misma cuenta de AERIQ para tu móvil y la web.";
        }
        ui.label(body, title, 30, true, INK).setGravity(Gravity.CENTER);
        ui.label(body, description, 14, false, MUTED).setGravity(Gravity.CENTER);
        LinearLayout card = ui.card(body);
        switch (screen) {
            case LOGIN: login(body, card); break;
            case REGISTER: register(body, card); break;
            case VERIFY: case FORGOT: case RESET: recovery(screen, body, card); break;
            case EDIT_PROFILE: profile(body, card); break;
            case CHANGE_PASSWORD: password(body, card); break;
            default: throw new IllegalArgumentException(screen.name());
        }
    }

    private void login(LinearLayout body, LinearLayout card) {
        EditText email = ui.field(card, "Correo electrónico", auth.pendingEmail, EMAIL);
        EditText password = ui.field(card, "Contraseña", "", PASSWORD);
        ui.link(card, "¿Has olvidado tu contraseña?", () -> activity.go(Screen.FORGOT));
        submit(card, "Entrar", "login", Screen.MAP, () -> !value(email).isEmpty() && password.length() > 0,
            () -> new Object[]{"email", value(email), "password", value(password)});
        ui.note(body, "Tu organización crea tu cuenta de trabajador.");
        ui.link(body, "Crear cuenta", () -> activity.go(Screen.REGISTER));
        LinearLayout publicMap = ui.card(body);
        ui.label(publicMap, "Explorar el mapa sin cuenta", 18, true, INK);
        ui.note(publicMap, "El mapa público se puede consultar sin iniciar sesión.");
        ui.outline(publicMap, "Abrir mapa", () -> activity.go(Screen.MAP));
    }

    private void register(LinearLayout body, LinearLayout card) {
        LinearLayout names = ui.row();
        LinearLayout first = ui.column(), last = ui.column();
        ui.weighted(names, first);
        ui.weighted(names, last);
        ui.add(card, names);
        EditText name = ui.field(first, "Nombre", "", TEXT);
        EditText surname = ui.field(last, "Apellidos", "", TEXT);
        EditText email = ui.field(card, "Correo electrónico", "", EMAIL);
        EditText password = ui.field(card, "Contraseña", "", PASSWORD);
        EditText confirm = ui.field(card, "Repetir contraseña", "", PASSWORD);
        CheckBox consent = new CheckBox(activity);
        consent.setText("Acepto el tratamiento de mis datos para crear la cuenta AERIQ.");
        ui.add(card, consent);
        submit(card, "Crear cuenta", "register", Screen.VERIFY,
            () -> name.length() > 0 && surname.length() > 0 && validEmail(email)
                && matching(password, confirm) && consent.isChecked(),
            () -> new Object[]{"name", value(name).trim(), "last", value(surname).trim(),
                "email", value(email), "password", value(password)});
        ui.link(body, "Ya tengo cuenta", () -> activity.go(Screen.LOGIN));
    }

    private void recovery(Screen screen, LinearLayout body, LinearLayout card) {
        EditText email = ui.field(card, "Correo electrónico", auth.pendingEmail, EMAIL);
        if (screen == Screen.FORGOT) {
            submit(card, "Enviar código", "forgot", Screen.RESET, () -> validEmail(email),
                () -> new Object[]{"email", value(email)});
        } else {
            EditText code = ui.field(card, "Código de seis cifras", "", InputType.TYPE_CLASS_NUMBER);
            code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
            if (screen == Screen.VERIFY) {
                submit(card, "Confirmar y entrar", "verify", Screen.MAP,
                    () -> validEmail(email) && code.length() == 6,
                    () -> new Object[]{"email", value(email), "code", value(code)});
                submit(card, "Reenviar código", "resend", null, () -> validEmail(email),
                    () -> new Object[]{"email", value(email)});
            } else {
                EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
                EditText confirm = ui.field(card, "Repetir contraseña", "", PASSWORD);
                submit(card, "Cambiar contraseña", "reset-password", Screen.MAP,
                    () -> validEmail(email) && code.length() == 6 && matching(password, confirm),
                    () -> new Object[]{"email", value(email), "code", value(code), "password", value(password)});
            }
        }
        ui.link(body, "Volver", () -> activity.go(screen == Screen.RESET ? Screen.FORGOT : Screen.LOGIN));
    }

    private void profile(LinearLayout body, LinearLayout card) {
        EditText name = ui.field(card, "Nombre", auth.user.name, TEXT);
        EditText last = ui.field(card, "Apellidos", auth.user.last, TEXT);
        EditText phone = ui.field(card, "Teléfono (opcional)", auth.user.phone, InputType.TYPE_CLASS_PHONE);
        ui.note(card, auth.user.email);
        submit(card, "Guardar cambios", "profile", Screen.ACCOUNT,
            () -> !value(name).trim().isEmpty() && !value(last).trim().isEmpty(),
            () -> new Object[]{"name", value(name).trim(), "last", value(last).trim(), "phone", value(phone).trim()});
        ui.link(body, "Volver a mi cuenta", () -> activity.go(Screen.ACCOUNT));
    }

    private void password(LinearLayout body, LinearLayout card) {
        EditText current = ui.field(card, "Contraseña actual", "", PASSWORD);
        EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
        EditText confirm = ui.field(card, "Confirmar nueva contraseña", "", PASSWORD);
        submit(card, "Actualizar contraseña", "change-password", Screen.MAP,
            () -> current.length() > 0 && matching(password, confirm),
            () -> new Object[]{"current", value(current), "password", value(password)});
        if (auth.user.mustChangePassword) ui.link(body, "Cerrar sesión", activity::logout);
        else ui.link(body, "Volver a mi cuenta", () -> activity.go(Screen.ACCOUNT));
    }

    private void submit(LinearLayout card, String caption, String action, Screen next,
                        java.util.function.BooleanSupplier valid, java.util.function.Supplier<Object[]> fields) {
        Button button = ui.action(caption, DARK, WHITE, () -> {});
        ui.add(card, button);
        button.setOnClickListener(view -> {
            if (auth.busy) return;
            if (!valid.getAsBoolean()) {
                activity.message("Revisa los datos. Las contraseñas nuevas deben coincidir y tener 12 caracteres.");
                return;
            }
            button.setEnabled(false);
            activity.submit(action, result -> {
                button.setEnabled(true);
                activity.message(result.message);
                if (result.needsVerification) activity.go(Screen.VERIFY);
                else if (result.success && next != null) activity.go(next);
            }, fields.get());
        });
    }

    private static String value(EditText input) { return input.getText().toString(); }
    private static boolean validEmail(EditText input) { return value(input).contains("@"); }
    private static boolean matching(EditText first, EditText second) {
        return first.length() >= 12 && value(first).equals(value(second));
    }
}
