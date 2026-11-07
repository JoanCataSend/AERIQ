package com.aeriq.app.ui;

import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.aeriq.app.MainActivity;
import com.aeriq.app.MainActivity.Screen;
import com.aeriq.app.R;
import com.aeriq.app.data.ForegroundCaptureLocation;
import com.aeriq.app.data.PlaceSearch;
import com.aeriq.app.data.PublicReadings;
import com.aeriq.app.data.PublicReadings.Reading;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.aeriq.app.ui.Views.*;

/** Mapa de ozono: mapa real (OpenStreetMap), búsqueda mundial y ficha de la zona elegida. */
public final class PublicMapScreen {
    private static final GeoPoint CAMPUS = new GeoPoint(38.99665, -0.16601);
    private static final double NEAR_ZONE_METERS = 3000;
    private final MainActivity activity;
    private final Views ui;
    private final ExecutorService worker;
    private final ForegroundCaptureLocation location;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MapView map;
    private final EditText query;
    private final LinearLayout suggestions;
    private final LinearLayout warning;
    private final TextView warningText;
    private final CheckBox markers, zones;
    // Ficha inferior
    private final View zoneDot;
    private final TextView zoneTitle, zoneSubtitle, zoneValue, zoneUnit, zoneChip, zoneUpdated;
    private List<Reading> readings = Collections.emptyList();
    private int request, searchRequest;
    private boolean closed, suppressSuggestions;
    private Marker locationMarker;
    private Runnable pendingSearch;

