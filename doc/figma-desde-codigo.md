# AERIQ · Frames de Figma desde el código local

Entrega de 40 frames editables dentro del archivo Proyecto. Se han conservado las páginas y frames originales.

- [Web actual desde código](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=223-2): 23 frames para las 14 páginas HTML, secciones del panel y variantes de solicitud y mapa.
- [Android actual desde código](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=223-3): 17 frames para los 16 destinos de Screen y la activación obligatoria.
- Fuentes de la representación: Inter en web; Roboto para el SansSerif de Android.
- Componentes reutilizados: botones, campos, filas, barras y navegación de la página App Android · Componentes. Cabecera y pie web compartidos.
- 285 enlaces de navegación web y 53 Android. Son enlaces de prototipo; no ejecutan autenticación, correo, exportación, Bluetooth ni operaciones de base de datos.

## Alcance visual

Es una reconstrucción semántica basada en HTML/CSS/JS y Compose, no una captura exacta de cada pantalla en ejecución. Los textos, formularios, tarjetas y gráficos se representan con capas editables. Las imágenes del Orb, los iconos y los mockups de la portada proceden de los recursos locales. No se modifica el código de ejecución de la web o Android en esta entrega.

Los mapas se representan con cartografía vectorial esquemática y datos ilustrativos; no son teselas geográficas reales ni consultan Plesk. El visor GLB se representa mediante una imagen de producto, sin giro 3D en el prototipo. No se han añadido suscripciones Premium ni pagos que ya se retiraron del producto.

Se revisaron capturas de portada, producto, cuenta, informes, solicitudes, mapa y los flujos principales Android, corrigiendo recortes y escalados detectados. La auditoría final confirma 1.302 textos editables, 264 instancias reutilizables, fuentes esperadas e imágenes con hash y dimensiones válidas. Los IDs y dimensiones están en [el inventario JSON](figma-desde-codigo.json).

## Web

| Frame | Enlace |
|---|---|
| WEB 01 · Inicio · index.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=226-2) |
| WEB 02 · Sensor Orb · sensor.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=226-120) |
| WEB 03 · Mi cuenta · perfil.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=226-235) |
| WEB 04 · Iniciar sesión · login.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=226-370) |
| WEB 05 · Registro · registro.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=226-450) |
| WEB 06 · Flota de Orb · sensores.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=226-542) |
| WEB 07 · Verificar correo · verificar.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-389) |
| WEB 08 · Recuperar contraseña · recuperar.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-464) |
| WEB 09 · Contraseña temporal · cambiar-clave.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-563) |
| WEB 10 · Preparar kit · carrito.html (página eliminada) | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-648) |
| WEB 11 · Presupuesto de kit · checkout.html (página eliminada) | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-732) |
| WEB 12 · Solicitar kits · solicitar.html (formulario anterior) | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-843) |
| WEB 13 · Solicitar estudio · solicitar.html?service=estudio | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-954) |
| WEB 14 · Solicitud empresa · solicitar.html?service=empresa | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1069) |
| WEB 15 · Organización / Resumen · portal.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1180) |
| WEB 16 · Organización / Trabajadores · portal.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1269) |
| WEB 17 · Organización / Campañas · portal.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1394) |
| WEB 18 · Organización / Dispositivos Orb · portal.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1530) |
| WEB 19 · Organización / Informes y CSV · portal.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1627) |
| WEB 20 · Organización / Solicitudes · portal.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1743) |
| WEB 21 · Editar Orb · sensores.html / diálogo | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=229-1851) |
| WEB 22 · Mapa público · mapa.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=230-1402) |
| WEB 23 · Mapa de mi organización · mapa.html | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=230-1601) |

## Android

| Frame | Enlace |
|---|---|
| APP 01 · Iniciar sesión · LOGIN | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-2) |
| APP 02 · Crear cuenta · REGISTER (no implementado: sin autoregistro) | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-29) |
| APP 03 · Verificar correo · VERIFY (no implementado: sin autoregistro) | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-66) |
| APP 04 · Recuperar contraseña · FORGOT | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-89) |
| APP 05 · Nueva contraseña · RESET | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-106) |
| APP 06 · Mapa público · MAP | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-137) |
| APP 07 · Aprende · LEARN | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-303) |
| APP 08 · Mi sensor · SENSOR | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-371) |
| APP 09 · Vincular sensor · LINK | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-464) |
| APP 10 · Historial · HISTORY | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=231-532) |
| APP 11 · Configurar sensor · CONFIG | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-281) |
| APP 12 · Comparar zonas · COMPARE | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-360) |
| APP 13 · Soluciones AERIQ · SERVICES | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-438) |
| APP 14 · Mi cuenta · ACCOUNT | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-502) |
| APP 15 · Datos personales · EDIT_PROFILE | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-650) |
| APP 16 · Cambiar contraseña · CHANGE_PASSWORD | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-676) |
| APP 17 · Activación obligatoria · CHANGE_PASSWORD mandatory | [Abrir](https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=232-704) |

