/*
 * Fichero: PublicInterpolationTest.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/test/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import com.aeriq.app.data.PublicReadings;
import com.aeriq.app.data.PublicReadings.Estimate;
import com.aeriq.app.data.PublicReadings.Reading;
import com.aeriq.app.data.PublicReadings.Reason;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/**
 * T25: interpolación acotada y zonas sin datos. Los casos densos, dispersos y simulados y sus valores
 * esperados son los mismos que en scripts/tests/aeriq-domain.test.js: si cambias uno, cambia el otro.
 */
public final class PublicInterpolationTest {
    private static final double TOLERANCE = 1e-6;
    // Rejilla de evaluación de 18 × 21 celdas de ~10 m alrededor del campus (centros de celda).
    private static final double LAT0 = 38.9960, LON0 = -0.1670, LAT_STEP = 0.00009, LON_STEP = 0.0001156;
    private static final int ROWS = 18, COLS = 21;
    private static final double[] A = {38.9966, -0.1662};
    private static final int[] O3 = {38, 42, 47, 51, 56, 61, 66, 72, 78, 84, 91, 97, 103, 108, 114};

    /**
     * Diseño lógico: lat: R, lon: R, value: R, simulated: B --> reading() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    private static Reading reading(double lat, double lon, double value, boolean simulated) {
        return reading("campaign", "ppb", lat, lon, value, "2026-11-02", simulated);
    }

    /**
     * Diseño lógico: campaign: Text, unit: Text, lat: R, lon: R, value: R, date: Text, simulated: B --> reading() --> Text
     * Descripción: Procesa reading y devuelve el resultado correspondiente.
     */
    private static Reading reading(String campaign, String unit, double lat, double lon, double value, String date, boolean simulated) {
        return new Reading("1", campaign, "UPV", lat, lon, value, unit, date, simulated, 30, 60, 100);
    }

    /**
     * Diseño lógico: minutes: Z --> stamp() --> Text
     * Descripción: Procesa stamp y devuelve el resultado correspondiente.
     */
    private static String stamp(int minutes) {
        return String.format(Locale.ROOT, "2026-11-10 %02d:%02d:00.000", 12 + minutes / 60, minutes % 60);
    }

