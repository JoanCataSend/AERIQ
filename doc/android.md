# Android AERIQ

## Pantallas conservadas y adaptadas

- Mapa de ozono: búsqueda, capas, oscuro/claro, ubicación opcional, leyenda, marcadores y comparación. Consulta la API pública autorizada, no una lista de puntos incrustada.
- Mi sensor: tarjeta Orb, O₃ actual, gráfica, resumen de campaña, recepción BLE y envío separados, asignaciones y control de captura.
- Vincular: mantiene la imagen Orb y las tarjetas originales; solo selecciona emisores asignados.
- Historial: Día/Semana/Mes/Año, gráficas por procedencia, mínimo/máximo y exportación CSV mediante selector de archivo Android. Datos guardados de las asignaciones del trabajador, no gráficos ficticios.
- Configurar: datos administrados del Orb, privacidad, envío y deselección.
- Aprende y Cuenta: contenido e identidad originales; cuenta/organización, contraseña, historial, ayuda y soluciones. No hay suscripciones personales.

## Red

`data/ApiRoutes.kt` centraliza `/AERIQ/`. `AeriqAuthManager` reutiliza login, verificación, recuperación y token cifrado. La contraseña temporal bloquea la app hasta activar/cambiarla; una invitación caducada requiere recuperar acceso.

`AeriqFieldClient` obtiene asignaciones e historial privados. Los POST incluyen evento estable, dispositivo, asignación, campaña, unidad, fecha UTC y GPS opcional. Reintenta una vez un fallo de transporte usando el mismo evento. La cola es de 30 lecturas, no persistente tras cerrar la app; no se promete conservación offline. Una actualización fallida de asignaciones no borra de pantalla el Orb previamente conocido y no se confunde con pérdida de Bluetooth; el servidor sigue validando cada envío.

## BLE y ubicación

Se mantienen fabricante `0x004C`, prefijo `02 15`, identificador ASCII `EPSG-GTI-PROY-3A`, tipos 14 ppb y 12 °C, contador de 8 bits y temperatura con signo. Vincular significa seleccionar anuncios iBeacon, no una conexión GATT permanente. Seleccionar de nuevo el mismo Orb no reinicia la deduplicación.

Tras 15 s sin anuncios se indica ausencia de recepción; el estado del servidor es independiente. Los permisos BLE dependen de versión Android. El GPS es opcional, solo se adjunta una posición reciente y se detiene en segundo plano. El mapa distingue el punto del teléfono de las mediciones.

La batería no se inventa: el contrato actual no transmite ese dato y falta el firmware/circuito para T22. Las pruebas físicas de radio, GPS y LED no se sustituyen por compilar.
