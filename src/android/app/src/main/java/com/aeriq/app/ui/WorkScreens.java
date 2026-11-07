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

    // ===================================================================== Mi sensor

    public void sensor(LinearLayout body, AeriqBleController ble, List<OrbAssignment> assignments,
                       String assignmentStatus, String serverStatus, boolean sending, List<StoredReading> history) {
        OrbAssignment found = null;
        for (OrbAssignment orb : assignments) if (orb.address.equalsIgnoreCase(ble.selectedAddress)) found = orb;
        final OrbAssignment selected = found;
        double medium = selected == null ? 60 : selected.medium, high = selected == null ? 100 : selected.high;

        // ---- Tarjeta del sensor: foto, nombre, recepción, Bluetooth y batería
        LinearLayout device = panel(body, 16);
        LinearLayout deviceRow = ui.row();
        ImageView photo = ui.photo(R.drawable.pbio_sensor_thumb, 16);
        deviceRow.addView(photo, new LinearLayout.LayoutParams(ui.dp(66), ui.dp(66)));
        LinearLayout identity = ui.column();
        identity.setPadding(ui.dp(16), 0, ui.dp(8), 0);
        TextView name = ui.text(selected == null ? "Sin sensor vinculado" : selected.name, 19, true, INK);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(name);
        LinearLayout receptionRow = ui.row();
        receptionRow.setPadding(0, ui.dp(4), 0, 0);
        android.view.View receptionDot = new android.view.View(activity);
        receptionRow.addView(receptionDot, new LinearLayout.LayoutParams(ui.dp(9), ui.dp(9)));
        TextView reception = ui.text("", 14, true, GREEN);
        reception.setPadding(ui.dp(7), 0, 0, 0);
        receptionRow.addView(reception, new LinearLayout.LayoutParams(0, -2, 1));
        identity.addView(receptionRow);
        deviceRow.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout status = ui.column();
        status.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        ImageView bluetooth = ui.image(R.drawable.ic_bluetooth, 36, 36);
        bluetooth.setPadding(ui.dp(9), ui.dp(9), ui.dp(9), ui.dp(9));
        LinearLayout.LayoutParams bluetoothParams = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36));
        bluetoothParams.gravity = android.view.Gravity.END;
        bluetoothParams.bottomMargin = ui.dp(6);
        status.addView(bluetooth, bluetoothParams);
        LinearLayout batteryRow = ui.row();
        ImageView batteryIcon = ui.image(R.drawable.ic_battery, 20, 20);
        batteryIcon.setColorFilter(MUTED);
        batteryRow.addView(batteryIcon, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        TextView battery = ui.text("—", 14, false, MUTED);
        battery.setPadding(ui.dp(5), 0, 0, 0);
        batteryRow.addView(battery);
        status.addView(batteryRow);
        deviceRow.addView(status);
        device.addView(deviceRow, new LinearLayout.LayoutParams(-1, -2));
        device.setOnClickListener(view -> activity.go(Screen.LINK));

        // ---- O₃ ahora + gráfica del día
        List<SensorWidgets.Point> today = new ArrayList<>();
        long midnight = Quality.startOfToday();
        List<StoredReading> mine = new ArrayList<>();
        if (selected != null) for (StoredReading reading : history) {
            if (reading.type != 14 || reading.deviceId != selected.id || reading.simulated != selected.simulated) continue;
            long time = Quality.utc(reading.date);
            if (time < midnight) continue;
            today.add(new SensorWidgets.Point(time, reading.value));
            mine.add(reading);
        }
        today.sort(Comparator.comparingLong(point -> point.time));
        String unit = "ppb";

        LinearLayout now = panel(body, 20);
        LinearLayout nowTop = ui.row();
        nowTop.setGravity(android.view.Gravity.TOP);
        LinearLayout nowLeft = ui.column();
        nowLeft.addView(ui.text("O₃ ahora", 15, true, INK));
        LinearLayout valueRow = ui.row();
        valueRow.setGravity(android.view.Gravity.BOTTOM);
        valueRow.setPadding(0, ui.dp(6), 0, 0);
        TextView ozone = ui.text("—", 56, true, INK);
        ozone.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        ozone.setIncludeFontPadding(false);
        valueRow.addView(ozone);
        TextView ozoneUnit = ui.text(unit, 15, false, MUTED);
        ozoneUnit.setPadding(ui.dp(4), 0, 0, ui.dp(6));
        valueRow.addView(ozoneUnit);
        nowLeft.addView(valueRow);
        nowTop.addView(nowLeft, new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = ui.text("Calidad: —", 14, true, MUTED);
        chip.setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7));
        nowTop.addView(chip);
        now.addView(nowTop, new LinearLayout.LayoutParams(-1, -2));
        SensorWidgets.DayAreaChart chart = new SensorWidgets.DayAreaChart(activity);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, ui.dp(150));
        chartParams.topMargin = ui.dp(18);
        now.addView(chart, chartParams);
        LinearLayout axis = ui.row();
        axis.setPadding(0, ui.dp(10), 0, 0);
        TextView axisStart = ui.text("00:00", 13, false, MUTED);
        TextView axisMiddle = ui.text("", 13, false, MUTED);
        axisMiddle.setGravity(android.view.Gravity.CENTER);
        TextView axisEnd = ui.text("", 13, false, MUTED);
        axisEnd.setGravity(android.view.Gravity.END);
        axis.addView(axisStart, new LinearLayout.LayoutParams(0, -2, 1));
        axis.addView(axisMiddle, new LinearLayout.LayoutParams(0, -2, 1));
        axis.addView(axisEnd, new LinearLayout.LayoutParams(0, -2, 1));
        now.addView(axis, new LinearLayout.LayoutParams(-1, -2));
        TextView lastMeasure = ui.text("Última medición: —", 14, false, MUTED);
        lastMeasure.setPadding(0, ui.dp(18), 0, ui.dp(16));
        now.addView(lastMeasure);
        now.addView(arrowButton("Ver historial", () -> activity.go(Screen.HISTORY)), new LinearLayout.LayoutParams(-1, ui.dp(54)));

        // ---- Tu día
        LinearLayout day = panel(body, 20);
        TextView dayTitle = ui.text("Tu día", 20, true, INK);
        dayTitle.setPadding(0, 0, 0, ui.dp(14));
        day.addView(dayTitle);
        LinearLayout tiles = ui.row();
        TextView distance = tile(tiles, "Distancia");
        tiles.addView(new android.view.View(activity), new LinearLayout.LayoutParams(ui.dp(12), 1));
        TextView breathed = tile(tiles, "O₃ medio respirado");
        day.addView(tiles, new LinearLayout.LayoutParams(-1, -2));
        TextView stripTitle = ui.text("Calidad del aire que has respirado hoy", 14, false, MUTED);
        stripTitle.setPadding(0, ui.dp(18), 0, ui.dp(10));
        day.addView(stripTitle);
        SensorWidgets.QualityStrip strip = new SensorWidgets.QualityStrip(activity);
        day.addView(strip, new LinearLayout.LayoutParams(-1, ui.dp(24)));
        LinearLayout stripAxis = ui.row();
        stripAxis.setPadding(0, ui.dp(8), 0, 0);
        stripAxis.addView(ui.text("00:00", 13, false, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        TextView nowLabel = ui.text("Ahora", 13, false, MUTED);
        stripAxis.addView(nowLabel);
        day.addView(stripAxis, new LinearLayout.LayoutParams(-1, -2));
        distance.setText(distanceToday(mine));

        // ---- Estado y configuración
        LinearLayout shortcuts = ui.row();
        shortcuts.addView(shortcut(R.drawable.ic_pulse, "Estado del sensor", true,
            () -> sensorStatus(ble, selected)), new LinearLayout.LayoutParams(0, ui.dp(132), 1));
        shortcuts.addView(new android.view.View(activity), new LinearLayout.LayoutParams(ui.dp(12), 1));
        shortcuts.addView(shortcut(R.drawable.ic_sliders, "Configurar sensor", false,
            () -> activity.go(Screen.CONFIG)), new LinearLayout.LayoutParams(0, ui.dp(132), 1));
        ui.add(body, shortcuts);

        // ---- Compartir con la red
        LinearLayout share = ui.row();
        share.setBackground(ui.shape(0xffe6efec, 22, 0));
        share.setPadding(ui.dp(18), ui.dp(16), ui.dp(16), ui.dp(16));
        ImageView users = ui.image(R.drawable.ic_users, 26, 26);
        users.setColorFilter(GREEN);
        share.addView(users, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));
        LinearLayout shareTexts = ui.column();
        shareTexts.setPadding(ui.dp(14), 0, ui.dp(10), 0);
        shareTexts.addView(ui.text(sending ? "Compartiendo con la red" : "Compartir con la red", 17, true, INK));
        shareTexts.addView(ui.text("Opcional. Solo ozono y zona aproximada.", 13, false, MUTED));
        share.addView(shareTexts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch shareToggle = ui.toggle(sending);
        shareToggle.setEnabled(selected != null);
        shareToggle.setContentDescription("Compartir mediciones con la red");
        shareToggle.setOnCheckedChangeListener((button, checked) -> {
            activity.setSharing(checked);
            ((TextView) shareTexts.getChildAt(0)).setText(checked ? "Compartiendo con la red" : "Compartir con la red");
        });
        share.addView(shareToggle);
        ui.add(body, share);
        if (selected == null) {
            ui.button(body, "Vincular un sensor", () -> activity.go(Screen.LINK));
            ui.note(body, assignmentStatus);
        }

        // ---- Actualización en directo (cada segundo)
        activity.updateLive(() -> {
            long nowMs = System.currentTimeMillis();
            boolean on = ble.bluetoothEnabled();
            bluetooth.setBackground(ui.shape(on ? 0xffe3edfb : 0xffeef1f0, 18, 0));
            bluetooth.setColorFilter(on ? 0xff2f80ed : 0xffa9b3af);
            bluetooth.setContentDescription(on ? "Bluetooth activado" : "Bluetooth desactivado");
            int dotColor;
            String text;
            long age = nowMs - ble.lastAdvertisementAt;
            if (selected == null) { dotColor = Quality.NONE; text = "Toca para vincular tu sensor"; }
            else if (!on) { dotColor = Quality.NONE; text = "Bluetooth desactivado"; }
            else if (ble.lastAdvertisementAt <= 0) { dotColor = Quality.MEDIUM; text = ble.scanning ? "Esperando mediciones…" : "Recepción en pausa"; }
            else if (age <= 15_000) { dotColor = Quality.LOW; text = "Recibiendo mediciones · hace " + Quality.ago(age); }
            else { dotColor = Quality.MEDIUM; text = "Sin mediciones · hace " + Quality.ago(age); }
            reception.setText(text);
            reception.setTextColor(dotColor == Quality.LOW ? GREEN : MUTED);
            android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(dotColor);
            receptionDot.setBackground(dot);
            // El firmware actual no transmite la batería: se muestra "—" en lugar de inventar un valor.
            battery.setText(ble.batteryPercent >= 0 ? ble.batteryPercent + "%" : "—");

            List<SensorWidgets.Point> points = new ArrayList<>(today);
            Double current = null;
            long lastAt = today.isEmpty() ? 0 : today.get(today.size() - 1).time;
            if (selected != null && ble.lastO3 != null) {
                current = (double) ble.lastO3.reading.value;
                if (ble.lastO3.receivedAtMillis > lastAt) {
                    points.add(new SensorWidgets.Point(ble.lastO3.receivedAtMillis, current));
                    lastAt = ble.lastO3.receivedAtMillis;
                }
            } else if (!today.isEmpty()) current = today.get(today.size() - 1).value;
            ozone.setText(current == null ? "—" : Quality.number(current));
            if (current == null) {
                chip.setText("Calidad: —");
                chip.setTextColor(MUTED);
                chip.setBackground(ui.shape(0xffeff3f1, 18, 0));
            } else {
                int level = Quality.level(current, medium, high);
                chip.setText("Calidad: " + Quality.label(level));
                chip.setTextColor(Quality.text(level));
                chip.setBackground(ui.shape(Quality.soft(level), 18, 0));
            }
            chart.setPoints(points);
            long span = nowMs - midnight;
            java.text.SimpleDateFormat hour = new java.text.SimpleDateFormat("HH:00", Locale.getDefault());
            axisMiddle.setText(hour.format(new Date(midnight + span / 2)));
            axisEnd.setText(hour.format(new Date(nowMs)));
            lastMeasure.setText("Última medición: " + (lastAt > 0 ? Quality.when(lastAt) : "—"));
            strip.setPoints(points, medium, high);
            if (points.isEmpty()) breathed.setText("—");
            else {
                double total = 0;
                for (SensorWidgets.Point point : points) total += point.value;
                breathed.setText(Quality.number(total / points.size()) + " " + unit);
            }
        });
    }

    /** Distancia recorrida hoy según las posiciones GPS guardadas con tus mediciones. */
    private String distanceToday(List<StoredReading> readings) {
        List<StoredReading> located = new ArrayList<>();
        for (StoredReading reading : readings) if (reading.latitude != null && reading.longitude != null) located.add(reading);
        located.sort(Comparator.comparing(reading -> reading.date));
        double meters = 0;
        float[] result = new float[1];
        for (int i = 1; i < located.size(); i++) {
            StoredReading a = located.get(i - 1), b = located.get(i);
            android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, result);
            if (result[0] >= 5 && result[0] <= 2000) meters += result[0]; // descarta ruido y saltos de GPS
        }
        if (located.size() < 2) return "—";
        return meters < 1000 ? Math.round(meters) + " m"
            : String.format(Locale.getDefault(), "%.1f km", meters / 1000);
    }

    private LinearLayout panel(LinearLayout body, int padding) {
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(WHITE, 26, LINE));
        panel.setElevation(ui.dp(1));
        panel.setPadding(ui.dp(padding), ui.dp(padding), ui.dp(padding), ui.dp(padding));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(14);
        body.addView(panel, params);
        return panel;
    }

    private TextView tile(LinearLayout row, String label) {
        LinearLayout tile = ui.column();
        tile.setBackground(ui.shape(0xfff1f4f3, 18, 0));
        tile.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        tile.addView(ui.text(label, 14, false, MUTED));
        TextView value = ui.text("—", 22, true, INK);
        value.setPadding(0, ui.dp(4), 0, 0);
        tile.addView(value);
        row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
    }

    private LinearLayout shortcut(int icon, String label, boolean soft, Runnable click) {
        LinearLayout card = ui.column();
        card.setBackground(ui.shape(WHITE, 26, LINE));
        card.setElevation(ui.dp(1));
        card.setPadding(ui.dp(18), ui.dp(18), ui.dp(14), ui.dp(16));
        ImageView picture = ui.image(icon, 46, 46);
        picture.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
        picture.setColorFilter(soft ? GREEN : MUTED);
        picture.setBackground(ui.shape(soft ? SOFT : 0xffeff3f1, 14, 0));
        card.addView(picture, new LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)));
        android.view.View space = new android.view.View(activity);
        card.addView(space, new LinearLayout.LayoutParams(1, 0, 1));
        card.addView(ui.text(label, 16, true, INK));
        card.setContentDescription(label);
        card.setOnClickListener(view -> click.run());
        return card;
    }

    private LinearLayout arrowButton(String label, Runnable click) {
        LinearLayout button = ui.row();
        button.setGravity(android.view.Gravity.CENTER);
        button.setBackground(ui.shape(WHITE, 20, 0xffd5dcd9));
        button.addView(ui.text(label, 16, true, INK));
        ImageView arrow = ui.image(R.drawable.ic_arrow_right, 20, 20);
        arrow.setColorFilter(INK);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20));
        arrowParams.leftMargin = ui.dp(10);
        button.addView(arrow, arrowParams);
        button.setContentDescription(label);
        button.setOnClickListener(view -> click.run());
        return button;
    }

    /** Detalle técnico (antes en la pantalla principal): recepción, diagnóstico, temperatura y envío. */
    private void sensorStatus(AeriqBleController ble, OrbAssignment selected) {
        int flags = ble.sensorDiagnostic;
        String diagnostic = flags < 0 ? "Sin diagnóstico recibido."
            : (flags & 128) != 0 ? "Señal fuera de rango (VGAS y VREF difieren más de 60 mV): revisa el cableado del sensor."
            : (flags & 8) != 0 ? "VREF no está cerca de la mitad de la alimentación: revisa el cable de VREF y el buffer."
            : (flags & 1) == 0 ? "Lectura inválida. Comprueba alimentación y cableado."
            : (flags & 2) != 0 ? "El cálculo de O₃ es negativo y se limita a 0. Revisa cero, VREF y estabilización."
            : (flags & 32) != 0 ? "Temperatura fuera del rango de compensación."
            : "Cálculo válido · la calibración física debe verificarse.";
        String message = "Sensor: " + (selected == null ? "ninguno seleccionado" : selected.name + " · " + selected.campaign)
            + "\n\nBluetooth: " + ble.status
            + "\n\nDiagnóstico: " + diagnostic
            + "\n\nTemperatura del módulo: " + (ble.lastTemperature == null ? "—" : ble.lastTemperature.reading.value + " °C")
            + "\n\nServidor: " + activity.serverStatus();
        new android.app.AlertDialog.Builder(activity).setTitle("Estado del sensor").setMessage(message)
            .setNegativeButton("Reintentar envíos", (dialog, which) -> activity.retryUploads())
            .setPositiveButton("Cerrar", null).show();
    }

    // ===================================================================== Vincular sensor

    public void link(LinearLayout body, AeriqBleController ble, List<OrbAssignment> assignments) {
        ImageView hero = ui.photo(R.drawable.pbio_sensor_hero, 26);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, ui.dp(176));
        heroParams.bottomMargin = ui.dp(18);
        body.addView(hero, heroParams);
        TextView title = ui.text("Conecta tu sensor", 30, true, INK);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
        body.addView(title);
        TextView subtitle = ui.text("Enciende el sensor y acércalo al móvil. AERIQ lo encontrará por Bluetooth.", 16, false, MUTED);
        subtitle.setPadding(0, ui.dp(6), 0, ui.dp(18));
        subtitle.setLineSpacing(ui.dp(3), 1);
        body.addView(subtitle);

        LinearLayout setup = panel(body, 18);
        Switch bluetooth = permissionRow(setup, R.drawable.ic_bluetooth, "Bluetooth", "Necesario para leer el sensor");
        bluetooth.setContentDescription("Buscar sensores por Bluetooth");
        bluetooth.setOnClickListener(view -> activity.toggleScan());
        android.view.View divider = new android.view.View(activity);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, ui.dp(1));
        dividerParams.setMargins(0, ui.dp(16), 0, ui.dp(16));
        setup.addView(divider, dividerParams);
        Switch place = permissionRow(setup, R.drawable.aeriq_location, "Ubicación", "Para situar cada medición en el mapa");
        place.setContentDescription("Permiso de ubicación");
        place.setOnClickListener(view -> activity.toggleLocationPermission());

        LinearLayout nearby = ui.row();
        nearby.setPadding(0, ui.dp(10), 0, ui.dp(12));
        nearby.addView(ui.text("Sensores cercanos", 19, true, INK), new LinearLayout.LayoutParams(0, -2, 1));
        ProgressBar spinner = new ProgressBar(activity);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GREEN));
        nearby.addView(spinner, new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        TextView searching = ui.text("Buscando…", 15, false, MUTED);
        searching.setPadding(ui.dp(8), 0, 0, 0);
        nearby.addView(searching);
        body.addView(nearby, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout devices = ui.column();
        body.addView(devices, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout shop = ui.row();
        shop.setBackground(ui.shape(0xffe6efec, 22, 0));
        shop.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(18));
        ImageView bag = ui.image(R.drawable.ic_bag, 28, 28);
        bag.setColorFilter(GREEN);
        shop.addView(bag, new LinearLayout.LayoutParams(ui.dp(28), ui.dp(28)));
        LinearLayout shopTexts = ui.column();
        shopTexts.setPadding(ui.dp(16), 0, ui.dp(12), 0);
        shopTexts.addView(ui.text("¿Aún no tienes sensor?", 17, true, INK));
        TextView shopText = ui.text("Puedes comprarlo en la web de AERIQ. El mapa público sigue siendo gratis.", 14, false, MUTED);
        shopText.setPadding(0, ui.dp(4), 0, 0);
        shopTexts.addView(shopText);
        shop.addView(shopTexts, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView external = ui.image(R.drawable.ic_external, 24, 24);
        external.setColorFilter(GREEN);
        shop.addView(external, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
        shop.setOnClickListener(view -> activity.open(ApiRoutes.BASE));
        LinearLayout.LayoutParams shopParams = new LinearLayout.LayoutParams(-1, -2);
        shopParams.topMargin = ui.dp(4);
        body.addView(shop, shopParams);

        final String[] previous = {null};
        activity.updateLive(() -> {
            bluetooth.setChecked(ble.scanning);
            place.setChecked(activity.locationAllowed());
            spinner.setVisibility(ble.scanning ? android.view.View.VISIBLE : android.view.View.GONE);
            searching.setText(ble.scanning ? "Buscando…" : "Búsqueda detenida");
            Map<String, AeriqBleController.FoundDevice> visible = new LinkedHashMap<>();
            for (AeriqBleController.FoundDevice device : ble.devices()) {
                if (System.currentTimeMillis() - device.lastSeen > 15_000) continue;
                visible.put(device.address, device);
            }
            List<AeriqBleController.FoundDevice> sorted = new ArrayList<>(visible.values());
            sorted.sort((a, b) -> Integer.compare(b.rssi, a.rssi));
            StringBuilder signature = new StringBuilder();
            signature.append(ble.selectedAddress).append(ble.scanning);
            for (OrbAssignment assignment : assignments) signature.append(assignment.address).append(assignment.name);
            for (AeriqBleController.FoundDevice device : sorted)
                signature.append(device.address).append(signal(device.rssi)).append(device.diagnostic);
            if (signature.toString().equals(previous[0])) return;
            previous[0] = signature.toString();
            devices.removeAllViews();
            if (sorted.isEmpty()) {
                TextView empty = ui.text(ble.scanning
                    ? "Buscando tu sensor… Déjalo encendido cerca del móvil."
                    : "Activa Bluetooth para ver los sensores cercanos.", 14, false, MUTED);
                empty.setPadding(0, ui.dp(4), 0, ui.dp(18));
                devices.addView(empty);
                return;
            }
            String highlighted = null;
            for (AeriqBleController.FoundDevice device : sorted)
                if (device.address.equalsIgnoreCase(ble.selectedAddress)) highlighted = device.address;
            if (highlighted == null) highlighted = sorted.get(0).address;
            for (AeriqBleController.FoundDevice device : sorted) {
                OrbAssignment match = null;
                for (OrbAssignment assignment : assignments)
                    if (assignment.address.equalsIgnoreCase(device.address)) match = assignment;
                deviceCard(devices, ble, device, match, device.address.equals(highlighted));
            }
        });
    }

    private Switch permissionRow(LinearLayout parent, int icon, String title, String description) {
        LinearLayout row = ui.row();
        ImageView picture = ui.image(icon, 52, 52);
        picture.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        picture.setColorFilter(GREEN);
        picture.setBackground(ui.shape(0xffe6efec, 16, 0));
        row.addView(picture, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(16), 0, ui.dp(10), 0);
        texts.addView(ui.text(title, 17, true, INK));
        TextView detail = ui.text(description, 14, false, MUTED);
        detail.setPadding(0, ui.dp(2), 0, 0);
        texts.addView(detail);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = ui.toggle(false);
        row.addView(toggle);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return toggle;
    }

    private String signal(int rssi) {
        return rssi >= -65 ? "Señal fuerte" : rssi >= -80 ? "Señal media" : "Señal débil";
    }

    private void deviceCard(LinearLayout parent, AeriqBleController ble, AeriqBleController.FoundDevice device,
                            OrbAssignment assigned, boolean highlighted) {
        boolean selected = device.address.equalsIgnoreCase(ble.selectedAddress);
        LinearLayout card = ui.row();
        card.setBackground(highlighted ? ui.shape(0xffe6efec, 22, 0) : ui.shape(WHITE, 22, LINE));
        if (highlighted) ((android.graphics.drawable.GradientDrawable) card.getBackground()).setStroke(ui.dp(2), GREEN);
        card.setPadding(ui.dp(14), ui.dp(14), ui.dp(16), ui.dp(14));
        ImageView photo = ui.photo(R.drawable.pbio_sensor_thumb, 14);
        card.addView(photo, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(16), 0, ui.dp(8), 0);
        TextView name = ui.text(assigned == null ? "PBIO O₃" : assigned.name, 17, true, INK);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(name);
        String id = assigned != null ? String.format(Locale.ROOT, "ID #%03d", assigned.id)
            : "ID " + device.address.substring(Math.max(0, device.address.length() - 5));
        texts.addView(ui.text(id + " · " + signal(device.rssi), 14, false, MUTED));
        if (device.diagnostic >= 0 && (device.diagnostic & 1) == 0)
            texts.addView(ui.text("Revisa la lectura (estado 0x" + Integer.toHexString(device.diagnostic) + ")", 12, true, RED));
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Runnable action = () -> {
            if (selected) { activity.go(Screen.SENSOR); return; }
            if (assigned != null) { activity.selectOrb(device.address); return; }
            EditText label = new EditText(activity);
            label.setSingleLine(true);
            label.setText("Sensor PBIO · " + device.address.substring(Math.max(0, device.address.length() - 5)));
            new android.app.AlertDialog.Builder(activity).setTitle("Vincula tu sensor")
                .setMessage("Ponle un nombre. Quedará guardado en tu cuenta y las nuevas lecturas serán privadas.")
                .setView(label).setNegativeButton("Cancelar", null)
                .setPositiveButton("Vincular", (dialog, which) -> {
                    String text = label.getText().toString().trim();
                    if (text.isEmpty() || text.length() > 80) { activity.message("Usa un nombre de entre 1 y 80 caracteres."); return; }
                    activity.assignSensor(device.address, text);
                }).show();
        };
        if (highlighted) {
            TextView button = ui.text(selected ? "Vinculado" : "Vincular", 16, true, WHITE);
            button.setGravity(android.view.Gravity.CENTER);
            button.setPadding(ui.dp(22), 0, ui.dp(22), 0);
            button.setBackground(ui.shape(GREEN, 16, 0));
            button.setOnClickListener(view -> action.run());
            card.addView(button, new LinearLayout.LayoutParams(-2, ui.dp(48)));
        } else {
            ImageView chevron = ui.image(R.drawable.ic_chevron, 20, 20);
            chevron.setColorFilter(MUTED);
            card.addView(chevron, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        }
        card.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(12);
        parent.addView(card, params);
    }

    public void history(LinearLayout body, List<StoredReading> history, String status) {
        LinearLayout results = ui.column();
        final int[] days = {7}, type = {14};
        ui.segments(body, new String[]{"Ozono · ppb", "Temperatura · °C"}, 0, index -> {
            type[0] = index == 0 ? 14 : 12;
            showHistory(results, history, days[0], type[0]);
        });
        ui.segments(body, new String[]{"Día", "Semana", "Mes", "Año"}, 1,
            index -> { days[0] = new int[]{1, 7, 30, 365}[index]; showHistory(results, history, days[0], type[0]); });
        ui.add(body, results);
        showHistory(results, history, 7, 14);
    }

    private void showHistory(LinearLayout results, List<StoredReading> history, int days, int type) {
        results.removeAllViews();
        String unit = type == 14 ? "ppb" : "°C";
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("UTC"));
        String cutoff = date.format(new Date(System.currentTimeMillis() - days * 86400000L));
        List<StoredReading> rows = new ArrayList<>();
        Map<Boolean, List<Double>> groups = new LinkedHashMap<>();
        for (StoredReading reading : history) if (reading.type == type && reading.date.compareTo(cutoff) >= 0) rows.add(reading);
        rows.sort(Comparator.comparing(reading -> reading.date));
        for (StoredReading reading : rows) groups.computeIfAbsent(reading.simulated, key -> new ArrayList<>()).add(reading.value);
        LinearLayout summary = ui.card(results);
        ui.label(summary, (type == 14 ? "O₃" : "Temperatura") + " · últimos " + days + " días", 19, true, INK);
        ui.note(summary, "UTC · Máximo 1.000 lecturas recientes de tus asignaciones.");
        if (rows.isEmpty()) ui.note(results, "No hay mediciones en este periodo.");
        for (Map.Entry<Boolean, List<Double>> group : groups.entrySet()) {
            List<Double> values = group.getValue();
            LinearLayout card = summary;
            ui.label(card, group.getKey() ? "Serie simulada" : "Serie real", 17, true, INK);
            card.addView(new MeasurementGraph(activity, values, type == 14), new LinearLayout.LayoutParams(-1, ui.dp(145)));
            ui.info(card, "Media de esta serie", average(values, unit));
        }
        if (!rows.isEmpty()) {
            LinearLayout extremes = ui.row();
            LinearLayout low = ui.column(), high = ui.column();
            ui.weighted(extremes, low);
            extremes.addView(new android.view.View(activity), new LinearLayout.LayoutParams(ui.dp(10), 1));
            ui.weighted(extremes, high);
            section(low, Collections.min(rows.stream().map(reading -> reading.value)
                .collect(java.util.stream.Collectors.toList())) + " " + unit, "Mínimo");
            section(high, Collections.max(rows.stream().map(reading -> reading.value)
                .collect(java.util.stream.Collectors.toList())) + " " + unit, "Máximo");
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
        ui.note(card, "Guardado automático al seleccionar tu sensor. GPS opcional y solo en primer plano.");
        toggle.setContentDescription("Enviar lecturas a la campaña");
        toggle.setEnabled(enabled);
        toggle.setChecked(sending);
        toggle.setOnCheckedChangeListener((button, checked) -> activity.setSharing(checked));
        ui.add(card, toggle);
        ui.label(card, "La publicación se autoriza en la campaña; enviar al servidor no publica automáticamente tu ubicación.", 11, false, MUTED);
        ui.note(card, "Si falla Internet, las lecturas quedan guardadas en el móvil y se reintentan. Desactivar este interruptor pausa las nuevas capturas, no borra las pendientes.");
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
        final boolean[] simulated = {false};
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
        return average(values, "ppb");
    }

    private String average(List<Double> values, String unit) {
        if (values.isEmpty()) return "—";
        double total = 0;
        for (double value : values) total += value;
        return String.format(Locale.getDefault(), "%.1f %s", total / values.size(), unit);
    }
}
