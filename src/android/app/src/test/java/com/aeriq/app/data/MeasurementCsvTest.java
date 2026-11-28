package com.aeriq.app.data;
/*
 * Fichero: MeasurementCsvTest.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/test/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public final class MeasurementCsvTest {
    /**
     * Diseño lógico: preservesCoordinatesAndEscapesSpreadsheetText()
     * Descripción: Describe la operación realizada y su resultado.
     */
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
