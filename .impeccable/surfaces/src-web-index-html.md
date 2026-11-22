---
version: 1
slug: "src-web-index-html"
primary_target: "src/web/index.html"
related_targets: ["src/web/mapa.html","src/web/sensor.html","src/web/solicitar.html","src/web/login.html","src/web/perfil.html","src/web/portal.html","src/web/sensores.html"]
---

## Scope

Web completa de AERIQ (`src/web/*.html`, hoja `src/web/aeriq.css`). La portada es Persuade; mapa, cuenta, organización, flota y formularios son Operate y heredan el mismo mundo. La app Android tiene su contraparte en Figma (página «AERIQ · Rediseño app (propuesta)»).

## Audience and job

Ciudadanía que consulta el ozono de su zona y organizaciones que piden kits o estudios, con el mismo peso. Acción principal: abrir el mapa público; secundaria: solicitar presupuesto.

## Proof and constraints

El mapa funcionando (capturas en portátil y móvil), el Orb real y sus piezas (fotos y modelo 3D originales). Las maquetas del mapa son recursos protegidos con una interfaz antigua (Premium, carrito, µg/m³): se recortan a la zona del mapa con capas HTML encima. Equipo y mediciones reales pendientes; nada inventado.

## Direction contract

THESIS: Presentación de producto minimalista al estilo de Apple, fijada por el usuario tras rechazar una versión «simplona»: producto y mapa como protagonistas, titulares enormes y centrados, mucho aire, tramos negros puntuales. Rechaza etiquetas sobre títulos, iconos de glifo y filas de insignias.

OWN-WORLD: Fondo blanco y gris muy claro #f5f6f5 en tarjetas de radio 24–28 px sin borde, tinta casi negra, negro puro en «Datos claros», verde de marca #0e6b5e solo en botones y enlaces con chevron (#62d2b3 sobre negro). Hanken Grotesk 400–800, titulares 700–750 con interletraje −0,035 a −0,04 em. Cabecera fina translúcida de 56 px.

STORY: Se ve primero el mapa abierto en un portátil y un móvil Android; luego el Orb por dentro, sus prestaciones, el recorrido del dato, las reglas de honestidad, las soluciones para organizaciones, quiénes somos y el cierre.

FIRST VIEWPORT: Fondo blanco. Titular centrado «Un mapa abierto a todos.» (hasta 96 px), entradilla gris, botón verde «Ver el mapa» y enlace «Conocer el Orb ›». Debajo, un portátil dibujado en CSS con la web del mapa y, superpuesto abajo a la derecha, un móvil Android con la app y una hoja inferior de ejemplo.

FORM: Dirección fijada por el usuario (referencias: web de Apple; tono claro con tramos negros). seed:3184a7b0fe32814b. Interacción firma: el despiece del Orb con el scroll, única animación autorada.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Unresolved

Nombres del equipo y mediciones reales pendientes. Dos diseños del Orb en los recursos (redondo con mosquetón y cápsula en el despiece). Formato de fecha y coordenadas del mapa: requiere tocar `public-map.js`, pendiente de permiso.
