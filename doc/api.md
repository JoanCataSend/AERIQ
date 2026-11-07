# Contratos API AERIQ

Las rutas se publican en `https://ldiamur.upv.edu.es/src/web/` (por ejemplo, `auth.php` y `mobile-auth.php` están en `httpdocs/src/web/`). El esquema completo está en [schema.sql](../src/database/schema.sql); no depende de tablas antiguas. Fechas UTC, JSON y HTTPS para sesiones/operaciones privadas.

## Cuentas

Web: `auth.php?action=session` devuelve `csrf,user`. Los POST `login,google,logout,forgot,reset-password,profile,change-password` mantienen el contrato existente, cookie segura y `X-CSRF-Token`. No hay autoregistro: las cuentas las crea un administrador desde el portal de la organización. `auth.php?action=firebase-config` devuelve la configuración pública de Firebase y si el botón de Google está listo.

Móvil: `mobile-auth.php` reutiliza acciones de cuenta; las protegidas utilizan `X-AERIQ-Token`. Login y recuperación pueden devolver `token,user`; el cambio de contraseña rota el token. Los tokens se guardan como hash en `sesiones_moviles`. `google` recibe `idToken` de Firebase. `social-login` ya no acepta correos sin comprobar: responde `410` hasta que Android envíe un token real. No existe `register` móvil: las cuentas las crea la organización.

La acción `google` verifica la firma RS256 del ID token con los certificados públicos de Google, comprueba emisor, proyecto, caducidad y proveedor `google.com`, y busca `usuarios.firebase_uid`. En el primer acceso exige que el correo verificado coincida con una cuenta creada por el administrador; nunca registra correos nuevos. Si la invitación sigue vigente, la activa y anula la contraseña temporal. Si la cuenta está desactivada o invitación caducada, rechaza el acceso.

`user` añade función, organización y `mustChangePassword`. Una cuenta invitada está inactiva y solo puede sustituir su contraseña antes de la caducidad. La contraseña temporal vence a las 24 h por defecto; `AERIQ_INVITATION_HOURS` permite 1–168 h. Recuperar por correo permite activar un acceso caducado. No se permite operar antes de activar la cuenta. La misma invitación se puede aceptar con Google usando exactamente el correo registrado por el administrador.

## Organización: `portal.php?action=…`

| Acción | Método / función | Datos |
| --- | --- | --- |
| `dashboard` | GET admin/analista | Personas, campañas y flota propias |
| `worker` | POST admin | `name,last,email,role` (trabajador/analista) |
| `campaign` | POST admin | `id?`, `name,zone,start,end,active,public,radius,medium,high` |
| `device` | POST admin | `id?`, `name,address,worker,campaign,active,simulated` |
| `readings` | GET admin/analista | `campaign,devices` (IDs con comas), `zone,from,to` |
| `map` | GET admin/analista/trabajador | Mismo formato de mapa público, dentro del ámbito privado autorizado |
| `requests` | GET comercial | Solicitudes recibidas |
| `request-status` | POST comercial | `id,status` (pendiente/en revision/respondida/cerrada) |

Todo POST del panel requiere CSRF. El rol y la organización se consultan en servidor. Las asignaciones preservan su histórico; dar de baja no borra mediciones. Fechas de campaña `YYYY-MM-DD`, radio 20–50 m y umbrales de ozono en ppb. Un informe de más de 10.000 filas devuelve 413 y pide acotar el filtro.

## Campo Android: `field.php`

GET devuelve `assignments` vigentes. GET `?action=history` devuelve hasta 1.000 lecturas válidas de las asignaciones actuales y anteriores del trabajador. Se valida token, activación y función.

POST incluye:

```json
{"eventId":"014be610-334a-49cb-8f78-345af82a2551","deviceId":1,"assignmentId":1,"campaignId":1,"address":"D0:00:00:00:00:01","type":14,"unit":"ppb","value":68,"capturedAt":"2026-11-03T12:00:00.000Z","counter":7,"rssi":-60,"latitude":38.99665,"longitude":-0.166,"accuracy":5}
```

GPS es opcional como conjunto. El servidor consulta el rango/unidad de `magnitudes`, comprueba asignación y fechas, y toma la procedencia del Orb. `assignmentId` y `unit` se comprueban si se incluyen; la app actual siempre los envía. No se permite que un cliente elija otra organización o declare su lectura como real.

El UUID de evento se genera una sola vez y se reutiliza al reintentar. Respuesta: `ok,id,eventId,duplicate`. Reutilizar el evento con datos diferentes devuelve 409; cambiar de asignación no reasigna lecturas históricas.

## Público: `public.php`

GET `?action=map&mode=real|simulated&hours=24|168|0` devuelve `measurements,mode,hours,truncated`; `0` significa histórico completo dentro del límite. Solo incluye ozono válido con coordenadas y publicación de campaña autorizada. Máximo 2.000 lecturas recientes del filtro, sin identidad del trabajador.

POST `?action=request`: `service` (administracion/empresa/estudio), `entity,contact,email,phone,zone,needs,consent`. Kits requieren `quantity`; estudios `surface,duration`. Devuelve número y confirmación de registro. El fallo del aviso a `AERIQ_COMMERCIAL_EMAIL` no revierte la solicitud guardada.

## Adaptador de consultas anteriores

`api.php?accion=health` comprueba conexión/esquema; `tipos` expone magnitudes. `dispositivos`, `medidas` y `ultima` necesitan sesión admin/analista y se proyectan desde el esquema único. No hay inserción anónima de compatibilidad: las escrituras usan el endpoint autenticado de campo.
