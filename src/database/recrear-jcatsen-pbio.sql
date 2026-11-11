-- AERIQ: reconstruccion especifica de jcatsen_pbio para phpMyAdmin.
-- BORRA todas las tablas y vistas de jcatsen_pbio y TODOS sus datos.
-- Exporta una copia SQL antes de importar. El borrado no admite ROLLBACK.
-- Incluye esquema y ejemplos; no importar despues los otros dos SQL.
-- Para conservar los datos actuales usa migracion-v2-catalogos.sql en su lugar.
-- Generado a partir de schema.sql y seed-demo.sql; no editar a mano.
USE `jcatsen_pbio`;
SET NAMES utf8mb4;
SET @aeriq_old_fk = @@FOREIGN_KEY_CHECKS;
SET @aeriq_old_concat = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 16777216;
SET FOREIGN_KEY_CHECKS = 0;

-- Primero todas las vistas, incluidas las de versiones anteriores.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_SCHEMA,'`','``'), '`.`',
 REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'jcatsen_pbio' AND TABLE_TYPE = 'VIEW';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP VIEW IF EXISTS ', @aeriq_objects));
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;

-- Despues todas las tablas. Sus triggers se eliminan junto con ellas.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_SCHEMA,'`','``'), '`.`',
 REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'jcatsen_pbio' AND TABLE_TYPE = 'BASE TABLE';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP TABLE IF EXISTS ', @aeriq_objects));
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;
SET FOREIGN_KEY_CHECKS = @aeriq_old_fk;
SET SESSION group_concat_max_len = @aeriq_old_concat;

USE `jcatsen_pbio`;
-- AERIQ · Esquema único para instalaciones nuevas (MariaDB 10.4+).
-- No contiene DROP DATABASE, cuentas reales ni datos de producción.
-- Las decisiones de diseño están explicadas en doc/base-de-datos.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================ Catálogos
-- Tablas pequeñas que sustituyen a los ENUM: añadir un rol, un permiso,
-- un servicio o un estado es un INSERT, no un ALTER TABLE.

