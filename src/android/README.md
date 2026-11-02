# AERIQ Android

Aplicación Kotlin con Jetpack Compose. Incluye acceso a cuentas, mapa de calles,
ubicación del teléfono, recepción iBeacon y envío voluntario de lecturas PBIO.

## Abrir y ejecutar

1. Abre esta carpeta (`src/android/`) en Android Studio.
2. Configura el SDK local y sincroniza Gradle.
3. Ejecuta la variante `debug` en un emulador o teléfono.
4. Para recibir BLE utiliza un dispositivo físico compatible y el emisor PBIO.

El proyecto declara SDK 36, mínimo 24, AGP 8.13.2, Gradle 8.13, Kotlin 2.0.21
y objetivo de bytecode Java 11. La JVM que ejecuta Gradle debe ser compatible con
AGP; utiliza el JDK de Android Studio configurado para el proyecto (17 o compatible).
Las versiones se mantienen en `gradle/libs.versions.toml` y los archivos Gradle.

## Dónde modificar cada cosa

Ruta base: `app/src/main/java/com/aeriq/app/`.

| Ruta | Contenido |
| --- | --- |
| `MainActivity.kt` | Arranque, navegación y coordinación BLE/API/cuentas |
| `ui/` | Pantallas Compose y componentes compartidos |
| `ble/` | Escaneo, selección del emisor y decodificación iBeacon |
| `data/` | Clientes HTTP de cuentas/medidas y almacenamiento cifrado del token |
| `app/src/main/res/` | Iconos, imágenes, estilos XML y textos |
| `app/src/test/` | Pruebas JVM, incluido el parser BLE |
| `app/src/androidTest/` | Pruebas instrumentadas |
| [design_assets](design_assets/README.md) | SVG de referencia para navegación |

## Pruebas

Desde esta carpeta:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
# Con emulador o dispositivo conectado:
.\gradlew.bat connectedDebugAndroidTest
```

`local.properties`, las carpetas de caché y los APK generados no se versionan.
La app conserva sus URLs: cuentas en `/AERIQ/mobile-auth.php` y medidas en
`/biometria/api.php`. No se cambian al mover el proyecto.

Más información: [diseño Android](../../doc/android.md),
[contratos API](../../doc/api.md) y [pruebas de aceptación](../../doc/pruebas.md).
