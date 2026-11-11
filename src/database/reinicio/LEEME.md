# Reinstalar AERIQ en Plesk

Abre la base `jcatsen_pbio` en phpMyAdmin. Importa estos archivos en orden desde la pestaña **Importar**.

1. [00-formatear.sql](00-formatear.sql) elimina las tablas, vistas y datos anteriores. Deja un marcador vacío para conservar el siguiente identificador de cuenta entre importaciones.
2. [01-instalar.sql](01-instalar.sql) instala las 25 tablas, las 4 vistas y los catálogos (roles, permisos, magnitudes, modelos de Orb, servicios, tipos de organización, objetivos de medición y estados). Elimina el marcador.
3. [02-admin.sql](02-admin.sql) crea la organización **AERIQ Gandia** y la administradora **Marina Ferrer**. Está preparado para importar directamente.
4. [03-simulacion.sql](03-simulacion.sql) es **opcional**: añade datos simulados para probar. Crea tres zonas, tres campañas (dos públicas y una privada), tres Orb simulados, 45 lecturas de ozono y 9 de temperatura de las últimas horas, y tres solicitudes con su historial. También crea cuentas de prueba, todas con la contraseña `1234` (igual que la administradora): `operador.simulacion@aeriq.example`, `operadora.simulacion@aeriq.example`, `analista.simulacion@aeriq.example` y `comercial.simulacion@codigonorte.example`. No borra nada y se puede repetir: las lecturas vuelven a quedar en las últimas horas para que se vean en el mapa con el filtro **Simulados**.

Si el archivo anterior de formateo falló al crear `_reinicio_aeriq` con el error 1044 y **Estructura** muestra que la base está vacía, continúa con el archivo actualizado `01-instalar.sql`. El marcador es opcional para instalar en una base vacía.

La cuenta inicial es `gandia@gmail.com` con la contraseña `1234`. Puedes entrar por correo y contraseña tanto en la web como en Android. Cambia esa contraseña tras el primer acceso si la web está publicada.

La instalación conserva el nombre y los permisos de la base en MariaDB. Las cuentas y sesiones anteriores se eliminan en el primer paso. Vuelve a dar de alta y asignar los Orb después de instalar.

`../schema.sql` contiene el esquema de referencia. La prueba de integración verifica que coincide con el bloque de definiciones de `01-instalar.sql`.
