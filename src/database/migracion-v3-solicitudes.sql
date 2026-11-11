-- AERIQ · Migración v2 -> v3: formulario de solicitudes comerciales.
-- Conserva TODAS las solicitudes y su historial. Es repetible: si un paso ya se
-- aplicó, no hace nada. MariaDB 10.4+. Requiere haber aplicado antes
-- migracion-v2-catalogos.sql (o una instalación v2 de schema.sql).
--
-- Antes de importar:
--   1. Exporta una copia SQL de la base (phpMyAdmin > Exportar).
--   2. Selecciona la base de AERIQ en phpMyAdmin (no information_schema).
-- Después: sube la web nueva (solicitar.html, solicitudes.js y los PHP de esta versión).
--
-- Cambios (explicados en doc/base-de-datos.md):
--   servicios administracion/empresa -> un único servicio 'kits' + tipos_organizacion
--   servicios                        -> + 'asesoramiento', + columna orden
--   solicitudes.cantidad             -> eliminada (el número de Orb lo recomienda Código Norte)
--   solicitudes.superficie           -> opcional también en estudios
--   solicitudes                      -> + tipo_organizacion_id, municipio, provincia, inicio_deseado
--   objetivos_medicion + solicitud_objetivos (para qué quiere medir el cliente)
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @db := DATABASE();

-- La vista lee columnas que cambian; se recrea al final.
DROP VIEW IF EXISTS vista_solicitudes;

