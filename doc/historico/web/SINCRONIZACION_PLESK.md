# AERIQ: versión local de la web de Plesk

## Qué se ha sincronizado

El 2 de noviembre de 2026 se han incorporado a `AERIQ_Web/` los archivos de
`AERIQ/` contenidos en `tal.zip`: 12 archivos actualizados, 11 añadidos y 35
que ya coincidían. La carpeta `server/` existente coincidía íntegramente con el ZIP.
Android no se ha modificado porque el ZIP solo contiene la web y su backend.

El diseño, los recursos, los formularios y la lógica proceden de la copia de Plesk.
Se ha añadido una nota al README para distinguir su estado actual del historial.
No se ha copiado `aa.zip`: es un duplicado de `server/` que contiene también su
configuración de base de datos y no es necesario para ejecutar la web.
La copia de los archivos locales sustituidos está en `.local-backups/`, fuera
de la carpeta web e ignorada por Git.

## Estructura

- `auth-client.js` y `auth.php`: sesión web con cookie, CSRF y operaciones de cuenta.
- `verificar.html` y `recuperar.html`: confirmación de correo y recuperación.
- `pbio-client.js` y `api.php`: consultas de medidas, dispositivos, tipos y mapa simulado.
- `mobile-auth.php`: autenticación Android mediante tokens, compartiendo las cuentas web.
- `server/AuthCore.php` y `server/Logica.php`: correo, validación y acceso a MariaDB.
- `server/SDBaseDatos.php`: configuración privada de conexión, conservada sin cambios.
- `server/*.example.php`: ejemplos para configurar una instalación diferente.
- `vendor/.htaccess`: protección del directorio de dependencias.

## Ejecutar o desplegar

El contenido de `AERIQ_Web/` corresponde al directorio `/AERIQ/` del alojamiento.
Necesita PHP 8.1 o superior, MySQLi con `get_result()` y MariaDB compatible con
las tablas existentes. El cambio de contraseña web utiliza también `mb_strlen()`
y requiere la extensión `mbstring`.

Para una instalación local, configura `server/SDBaseDatos.php` con una base local.
La configuración exportada de Plesk no implica que esa base esté disponible desde
este ordenador. Usa un servidor PHP; para reproducir las cookies seguras de la
sesión web utiliza HTTPS. Un servidor estático no ejecuta los endpoints `.php`.

El ZIP no incluye un volcado SQL ni las migraciones: para reproducir exactamente
los datos y el esquema hay que exportar la base desde phpMyAdmin/Plesk e importarla
en la instalación de destino. El código hace referencia a `Dispositivo`,
`TipoMedida`, `Medida`, `AeriqUsuario`, `AeriqCodigoEmail`, `AeriqAuthRate`,
`AeriqSesionMovil` y `AeriqMapaDemo`. No se han creado tablas ni datos inventados.

El correo usa `mail()` del alojamiento si no existe `server/SMTPConfig.php`.
Para SMTP, configura ese archivo a partir del ejemplo y ejecuta
`composer install --no-dev` en `AERIQ_Web/`. El ZIP solo incluye la protección
de `vendor/`, no PHPMailer instalado. Para `mail()`, el remitente se obtiene de
`MailConfig.php`, `AERIQ_MAIL_FROM` o del dominio del servidor.

Mantén los `.htaccess` al desplegar en Apache/Plesk. Si sirves la web con otro
servidor, configura allí el bloqueo de acceso HTTP a `server/`, `vendor/` y
los archivos de configuración. Los archivos privados están excluidos de Git.

## Comprobación funcional pendiente en un entorno con PHP y MariaDB

Comprueba `api.php?accion=health`, las lecturas del panel PBIO y los sensores
simulados del mapa. Después verifica registro, recepción del código, login,
edición de perfil, recuperación de contraseña y cierre de sesión.
Estas operaciones necesitan una base y un servicio de correo configurados.
La sincronización local no cambia el servidor Plesk.

Android mantiene los endpoints que ya tenía: autenticación en
`/AERIQ/mobile-auth.php` y envío de mediciones en `/biometria/api.php`.
Son rutas distintas; la API copiada en esta web no cambia automáticamente
el destino de envío de la aplicación.
