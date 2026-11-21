-- =====================================================================
-- AERIQ · 2-simulacion.sql  (OPCIONAL)
-- Datos de prueba para enseñar la web y la app: tres perfiles de calidad del aire
-- (bueno, regular y malo), uno por campaña. Importar DESPUÉS de
-- 1-instalar.sql, con la misma base seleccionada en phpMyAdmin.
-- No borra nada y se puede repetir: no duplica filas y vuelve a poner las
-- lecturas en las últimas horas para que se vean en el mapa.
--
-- Perfiles: BUENO = campus (Marta, Pau), REGULAR = playa (Lucía), MALO = recinto
-- privado (Álex). Todo va marcado como simulado (dispositivos y lecturas con simulado = 1,
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

-- ------------------------------------------------------------ Lecturas simuladas: tres perfiles (T42)
-- Cada campaña es un perfil de calidad del aire para probar el informe y el mapa: BUENO (campus), REGULAR
-- (playa) y MALO (recinto, privada). Por campaña: 24 lecturas de ozono, una por hora en las últimas 24 h, y 6 de
-- temperatura. El valor de cada lectura depende de su hora local (UTC+1, hora de invierno peninsular), así el
-- veredicto por franjas sale igual se importe a la hora que se importe. Ozono en ppb, temperatura en °C, fechas UTC.
-- Posiciones en una rejilla de unos 11 m para que el mapa pueda interpolar. Todo va con simulado = 1.
-- Repetible: cada evento se actualiza con la hora y el valor nuevos (también los 54 de versiones anteriores).
-- Perfil BUENO · campus (umbrales 60/100): todo por debajo de 60 ppb, sin picos
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
 SELECT CONCAT('ae300000-0000-4000-8000-',LPAD(0+t.k+1,12,'0')),@a1,14,
  ELT(HOUR(DATE_ADD(t.cap,INTERVAL 1 HOUR))+1,24,22,21,20,20,22,26,30,34,38,41,44,47,49,51,52,50,46,41,36,32,29,27,25),
  t.cap,ROUND(38.996651+FLOOR(t.k/6)*0.0001,6),ROUND(-0.166009+MOD(t.k,6)*0.00013,6),5,t.k+1,-55-MOD(t.k,12),1
 FROM (SELECT k,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL k*60+5 MINUTE) AS cap FROM (SELECT 0 k UNION ALL SELECT 1 k UNION ALL SELECT 2 k UNION ALL SELECT 3 k UNION ALL SELECT 4 k UNION ALL SELECT 5 k UNION ALL SELECT 6 k UNION ALL SELECT 7 k UNION ALL SELECT 8 k UNION ALL SELECT 9 k UNION ALL SELECT 10 k UNION ALL SELECT 11 k UNION ALL SELECT 12 k UNION ALL SELECT 13 k UNION ALL SELECT 14 k UNION ALL SELECT 15 k UNION ALL SELECT 16 k UNION ALL SELECT 17 k UNION ALL SELECT 18 k UNION ALL SELECT 19 k UNION ALL SELECT 20 k UNION ALL SELECT 21 k UNION ALL SELECT 22 k UNION ALL SELECT 23 k) h) t
 WHERE @a1 IS NOT NULL
ON DUPLICATE KEY UPDATE asignacion_id=VALUES(asignacion_id),magnitud_id=VALUES(magnitud_id),valor=VALUES(valor),capturada_en=VALUES(capturada_en),
 latitud=VALUES(latitud),longitud=VALUES(longitud),precision_m=VALUES(precision_m),contador=VALUES(contador),rssi=VALUES(rssi),simulado=VALUES(simulado);
-- Temperatura del módulo: seis lecturas, una cada cuatro horas.
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
 SELECT CONCAT('ae300000-0000-4000-8000-',LPAD(0+25+t.k,12,'0')),@a1,12,ELT(t.k+1,17.5,16.0,19.5,23.0,24.5,21.0),
  t.cap,38.996651,-0.166009,5,100+t.k,-60,1
 FROM (SELECT k,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL k*240+10 MINUTE) AS cap FROM (SELECT 0 k UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) h) t
 WHERE @a1 IS NOT NULL
ON DUPLICATE KEY UPDATE asignacion_id=VALUES(asignacion_id),magnitud_id=VALUES(magnitud_id),valor=VALUES(valor),capturada_en=VALUES(capturada_en),
 latitud=VALUES(latitud),longitud=VALUES(longitud),precision_m=VALUES(precision_m),contador=VALUES(contador),rssi=VALUES(rssi),simulado=VALUES(simulado);

