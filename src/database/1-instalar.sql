-- =====================================================================
-- AERIQ · 1-instalar.sql
-- Instala la base de datos completa de AERIQ en la base que tengas
-- seleccionada (phpMyAdmin → elige la base → pestaña Importar).
-- No lleva el nombre de la base dentro: sirve igual para mcalgar2_pbio,
-- jcatsen_pbio o cualquier otra.
--
-- ⚠ BORRA todas las tablas y vistas de esa base antes de instalar.
--
-- Crea: 29 tablas, 4 vistas, los catálogos (roles, permisos, magnitudes,
-- servicios…), la organización «AERIQ Gandia» y el admin principal
--   gandia@gmail.com / 1234  (cámbiala tras el primer acceso).
-- Después, si quieres datos de prueba, importa 2-simulacion.sql.
-- =====================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ------------------------------------------------------------ 1. Vaciar la base
-- Los IDs de cuenta siguen creciendo entre reinstalaciones: así una sesión
-- antigua del navegador o del móvil nunca coincide con una cuenta nueva.
SELECT GREATEST(UNIX_TIMESTAMP(), COALESCE(MAX(AUTO_INCREMENT), 1)) INTO @aeriq_next_user
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('usuarios', 'AeriqUsuario', '_reinicio_aeriq');

SET @aeriq_old_fk = @@FOREIGN_KEY_CHECKS;
SET @aeriq_old_concat = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 16777216;
SET FOREIGN_KEY_CHECKS = 0;
-- Primero las vistas y después las tablas (con sus triggers), también las de versiones anteriores.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'VIEW';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP VIEW IF EXISTS ', @aeriq_objects));
PREPARE aeriq_stmt FROM @aeriq_sql; EXECUTE aeriq_stmt; DEALLOCATE PREPARE aeriq_stmt;
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP TABLE IF EXISTS ', @aeriq_objects));
PREPARE aeriq_stmt FROM @aeriq_sql; EXECUTE aeriq_stmt; DEALLOCATE PREPARE aeriq_stmt;
SET FOREIGN_KEY_CHECKS = @aeriq_old_fk;
SET SESSION group_concat_max_len = @aeriq_old_concat;

-- ------------------------------------------------------------ 2. Estructura y catálogos
-- AERIQ · Esquema único para instalaciones nuevas (MariaDB 10.4+).
-- No contiene DROP DATABASE, cuentas reales ni datos de producción.
-- Las decisiones de diseño están explicadas en doc/base-de-datos.md.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================ Catálogos
-- Tablas pequeñas que sustituyen a los ENUM: añadir un rol, un permiso,
-- un servicio o un estado es un INSERT, no un ALTER TABLE.

