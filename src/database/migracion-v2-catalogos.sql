-- AERIQ · Migración v1 -> v2: catálogos en lugar de ENUM y datos repetidos.
-- Conserva TODOS los datos: cuentas, organizaciones, campañas, Orb, lecturas y solicitudes.
-- Es repetible: si un paso ya se aplicó, no hace nada. MariaDB 10.4+.
--
-- Antes de importar:
--   1. Exporta una copia SQL de la base (phpMyAdmin > Exportar).
--   2. Selecciona la base de AERIQ en phpMyAdmin (no information_schema).
--   3. Detén la captura Android durante la migración.
-- Después: sube la web nueva (los PHP de esta versión leen el esquema v2).
--
-- Cambios (explicados en doc/base-de-datos.md):
--   usuarios.rol (ENUM)            -> roles + permisos + rol_permisos, usuarios.rol_id
--   usuarios.firebase_uid          -> identidades_externas
--   campanas.zona (texto)          -> zonas, campanas.zona_id
--   campanas.umbral_medio/alto     -> campana_umbrales (+ umbrales de referencia en magnitudes)
--   dispositivos                   -> modelos_dispositivo + modelo_magnitudes, dispositivos.modelo_id
--   mediciones.tipo                -> mediciones.magnitud_id (mismo valor, nombre de clave foránea)
--   solicitudes.servicio/estado    -> servicios, estados_solicitud, solicitudes_historial
--   organizaciones.nombre          -> único (los duplicados se renombran con su id, no se borran)
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @db := DATABASE();
SET @aeriq_old_concat := @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 65536;

-- Las vistas antiguas leen columnas que desaparecen; se recrean al final.
DROP VIEW IF EXISTS vista_mediciones, vista_flota, vista_usuarios, vista_solicitudes;

