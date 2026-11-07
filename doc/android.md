# Android AERIQ

## Pantallas conservadas y adaptadas

- Mapa de ozono: mapa real OpenStreetMap a pantalla completa, panel inferior arrastrable (plegado: zona y valor; desplegado: calidad, hora, Ver historial y Comparar), con buscador mundial (Photon/OSM y, si falla, Geocoder de Android; sugerencias mientras escribes), controles flotantes de ubicación, capas y actualizar, leyenda en la parte inferior del mapa, aviso solo si la zona elegida tiene ozono moderado o alto, y ficha de zona (valor, calidad, hora, Ver historial, Comparar). Solo mediciones reales: se quitó el selector Reales/Simulados.
- Mi sensor: tarjeta del sensor PBIO con foto, estado de recepción con antigüedad (s, min, h, días), Bluetooth azul/gris y batería (“—” mientras el firmware no la transmita), O₃ ahora con calidad y gráfica de área del día, Tu día (distancia por GPS, O₃ medio, barra de calidad por tramos), accesos a Estado del sensor (diagnóstico, servidor, reintentos) y Configurar sensor, e interruptor Compartiendo con la red.
- Vincular: imagen del sensor PBIO, interruptores Bluetooth (búsqueda) y Ubicación (permiso), lista de sensores cercanos con señal; el más fuerte o el vinculado se destaca con el botón Vincular. Al abrir la pantalla empieza a buscar.
- Historial: pestañas Día/Semana/Mes/Año, valor actual, media, barras por tramo coloreadas por calidad (tramos futuros en gris), máximo/mínimo/media/mediciones y exportación CSV o PDF (PdfDocument nativo). Engranaje → Configurar sensor.
- Configurar sensor: estado en directo (conexión, batería, señal, última sincronización, SPEC O₃), nombre del sensor guardado en el móvil, Compartir con la red, Guardar mi recorrido y distancia (sin él las lecturas se envían sin GPS), Reconectar y Desvincular.
- Aprende: tarjeta verde, pestañas Ozono/Salud/Normativa, tarjetas con icono, enlace a la Directiva 2008/50/CE y otros contaminantes.
- Cuenta: perfil, Hazte Premium, datos personales, Mi sensor, contraseña, acceso con huella, avisos de ozono, compartir, exportar, privacidad, pedidos (web), ayuda y cerrar sesión.
- Comparar zonas (24 h/7 días/30 días con mediciones públicas reales; solo zonas con ≥3 puntos) y Premium (planes anual/mensual; el pago se abre en la web).
- Iniciar sesión: correo y contraseña, Google/Apple (aún no activos) y Entrar con huella debajo. Sin registro público: las cuentas las crea la organización. Con «Acceso con huella» activado, la sesión guardada solo se abre tras verificar la huella (BiometricPrompt, Android 9+).

## Red

`data/ApiRoutes.kt` centraliza `/AERIQ/`. `AeriqAuthManager` reutiliza login, verificación, recuperación y token cifrado. La contraseña temporal bloquea la app hasta activar/cambiarla; una invitación caducada requiere recuperar acceso.

`AeriqFieldClient` obtiene asignaciones e historial privados. Los POST incluyen evento estable, dispositivo, asignación, campaña, unidad, fecha UTC y GPS opcional. Reintenta una vez un fallo de transporte usando el mismo evento. La cola es de 30 lecturas, no persistente tras cerrar la app; no se promete conservación offline. Una actualización fallida de asignaciones no borra de pantalla el Orb previamente conocido y no se confunde con pérdida de Bluetooth; el servidor sigue validando cada envío.

## BLE y ubicación

Se mantienen fabricante `0x004C`, prefijo `02 15`, identificador ASCII `EPSG-GTI-PROY-3A`, tipos 14 ppb y 12 °C, contador de 8 bits y temperatura con signo. Vincular significa seleccionar anuncios iBeacon, no una conexión GATT permanente. Seleccionar de nuevo el mismo Orb no reinicia la deduplicación.

Tras 15 s sin anuncios se indica ausencia de recepción; el estado del servidor es independiente. Los permisos BLE dependen de versión Android. El GPS es opcional, solo se adjunta una posición reciente y se detiene en segundo plano. El mapa distingue el punto del teléfono de las mediciones.

La batería no se inventa: el contrato actual no transmite ese dato y falta el firmware/circuito para T22. Las pruebas físicas de radio, GPS y LED no se sustituyen por compilar.


## Escala de la interfaz

`MainActivity.UI_SCALE = 0.88` reduce la densidad de la actividad un 12 % (dp y sp a la vez) para que la app se vea como el diseño de Figma (390 dp de ancho). Cambiar ese número ajusta toda la app; 1.0 es el tamaño original.
