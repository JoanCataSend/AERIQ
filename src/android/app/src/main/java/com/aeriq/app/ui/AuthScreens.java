package com.aeriq.app.ui;

import android.app.AlertDialog;
import android.os.CancellationSignal;
import android.util.Log;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.aeriq.app.MainActivity;
import com.aeriq.app.MainActivity.Screen;
import com.aeriq.app.R;
import com.aeriq.app.data.AeriqAuthManager;
import com.aeriq.app.data.PasswordRules;

import static com.aeriq.app.ui.Views.*;

public final class AuthScreens {
    private static final String AUTH_TAG = "AERIQ_AUTH";
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
        body.addView(brand, new LinearLayout.LayoutParams(-1, ui.dp(screen == Screen.LOGIN ? 60 : 66)));
        String title, description;
        switch (screen) {
            case FORGOT: title = "Recuperar contraseña"; description = "Te enviaremos un correo con un código para establecer una nueva contraseña."; break;
            case RESET: title = "Nueva contraseña"; description = "Introduce el código del correo (caduca en 10 minutos) y tu nueva contraseña."; break;
            case EDIT_PROFILE: title = "Datos personales"; description = "Tu perfil se guarda en la misma base de datos que la web."; break;
            case CHANGE_PASSWORD:
                title = (auth.user != null && auth.user.mustChangePassword) ? "Sustituye tu contraseña temporal" : "Cambiar contraseña";
                description = "Elige una contraseña diferente: al menos 8 caracteres, con mayúscula, minúscula, número y símbolo.";
                break;
            default: title = "Iniciar sesión"; description = "Entra con la cuenta que te dio tu organización para ver tu sensor, tus mediciones y tus datos personales.";
        }
        TextView heading = ui.label(body, title, 30, true, INK);
        heading.setGravity(Gravity.CENTER);
        if (screen == Screen.LOGIN) {
            LinearLayout.LayoutParams headingParams = (LinearLayout.LayoutParams) heading.getLayoutParams();
            headingParams.bottomMargin = ui.dp(8);
        }
        TextView intro = ui.label(body, description, 14, false, MUTED);
        intro.setGravity(Gravity.CENTER);
        if (screen == Screen.LOGIN) {
            intro.setLineSpacing(ui.dp(3), 1f);
            LinearLayout.LayoutParams introParams = (LinearLayout.LayoutParams) intro.getLayoutParams();
            introParams.bottomMargin = ui.dp(20);
        }
        if (mandatory) ui.link(body, "Cerrar sesión", activity::logout);
        LinearLayout card = ui.card(body);
        if (screen == Screen.LOGIN) {
            card.setBackground(ui.shape(WHITE, 20, 0));
            card.setElevation(ui.dp(5));
            card.setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(20));
            LinearLayout.LayoutParams cardParams = (LinearLayout.LayoutParams) card.getLayoutParams();
            cardParams.bottomMargin = ui.dp(16);
        }
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
        // Campos
        EditText email = ui.field(card, "Correo electrónico", auth.pendingEmail, EMAIL);
        email.setHint("nombre@ejemplo.com");
        LinearLayout emailRow = (LinearLayout) email.getParent();
        LinearLayout.LayoutParams emailParams = (LinearLayout.LayoutParams) emailRow.getLayoutParams();
        emailParams.bottomMargin = ui.dp(16);
        EditText password = ui.field(card, "Contraseña", "", PASSWORD);
        password.setHint("Tu contraseña");
        LinearLayout passwordRow = (LinearLayout) password.getParent();
        ((LinearLayout.LayoutParams) passwordRow.getLayoutParams()).bottomMargin = ui.dp(4);

        // Olvidé contraseña: alineado a la derecha, con área táctil de 48 dp.
        TextView forgot = ui.text("¿Has olvidado tu contraseña?", 13, true, GREEN);
        forgot.setPaintFlags(forgot.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        forgot.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        forgot.setMinHeight(ui.dp(48));
        forgot.setFocusable(true);
        forgot.setOnClickListener(v -> activity.go(Screen.FORGOT));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.bottomMargin = ui.dp(12);
        card.addView(forgot, fp);

        // Botón Entrar (verde)
        Button entrar = submit(card, "Entrar", "login", Screen.MAP,
            () -> !value(email).isEmpty() && password.length() > 0 ? null : "Escribe tu correo y tu contraseña.",
            () -> new Object[]{"email", value(email), "password", value(password)});
        entrar.setTextSize(16);
        ui.pill(entrar, GREEN);

        // Divisor social
        ui.dividerWithText(card, "o continúa con");

        // Google. Sin botón de Apple: en Android no hay acceso con Apple y un botón que solo avisa confunde.
        LinearLayout googleBtn = ui.socialButton("Continuar con Google", R.drawable.ic_google_logo, WHITE, INK, 26, () -> socialLogin("google"));
        googleBtn.setContentDescription("Continuar con Google");
        card.addView(googleBtn, new LinearLayout.LayoutParams(-1, ui.dp(52)));

        LinearLayout.LayoutParams biometricParams = new LinearLayout.LayoutParams(-1, -2);
        biometricParams.topMargin = ui.dp(10);
        LinearLayout biometricButton = ui.socialButton("Entrar con huella", R.drawable.ic_fingerprint, DARK, WHITE, 26,
            activity::loginWithFingerprint);
        biometricButton.setContentDescription("Entrar con huella");
        biometricParams.height = ui.dp(52);
        card.addView(biometricButton, biometricParams);

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

        // No hay registro libre: las cuentas vienen con los kits AERIQ (ver modelo de negocio en doc/android.md).
        LinearLayout kit = ui.banner(body, Views.BANNER_BG);
        LinearLayout kitRow = ui.row();
        kitRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView kitIcon = ui.image(R.drawable.ic_users, 24, 24);
        kitIcon.setColorFilter(GREEN);
        LinearLayout.LayoutParams kitIconParams = new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24));
        kitIconParams.rightMargin = ui.dp(12);
        kitRow.addView(kitIcon, kitIconParams);
        LinearLayout kitText = ui.column();
        kitText.addView(ui.text("¿Aún no tienes cuenta?", 13, true, INK));
        kitText.addView(ui.text("No hay registro libre. Las cuentas vienen con los kits AERIQ para ayuntamientos "
            + "y empresas: te la crea el administrador de tu campaña.", 12, false, MUTED));
        kitRow.addView(kitText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView kitLink = ui.text("Solicitar un kit", 13, true, GREEN);
        kitLink.setPaintFlags(kitLink.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        kitLink.setPadding(ui.dp(8), ui.dp(4), 0, ui.dp(4));
        kitLink.setFocusable(true);
        kitLink.setContentDescription("Solicitar un kit AERIQ en la web");
        kitLink.setOnClickListener(v -> activity.open(com.aeriq.app.data.ApiRoutes.BASE + "solicitar.html"));
        kitRow.addView(kitLink);
        kit.addView(kitRow);
    }

    // ── BIOMETRÍA ──────────────────────────────────────────────────────────
    // ── SOCIAL ─────────────────────────────────────────────────────────────
    private void socialLogin(String provider) {
        boolean isApple = "apple".equalsIgnoreCase(provider);
        if (isApple) {
            activity.message("El acceso con Apple todavía no está disponible.");
            return;
        }
        signInWithGoogle();
    }

    private void signInWithGoogle() {
        try {
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build();
            CredentialManager manager = CredentialManager.create(activity);
            manager.getCredentialAsync(activity, request, new CancellationSignal(),
                androidx.core.content.ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<androidx.credentials.GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(androidx.credentials.GetCredentialResponse response) {
                        handleGoogleCredential(response.getCredential());
                    }
                    @Override public void onError(GetCredentialException error) {
                        if (!(error instanceof GetCredentialCancellationException)) {
                            showGoogleError("No se pudo elegir la cuenta de Google", error);
                        }
                    }
                });
        } catch (Exception error) {
            showGoogleError("No se pudo iniciar Google", error);
        }
    }

    private void handleGoogleCredential(Credential credential) {
        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            showGoogleError("Google no devolvió una credencial válida. Tipo recibido: " + credential.getType(), null);
            return;
        }
        try {
            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
            AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(google.getIdToken(), null);
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnCompleteListener(activity, task -> {
                    if (!task.isSuccessful() || FirebaseAuth.getInstance().getCurrentUser() == null) {
                        showGoogleError("Firebase no pudo validar la cuenta de Google. Comprueba que Google esté habilitado.", task.getException());
                        return;
                    }
                    FirebaseAuth.getInstance().getCurrentUser().getIdToken(true)
                        .addOnCompleteListener(activity, tokenTask -> {
                            if (!tokenTask.isSuccessful() || tokenTask.getResult() == null
                                    || tokenTask.getResult().getToken() == null) {
                                showGoogleError("No se pudo obtener el token de acceso de Firebase", tokenTask.getException());
                                return;
                            }
                            activity.submit("google", result -> {
                                if (result.success) {
                                    activity.message(result.message);
                                    activity.go(Screen.MAP);
                                } else {
                                    FirebaseAuth.getInstance().signOut();
                                    showGoogleError(result.message, null);
                                }
                            }, "idToken", tokenTask.getResult().getToken());
                        });
                });
        } catch (Exception error) {
            showGoogleError("No se pudo procesar la cuenta de Google", error);
        }
    }

    private void showGoogleError(String message, Throwable error) {
        String detail = error == null ? "" : "\n\n" + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : ": " + error.getMessage());
        if (error == null) Log.e(AUTH_TAG, message);
        else Log.e(AUTH_TAG, message, error);
        if (!activity.isFinishing()) {
            new AlertDialog.Builder(activity)
                .setTitle("Error al entrar con Google")
                .setMessage(message + detail)
                .setPositiveButton("Cerrar", null)
                .show();
        }
    }


    // ── RECOVERY ───────────────────────────────────────────────────────────
    private void recovery(Screen screen, LinearLayout body, LinearLayout card) {
        EditText email = ui.field(card, "Correo electrónico", auth.pendingEmail, EMAIL);
        email.setHint("nombre@ejemplo.com");
        if (screen == Screen.FORGOT) {
            submit(card, "Enviar código", "forgot", Screen.RESET, () -> validEmail(email) ? null : "Escribe un correo electrónico válido.",
                () -> new Object[]{"email", value(email)});
        } else {
            EditText code = ui.field(card, "Código", "", InputType.TYPE_CLASS_NUMBER);
            code.setHint("000000");
            code.setSaveEnabled(false);
            code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
            EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
            EditText confirm  = ui.field(card, "Repetir contraseña", "", PASSWORD);
            password.setHint("Mín. 8, con mayúscula, número y símbolo");
            confirm.setHint("Repite la contraseña");
            rules(card, password, confirm);
            submit(card, "Cambiar contraseña", "reset-password", Screen.MAP,
                () -> !validEmail(email) ? "Escribe un correo electrónico válido."
                    : code.length() != 6 ? "El código del correo tiene seis cifras." : newPassword(password, confirm),
                () -> new Object[]{"email", value(email), "code", value(code), "password", value(password)});
        }
    }

    // ── PROFILE / PASSWORD ─────────────────────────────────────────────────
    private void profile(LinearLayout body, LinearLayout card) {
        EditText name  = ui.field(card, "Nombre", auth.user.name, TEXT);
        EditText last  = ui.field(card, "Apellidos", auth.user.last, TEXT);
        EditText phone = ui.field(card, "Teléfono (opcional)", auth.user.phone, InputType.TYPE_CLASS_PHONE);
        submit(card, "Guardar cambios", "profile", Screen.ACCOUNT,
            () -> !value(name).trim().isEmpty() && !value(last).trim().isEmpty() ? null : "Escribe tu nombre y tus apellidos.",
            () -> new Object[]{"name", value(name).trim(), "last", value(last).trim(), "phone", value(phone).trim()});
        ui.label(body, "Correo de la cuenta: " + auth.user.email, 11, false, MUTED);
    }

    private void password(LinearLayout card) {
        EditText current  = ui.field(card, "Contraseña actual", "", PASSWORD);
        EditText password = ui.field(card, "Nueva contraseña", "", PASSWORD);
        EditText confirm  = ui.field(card, "Confirmar nueva contraseña", "", PASSWORD);
        if (auth.user.mustChangePassword) current.setHint("Contraseña temporal");
        password.setHint("Mín. 8, con mayúscula, número y símbolo");
        confirm.setHint("Repetir");
        rules(card, password, confirm);
        submit(card, "Actualizar contraseña", "change-password", Screen.MAP,
            () -> current.length() == 0 ? "Escribe tu contraseña actual." : newPassword(password, confirm),
            () -> new Object[]{"current", value(current), "password", value(password)});
    }

    // ── HELPERS ────────────────────────────────────────────────────────────
    /** problem: devuelve qué falta para poder enviar (null o vacío si todo está bien). */
    private Button submit(LinearLayout card, String caption, String action, Screen next,
                        java.util.function.Supplier<String> problem, java.util.function.Supplier<Object[]> fields) {
        Button button = ui.action(caption, GREEN, WHITE, () -> {});
        ui.add(card, button);
        button.setOnClickListener(view -> {
            if (auth.busy) return;
            String wrong = problem.get();
            if (wrong != null && !wrong.isEmpty()) {
                activity.message(wrong);
                return;
            }
            button.setEnabled(false);
            activity.submit(action, result -> {
                button.setEnabled(true);
                activity.message(result.message);
                if (result.success && next != null) activity.go(next);
            }, fields.get());
        });
        return button;
    }

    private static String value(EditText input) { return input.getText().toString(); }
    private static boolean validEmail(EditText input) { return value(input).contains("@"); }
    /** Qué le falta a la contraseña nueva (los requisitos los vuelve a comprobar el servidor). */
    private static String newPassword(EditText password, EditText confirm) {
        String problem = PasswordRules.problem(value(password));
        if (!problem.isEmpty()) return problem;
        return value(password).equals(value(confirm)) ? null : "Las contraseñas nuevas no coinciden.";
    }

    /** Lista de requisitos bajo la contraseña nueva, marcada en verde según se cumplen mientras se escribe. */
    private void rules(LinearLayout card, EditText password, EditText confirm) {
        LinearLayout box = ui.column();
        box.setBackground(ui.shape(0xfff1f4f3, 12, 0));
        box.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        box.addView(ui.text("Tu contraseña necesita:", 13, true, INK));
        TextView[] items = new TextView[PasswordRules.ALL.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = ui.text("", 13, false, MUTED);
            items[i].setPadding(0, ui.dp(3), 0, 0);
            box.addView(items[i]);
        }
        Runnable update = () -> {
            java.util.List<String> missing = PasswordRules.missing(value(password));
            for (int i = 0; i < items.length; i++) {
                boolean ok = !missing.contains(PasswordRules.ALL.get(i));
                items[i].setText((ok ? "● " : "○ ") + PasswordRules.ALL.get(i));
                items[i].setTextColor(ok ? GREEN : MUTED);
            }
            box.setContentDescription(missing.isEmpty() ? "La contraseña cumple todos los requisitos"
                : "A la contraseña le falta: " + String.join(", ", missing));
        };
        password.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable e) { update.run(); }
        });
        update.run();
        // Va justo después del campo de la contraseña nueva (antes de «Repetir»).
        android.view.View confirmRow = (android.view.View) confirm.getParent();
        int at = card.indexOfChild(confirmRow) - 1; // la etiqueta de «Repetir» va justo antes de su fila
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(16);
        params.topMargin = -ui.dp(6);
        card.addView(box, Math.max(0, at), params);
    }
}
