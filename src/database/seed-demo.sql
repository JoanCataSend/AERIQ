-- SOLO DEMOSTRACIÓN. Correos .example, MAC ficticias y mediciones simuladas.
-- Todas las cuentas: AeriqDemo-2026! (sustituirlas en un entorno real).
-- Roles (roles.id): 2 trabajador, 3 analista, 4 admin, 5 comercial.
SET time_zone = '+00:00';
START TRANSACTION;
INSERT INTO organizaciones(id,nombre) VALUES
 (1,'Ayuntamiento de Gandía · demostración'),
 (2,'Empresa Verde · demostración'),
 (3,'Código Norte · equipo comercial');
INSERT INTO usuarios(id,organizacion_id,rol_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en,invitacion_expira_en) VALUES
 (1,1,4,'Ana','Administradora','admin@gandia.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (2,1,2,'Pau','Operador','operador@gandia.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',0,1,NULL,DATE_ADD(UTC_TIMESTAMP(),INTERVAL 24 HOUR)),
 (3,1,3,'Marta','Analista','analista@gandia.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (4,3,5,'Joan','Comercial','comercial@codigonorte.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (5,2,4,'Elena','Administradora','admin@empresaverde.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL),
 (6,2,2,'Luis','Operador','operador@empresaverde.example','$2y$10$0Laxah.mivUKaWLxvpqdeO4Ukkyn2absN8vvGZgBIsBP.QL4WzTMK',1,0,UTC_TIMESTAMP(),NULL);
INSERT INTO zonas(id,organizacion_id,nombre) VALUES
 (1,1,'Campus UPV Gandía'),
 (2,2,'Instalaciones Empresa Verde');
INSERT INTO campanas(id,organizacion_id,zona_id,nombre,inicio,fin,publicar) VALUES
 (1,1,1,'Paseo por el campus · simulación',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),1),
 (2,2,2,'Estudio interno · simulación',DATE_SUB(UTC_DATE(),INTERVAL 7 DAY),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY),0);
-- La campaña 1 usa los umbrales de referencia del ozono; la 2 define los suyos.
INSERT INTO campana_umbrales(campana_id,magnitud_id,umbral_medio,umbral_alto) VALUES (2,14,50,90);
INSERT INTO dispositivos(id,organizacion_id,modelo_id,nombre,direccion_ble,simulado) VALUES
 (1,1,1,'Orb Campus 01','D0:00:00:00:00:01',1),
 (2,1,1,'Orb de reserva 02','D0:00:00:00:00:02',1),
 (3,2,1,'Orb Empresa 01','D0:00:00:00:00:03',1);
INSERT INTO asignaciones(id,organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en) VALUES
 (1,1,1,2,1,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY)),
 (2,2,3,6,2,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY));
INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado) VALUES
 ('00000000-0000-4000-8000-000000000001',1,14,42,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY),38.996651,-0.166009,5,1,-60,1),
 ('00000000-0000-4000-8000-000000000002',1,14,55,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY),38.996711,-0.166009,5,2,-61,1),
 ('00000000-0000-4000-8000-000000000003',1,14,68,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 6 HOUR),38.996651,-0.165929,5,3,-59,1),
 ('00000000-0000-4000-8000-000000000004',1,14,78,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 HOUR),38.996681,-0.165969,5,4,-58,1),
 ('00000000-0000-4000-8000-000000000005',1,14,104,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 HOUR),38.996691,-0.165939,5,5,-62,1),
 ('00000000-0000-4000-8000-000000000006',1,14,85,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE),38.996671,-0.165989,5,6,-60,1),
 ('00000000-0000-4000-8000-000000000007',1,12,22,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE),38.996671,-0.165989,5,7,-60,1),
 ('00000000-0000-4000-8000-000000000008',2,14,49,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 HOUR),38.998000,-0.162500,8,1,-65,1),
 ('00000000-0000-4000-8000-000000000009',2,14,57,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE),38.998060,-0.162500,8,2,-63,1);
-- Servicios: 1 kits, 2 estudio, 3 asesoramiento. Tipos: 1 administración, 2 empresa, 3 otra.
-- Estados: 1 pendiente, 2 en revisión, 3 respondida. Objetivos: ver objetivos_medicion.
INSERT INTO solicitudes(id,servicio_id,tipo_organizacion_id,estado_id,entidad,contacto,email,telefono,municipio,provincia,zona,superficie,duracion,inicio_deseado,necesidades,recibida_en) VALUES
 (1,1,1,1,'Ayuntamiento de ejemplo','Responsable municipal','municipio@example.test','600000001','Gandia','Valencia','Parques del municipio',NULL,'',NULL,'Kits para una campaña de movilidad.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 DAY)),
 (2,1,2,2,'Empresa de ejemplo','Responsable ambiental','empresa@example.test','','Oliva','Valencia','Recinto industrial',NULL,'',NULL,'Comparar turnos y zonas de acceso.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY)),
 (3,2,3,3,'Centro educativo de ejemplo','Coordinación del centro','centro@example.test','','Tavernes de la Valldigna','Valencia','Entorno del centro',2500,'2 semanas',NULL,'Estudio con informe y evolución diaria.',DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY));
INSERT INTO solicitud_objetivos(solicitud_id,objetivo_id) VALUES (1,1),(2,2),(3,1),(3,4);
INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id,cambiado_en) VALUES
 (1,1,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 3 DAY)),
 (2,1,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 DAY)),
 (2,2,4,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)),
 (3,1,NULL,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)),
 (3,3,4,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 HOUR));
COMMIT;
