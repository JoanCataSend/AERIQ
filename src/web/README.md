# AERIQ Web

Sitio HTML/CSS/JavaScript con backend PHP, sincronizado desde la copia de Plesk
`tal.zip` el 2 de noviembre de 2026. Esta carpeta corresponde a la raíz pública
`/AERIQ/` del alojamiento; se conservan sus rutas relativas.

## Pantallas

| Archivo | Función |
| --- | --- |
| `index.html` | Presentación, visor 3D, planes y contacto de demostración |
| `mapa.html` | Mapa Leaflet/OSM y sensores simulados consultados en MariaDB |
| `sensor.html` | Galería y ficha del dispositivo |
| `login.html`, `registro.html` | Inicio de sesión y alta de cuentas |
| `verificar.html`, `recuperar.html` | Códigos por correo y recuperación |
| `perfil.html` | Datos personales y contraseña; otras secciones son maqueta |
| `sensores.html` | Tabla de demostración y panel de lecturas PBIO almacenadas |
| `carrito.html`, `checkout.html` | Compra de demostración, sin cobros |

## Organización

- `app.js`: comportamiento de las pantallas y coordinación con los clientes.
- `styles.css`: estilos y adaptación a móvil compartidos.
- `auth-client.js`: sesión web y peticiones de cuenta con CSRF.
- `pbio-client.js`: peticiones de medidas y mapa.
- `api.php`, `auth.php`, `mobile-auth.php`: entradas HTTP del servidor.
- [server/](server/README.md): lógica de negocio, correo y configuración privada.
- [assets/](assets/README.md): imágenes, iconos y modelo 3D.
- `composer.json`: dependencia opcional PHPMailer para transporte SMTP.

## Ejecutar

La versión completa requiere PHP 8.1+, MySQLi, `mbstring`, MariaDB y correo
configurado. Para las sesiones web utiliza HTTPS, porque la cookie es segura.
Un servidor estático permite revisar la presentación pero no autenticar cuentas
ni consultar la base de datos.

Lee [despliegue](../../doc/despliegue.md) antes de preparar un servidor.
Desde la raíz del repositorio, `python scripts/build-plesk-package.py` crea
una carpeta publicable sin documentación ni credenciales.

La base de datos y las dependencias SMTP no venían en el ZIP. No se consideran
verificadas por copiar los archivos. Los pagos y el acceso social siguen siendo
demostrativos. Más detalle en [diseño web](../../doc/web.md).
