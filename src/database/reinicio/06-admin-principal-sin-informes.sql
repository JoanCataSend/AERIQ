-- Paso 06. El admin principal organiza campañas: no hace informes ni ve el mapa privado.
-- Importar en phpMyAdmin con la base seleccionada. Se puede repetir sin problema.
-- Tras importarlo, el admin principal debe cerrar sesión y volver a entrar.
SET NAMES utf8mb4;

DELETE rp FROM rol_permisos rp
JOIN roles r ON r.id=rp.rol_id AND r.codigo='admin'
JOIN permisos p ON p.id=rp.permiso_id AND p.codigo IN ('informes.ver','mapa.organizacion');

UPDATE roles SET descripcion='Crea campañas y nombra a su admin, sigue su estado y atiende las solicitudes.' WHERE codigo='admin';

SELECT r.nombre AS rol, GROUP_CONCAT(p.codigo ORDER BY p.id SEPARATOR ', ') AS permisos
FROM roles r LEFT JOIN rol_permisos rp ON rp.rol_id=r.id LEFT JOIN permisos p ON p.id=rp.permiso_id
GROUP BY r.id ORDER BY r.id;
