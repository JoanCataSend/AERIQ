package com.aeriq.app.ui;

import android.widget.*;
import com.aeriq.app.MainActivity;
import com.aeriq.app.MainActivity.Screen;
import com.aeriq.app.R;
import com.aeriq.app.ble.AeriqBleController;
import com.aeriq.app.data.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.aeriq.app.ui.Views.*;

public final class WorkScreens {
    private final MainActivity activity;
    private final Views ui;

    public WorkScreens(MainActivity activity, Views ui) {
        this.activity = activity;
        this.ui = ui;
    }

    public void sensor(LinearLayout body, AeriqBleController ble, List<OrbAssignment> assignments,
                       String assignmentStatus, String serverStatus, boolean sending, List<StoredReading> history) {
        OrbAssignment selected = null;
        for (OrbAssignment orb : assignments) if (orb.address.equalsIgnoreCase(ble.selectedAddress)) selected = orb;
        LinearLayout device = ui.card(body);
        ui.label(device, selected == null ? "Tu sensor Orb" : selected.name, 19, true, INK);
        ui.note(device, selected == null ? "Sin Orb seleccionado" : selected.campaign);
        TextView reception = ui.label(device, ble.status, 13, true, GREEN);
        LinearLayout current = ui.card(body);
        ui.label(current, "O₃ ahora" + (selected != null && selected.simulated ? " · SIMULADO" : ""), 15, true, GREEN);
        TextView ozone = ui.label(current, "— ppb", 46, true, INK);
        List<Double> values = new ArrayList<>();
        List<StoredReading> sorted = new ArrayList<>(history);
        sorted.sort(Comparator.comparing(reading -> reading.date));
        if (selected != null) {
            for (StoredReading reading : sorted) {
                if (reading.type == 14 && reading.deviceId == selected.id && reading.simulated == selected.simulated) values.add(reading.value);
            }
        }
        graph(current, values);
        ui.outline(current, "Ver historial y exportar →", () -> activity.go(Screen.HISTORY));
        LinearLayout health = ui.card(body);
        ui.label(health, "Recepción BLE y servidor", 17, true, INK);
        TextView server = ui.label(health, serverStatus, 13, false, MUTED);
        LinearLayout campaign = ui.card(body);
        ui.label(campaign, "Tu campaña", 19, true, INK);
        ui.note(campaign, "Lecturas guardadas: " + values.size());
        ui.note(campaign, "Media disponible: " + average(values));
        TextView temperature = ui.label(campaign, "Temperatura BLE: — · Batería: no transmitida", 13, false, MUTED);
        LinearLayout assigned = ui.card(body);
        ui.label(assigned, "Orb asignados", 18, true, INK);
        ui.note(assigned, assignmentStatus);
        for (OrbAssignment orb : assignments) ui.note(assigned, orb.name + " · " + orb.campaign);
        ui.button(assigned, "Buscar o vincular Orb", () -> activity.go(Screen.LINK));
        ui.outline(assigned, "Actualizar asignaciones", activity::refreshAssignments);
        sharing(body, sending, selected != null);
        activity.updateLive(() -> {
            long age = (System.currentTimeMillis() - ble.lastAdvertisementAt) / 1000;
            reception.setText(ble.scanning && ble.lastAdvertisementAt > 0
                ? (age <= 15 ? "Anuncio recibido hace " : "Sin anuncios desde hace ") + age + " s" : ble.status);
            ozone.setText(ble.lastO3 == null ? "— ppb" : ble.lastO3.reading.value + " ppb");
            server.setText(activity.serverStatus());
            temperature.setText("Temperatura BLE: " + (ble.lastTemperature == null ? "—" : ble.lastTemperature.reading.value)
                + " °C · Batería: no transmitida");
        });
    }

