package com.aeriq.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.aeriq.app.ble.*;
import com.aeriq.app.data.*;
import com.aeriq.app.ui.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    public enum Screen {
        LOGIN, REGISTER, VERIFY, FORGOT, RESET, MAP, LEARN, SENSOR, LINK, HISTORY,
        CONFIG, COMPARE, SERVICES, ACCOUNT, EDIT_PROFILE, CHANGE_PASSWORD
    }

    private static final EnumSet<Screen> PUBLIC = EnumSet.of(
        Screen.LOGIN, Screen.REGISTER, Screen.VERIFY, Screen.FORGOT, Screen.RESET, Screen.MAP, Screen.LEARN);
    private static final int BLE_PERMISSION = 1, ENABLE_BLUETOOTH = 2, GPS_CAPTURE = 3, GPS_MAP = 4, EXPORT = 5;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService maps = Executors.newSingleThreadExecutor();
    private Screen current = Screen.LOGIN;
    private Views ui;
    private AeriqAuthManager auth;
    private AeriqFieldClient field;
    private AeriqBleController ble;
    private ForegroundCaptureLocation location;
    private PublicMapScreen mapScreen;
    private LinearLayout root, body, header, navigation;
    private ScrollView scroll;
    private boolean sharing, started, restoring = true;
    private List<OrbAssignment> assignments = Collections.emptyList();
    private List<StoredReading> history = Collections.emptyList();
    private String assignmentStatus = "Esperando cuenta autorizada";
    private String historyStatus = "Consulta el historial de tu campaña";
    private String serverStatus = "Envío desactivado";
    private List<StoredReading> exportRows;
    private Runnable updateLive;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (updateLive != null) updateLive.run();
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
        field = new AeriqFieldClient(auth::sessionToken);
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
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        ui.add(body, new ProgressBar(this));
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
            current = destination;
            render();
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
        scroll.scrollTo(0, 0);
        boolean form = current.ordinal() <= Screen.RESET.ordinal()
            || current == Screen.EDIT_PROFILE || current == Screen.CHANGE_PASSWORD;
        if (form) {
            new AuthScreens(this, ui, auth).show(current, body);
            return;
        }
        showNavigation();
        WorkScreens work = new WorkScreens(this, ui);
        switch (current) {
            case MAP:
                title("Mapa de ozono", null);
                mapScreen = new PublicMapScreen(this, ui, body, maps);
                if (started) mapScreen.resume();
                break;
            case LEARN:
                title("Aprende", null);
                work.learn(body);
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
                work.history(body, history, historyStatus);
                break;
            case CONFIG:
                title("Configurar sensor", Screen.SENSOR);
                work.settings(body, selected(), sharing);
                break;
            case COMPARE:
                title("Comparar zonas", Screen.MAP);
                work.compare(body, maps);
                break;
            case SERVICES:
                title("Soluciones AERIQ", Screen.ACCOUNT);
                work.services(body);
                break;
            case ACCOUNT:
                title("Mi cuenta", null);
                work.account(body, auth.user, selected(), sharing);
                break;
            default:
                throw new IllegalStateException(current.name());
        }
    }

    public void title(String title, Screen back) {
        View left = back == null ? ui.image(R.drawable.aeriq_mark, 28, 28)
            : ui.action("‹", Views.BACKGROUND, Views.INK, () -> go(back));
        header.addView(left, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        TextView label = ui.text(title, 17, true, Views.INK);
        label.setGravity(Gravity.CENTER);
        ui.weighted(header, label);
        View right = current == Screen.SENSOR
            ? ui.action("⚙", Views.BACKGROUND, Views.INK, () -> go(Screen.CONFIG))
            : new View(this);
        header.addView(right, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
    }

    private void showNavigation() {
        Screen[] routes = {Screen.MAP, Screen.SENSOR, Screen.LEARN, Screen.ACCOUNT};
        String[] labels = {"Mapa", "Mi sensor", "Aprende", "Cuenta"};
        int[] icons = {R.drawable.figma_nav_map, R.drawable.figma_nav_chip, R.drawable.figma_nav_book, R.drawable.figma_nav_user};
        Screen active = current;
        if (EnumSet.of(Screen.LINK, Screen.CONFIG, Screen.HISTORY).contains(current)) active = Screen.SENSOR;
        if (current == Screen.COMPARE) active = Screen.MAP;
        if (current == Screen.SERVICES) active = Screen.ACCOUNT;
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
            if (result.success && auth.user != null && !auth.user.mustChangePassword
                && Arrays.asList("login", "verify", "reset-password", "change-password").contains(action)) {
                refreshWorker();
            }
            if (auth.user == null && !PUBLIC.contains(current)) {
                clearWorker();
                go(Screen.LOGIN);
                message(result.message);
                return;
            }
            done.accept(result);
        }, fields);
    }

    public void logout() {
        if (auth.busy) {
            message("Espera a que termine la operación.");
            return;
        }
        auth.logout();
        clearWorker();
        go(Screen.LOGIN);
        message("Has cerrado sesión.");
    }

    private void clearWorker() {
        setSharing(false);
        ble.stopScan();
        ble.unlink();
        history = Collections.emptyList();
        assignments = Collections.emptyList();
    }

    private boolean workerAccount() {
        return auth.user != null && "trabajador".equals(auth.user.role) && !auth.user.mustChangePassword;
    }

    private void refreshWorker() {
        refreshAssignments();
        refreshHistory();
    }

    public void refreshAssignments() {
        if (!workerAccount()) {
            assignmentStatus = "Accede con una cuenta de trabajador";
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
            if (current == Screen.SENSOR || current == Screen.LINK) render();
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
        field.upload(reading, assignment, location.current(), status -> serverStatus = status);
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
        if (assigned(address) == null) return;
        if (!address.equalsIgnoreCase(ble.selectedAddress)) setSharing(false);
        ble.select(address);
        go(Screen.SENSOR);
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
    public void updateLive(Runnable action) { updateLive = action; action.run(); }

    public void locateMap() { requestPermissions(locationPermissions(), GPS_MAP); }

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

    public void export(List<StoredReading> rows) {
        exportRows = new ArrayList<>(rows);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "AERIQ-historial.csv");
        startActivityForResult(intent, EXPORT);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ENABLE_BLUETOOTH && ble.bluetoothEnabled()) ble.startScan();
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
            case VERIFY: case REGISTER: case FORGOT: go(Screen.LOGIN); break;
            case RESET: go(Screen.FORGOT); break;
            case CONFIG: case HISTORY: case LINK: go(Screen.SENSOR); break;
            case EDIT_PROFILE: case CHANGE_PASSWORD: case SERVICES: go(Screen.ACCOUNT); break;
            case MAP: case LOGIN: finish(); break;
            default: go(Screen.MAP);
        }
    }
}
