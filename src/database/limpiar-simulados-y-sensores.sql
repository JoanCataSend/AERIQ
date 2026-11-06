-- AERIQ: retirar todos los sensores/asignaciones y borrar las lecturas simuladas.
-- Conserva cuentas, organizaciones, campañas, sesiones, correo y solicitudes.
-- Las lecturas reales existentes se ARCHIVAN antes de retirar sus sensores.
-- Exportar una copia SQL y detener la captura Android antes de importar.
-- Seleccionar la base correcta en phpMyAdmin; NO ejecutar en information_schema.
-- No usar después seed-demo.sql ni recrear-demo*.sql: reintroducirían simulados.
SET NAMES utf8mb4;
ALTER TABLE dispositivos ALTER simulado SET DEFAULT 0;

-- CREATE LIKE no copia claves foráneas: archivo independiente del registro activo.
-- No se reinician AUTO_INCREMENT ni se desactivan comprobaciones de FK.
CREATE TABLE IF NOT EXISTS archivo_sensores_dispositivos LIKE dispositivos;
CREATE TABLE IF NOT EXISTS archivo_sensores_asignaciones LIKE asignaciones;
CREATE TABLE IF NOT EXISTS archivo_sensores_mediciones LIKE mediciones;

START TRANSACTION;
-- Bloqueo durante la limpieza; realizarla en una ventana sin captura/altas.
SELECT id FROM dispositivos ORDER BY id FOR UPDATE;
SELECT id FROM asignaciones ORDER BY id FOR UPDATE;

INSERT INTO archivo_sensores_dispositivos
SELECT d.* FROM dispositivos d
WHERE EXISTS (SELECT 1 FROM asignaciones a JOIN mediciones m ON m.asignacion_id=a.id WHERE a.dispositivo_id=d.id AND m.simulado=0)
AND NOT EXISTS (SELECT 1 FROM archivo_sensores_dispositivos x WHERE x.id=d.id);

-- Se omite la columna calculada dispositivo_activo.
INSERT INTO archivo_sensores_asignaciones
 (id,organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en,finalizada_en)
SELECT a.id,a.organizacion_id,a.dispositivo_id,a.trabajador_id,a.campana_id,a.iniciada_en,a.finalizada_en
FROM asignaciones a
WHERE EXISTS (SELECT 1 FROM mediciones m WHERE m.asignacion_id=a.id AND m.simulado=0)
AND NOT EXISTS (SELECT 1 FROM archivo_sensores_asignaciones x WHERE x.id=a.id);

INSERT INTO archivo_sensores_mediciones
SELECT m.* FROM mediciones m WHERE m.simulado=0
AND NOT EXISTS (SELECT 1 FROM archivo_sensores_mediciones x WHERE x.id=m.id);

-- Orden hijo -> padre. Las lecturas reales ya están conservadas en archivo.
DELETE FROM mediciones;
DELETE FROM asignaciones;
DELETE FROM dispositivos;
COMMIT;

SELECT COUNT(*) AS sensores_activos_restantes FROM dispositivos;
SELECT COUNT(*) AS lecturas_activas_restantes FROM mediciones;
SELECT COUNT(*) AS lecturas_reales_archivadas FROM archivo_sensores_mediciones;
SELECT COUNT(*) AS cuentas_conservadas FROM usuarios;
SELECT COUNT(*) AS organizaciones_conservadas FROM organizaciones;
