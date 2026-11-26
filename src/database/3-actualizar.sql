-- =====================================================================
-- AERIQ · 3-actualizar.sql
-- Pone al día una base que ya tiene datos, SIN borrar nada, con los cambios
-- de estructura posteriores a 1-instalar.sql (noviembre de 2026):
--   · HU35: rangos realistas y salto máximo por magnitud, y motivo de
--     descarte en las mediciones (las descartadas se guardan como no válidas).
--   · HU36: tablas de avisos entre personas (estados_aviso, avisos,
--     aviso_mensajes).
-- Se importa en phpMyAdmin con la base seleccionada. Se puede repetir.
-- En una instalación nueva no hace falta: 1-instalar.sql ya lo incluye.
-- =====================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ------------------------------------------------------------ HU35 · Mediciones descartadas
ALTER TABLE magnitudes ADD COLUMN IF NOT EXISTS salto_maximo DOUBLE NULL AFTER umbral_alto_defecto;
ALTER TABLE magnitudes DROP CONSTRAINT IF EXISTS magnitud_salto;
ALTER TABLE magnitudes ADD CONSTRAINT magnitud_salto CHECK (salto_maximo IS NULL OR salto_maximo > 0);
UPDATE magnitudes SET valor_minimo=0,valor_maximo=500,salto_maximo=100 WHERE id=14;
UPDATE magnitudes SET valor_minimo=-20,valor_maximo=70,salto_maximo=10 WHERE id=12;
ALTER TABLE mediciones ADD COLUMN IF NOT EXISTS motivo_descarte VARCHAR(20) NULL AFTER valida;
-- Las lecturas guardadas como válidas que quedan fuera del rango nuevo (p. ej. 900 ppb de un sensor sin calibrar)
-- pasan a no válidas con su motivo: no se borran, pero dejan de salir en mapas e informes, como las nuevas.
UPDATE mediciones m JOIN magnitudes t ON t.id=m.magnitud_id SET m.valida=0,m.motivo_descarte='fuera_de_rango'
 WHERE m.valida=1 AND (m.valor<t.valor_minimo OR m.valor>t.valor_maximo);
-- Las lecturas no válidas anteriores no tenían motivo: se marcan como fuera de rango si lo están.
UPDATE mediciones m JOIN magnitudes t ON t.id=m.magnitud_id SET m.motivo_descarte='fuera_de_rango'
 WHERE m.valida=0 AND m.motivo_descarte IS NULL AND (m.valor<t.valor_minimo OR m.valor>t.valor_maximo);
UPDATE mediciones SET motivo_descarte='anomala' WHERE valida=0 AND motivo_descarte IS NULL;
ALTER TABLE mediciones DROP CONSTRAINT IF EXISTS medicion_descarte;
ALTER TABLE mediciones ADD CONSTRAINT medicion_descarte CHECK ((valida = 1 AND motivo_descarte IS NULL) OR (valida = 0 AND motivo_descarte IN ('fuera_de_rango','anomala')));
CREATE OR REPLACE VIEW vista_mediciones AS
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

-- ------------------------------------------------------------ HU36 · Avisos entre personas
-- ============================================================ Avisos entre personas (HU36)
-- Conversaciones tipo solicitud entre dos cuentas de la misma organización: el admin principal con
-- admins y operadores; el admin de campaña con los operadores de su campaña (y con el admin principal).
-- Quién puede escribir a quién lo decide el servidor (server/Avisos.php), no el formulario.
CREATE TABLE IF NOT EXISTS estados_aviso (
 id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 codigo VARCHAR(30) NOT NULL UNIQUE,
 nombre VARCHAR(60) NOT NULL,
 orden TINYINT UNSIGNED NOT NULL,
 final BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO estados_aviso (id,codigo,nombre,orden,final) VALUES
 (1,'abierto','Abierto',1,0),
 (2,'respondido','Respondido',2,0),
 (3,'cerrado','Cerrado',3,1);

CREATE TABLE IF NOT EXISTS avisos (
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

CREATE TABLE IF NOT EXISTS aviso_mensajes (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 aviso_id BIGINT UNSIGNED NOT NULL,
 autor_id BIGINT UNSIGNED NOT NULL,
 texto TEXT NOT NULL,
 creado_en DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 FOREIGN KEY (aviso_id) REFERENCES avisos(id) ON DELETE CASCADE,
 FOREIGN KEY (autor_id) REFERENCES usuarios(id),
 INDEX (aviso_id,creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


SELECT 'Base actualizada' AS resultado,
 (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE') AS tablas;
