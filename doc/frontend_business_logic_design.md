# Diseño de lógica de cliente AERIQ

## Component Design

**Responsabilidad.** Presentar a la GUI una interfaz lógica estable y convertir sus operaciones a las rutas API, además de encapsular HTTP, serialización, CSRF y errores. El proxy es `src/web/aeriq-client.js`; los cálculos locales puros están en `aeriq-domain.js`. `AeriqAuth` en `auth-client.js` es un adaptador separado para sesión, perfil y autenticación.

### Tipos y firmas compartidas con backend

Los tipos compuestos se definen en `doc/aeriq_business_logic_design.md` y se aplican sin cambios aquí. El contexto de sesión es implícito en las credenciales same-origin y se resuelve en el servidor. El subconjunto de dominio consumido por la GUI tiene estas firmas idénticas:

```text
accion: AccionPanel, filtros: FiltrosPanel --> consultar_panel() --> resultado: ResultadoPanel
ambito: AmbitoMapa, procedencia: Procedencia, horas: N --> consultar_mapa() --> resultado: ResultadoMapa
solicitud: DatosSolicitud --> enviar_solicitud() --> confirmacion: ConfirmacionSolicitud
--> consultar_servicios() --> catalogos: CatalogosSolicitud
```

### Adaptación del proxy a rutas actuales

| Operación lógica | Adaptador JavaScript | Ruta y acción actual | Forma de salida del API |
|---|---|---|---|
| `consultar_panel` | `Aeriq.consultar_panel(accion, filtros)` | Traduce `resumen/lecturas/solicitudes` a `portal.php?action=dashboard/readings/requests` | `ResumenPanel`, `InformePanel` o `SolicitudesPanel`, según la acción |
| `consultar_mapa` | `Aeriq.consultar_mapa(ambito, procedencia, horas)` | `public.php?action=map` o `portal.php?action=map` para organización | `ResultadoMapa: measurements,mode,hours,truncated` |
| `enviar_solicitud` | `Aeriq.enviar_solicitud(datos)` | `public.php?action=request` | `ConfirmacionSolicitud: ok,id,mailed,message` |
| `consultar_servicios` | `Aeriq.consultar_servicios()` | `public.php?action=services` | `CatalogosSolicitud: services,orgTypes,objectives,provinces,durations` |

La interfaz conserva `portal`, `map`, `submit` y `services` como alias compatibles para las operaciones de escritura y páginas aún no migradas. Las vistas usan los nombres lógicos para panel, mapa y solicitudes comerciales. El contexto de autenticación viaja en la cookie same-origin; el servidor carga y autoriza la identidad, sin confiar en un objeto `Acceso` del navegador. Las acciones restantes de gestión se enumeran en `doc/api.md`.

### GUI y transporte

- Las vistas `portal.js`, `fleet.js`, `notices.js` y `public-map.js` usan `Aeriq`/`AeriqAuth`; `app.js` envía los cambios de perfil con `AeriqAuth.post('profile', ...)` y no hace `fetch` directo.
- Las peticiones API de dominio se realizan en `aeriq-client.js`, autenticación y perfil en `auth-client.js`, PBIO legado en `pbio-client.js`, y configuración pública de Firebase en `google-auth.js`.
- `precarga-3d.js` descarga un recurso GLB estático; no es una operación de negocio ni una llamada a endpoint API.
- `aeriq-domain.js` calcula CSV, estadísticas e interpolación sin DOM ni red.

## Design Clarifications

- Los permisos visibles en el navegador son solo presentación; el servidor vuelve a autorizar cada operación.
- `google-auth.js` obtiene configuración pública desde `auth.php?action=firebase-config`; el intercambio del ID token sí usa `AeriqAuth.post('google', ...)`.
- La GUI se documenta en sus HTML y documentos de producto. Las operaciones lógicas no dependen de la presentación.

## General Rules

- **Programming Language:** JavaScript ES2020+ en navegador; HTML y CSS para presentación.
- La GUI invoca proxy de dominio y autenticación; solo los adaptadores construyen solicitudes HTTP de aplicación.
- No confiar en filtros, permisos ni identificadores enviados por el navegador; validar en servidor.
- **Function/Method Headers:** cada función o método debe tener antes un comentario con su propósito y diseño lógico `entradas → función → salida`; conservar el formato delimitado por líneas `--------------------` cuando se documenten contratos compartidos.
- **Code Readability:** mantener funciones enfocadas, cálculos puros reutilizables y DOM seguro (`textContent`, nodos y valores escapados).
- **Automated Testing:** generar pruebas unitarias de cálculos y pruebas de contrato para adaptadores, errores y permisos visibles.
