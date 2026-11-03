-- AERIQ · Esquema único para instalaciones nuevas (MariaDB 10.4+).
-- No contiene DROP DATABASE, cuentas reales ni datos de producción.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE organizaciones (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(160) NOT NULL,
 creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usuarios (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NULL,
 rol ENUM('visitante','trabajador','analista','admin','comercial') NOT NULL DEFAULT 'visitante',
 nombre VARCHAR(90) NOT NULL, apellidos VARCHAR(120) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE, telefono VARCHAR(40) NOT NULL DEFAULT '',
 password_hash VARCHAR(255) NOT NULL,
 activo BOOLEAN NOT NULL DEFAULT TRUE,
 debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE,
 invitacion_expira_en DATETIME NULL,
 verificado_en DATETIME NULL,
 creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY usuario_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id),
 CHECK (rol IN ('visitante','comercial') OR organizacion_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE campanas (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(160) NOT NULL, zona VARCHAR(200) NOT NULL,
 inicio DATE NOT NULL, fin DATE NOT NULL,
 activa BOOLEAN NOT NULL DEFAULT TRUE, publicar BOOLEAN NOT NULL DEFAULT FALSE,
 radio_m INT NOT NULL DEFAULT 30,
 umbral_medio DOUBLE NOT NULL DEFAULT 60, umbral_alto DOUBLE NOT NULL DEFAULT 100,
 UNIQUE KEY campana_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id),
 CHECK (fin >= inicio), CHECK (radio_m BETWEEN 20 AND 50),
 CHECK (umbral_medio >= 0 AND umbral_alto > umbral_medio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dispositivos (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(160) NOT NULL, direccion_ble VARCHAR(17) NULL UNIQUE,
 identificador_legacy VARCHAR(80) NULL,
 activo BOOLEAN NOT NULL DEFAULT TRUE, simulado BOOLEAN NOT NULL DEFAULT TRUE,
 UNIQUE KEY dispositivo_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Un cambio de asignación cierra la anterior; no cambia la autoría del histórico.
CREATE TABLE asignaciones (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 dispositivo_id BIGINT UNSIGNED NOT NULL, trabajador_id BIGINT UNSIGNED NOT NULL,
 campana_id BIGINT UNSIGNED NOT NULL,
 iniciada_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), finalizada_en DATETIME(3) NULL,
 dispositivo_activo BIGINT UNSIGNED GENERATED ALWAYS AS
   (CASE WHEN finalizada_en IS NULL THEN dispositivo_id ELSE NULL END) STORED,
 UNIQUE KEY un_destino_actual (dispositivo_activo),
 FOREIGN KEY (dispositivo_id,organizacion_id) REFERENCES dispositivos(id,organizacion_id),
 FOREIGN KEY (trabajador_id,organizacion_id) REFERENCES usuarios(id,organizacion_id),
 FOREIGN KEY (campana_id,organizacion_id) REFERENCES campanas(id,organizacion_id),
 CHECK (finalizada_en IS NULL OR finalizada_en >= iniciada_en),
 INDEX (trabajador_id,finalizada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE magnitudes (
 id INT PRIMARY KEY, nombre VARCHAR(60) NOT NULL, unidad VARCHAR(16) NOT NULL,
 valor_minimo DOUBLE NOT NULL, valor_maximo DOUBLE NOT NULL,
 CHECK (valor_maximo >= valor_minimo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO magnitudes VALUES (14,'Ozono','ppb',0,65535),(12,'Temperatura','°C',-32768,32767);

CREATE TABLE mediciones (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 evento CHAR(36) NOT NULL, asignacion_id BIGINT UNSIGNED NOT NULL,
 tipo INT NOT NULL, valor DOUBLE NOT NULL,
 capturada_en DATETIME(3) NOT NULL,
 recibida_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 latitud DOUBLE NULL, longitud DOUBLE NULL, precision_m DOUBLE NULL,
 contador INT NULL, rssi INT NULL,
 simulado BOOLEAN NOT NULL, valida BOOLEAN NOT NULL DEFAULT TRUE,
 origen_importacion VARCHAR(190) NULL UNIQUE,
 UNIQUE KEY evento_unico (evento),
 FOREIGN KEY (asignacion_id) REFERENCES asignaciones(id),
 FOREIGN KEY (tipo) REFERENCES magnitudes(id),
 CHECK ((latitud IS NULL AND longitud IS NULL AND precision_m IS NULL) OR
   (latitud IS NOT NULL AND longitud IS NOT NULL AND precision_m IS NOT NULL AND
    latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180 AND precision_m BETWEEN 0 AND 10000)),
 CHECK (contador BETWEEN 0 AND 255), CHECK (rssi BETWEEN -127 AND 20),
 INDEX (capturada_en), INDEX (asignacion_id,tipo,capturada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE solicitudes (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 servicio ENUM('administracion','empresa','estudio') NOT NULL,
 entidad VARCHAR(180) NOT NULL, contacto VARCHAR(160) NOT NULL,
 email VARCHAR(254) NOT NULL, telefono VARCHAR(40) NOT NULL DEFAULT '',
 cantidad INT NULL, zona VARCHAR(200) NOT NULL, superficie DOUBLE NULL,
 duracion VARCHAR(100) NOT NULL DEFAULT '', necesidades TEXT NOT NULL,
 consentimiento_en DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
 estado ENUM('pendiente','en revision','respondida','cerrada') NOT NULL DEFAULT 'pendiente',
 recibida_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK ((servicio='estudio' AND superficie > 0 AND cantidad IS NULL) OR
        (servicio<>'estudio' AND cantidad > 0 AND superficie IS NULL)),
 INDEX (estado,recibida_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE codigos_email (
 usuario_id BIGINT UNSIGNED NOT NULL, proposito ENUM('registro','recuperar') NOT NULL,
 codigo_hash VARCHAR(255) NOT NULL, vence_en DATETIME NOT NULL,
 enviado_en DATETIME NOT NULL, intentos INT NOT NULL DEFAULT 0,
 PRIMARY KEY (usuario_id,proposito), FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE sesiones_moviles (
 token_hash CHAR(64) PRIMARY KEY, usuario_id BIGINT UNSIGNED NOT NULL,
 vence_en DATETIME NOT NULL,
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
 INDEX (usuario_id,vence_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE limites_acceso (
 clave_hash CHAR(64) PRIMARY KEY, ventana_en DATETIME NOT NULL, intentos INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Vistas de lectura para entender los datos desde phpMyAdmin sin memorizar JOIN.
CREATE VIEW vista_mediciones AS
 SELECT m.id,m.capturada_en AS fecha_utc,o.nombre AS organizacion,
 c.nombre AS campana,c.zona,d.nombre AS dispositivo,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,
 t.nombre AS magnitud,m.valor,t.unidad,m.latitud,m.longitud,
 IF(m.simulado,'Simulado','Real') AS origen,
 IF(m.valida,'Válida','Pendiente o inválida') AS validacion
 FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id
 JOIN organizaciones o ON o.id=a.organizacion_id JOIN campanas c ON c.id=a.campana_id
 JOIN dispositivos d ON d.id=a.dispositivo_id JOIN usuarios u ON u.id=a.trabajador_id
 JOIN magnitudes t ON t.id=m.tipo;

CREATE VIEW vista_flota AS
 SELECT d.id,d.nombre,o.nombre AS organizacion,d.direccion_ble,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,c.nombre AS campana,
 IF(d.activo,'Alta','Baja administrativa') AS estado,
 IF(d.simulado,'Simulado','Real') AS origen
 FROM dispositivos d JOIN organizaciones o ON o.id=d.organizacion_id
 LEFT JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL
 LEFT JOIN usuarios u ON u.id=a.trabajador_id LEFT JOIN campanas c ON c.id=a.campana_id;
