# Diseño de base de datos AERIQ

## Component Design

La definición ejecutable es [`src/database/schema.sql`](../src/database/schema.sql); el modelo y sus decisiones están ampliados en [base de datos](base-de-datos.md). Este documento indexa las entidades por responsabilidad y remite al esquema canónico para la lista exacta de columnas, tipos, claves y restricciones, evitando mantener una segunda definición que pueda divergir.

| Grupo | Tablas | Propósito y vínculos principales |
|---|---|---|
| Autorización | `roles`, `permisos`, `rol_permisos` | Catálogo de funciones y relación muchos a muchos de permisos. |
| Medición | `magnitudes`, `modelos_dispositivo`, `modelo_magnitudes` | Unidades, rangos y magnitudes admitidas por cada modelo. |
| Comercial | `servicios`, `tipos_organizacion`, `objetivos_medicion`, `estados_solicitud` | Catálogos para formularios y seguimiento. |
| Organizaciones | `organizaciones`, `usuarios`, `identidades_externas` | Cuentas y proveedores de identidad vinculados a una organización y un rol. |
| Campañas y flota | `zonas`, `campanas`, `campana_umbrales`, `dispositivos`, `asignaciones`, `lecturas_bateria` | Inventario, publicación, campañas y asignaciones históricas. |
| Mediciones | `mediciones` | Eventos únicos vinculados a asignación y magnitud; GPS opcional y procedencia. |
| Solicitudes | `solicitudes`, `solicitud_objetivos`, `solicitudes_historial` | Solicitudes comerciales, objetivos y transiciones auditables. |
| Seguridad | `codigos_email`, `sesiones_moviles`, `limites_acceso` | Recuperación de un solo uso, sesiones móviles con token hash y límites de intentos. |
| Vistas | `vista_mediciones`, `vista_flota`, `vista_usuarios`, `vista_solicitudes` | Lectura legible para diagnóstico; no duplican almacenamiento. |

**Integridad.** Claves foráneas y compuestas mantienen la coherencia organizativa; índices únicos soportan identidad e idempotencia; las restricciones controlan rangos y estados. Los catálogos se usan en lugar de enumeraciones cerradas. Migraciones versionadas conservan datos; la recreación es explícita y respaldada.

## Relational Table Specifications


====================================================================================
TABLE: roles
DESCRIPTION: Entidad roles del modelo AERIQ.

COLUMNS:
+ id | TINYINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(30) | NOT NULL
+ nombre | VARCHAR(60) | NOT NULL
+ descripcion | VARCHAR(255) | NOT NULL | ''
+ requiere_organizacion | BOOLEAN | NOT NULL | TRUE
+ asignable | BOOLEAN | NOT NULL | FALSE

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: permisos
DESCRIPTION: Entidad permisos del modelo AERIQ.

COLUMNS:
+ id | SMALLINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(60) | NOT NULL
+ descripcion | VARCHAR(255) | NOT NULL

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: rol_permisos
DESCRIPTION: Entidad rol_permisos del modelo AERIQ.

COLUMNS:
+ rol_id | TINYINT UNSIGNED | NOT NULL
+ permiso_id | SMALLINT UNSIGNED | NOT NULL

PRIMARY KEY: rol_id, permiso_id

FOREIGN KEYS:
+ rol_id -> roles(id)
+ permiso_id -> permisos(id)

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================


====================================================================================
TABLE: magnitudes
DESCRIPTION: Entidad magnitudes del modelo AERIQ.

COLUMNS:
+ id | INT | NOT NULL
+ nombre | VARCHAR(60) | NOT NULL
+ unidad | VARCHAR(16) | NOT NULL
+ valor_minimo | DOUBLE | NOT NULL
+ valor_maximo | DOUBLE | NOT NULL
+ umbral_medio_defecto | DOUBLE | NULL
+ umbral_alto_defecto | DOUBLE | NULL

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ CHECK (valor_maximo >= valor_minimo)
+ CHECK ((umbral_medio_defecto IS NULL AND umbral_alto_defecto IS NULL) OR (umbral_medio_defecto >= 0 AND umbral_alto_defecto > umbral_medio_defecto))
====================================================================================


