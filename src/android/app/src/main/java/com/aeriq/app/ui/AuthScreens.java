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
        if (screen == Screen.LOGIN || screen == Screen.REGISTER) { loginScreen(body); return; }
        ImageView logo = ui.image(R.drawable.aeriq_logo, 99, 34);
        FrameLayout brand = new FrameLayout(activity);
        brand.addView(logo, new FrameLayout.LayoutParams(ui.dp(99), ui.dp(34), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        boolean mandatory = screen == Screen.CHANGE_PASSWORD && auth.user.mustChangePassword;
        if (screen != Screen.LOGIN && !mandatory) {
            ImageView back = ui.image(R.drawable.ic_back, 48, 48);
            back.setPadding(0, ui.dp(3), ui.dp(25), ui.dp(23));
            back.setContentDescription("Atrás");
            back.setOnClickListener(view -> activity.onBackPressed());
            brand.addView(back, new FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.TOP | Gravity.START));
        }
        body.addView(brand, new LinearLayout.LayoutParams(-1, ui.dp(66)));
        String title;
        String description;
        switch (screen) {
            case VERIFY: title = "Verifica tu correo"; description = "Introduce el código que hemos enviado. Caduca en 10 minutos."; break;
            case FORGOT: title = "Recuperar contraseña"; description = "Te enviaremos un código para establecer una nueva contraseña."; break;
            case RESET: title = "Nueva contraseña"; description = "Introduce el código recibido y tu nueva contraseña."; break;
            case EDIT_PROFILE: title = "Datos personales"; description = "Tu perfil se guarda en la misma base de datos que la web."; break;
            case CHANGE_PASSWORD:
                title = auth.user.mustChangePassword ? "Sustituye tu contraseña temporal" : "Cambiar contraseña";
                description = "Elige una contraseña diferente de al menos 12 caracteres.";
                break;
            default: title = "Iniciar sesión"; description = "La misma cuenta de AERIQ para tu móvil y la web.";
        }
        ui.label(body, title, 30, true, INK).setGravity(Gravity.CENTER);
        ui.label(body, description, 14, false, MUTED).setGravity(Gravity.CENTER);
        if (mandatory) ui.link(body, "Cerrar sesión", activity::logout);
        LinearLayout card = ui.card(body);
        switch (screen) {
            case VERIFY: case FORGOT: case RESET: recovery(screen, body, card); break;
            case EDIT_PROFILE: profile(body, card); break;
            case CHANGE_PASSWORD: password(card); break;
            default: throw new IllegalArgumentException(screen.name());
        }
    }

    // ---------------------------------------------------------------- Iniciar sesión (diseño Figma)
    // Las cuentas las crea la organización: no hay registro público desde la app.

    private void loginScreen(LinearLayout body) {
        LinearLayout brand = ui.row();
        brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, ui.dp(10), 0, ui.dp(26));
        ImageView mark = ui.image(R.drawable.aeriq_mark, 30, 30);
        brand.addView(mark, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)));
        TextView name = ui.text("AERIQ", 20, true, INK);
        name.setPadding(ui.dp(8), 0, 0, 0);
        brand.addView(name);
        body.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        TextView title = ui.text("Iniciar sesión", 32, true, INK);
        title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView lead = ui.text("Accede para ver tu sensor, tus mediciones y tus datos personales.", 14, false, MUTED);
        lead.setGravity(Gravity.CENTER);
        lead.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(18));
        body.addView(lead, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = ui.column();
        card.setBackground(ui.shape(WHITE, 24, LINE));
        card.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(22));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = ui.dp(18);
        body.addView(card, cardParams);

        EditText email = input(card, "Correo electrónico", "nombre@ejemplo.com", EMAIL, auth.pendingEmail, false);
        EditText password = input(card, "Contraseña", "Tu contraseña", PASSWORD, "", true);
        TextView forgot = ui.text("", 13, true, GREEN);
        android.text.SpannableString forgotText = new android.text.SpannableString("¿Has olvidado tu contraseña?");
        forgotText.setSpan(new android.text.style.UnderlineSpan(), 0, forgotText.length(), 0);
        forgot.setText(forgotText);
        forgot.setGravity(Gravity.END);
        forgot.setPadding(0, ui.dp(4), 0, ui.dp(14));
        forgot.setOnClickListener(view -> activity.go(Screen.FORGOT));
        card.addView(forgot, new LinearLayout.LayoutParams(-1, -2));

        TextView enter = ui.text("Entrar", 16, true, WHITE);
        enter.setGravity(Gravity.CENTER);
        enter.setBackground(ui.shape(GREEN, 18, 0));
        enter.setOnClickListener(view -> {
            if (auth.busy) return;
            if (value(email).trim().isEmpty() || password.length() == 0) {
                activity.message("Escribe tu correo y tu contraseña.");
                return;
            }
            enter.setEnabled(false);
            enter.setAlpha(.6f);
            activity.submit("login", result -> {
                enter.setEnabled(true);
                enter.setAlpha(1f);
                activity.message(result.message);
                if (result.needsVerification) activity.go(Screen.VERIFY);
                else if (result.success) activity.go(Screen.MAP);
            }, "email", value(email), "password", value(password));
        });
        card.addView(enter, new LinearLayout.LayoutParams(-1, ui.dp(56)));

        separator(card, "o continúa con");
        LinearLayout social = ui.row();
        social.addView(socialButton("G", 0, "Google"), new LinearLayout.LayoutParams(0, ui.dp(50), 1));
        social.addView(new android.view.View(activity), new LinearLayout.LayoutParams(ui.dp(10), 1));
        social.addView(socialButton(null, R.drawable.ic_apple, "Apple"), new LinearLayout.LayoutParams(0, ui.dp(50), 1));
        card.addView(social, new LinearLayout.LayoutParams(-1, -2));

        // Entrar con huella: debajo de Google y Apple.
        LinearLayout fingerprint = ui.row();
        fingerprint.setGravity(Gravity.CENTER);
        fingerprint.setBackground(ui.shape(DARK, 18, 0));
        ImageView print = ui.image(R.drawable.ic_fingerprint, 24, 24);
        print.setColorFilter(WHITE);
        fingerprint.addView(print, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
        TextView printLabel = ui.text("Entrar con huella", 16, true, WHITE);
        printLabel.setPadding(ui.dp(10), 0, 0, 0);
        fingerprint.addView(printLabel);
        fingerprint.setContentDescription("Entrar con huella");
        fingerprint.setOnClickListener(view -> activity.loginWithFingerprint());
        LinearLayout.LayoutParams printParams = new LinearLayout.LayoutParams(-1, ui.dp(56));
        printParams.topMargin = ui.dp(12);
        card.addView(fingerprint, printParams);

        LinearLayout publicMap = ui.row();
        publicMap.setBackground(ui.shape(0xffe6efec, 22, 0));
        publicMap.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18));
        ImageView mapIcon = ui.image(R.drawable.figma_nav_map, 24, 24);
        mapIcon.setColorFilter(GREEN);
        publicMap.addView(mapIcon, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(14), 0, ui.dp(10), 0);
        texts.addView(ui.text("¿Solo quieres ver el aire de tu ciudad?", 14, true, INK));
        TextView free = ui.text("El mapa público es gratis y no necesita cuenta.", 12, false, MUTED);
        free.setPadding(0, ui.dp(3), 0, 0);
        texts.addView(free);
        publicMap.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView see = ui.text("", 13, true, GREEN);
        android.text.SpannableString seeText = new android.text.SpannableString("Ver mapa");
        seeText.setSpan(new android.text.style.UnderlineSpan(), 0, seeText.length(), 0);
        see.setText(seeText);
        publicMap.addView(see);
        publicMap.setOnClickListener(view -> activity.go(Screen.MAP));
        body.addView(publicMap, new LinearLayout.LayoutParams(-1, -2));
    }

    private EditText input(LinearLayout parent, String label, String hint, int type, String value, boolean password) {
        TextView caption = ui.text(label, 13, true, INK);
        caption.setPadding(0, 0, 0, ui.dp(8));
        parent.addView(caption);
        LinearLayout box = ui.row();
        box.setBackground(ui.shape(0xfff1f4f3, 14, LINE));
        EditText input = new EditText(activity);
        input.setId(android.view.View.generateViewId());
        input.setSingleLine(true);
        input.setInputType(type);
        input.setTypeface(android.graphics.Typeface.DEFAULT);
        input.setTextSize(15);
        input.setTextColor(INK);
        input.setHintTextColor(0xff8a9792);
        input.setHint(hint);
        input.setText(value);
        input.setBackground(null);
        input.setPadding(ui.dp(16), 0, ui.dp(8), 0);
        caption.setLabelFor(input.getId());
        box.addView(input, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        if (password) {
            input.setSaveEnabled(false);
            TextView toggle = ui.text("Mostrar", 13, true, GREEN);
            toggle.setPadding(ui.dp(8), 0, ui.dp(16), 0);
            toggle.setGravity(Gravity.CENTER_VERTICAL);
            toggle.setOnClickListener(view -> {
                boolean hidden = input.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod;
                input.setTransformationMethod(hidden ? null : android.text.method.PasswordTransformationMethod.getInstance());
                input.setSelection(input.length());
                toggle.setText(hidden ? "Ocultar" : "Mostrar");
            });
            box.addView(toggle, new LinearLayout.LayoutParams(-2, ui.dp(54)));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(16);
        parent.addView(box, params);
        return input;
    }

    private void separator(LinearLayout parent, String label) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(18), 0, ui.dp(14));
        android.view.View left = new android.view.View(activity), right = new android.view.View(activity);
        left.setBackgroundColor(LINE);
        right.setBackgroundColor(LINE);
        row.addView(left, new LinearLayout.LayoutParams(0, ui.dp(1), 1));
        TextView text = ui.text(label, 12, false, MUTED);
        text.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        row.addView(text);
        row.addView(right, new LinearLayout.LayoutParams(0, ui.dp(1), 1));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private LinearLayout socialButton(String letter, int icon, String label) {
        LinearLayout button = ui.row();
        button.setGravity(Gravity.CENTER);
        button.setBackground(ui.shape(WHITE, 16, 0xffd5dcd9));
        if (letter != null) {
            TextView mark = ui.text(letter, 16, true, MUTED);
            button.addView(mark);
        } else {
            ImageView mark = ui.image(icon, 20, 20);
            mark.setColorFilter(INK);
            button.addView(mark, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        }
        TextView text = ui.text(label, 14, true, INK);
        text.setPadding(ui.dp(10), 0, 0, 0);
        button.addView(text);
        button.setContentDescription("Continuar con " + label);
        button.setOnClickListener(view ->
            activity.message("El acceso con " + label + " aún no está disponible. Entra con tu correo."));
        return button;
    }

    private void recovery(Screen screen, LinearLayout body, LinearLayout card) {
        EditText email = ui.field(card, screen == Screen.VERIFY ? "Correo de la cuenta" : "Correo electrónico",
            auth.pendingEmail, EMAIL);
        email.setHint("nombre@ejemplo.com");
        if (screen == Screen.FORGOT) {
            submit(card, "Enviar código", "forgot", Screen.RESET, () -> validEmail(email),
                () -> new Object[]{"email", value(email)});
        } else {
            EditText code = ui.field(card, screen == Screen.VERIFY ? "Código de 6 cifras" : "Código", "", InputType.TYPE_CLASS_NUMBER);
            code.setHint("000000");
            code.setSaveEnabled(false);
            code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
            if (screen == Screen.VERIFY) {
                submit(card, "Confirmar y entrar", "verify", Screen.MAP,
                    () -> validEmail(email) && code.length() == 6,
                    () -> new Object[]{"email", value(email), "code", value(code)});
                ui.link(body, "Reenviar código", () -> {
                    if (!validEmail(email)) activity.message("Introduce un correo válido.");
                    else activity.submit("resend", result -> activity.message(result.message), "email", value(email));
                });
                ui.label(body, "Los códigos son de un solo uso y tienen un límite de intentos.", 11, false, MUTED);
            } else {
                EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
                EditText confirm = ui.field(card, "Repetir contraseña", "", PASSWORD);
                password.setHint("Mínimo 12 caracteres");
                confirm.setHint("Repite la contraseña");
                submit(card, "Cambiar contraseña", "reset-password", Screen.MAP,
                    () -> validEmail(email) && code.length() == 6 && matching(password, confirm),
                    () -> new Object[]{"email", value(email), "code", value(code), "password", value(password)});
            }
        }
    }

    private void profile(LinearLayout body, LinearLayout card) {
        EditText name = ui.field(card, "Nombre", auth.user.name, TEXT);
        EditText last = ui.field(card, "Apellidos", auth.user.last, TEXT);
        EditText phone = ui.field(card, "Teléfono (opcional)", auth.user.phone, InputType.TYPE_CLASS_PHONE);
        submit(card, "Guardar cambios", "profile", Screen.ACCOUNT,
            () -> !value(name).trim().isEmpty() && !value(last).trim().isEmpty(),
            () -> new Object[]{"name", value(name).trim(), "last", value(last).trim(), "phone", value(phone).trim()});
        ui.label(body, "Correo de la cuenta: " + auth.user.email, 11, false, MUTED);
    }

    private void password(LinearLayout card) {
        EditText current = ui.field(card, "Contraseña actual", "", PASSWORD);
        EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
        EditText confirm = ui.field(card, "Confirmar nueva contraseña", "", PASSWORD);
        if (auth.user.mustChangePassword) current.setHint("Contraseña temporal");
        password.setHint("Mínimo 12 caracteres");
        confirm.setHint("Repetir");
        submit(card, "Actualizar contraseña", "change-password", Screen.MAP,
            () -> current.length() > 0 && matching(password, confirm),
            () -> new Object[]{"current", value(current), "password", value(password)});
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
