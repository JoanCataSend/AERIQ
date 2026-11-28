# Fichero: 2-simulacion.sql
# Autor: Codigo Norte
# Descripción: Archivo fuente de scripts/src/database.
# Fecha: 2026-09-25
# Copyright (c) 2026 Codigo Norte
# Autor: Codigo Norte
# Descripción: Archivo fuente de scripts/src/database.
# Fecha: 2026-09-25
# Copyright (c) 2026 Codigo Norte
# Autor: Codigo Norte
# Descripción: Archivo fuente de scripts/src/database.
# Fecha: 2026-09-25
# Copyright (c) 2026 Codigo Norte
-- =====================================================================
-- AERIQ · 2-simulacion.sql  (OPCIONAL)
-- Datos de prueba para enseñar la web y la app. Importar DESPUÉS de
-- 1-instalar.sql, con la misma base seleccionada en phpMyAdmin.
-- No borra nada y se puede repetir: no duplica filas y vuelve a poner las
-- lecturas en las últimas horas para que se vean en el mapa.
--
-- Todo va marcado como simulado (dispositivos y lecturas con simulado = 1,
-- correos .example). Cuentas, todas con contraseña 1234:
--   analista.simulacion@aeriq.example   Marta · admin de campaña (campus)
--   operador.simulacion@aeriq.example   Pau   · operador (campus)
--   operadora.simulacion@aeriq.example  Lucía · operadora (playa)
--   operador3.simulacion@aeriq.example  Álex  · operador (recinto, privada)
-- Si la web es pública, cambia o borra estas cuentas.
-- =====================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

-- ------------------------------------------------------------ Organización y admin principal
INSERT INTO organizaciones (nombre) SELECT 'AERIQ Gandia' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM organizaciones WHERE nombre='AERIQ Gandia');
SET @org := (SELECT MIN(id) FROM organizaciones WHERE nombre='AERIQ Gandia');
-- Los cambios de estado de las solicitudes de ejemplo los firma el admin principal.
SET @admin := (SELECT MIN(u.id) FROM usuarios u JOIN roles r ON r.id=u.rol_id AND r.codigo='admin' WHERE u.organizacion_id=@org);

-- ------------------------------------------------------------ Zonas y campañas
INSERT IGNORE INTO zonas (organizacion_id,nombre) VALUES (@org,'Campus UPV Gandia'),(@org,'Playa de Gandia'),(@org,'Recinto municipal');
INSERT INTO campanas (organizacion_id,zona_id,nombre,inicio,fin,activa,publicar,radio_m)
 SELECT @org,z.id,'Simulación · paseo por el campus',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 60 DAY),1,1,30
 FROM zonas z WHERE z.organizacion_id=@org AND z.nombre='Campus UPV Gandia'
 AND NOT EXISTS (SELECT 1 FROM campanas WHERE organizacion_id=@org AND nombre='Simulación · paseo por el campus');
SET @c1 := (SELECT MIN(id) FROM campanas WHERE organizacion_id=@org AND nombre='Simulación · paseo por el campus');
-- Al repetir la importación, la campaña sigue vigente hoy.
UPDATE campanas SET inicio=LEAST(inicio,DATE_SUB(UTC_DATE(),INTERVAL 7 DAY)),fin=GREATEST(fin,DATE_ADD(UTC_DATE(),INTERVAL 60 DAY)),activa=1 WHERE id=@c1;
INSERT INTO campanas (organizacion_id,zona_id,nombre,inicio,fin,activa,publicar,radio_m)
 SELECT @org,z.id,'Simulación · playa de Gandia',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 60 DAY),1,1,30
 FROM zonas z WHERE z.organizacion_id=@org AND z.nombre='Playa de Gandia'
 AND NOT EXISTS (SELECT 1 FROM campanas WHERE organizacion_id=@org AND nombre='Simulación · playa de Gandia');
