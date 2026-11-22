---
name: AERIQ
description: Ozono a pie de calle, medido con el Orb y publicado en un mapa abierto.
colors:
  brand: "#0e6b5e"
  brand-hover: "#0a594e"
  brand-on-dark: "#62d2b3"
  brand-tint: "#e6f0ed"
  brand-ink: "#0b4a41"
  low: "#219c72"
  mid: "#eea22d"
  high: "#e35b5d"
  bg: "#ffffff"
  surface: "#ffffff"
  alt: "#f5f6f5"
  plate: "#eef0ef"
  ink: "#1a1c1b"
  ink-2: "#4b504e"
  ink-3: "#686d6b"
  line: "#e3e6e4"
  line-strong: "#cfd4d1"
  dark: "#000000"
  dark-2: "#141615"
  on-dark: "#f5f6f5"
  on-dark-2: "#a3a8a5"
  map-canvas: "#e9edea"
  map-dark: "#1b2629"
typography:
  hero:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(44px, 7.4vw, 96px)"
    fontWeight: 750
    lineHeight: 1
    letterSpacing: "-0.04em"
  display:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(34px, 5.4vw, 64px)"
    fontWeight: 700
    lineHeight: 1.05
    letterSpacing: "-0.035em"
  headline:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(30px, 3.5vw, 48px)"
    fontWeight: 650
    lineHeight: 1.08
    letterSpacing: "-0.03em"
  title:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(21px, 1.9vw, 26px)"
    fontWeight: 650
    lineHeight: 1.15
    letterSpacing: "-0.02em"
  lead:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(18px, 1.7vw, 22px)"
    fontWeight: 450
    lineHeight: 1.45
  body:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.55
    letterSpacing: "-0.003em"
  label:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 550
    lineHeight: 1.4
  stat:
    fontFamily: "Hanken Grotesk, Segoe UI, system-ui, sans-serif"
    fontSize: "clamp(52px, 5.6vw, 76px)"
    fontWeight: 750
    lineHeight: 1
    letterSpacing: "-0.04em"
    fontFeature: "tnum"
rounded:
  control: "10px"
  field: "12px"
  thumb: "16px"
  panel: "24px"
  tile: "28px"
  pill: "999px"
spacing:
  gutter: "clamp(16px, 4vw, 40px)"
  max: "1120px"
  header: "56px"
  section: "clamp(80px, 10vw, 140px)"
  grid-gap: "16px"
  tile-pad: "clamp(28px, 3vw, 40px)"
components:
  button-primary:
    backgroundColor: "{colors.brand}"
    textColor: "#ffffff"
    rounded: "{rounded.pill}"
    padding: "0 22px"
    height: "48px"
  button-primary-hover:
    backgroundColor: "{colors.brand-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "0 22px"
    height: "48px"
  button-sm:
    rounded: "{rounded.pill}"
    padding: "0 16px"
    height: "40px"
  chevron-link:
    textColor: "{colors.brand}"
    typography: "{typography.body}"
  chevron-link-on-dark:
    textColor: "{colors.brand-on-dark}"
  tile:
    backgroundColor: "{colors.alt}"
    rounded: "{rounded.tile}"
    padding: "{spacing.tile-pad}"
  tile-dark:
    backgroundColor: "{colors.dark-2}"
    textColor: "{colors.on-dark}"
    rounded: "{rounded.tile}"
  panel:
    backgroundColor: "{colors.alt}"
    rounded: "{rounded.panel}"
    padding: "clamp(24px, 3vw, 40px)"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
    padding: "11px 14px"
    height: "48px"
  map-float:
    backgroundColor: "#ffffff"
    rounded: "14px"
  section-dark:
    backgroundColor: "{colors.dark}"
    textColor: "{colors.on-dark}"
---

# Design System: AERIQ

## Overview

**Creative North Star: "El escaparate silencioso"**

AERIQ se presenta como un producto físico en un escaparate limpio: el Orb y el mapa son los protagonistas y todo lo demás retrocede. Titulares enormes y centrados en Hanken Grotesk con interletraje negativo, mucho aire vertical entre secciones, fondo blanco con tramos de gris muy claro y algún tramo de negro puro que cambia el ritmo. La dirección la fijó el usuario: presentación de producto minimalista al estilo de la web de Apple, sin copiar su marca.

La densidad sube en las páginas de operación (mapa, cuenta, organización, flota, formularios) pero el mundo es el mismo: misma tipografía, mismo verde como único acento, mismas superficies grises sin borde, misma cabecera fina translúcida. Una sola hoja de estilos (`src/web/aeriq.css`) sirve a todas las páginas.

