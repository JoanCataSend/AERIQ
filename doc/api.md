# Contratos API AERIQ

Las rutas se publican en `https://ldiamur.upv.edu.es/src/web/` (por ejemplo, `auth.php` y `mobile-auth.php` están en `httpdocs/src/web/`). El esquema completo está en [schema.sql](../src/database/schema.sql); no depende de tablas antiguas. Fechas UTC, JSON y HTTPS para sesiones/operaciones privadas.

## Cuentas

Web: `auth.php?action=session` devuelve `csrf,user`. Los POST `login,google,logout,forgot,reset-password,profile,change-password` mantienen el contrato existente, cookie segura y `X-CSRF-Token`. No hay autoregistro: las cuentas las crea un administrador desde el portal de la organización. `auth.php?action=firebase-config` devuelve la configuración pública de Firebase y si el botón de Google está listo.

Móvil: `mobile-auth.php` reutiliza acciones de cuenta; las protegidas utilizan `X-AERIQ-Token`. Login y recuperación pueden devolver `token,user`; el cambio de contraseña rota el token. Los tokens se guardan como hash en `sesiones_moviles`. `google` recibe `idToken` de Firebase. `social-login` ya no acepta correos sin comprobar: responde `410` hasta que Android envíe un token real. No existe `register` móvil: las cuentas las crea la organización.

La acción `google` verifica la firma RS256 del ID token con los certificados públicos de Google, comprueba emisor, proyecto, caducidad y proveedor `google.com`, y busca el UID en `identidades_externas` (proveedor `firebase`). En el primer acceso exige que el correo verificado coincida con una cuenta creada por el administrador; nunca registra correos nuevos. Si la invitación sigue vigente, la activa y anula la contraseña temporal. Si la cuenta está desactivada o invitación caducada, rechaza el acceso.

`user` incluye `role` (código del rol, p. ej. `admin`), `roleName` (nombre visible), `permissions` (lista de permisos que concede el rol según `rol_permisos`), `organizationId`, `organization` y `mustChangePassword`. Los clientes deciden qué mostrar con `permissions`, no comparando nombres de rol: así un rol nuevo funciona sin cambiar la web ni la app. Una cuenta invitada está inactiva y solo puede sustituir su contraseña antes de la caducidad. La contraseña temporal vence a las 24 h por defecto; `AERIQ_INVITATION_HOURS` permite 1–168 h. Recuperar por correo permite activar un acceso caducado. No se permite operar antes de activar la cuenta. La misma invitación se puede aceptar con Google usando exactamente el correo registrado por el administrador.

## Organización: `portal.php?action=…`

| Acción | Método / función | Datos |
| --- | --- | --- |
| `dashboard` | GET `informes.ver` | `workers` (con `rol`, `rol_nombre`, `puede_medir`), `campaigns` (con `zona` y umbrales de ozono), `devices` (con `modelo`), `zones`, `roles` asignables y `thresholds` de referencia |
| `worker` | POST `organizacion.gestionar` | `name,last,email,role`; `role` debe ser un código de `roles` con `asignable=1` (hoy trabajador o analista) |
| `campaign` | POST `organizacion.gestionar` | `id?`, `name,zone,start,end,active,public,radius,medium,high`. `zone` es el nombre: se reutiliza la zona si existe. `medium/high` se guardan en `campana_umbrales` |
| `device` | POST `organizacion.gestionar` | `id?`, `name,address,worker,campaign,active,simulated`; `worker` debe tener el permiso `mediciones.registrar` |
| `readings` | GET `informes.ver` | `type` (14, 12 o `all`), `campaign,devices` (IDs con comas), `zone` (nombre de zona), `from,to`. Cada fila incluye `tipo`; los umbrales son `null` en magnitudes sin umbral (temperatura) |
| `map` | GET `mapa.organizacion` | Mismo formato de mapa público. Sin `informes.ver` solo ve sus propias asignaciones |
| `requests` | GET `solicitudes.gestionar` | `requests` (con `servicio`, `servicio_nombre`, `estado`, `estado_nombre` e `historial`) y `states` (catálogo ordenado) |
| `request-status` | POST `solicitudes.gestionar` | `id,status`; `status` es un código de `estados_solicitud` (`pendiente`, `en_revision`, `respondida`, `cerrada`). Cada cambio queda en `solicitudes_historial` con la persona y la fecha |

