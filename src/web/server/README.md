# Servidor AERIQ

| Archivo | Responsabilidad |
| --- | --- |
| `Logica.php` | Conexión privada MySQLi en UTF-8/UTC |
| `AuthCore.php` | Reglas de autenticación y sesión; usa el repositorio de cuentas/códigos y el puerto de correo |
| `AeriqCore.php` | Operaciones de aplicación, organizaciones, campañas, asignaciones, mediciones, informes y solicitudes; conserva consultas de casos de uso y coordina transacciones/correo |
| `AeriqHttp.php` | Cookie/CSRF, token móvil y frontera HTTP; carga explícitamente autenticación, dominio y mapeo de errores |
| `../business_logic/DomainError.php` | Errores semánticos del dominio sin estado HTTP |
| `../communication/HttpErrorMapper.php` | Traducción de errores semánticos a estados HTTP |
| `../communication/MailTransport.php` | Configuración y envío SMTP/PHPMailer/mail(); expone operaciones de envío y disponibilidad |
| `../database/SqlGateway.php` | Conexión y ejecución SQL parametrizada común |
| `../database/AuthRepository.php` | Consultas de cuentas, límites de acceso y códigos de correo |
| `../database/AeriqRepository.php` | Lecturas, escrituras, transacciones y consultas de entidades AERIQ |
| `../database/AeriqQueryCatalog.php` | Sentencias SQL agrupadas por caso de uso |
| `FirebaseAuth.php` | Verifica el ID token de Google y vincula la cuenta en `identidades_externas` sin crear cuentas |
| `*.example.php` | Plantillas sin credenciales |

`SDBaseDatos.php`, `SMTPConfig.php`, `MailConfig.php` y `FirebaseConfig.php` son privados y están ignorados por Git. `.htaccess` bloquea las descargas HTTP de esta carpeta. Las funciones/organización se consultan en servidor, nunca se toman como autorización declarada en JSON.

Firebase solo se usa como proveedor de identidad de Google. La configuración web (`apiKey`, `appId`) es pública; no se guarda ninguna clave de servicio. El servidor verifica la firma con los certificados públicos de Google y exige que el correo ya exista en `usuarios`.

La base ahora integra rol y activación en `usuarios` y conserva asignaciones históricas. No hay una tabla de permisos superpuesta al esquema antiguo. [Base de datos](../../../doc/base-de-datos.md) y [API](../../../doc/api.md) documentan las entidades y contratos.