====================================================================================
TABLE: modelos_dispositivo
DESCRIPTION: Entidad modelos_dispositivo del modelo AERIQ.

COLUMNS:
+ id | SMALLINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(40) | NOT NULL
+ nombre | VARCHAR(120) | NOT NULL

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: modelo_magnitudes
DESCRIPTION: Entidad modelo_magnitudes del modelo AERIQ.

COLUMNS:
+ modelo_id | SMALLINT UNSIGNED | NOT NULL
+ magnitud_id | INT | NOT NULL

PRIMARY KEY: modelo_id, magnitud_id

FOREIGN KEYS:
+ modelo_id -> modelos_dispositivo(id)
+ magnitud_id -> magnitudes(id)

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================


====================================================================================
TABLE: servicios
DESCRIPTION: Entidad servicios del modelo AERIQ.

COLUMNS:
+ id | TINYINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(30) | NOT NULL
+ nombre | VARCHAR(80) | NOT NULL
+ descripcion | VARCHAR(255) | NOT NULL | ''
+ mide_superficie | BOOLEAN | NOT NULL | FALSE
+ orden | TINYINT UNSIGNED | NOT NULL | 0
+ activo | BOOLEAN | NOT NULL | TRUE

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: tipos_organizacion
DESCRIPTION: Entidad tipos_organizacion del modelo AERIQ.

COLUMNS:
+ id | TINYINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(30) | NOT NULL
+ nombre | VARCHAR(80) | NOT NULL
+ orden | TINYINT UNSIGNED | NOT NULL | 0
+ activo | BOOLEAN | NOT NULL | TRUE

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: objetivos_medicion
DESCRIPTION: Entidad objetivos_medicion del modelo AERIQ.

COLUMNS:
+ id | TINYINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(30) | NOT NULL
+ nombre | VARCHAR(80) | NOT NULL
+ orden | TINYINT UNSIGNED | NOT NULL | 0
+ activo | BOOLEAN | NOT NULL | TRUE

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: estados_solicitud
DESCRIPTION: Entidad estados_solicitud del modelo AERIQ.

COLUMNS:
+ id | TINYINT UNSIGNED | NOT NULL | Auto-Increment
+ codigo | VARCHAR(30) | NOT NULL
+ nombre | VARCHAR(60) | NOT NULL
+ orden | TINYINT UNSIGNED | NOT NULL
+ final | BOOLEAN | NOT NULL | FALSE

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE (codigo)
====================================================================================


====================================================================================
TABLE: organizaciones
DESCRIPTION: Entidad organizaciones del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ nombre | VARCHAR(160) | NOT NULL
+ creada_en | DATETIME | NOT NULL | CURRENT_TIMESTAMP

PRIMARY KEY: id

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ UNIQUE KEY organizacion_nombre (nombre)
====================================================================================


====================================================================================
TABLE: usuarios
DESCRIPTION: Entidad usuarios del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ organizacion_id | BIGINT UNSIGNED | NULL
+ rol_id | TINYINT UNSIGNED | NOT NULL | 1
+ nombre | VARCHAR(90) | NOT NULL
+ apellidos | VARCHAR(120) | NOT NULL
+ email | VARCHAR(254) | NOT NULL
+ telefono | VARCHAR(40) | NOT NULL | ''
+ password_hash | VARCHAR(255) | NOT NULL
+ activo | BOOLEAN | NOT NULL | TRUE
+ debe_cambiar_clave | BOOLEAN | NOT NULL | FALSE
+ invitacion_expira_en | DATETIME | NULL
+ verificado_en | DATETIME | NULL
+ creada_en | DATETIME | NOT NULL | CURRENT_TIMESTAMP

PRIMARY KEY: id

FOREIGN KEYS:
+ organizacion_id -> organizaciones(id)
+ rol_id -> roles(id)

CONSTRAINTS:
+ UNIQUE (email)
+ UNIQUE KEY usuario_organizacion (id,organizacion_id)
====================================================================================


