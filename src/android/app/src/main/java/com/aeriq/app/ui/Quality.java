package com.aeriq.app.ui;
/*
 * Fichero: Quality.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ui.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Diseño lógico: Quality() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
    private Quality() {}

    /**
     * Diseño lógico: value: R, medium: R, high: R --> level() --> Z
     * Descripción: Clasifica el valor según los umbrales medio y alto.
     */
    public static int level(double value, double medium, double high) {
        return value >= high ? 2 : value >= medium ? 1 : 0;
    }

    /**
     * Diseño lógico: level: Z --> color() --> Z
     * Descripción: Procesa color y devuelve el resultado correspondiente.
     */
    public static int color(int level) { return level == 2 ? HIGH : level == 1 ? MEDIUM : LOW; }
    /**
     * Diseño lógico: level: Z --> soft() --> Z
     * Descripción: Procesa soft y devuelve el resultado correspondiente.
     */
    public static int soft(int level) { return level == 2 ? HIGH_SOFT : level == 1 ? MEDIUM_SOFT : LOW_SOFT; }
    /**
     * Diseño lógico: level: Z --> text() --> Z
     * Descripción: Procesa text y devuelve el resultado correspondiente.
     */
    public static int text(int level) { return level == 2 ? HIGH_TEXT : level == 1 ? MEDIUM_TEXT : LOW_TEXT; }
    /**
     * Diseño lógico: level: Z --> label() --> Text
     * Descripción: Procesa label y devuelve el resultado correspondiente.
     */
    public static String label(int level) { return level == 2 ? "Mala" : level == 1 ? "Moderada" : "Buena"; }

    /**
     * Diseño lógico: date: Text --> utc() --> Z
     * Descripción: Convierte una fecha UTC en milisegundos desde el epoch.
     */
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

    /**
     * Diseño lógico: millis: Z --> when() --> Text
     * Descripción: Formatea una fecha como hora local relativa al día actual.
     */
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

    /**
     * Diseño lógico: millis: Z --> ago() --> Text
     * Descripción: Formatea una duración como segundos, minutos, horas o días.
     */
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

    /**
     * Diseño lógico: startOfToday() --> Z
     * Descripción: Procesa start of today y devuelve el resultado correspondiente.
     */
    public static long startOfToday() {
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }

    /**
     * Diseño lógico: a: Text, b: Text --> sameDay() --> B
     * Descripción: Procesa same day y devuelve el resultado correspondiente.
     */
    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Diseño lógico: value: R --> number() --> Text
     * Descripción: Procesa number y devuelve el resultado correspondiente.
     */
    public static String number(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
            ? String.valueOf(Math.round(value))
            : String.format(Locale.getDefault(), "%.1f", value);
    }
}