Rechazos confirmados: etiquetas pequeñas sobre los títulos, iconos de glifo decorativos y filas de insignias. La versión anterior «simplona» (estándar limpio con paneles bordeados) quedó sustituida.

La contraparte de la app Android vive en el archivo de Figma «Proyecto» (pLq0la9g1ew4pFn6OUqUBQ), página «AERIQ · Rediseño app (propuesta)»: 15 pantallas construidas con las variables locales «AERIQ 2026 · color» y los componentes «App · Barra inferior», «App · Botón», «App · Campo», «App · Fila de lista» y «App · Botón atrás», con la misma tipografía.

**Key Characteristics:**
- Producto y mapa como protagonistas; el texto los acompaña, no compite.
- Titulares centrados de 700–750 con interletraje de −0,035 a −0,04 em.
- Superficies grises claras de radio grande y sin borde; negro puro como contraste puntual.
- Un único acento (verde de marca) reservado a acciones.
- Colores de nivel solo para datos, siempre con texto al lado.

## Colors

Paleta casi neutra de blancos, grises fríos y tinta casi negra, con un verde profundo como única voz y tres colores de nivel que pertenecen a los datos, no a la interfaz.

### Primary
- **Verde Código Norte** (brand): botones primarios, enlaces con chevron, enlaces de texto, foco, casillas y el número activo del despiece. Su versión de hover (brand-hover) solo oscurece.
- **Verde menta sobre negro** (brand-on-dark): el mismo papel que el verde de marca dentro de los tramos negros y de las tarjetas oscuras (enlaces con chevron, cifra grande).
- **Verde niebla / Verde bosque** (brand-tint / brand-ink): pareja de fondo y texto del avatar de cuenta. Uso mínimo.

### Neutral
- **Blanco** (bg, surface): fondo de página y de controles.
- **Gris escaparate** (alt): tarjetas, paneles, estadísticas, miniaturas, oferta y pie. Es la superficie de agrupación por defecto.
- **Gris bandeja** (plate): fondo de las pantallas dibujadas del portátil y el móvil mientras carga la imagen.
- **Tinta** (ink), **Tinta media** (ink-2), **Tinta suave** (ink-3): titulares y texto principal; texto secundario y navegación; entradillas, ayudas y metadatos.
- **Línea** (line) y **Línea marcada** (line-strong): divisores de filas y tablas; bordes de campos, botones secundarios y controles.
- **Negro puro** (dark): secciones oscuras completas (en portada, «Datos claros»). **Negro carbón** (dark-2): tarjeta oscura dentro del bento.
- **Texto sobre negro** (on-dark, on-dark-2): titulares y texto secundario dentro de los tramos negros.
- **Lienzo del mapa** (map-canvas, map-dark): fondo del contenedor Leaflet en tema claro y oscuro.

### Data
- **Bajo / Medio / Alto** (low, mid, high): puntos de leyenda, franja de escala y capa del mapa. Nunca en botones, fondos de sección ni decoración.

### Named Rules
**La regla de la voz única.** El verde de marca es el único color de interfaz con tono. Aparece en acciones (botón primario, enlace con chevron, enlace de texto, foco) y en nada más.

**La regla del dato con nombre.** Verde, naranja y rojo de nivel solo representan mediciones, y siempre van acompañados de su etiqueta en texto («Bajo», «Medio», «Alto»), porque el color solo no basta para personas con daltonismo.

## Typography

**Display Font:** Hanken Grotesk (con Segoe UI, system-ui, sans-serif)
**Body Font:** Hanken Grotesk, la misma familia
**Label/Mono Font:** ninguna distinta; las cifras usan `font-variant-numeric: tabular-nums`

**Character:** Una sola grotesca de ejes variables (400–800) usada en pesos intermedios (450, 550, 650, 750) para escalonar la jerarquía sin cambiar de familia. Los tamaños grandes se aprietan con interletraje negativo; el cuerpo queda casi neutro.

### Hierarchy
- **Hero** (750, hasta 96 px, interlínea 1, −0,04 em): titular de primera vista de la portada, centrado, máximo 14ch. En la ficha del Orb el nombre del producto llega a 128 px (interlínea 0,95).
- **Display** (700, hasta 64 px, 1,05, −0,035 em): titulares de sección grandes y centrados (despiece, bloques de portada). Los titulares de página interior y de acceso usan 750 y −0,04 / −0,035 em en el mismo rango.
- **Headline** (650, hasta 48 px, 1,08, −0,03 em): cabeceras de sección alineadas a la izquierda.
- **Title** (650–700, 21–26 px, −0,02 a −0,025 em): tarjetas del bento, paneles, pasos y reglas.
- **Lead** (450, 18–22 px, 1,45, tinta suave): entradillas bajo titulares, máximo 40–46ch.
- **Body** (400, 17 px, 1,55): texto corrido; en tarjetas baja a 16–16,5 px.
- **Label** (550, 13,5–14 px): etiquetas de campo, navegación, metadatos. Sin mayúsculas forzadas.
- **Stat** (750, hasta 76 px, −0,04 em, cifras tabulares): cifras grandes del bento y de las estadísticas del portal.

