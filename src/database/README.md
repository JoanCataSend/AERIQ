# Base de datos AERIQ

Un único esquema legible, sin la combinación anterior de tablas Sprint 0 y tablas organizativas añadidas. Los nombres describen lo que guardan y las relaciones tienen claves foráneas.

## Archivos

- [Reinstalación limpia en Plesk](reinicio/LEEME.md) con inventario, borrado y creación del esquema, y alta del primer administrador.
- [schema.sql](schema.sql): 26 tablas (catálogos, cuentas, campañas, flota con batería, mediciones, solicitudes y seguridad), restricciones, índices y cuatro vistas de lectura. No borra ninguna base.
- [migracion-v2-catalogos.sql](migracion-v2-catalogos.sql): lleva una base anterior (con `usuarios.rol` como `ENUM`) al esquema actual **sin perder datos**. Repetible. Es la opción para la base de Plesk.
- [migracion-v3-solicitudes.sql](migracion-v3-solicitudes.sql): formulario de solicitudes por tipo de organización y servicio (`tipos_organizacion`, `objetivos_medicion`, `solicitud_objetivos`). Repetible, conserva las solicitudes. Aplicar después de la v2.
- [migracion-v4-bateria.sql](migracion-v4-bateria.sql): añade la batería de los Orb (`lecturas_bateria`). Repetible, no borra datos. Aplicar después de la v3 (funciona también sobre una v2).
- [anadir-firebase-google.sql](anadir-firebase-google.sql): migración antigua para bases anteriores a la v2; en una base v2 no hace nada.
- Los `recrear-*.sql` y `reinicio/01-instalar.sql` se generan a partir de `schema.sql` y `seed-demo.sql` con `python scripts/build-database-scripts.py`. Si cambias el esquema o los datos demo, vuelve a ejecutarlo; la prueba de integración comprueba que coinciden.
- [seed-demo.sql](seed-demo.sql): datos ficticios para una instalación nueva. No se importa automáticamente en producción.
- [Diseño](../../doc/base-de-datos.md): relaciones y decisiones del modelo.

## Leer los datos sin complicaciones

En phpMyAdmin abre `vista_mediciones`: verás fecha UTC, organización, campaña, nombre del Orb, trabajador, valor, unidad y origen. `vista_flota` muestra los dispositivos y sus asignaciones actuales con nombres, no solo identificadores.

```sql
SELECT * FROM vista_mediciones ORDER BY fecha_utc DESC;
SELECT * FROM vista_flota;
```

`mediciones` referencia una `asignacion`; esta identifica organización, Orb, trabajador y campaña. Cambiar de trabajador cierra la asignación anterior sin cambiar sus lecturas. `magnitudes` define el nombre, unidad, rango y umbrales de referencia del ozono y la temperatura. `vista_usuarios` muestra cada cuenta con su rol y `vista_solicitudes` cada solicitud con su servicio y estado.

```sql
SELECT * FROM vista_usuarios;
SELECT * FROM vista_solicitudes ORDER BY recibida_en DESC;
```

## Instalación nueva

### Recrear una base existente desde phpMyAdmin (Plesk)

Para una base real, sigue [Reinstalar AERIQ](reinicio/LEEME.md). [recrear-demo-plesk.sql](recrear-demo-plesk.sql) es una instantánea anterior con cuentas y mediciones ficticias para demostración.

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

Para ampliar el mapa de playa, importa [anadir-mediciones-playa-gandia.sql](anadir-mediciones-playa-gandia.sql) en `jcatsen_pbio`. Añade 54 lecturas públicas simuladas (18 verdes, 18 naranjas y 18 rojas), con posiciones ilustrativas repartidas por el entorno de la playa de Gandía. No borra datos y puede repetirse sin duplicarlos. Verlas en **Simulados**; sus fechas se actualizan al importarlas. Requiere las cuentas y el esquema demo actuales.

Para añadir ejemplos sin recrear la base de Plesk, importa [anadir-mediciones-publicas-privadas.sql](anadir-mediciones-publicas-privadas.sql) en `jcatsen_pbio`, una vez instalado el esquema actual y las cuentas demo. Añade tres campañas con doce lecturas simuladas cada una: campus público, recinto municipal privado y recinto de empresa privado. Se puede repetir sin duplicar lecturas; actualiza sus fechas. En el mapa selecciona **Simulados**. El mapa público muestra la campaña pública; el mapa de organización requiere sesión y restringe las privadas a su organización y, para trabajadores, a sus propias asignaciones. No cambia contraseñas ni activa trabajadores pendientes.

Contraseña de todas: `AeriqDemo-2026!`. Son cuentas ficticias con dominios `.example`, no direcciones de contacto.

| Correo | Función |
| --- | --- |
| `admin@gandia.example` | Administradora del ayuntamiento demo |
| `operador@gandia.example` | Trabajador: primer acceso obliga a activar/cambiar contraseña |
| `analista@gandia.example` | Informes y exportación del ayuntamiento |
| `comercial@codigonorte.example` | Solicitudes comerciales |
| `admin@empresaverde.example` | Administradora de otra organización |
| `operador@empresaverde.example` | Trabajador de la segunda organización |

Hay tres Orb con MAC ficticias, dos zonas, dos campañas (la segunda con umbrales propios), nueve mediciones simuladas y tres solicitudes con su historial. Cambia la MAC por la real para probar BLE. Ningún valor de ejemplo se presenta como medición real; la segunda campaña es privada. No publiques estas cuentas conocidas en un entorno real.
