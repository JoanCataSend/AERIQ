package com.aeriq.app.data

/** Un único origen para web, cuentas, mapas y mediciones de AERIQ. */
object ApiRoutes {
    const val BASE = "https://jcatsen.upv.edu.es/AERIQ/"
    const val AUTH = BASE + "mobile-auth.php"
    const val FIELD = BASE + "field.php"
    const val PUBLIC_MAP = BASE + "public.php"
}
