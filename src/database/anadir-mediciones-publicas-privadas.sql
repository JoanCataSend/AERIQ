-- AERIQ: anadir 36 lecturas SIMULADAS sin borrar datos existentes.
-- Requiere el esquema v2 (schema.sql o migracion-v2-catalogos.sql) y las cuentas demo. Importar en phpMyAdmin.
-- Puede repetirse: actualiza las fechas de estos ejemplos, sin duplicarlos.
USE `jcatsen_pbio`;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- Demo publica - paseo del campus: visible para cualquier visitante.
SET @demo_user = (SELECT id FROM usuarios WHERE email='operador@gandia.example' AND rol_id=(SELECT id FROM roles WHERE codigo='trabajador') LIMIT 1);
SET @demo_org = (SELECT organizacion_id FROM usuarios WHERE id=@demo_user);
INSERT IGNORE INTO zonas(organizacion_id,nombre) SELECT @demo_org,'Gandia - demostracion' WHERE @demo_org IS NOT NULL;
INSERT INTO campanas(organizacion_id,zona_id,nombre,inicio,fin,publicar)
SELECT @demo_org,(SELECT id FROM zonas WHERE organizacion_id=@demo_org AND nombre='Gandia - demostracion'),'Demo publica - paseo del campus',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),1
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo publica - paseo del campus');
SET @demo_campaign = (SELECT MIN(id) FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo publica - paseo del campus');
INSERT INTO dispositivos(organizacion_id,nombre,simulado)
SELECT @demo_org,'Orb demo publica',1
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dispositivos WHERE organizacion_id=@demo_org AND nombre='Orb demo publica' AND simulado=1 AND direccion_ble IS NULL);
SET @demo_device = (SELECT MIN(id) FROM dispositivos WHERE organizacion_id=@demo_org AND nombre='Orb demo publica' AND simulado=1 AND direccion_ble IS NULL);
INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
SELECT @demo_org,@demo_device,@demo_user,@demo_campaign,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)
WHERE @demo_device IS NOT NULL AND @demo_campaign IS NOT NULL AND NOT EXISTS(SELECT 1 FROM asignaciones WHERE dispositivo_id=@demo_device AND finalizada_en IS NULL);
SET @demo_assignment = (SELECT MIN(id) FROM asignaciones WHERE dispositivo_id=@demo_device AND trabajador_id=@demo_user AND campana_id=@demo_campaign AND finalizada_en IS NULL);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000100',@demo_assignment,14,42,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 240 MINUTE),38.996651,-0.166009,5,1,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000101',@demo_assignment,14,49,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 220 MINUTE),38.996711,-0.166009,5,2,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000102',@demo_assignment,14,56,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 200 MINUTE),38.996771,-0.166009,5,3,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000103',@demo_assignment,14,63,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 180 MINUTE),38.996651,-0.165929,5,4,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000104',@demo_assignment,14,70,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 160 MINUTE),38.996711,-0.165929,5,5,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000105',@demo_assignment,14,77,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 140 MINUTE),38.996771,-0.165929,5,6,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000106',@demo_assignment,14,84,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 120 MINUTE),38.996651,-0.166009,5,7,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000107',@demo_assignment,14,91,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 100 MINUTE),38.996711,-0.166009,5,8,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000108',@demo_assignment,14,98,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 80 MINUTE),38.996771,-0.166009,5,9,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000109',@demo_assignment,14,105,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 60 MINUTE),38.996651,-0.165929,5,10,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000110',@demo_assignment,14,43,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 40 MINUTE),38.996711,-0.165929,5,11,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000111',@demo_assignment,14,50,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE),38.996771,-0.165929,5,12,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);

-- Demo privada - recinto municipal: solo organizacion y trabajador asignado.
SET @demo_user = (SELECT id FROM usuarios WHERE email='operador@gandia.example' AND rol_id=(SELECT id FROM roles WHERE codigo='trabajador') LIMIT 1);
SET @demo_org = (SELECT organizacion_id FROM usuarios WHERE id=@demo_user);
INSERT IGNORE INTO zonas(organizacion_id,nombre) SELECT @demo_org,'Gandia - demostracion' WHERE @demo_org IS NOT NULL;
INSERT INTO campanas(organizacion_id,zona_id,nombre,inicio,fin,publicar)
SELECT @demo_org,(SELECT id FROM zonas WHERE organizacion_id=@demo_org AND nombre='Gandia - demostracion'),'Demo privada - recinto municipal',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),0
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo privada - recinto municipal');
SET @demo_campaign = (SELECT MIN(id) FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo privada - recinto municipal');
INSERT INTO dispositivos(organizacion_id,nombre,simulado)
SELECT @demo_org,'Orb demo privada-gandia',1
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dispositivos WHERE organizacion_id=@demo_org AND nombre='Orb demo privada-gandia' AND simulado=1 AND direccion_ble IS NULL);
SET @demo_device = (SELECT MIN(id) FROM dispositivos WHERE organizacion_id=@demo_org AND nombre='Orb demo privada-gandia' AND simulado=1 AND direccion_ble IS NULL);
INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
SELECT @demo_org,@demo_device,@demo_user,@demo_campaign,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)
WHERE @demo_device IS NOT NULL AND @demo_campaign IS NOT NULL AND NOT EXISTS(SELECT 1 FROM asignaciones WHERE dispositivo_id=@demo_device AND finalizada_en IS NULL);
SET @demo_assignment = (SELECT MIN(id) FROM asignaciones WHERE dispositivo_id=@demo_device AND trabajador_id=@demo_user AND campana_id=@demo_campaign AND finalizada_en IS NULL);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000200',@demo_assignment,14,65,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 240 MINUTE),38.997100,-0.165500,5,1,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000201',@demo_assignment,14,72,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 220 MINUTE),38.997160,-0.165500,5,2,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000202',@demo_assignment,14,79,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 200 MINUTE),38.997220,-0.165500,5,3,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000203',@demo_assignment,14,86,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 180 MINUTE),38.997100,-0.165420,5,4,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000204',@demo_assignment,14,93,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 160 MINUTE),38.997160,-0.165420,5,5,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000205',@demo_assignment,14,100,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 140 MINUTE),38.997220,-0.165420,5,6,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000206',@demo_assignment,14,107,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 120 MINUTE),38.997100,-0.165500,5,7,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000207',@demo_assignment,14,114,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 100 MINUTE),38.997160,-0.165500,5,8,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000208',@demo_assignment,14,121,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 80 MINUTE),38.997220,-0.165500,5,9,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000209',@demo_assignment,14,128,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 60 MINUTE),38.997100,-0.165420,5,10,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000210',@demo_assignment,14,66,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 40 MINUTE),38.997160,-0.165420,5,11,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000211',@demo_assignment,14,73,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE),38.997220,-0.165420,5,12,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);

