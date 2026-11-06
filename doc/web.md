# Web: conservar diseño, completar funciones

## Diseño recuperado

La portada conserva su composición original, las maquetas web/móvil y el visor 3D. Entre el hero y «El dispositivo» hay una vista explosionada del Orb (`product-explode.js`): la sección se fija y anime.js 4 (CDN, `onScroll` con `sync`) abre la carcasa, separa los componentes, muestra sus etiquetas HTML y enciende el logotipo; el recorrido se invierte al subir. Sin anime.js o con `prefers-reduced-motion` se muestra el estado abierto estático. Añade `?explode-debug` a la URL para superponer el render cerrado y ver la máscara de la pared frontal al calibrar. La ficha Orb conserva sus cuatro imágenes transparentes, la galería y el selector de cantidad. La cuenta y la flota recuperan la estructura anterior de tarjetas, barra lateral, tabla, búsqueda, filtros y diálogo de edición. Se mantienen logotipo, colores, CSS global y adaptación responsive.

Los antiguos planes se convierten en tres tarjetas de servicios: kits para administraciones, kits para empresas y estudios de Código Norte. El recorrido cantidad → revisión → formulario se conserva como solicitud de presupuesto, sin pagos ni suscripciones personales. Las rutas antiguas `carrito.html` y `checkout.html` siguen existiendo con esa nueva finalidad.

## Pantallas operativas

- `portal.html`: resumen y navegación por trabajadores, campañas, dispositivos, informes o solicitudes según función.
- `sensores.html` + `fleet.js`: tabla organizativa real; alta/baja administrativa, nombre, asignaciones, origen y última actividad.
- `mapa.html` + `public-map.js`: controles, búsqueda local, capas, aspecto claro/oscuro recordado, selección y CSV. Origen real/simulado, periodo y ámbito público/organización.
- `perfil.html`: datos y contraseña existentes; organización, función y herramientas de campaña en lugar de pedidos ficticios.
- `solicitar.html`: formulario de kits/estudios, campos condicionales, consentimiento y confirmación persistida.
- `cambiar-clave.html`: activación por cambio inicial, protegida también en servidor.

## Informes

Campaña, zona, varios dispositivos y fechas UTC; estadísticas, evolución diaria/semanal, tabla y sucesos de umbral. Series reales y simuladas separadas. CSV con escape de texto y coordenadas negativas numéricas. El servidor rechaza resultados de más de 10.000 filas en vez de exportarlos incompletos sin avisar.

La vista de mapa limita a las 2.000 mediciones más recientes del filtro y acota el muestreo por rendimiento. No se utilizan círculos ficticios de 125 m como interpolación científica ni datos locales inventados ante fallos de la API.
