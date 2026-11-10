package com.aeriq.app.ui;

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

/** Pantallas del diseño Android de Figma: Aprende, Historial, Configurar, Comparar, Premium y Cuenta. */
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

    private LinearLayout panel(LinearLayout body, int padding) {
        LinearLayout panel = ui.column();
        panel.setBackground(ui.shape(WHITE, 24, LINE));
        panel.setPadding(ui.dp(padding), ui.dp(padding), ui.dp(padding), ui.dp(padding));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(14);
        body.addView(panel, params);
        return panel;
    }

    private TextView text(LinearLayout parent, String value, int size, boolean bold, int color, int top) {
        TextView view = ui.text(value, size, bold, color);
        view.setPadding(0, ui.dp(top), 0, 0);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private ImageView tile(int icon, int size, int background, int tint) {
        ImageView image = ui.image(icon, size, size);
        int pad = Math.round(size * .26f);
        image.setPadding(ui.dp(pad), ui.dp(pad), ui.dp(pad), ui.dp(pad));
        image.setBackground(ui.shape(background, 12, 0));
        image.setColorFilter(tint);
        return image;
    }

    private View divider() {
        View line = new View(activity);
        line.setBackgroundColor(LINE);
        return line;
    }

    private void addDivider(LinearLayout parent, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(1));
        params.setMargins(0, ui.dp(top), 0, ui.dp(bottom));
        parent.addView(divider(), params);
    }

    private TextView chip(String label, int background, int color) {
        TextView chip = ui.text(label, 13, true, color);
        chip.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5));
        chip.setBackground(ui.shape(background, 16, 0));
        return chip;
    }

    private CharSequence underline(String value) {
        SpannableString text = new SpannableString(value);
        text.setSpan(new UnderlineSpan(), 0, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    /** Selector de pestañas: fondo gris, pestaña activa blanca. */
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
            strip.addView(item, new LinearLayout.LayoutParams(0, ui.dp(40), 1));
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

    private void select(TextView[] items, int active) {
        for (int i = 0; i < items.length; i++) {
            items[i].setTextColor(i == active ? INK : MUTED);
            items[i].setBackground(i == active ? ui.shape(WHITE, 12, 0) : null);
            items[i].setElevation(i == active ? ui.dp(1) : 0);
        }
    }

    /** Fila de menú: icono en baldosa, título, subtítulo opcional y elemento final. */
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

    private ImageView chevron() {
        ImageView image = ui.image(R.drawable.ic_chevron, 18, 18);
        image.setColorFilter(MUTED);
        image.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        return image;
    }

    private LinearLayout darkPromo(LinearLayout body, String title, String subtitle, Runnable click) {
        LinearLayout card = ui.row();
        card.setBackground(ui.shape(DARK, 22, 0));
        card.setPadding(ui.dp(14), ui.dp(16), ui.dp(18), ui.dp(16));
        card.addView(tile(R.drawable.ic_sparkles, 40, 0xff36403c, WHITE), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
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

    private void footer(LinearLayout body, String value) {
        TextView note = ui.text(value, 12, false, MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(8));
        body.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    // ================================================================= Aprende

    public void learn(LinearLayout body) {
        LinearLayout hero = ui.column();
        hero.setBackground(ui.shape(GREEN, 26, 0));
        hero.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(22));
        text(hero, "Aprende", 13, false, 0xccffffff, 0);
        TextView title = text(hero, "Un aire limpio empieza por conocerlo.", 28, true, WHITE, 8);
        title.setLineSpacing(0, .95f);
        text(hero, "Qué contamina, cómo afecta a tu salud y qué dice la normativa.", 14, false, 0xe6ffffff, 8);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.bottomMargin = ui.dp(16);
        body.addView(hero, heroParams);

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
        law.setText(underline("Ver la normativa ↗"));
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

        LinearLayout export = ui.row();
        export.setGravity(Gravity.CENTER);
        export.setBackground(ui.shape(GREEN, 18, 0));
        ImageView download = ui.image(R.drawable.ic_download, 22, 22);
        download.setColorFilter(WHITE);
        export.addView(download, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        TextView exportLabel = ui.text("Exportar datos (CSV o PDF)", 16, true, WHITE);
        exportLabel.setPadding(ui.dp(10), 0, 0, 0);
        export.addView(exportLabel);
        export.setAlpha(rows.isEmpty() ? .5f : 1f);
        export.setOnClickListener(view -> {
            if (rows.isEmpty()) { activity.message("No hay mediciones que exportar en este periodo."); return; }
            new android.app.AlertDialog.Builder(activity).setTitle("Exportar datos")
                .setItems(new String[]{"CSV (hoja de cálculo)", "PDF (informe)"}, (dialog, which) -> {
                    if (which == 0) activity.export(rows); else activity.exportPdf(rows, header);
                }).show();
        });
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(-1, ui.dp(56));
        exportParams.topMargin = ui.dp(4);
        exportParams.bottomMargin = ui.dp(14);
        content.addView(export, exportParams);
        darkPromo(content, "Compara tu zona con el resto de la red", "Históricos ampliados con Premium",
            () -> activity.go(Screen.COMPARE));
    }

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

    // ================================================================= Configurar sensor

    public void settings(LinearLayout body, AeriqBleController ble, OrbAssignment sensor, boolean sharing, AppPrefs prefs) {
        LinearLayout state = panel(body, 18);
        LinearLayout head = ui.row();
        head.addView(ui.text("Estado del sensor", 18, true, INK), new LinearLayout.LayoutParams(0, -2, 1));
        TextView connected = chip("● Conectado", PANEL_SOFT, GREEN);
        head.addView(connected);
        state.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView connection = infoRow(state, "Conexión", "Bluetooth LE", true);
        TextView battery = infoRow(state, "Batería", "—", true);
        TextView signal = infoRow(state, "Señal", "—", true);
        TextView sync = infoRow(state, "Última sincronización", "—", true);
        infoRow(state, "Sensor", "SPEC O₃", false);

        LinearLayout naming = panel(body, 18);
        text(naming, "Nombre del sensor", 18, true, INK, 0);
        text(naming, "Nombre", 13, true, INK, 14);
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setTextSize(15);
        name.setTextColor(INK);
        name.setHintTextColor(0xff8a9792);
        name.setHint(sensor == null ? "Vincula un sensor primero" : "Sensor PBIO");
        name.setText(sensor == null ? "" : activity.sensorName(sensor));
        name.setEnabled(sensor != null);
        name.setBackground(ui.shape(FIELD, 14, LINE));
        name.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, ui.dp(52));
        nameParams.topMargin = ui.dp(8);
        naming.addView(name, nameParams);
        TextView save = ui.text("Guardar nombre", 15, true, INK);
        save.setGravity(Gravity.CENTER);
        save.setBackground(ui.shape(WHITE, 16, 0xffd5dcd9));
        save.setAlpha(sensor == null ? .5f : 1f);
        save.setOnClickListener(view -> {
            if (sensor == null) { activity.message("Vincula un sensor primero."); return; }
            String value = name.getText().toString().trim();
            if (value.isEmpty() || value.length() > 80) { activity.message("Usa un nombre de entre 1 y 80 caracteres."); return; }
            prefs.setAlias(sensor.address, value);
            activity.message("Nombre guardado en este móvil.");
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, ui.dp(48));
        saveParams.topMargin = ui.dp(12);
        naming.addView(save, saveParams);

        LinearLayout share = panel(body, 18);
        LinearLayout shareHead = ui.row();
        shareHead.setGravity(Gravity.TOP);
        LinearLayout shareTexts = ui.column();
        shareTexts.addView(ui.text("Compartir con la red", 18, true, INK));
        TextView optional = chip("Opcional", CHIP, MUTED);
        LinearLayout.LayoutParams optionalParams = new LinearLayout.LayoutParams(-2, -2);
        optionalParams.topMargin = ui.dp(8);
        shareTexts.addView(optional, optionalParams);
        shareHead.addView(shareTexts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch shareToggle = ui.toggle(sharing);
        shareToggle.setEnabled(sensor != null);
        shareToggle.setOnCheckedChangeListener((button, checked) -> activity.setSharing(checked));
        shareHead.addView(shareToggle);
        share.addView(shareHead, new LinearLayout.LayoutParams(-1, -2));
        TextView explain = text(share, "Tus mediciones ayudan a mejorar el mapa público. Compartes ozono, fecha y zona "
            + "aproximada. Nunca tu recorrido exacto.", 14, false, MUTED, 14);
        explain.setLineSpacing(ui.dp(2), 1);
        addDivider(share, 14, 12);
        LinearLayout route = ui.row();
        LinearLayout routeTexts = ui.column();
        routeTexts.addView(ui.text("Guardar mi recorrido y distancia", 15, true, INK));
        routeTexts.addView(ui.text("Solo lo ves tú. No se publica.", 12, false, MUTED));
        route.addView(routeTexts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch routeToggle = ui.toggle(prefs.saveRoute());
        routeToggle.setOnCheckedChangeListener((button, checked) -> prefs.setSaveRoute(checked));
        route.addView(routeToggle);
        share.addView(route, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout reconnect = wideButton(R.drawable.ic_refresh, "Reconectar sensor", INK, WHITE, 0xffd5dcd9);
        reconnect.setOnClickListener(view -> activity.reconnectSensor());
        LinearLayout.LayoutParams reconnectParams = new LinearLayout.LayoutParams(-1, ui.dp(54));
        reconnectParams.bottomMargin = ui.dp(12);
        body.addView(reconnect, reconnectParams);
        LinearLayout unlink = wideButton(R.drawable.ic_unlink, "Desvincular sensor", RED, 0xfffff7f6, 0xffefc3c0);
        unlink.setOnClickListener(view -> {
            if (sensor == null) { activity.message("No hay ningún sensor vinculado."); return; }
            new android.app.AlertDialog.Builder(activity).setTitle("Desvincular sensor")
                .setMessage("Este móvil dejará de recibir las mediciones de " + activity.sensorName(sensor)
                    + ". Podrás volver a vincularlo cuando quieras.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desvincular", (dialog, which) -> { activity.unlink(); activity.go(Screen.LINK); })
                .show();
        });
        LinearLayout.LayoutParams unlinkParams = new LinearLayout.LayoutParams(-1, ui.dp(54));
        unlinkParams.bottomMargin = ui.dp(12);
        body.addView(unlink, unlinkParams);
        footer(body, "Puedes cambiar estas opciones cuando quieras. Las funciones esenciales del sensor no se bloquean.");

        activity.updateLive(() -> {
            long age = System.currentTimeMillis() - ble.lastAdvertisementAt;
            boolean live = sensor != null && ble.lastAdvertisementAt > 0 && age <= 15_000;
            connected.setText(live ? "● Conectado" : sensor == null ? "Sin sensor" : "● Sin conexión");
            connected.setTextColor(live ? GREEN : MUTED);
            connected.setBackground(ui.shape(live ? PANEL_SOFT : CHIP, 16, 0));
            connection.setText(ble.bluetoothEnabled() ? "Bluetooth LE" : "Bluetooth desactivado");
            battery.setText(ble.batteryPercent >= 0 ? ble.batteryPercent + " %" : "—");
            Integer rssi = null;
            if (sensor != null) for (AeriqBleController.FoundDevice device : ble.devices())
                if (device.address.equalsIgnoreCase(sensor.address)) rssi = device.rssi;
            signal.setText(rssi == null || !live ? "—" : rssi >= -65 ? "Fuerte" : rssi >= -80 ? "Media" : "Débil");
            sync.setText(sensor == null || ble.lastAdvertisementAt <= 0 ? "—" : "Hace " + Quality.ago(age));
        });
    }

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

    public void compare(LinearLayout body, ExecutorService worker) {
        LinearLayout badge = ui.row();
        badge.setPadding(0, 0, 0, ui.dp(14));
        LinearLayout premium = ui.row();
        premium.setBackground(ui.shape(DARK, 16, 0));
        premium.setPadding(ui.dp(10), ui.dp(6), ui.dp(12), ui.dp(6));
        ImageView sparkles = ui.image(R.drawable.ic_sparkles, 16, 16);
        sparkles.setColorFilter(WHITE);
        premium.addView(sparkles, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
        TextView premiumLabel = ui.text("Premium", 13, true, WHITE);
        premiumLabel.setPadding(ui.dp(6), 0, 0, 0);
        premium.addView(premiumLabel);
        badge.addView(premium);
        TextView subtitle = ui.text("Análisis ampliado de la red", 13, false, MUTED);
        subtitle.setPadding(ui.dp(10), 0, 0, 0);
        badge.addView(subtitle);
        body.addView(badge, new LinearLayout.LayoutParams(-1, -2));

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
                List<PublicReadings.Reading> rows = PublicReadings.fetch(false).rows;
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

    private static String capitalize(String value) {
        return value.isEmpty() ? value : value.substring(0, 1).toUpperCase(ES) + value.substring(1);
    }

    // ================================================================= Premium

    public void premium(LinearLayout body) {
        LinearLayout hero = ui.column();
        hero.setBackground(ui.shape(DARK, 26, 0));
        hero.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(22));
        LinearLayout badge = ui.row();
        badge.setBackground(ui.shape(0xff36403c, 14, 0));
        badge.setPadding(ui.dp(10), ui.dp(5), ui.dp(12), ui.dp(5));
        ImageView sparkles = ui.image(R.drawable.ic_sparkles, 16, 16);
        sparkles.setColorFilter(WHITE);
        badge.addView(sparkles, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
        TextView badgeLabel = ui.text("Premium", 13, true, WHITE);
        badgeLabel.setPadding(ui.dp(6), 0, 0, 0);
        badge.addView(badgeLabel);
        hero.addView(badge, new LinearLayout.LayoutParams(-2, -2));
        TextView title = text(hero, "Entiende el ozono de toda tu ciudad.", 28, true, WHITE, 14);
        title.setLineSpacing(0, .95f);
        text(hero, "Funciones de análisis para quien quiere ir más allá de sus propias mediciones.", 14, false, 0xffb7c2be, 10);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.bottomMargin = ui.dp(14);
        body.addView(hero, heroParams);

        LinearLayout features = panel(body, 18);
        for (String feature : new String[]{"Comparación de zonas de la red", "Históricos ampliados de la red",
            "Informes automáticos", "Una cuenta para la app y la web"}) {
            LinearLayout row = ui.row();
            row.setPadding(0, ui.dp(6), 0, ui.dp(6));
            row.addView(tile(R.drawable.ic_tick, 22, PANEL_SOFT, GREEN), new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
            TextView label = ui.text(feature, 15, false, INK);
            label.setPadding(ui.dp(12), 0, 0, 0);
            row.addView(label);
            features.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }

        String[] plan = {"anual"};
        LinearLayout annual = planCard(body, "Anual", "Equivale a 10 mensualidades", "39,90 €", "/año");
        LinearLayout monthly = planCard(body, "Mensual", "Cancela cuando quieras", "3,99 €", "/mes");
        Runnable refresh = () -> {
            stylePlan(annual, "anual".equals(plan[0]));
            stylePlan(monthly, "mensual".equals(plan[0]));
        };
        annual.setOnClickListener(view -> { plan[0] = "anual"; refresh.run(); });
        monthly.setOnClickListener(view -> { plan[0] = "mensual"; refresh.run(); });
        refresh.run();

        LinearLayout terms = panel(body, 16);
        terms.addView(boldLead("Renovación y cancelación.", " Se cobra al inicio de cada periodo. Si cancelas, Premium "
            + "sigue activo hasta el final del periodo pagado."));
        TextView secure = boldLead("Pago seguro.", " Lo gestiona un proveedor de pagos externo: AERIQ no guarda los datos de tu tarjeta.");
        secure.setPadding(0, ui.dp(10), 0, 0);
        terms.addView(secure);

        TextView pay = ui.text("Continuar al pago seguro", 16, true, WHITE);
        pay.setGravity(Gravity.CENTER);
        pay.setBackground(ui.shape(GREEN, 18, 0));
        pay.setOnClickListener(view -> activity.open(ApiRoutes.BASE + "checkout.html?service=premium&plan=" + plan[0]));
        LinearLayout.LayoutParams payParams = new LinearLayout.LayoutParams(-1, ui.dp(56));
        payParams.bottomMargin = ui.dp(14);
        body.addView(pay, payParams);
        footer(body, "El mapa público, los avisos y las funciones esenciales de tu sensor siguen gratis, con o sin Premium.");
    }

    private TextView boldLead(String lead, String rest) {
        android.text.SpannableStringBuilder value = new android.text.SpannableStringBuilder(lead + rest);
        value.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, lead.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setSpan(new android.text.style.ForegroundColorSpan(INK), 0, lead.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView view = ui.text("", 13, false, 0xff3b4642);
        view.setText(value);
        view.setLineSpacing(ui.dp(2), 1);
        return view;
    }

    private LinearLayout planCard(LinearLayout body, String name, String detail, String price, String period) {
        LinearLayout card = ui.row();
        card.setPadding(ui.dp(16), ui.dp(18), ui.dp(18), ui.dp(18));
        View radio = new View(activity);
        card.addView(radio, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        LinearLayout texts = ui.column();
        texts.setPadding(ui.dp(14), 0, ui.dp(8), 0);
        texts.addView(ui.text(name, 17, true, INK));
        texts.addView(ui.text(detail, 12, false, MUTED));
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView amount = ui.text(price, 22, true, INK);
        card.addView(amount);
        TextView per = ui.text(period, 13, false, MUTED);
        per.setPadding(ui.dp(2), ui.dp(6), 0, 0);
        card.addView(per);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(12);
        body.addView(card, params);
        return card;
    }

    private void stylePlan(LinearLayout card, boolean selected) {
        GradientDrawable background = ui.shape(selected ? PANEL_SOFT : WHITE, 22, 0);
        background.setStroke(ui.dp(selected ? 2 : 1), selected ? GREEN : LINE);
        card.setBackground(background);
        GradientDrawable radio = new GradientDrawable();
        radio.setShape(GradientDrawable.OVAL);
        radio.setColor(selected ? GREEN : WHITE);
        radio.setStroke(ui.dp(selected ? 5 : 2), selected ? WHITE : 0xffb9c3bf);
        View dot = card.getChildAt(0);
        if (selected) {
            android.graphics.drawable.LayerDrawable ring = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                oval(GREEN), oval(WHITE), oval(GREEN)});
            ring.setLayerInset(1, ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2));
            ring.setLayerInset(2, ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(6));
            dot.setBackground(ring);
        } else dot.setBackground(radio);
    }

    private GradientDrawable oval(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        return shape;
    }

    // ================================================================= Cuenta

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
        details.addView(chip(user.organization.isEmpty() ? "Plan gratuito" : user.organization, CHIP, MUTED),
            new LinearLayout.LayoutParams(-2, -2));
        identity.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        profile.addView(identity, new LinearLayout.LayoutParams(-1, -2));

        darkPromo(body, "Hazte Premium", "Compara zonas y consulta históricos de la red", () -> activity.go(Screen.PREMIUM));

        LinearLayout data = panel(body, 16);
        menuRow(data, R.drawable.figma_nav_user, GREEN, PANEL_SOFT, "Datos personales", INK, null, chevron(),
            () -> activity.go(Screen.EDIT_PROFILE));
        data.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        menuRow(data, R.drawable.figma_nav_chip, GREEN, PANEL_SOFT, "Mi sensor",
            INK, sensor == null ? "Sin sensor vinculado" : activity.sensorName(sensor) + " · Vinculado", chevron(),
            () -> activity.go(Screen.SENSOR));

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
                activity.message("Acceso biométrico desactivado para esta cuenta.");
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
                    activity.message("Acceso biométrico vinculado a esta cuenta.");
                }
            });
        });
        menuRow(security, R.drawable.ic_fingerprint, GREEN, PANEL_SOFT, "Acceso con huella", INK, null, fingerprint, null);
        security.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        Switch alerts = ui.toggle(prefs.ozoneAlerts());
        alerts.setOnCheckedChangeListener((button, checked) -> prefs.setOzoneAlerts(checked));
        menuRow(security, R.drawable.ic_bell, GREEN, PANEL_SOFT, "Avisos de ozono", INK,
            "Cuando suba el nivel en tu zona", alerts, null);

        LinearLayout privacy = panel(body, 16);
        menuRow(privacy, R.drawable.ic_users, GREEN, PANEL_SOFT, "Compartir con la red", INK,
            "Opcional · " + (sharing ? "Activado" : "Desactivado"), chevron(), () -> activity.go(Screen.CONFIG));
        privacy.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        menuRow(privacy, R.drawable.ic_download, GREEN, PANEL_SOFT, "Exportar mis datos", INK, null, chevron(),
            () -> activity.go(Screen.HISTORY));
        privacy.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        menuRow(privacy, R.drawable.ic_shield, GREEN, PANEL_SOFT, "Privacidad y ubicación", INK, null, chevron(),
            () -> activity.go(Screen.CONFIG));

        LinearLayout help = panel(body, 16);
        ImageView external = ui.image(R.drawable.ic_external, 20, 20);
        external.setColorFilter(MUTED);
        external.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        menuRow(help, R.drawable.ic_package, GREEN, PANEL_SOFT, "Mis pedidos", INK, "Se gestionan en la web", external,
            () -> activity.open(ApiRoutes.BASE + "perfil.html"));
        help.addView(divider(), new LinearLayout.LayoutParams(-1, ui.dp(1)));
        menuRow(help, R.drawable.ic_help, GREEN, PANEL_SOFT, "Ayuda y contacto", INK, null, chevron(),
            () -> activity.go(Screen.SERVICES));

        LinearLayout logout = panel(body, 16);
        menuRow(logout, R.drawable.ic_logout, RED, 0xfffde8e7, "Cerrar sesión", RED, null, null, activity::logout);
    }

    private String initial(String name) {
        return name == null || name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(ES);
    }
}