-- Demo privada - recinto de empresa: solo organizacion y trabajador asignado.
SET @demo_user = (SELECT id FROM usuarios WHERE email='operador@empresaverde.example' AND rol_id=(SELECT id FROM roles WHERE codigo='trabajador') LIMIT 1);
SET @demo_org = (SELECT organizacion_id FROM usuarios WHERE id=@demo_user);
INSERT IGNORE INTO zonas(organizacion_id,nombre) SELECT @demo_org,'Gandia - demostracion' WHERE @demo_org IS NOT NULL;
INSERT INTO campanas(organizacion_id,zona_id,nombre,inicio,fin,publicar)
SELECT @demo_org,(SELECT id FROM zonas WHERE organizacion_id=@demo_org AND nombre='Gandia - demostracion'),'Demo privada - recinto de empresa',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),0
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo privada - recinto de empresa');
SET @demo_campaign = (SELECT MIN(id) FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo privada - recinto de empresa');
INSERT INTO dispositivos(organizacion_id,nombre,simulado)
SELECT @demo_org,'Orb demo privada-empresa',1
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dispositivos WHERE organizacion_id=@demo_org AND nombre='Orb demo privada-empresa' AND simulado=1 AND direccion_ble IS NULL);
SET @demo_device = (SELECT MIN(id) FROM dispositivos WHERE organizacion_id=@demo_org AND nombre='Orb demo privada-empresa' AND simulado=1 AND direccion_ble IS NULL);
INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
SELECT @demo_org,@demo_device,@demo_user,@demo_campaign,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)
WHERE @demo_device IS NOT NULL AND @demo_campaign IS NOT NULL AND NOT EXISTS(SELECT 1 FROM asignaciones WHERE dispositivo_id=@demo_device AND finalizada_en IS NULL);
SET @demo_assignment = (SELECT MIN(id) FROM asignaciones WHERE dispositivo_id=@demo_device AND trabajador_id=@demo_user AND campana_id=@demo_campaign AND finalizada_en IS NULL);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000300',@demo_assignment,14,49,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 240 MINUTE),38.998000,-0.162500,5,1,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000301',@demo_assignment,14,56,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 220 MINUTE),38.998060,-0.162500,5,2,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000302',@demo_assignment,14,63,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 200 MINUTE),38.998120,-0.162500,5,3,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000303',@demo_assignment,14,70,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 180 MINUTE),38.998000,-0.162420,5,4,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000304',@demo_assignment,14,77,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 160 MINUTE),38.998060,-0.162420,5,5,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000305',@demo_assignment,14,84,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 140 MINUTE),38.998120,-0.162420,5,6,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000306',@demo_assignment,14,91,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 120 MINUTE),38.998000,-0.162500,5,7,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000307',@demo_assignment,14,98,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 100 MINUTE),38.998060,-0.162500,5,8,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000308',@demo_assignment,14,105,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 80 MINUTE),38.998120,-0.162500,5,9,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000309',@demo_assignment,14,112,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 60 MINUTE),38.998000,-0.162420,5,10,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000310',@demo_assignment,14,50,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 40 MINUTE),38.998060,-0.162420,5,11,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae100000-0000-4000-8000-000000000311',@demo_assignment,14,57,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 20 MINUTE),38.998120,-0.162420,5,12,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
COMMIT;
SELECT c.nombre AS campana,IF(c.publicar,'PUBLICA','PRIVADA') AS visibilidad,o.nombre AS organizacion,COUNT(*) AS mediciones_demo
FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN campanas c ON c.id=a.campana_id JOIN organizaciones o ON o.id=a.organizacion_id
WHERE m.evento LIKE 'ae100000-%' GROUP BY c.id,c.nombre,c.publicar,o.nombre;
-- Deben aparecer 3 campanas con 12 mediciones cada una.
