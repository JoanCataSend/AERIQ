-- Paso 04. Roles por campaña. Importar en phpMyAdmin con la base mcalgar2_pbio seleccionada.
-- Admin principal: crea campañas y sus admins, ve todo, gestiona solicitudes, NO exporta CSV.
-- Admin de campaña (antes analista): una sola campaña; crea operadores, registra Orb, exporta CSV.
-- Operador (antes trabajador): pertenece a una campaña y mide con la app.
-- El rol comercial desaparece. Requiere el código PHP/JS de la fase 2 para funcionar en la web.
SET NAMES utf8mb4;

-- 1. Cada cuenta puede pertenecer a una campaña de SU organización.
--    NULL = admin principal. La clave compuesta impide apuntar a una campaña de otra organización.
ALTER TABLE usuarios
  ADD COLUMN campana_id BIGINT UNSIGNED NULL AFTER rol_id,
  ADD CONSTRAINT usuario_campana FOREIGN KEY (campana_id,organizacion_id) REFERENCES campanas(id,organizacion_id);

-- 2. Permisos nuevos. organizacion.gestionar se sustituye por dos más concretos.
INSERT INTO permisos (id,codigo,descripcion) VALUES
 (6,'campanas.gestionar','Crear campañas y los administradores de cada campaña.'),
 (7,'equipo.gestionar','Crear operadores y registrar y asignar los Orb de su campaña.'),
 (8,'datos.exportar','Exportar a CSV las lecturas de su campaña.');

-- 3. Roles: renombrar y quitar el comercial.
UPDATE roles SET nombre='Administrador principal',
  descripcion='Crea campañas y sus administradores, ve todos los datos y atiende las solicitudes.', asignable=0
  WHERE codigo='admin';
UPDATE roles SET codigo='admin_campana', nombre='Admin de campaña',
  descripcion='Gestiona una campaña: crea operadores, registra los Orb y exporta CSV.', asignable=1
  WHERE codigo='analista';
UPDATE roles SET nombre='Operador',
  descripcion='Mide con los Orb que tiene asignados en su campaña.', asignable=1
  WHERE codigo='trabajador';
-- Las cuentas comerciales se borran (sus cambios de estado quedan en el historial sin autor).
DELETE FROM usuarios WHERE rol_id=(SELECT id FROM roles WHERE codigo='comercial');
DELETE FROM roles WHERE codigo='comercial';

-- 4. Qué puede hacer cada rol (se rehace entero).
DELETE FROM rol_permisos;
INSERT INTO rol_permisos (rol_id,permiso_id)
SELECT r.id,p.id FROM roles r JOIN permisos p ON
     (r.codigo='admin'         AND p.codigo IN ('mapa.organizacion','informes.ver','campanas.gestionar','solicitudes.gestionar'))
  OR (r.codigo='admin_campana' AND p.codigo IN ('mapa.organizacion','informes.ver','equipo.gestionar','datos.exportar'))
  OR (r.codigo='trabajador'    AND p.codigo IN ('mapa.organizacion','mediciones.registrar'));
DELETE FROM permisos WHERE codigo='organizacion.gestionar';

-- Resumen: cada rol con sus permisos, y cuentas que aún necesitan campaña.
SELECT r.nombre AS rol, GROUP_CONCAT(p.codigo ORDER BY p.id SEPARATOR ', ') AS permisos
FROM roles r LEFT JOIN rol_permisos rp ON rp.rol_id=r.id LEFT JOIN permisos p ON p.id=rp.permiso_id
GROUP BY r.id ORDER BY r.id;
SELECT u.email, r.nombre AS rol FROM usuarios u JOIN roles r ON r.id=u.rol_id
WHERE r.codigo IN ('admin_campana','trabajador') AND u.campana_id IS NULL;
