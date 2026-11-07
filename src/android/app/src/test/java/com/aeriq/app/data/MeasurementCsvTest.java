package com.aeriq.app.data;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public final class MeasurementCsvTest {
    @Test public void preservesCoordinatesAndEscapesSpreadsheetText() {
        StoredReading reading = new StoredReading(1, 68, "ppb", 14, "2026-11-03 12:30:00.000",
            "=FORMULA()", "Campaña; norte", "Zona \"A\"", 38.99, -0.166, true, 0);
        String csv = MeasurementCsv.format(Collections.singletonList(reading));
        assertTrue(csv.startsWith("\uFEFFfecha_utc"));
        assertTrue(csv.contains("\"-0.166\""));
        assertTrue(csv.contains("\"'=FORMULA()\""));
        assertTrue(csv.contains("\"Zona \"\"A\"\"\""));
        assertTrue(csv.contains("2026-11-03 12:30:00.000"));
    }
}
