# Sensor AERIQ · SparkFun Pro nRF52840 Mini

Firmware para SPEC 110406 O3 / ULPSM-O3. Abrir [NodoO3/NodoO3.ino](NodoO3/NodoO3.ino)
en Arduino IDE. Core **Adafruit nRF52 BSP 1.7.0**, placa **SparkFun Pro nRF52840 Mini**;
Bluefruit viene incluida en el core.

```powershell
arduino-cli compile --fqbn adafruit:nrf52:sparkfunnrf52840mini src/firmware/NodoO3
# Sustituir COMx por el puerto real:
arduino-cli upload --fqbn adafruit:nrf52:sparkfunnrf52840mini --port COMx src/firmware/NodoO3
```

## Procedencia y estructura

Referencias inspeccionadas de [PBIO-O3-GTI3A-2026](https://github.com/JoanCataSend/PBIO-O3-GTI3A-2026):

- `main`, commit `7ac3a8270b8d598e486c95d57820ba741b7794a7`: `src/firmware/NodoO3/`.
  Arquitectura Medidor → Publicador → EmisoraBLE; lecturas de ejemplo.
- `sensor-real-final`, commit `52527205122193e12279533f6850c354b67e3186`:
  [Medidor.h](https://github.com/JoanCataSend/PBIO-O3-GTI3A-2026/blob/52527205122193e12279533f6850c354b67e3186/firmware/NodoO3/Medidor.h).
  Fuente de pines, ADC, sensibilidad, compensación y filtrado.
- Contrato contrastado con el parser local `src/android/app/src/main/java/com/aeriq/app/ble/IBeaconParser.java`.

Flujo: sensor → BLE → Android → API → BBDD → web. Sólo se modifica el sensor.

| Archivo | Función |
| --- | --- |
| `NodoO3.ino` | Inicialización BLE y máquina de estados |
| `Medidor.h` | Adquisición incremental de tres canales ADC |
| `Calibracion.h` | Conversión física, filtros y diagnóstico |
| `Protocolo.h` | Serialización explícita y temporizadores |
| `tests/` | Casos de conversión, filtrado, bytes y rollover |

## Pines y fórmulas reales

VGAS → **P0.05**, VREF → **P0.28**, VTEMP → **P0.29**, masa común.
En la variante SparkFun del BSP 1.7.0 el mapa es directo: índices Arduino 5, 28, 29.
Si se cambia de placa, revisar su mapa. La escala ADC de 3.6 V no autoriza superar
los límites eléctricos de los pines; mantener el circuito del módulo sensor.

ADC 12 bits, `AR_DEFAULT`, sample time 40 µs y oversampling 16. Se descarta una
conversión al cambiar de canal y se esperan al menos 300 µs; se promedian 50
muestras por canal, separadas al menos 2 ms.

```text
V = mediaADC × 3.6 / 4095
Vplus = 2 × Vref
T = (87 / Vplus) × Vtemp − 18                    [°C]
M = −74.04 × 499 × 10⁻⁶ = −0.03694596           [V/ppm]
ppmRaw = (Vgas − (Vref + Voffset)) / M
Tc = limitar(T, −20, 50)
zeroShift = Tc <= 30 ? 0 : 0.0066 × (Tc − 30)   [ppm]
span = 1 + 0.003 × (Tc − 20)
ppb = 1000 × (ppmRaw − zeroShift) / span
salida = limitar(EMA(mediana5(ppb), alpha=0.25), 0, 20000)
```

Sensibilidad individual −74.04 nA/ppm del Data Matrix
`032824010609 110406 O3 2404 -74.04`. Comprobarla antes de usar otro sensor.
`Voffset=0` procede de la referencia; no es una calibración de cero medida.
Los coeficientes térmicos son los típicos usados en esa rama, no un nuevo ajuste
experimental. No se inventa un tiempo de estabilización: verificarlo en banco.

Se redondean O3 y temperatura con `lroundf`. La mediana arranca con las muestras
disponibles (media de las dos centrales si son pares); la EMA se inicializa con
la primera mediana. Se limita la salida, no la memoria interna de la EMA.

## Temporización y errores

No hay `delay()`, esperas activas ni espera por USB en el sketch. Se hace como
máximo una conversión por llamada al medidor. `analogRead()` y la calibración
inicial son llamadas sincrónicas acotadas del core. Las esperas se resuelven con
`millis()` y, para los 300 µs, `micros()`, con restas sin signo resistentes al rollover.
La publicidad permanece detenida durante la adquisición.

Ciclo válido: adquisición (~300 ms o más), O3 1200 ms, pausa 300 ms, temperatura
1200 ms, reposo 1500 ms. O3 y temperatura proceden del mismo ciclo y comparten
secuencia. Primera secuencia 1; después de 255 vuelve a 0 y al reiniciar empieza
de nuevo. No sirve como identificador global ni fecha.

Vplus ≤ 0.1 V, cálculo no finito o cualquier muestra ADC 0/4095 invalidan el ciclo
y reinician el filtro. Se transmite sólo estado durante 1200 ms y se reposa
1500 ms. No se publican ceros ficticios como lecturas válidas. Fuera de −20..50 °C
se marca diagnóstico y se usan coeficientes del extremo, conservando T real.
Un fallo de inicialización/construcción/arranque BLE detiene la publicación
(`radioError`), para revisar y reiniciar el dispositivo.

## Especificación exacta BLE

Anuncios legacy **non-connectable scannable**, intervalo 100 ms, radio +4 dBm.
No hay conexión GATT ni confirmación de recepción. Usar escaneo activo para
recibir el scan response; los anuncios se repiten y deben deduplicarse.

### Medidas: compatible con Android actual

AD completo (30 bytes): `02 01 06 1A FF` seguido de los 25 bytes del fabricante.
`getManufacturerSpecificData(0x004C)` en Android elimina `4C 00` y devuelve 23 bytes:

| Offset Android | Bytes | Contenido |
| --- | --- | --- |
| 0 | 2 | `02 15` |
| 2 | 16 | ASCII `EPSG-GTI-PROY-3A`, sin NUL |
| 18 | 1 | `0E` O3; `0C` temperatura; `0F` estado de ciclo inválido |
| 19 | 1 | Secuencia uint8 del ciclo |
| 20 | 2 | Big-endian: O3 uint16 ppb; temperatura int16 °C complemento a dos; estado uint16 |
| 22 | 1 | `CB` = −53 dBm de referencia RSSI; no es la potencia radio |

Ejemplo fabricante incluido: O3 **123 ppb**, ciclo **7**:

```text
4C 00 02 15 45 50 53 47 2D 47 54 49 2D 50 52 4F 59 2D 33 41 0E 07 00 7B CB
```

Temperatura **−12 °C**, mismo ciclo: sustituir `0E 07 00 7B` por `0C 07 FF F4`.
La app actual acepta tipos 14 y 12 como medidas y tipo 15 para descubrir el sensor
y mostrar el diagnóstico, sin publicarlo como medida. Versiones anteriores
descartaban el 15 y no mostraban sensores con ciclos inválidos. Sigue identificando
el sensor por MAC BLE para su asignación. Los campos iBeacon major son tipo/secuencia,
minor es el valor; el UUID es etiqueta del proyecto, no ID individual.

### Scan response: ID estable y estado, extensión v1

AD de 30 bytes: `1D 21` + UUID little-endian (16 bytes) + payload (12 bytes).
UUID **`e3b44c20-3f02-4d76-9cb2-bdcb3105a001`**; orden en radio:
`01 A0 05 31 CB BD B2 9C 76 4D 02 3F 20 4C B4 E3`.
No se incluye nombre local para respetar el límite de 31 bytes.

Android: `record.getServiceData(new ParcelUuid(UUID.fromString("e3b44c20-3f02-4d76-9cb2-bdcb3105a001")))`
devuelve los 12 bytes siguientes, sin cabecera AD ni UUID:

| Offset | Bytes | Contenido |
| --- | --- | --- |
| 0 | 1 | Versión `01`; rechazar otras versiones |
| 1 | 1 | Misma secuencia que el anuncio |
| 2 | 8 | ID uint64 big-endian: `(FICR.DEVICEID[1] << 32) \| FICR.DEVICEID[0]` |
| 10 | 2 | Máscara de estado uint16 big-endian |

Representar el ID como **16 dígitos hexadecimales**, evitando el signo de `long`
Java. Es público y estable para el chip, no es una credencial ni una dirección
MAC. La app actual todavía no interpreta esta extensión; el receptor futuro
debe asociarla por MAC y secuencia y aceptar que puede faltar el scan response.
No atribuir estado antiguo a un ciclo nuevo ni deduplicar sólo por secuencia.

Ejemplo ID `0123456789ABCDEF`, ciclo 7, válido:

```text
01 07 01 23 45 67 89 AB CD EF 00 01
```

| Bit | Máscara hex | Significado |
| --- | --- | --- |
| 0 | `0001` | Cálculo válido; no certifica estabilización física |
| 1 | `0002` | O3 filtrado negativo limitado a cero |
| 2 | `0004` | O3 filtrado superior a 20000 limitado |
| 3 | `0008` | Referencia/alimentación derivada inválida |
| 4 | `0010` | Alguna muestra ADC saturada |
| 5 | `0020` | Temperatura fuera del intervalo de compensación |
| 6 | `0040` | Cálculo no finito |
| 7–15 | | Reservados, emisor los deja a cero |

Es una máscara combinable. Sin bit 0 no guardar lecturas del ciclo. No se
transmite batería: faltan circuito/divisor y calibración. Tampoco se suponen GPIO
de LED ni se confunde recepción BLE con conexión GATT.

## Validación

Compilación verificada con BSP 1.7.0, incluido diagnóstico serie USB.
Falta validación física: cero y concentración con referencia, voltajes ADC,
estabilidad tras arranque, temperatura y escaneo activo Android. Confirmar ambas
medidas y extensión con la misma secuencia. Forzar la entrada ADC de VREF a cero
en un montaje de prueba debe generar estado sin guardar medidas falsas; una
entrada desconectada puede quedar flotante. No se ha flasheado una placa ni
publicado en Plesk.

Las pruebas C++ de `tests/` se han ejecutado en WebAssembly: conversión,
compensación, filtros, errores, vectores de bytes y rollover correctos. No
verifican eléctricamente el ADC ni el funcionamiento de la radio.

### Probar desde el monitor serie

1. Subir de nuevo `NodoO3/NodoO3.ino` a la placa; editar el archivo local no
   actualiza el firmware ya grabado.
2. Seleccionar el puerto USB de la placa en Arduino IDE y abrir el monitor a
   **115200 baudios**. El puerto puede cambiar tras la carga/reinicio.
3. Cada 2 segundos aparece una línea `AERIQ id=... ciclo=...`, con las tensiones
   VGAS/VREF/VTEMP en mV, O3 en ppb, temperatura, máscara de estado y estado BLE.
   `SIN_MEDIDA` indica que aún no hay un ciclo completo. `INVALIDA` indica que
   O3/T no deben interpretarse como mediciones; consultar la máscara anterior.
4. El contador avanza aproximadamente cada 4.5 segundos con medidas válidas.
   `BLE=OK` sólo confirma inicialización y arranque de publicidad sin error;
   no confirma recepción en Android. Verificar recepción con un escáner BLE o la
   app y las tramas descritas arriba. El nombre AERIQ no se anuncia: identificar
   por MAC, fabricante 0x004C y etiqueta de proyecto.

El firmware no espera a que se abra el monitor y descarta salida si no hay espacio
en el buffer USB. Si no se ve nada: comprobar carga completada, puerto de la
aplicación (no sólo bootloader), cable USB de datos, cerrar otros monitores y
reiniciar la placa con el monitor abierto.
