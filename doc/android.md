# Diseño Android

## Organización

`MainActivity.kt` crea los controladores, solicita permisos BLE y compone
`AeriqApplication`. La navegación usa un enum `Screen`; las pantallas restringidas
redirigen a login cuando falta una cuenta. `ui/Common.kt` centraliza componentes,
colores, tipografía y navegación inferior. `ui/theme/` conserva el tema generado
del proyecto; el tema aplicado por la actividad es `AeriqTheme` de `Common.kt`.

`AuthScreens.kt` contiene registro, verificación, recuperación, perfil y contraseña.
`SensorScreens.kt` contiene sensor, vinculación, historial y configuración.
`MapScreens.kt` y `RealOzoneMap.kt` gestionan mapa y localización.
`CommercialScreens.kt` contiene comparación, Premium y cuenta.

## Contrato BLE

| Campo | Valor o interpretación |
| --- | --- |
| Fabricante | `0x004C` |
| Prefijo | `02 15` |
| Identificador | `EPSG-GTI-PROY-3A`, 16 bytes ASCII |
| Major | Byte alto: tipo; byte bajo: contador |
| Tipo ozono | `14`, valor natural de 16 bits en ppb |
| Tipo temperatura | `12`, valor de 16 bits con signo en °C |
| Minor | Valor de la lectura |

El emisor es un iBeacon no conectable: «vincular» selecciona su dirección,
sin conexión GATT. El controlador evita publicar repetidamente el mismo contador
por dirección y tipo. El escaneo se detiene al pasar la actividad a segundo plano.
El firmware de referencia de Sprint 0 emite valores de prueba; su código no se
ha incorporado a esta entrega.

## Red y cuenta

`PbioApiClient` envía JSON en una cola serial limitada a 30 trabajos y no reintenta
las escrituras automáticamente. Se activa desde la opción voluntaria de compartir.
No incorpora GPS ni token de cuenta a ese POST de Sprint 0.

`AeriqAuthManager` comunica las operaciones de cuenta con `mobile-auth.php`.
`AeriqTokenStore` guarda el token de sesión con AES-GCM y Android Keystore.
Las URLs vigentes se documentan en [API](api.md); el cambio de carpetas no las altera.

## Mapa y permisos

osmdroid muestra calles OpenStreetMap. Los cinco puntos de ozono son ejemplos
definidos en `RealOzoneMap.kt`. El punto azul representa la localización obtenida
del teléfono si hay permiso aproximado o preciso. La suscripción a ubicaciones se
retira al salir de la composición del mapa; el ciclo de primer plano/segundo plano
debe incluirse en las pruebas en dispositivo.

BLE solicita `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT` en Android 12+; en versiones
anteriores utiliza permiso de ubicación precisa. El mapa solicita ubicación
aproximada y precisa. No hay permiso de ubicación en segundo plano.

El historial, recorridos, gráficos y funciones Premium incluyen comportamientos
ilustrativos. Consulta [estado](estado.md) y [pruebas](pruebas.md).