====================================================================================
TABLE: identidades_externas
DESCRIPTION: Entidad identidades_externas del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ usuario_id | BIGINT UNSIGNED | NOT NULL
+ proveedor | VARCHAR(30) | NOT NULL
+ uid_externo | VARCHAR(128) | NOT NULL
+ vinculada_en | DATETIME | NOT NULL | CURRENT_TIMESTAMP

PRIMARY KEY: id

FOREIGN KEYS:
+ usuario_id -> usuarios(id)

CONSTRAINTS:
+ UNIQUE KEY identidad_unica (proveedor,uid_externo)
+ UNIQUE KEY una_por_proveedor (usuario_id,proveedor)
====================================================================================


====================================================================================
TABLE: zonas
DESCRIPTION: Entidad zonas del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ organizacion_id | BIGINT UNSIGNED | NOT NULL
+ nombre | VARCHAR(200) | NOT NULL

PRIMARY KEY: id

FOREIGN KEYS:
+ organizacion_id -> organizaciones(id)

CONSTRAINTS:
+ UNIQUE KEY zona_nombre (organizacion_id,nombre)
+ UNIQUE KEY zona_organizacion (id,organizacion_id)
====================================================================================


====================================================================================
TABLE: campanas
DESCRIPTION: Entidad campanas del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ organizacion_id | BIGINT UNSIGNED | NOT NULL
+ zona_id | BIGINT UNSIGNED | NOT NULL
+ nombre | VARCHAR(160) | NOT NULL
+ inicio | DATE | NOT NULL
+ fin | DATE | NOT NULL
+ activa | BOOLEAN | NOT NULL | TRUE
+ publicar | BOOLEAN | NOT NULL | FALSE
+ radio_m | INT | NOT NULL | 30

PRIMARY KEY: id

FOREIGN KEYS:
+ organizacion_id -> organizaciones(id)
+ zona_id,organizacion_id -> zonas(id,organizacion_id)

CONSTRAINTS:
+ UNIQUE KEY campana_organizacion (id,organizacion_id)
+ CHECK (fin >= inicio)
+ CHECK (radio_m BETWEEN 20 AND 50)
====================================================================================


====================================================================================
TABLE: campana_umbrales
DESCRIPTION: Entidad campana_umbrales del modelo AERIQ.

COLUMNS:
+ campana_id | BIGINT UNSIGNED | NOT NULL
+ magnitud_id | INT | NOT NULL
+ umbral_medio | DOUBLE | NOT NULL
+ umbral_alto | DOUBLE | NOT NULL

PRIMARY KEY: campana_id, magnitud_id

FOREIGN KEYS:
+ campana_id -> campanas(id)
+ magnitud_id -> magnitudes(id)

CONSTRAINTS:
+ CHECK (umbral_medio >= 0 AND umbral_alto > umbral_medio)
====================================================================================


====================================================================================
TABLE: dispositivos
DESCRIPTION: Entidad dispositivos del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ organizacion_id | BIGINT UNSIGNED | NOT NULL
+ modelo_id | SMALLINT UNSIGNED | NOT NULL | 1
+ nombre | VARCHAR(160) | NOT NULL
+ direccion_ble | VARCHAR(17) | NULL
+ activo | BOOLEAN | NOT NULL | TRUE
+ simulado | BOOLEAN | NOT NULL | FALSE

PRIMARY KEY: id

FOREIGN KEYS:
+ organizacion_id -> organizaciones(id)
+ modelo_id -> modelos_dispositivo(id)

CONSTRAINTS:
+ UNIQUE (direccion_ble)
+ UNIQUE KEY dispositivo_organizacion (id,organizacion_id)
====================================================================================


====================================================================================
TABLE: asignaciones
DESCRIPTION: Entidad asignaciones del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ organizacion_id | BIGINT UNSIGNED | NOT NULL
+ dispositivo_id | BIGINT UNSIGNED | NOT NULL
+ trabajador_id | BIGINT UNSIGNED | NOT NULL
+ campana_id | BIGINT UNSIGNED | NOT NULL
+ iniciada_en | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3)
+ finalizada_en | DATETIME(3) | NULL
+ dispositivo_activo | BIGINT UNSIGNED | NULL

