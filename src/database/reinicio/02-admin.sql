-- Paso 02. Crea el primer administrador y reutiliza la organización si ya existe.
SET NAMES utf8mb4;

INSERT INTO `jcatsen_pbio`.`organizaciones` (nombre)
SELECT 'AERIQ Gandia'
WHERE NOT EXISTS (SELECT 1 FROM `jcatsen_pbio`.`usuarios`)
  AND NOT EXISTS (
      SELECT 1 FROM `jcatsen_pbio`.`organizaciones` WHERE nombre = 'AERIQ Gandia'
  );

INSERT INTO `jcatsen_pbio`.`usuarios`
    (organizacion_id, rol, nombre, apellidos, email, password_hash, verificado_en)
SELECT o.id, 'admin', 'Marina', 'Ferrer', 'gandia@gmail.com', '$2y$10$/MrmCv/SRn0c1d9JG47pJuozOWhX7bR6KfEGG6iZO2oB.NtFstlY2', UTC_TIMESTAMP()
FROM `jcatsen_pbio`.`organizaciones` AS o
WHERE o.nombre = 'AERIQ Gandia'
  AND NOT EXISTS (SELECT 1 FROM `jcatsen_pbio`.`usuarios`)
ORDER BY o.id
LIMIT 1;

SELECT u.email, u.nombre, u.apellidos, u.rol, o.nombre AS organizacion
FROM `jcatsen_pbio`.`usuarios` AS u
JOIN `jcatsen_pbio`.`organizaciones` AS o ON o.id = u.organizacion_id
WHERE u.email = 'gandia@gmail.com' AND u.rol = 'admin';
