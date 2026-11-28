package com.aeriq.app.data;
/*
 * Fichero: MeasurementCsv.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import java.util.List;

public final class MeasurementCsv {

    private MeasurementCsv() {}

    /**
     * Formatea.
     * Diseño lógico: (List<StoredReading> rows) → format() → String; según doc/android_design.md.
     */
    public static String format(List<StoredReading> rows) {
        StringBuilder csv = new StringBuilder("\uFEFFfecha_utc;valor;unidad;dispositivo;campana;zona;latitud;longitud;simulado\r\n");
        for (StoredReading row : rows) {
            Object[] values = {row.date, row.value, row.unit, row.device, row.campaign, row.zone,
                row.latitude, row.longitude, row.simulated};
            for (int i = 0; i < values.length; i++) {
                if (i > 0) csv.append(';');
                Object value = values[i];
                String raw = value == null ? "" : value.toString();
                String trimmed = raw.trim();
                if (value instanceof String && !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) raw = "'" + raw;
                csv.append('"').append(raw.replace("\"", "\"\"")).append('"');
            }
            csv.append("\r\n");
        }
        return csv.toString();
    }
}