Todo POST del panel requiere CSRF. El rol, sus permisos y la organización se consultan en servidor. Las asignaciones preservan su histórico; dar de baja no borra mediciones. Fechas de campaña `YYYY-MM-DD`, radio 20–50 m y umbrales de ozono en ppb. Un informe de más de 10.000 filas devuelve 413 y pide acotar el filtro.

## Campo Android: `field.php`

Requiere el permiso `mediciones.registrar` (igual que `sensor-link.php`). GET devuelve `assignments` vigentes, con `zona`, `umbral_medio` y `umbral_alto` del ozono (los de la campaña o, si no tiene, los de referencia). GET `?action=history` devuelve hasta 1.000 lecturas válidas de las asignaciones actuales y anteriores del trabajador. Se valida token, activación y función.

POST incluye:

```json
{"eventId":"014be610-334a-49cb-8f78-345af82a2551","deviceId":1,"assignmentId":1,"campaignId":1,"address":"D0:00:00:00:00:01","type":14,"unit":"ppb","value":68,"capturedAt":"2026-11-03T12:00:00.000Z","counter":7,"rssi":-60,"latitude":38.99665,"longitude":-0.166,"accuracy":5}
```

GPS es opcional como conjunto. El servidor consulta el rango/unidad de `magnitudes`, rechaza (422) una magnitud que el modelo del Orb no mide (`modelo_magnitudes`), comprueba asignación y fechas, y toma la procedencia del Orb. `assignmentId` y `unit` se comprueban si se incluyen; la app actual siempre los envía. No se permite que un cliente elija otra organización o declare su lectura como real.

El UUID de evento se genera una sola vez y se reutiliza al reintentar. Respuesta: `ok,id,eventId,duplicate`. Reutilizar el evento con datos diferentes devuelve 409; cambiar de asignación no reasigna lecturas históricas.

## Público: `public.php`

GET `?action=map&mode=real|simulated&hours=24|168|0` devuelve `measurements,mode,hours,truncated`; `0` significa histórico completo dentro del límite. Solo incluye ozono válido con coordenadas y publicación de campaña autorizada. Máximo 2.000 lecturas recientes del filtro, sin identidad del trabajador.

GET `?action=services` devuelve los catálogos del formulario comercial:
- `services`: código, nombre, descripción y `mide_superficie` de cada servicio activo (`kits`, `estudio`, `asesoramiento`), ordenados por `servicios.orden`.
- `orgTypes` y `objectives`: código y nombre de los tipos de organización y de los objetivos de medición activos.
- `provinces`: provincias de España (por ahora solo se atiende España).
- `durations`: duraciones que se ofrecen para un estudio.

POST `?action=request` (máximo 8 por hora y dirección):
- Obligatorios: `interest` (código de servicio), `orgType`, `entity`, `contact`, `email`, `municipality`, `province` (de `provinces`), `zone`, `objectives` (lista no vacía de códigos), `needs` (máximo 6.000 caracteres) y `consent: true`.
- `phone` es opcional. Si se envía, son 9 cifras que empiezan por 6, 7, 8 o 9, sin prefijo ni espacios.
- Solo en servicios con `mide_superficie` (estudio): `duration` (de `durations`) es obligatoria; `surface` (m², mayor que 0) y `startMonth` (`AAAA-MM`, no anterior al mes actual) son opcionales. En el resto de servicios estos campos se ignoran.
- No se pide cantidad de Orb: la recomienda Código Norte.
- `website` es un campo trampa. Si llega con texto, se responde 201 sin guardar nada.

Un error de validación devuelve 422 con `error` y `field` (el campo que hay que revisar). La solicitud nace en el primer estado del catálogo, guarda sus objetivos en `solicitud_objetivos` y se registra en `solicitudes_historial`. Devuelve número y confirmación de registro. El fallo del aviso a `AERIQ_COMMERCIAL_EMAIL` no revierte la solicitud guardada.

## Adaptador de consultas anteriores

`api.php?accion=health` comprueba conexión/esquema (`aeriq-unificado-v2`); `tipos` expone magnitudes. `dispositivos`, `medidas` y `ultima` necesitan una sesión con `informes.ver` y se proyectan desde el esquema único. No hay inserción anónima de compatibilidad: las escrituras usan el endpoint autenticado de campo.
