# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

La app Android (`src/android/`, Java 11 con Android Views) es una superficie aparte; su rediseño se prepara en Figma, no en este código.

## Users

Dos públicos con el mismo peso en la portada (confirmado por el usuario):

- **Ciudadanía:** cualquier persona que quiere saber cómo está el ozono en su entorno. Consulta el mapa público gratis, sin cuenta.
- **Organizaciones:** ayuntamientos y administraciones, empresas privadas y quien necesita un estudio puntual. Evalúan si AERIQ les sirve y piden un presupuesto de kits o de estudio.

Dentro del producto hay además usuarios operativos con cuenta: administradores y analistas de cada organización (panel, flota, informes, CSV), trabajadores de campo (app Android con el Orb) y el equipo comercial de Código Norte (solicitudes).

## Product Purpose

Medir el ozono (O₃) a pie de calle con un sensor portátil y mostrarlo en un mapa comprensible. El flujo completo es: Orb (firmware nRF52840) → anuncios BLE iBeacon → app Android → API PHP → MariaDB → web y mapas.

Éxito: que una persona entienda el ozono de su zona en segundos y que una organización encuentre motivos claros para pedir un presupuesto.

## Positioning

Código Norte se presenta como una startup con un producto en fase de prototipo (confirmado por el usuario). Habla con la seguridad de una empresa, pero dice con honestidad que el Orb es un prototipo y que sus mediciones deben validarse y no sustituyen a las estaciones oficiales.

Lo que la distingue: un sensor que cabe en un llavero y viaja con quien mide, en lugar de estaciones fijas; datos separados por organización; y un mapa público que solo muestra lo que cada campaña autoriza a publicar.

## Operating Context

- Mediciones de ejemplo y pruebas en la zona de Gandia (campus UPV, playa, Grau).
- Las organizaciones gestionan trabajadores, campañas y dispositivos Orb desde el portal web; los trabajadores capturan con la app Android.
- Las solicitudes de kits o estudios llegan a un panel comercial privado de Código Norte.
- Despliegue en Plesk (`https://ldiamur.upv.edu.es/src/web/`). Web sin framework ni build: HTML, CSS y JS planos, PHP sin framework.

## Capabilities and Constraints

- Mapa público con mediciones reales o simuladas (siempre separadas), periodo (24 h, 7 días, histórico), capas, búsqueda, tema claro/oscuro y exportación CSV.
- Interpolación IDW local acotada (20–50 m); sin soporte suficiente, la zona queda sin color.
- Ozono en ppb, temperatura en °C, fechas en UTC. No se convierten unidades.
- Los umbrales verde/naranja/rojo son parámetros de campaña, no umbrales oficiales.
- No hay autoregistro, ni venta ni suscripciones a particulares: las organizaciones piden presupuesto.
- Batería y LED del Orb: pendientes de hardware y firmware. No se muestra ningún porcentaje inventado.
- Los recursos gráficos originales (imágenes del Orb, piezas, maquetas, modelo 3D, SVG) no se sustituyen: `scripts/audit-repository.py` comprueba sus SHA-256 contra `scripts/tests/original-assets.json`.

## Brand Commitments

- Nombres: **AERIQ** (producto), **Orb** (sensor), **Código Norte** (empresa desarrolladora).
- Se conservan el logotipo (`src/web/assets/aeriq_logo.png`, `aeriq_mark.png`) y el verde de marca (`#0e6b5e`).
- Lema actual en el pie: «Medir para comprender.»
- Todo el contenido y los nombres del dominio están en español.
- Preferencia visual confirmada por el usuario (9 nov 2026): el estándar limpio de la categoría, ejecutado con oficio y sin rasgos de «hecho por IA». Referencias de nivel de acabado, no para copiar: Airthings, IQAir y Plume Labs (Flow).

## Evidence on Hand

- Fotografías de producto del Orb con fondo transparente, vistas de la carcasa y de cada pieza interna: `src/web/assets/`, `src/web/assets/explode/`.
- Modelo 3D del sensor: `src/web/assets/aeriq_sensor_3d.glb`.
- Maquetas del mapa en ordenador y móvil: `src/web/assets/mapa_web_mockup.webp`, `mapa_movil_mockup.webp`.
- Componentes reales del Orb: sensor SPEC ULPSM-O3, SparkFun Pro nRF52840 Mini, batería LiPo 3,7 V 500 mAh, tres LEDs de batería y un LED RGB bajo el logotipo.
- **Pendiente:** el usuario tiene mediciones reales y los nombres del equipo, pero todavía no los ha aportado. Las mediciones de `src/database/anadir-mediciones-playa-gandia.sql` son simuladas.
- No existen testimonios, clientes, cifras de impacto ni precios. No se inventan.

## Product Principles

1. Honestidad de datos ante todo: real y simulado nunca se confunden, y lo que no se ha medido no se muestra.
2. Comprensible a la primera: una persona sin formación técnica entiende qué significa el color de su zona.
3. El objeto físico es la prueba: el Orb y sus piezas reales cuentan el producto mejor que cualquier promesa.
4. Cada organización ve solo lo suyo; publicar es una decisión explícita.

## Accessibility & Inclusion

Sin requisito formal establecido. La web ya respeta `prefers-reduced-motion` en la animación del Orb, y los colores del mapa deben acompañarse siempre de texto, porque el rojo y el verde no bastan para personas con daltonismo.