PRIMARY KEY: id

FOREIGN KEYS:
+ dispositivo_id,organizacion_id -> dispositivos(id,organizacion_id)
+ trabajador_id,organizacion_id -> usuarios(id,organizacion_id)
+ campana_id,organizacion_id -> campanas(id,organizacion_id)

CONSTRAINTS:
+ UNIQUE KEY un_destino_actual (dispositivo_activo)
+ CHECK (finalizada_en IS NULL OR finalizada_en >= iniciada_en)
====================================================================================


====================================================================================
TABLE: lecturas_bateria
DESCRIPTION: Entidad lecturas_bateria del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ dispositivo_id | BIGINT UNSIGNED | NOT NULL
+ porcentaje | TINYINT UNSIGNED | NOT NULL
+ capturada_en | DATETIME(3) | NOT NULL
+ recibida_en | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3)
+ usuario_id | BIGINT UNSIGNED | NULL

PRIMARY KEY: id

FOREIGN KEYS:
+ dispositivo_id -> dispositivos(id)
+ usuario_id -> usuarios(id)

CONSTRAINTS:
+ CHECK (porcentaje <= 100)
====================================================================================


====================================================================================
TABLE: mediciones
DESCRIPTION: Entidad mediciones del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ evento | CHAR(36) | NOT NULL
+ asignacion_id | BIGINT UNSIGNED | NOT NULL
+ magnitud_id | INT | NOT NULL
+ valor | DOUBLE | NOT NULL
+ capturada_en | DATETIME(3) | NOT NULL
+ recibida_en | DATETIME(3) | NOT NULL | CURRENT_TIMESTAMP(3)
+ latitud | DOUBLE | NULL
+ longitud | DOUBLE | NULL
+ precision_m | DOUBLE | NULL
+ contador | TINYINT UNSIGNED | NULL
+ rssi | TINYINT | NULL
+ simulado | BOOLEAN | NOT NULL
+ valida | BOOLEAN | NOT NULL | TRUE

PRIMARY KEY: id

FOREIGN KEYS:
+ asignacion_id -> asignaciones(id)
+ magnitud_id -> magnitudes(id)

CONSTRAINTS:
+ UNIQUE KEY evento_unico (evento)
+ CHECK ((latitud IS NULL AND longitud IS NULL AND precision_m IS NULL) OR (latitud IS NOT NULL AND longitud IS NOT NULL AND precision_m IS NOT NULL AND latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180 AND precision_m BETWEEN 0 AND 10000))
+ CHECK (rssi BETWEEN -127 AND 20)
====================================================================================


====================================================================================
TABLE: solicitudes
DESCRIPTION: Entidad solicitudes del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ servicio_id | TINYINT UNSIGNED | NOT NULL
+ tipo_organizacion_id | TINYINT UNSIGNED | NULL
+ estado_id | TINYINT UNSIGNED | NOT NULL | 1
+ entidad | VARCHAR(180) | NOT NULL
+ contacto | VARCHAR(160) | NOT NULL
+ email | VARCHAR(254) | NOT NULL
+ telefono | VARCHAR(40) | NOT NULL | ''
+ municipio | VARCHAR(120) | NOT NULL | ''
+ provincia | VARCHAR(60) | NOT NULL | ''
+ zona | VARCHAR(200) | NOT NULL
+ superficie | DOUBLE | NULL
+ duracion | VARCHAR(100) | NOT NULL | ''
+ inicio_deseado | DATE | NULL
+ necesidades | TEXT | NOT NULL
+ consentimiento_en | DATETIME | NULL | CURRENT_TIMESTAMP
+ recibida_en | DATETIME | NOT NULL | CURRENT_TIMESTAMP

PRIMARY KEY: id

FOREIGN KEYS:
+ servicio_id -> servicios(id)
+ tipo_organizacion_id -> tipos_organizacion(id)
+ estado_id -> estados_solicitud(id)

