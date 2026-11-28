package com.aeriq.app.ui;
/*
 * Fichero: PublicMapScreen.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
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
import com.aeriq.app.data.PublicReadings.Estimate;
import com.aeriq.app.data.PublicReadings.Reading;
import com.aeriq.app.data.PublicReadings.Reason;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.views.Projection;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.aeriq.app.ui.Views.*;

/** Mapa de ozono: mapa real (OpenStreetMap), búsqueda mundial y ficha de la zona elegida. */
public final class PublicMapScreen {
    private static final GeoPoint CAMPUS = new GeoPoint(38.99665, -0.16601);
    private static final double NEAR_ZONE_METERS = 3000;
    private static final int RADIUS_STEP = 5;
    /** Separación (dp) entre los puntos evaluados para pintar las zonas. */
    private static final int ZONE_STEP_DP = 4;
    /**
     * Color ARGB de cada estado de PublicReadings.raster: sin datos, insuficiente, bajo, moderado y alto.
     * Los tres niveles con la misma transparencia (T47), igual que la web.
     */
    private static final int[] ZONE_COLORS = {0x00000000, 0x30686d6b, 0x5c219c72, 0x5ceea22d, 0x5ce35b5d};
    private final ZoneOverlay zoneOverlay = new ZoneOverlay();
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
    private final Switch markers, zones, dark, campaignRadius;
    private final SeekBar radiusSeek;
    private final TextView radiusValue, radiusLabel, legend;
    // Ficha inferior
    private final View zoneDot;
    private final TextView zoneTitle, zoneSubtitle, zoneValue, zoneUnit, zoneChip, zoneUpdated;
    private List<Reading> readings = Collections.emptyList();
    // Interpolación (T25): posiciones de soporte por campaña, unidad y origen; el cálculo está en PublicReadings.
    private List<List<Reading>> groups = Collections.emptyList();
    /**
     * Diseño lógico: activity: Text, ui: Text, stage: Text, worker: Text --> PublicMapScreen() --> Text
     * Descripción: Procesa public map screen y devuelve el resultado correspondiente.
     */
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
        // Las zonas dependen de la vista: se recalculan al terminar de mover o ampliar el mapa, y en el primer dibujado.
        map.addMapListener(new DelayedMapListener(new MapListener() {
            /**
             * Diseño lógico: (ScrollEvent event) → onScroll() → boolean; según doc/android_design.md.
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public boolean onScroll(ScrollEvent event) { refreshZones(); return false; }
            /**
             * Diseño lógico: (ZoomEvent event) → onZoom() → boolean; según doc/android_design.md.
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public boolean onZoom(ZoomEvent event) { refreshZones(); return false; }
        }, 150));
        map.addOnFirstLayoutListener((view, left, top, right, bottom) -> refreshZones());

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
        searchBar.setTag("tour:search"); // elementos que ilumina el tour (HU33)
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
        View locateButton = control(R.drawable.ic_target, "Mostrar mi ubicación", () -> {
            if (location.allowed()) locate();
            else activity.locateMap();
        });
        locateButton.setTag("tour:locate");
        controls.addView(locateButton);
        View layersButton = control(R.drawable.ic_layers, "Capas del mapa", this::showLayers);
        layersButton.setTag("tour:layers");
        controls.addView(layersButton);
        controls.addView(control(R.drawable.ic_refresh, "Actualizar mediciones", this::load));

        // Leyenda en la parte de abajo del mapa
        legend = ui.text("", 14, false, INK);
        legend.setText(legendText(false));
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setBackground(ui.shape(WHITE, 22, 0));
        legend.setElevation(ui.dp(3));
        legend.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(10));
        legend.setTag("tour:legend");

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
        // Los controles se crean una vez (conservan su estado) y se muestran en una hoja de la app.
        zones = ui.toggle(true);
        markers = ui.toggle(true);
        dark = ui.toggle(false);
        campaignRadius = ui.toggle(true);
        dark.setOnCheckedChangeListener((button, checked) -> {
            map.getOverlayManager().getTilesOverlay().setColorFilter(checked
                ? new ColorMatrixColorFilter(new float[]{-.65f,0,0,0,175, 0,-.65f,0,0,185, 0,0,-.65f,0,183, 0,0,0,1,0})
                : null);
            map.invalidate();
        });
        zones.setOnCheckedChangeListener((button, checked) -> draw());
        markers.setOnCheckedChangeListener((button, checked) -> draw());
        // Radio de interpolación: por defecto el de cada campaña; el control lo sustituye solo en esta vista.
        radiusValue = ui.text("", 17, true, GREEN);
        radiusLabel = ui.text("", 12, false, MUTED);
        radiusSeek = new SeekBar(activity);
        radiusSeek.setMax((PublicReadings.RADIUS_MAX - PublicReadings.RADIUS_MIN) / RADIUS_STEP);
        radiusSeek.setProgress((PublicReadings.RADIUS_DEFAULT - PublicReadings.RADIUS_MIN) / RADIUS_STEP);
        radiusSeek.setContentDescription("Radio de interpolación, de 20 a 50 metros");
        radiusSeek.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        radiusSeek.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN));
        radiusSeek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffcfd8d4));
        radiusSeek.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12));
        campaignRadius.setOnCheckedChangeListener((button, checked) -> {
            radiusOverride = checked ? 0 : PublicReadings.RADIUS_MIN + radiusSeek.getProgress() * RADIUS_STEP;
            refreshRadius();
        });
        radiusSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            /**
             * Diseño lógico: (SeekBar bar, int progress, boolean fromUser) --> onProgressChanged()
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                // Al desmarcar el interruptor, su propio aviso aplica el radio elegido.
                if (campaignRadius.isChecked()) { campaignRadius.setChecked(false); return; }
                radiusOverride = PublicReadings.RADIUS_MIN + progress * RADIUS_STEP;
                refreshRadius();
            }
            /**
             * Diseño lógico: (SeekBar bar) --> onStartTrackingTouch()
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            /**
             * Diseño lógico: (SeekBar bar) --> onStopTrackingTouch()
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        showRadius();

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
        zoneSubtitle = ui.text("Mediciones publicadas por cada campaña", 14, false, MUTED);
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
        View gap = new View(activity);
        actions.addView(gap, new LinearLayout.LayoutParams(ui.dp(10), 1));
        historyAction = history;
        historyGap = gap;
        LinearLayout compare = ui.row();
        compare.setGravity(Gravity.CENTER);
        compare.setBackground(ui.shape(DARK, 18, 0));
        ImageView scale = ui.image(R.drawable.ic_scale, 20, 20);
        scale.setColorFilter(WHITE);
        compare.addView(scale, new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        TextView compareLabel = ui.text("Comparar zonas", 15, true, WHITE);
        compareLabel.setPadding(ui.dp(10), 0, 0, 0);
        compare.addView(compareLabel);
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
            /**
             * Diseño lógico: (CharSequence s, int start, int count, int after) --> beforeTextChanged()
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            /**
             * Diseño lógico: (CharSequence s, int start, int before, int count) --> onTextChanged()
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            /**
             * Diseño lógico: (Editable text) --> afterTextChanged()
             * Descripción: Describe la operación realizada y su resultado.
             */
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

        showZone(null, null, null);
        load();
    }

    private LinearLayout grip, bottom;
    private int hiddenOffset;
    private boolean expanded, dragging;
    private float downY, startTranslation;
    private long downTime;

    /**
     * Diseño lógico: view: Text, event: Text --> dragSheet() --> B
     * Descripción: Procesa drag sheet y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: open: B --> setExpanded()
     * Descripción: Procesa set expanded y devuelve el resultado correspondiente..
     */
    private void setExpanded(boolean open) {
        expanded = open;
        bottom.animate().translationY(open ? 0 : hiddenOffset).setDuration(220)
            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /**
     * Diseño lógico: width: Z, height: Z, gravity: Z, left: Z, top: Z, right: Z, bottom: Z --> frameParams() --> Text
     * Descripción: Procesa frame params y devuelve el resultado correspondiente.
     */
    private FrameLayout.LayoutParams frameParams(int width, int height, int gravity, int left, int top, int right, int bottom) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height, gravity);
        params.setMargins(ui.dp(left), ui.dp(top), ui.dp(right), ui.dp(bottom));
        return params;
    }

    /**
     * Diseño lógico: icon: Z, description: Text, click: Text --> control() --> Text
     * Descripción: Procesa control y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: clipped: B --> legendText() --> Text
     * Descripción: Procesa legend text y devuelve el resultado correspondiente.
     */
    private CharSequence legendText(boolean clipped) {
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
        // Gris: hay mediciones cerca pero no bastan. Sin color: sin datos.
        text.append("\n");
        int grey = text.length();
        text.append("●");
        text.setSpan(new ForegroundColorSpan(0xff9aa3a0), grey, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(" Datos insuficientes · sin color: sin datos");
        // El tope de rendimiento se avisa: una zona recortada no debe confundirse con una zona sin datos.
        if (clipped) text.append("\nZonas incompletas: hay demasiadas mediciones para pintarlas todas.");
        return text;
    }

    // ---------------------------------------------------------------- interpolación

    /**
     * Diseño lógico: group: [ Text ] --> radiusOf() --> Z
     * Descripción: Procesa radius of y devuelve el resultado correspondiente.
     */
    private int radiusOf(List<Reading> group) { return radiusOverride != 0 ? radiusOverride : group.get(0).radius; }

    /**
     * Diseño lógico: showRadius()
     * Descripción: Procesa show radius y devuelve el resultado correspondiente..
     */
    private void showRadius() {
        if (radiusOverride != 0) {
            radiusValue.setText(radiusOverride + " m");
            radiusLabel.setText("Radio de prueba: solo cambia esta vista y no se guarda.");
            return;
        }
        int min = Integer.MAX_VALUE, max = 0;
        for (List<Reading> group : groups) {
            if (group.isEmpty()) continue;
            min = Math.min(min, group.get(0).radius);
            max = Math.max(max, group.get(0).radius);
        }
        if (max == 0) min = max = PublicReadings.RADIUS_DEFAULT;
        radiusValue.setText(min == max ? min + " m" : min + "–" + max + " m");
        radiusLabel.setText("El que ha fijado cada campaña. Mueve la barra para probar otro (20–50 m).");
    }

    /**
     * Diseño lógico: refreshRadius()
     * Descripción: Procesa refresh radius y devuelve el resultado correspondiente..
     */
    private void refreshRadius() {
        showRadius();
        draw();
        if (queried != null) query(queried);
    }

    /**
     * Tocar el mapa explica ese punto en la ficha inferior: valor estimado, datos insuficientes o sin datos.
     * Con varias campañas gana la que estima; a igualdad, la de medición más cercana.
     */
    /**
     * Diseño lógico: point: Text --> query()
     * Descripción: Procesa query y devuelve el resultado correspondiente..
     */
    private void query(GeoPoint point) {
        Estimate best = null;
        List<Reading> bestGroup = null;
        int bestRank = -1;
        for (List<Reading> group : groups) {
            if (group.isEmpty()) continue;
            Estimate estimate = PublicReadings.estimate(point.getLatitude(), point.getLongitude(), group, radiusOf(group));
            int rank = estimate.value != null ? 2 : estimate.reason == Reason.INSUFFICIENT ? 1 : 0;
            if (rank > bestRank || (rank == bestRank && estimate.nearest < best.nearest)) {
                best = estimate;
                bestGroup = group;
                bestRank = rank;
            }
        }
        int radius = bestGroup == null ? PublicReadings.RADIUS_DEFAULT : radiusOf(bestGroup);
        if (best == null) {
            best = PublicReadings.estimate(point.getLatitude(), point.getLongitude(), Collections.<Reading>emptyList(), radius);
        }
        Reading sample = bestGroup == null ? null : bestGroup.get(0);
        String[] text = PublicReadings.describe(best, radius, sample == null ? "" : sample.unit,
            sample == null ? 0 : sample.medium, sample == null ? 0 : sample.high, sample != null && sample.simulated);
        queried = point;
        placeRequest++;
        warningLevel = 0;
        zoneTitle.setText(text[0]);
        zoneSubtitle.setText(text[1]);
        zoneUpdated.setText("");
        warning.setVisibility(View.GONE);
        if (best.value != null && sample != null) {
            int level = Quality.level(best.value, sample.medium, sample.high);
            zoneDot.setBackground(dot(Quality.color(level)));
            zoneValue.setText(Quality.number(best.value));
            zoneUnit.setText(sample.unit);
            zoneChip.setText("Estimación: " + Quality.label(level));
            zoneChip.setTextColor(Quality.text(level));
            zoneChip.setBackground(ui.shape(Quality.soft(level), 18, 0));
        } else {
            zoneDot.setBackground(dot(Quality.NONE));
            zoneValue.setText("—");
            zoneUnit.setText("");
            zoneChip.setText("Calidad: —");
            zoneChip.setTextColor(MUTED);
            zoneChip.setBackground(ui.shape(0xffeff3f1, 18, 0));
        }
        setExpanded(true);
    }

    /**
     * Diseño lógico: showLayers()
     * Descripción: Procesa show layers y devuelve el resultado correspondiente..
     */
    private void showLayers() {
        AppSheet sheet = new AppSheet(activity, ui, "Capas del mapa", "Qué mediciones ver y cómo pintarlas");
        sheet.section("Periodo");
        int[] periodHours = {24, 168, 0};
        int selected = 0;
        for (int i = 0; i < periodHours.length; i++) if (periodHours[i] == hours) selected = i;
        ui.segments(sheet.content, new String[]{"Últimas 24 h", "7 días", "Todo"}, selected, index -> {
            if (hours != periodHours[index]) { hours = periodHours[index]; load(); }
        });
        sheet.section("Mostrar en el mapa");
        LinearLayout show = sheet.group();
        sheet.toggle(show, "Zonas estimadas", "Color alrededor de cada medición (interpolación)", zones);
        sheet.toggle(show, "Puntos de medición", "Cada lectura publicada", markers);
        sheet.toggle(show, "Mapa oscuro", null, dark);
        sheet.section("Radio de interpolación");
        LinearLayout radius = sheet.group();
        LinearLayout head = ui.row();
        head.setPadding(0, ui.dp(12), 0, 0);
        head.addView(ui.text("Radio", 15, true, INK), new LinearLayout.LayoutParams(0, -2, 1));
        detach(radiusValue);
        head.addView(radiusValue);
        radius.addView(head, new LinearLayout.LayoutParams(-1, -2));
        detach(radiusSeek);
        radius.addView(radiusSeek, new LinearLayout.LayoutParams(-1, -2));
        detach(radiusLabel);
        radiusLabel.setPadding(0, 0, 0, ui.dp(12));
        radius.addView(radiusLabel);
        sheet.toggle(radius, "Usar el radio de cada campaña", null, campaignRadius);
        sheet.primary("Listo", null);
        sheet.show();
    }

    /**
     * Diseño lógico: view: Text --> detach()
     * Descripción: Procesa detach y devuelve el resultado correspondiente..
     */
    private static void detach(View view) {
        if (view.getParent() != null) ((android.view.ViewGroup) view.getParent()).removeView(view);
    }

    // ---------------------------------------------------------------- datos

    /**
     * Diseño lógico: load()
     * Descripción: Carga los datos requeridos por el componente..
     */
    private void load() {
        int generation = ++request;
        worker.execute(() -> {
            try {
                PublicReadings.Result result = PublicReadings.fetch(false, hours);
                activity.runOnUiThread(() -> {
                    if (closed || generation != request) return;
                    readings = result.rows;
                    GeoPoint pending = queried;
                    Map<String, List<Reading>> byKey = new LinkedHashMap<>();
                    for (Reading reading : readings) {
                        byKey.computeIfAbsent(PublicReadings.groupKey(reading), key -> new ArrayList<>()).add(reading);
                    }
                    List<List<Reading>> support = new ArrayList<>();
                    for (List<Reading> group : byKey.values()) support.add(PublicReadings.support(group));
                    groups = support;
                    showRadius();
                    draw();
                    if (here != null) showZone(nearestZone(here), null, here);
                    else if (!readings.isEmpty()) {
                        Reading latest = readings.get(0);
                        for (Reading reading : readings) if (reading.date.compareTo(latest.date) > 0) latest = reading;
                        if (locationMarker == null) map.getController().setCenter(latest.position());
                        showZone(latest.zone, null, latest.position());
                    } else showZone(null, null, null);
                    // La consulta de un punto se mantiene tras recargar los datos.
                    if (pending != null) query(pending);
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

    /**
     * Diseño lógico: draw()
     * Descripción: Procesa draw y devuelve el resultado correspondiente..
     */
    private void draw() {
        updateHistoryAction();
        map.getOverlays().clear();
        // Debajo de todo: recoge los toques que no caen en un marcador y consulta ese punto.
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            /**
             * Diseño lógico: (GeoPoint point) → singleTapConfirmedHelper() → boolean; según doc/android_design.md.
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public boolean singleTapConfirmedHelper(GeoPoint point) {
                query(point);
                return true;
            }
            /**
             * Diseño lógico: (GeoPoint point) → longPressHelper() → boolean; según doc/android_design.md.
             * Descripción: Describe la operación realizada y su resultado.
             */
            @Override public boolean longPressHelper(GeoPoint point) { return false; }
        }));
        if (zones.isChecked()) map.getOverlays().add(zoneOverlay);
        refreshZones();
        // Un punto por posición: la lectura más reciente de cada sitio, la misma con la que se pinta la zona (T46).
        if (markers.isChecked()) for (List<Reading> group : groups) for (Reading reading : group) {
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
                showZone(reading.zone, null, reading.position());
                setExpanded(true);
                return true;
            });
            map.getOverlays().add(marker);
        }
        if (locationMarker != null) map.getOverlays().add(locationMarker);
        map.invalidate();
    }

    /**
     * Pinta las celdas alrededor de las mediciones. Color: tres o más posiciones dentro del radio.
     * Gris discontinuo: hay mediciones cerca pero no bastan. Sin pintar: sin datos.
     * Devuelve true si el tope de rendimiento dejó celdas sin pintar.
     */
    /**
     * Diseño lógico: refreshZones()
     * Descripción: Procesa refresh zones y devuelve el resultado correspondiente..
     */
    private void refreshZones() {
        // Los avisos del mapa llegan después del constructor; la comprobación cubre un aviso temprano.
        if (closed || zones == null || legend == null) return;
        boolean clipped = false;
        zoneOverlay.clear();
        int width = map.getWidth(), height = map.getHeight();
        if (zones.isChecked() && !groups.isEmpty() && width > 0 && height > 0) {
            // Rejilla de pantalla: un punto cada ZONE_STEP_DP. En Web Mercator la latitud solo depende de la
            // fila y la longitud de la columna, así que basta con convertir una fila y una columna.
            Projection projection = map.getProjection();
            int step = Math.max(2, ui.dp(ZONE_STEP_DP)), columns = (width + step - 1) / step, rows = (height + step - 1) / step;
            double[] lats = new double[rows], lons = new double[columns];
            for (int i = 0; i < rows; i++) lats[i] = projection.fromPixels(0, i * step + step / 2).getLatitude();
            for (int j = 0; j < columns; j++) lons[j] = projection.fromPixels(j * step + step / 2, 0).getLongitude();
            int[] radii = new int[groups.size()];
            for (int g = 0; g < radii.length; g++) radii[g] = groups.get(g).isEmpty() ? 0 : radiusOf(groups.get(g));
            PublicReadings.Raster grid = PublicReadings.raster(groups, radii, lats, lons, PublicReadings.MAX_EVALUATIONS);
            clipped = grid.clipped;
            if (grid.evaluated > 0) {
                int[] pixels = new int[grid.states.length];
                for (int k = 0; k < pixels.length; k++) pixels[k] = ZONE_COLORS[grid.states[k]];
                IGeoPoint northWest = projection.fromPixels(0, 0), southEast = projection.fromPixels(columns * step, rows * step);
                zoneOverlay.set(Bitmap.createBitmap(pixels, columns, rows, Bitmap.Config.ARGB_8888),
                    new BoundingBox(northWest.getLatitude(), southEast.getLongitude(), southEast.getLatitude(), northWest.getLongitude()));
            }
        }
        legend.setText(legendText(clipped));
        map.invalidate();
    }

    /**
     * Zonas estimadas como imagen: la rejilla de pantalla se amplía con filtrado, así el borde sigue el radio
     * de cada medición (redondo) en vez de formar cuadrados. Mientras se arrastra o se hace zoom, la imagen se
     * mueve con el mapa; al parar se vuelve a calcular. No consume toques: llegan a la consulta de punto.
     */
    private static final class ZoneOverlay extends Overlay {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Rect target = new Rect();
        private final Point corner = new Point(), opposite = new Point();
        private Bitmap bitmap;
        private BoundingBox box;

        /**
         * Diseño lógico: image: Text, bounds: Text --> set()
         * Descripción: Procesa set y devuelve el resultado correspondiente..
         */
        void set(Bitmap image, BoundingBox bounds) {
            clear();
            bitmap = image;
            box = bounds;
        }

        /**
         * Diseño lógico: clear()
         * Descripción: Procesa clear y devuelve el resultado correspondiente..
         */
        void clear() {
            if (bitmap != null) bitmap.recycle();
            bitmap = null;
            box = null;
        }

        /**
         * Diseño lógico: (Canvas canvas, Projection projection) --> draw()
         * Descripción: Describe la operación realizada y su resultado.
         */
        @Override public void draw(Canvas canvas, Projection projection) {
            if (bitmap == null || box == null) return;
            projection.toPixels(new GeoPoint(box.getLatNorth(), box.getLonWest()), corner);
            projection.toPixels(new GeoPoint(box.getLatSouth(), box.getLonEast()), opposite);
            target.set(corner.x, corner.y, opposite.x, opposite.y);
            canvas.drawBitmap(bitmap, null, target, paint);
        }
    }

    /**
     * Rellena la ficha inferior. El título es el lugar (la ciudad de where, o el que se ha buscado), no el nombre
     * interno de la zona de la campaña. Con zona: media de las lecturas de la última hora medida en esa zona.
     * Sin zona: indica que allí aún no hay mediciones publicadas.
     */
    /**
     * Diseño lógico: zone: Text, placeName: Text, where: Text --> showZone()
     * Descripción: Procesa show zone y devuelve el resultado correspondiente..
     */
    private void showZone(String zone, String placeName, GeoPoint where) {
        queried = null;
        List<Reading> rows = new ArrayList<>();
        if (zone != null) for (Reading reading : readings) if (reading.zone.equals(zone)) rows.add(reading);
        if (rows.isEmpty()) {
            warningLevel = 0;
            boolean somewhere = placeName != null || where != null;
            setPlace(placeName, where, readings.isEmpty() ? "Sin mediciones publicadas" : "Elige una zona");
            zoneDot.setBackground(dot(Quality.NONE));
            zoneSubtitle.setText(somewhere ? "Aún no hay mediciones publicadas cerca" : "Mediciones publicadas por cada campaña");
            zoneValue.setText("—");
            zoneUnit.setText("");
            zoneChip.setText("Calidad: —");
            zoneChip.setTextColor(MUTED);
            zoneChip.setBackground(ui.shape(0xffeff3f1, 18, 0));
            zoneUpdated.setText("");
            return;
        }
        long latest = 0;
        Reading newest = rows.get(0);
        for (Reading reading : rows) {
            long time = Quality.utc(reading.date);
            if (time > latest) { latest = time; newest = reading; }
        }
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
        warningLevel = level;
        // Si no se sabe la ciudad, se usa el nombre de la zona como último recurso.
        setPlace(placeName, where != null ? where : newest.position(), zone);
        zoneDot.setBackground(dot(Quality.color(level)));
        zoneSubtitle.setText(count + (count == 1 ? " medición" : " mediciones")
            + (points.size() > 1 ? " en " + points.size() + " puntos" : ""));
        zoneValue.setText(Quality.number(value));
        zoneUnit.setText(sample.unit);
        zoneChip.setText("Calidad: " + Quality.label(level));
        zoneChip.setTextColor(Quality.text(level));
        zoneChip.setBackground(ui.shape(Quality.soft(level), 18, 0));
        zoneUpdated.setText("Actualizado " + Quality.when(latest));
    }

    /**
     * Pone el título de la ficha: el nombre conocido o la ciudad de where (sin calle). La ciudad se busca en
     * segundo plano; si llega tarde (ya se ha elegido otra cosa) se descarta. Si no se encuentra, fallback.
     */
    /**
     * Diseño lógico: known: Text, where: Text, fallback: Text --> setPlace()
     * Descripción: Procesa set place y devuelve el resultado correspondiente..
     */
    private void setPlace(String known, GeoPoint where, String fallback) {
        int generation = ++placeRequest;
        String cached = known != null ? known : where == null ? fallback
            : PlaceSearch.cachedCity(where.getLatitude(), where.getLongitude());
        if (cached != null) { showPlace(cached); return; }
        showPlace("Buscando la ciudad…");
        worker.execute(() -> {
            String city = PlaceSearch.city(activity, where.getLatitude(), where.getLongitude());
            activity.runOnUiThread(() -> {
                if (closed || generation != placeRequest) return;
                showPlace(city != null ? city : fallback);
            });
        });
    }

    /**
     * Diseño lógico: name: Text --> showPlace()
     * Descripción: Procesa show place y devuelve el resultado correspondiente..
     */
    private void showPlace(String name) {
        placeLabel = name;
        zoneTitle.setText(name);
        if (warningLevel == 0) { warning.setVisibility(View.GONE); return; }
        SpannableStringBuilder text = new SpannableStringBuilder("Aviso: ");
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(Quality.MEDIUM_TEXT), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(warningLevel == 2
            ? "ozono alto en " + placeLabel + ". Evita la actividad física al aire libre."
            : "ozono moderado en " + placeLabel + ". Evita el ejercicio intenso en horas de sol.");
        warningText.setText(text);
        warning.setVisibility(View.VISIBLE);
    }

    /**
     * Diseño lógico: color: Z --> dot() --> Text
     * Descripción: Procesa dot y devuelve el resultado correspondiente.
     */
    private GradientDrawable dot(int color) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        return dot;
    }

    // ---------------------------------------------------------------- búsqueda

    /**
     * Diseño lógico: term: Text, goToFirst: B --> suggest()
     * Descripción: Procesa suggest y devuelve el resultado correspondiente..
     */
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
                        activity.message(problem != null ? "No se pudo buscar: " + problem : "No se ha encontrado \"" + clean + "\"", (problem != null ? MainActivity.TONE_ERROR : MainActivity.TONE_WARN));
                        return;
                    }
                    choose(found.get(0));
                    return;
                }
                showSuggestions(found);
            });
        });
    }

    /**
     * Diseño lógico: places: [ Text ] --> showSuggestions()
     * Descripción: Procesa show suggestions y devuelve el resultado correspondiente..
     */
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

    /**
     * Diseño lógico: hideSuggestions()
     * Descripción: Procesa hide suggestions y devuelve el resultado correspondiente..
     */
    private void hideSuggestions() {
        suggestions.setVisibility(View.GONE);
    }

    /**
     * Diseño lógico: place: Text --> choose()
     * Descripción: Procesa choose y devuelve el resultado correspondiente..
     */
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
        showZone(nearestZone(target), place.name, target);
    }

    /**
     * Diseño lógico: point: Text --> nearestZone() --> Text
     * Descripción: Procesa nearest zone y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: locate()
     * Descripción: Procesa locate y devuelve el resultado correspondiente..
     */
    public void locate() { locate(false); }

    /**
     * Diseño lógico: quiet: B --> locate()
     * Descripción: Procesa locate y devuelve el resultado correspondiente..
     */
    private void locate(boolean quiet) {
        if (!location.allowed()) {
            if (!quiet) activity.message("No se ha concedido permiso de ubicación.", MainActivity.TONE_WARN);
            return;
        }
        Location point = location.current();
        if (point == null) {
            if (quiet) return;
            location.start();
            activity.message("Buscando tu ubicación…", MainActivity.TONE_INFO);
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
        here = position;
        showZone(nearestZone(position), null, position);
        draw();
    }

    /**
     * Diseño lógico: resume()
     * Descripción: Procesa resume y devuelve el resultado correspondiente..
     */
    public void resume() {
        map.onResume();
        updateHistoryAction();
        // Con el permiso ya concedido, al abrir el mapa se busca tu posición (sin volver a pedir permiso).
        if (location.allowed()) {
            location.start();
            if (!autoLocated) awaitPosition(10);
        }
    }

    /**
     * Diseño lógico: tries: Z --> awaitPosition()
     * Descripción: Procesa await position y devuelve el resultado correspondiente..
     */
    private void awaitPosition(int tries) {
        if (closed || autoLocated) return;
        if (location.current() != null) { autoLocated = true; locate(true); return; }
        if (tries > 0) main.postDelayed(() -> awaitPosition(tries - 1), 1500);
    }

    /**
     * Diseño lógico: updateHistoryAction()
     * Descripción: Procesa update history action y devuelve el resultado correspondiente..
     */
    private void updateHistoryAction() {
        if (historyAction == null) return;
        int visibility = activity.signedIn() ? View.VISIBLE : View.GONE;
        historyAction.setVisibility(visibility);
        historyGap.setVisibility(visibility);
    }
    /**
     * Diseño lógico: pause()
     * Descripción: Procesa pause y devuelve el resultado correspondiente..
     */
    public void pause() { location.stop(); map.onPause(); }
    /**
     * Diseño lógico: close()
     * Descripción: Finaliza la operación y libera los recursos asociados. (efecto: libera los recursos).
     */
    public void close() {
        closed = true;
        if (pendingSearch != null) main.removeCallbacks(pendingSearch);
        pause();
        map.onDetach();
        zoneOverlay.clear();
    }
}
