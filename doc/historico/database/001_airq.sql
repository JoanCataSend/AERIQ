-- AirQ: ampliación aditiva sobre las tablas de cuentas existentes de Plesk.
-- Importar DESPUÉS de restaurar el esquema original. No borra ni altera sus tablas.
CREATE TABLE IF NOT EXISTS AirqOrganizacion (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(160) NOT NULL, creada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS AirqAcceso (
 usuario_id BIGINT UNSIGNED PRIMARY KEY, organizacion_id BIGINT UNSIGNED NULL,
 rol ENUM('trabajador','analista','admin','comercial') NOT NULL DEFAULT 'trabajador',
 cambiar_clave TINYINT NOT NULL DEFAULT 0, activo TINYINT NOT NULL DEFAULT 1,
 INDEX(organizacion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS AirqCampana (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(160) NOT NULL, zona VARCHAR(200) NOT NULL,
 inicio DATE NOT NULL, fin DATE NOT NULL, activa TINYINT NOT NULL DEFAULT 1,
 publicar TINYINT NOT NULL DEFAULT 0, radio_m INT NOT NULL DEFAULT 30,
 umbral_medio DOUBLE NOT NULL DEFAULT 60, umbral_alto DOUBLE NOT NULL DEFAULT 100,
 unidad VARCHAR(12) NOT NULL DEFAULT 'ppb', INDEX(organizacion_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS AirqDispositivo (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(160) NOT NULL, direccion_ble VARCHAR(17) NOT NULL UNIQUE,
 trabajador_id BIGINT UNSIGNED NULL, campana_id BIGINT UNSIGNED NULL,
 activo TINYINT NOT NULL DEFAULT 1, simulado TINYINT NOT NULL DEFAULT 1,
 INDEX(organizacion_id), INDEX(trabajador_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS AirqLectura (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, evento CHAR(36) NOT NULL,
 dispositivo_id BIGINT UNSIGNED NOT NULL, campana_id BIGINT UNSIGNED NOT NULL,
 usuario_id BIGINT UNSIGNED NOT NULL, tipo INT NOT NULL, valor DOUBLE NOT NULL,
 unidad VARCHAR(12) NOT NULL, capturada_en DATETIME(3) NOT NULL,
 recibida_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 latitud DOUBLE NULL, longitud DOUBLE NULL, precision_m DOUBLE NULL,
 contador INT NOT NULL, rssi INT NOT NULL, simulado TINYINT NOT NULL,
 valida TINYINT NOT NULL DEFAULT 1,
 UNIQUE(dispositivo_id,evento), INDEX(campana_id,capturada_en), INDEX(dispositivo_id,tipo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS AirqSolicitud (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 servicio ENUM('administracion','empresa','estudio') NOT NULL,
 entidad VARCHAR(180) NOT NULL, contacto VARCHAR(160) NOT NULL,
 email VARCHAR(254) NOT NULL, telefono VARCHAR(40) NOT NULL DEFAULT '',
 cantidad INT NULL, zona VARCHAR(200) NOT NULL, superficie DOUBLE NULL,
 duracion VARCHAR(100) NOT NULL DEFAULT '', necesidades TEXT NOT NULL,
 estado ENUM('pendiente','en revision','respondida','cerrada') NOT NULL DEFAULT 'pendiente',
 recibida_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX(estado,recibida_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
