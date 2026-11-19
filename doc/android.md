# Android AERIQ

## Pantallas conservadas y adaptadas

- Mapa de ozono (sin icono de avisos: no hay notificaciones): mapa real OpenStreetMap a pantalla completa, panel inferior arrastrable (plegado: zona y valor; desplegado: calidad, hora, Ver historial —solo con sesión— y Comparar zonas), con buscador mundial (Photon/OSM y, si falla, Geocoder de Android; sugerencias mientras escribes), controles flotantes de ubicación, capas y actualizar, leyenda en la parte inferior del mapa, aviso solo si la zona elegida tiene ozono moderado o alto, y ficha de zona (valor, calidad, hora, Ver historial, Comparar). Solo mediciones reales: se quitó el selector Reales/Simulados. Las zonas siguen la misma [interpolación](interpolacion.md) que la web: color con tres o más posiciones dentro del radio, gris con datos insuficientes y sin color sin datos; en «Capas» se elige el periodo (últimas 24 h, 7 días o todo el histórico, como en la web) y se puede probar un radio de 20 a 50 m sin guardarlo, y al tocar un punto del mapa la ficha inferior explica su estimación o por qué no hay nivel.
- Mi sensor: tarjeta del sensor PBIO con foto, estado de recepción con antigüedad (s, min, h, días), Bluetooth azul/gris y batería (“—” mientras el firmware no la transmita), O₃ ahora con calidad y gráfica de área del día, Tu día (distancia por GPS, O₃ medio, barra de calidad por tramos), accesos a Estado del sensor (diagnóstico, servidor, reintentos) y Detalles del sensor, y un aviso de envío automático a la campaña (sin interruptor).
- Vincular: imagen del sensor PBIO, interruptores Bluetooth (búsqueda) y Ubicación (permiso), lista de sensores cercanos con señal; el más fuerte o el vinculado se destaca con el botón Vincular. Al abrir la pantalla empieza a buscar. La app no se asigna Orb: si el detectado no está asignado a la cuenta, explica que lo asigna el administrador de campaña desde el panel web (con su identificador) y permite actualizar las asignaciones. La tarjeta inferior explica que los Orb se venden en kits para ayuntamientos y empresas y abre `solicitar.html`.
- Historial: pestañas Día/Semana/Mes/Año, valor actual, media, barras por tramo coloreadas por calidad (tramos futuros en gris), máximo/mínimo/media/mediciones.
- Detalles del sensor (antes «Configurar sensor»): solo consulta. Estado en directo (conexión, batería, señal, última lectura, SPEC O₃), Orb y campaña asignados, permiso de ubicación (con botón para concederlo si falta) y Reconectar. El nombre del Orb, la campaña y la publicación los gestiona el admin de campaña en el panel web; no hay interruptores de compartir ni de recorrido: el envío y la posición van siempre.
- Aprende: tarjeta verde, pestañas Ozono/Salud/Normativa, tarjetas con icono, enlace a la Directiva 2008/50/CE y otros contaminantes.
- Cuenta: perfil con la función y su campaña («Operador · Campaña X»), datos personales, Mi sensor (operador) o Panel (administradores), contraseña, acceso con huella, ayuda y cerrar sesión.
- Comparar zonas (24 h/7 días/30 días con mediciones públicas reales; solo zonas con ≥3 puntos). Es libre y no necesita cuenta; descarga el histórico público para que las pestañas de 7 y 30 días tengan datos, y sin mediciones muestra un único aviso. No hay Premium, suscripciones ni pagos en la app: AERIQ vende kits a organizaciones.
- Iniciar sesión: correo y contraseña, «Continuar con Google» y «Entrar con huella». No hay botón de Apple (no existe en Android). Sin registro público: un aviso «¿Aún no tienes cuenta?» explica que las cuentas vienen con los kits AERIQ para ayuntamientos y empresas, que las crea el administrador de la campaña, y enlaza a «Solicitar un kit» (`solicitar.html`). Con «Acceso con huella» activado, la sesión guardada solo se abre tras verificar la huella (BiometricPrompt, Android 9+).

## Funciones y permisos

La app sigue el mismo reparto que el panel web (`portal.php`, roles por campaña) y decide por permisos, nunca por el nombre del rol:

