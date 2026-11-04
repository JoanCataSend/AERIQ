package com.aeriq.app.ui;

import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.widget.*;
import com.aeriq.app.MainActivity;
import com.aeriq.app.MainActivity.Screen;
import com.aeriq.app.data.ForegroundCaptureLocation;
import com.aeriq.app.data.PublicReadings;
import com.aeriq.app.data.PublicReadings.Reading;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import java.util.*;
import java.util.concurrent.ExecutorService;

import static com.aeriq.app.ui.Views.*;

public final class PublicMapScreen {
    private static final GeoPoint CAMPUS = new GeoPoint(38.99665, -0.16601);
    private final MainActivity activity;
    private final ExecutorService worker;
    private final ForegroundCaptureLocation location;
    private final MapView map;
    private final TextView status, detail;
    private final CheckBox simulated, markers, zones;
    private List<Reading> readings = Collections.emptyList();
    private int request;
    private boolean closed;
    private Marker locationMarker;

    public PublicMapScreen(MainActivity activity, Views ui, LinearLayout body, ExecutorService worker) {
        this.activity = activity;
        this.worker = worker;
        location = new ForegroundCaptureLocation(activity);
        Configuration.getInstance().setUserAgentValue("AERIQ-Android/1.0 (" + activity.getPackageName() + ")");
        map = new MapView(activity);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(16.0);
        map.getController().setCenter(CAMPUS);
        map.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            view.getParent().requestDisallowInterceptTouchEvent(action != android.view.MotionEvent.ACTION_UP
                && action != android.view.MotionEvent.ACTION_CANCEL);
            return false;
        });
        EditText query = ui.field(body, "Buscar zona", "", android.text.InputType.TYPE_CLASS_TEXT);
        query.setHint("UPV, playa, Grau…");
        ui.outline(body, "Buscar", () -> search(query.getText().toString()));
        simulated = toggle(body, "Mostrar mediciones simuladas", false);
        status = ui.label(body, "Consultando mediciones autorizadas…", 12, false, MUTED);
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-1, ui.dp(380));
        map.setBackground(ui.shape(SOFT, 18, LINE));
        map.setClipToOutline(true);
        body.addView(map, mapParams);
        LinearLayout zoom = ui.row();
        ui.weighted(zoom, ui.action("+", WHITE, INK, () -> map.getController().zoomIn()));
        ui.weighted(zoom, ui.action("−", WHITE, INK, () -> map.getController().zoomOut()));
        ui.weighted(zoom, ui.action("UPV", WHITE, INK, () -> map.getController().animateTo(CAMPUS)));
        ui.weighted(zoom, ui.action("Actualizar", WHITE, INK, this::load));
        ui.add(body, zoom);
        LinearLayout layers = ui.card(body);
        ui.label(layers, "Capas del mapa", 16, true, INK);
        zones = toggle(layers, "Interpolación local de 20–50 m", true);
        markers = toggle(layers, "Mediciones", true);
        CheckBox dark = toggle(layers, "Modo oscuro", false);
        dark.setOnCheckedChangeListener((button, checked) -> {
            map.getOverlayManager().getTilesOverlay().setColorFilter(checked
                ? new ColorMatrixColorFilter(new float[]{-.65f,0,0,0,175, 0,-.65f,0,0,185, 0,0,-.65f,0,183, 0,0,0,1,0})
                : null);
            map.invalidate();
        });
        LinearLayout legend = ui.row();
        ui.weighted(legend, ui.text("● Bajo", 13, true, 0xff219c72));
        ui.weighted(legend, ui.text("● Medio", 13, true, 0xffae6600));
        ui.weighted(legend, ui.text("● Alto", 13, true, RED));
        ui.add(body, legend);
        detail = ui.label(ui.card(body), "Selecciona una medición en el mapa.", 14, false, INK);
        ui.outline(body, "Mostrar mi ubicación", () -> {
            if (location.allowed()) locate();
            else activity.locateMap();
        });
        ui.outline(body, "Comparar zonas publicadas", () -> activity.go(Screen.COMPARE));
        ui.note(body, "© OpenStreetMap contributors. Sin al menos tres ubicaciones próximas, las áreas permanecen sin color.");
        simulated.setOnCheckedChangeListener((button, checked) -> load());
        zones.setOnCheckedChangeListener((button, checked) -> draw());
        markers.setOnCheckedChangeListener((button, checked) -> draw());
        load();
    }

    private CheckBox toggle(LinearLayout parent, String label, boolean checked) {
        CheckBox button = new CheckBox(activity);
        button.setText(label);
        button.setChecked(checked);
        parent.addView(button);
        return button;
    }

    private void load() {
        int generation = ++request;
        boolean demo = simulated.isChecked();
        readings = Collections.emptyList();
        draw();
        detail.setText("Selecciona una medición en el mapa.");
        status.setText("Consultando mediciones autorizadas…");
        worker.execute(() -> {
            try {
                PublicReadings.Result result = PublicReadings.fetch(demo);
                activity.runOnUiThread(() -> {
                    if (closed || generation != request) return;
                    readings = result.rows;
                    status.setText(readings.size() + " lecturas publicadas" + (result.truncated ? " · límite alcanzado" : ""));
                    if (!readings.isEmpty()) map.getController().setCenter(readings.get(0).position());
                    draw();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!closed && generation == request) status.setText("No se pudo cargar el mapa: " + error.getMessage());
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
            icon.setStroke(3, Color.WHITE);
            icon.setSize(25, 25);
            marker.setIcon(icon);
            marker.setOnMarkerClickListener((selected, view) -> {
                detail.setText(reading.value + " " + reading.unit + "\n" + reading.zone + "\n"
                    + reading.date + " UTC · " + (reading.simulated ? "Simulada" : "Real"));
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
            groups.computeIfAbsent(reading.campaign + "/" + reading.unit + "/" + reading.simulated,
                key -> new ArrayList<>()).add(reading);
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
                    cell.getFillPaint().setColor((first.color(value) & 0xffffff) | 0x3c000000);
                    cell.getOutlinePaint().setStrokeWidth(0);
                    map.getOverlays().add(cell);
                    drawn++;
                }
            }
        }
    }

    private void search(String term) {
        String query = term.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        for (Reading reading : readings) {
            if (reading.zone.toLowerCase(Locale.ROOT).contains(query)) {
                map.getController().animateTo(reading.position());
                return;
            }
        }
        if (query.contains("upv") || query.contains("campus")) map.getController().animateTo(CAMPUS);
        else if (query.contains("playa") || query.contains("platja")) map.getController().animateTo(new GeoPoint(38.999, -.1589));
        else if (query.contains("grau") || query.contains("grao")) map.getController().animateTo(new GeoPoint(38.9922, -.1656));
        else status.setText("Busca una zona publicada, UPV, playa o Grau de Gandía.");
    }

    public void locate() {
        if (!location.allowed()) {
            status.setText("No se ha concedido permiso de ubicación.");
            return;
        }
        Location point = location.current();
        if (point == null) {
            location.start();
            status.setText("Esperando ubicación del dispositivo…");
            return;
        }
        GeoPoint position = new GeoPoint(point.getLatitude(), point.getLongitude());
        locationMarker = new Marker(map);
        locationMarker.setPosition(position);
        locationMarker.setTitle("Mi ubicación");
        map.getController().animateTo(position);
        draw();
    }

    public void resume() { map.onResume(); }
    public void pause() { location.stop(); map.onPause(); }
    public void close() { closed = true; pause(); map.onDetach(); }
}
