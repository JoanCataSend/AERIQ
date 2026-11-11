-- AERIQ · Migración v3 -> v4: nivel de batería de cada Orb.
-- Añade la tabla lecturas_bateria y la batería a vista_flota. No borra ni cambia datos.
-- Repetible. Importar con la base de AERIQ seleccionada en phpMyAdmin, después de
-- migracion-v3-solicitudes.sql (o sobre una instalación hecha con schema.sql actual).
-- No depende de las tablas de solicitudes: también funciona directamente sobre una base v2.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- Nivel de batería que informa el propio Orb (medidor MAX17048) a través de la app.
-- No es una medición ambiental: pertenece al Orb, no a una campaña, por eso no va en mediciones.
CREATE TABLE IF NOT EXISTS lecturas_bateria (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 dispositivo_id BIGINT UNSIGNED NOT NULL,
 porcentaje TINYINT UNSIGNED NOT NULL,
 capturada_en DATETIME(3) NOT NULL,
 recibida_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 usuario_id BIGINT UNSIGNED NULL,
 FOREIGN KEY (dispositivo_id) REFERENCES dispositivos(id),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL,
 CHECK (porcentaje <= 100),
 INDEX (dispositivo_id,capturada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE OR REPLACE VIEW vista_flota AS
 SELECT d.id,d.nombre,o.nombre AS organizacion,md.nombre AS modelo,d.direccion_ble,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,c.nombre AS campana,
 IF(d.activo,'Alta','Baja administrativa') AS estado,
 IF(d.simulado,'Simulado','Real') AS origen,
 (SELECT b.porcentaje FROM lecturas_bateria b WHERE b.dispositivo_id=d.id ORDER BY b.capturada_en DESC,b.id DESC LIMIT 1) AS bateria_pct,
 (SELECT MAX(b.capturada_en) FROM lecturas_bateria b WHERE b.dispositivo_id=d.id) AS bateria_utc
 FROM dispositivos d JOIN organizaciones o ON o.id=d.organizacion_id
 JOIN modelos_dispositivo md ON md.id=d.modelo_id
 LEFT JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL
 LEFT JOIN usuarios u ON u.id=a.trabajador_id LEFT JOIN campanas c ON c.id=a.campana_id;

SELECT COUNT(*) AS lecturas_bateria FROM lecturas_bateria;