    public void link(LinearLayout body, AeriqBleController ble, List<OrbAssignment> assignments) {
        ImageView image = ui.image(R.drawable.aeriq_orb_hero, 358, 166);
        body.addView(image, new LinearLayout.LayoutParams(-1, ui.dp(166)));
        ui.label(body, "Conecta tu sensor", 27, true, INK);
        ui.note(body, "Enciende el Orb y acércalo al móvil. AERIQ lo encontrará por Bluetooth.");
        LinearLayout setup = ui.card(body);
        ui.label(setup, "Bluetooth", 17, true, INK);
        TextView status = ui.label(setup, ble.status, 13, false, MUTED);
        Button scan = ui.button(setup, "Buscar sensores", activity::toggleScan);
        ui.note(setup, "Ubicación opcional para situar las mediciones.");
        ui.label(body, "Sensores cercanos", 18, true, INK);
        LinearLayout devices = ui.column();
        ui.add(body, devices);
        ui.note(body, "Vincular selecciona el emisor iBeacon asignado por tu organización.");
        final String[] previous = {null};
        activity.updateLive(() -> {
            status.setText(ble.status);
            scan.setText(ble.scanning ? "Detener búsqueda" : "Buscar sensores");
            Map<String, AeriqBleController.FoundDevice> visible = new LinkedHashMap<>();
            for (AeriqBleController.FoundDevice device : ble.devices()) {
                if (System.currentTimeMillis() - device.lastSeen > 15_000) continue;
                for (OrbAssignment assignment : assignments) {
                    if (assignment.address.equalsIgnoreCase(device.address)) visible.put(device.address, device);
                }
            }
            StringBuilder signature = new StringBuilder();
            for (AeriqBleController.FoundDevice device : visible.values()) signature.append(device.address).append(device.rssi);
            if (signature.toString().equals(previous[0])) return;
            previous[0] = signature.toString();
            devices.removeAllViews();
            if (visible.isEmpty()) ui.note(devices, "No se han detectado Orb asignados.");
            for (AeriqBleController.FoundDevice device : visible.values()) {
                LinearLayout card = ui.card(devices);
                ui.label(card, device.name, 16, true, INK);
                ui.note(card, device.address + " · " + device.rssi + " dBm");
                ui.button(card, "Vincular", () -> activity.selectOrb(device.address));
            }
        });
    }

