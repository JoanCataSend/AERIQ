# Interpolación acotada y zonas sin datos (T25 · HU28)

El mapa representa las áreas medidas sin extenderlas a distancias arbitrarias. Una zona solo recibe un nivel de ozono si hay mediciones suficientes cerca; si no, queda **sin datos**. Sin datos no significa aire limpio.

El cálculo es el mismo en la web (`src/web/aeriq-domain.js`) y en Android (`data/PublicReadings.java`), con los mismos parámetros y las mismas pruebas.

## Algoritmo

Ponderación inversa a la distancia (IDW) local y acotada:

1. **Grupos.** Solo se interpolan juntas las lecturas de la misma campaña, la misma unidad y el mismo origen (real o simulado). Reales y simuladas nunca se mezclan.
2. **Posiciones de soporte.** Dentro de cada grupo, las lecturas a menos de `separacionMin` entre sí cuentan como una sola posición: la deriva del GPS de un trabajador parado no crea cobertura. Cada posición queda representada por su lectura más reciente, sea cual sea el orden en que lleguen los datos.
3. **Estimación en un punto.** Se toman las posiciones de soporte a una distancia menor o igual que el radio:

   | Situación | Resultado |
   | --- | --- |
   | Radio fuera de 20–50 m | No se estima (`radio`) |
   | Ninguna posición dentro del radio | Sin datos (`lejos`) |
   | 1 o 2 posiciones | Datos insuficientes (`insuficiente`) |
   | Posiciones de grupos distintos | No se estima (`mezcla`) |
   | 3 o más posiciones | `valor = Σ(vᵢ·wᵢ) / Σwᵢ`, con `wᵢ = 1 / max(distanciaMin, dᵢ)^potencia` |

   Al ser una media ponderada, el valor nunca sale del rango de las mediciones usadas. La distancia es haversine con radio terrestre de 6 371 000 m.
4. **Pintado.** El mapa se divide en celdas de `celda` metros alrededor de las mediciones y cada celda se evalúa en su centro.

## Parámetros

Están en un único sitio por plataforma: `AeriqDomain.INTERPOLACION` en la web y las constantes de `PublicReadings` en Android.

| Parámetro | Web | Android | Valor | Significado |
| --- | --- | --- | --- | --- |
| Radio mínimo y máximo | `radioMin`, `radioMax` | `RADIUS_MIN`, `RADIUS_MAX` | 20 y 50 m | Rango admitido del radio |
| Radio por defecto | `radio` | `RADIUS_DEFAULT` | 30 m | El de una campaña nueva |
| Posiciones mínimas | `minPosiciones` | `MIN_POSITIONS` | 3 | Soporte exigido dentro del radio |
| Separación mínima | `separacionMin` | `MIN_SEPARATION` | 5 m | Por debajo, dos lecturas son la misma posición |
| Potencia | `potencia` | `POWER` | 2 | Exponente del peso |
| Distancia mínima | `distanciaMin` | `MIN_DISTANCE` | 1 m | Evita dividir entre cero sobre una medición |
| Celda | `celda` | `CELL` | 10 m | Lado de la celda pintada |

En la web, `support`, `estimate` e `interpolate` aceptan un último argumento con los parámetros que se quieran cambiar, por ejemplo `{separacionMin: 1}`.

### El radio

- **Por campaña.** Se guarda en `campanas.radio_m` (20–50, por defecto 30) y se edita en el panel de organización. El servidor rechaza valores fuera de rango y la base de datos tiene un `CHECK`. Viaja con cada medición del mapa (`radio_m`).
- **De prueba, en el mapa.** El control «Radio de interpolación» (web: barra lateral; Android: «Capas») sustituye al de la campaña solo en esa vista, de 20 a 50 m en pasos de 5. No se guarda ni se envía al servidor; «Usar el radio de cada campaña» lo deshace.

## Qué muestra el mapa

| Aspecto | Significado |
| --- | --- |
| Celda verde, naranja o roja | Valor estimado con tres o más posiciones dentro del radio; el color sale de los umbrales de la campaña |
| Celda gris con borde discontinuo | Datos insuficientes: hay una o dos posiciones cerca, pero no bastan |
| Sin color | Sin datos: no hay mediciones dentro del radio |

Al pulsar cualquier punto del mapa se explica ese punto: «Estimación: 62 ppb · moderado», con cuántas posiciones y a qué distancia está la más cercana, y la advertencia de que es un valor interpolado, no medido; «Datos insuficientes», con cuántas posiciones hay y cuántas hacen falta; o «Sin datos», con la distancia a la medición más cercana. La consulta no cambia la medición seleccionada ni la exportación CSV.

Por rendimiento se pintan como máximo 2.500 celdas en la web y 1.500 en Android. Si se alcanza el tope, el mapa lo avisa («Zonas recortadas…» o «Zonas incompletas…») para que una zona recortada no se confunda con una zona sin datos.

## Validación

Los mismos casos y los mismos valores esperados (tolerancia 1e-6) están en `scripts/tests/aeriq-domain.test.js` y en `PublicInterpolationTest.java`. Se evalúa una rejilla de 18 × 21 celdas alrededor del campus.

| Caso | Datos | Resultado comprobado |
| --- | --- | --- |
| Denso | Rejilla 5 × 3 de la simulación, posiciones a ~11 m | Con 30 m: 53 celdas estimadas, 23 insuficientes y 302 sin datos; valores dentro del rango de las muestras; ninguna celda estimada a más del radio de una medición |
| Radio configurable | Los mismos datos con 20, 30, 40 y 50 m | 30, 53, 87 y 126 celdas estimadas |
| Disperso | Tres puntos a ~90 m | Ninguna celda estimada con ningún radio admitido |
| Disperso | Dos posiciones próximas | Solo datos insuficientes |
| Disperso | Tres puntos a ~40 m | 0, 3, 11 y 27 celdas con 20, 30, 40 y 50 m |
| Deriva GPS | Tres lecturas a menos de 5 m | Una sola posición; nunca se colorea |
| Simulado | El caso denso marcado como simulado | Los mismos valores que con datos reales |
| Mezcla | Una lectura real, de otra unidad o de otra campaña entre las demás | No se estima |
| Límites | Radio 19, 51, 0 o no numérico | No se estima; 20 y 50 sí |
| Repetidos | Varias lecturas en una posición, en distinto orden | Siempre cuenta la más reciente |

Además:

- `scripts/tests/map-interpolation.test.js` ejecuta el mapa web en jsdom con un doble de Leaflet: una medición aislada solo produce celdas grises, el caso denso produce celdas de color que coinciden con el cálculo directo en su centro, el control de radio cambia la cobertura, la consulta de un punto da los tres mensajes y el recorte se avisa.
- `scripts/tests/integration-aeriq.py` comprueba que el servidor rechaza radios de campaña fuera de 20–50 m y que el mapa público devuelve el radio guardado.
- `scripts/tests/preview-aeriq.js` sirve posiciones ficticias (un grupo denso, un par y un punto aislado) para revisarlo a ojo.

## Límites

- No es una validación científica: IDW con estos parámetros es una representación prudente, no un modelo de dispersión del ozono.
- Dentro del periodo elegido en el mapa (24 h, 7 días o histórico), cada posición usa su lectura más reciente; lecturas de horas distintas pueden combinarse en una misma zona.
- La precisión depende del GPS del teléfono. La separación mínima de 5 m es un valor de partida, no una medida de la precisión real.
- Los umbrales de color son parámetros de cada campaña, no umbrales oficiales.
- En Android el mapa solo carga mediciones reales y las celdas no se recalculan al mover el mapa.
