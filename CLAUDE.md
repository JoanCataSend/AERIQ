# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

AERIQ (producto) · Orb (sensor de ozono) · Código Norte (empresa). Proyecto académico PBIO (UPV). Toda la documentación y los nombres del dominio/esquema están en español; mantén ese idioma.

Flujo completo: **Orb (firmware nRF52840) → anuncios BLE iBeacon → app Android → API PHP → MariaDB → web y mapas.**

## Comandos

Desde la raíz del repositorio (Windows/PowerShell; Python 3.9+):

```powershell
python scripts/audit-repository.py                 # estructura, enlaces de docs, secretos, sintaxis JS/PHP, SHA-256 de recursos originales
node scripts/tests/aeriq-domain.test.js            # dominio JS: IDW, estadísticas, CSV (sin DOM)
node scripts/tests/ui-contract.test.js             # prueba DOM con jsdom y API falsa (ver abajo)
python scripts/tests/integration-aeriq.py --php C:/xampp/php/php.exe --mysql-bin C:/xampp/mysql/bin   # PHP+MariaDB aislados
php scripts/tests/google-auth-unit.php
python scripts/tests/google-auth-live.py           # solo envía un token inválido al despliegue real
node scripts/tests/preview-aeriq.js                # vista previa estática con respuestas ficticias en 127.0.0.1:8766 (sin PHP)
```

jsdom se instala solo como herramienta local, nunca como dependencia de la web:
`npm install --prefix .local-backups/test-tools jsdom --no-audit --no-fund`

Android (desde `src/android/`):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.aeriq.app.ble.IBeaconParserTest"   # una sola clase
```

Firmware: `arduino-cli compile --fqbn adafruit:nrf52:sparkfunnrf52840mini src/firmware/NodoO3`. Sus pruebas lógicas se compilan a WebAssembly con clang y se ejecutan con `node src/firmware/tests/run.mjs` (pasos exactos en `src/firmware/tests/README.md`).

Empaquetado para Plesk: `python scripts/build-plesk-package.py` (salida en `.dist/`).

## Arquitectura

### Web/API (`src/web/`, es la raíz desplegable tal cual)

HTML/CSS/JS sin framework ni build; PHP sin framework. `styles.css` es el CSS compartido. Endpoints delgados que delegan en `server/`:

| Endpoint | Uso | Autenticación |
|---|---|---|
| `auth.php` | Cuentas web (login, Google, recuperación, perfil) | cookie + cabecera `X-CSRF-Token` |
| `mobile-auth.php` | Cuentas Android | `X-AERIQ-Token` (hash en `sesiones_moviles`) |
| `portal.php?action=…` | Panel de organización, flota, informes, solicitudes | sesión + rol + CSRF en POST |
| `field.php` | Asignaciones, historial e ingesta de lecturas desde Android | token móvil, rol trabajador/admin |
| `public.php` | Mapa público y formularios de solicitud | anónimo |
| `api.php?accion=…` | Adaptador de compatibilidad (solo lectura) | admin/analista para datos privados |

Capas en `server/`: `Logica.php` (conexión MySQLi UTF-8/UTC) → `AuthCore.php` (identidad, contraseñas, correo) → `AeriqCore.php` (dominio y permisos) → `AeriqHttp.php` (CSRF, token móvil, errores HTTP vía `AeriqError` / `aeriq_failure`). `FirebaseAuth.php` verifica ID tokens de Google con certificados públicos y solo vincula cuentas ya existentes (no hay autoregistro). Clientes JS: `auth-client.js`, `aeriq-client.js`/`pbio-client.js`; `aeriq-domain.js` contiene cálculos puros (IDW, CSV, estadísticas) testeables sin DOM.

Reglas del dominio que el código hace cumplir:
- Rol y organización se leen siempre en servidor, nunca del JSON del cliente.
- Las únicas escrituras de mediciones van por `field.php`; no hay inserción anónima.
- Ingesta idempotente por `eventId` (reintento con datos distintos → 409).
- Datos reales y simulados separados; enviar al servidor ≠ publicar en el mapa público.
- Ozono en ppb, temperatura en °C, fechas UTC; no se convierten unidades.
- Cambiar de trabajador cierra la asignación anterior; las lecturas conservan su asignación histórica.

Si cambias un contrato HTTP, actualiza `doc/api.md` y los clientes (web y Android) afectados.

### Base de datos (`src/database/`)

Esquema único en `schema.sql` (once tablas, vistas `vista_mediciones` y `vista_flota`). `mediciones` → `asignaciones` (organización + Orb + trabajador + campaña); `magnitudes` define rango/unidad usados por la validación de ingesta. Los demás `.sql` son migraciones repetibles, datos demo o scripts de recreación; documenta cualquier cambio de esquema como migración antes de aplicarlo.

`scripts/reset-database.py` y `recrear-*.sql` son destructivos: usa `--dry-run`, exigen `--confirm RECREAR:<base>` y hacen copia previa. No los ejecutes como paso rutinario ni contra Plesk sin que el usuario lo pida.

### Android (`src/android/`)

Java 11 con **Android Views** (no Compose, aunque `src/README.md` diga Kotlin/Compose). Paquete `com.aeriq.app`, minSdk 24, targetSdk 36. `MainActivity` coordina navegación y permisos; `ui/` pantallas; `ble/` parser iBeacon y deduplicación; `data/` clientes HTTP, token cifrado, cola de reintentos (`ReadingOutbox`). `data/ApiRoutes.java` es el único lugar con el origen del servidor (`https://ldiamur.upv.edu.es/src/web/`).

### Firmware (`src/firmware/NodoO3/`)

Arduino, Adafruit nRF52 BSP 1.7.0, SparkFun Pro nRF52840 Mini. Sin `delay()`: máquina de estados con `millis()`/`micros()`. El contrato BLE (tipos 14 = O3, 12 = temperatura, 15 = estado inválido; major = tipo/secuencia, minor = valor) está especificado en `src/firmware/README.md` y debe mantenerse compatible con `IBeaconParser.java`.

## Configuración privada (excluida de Git)

`src/web/server/{SDBaseDatos,SMTPConfig,MailConfig,FirebaseConfig}.php` (copiar de los `*.example.php`) y `src/android/app/google-services.json`. `.local-backups/` guarda copias, entornos de integración y herramientas de prueba; `.dist/` los paquetes. `vendor/` se rellena con Composer solo si se usa SMTP.

## Convenciones

- No sustituyas recursos gráficos originales (imágenes, SVG, galería, visor 3D): la auditoría comprueba sus SHA-256 contra `scripts/tests/original-assets.json`.
- No inventes datos: sin batería ni GPS ficticios, sin presentar datos simulados como reales.
- `doc/historico/` es solo referencia; las instrucciones vigentes están en los README y en `doc/`.
- Al terminar un cambio, indica qué se comprobó y qué no (BLE, GPS, correo real y Plesk requieren entorno físico).