### Named Rules
**La regla del titular sin etiqueta.** Ningún titular lleva encima una etiqueta pequeña, versalita o en mayúsculas. El titular se sostiene solo.

**La regla del interletraje negativo.** A partir de 21 px, el interletraje es negativo y crece con el tamaño (de −0,015 a −0,04 em). El cuerpo se queda en −0,003 em.

## Layout

Contenedor centrado de 1120 px como máximo, con margen lateral fluido (16–40 px). Las secciones de portada respiran mucho (80–140 px arriba y abajo) y alternan blanco, gris escaparate y negro puro. Los titulares de presentación se centran con entradillas estrechas; las cabeceras de sección operativas se alinean a la izquierda con un máximo de 640–720 px.

Rejillas principales: bento de 3 columnas con separación de 16 px (2 por debajo de 1050 px, 1 por debajo de 560 px); pasos y columnas de tres; reglas y planes en 2 columnas que pasan a 1 por debajo de 860 px. Páginas de cuenta y organización: navegación lateral fija de 260 px más contenido; formularios con columna lateral de 340 px; ambas se apilan por debajo de 1050 px y la navegación lateral pasa a fila desplazable. El mapa ocupa toda la altura bajo la cabecera con barra lateral de 340 px (300 px por debajo de 1050 px); en móvil el mapa va arriba y los filtros debajo.

Cortes observados: 1050, 860, 780, 640 y 560 px.

## Elevation & Depth

El sistema es plano por defecto: la profundidad la dan los cambios de tono (blanco, gris escaparate, negro) y no las sombras. Las sombras aparecen solo en lo que flota de verdad sobre otra cosa: controles sobre el mapa, el segmento seleccionado, el aviso, el diálogo, el menú móvil y los dispositivos dibujados de la primera vista. Las fotos del Orb y sus piezas llevan sombra proyectada propia (drop-shadow) para asentarse sobre el gris.

### Shadow Vocabulary
- **Apoyo leve** (`box-shadow: 0 1px 2px rgba(0,0,0,.05), 0 4px 12px rgba(0,0,0,.05)`): segmento seleccionado, opción activa del estilo de mapa, chips de la maqueta.
- **Flotante** (`box-shadow: 0 2px 6px rgba(0,0,0,.06), 0 24px 48px rgba(0,0,0,.12)`): controles flotantes del mapa, aviso, diálogo, menú móvil.

### Named Rules
**La regla de la tarjeta sin sombra.** Tarjetas, paneles y estadísticas no llevan sombra ni borde; se separan del blanco solo por el gris escaparate.

## Shapes

Esquinas generosas y sin bordes en las superficies grandes (24 px en paneles y estadísticas, 28 px en tarjetas del bento, oferta y diálogo), cápsula completa en botones, botones sociales, selector de cantidad y estado del producto, y radios contenidos en los controles de formulario (12 px en campos de formulario, 10 px en controles del mapa y filas de lista, 16 px en miniaturas y tablas). Los puntos de nivel y los números de paso o de pieza son círculos. El portátil y el móvil de la primera vista se dibujan con CSS (bisel con sombras de anillo, base con degradado metálico, móvil de radio 34 px).

Los recursos gráficos originales (fotos del Orb, piezas, maquetas, modelo 3D) están protegidos por SHA-256 y nunca se editan: solo se recortan con CSS (posición y escala dentro de un contenedor con `overflow:hidden`). Las maquetas del mapa muestran una interfaz antigua y se tapan con capas HTML (barra de búsqueda, leyenda, hoja inferior) que reproducen la interfaz actual.

## Components

### Buttons
Cápsulas rotundas y sobrias.
- **Shape:** cápsula completa (999 px), 48 px de alto; versión pequeña de 40 px (32 px en la cabecera).
- **Primary:** fondo verde de marca, texto blanco, peso 550 a 15,5 px.
- **Hover / Focus:** el fondo oscurece al verde de hover; el icono de flecha se desplaza 2 px; al pulsar escala a 0,98. Foco con contorno verde de 2 px y separación de 3 px.
- **Secondary:** fondo blanco con borde de línea marcada y tinta; en hover el borde pasa a tinta suave. `.btn-outline` es un duplicado exacto.
- **Social (acceso):** cápsula blanca con borde; la variante de Apple es tinta sobre tinta.

