-- Paso 02. Crea el primer administrador y reutiliza la organización si ya existe.
SET NAMES utf8mb4;

INSERT INTO `jcatsen_pbio`.`organizaciones` (nombre)
SELECT 'AERIQ Gandia'
WHERE NOT EXISTS (SELECT 1 FROM `jcatsen_pbio`.`usuarios`)
  AND NOT EXISTS (
      SELECT 1 FROM `jcatsen_pbio`.`organizaciones` WHERE nombre = 'AERIQ Gandia'
  );

INSERT INTO `jcatsen_pbio`.`usuarios`
    (organizacion_id, rol_id, nombre, apellidos, email, password_hash, verificado_en)
SELECT o.id, r.id, 'Marina', 'Ferrer', 'gandia@gmail.com', '$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2', UTC_TIMESTAMP()
FROM `jcatsen_pbio`.`organizaciones` AS o
JOIN `jcatsen_pbio`.`roles` AS r ON r.codigo = 'admin'
WHERE o.nombre = 'AERIQ Gandia'
  AND NOT EXISTS (SELECT 1 FROM `jcatsen_pbio`.`usuarios`)
ORDER BY o.id
LIMIT 1;

SELECT u.email, u.nombre, u.apellidos, r.nombre AS rol, o.nombre AS organizacion
FROM `jcatsen_pbio`.`usuarios` AS u
JOIN `jcatsen_pbio`.`organizaciones` AS o ON o.id = u.organizacion_id
JOIN `jcatsen_pbio`.`roles` AS r ON r.id = u.rol_id
WHERE u.email = 'gandia@gmail.com' AND r.codigo = 'admin';
