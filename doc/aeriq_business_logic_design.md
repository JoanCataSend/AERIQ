# Diseño de lógica de negocio AERIQ

## Component Design

**Responsabilidad objetivo.** Aplicar reglas de cuentas, permisos, organizaciones, campañas, dispositivos, asignaciones, mediciones, informes, mapas, solicitudes y avisos. La lógica no depende de HTTP, sesiones, códigos de estado ni SMTP. Los errores se expresan mediante categorías semánticas; `src/web/communication/HttpErrorMapper.php` las traduce a estados HTTP.

**Estado de la implementación.** El contrato de dominio está separado de HTTP para los errores. `AeriqCore.php` contiene casos de uso y reglas; la persistencia de entidades pasa por `AeriqRepository.php` y `AuthRepository.php`, que consultan `AeriqQueryCatalog.php` y delegan la ejecución parametrizada/transacciones en `SqlGateway.php`. El envío está en `communication/MailTransport.php`. `DomainError.php` expresa errores semánticos sin estado HTTP.

### Tipos lógicos

```text
Acceso = (usuario_id: N, organizacion_id: N?, campana_id: N?, rol: Text, permisos: [Text])
Procedencia = { real, simulada }
TipoMagnitud = { ozono, temperatura, todas }
AmbitoMapa = { publico, organizacion, propio }
FiltrosPanel = (type: {12, 14, all}, campaign: Text?, devices: Text?, zone: Text?, from: Text?, to: Text?, export: B)
Lectura = (id: N, evento: Text, asignacion_id: N, magnitud_id: N, valor: R, capturada_en: Text, latitud: R?, longitud: R?, precision_m: R?, simulado: B, valida: B, motivo_descarte: Text?)
LecturaInforme = (id: N, evento: Text, dispositivo_id: N, campana_id: N, tipo: N, valor: R, unidad: Text, capturada_en: Text, latitud: R?, longitud: R?, simulado: B, dispositivo: Text, campana: Text, zona: Text, umbral_medio: R?, umbral_alto: R?)
LecturaDescartada = (id: N, tipo: N, valor: R, unidad: Text, valor_minimo: R, valor_maximo: R, salto_maximo: R, capturada_en: Text, simulado: B, motivo_descarte: Text, dispositivo: Text, campana: Text)
MedicionMapa = (id: N, valor: R, unidad: Text, capturada_en: Text, latitud: R, longitud: R, simulado: B, zona: Text, campaignId: N, radio_m: N, umbral_medio: R?, umbral_alto: R?, dispositivo_id: N?, dispositivo: Text?)
ResultadoMapa = (measurements: [MedicionMapa], mode: Text, hours: N, truncated: B)
DatosSolicitud = (interest: Text, orgType: Text, entity: Text, contact: Text, email: Text, phone: Text?, municipality: Text, province: Text, zone: Text, objectives: [Text], needs: Text, duration: Text?, surface: R?, startMonth: Text?, consent: B, website: Text?)
ConfirmacionSolicitud = (ok: B, id: N, mailed: B, message: Text)
Servicio = (codigo: Text, nombre: Text, descripcion: Text, mide_superficie: B)
TipoOrganizacion = (codigo: Text, nombre: Text)
ObjetivoMedicion = (codigo: Text, nombre: Text)
CatalogosSolicitud = (services: [Servicio], orgTypes: [TipoOrganizacion], objectives: [ObjetivoMedicion], provinces: [Text], durations: [Text])
AccionPanel = { resumen, lecturas, solicitudes }
Trabajador = (id: N, nombre: Text, apellidos: Text, email: Text, telefono: Text, campana_id: N?, rol: Text, rol_nombre: Text, puede_medir: B, activo: B)
Campana = (id: N, nombre: Text, zona: Text, inicio: Text, fin: Text, activa: B, publicar: B, radio_m: N)
Dispositivo = (id: N, nombre: Text, modelo: Text, activo: B, simulado: B)
Zona = (id: N, nombre: Text)
Rol = (codigo: Text, nombre: Text, descripcion: Text)
EstadoSolicitud = (codigo: Text, nombre: Text, orden: N)
Solicitud = (id: N, contacto: Text, email: Text, servicio: Text, estado: Text, recibida_en: Text)
Umbrales = (umbral_medio: R, umbral_alto: R)
ResumenPanel = (workers: [Trabajador], campaigns: [Campana], devices: [Dispositivo], zones: [Zona], roles: [Rol], thresholds: Umbrales)
InformePanel = (readings: [LecturaInforme], discarded: [LecturaDescartada])
SolicitudesPanel = (requests: [Solicitud], states: [EstadoSolicitud])
ResultadoPanel = ResumenPanel | InformePanel | SolicitudesPanel
```

`ResultadoPanel` es un tipo suma: cada variante devuelve la estructura indicada para la acción elegida. Los códigos de magnitud del contrato (`14`, `12`, `all`) corresponden a ozono, temperatura y todas. Las rutas actuales devuelven estructuras JSON concretas descritas en `doc/api.md`. Los campos opcionales se marcan con `?`. Las fechas son texto ISO-8601/UTC salvo filtros de calendario `YYYY-MM-DD`.