    /**
     * Diseño lógico: simulated: B --> dense() --> [ Text ]
     * Descripción: Procesa dense y devuelve el resultado correspondiente.
     */
    private static List<Reading> dense(boolean simulated) {
        List<Reading> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) for (int j = 0; j < 5; j++) {
            rows.add(reading("campaign", "ppb", 38.996651 + i * 0.0001, -0.166009 + j * 0.00013, O3[i * 5 + j],
                stamp((i * 5 + j) * 20), simulated));
        }
        return rows;
    }

    private static final class Sweep {
        int value, insufficient, far, mixed, radius;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum;
    }

    /**
     * Diseño lógico: rows: [ Text ], radius: Z --> sweep() --> Text
     * Descripción: Procesa sweep y devuelve el resultado correspondiente.
     */
    private static Sweep sweep(List<Reading> rows, int radius) {
        List<Reading> support = PublicReadings.support(rows);
        Sweep result = new Sweep();
        for (int i = 0; i < ROWS; i++) for (int j = 0; j < COLS; j++) {
            Estimate estimate = PublicReadings.estimate(LAT0 + (i + .5) * LAT_STEP, LON0 + (j + .5) * LON_STEP, support, radius);
            switch (estimate.reason) {
                case INSUFFICIENT: result.insufficient++; break;
                case FAR: result.far++; break;
                case MIXED: result.mixed++; break;
                case RADIUS: result.radius++; break;
                default:
                    // Nunca se colorea sin tres posiciones dentro del radio.
                    assertTrue(estimate.positions >= 3 && estimate.nearest <= radius);
                    result.value++;
                    result.min = Math.min(result.min, estimate.value);
                    result.max = Math.max(result.max, estimate.value);
                    result.sum += estimate.value;
            }
            if (estimate.reason != Reason.NONE) assertNull(estimate.value);
        }
        return result;
    }

    /**
     * Diseño lógico: parametersMatchTheWeb()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void parametersMatchTheWeb() {
        assertEquals(20, PublicReadings.RADIUS_MIN);
        assertEquals(50, PublicReadings.RADIUS_MAX);
        assertEquals(30, PublicReadings.RADIUS_DEFAULT);
        assertEquals(3, PublicReadings.MIN_POSITIONS);
        assertEquals(5, PublicReadings.MIN_SEPARATION, 0);
        assertEquals(3, PublicReadings.POWER, 0);
        assertEquals(0.5, PublicReadings.OWN_FRACTION, 0);
        assertEquals(15, PublicReadings.OWN_MAX, 0);
        assertEquals(1, PublicReadings.MIN_DISTANCE, 0);
        assertEquals(10, PublicReadings.CELL, 0);
    }

    /**
     * Diseño lógico: noCoverageOrRepeatedLocationStaysWithoutData()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void noCoverageOrRepeatedLocationStaysWithoutData() {
        Reading row = reading(38.99665, -.166, 50, false);
        assertNull(PublicReadings.interpolate(38.99665, -.166, Arrays.asList(row, row, row), 30));
        assertNull(PublicReadings.interpolate(39, -.16, Collections.singletonList(row), 30));
        Estimate empty = PublicReadings.estimate(A[0], A[1], Collections.<Reading>emptyList(), 30);
        assertEquals(Reason.FAR, empty.reason);
        assertTrue(Double.isInfinite(empty.nearest));
    }

    /**
     * Diseño lógico: compatibleNearbyPositionsInterpolateWithoutMixingSimulated()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void compatibleNearbyPositionsInterpolateWithoutMixingSimulated() {
        Reading first = reading(38.99665, -.166, 50, false);
        Reading second = reading(38.99670, -.166, 70, false);
        List<Reading> rows = Arrays.asList(first, second, reading(38.99665, -.16594, 90, false));
        Double value = PublicReadings.interpolate(38.99666, -.16599, rows, 30);
        assertNotNull(value);
        assertTrue(value >= 50 && value <= 90);
        rows.set(2, reading(38.99665, -.16594, 90, true));
        assertNull(PublicReadings.interpolate(38.99666, -.16599, rows, 30));
        assertNull(PublicReadings.interpolate(38.99666, -.16599, rows, 51));
    }

    /**
     * Diseño lógico: denseCaseColoursOnlyWhatIsCovered()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void denseCaseColoursOnlyWhatIsCovered() {
        List<Reading> rows = dense(false);
        List<Reading> support = PublicReadings.support(rows);
        assertEquals(15, support.size());
        Sweep result = sweep(rows, 30);
        assertArrayEquals(new int[]{53, 23, 302, 0, 0},
            new int[]{result.value, result.insufficient, result.far, result.mixed, result.radius});
        assertEquals(3932.380126722633, result.sum, TOLERANCE);
        assertEquals(38, result.min, TOLERANCE);
        assertEquals(114, result.max, TOLERANCE);
        assertTrue("IDW no sale del rango de las muestras", result.min >= 38 && result.max <= 114);

        Estimate estimate = PublicReadings.estimate(38.9967, -0.1658, support, 30);
        assertEquals(59.6380563517204, estimate.value, TOLERANCE);
        assertEquals(14, estimate.positions);
        assertEquals(Reason.NONE, estimate.reason);
        estimate = PublicReadings.estimate(38.997, -0.16575, support, 30);
        assertEquals(97.18262845667287, estimate.value, TOLERANCE);
        assertEquals(8, estimate.positions);
        estimate = PublicReadings.estimate(38.9972, -0.16575, support, 30);
        assertNull(estimate.value);
        assertEquals(Reason.FAR, estimate.reason);
        assertEquals(38.80712562013137, estimate.nearest, TOLERANCE);
        // Sobre una muestra (y en su zona propia, T46) manda el valor medido en ella.
        estimate = PublicReadings.estimate(38.996651, -0.166009, support, 30);
        assertEquals(38, estimate.value, TOLERANCE);
        assertTrue(estimate.own);
        assertEquals(8, estimate.positions);
    }

    /**
     * Diseño lógico: coverageGrowsWithTheConfiguredRadiusAndStopsAtItsLimits()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void coverageGrowsWithTheConfiguredRadiusAndStopsAtItsLimits() {
        List<Reading> rows = dense(false);
        int[] expected = {30, 53, 87, 126}, radii = {20, 30, 40, 50};
        for (int i = 0; i < radii.length; i++) assertEquals(expected[i], sweep(rows, radii[i]).value);
        // Tres puntos a ~40 m: sin datos con 20 m; la zona estimada aparece y crece al ampliar el radio.
        List<Reading> triangle = Arrays.asList(reading(A[0], A[1], 50, false),
            reading(A[0] + 0.00036, A[1], 70, false), reading(A[0] + 0.00018, A[1] + 0.0004, 90, false));
        int[] expectedTriangle = {0, 3, 11, 27};
        for (int i = 0; i < radii.length; i++) assertEquals(expectedTriangle[i], sweep(triangle, radii[i]).value);
        // 20 y 50 valen; 19, 51 y 0 no estiman nada.
        List<Reading> support = PublicReadings.support(rows);
        for (int radius : new int[]{19, 51, 0}) {
            assertEquals(Reason.RADIUS, PublicReadings.estimate(38.9967, -0.1658, support, radius).reason);
        }
        for (int radius : new int[]{20, 50}) {
            assertEquals(Reason.NONE, PublicReadings.estimate(38.9967, -0.1658, support, radius).reason);
        }
    }

    /**
     * Diseño lógico: sparseCasesStayWithoutData()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void sparseCasesStayWithoutData() {
        // Tres puntos a ~90 m no se unen con ningún radio admitido.
        List<Reading> sparse = Arrays.asList(reading(A[0], A[1], 50, false),
            reading(A[0] + 0.00081, A[1], 70, false), reading(A[0], A[1] + 0.00104, 90, false));
        for (int radius : new int[]{20, 30, 40, 50}) {
            Sweep result = sweep(sparse, radius);
            assertEquals(0, result.value);
            assertTrue(result.insufficient > 0 && result.far > 0);
        }
        assertEquals(Reason.INSUFFICIENT, PublicReadings.estimate(A[0], A[1], PublicReadings.support(sparse), 50).reason);
        // Dos posiciones próximas tampoco bastan.
        List<Reading> pair = Arrays.asList(sparse.get(0), reading(A[0] + 0.0001, A[1], 70, false));
        for (int radius : new int[]{30, 50}) {
            Sweep result = sweep(pair, radius);
            assertEquals(0, result.value);
            assertTrue(result.insufficient > 0);
        }
    }

    /**
     * Diseño lógico: gpsDriftCountsAsOnePosition()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void gpsDriftCountsAsOnePosition() {
        // Tres lecturas a menos de 5 m son una sola posición (la más reciente).
        List<Reading> jitter = Arrays.asList(
            reading("campaign", "ppb", A[0], A[1], 50, stamp(0), false),
            reading("campaign", "ppb", A[0] + 0.00001, A[1], 70, stamp(1), false),
            reading("campaign", "ppb", A[0], A[1] + 0.00002, 90, stamp(2), false));
        List<Reading> support = PublicReadings.support(jitter);
        assertEquals(1, support.size());
        assertEquals(90, support.get(0).value, 0);
        for (int radius : new int[]{30, 50}) assertEquals(0, sweep(jitter, radius).value);
        assertNull(PublicReadings.interpolate(A[0], A[1], jitter, 50));
        // La separación es un parámetro: con 1 m esas tres lecturas sí cuentan como posiciones distintas.
        assertNotNull(PublicReadings.estimate(A[0], A[1], PublicReadings.support(jitter, 1), 30).value);
    }

    /**
     * Diseño lógico: simulatedInterpolatesAloneAndNeverMixed()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void simulatedInterpolatesAloneAndNeverMixed() {
        assertEquals(3932.380126722633, sweep(dense(true), 30).sum, TOLERANCE);
        List<Reading> simulated = dense(true), real = dense(false);
        List<Reading> mixed = new ArrayList<>(simulated.subList(0, 14));
        mixed.add(real.get(14));
        assertEquals(Reason.MIXED, PublicReadings.estimate(38.99682, -0.16552, PublicReadings.support(mixed), 30).reason);
        Reading last = real.get(14);
        List<Reading> units = new ArrayList<>(real.subList(0, 14));
        units.add(reading("campaign", "µg/m³", last.latitude, last.longitude, last.value, last.date, false));
        assertEquals(Reason.MIXED, PublicReadings.estimate(38.99682, -0.16552, PublicReadings.support(units), 30).reason);
        List<Reading> campaigns = new ArrayList<>(real.subList(0, 14));
        campaigns.add(reading("other", "ppb", last.latitude, last.longitude, last.value, last.date, false));
        assertEquals(Reason.MIXED, PublicReadings.estimate(38.99682, -0.16552, PublicReadings.support(campaigns), 30).reason);
    }

    /**
     * Diseño lógico: repeatedReadingsUseTheMostRecentWhateverTheOrder()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void repeatedReadingsUseTheMostRecentWhateverTheOrder() {
        List<Reading> rows = Arrays.asList(
            reading("campaign", "ppb", A[0], A[1], 10, stamp(0), false),
            reading("campaign", "ppb", A[0], A[1], 99, stamp(30), false),
            reading("campaign", "ppb", A[0] + 0.0001, A[1], 70, stamp(5), false),
            reading("campaign", "ppb", A[0], A[1] + 0.00013, 90, stamp(6), false));
        double lat = A[0] + 0.00003, lon = A[1] + 0.00003;
        assertEquals(99, PublicReadings.interpolate(lat, lon, rows, 30), TOLERANCE);
        assertEquals(99, PublicReadings.interpolate(lat, lon,
            Arrays.asList(rows.get(1), rows.get(3), rows.get(0), rows.get(2)), 30), TOLERANCE);
        List<Reading> reversed = new ArrayList<>(rows);
        Collections.reverse(reversed);
        assertEquals(99, PublicReadings.interpolate(lat, lon, reversed, 30), TOLERANCE);
    }

    /**
     * Diseño lógico: pointQueryExplainsValueOrLackOfData()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void pointQueryExplainsValueOrLackOfData() {
        List<Reading> support = PublicReadings.support(dense(true));
        String[] text = PublicReadings.describe(PublicReadings.estimate(38.9967, -0.1658, support, 30), 30, "ppb", 60, 100, true);
        assertEquals("Estimación: 59,6 ppb · bajo", text[0]);
        assertEquals("14 posiciones en 30 m · la más cercana a 7 m. Valor interpolado, no medido · datos simulados.", text[1]);
        text = PublicReadings.describe(PublicReadings.estimate(38.9972, -0.16575, support, 30), 30, "ppb", 60, 100, false);
        assertEquals("Sin datos", text[0]);
        assertEquals("La medición más cercana está a 39 m (radio de 30 m). Sin datos no significa aire limpio.", text[1]);
        List<Reading> one = PublicReadings.support(Collections.singletonList(reading(A[0], A[1], 50, false)));
        text = PublicReadings.describe(PublicReadings.estimate(A[0], A[1], one, 30), 30, "ppb", 60, 100, false);
        assertEquals("Datos insuficientes", text[0]);
        assertEquals("1 posición en 30 m; hacen falta 3. No se asigna nivel de ozono.", text[1]);
        text = PublicReadings.describe(PublicReadings.estimate(A[0], A[1], Collections.<Reading>emptyList(), 30), 30, "ppb", 60, 100, false);
        assertEquals("No hay mediciones publicadas. Sin datos no significa aire limpio.", text[1]);
    }

    // ---- Rejilla de pintado (zonas redondas): mismos casos y recuentos que aeriq-domain.test.js ----

    /**
     * Diseño lógico: gridLats() --> R
     * Descripción: Procesa grid lats y devuelve el resultado correspondiente.
     */
    private static double[] gridLats() {
        double[] lats = new double[ROWS];
        for (int i = 0; i < ROWS; i++) lats[i] = LAT0 + (i + .5) * LAT_STEP;
        return lats;
    }

    /**
     * Diseño lógico: gridLons() --> R
     * Descripción: Procesa grid lons y devuelve el resultado correspondiente.
     */
    private static double[] gridLons() {
        double[] lons = new double[COLS];
        for (int j = 0; j < COLS; j++) lons[j] = LON0 + (j + .5) * LON_STEP;
        return lons;
    }

    /**
     * Diseño lógico: raster: Text --> tally() --> Z
     * Descripción: Procesa tally y devuelve el resultado correspondiente.
     */
    private static int[] tally(PublicReadings.Raster raster) {
        int[] counts = new int[5];
        for (byte state : raster.states) counts[state]++;
        return counts;
    }

    /**
     * Diseño lógico: rows: [ Text ], radius: Z, lats: R, lons: R, max: Z --> raster() --> Text
     * Descripción: Procesa raster y devuelve el resultado correspondiente.
     */
    private static PublicReadings.Raster raster(List<Reading> rows, int radius, double[] lats, double[] lons, int max) {
        return PublicReadings.raster(Collections.singletonList(PublicReadings.support(rows)), new int[]{radius}, lats, lons, max);
    }

    /**
     * Diseño lógico: rasterMatchesThePointEstimateEverywhere()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void rasterMatchesThePointEstimateEverywhere() {
        double[] lats = gridLats(), lons = gridLons();
        List<Reading> rows = dense(false), support = PublicReadings.support(rows);
        PublicReadings.Raster grid = raster(rows, 30, lats, lons, PublicReadings.MAX_EVALUATIONS);
        assertEquals(ROWS, grid.rows);
        assertEquals(COLS, grid.columns);
        assertFalse(grid.clipped);
        // 53 con valor (bajo + moderado + alto) y 23 insuficientes, como el barrido punto a punto.
        assertArrayEquals(new int[]{302, 23, 21, 21, 11}, tally(grid));
        for (int i = 0; i < ROWS; i++) for (int j = 0; j < COLS; j++) {
            Estimate estimate = PublicReadings.estimate(lats[i], lons[j], support, 30);
            byte expected = estimate.value != null
                ? (estimate.value >= 100 ? PublicReadings.ZONE_HIGH : estimate.value >= 60 ? PublicReadings.ZONE_MEDIUM : PublicReadings.ZONE_LOW)
                : estimate.reason == Reason.INSUFFICIENT ? PublicReadings.ZONE_INSUFFICIENT : PublicReadings.ZONE_NONE;
            assertEquals(expected, grid.states[i * COLS + j]);
        }
        // Solo se evalúan los puntos cercanos a alguna posición, no toda la rejilla.
        assertTrue(grid.evaluated < ROWS * COLS);
        int[] radii = {20, 30, 40, 50}, expected = {30, 53, 87, 126}, triangle = {0, 3, 11, 27};
        List<Reading> tri = Arrays.asList(reading(A[0], A[1], 50, false),
            reading(A[0] + 0.00036, A[1], 70, false), reading(A[0] + 0.00018, A[1] + 0.0004, 90, false));
        for (int i = 0; i < radii.length; i++) {
            int[] t = tally(raster(rows, radii[i], lats, lons, PublicReadings.MAX_EVALUATIONS));
            assertEquals(expected[i], t[2] + t[3] + t[4]);
            t = tally(raster(tri, radii[i], lats, lons, PublicReadings.MAX_EVALUATIONS));
            assertEquals(triangle[i], t[2] + t[3] + t[4]);
        }
    }

    /**
     * Diseño lógico: rasterWorksTopDownLimitsRadiusAndReportsClipping()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void rasterWorksTopDownLimitsRadiusAndReportsClipping() {
        double[] lats = gridLats(), lons = gridLons(), topDown = new double[ROWS];
        for (int i = 0; i < ROWS; i++) topDown[i] = lats[ROWS - 1 - i];
        List<Reading> rows = dense(false);
        // De arriba abajo, como la pantalla: mismo resultado.
        assertArrayEquals(new int[]{302, 23, 21, 21, 11}, tally(raster(rows, 30, topDown, lons, PublicReadings.MAX_EVALUATIONS)));
        // Un radio fuera de 20–50 m no pinta nada.
        assertArrayEquals(new int[]{ROWS * COLS, 0, 0, 0, 0}, tally(raster(rows, 51, lats, lons, PublicReadings.MAX_EVALUATIONS)));
        // Dos grupos que se tocan: donde uno estima y el otro no basta, gana el valor estimado.
        List<Reading> other = Collections.singletonList(reading("other", "ppb", rows.get(0).latitude, rows.get(0).longitude, 50, stamp(0), false));
        PublicReadings.Raster both = PublicReadings.raster(Arrays.asList(PublicReadings.support(other), PublicReadings.support(rows)),
            new int[]{30, 30}, lats, lons, PublicReadings.MAX_EVALUATIONS);
        assertArrayEquals(new int[]{302, 23, 21, 21, 11}, tally(both));
        // Tope de evaluaciones: se marca recortado para avisarlo en el mapa.
        PublicReadings.Raster clipped = raster(rows, 30, lats, lons, 10);
        assertEquals(10, clipped.evaluated);
        assertTrue(clipped.clipped);
    }

    /**
     * Diseño lógico: aSingleMeasurementPaintsACircleOfTheRadiusNotASquare()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void aSingleMeasurementPaintsACircleOfTheRadiusNotASquare() {
        // Rejilla de ~4 m alrededor de un punto: lo pintado es un disco de 30 m (un cuadrado llegaría a ~42 m).
        int n = 41;
        double step = 4 / PublicReadings.METERS_PER_DEGREE, cos = Math.cos(A[0] * Math.PI / 180);
        double[] lats = new double[n], lons = new double[n];
        for (int i = 0; i < n; i++) {
            lats[i] = A[0] + (i - n / 2) * step;
            lons[i] = A[1] + (i - n / 2) * step / cos;
        }
        PublicReadings.Raster grid = raster(Collections.singletonList(reading(A[0], A[1], 50, false)), 30, lats, lons, PublicReadings.MAX_EVALUATIONS);
        double farthest = 0;
        int painted = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            if (grid.states[i * n + j] == PublicReadings.ZONE_NONE) continue;
            assertEquals(PublicReadings.ZONE_INSUFFICIENT, grid.states[i * n + j]);
            farthest = Math.max(farthest, PublicReadings.distance(lats[i], lons[j], A[0], A[1]));
            painted++;
        }
        assertTrue(farthest <= 30 && farthest > 28);
        double disc = Math.PI * 30 * 30 / 16;
        assertTrue(painted + " puntos para un disco de ~" + Math.round(disc), Math.abs(painted - disc) / disc < .05);
    }

    // ---- T46: el color junto a cada punto coincide con su medición (mismos casos que aeriq-domain.test.js) ----

    /**
     * Diseño lógico: grid: Text, lats: R, lons: R, lat: R, lon: R --> levelAt() --> N
     * Descripción: Procesa level at y devuelve el resultado correspondiente.
     */
    private static byte levelAt(PublicReadings.Raster grid, double[] lats, double[] lons, double lat, double lon) {
        int i = 0, j = 0;
        for (int k = 1; k < lats.length; k++) if (Math.abs(lats[k] - lat) < Math.abs(lats[i] - lat)) i = k;
        for (int k = 1; k < lons.length; k++) if (Math.abs(lons[k] - lon) < Math.abs(lons[j] - lon)) j = k;
        return grid.states[i * lons.length + j];
    }

    /**
     * Diseño lógico: from: R, to: R, step: R --> range() --> R
     * Descripción: Procesa range y devuelve el resultado correspondiente.
     */
    private static double[] range(double from, double to, double step) {
        int n = (int) Math.floor((to - from) / step) + 1;
        double[] out = new double[n];
        for (int k = 0; k < n; k++) out[k] = from + k * step;
        return out;
    }

    /**
     * Diseño lógico: value: R --> zoneOf() --> N
     * Descripción: Procesa zone of y devuelve el resultado correspondiente.
     */
    private static byte zoneOf(double value) {
        return value >= 100 ? PublicReadings.ZONE_HIGH : value >= 60 ? PublicReadings.ZONE_MEDIUM : PublicReadings.ZONE_LOW;
    }

    /**
     * Diseño lógico: redPointIsNotSurroundedByYellow()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void redPointIsNotSurroundedByYellow() {
        List<Reading> ring = new ArrayList<>();
        ring.add(reading("campaign", "ppb", A[0], A[1], 110, stamp(0), false));
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            ring.add(reading("campaign", "ppb", A[0] + 0.0001 * Math.sin(a), A[1] + 0.000128 * Math.cos(a), 70, stamp(k + 1), false));
        }
        List<Reading> support = PublicReadings.support(ring);
        double own = PublicReadings.ownRadii(support).get(support.stream().filter(r -> r.value == 110).findFirst().get());
        assertTrue("zona propia = mitad de la distancia a la vecina", own > 5 && own < 6);
        double[] lats = range(A[0] - 0.0004, A[0] + 0.0004, 0.000009), lons = range(A[1] - 0.0005, A[1] + 0.0005, 0.000009 * 1.28);
        PublicReadings.Raster grid = PublicReadings.raster(Collections.singletonList(support), new int[]{30}, lats, lons, PublicReadings.MAX_EVALUATIONS);
        double[][] around = {{0, 0}, {0.00003, 0}, {-0.00003, 0}, {0, 0.00004}, {0, -0.00004}, {0.00002, 0.00003}};
        for (double[] d : around) assertEquals(PublicReadings.ZONE_HIGH, levelAt(grid, lats, lons, A[0] + d[0], A[1] + d[1]));
        for (Reading r : ring) assertEquals(zoneOf(r.value), levelAt(grid, lats, lons, r.latitude, r.longitude));
    }

    /**
     * Diseño lógico: simulatedProfilesKeepTheColourOfEachPoint()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void simulatedProfilesKeepTheColourOfEachPoint() {
        int[][] profiles = {
            {24, 22, 21, 20, 20, 22, 26, 30, 34, 38, 41, 44, 47, 49, 51, 52, 50, 46, 41, 36, 32, 29, 27, 25},
            {38, 36, 35, 34, 35, 37, 45, 52, 60, 66, 70, 74, 79, 84, 87, 86, 82, 76, 70, 66, 62, 58, 54, 48},
            {66, 62, 60, 58, 61, 65, 84, 94, 102, 110, 116, 121, 126, 131, 134, 129, 120, 112, 104, 98, 92, 86, 78, 72}};
        for (int p = 0; p < profiles.length; p++) {
            List<Reading> rows = new ArrayList<>();
            for (int k = 0; k < 24; k++) rows.add(reading("campaign", "ppb", A[0] + (k / 6) * 0.0001, A[1] + (k % 6) * 0.00013, profiles[p][k], stamp(k), false));
            double[] lats = range(A[0] - 0.0004, A[0] + 0.0008, 0.000009), lons = range(A[1] - 0.0005, A[1] + 0.0012, 0.000009 * 1.28);
            PublicReadings.Raster grid = PublicReadings.raster(Collections.singletonList(PublicReadings.support(rows)), new int[]{30}, lats, lons, PublicReadings.MAX_EVALUATIONS);
            for (Reading r : rows) assertEquals("perfil " + p + ", punto de " + r.value, zoneOf(r.value), levelAt(grid, lats, lons, r.latitude, r.longitude));
            boolean yellowOrRed = false, red = false;
            for (byte state : grid.states) { if (state >= PublicReadings.ZONE_MEDIUM) yellowOrRed = true; if (state == PublicReadings.ZONE_HIGH) red = true; }
            if (p == 0) assertFalse("el perfil bueno no tiene zonas amarillas ni rojas", yellowOrRed);
            if (p == 2) assertTrue("el perfil malo tiene zonas rojas", red);
        }
    }

    // ---- T47: donde se solapan dos campañas no gana la primera ----

    /**
     * Diseño lógico: overlappingCampaignsAreInterpolatedTogether()
     * Descripción: Describe la operación realizada y su resultado.
     */
    @Test public void overlappingCampaignsAreInterpolatedTogether() {
        List<Reading> left = new ArrayList<>(), right = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            left.add(reading("one", "ppb", A[0] + (k % 2) * 0.0001, A[1] + (k / 2) * 0.00013, 40, stamp(k), false));
            right.add(reading("two", "ppb", A[0] + (k % 2) * 0.0001, A[1] + 0.00026 + (k / 2) * 0.00013, 105, stamp(k), false));
        }
        double[] lats = new double[40], lons = new double[80];
        for (int i = 0; i < 40; i++) lats[i] = 38.99645 + i * 0.00001;
        for (int j = 0; j < 80; j++) lons[j] = -0.16635 + j * 0.0000128;
        List<Reading> l = PublicReadings.support(left), r = PublicReadings.support(right);
        PublicReadings.Raster ab = PublicReadings.raster(Arrays.asList(l, r), new int[]{30, 30}, lats, lons, PublicReadings.MAX_EVALUATIONS);
        PublicReadings.Raster ba = PublicReadings.raster(Arrays.asList(r, l), new int[]{30, 30}, lats, lons, PublicReadings.MAX_EVALUATIONS);
        assertArrayEquals("el resultado no depende del orden de las campañas", ab.states, ba.states);
        boolean low = false, medium = false, high = false;
        for (byte state : ab.states) { low |= state == PublicReadings.ZONE_LOW; medium |= state == PublicReadings.ZONE_MEDIUM; high |= state == PublicReadings.ZONE_HIGH; }
        assertTrue("la zona común muestra un valor intermedio", medium);
        assertTrue(low && high);
    }
}
