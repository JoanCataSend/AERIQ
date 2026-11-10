package com.aeriq.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.*;




import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.aeriq.app.ble.*;
import com.aeriq.app.data.*;
import com.aeriq.app.ui.*;
import javax.crypto.Cipher;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class MainActivity extends androidx.fragment.app.FragmentActivity {
    /**
     * Escala global de la interfaz. El diseño de Figma mide 390 dp de ancho; en Android, con la
     * densidad por defecto, todo se veía ~12 % más grande de lo deseado. Se reduce la densidad
     * de la actividad (dp y sp a la vez), así toda la app encoge igual sin cambiar su estructura.
     * Para ajustarlo basta con cambiar este número (1.0 = tamaño original).
     */
    public static final float UI_SCALE = 0.88f;

    @Override protected void attachBaseContext(android.content.Context base) {
        android.content.res.Configuration config = new android.content.res.Configuration(base.getResources().getConfiguration());
        int density = config.densityDpi;
        if (density > 0) {
            config.densityDpi = Math.round(density * UI_SCALE);
            if (config.screenWidthDp > 0) config.screenWidthDp = Math.round(config.screenWidthDp / UI_SCALE);
            if (config.screenHeightDp > 0) config.screenHeightDp = Math.round(config.screenHeightDp / UI_SCALE);
            if (config.smallestScreenWidthDp > 0) config.smallestScreenWidthDp = Math.round(config.smallestScreenWidthDp / UI_SCALE);
            base = base.createConfigurationContext(config);
        }
        super.attachBaseContext(base);
    }

    public enum Screen {
        LOGIN, FORGOT, RESET, MAP, LEARN, SENSOR, LINK, HISTORY,
        CONFIG, COMPARE, SERVICES, ACCOUNT, EDIT_PROFILE, CHANGE_PASSWORD, PREMIUM
    }

    private static final EnumSet<Screen> PUBLIC = EnumSet.of(
        Screen.LOGIN, Screen.FORGOT, Screen.RESET, Screen.MAP, Screen.LEARN);
    private static final int BLE_PERMISSION = 1, ENABLE_BLUETOOTH = 2, GPS_CAPTURE = 3, GPS_MAP = 4, EXPORT = 5, EXPORT_PDF = 6;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService maps = Executors.newSingleThreadExecutor();
    private Screen current = Screen.LOGIN;
    private Views ui;
    private AeriqAuthManager auth;
    private AppPrefs prefs;
    private BiometricAccessStore biometricAccess;
    private AeriqFieldClient field;
    private AeriqBleController ble;
    private ForegroundCaptureLocation location;
    private PublicMapScreen mapScreen;
    private LinearLayout root, body, header, navigation;
    private FrameLayout stage; // contenedor a pantalla completa (mapa)
    private ScrollView scroll;
    private boolean sharing, started, restoring = true;
    private boolean linkingSensor;
    private long lastSyncAt;
    private List<OrbAssignment> assignments = Collections.emptyList();
    private List<StoredReading> history = Collections.emptyList();
    private String assignmentStatus = "Esperando cuenta autorizada";
    private String historyStatus = "Consulta el historial de tu campaña";
    private String serverStatus = "Envío desactivado";
    private List<StoredReading> exportRows;
    private String exportTitle = "";
    private Runnable updateLive;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (updateLive != null) updateLive.run();
            long now = android.os.SystemClock.elapsedRealtime();
            if (workerAccount() && now - lastSyncAt >= 30000) {
                lastSyncAt = now;
                field.flushPending(status -> serverStatus = status);
                if (current == Screen.SENSOR || current == Screen.HISTORY) refreshHistory();
            }
            main.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        }
        getWindow().setStatusBarColor(Views.BACKGROUND);
        getWindow().setNavigationBarColor(Views.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
        ui = new Views(this);
        auth = new AeriqAuthManager(this);
        prefs = new AppPrefs(this);
        biometricAccess = new BiometricAccessStore(this);
        field = new AeriqFieldClient(this, auth::sessionToken, () -> auth.user == null ? 0L : auth.user.id);
        location = new ForegroundCaptureLocation(this);
        ble = new AeriqBleController(this, this::receive);
        if (state != null) {
            current = Screen.valueOf(state.getString("screen", Screen.LOGIN.name()));
        }
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.shell);
        header = findViewById(R.id.header);
        navigation = findViewById(R.id.navigation);
        scroll = findViewById(R.id.scroll);
        body = findViewById(R.id.content);
        stage = new FrameLayout(this);
        stage.setVisibility(View.GONE);
        root.addView(stage, root.indexOfChild(scroll) + 1, new LinearLayout.LayoutParams(-1, 0, 1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ui.add(body, new ProgressBar(this));
        long biometricUserId = auth.savedUserId();
        boolean legacyBiometric = prefs.consumeLegacyFingerprint(biometricUserId);
        if (legacyBiometric && !biometricAccess.hasEnrollment(biometricUserId) && auth.sessionToken() != null) {
            auth.clearLocalSession();
            restoring = false;
            current = Screen.LOGIN;
            render();
            message("Por seguridad, vuelve a entrar con tu correo y activa de nuevo el acceso biométrico.");
            return;
        }
        if (biometricAccess.hasEnrollment(biometricUserId) && auth.sessionToken() != null) {
            try {
                biometricAccess.beginUnlock(biometricUserId);
            } catch (Exception invalidKey) {
                biometricAccess.disable(biometricUserId);
                auth.clearLocalSession();
                restoring = false;
                current = Screen.LOGIN;
                render();
                message("La biometría registrada cambió o ya no está disponible. Entra con tu correo y actívala de nuevo.");
                return;
            }
            // Con "Acceso con huella" la sesión guardada solo se abre tras verificar la huella.
            restoring = false;
            current = Screen.LOGIN;
            render();
            return;
        }
        auth.restore(result -> {
            restoring = false;
            if (!result.message.isEmpty() && !result.success) message(result.message);
            if (auth.user != null && current.ordinal() <= Screen.RESET.ordinal()) current = Screen.MAP;
            go(current);
            refreshWorker();
        });
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", current.name());
    }

    public void go(Screen destination) {
        if (auth.user != null && auth.user.mustChangePassword) destination = Screen.CHANGE_PASSWORD;
        else if (auth.user == null && !PUBLIC.contains(destination)) {
            destination = Screen.LOGIN;
            message("Inicia sesión para acceder a tus datos y al sensor.");
        }
        if (current != destination || body.getChildCount() == 0 || mapScreen == null) {
            boolean enteringLink = destination == Screen.LINK && current != Screen.LINK;
            current = destination;
            render();
            // Al abrir "Vincular sensor" se empieza a buscar directamente, como en el diseño.
            if (enteringLink && !ble.scanning) toggleScan();
        }
    }

    private void render() {
        if (restoring || isDestroyed()) return;
        if (mapScreen != null) {
            mapScreen.close();
            mapScreen = null;
        }
        updateLive = null;
        header.removeAllViews();
        navigation.removeAllViews();
        body.removeAllViews();
        stage.removeAllViews();
        stage.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        scroll.scrollTo(0, 0);
        boolean form = current.ordinal() <= Screen.RESET.ordinal()
            || current == Screen.EDIT_PROFILE || current == Screen.CHANGE_PASSWORD;
        navigation.setVisibility(form ? View.GONE : View.VISIBLE);
        header.setVisibility(form ? View.GONE : View.VISIBLE);
        if (form) {
            new AuthScreens(this, ui, auth).show(current, body);
            return;
        }
        showNavigation();
        WorkScreens work = new WorkScreens(this, ui);
        DesignScreens design = new DesignScreens(this, ui);
        switch (current) {
            case MAP:
                title("Mapa de ozono", null, R.drawable.ic_bell, () -> message("No tienes avisos nuevos."));
                scroll.setVisibility(View.GONE);
                stage.setVisibility(View.VISIBLE);
                mapScreen = new PublicMapScreen(this, ui, stage, maps);
                if (started) mapScreen.resume();
                break;
            case LEARN:
                title("Aprende", null);
                design.learn(body);
                break;
            case SENSOR:
                title("Mi sensor", null, R.drawable.ic_settings, () -> go(Screen.CONFIG));
                work.sensor(body, ble, assignments, assignmentStatus, serverStatus, sharing, history);
                break;
            case LINK:
                title("Vincular sensor", Screen.SENSOR);
                work.link(body, ble, assignments);
                break;
            case HISTORY:
                title("Historial", Screen.SENSOR, R.drawable.ic_settings, () -> go(Screen.CONFIG));
                design.history(body, history, historyStatus, selected());
                break;
            case CONFIG:
                title("Configurar sensor", Screen.SENSOR);
                design.settings(body, ble, selected(), sharing, prefs);
                break;
            case COMPARE:
                title("Comparar zonas", Screen.MAP);
                design.compare(body, maps);
                break;
            case SERVICES:
                title("Soluciones AERIQ", Screen.ACCOUNT);
                work.services(body);
                break;
            case ACCOUNT:
                title("Cuenta", null);
                design.account(body, auth.user, selected(), sharing, prefs);
                break;
            case PREMIUM:
                title("Premium", Screen.ACCOUNT);
                design.premium(body);
                break;
            default:
                throw new IllegalStateException(current.name());
        }
    }

    public void title(String title, Screen back) { title(title, back, 0, null); }

    public void title(String title, Screen back, int rightIcon, Runnable rightAction) {
        ImageView left = ui.image(back == null ? R.drawable.aeriq_mark : R.drawable.ic_back, 28, 28);
        left.setPadding(0, ui.dp(18), ui.dp(20), ui.dp(18));
        if (back != null) {
            left.setContentDescription("Atrás");
            left.setOnClickListener(view -> go(back));
        }
        header.addView(left, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(66)));
        TextView label = ui.text(title, 17, true, Views.INK);
        label.setGravity(Gravity.CENTER);
        ui.weighted(header, label);
        if (rightIcon != 0) {
            ImageView right = ui.image(rightIcon, 28, 28);
            right.setPadding(ui.dp(10), ui.dp(10), 0, ui.dp(10));
            right.setColorFilter(Views.INK);
            right.setOnClickListener(view -> rightAction.run());
            header.addView(right, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        } else {
            View right = new View(this);
            header.addView(right, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        }
    }

    private void showNavigation() {
        Screen[] routes = {Screen.MAP, Screen.SENSOR, Screen.LEARN, Screen.ACCOUNT};
        String[] labels = {"Mapa", "Mi sensor", "Aprende", "Cuenta"};
        int[] icons = {R.drawable.figma_nav_map, R.drawable.figma_nav_chip, R.drawable.figma_nav_book, R.drawable.figma_nav_user};
        Screen active = current;
        if (EnumSet.of(Screen.LINK, Screen.CONFIG, Screen.HISTORY).contains(current)) active = Screen.SENSOR;
        if (current == Screen.COMPARE) active = Screen.MAP;
        if (current == Screen.SERVICES || current == Screen.PREMIUM) active = Screen.ACCOUNT;
        for (int index = 0; index < routes.length; index++) {
            Screen route = routes[index];
            boolean selected = active == route;
            LinearLayout tab = ui.column();
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, ui.dp(8), 0, ui.dp(8));
            ImageView icon = ui.image(icons[index], 56, 30);
            icon.setPadding(ui.dp(17), ui.dp(4), ui.dp(17), ui.dp(4));
            icon.setColorFilter(selected ? Views.GREEN : Views.MUTED);
            if (selected) icon.setBackground(ui.shape(Views.SOFT, 18, 0));
            tab.addView(icon, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(30)));
            tab.addView(ui.text(labels[index], 12, selected, selected ? Views.GREEN : Views.MUTED));
            tab.setContentDescription(labels[index]);
            tab.setOnClickListener(view -> go(route));
            ui.weighted(navigation, tab);
        }
    }

    public void submit(String action, Consumer<AeriqAuthManager.Result> done, Object... fields) {
        auth.submit(action, result -> {
            boolean biometricNeedsReenrollment = result.success && auth.user != null
                && Arrays.asList("login", "google", "social-login").contains(action)
                && prefs.consumeLegacyFingerprint(auth.user.id)
                && !biometricAccess.hasEnrollment(auth.user.id);
            boolean biometricPasswordChanged = result.success && "change-password".equals(action)
                && auth.user != null && biometricAccess.hasEnrollment(auth.user.id);
            if (biometricPasswordChanged) biometricAccess.disable(auth.user.id);
            if (result.success && auth.user != null && !auth.user.mustChangePassword
                && Arrays.asList("login", "reset-password", "change-password", "social-login", "google").contains(action)) {
                refreshWorker();
            }
            if (auth.user == null && !PUBLIC.contains(current)) {
                clearWorker();
                go(Screen.LOGIN);
                message(result.message);
                return;
            }
            done.accept(result);
            if (biometricNeedsReenrollment) {
                message("Por seguridad, activa de nuevo el acceso biométrico desde Cuenta.");
            }
            if (biometricPasswordChanged) {
                message("La contraseña cambió. Vuelve a activar el acceso con huella desde Cuenta.");
            }
        }, fields);
    }

    public void logout() {
        if (auth.busy) {
            message("Espera a que termine la operación.");
            return;
        }
        auth.logout();
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        clearWorker();
        go(Screen.LOGIN);
        message("Has cerrado sesión.");
    }

    private void clearWorker() {
        linkingSensor = false;
        setSharing(false);
        ble.stopScan();
        ble.unlink();
        history = Collections.emptyList();
        assignments = Collections.emptyList();
    }

    private boolean workerAccount() {
        return auth.user != null && ("trabajador".equals(auth.user.role) || "admin".equals(auth.user.role)) && !auth.user.mustChangePassword;
    }

    private void refreshWorker() {
        refreshAssignments();
        refreshHistory();
    }

    public void refreshAssignments() {
        if (!workerAccount()) {
            assignmentStatus = "Accede con una cuenta de trabajador o administrador";
            return;
        }
        assignmentStatus = "Consultando asignaciones…";
        field.assignments((rows, error) -> {
            if (error == null) {
                assignments = rows;
                assignmentStatus = rows.size() + " asignaciones activas";
                if (selected() == null) {
                    ble.unlink();
                    setSharing(false);
                }
            } else assignmentStatus = "No se pudo actualizar: " + error;
            if (current == Screen.SENSOR || current == Screen.LINK || current == Screen.ACCOUNT) render();
        });
    }

    public void refreshHistory() {
        if (!workerAccount()) return;
        historyStatus = "Consultando historial…";
        field.history((rows, error) -> {
            if (error == null) history = rows;
            historyStatus = error == null ? rows.size() + " lecturas disponibles · máximo 1.000" : error;
            if (current == Screen.HISTORY || current == Screen.SENSOR) render();
        });
    }

    private OrbAssignment selected() { return assigned(ble.selectedAddress); }

    private OrbAssignment assigned(String address) {
        for (OrbAssignment assignment : assignments) {
            if (!assignment.address.isEmpty() && assignment.address.equalsIgnoreCase(address)) return assignment;
        }
        return null;
    }

    private void receive(LiveMeasurement reading) {
        OrbAssignment assignment = assigned(reading.address);
        if (!started || !sharing || assignment == null) return;
        serverStatus = "Enviando " + reading.reading.label() + "…";
        // Sin "Guardar mi recorrido" la lectura se envía sin posición GPS.
        field.upload(reading, assignment, prefs.saveRoute() ? location.current() : null, status -> serverStatus = status);
    }

    public void setSharing(boolean enabled) {
        sharing = enabled && selected() != null;
        serverStatus = sharing ? "Envío activado: esperando lecturas de campaña" : "Envío desactivado";
        if (!sharing) location.stop();
        else if (location.allowed()) {
            if (started) location.start();
        } else requestPermissions(locationPermissions(), GPS_CAPTURE);
    }

    public void selectOrb(String address) {
        if (assigned(address) == null) {
            message("Asigna primero el sensor a tu cuenta.");
            return;
        }
        if (!address.equalsIgnoreCase(ble.selectedAddress)) setSharing(false);
        ble.select(address);
        setSharing(!assigned(address).simulated);
        go(Screen.SENSOR);
    }

    public void assignSensor(String address, String name) {
        if (linkingSensor) { message("Se está guardando la asignación…"); return; }
        if (!workerAccount()) { message("Necesitas una cuenta de trabajador o administrador de tu organización."); return; }
        linkingSensor = true;
        message("Asignando sensor a tu cuenta…");
        field.linkSensor(address, name, (rows, error) -> {
            linkingSensor = false;
            if (error != null) {
                new android.app.AlertDialog.Builder(this).setTitle("No se pudo asignar el sensor")
                    .setMessage(error + "\n\nSi aparece HTTP 404, falta publicar la API sensor-link.php en el servidor.")
                    .setPositiveButton("Entendido", null).show();
                return;
            }
            assignments = rows;
            assignmentStatus = rows.size() + " sensores asignados a tu cuenta";
            selectOrb(address);
            message("Sensor asignado a tu cuenta");
        });
    }

    public void unlink() {
        ble.unlink();
        setSharing(false);
        render();
    }

    public void toggleScan() {
        if (ble.scanning) {
            ble.stopScan();
            return;
        }
        if (!ble.hasScanPermissions()) requestPermissions(ble.requiredScanPermissions(), BLE_PERMISSION);
        else scanWithBluetooth();
    }

    private void scanWithBluetooth() {
        if (ble.bluetoothEnabled()) ble.startScan();
        else {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH);
            } catch (SecurityException | android.content.ActivityNotFoundException error) {
                message("Activa Bluetooth desde los ajustes de Android.");
            }
        }
    }

    public String serverStatus() { return serverStatus; }
    public void retryUploads() {
        serverStatus = "Reintentando lecturas pendientes…";
        field.retryPending(status -> serverStatus = status);
    }
    public void updateLive(Runnable action) { updateLive = action; action.run(); }

    public void locateMap() { requestPermissions(locationPermissions(), GPS_MAP); }

    public boolean locationAllowed() { return location.allowed(); }

    /** Interruptor "Ubicación" de Vincular: pide el permiso o abre los ajustes para retirarlo. */
    public void toggleLocationPermission() {
        if (!location.allowed()) {
            requestPermissions(locationPermissions(), GPS_CAPTURE);
            return;
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
            message("Desactiva la ubicación en Permisos.");
        } catch (android.content.ActivityNotFoundException error) {
            message("Puedes retirar el permiso desde los ajustes de Android.");
        }
    }

    private String[] locationPermissions() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == BLE_PERMISSION) {
            if (ble.hasScanPermissions()) scanWithBluetooth();
            else message("No se han concedido los permisos Bluetooth");
        } else if (request == GPS_CAPTURE && sharing && started) location.start();
        else if (request == GPS_MAP && mapScreen != null) mapScreen.locate();
    }

    /** Nombre del sensor en este móvil: el que puso el usuario o el de la asignación. */
    public String sensorName(OrbAssignment sensor) {
        if (sensor == null) return "";
        String alias = prefs.alias(sensor.address);
        return alias != null ? alias : sensor.name;
    }

    public AppPrefs prefs() { return prefs; }

    public void reconnectSensor() {
        ble.stopScan();
        toggleScan();
        message("Buscando de nuevo tu sensor…");
    }

    public boolean biometricAvailable() {
        return androidx.biometric.BiometricManager.from(this).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
    }

    public boolean biometricEnabled(long userId) {
        if (!biometricAccess.hasEnrollment(userId)) return false;
        try {
            biometricAccess.beginUnlock(userId);
            return true;
        } catch (Exception invalidKey) {
            biometricAccess.disable(userId);
            return false;
        }
    }

    /** Activates a cryptographic biometric key scoped to the signed-in AERIQ user. */
    public void enableBiometricAccess(long userId, Consumer<Boolean> done) {
        if (auth.user == null || auth.user.id != userId || auth.sessionToken() == null) {
            message("Inicia sesión en esta cuenta antes de activar la biometría.");
            done.accept(false);
            return;
        }
        if (!biometricAvailable()) {
            message("Configura una huella o biometría fuerte en el móvil antes de activarla.");
            done.accept(false);
            return;
        }

        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Contraseña de AERIQ");
        passwordInput.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
        new android.app.AlertDialog.Builder(this)
            .setTitle("Confirma tu contraseña")
            .setMessage("Se guardará cifrada en este móvil para iniciar sesión después de validar tu huella.")
            .setView(passwordInput)
            .setNegativeButton("Cancelar", (dialog, which) -> done.accept(false))
            .setPositiveButton("Continuar", (dialog, which) -> {
                String password = passwordInput.getText().toString();
                if (password.isEmpty()) {
                    message("Escribe tu contraseña para activar el acceso biométrico.");
                    done.accept(false);
                    return;
                }
                enrollBiometricCredential(userId, password, done);
            })
            .show();
    }

    private void enrollBiometricCredential(long userId, String password, Consumer<Boolean> done) {
        final Cipher cipher;
        try {
            cipher = biometricAccess.beginEnrollment(userId);
        } catch (Exception error) {
            biometricAccess.disable(userId);
            message("No se pudo preparar la clave biométrica en este móvil.");
            done.accept(false);
            return;
        }
        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                    try {
                        androidx.biometric.BiometricPrompt.CryptoObject crypto = result.getCryptoObject();
                        biometricAccess.finishEnrollment(userId, crypto == null ? null : crypto.getCipher(),
                            auth.user.email, password);
                        done.accept(true);
                    } catch (Exception error) {
                        biometricAccess.disable(userId);
                        message("No se pudo vincular la biometría a esta cuenta.");
                        done.accept(false);
                    }
                }

                @Override public void onAuthenticationError(int code, CharSequence error) {
                    biometricAccess.disable(userId);
                    if (code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        message(error.toString());
                    }
                    done.accept(false);
                }
            });
        androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Activar acceso biométrico")
            .setSubtitle("Vincula una clave segura a " + auth.user.email)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build();
        prompt.authenticate(promptInfo, new androidx.biometric.BiometricPrompt.CryptoObject(cipher));
    }

    public void disableBiometricAccess(long userId) {
        biometricAccess.disable(userId);
    }

    /** Inicia una sesión nueva con credenciales descifradas solo tras validar biometría. */
    public void loginWithFingerprint() {
        long expectedUserId = biometricAccess.enrolledUserId();
        if (!biometricAccess.hasEnrollment(expectedUserId)) {
            message("Entra con la cuenta que quieras vincular y activa «Acceso con huella» en Cuenta.");
            return;
        }
        if (!biometricAvailable()) {
            message("Este móvil no permite entrar con huella. Usa tu correo.");
            return;
        }
        final Cipher cipher;
        try {
            cipher = biometricAccess.beginUnlock(expectedUserId);
        } catch (Exception invalidKey) {
            biometricAccess.disable(expectedUserId);
            render();
            message("La huella de esta cuenta ya no está vinculada. Entra con tu correo y actívala de nuevo.");
            return;
        }
        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                    completeBiometricLogin(expectedUserId, result.getCryptoObject());
                }
                @Override public void onAuthenticationError(int code, CharSequence error) {
                    if (code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) message(error.toString());
                }
            });
        androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar en AERIQ")
            .setSubtitle("Usa tu huella para entrar en " + biometricAccess.enrolledEmail())
            .setNegativeButtonText("Usar correo")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build();
        prompt.authenticate(promptInfo, new androidx.biometric.BiometricPrompt.CryptoObject(cipher));
    }

    private void completeBiometricLogin(long expectedUserId, androidx.biometric.BiometricPrompt.CryptoObject crypto) {
        final BiometricAccessStore.Credential credential;
        try {
            credential = crypto == null ? null : biometricAccess.finishUnlock(expectedUserId, crypto.getCipher());
        } catch (Exception error) {
            biometricAccess.disable(expectedUserId);
            message("No se pudieron recuperar las credenciales. Entra con tu correo y vuelve a activar la huella.");
            return;
        }
        if (credential == null) {
            biometricAccess.disable(expectedUserId);
            message("No se pudieron recuperar las credenciales. Entra con tu correo y vuelve a activar la huella.");
            return;
        }
        auth.submit("login", loggedIn -> {
            if (!loggedIn.success) {
                String detail = loggedIn.message == null ? "" : loggedIn.message;
                String normalized = detail.toLowerCase(Locale.ROOT);
                if (normalized.contains("contraseña incorrectos") || normalized.contains("no está activada")) {
                    biometricAccess.disable(expectedUserId);
                    message("La contraseña guardada ya no es válida. Entra con correo y vuelve a activar la huella.");
                } else {
                    message(detail.isEmpty()
                        ? "No se pudo iniciar sesión con la huella. Entra con tu correo y contraseña."
                        : detail);
                }
                return;
            }
            if (auth.user == null || auth.user.id != expectedUserId) {
                biometricAccess.disable(expectedUserId);
                auth.clearLocalSession();
                message("La cuenta vinculada no coincide. Entra con tu correo y contraseña.");
                return;
            }
            go(Screen.MAP);
            refreshWorker();
        }, "email", credential.email, "password", credential.password);
    }

    public void exportPdf(List<StoredReading> rows, String title) {
        exportRows = new ArrayList<>(rows);
        exportTitle = title;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "AERIQ-historial.pdf");
        startActivityForResult(intent, EXPORT_PDF);
    }

    private void writePdf(Uri uri, List<StoredReading> rows, String title) {
        maps.execute(() -> {
            android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
            try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new java.io.IOException("Destino no disponible");
                android.graphics.Paint bold = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                bold.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                android.graphics.Paint normal = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                normal.setTextSize(9);
                normal.setColor(0xff17201d);
                java.text.SimpleDateFormat local = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                double total = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
                for (StoredReading reading : rows) {
                    total += reading.value; min = Math.min(min, reading.value); max = Math.max(max, reading.value);
                }
                int pageNumber = 1, index = 0;
                while (index < rows.size() || pageNumber == 1) {
                    android.graphics.pdf.PdfDocument.Page page = document.startPage(
                        new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageNumber).create());
                    android.graphics.Canvas canvas = page.getCanvas();
                    float y = 50;
                    if (pageNumber == 1) {
                        bold.setTextSize(18); bold.setColor(0xff0e6b5e);
                        canvas.drawText("AERIQ · Historial de ozono", 40, y, bold); y += 20;
                        normal.setTextSize(10);
                        canvas.drawText(title + " · " + rows.size() + " mediciones · generado " + local.format(new Date()), 40, y, normal); y += 16;
                        if (!rows.isEmpty()) {
                            String unit = rows.get(0).unit;
                            canvas.drawText(String.format(Locale.getDefault(), "Media %.1f %s · Mínimo %.1f · Máximo %.1f",
                                total / rows.size(), unit, min, max), 40, y, normal);
                        }
                        y += 26;
                    }
                    bold.setTextSize(9); bold.setColor(0xff17201d);
                    canvas.drawText("Fecha (hora local)", 40, y, bold);
                    canvas.drawText("Valor", 160, y, bold);
                    canvas.drawText("Sensor", 220, y, bold);
                    canvas.drawText("Zona", 390, y, bold);
                    y += 14;
                    normal.setTextSize(9);
                    while (index < rows.size() && y < 805) {
                        StoredReading reading = rows.get(index++);
                        long time = com.aeriq.app.ui.Quality.utc(reading.date);
                        canvas.drawText(time > 0 ? local.format(new Date(time)) : reading.date, 40, y, normal);
                        canvas.drawText(com.aeriq.app.ui.Quality.number(reading.value) + " " + reading.unit, 160, y, normal);
                        canvas.drawText(clip(reading.device, 30), 220, y, normal);
                        canvas.drawText(clip(reading.zone, 32), 390, y, normal);
                        y += 13;
                    }
                    normal.setTextSize(8);
                    canvas.drawText("Página " + pageNumber + " · Mediciones complementarias, no sustituyen a las estaciones oficiales.", 40, 825, normal);
                    document.finishPage(page);
                    pageNumber++;
                    if (rows.isEmpty()) break;
                }
                document.writeTo(output);
                runOnUiThread(() -> message("PDF guardado."));
            } catch (Exception error) {
                runOnUiThread(() -> message("No se pudo exportar: " + error.getMessage()));
            } finally {
                document.close();
            }
        });
    }

    private static String clip(String value, int length) {
        return value == null ? "" : value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }

    public void export(List<StoredReading> rows) {
        exportRows = new ArrayList<>(rows);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "AERIQ-historial.csv");
        startActivityForResult(intent, EXPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BLUETOOTH && ble.bluetoothEnabled()) ble.startScan();
        if (request == EXPORT_PDF && result == RESULT_OK && data != null && exportRows != null) {
            List<StoredReading> rows = exportRows;
            exportRows = null;
            writePdf(data.getData(), rows, exportTitle);
            return;
        }
        if (request == EXPORT && result == RESULT_OK && data != null && exportRows != null) {
            Uri uri = data.getData();
            String csv = MeasurementCsv.format(exportRows);
            exportRows = null;
            maps.execute(() -> {
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new java.io.IOException("Destino no disponible");
                    output.write(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    runOnUiThread(() -> message("CSV guardado."));
                } catch (Exception error) {
                    runOnUiThread(() -> message("No se pudo exportar: " + error.getMessage()));
                }
            });
        }
    }

    public void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException error) { message("No hay navegador disponible."); }
    }

    public void message(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        if (ble.selectedAddress != null && ble.hasScanPermissions() && ble.bluetoothEnabled()) ble.startScan();
        if (sharing) location.start();
        if (mapScreen != null) mapScreen.resume();
        main.post(tick);
    }

    @Override protected void onStop() {
        started = false;
        main.removeCallbacks(tick);
        ble.stopScan();
        location.stop();
        if (mapScreen != null) mapScreen.pause();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (mapScreen != null) mapScreen.close();
        auth.close();
        field.close();
        maps.shutdownNow();
        super.onDestroy();
    }

    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        navigateBack();
    }

    private void navigateBack() {
        switch (current) {
            case FORGOT: go(Screen.LOGIN); break;
            case RESET: go(Screen.FORGOT); break;
            case CONFIG: case HISTORY: case LINK: go(Screen.SENSOR); break;
            case EDIT_PROFILE: case CHANGE_PASSWORD: case SERVICES: case PREMIUM: go(Screen.ACCOUNT); break;
            case MAP: case LOGIN: finish(); break;
            default: go(Screen.MAP);
        }
    }
}
