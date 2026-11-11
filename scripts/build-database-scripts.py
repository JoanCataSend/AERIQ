"""Regenera los SQL de recreación y reinstalación a partir de schema.sql y seed-demo.sql.

Uso: python scripts/build-database-scripts.py
Reescribe recrear-demo-plesk.sql, recrear-jcatsen-pbio*.sql y el bloque de esquema de
reinicio/01-instalar.sql. No conecta con ninguna base de datos.
"""
import re
from pathlib import Path

DB = Path(__file__).resolve().parents[1] / 'src' / 'database'
schema = (DB / 'schema.sql').read_text(encoding='utf-8')
seed = (DB / 'seed-demo.sql').read_text(encoding='utf-8')
names = re.findall(r'CREATE (?:TABLE|VIEW) (\w+)', schema)

def qualify(sql, db='jcatsen_pbio'):
    pattern = r'\b(CREATE TABLE|CREATE VIEW|INSERT INTO|REFERENCES|FROM|JOIN) (' + '|'.join(names) + r')\b'
    return re.sub(pattern, lambda m: f'{m.group(1)} `{db}`.`{m.group(2)}`', sql)

def write(path, text):
    path.write_bytes(text.replace('\r\n', '\n').replace('\n', '\r\n').encode('utf-8'))

def drop_all(db_expr, qualified_db=None):
    where = f"TABLE_SCHEMA = {db_expr}"
    return f"""SET @aeriq_old_fk = @@FOREIGN_KEY_CHECKS;
SET @aeriq_old_concat = @@SESSION.group_concat_max_len;
SET SESSION group_concat_max_len = 16777216;
SET FOREIGN_KEY_CHECKS = 0;

-- Primero todas las vistas, incluidas las de versiones anteriores.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_SCHEMA,'`','``'), '`.`',
 REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES
WHERE {where} AND TABLE_TYPE = 'VIEW';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP VIEW IF EXISTS ', @aeriq_objects));
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;

-- Despues todas las tablas. Sus triggers se eliminan junto con ellas.
SELECT GROUP_CONCAT(CONCAT('`', REPLACE(TABLE_SCHEMA,'`','``'), '`.`',
 REPLACE(TABLE_NAME,'`','``'), '`') SEPARATOR ',') INTO @aeriq_objects
FROM information_schema.TABLES
WHERE {where} AND TABLE_TYPE = 'BASE TABLE';
SET @aeriq_sql = IF(@aeriq_objects IS NULL, 'DO 0', CONCAT('DROP TABLE IF EXISTS ', @aeriq_objects));
PREPARE aeriq_reset_stmt FROM @aeriq_sql;
EXECUTE aeriq_reset_stmt;
DEALLOCATE PREPARE aeriq_reset_stmt;
SET FOREIGN_KEY_CHECKS = @aeriq_old_fk;
SET SESSION group_concat_max_len = @aeriq_old_concat;
"""

# 1. Recreación genérica (base seleccionada en phpMyAdmin).
demo = f"""-- AERIQ: RECREACION COMPLETA CON DATOS DEMO PARA PHPMYADMIN / PLESK.
-- ATENCION: elimina TODAS las tablas y vistas de la base seleccionada.
-- Exporta una copia SQL antes de importar. No se puede deshacer con ROLLBACK.
-- Selecciona exclusivamente la base de AERIQ en phpMyAdmin antes de importar.
-- Conserva la base, sus usuarios y permisos: no necesita DROP DATABASE.
-- No elimina procedimientos ni eventos programados ajenos a AERIQ.
-- Generado a partir de schema.sql y seed-demo.sql; no editar a mano.
SET NAMES utf8mb4;
{drop_all('DATABASE()')}
-- Esquema actual.
{schema}
-- Datos de ejemplo.
{seed}
SELECT 'AERIQ recreada con datos demo' AS resultado;
"""
write(DB / 'recrear-demo-plesk.sql', demo)

# 2. Recreación específica de jcatsen_pbio (nombres cualificados).
jc = f"""-- AERIQ: reconstruccion especifica de jcatsen_pbio para phpMyAdmin.
-- BORRA todas las tablas y vistas de jcatsen_pbio y TODOS sus datos.
-- Exporta una copia SQL antes de importar. El borrado no admite ROLLBACK.
-- Incluye esquema y ejemplos; no importar despues los otros dos SQL.
-- Para conservar los datos actuales usa migracion-v2-catalogos.sql en su lugar.
-- Generado a partir de schema.sql y seed-demo.sql; no editar a mano.
USE `jcatsen_pbio`;
SET NAMES utf8mb4;
{drop_all("'jcatsen_pbio'")}
USE `jcatsen_pbio`;
{qualify(schema)}
{qualify(seed)}
SELECT DATABASE() AS base_actual, COUNT(*) AS usuarios_demo FROM `jcatsen_pbio`.`usuarios`;
"""
for name in ['recrear-jcatsen-pbio.sql', 'recrear-jcatsen-pbio-v2.sql']:
    write(DB / name, jc)

# 3. Reinstalación por fases: 01-instalar conserva cabecera y pie, cambia el esquema.
install_path = DB / 'reinicio' / '01-instalar.sql'
install = install_path.read_text(encoding='utf-8').replace('\r\n', '\n')
start = install.index('-- AERIQ · Esquema único')
end = install.index('-- Conserva el l')
install = install[:start] + qualify(schema) + '\n' + install[end:]
install = install.replace('instala las once tablas', 'instala las tablas')
write(install_path, install)
print('Tablas:', [n for n in names if not n.startswith('vista_')])
print('Vistas:', [n for n in names if n.startswith('vista_')])
