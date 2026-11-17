package com.aeriq.app.ui;

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

    private static Reading reading(double lat, double lon, double value, boolean simulated) {
        return reading("campaign", "ppb", lat, lon, value, "2026-11-02", simulated);
    }

    private static Reading reading(String campaign, String unit, double lat, double lon, double value, String date, boolean simulated) {
        return new Reading("1", campaign, "UPV", lat, lon, value, unit, date, simulated, 30, 60, 100);
    }

    private static String stamp(int minutes) {
        return String.format(Locale.ROOT, "2026-11-10 %02d:%02d:00.000", 12 + minutes / 60, minutes % 60);
    }

    /** La rejilla 5 × 3 de la simulación (03-simulacion.sql), posiciones a ~11 m. */
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

    @Test public void parametersMatchTheWeb() {
        assertEquals(20, PublicReadings.RADIUS_MIN);
        assertEquals(50, PublicReadings.RADIUS_MAX);
        assertEquals(30, PublicReadings.RADIUS_DEFAULT);
        assertEquals(3, PublicReadings.MIN_POSITIONS);
        assertEquals(5, PublicReadings.MIN_SEPARATION, 0);
        assertEquals(2, PublicReadings.POWER, 0);
        assertEquals(1, PublicReadings.MIN_DISTANCE, 0);
        assertEquals(10, PublicReadings.CELL, 0);
    }

    @Test public void noCoverageOrRepeatedLocationStaysWithoutData() {
        Reading row = reading(38.99665, -.166, 50, false);
        assertNull(PublicReadings.interpolate(38.99665, -.166, Arrays.asList(row, row, row), 30));
        assertNull(PublicReadings.interpolate(39, -.16, Collections.singletonList(row), 30));
        Estimate empty = PublicReadings.estimate(A[0], A[1], Collections.<Reading>emptyList(), 30);
        assertEquals(Reason.FAR, empty.reason);
        assertTrue(Double.isInfinite(empty.nearest));
    }

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

    @Test public void denseCaseColoursOnlyWhatIsCovered() {
        List<Reading> rows = dense(false);
        List<Reading> support = PublicReadings.support(rows);
        assertEquals(15, support.size());
        Sweep result = sweep(rows, 30);
        assertArrayEquals(new int[]{53, 23, 302, 0, 0},
            new int[]{result.value, result.insufficient, result.far, result.mixed, result.radius});
        assertEquals(3957.222753625807, result.sum, TOLERANCE);
        assertEquals(42.876886387974906, result.min, TOLERANCE);
        assertEquals(109.04794135160219, result.max, TOLERANCE);
        assertTrue("IDW no sale del rango de las muestras", result.min >= 38 && result.max <= 114);

        Estimate estimate = PublicReadings.estimate(38.9967, -0.1658, support, 30);
        assertEquals(62.25689582591126, estimate.value, TOLERANCE);
        assertEquals(14, estimate.positions);
        assertEquals(Reason.NONE, estimate.reason);
        estimate = PublicReadings.estimate(38.997, -0.16575, support, 30);
        assertEquals(95.27058686717591, estimate.value, TOLERANCE);
        assertEquals(8, estimate.positions);
        estimate = PublicReadings.estimate(38.9972, -0.16575, support, 30);
        assertNull(estimate.value);
        assertEquals(Reason.FAR, estimate.reason);
        assertEquals(38.80712562013137, estimate.nearest, TOLERANCE);
        // Sobre una muestra pesa sobre todo esa muestra, sin exigir igualdad.
        estimate = PublicReadings.estimate(38.996651, -0.166009, support, 30);
        assertEquals(38.58790393896943, estimate.value, TOLERANCE);
        assertEquals(8, estimate.positions);
    }

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

    @Test public void simulatedInterpolatesAloneAndNeverMixed() {
        assertEquals(3957.222753625807, sweep(dense(true), 30).sum, TOLERANCE);
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

    @Test public void repeatedReadingsUseTheMostRecentWhateverTheOrder() {
        List<Reading> rows = Arrays.asList(
            reading("campaign", "ppb", A[0], A[1], 10, stamp(0), false),
            reading("campaign", "ppb", A[0], A[1], 99, stamp(30), false),
            reading("campaign", "ppb", A[0] + 0.0001, A[1], 70, stamp(5), false),
            reading("campaign", "ppb", A[0], A[1] + 0.00013, 90, stamp(6), false));
        double lat = A[0] + 0.00003, lon = A[1] + 0.00003;
        assertEquals(92.50890267193577, PublicReadings.interpolate(lat, lon, rows, 30), TOLERANCE);
        assertEquals(92.50890267193577, PublicReadings.interpolate(lat, lon,
            Arrays.asList(rows.get(1), rows.get(3), rows.get(0), rows.get(2)), 30), TOLERANCE);
        List<Reading> reversed = new ArrayList<>(rows);
        Collections.reverse(reversed);
        assertEquals(92.50890267193577, PublicReadings.interpolate(lat, lon, reversed, 30), TOLERANCE);
    }

    @Test public void pointQueryExplainsValueOrLackOfData() {
        List<Reading> support = PublicReadings.support(dense(true));
        String[] text = PublicReadings.describe(PublicReadings.estimate(38.9967, -0.1658, support, 30), 30, "ppb", 60, 100, true);
        assertEquals("Estimación: 62 ppb · moderado", text[0]);
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
}
