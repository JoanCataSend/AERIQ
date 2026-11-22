package com.aeriq.app.data;

import java.util.List;

public final class MeasurementCsv {
    private MeasurementCsv() {}

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
