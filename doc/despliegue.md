# Despliegue en Plesk

## Requisitos

- PHP 8.1 o superior con MySQLi, soporte de `get_result()` y `mbstring`.
- MariaDB con el esquema y los datos del entorno correspondiente.
- HTTPS para las cuentas y la cookie segura de sesión web.
- Apache con los `.htaccess` activos, o reglas equivalentes si se usa otro servidor.
- Servicio de correo configurado para `mail()` o SMTP.
- Acceso a los servicios externos de cartografía y librerías utilizadas por la web.

## Generar el paquete

Desde la raíz del repositorio:

```powershell
python scripts/audit-repository.py
python scripts/build-plesk-package.py
```

Se genera `.dist/plesk/AERIQ/`, con los HTML, CSS, JavaScript, recursos,
endpoints PHP, lógica de servidor, ejemplos de configuración y `.htaccess`.
El generador no copia README, documentación, archivos privados, ZIP internos
ni dependencias instaladas. Si el destino ya existe se detiene: usa `--output`
con una carpeta nueva dentro de `.dist/` para una segunda entrega.

```powershell
python scripts/build-plesk-package.py --output .dist/entrega-02/AERIQ
```

No subas la raíz completa del repositorio: publica el contenido de la carpeta
generada en el directorio web `/AERIQ/` del alojamiento. Al actualizar un sitio
existente conserva su configuración privada; no vacíes la carpeta remota.

## Base de datos

El ZIP no incluía SQL. Exporta estructura y datos desde phpMyAdmin/Plesk para
restaurar un entorno equivalente. No ejecutes migraciones inventadas sobre
la base existente. Consulta [base de datos](base-de-datos.md).

En una instalación nueva copia `server/SDBaseDatos.example.php` como
`server/SDBaseDatos.php` y configura los datos del entorno. En un sitio ya
operativo conserva su archivo privado. No lo subas a Git.

## Correo

Sin `server/SMTPConfig.php`, el backend usa `mail()` del alojamiento. El remitente
se obtiene de `server/MailConfig.php`, `AERIQ_MAIL_FROM` o el dominio del servidor.
Puedes partir de `MailConfig.example.php` para configurar un remitente autorizado.

Si necesitas SMTP, configura `server/SMTPConfig.php` a partir del ejemplo y
ejecuta `composer install --no-dev` en la raíz web publicada. El archivo
`composer.json` solicita PHPMailer; el ZIP no incluye su instalación ni un
`composer.lock`. Revisa y valida las versiones resueltas antes de fijar una entrega.

Conserva los bloqueos HTTP de `server/`, `vendor/` y archivos de configuración.
El servidor PHP de desarrollo no interpreta `.htaccess`: utiliza un entorno
local apropiado y no publiques ese servidor como alojamiento definitivo.

## Verificar la puesta en marcha

1. Abre `/AERIQ/` y comprueba navegación, galería y recursos.
2. Consulta `api.php?accion=health` y las medidas existentes, sin insertar datos.
3. Comprueba los puntos simulados del mapa y su etiqueta.
4. Con una cuenta de prueba, verifica correo, login, perfil y recuperación.
5. Valida Android y confirma si `/biometria/api.php` comparte la base esperada.

Consulta [pruebas](pruebas.md) para los pasos y resultados esperados. El script
de empaquetado solo genera archivos locales; no publica ni conecta con Plesk.
