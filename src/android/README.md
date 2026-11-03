# Android AERIQ

Aplicación Kotlin/Compose con la identidad y los componentes visuales originales. Conserva mapa, Mi sensor, vinculación, historial, configuración, comparación, Aprende y Cuenta. Las suscripciones se adaptan a soluciones para organizaciones según el backlog actual.

## Abrir y compilar

Abre esta carpeta en Android Studio y sincroniza Gradle. El proyecto declara SDK 36, mínimo 24, AGP 8.13.2, Gradle 8.13 y Kotlin 2.0.21. Utiliza un JDK compatible (el entorno de pruebas tiene JDK 17).

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

Para una primera instalación sin caché se necesita sincronización con Internet. BLE y GPS se validan en teléfono físico. Las pruebas instrumentadas requieren emulador o dispositivo.

## Rutas y componentes

- `MainActivity.kt`: navegación, permisos y coordinación.
- `ui/`: componentes originales adaptados; gráficas e historial usan lecturas guardadas.
- `ble/`: iBeacon, selección de Orb autorizado y deduplicación de anuncios.
- `data/AeriqAuthManager.kt`: cuentas y sesión; `AeriqTokenStore.kt`: token cifrado.
- `data/AeriqFieldClient.kt`: asignaciones, historial y envío autenticado.
- `data/ApiRoutes.kt`: único origen web/cuentas/captura/mapa.
- `data/MeasurementCsv.kt`: exportación real con escape de CSV.
- `app/src/test/`: parser, interpolación y CSV.
- `design_assets/`: SVG originales de referencia; `res/` conserva logotipos, iconos y fotografías.

La invitación crea una cuenta pendiente. El primer acceso debe cambiar la contraseña temporal. La recepción BLE y el envío tienen estados separados; GPS opcional solo en primer plano. No se estima ni inventa batería. Consulta el [diseño Android](../../doc/android.md) y la [guía de despliegue](../../doc/despliegue.md).
