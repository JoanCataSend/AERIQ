# Base de datos AERIQ

Un único esquema legible, sin la combinación anterior de tablas Sprint 0 y tablas organizativas añadidas. Los nombres describen lo que guardan y las relaciones tienen claves foráneas.

## Archivos

- [schema.sql](schema.sql): once tablas, restricciones, índices y dos vistas de lectura. No borra ninguna base.
- [seed-demo.sql](seed-demo.sql): datos ficticios para una instalación nueva. No se importa automáticamente en producción.
- [Diseño](../../doc/base-de-datos.md): relaciones y decisiones del modelo.

## Leer los datos sin complicaciones

En phpMyAdmin abre `vista_mediciones`: verás fecha UTC, organización, campaña, nombre del Orb, trabajador, valor, unidad y origen. `vista_flota` muestra los dispositivos y sus asignaciones actuales con nombres, no solo identificadores.

```sql
SELECT * FROM vista_mediciones ORDER BY fecha_utc DESC;
SELECT * FROM vista_flota;
```

`mediciones` referencia una `asignacion`; esta identifica organización, Orb, trabajador y campaña. Cambiar de trabajador cierra la asignación anterior sin cambiar sus lecturas. `magnitudes` define el nombre, unidad y rango del ozono y la temperatura.

## Instalación nueva

### Recrear una base existente desde phpMyAdmin (Plesk)

Exporta primero una copia SQL. Selecciona exclusivamente la base de AERIQ e importa [recrear-demo-plesk.sql](recrear-demo-plesk.sql). Este archivo elimina todas sus tablas y vistas, incluidas las antiguas, y carga el esquema y los ejemplos en una sola operación. No importes después `schema.sql` ni `seed-demo.sql`: ya están incluidos. Conserva el nombre de la base, los usuarios y los permisos. No elimina rutinas ni eventos programados ajenos al proyecto. El borrado no se puede deshacer con ROLLBACK.

Crea una base vacía e importa primero `schema.sql`. Añade `seed-demo.sql` únicamente si quieres la demostración. Configura la conexión privada en `src/web/server/SDBaseDatos.php`. No se mantiene un segundo esquema heredado para ejecutar la aplicación.

## Recreación con copia previa

```powershell
# Solo mostrar el plan. No conecta ni borra.
python scripts/reset-database.py --database aeriq_demo --user root --mysql-bin C:/xampp/mysql/bin --demo --dry-run

# Recrear exactamente esa base, después de guardar una copia si ya existe.
python scripts/reset-database.py --database aeriq_demo --user root --mysql-bin C:/xampp/mysql/bin --demo --confirm RECREAR:aeriq_demo
```

El script pide la contraseña sin mostrarla, rechaza bases del sistema y se detiene si no puede obtener la copia. Los respaldos privados se guardan en `.local-backups/`. Para automatizar una prueba local puede leerse `AERIQ_DB_PASSWORD` del entorno; no se pasa por argumentos.

## Conservar datos anteriores

Prepara otra base vacía con `schema.sql`, sin datos demo. Crea dos configuraciones privadas con el formato de `SDBaseDatos.example.php`, una para origen y otra para destino:

```text
php scripts/migrate-legacy.php --source=origen.php --target=destino.php
php scripts/migrate-legacy.php --source=origen.php --target=destino.php --apply
```

La primera orden es un inventario de solo lectura. La segunda conserva cuentas, organizaciones/campañas/Orb y lecturas de las estructuras anteriores compatibles; no borra el origen. Conserva valores y fechas, no inventa GPS ni MAC y no convierte unidades. Rechaza un destino con datos o unidades desconocidas, en vez de sobrescribir o interpretar a ciegas. Las sesiones/códigos anteriores no se importan. La antigua tabla cartográfica ilustrativa no se utiliza como fuente de mediciones reales; consérvala en el respaldo si existe.

## Cuentas de demostración

Contraseña de todas: `AeriqDemo-2026!`. Son cuentas ficticias con dominios `.example`, no direcciones de contacto.

| Correo | Función |
| --- | --- |
| `admin@gandia.example` | Administradora del ayuntamiento demo |
| `operador@gandia.example` | Trabajador: primer acceso obliga a activar/cambiar contraseña |
| `analista@gandia.example` | Informes y exportación del ayuntamiento |
| `comercial@codigonorte.example` | Solicitudes comerciales |
| `admin@empresaverde.example` | Administradora de otra organización |
| `operador@empresaverde.example` | Trabajador de la segunda organización |

Hay tres Orb con MAC ficticias, dos campañas, nueve mediciones simuladas y tres solicitudes. Cambia la MAC por la real para probar BLE. Ningún valor de ejemplo se presenta como medición real; la segunda campaña es privada. No publiques estas cuentas conocidas en un entorno real.
