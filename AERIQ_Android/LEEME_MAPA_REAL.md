# AERIQ Android — Mapa real + ubicación del dispositivo

Esta versión modifica directamente el proyecto Android Studio facilitado el 01/11/2026. Está en **Kotlin + Jetpack Compose** y mantiene intactos el BLE iBeacon y el cliente PHP existentes.

## Novedades

- Cartografía **OpenStreetMap nativa** mediante `osmdroid-android:6.1.20` (Maven Central). No utiliza Google Maps ni necesita API key.
- Desplazamiento y zoom con los dedos; el mapa comienza en la UPV del Grao de Gandía, **38.996651, -0.166009**.
- Al **entrar por primera vez en la pestaña Mapa**, Android solicita los permisos de ubicación **aproximada y precisa**. El sistema puede conceder solo la aproximada. Si se deniegan, el mapa sigue disponible.
- El **punto azul** representa la ubicación obtenida del dispositivo por `LocationManager` (GPS/red/fused, cuando esté disponible). No se utiliza para calcular automáticamente ozono ni se envía al servidor.
- Cuando se obtiene la primera ubicación, el mapa puede centrarse automáticamente en ella. Los botones de la diana y «Ver UPV» permiten desplazarse a la ubicación actual o al campus, respectivamente.
- Se mantienen **cinco sensores y zonas de ozono ficticios** cerca de la UPV, como en la web. Los datos se identifican explícitamente como DEMO. No hay información en directo ni conexión a estaciones oficiales.
- Selector de capas (zonas/sensores) y **modo claro / modo oscuro**. El oscurecimiento se aplica a las teselas; los sensores conservan su color.
- Búsqueda acotada a **UPV, Gandía, Grau y playa de Gandía**, de demostración. Buscar cualquier calle requerirá conectar más adelante un geocodificador.

## Cómo abrirlo

1. Haz una copia de seguridad de tu proyecto anterior.
2. En Android Studio, usa **File > Open** y selecciona la carpeta `AERIQ` de este ZIP.
3. Espera a que Gradle descargue `org.osmdroid:osmdroid-android:6.1.20` y `androidx.lifecycle:lifecycle-runtime-compose:2.10.0`. Se necesita Internet al sincronizar por primera vez.
4. Compila usando **Build > Rebuild Project**, y después ejecuta la app en tu móvil Android.
5. Entra desde la pantalla de acceso a **Mapa**. Aparecerá el diálogo de permisos. Prueba tanto «Permitir» como «No permitir».
6. Si la ubicación no aparece, activa **Ubicación** en los ajustes del teléfono, comprueba que tienes cobertura y pulsa la diana. En un emulador puedes configurar una posición simulada desde **Extended Controls > Location**.

> Este paquete contiene el código fuente del proyecto, no un APK nuevo. No se ha podido ejecutar una compilación Android ni probar GPS físico en este entorno; la verificación definitiva debe realizarse en Android Studio y un dispositivo.

## Archivos modificados o añadidos

- `app/src/main/java/com/aeriq/app/ui/MapScreens.kt` (interfaz del mapa, permisos de ubicación y actualización del dispositivo).
- `app/src/main/java/com/aeriq/app/ui/RealOzoneMap.kt` (**nuevo**, mapa real, capas, marcadores y estilo).
- `app/src/main/AndroidManifest.xml` (permisos `ACCESS_COARSE_LOCATION` y `ACCESS_NETWORK_STATE`; se conservan `ACCESS_FINE_LOCATION` e `INTERNET`).
- `app/build.gradle.kts` (dependencias del mapa y Lifecycle Compose).

## Privacidad y precisión

- La ubicación únicamente se consulta mientras la pantalla Mapa está en primer plano y se dejan de recibir actualizaciones al abandonarla.
- La app no pide permiso de **ubicación en segundo plano**.
- Android 12+ permite autorizar ubicación **aproximada** aunque se soliciten ambos permisos.
- Puede que el GPS tarde en obtener la primera posición; si no hay localización disponible, se sigue mostrando la UPV sin inventar una posición del usuario.
- Los cinco sensores de ozono, sus concentraciones y sus zonas coloreadas son **datos de demostración**. La presencia de cartografía real no los convierte en mediciones reales.

## Consideraciones antes de publicar

Para una demo universitaria basta con la cartografía de OpenStreetMap y osmdroid. El servidor público de teselas de OSM tiene condiciones de uso y límites de tráfico; para desplegar una aplicación comercial con muchos usuarios habría que escoger un proveedor/cartografía con SLA adecuado. Se reconoce a OpenStreetMap dentro de la interfaz.

## Si solo quieres usar el parche

El ZIP denominado `AERIQ_ANDROID_PATCH_MAPA_REAL.zip` contiene únicamente los cuatro archivos de arriba. Descomprímelo y copia la carpeta `app/` encima de la carpeta `app/` de tu proyecto existente conservando lo demás. **No copies** archivos compilados ni el APK viejo.
