-- Paso 00. Borra todas las tablas y vistas de jcatsen_pbio, incluidos sus datos.
USE `jcatsen_pbio`;
SET NAMES utf8mb4;
-- Evita que una sesión web o cola móvil antigua coincida con una cuenta nueva.
SELECT GREATEST(UNIX_TIMESTAMP(), COALESCE(MAX(AUTO_INCREMENT), 1))
INTO @aeriq_next_user
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'jcatsen_pbio' AND TABLE_NAME IN ('usuarios', 'AeriqUsuario', '_reinicio_aeriq');

SET @aeriq_old_fk = @@FOREIGN_KEY_CHECKS;
SET @aeriq_old_concat = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 16777216;
SET FOREIGN_KEY_CHECKS = 0;

-- Primero todas las vistas, incluidas las de versiones anteriores.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_SCHEMA,'`','``'), '`.`',
 REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'jcatsen_pbio' AND TABLE_TYPE = 'VIEW';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP VIEW IF EXISTS ', @aeriq_objects));
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;

-- Despues todas las tablas. Sus triggers se eliminan junto con ellas.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_SCHEMA,'`','``'), '`.`',
 REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'jcatsen_pbio' AND TABLE_TYPE = 'BASE TABLE';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP TABLE IF EXISTS ', @aeriq_objects));
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;
SET FOREIGN_KEY_CHECKS = @aeriq_old_fk;
SET SESSION group_concat_max_len = @aeriq_old_concat;

-- Este marcador vacío conserva el siguiente ID entre importaciones.
CREATE TABLE `jcatsen_pbio`.`_reinicio_aeriq` (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
) ENGINE=InnoDB;
SET @aeriq_sql = CONCAT('ALTER TABLE `jcatsen_pbio`.`_reinicio_aeriq` AUTO_INCREMENT = ', @aeriq_next_user);
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;

SELECT 'Formateo completado. Importa 01-instalar.sql.' AS resultado;