SET @c2 := (SELECT MIN(id) FROM campanas WHERE organizacion_id=@org AND nombre='Simulación · playa de Gandia');
-- Al repetir la importación, la campaña sigue vigente hoy.
UPDATE campanas SET inicio=LEAST(inicio,DATE_SUB(UTC_DATE(),INTERVAL 7 DAY)),fin=GREATEST(fin,DATE_ADD(UTC_DATE(),INTERVAL 60 DAY)),activa=1 WHERE id=@c2;
-- Esta campaña define umbrales propios de ozono; las otras usan los de referencia (60 / 100 ppb).
INSERT IGNORE INTO campana_umbrales (campana_id,magnitud_id,umbral_medio,umbral_alto) VALUES (@c2,14,55,90);
INSERT INTO campanas (organizacion_id,zona_id,nombre,inicio,fin,activa,publicar,radio_m)
 SELECT @org,z.id,'Simulación · recinto municipal (privada)',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 60 DAY),1,0,30
 FROM zonas z WHERE z.organizacion_id=@org AND z.nombre='Recinto municipal'
 AND NOT EXISTS (SELECT 1 FROM campanas WHERE organizacion_id=@org AND nombre='Simulación · recinto municipal (privada)');
SET @c3 := (SELECT MIN(id) FROM campanas WHERE organizacion_id=@org AND nombre='Simulación · recinto municipal (privada)');
-- Al repetir la importación, la campaña sigue vigente hoy.
UPDATE campanas SET inicio=LEAST(inicio,DATE_SUB(UTC_DATE(),INTERVAL 7 DAY)),fin=GREATEST(fin,DATE_ADD(UTC_DATE(),INTERVAL 60 DAY)),activa=1 WHERE id=@c3;


-- ------------------------------------------------------------ Cuentas de prueba (una campaña cada una)
INSERT INTO usuarios (organizacion_id,rol_id,campana_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en)
 SELECT @org,r.id,@c1,'Marta','Admin de campaña (simulación)','analista.simulacion@aeriq.example','$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2',1,0,UTC_TIMESTAMP() FROM roles r
 WHERE r.codigo='admin_campana' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE email='analista.simulacion@aeriq.example');
INSERT INTO usuarios (organizacion_id,rol_id,campana_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en)
 SELECT @org,r.id,@c1,'Pau','Operador (simulación)','operador.simulacion@aeriq.example','$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2',1,0,UTC_TIMESTAMP() FROM roles r
 WHERE r.codigo='trabajador' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE email='operador.simulacion@aeriq.example');
INSERT INTO usuarios (organizacion_id,rol_id,campana_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en)
 SELECT @org,r.id,@c2,'Lucía','Operadora (simulación)','operadora.simulacion@aeriq.example','$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2',1,0,UTC_TIMESTAMP() FROM roles r
 WHERE r.codigo='trabajador' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE email='operadora.simulacion@aeriq.example');
INSERT INTO usuarios (organizacion_id,rol_id,campana_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en)
 SELECT @org,r.id,@c3,'Álex','Operador (simulación)','operador3.simulacion@aeriq.example','$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2',1,0,UTC_TIMESTAMP() FROM roles r
 WHERE r.codigo='trabajador' AND NOT EXISTS (SELECT 1 FROM usuarios WHERE email='operador3.simulacion@aeriq.example');
-- Si ya existían de una importación anterior: contraseña 1234 y su campaña de nuevo.
UPDATE usuarios SET password_hash='$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2' WHERE email IN ('analista.simulacion@aeriq.example','operador.simulacion@aeriq.example','operadora.simulacion@aeriq.example','operador3.simulacion@aeriq.example');
UPDATE usuarios SET campana_id=@c1 WHERE email IN ('analista.simulacion@aeriq.example','operador.simulacion@aeriq.example') AND organizacion_id=@org;
UPDATE usuarios SET campana_id=@c2 WHERE email='operadora.simulacion@aeriq.example' AND organizacion_id=@org;
UPDATE usuarios SET campana_id=@c3 WHERE email='operador3.simulacion@aeriq.example' AND organizacion_id=@org;
SET @pau := (SELECT id FROM usuarios WHERE email='operador.simulacion@aeriq.example');
SET @lucia := (SELECT id FROM usuarios WHERE email='operadora.simulacion@aeriq.example');
SET @alex := (SELECT id FROM usuarios WHERE email='operador3.simulacion@aeriq.example');

