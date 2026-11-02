# AERIQ · WEB (Proyecto Aplicaciones de Biometría y Medio Ambiente)

## Qué contiene

Nueve páginas derivadas del frame **WEB (nodo 173:76)** del archivo Figma `Proyecto`:

1. `index.html`: Landing / planes / nosotros / contacto.
2. `mapa.html`: Mapa de ozono y panel de filtros.
3. `sensor.html`: Ficha del sensor, características, galería y compra.
4. `perfil.html`: Perfil, contraseña, direcciones y pedidos de demostración.
5. `registro.html`: Registro de demostración.
6. `login.html`: Inicio de sesión de demostración.
7. `sensores.html`: Tabla de nodos y privacidad.
8. `carrito.html`: Carrito con cantidades sincronizadas.
9. `checkout.html`: Entrega y opciones de pago sin cobros reales.

**Tecnologías:** HTML5 semántico, **un solo `styles.css` compartido**, JavaScript ES6 en **un solo `app.js` compartido**. Sin React, Bootstrap, Tailwind, Node ni servidor obligatorios. Responsive con CSS Grid, Flexbox y media queries.

## Cómo abrir

Abrir `index.html` con un navegador actualizado. Para mejor comprobación, usar la extensión **Live Server** de VS Code o ejecutar desde esta carpeta: `python -m http.server 8000` y visitar `http://localhost:8000`.

## Qué funciona

- Navegación entre todas las páginas, menús móviles y enlaces interiores.
- Producto -> añadir al carrito -> cantidades -> subtotal y checkout.
- Selección de entrega y método de pago, totales orientativos (envío de **6,90 €** cuando se selecciona domicilio).
- Mapa ilustrativo de Valencia: seleccionar sensores, alternar capas y exportar CSV **de demostración**.
- Tabla de sensores: filtros, búsqueda, cambiar nombre y controles de privacidad **visuales**.
- Formularios con validación del navegador y mensajes. Las cuentas y preferencias de demostración se almacenan en `localStorage` del navegador.
- Accesibilidad básica: etiquetas, estados de foco y navegación con teclado.

## Límites importantes (para explicar en clase)

- **No hay backend operativo, autenticación real, pagos, mapas geográficos reales ni sensores BLE conectados.** No uses contraseñas ni datos personales auténticos.
- La opción de pago representa un flujo UX/UI: **no cobra ni registra pedidos**. Google y Apple tampoco están integrados.
- Las posiciones, concentraciones, estados, notificaciones y perfiles son **datos ficticios**, igual que en el Figma.
- El mapa se ha reconstruido como ilustración HTML/CSS/SVG interactiva, no como tiles de Google Maps u OpenStreetMap.
- Se utiliza el **logotipo AERIQ original** y algunos recursos de la entrega Android anterior, extraídos de los archivos facilitados. Los mockups complejos de Figma se han reconstruido en HTML/CSS y la ilustración blanca del sensor se ha modelado con CSS; **no deben confundirse con fotografías exactas exportadas del Figma**.
- La fuente indicada en el Figma es **Inter**: se carga desde Google Fonts cuando hay internet, con fuentes de sistema como alternativa si no hay conexión. No se redistribuye ningún archivo de fuente.
- **Figma gobierna los precios visuales**: sensor 249 €, plan personal 3,99 €/mes o 39,90 €/año, empresa pequeña 99 €/mes o 990 €/año, empresa grande 249 €/mes o 2490 €/año. Estos importes **no coinciden con algunas cifras de PDFs anteriores** y deben consensuarse antes de entrega comercial. También aparece 6,90 € de envío como estimación ilustrativa.

## CSS global

`styles.css` contiene `:root` con tokens y bloques comentados: navegación/pie, home, mapa, producto, formularios, perfil, sensores, compra y responsive. Los estilos especiales usan clases descriptivas como `.map-sidebar`, `.product-visual` y `.checkout-card`; no se crean CSS separados por página.

## Nota sobre el proyecto

Este paquete implementa el diseño web como **prototipo funcional front-end de Sprint 1**. No prueba el funcionamiento real de la plataforma de mediciones ni sustituye a la integración Android/PHP/MariaDB del Sprint 0.


## Cambio posterior

- La sección del dispositivo en la landing (`index.html`) usa ahora un visor 3D interactivo basado en `<model-viewer>` y el archivo `assets/aeriq_sensor_3d.glb`.
- Requiere conexión para cargar la librería `model-viewer` desde CDN la primera vez, salvo que la sustituyas por una copia local.


