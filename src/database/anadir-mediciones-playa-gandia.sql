-- AERIQ: 54 mediciones PUBLICAS SIMULADAS en el entorno de la playa de Gandia.
-- Posiciones ilustrativas, no son observaciones reales ni una evaluacion ambiental.
-- 18 verdes (<60 ppb), 18 naranjas (60 a <100), 18 rojas (>=100).
-- No borra datos ni modifica cuentas. Repetir actualiza las fechas sin duplicar.
-- Requiere el esquema actual y operador@gandia.example.
USE `jcatsen_pbio`;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;
SET @demo_user = (SELECT id FROM usuarios WHERE email='operador@gandia.example' AND rol='trabajador' LIMIT 1);
SET @demo_org = (SELECT organizacion_id FROM usuarios WHERE id=@demo_user);
INSERT INTO campanas(organizacion_id,nombre,zona,inicio,fin,publicar)
SELECT @demo_org,'Demo publica - playa de Gandia','Playa de Gandia - ubicaciones ilustrativas',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),1
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo publica - playa de Gandia');
SET @demo_campaign = (SELECT MIN(id) FROM campanas WHERE organizacion_id=@demo_org AND nombre='Demo publica - playa de Gandia');
INSERT INTO dispositivos(organizacion_id,nombre,identificador_legacy,simulado)
SELECT @demo_org,'Orb demo playa','aeriq-demo-playa-publica',1
WHERE @demo_org IS NOT NULL AND NOT EXISTS(SELECT 1 FROM dispositivos WHERE organizacion_id=@demo_org AND identificador_legacy='aeriq-demo-playa-publica');
SET @demo_device = (SELECT MIN(id) FROM dispositivos WHERE organizacion_id=@demo_org AND identificador_legacy='aeriq-demo-playa-publica');
INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
SELECT @demo_org,@demo_device,@demo_user,@demo_campaign,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)
WHERE @demo_device IS NOT NULL AND @demo_campaign IS NOT NULL AND NOT EXISTS(SELECT 1 FROM asignaciones WHERE dispositivo_id=@demo_device AND finalizada_en IS NULL);
SET @demo_assignment = (SELECT MIN(id) FROM asignaciones WHERE dispositivo_id=@demo_device AND trabajador_id=@demo_user AND campana_id=@demo_campaign AND finalizada_en IS NULL);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000001',@demo_assignment,14,35,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 90 MINUTE),38.999930,-0.158990,5,1,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000002',@demo_assignment,14,36,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 89 MINUTE),38.999930,-0.158900,5,2,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000003',@demo_assignment,14,37,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 88 MINUTE),38.999930,-0.158810,5,3,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000004',@demo_assignment,14,38,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 87 MINUTE),39.000000,-0.158990,5,4,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000005',@demo_assignment,14,39,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 86 MINUTE),39.000000,-0.158900,5,5,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000006',@demo_assignment,14,40,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 85 MINUTE),39.000000,-0.158810,5,6,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000007',@demo_assignment,14,41,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 84 MINUTE),39.000070,-0.158990,5,7,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000008',@demo_assignment,14,42,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 83 MINUTE),39.000070,-0.158900,5,8,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000009',@demo_assignment,14,43,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 82 MINUTE),39.000070,-0.158810,5,9,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000010',@demo_assignment,14,72,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 81 MINUTE),39.002930,-0.160590,5,10,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000011',@demo_assignment,14,73,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 80 MINUTE),39.002930,-0.160500,5,11,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000012',@demo_assignment,14,74,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 79 MINUTE),39.002930,-0.160410,5,12,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000013',@demo_assignment,14,75,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 78 MINUTE),39.003000,-0.160590,5,13,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000014',@demo_assignment,14,76,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 77 MINUTE),39.003000,-0.160500,5,14,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000015',@demo_assignment,14,77,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 76 MINUTE),39.003000,-0.160410,5,15,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000016',@demo_assignment,14,78,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 75 MINUTE),39.003070,-0.160590,5,16,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000017',@demo_assignment,14,79,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 74 MINUTE),39.003070,-0.160500,5,17,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000018',@demo_assignment,14,80,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 73 MINUTE),39.003070,-0.160410,5,18,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000019',@demo_assignment,14,110,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 72 MINUTE),39.005930,-0.162090,5,19,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000020',@demo_assignment,14,111,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 71 MINUTE),39.005930,-0.162000,5,20,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000021',@demo_assignment,14,112,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 70 MINUTE),39.005930,-0.161910,5,21,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000022',@demo_assignment,14,113,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 69 MINUTE),39.006000,-0.162090,5,22,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000023',@demo_assignment,14,114,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 68 MINUTE),39.006000,-0.162000,5,23,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000024',@demo_assignment,14,115,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 67 MINUTE),39.006000,-0.161910,5,24,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000025',@demo_assignment,14,116,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 66 MINUTE),39.006070,-0.162090,5,25,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000026',@demo_assignment,14,117,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 65 MINUTE),39.006070,-0.162000,5,26,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000027',@demo_assignment,14,118,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 64 MINUTE),39.006070,-0.161910,5,27,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000028',@demo_assignment,14,40,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 63 MINUTE),39.008930,-0.163790,5,28,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000029',@demo_assignment,14,41,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 62 MINUTE),39.008930,-0.163700,5,29,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000030',@demo_assignment,14,42,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 61 MINUTE),39.008930,-0.163610,5,30,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000031',@demo_assignment,14,43,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 60 MINUTE),39.009000,-0.163790,5,31,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000032',@demo_assignment,14,44,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 59 MINUTE),39.009000,-0.163700,5,32,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000033',@demo_assignment,14,45,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 58 MINUTE),39.009000,-0.163610,5,33,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000034',@demo_assignment,14,46,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 57 MINUTE),39.009070,-0.163790,5,34,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000035',@demo_assignment,14,47,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 56 MINUTE),39.009070,-0.163700,5,35,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000036',@demo_assignment,14,48,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 55 MINUTE),39.009070,-0.163610,5,36,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000037',@demo_assignment,14,78,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 54 MINUTE),39.011930,-0.165390,5,37,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000038',@demo_assignment,14,79,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 53 MINUTE),39.011930,-0.165300,5,38,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000039',@demo_assignment,14,80,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 52 MINUTE),39.011930,-0.165210,5,39,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000040',@demo_assignment,14,81,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 51 MINUTE),39.012000,-0.165390,5,40,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000041',@demo_assignment,14,82,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 50 MINUTE),39.012000,-0.165300,5,41,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000042',@demo_assignment,14,83,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 49 MINUTE),39.012000,-0.165210,5,42,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000043',@demo_assignment,14,84,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 48 MINUTE),39.012070,-0.165390,5,43,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000044',@demo_assignment,14,85,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 47 MINUTE),39.012070,-0.165300,5,44,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000045',@demo_assignment,14,86,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 46 MINUTE),39.012070,-0.165210,5,45,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000046',@demo_assignment,14,120,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 45 MINUTE),39.014930,-0.167090,5,46,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000047',@demo_assignment,14,121,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 44 MINUTE),39.014930,-0.167000,5,47,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000048',@demo_assignment,14,122,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 43 MINUTE),39.014930,-0.166910,5,48,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000049',@demo_assignment,14,123,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 42 MINUTE),39.015000,-0.167090,5,49,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000050',@demo_assignment,14,124,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 41 MINUTE),39.015000,-0.167000,5,50,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000051',@demo_assignment,14,125,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 40 MINUTE),39.015000,-0.166910,5,51,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000052',@demo_assignment,14,126,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 39 MINUTE),39.015070,-0.167090,5,52,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000053',@demo_assignment,14,127,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 38 MINUTE),39.015070,-0.167000,5,53,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
SELECT 'ae200000-0000-4000-8000-000000000054',@demo_assignment,14,128,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 37 MINUTE),39.015070,-0.166910,5,54,-60,1
WHERE @demo_assignment IS NOT NULL
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);
COMMIT;
SELECT COUNT(*) AS mediciones_playa,
 SUM(valor<60) AS verdes,SUM(valor>=60 AND valor<100) AS naranjas,SUM(valor>=100) AS rojas
FROM mediciones WHERE evento LIKE 'ae200000-%';
-- Resultado esperado: 54 mediciones; 18 verdes, 18 naranjas, 18 rojas.
-- Mapa publico: seleccionar Simulados. Datos recientes dentro de las ultimas 24 horas.