| Cuenta | Permiso que lo decide | Qué ve en la app |
| --- | --- | --- |
| Sin sesión (ciudadano) | — | Mapa público, Comparar zonas, Aprende e inicio de sesión |
| Operador | `mediciones.registrar` | Mapa, Mi sensor (consulta), Historial (sin exportar), Detalles del sensor, Aprende y Cuenta |
| Admin de campaña | `equipo.gestionar` | Mapa, Aprende, Cuenta y la pestaña Panel, que explica lo que gestiona, abre el panel web y permite **Asignar un Orb con el móvil** |
| Admin principal | `campanas.gestionar` | Igual que el admin de campaña, con sus tareas (campañas, admins y solicitudes) |

- Las pantallas del Orb (Mi sensor, Vincular, Historial, Detalles) solo existen para quien mide; a un administrador se le lleva a Panel.
- El operador no configura nada del Orb: lo registra, nombra y asigna el admin de campaña en la web, y la publicación en el mapa la decide en «Ajustes de la campaña» («Mostrar las mediciones en el mapa público»).
- Si el operador tiene un único Orb asignado, la app lo selecciona sola al detectarlo por Bluetooth y empieza a enviar; con varios, se elige en Vincular.
- Asignar Orb (admin de campaña): Panel → «Asignar un Orb con el móvil» busca los Orb cercanos por Bluetooth (MAC, señal y si ya están registrados y quién los lleva), y al tocar uno se elige el nombre y el operador. Usa `mobile-team.php` con las mismas reglas que «Registrar Orb» del panel web: solo operadores de su campaña y un Orb por operador. El operador lo recibe solo al abrir su app y, si es su único Orb, se conecta al detectarlo.
- No se exportan datos desde la app: en la web el operador consulta sus lecturas pero no las descarga.
- El mapa de la app es el público. El mapa privado y los informes de la campaña están en el panel web (usan la sesión web, no el token del móvil).

## Red

`data/ApiRoutes.java` centraliza `https://jcatsen.upv.edu.es/AERIQ/`. `AeriqAuthManager` reutiliza login, recuperación y token cifrado. La contraseña temporal bloquea la app hasta activar/cambiarla; una invitación caducada requiere recuperar acceso. Las cuentas solo las crea un administrador.

`AeriqFieldClient` obtiene asignaciones e historial privados. Los POST incluyen evento estable, dispositivo, asignación, campaña, unidad, fecha UTC y GPS opcional. Reintenta una vez un fallo de transporte usando el mismo evento. La cola es de 30 lecturas, no persistente tras cerrar la app; no se promete conservación offline. Una actualización fallida de asignaciones no borra de pantalla el Orb previamente conocido y no se confunde con pérdida de Bluetooth; el servidor sigue validando cada envío.

## BLE y ubicación

Se mantienen fabricante `0x004C`, prefijo `02 15`, identificador ASCII `EPSG-GTI-PROY-3A`, tipos 14 ppb y 12 °C, contador de 8 bits y temperatura con signo. Vincular significa seleccionar anuncios iBeacon, no una conexión GATT permanente. Seleccionar de nuevo el mismo Orb no reinicia la deduplicación.

Tras 15 s sin anuncios se indica ausencia de recepción; el estado del servidor es independiente. Los permisos BLE dependen de versión Android. El GPS es opcional, solo se adjunta una posición reciente (menos de 60 s) y se detiene en segundo plano. `ForegroundCaptureLocation` pide posiciones cada 2 s sin distancia mínima, para que una persona parada siga recibiendo posiciones frescas (antes, con 3 m de distancia mínima, al minuto quieta las lecturas se enviaban sin GPS y no salían en el mapa). Entre red y GPS se queda con la más precisa de los últimos 10 s. Estas posiciones son también las que forman la ruta del Orb en el mapa web de la organización. El mapa distingue el punto del teléfono de las mediciones.

La batería no se inventa: el contrato actual no transmite ese dato y falta el firmware/circuito para T22. Las pruebas físicas de radio, GPS y LED no se sustituyen por compilar.


## Escala de la interfaz

`MainActivity.UI_SCALE = 0.88` reduce la densidad de la actividad un 12 % (dp y sp a la vez) para que la app se vea como el diseño de Figma (390 dp de ancho). Cambiar ese número ajusta toda la app; 1.0 es el tamaño original.
