# Diseño de lógica de negocio AERIQ

## Component Design

**Responsabilidad.** Aplicar reglas de cuentas, permisos, organizaciones, campañas, dispositivos Orb, asignaciones, mediciones, informes, mapas y solicitudes. La implementación actual se concentra en `src/web/server/AeriqCore.php`, con autenticación/correo en `AuthCore.php` y persistencia SQL mediante `Logica.php`.

**Operaciones lógicas.** Las rutas de comunicación invocan servicios equivalentes a:

| Operación | Entradas lógicas | Resultado |
|---|---|---|
| `obtener_acceso` | `usuario_id: N` | `Acceso` |
| `crear_trabajador` | `Acceso, DatosTrabajador` | `ResultadoAlta` |
| `guardar_campana` | `Acceso, DatosCampana` | `Identificador` |
| `guardar_dispositivo` | `Acceso, DatosDispositivo` | `Identificador` |
| `registrar_medicion` | `IdentidadMovil, Lectura` | `ResultadoIngesta` |
| `consultar_mediciones` | `Acceso, FiltrosInforme` | `[Medicion]` |
| `consultar_mapa` | `Acceso?, FiltrosMapa` | `[MedicionPublicable]` |
| `registrar_solicitud` | `DatosSolicitud` | `Identificador` |
| `cambiar_estado_solicitud` | `Acceso, Identificador, EstadoSolicitud` | `Resultado` |

Tipos compuestos: `Acceso = (usuario_id: N, organizacion_id: N?, permisos: [Text])`; `Lectura = (event_id: Text, dispositivo_id: N, magnitud_id: N, valor: R, capturada_en_utc: Text, latitud: R?, longitud: R?, simulada: B)`; los tipos restantes se definen por los campos del contrato de `doc/api.md` y el esquema relacional.

**Reglas invariantes.** Obtener roles y organización desde el servidor; comprobar permiso y propiedad organizativa antes de leer/escribir; cerrar asignaciones anteriores conservando su historial; deduplicar por `event_id` y rechazar reintentos con contenido diferente; almacenar O₃ en ppb, temperatura en °C y tiempos en UTC; conservar GPS opcional; separar lecturas reales y simuladas; publicar únicamente campañas autorizadas; limitar la interpolación al radio configurado.

**Dependencias.** El diseño objetivo recibe repositorios y servicios de correo como interfaces, sin tipos HTTP ni acceso a transporte. La implementación existente todavía combina reglas y consultas SQL y usa `AeriqError` con estado HTTP; esa deuda de separación queda explícita para evitar describir como existente una arquitectura que no está implementada.

## Design Clarifications

- Las operaciones públicas de aplicación son las enumeradas arriba; los nombres PHP actuales pueden evolucionar sin cambiar el significado del dominio.
- Correos y base de datos son efectos externos detrás de adaptadores. Una invitación debe generar contraseña aleatoria por cuenta, persistir únicamente el hash y revertir el alta si el transporte falla antes de confirmar.
- Las respuestas de error pertenecen a la capa de comunicación; el dominio debe expresar errores semánticos sin códigos HTTP en una futura extracción.

## General Rules

- **Programming Language:** PHP 8.1+ en la implementación actual; firmas lógicas independientes del lenguaje según `Software Engineering Specification` del agente.
- La lógica de negocio no debe depender de rutas, solicitudes/respuestas HTTP, sesiones PHP ni códigos de estado HTTP.
- Separar consultas SQL detrás de repositorios y correo detrás de un servicio, manteniendo las transacciones en operaciones atómicas.
- **Function/Method Headers:** cada función o método nuevo debe incluir diseño lógico en un bloque delimitado por líneas `--------------------`.
- **Code Readability:** código claro y autoexplicativo, con comentarios para reglas no obvias.
- **Automated Testing:** generar pruebas para autorización entre organizaciones, idempotencia, límites, fechas, datos simulados/reales y transacciones.
