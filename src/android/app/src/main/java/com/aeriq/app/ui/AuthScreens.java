package com.aeriq.app.ui;

import android.app.AlertDialog;
import android.os.Build;
import android.os.CancellationSignal;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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
        ImageView logo = ui.image(R.drawable.aeriq_logo, 99, 34);
        FrameLayout brand = new FrameLayout(activity);
        brand.addView(logo, new FrameLayout.LayoutParams(ui.dp(99), ui.dp(34), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        boolean mandatory = screen == Screen.CHANGE_PASSWORD && auth.user != null && auth.user.mustChangePassword;
        if (screen != Screen.LOGIN && !mandatory) {
            ImageView back = ui.image(R.drawable.ic_back, 48, 48);
            back.setPadding(0, ui.dp(3), ui.dp(25), ui.dp(23));
            back.setContentDescription("Atrás");
            back.setOnClickListener(view -> activity.onBackPressed());
            brand.addView(back, new FrameLayout.LayoutParams(ui.dp(48), ui.dp(48), Gravity.TOP | Gravity.START));
        }
        body.addView(brand, new LinearLayout.LayoutParams(-1, ui.dp(66)));
        String title, description;
        switch (screen) {
            case FORGOT: title = "Recuperar contraseña"; description = "Te enviaremos un código para establecer una nueva contraseña."; break;
            case RESET: title = "Nueva contraseña"; description = "Introduce el código recibido y tu nueva contraseña."; break;
            case EDIT_PROFILE: title = "Datos personales"; description = "Tu perfil se guarda en la misma base de datos que la web."; break;
            case CHANGE_PASSWORD:
                title = (auth.user != null && auth.user.mustChangePassword) ? "Sustituye tu contraseña temporal" : "Cambiar contraseña";
                description = "Elige una contraseña diferente de al menos 12 caracteres.";
                break;
            default: title = "Iniciar sesión"; description = "Accede para ver tu sensor, tus mediciones y tus datos personales.";
        }
        ui.label(body, title, 30, true, INK).setGravity(Gravity.CENTER);
        ui.label(body, description, 14, false, MUTED).setGravity(Gravity.CENTER);
        if (mandatory) ui.link(body, "Cerrar sesión", activity::logout);
        LinearLayout card = ui.card(body);
        switch (screen) {
            case LOGIN: login(body, card); break;
            case FORGOT: case RESET: recovery(screen, body, card); break;
            case EDIT_PROFILE: profile(body, card); break;
            case CHANGE_PASSWORD: password(card); break;
            default: throw new IllegalArgumentException(screen.name());
        }
    }

    // ── LOGIN ──────────────────────────────────────────────────────────────
    private void login(LinearLayout body, LinearLayout card) {
        // Botón principal: Entrar con huella
        ui.buttonWithIcon(card, "Entrar con huella", R.drawable.ic_fingerprint, DARK, WHITE, this::biometricLogin);

        // Divisor
        ui.dividerWithText(card, "o con tu correo");

        // Campos
        EditText email = ui.field(card, "Correo electrónico", auth.pendingEmail, EMAIL);
        email.setHint("nombre@ejemplo.com");
        EditText password = ui.field(card, "Contraseña", "", PASSWORD);
        password.setHint("Tu contraseña");

        // Olvidé contraseña (alineado derecha)
        TextView forgot = ui.text("¿Has olvidado tu contraseña?", 13, true, GREEN);
        forgot.setPadding(0, ui.dp(4), 0, ui.dp(8));
        forgot.setGravity(Gravity.END);
        forgot.setFocusable(true);
        forgot.setOnClickListener(v -> activity.go(Screen.FORGOT));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.bottomMargin = ui.dp(12);
        card.addView(forgot, fp);

        // Botón Entrar (verde)
        submit(card, "Entrar", "login", Screen.MAP,
            () -> !value(email).isEmpty() && password.length() > 0,
            () -> new Object[]{"email", value(email), "password", value(password)});

        // Divisor social
        ui.dividerWithText(card, "o continúa con");

        // Fila Google / Apple
        LinearLayout socialRow = ui.row();
        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, -2);
        srp.bottomMargin = ui.dp(8);
        socialRow.setLayoutParams(srp);

        LinearLayout googleBtn = ui.socialButton("Google", R.drawable.ic_google_logo, () -> socialLogin("google"));
        LinearLayout appleBtn  = ui.socialButton("Apple",  R.drawable.ic_apple_logo,  () -> socialLogin("apple"));

        LinearLayout.LayoutParams lh = new LinearLayout.LayoutParams(0, -2, 1);
        lh.rightMargin = ui.dp(6);
        socialRow.addView(googleBtn, lh);

        LinearLayout.LayoutParams rh = new LinearLayout.LayoutParams(0, -2, 1);
        rh.leftMargin = ui.dp(6);
        socialRow.addView(appleBtn, rh);

        card.addView(socialRow);

        // Banner "Solo quieres ver el aire de tu ciudad"
        LinearLayout banner = ui.banner(body, Views.BANNER_BG);
        LinearLayout mapRow = ui.row();
        mapRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView mapIcon = ui.image(R.drawable.ic_map_folded, 24, 24);
        LinearLayout.LayoutParams icP = new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24));
        icP.rightMargin = ui.dp(12);
        mapRow.addView(mapIcon, icP);

        LinearLayout textCol = ui.column();
        textCol.addView(ui.text("¿Solo quieres ver el aire de tu ciudad?", 13, true, INK));
        textCol.addView(ui.text("El mapa público es gratis y no necesita cuenta.", 12, false, MUTED));
        mapRow.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView mapLink = ui.text("Ver mapa", 13, true, GREEN);
        mapLink.setPaintFlags(mapLink.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        mapLink.setPadding(ui.dp(8), ui.dp(4), 0, ui.dp(4));
        mapLink.setFocusable(true);
        mapLink.setOnClickListener(v -> activity.go(Screen.MAP));
        mapRow.addView(mapLink);

        banner.addView(mapRow);
    }

    // ── BIOMETRÍA ──────────────────────────────────────────────────────────
    private void biometricLogin() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                CancellationSignal signal = new CancellationSignal();
                android.hardware.biometrics.BiometricPrompt prompt =
                    new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                        .setTitle("Iniciar sesión con huella")
                        .setSubtitle("AERIQ")
                        .setDescription("Coloca tu huella en el lector para acceder")
                        .setNegativeButton("Cancelar", activity.getMainExecutor(), (d, w) -> {})
                        .build();
                prompt.authenticate(signal, activity.getMainExecutor(),
                    new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r) { performBiometricAuth(); }
                        @Override public void onAuthenticationError(int code, CharSequence msg) {
                            if (code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
                             || code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE
                             || code == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT)
                                showBiometricFallback();
                            else if (code != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED
                                  && code != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED)
                                activity.message("Biometría: " + msg);
                        }
                        @Override public void onAuthenticationFailed() { activity.message("Huella no reconocida. Inténtalo de nuevo."); }
                    });
                return;
            } catch (Exception ignored) {}
        }
        showBiometricFallback();
    }

    private void performBiometricAuth() {
        activity.submit("biometric", result -> {
            activity.message(result.success ? "Acceso con huella verificado." : result.message);
            if (result.success) activity.go(Screen.MAP);
        }, "email", auth.getLastEmail());
    }

    private void showBiometricFallback() {
        new AlertDialog.Builder(activity)
            .setTitle("Acceso con huella")
            .setMessage("No se detectó un sensor biométrico configurado. ¿Acceder con verificación de demostración?")
            .setPositiveButton("Acceder", (d, w) -> performBiometricAuth())
            .setNegativeButton("Cancelar", null)
            .show();
    }

    // ── SOCIAL ─────────────────────────────────────────────────────────────
    private void socialLogin(String provider) {
        boolean isApple = "apple".equalsIgnoreCase(provider);
        // No se inventan correos ni cuentas: el acceso social exige Firebase nativo.
        activity.message("El acceso con " + (isApple ? "Apple" : "Google")
            + " todavía no está disponible en Android. Usa tu correo y contraseña o entra desde la web.");
    }


    // ── RECOVERY ───────────────────────────────────────────────────────────
    private void recovery(Screen screen, LinearLayout body, LinearLayout card) {
        EditText email = ui.field(card, "Correo electrónico", auth.pendingEmail, EMAIL);
        email.setHint("nombre@ejemplo.com");
        if (screen == Screen.FORGOT) {
            submit(card, "Enviar código", "forgot", Screen.RESET, () -> validEmail(email),
                () -> new Object[]{"email", value(email)});
        } else {
            EditText code = ui.field(card, "Código", "", InputType.TYPE_CLASS_NUMBER);
            code.setHint("000000");
            code.setSaveEnabled(false);
            code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
            EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
            EditText confirm  = ui.field(card, "Repetir contraseña", "", PASSWORD);
            password.setHint("Mínimo 12 caracteres");
            confirm.setHint("Repite la contraseña");
            submit(card, "Cambiar contraseña", "reset-password", Screen.MAP,
                () -> validEmail(email) && code.length() == 6 && matching(password, confirm),
                () -> new Object[]{"email", value(email), "code", value(code), "password", value(password)});
        }
    }

    // ── PROFILE / PASSWORD ─────────────────────────────────────────────────
    private void profile(LinearLayout body, LinearLayout card) {
        EditText name  = ui.field(card, "Nombre", auth.user.name, TEXT);
        EditText last  = ui.field(card, "Apellidos", auth.user.last, TEXT);
        EditText phone = ui.field(card, "Teléfono (opcional)", auth.user.phone, InputType.TYPE_CLASS_PHONE);
        submit(card, "Guardar cambios", "profile", Screen.ACCOUNT,
            () -> !value(name).trim().isEmpty() && !value(last).trim().isEmpty(),
            () -> new Object[]{"name", value(name).trim(), "last", value(last).trim(), "phone", value(phone).trim()});
        ui.label(body, "Correo de la cuenta: " + auth.user.email, 11, false, MUTED);
    }

    private void password(LinearLayout card) {
        EditText current  = ui.field(card, "Contraseña actual", "", PASSWORD);
        EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
        EditText confirm  = ui.field(card, "Confirmar nueva contraseña", "", PASSWORD);
        if (auth.user.mustChangePassword) current.setHint("Contraseña temporal");
        password.setHint("Mínimo 12 caracteres");
        confirm.setHint("Repetir");
        submit(card, "Actualizar contraseña", "change-password", Screen.MAP,
            () -> current.length() > 0 && matching(password, confirm),
            () -> new Object[]{"current", value(current), "password", value(password)});
    }

    // ── HELPERS ────────────────────────────────────────────────────────────
    private void submit(LinearLayout card, String caption, String action, Screen next,
                        java.util.function.BooleanSupplier valid, java.util.function.Supplier<Object[]> fields) {
        Button button = ui.action(caption, GREEN, WHITE, () -> {});
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
                if (result.success && next != null) activity.go(next);
            }, fields.get());
        });
    }

    private static String value(EditText input) { return input.getText().toString(); }
    private static boolean validEmail(EditText input) { return value(input).contains("@"); }
    private static boolean matching(EditText first, EditText second) {
        return first.length() >= 12 && value(first).equals(value(second));
    }
}
