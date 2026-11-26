# Diseño de comunicación web AERIQ

## Component Design

**Responsabilidad.** Recibir solicitudes HTTP, validar método y forma de transporte, obtener identidad de sesión/token, invocar operaciones lógicas y serializar respuestas JSON. Los puntos de entrada están en `src/web/`: `auth.php`, `mobile-auth.php`, `portal.php`, `field.php`, `public.php`, `api.php`, `mobile-team.php`, `mobile-avisos.php` y `sensor-link.php`.

### Contrato lógico con la capa de negocio

Las firmas compartidas se definen en `doc/aeriq_business_logic_design.md`. La capa de comunicación valida datos propios del protocolo (método, JSON, CSRF/token, parámetros de ruta y serialización); las reglas y permisos de dominio deben ejecutarse en servicios de negocio y no en las rutas.

### Estado actual y delegación

Las rutas eligen el contrato y método HTTP; `portal.php` delega autorización a `aeriq_portal_access()` y las lecturas de panel, mapa, catálogos y solicitudes a operaciones de `AeriqCore.php`. `api.php` ya delega sus consultas de compatibilidad. `AuthCore.php` mantiene las reglas de sesión y autenticación, y delega la persistencia de cuentas/códigos a `AuthRepository.php`. La ejecución parametrizada común está en `SqlGateway.php`; el envío SMTP/PHPMailer/mail() está en `MailTransport.php`. `AeriqCore.php` conserva SQL específico de sus casos de uso y transacciones, pendiente de migración a repositorios por entidad.

| Interfaz | Transporte y autorización | Delegación actual | Contrato |
|---|---|---|---|
| `auth.php?action=…` | JSON; sesión/CSRF o acceso público según acción | `AuthCore.php`, `FirebaseAuth.php`, `AeriqCore.php` | sesión, login, perfil, recuperación y correo; [API](api.md) |
| `mobile-auth.php?action=…` | JSON; credenciales/token móvil | `AuthCore.php`, `FirebaseAuth.php`, `AeriqCore.php` | acciones de cuenta móvil; [API](api.md) |
| `portal.php?action=…` | JSON; sesión, permisos y CSRF en escrituras | `AeriqCore.php`, `Avisos.php` | panel, flota, informes, solicitudes y avisos; [API](api.md) |
| `field.php?action=…` | JSON; token móvil y asignación vigente | `AeriqCore.php` | ingesta, historial, batería y compartir; [API](api.md) |
| `public.php?action=…` | JSON; anónimo con límites de abuso | `AeriqCore.php` | mapa público, catálogos y solicitudes; [API](api.md) |
| `mobile-team.php`, `mobile-avisos.php` | JSON; token móvil y permisos | `AeriqCore.php`, `Avisos.php` | mismos casos de uso que la web |
| `sensor-link.php` | JSON; sesión móvil/web según contrato | `SensorLink.php` | vinculación de AERIQ y campaña |
| `api.php?accion=…` | JSON; lectura pública acotada o sesión con permiso | `aeriq_health()`, `aeriq_magnitudes()`, `aeriq_devices()` y `aeriq_readings()` | PBIO legado; [API](api.md) |

### Errores

`src/web/business_logic/DomainError.php` expresa categorías semánticas sin códigos de transporte. `src/web/communication/HttpErrorMapper.php` las traduce al estado HTTP y mantiene los campos `error` y opcionalmente `field`. `AeriqHttp.php`, `auth.php` y `mobile-auth.php` aplican esa traducción.

## Design Clarifications

- Las rutas, parámetros y respuestas HTTP exactos permanecen en `doc/api.md`.
- Nunca se acepta la organización, rol o permiso del cliente como autoridad. La identidad se relee y autoriza en servidor.
- El mapa público solo incluye mediciones válidas, geolocalizadas y autorizadas para publicación.
- Las rutas y los casos de uso no deben depender de detalles del transporte ni de la conexión. El transporte de correo y la conexión SQL tienen adaptadores; las consultas específicas de AeriqCore están en migración a repositorios.

## General Rules

- **Programming Language:** PHP 8.1+; HTTP/JSON es el protocolo externo.
- Las rutas solo deben encargarse de transporte, validación del protocolo y serialización; las reglas se delegan a servicios de negocio.
- Los parámetros SQL se enlazan; no interpolar datos de usuario en consultas.
- Los errores no deben exponer credenciales, tokens, rutas del servidor ni consultas SQL.
- **Function/Method Headers:** cada función o método nuevo debe incluir su diseño lógico en un bloque delimitado por líneas `--------------------`.
- **Code Readability:** nombres y flujo claros; evitar comentarios que repitan el código.
- **Automated Testing:** generar pruebas unitarias/de integración para rutas críticas, autenticación, autorización, errores y contratos.
