package com.aeriq.app.ui;

import java.text.SimpleDateFormat;
import java.util.*;

/** Niveles de calidad, colores y fechas compartidos por el mapa y Mi sensor. */
public final class Quality {
    public static final int LOW = 0xff219c72, MEDIUM = 0xffeea22d, HIGH = 0xffe35b5d, NONE = 0xffd5dcd9;
    public static final int LOW_SOFT = 0xffdcefe8, MEDIUM_SOFT = 0xfffbe8cc, HIGH_SOFT = 0xfff9dcdc;
    public static final int LOW_TEXT = 0xff0e6b5e, MEDIUM_TEXT = 0xff94580a, HIGH_TEXT = 0xffb3302f;

    private Quality() {}

    /** 0 = bajo/buena, 1 = moderado, 2 = alto/mala. */
    public static int level(double value, double medium, double high) {
        return value >= high ? 2 : value >= medium ? 1 : 0;
    }

    public static int color(int level) { return level == 2 ? HIGH : level == 1 ? MEDIUM : LOW; }
    public static int soft(int level) { return level == 2 ? HIGH_SOFT : level == 1 ? MEDIUM_SOFT : LOW_SOFT; }
    public static int text(int level) { return level == 2 ? HIGH_TEXT : level == 1 ? MEDIUM_TEXT : LOW_TEXT; }
    public static String label(int level) { return level == 2 ? "Mala" : level == 1 ? "Moderada" : "Buena"; }

    /** Fecha UTC del servidor ("yyyy-MM-dd HH:mm:ss") a milisegundos; 0 si no se entiende. */
    public static long utc(String date) {
        if (date == null) return 0;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date parsed = format.parse(date.length() > 19 ? date.substring(0, 19) : date);
            return parsed == null ? 0 : parsed.getTime();
        } catch (Exception error) {
            return 0;
        }
    }

    /** "hoy, 12:45", "ayer, 09:10" o "3 nov, 18:00" en hora local. */
    public static String when(long millis) {
        if (millis <= 0) return "—";
        Calendar today = Calendar.getInstance();
        Calendar moment = Calendar.getInstance();
        moment.setTimeInMillis(millis);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        if (sameDay(today, moment)) return "hoy, " + time;
        today.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(today, moment)) return "ayer, " + time;
        return new SimpleDateFormat("d MMM", new Locale("es", "ES")).format(new Date(millis)) + ", " + time;
    }

    /** "5 s", "3 min", "2 h", "4 días". */
    public static String ago(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) return seconds + " s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h";
        long days = hours / 24;
        return days + (days == 1 ? " día" : " días");
    }

    public static long startOfToday() {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
            ? String.valueOf(Math.round(value))
            : String.format(Locale.getDefault(), "%.1f", value);
    }
}