-- ------------------------------------------------------------ AERIQ simulados y asignaciones (uno por operador)
INSERT INTO dispositivos (organizacion_id,modelo_id,nombre,direccion_ble,activo,simulado)
 SELECT @org,1,'AERIQ simulado · campus','D0:00:00:00:00:A1',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dispositivos WHERE direccion_ble='D0:00:00:00:00:A1');
SET @d1 := (SELECT id FROM dispositivos WHERE direccion_ble='D0:00:00:00:00:A1' AND organizacion_id=@org AND simulado=1);
INSERT INTO asignaciones (organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
 SELECT @org,@d1,@pau,@c1,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 7 DAY) FROM DUAL
 WHERE @d1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM asignaciones WHERE dispositivo_id=@d1 AND finalizada_en IS NULL);
SET @a1 := (SELECT id FROM asignaciones WHERE dispositivo_id=@d1 AND finalizada_en IS NULL);
INSERT INTO dispositivos (organizacion_id,modelo_id,nombre,direccion_ble,activo,simulado)
 SELECT @org,1,'AERIQ simulado · playa','D0:00:00:00:00:A2',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dispositivos WHERE direccion_ble='D0:00:00:00:00:A2');
SET @d2 := (SELECT id FROM dispositivos WHERE direccion_ble='D0:00:00:00:00:A2' AND organizacion_id=@org AND simulado=1);
INSERT INTO asignaciones (organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
 SELECT @org,@d2,@lucia,@c2,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 7 DAY) FROM DUAL
 WHERE @d2 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM asignaciones WHERE dispositivo_id=@d2 AND finalizada_en IS NULL);
SET @a2 := (SELECT id FROM asignaciones WHERE dispositivo_id=@d2 AND finalizada_en IS NULL);
INSERT INTO dispositivos (organizacion_id,modelo_id,nombre,direccion_ble,activo,simulado)
 SELECT @org,1,'AERIQ simulado · recinto','D0:00:00:00:00:A3',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM dispositivos WHERE direccion_ble='D0:00:00:00:00:A3');
SET @d3 := (SELECT id FROM dispositivos WHERE direccion_ble='D0:00:00:00:00:A3' AND organizacion_id=@org AND simulado=1);
INSERT INTO asignaciones (organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en)
 SELECT @org,@d3,@alex,@c3,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 7 DAY) FROM DUAL
 WHERE @d3 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM asignaciones WHERE dispositivo_id=@d3 AND finalizada_en IS NULL);
SET @a3 := (SELECT id FROM asignaciones WHERE dispositivo_id=@d3 AND finalizada_en IS NULL);