### Operaciones de dominio compartidas con el proxy

```text
accion: AccionPanel, filtros: FiltrosPanel --> consultar_panel() --> resultado: ResultadoPanel
ambito: AmbitoMapa, procedencia: Procedencia, horas: N --> consultar_mapa() --> resultado: ResultadoMapa
solicitud: DatosSolicitud --> enviar_solicitud() --> confirmacion: ConfirmacionSolicitud
--> consultar_servicios() --> catalogos: CatalogosSolicitud
```

El contexto `Acceso` se obtiene en servidor desde la sesión/token y se inyecta en las operaciones protegidas; el cliente no lo envía ni lo usa como autoridad. `consultar_panel` vuelve a comprobar rol, organización y campaña en servidor. `consultar_mapa` aplica publicación autorizada y límites de periodo según el ámbito. `enviar_solicitud` valida el catálogo, persiste solicitud, objetivos e historial, y devuelve una confirmación aunque falle el correo posterior. `LecturaInforme.evento`, `tipo`, `dispositivo_id` y `campana_id` corresponden a `mediciones.evento`, `mediciones.magnitud_id`, `asignaciones.dispositivo_id` y `asignaciones.campana_id`, respectivamente; los nombres expuestos los fija la consulta de `aeriq_readings()`.

### Correspondencia con persistencia

| Operación / regla | Tablas y columnas principales |
|---|---|
| Obtener acceso y permisos | `usuarios(id, organizacion_id, campana_id, rol_id, activo, debe_cambiar_clave)`, `roles(id, codigo, requiere_organizacion)`, `rol_permisos(rol_id, permiso_id)`, `permisos(id, codigo)` |
| Catálogo y adaptador PBIO | `organizaciones(id)`, `magnitudes(id, nombre, unidad)`, `dispositivos(id, organizacion_id, nombre)` |
| Campañas y umbrales | `campanas(id, organizacion_id, zona_id, inicio, fin, activa, publicar, radio_m)`, `campana_umbrales(campana_id, magnitud_id, umbral_medio, umbral_alto)`, `zonas(id, organizacion_id, nombre)` |
| Dispositivos y asignaciones | `dispositivos(id, organizacion_id, modelo_id, direccion_ble, activo, simulado)`, `asignaciones(id, organizacion_id, dispositivo_id, trabajador_id, campana_id, iniciada_en, finalizada_en)` |
| Ingesta e informes | `mediciones(id, evento, asignacion_id, magnitud_id, valor, capturada_en, latitud, longitud, simulado, valida, motivo_descarte)`, `magnitudes(id, unidad, valor_minimo, valor_maximo)`, `modelo_magnitudes(modelo_id, magnitud_id)`, `lecturas_bateria(dispositivo_id, porcentaje, capturada_en)` |
| Solicitudes comerciales | `solicitudes(id, servicio_id, estado_id, entidad, contacto, email, zona, superficie, duracion, inicio_deseado, necesidades, recibida_en)`, `solicitud_objetivos(solicitud_id, objetivo_id)`, `solicitudes_historial(solicitud_id, estado_id, usuario_id, cambiado_en)`, catálogos `servicios`, `tipos_organizacion`, `objetivos_medicion`, `estados_solicitud` |
| Avisos | `avisos(id, organizacion_id, creado_por, destinatario_id, estado_id, ...)`, `aviso_mensajes(aviso_id, autor_id, texto, creado_en)`, `estados_aviso(id, codigo)` |

La definición exhaustiva de campos y restricciones está en `doc/database_design.md` y el esquema ejecutable es `src/database/1-instalar.sql`.

## Design Clarifications

- El proxy web comparte las cuatro firmas lógicas anteriores. El login, la sesión y el perfil son funciones de autenticación (`AeriqAuth`) y no se presentan como operaciones de este componente.
- El mapeo de cada acción HTTP y la forma concreta de JSON están en `doc/web_communication_design.md` y `doc/api.md`.
- Los repositorios son la única capa que ejecuta las operaciones SQL; el servicio de negocio selecciona la operación y aplica validaciones/reglas antes y después de persistir.

## General Rules

- **Programming Language:** PHP 8.1+; firmas lógicas independientes del lenguaje conforme a `Software_Engineering_Spec.md`.
- La lógica de negocio no depende de rutas, solicitudes/respuestas HTTP, sesiones PHP ni códigos de estado HTTP.
- El acceso SQL se parametriza; las operaciones que cambian varias entidades se ejecutan en una transacción.
- Los correos y la persistencia se invocan mediante adaptadores inyectados al completar la separación pendiente.
- **Function/Method Headers:** cada función o método nuevo debe incluir diseño lógico en un bloque delimitado por líneas `--------------------`.
- **Code Readability:** código claro y autoexplicativo, con comentarios para reglas no obvias.
- **Automated Testing:** generar pruebas para autorización entre organizaciones, idempotencia, límites, fechas, procedencia de datos y transacciones.
