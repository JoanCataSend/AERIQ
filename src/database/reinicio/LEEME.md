# Reinstalar AERIQ en Plesk

Abre la base `jcatsen_pbio` en phpMyAdmin. Importa estos archivos en orden desde la pestaña **Importar**.

1. [00-formatear.sql](00-formatear.sql) elimina las tablas, vistas y datos anteriores. Deja un marcador vacío para conservar el siguiente identificador de cuenta entre importaciones.
2. [01-instalar.sql](01-instalar.sql) instala las once tablas, dos vistas y el catálogo de ozono y temperatura. Elimina el marcador.
3. [02-admin.sql](02-admin.sql) crea la organización **AERIQ Gandia** y la administradora **Marina Ferrer**. Está preparado para importar directamente.

Si el archivo anterior de formateo falló al crear `_reinicio_aeriq` con el error 1044 y **Estructura** muestra que la base está vacía, continúa con el archivo actualizado `01-instalar.sql`. El marcador es opcional para instalar en una base vacía.

La cuenta inicial es `gandia@gmail.com` con la contraseña `1234`. Puedes entrar por correo y contraseña tanto en la web como en Android. Cambia esa contraseña tras el primer acceso si la web está publicada.

La instalación conserva el nombre y los permisos de la base en MariaDB. Las cuentas y sesiones anteriores se eliminan en el primer paso. Vuelve a dar de alta y asignar los Orb después de instalar.

`../schema.sql` contiene el esquema de referencia. La prueba de integración verifica que coincide con el bloque de definiciones de `01-instalar.sql`.
