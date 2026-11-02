# Base de datos MariaDB

La implementación PHP espera una base ya inicializada. El ZIP de Plesk no
incluía esquema SQL, migraciones ni datos exportados; esta carpeta documenta
esa dependencia y el procedimiento para incorporarla sin inventar el esquema.

## Obtener una copia reproducible

1. Exporta desde phpMyAdmin/Plesk la estructura de la base utilizada por AERIQ.
2. Revisa el SQL y separa estructura, datos de demostración y datos privados.
3. Incorpora aquí únicamente el esquema y las migraciones revisadas, sin cuentas,
   códigos de correo, hashes de sesiones ni datos personales.
4. Importa la copia privada completa solo en el entorno autorizado de destino.
5. Configura `../web/server/SDBaseDatos.php` para ese entorno y comprueba las API.

No hay scripts SQL ejecutables en esta entrega. Los nombres y campos observados
en las consultas están en [el documento de base de datos](../../doc/base-de-datos.md).
Los campos observados no sustituyen una exportación con claves, tipos e índices.
