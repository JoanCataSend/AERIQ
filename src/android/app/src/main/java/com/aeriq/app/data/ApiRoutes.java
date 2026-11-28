package com.aeriq.app.data;
/*
 * Fichero: ApiRoutes.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/data.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
public final class ApiRoutes {

    /**
     * Diseño lógico: ApiRoutes() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    private ApiRoutes() {}
    public static final String BASE = "https://jcatsen.upv.edu.es/AERIQ/";
    public static final String AUTH = BASE + "mobile-auth.php";
    public static final String FIELD = BASE + "field.php";
    public static final String PUBLIC_MAP = BASE + "public.php";
    /** Orb de la campaña para el admin de campaña (registrar y asignar desde la app). */
    public static final String TEAM = BASE + "mobile-team.php";
    /** Avisos entre personas de la organización (HU36). */
    public static final String NOTICES = BASE + "mobile-avisos.php";
}