-- ------------------------------------------------------------ Lecturas simuladas
-- Ozono en ppb y temperatura en °C, fechas UTC relativas a la importación.
-- Las posiciones son ilustrativas y están a unos 11 m entre sí para que el mapa pueda interpolar.
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado) VALUES
 ('ae300000-0000-4000-8000-000000000001',@a1,14,38,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 290 MINUTE),38.996651,-0.166009,5,1,-55,1),
 ('ae300000-0000-4000-8000-000000000002',@a1,14,42,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 270 MINUTE),38.996651,-0.165879,5,2,-56,1),
 ('ae300000-0000-4000-8000-000000000003',@a1,14,47,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 250 MINUTE),38.996651,-0.165749,5,3,-57,1),
 ('ae300000-0000-4000-8000-000000000004',@a1,14,51,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 230 MINUTE),38.996651,-0.165619,5,4,-58,1),
 ('ae300000-0000-4000-8000-000000000005',@a1,14,56,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 210 MINUTE),38.996651,-0.165489,5,5,-59,1),
 ('ae300000-0000-4000-8000-000000000006',@a1,14,61,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 190 MINUTE),38.996751,-0.166009,5,6,-60,1),
 ('ae300000-0000-4000-8000-000000000007',@a1,14,66,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 170 MINUTE),38.996751,-0.165879,5,7,-61,1),
 ('ae300000-0000-4000-8000-000000000008',@a1,14,72,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 150 MINUTE),38.996751,-0.165749,5,8,-62,1),
 ('ae300000-0000-4000-8000-000000000009',@a1,14,78,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 130 MINUTE),38.996751,-0.165619,5,9,-63,1),
 ('ae300000-0000-4000-8000-000000000010',@a1,14,84,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 110 MINUTE),38.996751,-0.165489,5,10,-64,1),
 ('ae300000-0000-4000-8000-000000000011',@a1,14,91,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 90 MINUTE),38.996851,-0.166009,5,11,-65,1),
 ('ae300000-0000-4000-8000-000000000012',@a1,14,97,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 70 MINUTE),38.996851,-0.165879,5,12,-66,1),
 ('ae300000-0000-4000-8000-000000000013',@a1,14,103,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 50 MINUTE),38.996851,-0.165749,5,13,-55,1),
 ('ae300000-0000-4000-8000-000000000014',@a1,14,108,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE),38.996851,-0.165619,5,14,-56,1),
 ('ae300000-0000-4000-8000-000000000015',@a1,14,114,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE),38.996851,-0.165489,5,15,-57,1),
 ('ae300000-0000-4000-8000-000000000016',@a1,12,18.5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE),38.996651,-0.166009,5,100,-60,1),
 ('ae300000-0000-4000-8000-000000000017',@a1,12,21.0,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 120 MINUTE),38.996651,-0.166009,5,101,-60,1),
 ('ae300000-0000-4000-8000-000000000018',@a1,12,23.5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 210 MINUTE),38.996651,-0.166009,5,102,-60,1),
 ('ae300000-0000-4000-8000-000000000019',@a2,14,35,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 290 MINUTE),38.999000,-0.158900,5,1,-55,1),
 ('ae300000-0000-4000-8000-000000000020',@a2,14,40,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 270 MINUTE),38.999000,-0.158770,5,2,-56,1),
 ('ae300000-0000-4000-8000-000000000021',@a2,14,44,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 250 MINUTE),38.999000,-0.158640,5,3,-57,1),
 ('ae300000-0000-4000-8000-000000000022',@a2,14,49,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 230 MINUTE),38.999000,-0.158510,5,4,-58,1),
 ('ae300000-0000-4000-8000-000000000023',@a2,14,53,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 210 MINUTE),38.999000,-0.158380,5,5,-59,1),
 ('ae300000-0000-4000-8000-000000000024',@a2,14,57,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 190 MINUTE),38.999100,-0.158900,5,6,-60,1),
 ('ae300000-0000-4000-8000-000000000025',@a2,14,62,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 170 MINUTE),38.999100,-0.158770,5,7,-61,1),
 ('ae300000-0000-4000-8000-000000000026',@a2,14,68,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 150 MINUTE),38.999100,-0.158640,5,8,-62,1),
 ('ae300000-0000-4000-8000-000000000027',@a2,14,74,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 130 MINUTE),38.999100,-0.158510,5,9,-63,1),
 ('ae300000-0000-4000-8000-000000000028',@a2,14,81,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 110 MINUTE),38.999100,-0.158380,5,10,-64,1),
 ('ae300000-0000-4000-8000-000000000029',@a2,14,86,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 90 MINUTE),38.999200,-0.158900,5,11,-65,1),
 ('ae300000-0000-4000-8000-000000000030',@a2,14,92,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 70 MINUTE),38.999200,-0.158770,5,12,-66,1),
 ('ae300000-0000-4000-8000-000000000031',@a2,14,95,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 50 MINUTE),38.999200,-0.158640,5,13,-55,1),
 ('ae300000-0000-4000-8000-000000000032',@a2,14,99,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE),38.999200,-0.158510,5,14,-56,1),
 ('ae300000-0000-4000-8000-000000000033',@a2,14,104,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE),38.999200,-0.158380,5,15,-57,1),
 ('ae300000-0000-4000-8000-000000000034',@a2,12,18.5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE),38.999000,-0.158900,5,100,-60,1),
 ('ae300000-0000-4000-8000-000000000035',@a2,12,21.0,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 120 MINUTE),38.999000,-0.158900,5,101,-60,1),
 ('ae300000-0000-4000-8000-000000000036',@a2,12,23.5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 210 MINUTE),38.999000,-0.158900,5,102,-60,1),
 ('ae300000-0000-4000-8000-000000000037',@a3,14,45,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 290 MINUTE),38.967400,-0.182000,5,1,-55,1),
 ('ae300000-0000-4000-8000-000000000038',@a3,14,52,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 270 MINUTE),38.967400,-0.181870,5,2,-56,1),
 ('ae300000-0000-4000-8000-000000000039',@a3,14,58,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 250 MINUTE),38.967400,-0.181740,5,3,-57,1),
 ('ae300000-0000-4000-8000-000000000040',@a3,14,63,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 230 MINUTE),38.967400,-0.181610,5,4,-58,1),
 ('ae300000-0000-4000-8000-000000000041',@a3,14,67,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 210 MINUTE),38.967400,-0.181480,5,5,-59,1),
 ('ae300000-0000-4000-8000-000000000042',@a3,14,71,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 190 MINUTE),38.967500,-0.182000,5,6,-60,1),
 ('ae300000-0000-4000-8000-000000000043',@a3,14,75,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 170 MINUTE),38.967500,-0.181870,5,7,-61,1),
 ('ae300000-0000-4000-8000-000000000044',@a3,14,79,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 150 MINUTE),38.967500,-0.181740,5,8,-62,1),
 ('ae300000-0000-4000-8000-000000000045',@a3,14,82,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 130 MINUTE),38.967500,-0.181610,5,9,-63,1),
 ('ae300000-0000-4000-8000-000000000046',@a3,14,76,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 110 MINUTE),38.967500,-0.181480,5,10,-64,1),
 ('ae300000-0000-4000-8000-000000000047',@a3,14,70,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 90 MINUTE),38.967600,-0.182000,5,11,-65,1),
 ('ae300000-0000-4000-8000-000000000048',@a3,14,64,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 70 MINUTE),38.967600,-0.181870,5,12,-66,1),
 ('ae300000-0000-4000-8000-000000000049',@a3,14,59,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 50 MINUTE),38.967600,-0.181740,5,13,-55,1),
 ('ae300000-0000-4000-8000-000000000050',@a3,14,54,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE),38.967600,-0.181610,5,14,-56,1),
 ('ae300000-0000-4000-8000-000000000051',@a3,14,50,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 10 MINUTE),38.967600,-0.181480,5,15,-57,1),
 ('ae300000-0000-4000-8000-000000000052',@a3,12,18.5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE),38.967400,-0.182000,5,100,-60,1),
 ('ae300000-0000-4000-8000-000000000053',@a3,12,21.0,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 120 MINUTE),38.967400,-0.182000,5,101,-60,1),
 ('ae300000-0000-4000-8000-000000000054',@a3,12,23.5,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 210 MINUTE),38.967400,-0.182000,5,102,-60,1)
