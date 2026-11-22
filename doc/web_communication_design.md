# Diseño de comunicación web AERIQ

## Component Design

**Responsabilidad.** Adaptar las interfaces externas a las operaciones de dominio. Los puntos de entrada PHP están en `src/web/`: `auth.php`, `mobile-auth.php`, `portal.php`, `field.php`, `public.php` y `api.php`. Las rutas, métodos, autenticación, CSRF, validación del formato de transporte y códigos HTTP se describen en [API](api.md). La capa delega reglas de negocio a `server/AeriqCore.php` y autenticación a `server/AuthCore.php`; `AeriqHttp.php` adapta errores y respuestas.

**Flujo.** La comunicación valida el método y la identidad, obtiene JSON/formulario, solicita autorización vigente al servidor, invoca la operación del dominio y serializa el resultado. No se acepta organización, rol ni permisos como autoridad cuando proceden del cliente. Las escrituras requieren sesión y CSRF o token móvil, según el contrato.

**Interfaces públicas principales.**

| Interfaz | Transporte | Autorización | Delegación |
|---|---|---|---|
| `auth.php?action=…` | HTTP JSON | sesión/CSRF o flujo de acceso | `AuthCore.php`, `FirebaseAuth.php` |
| `mobile-auth.php?action=…` | HTTP JSON | credenciales y token móvil | `AuthCore.php`, `AeriqCore.php` |
| `portal.php?action=…` | HTTP JSON | sesión, permiso y CSRF para escrituras | operaciones de organización |
| `field.php?action=…` | HTTP JSON | token móvil, usuario y asignación | ingesta, historial, batería |
| `public.php?action=…` | HTTP JSON | anónimo para mapa autorizado y solicitudes | mapa público, servicios y solicitudes |
| `api.php?accion=…` | HTTP JSON | adaptador de compatibilidad con permisos | lecturas autorizadas |

## Design Clarifications

- Las rutas son contratos actuales; sus parámetros y respuestas completos permanecen documentados en `doc/api.md`.
- `AeriqError` contiene hoy códigos HTTP y se maneja en la frontera PHP. Si se extrae el dominio a una biblioteca independiente, el adaptador debe traducir errores de dominio a esos códigos.
- El mapa público solo publica campañas autorizadas; que una medición llegue al servidor no implica que sea pública.

## General Rules

- **Programming Language:** PHP 8.1+ en servidor; HTTP/JSON como protocolo externo.
- Cada endpoint debe delegar reglas, consultas y persistencia; no duplicar lógica de autorización entre rutas.
- Los parámetros SQL se enlazan; no interpolar datos del usuario en consultas.
- Los errores no deben exponer credenciales, tokens, rutas del servidor ni consultas SQL.
- **Function/Method Headers:** cada función o método nuevo debe incluir su diseño lógico en un bloque de comentario delimitado por líneas `--------------------`.
- **Code Readability:** nombres y flujo claros; evitar comentarios que repitan el código.
- **Automated Testing:** generar pruebas unitarias/de integración para rutas críticas, autenticación, autorización, errores y contratos.