CREATE TABLE roles (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 descripcion VARCHAR(255) NOT NULL DEFAULT '',
 requiere_organizacion BOOLEAN NOT NULL DEFAULT TRUE,
 asignable BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Admin principal: crea campañas y nombra a su admin, sigue su estado y atiende las solicitudes.
-- Admin de campaña: una sola campaña; crea operadores, registra y asigna los AERIQ y exporta CSV.
-- Operador (código «trabajador»): pertenece a una campaña y mide con la app.
INSERT INTO roles (id,codigo,nombre,descripcion,requiere_organizacion,asignable) VALUES
 (1,'visitante','Visitante','Cuenta sin organización. Solo consulta pública.',0,0),
 (2,'trabajador','Operador','Mide con los AERIQ que tiene asignados en su campaña.',1,1),
 (3,'admin_campana','Admin de campaña','Gestiona una campaña: crea operadores, registra los AERIQ y exporta CSV.',1,1),
 (4,'admin','Administrador principal','Crea campañas y nombra a su admin, sigue su estado y atiende las solicitudes.',1,0);

CREATE TABLE permisos (
 id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(60) NOT NULL UNIQUE,
 descripcion VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- El id 3 (organizacion.gestionar) ya no existe: se sustituyó por campanas.gestionar y equipo.gestionar.
INSERT INTO permisos (id,codigo,descripcion) VALUES
 (1,'mapa.organizacion','Ver el mapa privado de su organización.'),
 (2,'informes.ver','Ver el panel, los informes y exportar los datos de toda la organización.'),
 (4,'mediciones.registrar','Recibir el AERIQ que le asigna su admin y enviar lecturas desde Android.'),
 (5,'solicitudes.gestionar','Ver las solicitudes comerciales y cambiar su estado.'),
 (6,'campanas.gestionar','Crear campañas y los administradores de cada campaña.'),
 (7,'equipo.gestionar','Crear operadores y registrar y asignar los AERIQ de su campaña.'),
 (8,'datos.exportar','Exportar a CSV las lecturas de su campaña.');

CREATE TABLE rol_permisos (
 rol_id TINYINT UNSIGNED NOT NULL,
 permiso_id SMALLINT UNSIGNED NOT NULL,
 PRIMARY KEY (rol_id,permiso_id),
 FOREIGN KEY (rol_id) REFERENCES roles(id) ON DELETE CASCADE,
 FOREIGN KEY (permiso_id) REFERENCES permisos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO rol_permisos (rol_id,permiso_id) VALUES
 (2,1),(2,4),
 (3,1),(3,2),(3,7),(3,8),
 (4,5),(4,6);

-- El id coincide con el código de tipo del anuncio BLE del AERIQ (14 = O3, 12 = temperatura):
-- es una clave natural fijada por el firmware, por eso no es AUTO_INCREMENT.
-- valor_minimo/valor_maximo: rango admitido (HU35); fuera de él la lectura se guarda como no válida.
-- salto_maximo: diferencia máxima con la media de las 5 lecturas anteriores; si la supera, es anómala.
CREATE TABLE magnitudes (
 id INT PRIMARY KEY, nombre VARCHAR(60) NOT NULL, unidad VARCHAR(16) NOT NULL,
 valor_minimo DOUBLE NOT NULL, valor_maximo DOUBLE NOT NULL,
 umbral_medio_defecto DOUBLE NULL, umbral_alto_defecto DOUBLE NULL,
 salto_maximo DOUBLE NULL,
 CHECK (valor_maximo >= valor_minimo), CONSTRAINT magnitud_salto CHECK (salto_maximo IS NULL OR salto_maximo > 0),
 CHECK ((umbral_medio_defecto IS NULL AND umbral_alto_defecto IS NULL) OR
        (umbral_medio_defecto >= 0 AND umbral_alto_defecto > umbral_medio_defecto))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO magnitudes (id,nombre,unidad,valor_minimo,valor_maximo,umbral_medio_defecto,umbral_alto_defecto,salto_maximo) VALUES
 (14,'Ozono','ppb',0,500,60,100,100),
 (12,'Temperatura','°C',-20,70,NULL,NULL,10);

CREATE TABLE modelos_dispositivo (
 id SMALLINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(40) NOT NULL UNIQUE,
 nombre VARCHAR(120) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO modelos_dispositivo (id,codigo,nombre) VALUES
 (1,'orb-v1','AERIQ v1 · nRF52840 (ozono y temperatura)');

-- Qué magnitudes puede enviar cada modelo; la ingesta lo comprueba.
CREATE TABLE modelo_magnitudes (
 modelo_id SMALLINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL,
 PRIMARY KEY (modelo_id,magnitud_id),
 FOREIGN KEY (modelo_id) REFERENCES modelos_dispositivo(id) ON DELETE CASCADE,
 FOREIGN KEY (magnitud_id) REFERENCES magnitudes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO modelo_magnitudes (modelo_id,magnitud_id) VALUES (1,12),(1,14);

CREATE TABLE servicios (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 descripcion VARCHAR(255) NOT NULL DEFAULT '',
 mide_superficie BOOLEAN NOT NULL DEFAULT FALSE,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Qué le interesa al cliente. El número de AERIQ lo recomienda Código Norte.
INSERT INTO servicios (id,codigo,nombre,descripcion,mide_superficie,orden) VALUES
 (1,'kits','Comprar AERIQ para mi organización','Sensores propios con app y panel. Te recomendamos cuántos necesitas.',0,1),
 (2,'estudio','Contratar un estudio de ozono','Medimos nosotros la zona durante un tiempo, sin comprar sensores, y entregamos un informe.',1,2),
 (3,'asesoramiento','No lo tengo claro, aconsejadme','Cuéntanos tu caso y te proponemos la opción que mejor encaja.',0,3);

-- Quién hace la solicitud, independiente de lo que pide.
CREATE TABLE tipos_organizacion (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO tipos_organizacion (id,codigo,nombre,orden) VALUES
 (1,'administracion','Administración pública',1),
 (2,'empresa','Empresa',2),
 (3,'otra','Otra entidad (centro educativo, asociación…)',3);

-- Para qué quiere medir el ozono. Ayuda a recomendar la solución.
CREATE TABLE objetivos_medicion (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(80) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL DEFAULT 0,
 activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO objetivos_medicion (id,codigo,nombre,orden) VALUES
 (1,'espacios_publicos','Calidad del aire en espacios públicos',1),
 (2,'salud_laboral','Salud y seguridad laboral',2),
 (3,'normativa','Informe técnico o normativa',3),
 (4,'investigacion','Investigación o divulgación',4),
 (5,'otro','Otro',5);

CREATE TABLE estados_solicitud (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL,
 final BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO estados_solicitud (id,codigo,nombre,orden,final) VALUES
 (1,'pendiente','Pendiente',1,0),
 (2,'en_revision','En revisión',2,0),
 (3,'respondida','Respondida',3,0),
 (4,'cerrada','Cerrada',4,1);

-- ============================================================ Organizaciones y cuentas

CREATE TABLE organizaciones (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 nombre VARCHAR(160) NOT NULL,
 creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY organizacion_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Una cuenta pertenece como máximo a una organización y tiene un rol.
-- Que los roles con requiere_organizacion tengan organización lo comprueba la aplicación.
CREATE TABLE usuarios (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NULL,
 rol_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
 campana_id BIGINT UNSIGNED NULL,
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
 FOREIGN KEY (rol_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Accesos con proveedores externos (hoy Firebase/Google). Una fila por proveedor y cuenta.
CREATE TABLE identidades_externas (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 usuario_id BIGINT UNSIGNED NOT NULL,
 proveedor VARCHAR(30) NOT NULL,
 uid_externo VARCHAR(128) NOT NULL,
 vinculada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY identidad_unica (proveedor,uid_externo),
 UNIQUE KEY una_por_proveedor (usuario_id,proveedor),
 FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Campañas y flota

-- Zonas reutilizables por organización: los informes filtran por zona sin depender de cómo se escribió.
CREATE TABLE zonas (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(200) NOT NULL,
 UNIQUE KEY zona_nombre (organizacion_id,nombre),
 UNIQUE KEY zona_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE campanas (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 zona_id BIGINT UNSIGNED NOT NULL,
 nombre VARCHAR(160) NOT NULL,
 inicio DATE NOT NULL, fin DATE NOT NULL,
 activa BOOLEAN NOT NULL DEFAULT TRUE, publicar BOOLEAN NOT NULL DEFAULT FALSE,
 radio_m INT NOT NULL DEFAULT 30,
 UNIQUE KEY campana_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id),
 FOREIGN KEY (zona_id,organizacion_id) REFERENCES zonas(id,organizacion_id),
 CHECK (fin >= inicio), CHECK (radio_m BETWEEN 20 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Cada cuenta pertenece a una campaña de SU organización (NULL = admin principal, que ve todas).
ALTER TABLE usuarios ADD CONSTRAINT usuario_campana FOREIGN KEY (campana_id,organizacion_id) REFERENCES campanas(id,organizacion_id);

-- Umbrales propios de una campaña. Sin fila, se usan los de referencia de la magnitud.
CREATE TABLE campana_umbrales (
 campana_id BIGINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL,
 umbral_medio DOUBLE NOT NULL, umbral_alto DOUBLE NOT NULL,
 PRIMARY KEY (campana_id,magnitud_id),
 FOREIGN KEY (campana_id) REFERENCES campanas(id) ON DELETE CASCADE,
 FOREIGN KEY (magnitud_id) REFERENCES magnitudes(id),
 CHECK (umbral_medio >= 0 AND umbral_alto > umbral_medio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dispositivos (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, organizacion_id BIGINT UNSIGNED NOT NULL,
 modelo_id SMALLINT UNSIGNED NOT NULL DEFAULT 1,
 nombre VARCHAR(160) NOT NULL, direccion_ble VARCHAR(17) NULL UNIQUE,
 activo BOOLEAN NOT NULL DEFAULT TRUE, simulado BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE KEY dispositivo_organizacion (id,organizacion_id),
 FOREIGN KEY (organizacion_id) REFERENCES organizaciones(id),
 FOREIGN KEY (modelo_id) REFERENCES modelos_dispositivo(id)
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

-- Nivel de batería que informa el propio AERIQ (medidor MAX17048) a través de la app.
-- No es una medición ambiental: pertenece al AERIQ, no a una campaña, por eso no va en mediciones.
CREATE TABLE lecturas_bateria (
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

-- ============================================================ Mediciones

CREATE TABLE mediciones (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 evento CHAR(36) NOT NULL, asignacion_id BIGINT UNSIGNED NOT NULL,
 magnitud_id INT NOT NULL, valor DOUBLE NOT NULL,
 capturada_en DATETIME(3) NOT NULL,
 recibida_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 latitud DOUBLE NULL, longitud DOUBLE NULL, precision_m DOUBLE NULL,
 contador TINYINT UNSIGNED NULL, rssi TINYINT NULL,
 simulado BOOLEAN NOT NULL, valida BOOLEAN NOT NULL DEFAULT TRUE,
 -- HU35: por qué no es válida. Se guarda para dejar constancia; mapas, informes, historial y CSV usan valida=1.
 motivo_descarte VARCHAR(20) NULL,
 UNIQUE KEY evento_unico (evento),
 FOREIGN KEY (asignacion_id) REFERENCES asignaciones(id),
 FOREIGN KEY (magnitud_id) REFERENCES magnitudes(id),
 CHECK ((latitud IS NULL AND longitud IS NULL AND precision_m IS NULL) OR
   (latitud IS NOT NULL AND longitud IS NOT NULL AND precision_m IS NOT NULL AND
    latitud BETWEEN -90 AND 90 AND longitud BETWEEN -180 AND 180 AND precision_m BETWEEN 0 AND 10000)),
 CHECK (rssi BETWEEN -127 AND 20),
 CONSTRAINT medicion_descarte CHECK ((valida = 1 AND motivo_descarte IS NULL) OR (valida = 0 AND motivo_descarte IN ('fuera_de_rango','anomala'))),
 INDEX (capturada_en), INDEX (asignacion_id,magnitud_id,capturada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Solicitudes comerciales

-- Qué pide (servicio), quién lo pide (tipo de organización) y dónde.
-- No se pide cantidad de AERIQ: la recomienda Código Norte. Superficie, duración e
-- inicio solo aplican a estudios (servicios.mide_superficie); la superficie es
-- opcional porque el cliente no suele saberla. tipo_organizacion_id NULL = solicitud
-- anterior a v3 en la que no se preguntaba.
CREATE TABLE solicitudes (
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
 FOREIGN KEY (servicio_id) REFERENCES servicios(id),
 FOREIGN KEY (tipo_organizacion_id) REFERENCES tipos_organizacion(id),
 FOREIGN KEY (estado_id) REFERENCES estados_solicitud(id),
 CHECK (superficie IS NULL OR superficie > 0),
 INDEX (estado_id,recibida_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Objetivos marcados en cada solicitud (uno o varios).
CREATE TABLE solicitud_objetivos (
 solicitud_id BIGINT UNSIGNED NOT NULL,
 objetivo_id TINYINT UNSIGNED NOT NULL,
 PRIMARY KEY (solicitud_id,objetivo_id),
 FOREIGN KEY (solicitud_id) REFERENCES solicitudes(id) ON DELETE CASCADE,
 FOREIGN KEY (objetivo_id) REFERENCES objetivos_medicion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Quién cambió cada solicitud de estado y cuándo. usuario_id NULL = formulario público.
CREATE TABLE solicitudes_historial (
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

-- ============================================================ Avisos entre personas (HU36)
-- Conversaciones tipo solicitud entre dos cuentas de la misma organización: el admin principal con
-- admins y operadores; el admin de campaña con los operadores de su campaña (y con el admin principal).
-- Quién puede escribir a quién lo decide el servidor (server/Avisos.php), no el formulario.
CREATE TABLE estados_aviso (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL,
 final BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO estados_aviso (id,codigo,nombre,orden,final) VALUES
 (1,'abierto','Abierto',1,0),
 (2,'respondido','Respondido',2,0),
 (3,'cerrado','Cerrado',3,1);

CREATE TABLE avisos (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 organizacion_id BIGINT UNSIGNED NOT NULL,
 creado_por BIGINT UNSIGNED NOT NULL,
 destinatario_id BIGINT UNSIGNED NOT NULL,
 asunto VARCHAR(160) NOT NULL,
 estado_id TINYINT UNSIGNED NOT NULL DEFAULT 1,
 creado_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 actualizado_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 -- Última vez que cada parte abrió la conversación: lo que llegó después es «nuevo».
 visto_creador_en DATETIME(3) NULL,
 visto_destinatario_en DATETIME(3) NULL,
 FOREIGN KEY (creado_por,organizacion_id) REFERENCES usuarios(id,organizacion_id),
 FOREIGN KEY (destinatario_id,organizacion_id) REFERENCES usuarios(id,organizacion_id),
 FOREIGN KEY (estado_id) REFERENCES estados_aviso(id),
 CHECK (creado_por <> destinatario_id),
 INDEX (creado_por,actualizado_en), INDEX (destinatario_id,actualizado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE aviso_mensajes (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 aviso_id BIGINT UNSIGNED NOT NULL,
 autor_id BIGINT UNSIGNED NOT NULL,
 texto TEXT NOT NULL,
 creado_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY (aviso_id) REFERENCES avisos(id) ON DELETE CASCADE,
 FOREIGN KEY (autor_id) REFERENCES usuarios(id),
 INDEX (aviso_id,creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================ Seguridad técnica
-- proposito sigue siendo ENUM: es un conjunto cerrado que solo cambia junto con el código.

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

-- ============================================================ Vistas
-- Para leer los datos desde phpMyAdmin con nombres, sin memorizar JOIN.

CREATE VIEW vista_mediciones AS
 SELECT m.id,m.capturada_en AS fecha_utc,o.nombre AS organizacion,
 c.nombre AS campana,z.nombre AS zona,d.nombre AS dispositivo,
 CONCAT(u.nombre,' ',u.apellidos) AS trabajador,
 t.nombre AS magnitud,m.valor,t.unidad,m.latitud,m.longitud,
 IF(m.simulado,'Simulado','Real') AS origen,
 IF(m.valida,'Válida',CONCAT('Descartada: ',CASE m.motivo_descarte WHEN 'fuera_de_rango' THEN 'fuera de rango' WHEN 'anomala' THEN 'anómala' ELSE 'sin motivo' END)) AS validacion
 FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id
 JOIN organizaciones o ON o.id=a.organizacion_id JOIN campanas c ON c.id=a.campana_id
 JOIN zonas z ON z.id=c.zona_id
 JOIN dispositivos d ON d.id=a.dispositivo_id JOIN usuarios u ON u.id=a.trabajador_id
 JOIN magnitudes t ON t.id=m.magnitud_id;

CREATE VIEW vista_flota AS
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

CREATE VIEW vista_usuarios AS
 SELECT u.id,CONCAT(u.nombre,' ',u.apellidos) AS nombre,u.email,
 o.nombre AS organizacion,r.nombre AS rol,
 CASE WHEN u.debe_cambiar_clave THEN 'Invitación pendiente'
      WHEN u.activo THEN 'Activa' ELSE 'Desactivada' END AS estado,
 (SELECT GROUP_CONCAT(i.proveedor) FROM identidades_externas i WHERE i.usuario_id=u.id) AS accesos_externos
 FROM usuarios u JOIN roles r ON r.id=u.rol_id
 LEFT JOIN organizaciones o ON o.id=u.organizacion_id;

CREATE VIEW vista_solicitudes AS
 SELECT s.id,s.recibida_en,sv.nombre AS servicio,t.nombre AS tipo_organizacion,e.nombre AS estado,
 s.entidad,s.contacto,s.email,s.telefono,s.municipio,s.provincia,s.zona,s.superficie,s.duracion,s.inicio_deseado,
 (SELECT GROUP_CONCAT(o.nombre ORDER BY o.orden SEPARATOR ', ') FROM solicitud_objetivos so
  JOIN objetivos_medicion o ON o.id=so.objetivo_id WHERE so.solicitud_id=s.id) AS objetivos,
 (SELECT MAX(h.cambiado_en) FROM solicitudes_historial h WHERE h.solicitud_id=s.id) AS ultimo_cambio
 FROM solicitudes s JOIN servicios sv ON sv.id=s.servicio_id
 LEFT JOIN tipos_organizacion t ON t.id=s.tipo_organizacion_id
 JOIN estados_solicitud e ON e.id=s.estado_id;

SET @aeriq_sql = CONCAT('ALTER TABLE usuarios AUTO_INCREMENT = ', @aeriq_next_user);
PREPARE aeriq_stmt FROM @aeriq_sql; EXECUTE aeriq_stmt; DEALLOCATE PREPARE aeriq_stmt;

-- ------------------------------------------------------------ 3. Organización y admin principal
INSERT INTO organizaciones (nombre) VALUES ('AERIQ Gandia');
-- Contraseña inicial: 1234 (hash bcrypt).
INSERT INTO usuarios (organizacion_id, rol_id, nombre, apellidos, email, password_hash, verificado_en)
SELECT o.id, r.id, 'Marina', 'Ferrer', 'gandia@gmail.com', '$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2', UTC_TIMESTAMP()
FROM organizaciones o JOIN roles r ON r.codigo = 'admin' WHERE o.nombre = 'AERIQ Gandia';

-- ------------------------------------------------------------ Resumen
SELECT DATABASE() AS base_instalada, (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()) AS tablas_y_vistas;
SELECT r.nombre AS rol, GROUP_CONCAT(p.codigo ORDER BY p.id SEPARATOR ', ') AS permisos
FROM roles r LEFT JOIN rol_permisos rp ON rp.rol_id = r.id LEFT JOIN permisos p ON p.id = rp.permiso_id
GROUP BY r.id ORDER BY r.id;
SELECT email, nombre, apellidos FROM usuarios;