ON DUPLICATE KEY UPDATE capturada_en=VALUES(capturada_en);

-- ------------------------------------------------------------ Solicitudes comerciales
-- Servicios: kits, estudio (superficie opcional, duración) y asesoramiento. Cada solicitud
-- lleva su tipo de organización y al menos un objetivo de medición.
INSERT INTO solicitudes (servicio_id,tipo_organizacion_id,estado_id,entidad,contacto,email,municipio,provincia,zona,superficie,duracion,necesidades,recibida_en)
 SELECT s.id,t.id,e.id,'Ayuntamiento de ejemplo (simulación)','Responsable municipal','municipio.simulacion@example.test','Gandia','Valencia','Parques del municipio',NULL,'','Kits para una campaña de movilidad.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 DAY)
 FROM servicios s JOIN estados_solicitud e ON e.codigo='pendiente' JOIN tipos_organizacion t ON t.codigo='administracion'
 WHERE s.codigo='kits' AND NOT EXISTS (SELECT 1 FROM solicitudes WHERE email='municipio.simulacion@example.test' AND entidad='Ayuntamiento de ejemplo (simulación)');
SET @s := (SELECT MIN(id) FROM solicitudes WHERE email='municipio.simulacion@example.test' AND entidad='Ayuntamiento de ejemplo (simulación)');
INSERT IGNORE INTO solicitud_objetivos (solicitud_id,objetivo_id)
 SELECT @s,o.id FROM objetivos_medicion o WHERE o.codigo IN ('espacios_publicos');
INSERT INTO solicitudes_historial (solicitud_id,estado_id,usuario_id,cambiado_en)
 SELECT * FROM (SELECT @s,e.id,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 72 HOUR) FROM estados_solicitud e WHERE e.codigo='pendiente') h
 WHERE NOT EXISTS (SELECT 1 FROM solicitudes_historial WHERE solicitud_id=@s);
