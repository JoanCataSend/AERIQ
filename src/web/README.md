# Web AERIQ

Raíz desplegable del producto AERIQ de Código Norte. Se conserva el diseño de la versión de Plesk: recursos, galería, visor 3D, componentes, navegación y estilos compartidos.

| Página | Función actual |
| --- | --- |
| `index.html` | Portada original, vista explosionada del Orb con el scroll (`product-explode.js` + anime.js), Orb 3D, tres soluciones y acceso al mapa |
| `sensor.html` | Galería de cuatro vistas y preparación de un kit |
| `mapa.html` | Público/organización, real/simulado, periodo, búsqueda, capas y aspecto |
| `sensores.html` | Flota real con tabla, filtros, edición y asignación |
| `portal.html` | Secciones de organización: equipo, campañas, Orb, informes; comercial ve solicitudes |
| `perfil.html` | Perfil/contraseña, organización y herramientas |
| `login.html` | Acceso con correo/contraseña o Google mediante Firebase (solo cuentas ya invitadas) |
| `recuperar.html`, `cambiar-clave.html` | Códigos de correo, recuperación y activación inicial |
| `solicitar.html` | Único formulario comercial: kits, estudio temporal de ozono o asesoramiento, con validación en línea |

Los formularios/llamadas existentes se reutilizan y se amplían con `fleet.js`, `portal.js`, `public-map.js`, `solicitudes.js` y los clientes especializados. `styles.css` sigue siendo el CSS compartido. No se han sustituido los recursos por imágenes nuevas.

El servidor utiliza un único [schema.sql](../database/schema.sql). Consulta [API](../../doc/api.md), [despliegue](../../doc/despliegue.md) y [pruebas](../../doc/pruebas.md). Un servidor estático solo permite presentación; los flujos de datos necesitan PHP, base y correo configurados.
