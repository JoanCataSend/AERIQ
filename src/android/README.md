# Android AERIQ

Aplicación Java con Android Views y un layout XML para la navegación con la identidad y los componentes visuales originales. Conserva mapa, Mi sensor, vinculación, historial, configuración, comparación, Aprende y Cuenta. Las suscripciones se adaptan a soluciones para organizaciones según el backlog actual.

## Abrir y compilar

Abre esta carpeta en Android Studio y sincroniza Gradle. El proyecto declara SDK 36, mínimo 24, AGP 8.13.2, Gradle 8.14.6 y código Java 11. Utiliza un JDK compatible (el entorno de pruebas tiene JDK 17).

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

Para una primera instalación sin caché se necesita sincronización con Internet. BLE y GPS se validan en teléfono físico. Las pruebas instrumentadas requieren emulador o dispositivo.

## Firebase Authentication

`app/google-services.json` es configuración local y está excluida de Git. Descarga el archivo de la app Android `com.aeriq.app` desde Firebase Console y colócalo en `src/android/app/google-services.json` antes de sincronizar o compilar. No subas ese archivo a GitHub. La configuración privada de servidor (`FirebaseConfig.php`) también está excluida; `FirebaseConfig.example.php` contiene únicamente la configuración pública que necesita el login web.

## Rutas y componentes

- `MainActivity.java`: navegación, permisos y coordinación.
- `ui/`: pantallas y componentes Android Views; gráficas e historial usan lecturas guardadas.
- `ble/`: iBeacon, selección de Orb autorizado y deduplicación de anuncios.
- `data/AeriqAuthManager.java`: cuentas y sesión; `AeriqTokenStore.java`: token cifrado.
- `data/AeriqFieldClient.java`: asignaciones, historial y envío autenticado.
- `data/ApiRoutes.java`: único origen web/cuentas/captura/mapa.
- `data/MeasurementCsv.java`: exportación real con escape de CSV.
- `app/src/test/`: parser, interpolación y CSV.
- `design_assets/`: SVG originales de referencia; `res/` conserva logotipos, iconos y fotografías.

La invitación crea una cuenta pendiente. El primer acceso debe cambiar la contraseña temporal. La recepción BLE y el envío tienen estados separados; GPS opcional solo en primer plano. No se estima ni inventa batería. Consulta el [diseño Android](../../doc/android.md) y la [guía de despliegue](../../doc/despliegue.md).