CREATE TABLE `jcatsen_pbio`.`roles` (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 descripcion VARCHAR(255) NOT NULL DEFAULT '',
 requiere_organizacion BOOLEAN NOT NULL DEFAULT TRUE,
 asignable BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`roles` (id,codigo,nombre,descripcion,requiere_organizacion,asignable) VALUES
 (1,'visitante','Visitante','Cuenta sin organización. Solo consulta pública.',0,0),
 (2,'trabajador','Trabajador','Recoge mediciones con los Orb que tiene asignados.',1,1),
 (3,'analista','Analista','Consulta informes y exporta los datos de su organización.',1,1),
 (4,'admin','Administrador','Gestiona cuentas, campañas y Orb de su organización.',1,0),
 (5,'comercial','Comercial','Atiende las solicitudes de presupuesto de Código Norte.',0,0);

CREATE TABLE `jcatsen_pbio`.`permisos` (
 id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(60) NOT NULL UNIQUE,
 descripcion VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`permisos` (id,codigo,descripcion) VALUES
 (1,'mapa.organizacion','Ver el mapa privado de su organización.'),
 (2,'informes.ver','Ver el panel, los informes y exportar los datos de toda la organización.'),
 (3,'organizacion.gestionar','Dar de alta cuentas, campañas y Orb de su organización.'),
 (4,'mediciones.registrar','Recibir asignaciones, vincular Orb propios y enviar lecturas desde Android.'),
 (5,'solicitudes.gestionar','Ver las solicitudes comerciales y cambiar su estado.');

CREATE TABLE `jcatsen_pbio`.`rol_permisos` (
 rol_id TINYINT UNSIGNED NOT NULL,
 permiso_id SMALLINT UNSIGNED NOT NULL,
 PRIMARY KEY (rol_id,permiso_id),
 FOREIGN KEY (rol_id) REFERENCES `jcatsen_pbio`.`roles`(id) ON DELETE CASCADE,
 FOREIGN KEY (permiso_id) REFERENCES `jcatsen_pbio`.`permisos`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`rol_permisos` (rol_id,permiso_id) VALUES
 (2,1),(2,4),
 (3,1),(3,2),
 (4,1),(4,2),(4,3),(4,4),
 (5,5);

-- El id coincide con el código de tipo del anuncio BLE del Orb (14 = O3, 12 = temperatura):
-- es una clave natural fijada por el firmware, por eso no es AUTO_INCREMENT.
CREATE TABLE `jcatsen_pbio`.`magnitudes` (
 id INT PRIMARY KEY, nombre VARCHAR(60) NOT NULL, unidad VARCHAR(16) NOT NULL,
 valor_minimo DOUBLE NOT NULL, valor_maximo DOUBLE NOT NULL,
 umbral_medio_defecto DOUBLE NULL, umbral_alto_defecto DOUBLE NULL,
 CHECK (valor_maximo >= valor_minimo),
 CHECK ((umbral_medio_defecto IS NULL AND umbral_alto_defecto IS NULL) OR
        (umbral_medio_defecto >= 0 AND umbral_alto_defecto > umbral_medio_defecto))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`magnitudes` VALUES
 (14,'Ozono','ppb',0,65535,60,100),
 (12,'Temperatura','°C',-32768,32767,NULL,NULL);

CREATE TABLE `jcatsen_pbio`.`modelos_dispositivo` (
 id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(40) NOT NULL UNIQUE,
 nombre VARCHAR(120) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`modelos_dispositivo` (id,codigo,nombre) VALUES
 (1,'orb-v1','Orb v1 · nRF52840 (ozono y temperatura)');

-- Qué magnitudes puede enviar cada modelo; la ingesta lo comprueba.
CREATE TABLE `jcatsen_pbio`.`modelo_magnitudes` (
 modelo_id SMALLINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL,
 PRIMARY KEY (modelo_id,magnitud_id),
 FOREIGN KEY (modelo_id) REFERENCES `jcatsen_pbio`.`modelos_dispositivo`(id) ON DELETE CASCADE,
 FOREIGN KEY (magnitud_id) REFERENCES `jcatsen_pbio`.`magnitudes`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`modelo_magnitudes` (modelo_id,magnitud_id) VALUES (1,12),(1,14);

CREATE TABLE `jcatsen_pbio`.`servicios` (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 descripcion VARCHAR(255) NOT NULL DEFAULT '',
 mide_superficie BOOLEAN NOT NULL DEFAULT FALSE,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Qué le interesa al cliente. El número de Orb lo recomienda Código Norte.
INSERT INTO `jcatsen_pbio`.`servicios` (id,codigo,nombre,descripcion,mide_superficie,orden) VALUES
 (1,'kits','Comprar Orb para mi organización','Sensores propios con app y panel. Te recomendamos cuántos necesitas.',0,1),
 (2,'estudio','Contratar un estudio de ozono','Medimos nosotros la zona durante un tiempo, sin comprar sensores, y entregamos un informe.',1,2),
 (3,'asesoramiento','No lo tengo claro, aconsejadme','Cuéntanos tu caso y te proponemos la opción que mejor encaja.',0,3);

-- Quién hace la solicitud, independiente de lo que pide.
CREATE TABLE `jcatsen_pbio`.`tipos_organizacion` (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`tipos_organizacion` (id,codigo,nombre,orden) VALUES
 (1,'administracion','Administración pública',1),
 (2,'empresa','Empresa',2),
 (3,'otra','Otra entidad (centro educativo, asociación…)',3);

-- Para qué quiere medir el ozono. Ayuda a recomendar la solución.
CREATE TABLE `jcatsen_pbio`.`objetivos_medicion` (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`objetivos_medicion` (id,codigo,nombre,orden) VALUES
 (1,'espacios_publicos','Calidad del aire en espacios públicos',1),
 (2,'salud_laboral','Salud y seguridad laboral',2),
 (3,'normativa','Informe técnico o normativa',3),
 (4,'investigacion','Investigación o divulgación',4),
 (5,'otro','Otro',5);

CREATE TABLE `jcatsen_pbio`.`estados_solicitud` (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL,
 final BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO `jcatsen_pbio`.`estados_solicitud` (id,codigo,nombre,orden,final) VALUES
 (1,'pendiente','Pendiente',1,0),
 (2,'en_revision','En revisión',2,0),
 (3,'respondida','Respondida',3,0),
 (4,'cerrada','Cerrada',4,1);

-- ============================================================ Organizaciones y cuentas

CREATE TABLE `jcatsen_pbio`.`organizaciones` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(160) NOT NULL,
 creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY organizacion_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Una cuenta pertenece como máximo a una organización y tiene un rol.
-- Que los roles con requiere_organizacion tengan organización lo comprueba la aplicación.
CREATE TABLE `jcatsen_pbio`.`usuarios` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NULL,
 rol_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
 nombre VARCHAR(90) NOT NULL, apellidos VARCHAR(120) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE, telefono VARCHAR(40) NOT NULL DEFAULT '',
 password_hash VARCHAR(255) NOT NULL,
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE,
 invitacion_expira_en DATETIME NULL,
 verificado_en DATETIME NULL,
 creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY usuario_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES `jcatsen_pbio`.`organizaciones`(id),
 FOREIGN KEY (rol_id) REFERENCES `jcatsen_pbio`.`roles`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Accesos con proveedores externos (hoy Firebase/Google). Una fila por proveedor y cuenta.
CREATE TABLE `jcatsen_pbio`.`identidades_externas` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 usuario_id BIGINT UNSIGNED NOT NULL,
 proveedor VARCHAR(30) NOT NULL,
 uid_externo VARCHAR(128) NOT NULL,
 vinculada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY identidad_unica (proveedor,uid_externo),
 UNIQUE KEY una_por_proveedor (usuario_id,proveedor),
 FOREIGN KEY (usuario_id) REFERENCES `jcatsen_pbio`.`usuarios`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Campañas y flota

-- Zonas reutilizables por organización: los informes filtran por zona sin depender de cómo se escribió.
CREATE TABLE `jcatsen_pbio`.`zonas` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(200) NOT NULL,
 UNIQUE KEY zona_nombre (organizacion_id,nombre),
 UNIQUE KEY zona_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES `jcatsen_pbio`.`organizaciones`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `jcatsen_pbio`.`campanas` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 zona_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(160) NOT NULL,
 inicio DATE NOT NULL, fin DATE NOT NULL,
 activa BOOLEAN NOT NULL DEFAULT TRUE, publicar BOOLEAN NOT NULL DEFAULT FALSE,
 radio_m INT NOT NULL DEFAULT 30,
 UNIQUE KEY campana_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES `jcatsen_pbio`.`organizaciones`(id),
 FOREIGN KEY (zona_id,organizacion_id) REFERENCES `jcatsen_pbio`.`zonas`(id,organizacion_id),
 CHECK (fin >= inicio), CHECK (radio_m BETWEEN 20 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Umbrales propios de una campaña. Sin fila, se usan los de referencia de la magnitud.
CREATE TABLE `jcatsen_pbio`.`campana_umbrales` (
 campana_id BIGINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL,
 umbral_medio DOUBLE NOT NULL, umbral_alto DOUBLE NOT NULL,
 PRIMARY KEY (campana_id,magnitud_id),
 FOREIGN KEY (campana_id) REFERENCES `jcatsen_pbio`.`campanas`(id) ON DELETE CASCADE,
 FOREIGN KEY (magnitud_id) REFERENCES `jcatsen_pbio`.`magnitudes`(id),
 CHECK (umbral_medio >= 0 AND umbral_alto > umbral_medio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `jcatsen_pbio`.`dispositivos` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 modelo_id SMALLINT UNSIGNED NOT NULL DEFAULT 1,
 nombre VARCHAR(160) NOT NULL, direccion_ble VARCHAR(17) NULL UNIQUE,
 activo BOOLEAN NOT NULL DEFAULT TRUE, simulado BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE KEY dispositivo_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES `jcatsen_pbio`.`organizaciones`(id),
 FOREIGN KEY (modelo_id) REFERENCES `jcatsen_pbio`.`modelos_dispositivo`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Un cambio de asignación cierra la anterior; no cambia la autoría del histórico.
CREATE TABLE `jcatsen_pbio`.`asignaciones` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 dispositivo_id BIGINT UNSIGNED NOT NULL, trabajador_id BIGINT UNSIGNED NOT NULL,
 campana_id BIGINT UNSIGNED NOT NULL,
 iniciada_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), finalizada_en DATETIME(3) NULL,
 dispositivo_activo BIGINT UNSIGNED GENERATED ALWAYS AS
   (CASE WHEN finalizada_en IS NULL THEN dispositivo_id ELSE NULL END) STORED,
 UNIQUE KEY un_destino_actual (dispositivo_activo),
 FOREIGN KEY (dispositivo_id,organizacion_id) REFERENCES `jcatsen_pbio`.`dispositivos`(id,organizacion_id),
 FOREIGN KEY (trabajador_id,organizacion_id) REFERENCES `jcatsen_pbio`.`usuarios`(id,organizacion_id),
 FOREIGN KEY (campana_id,organizacion_id) REFERENCES `jcatsen_pbio`.`campanas`(id,organizacion_id),
 CHECK (finalizada_en IS NULL OR finalizada_en >= iniciada_en),
 INDEX (trabajador_id,finalizada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Mediciones

CREATE TABLE `jcatsen_pbio`.`mediciones` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 evento CHAR(36) NOT NULL, asignacion_id BIGINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL, valor DOUBLE NOT NULL,
 capturada_en DATETIME(3) NOT NULL,
 recibida_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 latitud DOUBLE NULL, longitud DOUBLE NULL, precision_m DOUBLE NULL,
 contador TINYINT UNSIGNED NULL, rssi TINYINT NULL,
 simulado BOOLEAN NOT NULL, valida BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE KEY evento_unico (evento),
 FOREIGN KEY (asignacion_id) REFERENCES `jcatsen_pbio`.`asignaciones`(id),
 FOREIGN KEY (magnitud_id) REFERENCES `jcatsen_pbio`.`magnitudes`(id),
 CHECK ((latitud IS NULL AND longitud IS NULL AND precision_m IS NULL) OR
   (latitud IS NOT NULL AND longitud IS NOT NULL AND precision_m IS NOT NULL AND
    latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180 AND precision_m BETWEEN 0 AND 10000)),
 CHECK (rssi BETWEEN -127 AND 20),
 INDEX (capturada_en), INDEX (asignacion_id,magnitud_id,capturada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Solicitudes comerciales

-- Qué pide (servicio), quién lo pide (tipo de organización) y dónde.
-- No se pide cantidad de Orb: la recomienda Código Norte. Superficie, duración e
-- inicio solo aplican a estudios (servicios.mide_superficie); la superficie es
-- opcional porque el cliente no suele saberla. tipo_organizacion_id NULL = solicitud
-- anterior a v3 en la que no se preguntaba.
CREATE TABLE `jcatsen_pbio`.`solicitudes` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 servicio_id TINYINT UNSIGNED NOT NULL,
 tipo_organizacion_id TINYINT UNSIGNED NULL,
 estado_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
 entidad VARCHAR(180) NOT NULL, contacto VARCHAR(160) NOT NULL,
 email VARCHAR(254) NOT NULL, telefono VARCHAR(40) NOT NULL DEFAULT '',
 municipio VARCHAR(120) NOT NULL DEFAULT '', provincia VARCHAR(60) NOT NULL DEFAULT '',
 zona VARCHAR(200) NOT NULL, superficie DOUBLE NULL,
 duracion VARCHAR(100) NOT NULL DEFAULT '', inicio_deseado DATE NULL,
 necesidades TEXT NOT NULL,
 consentimiento_en DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
 recibida_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (servicio_id) REFERENCES `jcatsen_pbio`.`servicios`(id),
 FOREIGN KEY (tipo_organizacion_id) REFERENCES `jcatsen_pbio`.`tipos_organizacion`(id),
 FOREIGN KEY (estado_id) REFERENCES `jcatsen_pbio`.`estados_solicitud`(id),
 CHECK (superficie IS NULL OR superficie > 0),
 INDEX (estado_id,recibida_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Objetivos marcados en cada solicitud (uno o varios).
CREATE TABLE `jcatsen_pbio`.`solicitud_objetivos` (
 solicitud_id BIGINT UNSIGNED NOT NULL,
 objetivo_id TINYINT UNSIGNED NOT NULL,
 PRIMARY KEY (solicitud_id,objetivo_id),
 FOREIGN KEY (solicitud_id) REFERENCES `jcatsen_pbio`.`solicitudes`(id) ON DELETE CASCADE,
 FOREIGN KEY (objetivo_id) REFERENCES `jcatsen_pbio`.`objetivos_medicion`(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quién cambió cada solicitud de estado y cuándo. usuario_id NULL = formulario público.
CREATE TABLE `jcatsen_pbio`.`solicitudes_historial` (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 solicitud_id BIGINT UNSIGNED NOT NULL,
 estado_id TINYINT UNSIGNED NOT NULL,
 usuario_id BIGINT UNSIGNED NULL,
 cambiado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (solicitud_id) REFERENCES `jcatsen_pbio`.`solicitudes`(id) ON DELETE CASCADE,
 FOREIGN KEY (estado_id) REFERENCES `jcatsen_pbio`.`estados_solicitud`(id),
 FOREIGN KEY (usuario_id) REFERENCES `jcatsen_pbio`.`usuarios`(id) ON DELETE SET NULL,
 INDEX (solicitud_id,cambiado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Seguridad técnica
-- proposito sigue siendo ENUM: es un conjunto cerrado que solo cambia junto con el código.

CREATE TABLE `jcatsen_pbio`.`codigos_email` (
 usuario_id BIGINT UNSIGNED NOT NULL, proposito ENUM('registro','recuperar') NOT NULL,
 codigo_hash VARCHAR(255) NOT NULL, vence_en DATETIME NOT NULL,
 enviado_en DATETIME NOT NULL, intentos INT NOT NULL DEFAULT 0,
 PRIMARY KEY (usuario_id,proposito), FOREIGN KEY (usuario_id) REFERENCES `jcatsen_pbio`.`usuarios`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `jcatsen_pbio`.`sesiones_moviles` (
 token_hash CHAR(64) PRIMARY KEY, usuario_id BIGINT UNSIGNED NOT NULL,
 vence_en DATETIME NOT NULL,
 FOREIGN KEY (usuario_id) REFERENCES `jcatsen_pbio`.`usuarios`(id) ON DELETE CASCADE,
 INDEX (usuario_id,vence_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE `jcatsen_pbio`.`limites_acceso` (
 clave_hash CHAR(64) PRIMARY KEY, ventana_en DATETIME NOT NULL, intentos INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Vistas
-- Para leer los datos desde phpMyAdmin con nombres, sin memorizar JOIN.

CREATE VIEW `jcatsen_pbio`.`vista_mediciones` AS
 SELECT m.id,m.capturada_en AS fecha_utc,o.nombre AS organizacion,
 c.nombre AS campana,z.nombre AS zona,d.nombre AS dispositivo,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,
 t.nombre AS magnitud,m.valor,t.unidad,m.latitud,m.longitud,
 IF(m.simulado,'Simulado','Real') AS origen,
 IF(m.valida,'Válida','Pendiente o inválida') AS validacion
 FROM `jcatsen_pbio`.`mediciones` m JOIN `jcatsen_pbio`.`asignaciones` a ON a.id=m.asignacion_id
 JOIN `jcatsen_pbio`.`organizaciones` o ON o.id=a.organizacion_id JOIN `jcatsen_pbio`.`campanas` c ON c.id=a.campana_id
 JOIN `jcatsen_pbio`.`zonas` z ON z.id=c.zona_id
 JOIN `jcatsen_pbio`.`dispositivos` d ON d.id=a.dispositivo_id JOIN `jcatsen_pbio`.`usuarios` u ON u.id=a.trabajador_id
 JOIN `jcatsen_pbio`.`magnitudes` t ON t.id=m.magnitud_id;

CREATE VIEW `jcatsen_pbio`.`vista_flota` AS
 SELECT d.id,d.nombre,o.nombre AS organizacion,md.nombre AS modelo,d.direccion_ble,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,c.nombre AS campana,
 IF(d.activo,'Alta','Baja administrativa') AS estado,
 IF(d.simulado,'Simulado','Real') AS origen
 FROM `jcatsen_pbio`.`dispositivos` d JOIN `jcatsen_pbio`.`organizaciones` o ON o.id=d.organizacion_id
 JOIN `jcatsen_pbio`.`modelos_dispositivo` md ON md.id=d.modelo_id
 LEFT JOIN `jcatsen_pbio`.`asignaciones` a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL
 LEFT JOIN `jcatsen_pbio`.`usuarios` u ON u.id=a.trabajador_id LEFT JOIN `jcatsen_pbio`.`campanas` c ON c.id=a.campana_id;

CREATE VIEW `jcatsen_pbio`.`vista_usuarios` AS
 SELECT u.id,CONCAT(u.nombre,' ',u.apellidos) AS nombre,u.email,
 o.nombre AS organizacion,r.nombre AS rol,
 CASE WHEN u.debe_cambiar_clave THEN 'Invitación pendiente'
      WHEN u.activo THEN 'Activa' ELSE 'Desactivada' END AS estado,
 (SELECT GROUP_CONCAT(i.proveedor) FROM `jcatsen_pbio`.`identidades_externas` i WHERE i.usuario_id=u.id) AS accesos_externos
 FROM `jcatsen_pbio`.`usuarios` u JOIN `jcatsen_pbio`.`roles` r ON r.id=u.rol_id
 LEFT JOIN `jcatsen_pbio`.`organizaciones` o ON o.id=u.organizacion_id;

CREATE VIEW `jcatsen_pbio`.`vista_solicitudes` AS
 SELECT s.id,s.recibida_en,sv.nombre AS servicio,t.nombre AS tipo_organizacion,e.nombre AS estado,
 s.entidad,s.contacto,s.email,s.telefono,s.municipio,s.provincia,s.zona,s.superficie,s.duracion,s.inicio_deseado,
 (SELECT GROUP_CONCAT(o.nombre ORDER BY o.orden SEPARATOR ', ') FROM `jcatsen_pbio`.`solicitud_objetivos` so
  JOIN `jcatsen_pbio`.`objetivos_medicion` o ON o.id=so.objetivo_id WHERE so.solicitud_id=s.id) AS objetivos,
 (SELECT MAX(h.cambiado_en) FROM `jcatsen_pbio`.`solicitudes_historial` h WHERE h.solicitud_id=s.id) AS ultimo_cambio
 FROM `jcatsen_pbio`.`solicitudes` s JOIN `jcatsen_pbio`.`servicios` sv ON sv.id=s.servicio_id
 LEFT JOIN `jcatsen_pbio`.`tipos_organizacion` t ON t.id=s.tipo_organizacion_id
 JOIN `jcatsen_pbio`.`estados_solicitud` e ON e.id=s.estado_id;

-- SOLO DEMOSTRACIÓN. Correos .example, MAC ficticias y mediciones simuladas.
-- Todas las cuentas: AeriqDemo-2026! (sustituirlas en un entorno real).
-- Roles (roles.id): 2 trabajador, 3 analista, 4 admin, 5 comercial.
SET time_zone = '+00:00';
START TRANSACTION;
INSERT INTO `jcatsen_pbio`.`organizaciones`(id,nombre) VALUES
 (1,'Ayuntamiento de Gandía · demostración'),
 (2,'Empresa Verde · demostración'),
 (3,'Código Norte · equipo comercial');
INSERT INTO `jcatsen_pbio`.`usuarios`(id,organizacion_id,rol_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en,invitacion_expira_en) VALUES
 (1,1,4,'Ana','Administradora','admin@gandia.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (2,1,2,'Pau','Operador','operador@gandia.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',0,1,NULL,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 24 HOUR)),
 (3,1,3,'Marta','Analista','analista@gandia.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (4,3,5,'Joan','Comercial','comercial@codigonorte.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (5,2,4,'Elena','Administradora','admin@empresaverde.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (6,2,2,'Luis','Operador','operador@empresaverde.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL);
INSERT INTO `jcatsen_pbio`.`zonas`(id,organizacion_id,nombre) VALUES
 (1,1,'Campus UPV Gandía'),
 (2,2,'Instalaciones Empresa Verde');
INSERT INTO `jcatsen_pbio`.`campanas`(id,organizacion_id,zona_id,nombre,inicio,fin,publicar) VALUES
 (1,1,1,'Paseo por el campus · simulación',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),1),
 (2,2,2,'Estudio interno · simulación',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),0);
-- La campaña 1 usa los umbrales de referencia del ozono; la 2 define los suyos.
INSERT INTO `jcatsen_pbio`.`campana_umbrales`(campana_id,magnitud_id,umbral_medio,umbral_alto) VALUES (2,14,50,90);
INSERT INTO `jcatsen_pbio`.`dispositivos`(id,organizacion_id,modelo_id,nombre,direccion_ble,simulado) VALUES
 (1,1,1,'Orb Campus 01','D0:00:00:00:00:01',1),
 (2,1,1,'Orb de reserva 02','D0:00:00:00:00:02',1),
 (3,2,1,'Orb Empresa 01','D0:00:00:00:00:03',1);
INSERT INTO `jcatsen_pbio`.`asignaciones`(id,organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en) VALUES
 (1,1,1,2,1,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)),
 (2,2,3,6,2,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY));
INSERT INTO `jcatsen_pbio`.`mediciones`(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado) VALUES
 ('00000000-0000-4000-8000-000000000001',1,14,42,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY),38.996651,-0.166009,5,1,-60,1),
 ('00000000-0000-4000-8000-000000000002',1,14,55,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY),38.996711,-0.166009,5,2,-61,1),
 ('00000000-0000-4000-8000-000000000003',1,14,68,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 HOUR),38.996651,-0.165929,5,3,-59,1),
 ('00000000-0000-4000-8000-000000000004',1,14,78,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 HOUR),38.996681,-0.165969,5,4,-58,1),
 ('00000000-0000-4000-8000-000000000005',1,14,104,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 HOUR),38.996691,-0.165939,5,5,-62,1),
 ('00000000-0000-4000-8000-000000000006',1,14,85,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE),38.996671,-0.165989,5,6,-60,1),
 ('00000000-0000-4000-8000-000000000007',1,12,22,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE),38.996671,-0.165989,5,7,-60,1),
 ('00000000-0000-4000-8000-000000000008',2,14,49,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 HOUR),38.998000,-0.162500,8,1,-65,1),
 ('00000000-0000-4000-8000-000000000009',2,14,57,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE),38.998060,-0.162500,8,2,-63,1);
-- Servicios: 1 kits, 2 estudio, 3 asesoramiento. Tipos: 1 administración, 2 empresa, 3 otra.
-- Estados: 1 pendiente, 2 en revisión, 3 respondida. Objetivos: ver objetivos_medicion.
INSERT INTO `jcatsen_pbio`.`solicitudes`(id,servicio_id,tipo_organizacion_id,estado_id,entidad,contacto,email,telefono,municipio,provincia,zona,superficie,duracion,inicio_deseado,necesidades,recibida_en) VALUES
 (1,1,1,1,'Ayuntamiento de ejemplo','Responsable municipal','municipio@example.test','600000001','Gandia','Valencia','Parques del municipio',NULL,'',NULL,'Kits para una campaña de movilidad.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 DAY)),
 (2,1,2,2,'Empresa de ejemplo','Responsable ambiental','empresa@example.test','','Oliva','Valencia','Recinto industrial',NULL,'',NULL,'Comparar turnos y zonas de acceso.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY)),
 (3,2,3,3,'Centro educativo de ejemplo','Coordinación del centro','centro@example.test','','Tavernes de la Valldigna','Valencia','Entorno del centro',2500,'2 semanas',NULL,'Estudio con informe y evolución diaria.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY));
INSERT INTO `jcatsen_pbio`.`solicitud_objetivos`(solicitud_id,objetivo_id) VALUES (1,1),(2,2),(3,1),(3,4);
INSERT INTO `jcatsen_pbio`.`solicitudes_historial`(solicitud_id,estado_id,usuario_id,cambiado_en) VALUES
 (1,1,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 DAY)),
 (2,1,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY)),
 (2,2,4,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)),
 (3,1,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)),
 (3,3,4,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 HOUR));
COMMIT;

SELECT DATABASE() AS base_actual, COUNT(*) AS usuarios_demo FROM `jcatsen_pbio`.`usuarios`;
