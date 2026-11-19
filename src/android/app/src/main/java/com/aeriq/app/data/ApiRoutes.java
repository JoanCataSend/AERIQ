package com.aeriq.app.data;

public final class ApiRoutes {
    private ApiRoutes() {}
    public static final String BASE = "https://jcatsen.upv.edu.es/AERIQ/";
    public static final String AUTH = BASE + "mobile-auth.php";
    public static final String FIELD = BASE + "field.php";
    public static final String PUBLIC_MAP = BASE + "public.php";
    /** Orb de la campaña para el admin de campaña (registrar y asignar desde la app). */
    public static final String TEAM = BASE + "mobile-team.php";
}