INSERT INTO solicitudes (servicio_id,tipo_organizacion_id,estado_id,entidad,contacto,email,municipio,provincia,zona,superficie,duracion,necesidades,recibida_en)
 SELECT s.id,t.id,e.id,'Empresa de ejemplo (simulación)','Responsable ambiental','empresa.simulacion@example.test','Oliva','Valencia','Recinto industrial',NULL,'','Comparar turnos y zonas de acceso.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY)
 FROM servicios s JOIN estados_solicitud e ON e.codigo='en_revision' JOIN tipos_organizacion t ON t.codigo='empresa'
 WHERE s.codigo='kits' AND NOT EXISTS (SELECT 1 FROM solicitudes WHERE email='empresa.simulacion@example.test' AND entidad='Empresa de ejemplo (simulación)');
SET @s := (SELECT MIN(id) FROM solicitudes WHERE email='empresa.simulacion@example.test' AND entidad='Empresa de ejemplo (simulación)');
INSERT IGNORE INTO solicitud_objetivos (solicitud_id,objetivo_id)
 SELECT @s,o.id FROM objetivos_medicion o WHERE o.codigo IN ('salud_laboral');
INSERT INTO solicitudes_historial (solicitud_id,estado_id,usuario_id,cambiado_en)
 SELECT * FROM (SELECT @s,e.id,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 48 HOUR) FROM estados_solicitud e WHERE e.codigo='pendiente'
  UNION ALL SELECT @s,e.id,@admin,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 42 HOUR) FROM estados_solicitud e WHERE e.codigo='en_revision') h
 WHERE NOT EXISTS (SELECT 1 FROM solicitudes_historial WHERE solicitud_id=@s);
INSERT INTO solicitudes (servicio_id,tipo_organizacion_id,estado_id,entidad,contacto,email,municipio,provincia,zona,superficie,duracion,necesidades,recibida_en)
 SELECT s.id,t.id,e.id,'Centro educativo de ejemplo (simulación)','Coordinación del centro','centro.simulacion@example.test','Tavernes de la Valldigna','Valencia','Entorno del centro',2500,'2 semanas','Estudio con informe y evolución diaria.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)
 FROM servicios s JOIN estados_solicitud e ON e.codigo='respondida' JOIN tipos_organizacion t ON t.codigo='otra'
 WHERE s.codigo='estudio' AND NOT EXISTS (SELECT 1 FROM solicitudes WHERE email='centro.simulacion@example.test' AND entidad='Centro educativo de ejemplo (simulación)');
SET @s := (SELECT MIN(id) FROM solicitudes WHERE email='centro.simulacion@example.test' AND entidad='Centro educativo de ejemplo (simulación)');
INSERT IGNORE INTO solicitud_objetivos (solicitud_id,objetivo_id)
 SELECT @s,o.id FROM objetivos_medicion o WHERE o.codigo IN ('espacios_publicos','investigacion');
INSERT INTO solicitudes_historial (solicitud_id,estado_id,usuario_id,cambiado_en)
 SELECT * FROM (SELECT @s,e.id,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 24 HOUR) FROM estados_solicitud e WHERE e.codigo='pendiente'
  UNION ALL SELECT @s,e.id,@admin,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 18 HOUR) FROM estados_solicitud e WHERE e.codigo='en_revision'
  UNION ALL SELECT @s,e.id,@admin,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 12 HOUR) FROM estados_solicitud e WHERE e.codigo='respondida') h
 WHERE NOT EXISTS (SELECT 1 FROM solicitudes_historial WHERE solicitud_id=@s);
COMMIT;

-- Resumen: lo que hay ahora en la base (fechas en UTC).
SELECT campana, origen, COUNT(*) AS lecturas, MAX(fecha_utc) AS ultima_utc FROM vista_mediciones GROUP BY campana, origen ORDER BY campana;
SELECT nombre, email, organizacion, rol, estado FROM vista_usuarios ORDER BY organizacion, rol;
SELECT id, servicio, tipo_organizacion, estado, entidad, objetivos FROM vista_solicitudes ORDER BY id;
