# Servidor PHP

Esta carpeta contiene la lógica utilizada por los endpoints PHP situados un
nivel por encima. Su contenido no debe descargarse por HTTP; `.htaccess`
contiene el bloqueo para Apache.

| Archivo | Responsabilidad |
| --- | --- |
| `Logica.php` | Conexión MySQLi, validación de lecturas, consultas e inserción |
| `AuthCore.php` | Validación de cuenta, códigos, límites de intentos, correo y sesión web |
| `SDBaseDatos.example.php` | Plantilla de conexión MariaDB |
| `MailConfig.example.php` | Plantilla de remitente para `mail()` |
| `SMTPConfig.example.php` | Plantilla de conexión SMTP alternativa |

La configuración real usa los nombres sin `.example`: `SDBaseDatos.php`,
`MailConfig.php` y `SMTPConfig.php`. Son privados y están ignorados por Git.
La copia local de la configuración de base de datos se ha conservado al reorganizar.

`AuthCore.php` carga `Logica.php`. Los endpoints `auth.php` y `mobile-auth.php`
comparten esta lógica y las mismas tablas de cuentas. El endpoint `api.php`
utiliza `Logica.php` para las medidas y el mapa simulado.

Para SMTP instala las dependencias con Composer en `src/web/`. En ausencia de
`SMTPConfig.php`, el código intenta usar `mail()` del alojamiento.

Consulta [API](../../../doc/api.md), [base de datos](../../../doc/base-de-datos.md)
y [despliegue](../../../doc/despliegue.md).