    public void history(LinearLayout body, List<StoredReading> history, String status) {
        Spinner period = new Spinner(activity);
        ArrayAdapter<String> options = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"Día", "Semana", "Mes", "Año"});
        period.setAdapter(options);
        period.setSelection(1);
        ui.add(body, period);
        ui.note(body, status);
        ui.outline(body, "Actualizar", activity::refreshHistory);
        LinearLayout results = ui.column();
        ui.add(body, results);
        period.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int index, long id) {
                int days = new int[]{1, 7, 30, 365}[index];
                showHistory(results, history, days);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void showHistory(LinearLayout results, List<StoredReading> history, int days) {
        results.removeAllViews();
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        String cutoff = date.format(new Date(System.currentTimeMillis() - days * 86400000L));
        List<StoredReading> rows = new ArrayList<>();
        Map<Boolean, List<Double>> groups = new LinkedHashMap<>();
        for (StoredReading reading : history) if (reading.type == 14 && reading.date.compareTo(cutoff) >= 0) rows.add(reading);
        rows.sort(Comparator.comparing(reading -> reading.date));
        for (StoredReading reading : rows) groups.computeIfAbsent(reading.simulated, key -> new ArrayList<>()).add(reading.value);
        ui.label(results, "O₃ · últimos " + days + " días", 19, true, INK);
        if (rows.isEmpty()) ui.note(results, "No hay mediciones en este periodo.");
        for (Map.Entry<Boolean, List<Double>> group : groups.entrySet()) {
            List<Double> values = group.getValue();
            LinearLayout card = ui.card(results);
            ui.label(card, group.getKey() ? "Serie simulada" : "Serie real", 17, true, INK);
            graph(card, values);
            ui.note(card, "Media: " + average(values) + " · Mínimo: " + Collections.min(values) + " · Máximo: " + Collections.max(values));
        }
        ui.button(results, "Exportar mediciones CSV", () -> activity.export(rows)).setEnabled(!rows.isEmpty());
        for (int index = rows.size() - 1; index >= Math.max(0, rows.size() - 80); index--) {
            StoredReading reading = rows.get(index);
            LinearLayout card = ui.card(results);
            ui.label(card, reading.device + " · " + reading.value + " " + reading.unit, 15, true, INK);
            ui.note(card, reading.date + " UTC · " + reading.campaign + " · " + (reading.simulated ? "Simulada" : "Real"));
            ui.note(card, reading.latitude == null ? "Sin GPS autorizado" : reading.latitude + ", " + reading.longitude);
        }
    }

    public void settings(LinearLayout body, OrbAssignment orb, boolean sending) {
        LinearLayout card = ui.card(body);
        ui.label(card, "Tu dispositivo", 19, true, INK);
        ui.note(card, "Nombre: " + (orb == null ? "Sin selección" : orb.name));
        ui.note(card, "Campaña: " + (orb == null ? "Sin asignación" : orb.campaign));
        ui.note(card, "Recepción: anuncios iBeacon · Batería: no transmitida");
        ui.note(card, "El administrador gestiona el nombre, el alta y la asignación desde la web.");
        sharing(body, sending, orb != null);
        ui.outline(body, "Buscar / cambiar Orb", () -> activity.go(Screen.LINK));
        ui.outline(body, "Deseleccionar este Orb", activity::unlink);
    }

    private void sharing(LinearLayout body, boolean sending, boolean enabled) {
        LinearLayout card = ui.card(body);
        Switch toggle = new Switch(activity);
        toggle.setText("Enviar lecturas a la campaña");
        toggle.setEnabled(enabled);
        toggle.setChecked(sending);
        toggle.setOnCheckedChangeListener((button, checked) -> activity.setSharing(checked));
        ui.add(card, toggle);
        ui.note(card, "GPS opcional y solo en primer plano. La publicación se autoriza en la campaña.");
    }

    public void account(LinearLayout body, AeriqAuthManager.User user, OrbAssignment orb, boolean sharing) {
        LinearLayout profile = ui.card(body);
        ui.label(profile, user.fullName(), 19, true, INK);
        ui.note(profile, user.email);
        ui.note(profile, user.organization.isEmpty() ? "Consulta pública" : user.organization);
        ui.label(profile, user.role, 13, true, GREEN);
        LinearLayout data = ui.card(body);
        menu(data, "Datos personales", Screen.EDIT_PROFILE);
        menu(data, "Mi sensor · " + (orb == null ? "Sin Orb seleccionado" : orb.name), Screen.SENSOR);
        LinearLayout security = ui.card(body);
        menu(security, "Cambiar contraseña", Screen.CHANGE_PASSWORD);
        ui.outline(security, "Acceso biométrico · mejora prevista", () -> activity.message("La sesión utiliza Android Keystore."));
        LinearLayout privacy = ui.card(body);
        menu(privacy, "Envío de campaña · " + (sharing ? "Activado" : "Desactivado"), Screen.CONFIG);
        menu(privacy, "Historial y exportación", Screen.HISTORY);
        menu(privacy, "Privacidad y ubicación", Screen.CONFIG);
        ui.add(body, ui.action("Soluciones para organizaciones", DARK, WHITE, () -> activity.go(Screen.SERVICES)));
        LinearLayout organization = ui.card(body);
        ui.outline(organization, "Panel de organización ↗", () -> activity.open(ApiRoutes.BASE + "portal.html"));
        menu(organization, "Ayuda y contacto", Screen.SERVICES);
        ui.add(ui.card(body), ui.action("Cerrar sesión", WHITE, RED, activity::logout));
        ui.note(body, "AERIQ · Orb · Desarrollado por Código Norte");
    }

    private void menu(LinearLayout card, String label, Screen destination) {
        ui.outline(card, label, () -> activity.go(destination));
    }

    public void services(LinearLayout body) {
        section(body, "Tu campaña, a tu medida.", "Código Norte desarrolla AERIQ y el sensor Orb. La consulta del mapa público es gratuita.");
        service(body, "Kits para administraciones", "Para campañas municipales y estudios del territorio.", "administracion");
        service(body, "Kits para empresas", "Sensores, trabajadores y campañas de tu organización.", "empresa");
        service(body, "Estudios ambientales", "Código Norte realiza el estudio sin que tengas que comprar sensores.", "estudio");
        ui.note(body, "Los presupuestos se preparan al revisar las necesidades.");
    }

    private void service(LinearLayout body, String title, String description, String kind) {
        LinearLayout card = section(body, title, description);
        ui.button(card, "Solicitar información ↗", () -> activity.open(ApiRoutes.BASE + "solicitar.html?service=" + kind));
    }

    public void learn(LinearLayout body) {
        section(body, "Un aire limpio empieza por conocerlo.", "Qué contamina, cómo afecta a tu salud y qué dice la normativa.");
        section(body, "¿Qué es el ozono troposférico?", "Se forma cerca del suelo cuando la luz del sol reacciona con contaminantes del tráfico y la industria.");
        section(body, "Efectos en la salud", "• Irritación de ojos, nariz y garganta\n• Tos y dificultad para respirar\n• Puede empeorar el asma.");
        section(body, "Cómo protegerte", "• Consulta el mapa antes de hacer deporte\n• Evita el ejercicio intenso en las horas de más sol.");
        section(body, "Normativa", "Consulta los valores vigentes en las fuentes oficiales.");
        section(body, "Otros contaminantes", "Ozono (O₃)\nDióxido de nitrógeno (NO₂)\nPartículas (PM10 y PM2,5)\nMonóxido de carbono (CO)");
        ui.note(body, "Las mediciones complementan las estaciones oficiales de vigilancia ambiental.");
    }

    public void compare(LinearLayout body, ExecutorService worker) {
        section(body, "Conoce las zonas medidas", "Compara datos publicados. Las zonas sin mediciones no reciben un valor estimado.");
        Switch simulated = new Switch(activity);
        simulated.setText("Mediciones simuladas");
        simulated.setChecked(true);
        ui.add(body, simulated);
        LinearLayout results = ui.column();
        ui.add(body, results);
        int[] request = {0};
        Runnable load = () -> {
            int generation = ++request[0];
            boolean demo = simulated.isChecked();
            results.removeAllViews();
            ui.note(results, "Consultando mediciones públicas…");
            worker.execute(() -> {
                try {
                    List<PublicReadings.Reading> rows = PublicReadings.fetch(demo).rows;
                    activity.runOnUiThread(() -> {
                        if (!results.isAttachedToWindow() || generation != request[0]) return;
                        results.removeAllViews();
                        Map<String, List<PublicReadings.Reading>> zones = new LinkedHashMap<>();
                        rows.sort(Comparator.comparing(reading -> reading.date));
                        for (PublicReadings.Reading reading : rows) {
                            zones.computeIfAbsent(reading.zone + " · " + reading.unit, key -> new ArrayList<>()).add(reading);
                        }
                        ui.note(results, rows.size() + " mediciones públicas");
                        for (Map.Entry<String, List<PublicReadings.Reading>> zone : zones.entrySet()) {
                            List<Double> values = new ArrayList<>();
                            for (PublicReadings.Reading reading : zone.getValue()) values.add(reading.value);
                            LinearLayout card = ui.card(results);
                            ui.label(card, zone.getKey(), 17, true, INK);
                            graph(card, values);
                            ui.note(card, "Mediciones: " + values.size() + " · Media: " + average(values));
                            ui.note(card, "Mínimo / máximo: " + Collections.min(values) + " / " + Collections.max(values));
                        }
                    });
                } catch (Exception error) {
                    activity.runOnUiThread(() -> {
                        if (!results.isAttachedToWindow() || generation != request[0]) return;
                        results.removeAllViews();
                        ui.note(results, "No se pudo consultar el mapa: " + error.getMessage());
                    });
                }
            });
        };
        simulated.setOnCheckedChangeListener((button, checked) -> load.run());
        load.run();
    }

    private LinearLayout section(LinearLayout body, String title, String description) {
        LinearLayout card = ui.card(body);
        ui.label(card, title, 19, true, INK);
        ui.note(card, description);
        return card;
    }

    private void graph(LinearLayout body, List<Double> values) {
        if (values.isEmpty()) ui.note(body, "La gráfica aparecerá al guardar mediciones.");
        else body.addView(new MeasurementGraph(activity, values), new LinearLayout.LayoutParams(-1, ui.dp(145)));
    }

    private String average(List<Double> values) {
        if (values.isEmpty()) return "—";
        double total = 0;
        for (double value : values) total += value;
        return String.format(Locale.getDefault(), "%.1f ppb", total / values.size());
    }
}