-- Perfil REGULAR · playa (umbrales propios 55/90): buena de madrugada y regular el resto, picos moderados
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
 SELECT CONCAT('ae300000-0000-4000-8000-',LPAD(30+t.k+1,12,'0')),@a2,14,
  ELT(HOUR(DATE_ADD(t.cap,INTERVAL 1 HOUR))+1,38,36,35,34,35,37,45,52,60,66,70,74,79,84,87,86,82,76,70,66,62,58,54,48),
  t.cap,ROUND(38.999+FLOOR(t.k/6)*0.0001,6),ROUND(-0.1589+MOD(t.k,6)*0.00013,6),5,t.k+1,-55-MOD(t.k,12),1
 FROM (SELECT k,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL k*60+5 MINUTE) AS cap FROM (SELECT 0 k UNION ALL SELECT 1 k UNION ALL SELECT 2 k UNION ALL SELECT 3 k UNION ALL SELECT 4 k UNION ALL SELECT 5 k UNION ALL SELECT 6 k UNION ALL SELECT 7 k UNION ALL SELECT 8 k UNION ALL SELECT 9 k UNION ALL SELECT 10 k UNION ALL SELECT 11 k UNION ALL SELECT 12 k UNION ALL SELECT 13 k UNION ALL SELECT 14 k UNION ALL SELECT 15 k UNION ALL SELECT 16 k UNION ALL SELECT 17 k UNION ALL SELECT 18 k UNION ALL SELECT 19 k UNION ALL SELECT 20 k UNION ALL SELECT 21 k UNION ALL SELECT 22 k UNION ALL SELECT 23 k) h) t
 WHERE @a2 IS NOT NULL
ON DUPLICATE KEY UPDATE asignacion_id=VALUES(asignacion_id),magnitud_id=VALUES(magnitud_id),valor=VALUES(valor),capturada_en=VALUES(capturada_en),
 latitud=VALUES(latitud),longitud=VALUES(longitud),precision_m=VALUES(precision_m),contador=VALUES(contador),rssi=VALUES(rssi),simulado=VALUES(simulado);
-- Temperatura del módulo: seis lecturas, una cada cuatro horas.
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
 SELECT CONCAT('ae300000-0000-4000-8000-',LPAD(30+25+t.k,12,'0')),@a2,12,ELT(t.k+1,17.5,16.0,19.5,23.0,24.5,21.0),
  t.cap,38.999,-0.1589,5,100+t.k,-60,1
 FROM (SELECT k,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL k*240+10 MINUTE) AS cap FROM (SELECT 0 k UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) h) t
 WHERE @a2 IS NOT NULL
ON DUPLICATE KEY UPDATE asignacion_id=VALUES(asignacion_id),magnitud_id=VALUES(magnitud_id),valor=VALUES(valor),capturada_en=VALUES(capturada_en),
 latitud=VALUES(latitud),longitud=VALUES(longitud),precision_m=VALUES(precision_m),contador=VALUES(contador),rssi=VALUES(rssi),simulado=VALUES(simulado);

-- Perfil MALO · recinto (umbrales 60/100): mala por la mañana y por la tarde, picos altos
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
 SELECT CONCAT('ae300000-0000-4000-8000-',LPAD(60+t.k+1,12,'0')),@a3,14,
  ELT(HOUR(DATE_ADD(t.cap,INTERVAL 1 HOUR))+1,66,62,60,58,61,65,84,94,102,110,116,121,126,131,134,129,120,112,104,98,92,86,78,72),
  t.cap,ROUND(38.9674+FLOOR(t.k/6)*0.0001,6),ROUND(-0.182+MOD(t.k,6)*0.00013,6),5,t.k+1,-55-MOD(t.k,12),1
 FROM (SELECT k,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL k*60+5 MINUTE) AS cap FROM (SELECT 0 k UNION ALL SELECT 1 k UNION ALL SELECT 2 k UNION ALL SELECT 3 k UNION ALL SELECT 4 k UNION ALL SELECT 5 k UNION ALL SELECT 6 k UNION ALL SELECT 7 k UNION ALL SELECT 8 k UNION ALL SELECT 9 k UNION ALL SELECT 10 k UNION ALL SELECT 11 k UNION ALL SELECT 12 k UNION ALL SELECT 13 k UNION ALL SELECT 14 k UNION ALL SELECT 15 k UNION ALL SELECT 16 k UNION ALL SELECT 17 k UNION ALL SELECT 18 k UNION ALL SELECT 19 k UNION ALL SELECT 20 k UNION ALL SELECT 21 k UNION ALL SELECT 22 k UNION ALL SELECT 23 k) h) t
 WHERE @a3 IS NOT NULL
ON DUPLICATE KEY UPDATE asignacion_id=VALUES(asignacion_id),magnitud_id=VALUES(magnitud_id),valor=VALUES(valor),capturada_en=VALUES(capturada_en),
 latitud=VALUES(latitud),longitud=VALUES(longitud),precision_m=VALUES(precision_m),contador=VALUES(contador),rssi=VALUES(rssi),simulado=VALUES(simulado);
-- Temperatura del módulo: seis lecturas, una cada cuatro horas.
INSERT INTO mediciones (evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado)
 SELECT CONCAT('ae300000-0000-4000-8000-',LPAD(60+25+t.k,12,'0')),@a3,12,ELT(t.k+1,17.5,16.0,19.5,23.0,24.5,21.0),
  t.cap,38.9674,-0.182,5,100+t.k,-60,1
 FROM (SELECT k,DATE_SUB(UTC_TIMESTAMP(3),INTERVAL k*240+10 MINUTE) AS cap FROM (SELECT 0 k UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5) h) t
 WHERE @a3 IS NOT NULL
ON DUPLICATE KEY UPDATE asignacion_id=VALUES(asignacion_id),magnitud_id=VALUES(magnitud_id),valor=VALUES(valor),capturada_en=VALUES(capturada_en),
 latitud=VALUES(latitud),longitud=VALUES(longitud),precision_m=VALUES(precision_m),contador=VALUES(contador),rssi=VALUES(rssi),simulado=VALUES(simulado);

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
