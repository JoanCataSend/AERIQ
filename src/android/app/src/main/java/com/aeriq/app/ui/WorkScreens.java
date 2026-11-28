package com.aeriq.app.ui;
/*
 * Fichero: WorkScreens.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
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

    /**
     * Diseño lógico: activity: Text, ui: Text --> WorkScreens() --> Text
     * Descripción: Procesa work screens y devuelve el resultado correspondiente.
     */
    public WorkScreens(MainActivity activity, Views ui) {
        this.activity = activity;
        this.ui = ui;
    }

    // ===================================================================== Mi sensor

    /**
     * Diseño lógico: body: Text, ble: Text, assignments: [ Text ], assignmentStatus: Text, serverStatus: Text, sending: B, history: [ Text ] --> sensor()
     * Descripción: Procesa sensor y devuelve el resultado correspondiente..
     */
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
        TextView name = ui.text(selected == null ? "Sin sensor vinculado" : activity.sensorName(selected), 19, true, INK);
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
        shortcuts.addView(shortcut(R.drawable.ic_sliders, "Detalles del sensor", false,
            () -> activity.go(Screen.CONFIG)), new LinearLayout.LayoutParams(0, ui.dp(132), 1));
        ui.add(body, shortcuts);

        // ---- Envío a la campaña: automático, sin interruptor. Publicarlo lo decide el admin de campaña en la web.
        LinearLayout share = ui.row();
        share.setBackground(ui.shape(0xffe6efec, 22, 0));
        share.setPadding(ui.dp(18), ui.dp(16), ui.dp(16), ui.dp(16));
        ImageView users = ui.image(R.drawable.ic_users, 26, 26);
        users.setColorFilter(GREEN);
        share.addView(users, new LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)));
        LinearLayout shareTexts = ui.column();
        shareTexts.setPadding(ui.dp(14), 0, 0, 0);
        shareTexts.addView(ui.text(selected == null ? "Envío a tu campaña" : sending ? "Enviando a tu campaña" : "Envío en pausa",
            17, true, INK));
        shareTexts.addView(ui.text(selected == null
            ? "Cuando tengas un Orb asignado, cada lectura se enviará sola con su posición y hora."
            : "Cada lectura se envía sola con su posición y hora. El admin de tu campaña decide si sale en el mapa público.",
            13, false, MUTED));
        share.addView(shareTexts, new LinearLayout.LayoutParams(0, -2, 1));
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
            // Batería en directo del Orb; si no llega, la última guardada. Sin medidor: "—", nunca inventada.
            battery.setText(ble.batteryPercent >= 0 ? ble.batteryPercent + "%"
                : selected != null && selected.batteryPercent >= 0 ? selected.batteryPercent + "% (última)" : "—");

            List<SensorWidgets.Point> points = new ArrayList<>(today);
            Double current = null;
            boolean discarded = false;
            long lastAt = today.isEmpty() ? 0 : today.get(today.size() - 1).time;
            if (selected != null && ble.lastO3 != null) {
                current = (double) ble.lastO3.reading.value;
                // HU35: fuera de rango se enseña como descartada y no entra en la gráfica ni en «Tu día».
                discarded = !selected.inRange(ble.lastO3.reading.typeId, current);
                if (!discarded && ble.lastO3.receivedAtMillis > lastAt) {
                    points.add(new SensorWidgets.Point(ble.lastO3.receivedAtMillis, current));
                    lastAt = ble.lastO3.receivedAtMillis;
                }
            } else if (!today.isEmpty()) current = today.get(today.size() - 1).value;
            ozone.setText(current == null ? "—" : Quality.number(current));
            if (discarded) {
                chip.setText("Descartada: fuera de rango (" + selected.range(14) + ")");
                chip.setTextColor(Quality.MEDIUM_TEXT);
                chip.setBackground(ui.shape(Quality.MEDIUM_SOFT, 18, 0));
            } else if (current == null) {
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

    /**
     * Diseño lógico: readings: [ Text ] --> distanceToday() --> Text
     * Descripción: Procesa distance today y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: body: Text, padding: Z --> panel() --> Text
     * Descripción: Procesa panel y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: row: Text, label: Text --> tile() --> Text
     * Descripción: Procesa tile y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: icon: Z, label: Text, soft: B, click: Text --> shortcut() --> Text
     * Descripción: Procesa shortcut y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: label: Text, click: Text --> arrowButton() --> Text
     * Descripción: Procesa arrow button y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: ble: Text, selected: Text --> sensorStatus()
     * Descripción: Procesa sensor status y devuelve el resultado correspondiente..
     */
    private void sensorStatus(AeriqBleController ble, OrbAssignment selected) {
        int flags = ble.sensorDiagnostic;
        // 0 = sin datos, 1 = correcto, 2 = a revisar, 3 = fallo.
        int severity = flags < 0 ? 0 : (flags & 128) != 0 || (flags & 8) != 0 || (flags & 1) == 0 ? 3
            : (flags & 2) != 0 || (flags & 32) != 0 ? 2 : 1;
        String diagnostic = flags < 0 ? "Aún no ha llegado ningún diagnóstico del Orb."
            : (flags & 128) != 0 ? "Señal fuera de rango (VGAS y VREF difieren más de 60 mV): revisa el cableado del sensor."
            : (flags & 8) != 0 ? "VREF no está cerca de la mitad de la alimentación: revisa el cable de VREF y el buffer."
            : (flags & 1) == 0 ? "Lectura inválida. Comprueba alimentación y cableado."
            : (flags & 2) != 0 ? "El cálculo de O₃ es negativo y se limita a 0. Revisa cero, VREF y estabilización."
            : (flags & 32) != 0 ? "Temperatura fuera del rango de compensación."
            : "Cálculo válido. La calibración física debe verificarse.";
        int[] tones = {MUTED, Quality.LOW, Quality.MEDIUM, Quality.HIGH};
        int[] softs = {0xffeff3f1, Quality.soft(0), Quality.soft(1), Quality.soft(2)};
        String[] headlines = {"Sin datos del sensor", "Funciona correctamente", "Revisa el sensor", "El sensor tiene un fallo"};

        AppSheet sheet = new AppSheet(activity, ui, "Estado del sensor",
            selected == null ? "Ningún sensor seleccionado" : activity.sensorName(selected) + " · " + selected.campaign);

        LinearLayout summary = ui.row();
        summary.setBackground(ui.shape(softs[severity], 20, 0));
        summary.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        android.view.View dot = new android.view.View(activity);
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        circle.setColor(severity == 0 ? Quality.NONE : tones[severity]);
        dot.setBackground(circle);
        summary.addView(dot, new LinearLayout.LayoutParams(ui.dp(12), ui.dp(12)));
        TextView headline = ui.text(headlines[severity], 16, true, severity == 0 ? INK : Quality.text(severity - 1));
        headline.setPadding(ui.dp(12), 0, 0, 0);
        summary.addView(headline, new LinearLayout.LayoutParams(0, -2, 1));
        sheet.content.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        sheet.section("Detalle");
        LinearLayout details = sheet.group();
        sheet.info(details, R.drawable.ic_bluetooth, "Bluetooth", ble.status, 0xff2f80ed);
        sheet.info(details, R.drawable.ic_pulse, "Diagnóstico", diagnostic, severity == 0 ? MUTED : tones[severity]);
        sheet.info(details, R.drawable.aeriq_temperature, "Temperatura del módulo",
            ble.lastTemperature == null ? "—" : ble.lastTemperature.reading.value + " °C", GREEN);
        sheet.info(details, R.drawable.ic_globe, "Envío al servidor", activity.serverStatus(), GREEN);

        sheet.secondary("Reintentar envíos", activity::retryUploads);
        sheet.primary("Cerrar", null);
        sheet.show();
    }

    // ===================================================================== Vincular sensor

    /**
     * Diseño lógico: body: Text, ble: Text, assignments: [ Text ] --> link()
     * Descripción: Procesa link y devuelve el resultado correspondiente..
     */
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
        shopTexts.addView(ui.text("¿Tu organización aún no tiene Orb?", 17, true, INK));
        TextView shopText = ui.text("Los Orb se venden en kits para ayuntamientos y empresas, con cuentas para su equipo. "
            + "Solicita información en la web.", 14, false, MUTED);
        shopText.setPadding(0, ui.dp(4), 0, 0);
        shopTexts.addView(shopText);
        shop.addView(shopTexts, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView external = ui.image(R.drawable.ic_external, 24, 24);
        external.setColorFilter(GREEN);
        shop.addView(external, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
        shop.setOnClickListener(view -> activity.open(ApiRoutes.BASE + "solicitar.html"));
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

    /**
     * Diseño lógico: parent: Text, icon: Z, title: Text, description: Text --> permissionRow() --> Text
     * Descripción: Procesa permission row y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: rssi: Z --> signal() --> Text
     * Descripción: Procesa signal y devuelve el resultado correspondiente.
     */
    private String signal(int rssi) {
        return rssi >= -65 ? "Señal fuerte" : rssi >= -80 ? "Señal media" : "Señal débil";
    }

    /**
     * Diseño lógico: parent: Text, ble: Text, device: Text, assigned: Text, highlighted: B --> deviceCard()
     * Descripción: Procesa device card y devuelve el resultado correspondiente..
     */
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
        TextView name = ui.text(assigned == null ? "PBIO O₃" : activity.sensorName(assigned), 17, true, INK);
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
            // La app no se asigna Orb: lo hace el administrador de campaña desde el panel web (sensor-link.php responde 403).
            new android.app.AlertDialog.Builder(activity).setTitle("Este Orb no está asignado a tu cuenta")
                .setMessage("Los Orb los asigna el administrador de tu campaña desde el panel web de AERIQ. "
                    + "Pídeselo indicando este identificador: " + device.address + ". Cuando lo asigne, aparecerá aquí.")
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Actualizar", (dialog, which) -> activity.refreshAssignments()).show();
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

    /**
     * Pestaña Panel para quien gestiona y no mide (admin de campaña o admin principal). Lo que muestra sale de
     * los permisos del servidor, como el panel web: la gestión se hace allí, la app solo la explica y la abre.
     */
    /**
     * Diseño lógico: body: Text, user: Text --> panel()
     * Descripción: Procesa panel y devuelve el resultado correspondiente..
     */
    public void panel(LinearLayout body, AeriqAuthManager.User user) {
        if (user == null) return;
        LinearLayout who = panel(body, 18);
        who.addView(ui.text(user.roleName, 22, true, INK));
        String scope = !user.campaign.isEmpty() ? "Campaña: " + user.campaign
            : !user.organization.isEmpty() ? user.organization : "";
        if (!scope.isEmpty()) {
            TextView place = ui.text(scope, 14, false, MUTED);
            place.setPadding(0, ui.dp(4), 0, 0);
            who.addView(place);
        }
        TextView intro = ui.text("La gestión se hace en el panel web de AERIQ. La app es para quien mide con el Orb; "
            + "desde aquí también puedes consultar el mapa público y Aprende.", 14, false, MUTED);
        intro.setPadding(0, ui.dp(12), 0, 0);
        intro.setLineSpacing(ui.dp(2), 1);
        who.addView(intro);

        List<String> tasks = new ArrayList<>();
        if (user.can(AeriqAuthManager.User.PERMISSION_CAMPAIGNS)) {
            tasks.add("Crear campañas y nombrar a su admin");
            tasks.add("Seguir el estado de cada campaña y sus Orb");
            tasks.add("Atender las solicitudes de kits y estudios");
        }
        if (user.can(AeriqAuthManager.User.PERMISSION_TEAM)) {
            tasks.add("Crear operadores y reenviar o desactivar sus cuentas");
            tasks.add("Registrar los Orb y asignar uno a cada operador (también desde aquí, con el móvil)");
            tasks.add("Ajustar umbrales y radio, y decidir si la campaña sale en el mapa público");
            tasks.add("Consultar informes y exportar CSV");
        }
        if (!tasks.isEmpty()) {
            LinearLayout list = panel(body, 18);
            list.addView(ui.text("En el panel web puedes", 17, true, INK));
            for (String task : tasks) {
                LinearLayout row = ui.row();
                row.setGravity(android.view.Gravity.TOP);
                row.setPadding(0, ui.dp(10), 0, 0);
                ImageView tick = ui.image(R.drawable.ic_tick, 20, 20);
                tick.setColorFilter(GREEN);
                row.addView(tick, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
                TextView label = ui.text(task, 15, false, INK);
                label.setPadding(ui.dp(12), 0, 0, 0);
                row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
        }
        if (user.can(AeriqAuthManager.User.PERMISSION_TEAM)) {
            Button assign = ui.button(body, "Asignar un Orb con el móvil", () -> activity.go(Screen.ASSIGN));
            assign.setContentDescription("Buscar Orb por Bluetooth y asignarlos a operadores");
            ui.outline(body, "Abrir el panel web", () -> activity.open(ApiRoutes.BASE + "portal.html"));
        } else {
            Button open = ui.button(body, "Abrir el panel web", () -> activity.open(ApiRoutes.BASE + "portal.html"));
            open.setContentDescription("Abrir el panel web de AERIQ en el navegador");
        }
        ui.note(body, "Entra en la web con este mismo correo y contraseña.");
    }

    /**
     * Asignar Orb (admin de campaña): busca los Orb cercanos por Bluetooth para no teclear la MAC, y registra o
     * reasigna el elegido a un operador de su campaña. El operador lo recibe solo al abrir su app.
     */
    /**
     * Diseño lógico: body: Text, ble: Text, team: Text, status: Text --> assign()
     * Descripción: Procesa assign y devuelve el resultado correspondiente..
     */
    public void assign(LinearLayout body, AeriqBleController ble, CampaignTeam team, String status) {
        LinearLayout intro = panel(body, 18);
        intro.addView(ui.text("Asigna un Orb a un operador", 19, true, INK));
        TextView how = ui.text("Enciende el Orb y acércalo al móvil. Toca el que quieras y elige el operador: le aparecerá "
            + "en su app sin que tenga que hacer nada.", 14, false, MUTED);
        how.setPadding(0, ui.dp(8), 0, 0);
        how.setLineSpacing(ui.dp(2), 1);
        intro.addView(how);
        if (!status.isEmpty()) {
            TextView state = ui.text(status, 13, true, GREEN);
            state.setPadding(0, ui.dp(10), 0, 0);
            intro.addView(state);
        }

        LinearLayout people = panel(body, 18);
        people.addView(ui.text("Operadores de tu campaña", 17, true, INK));
        if (team.operators.isEmpty()) {
            TextView none = ui.text("Aún no hay operadores. Créalos en el panel web: Equipo → Añadir operador.", 14, false, MUTED);
            none.setPadding(0, ui.dp(8), 0, 0);
            people.addView(none);
        }
        for (CampaignTeam.Operator operator : team.operators) {
            LinearLayout row = ui.column();
            row.setPadding(0, ui.dp(10), 0, 0);
            row.addView(ui.text(operator.name, 15, true, INK));
            row.addView(ui.text((operator.orb.isEmpty() ? "Sin Orb" : "Lleva " + operator.orb)
                + (operator.pending ? " · invitación pendiente" : !operator.active ? " · cuenta desactivada" : ""), 13, false, MUTED));
            people.addView(row);
        }

        LinearLayout near = panel(body, 18);
        LinearLayout head = ui.row();
        head.addView(ui.text("Orb cercanos", 17, true, INK), new LinearLayout.LayoutParams(0, -2, 1));
        TextView scan = ui.text("", 14, true, GREEN);
        scan.setGravity(android.view.Gravity.CENTER);
        scan.setMinHeight(ui.dp(48));
        scan.setPadding(ui.dp(12), 0, 0, 0);
        scan.setOnClickListener(view -> activity.toggleScan());
        head.addView(scan);
        near.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView scanState = ui.text("", 13, false, MUTED);
        near.addView(scanState);
        LinearLayout list = ui.column();
        near.addView(list, new LinearLayout.LayoutParams(-1, -2));
        String[] shown = {null};
        activity.updateLive(() -> {
            scan.setText(ble.scanning ? "Detener" : "Buscar");
            scan.setContentDescription(ble.scanning ? "Detener la búsqueda de Orb" : "Buscar Orb cercanos por Bluetooth");
            List<AeriqBleController.FoundDevice> found = ble.devices();
            scanState.setText(ble.scanning ? (found.isEmpty() ? "Buscando Orb encendidos cerca…" : found.size() + " Orb detectados")
                : found.isEmpty() ? "Pulsa Buscar para encontrar los Orb encendidos." : "Búsqueda detenida.");
            // Solo se rehace la lista si cambian los Orb o su señal, para no parpadear cada segundo.
            StringBuilder key = new StringBuilder();
            for (AeriqBleController.FoundDevice device : found) key.append(device.address).append(signal(device.rssi));
            if (key.toString().equals(shown[0])) return;
            shown[0] = key.toString();
            list.removeAllViews();
            for (AeriqBleController.FoundDevice device : found) list.addView(nearbyOrb(device, team));
        });
        // Al entrar se empieza a buscar, como en Vincular.
        if (!ble.scanning) activity.toggleScan();
    }

    /**
     * Diseño lógico: device: Text, team: Text --> nearbyOrb() --> Text
     * Descripción: Procesa nearby orb y devuelve el resultado correspondiente.
     */
    private android.view.View nearbyOrb(AeriqBleController.FoundDevice device, CampaignTeam team) {
        CampaignTeam.Device registered = team.byAddress(device.address);
        CampaignTeam.Operator holder = team.holder(registered);
        LinearLayout card = ui.row();
        card.setPadding(0, ui.dp(12), 0, ui.dp(12));
        card.setMinimumHeight(ui.dp(56));
        LinearLayout texts = ui.column();
        texts.addView(ui.text(registered != null ? registered.name : "Orb sin registrar", 15, true, INK));
        texts.addView(ui.text(device.address + " · " + signal(device.rssi), 13, false, MUTED));
        texts.addView(ui.text(holder != null ? "Lo lleva " + holder.name
            : registered != null ? "Registrado, sin operador" : "Se registrará en tu campaña al asignarlo", 13, false,
            holder != null ? MUTED : GREEN));
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = ui.text(holder != null ? "Cambiar" : "Asignar", 15, true, GREEN);
        action.setPadding(ui.dp(12), 0, 0, 0);
        card.addView(action);
        card.setContentDescription((registered != null ? registered.name : "Orb sin registrar") + ", " + device.address
            + ". Toca para asignarlo a un operador.");
        card.setOnClickListener(view -> chooseOperator(device.address, registered, team));
        return card;
    }

    /**
     * Diseño lógico: address: Text, registered: Text, team: Text --> chooseOperator()
     * Descripción: Procesa choose operator y devuelve el resultado correspondiente..
     */
    private void chooseOperator(String address, CampaignTeam.Device registered, CampaignTeam team) {
        List<CampaignTeam.Operator> candidates = new ArrayList<>();
        for (CampaignTeam.Operator operator : team.operators) if (operator.active || operator.pending) candidates.add(operator);
        if (candidates.isEmpty()) {
            activity.message("No hay operadores en tu campaña. Créalos en el panel web.", MainActivity.TONE_WARN);
            return;
        }
        LinearLayout form = ui.column();
        form.setPadding(ui.dp(22), ui.dp(8), ui.dp(22), 0);
        form.addView(ui.text("Nombre del Orb", 13, true, INK));
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setText(registered != null ? registered.name : "Orb " + address.substring(Math.max(0, address.length() - 5)));
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView pick = ui.text("Operador", 13, true, INK);
        pick.setPadding(0, ui.dp(12), 0, 0);
        form.addView(pick);
        RadioGroup group = new RadioGroup(activity);
        for (int i = 0; i < candidates.size(); i++) {
            CampaignTeam.Operator operator = candidates.get(i);
            RadioButton option = new RadioButton(activity);
            option.setId(i + 1);
            option.setMinHeight(ui.dp(48));
            option.setText(operator.name + (operator.orb.isEmpty() ? " · sin Orb" : " · lleva " + operator.orb));
            option.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
            group.addView(option);
        }
        form.addView(group);
        TextView rule = ui.text("Cada operador lleva un solo Orb: si ya tenía otro, quedará libre.", 12, false, MUTED);
        rule.setPadding(0, ui.dp(8), 0, 0);
        form.addView(rule);
        new android.app.AlertDialog.Builder(activity).setTitle(address).setView(form)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Asignar", (dialog, which) -> {
                String value = name.getText().toString().trim();
                int chosen = group.getCheckedRadioButtonId();
                if (value.isEmpty() || value.length() > 160) { activity.message("Pon un nombre de entre 1 y 160 caracteres.", MainActivity.TONE_ERROR); return; }
                if (chosen < 1) { activity.message("Elige el operador.", MainActivity.TONE_ERROR); return; }
                activity.assignOrb(registered, address, value, candidates.get(chosen - 1));
            }).show();
    }

    /**
     * Diseño lógico: body: Text --> services()
     * Descripción: Procesa services y devuelve el resultado correspondiente..
     */
    public void services(LinearLayout body) {
        section(body, "Tu campaña, a tu medida.", "Código Norte desarrolla AERIQ y el sensor Orb. La consulta del mapa público es gratuita.");
        service(body, "Kits para administraciones", "Para campañas municipales y estudios del territorio.", "administracion");
        service(body, "Kits para empresas", "Sensores, trabajadores y campañas de tu organización.", "empresa");
        service(body, "Estudios ambientales", "Código Norte realiza el estudio sin que tengas que comprar sensores.", "estudio");
        ui.note(body, "Los presupuestos se preparan al revisar las necesidades.");
        // HU33: el tutorial de la primera vez también se vuelve a ver desde Ayuda.
        LinearLayout help = section(body, "¿Cómo funciona la app?", "Vuelve a ver la introducción: el mapa, tu cuenta, el Orb y los permisos.");
        ui.button(help, "Ver el tutorial de la app", activity::showTutorial);
    }

    /**
     * Diseño lógico: body: Text, title: Text, description: Text, kind: Text --> service()
     * Descripción: Procesa service y devuelve el resultado correspondiente..
     */
    private void service(LinearLayout body, String title, String description, String kind) {
        LinearLayout card = section(body, title, description);
        ui.button(card, "Solicitar información", () -> activity.open(ApiRoutes.BASE + "solicitar.html?service=" + kind));
    }

    /**
     * Diseño lógico: body: Text, title: Text, description: Text --> section() --> Text
     * Descripción: Procesa section y devuelve el resultado correspondiente.
     */
    private LinearLayout section(LinearLayout body, String title, String description) {
        LinearLayout card = ui.card(body);
        ui.label(card, title, 19, true, INK);
        ui.note(card, description);
        return card;
    }

}
