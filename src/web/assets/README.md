# Recursos visuales web

| Grupo | Uso |
| --- | --- |
| `aeriq_logo.png`, `aeriq_mark.png` | Identidad de marca |
| `ic_*.png`, `figma_nav_*.png` | Iconos de interfaz |
| `sensor_view_*.png` | Galería del sensor con transparencia |
| `sensor_view_*.jpg` | Versiones JPG conservadas de la entrega |
| `mapa_*_mockup.webp` | Capturas estáticas de presentación |
| `aeriq_sensor_3d.glb` | Modelo del visor de producto, unos 59 MB |
| `google-g.svg`, `apple-mark-white.svg` | Botones sociales de demostración |
| `explode/orb-*.webp`, `explode/pieza-*.webp` | Capas de la vista explosionada de la portada (640/960/1324 px las carcasas, 400/720 px las piezas) |

Las carcasas de `explode/` comparten un mismo recorte y ya están alineadas con
`orb-cerrado` (la tapa encendida, alineada por el logotipo con la apagada), así
que se apilan sin correcciones en CSS. Las piezas están recortadas a su área
visible; su posición se define en `styles.css`.

Los recursos se referencian por rutas relativas desde las páginas. El modelo 3D
y las imágenes locales se incluyen en el paquete de Plesk; Leaflet, model-viewer,
anime.js, fuentes de Google y teselas OSM necesitan acceso a servicios externos.

Las notas de procedencia gráfica de entregas anteriores se conservan en
[el histórico web](../../../doc/historico/web/README_prototipo.md). No hay una
licencia global nueva atribuida a los recursos por esta reorganización.
