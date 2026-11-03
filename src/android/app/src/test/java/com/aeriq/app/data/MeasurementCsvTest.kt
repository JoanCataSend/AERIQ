package com.aeriq.app.data

import org.junit.Assert.*
import org.junit.Test

class MeasurementCsvTest {
    @Test fun preservesCoordinatesAndEscapesSpreadsheetText() {
        val r=StoredReading(1,68.0,"ppb",14,"2026-11-03 12:30:00.000","=FORMULA()","Campaña; norte","Zona \"A\"",38.99,-0.166,true)
        val csv=measurementCsv(listOf(r))
        assertTrue(csv.startsWith("\uFEFFfecha_utc"))
        assertTrue(csv.contains("\"-0.166\""))
        assertTrue(csv.contains("\"'=FORMULA()\""))
        assertTrue(csv.contains("\"Zona \"\"A\"\"\""))
        assertTrue(csv.contains("2026-11-03 12:30:00.000"))
    }
}