-- ------------------------------------------------------------ 1. Catálogos nuevos
CREATE TABLE IF NOT EXISTS tipos_organizacion (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO tipos_organizacion (id,codigo,nombre,orden) VALUES
 (1,'administracion','Administración pública',1),
 (2,'empresa','Empresa',2),
 (3,'otra','Otra entidad (centro educativo, asociación…)',3);

CREATE TABLE IF NOT EXISTS objetivos_medicion (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO objetivos_medicion (id,codigo,nombre,orden) VALUES
 (1,'espacios_publicos','Calidad del aire en espacios públicos',1),
 (2,'salud_laboral','Salud y seguridad laboral',2),
 (3,'normativa','Informe técnico o normativa',3),
 (4,'investigacion','Investigación o divulgación',4),
 (5,'otro','Otro',5);

-- ------------------------------------------------------------ 2. Columnas nuevas de solicitudes
ALTER TABLE solicitudes
 ADD COLUMN IF NOT EXISTS tipo_organizacion_id TINYINT UNSIGNED NULL AFTER servicio_id,
 ADD COLUMN IF NOT EXISTS municipio VARCHAR(120) NOT NULL DEFAULT '' AFTER telefono,
 ADD COLUMN IF NOT EXISTS provincia VARCHAR(60) NOT NULL DEFAULT '' AFTER municipio,
 ADD COLUMN IF NOT EXISTS inicio_deseado DATE NULL AFTER duracion;
SET @s := IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='solicitudes' AND CONSTRAINT_NAME='solicitudes_tipo_organizacion')=0,
 'ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_tipo_organizacion FOREIGN KEY (tipo_organizacion_id) REFERENCES tipos_organizacion(id)','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

-- ------------------------------------------------------------ 3. Servicios: kits + tipo de organización
ALTER TABLE servicios ADD COLUMN IF NOT EXISTS orden TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER mide_superficie;
INSERT INTO servicios (codigo,nombre,descripcion,mide_superficie,orden) VALUES
 ('kits','Comprar Orb para mi organización','Sensores propios con app y panel. Te recomendamos cuántos necesitas.',0,1),
 ('asesoramiento','No lo tengo claro, aconsejadme','Cuéntanos tu caso y te proponemos la opción que mejor encaja.',0,3)
 ON DUPLICATE KEY UPDATE codigo=codigo;
UPDATE servicios SET nombre='Contratar un estudio de ozono',
 descripcion='Medimos nosotros la zona durante un tiempo, sin comprar sensores, y entregamos un informe.',orden=2
 WHERE codigo='estudio' AND orden=0;

-- Las solicitudes de los antiguos servicios conservan quién las pidió y pasan a 'kits'.
UPDATE solicitudes s JOIN servicios v ON v.id=s.servicio_id JOIN tipos_organizacion t ON t.codigo=v.codigo
 SET s.tipo_organizacion_id=t.id WHERE v.codigo IN ('administracion','empresa') AND s.tipo_organizacion_id IS NULL;
SET @kits := (SELECT id FROM servicios WHERE codigo='kits');
UPDATE solicitudes s JOIN servicios v ON v.id=s.servicio_id SET s.servicio_id=@kits WHERE v.codigo IN ('administracion','empresa');
DELETE FROM servicios WHERE codigo IN ('administracion','empresa');

-- ------------------------------------------------------------ 4. Sin cantidad; superficie opcional
SET @c := (SELECT GROUP_CONCAT(CONCAT('DROP CONSTRAINT `',CONSTRAINT_NAME,'`')) FROM information_schema.CHECK_CONSTRAINTS
 WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='solicitudes' AND CHECK_CLAUSE LIKE '%`cantidad`%');
SET @s := IF(@c IS NULL,'DO 0',CONCAT('ALTER TABLE solicitudes ',@c));
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;
ALTER TABLE solicitudes DROP COLUMN IF EXISTS cantidad;
SET @s := IF((SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='solicitudes' AND CONSTRAINT_NAME='solicitud_superficie')=0,
 'ALTER TABLE solicitudes ADD CONSTRAINT solicitud_superficie CHECK (superficie IS NULL OR superficie > 0)','DO 0');
PREPARE aeriq_m FROM @s; EXECUTE aeriq_m; DEALLOCATE PREPARE aeriq_m;

-- ------------------------------------------------------------ 5. Objetivos de cada solicitud
CREATE TABLE IF NOT EXISTS solicitud_objetivos (
 solicitud_id BIGINT UNSIGNED NOT NULL,
 objetivo_id TINYINT UNSIGNED NOT NULL,
 PRIMARY KEY (solicitud_id,objetivo_id),
 FOREIGN KEY (solicitud_id) REFERENCES solicitudes(id) ON DELETE CASCADE,
 FOREIGN KEY (objetivo_id) REFERENCES objetivos_medicion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ------------------------------------------------------------ 6. Vista para phpMyAdmin
CREATE OR REPLACE VIEW vista_solicitudes AS
 SELECT s.id,s.recibida_en,sv.nombre AS servicio,t.nombre AS tipo_organizacion,e.nombre AS estado,
 s.entidad,s.contacto,s.email,s.telefono,s.municipio,s.provincia,s.zona,s.superficie,s.duracion,s.inicio_deseado,
 (SELECT GROUP_CONCAT(o.nombre ORDER BY o.orden SEPARATOR ', ') FROM solicitud_objetivos so
  JOIN objetivos_medicion o ON o.id=so.objetivo_id WHERE so.solicitud_id=s.id) AS objetivos,
 (SELECT MAX(h.cambiado_en) FROM solicitudes_historial h WHERE h.solicitud_id=s.id) AS ultimo_cambio
 FROM solicitudes s JOIN servicios sv ON sv.id=s.servicio_id
 LEFT JOIN tipos_organizacion t ON t.id=s.tipo_organizacion_id
 JOIN estados_solicitud e ON e.id=s.estado_id;

-- Resumen para comprobar el resultado en phpMyAdmin.
SELECT sv.codigo AS servicio, COALESCE(t.codigo,'(sin dato)') AS tipo_organizacion, COUNT(s.id) AS solicitudes
 FROM servicios sv LEFT JOIN solicitudes s ON s.servicio_id=sv.id LEFT JOIN tipos_organizacion t ON t.id=s.tipo_organizacion_id
 GROUP BY sv.id, t.id ORDER BY sv.orden, t.orden;
