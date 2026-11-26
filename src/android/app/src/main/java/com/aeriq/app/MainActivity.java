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

    /**
     * Gestiona attach base context.
     * Diseño lógico: (android.content.Context base) → attachBaseContext() → void; según doc/android_design.md.
     */
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
        CONFIG, COMPARE, SERVICES, ACCOUNT, EDIT_PROFILE, CHANGE_PASSWORD, PANEL, ASSIGN, NOTICES, NOTICE, NOTICE_NEW
    }
    /** Pantallas del Orb: solo para cuentas que miden (permiso mediciones.registrar), como en la web. */
    private static final EnumSet<Screen> SENSOR_SCREENS = EnumSet.of(Screen.SENSOR, Screen.LINK, Screen.HISTORY, Screen.CONFIG);

    /** Avisos entre personas (HU36): solo cuentas de una organización. */
    private static final EnumSet<Screen> NOTICE_SCREENS = EnumSet.of(Screen.NOTICES, Screen.NOTICE, Screen.NOTICE_NEW);

    private static final EnumSet<Screen> PUBLIC = EnumSet.of(
        Screen.LOGIN, Screen.FORGOT, Screen.RESET, Screen.MAP, Screen.LEARN, Screen.COMPARE);
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
        /**
         * Ejecuta.
         * Diseño lógico: (sin parámetros) → run() → void; según doc/android_design.md.
         */
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

    /**
     * Atiende el evento creación.
     * Diseño lógico: (Bundle state) → onCreate() → void; según doc/android_design.md.
     */
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
        ble.onBattery = this::receiveBattery;
        // El operador no tiene que vincular a mano su único Orb: al verlo por Bluetooth se conecta y empieza a enviar.
        ble.onAutoSelect = () -> {
            OrbAssignment orb = selected();
            setSharing(orb != null && !orb.simulated);
            if (current == Screen.SENSOR || current == Screen.LINK) render();
        };
        if (state != null) {
            // Una pantalla guardada que ya no existe (por ejemplo, la antigua Premium) vuelve al inicio de sesión.
            try { current = Screen.valueOf(state.getString("screen", Screen.LOGIN.name())); }
            catch (IllegalArgumentException removed) { current = Screen.LOGIN; }
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
        // HU34/HU33: la animación sale en cada arranque nuevo (no al recrear la actividad); después, el tutorial si nunca se vio.
        if (state == null) showIntro();
        long biometricUserId = auth.savedUserId();
        boolean legacyBiometric = prefs.consumeLegacyFingerprint(biometricUserId);
        if (legacyBiometric && !biometricAccess.hasEnrollment(biometricUserId) && auth.sessionToken() != null) {
            auth.clearLocalSession();
            restoring = false;
            current = Screen.LOGIN;
            render();
            message("Por seguridad, vuelve a entrar con tu correo y activa de nuevo el acceso biométrico.", TONE_WARN);
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
                message("La biometría registrada cambió o ya no está disponible. Entra con tu correo y actívala de nuevo.", TONE_WARN);
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
            if (!result.message.isEmpty() && !result.success) message(result.message, TONE_ERROR);
            if (auth.user != null && current.ordinal() <= Screen.RESET.ordinal()) current = Screen.MAP;
            go(current);
            refreshWorker();
        });
    }

    /**
     * Atiende el evento save instance estado.
     * Diseño lógico: (Bundle state) → onSaveInstanceState() → void; según doc/android_design.md.
     */
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", current.name());
    }

    /**
     * Gestiona go.
     * Diseño lógico: (Screen destination) → go() → void; según doc/android_design.md.
     */
    public void go(Screen destination) {
        if (auth.user != null && auth.user.mustChangePassword) destination = Screen.CHANGE_PASSWORD;
        else if (auth.user == null && !PUBLIC.contains(destination)) {
            destination = Screen.LOGIN;
            message("Inicia sesión con la cuenta de tu organización para acceder al sensor.", TONE_WARN);
        } else if (auth.user != null && !auth.user.collects() && SENSOR_SCREENS.contains(destination)) {
            // Los administradores no miden: su pestaña es Panel (gestión en la web).
            destination = Screen.PANEL;
        } else if (destination == Screen.PANEL && (auth.user == null || auth.user.collects())) {
            destination = auth.user == null ? Screen.LOGIN : Screen.SENSOR;
        } else if (destination == Screen.ASSIGN && (auth.user == null
            || !auth.user.can(AeriqAuthManager.User.PERMISSION_TEAM))) {
            // Asignar Orb es del admin de campaña, como «Registrar Orb» en el panel web.
            destination = auth.user == null ? Screen.LOGIN : Screen.PANEL;
        } else if (NOTICE_SCREENS.contains(destination) && !noticesAvailable()) {
            destination = Screen.ACCOUNT;
        } else if (destination == Screen.NOTICE && noticeId == 0) {
            destination = Screen.NOTICES;
        }
        if (destination == Screen.NOTICES && current != Screen.NOTICES) refreshNotices();
        // Al salir de Asignar Orb se deja de buscar por Bluetooth: el admin no recibe lecturas.
        if (current == Screen.ASSIGN && destination != Screen.ASSIGN && ble.scanning) ble.stopScan();
        if (destination == Screen.ASSIGN && current != Screen.ASSIGN) refreshTeam();
        if (current != destination || body.getChildCount() == 0 || mapScreen == null) {
            boolean enteringLink = destination == Screen.LINK && current != Screen.LINK;
            current = destination;
            render();
            // Al abrir "Vincular sensor" se empieza a buscar directamente, como en el diseño.
            if (enteringLink && !ble.scanning) toggleScan();
        }
    }

    /**
     * Representa.
     * Diseño lógico: (sin parámetros) → render() → void; según doc/android_design.md.
     */
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
                title("Mapa de ozono", null);
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
                title("Mi sensor", null);
                work.sensor(body, ble, assignments, assignmentStatus, serverStatus, sharing, history);
                break;
            case LINK:
                title("Vincular sensor", Screen.SENSOR);
                work.link(body, ble, assignments);
                break;
            case HISTORY:
                title("Historial", Screen.SENSOR);
                design.history(body, history, historyStatus, selected());
                break;
            case CONFIG:
                title("Detalles del sensor", Screen.SENSOR);
                design.details(body, ble, selected(), location.allowed());
                break;
            case COMPARE:
                title("Comparar zonas", Screen.MAP);
                design.compare(body, maps);
                break;
            case SERVICES:
                title("Soluciones AERIQ", Screen.ACCOUNT);
                work.services(body);
                break;
            case PANEL:
                title("Panel", null);
                work.panel(body, auth.user);
                break;
            case ASSIGN:
                title("Asignar Orb", Screen.PANEL);
                work.assign(body, ble, team, teamStatus);
                break;
            case NOTICES:
                title("Avisos", Screen.ACCOUNT);
                noticeScreens().inbox(body, noticeInbox, noticeStatus);
                break;
            case NOTICE:
                title("Aviso", Screen.NOTICES);
                noticeScreens().conversation(body, noticeConversation, noticeStatus);
                break;
            case NOTICE_NEW:
                title("Nuevo aviso", Screen.NOTICES);
                noticeScreens().compose(body, noticeInbox);
                break;
            case ACCOUNT:
                title("Cuenta", null);
                design.account(body, auth.user, selected(), sharing, prefs);
                break;
            default:
                throw new IllegalStateException(current.name());
        }
    }

    /**
     * Gestiona title.
     * Diseño lógico: (String title, Screen back) → title() → void; según doc/android_design.md.
     */
    public void title(String title, Screen back) { title(title, back, 0, null); }

    /**
     * Gestiona title.
     * Diseño lógico: (String title, Screen back, int rightIcon, Runnable rightAction) → title() → void; según doc/android_design.md.
     */
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

    /**
     * Muestra navegación.
     * Diseño lógico: (sin parámetros) → showNavigation() → void; según doc/android_design.md.
     */
    private void showNavigation() {
        // La segunda pestaña depende de los permisos: quien mide ve «Mi sensor»; quien gestiona, «Panel».
        boolean manager = auth.user != null && !auth.user.collects() && auth.user.manages();
        Screen[] routes = {Screen.MAP, manager ? Screen.PANEL : Screen.SENSOR, Screen.LEARN, Screen.ACCOUNT};
        String[] labels = {"Mapa", manager ? "Panel" : "Mi sensor", "Aprende", "Cuenta"};
        int[] icons = {R.drawable.figma_nav_map, manager ? R.drawable.ic_sliders : R.drawable.figma_nav_chip,
            R.drawable.figma_nav_book, R.drawable.figma_nav_user};
        Screen active = current;
        if (EnumSet.of(Screen.LINK, Screen.CONFIG, Screen.HISTORY).contains(current)) active = Screen.SENSOR;
        if (current == Screen.COMPARE) active = Screen.MAP;
        if (current == Screen.SERVICES || NOTICE_SCREENS.contains(current)) active = Screen.ACCOUNT;
        if (current == Screen.ASSIGN) active = Screen.PANEL;
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
            // La etiqueta ocupa todo el ancho de la pestaña: hay que centrar su texto
            // para que quede justo debajo del icono (si no, se pega a la izquierda).
            TextView label = ui.text(labels[index], 12, selected, selected ? Views.GREEN : Views.MUTED);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            tab.addView(label, new LinearLayout.LayoutParams(-1, -2));
            tab.setContentDescription(labels[index]);
            tab.setTag("tour:tab:" + route.name()); // para el tour con foco (HU33)
            // Avisos nuevos (HU36): punto rojo sobre «Cuenta», donde está la bandeja.
            if (route == Screen.ACCOUNT && noticeInbox.unread > 0) {
                FrameLayout holder = new FrameLayout(this);
                tab.removeView(icon);
                holder.addView(icon, new FrameLayout.LayoutParams(ui.dp(56), ui.dp(30)));
                View dot = new View(this);
                dot.setBackground(ui.shape(Views.RED, 5, Views.WHITE));
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(ui.dp(10), ui.dp(10), Gravity.TOP | Gravity.END);
                dotParams.setMargins(0, ui.dp(1), ui.dp(12), 0);
                holder.addView(dot, dotParams);
                tab.addView(holder, 0, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(30)));
                tab.setContentDescription(labels[index] + ", " + noticeInbox.unread
                    + (noticeInbox.unread == 1 ? " aviso nuevo" : " avisos nuevos"));
            }
            tab.setOnClickListener(view -> go(route));
            ui.weighted(navigation, tab);
        }
    }

    /**
     * Envía.
     * Diseño lógico: (String action, Consumer<AeriqAuthManager.Result> done, Object... fields) → submit() → void; según doc/android_design.md.
     */
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
                message(result.message, TONE_WARN);
                return;
            }
            done.accept(result);
            if (biometricNeedsReenrollment) {
                message("Por seguridad, activa de nuevo el acceso biométrico desde Cuenta.", TONE_WARN);
            }
            if (biometricPasswordChanged) {
                message("La contraseña cambió. Vuelve a activar el acceso con huella o cara desde Cuenta.", TONE_WARN);
            }
        }, fields);
    }

    /**
     * Gestiona cierre de sesión.
     * Diseño lógico: (sin parámetros) → logout() → void; según doc/android_design.md.
     */
    public void logout() {
        if (auth.busy) {
            message("Espera a que termine la operación.", TONE_WARN);
            return;
        }
        auth.logout();
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        clearWorker();
        go(Screen.LOGIN);
        message("Has cerrado sesión.", TONE_OK);
    }

    /**
     * Gestiona clear operador.
     * Diseño lógico: (sin parámetros) → clearWorker() → void; según doc/android_design.md.
     */
    private void clearWorker() {
        setSharing(false);
        ble.preferredAddress = null;
        team = CampaignTeam.empty();
        noticeInbox = Notices.Inbox.empty();
        noticeConversation = null;
        noticeId = 0;
        ble.stopScan();
        ble.unlink();
        history = Collections.emptyList();
        assignments = Collections.emptyList();
    }

    /**
     * Gestiona operador cuenta.
     * Diseño lógico: (sin parámetros) → workerAccount() → boolean; según doc/android_design.md.
     */
    private boolean workerAccount() {
        return auth.user != null && auth.user.can(AeriqAuthManager.User.PERMISSION_COLLECT) && !auth.user.mustChangePassword;
    }

    /**
     * Actualiza operador.
     * Diseño lógico: (sin parámetros) → refreshWorker() → void; según doc/android_design.md.
     */
    private void refreshWorker() {
        refreshNotices();
        refreshAssignments();
        refreshHistory();
    }

    /**
     * Actualiza asignaciones.
     * Diseño lógico: (sin parámetros) → refreshAssignments() → void; según doc/android_design.md.
     */
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
                OrbAssignment only = onlyRealAssignment();
                ble.preferredAddress = only == null ? null : only.address;
                if (only != null && selected() == null && started && ble.hasScanPermissions() && ble.bluetoothEnabled()) ble.startScan();
                if (selected() == null) {
                    ble.unlink();
                    setSharing(false);
                }
            } else assignmentStatus = "No se pudo actualizar: " + error;
            if (current == Screen.SENSOR || current == Screen.LINK || current == Screen.ACCOUNT) render();
        });
    }

    /**
     * Actualiza historial.
     * Diseño lógico: (sin parámetros) → refreshHistory() → void; según doc/android_design.md.
     */
    public void refreshHistory() {
        if (!workerAccount()) return;
        historyStatus = "Consultando historial…";
        field.history((rows, error) -> {
            if (error == null) history = rows;
            historyStatus = error == null ? rows.size() + " lecturas disponibles · máximo 1.000" : error;
            if (current == Screen.HISTORY || current == Screen.SENSOR) render();
        });
    }

    /**
     * Gestiona selected.
     * Diseño lógico: (sin parámetros) → selected() → OrbAssignment; según doc/android_design.md.
     */
    private OrbAssignment selected() { return assigned(ble.selectedAddress); }

    // ---------------------------------------------------------------- Avisos (HU36)
    private NoticeScreens noticeScreens;
    private Notices.Inbox noticeInbox = Notices.Inbox.empty();
    private Notices.Conversation noticeConversation;
    private String noticeStatus = "";
    private long noticeId;

    /**
     * Gestiona aviso screens.
     * Diseño lógico: (sin parámetros) → noticeScreens() → NoticeScreens; según doc/android_design.md.
     */
    private NoticeScreens noticeScreens() {
        if (noticeScreens == null) noticeScreens = new NoticeScreens(this, ui);
        return noticeScreens;
    }

    /** Los avisos son para cuentas de una organización (operador, admin de campaña y admin principal). */
    /**
     * Diseño lógico: (sin parámetros) → noticesAvailable() → boolean; según doc/android_design.md.
     */
    public boolean noticesAvailable() {
        return auth.user != null && !auth.user.mustChangePassword && !auth.user.organization.isEmpty();
    }

    /**
     * Gestiona avisos unread.
     * Diseño lógico: (sin parámetros) → noticesUnread() → int; según doc/android_design.md.
     */
    public int noticesUnread() { return noticeInbox.unread; }

    /**
     * Representa current.
     * Diseño lógico: (sin parámetros) → renderCurrent() → void; según doc/android_design.md.
     */
    public void renderCurrent() { render(); }

    /**
     * Actualiza avisos.
     * Diseño lógico: (sin parámetros) → refreshNotices() → void; según doc/android_design.md.
     */
    public void refreshNotices() {
        if (!noticesAvailable()) return;
        if (current == Screen.NOTICES && noticeInbox.notices.isEmpty()) noticeStatus = "Consultando tus avisos…";
        field.notices((inbox, error) -> {
            int before = noticeInbox.unread;
            boolean hadContacts = !noticeInbox.contacts.isEmpty();
            if (error == null) noticeInbox = inbox;
            noticeStatus = error == null ? "" : "No se pudo consultar: " + error;
            // Se repinta solo donde no se borra nada escrito; en el resto, el punto se actualiza en la siguiente visita.
            if (current == Screen.NOTICES || current == Screen.ACCOUNT || (current == Screen.NOTICE_NEW && !hadContacts)) render();
            else if (before != noticeInbox.unread && EnumSet.of(Screen.SENSOR, Screen.LEARN, Screen.PANEL, Screen.NOTICE).contains(current)) render();
        });
    }

    /**
     * Abre aviso.
     * Diseño lógico: (long id) → openNotice() → void; según doc/android_design.md.
     */
    public void openNotice(long id) {
        noticeId = id;
        noticeConversation = null;
        noticeStatus = "";
        go(Screen.NOTICE);
        field.notice(id, (notice, error) -> {
            if (noticeId != id) return;
            noticeConversation = notice;
            noticeStatus = error == null ? "" : "No se pudo abrir: " + error;
            if (current == Screen.NOTICE) render();
            if (error == null) refreshNotices(); // abrirlo lo marca como visto: baja el contador
        });
    }

    /**
     * Crea aviso.
     * Diseño lógico: (long to, String subject, String text) → createNotice() → void; según doc/android_design.md.
     */
    public void createNotice(long to, String subject, String text) {
        message("Enviando el aviso…", TONE_INFO);
        field.createNotice(to, subject, text, (result, error) -> {
            if (error != null) { message(error, TONE_ERROR); return; }
            message(result, TONE_OK);
            go(Screen.NOTICES);
            refreshNotices();
        });
    }

    /**
     * Gestiona reply aviso.
     * Diseño lógico: (long id, String text) → replyNotice() → void; según doc/android_design.md.
     */
    public void replyNotice(long id, String text) {
        message("Enviando la respuesta…", TONE_INFO);
        field.replyNotice(id, text, (result, error) -> {
            if (error != null) { message(error, TONE_ERROR); return; }
            message(result, TONE_OK);
            openNotice(id);
        });
    }

    /**
     * Cierra aviso.
     * Diseño lógico: (long id) → closeNotice() → void; según doc/android_design.md.
     */
    public void closeNotice(long id) {
        field.closeNotice(id, (result, error) -> {
            if (error != null) { message(error, TONE_ERROR); return; }
            message(result, TONE_OK);
            openNotice(id);
        });
    }

    // ---------------------------------------------------------------- Asignar Orb (admin de campaña)
    private CampaignTeam team = CampaignTeam.empty();
    private String teamStatus = "Consultando tu campaña…";

    /**
     * Actualiza equipo.
     * Diseño lógico: (sin parámetros) → refreshTeam() → void; según doc/android_design.md.
     */
    public void refreshTeam() {
        teamStatus = "Consultando tu campaña…";
        field.team((rows, error) -> {
            if (error == null) team = rows;
            teamStatus = error == null ? (rows.campaign.isEmpty() ? "" : "Campaña: " + rows.campaign) : "No se pudo consultar: " + error;
            if (current == Screen.ASSIGN) render();
        });
    }

    /** Registra o reasigna el Orb al operador elegido; el servidor aplica las mismas reglas que el panel web. */
    /**
     * Diseño lógico: (CampaignTeam.Device existing, String address, String name, CampaignTeam.Operator operator) → assignOrb() → void; según doc/android_design.md.
     */
    public void assignOrb(CampaignTeam.Device existing, String address, String name, CampaignTeam.Operator operator) {
        message("Asignando " + name + " a " + operator.name + "…", TONE_INFO);
        field.assignOrb(existing == null ? null : existing.id, name, address, operator.id,
            existing != null && existing.simulated, error -> {
                if (error != null) {
                    new android.app.AlertDialog.Builder(this).setTitle("No se pudo asignar el Orb")
                        .setMessage(error).setPositiveButton("Entendido", null).show();
                    return;
                }
                message(name + " asignado a " + operator.name + ". Le aparecerá al abrir la app.", TONE_OK);
                refreshTeam();
            });
    }

    /** El único Orb real asignado a la cuenta, o null si no hay ninguno o hay varios (entonces se elige en Vincular). */
    /**
     * Diseño lógico: (sin parámetros) → onlyRealAssignment() → OrbAssignment; según doc/android_design.md.
     */
    private OrbAssignment onlyRealAssignment() {
        OrbAssignment only = null;
        for (OrbAssignment orb : assignments) {
            if (orb.simulated || orb.address.isEmpty()) continue;
            if (only != null) return null;
            only = orb;
        }
        return only;
    }

    /**
     * Gestiona assigned.
     * Diseño lógico: (String address) → assigned() → OrbAssignment; según doc/android_design.md.
     */
    private OrbAssignment assigned(String address) {
        for (OrbAssignment assignment : assignments) {
            if (!assignment.address.isEmpty() && assignment.address.equalsIgnoreCase(address)) return assignment;
        }
        return null;
    }

    /**
     * Gestiona receive.
     * Diseño lógico: (LiveMeasurement reading) → receive() → void; según doc/android_design.md.
     */
    private void receive(LiveMeasurement reading) {
        OrbAssignment assignment = assigned(reading.address);
        if (!started || !sharing || assignment == null) return;
        // HU35: se comprueba el rango antes de enviar. Se envía igualmente: el servidor la guarda como no válida.
        if (!assignment.inRange(reading.reading.typeId, reading.reading.value)) {
            serverStatus = "Lectura descartada: " + reading.reading.value + " " + reading.reading.unit()
                + " está fuera del rango admitido (" + assignment.range(reading.reading.typeId) + ")";
            field.upload(reading, assignment, location.current(), status -> serverStatus = status);
            return;
        }
        serverStatus = "Enviando " + reading.reading.label() + "…";
        // La posición va siempre: sin ella la lectura no puede salir en el mapa de la campaña.
        field.upload(reading, assignment, location.current(), status -> serverStatus = status);
    }

    private String batterySentAddress;
    private int batterySentPercent = -1;
    private long batterySentAt;

    /** Envía la batería del Orb asignado si ha cambiado o han pasado 10 minutos desde el último envío. */
    /**
     * Diseño lógico: (String address, int percent) → receiveBattery() → void; según doc/android_design.md.
     */
    private void receiveBattery(String address, int percent) {
        OrbAssignment assignment = assigned(address);
        if (assignment == null || assignment.simulated) return;
        long now = System.currentTimeMillis();
        boolean same = address.equalsIgnoreCase(batterySentAddress) && percent == batterySentPercent;
        if (same && now - batterySentAt < 10 * 60_000L) return;
        batterySentAddress = address;
        batterySentPercent = percent;
        batterySentAt = now;
        field.battery(assignment, address, percent, now);
    }

    /**
     * Envío a la campaña: las lecturas del Orb seleccionado se envían solas a su campaña. Publicarlas en el mapa
     * público lo decide el administrador de la campaña desde el panel web, no la app.
     */
    /**
     * Diseño lógico: (boolean enabled) → setSharing() → void; según doc/android_design.md.
     */
    public void setSharing(boolean enabled) {
        sharing = enabled && selected() != null;
        serverStatus = sharing ? "Envío activado: esperando lecturas de campaña" : "Envío desactivado";
        if (!sharing) location.stop();
        else if (location.allowed()) {
            if (started) location.start();
        } else requestPermissions(locationPermissions(), GPS_CAPTURE);
    }

    /**
     * Selecciona Orb.
     * Diseño lógico: (String address) → selectOrb() → void; según doc/android_design.md.
     */
    public void selectOrb(String address) {
        if (assigned(address) == null) {
            message("Asigna primero el sensor a tu cuenta.", TONE_WARN);
            return;
        }
        if (!address.equalsIgnoreCase(ble.selectedAddress)) setSharing(false);
        ble.select(address);
        setSharing(!assigned(address).simulated);
        go(Screen.SENSOR);
    }

    /**
     * Gestiona unlink.
     * Diseño lógico: (sin parámetros) → unlink() → void; según doc/android_design.md.
     */
    public void unlink() {
        ble.unlink();
        setSharing(false);
        render();
    }

    /**
     * Alterna scan.
     * Diseño lógico: (sin parámetros) → toggleScan() → void; según doc/android_design.md.
     */
    public void toggleScan() {
        if (ble.scanning) {
            ble.stopScan();
            return;
        }
        if (!ble.hasScanPermissions()) requestPermissions(ble.requiredScanPermissions(), BLE_PERMISSION);
        else scanWithBluetooth();
    }

    /**
     * Gestiona scan with bluetooth.
     * Diseño lógico: (sin parámetros) → scanWithBluetooth() → void; según doc/android_design.md.
     */
    private void scanWithBluetooth() {
        if (ble.bluetoothEnabled()) ble.startScan();
        else {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH);
            } catch (SecurityException | android.content.ActivityNotFoundException error) {
                message("Activa Bluetooth desde los ajustes de Android.", TONE_WARN);
            }
        }
    }

    /**
     * Gestiona server estado.
     * Diseño lógico: (sin parámetros) → serverStatus() → String; según doc/android_design.md.
     */
    public String serverStatus() { return serverStatus; }
    /**
     * Gestiona retry uploads.
     * Diseño lógico: (sin parámetros) → retryUploads() → void; según doc/android_design.md.
     */
    public void retryUploads() {
        serverStatus = "Reintentando lecturas pendientes…";
        field.retryPending(status -> serverStatus = status);
    }
    /**
     * Actualiza live.
     * Diseño lógico: (Runnable action) → updateLive() → void; según doc/android_design.md.
     */
    public void updateLive(Runnable action) { updateLive = action; action.run(); }

    /**
     * Gestiona locate mapa.
     * Diseño lógico: (sin parámetros) → locateMap() → void; según doc/android_design.md.
     */
    public void locateMap() { requestPermissions(locationPermissions(), GPS_MAP); }

    /**
     * Gestiona ubicación allowed.
     * Diseño lógico: (sin parámetros) → locationAllowed() → boolean; según doc/android_design.md.
     */
    public boolean locationAllowed() { return location.allowed(); }

    /** Interruptor "Ubicación" de Vincular: pide el permiso o abre los ajustes para retirarlo. */
    /**
     * Diseño lógico: (sin parámetros) → toggleLocationPermission() → void; según doc/android_design.md.
     */
    public void toggleLocationPermission() {
        if (!location.allowed()) {
            requestPermissions(locationPermissions(), GPS_CAPTURE);
            return;
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
            message("Desactiva la ubicación en Permisos.", TONE_WARN);
        } catch (android.content.ActivityNotFoundException error) {
            message("Puedes retirar el permiso desde los ajustes de Android.", TONE_WARN);
        }
    }

    /**
     * Gestiona ubicación permisos.
     * Diseño lógico: (sin parámetros) → locationPermissions() → String[]; según doc/android_design.md.
     */
    private String[] locationPermissions() {
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    /**
     * Atiende el evento petición permisos resultado.
     * Diseño lógico: (int request, String[] permissions, int[] results) → onRequestPermissionsResult() → void; según doc/android_design.md.
     */
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == BLE_PERMISSION) {
            if (ble.hasScanPermissions()) scanWithBluetooth();
            else message("No se han concedido los permisos Bluetooth", TONE_ERROR);
        } else if (request == GPS_CAPTURE) {
            if (sharing && started) location.start();
            if (current == Screen.CONFIG) render();
        }
        else if (request == GPS_MAP && mapScreen != null) mapScreen.locate();
    }

    /** Nombre del sensor en este móvil: el que puso el usuario o el de la asignación. */
    /** Nombre del Orb tal como lo registra el admin de campaña en el panel web. */
    /**
     * Diseño lógico: (OrbAssignment sensor) → sensorName() → String; según doc/android_design.md.
     */
    public String sensorName(OrbAssignment sensor) { return sensor == null ? "" : sensor.name; }

    /** Pide el permiso de ubicación desde «Detalles del sensor» si la persona lo denegó. */
    /**
     * Diseño lógico: (sin parámetros) → requestCaptureLocation() → void; según doc/android_design.md.
     */
    public void requestCaptureLocation() {
        if (location.allowed()) { message("La ubicación ya está permitida.", TONE_OK); return; }
        requestPermissions(locationPermissions(), GPS_CAPTURE);
    }

    /**
     * Gestiona preferencias.
     * Diseño lógico: (sin parámetros) → prefs() → AppPrefs; según doc/android_design.md.
     */
    public AppPrefs prefs() { return prefs; }

    /**
     * Gestiona reconnect sensor.
     * Diseño lógico: (sin parámetros) → reconnectSensor() → void; según doc/android_design.md.
     */
    public void reconnectSensor() {
        ble.stopScan();
        toggleScan();
        message("Buscando de nuevo tu sensor…", TONE_INFO);
    }

    /**
     * Gestiona biometric available.
     * Diseño lógico: (sin parámetros) → biometricAvailable() → boolean; según doc/android_design.md.
     */
    public boolean biometricAvailable() {
        return androidx.biometric.BiometricManager.from(this).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Gestiona biometric enabled.
     * Diseño lógico: (long userId) → biometricEnabled() → boolean; según doc/android_design.md.
     */
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
    /**
     * Diseño lógico: (long userId, Consumer<Boolean> done) → enableBiometricAccess() → void; según doc/android_design.md.
     */
    public void enableBiometricAccess(long userId, Consumer<Boolean> done) {
        if (auth.user == null || auth.user.id != userId || auth.sessionToken() == null) {
            message("Inicia sesión en esta cuenta antes de activar la biometría.", TONE_WARN);
            done.accept(false);
            return;
        }
        if (!biometricAvailable()) {
            message("Configura una huella o biometría fuerte en el móvil antes de activarla.", TONE_WARN);
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
            .setMessage("Se guardará cifrada en este móvil para iniciar sesión después de validar tu huella o tu cara.")
            .setView(passwordInput)
            .setNegativeButton("Cancelar", (dialog, which) -> done.accept(false))
            .setPositiveButton("Continuar", (dialog, which) -> {
                String password = passwordInput.getText().toString();
                if (password.isEmpty()) {
                    message("Escribe tu contraseña para activar el acceso biométrico.", TONE_WARN);
                    done.accept(false);
                    return;
                }
                enrollBiometricCredential(userId, password, done);
            })
            .show();
    }

    /**
     * Gestiona enroll biometric credential.
     * Diseño lógico: (long userId, String password, Consumer<Boolean> done) → enrollBiometricCredential() → void; según doc/android_design.md.
     */
    private void enrollBiometricCredential(long userId, String password, Consumer<Boolean> done) {
        final Cipher cipher;
        try {
            cipher = biometricAccess.beginEnrollment(userId);
        } catch (Exception error) {
            biometricAccess.disable(userId);
            message("No se pudo preparar la clave biométrica en este móvil.", TONE_ERROR);
            done.accept(false);
            return;
        }
        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                /**
                 * Atiende el evento authentication succeeded.
                 * Diseño lógico: (androidx.biometric.BiometricPrompt.AuthenticationResult result) → onAuthenticationSucceeded() → void; según doc/android_design.md.
                 */
                @Override public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                    try {
                        androidx.biometric.BiometricPrompt.CryptoObject crypto = result.getCryptoObject();
                        biometricAccess.finishEnrollment(userId, crypto == null ? null : crypto.getCipher(),
                            auth.user.email, password);
                        done.accept(true);
                    } catch (Exception error) {
                        biometricAccess.disable(userId);
                        message("No se pudo vincular la biometría a esta cuenta.", TONE_ERROR);
                        done.accept(false);
                    }
                }

                /**
                 * Atiende el evento authentication error.
                 * Diseño lógico: (int code, CharSequence error) → onAuthenticationError() → void; según doc/android_design.md.
                 */
                @Override public void onAuthenticationError(int code, CharSequence error) {
                    biometricAccess.disable(userId);
                    if (code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        message(error.toString(), TONE_ERROR);
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

    /**
     * Gestiona disable biometric acceso.
     * Diseño lógico: (long userId) → disableBiometricAccess() → void; según doc/android_design.md.
     */
    public void disableBiometricAccess(long userId) {
        biometricAccess.disable(userId);
    }

    /** Inicia una sesión nueva con credenciales descifradas solo tras validar biometría. */
    /**
     * Diseño lógico: (sin parámetros) → loginWithFingerprint() → void; según doc/android_design.md.
     */
    public void loginWithFingerprint() {
        long expectedUserId = biometricAccess.enrolledUserId();
        if (!biometricAccess.hasEnrollment(expectedUserId)) {
            message("Entra con la cuenta que quieras vincular y activa «Acceso con huella o cara» en Cuenta.", TONE_WARN);
            return;
        }
        if (!biometricAvailable()) {
            message("Este móvil no permite entrar con huella o cara. Usa tu correo.", TONE_WARN);
            return;
        }
        final Cipher cipher;
        try {
            cipher = biometricAccess.beginUnlock(expectedUserId);
        } catch (Exception invalidKey) {
            biometricAccess.disable(expectedUserId);
            render();
            message("La huella de esta cuenta ya no está vinculada. Entra con tu correo y actívala de nuevo.", TONE_WARN);
            return;
        }
        androidx.biometric.BiometricPrompt prompt = new androidx.biometric.BiometricPrompt(this,
            androidx.core.content.ContextCompat.getMainExecutor(this),
            new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                /**
                 * Atiende el evento authentication succeeded.
                 * Diseño lógico: (androidx.biometric.BiometricPrompt.AuthenticationResult result) → onAuthenticationSucceeded() → void; según doc/android_design.md.
                 */
                @Override public void onAuthenticationSucceeded(androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                    completeBiometricLogin(expectedUserId, result.getCryptoObject());
                }
                /**
                 * Atiende el evento authentication error.
                 * Diseño lógico: (int code, CharSequence error) → onAuthenticationError() → void; según doc/android_design.md.
                 */
                @Override public void onAuthenticationError(int code, CharSequence error) {
                    if (code != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_CANCELED
                        && code != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) message(error.toString(), TONE_ERROR);
                }
            });
        androidx.biometric.BiometricPrompt.PromptInfo promptInfo = new androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar en AERIQ")
            .setSubtitle("Usa tu huella o tu cara para entrar en " + biometricAccess.enrolledEmail())
            .setNegativeButtonText("Usar correo")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build();
        prompt.authenticate(promptInfo, new androidx.biometric.BiometricPrompt.CryptoObject(cipher));
    }

    /**
     * Gestiona complete biometric login.
     * Diseño lógico: (long expectedUserId, androidx.biometric.BiometricPrompt.CryptoObject crypto) → completeBiometricLogin() → void; según doc/android_design.md.
     */
    private void completeBiometricLogin(long expectedUserId, androidx.biometric.BiometricPrompt.CryptoObject crypto) {
        final BiometricAccessStore.Credential credential;
        try {
            credential = crypto == null ? null : biometricAccess.finishUnlock(expectedUserId, crypto.getCipher());
        } catch (Exception error) {
            biometricAccess.disable(expectedUserId);
            message("No se pudieron recuperar las credenciales. Entra con tu correo y vuelve a activar la huella.", TONE_ERROR);
            return;
        }
        if (credential == null) {
            biometricAccess.disable(expectedUserId);
            message("No se pudieron recuperar las credenciales. Entra con tu correo y vuelve a activar la huella.", TONE_ERROR);
            return;
        }
        auth.submit("login", loggedIn -> {
            if (!loggedIn.success) {
                String detail = loggedIn.message == null ? "" : loggedIn.message;
                String normalized = detail.toLowerCase(Locale.ROOT);
                if (normalized.contains("contraseña incorrectos") || normalized.contains("no está activada")) {
                    biometricAccess.disable(expectedUserId);
                    message("La contraseña guardada ya no es válida. Entra con correo y vuelve a activar la huella.", TONE_ERROR);
                } else {
                    message(detail.isEmpty()
                        ? "No se pudo iniciar sesión con la huella. Entra con tu correo y contraseña."
                        : detail, TONE_ERROR);
                }
                return;
            }
            if (auth.user == null || auth.user.id != expectedUserId) {
                biometricAccess.disable(expectedUserId);
                auth.clearLocalSession();
                message("La cuenta vinculada no coincide. Entra con tu correo y contraseña.", TONE_ERROR);
                return;
            }
            go(Screen.MAP);
            refreshWorker();
        }, "email", credential.email, "password", credential.password);
    }

    // Exportar CSV/PDF no se ofrece en la app (en la web el operador no descarga datos). Se conserva por si se habilita.
    /**
     * Diseño lógico: (List<StoredReading> rows, String title) → exportPdf() → void; según doc/android_design.md.
     */
    public void exportPdf(List<StoredReading> rows, String title) {
        exportRows = new ArrayList<>(rows);
        exportTitle = title;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "AERIQ-historial.pdf");
        startActivityForResult(intent, EXPORT_PDF);
    }

    /**
     * Escribe pdf.
     * Diseño lógico: (Uri uri, List<StoredReading> rows, String title) → writePdf() → void; según doc/android_design.md.
     */
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
                runOnUiThread(() -> message("PDF guardado.", TONE_OK));
            } catch (Exception error) {
                runOnUiThread(() -> message("No se pudo exportar: " + error.getMessage()));
            } finally {
                document.close();
            }
        });
    }

    /**
     * Gestiona clip.
     * Diseño lógico: (String value, int length) → clip() → String; según doc/android_design.md.
     */
    private static String clip(String value, int length) {
        return value == null ? "" : value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }

    /**
     * Gestiona export.
     * Diseño lógico: (List<StoredReading> rows) → export() → void; según doc/android_design.md.
     */
    public void export(List<StoredReading> rows) {
        exportRows = new ArrayList<>(rows);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "AERIQ-historial.csv");
        startActivityForResult(intent, EXPORT);
    }

    /**
     * Atiende el evento activity resultado.
     * Diseño lógico: (int request, int result, Intent data) → onActivityResult() → void; según doc/android_design.md.
     */
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
                    runOnUiThread(() -> message("CSV guardado.", TONE_OK));
                } catch (Exception error) {
                    runOnUiThread(() -> message("No se pudo exportar: " + error.getMessage()));
                }
            });
        }
    }

    /**
     * Abre.
     * Diseño lógico: (String url) → open() → void; según doc/android_design.md.
     */
    public void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException error) { message("No hay navegador disponible.", TONE_ERROR); }
    }

    /**
     * Gestiona signed in.
     * Diseño lógico: (sin parámetros) → signedIn() → boolean; según doc/android_design.md.
     */
    public boolean signedIn() { return auth.user != null; }

    /** Tono de los mensajes: verde = hecho, naranja = aviso, rojo = error, oscuro = información. */
    public static final int TONE_INFO = 0, TONE_OK = 1, TONE_WARN = 2, TONE_ERROR = 3;
    private TextView notice;
    private final Runnable hideNotice = () -> { if (notice != null) notice.animate().alpha(0).setDuration(200).withEndAction(() -> notice.setVisibility(View.GONE)).start(); };

    /**
     * Gestiona mensaje.
     * Diseño lógico: (String text) → message() → void; según doc/android_design.md.
     */
    public void message(String text) { message(text, TONE_INFO); }

    /**
     * Aviso abajo de la pantalla con el color de su tono (sustituye al Toast gris, que no admite colores).
     * Los errores se quedan más tiempo. Se anuncia también al lector de pantalla.
     */
    /**
     * Diseño lógico: (String text, int tone) → message() → void; según doc/android_design.md.
     */
    public void message(String text, int tone) {
        if (text == null || text.trim().isEmpty()) return;
        FrameLayout content = findViewById(android.R.id.content);
        if (notice == null) {
            notice = new TextView(this);
            notice.setTextColor(0xffffffff);
            notice.setTextSize(15);
            notice.setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14));
            notice.setElevation(ui.dp(8));
            notice.setVisibility(View.GONE);
            notice.setOnClickListener(view -> hideNotice.run());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            params.setMargins(ui.dp(16), 0, ui.dp(16), ui.dp(96));
            content.addView(notice, params);
        }
        int color = tone == TONE_OK ? 0xff0e6b5e : tone == TONE_WARN ? 0xffa85a00 : tone == TONE_ERROR ? 0xffb3261e : 0xff1c2522;
        notice.setBackground(ui.shape(color, 16, 0));
        notice.setText(text);
        notice.setContentDescription((tone == TONE_ERROR ? "Error: " : tone == TONE_WARN ? "Aviso: " : "") + text);
        notice.removeCallbacks(hideNotice);
        notice.animate().cancel();
        notice.bringToFront();
        notice.setAlpha(0);
        notice.setVisibility(View.VISIBLE);
        notice.animate().alpha(1).setDuration(180).start();
        notice.announceForAccessibility(notice.getContentDescription());
        notice.postDelayed(hideNotice, tone == TONE_ERROR ? 6000 : 3800);
    }

    /**
     * Atiende el evento start.
     * Diseño lógico: (sin parámetros) → onStart() → void; según doc/android_design.md.
     */
    @Override protected void onStart() {
        super.onStart();
        started = true;
        if ((ble.selectedAddress != null || ble.preferredAddress != null) && ble.hasScanPermissions() && ble.bluetoothEnabled()) ble.startScan();
        if (sharing) location.start();
        if (mapScreen != null) mapScreen.resume();
        if (!restoring) refreshNotices(); // al volver a la app se actualizan los avisos nuevos
        main.post(tick);
    }

    /**
     * Atiende el evento stop.
     * Diseño lógico: (sin parámetros) → onStop() → void; según doc/android_design.md.
     */
    @Override protected void onStop() {
        started = false;
        main.removeCallbacks(tick);
        ble.stopScan();
        location.stop();
        if (mapScreen != null) mapScreen.pause();
        super.onStop();
    }

    /**
     * Atiende el evento destroy.
     * Diseño lógico: (sin parámetros) → onDestroy() → void; según doc/android_design.md.
     */
    @Override protected void onDestroy() {
        if (mapScreen != null) mapScreen.close();
        auth.close();
        field.close();
        maps.shutdownNow();
        super.onDestroy();
    }

    /**
     * Atiende el evento back pressed.
     * Diseño lógico: (sin parámetros) → onBackPressed() → void; según doc/android_design.md.
     */
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        navigateBack();
    }

    private SplashView splash;
    private SpotlightTour tutorial;

    /** Animación de apertura (cada vez que se abre la app) y, si nunca se ha visto, el tutorial. */
    /**
     * Diseño lógico: (sin parámetros) → showIntro() → void; según doc/android_design.md.
     */
    private void showIntro() {
        FrameLayout content = findViewById(android.R.id.content);
        splash = new SplashView(this);
        content.addView(splash, new FrameLayout.LayoutParams(-1, -1));
        splash.start(() -> {
            content.removeView(splash);
            splash = null;
            if (!prefs.tutorialSeen()) showTutorial();
        });
    }

    /**
     * Tutorial de la app (HU33): tour con foco sobre la app real. Se hace en el mapa, que existe con y sin cuenta.
     * Al terminarlo o saltarlo queda marcado como visto y no vuelve a salir solo (se puede repetir desde Aprende o
     * desde Cuenta → Ayuda y contacto).
     */
    /**
     * Diseño lógico: (sin parámetros) → showTutorial() → void; según doc/android_design.md.
     */
    public void showTutorial() {
        if (tutorial != null || splash != null) return;
        if (restoring) { main.postDelayed(this::showTutorial, 150); return; } // aún se está abriendo la sesión
        if (auth.user != null && auth.user.mustChangePassword) return;       // primero, la contraseña nueva
        if (current != Screen.MAP) go(Screen.MAP);
        FrameLayout content = findViewById(android.R.id.content);
        tutorial = new SpotlightTour(this, ui, tourSteps(), tag -> getWindow().getDecorView().findViewWithTag(tag), () -> {
            prefs.setTutorialSeen();
            content.removeView(tutorial);
            tutorial = null;
        });
        content.addView(tutorial, new FrameLayout.LayoutParams(-1, -1));
        tutorial.start();
    }

    /** Pasos del tour. Los de pestañas dependen de la cuenta: quien mide ve «Mi sensor»; quien gestiona, «Panel». */
    /**
     * Diseño lógico: (sin parámetros) → tourSteps() → List<SpotlightTour.Step>; según doc/android_design.md.
     */
    private List<SpotlightTour.Step> tourSteps() {
        boolean signedIn = auth.user != null;
        List<SpotlightTour.Step> steps = new ArrayList<>();
        steps.add(new SpotlightTour.Step("tour:search", "Busca cualquier sitio",
            "Escribe una ciudad o una dirección para ver cómo está el ozono en esa zona."));
        steps.add(new SpotlightTour.Step("tour:legend", "Qué significan los colores",
            "Cada punto es una medición. Verde es ozono bajo, naranja moderado y rojo alto. En gris hay pocas mediciones "
                + "para dar un nivel, y sin color no hay datos (no significa aire limpio)."));
        steps.add(new SpotlightTour.Step("tour:layers", "Capas del mapa",
            "Elige el periodo (últimas 24 h, 7 días o todo) y qué ver: zonas, puntos o el radio de cada zona."));
        steps.add(new SpotlightTour.Step("tour:locate", "Tu ubicación",
            "Centra el mapa donde estás. La app te pedirá permiso de ubicación solo cuando toques este botón."));
        steps.add(new SpotlightTour.Step("tour:tab:" + Screen.SENSOR.name(), "Tu Orb",
            "Si mides con un Orb, enciéndelo y acércalo al móvil: la app lo busca por Bluetooth (y te pide ese permiso "
                + "entonces). Con un solo Orb asignado lo elige sola; con varios, lo tocas en «Vincular». Aquí ves el ozono "
                + "en directo." + (signedIn ? "" : " Los Orb los asigna tu organización.")));
        steps.add(new SpotlightTour.Step("tour:tab:" + Screen.PANEL.name(), "Tu panel",
            "Lo que gestionas desde la web y, si eres admin de campaña, «Asignar un Orb con el móvil»."));
        steps.add(new SpotlightTour.Step("tour:tab:" + Screen.LEARN.name(), "Aprende",
            "Qué es el ozono, cómo afecta a la salud y qué dice la normativa. Desde aquí puedes repetir este tutorial."));
        steps.add(new SpotlightTour.Step("tour:tab:" + Screen.ACCOUNT.name(), signedIn ? "Tu cuenta" : "Entra con tu cuenta",
            signedIn ? "Tus datos, los avisos con tu admin o tu equipo, el acceso con huella o cara y la ayuda."
                : "Las cuentas las da tu organización con los kits de AERIQ. Si te la han dado, entra aquí con tu correo. "
                + "Sin cuenta puedes usar el mapa y Aprende."));
        return steps;
    }

    /**
     * Gestiona navigate back.
     * Diseño lógico: (sin parámetros) → navigateBack() → void; según doc/android_design.md.
     */
    private void navigateBack() {
        if (splash != null) return; // la animación dura menos de 2 s y termina sola
        if (tutorial != null) { tutorial.back(); return; }
        switch (current) {
            case FORGOT: go(Screen.LOGIN); break;
            case RESET: go(Screen.FORGOT); break;
            case CONFIG: case HISTORY: case LINK: go(Screen.SENSOR); break;
            case ASSIGN: go(Screen.PANEL); break;
            case EDIT_PROFILE: case CHANGE_PASSWORD: case SERVICES: case NOTICES: go(Screen.ACCOUNT); break;
            case NOTICE: case NOTICE_NEW: go(Screen.NOTICES); break;
            case MAP: case LOGIN: finish(); break;
            default: go(Screen.MAP);
        }
    }
}
