# Servidor AERIQ

| Archivo | Responsabilidad |
| --- | --- |
| `Logica.php` | Conexión privada MySQLi en UTF-8/UTC |
| `AuthCore.php` | Registro, contraseña, códigos y correo reutilizados |
| `AeriqCore.php` | Organizaciones, campañas, asignaciones, mediciones, informes y solicitudes |
| `AeriqHttp.php` | Cookie/CSRF, token móvil y errores HTTP |
| `FirebaseAuth.php` | Verifica el ID token de Google y vincula `usuarios.firebase_uid` sin crear cuentas |
| `*.example.php` | Plantillas sin credenciales |

`SDBaseDatos.php`, `SMTPConfig.php`, `MailConfig.php` y `FirebaseConfig.php` son privados y están ignorados por Git. `.htaccess` bloquea las descargas HTTP de esta carpeta. Las funciones/organización se consultan en servidor, nunca se toman como autorización declarada en JSON.

Firebase solo se usa como proveedor de identidad de Google. La configuración web (`apiKey`, `appId`) es pública; no se guarda ninguna clave de servicio. El servidor verifica la firma con los certificados públicos de Google y exige que el correo ya exista en `usuarios`.

La base ahora integra rol y activación en `usuarios` y conserva asignaciones históricas. No hay una tabla de permisos superpuesta al esquema antiguo. [Base de datos](../../../doc/base-de-datos.md) y [API](../../../doc/api.md) documentan las entidades y contratos.
