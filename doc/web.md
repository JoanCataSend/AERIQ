# Diseño web

## Entrada y navegación

Las once páginas están en `src/web/`. Comparten `styles.css` y `app.js`.
Las funciones de inicialización comprueban si existe el elemento correspondiente
antes de registrar sus eventos. Las páginas cargan los clientes que necesitan
antes del script de interfaz.

## Estado

`localStorage` conserva cantidades del carrito y apariencia del mapa con prefijo
`aeriq_`. La autenticación vigente se consulta a `auth.php`; la versión actual
ya no utiliza un usuario ficticio de localStorage para acreditar una cuenta.
Contraseñas y códigos se envían al servidor, sin guardarse en ese almacenamiento.

## Datos y servicios

- `auth-client.js`: obtiene sesión/CSRF, envía operaciones de cuenta y renueva la
  caché de sesión tras operaciones relevantes.
- `pbio-client.js`: consultas de medidas, tipos, dispositivos, última lectura,
  salud y sensores simulados. Incluye un tiempo máximo de petición.
- Mapa: Leaflet, teselas OpenStreetMap y consulta de `mapa-demo` cada 30 segundos
  si la página está visible. El modo oscuro filtra visualmente las teselas.
- Panel PBIO: consulta de medidas cada 15 segundos si la página está visible;
  agrupa la lectura más reciente disponible por dispositivo y tipo.
- Producto: galería local y visor model-viewer con archivo GLB local.

## Límites de la interfaz

Los formularios de contacto y compra no envían pedidos ni cobran. Los botones
sociales no implementan OAuth. La tabla de dispositivos, sus nombres y controles
de privacidad siguen siendo una demostración; el panel PBIO es un flujo diferente.
Las direcciones y pedidos del perfil tampoco tienen persistencia implementada.

## Cambios frecuentes

Para una pantalla cambia el HTML y las clases en el CSS compartido. Las llamadas
HTTP pertenecen a los clientes, y las operaciones de servidor a PHP. Al añadir una
página actualiza el [README web](../src/web/README.md) y comprueba referencias con
`python scripts/audit-repository.py` desde la raíz.