-- ------------------------------------------------------------ 1. Catálogos
CREATE TABLE IF NOT EXISTS roles (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 descripcion VARCHAR(255) NOT NULL DEFAULT '',
 requiere_organizacion BOOLEAN NOT NULL DEFAULT TRUE,
 asignable BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO roles (id,codigo,nombre,descripcion,requiere_organizacion,asignable) VALUES
 (1,'visitante','Visitante','Cuenta sin organización. Solo consulta pública.',0,0),
 (2,'trabajador','Trabajador','Recoge mediciones con los Orb que tiene asignados.',1,1),
 (3,'analista','Analista','Consulta informes y exporta los datos de su organización.',1,1),
 (4,'admin','Administrador','Gestiona cuentas, campañas y Orb de su organización.',1,0),
 (5,'comercial','Comercial','Atiende las solicitudes de presupuesto de Código Norte.',0,0);

CREATE TABLE IF NOT EXISTS permisos (
 id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(60) NOT NULL UNIQUE,
 descripcion VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO permisos (id,codigo,descripcion) VALUES
 (1,'mapa.organizacion','Ver el mapa privado de su organización.'),
 (2,'informes.ver','Ver el panel, los informes y exportar los datos de toda la organización.'),
 (3,'organizacion.gestionar','Dar de alta cuentas, campañas y Orb de su organización.'),
 (4,'mediciones.registrar','Recibir asignaciones, vincular Orb propios y enviar lecturas desde Android.'),
 (5,'solicitudes.gestionar','Ver las solicitudes comerciales y cambiar su estado.');

CREATE TABLE IF NOT EXISTS rol_permisos (
 rol_id TINYINT UNSIGNED NOT NULL,
 permiso_id SMALLINT UNSIGNED NOT NULL,
 PRIMARY KEY (rol_id,permiso_id),
 FOREIGN KEY (rol_id) REFERENCES roles(id) ON DELETE CASCADE,
 FOREIGN KEY (permiso_id) REFERENCES permisos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO rol_permisos (rol_id,permiso_id) VALUES
 (2,1),(2,4),
 (3,1),(3,2),
 (4,1),(4,2),(4,3),(4,4),
 (5,5);

ALTER TABLE magnitudes
 ADD COLUMN IF NOT EXISTS umbral_medio_defecto DOUBLE NULL,
 ADD COLUMN IF NOT EXISTS umbral_alto_defecto DOUBLE NULL;
UPDATE magnitudes SET umbral_medio_defecto=60, umbral_alto_defecto=100
 WHERE id=14 AND umbral_medio_defecto IS NULL AND umbral_alto_defecto IS NULL;
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='magnitudes' AND CONSTRAINT_NAME='magnitud_umbrales')=0,
 'ALTER TABLE magnitudes ADD CONSTRAINT magnitud_umbrales CHECK ((umbral_medio_defecto IS NULL AND umbral_alto_defecto IS NULL) OR (umbral_medio_defecto >= 0 AND umbral_alto_defecto > umbral_medio_defecto))','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

CREATE TABLE IF NOT EXISTS modelos_dispositivo (
 id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(40) NOT NULL UNIQUE,
 nombre VARCHAR(120) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO modelos_dispositivo (id,codigo,nombre) VALUES
 (1,'orb-v1','Orb v1 · nRF52840 (ozono y temperatura)');

CREATE TABLE IF NOT EXISTS modelo_magnitudes (
 modelo_id SMALLINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL,
 PRIMARY KEY (modelo_id,magnitud_id),
 FOREIGN KEY (modelo_id) REFERENCES modelos_dispositivo(id) ON DELETE CASCADE,
 FOREIGN KEY (magnitud_id) REFERENCES magnitudes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO modelo_magnitudes (modelo_id,magnitud_id) VALUES (1,12),(1,14);

CREATE TABLE IF NOT EXISTS servicios (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 descripcion VARCHAR(255) NOT NULL DEFAULT '',
 mide_superficie BOOLEAN NOT NULL DEFAULT FALSE,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO servicios (id,codigo,nombre,descripcion,mide_superficie) VALUES
 (1,'administracion','Kits para administraciones públicas','Kits Orb para campañas municipales.',0),
 (2,'empresa','Kits para empresas privadas','Kits Orb para instalaciones y recintos privados.',0),
 (3,'estudio','Estudio ambiental de Código Norte','Estudio completo con informe, según la superficie.',1);

CREATE TABLE IF NOT EXISTS estados_solicitud (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL,
 final BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO estados_solicitud (id,codigo,nombre,orden,final) VALUES
 (1,'pendiente','Pendiente',1,0),
 (2,'en_revision','En revisión',2,0),
 (3,'respondida','Respondida',3,0),
 (4,'cerrada','Cerrada',4,1);

-- ------------------------------------------------------------ 2. Organizaciones
-- Dos organizaciones con el mismo nombre no se distinguen en la interfaz.
-- Se conserva la más antigua y las demás reciben su id en el nombre. No se borra ninguna.
UPDATE organizaciones o
 JOIN (SELECT nombre, MIN(id) AS primera FROM organizaciones GROUP BY nombre HAVING COUNT(*)>1) d
   ON d.nombre=o.nombre AND o.id<>d.primera
 SET o.nombre=CONCAT(LEFT(o.nombre,140),' (#',o.id,')');
ALTER TABLE organizaciones ADD UNIQUE KEY IF NOT EXISTS organizacion_nombre (nombre);

-- ------------------------------------------------------------ 3. Cuentas: rol -> rol_id
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS rol_id TINYINT UNSIGNED NULL AFTER organizacion_id;
SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='usuarios' AND COLUMN_NAME='rol')>0,
 'UPDATE usuarios u JOIN roles r ON r.codigo=u.rol SET u.rol_id=r.id WHERE u.rol_id IS NULL','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
UPDATE usuarios SET rol_id=1 WHERE rol_id IS NULL;
ALTER TABLE usuarios MODIFY rol_id TINYINT UNSIGNED NOT NULL DEFAULT 1;
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='usuarios' AND CONSTRAINT_NAME='usuarios_rol')=0,
 'ALTER TABLE usuarios ADD CONSTRAINT usuarios_rol FOREIGN KEY (rol_id) REFERENCES roles(id)','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
-- La comprobación antigua citaba los valores del ENUM; ahora la regla vive en roles.requiere_organizacion.
SET @c := (SELECT GROUP_CONCAT(CONCAT('DROP CONSTRAINT `',CONSTRAINT_NAME,'`')) FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='usuarios' AND CHECK_CLAUSE LIKE '%`rol`%');
SET @s := IF(@c IS NULL,'DO 0',CONCAT('ALTER TABLE usuarios ',@c));
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
ALTER TABLE usuarios DROP COLUMN IF EXISTS rol;

-- ------------------------------------------------------------ 4. Identidades externas
CREATE TABLE IF NOT EXISTS identidades_externas (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 usuario_id BIGINT UNSIGNED NOT NULL,
 proveedor VARCHAR(30) NOT NULL,
 uid_externo VARCHAR(128) NOT NULL,
 vinculada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY identidad_unica (proveedor,uid_externo),
 UNIQUE KEY una_por_proveedor (usuario_id,proveedor),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='usuarios' AND COLUMN_NAME='firebase_uid')>0,
 'INSERT IGNORE INTO identidades_externas(usuario_id,proveedor,uid_externo) SELECT id,''firebase'',firebase_uid FROM usuarios WHERE firebase_uid IS NOT NULL','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
ALTER TABLE usuarios DROP INDEX IF EXISTS usuario_firebase_uid;
ALTER TABLE usuarios DROP COLUMN IF EXISTS firebase_uid;

-- ------------------------------------------------------------ 5. Zonas y umbrales de campaña
CREATE TABLE IF NOT EXISTS zonas (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(200) NOT NULL,
 UNIQUE KEY zona_nombre (organizacion_id,nombre),
 UNIQUE KEY zona_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE campanas ADD COLUMN IF NOT EXISTS zona_id BIGINT UNSIGNED NULL AFTER organizacion_id;
SET @zona := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='campanas' AND COLUMN_NAME='zona');
SET @s := IF(@zona>0,'INSERT IGNORE INTO zonas(organizacion_id,nombre) SELECT DISTINCT organizacion_id,TRIM(zona) FROM campanas','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
SET @s := IF(@zona>0,'UPDATE campanas c JOIN zonas z ON z.organizacion_id=c.organizacion_id AND z.nombre=TRIM(c.zona) SET c.zona_id=z.id WHERE c.zona_id IS NULL','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
ALTER TABLE campanas MODIFY zona_id BIGINT UNSIGNED NOT NULL;
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='campanas' AND CONSTRAINT_NAME='campanas_zona')=0,
 'ALTER TABLE campanas ADD CONSTRAINT campanas_zona FOREIGN KEY (zona_id,organizacion_id) REFERENCES zonas(id,organizacion_id)','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

CREATE TABLE IF NOT EXISTS campana_umbrales (
 campana_id BIGINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL,
 umbral_medio DOUBLE NOT NULL, umbral_alto DOUBLE NOT NULL,
 PRIMARY KEY (campana_id,magnitud_id),
 FOREIGN KEY (campana_id) REFERENCES campanas(id) ON DELETE CASCADE,
 FOREIGN KEY (magnitud_id) REFERENCES magnitudes(id),
 CHECK (umbral_medio >= 0 AND umbral_alto > umbral_medio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Los umbrales antiguos eran siempre de ozono (14). Se conservan tal cual.
SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='campanas' AND COLUMN_NAME='umbral_medio')>0,
 'INSERT IGNORE INTO campana_umbrales(campana_id,magnitud_id,umbral_medio,umbral_alto) SELECT id,14,umbral_medio,umbral_alto FROM campanas','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
SET @c := (SELECT GROUP_CONCAT(CONCAT('DROP CONSTRAINT `',CONSTRAINT_NAME,'`')) FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='campanas' AND CHECK_CLAUSE LIKE '%`umbral_%');
SET @s := IF(@c IS NULL,'DO 0',CONCAT('ALTER TABLE campanas ',@c));
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
ALTER TABLE campanas DROP COLUMN IF EXISTS zona, DROP COLUMN IF EXISTS umbral_medio, DROP COLUMN IF EXISTS umbral_alto;

-- ------------------------------------------------------------ 6. Modelo de cada Orb
-- Todos los Orb existentes son del único modelo fabricado hasta ahora (orb-v1).
ALTER TABLE dispositivos ADD COLUMN IF NOT EXISTS modelo_id SMALLINT UNSIGNED NOT NULL DEFAULT 1 AFTER organizacion_id;
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='dispositivos' AND CONSTRAINT_NAME='dispositivos_modelo')=0,
 'ALTER TABLE dispositivos ADD CONSTRAINT dispositivos_modelo FOREIGN KEY (modelo_id) REFERENCES modelos_dispositivo(id)','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

-- ------------------------------------------------------------ 7. Mediciones: tipo -> magnitud_id
-- Solo cambia el nombre; los valores (14, 12) y la clave foránea se conservan.
SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='mediciones' AND COLUMN_NAME='tipo')>0,
 'ALTER TABLE mediciones CHANGE COLUMN tipo magnitud_id INT NOT NULL','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

-- ------------------------------------------------------------ 8. Solicitudes
ALTER TABLE solicitudes
 ADD COLUMN IF NOT EXISTS servicio_id TINYINT UNSIGNED NULL AFTER id,
 ADD COLUMN IF NOT EXISTS estado_id TINYINT UNSIGNED NULL AFTER servicio_id;
SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='solicitudes' AND COLUMN_NAME='servicio')>0,
 'UPDATE solicitudes s JOIN servicios v ON v.codigo=s.servicio SET s.servicio_id=v.id WHERE s.servicio_id IS NULL','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
-- El estado antiguo «en revision» llevaba un espacio; su código pasa a ser en_revision.
SET @s := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='solicitudes' AND COLUMN_NAME='estado')>0,
 'UPDATE solicitudes s JOIN estados_solicitud e ON e.codigo=REPLACE(s.estado,'' '',''_'') SET s.estado_id=e.id WHERE s.estado_id IS NULL','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
UPDATE solicitudes SET estado_id=1 WHERE estado_id IS NULL;
ALTER TABLE solicitudes
 MODIFY servicio_id TINYINT UNSIGNED NOT NULL,
 MODIFY estado_id TINYINT UNSIGNED NOT NULL DEFAULT 1;
SET @c := (SELECT GROUP_CONCAT(CONCAT('DROP CONSTRAINT `',CONSTRAINT_NAME,'`')) FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='solicitudes' AND CHECK_CLAUSE LIKE '%`servicio`%');
SET @s := IF(@c IS NULL,'DO 0',CONCAT('ALTER TABLE solicitudes ',@c));
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
ALTER TABLE solicitudes DROP INDEX IF EXISTS estado;
ALTER TABLE solicitudes DROP COLUMN IF EXISTS servicio, DROP COLUMN IF EXISTS estado;
ALTER TABLE solicitudes ADD INDEX IF NOT EXISTS estado_id (estado_id,recibida_en);
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='solicitudes' AND CONSTRAINT_NAME='solicitudes_servicio')=0,
 'ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_servicio FOREIGN KEY (servicio_id) REFERENCES servicios(id), ADD CONSTRAINT solicitudes_estado FOREIGN KEY (estado_id) REFERENCES estados_solicitud(id)','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='solicitudes' AND CONSTRAINT_NAME='solicitud_cantidad_superficie')=0,
 'ALTER TABLE solicitudes ADD CONSTRAINT solicitud_cantidad_superficie CHECK ((cantidad > 0 AND superficie IS NULL) OR (cantidad IS NULL AND superficie > 0))','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

CREATE TABLE IF NOT EXISTS solicitudes_historial (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 solicitud_id BIGINT UNSIGNED NOT NULL,
 estado_id TINYINT UNSIGNED NOT NULL,
 usuario_id BIGINT UNSIGNED NULL,
 cambiado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (solicitud_id) REFERENCES solicitudes(id) ON DELETE CASCADE,
 FOREIGN KEY (estado_id) REFERENCES estados_solicitud(id),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL,
 INDEX (solicitud_id,cambiado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Solo se conoce la recepción (siempre «pendiente»). Los cambios anteriores a la
-- migración no tenían fecha ni autor y no se inventan.
INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id,cambiado_en)
 SELECT s.id,1,NULL,s.recibida_en FROM solicitudes s
 WHERE NOT EXISTS (SELECT 1 FROM solicitudes_historial h WHERE h.solicitud_id=s.id);

-- ------------------------------------------------------------ 9. Vistas
CREATE OR REPLACE VIEW vista_mediciones AS
 SELECT m.id,m.capturada_en AS fecha_utc,o.nombre AS organizacion,
 c.nombre AS campana,z.nombre AS zona,d.nombre AS dispositivo,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,
 t.nombre AS magnitud,m.valor,t.unidad,m.latitud,m.longitud,
 IF(m.simulado,'Simulado','Real') AS origen,
 IF(m.valida,'Válida','Pendiente o inválida') AS validacion
 FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id
 JOIN organizaciones o ON o.id=a.organizacion_id JOIN campanas c ON c.id=a.campana_id
 JOIN zonas z ON z.id=c.zona_id
 JOIN dispositivos d ON d.id=a.dispositivo_id JOIN usuarios u ON u.id=a.trabajador_id
 JOIN magnitudes t ON t.id=m.magnitud_id;

CREATE OR REPLACE VIEW vista_flota AS
 SELECT d.id,d.nombre,o.nombre AS organizacion,md.nombre AS modelo,d.direccion_ble,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,c.nombre AS campana,
 IF(d.activo,'Alta','Baja administrativa') AS estado,
 IF(d.simulado,'Simulado','Real') AS origen
 FROM dispositivos d JOIN organizaciones o ON o.id=d.organizacion_id
 JOIN modelos_dispositivo md ON md.id=d.modelo_id
 LEFT JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL
 LEFT JOIN usuarios u ON u.id=a.trabajador_id LEFT JOIN campanas c ON c.id=a.campana_id;

CREATE OR REPLACE VIEW vista_usuarios AS
 SELECT u.id,CONCAT(u.nombre,' ',u.apellidos) AS nombre,u.email,
 o.nombre AS organizacion,r.nombre AS rol,
 CASE WHEN u.debe_cambiar_clave THEN 'Invitación pendiente'
      WHEN u.activo THEN 'Activa' ELSE 'Desactivada' END AS estado,
 (SELECT GROUP_CONCAT(i.proveedor) FROM identidades_externas i WHERE i.usuario_id=u.id) AS accesos_externos
 FROM usuarios u JOIN roles r ON r.id=u.rol_id
 LEFT JOIN organizaciones o ON o.id=u.organizacion_id;

CREATE OR REPLACE VIEW vista_solicitudes AS
 SELECT s.id,s.recibida_en,sv.nombre AS servicio,e.nombre AS estado,
 s.entidad,s.contacto,s.email,s.zona,s.cantidad,s.superficie,
 (SELECT MAX(h.cambiado_en) FROM solicitudes_historial h WHERE h.solicitud_id=s.id) AS ultimo_cambio
 FROM solicitudes s JOIN servicios sv ON sv.id=s.servicio_id
 JOIN estados_solicitud e ON e.id=s.estado_id;

SET SESSION group_concat_max_len = @aeriq_old_concat;

-- Resumen para comprobar el resultado en phpMyAdmin.
SELECT r.nombre AS rol, COUNT(u.id) AS cuentas FROM roles r LEFT JOIN usuarios u ON u.rol_id=r.id GROUP BY r.id ORDER BY r.id;
SELECT (SELECT COUNT(*) FROM organizaciones) AS organizaciones,(SELECT COUNT(*) FROM zonas) AS zonas,
 (SELECT COUNT(*) FROM campanas) AS campanas,(SELECT COUNT(*) FROM dispositivos) AS orb,
 (SELECT COUNT(*) FROM mediciones) AS mediciones,(SELECT COUNT(*) FROM solicitudes) AS solicitudes;