### Enlace con chevron
El enlace secundario característico: texto verde de marca a 17 px, peso 550, sin subrayado, seguido de un chevron SVG de 16 px que avanza 2 px en hover mientras aparece el subrayado. En tramos negros usa el verde menta. Acompaña al botón primario como segunda acción.

### Cards / Containers
- **Corner Style:** 28 px en tarjetas del bento y oferta; 24 px en paneles y estadísticas.
- **Background:** gris escaparate; la tarjeta oscura usa negro carbón con cifra en verde menta.
- **Shadow Strategy:** ninguna (ver Elevation & Depth).
- **Border:** ninguno.
- **Internal Padding:** 28–40 px fluidos; 24–26 px en estadísticas.

### Inputs / Fields
- **Style:** fondo blanco, borde de línea marcada de 1 px, radio 12 px, 48 px de alto, texto de 15,5 px; etiqueta encima a 14 px peso 550 en tinta media.
- **Focus:** borde verde de marca más halo de 3 px en verde al 14 %.
- **Hover:** borde en tinta suave.
- **Selects:** misma caja con chevron SVG en línea a la derecha. Controles segmentados sobre bandeja gris con la opción activa en blanco y apoyo leve.

### Navigation
- **Cabecera:** fija, 56 px, blanco al 82 % con desenfoque y saturación, línea inferior al 8 % de negro. Logotipo a 24 px, enlaces de 14 px peso 500 en tinta media; la página actual pasa a tinta y peso 650. Por debajo de 860 px, menú desplegable a ancho completo con sombra flotante.
- **Navegación lateral (cuenta y organización):** enlaces de 15 px con radio 10 px; hover y activo sobre gris escaparate, el activo en peso 650.
- **Migas:** 14 px en tinta suave, sobre los titulares de páginas interiores.

### Controles flotantes del mapa
Cajas blancas al 97 % sin borde, radio 14 px y sombra flotante sobre el mapa Leaflet: aviso de datos de demostración, zoom, ficha de la medición seleccionada (cifra a 24 px tabular) y leyenda con franja de escala. En tema oscuro pasan a un gris azulado al 96 % con texto claro.

### Dispositivos de la primera vista
Portátil y móvil Android dibujados en CSS que enmarcan recortes de las maquetas protegidas del mapa, con capas HTML encima que actualizan la interfaz. El móvil se superpone abajo a la derecha del portátil. Firma de la portada; no se reutiliza como decoración en otras páginas.

### Despiece del Orb
La única animación autorada: al hacer scroll, la carcasa se abre y las cinco piezas reales se separan, con líneas guía y números circulares que se activan en verde de marca. Respeta `prefers-reduced-motion`. En móvil las etiquetas pasan a una lista bajo la imagen.

## Do's and Don'ts

### Do:
- **Do** usar el verde de marca solo para acciones: botón primario, enlace con chevron, enlace de texto y foco; verde menta dentro de los tramos negros.
- **Do** agrupar con superficies gris escaparate sin borde ni sombra, radio 24 px (paneles) o 28 px (tarjetas).
- **Do** centrar los titulares de presentación con peso 700–750 e interletraje de −0,035 a −0,04 em, con una entradilla gris de 18–22 px debajo.
- **Do** emparejar el botón primario con un enlace con chevron como segunda acción.
- **Do** acompañar cada color de nivel con su etiqueta en texto.
- **Do** recortar los recursos originales solo con CSS y tapar la interfaz antigua de las maquetas con capas HTML.
- **Do** usar cifras tabulares en mediciones, estadísticas y tablas.

### Don't:
- **Don't** poner etiquetas pequeñas o en mayúsculas encima de los titulares.
- **Don't** usar iconos de glifo decorativos ni filas de insignias; los iconos son SVG funcionales (chevron, búsqueda, menú, zoom).
- **Don't** usar los colores de nivel en botones, fondos o decoración.
- **Don't** añadir un segundo color de acento ni volver a los paneles bordeados del sistema anterior.
- **Don't** editar, sustituir ni regenerar las imágenes, maquetas o el modelo 3D originales.
- **Don't** añadir animaciones autoradas fuera del despiece del Orb.
- **Don't** copiar la marca de Apple (logotipos, nombres de producto, iconografía); la referencia es el nivel de acabado y la composición.
