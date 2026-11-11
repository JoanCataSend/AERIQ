-- AERIQ: vincula una cuenta existente con Google mediante Firebase Authentication.
-- No crea usuarios ni modifica correos. Ejecutar una vez sobre la base de AERIQ
-- (phpMyAdmin: selecciona la base, pestaña Importar).
-- Es repetible: si la columna o el índice ya existen, no hace cambios.
-- OBSOLETA desde el esquema v2: los accesos externos viven en identidades_externas.
-- Si esa tabla existe (base migrada con migracion-v2-catalogos.sql), no hace nada.

SET NAMES utf8mb4;

SET @add_column := (
  SELECT IF(
    COUNT(*) = 0 AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'identidades_externas') = 0,
    'ALTER TABLE usuarios ADD COLUMN firebase_uid VARCHAR(128) NULL AFTER password_hash',
    'SELECT ''firebase_uid ya existe'' AS aviso'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'usuarios'
    AND COLUMN_NAME = 'firebase_uid'
);
PREPARE stmt FROM @add_column;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_index := (
  SELECT IF(
    COUNT(*) = 0 AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios' AND COLUMN_NAME = 'firebase_uid') = 1,
    'ALTER TABLE usuarios ADD UNIQUE KEY usuario_firebase_uid (firebase_uid)',
    'SELECT ''usuario_firebase_uid ya existe'' AS aviso'
  )
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'usuarios'
    AND INDEX_NAME = 'usuario_firebase_uid'
);
PREPARE stmt FROM @add_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