    public PublicMapScreen(MainActivity activity, Views ui, FrameLayout stage, ExecutorService worker) {
        this.activity = activity;
        this.ui = ui;
        this.worker = worker;
        location = new ForegroundCaptureLocation(activity);
        Configuration.getInstance().setUserAgentValue("AERIQ-Android/1.0 (" + activity.getPackageName() + ")");

        // ---------- Mapa real a pantalla completa con controles flotantes ----------
        FrameLayout frame = stage;
        frame.setBackgroundColor(SOFT);
        map = new MapView(activity);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER);
        map.setTilesScaledToDpi(true);
        map.getController().setZoom(15.0);
        map.getController().setCenter(CAMPUS);
        map.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            view.getParent().requestDisallowInterceptTouchEvent(action != android.view.MotionEvent.ACTION_UP
                && action != android.view.MotionEvent.ACTION_CANCEL);
            if (action == android.view.MotionEvent.ACTION_DOWN) hideSuggestions();
            return false;
        });
        frame.addView(map, new FrameLayout.LayoutParams(-1, -1));

        // Buscador (cualquier ciudad, pueblo o dirección del mundo)
        LinearLayout searchBar = ui.row();
        searchBar.setBackground(ui.shape(WHITE, 20, 0));
        searchBar.setElevation(ui.dp(4));
        searchBar.setPadding(ui.dp(16), 0, ui.dp(8), 0);
        ImageView searchIcon = ui.image(R.drawable.ic_search, 22, 22);
        searchIcon.setColorFilter(MUTED);
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)));
        query = new EditText(activity);
        query.setSingleLine(true);
        query.setBackground(null);
        query.setHint("Buscar ciudad o dirección…");
        query.setHintTextColor(MUTED);
        query.setTextColor(INK);
        query.setTextSize(16);
        query.setPadding(ui.dp(12), 0, ui.dp(4), 0);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        searchBar.addView(query, new LinearLayout.LayoutParams(0, ui.dp(56), 1));
        frame.addView(searchBar, frameParams(-1, ui.dp(56), Gravity.TOP, 12, 12, 12, 0));

        // Aviso según el ozono de la zona seleccionada (solo moderado o alto)
        warning = ui.row();
        warning.setBackground(ui.shape(0xfffdf3df, 18, 0xfff1d49b));
        warning.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        ImageView alert = ui.image(R.drawable.ic_alert, 20, 20);
        alert.setColorFilter(Quality.MEDIUM_TEXT);
        warning.addView(alert, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        warningText = ui.text("", 14, false, 0xff4a3a1c);
        warningText.setPadding(ui.dp(12), 0, 0, 0);
        warning.addView(warningText, new LinearLayout.LayoutParams(0, -2, 1));
        warning.setVisibility(View.GONE);
        frame.addView(warning, frameParams(-1, -2, Gravity.TOP, 12, 78, 12, 0));

        // Sugerencias del buscador (encima del aviso)
        suggestions = ui.column();
        suggestions.setBackground(ui.shape(WHITE, 18, LINE));
        suggestions.setElevation(ui.dp(6));
        suggestions.setVisibility(View.GONE);
        frame.addView(suggestions, frameParams(-1, -2, Gravity.TOP, 12, 74, 12, 0));

        // Controles: mi ubicación, capas, actualizar
        LinearLayout controls = ui.column();
        controls.addView(control(R.drawable.ic_target, "Mostrar mi ubicación", () -> {
            if (location.allowed()) locate();
            else activity.locateMap();
        }));
        controls.addView(control(R.drawable.ic_layers, "Capas del mapa", this::showLayers));
        controls.addView(control(R.drawable.ic_refresh, "Actualizar mediciones", this::load));

        // Leyenda en la parte de abajo del mapa
        TextView legend = ui.text("", 14, false, INK);
        legend.setText(legendText());
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setBackground(ui.shape(WHITE, 22, 0));
        legend.setElevation(ui.dp(3));
        legend.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));

        // Fila flotante encima del panel: leyenda a la izquierda y botones a la derecha.
        // Va dentro del mismo grupo que el panel para subir y bajar con él.
        LinearLayout floating = ui.row();
        floating.setGravity(Gravity.BOTTOM);
        floating.setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(12));
        LinearLayout legendBox = ui.row();
        legendBox.setGravity(Gravity.BOTTOM);
        legendBox.addView(legend);
        floating.addView(legendBox, new LinearLayout.LayoutParams(0, -2, 1));
        floating.addView(controls);

        // ---------- Capas ----------
        layers = ui.column();
        layers.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
        zones = toggle(layers, "Zonas coloreadas (interpolación 20–50 m)", true);
        markers = toggle(layers, "Puntos de medición", true);
        CheckBox dark = toggle(layers, "Mapa oscuro", false);
        dark.setOnCheckedChangeListener((button, checked) -> {
            map.getOverlayManager().getTilesOverlay().setColorFilter(checked
                ? new ColorMatrixColorFilter(new float[]{-.65f,0,0,0,175, 0,-.65f,0,0,185, 0,0,-.65f,0,183, 0,0,0,1,0})
                : null);
            map.invalidate();
        });
        zones.setOnCheckedChangeListener((button, checked) -> draw());
        markers.setOnCheckedChangeListener((button, checked) -> draw());

        // ---------- Ficha de la zona ----------
        // Panel inferior arrastrable: plegado solo enseña la rayita y la zona; desplegado, todo.
        LinearLayout sheet = ui.column();
        GradientDrawable sheetBackground = new GradientDrawable();
        sheetBackground.setColor(WHITE);
        float corner = ui.dp(26);
        sheetBackground.setCornerRadii(new float[]{corner, corner, corner, corner, 0, 0, 0, 0});
        sheet.setBackground(sheetBackground);
        sheet.setElevation(ui.dp(10));
        sheet.setPadding(ui.dp(18), ui.dp(10), ui.dp(18), ui.dp(18));
        grip = ui.column();
        View handle = new View(activity);
        handle.setBackground(ui.shape(0xffd5dcd9, 3, 0));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ui.dp(44), ui.dp(5));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = ui.dp(14);
        grip.addView(handle, handleParams);
        grip.setContentDescription("Arrastra hacia arriba para ver la zona");

        LinearLayout top = ui.row();
        zoneDot = new View(activity);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16));
        dotParams.rightMargin = ui.dp(14);
        top.addView(zoneDot, dotParams);
        LinearLayout names = ui.column();
        zoneTitle = ui.text("Cargando mediciones…", 20, true, INK);
        zoneSubtitle = ui.text("Red colaborativa", 14, false, MUTED);
        names.addView(zoneTitle);
        names.addView(zoneSubtitle);
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout valueBox = ui.row();
        valueBox.setGravity(Gravity.BOTTOM);
        zoneValue = ui.text("—", 44, true, INK);
        zoneValue.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        zoneValue.setIncludeFontPadding(false);
        zoneUnit = ui.text("", 13, false, MUTED);
        zoneUnit.setPadding(ui.dp(4), 0, 0, ui.dp(4));
        valueBox.addView(zoneValue);
        valueBox.addView(zoneUnit);
        top.addView(valueBox);
        top.setPadding(0, 0, 0, ui.dp(8));
        grip.addView(top, new LinearLayout.LayoutParams(-1, -2));
        sheet.addView(grip, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout meta = ui.row();
        meta.setPadding(0, ui.dp(8), 0, ui.dp(16));
        zoneChip = ui.text("Calidad: —", 14, true, MUTED);
        zoneChip.setPadding(ui.dp(14), ui.dp(7), ui.dp(14), ui.dp(7));
        zoneChip.setBackground(ui.shape(0xffeff3f1, 18, 0));
        meta.addView(zoneChip);
        zoneUpdated = ui.text("", 13, false, MUTED);
        zoneUpdated.setGravity(Gravity.END);
        meta.addView(zoneUpdated, new LinearLayout.LayoutParams(0, -2, 1));
        sheet.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = ui.row();
        LinearLayout history = ui.row();
        history.setGravity(Gravity.CENTER);
        history.setBackground(ui.shape(WHITE, 18, 0xffd5dcd9));
        ImageView chart = ui.image(R.drawable.ic_chart, 20, 20);
        chart.setColorFilter(INK);
        history.addView(chart, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        TextView historyLabel = ui.text("Ver historial", 15, true, INK);
        historyLabel.setPadding(ui.dp(10), 0, 0, 0);
        history.addView(historyLabel);
        history.setOnClickListener(view -> activity.go(Screen.HISTORY));
        actions.addView(history, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        actions.addView(new View(activity), new LinearLayout.LayoutParams(ui.dp(10), 1));
        LinearLayout compare = ui.row();
        compare.setGravity(Gravity.CENTER);
        compare.setBackground(ui.shape(DARK, 18, 0));
        ImageView sparkles = ui.image(R.drawable.ic_sparkles, 20, 20);
        sparkles.setColorFilter(WHITE);
        compare.addView(sparkles, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        TextView compareLabel = ui.text("Comparar", 15, true, WHITE);
        compareLabel.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        compare.addView(compareLabel);
        TextView premium = ui.text("Premium", 11, true, INK);
        premium.setPadding(ui.dp(8), ui.dp(3), ui.dp(8), ui.dp(3));
        premium.setBackground(ui.shape(WHITE, 12, 0));
        compare.addView(premium);
        compare.setOnClickListener(view -> activity.go(Screen.COMPARE));
        actions.addView(compare, new LinearLayout.LayoutParams(0, ui.dp(54), 1));
        sheet.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        TextView disclaimer = ui.text("Mediciones complementarias: no sustituyen a las estaciones oficiales de vigilancia. "
            + "Mapa © OpenStreetMap contributors.", 12, false, MUTED);
        disclaimer.setPadding(0, ui.dp(14), 0, 0);
        sheet.addView(disclaimer);

        bottom = ui.column();
        bottom.addView(floating, new LinearLayout.LayoutParams(-1, -2));
        bottom.addView(sheet, new LinearLayout.LayoutParams(-1, -2));
        frame.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        sheet.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int offset = Math.max(0, sheet.getHeight() - grip.getBottom());
            if (offset == hiddenOffset) return;
            hiddenOffset = offset;
            bottom.setTranslationY(expanded ? 0 : hiddenOffset);
        });
        grip.setOnTouchListener(this::dragSheet);

        // ---------- Búsqueda ----------
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                if (suppressSuggestions) return;
                if (pendingSearch != null) main.removeCallbacks(pendingSearch);
                String term = text.toString().trim();
                if (term.length() < 2) { hideSuggestions(); return; }
                pendingSearch = () -> suggest(term, false);
                main.postDelayed(pendingSearch, 450);
            }
        });
        query.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            if (pendingSearch != null) main.removeCallbacks(pendingSearch);
            suggest(query.getText().toString(), true);
            return true;
        });

        showZone(null, null);
        load();
    }

    private LinearLayout layers;
    private LinearLayout grip, bottom;
    private int hiddenOffset;
    private boolean expanded, dragging;
    private float downY, startTranslation;
    private long downTime;

    /** Arrastre del panel: sigue al dedo y al soltar se queda abierto o cerrado. Un toque lo alterna. */
    private boolean dragSheet(View view, android.view.MotionEvent event) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downY = event.getRawY();
                downTime = event.getEventTime();
                startTranslation = bottom.getTranslationY();
                dragging = false;
                bottom.animate().cancel();
                hideSuggestions();
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                float delta = event.getRawY() - downY;
                if (!dragging && Math.abs(delta) > ui.dp(6)) dragging = true;
                if (dragging) bottom.setTranslationY(Math.max(0, Math.min(hiddenOffset, startTranslation + delta)));
                return true;
            }
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL: {
                if (!dragging) { setExpanded(!expanded); return true; }
                float delta = event.getRawY() - downY;
                long time = Math.max(1, event.getEventTime() - downTime);
                float speed = delta / time; // px/ms; negativo = hacia arriba
                boolean open = speed < -0.5f || (speed <= 0.5f && bottom.getTranslationY() < hiddenOffset / 2f);
                setExpanded(open);
                return true;
            }
            default:
                return false;
        }
    }

    private void setExpanded(boolean open) {
        expanded = open;
        bottom.animate().translationY(open ? 0 : hiddenOffset).setDuration(220)
            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private FrameLayout.LayoutParams frameParams(int width, int height, int gravity, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
        params.setMargins(ui.dp(left), ui.dp(top), ui.dp(right), ui.dp(bottom));
        return params;
    }

    private View control(int icon, String description, Runnable click) {
        ImageView button = ui.image(icon, 52, 52);
        button.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        button.setColorFilter(INK);
        button.setBackground(ui.shape(WHITE, 18, 0));
        button.setElevation(ui.dp(4));
        button.setContentDescription(description);
        button.setOnClickListener(view -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52));
        params.bottomMargin = ui.dp(10);
        button.setLayoutParams(params);
        return button;
    }

    private CharSequence legendText() {
        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append("Leyenda");
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        String[] labels = {"Bajo", "Moderado", "Alto"};
        int[] colors = {Quality.LOW, Quality.MEDIUM, Quality.HIGH};
        for (int i = 0; i < labels.length; i++) {
            text.append("   ");
            int start = text.length();
            text.append("●");
            text.setSpan(new ForegroundColorSpan(colors[i]), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(" ").append(labels[i]);
        }
        return text;
    }

    private void showLayers() {
        if (layers.getParent() != null) ((android.view.ViewGroup) layers.getParent()).removeView(layers);
        new android.app.AlertDialog.Builder(activity).setTitle("Capas del mapa").setView(layers)
            .setPositiveButton("Cerrar", null).show();
    }

    private CheckBox toggle(LinearLayout parent, String label, boolean checked) {
        CheckBox button = new CheckBox(activity);
        button.setText(label);
        button.setChecked(checked);
        button.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));
        parent.addView(button);
        return button;
    }

    // ---------------------------------------------------------------- datos

    private void load() {
        int generation = ++request;
        worker.execute(() -> {
            try {
                PublicReadings.Result result = PublicReadings.fetch(false);
                activity.runOnUiThread(() -> {
                    if (closed || generation != request) return;
                    readings = result.rows;
                    draw();
                    if (!readings.isEmpty()) {
                        Reading latest = readings.get(0);
                        for (Reading reading : readings) if (reading.date.compareTo(latest.date) > 0) latest = reading;
                        if (locationMarker == null) map.getController().setCenter(latest.position());
                        showZone(latest.zone, null);
                    } else showZone(null, null);
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (closed || generation != request) return;
                    zoneTitle.setText("Sin conexión");
                    zoneSubtitle.setText("No se pudieron cargar las mediciones: " + error.getMessage());
                });
            }
        });
    }

    private void draw() {
        map.getOverlays().clear();
        if (zones.isChecked()) drawZones();
        if (markers.isChecked()) for (Reading reading : readings) {
            Marker marker = new Marker(map);
            marker.setPosition(reading.position());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            GradientDrawable icon = new GradientDrawable();
            icon.setShape(GradientDrawable.OVAL);
            icon.setColor(reading.color(reading.value));
            icon.setStroke(ui.dp(3), Color.WHITE);
            icon.setSize(ui.dp(18), ui.dp(18));
            marker.setIcon(icon);
            marker.setOnMarkerClickListener((selected, view) -> {
                showZone(reading.zone, null);
                setExpanded(true);
                return true;
            });
            map.getOverlays().add(marker);
        }
        if (locationMarker != null) map.getOverlays().add(locationMarker);
        map.invalidate();
    }

    private void drawZones() {
        Map<String, List<Reading>> groups = new LinkedHashMap<>();
        for (Reading reading : readings) {
            groups.computeIfAbsent(reading.campaign + "/" + reading.unit, key -> new ArrayList<>()).add(reading);
        }
        int drawn = 0, examined = 0;
        for (List<Reading> group : groups.values()) {
            Reading first = group.get(0);
            double latStep = 10.0 / 111320;
            double lonStep = latStep / Math.max(0.01, Math.cos(Math.toRadians(first.latitude)));
            Set<String> visited = new HashSet<>();
            Map<String, List<Reading>> buckets = new HashMap<>();
            for (Reading reading : group) {
                long x = (long) Math.floor(reading.latitude / (latStep * 6));
                long y = (long) Math.floor(reading.longitude / (lonStep * 6));
                buckets.computeIfAbsent(x + "/" + y, key -> new ArrayList<>()).add(reading);
            }
            for (Reading reading : group) {
                long a = Math.round(reading.latitude / latStep), b = Math.round(reading.longitude / lonStep);
                for (int i = -5; i <= 5; i++) for (int j = -5; j <= 5; j++) {
                    if (!visited.add((a + i) + "/" + (b + j))) continue;
                    if (++examined > 5000 || drawn >= 1500) return;
                    double lat = (a + i) * latStep, lon = (b + j) * lonStep;
                    long x = (long) Math.floor(lat / (latStep * 6)), y = (long) Math.floor(lon / (lonStep * 6));
                    List<Reading> nearby = new ArrayList<>();
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                        List<Reading> bucket = buckets.get((x + dx) + "/" + (y + dy));
                        if (bucket != null) nearby.addAll(bucket);
                    }
                    Double value = PublicReadings.interpolate(lat, lon, nearby, first.radius);
                    if (value == null) continue;
                    Polygon cell = new Polygon(map);
                    cell.setPoints(Arrays.asList(new GeoPoint(lat, lon), new GeoPoint(lat + latStep, lon),
                        new GeoPoint(lat + latStep, lon + lonStep), new GeoPoint(lat, lon + lonStep)));
                    cell.getFillPaint().setColor((first.color(value) & 0xffffff) | 0x46000000);
                    cell.getOutlinePaint().setStrokeWidth(0);
                    cell.getOutlinePaint().setColor(Color.TRANSPARENT);
                    cell.setOnClickListener((polygon, view, point) -> false);
                    map.getOverlays().add(cell);
                    drawn++;
                }
            }
        }
    }

    /**
     * Rellena la ficha inferior. Con zona: media de las lecturas de la última hora medida en esa zona.
     * Sin zona pero con lugar buscado: indica que allí aún no hay sensores.
     */
    private void showZone(String zone, String placeName) {
        List<Reading> rows = new ArrayList<>();
        if (zone != null) for (Reading reading : readings) if (reading.zone.equals(zone)) rows.add(reading);
        if (rows.isEmpty()) {
            zoneDot.setBackground(dot(Quality.NONE));
            zoneTitle.setText(placeName != null ? placeName : readings.isEmpty() ? "Sin mediciones publicadas" : "Elige una zona");
            zoneSubtitle.setText(placeName != null ? "Aún no hay sensores publicados aquí" : "Red colaborativa");
            zoneValue.setText("—");
            zoneUnit.setText("");
            zoneChip.setText("Calidad: —");
            zoneChip.setTextColor(MUTED);
            zoneChip.setBackground(ui.shape(0xffeff3f1, 18, 0));
            zoneUpdated.setText("");
            warning.setVisibility(View.GONE);
            return;
        }
        long latest = 0;
        for (Reading reading : rows) latest = Math.max(latest, Quality.utc(reading.date));
        double sum = 0;
        int count = 0;
        Set<String> points = new HashSet<>();
        for (Reading reading : rows) {
            if (latest - Quality.utc(reading.date) > 3600_000L) continue;
            sum += reading.value;
            count++;
            points.add(reading.latitude + "/" + reading.longitude);
        }
        Reading sample = rows.get(0);
        double value = count == 0 ? sample.value : sum / count;
        int level = Quality.level(value, sample.medium, sample.high);
        zoneDot.setBackground(dot(Quality.color(level)));
        zoneTitle.setText(placeName != null && !placeName.equalsIgnoreCase(zone) ? placeName + " · " + zone : zone);
        zoneSubtitle.setText("Red colaborativa · " + count + (count == 1 ? " medición" : " mediciones")
            + (points.size() > 1 ? " en " + points.size() + " puntos" : ""));
        zoneValue.setText(Quality.number(value));
        zoneUnit.setText(sample.unit);
        zoneChip.setText("Calidad: " + Quality.label(level));
        zoneChip.setTextColor(Quality.text(level));
        zoneChip.setBackground(ui.shape(Quality.soft(level), 18, 0));
        zoneUpdated.setText("Actualizado " + Quality.when(latest));
        if (level == 0) warning.setVisibility(View.GONE);
        else {
            SpannableStringBuilder text = new SpannableStringBuilder("Aviso: ");
            text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(Quality.MEDIUM_TEXT), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append(level == 2
                ? "ozono alto en " + zone + ". Evita la actividad física al aire libre."
                : "ozono moderado en " + zone + ". Evita el ejercicio intenso en horas de sol.");
            warningText.setText(text);
            warning.setVisibility(View.VISIBLE);
        }
    }

    private GradientDrawable dot(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        return dot;
    }

    // ---------------------------------------------------------------- búsqueda

    private void suggest(String term, boolean goToFirst) {
        String clean = term.trim();
        if (clean.isEmpty()) return;
        int generation = ++searchRequest;
        worker.execute(() -> {
            List<PlaceSearch.Place> places;
            String error = null;
            try {
                places = PlaceSearch.search(activity, clean, 6);
            } catch (Exception failure) {
                places = Collections.emptyList();
                error = failure.getMessage();
            }
            List<PlaceSearch.Place> found = places;
            String problem = error;
            activity.runOnUiThread(() -> {
                if (closed || generation != searchRequest) return;
                if (goToFirst) {
                    if (found.isEmpty()) {
                        activity.message(problem != null ? "No se pudo buscar: " + problem : "No se ha encontrado \"" + clean + "\"");
                        return;
                    }
                    choose(found.get(0));
                    return;
                }
                showSuggestions(found);
            });
        });
    }

    private void showSuggestions(List<PlaceSearch.Place> places) {
        suggestions.removeAllViews();
        if (places.isEmpty() || !query.hasFocus()) { hideSuggestions(); return; }
        for (int i = 0; i < places.size(); i++) {
            PlaceSearch.Place place = places.get(i);
            LinearLayout item = ui.row();
            item.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));
            ImageView pin = ui.image(R.drawable.aeriq_location, 20, 20);
            pin.setColorFilter(GREEN);
            item.addView(pin, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
            LinearLayout texts = ui.column();
            texts.setPadding(ui.dp(12), 0, 0, 0);
            texts.addView(ui.text(place.name, 15, true, INK));
            if (!place.detail.isEmpty()) texts.addView(ui.text(place.detail, 12, false, MUTED));
            item.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            item.setOnClickListener(view -> choose(place));
            suggestions.addView(item, new LinearLayout.LayoutParams(-1, -2));
            if (i < places.size() - 1) {
                View divider = new View(activity);
                divider.setBackgroundColor(LINE);
                suggestions.addView(divider, new LinearLayout.LayoutParams(-1, 1));
            }
        }
        suggestions.setVisibility(View.VISIBLE);
    }

    private void hideSuggestions() {
        suggestions.setVisibility(View.GONE);
    }

    private void choose(PlaceSearch.Place place) {
        hideSuggestions();
        suppressSuggestions = true;
        query.setText(place.name);
        query.setSelection(query.length());
        suppressSuggestions = false;
        query.clearFocus();
        InputMethodManager keyboard = activity.getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(query.getWindowToken(), 0);
        GeoPoint target = new GeoPoint(place.latitude, place.longitude);
        if (place.box != null && Math.abs(place.box[0] - place.box[2]) > 1e-4) {
            BoundingBox box = new BoundingBox(place.box[0], place.box[1], place.box[2], place.box[3]);
            map.post(() -> map.zoomToBoundingBox(box, true, ui.dp(40)));
        } else {
            map.getController().setZoom(place.zoom());
            map.getController().animateTo(target);
        }
        showZone(nearestZone(target), place.name);
    }

    private String nearestZone(GeoPoint point) {
        String best = null;
        double distance = NEAR_ZONE_METERS;
        for (Reading reading : readings) {
            double meters = point.distanceToAsDouble(reading.position());
            if (meters < distance) { distance = meters; best = reading.zone; }
        }
        return best;
    }

    // ---------------------------------------------------------------- ubicación

    public void locate() {
        if (!location.allowed()) {
            activity.message("No se ha concedido permiso de ubicación.");
            return;
        }
        Location point = location.current();
        if (point == null) {
            location.start();
            activity.message("Buscando tu ubicación…");
            main.postDelayed(() -> { if (!closed && location.current() != null) locate(); }, 2500);
            return;
        }
        GeoPoint position = new GeoPoint(point.getLatitude(), point.getLongitude());
        locationMarker = new Marker(map);
        locationMarker.setPosition(position);
        locationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        GradientDrawable me = new GradientDrawable();
        me.setShape(GradientDrawable.OVAL);
        me.setColor(0xff2f80ed);
        me.setStroke(ui.dp(4), Color.WHITE);
        me.setSize(ui.dp(22), ui.dp(22));
        locationMarker.setIcon(me);
        locationMarker.setTitle("Mi ubicación");
        map.getController().setZoom(16.0);
        map.getController().animateTo(position);
        String zone = nearestZone(position);
        if (zone != null) showZone(zone, null);
        draw();
    }

    public void resume() { map.onResume(); }
    public void pause() { location.stop(); map.onPause(); }
    public void close() {
        closed = true;
        if (pendingSearch != null) main.removeCallbacks(pendingSearch);
        pause();
        map.onDetach();
    }
}