## Actualización de octubre 2026 — estudio 3D y mapa geográfico

- `index.html`: fondo de estudio con luces suaves, suelo y plataforma (solo CSS); el visor carga de forma diferida el mismo `assets/aeriq_sensor_3d.glb` (aprox. 59 MB). Los controles de giro/zoom permanecen activos.
- `mapa.html`: ahora usa **Leaflet 1.9.4** y teselas de **OpenStreetMap** en estilo estándar de calles; no es Google Maps y no necesita una clave privada de Google. Es necesario tener Internet para ver las teselas.
- Centro geográfico: **Campus de Gandia, UPV — 38.996651, -0.166009** (dirección: C/ Paranimf, 1).
- Se incluyen 5 **sensores FICTICIOS** en ubicaciones aproximadas de su entorno, únicamente para probar la interfaz. Ni las posiciones ni los valores reflejan sensores desplegados, estaciones oficiales o información de ozono medida. La capa de círculos es una visualización ilustrativa, **NO un mapa de calor científico interpolado**.
- El campo de búsqueda está limitado a lugares predeterminados (UPV, playa de Gandia, Grau). No utiliza un geocodificador externo.
- Mantiene un solo `styles.css` y un solo `app.js`.
- **Se recomienda Live Server o `python -m http.server 8000` desde la carpeta `AERIQ_WEB`**, abrir `http://localhost:8000/`.
- Para el mapa se descarga Leaflet desde un CDN al abrir la página; el archivo GLB va dentro del ZIP.

## Ajustes posteriores

- `sensor.html` ahora incluye una galería de vistas del producto (frontal, trasera, izquierda y derecha) y un selector visual de acabados de demostración.
- `mapa.html` usa ahora una capa CARTO Voyager, más limpia y próxima a un mapa ligero tipo Apple Maps.

## Último ajuste

- El mapa se ha ajustado a una capa OpenStreetMap sin necesidad de API key.
- Las vistas del sensor se sirven ahora como PNG con fondo transparente para integrarse mejor en la maqueta.


## Cambios UI – mapa, sensor, acerca de y acceso social (31 octubre 2026)

- `mapa.html`: interruptor **Claro/Oscuro**, por defecto oscuro; se recuerda la selección en `localStorage`. Usa **únicamente** las teselas libres de clave de OpenStreetMap; el modo oscuro se consigue mediante CSS aplicado solo a la cartografía (los marcadores y etiquetas AERIQ mantienen sus colores). No es una capa/vector nativa oficial de Apple Maps. Se necesita Internet para las teselas.
- `sensor.html`: retirado el selector de acabados y el pequeño texto de vista/color que aparecía sobre la foto. Se mantiene la galería con cuatro vistas y las imágenes PNG transparentes.
- `index.html`: ampliada la sección «Sobre nosotros» explicando el origen universitario, BLE, Android, web, participación voluntaria y limitaciones de un prototipo.
- `login.html` y `registro.html`: botones de Google (G multicolor) y Apple (blanco sobre negro) con imágenes SVG locales. No hay OAuth real: al pulsarlos la interfaz informa de que es una demostración. La integración real requiere configurar los proveedores y revisar sus reglas de marca.
- `styles.css`: **único archivo de estilos** para todas las páginas, incluidas las nuevas clases. `app.js`: único archivo JavaScript.

### Fuentes y marcas de los botones sociales

La letra G multicolor sigue los colores y geometría de la marca Google. La silueta de Apple del archivo local se obtuvo del glifo de Apple incluido en Font Awesome 5 Brands (cuyo arte se distribuye con licencia CC BY 4.0); para un producto publicado, sustituirla por el archivo autorizado en Apple Design Resources y seguir las directrices de Google Identity Services / Sign in with Apple. No se ha conectado una autenticación real.


## Actualización: vistas del mapa en la landing

La sección de introducción de `index.html` ahora muestra capturas de las vistas web y móvil de AERIQ en sus maquetas de ordenador y teléfono. Las imágenes se guardan localmente en `assets/mapa_web_mockup.webp` y `assets/mapa_movil_mockup.webp` (sin depender de Figma ni de URLs temporales). La vista de ordenador enlaza con `mapa.html`. Son imágenes estáticas de demostración; el mapa real interactivo sigue en `mapa.html`. El resto de las páginas, el CSS compartido y la lógica general permanecen intactos.