CONSTRAINTS:
+ CHECK (superficie IS NULL OR superficie > 0)
====================================================================================


====================================================================================
TABLE: solicitud_objetivos
DESCRIPTION: Entidad solicitud_objetivos del modelo AERIQ.

COLUMNS:
+ solicitud_id | BIGINT UNSIGNED | NOT NULL
+ objetivo_id | TINYINT UNSIGNED | NOT NULL

PRIMARY KEY: solicitud_id, objetivo_id

FOREIGN KEYS:
+ solicitud_id -> solicitudes(id)
+ objetivo_id -> objetivos_medicion(id)

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================


====================================================================================
TABLE: solicitudes_historial
DESCRIPTION: Entidad solicitudes_historial del modelo AERIQ.

COLUMNS:
+ id | BIGINT UNSIGNED | NOT NULL | Auto-Increment
+ solicitud_id | BIGINT UNSIGNED | NOT NULL
+ estado_id | TINYINT UNSIGNED | NOT NULL
+ usuario_id | BIGINT UNSIGNED | NULL
+ cambiado_en | DATETIME | NOT NULL | CURRENT_TIMESTAMP

PRIMARY KEY: id

FOREIGN KEYS:
+ solicitud_id -> solicitudes(id)
+ estado_id -> estados_solicitud(id)
+ usuario_id -> usuarios(id)

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================


====================================================================================
TABLE: codigos_email
DESCRIPTION: Entidad codigos_email del modelo AERIQ.

COLUMNS:
+ usuario_id | BIGINT UNSIGNED | NOT NULL
+ proposito | ENUM('registro','recuperar') | NOT NULL
+ codigo_hash | VARCHAR(255) | NOT NULL
+ vence_en | DATETIME | NOT NULL
+ enviado_en | DATETIME | NOT NULL
+ intentos | INT | NOT NULL | 0

PRIMARY KEY: usuario_id, proposito

FOREIGN KEYS:
+ usuario_id -> usuarios(id)

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================


====================================================================================
TABLE: sesiones_moviles
DESCRIPTION: Entidad sesiones_moviles del modelo AERIQ.

COLUMNS:
+ token_hash | CHAR(64) | NOT NULL
+ usuario_id | BIGINT UNSIGNED | NOT NULL
+ vence_en | DATETIME | NOT NULL

PRIMARY KEY: token_hash

FOREIGN KEYS:
+ usuario_id -> usuarios(id)

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================


====================================================================================
TABLE: limites_acceso
DESCRIPTION: Entidad limites_acceso del modelo AERIQ.

COLUMNS:
+ clave_hash | CHAR(64) | NOT NULL
+ ventana_en | DATETIME | NOT NULL
+ intentos | INT | NOT NULL

PRIMARY KEY: clave_hash

FOREIGN KEYS:
+ Ninguna

CONSTRAINTS:
+ Unicidad e índices según schema.sql.
====================================================================================

## Design Clarifications

- `schema.sql` es la especificación física completa y única. No editar esta tabla de resumen como sustituto del esquema.
- La base usa MariaDB y las fechas operativas se almacenan en UTC. Ozono se expresa en ppb y temperatura en °C.
- La interpolación y los informes se calculan sobre datos autorizados; la publicación pública se controla por campaña.

## General Rules

- **Programming Language:** SQL compatible con MariaDB (DDL y consultas parametrizadas desde PHP 8.1+).
- Toda modificación de esquema requiere migración repetible/documentada, compatibilidad y estrategia de respaldo antes de despliegue.
- Especificar PK, FK, nulabilidad, valor por defecto, índices y restricciones en el esquema canónico.
- **Function/Method Headers:** aplica a rutinas almacenadas nuevas; comentar el diseño lógico en bloque delimitado por líneas `--------------------`.
- **Code Readability:** nombres de tablas/columnas claros y coherentes en español.
- **Automated Testing:** cubrir integridad referencial, restricciones, permisos por organización, migraciones e idempotencia en una base aislada.


