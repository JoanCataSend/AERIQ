package com.aeriq.app.ui;

import com.aeriq.app.data.PublicReadings;
import com.aeriq.app.data.PublicReadings.Reading;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class PublicInterpolationTest {
    private Reading reading(double lat, double lon, double value, boolean simulated) {
        return new Reading("1", "campaign", "UPV", lat, lon, value, "ppb", "2026-11-02", simulated, 30, 60, 100);
    }

    @Test public void noCoverageOrRepeatedLocationStaysWithoutData() {
        Reading row = reading(38.99665, -.166, 50, false);
        assertNull(PublicReadings.interpolate(38.99665, -.166, Arrays.asList(row, row, row), 30));
        assertNull(PublicReadings.interpolate(39, -.16, Collections.singletonList(row), 30));
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
}
