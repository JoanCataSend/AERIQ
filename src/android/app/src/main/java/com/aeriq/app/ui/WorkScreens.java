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
        ui.label(current, "O₃ ahora", 17, true, INK);
        if (selected != null && selected.simulated) ui.label(current, "SIMULADO", 11, true, GREEN);
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
        ui.label(current, "Histórico guardado · fechas UTC", 11, false, MUTED);
        ui.outline(current, "Ver historial y exportar →", () -> activity.go(Screen.HISTORY));
        LinearLayout health = ui.row();
        LinearLayout receptionColumn = ui.column(), serverColumn = ui.column();
        ui.weighted(health, receptionColumn);
        health.addView(new android.view.View(activity), new LinearLayout.LayoutParams(ui.dp(10), 1));
        ui.weighted(health, serverColumn);
        LinearLayout receptionCard = section(receptionColumn, "Recepción BLE", ble.status);
        TextView healthStatus = (TextView) receptionCard.getChildAt(1);
        LinearLayout serverCard = section(serverColumn, "Servidor", serverStatus);
        TextView server = (TextView) serverCard.getChildAt(1);
        ui.add(body, health);
        LinearLayout campaign = ui.card(body);
        ui.label(campaign, "Tu campaña", 19, true, INK);
        ui.info(campaign, "Lecturas guardadas", Integer.toString(values.size()));
        ui.info(campaign, "Media disponible", average(values));
        TextView temperature = ui.label(campaign, "Temperatura BLE: — · Batería: no transmitida", 13, false, MUTED);
        LinearLayout assigned = ui.card(body);
        ui.label(assigned, "Orb asignados", 18, true, INK);
        ui.note(assigned, assignmentStatus);
        for (OrbAssignment orb : assignments) ui.note(assigned, orb.name + " · " + orb.campaign);
        ui.button(assigned, "Buscar o asignar sensor", () -> activity.go(Screen.LINK));
        ui.outline(assigned, "Actualizar asignaciones", activity::refreshAssignments);
        sharing(body, sending, selected != null, "Enviar a la campaña");
        activity.updateLive(() -> {
            long age = (System.currentTimeMillis() - ble.lastAdvertisementAt) / 1000;
            reception.setText(ble.scanning && ble.lastAdvertisementAt > 0
                ? (age <= 15 ? "Anuncio recibido hace " : "Sin anuncios desde hace ") + age + " s" : ble.status);
            healthStatus.setText(reception.getText());
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
        ui.note(setup, "Necesario para leer el sensor");
        Switch scan = new Switch(activity);
        scan.setContentDescription("Buscar sensores Bluetooth");
        scan.setOnClickListener(view -> activity.toggleScan());
        ui.add(setup, scan);
        ui.info(setup, "Ubicación", "Solo con permiso");
        ui.label(body, "Sensores cercanos", 18, true, INK);
        TextView status = ui.label(body, ble.status, 12, false, MUTED);
        LinearLayout devices = ui.column();
        ui.add(body, devices);
        section(body, "¿Aún no tienes sensor?", "El mapa público sigue siendo gratis.");
        ui.note(body, "Puedes asignar un sensor libre a tu cuenta. Las nuevas mediciones se guardan en una campaña privada; los sensores ya asignados conservan su campaña.");
        final String[] previous = {null};
        activity.updateLive(() -> {
            status.setText(ble.status);
            scan.setChecked(ble.scanning);
            Map<String, AeriqBleController.FoundDevice> visible = new LinkedHashMap<>();
            for (AeriqBleController.FoundDevice device : ble.devices()) {
                if (System.currentTimeMillis() - device.lastSeen > 15_000) continue;
                visible.put(device.address, device);
            }
            StringBuilder signature = new StringBuilder();
            signature.append(ble.selectedAddress).append(ble.scanning);
            for (OrbAssignment assignment : assignments) signature.append(assignment.address).append(assignment.name);
            for (AeriqBleController.FoundDevice device : visible.values())
                signature.append(device.address).append(device.rssi >= -65 ? 2 : device.rssi >= -80 ? 1 : 0).append(device.diagnostic);
            if (signature.toString().equals(previous[0])) return;
            previous[0] = signature.toString();
            devices.removeAllViews();
            if (visible.isEmpty()) ui.note(devices, ble.scanning
                ? "Buscando tu Orb… Déjalo encendido cerca del móvil."
                : "Activa la búsqueda para ver los sensores cercanos.");
            for (AeriqBleController.FoundDevice device : visible.values()) {
                LinearLayout card = ui.card(devices);
                OrbAssignment match = null;
                for (OrbAssignment assignment : assignments)
                    if (assignment.address.equalsIgnoreCase(device.address)) match = assignment;
                final OrbAssignment assigned = match;
                boolean selected = device.address.equalsIgnoreCase(ble.selectedAddress);
                LinearLayout identity = ui.row();
                ImageView orb = ui.image(R.drawable.aeriq_orb_hero, 72, 72);
                identity.addView(orb, new LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)));
                LinearLayout details = ui.column();
                details.setPadding(ui.dp(14), 0, 0, 0);
                ui.weighted(identity, details);
                ui.label(details, assigned == null ? "Sensor Orb" : assigned.name, 20, true, INK);
                ui.label(details, selected ? "✓ Seleccionado" : assigned != null ? "✓ Asignado a tu cuenta" : "Detectado por Bluetooth", 12, true, GREEN);
                ui.add(card, identity);
                ui.note(card, device.address);
                ui.info(card, "Señal", device.rssi >= -65 ? "Buena · cerca de ti" : device.rssi >= -80 ? "Media" : "Débil · acerca el sensor");
                if (device.diagnostic >= 0) {
                    ui.label(card, "Necesita revisar la lectura", 13, true, RED);
                    ui.note(card, "Puedes asignarlo igualmente. Estado 0x" + Integer.toHexString(device.diagnostic) + "; comprueba el monitor serie y el cableado.");
                }
                if (assigned != null) ui.note(card, "Campaña: " + assigned.campaign);
                else ui.note(card, "Guárdalo en tu cuenta para empezar a registrar tus mediciones.");
                Button choose = ui.button(card, selected ? "Sensor seleccionado" : assigned != null ? "Usar este sensor" : "Asignar a mi cuenta", () -> {
                    if (assigned != null) { activity.selectOrb(device.address); return; }
                    EditText name = new EditText(activity);
                    name.setSingleLine(true);
                    name.setText("Mi Orb · " + device.address.substring(12));
                    new android.app.AlertDialog.Builder(activity).setTitle("Asigna tu sensor")
                        .setMessage("Ponle un nombre. La asignación quedará guardada en tu cuenta y las nuevas lecturas serán privadas.")
                        .setView(name).setNegativeButton("Cancelar", null)
                        .setPositiveButton("Asignar sensor", (dialog, which) -> {
                            String label = name.getText().toString().trim();
                            if (label.isEmpty() || label.length() > 80) { activity.message("Usa un nombre de entre 1 y 80 caracteres."); return; }
                            activity.assignSensor(device.address, label);
                        }).show();
                });
                choose.setEnabled(!selected);
            }
        });
    }

    public void history(LinearLayout body, List<StoredReading> history, String status) {
        LinearLayout results = ui.column();
        ui.segments(body, new String[]{"Día", "Semana", "Mes", "Año"}, 1,
            index -> showHistory(results, history, new int[]{1, 7, 30, 365}[index]));
        ui.add(body, results);
        showHistory(results, history, 7);
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
        LinearLayout summary = ui.card(results);
        ui.label(summary, "O₃ · últimos " + days + " días", 19, true, INK);
        ui.note(summary, "UTC · Máximo 1.000 lecturas recientes de tus asignaciones.");
        if (rows.isEmpty()) ui.note(results, "No hay mediciones en este periodo.");
        for (Map.Entry<Boolean, List<Double>> group : groups.entrySet()) {
            List<Double> values = group.getValue();
            LinearLayout card = summary;
            ui.label(card, group.getKey() ? "Serie simulada" : "Serie real", 17, true, INK);
            card.addView(new MeasurementGraph(activity, values, true), new LinearLayout.LayoutParams(-1, ui.dp(145)));
            ui.info(card, "Media de esta serie", average(values));
        }
        if (!rows.isEmpty()) {
            LinearLayout extremes = ui.row();
            LinearLayout low = ui.column(), high = ui.column();
            ui.weighted(extremes, low);
            extremes.addView(new android.view.View(activity), new LinearLayout.LayoutParams(ui.dp(10), 1));
            ui.weighted(extremes, high);
            section(low, Collections.min(rows.stream().map(reading -> reading.value)
                .collect(java.util.stream.Collectors.toList())) + " ppb", "Mínimo");
            section(high, Collections.max(rows.stream().map(reading -> reading.value)
                .collect(java.util.stream.Collectors.toList())) + " ppb", "Máximo");
            ui.add(results, extremes);
        }
        LinearLayout count = ui.card(results);
        ui.info(count, "Mediciones", Integer.toString(rows.size()));
        ui.note(count, "Series reales y simuladas separadas.");
        ui.button(results, "Exportar mediciones CSV", () -> activity.export(rows)).setEnabled(!rows.isEmpty());
        ui.outline(results, "Actualizar", activity::refreshHistory);
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
        ui.info(card, "Nombre", orb == null ? "Sin selección" : orb.name);
        ui.info(card, "Campaña", orb == null ? "Sin asignación" : orb.campaign);
        ui.info(card, "Recepción", "Anuncios iBeacon");
        ui.info(card, "Batería", "No transmitida");
        ui.note(card, "El administrador gestiona el nombre, el alta y la asignación desde la web.");
        sharing(body, sending, orb != null, "Enviar lecturas");
        ui.outline(body, "Buscar / cambiar Orb", () -> activity.go(Screen.LINK));
        ui.outline(body, "Deseleccionar este Orb", activity::unlink);
    }

    private void sharing(LinearLayout body, boolean sending, boolean enabled, String title) {
        LinearLayout card = ui.card(body);
        Switch toggle = new Switch(activity);
        ui.label(card, title, 19, true, INK);
        ui.note(card, "GPS opcional y solo en primer plano");
        toggle.setContentDescription("Enviar lecturas a la campaña");
        toggle.setEnabled(enabled);
        toggle.setChecked(sending);
        toggle.setOnCheckedChangeListener((button, checked) -> activity.setSharing(checked));
        ui.add(card, toggle);
        ui.label(card, "La publicación se autoriza en la campaña; enviar al servidor no publica automáticamente tu ubicación.", 11, false, MUTED);
    }

    public void account(LinearLayout body, AeriqAuthManager.User user, OrbAssignment orb, boolean sharing, List<OrbAssignment> assignments) {
        LinearLayout profile = ui.card(body);
        LinearLayout identity = ui.row();
        TextView avatar = ui.text(initial(user.name) + initial(user.last), 21, true, GREEN);
        avatar.setGravity(android.view.Gravity.CENTER);
        avatar.setBackground(ui.shape(SOFT, 29, 0));
        identity.addView(avatar, new LinearLayout.LayoutParams(ui.dp(58), ui.dp(58)));
        LinearLayout details = ui.column();
        details.setPadding(ui.dp(14), 0, 0, 0);
        details.addView(ui.text(user.fullName(), 18, true, INK));
        details.addView(ui.text(user.email, 12, false, MUTED));
        details.addView(ui.text(user.organization.isEmpty() ? "Consulta pública" : user.organization, 11, false, MUTED));
        details.addView(ui.text(user.role, 11, true, GREEN));
        ui.weighted(identity, details);
        ui.add(profile, identity);
        LinearLayout data = ui.card(body);
        menu(data, R.drawable.figma_nav_user, "Datos personales", Screen.EDIT_PROFILE);
        menu(data, R.drawable.figma_nav_chip, "Mi sensor", Screen.SENSOR);
        ui.outline(data, "Buscar y asignar sensor", () -> activity.go(Screen.LINK));
        LinearLayout sensors = ui.card(body);
        ui.label(sensors, "Mis sensores", 19, true, INK);
        if (assignments.isEmpty()) ui.note(sensors, "Todavía no tienes sensores asignados. Busca tu Orb para añadirlo.");
        for (OrbAssignment assignment : assignments) {
            ui.label(sensors, assignment.name, 15, true, GREEN);
            ui.note(sensors, assignment.address + " · " + assignment.campaign);
        }
        ui.note(sensors, "La asignación pertenece a tu cuenta. Seleccionar el sensor permite recibirlo en este móvil.");
        LinearLayout security = ui.card(body);
        menu(security, R.drawable.ic_lock, "Cambiar contraseña", Screen.CHANGE_PASSWORD);
        ui.menu(security, R.drawable.ic_fingerprint, "Acceso biométrico", INK,
            () -> activity.message("La sesión utiliza Android Keystore."));
        ui.label(security, "Mejora prevista", 11, false, MUTED);
        LinearLayout privacy = ui.card(body);
        menu(privacy, R.drawable.aeriq_community, "Envío de campaña", Screen.CONFIG);
        menu(privacy, R.drawable.ic_download, "Historial y exportación", Screen.HISTORY);
        menu(privacy, R.drawable.ic_shield, "Privacidad y ubicación", Screen.CONFIG);
        LinearLayout solutions = ui.card(body);
        solutions.setBackground(ui.shape(DARK, 20, 0));
        ui.label(solutions, "Soluciones para organizaciones", 16, true, WHITE);
        ui.label(solutions, "Kits Orb y estudios de Código Norte", 12, false, WHITE);
        solutions.setOnClickListener(view -> activity.go(Screen.SERVICES));
        LinearLayout organization = ui.card(body);
        ui.menu(organization, R.drawable.ic_globe, "Panel de organización", INK,
            () -> activity.open(ApiRoutes.BASE + "portal.html"));
        ui.label(organization, "Trabajadores, campañas e informes en la web", 11, false, MUTED);
        menu(organization, R.drawable.ic_info, "Ayuda y contacto", Screen.SERVICES);
        ui.menu(ui.card(body), R.drawable.ic_logout, "Cerrar sesión", RED, activity::logout);
        ui.note(body, "AERIQ · Orb · Desarrollado por Código Norte");
    }

    private String initial(String name) {
        return name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void menu(LinearLayout card, int icon, String label, Screen destination) {
        ui.menu(card, icon, label, INK, () -> activity.go(destination));
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
        LinearLayout intro = ui.card(body);
        intro.setBackground(ui.shape(GREEN, 22, 0));
        ui.label(intro, "Un aire limpio empieza por conocerlo.", 26, true, WHITE);
        ui.label(intro, "Qué contamina, cómo afecta a tu salud y qué dice la normativa.", 13, false, WHITE);
        LinearLayout sections = ui.column();
        LinearLayout[] anchors = new LinearLayout[3];
        ui.segments(body, new String[]{"Ozono", "Salud", "Normativa"}, 0, index -> {
            if (anchors[index] != null) anchors[index].requestRectangleOnScreen(
                new android.graphics.Rect(0, 0, anchors[index].getWidth(), anchors[index].getHeight()));
        });
        ui.add(body, sections);
        anchors[0] = section(sections, "¿Qué es el ozono troposférico?", "Se forma cerca del suelo cuando la luz del sol reacciona con contaminantes del tráfico y la industria.");
        anchors[1] = section(sections, "Efectos en la salud", "• Irritación de ojos, nariz y garganta\n• Tos y dificultad para respirar\n• Puede empeorar el asma.");
        section(sections, "Cómo protegerte", "• Consulta el mapa antes de hacer deporte\n• Evita el ejercicio intenso en las horas de más sol.");
        anchors[2] = section(sections, "Normativa", "Consulta los valores vigentes en las fuentes oficiales.");
        section(sections, "Otros contaminantes", "Ozono (O₃)\nDióxido de nitrógeno (NO₂)\nPartículas (PM10 y PM2,5)\nMonóxido de carbono (CO)");
        ui.note(body, "Las mediciones complementan las estaciones oficiales de vigilancia ambiental.");
    }

    public void compare(LinearLayout body, ExecutorService worker) {
        section(body, "Conoce las zonas medidas", "Compara datos publicados. Las zonas sin mediciones no reciben un valor estimado.");
        final boolean[] simulated = {true};
        LinearLayout results = ui.column();
        int[] request = {0};
        Runnable load = () -> {
            int generation = ++request[0];
            boolean demo = simulated[0];
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
                            ui.info(card, "Mediciones", Integer.toString(values.size()));
                            ui.info(card, "Media", average(values));
                            ui.info(card, "Mínimo / máximo", Collections.min(values) + " / " + Collections.max(values));
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
        ui.segments(body, new String[]{"Reales", "Simulados"}, 1, index -> {
            simulated[0] = index == 1;
            load.run();
        });
        ui.add(body, results);
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
