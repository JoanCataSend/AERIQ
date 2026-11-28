package com.aeriq.app.ui;
/*
 * Fichero: DesignScreens.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
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

/** Pantallas del diseño Android de Figma: Aprende, Historial, Configurar, Comparar y Cuenta. */
public final class DesignScreens {
    private static final Locale ES = new Locale("es", "ES");
    private static final int FIELD = 0xfff1f4f3, PANEL_SOFT = 0xffe6efec, CHIP = 0xffeef1f0;
    private final MainActivity activity;
    private final Views ui;



    public DesignScreens(MainActivity activity, Views ui) {
        this.activity = activity;
        this.ui = ui;
    }

    // ================================================================= utilidades comunes

    /**
     * Diseño lógico: (LinearLayout body, int padding) → panel() → LinearLayout; según doc/android_design.md.
     */
    private LinearLayout panel(LinearLayout body, int padding) {
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(WHITE, 24, LINE));
        panel.setPadding(ui.dp(padding), ui.dp(padding), ui.dp(padding), ui.dp(padding));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(14);
        body.addView(panel, params);
        return panel;
    }

    /**
     * Gestiona texto.
     * Diseño lógico: (LinearLayout parent, String value, int size, boolean bold, int color, int top) → text() → TextView; según doc/android_design.md.
     */
    private TextView text(LinearLayout parent, String value, int size, boolean bold, int color, int top) {
        TextView view = ui.text(value, size, bold, color);
        view.setPadding(0, ui.dp(top), 0, 0);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    /**
     * Gestiona tile.
     * Diseño lógico: (int icon, int size, int background, int tint) → tile() → ImageView; según doc/android_design.md.
     */
    private ImageView tile(int icon, int size, int background, int tint) {
        ImageView image = ui.image(icon, size, size);
        int pad = Math.round(size * .26f);
        image.setPadding(ui.dp(pad), ui.dp(pad), ui.dp(pad), ui.dp(pad));
        image.setBackground(ui.shape(background, 12, 0));
        image.setColorFilter(tint);
        return image;
    }

    /**
     * Gestiona divider.
     * Diseño lógico: (sin parámetros) → divider() → View; según doc/android_design.md.
     */
    private View divider() {
        View line = new View(activity);
        line.setBackgroundColor(LINE);
        return line;
    }

    /**
     * Gestiona add divider.
     * Diseño lógico: (LinearLayout parent, int top, int bottom) → addDivider() → void; según doc/android_design.md.
     */
    private void addDivider(LinearLayout parent, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(1));
        params.setMargins(0, ui.dp(top), 0, ui.dp(bottom));
        parent.addView(divider(), params);
    }

    /**
     * Gestiona chip.
     * Diseño lógico: (String label, int background, int color) → chip() → TextView; según doc/android_design.md.
     */
    private TextView chip(String label, int background, int color) {
        TextView chip = ui.text(label, 13, true, color);
        chip.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5));
        chip.setBackground(ui.shape(background, 16, 0));
        return chip;
    }

    /**
     * Gestiona underline.
     * Diseño lógico: (String value) → underline() → CharSequence; según doc/android_design.md.
     */
    private CharSequence underline(String value) {
        SpannableString text = new SpannableString(value);
        text.setSpan(new UnderlineSpan(), 0, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** Selector de pestañas: fondo gris, pestaña activa blanca. */
    /**
     * Diseño lógico: (LinearLayout body, String[] labels, int initial, java.util.function.IntConsumer selected) → tabs() → TextView[]; según doc/android_design.md.
     */
    private TextView[] tabs(LinearLayout body, String[] labels, int initial, java.util.function.IntConsumer selected) {
        LinearLayout strip = ui.row();
        strip.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        strip.setBackground(ui.shape(0xffe6ebe9, 16, 0));
        TextView[] items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int option = i;
            TextView item = ui.text(labels[i], 14, true, MUTED);
            item.setGravity(Gravity.CENTER);
            items[i] = item;
            strip.addView(item, new LinearLayout.LayoutParams(0, ui.dp(44), 1));
            item.setOnClickListener(view -> {
                select(items, option);
                selected.accept(option);
            });
        }
        select(items, initial);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(14);
        body.addView(strip, params);
        return items;
    }

    /**
     * Selecciona.
     * Diseño lógico: (TextView[] items, int active) → select() → void; según doc/android_design.md.
     */
    private void select(TextView[] items, int active) {
        for (int i = 0; i < items.length; i++) {
            items[i].setSelected(i == active);
            items[i].setTextColor(i == active ? INK : MUTED);
            items[i].setBackground(i == active ? ui.shape(WHITE, 12, 0) : null);
            items[i].setElevation(i == active ? ui.dp(1) : 0);
        }
    }

    /** Fila de menú: icono en baldosa, título, subtítulo opcional y elemento final. */
    /**
     * Diseño lógico: (parámetros de la firma) → menuRow() → LinearLayout; según doc/android_design.md.
     */
    private LinearLayout menuRow(LinearLayout parent, int icon, int tint, int background, String title, int titleColor,
                                 String subtitle, View trailing, Runnable click) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, ui.dp(12));
        row.addView(tile(icon, 40, background, tint), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        texts.addView(ui.text(title, 16, true, titleColor));
        if (subtitle != null) {
            TextView sub = ui.text(subtitle, 12, false, MUTED);
            sub.setPadding(0, ui.dp(2), 0, 0);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (trailing != null) row.addView(trailing);
        if (click != null) {
            row.setOnClickListener(view -> click.run());
            row.setContentDescription(title);
        }
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    /**
     * Gestiona chevron.
     * Diseño lógico: (sin parámetros) → chevron() → ImageView; según doc/android_design.md.
     */
    private ImageView chevron() {
        ImageView image = ui.image(R.drawable.ic_chevron, 18, 18);
        image.setColorFilter(MUTED);
        image.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        return image;
    }

    /**
     * Gestiona dark promo.
     * Diseño lógico: (LinearLayout body, String title, String subtitle, Runnable click) → darkPromo() → LinearLayout; según doc/android_design.md.
     */
    private LinearLayout darkPromo(LinearLayout body, String title, String subtitle, Runnable click) {
        LinearLayout card = ui.row();
        card.setBackground(ui.shape(DARK, 22, 0));
        card.setPadding(ui.dp(14), ui.dp(16), ui.dp(18), ui.dp(16));
        card.addView(tile(R.drawable.ic_scale, 40, 0xff36403c, WHITE), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        texts.addView(ui.text(title, 15, true, WHITE));
        TextView sub = ui.text(subtitle, 13, false, 0xffb7c2be);
        sub.setPadding(0, ui.dp(3), 0, 0);
        texts.addView(sub);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = ui.image(R.drawable.ic_chevron, 18, 18);
        arrow.setColorFilter(WHITE);
        card.addView(arrow, new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        card.setOnClickListener(view -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(14);
        body.addView(card, params);
        return card;
    }

    /**
     * Gestiona footer.
     * Diseño lógico: (LinearLayout body, String value) → footer() → void; según doc/android_design.md.
     */
    private void footer(LinearLayout body, String value) {
        TextView note = ui.text(value, 12, false, MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(8));
        body.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    // ================================================================= Aprende

    /**
     * Diseño lógico: (LinearLayout body) → learn() → void; según doc/android_design.md.
     */
    public void learn(LinearLayout body) {
        LinearLayout hero = ui.column();
        hero.setBackground(ui.shape(GREEN, 26, 0));
        hero.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(22));
        TextView title = text(hero, "Un aire limpio empieza por conocerlo.", 28, true, WHITE, 0);
        title.setLineSpacing(0, .95f);
        text(hero, "Qué contamina, cómo afecta a tu salud y qué dice la normativa.", 14, false, 0xe6ffffff, 8);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.bottomMargin = ui.dp(16);
        body.addView(hero, heroParams);
        // HU33: el tutorial de la primera vez se puede volver a ver aquí.
        Button again = ui.action("Ver el tutorial de la app", WHITE, GREEN, 26, activity::showTutorial);
        again.setContentDescription("Volver a ver el tutorial de la app");
        ui.add(body, again);

        LinearLayout[] anchors = new LinearLayout[3];
        tabs(body, new String[]{"Ozono", "Salud", "Normativa"}, 0, index -> {
            LinearLayout target = anchors[index];
            if (target != null && body.getParent() instanceof ScrollView)
                ((ScrollView) body.getParent()).smoothScrollTo(0, target.getTop() - ui.dp(12));
        });

        anchors[0] = infoCard(body, R.drawable.ic_air, "¿Qué es el ozono troposférico?",
            "Es un gas que se forma cerca del suelo cuando la luz del sol reacciona con contaminantes del tráfico "
                + "y la industria. Sube en las horas centrales del día y en verano.", null);
        anchors[1] = infoCard(body, R.drawable.ic_heart, "Efectos en la salud", null, new String[]{
            "Irritación de ojos, nariz y garganta", "Tos y dificultad para respirar",
            "Puede empeorar el asma y otras enfermedades respiratorias"});
        infoCard(body, R.drawable.ic_shield, "Cómo protegerte", null, new String[]{
            "Consulta el mapa antes de hacer deporte al aire libre",
            "Evita el ejercicio intenso en las horas de más sol",
            "Cuando el nivel sea alto, elige zonas verdes y primeras o últimas horas"});
        anchors[2] = infoCard(body, R.drawable.ic_scale, "Normativa",
            "La Directiva europea 2008/50/CE fija umbrales de información y de alerta para el ozono. "
                + "Consulta los valores vigentes en las fuentes oficiales.", null);
        TextView law = ui.text("", 14, true, GREEN);
        law.setText(underline("Ver la normativa en la web"));
        law.setPadding(0, ui.dp(10), 0, 0);
        law.setOnClickListener(view -> activity.open("https://eur-lex.europa.eu/legal-content/ES/TXT/?uri=CELEX:32008L0050"));
        anchors[2].addView(law);

        LinearLayout others = panel(body, 18);
        text(others, "Otros contaminantes", 18, true, INK, 0);
        String[][] rows = {
            {"Ozono (O₃)", "Lo mide tu sensor AERIQ", "Gas oxidante que se forma con la luz del sol. Es el contaminante que mide tu sensor."},
            {"Dióxido de nitrógeno (NO₂)", "Procede sobre todo del tráfico", "Lo emiten los motores de combustión. Irrita las vías respiratorias y ayuda a formar ozono."},
            {"Partículas (PM10 y PM2,5)", "Se pueden inhalar en profundidad", "Polvo y hollín microscópicos. Las PM2,5 llegan hasta los pulmones y la sangre."},
            {"Monóxido de carbono (CO)", "Combustión incompleta", "Gas sin olor que reduce el transporte de oxígeno en la sangre."}};
        for (int i = 0; i < rows.length; i++) {
            String[] row = rows[i];
            LinearLayout line = ui.row();
            line.setPadding(0, ui.dp(12), 0, ui.dp(12));
            LinearLayout texts = ui.column();
            texts.addView(ui.text(row[0], 15, true, INK));
            TextView sub = ui.text(row[1], 13, false, MUTED);
            sub.setPadding(0, ui.dp(2), 0, 0);
            texts.addView(sub);
            line.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(chevron());
            line.setOnClickListener(view -> new android.app.AlertDialog.Builder(activity)
                .setTitle(row[0]).setMessage(row[2]).setPositiveButton("Cerrar", null).show());
            others.addView(line, new LinearLayout.LayoutParams(-1, -2));
            if (i < rows.length - 1) addDivider(others, 0, 0);
        }
        footer(body, "AERIQ mide ozono (O₃). Sus mediciones son complementarias y no sustituyen a las estaciones "
            + "oficiales ni constituyen un diagnóstico de exposición personal.");
    }

    /**
     * Gestiona info card.
     * Diseño lógico: (LinearLayout body, int icon, String title, String description, String[] bullets) → infoCard() → LinearLayout; según doc/android_design.md.
     */
    private LinearLayout infoCard(LinearLayout body, int icon, String title, String description, String[] bullets) {
        LinearLayout card = panel(body, 18);
        LinearLayout head = ui.row();
        head.addView(tile(icon, 38, PANEL_SOFT, GREEN), new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));
        TextView heading = ui.text(title, 18, true, INK);
        heading.setPadding(ui.dp(12), 0, 0, 0);
        head.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));
        if (description != null) {
            TextView text = text(card, description, 14, false, MUTED, 12);
            text.setLineSpacing(ui.dp(3), 1);
        }
        if (bullets != null) for (String bullet : bullets) {
            LinearLayout line = ui.row();
            line.setGravity(Gravity.TOP);
            line.setPadding(0, ui.dp(10), 0, 0);
            View dot = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(GREEN);
            dot.setBackground(shape);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ui.dp(6), ui.dp(6));
            dotParams.topMargin = ui.dp(7);
            dotParams.rightMargin = ui.dp(12);
            line.addView(dot, dotParams);
            line.addView(ui.text(bullet, 14, false, INK), new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(line, new LinearLayout.LayoutParams(-1, -2));
        }
        return card;
    }

    // ================================================================= Historial

    /**
     * Diseño lógico: (LinearLayout body, List<StoredReading> history, String status, OrbAssignment sensor) → history() → void; según doc/android_design.md.
     */
    public void history(LinearLayout body, List<StoredReading> history, String status, OrbAssignment sensor) {
        double medium = sensor == null ? 60 : sensor.medium, high = sensor == null ? 100 : sensor.high;
        List<StoredReading> ozone = new ArrayList<>();
        for (StoredReading reading : history) {
            if (reading.type != 14) continue;
            if (sensor != null && reading.deviceId != sensor.id) continue;
            ozone.add(reading);
        }
        LinearLayout content = ui.column();
        tabs(body, new String[]{"Día", "Semana", "Mes", "Año"}, 0,
            index -> showHistory(content, ozone, index, medium, high, status));
        body.addView(content, new LinearLayout.LayoutParams(-1, -2));
        showHistory(content, ozone, 0, medium, high, status);
    }

    /**
     * Muestra historial.
     * Diseño lógico: (LinearLayout content, List<StoredReading> all, int period, double medium, double high, String status) → showHistory() → void; según doc/android_design.md.
     */
    private void showHistory(LinearLayout content, List<StoredReading> all, int period, double medium, double high, String status) {
        content.removeAllViews();
        long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(Quality.startOfToday());
        int slots;
        long[] starts;
        String[] labels;
        String header;
        String averageLabel;
        switch (period) {
            case 1: {
                slots = 7;
                calendar.add(Calendar.DAY_OF_YEAR, -6);
                starts = new long[slots + 1];
                String[] days = {"D", "L", "M", "X", "J", "V", "S"};
                labels = new String[slots];
                for (int i = 0; i <= slots; i++) {
                    starts[i] = calendar.getTimeInMillis();
                    if (i < slots) labels[i] = days[calendar.get(Calendar.DAY_OF_WEEK) - 1];
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                }
                header = "Últimos 7 días";
                averageLabel = "Media de la semana: ";
                break;
            }
            case 2: {
                slots = 30;
                calendar.add(Calendar.DAY_OF_YEAR, -29);
                starts = new long[slots + 1];
                List<String> marks = new ArrayList<>();
                for (int i = 0; i <= slots; i++) {
                    starts[i] = calendar.getTimeInMillis();
                    if (i < slots && i % 7 == 0 || i == slots - 1)
                        marks.add(new SimpleDateFormat("d/M", ES).format(new Date(starts[i])));
                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                }
                labels = marks.toArray(new String[0]);
                header = "Últimos 30 días";
                averageLabel = "Media del mes: ";
                break;
            }
            case 3: {
                slots = 12;
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.add(Calendar.MONTH, -11);
                starts = new long[slots + 1];
                String[] months = {"E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"};
                labels = new String[slots];
                for (int i = 0; i <= slots; i++) {
                    starts[i] = calendar.getTimeInMillis();
                    if (i < slots) labels[i] = months[calendar.get(Calendar.MONTH)];
                    calendar.add(Calendar.MONTH, 1);
                }
                header = "Últimos 12 meses";
                averageLabel = "Media del año: ";
                break;
            }
            default: {
                slots = 24;
                starts = new long[slots + 1];
                for (int i = 0; i <= slots; i++) {
                    starts[i] = calendar.getTimeInMillis();
                    calendar.add(Calendar.HOUR_OF_DAY, 1);
                }
                labels = new String[]{"00", "06", "12", "18", "24"};
                header = "Hoy, " + new SimpleDateFormat("d 'de' MMMM", ES).format(new Date(now));
                averageLabel = "Media del día: ";
            }
        }
        double[] sum = new double[slots];
        int[] count = new int[slots];
        List<StoredReading> rows = new ArrayList<>();
        StoredReading latest = null;
        long latestAt = 0;
        double total = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (StoredReading reading : all) {
            long time = Quality.utc(reading.date);
            if (time < starts[0] || time >= starts[slots]) continue;
            int slot = 0;
            while (slot < slots - 1 && time >= starts[slot + 1]) slot++;
            sum[slot] += reading.value;
            count[slot]++;
            rows.add(reading);
            total += reading.value;
            min = Math.min(min, reading.value);
            max = Math.max(max, reading.value);
            if (time >= latestAt) { latestAt = time; latest = reading; }
        }
        rows.sort(Comparator.comparing(reading -> reading.date));
        Double[] values = new Double[slots];
        boolean[] future = new boolean[slots];
        boolean[] levels = new boolean[3];
        for (int i = 0; i < slots; i++) {
            future[i] = starts[i] > now;
            if (count[i] > 0) {
                values[i] = sum[i] / count[i];
                levels[Quality.level(values[i], medium, high)] = true;
            }
        }
        String unit = latest == null ? "ppb" : latest.unit;

        LinearLayout card = panel(content, 20);
        text(card, header, 14, false, MUTED, 0);
        LinearLayout valueRow = ui.row();
        valueRow.setGravity(Gravity.BOTTOM);
        valueRow.setPadding(0, ui.dp(16), 0, 0);
        TextView label = ui.text("O₃", 22, true, INK);
        label.setPadding(0, 0, ui.dp(8), ui.dp(6));
        valueRow.addView(label);
        TextView value = ui.text(latest == null ? "—" : Quality.number(latest.value), 48, true, INK);
        value.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        value.setIncludeFontPadding(false);
        valueRow.addView(value);
        TextView unitView = ui.text(unit, 13, false, MUTED);
        unitView.setPadding(ui.dp(4), 0, 0, ui.dp(6));
        valueRow.addView(unitView);
        card.addView(valueRow, new LinearLayout.LayoutParams(-1, -2));
        text(card, averageLabel + (rows.isEmpty() ? "—" : Quality.number(total / rows.size()) + " " + unit), 14, false, MUTED, 14);
        SensorWidgets.HistoryBars bars = new SensorWidgets.HistoryBars(activity);
        bars.set(values, future, labels, medium, high);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, ui.dp(170));
        barParams.topMargin = ui.dp(16);
        card.addView(bars, barParams);
        LinearLayout legend = ui.row();
        legend.setPadding(0, ui.dp(12), 0, 0);
        if (!levels[0] && !levels[1] && !levels[2]) { levels[0] = true; levels[1] = true; }
        String[] names = {"Bajo", "Moderado", "Alto"};
        for (int i = 0; i < 3; i++) if (levels[i]) legend.addView(legendItem(names[i], Quality.color(i)));
        card.addView(legend, new LinearLayout.LayoutParams(-1, -2));
        if (rows.isEmpty()) text(card, all.isEmpty() ? status : "No hay mediciones en este periodo.", 13, false, MUTED, 10);

        LinearLayout first = ui.row(), second = ui.row();
        statTile(first, "Máximo", rows.isEmpty() ? "—" : Quality.number(max) + " " + unit, true);
        statTile(first, "Mínimo", rows.isEmpty() ? "—" : Quality.number(min) + " " + unit, false);
        statTile(second, "Media", rows.isEmpty() ? "—" : Quality.number(total / rows.size()) + " " + unit, true);
        statTile(second, "Mediciones", Integer.toString(rows.size()), false);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = ui.dp(12);
        content.addView(first, rowParams);
        content.addView(second, new LinearLayout.LayoutParams(rowParams));

        // Sin exportación: en la web el operador consulta sus lecturas pero no las descarga; exporta el admin de campaña.
        darkPromo(content, "Compara tu zona con el resto de la red", "Media de ozono de cada zona del mapa público",
            () -> activity.go(Screen.COMPARE));
    }

    /**
     * Gestiona legend item.
     * Diseño lógico: (String name, int color) → legendItem() → View; según doc/android_design.md.
     */
    private View legendItem(String name, int color) {
        LinearLayout item = ui.row();
        item.setPadding(0, 0, ui.dp(16), 0);
        View dot = new View(activity);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        dot.setBackground(shape);
        item.addView(dot, new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)));
        TextView label = ui.text(name, 13, false, MUTED);
        label.setPadding(ui.dp(6), 0, 0, 0);
        item.addView(label);
        return item;
    }

    /**
     * Gestiona stat tile.
     * Diseño lógico: (LinearLayout row, String label, String value, boolean gap) → statTile() → void; según doc/android_design.md.
     */
    private void statTile(LinearLayout row, String label, String value, boolean gap) {
        LinearLayout tile = ui.column();
        tile.setBackground(ui.shape(0xffeaefed, 18, 0));
        tile.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        tile.addView(ui.text(label, 13, false, MUTED));
        TextView number = ui.text(value, 19, true, INK);
        number.setPadding(0, ui.dp(4), 0, 0);
        tile.addView(number);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (gap) params.rightMargin = ui.dp(10);
        row.addView(tile, params);
    }

    // ================================================================= Detalles del sensor

    /**
     * Detalles del sensor para el operador: solo consulta. El nombre del Orb, su campaña y si se publica los
     * gestiona el admin de campaña en el panel web; el envío y la posición van siempre. Reconectar sí se ofrece.
     */
    /**
     * Diseño lógico: (LinearLayout body, AeriqBleController ble, OrbAssignment sensor, boolean locationAllowed) → details() → void; según doc/android_design.md.
     */
    public void details(LinearLayout body, AeriqBleController ble, OrbAssignment sensor, boolean locationAllowed) {
        LinearLayout state = panel(body, 18);
        LinearLayout head = ui.row();
        head.addView(ui.text("Estado del sensor", 18, true, INK), new LinearLayout.LayoutParams(0, -2, 1));
        TextView connected = chip("● Conectado", PANEL_SOFT, GREEN);
        head.addView(connected);
        state.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView connection = infoRow(state, "Conexión", "Bluetooth LE", true);
        TextView battery = infoRow(state, "Batería", "—", true);
        TextView signal = infoRow(state, "Señal", "—", true);
        TextView sync = infoRow(state, "Última lectura", "—", true);
        infoRow(state, "Sensor", "SPEC O₃", false);

        LinearLayout assignment = panel(body, 18);
        text(assignment, "Tu asignación", 18, true, INK, 0);
        infoRow(assignment, "Orb", sensor == null ? "Sin Orb asignado" : activity.sensorName(sensor), true);
        infoRow(assignment, "Campaña", sensor == null ? "—" : sensor.campaign, false);
        text(assignment, "El nombre del Orb, su campaña y si sale en el mapa público los gestiona el admin de tu campaña "
            + "desde el panel web.", 13, false, MUTED, 10);

        LinearLayout capture = panel(body, 18);
        text(capture, "Envío y ubicación", 18, true, INK, 0);
        text(capture, "Con la app abierta, cada lectura del Orb se envía sola a tu campaña con su posición y hora. "
            + "Sin ubicación la lectura se guarda, pero no puede salir en el mapa.", 14, false, MUTED, 8);
        infoRow(capture, "Permiso de ubicación", locationAllowed ? "Permitido" : "Falta", false);
        if (!locationAllowed) {
            LinearLayout allow = wideButton(R.drawable.aeriq_location, "Permitir ubicación", WHITE, GREEN, 0);
            allow.setOnClickListener(view -> activity.requestCaptureLocation());
            LinearLayout.LayoutParams allowParams = new LinearLayout.LayoutParams(-1, ui.dp(52));
            allowParams.topMargin = ui.dp(8);
            capture.addView(allow, allowParams);
        }

        LinearLayout reconnect = wideButton(R.drawable.ic_refresh, "Reconectar sensor", INK, WHITE, 0xffd5dcd9);
        reconnect.setOnClickListener(view -> activity.reconnectSensor());
        LinearLayout.LayoutParams reconnectParams = new LinearLayout.LayoutParams(-1, ui.dp(54));
        reconnectParams.bottomMargin = ui.dp(12);
        body.addView(reconnect, reconnectParams);
        footer(body, "Si el Orb no aparece, acércalo al móvil, comprueba que el Bluetooth está activado y pulsa Reconectar.");

        activity.updateLive(() -> {
            long age = System.currentTimeMillis() - ble.lastAdvertisementAt;
            boolean live = sensor != null && ble.lastAdvertisementAt > 0 && age <= 15_000;
            connected.setText(live ? "● Conectado" : sensor == null ? "Sin sensor" : "● Sin conexión");
            connected.setTextColor(live ? GREEN : MUTED);
            connected.setBackground(ui.shape(live ? PANEL_SOFT : CHIP, 16, 0));
            connection.setText(ble.bluetoothEnabled() ? "Bluetooth LE" : "Bluetooth desactivado");
            battery.setText(ble.batteryPercent >= 0 ? ble.batteryPercent + " %"
                : sensor != null && sensor.batteryPercent >= 0 ? sensor.batteryPercent + " % (última)" : "—");
            Integer rssi = null;
            if (sensor != null) for (AeriqBleController.FoundDevice device : ble.devices())
                if (device.address.equalsIgnoreCase(sensor.address)) rssi = device.rssi;
            signal.setText(rssi == null || !live ? "—" : rssi >= -65 ? "Fuerte" : rssi >= -80 ? "Media" : "Débil");
            sync.setText(sensor == null || ble.lastAdvertisementAt <= 0 ? "—" : "Hace " + Quality.ago(age));
        });
    }

    /**
     * Gestiona info fila.
     * Diseño lógico: (LinearLayout parent, String label, String value, boolean line) → infoRow() → TextView; según doc/android_design.md.
     */
    private TextView infoRow(LinearLayout parent, String label, String value, boolean line) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, ui.dp(12));
        row.addView(ui.text(label, 14, false, MUTED), new LinearLayout.LayoutParams(0, -2, 1));
        TextView view = ui.text(value, 14, true, INK);
        row.addView(view);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        if (line) parent.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        return view;
    }

    /**
     * Gestiona wide botón.
     * Diseño lógico: (int icon, String label, int color, int background, int border) → wideButton() → LinearLayout; según doc/android_design.md.
     */
    private LinearLayout wideButton(int icon, String label, int color, int background, int border) {
        LinearLayout button = ui.row();
        button.setGravity(Gravity.CENTER);
        button.setBackground(ui.shape(background, 18, border));
        ImageView image = ui.image(icon, 22, 22);
        image.setColorFilter(color);
        button.addView(image, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        TextView text = ui.text(label, 16, true, color);
        text.setPadding(ui.dp(10), 0, 0, 0);
        button.addView(text);
        button.setContentDescription(label);
        return button;
    }

    // ================================================================= Comparar zonas

    /**
     * Diseño lógico: (LinearLayout body, ExecutorService worker) → compare() → void; según doc/android_design.md.
     */
    public void compare(LinearLayout body, ExecutorService worker) {
        // Función libre: usa las mismas mediciones públicas que el mapa, sin cuenta.
        TextView subtitle = ui.text("Media de ozono de cada zona con mediciones publicadas.", 13, false, MUTED);
        subtitle.setPadding(0, 0, 0, ui.dp(14));
        body.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout results = ui.column();
        int[] request = {0};
        @SuppressWarnings("unchecked") List<PublicReadings.Reading>[] cache = new List[]{null};
        int[] period = {1};
        Runnable render = () -> renderCompare(results, cache[0], period[0]);
        tabs(body, new String[]{"24 h", "7 días", "30 días"}, 1, index -> { period[0] = index; render.run(); });
        body.addView(results, new LinearLayout.LayoutParams(-1, -2));
        text(results, "Consultando mediciones de la red…", 13, false, MUTED, 0);
        int generation = ++request[0];
        worker.execute(() -> {
            try {
                // Histórico (hasta 10.000 lecturas recientes): las pestañas de 7 y 30 días necesitan más que las últimas 24 h.
                List<PublicReadings.Reading> rows = PublicReadings.fetch(false, 0).rows;
                activity.runOnUiThread(() -> {
                    if (!results.isAttachedToWindow() || generation != request[0]) return;
                    cache[0] = rows;
                    render.run();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!results.isAttachedToWindow()) return;
                    results.removeAllViews();
                    text(results, "No se pudo consultar la red: " + error.getMessage(), 13, false, MUTED, 0);
                });
            }
        });
    }

    private static final class Zone {
        final String name;
        final List<PublicReadings.Reading> rows = new ArrayList<>();
        final Set<String> points = new HashSet<>();
        double sum;

        Zone(String name) { this.name = name; }

        boolean enough() { return points.size() >= 3 && rows.size() >= 3; }

        double average() { return sum / Math.max(1, rows.size()); }
    }

    /**
     * Representa compare.
     * Diseño lógico: (LinearLayout results, List<PublicReadings.Reading> all, int period) → renderCompare() → void; según doc/android_design.md.
     */
    private void renderCompare(LinearLayout results, List<PublicReadings.Reading> all, int period) {
        if (all == null) return;
        results.removeAllViews();
        long now = System.currentTimeMillis();
        long span = period == 0 ? 86_400_000L : period == 1 ? 7 * 86_400_000L : 30 * 86_400_000L;
        int buckets = period == 0 ? 24 : period == 1 ? 7 : 30;
        long start = now - span;
        Map<String, Zone> zones = new LinkedHashMap<>();
        for (PublicReadings.Reading reading : all) {
            long time = Quality.utc(reading.date);
            if (time < start || time > now) continue;
            Zone zone = zones.get(reading.zone);
            if (zone == null) { zone = new Zone(reading.zone); zones.put(reading.zone, zone); }
            zone.rows.add(reading);
            zone.points.add(reading.latitude + "/" + reading.longitude);
            zone.sum += reading.value;
        }
        List<Zone> ordered = new ArrayList<>(zones.values());
        ordered.sort((a, b) -> a.enough() != b.enough() ? (a.enough() ? -1 : 1) : Integer.compare(b.rows.size(), a.rows.size()));
        if (ordered.isEmpty()) {
            LinearLayout empty = panel(results, 18);
            text(empty, "Sin zonas que comparar", 19, true, INK, 0);
            text(empty, "No hay mediciones publicadas en este periodo. Prueba con un periodo más largo.", 14, false, MUTED, 8);
            return;
        }

        LinearLayout chartCard = panel(results, 18);
        LinearLayout legend = ui.row();
        int[] colors = {Quality.MEDIUM, GREEN};
        List<Double[]> series = new ArrayList<>();
        List<Integer> lineColors = new ArrayList<>();
        int shown = 0;
        for (Zone zone : ordered) {
            if (!zone.enough() || shown >= 2) continue;
            Double[] line = new Double[buckets];
            double[] sum = new double[buckets];
            int[] count = new int[buckets];
            for (PublicReadings.Reading reading : zone.rows) {
                int index = (int) Math.min(buckets - 1, (Quality.utc(reading.date) - start) * buckets / span);
                if (index < 0) continue;
                sum[index] += reading.value;
                count[index]++;
            }
            for (int i = 0; i < buckets; i++) if (count[i] > 0) line[i] = sum[i] / count[i];
            series.add(line);
            lineColors.add(colors[shown]);
            View dot = legendItem(zone.name, colors[shown]);
            ((TextView) ((LinearLayout) dot).getChildAt(1)).setTextColor(INK);
            ((TextView) ((LinearLayout) dot).getChildAt(1)).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            legend.addView(dot);
            shown++;
        }
        chartCard.addView(legend, new LinearLayout.LayoutParams(-1, -2));
        String[] labels = new String[4];
        for (int i = 0; i < 4; i++) {
            long time = start + span * i / 3;
            labels[i] = period == 0 ? new SimpleDateFormat("HH:mm", ES).format(new Date(time))
                : period == 1 ? capitalize(new SimpleDateFormat("EEE", ES).format(new Date(time)).replace(".", ""))
                : new SimpleDateFormat("d MMM", ES).format(new Date(time)).replace(".", "");
        }
        if (series.isEmpty()) {
            text(chartCard, "Aún no hay zonas con datos suficientes en este periodo.", 13, false, MUTED, 12);
        } else {
            SensorWidgets.CompareChart chart = new SensorWidgets.CompareChart(activity);
            chart.set(series, lineColors, labels);
            LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(-1, ui.dp(180));
            chartParams.topMargin = ui.dp(16);
            chartCard.addView(chart, chartParams);
        }

        LinearLayout list = panel(results, 18);
        text(list, "Zonas comparadas", 19, true, INK, 0);
        if (ordered.isEmpty()) text(list, "No hay mediciones publicadas en este periodo.", 13, false, MUTED, 10);
        for (int i = 0; i < ordered.size(); i++) {
            Zone zone = ordered.get(i);
            boolean enough = zone.enough();
            LinearLayout row = ui.row();
            row.setPadding(0, ui.dp(12), 0, ui.dp(12));
            LinearLayout texts = ui.column();
            texts.addView(ui.text(zone.name, 15, true, enough ? INK : 0xff9aa5a1));
            texts.addView(ui.text(zone.points.size() + (zone.points.size() == 1 ? " punto de medida" : " puntos de medida"),
                13, false, enough ? MUTED : 0xff9aa5a1));
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            TextView number = ui.text(enough ? Quality.number(zone.average()) : "—", 17, true, enough ? INK : 0xff9aa5a1);
            number.setPadding(ui.dp(8), 0, ui.dp(12), 0);
            row.addView(number);
            row.addView(chip(enough ? "Datos suficientes" : "Sin datos suficientes",
                enough ? PANEL_SOFT : CHIP, enough ? GREEN : MUTED));
            list.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i < ordered.size() - 1) list.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        }
        footer(results, "Solo se comparan zonas con datos suficientes (al menos 3 puntos de medida).");
    }

    /**
     * Gestiona capitalize.
     * Diseño lógico: (String value) → capitalize() → String; según doc/android_design.md.
     */
    private static String capitalize(String value) {
        return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(ES) + value.substring(1);
    }

    // ================================================================= Cuenta

    /**
     * Diseño lógico: (LinearLayout body, AeriqAuthManager.User user, OrbAssignment sensor, boolean sharing, AppPrefs prefs) → account() → void; según doc/android_design.md.
     */
    public void account(LinearLayout body, AeriqAuthManager.User user, OrbAssignment sensor, boolean sharing, AppPrefs prefs) {
        LinearLayout profile = panel(body, 18);
        LinearLayout identity = ui.row();
        TextView avatar = ui.text(initial(user.name) + initial(user.last), 22, true, GREEN);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xffcfe5de);
        circle.setStroke(ui.dp(4), 0xffe6f1ed);
        avatar.setBackground(circle);
        identity.addView(avatar, new LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)));
        LinearLayout details = ui.column();
        details.setPadding(ui.dp(16), 0, 0, 0);
        details.addView(ui.text(user.fullName().isEmpty() ? user.email : user.fullName(), 19, true, INK));
        TextView email = ui.text(user.email, 13, false, MUTED);
        email.setPadding(0, ui.dp(2), 0, ui.dp(6));
        details.addView(email);
        // Función y ámbito, como en el panel web: «Operador · Campaña X», «Admin de campaña · Campaña X»…
        String scope = !user.campaign.isEmpty() ? user.campaign : user.organization;
        details.addView(chip(user.roleName + (scope.isEmpty() ? "" : " · " + scope), CHIP, MUTED),
            new LinearLayout.LayoutParams(-2, -2));
        identity.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        profile.addView(identity, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout data = panel(body, 16);
        menuRow(data, R.drawable.figma_nav_user, GREEN, PANEL_SOFT, "Datos personales", INK, null, chevron(),
            () -> activity.go(Screen.EDIT_PROFILE));
        if (user.collects()) {
            data.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
            menuRow(data, R.drawable.figma_nav_chip, GREEN, PANEL_SOFT, "Mi sensor",
                INK, sensor == null ? "Sin Orb conectado" : activity.sensorName(sensor) + " · Conectado", chevron(),
                () -> activity.go(Screen.SENSOR));
        } else if (user.manages()) {
            data.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
            menuRow(data, R.drawable.ic_sliders, GREEN, PANEL_SOFT, "Panel", INK, "La gestión se hace en la web", chevron(),
                () -> activity.go(Screen.PANEL));
        }

        // Avisos (HU36): bandeja de mensajes con tu admin o tu equipo.
        if (activity.noticesAvailable()) {
            data.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
            int unread = activity.noticesUnread();
            View trailing = chevron();
            if (unread > 0) trailing = chip(String.valueOf(unread), RED, WHITE);
            LinearLayout row = menuRow(data, R.drawable.ic_help, GREEN, PANEL_SOFT, "Avisos", INK,
                unread > 0 ? (unread == 1 ? "1 aviso nuevo" : unread + " avisos nuevos") : "Mensajes con tu admin o tu equipo",
                trailing, () -> activity.go(Screen.NOTICES));
            row.setContentDescription("Avisos" + (unread > 0 ? ", " + unread + (unread == 1 ? " nuevo" : " nuevos") : ""));
        }

        LinearLayout security = panel(body, 16);
        menuRow(security, R.drawable.ic_lock, GREEN, PANEL_SOFT, "Cambiar contraseña", INK, null, chevron(),
            () -> activity.go(Screen.CHANGE_PASSWORD));
        security.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        boolean biometricIsEnabled = activity.biometricEnabled(user.id);
        Switch fingerprint = ui.toggle(biometricIsEnabled);
        boolean[] updatingFingerprintSwitch = {false};
        fingerprint.setOnCheckedChangeListener((button, checked) -> {
            if (updatingFingerprintSwitch[0]) return;
            if (!checked) {
                activity.disableBiometricAccess(user.id);
                activity.message("Acceso biométrico desactivado para esta cuenta.", MainActivity.TONE_OK);
                return;
            }
            button.setEnabled(false);
            activity.enableBiometricAccess(user.id, enabled -> {
                button.setEnabled(true);
                if (!enabled) {
                    updatingFingerprintSwitch[0] = true;
                    button.setChecked(false);
                    updatingFingerprintSwitch[0] = false;
                } else {
                    activity.message("Acceso biométrico vinculado a esta cuenta.", MainActivity.TONE_OK);
                }
            });
        });
        menuRow(security, R.drawable.ic_fingerprint, GREEN, PANEL_SOFT, "Acceso con huella o cara", INK, null, fingerprint, null);

        LinearLayout help = panel(body, 16);
        menuRow(help, R.drawable.ic_help, GREEN, PANEL_SOFT, "Ayuda y contacto", INK, null, chevron(),
            () -> activity.go(Screen.SERVICES));

        LinearLayout logout = panel(body, 16);
        menuRow(logout, R.drawable.ic_logout, RED, 0xfffde8e7, "Cerrar sesión", RED, null, null, activity::logout);
    }

    /**
     * Gestiona initial.
     * Diseño lógico: (String name) → initial() → String; según doc/android_design.md.
     */
    private String initial(String name) {
        return name == null || name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(ES);
    }
}
