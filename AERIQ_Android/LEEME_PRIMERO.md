# AERIQ — revisión visual Android (Figma → Jetpack Compose)

## Qué cambia respecto al ZIP que ya te compilaba

- **Marca real AERIQ**: logotipo horizontal auténtico y símbolo aislado (extraídos del PDF «Identidad visual.pdf» que has enviado), sin usar caracteres Unicode como sustitutos.
- **Barra inferior**: iconos de Mapa, Mi sensor, Aprende y Cuenta tomados de las **rutas SVG originales** del frame editable `Web > ANDORID` de Figma. Incluimos sus archivos SVG de referencia en `design_assets/figma_svg/` y versiones PNG transparentes a x5 para usar en Android.
- **Iconos funcionales**: iconografía de línea 24 × 24; varios iconos se extraen del set auténtico del manual de identidad y otros se reconstruyen con trazos estilo Lucide para cubrir acciones específicas de las pantallas (atrás, ajustes, campana, huella, etc.). No se presentan esos últimos como vectores exportados exactamente del nodo de Figma.
- **Pantallas**: se han reconstruido las 11 en Compose (login, registro, mapa, aprende, mi sensor, vincular, historial, configurar, comparar, Premium, cuenta) con tarjetas, navegación, tipografía por componentes, precios, selección, chips y espaciado más próximos al Figma.
- **App en el lanzador**: usa icono corporativo y nombre «AERIQ».
- **Conserva intactos** los archivos de BLE e API del ZIP que ya compilaba; el firmware iBeacon sigue siendo el mismo.

## Cómo instalar sobre TU proyecto Android Studio que ya compila

1. **Haz una copia** de la carpeta de tu proyecto por seguridad.
2. Descomprime este ZIP.
3. Copia `app/src/main/` del ZIP a `app/src/main/` de tu proyecto, **incluyendo por completo `res/drawable-nodpi/` y `res/values/styles.xml`**. Acepta reemplazar los `.kt` y el `AndroidManifest.xml` existentes.
4. No sustituyas tus archivos `build.gradle.kts`, ni la configuración Gradle/SDK del proyecto que ya tienes funcionando. Continúa usando Kotlin + Jetpack Compose + Material 3.
5. Copia también `app/src/test/` si deseas conservar las pruebas del decodificador.
6. Pulsa `Build > Rebuild Project`; prueba las pantallas desde el móvil/emulador. Para BLE real necesitas móvil y placa SparkFun.

## Fuentes y diferencias pendientes para una identidad 100 % exacta

El **manual corporativo** adjunto especifica *Space Grotesk* para títulos e *IBM Plex Sans* para texto y números. Sin embargo, el **Figma editable Android emplea Inter** en las pantallas inspeccionadas. Son especificaciones diferentes y no deben confundirse. Hemos priorizado la geometría, los colores y los recursos del Figma para las vistas; los estilos tipográficos están centralizados en `AeriqFonts` dentro de `ui/Common.kt` para que puedas insertar la familia definitiva sin modificar 11 pantallas.

**No se incluyen ficheros de fuentes en el ZIP.** Por eso, hasta importar fuentes al proyecto Android Studio, se usa el fallback sans-serif del sistema. Es una diferencia real respecto al diseño, no debe considerarse pixel-perfect. Si importas las familias con licencia adecuada en `app/src/main/res/font/`, cambia `AeriqFonts.headings` / `AeriqFonts.body` a los correspondientes `FontFamily(Font(R.font...))`.

**Fotografía del sensor:** el frame de Figma presenta un dispositivo blanco PBIO (un relleno de imagen original) y el manual corporativo adjunto trae fotografías del producto AERIQ Orb. Este paquete incluye fotografía del manual, no el relleno raster original de ese nodo de Figma. Para reproducir esa parte exactamente faltaría exportar el bitmap original `devf1-image` de Figma (nodo `176:6253`) e incorporarlo como recurso. No lo sustituyas por una captura completa de la pantalla.

**Varias ilustraciones/gráficas de datos** se reconstruyen en Compose, porque deben ser dibujables y dinámicas; sus datos siguen siendo de demostración. No es una certificación de paridad píxel a píxel ni de compilación final en Android SDK (no disponible aquí).

## Colores y fuentes de referencia

- Figma Android: UI verde `#0E6B5E`, texto `#17201D`, fondo `#F4F7F6`, secundarios `#5E6B66`, blanco `#FFFFFF`.
- Manual corporativo: verde pino `#12342C`, esmeralda `#006B53`, menta `#DFF1E8`, blanco cálido `#FBFDFA`, grafito `#1C1F1E`.
- Manual corporativo, cap. 4: Space Grotesk + IBM Plex Sans.
- Figma (frame `176:2`): pantallas nativas a 390 px de ancho, capa editable; las pantallas pueden exceder la altura disponible y por eso usan scroll.

## BLE e integración

- El escaneo y parser iBeacon existentes siguen en `app/src/main/java/com/aeriq/app/ble/`.
- «Vincular» = seleccionar un emisor no conectable (no GATT).
- El firmware original de Sprint 0 emite datos **ficticios**; 123 ppb de ozono y −12 °C. El sensor no transmite batería ni GPS.
- Los gráficos de historial/mapa siguen mostrando ejemplos, **no** datos reales. La lectura BLE se muestra en **ppb**; no se confunde con µg/m³.
- No hay login, Google/Apple, pagos, rutas ni mapas online implementados; esas acciones informan que falta el backend.
- El POST a la API sigue condicionado a la activación voluntaria de «Compartir con la red»; de inicio está desactivado.

## Archivos

`app/src/main/java/com/aeriq/app/ui/`: cinco archivos Kotlin compartidos. `app/src/main/res/drawable-nodpi/`: logotipos, iconos y recurso fotográfico. `app/src/main/res/values/styles.xml`: barras de sistema. `app/src/main/AndroidManifest.xml`: etiqueta e icono de app. `design_assets/figma_svg/`: archivos SVG originales de navegación, usados para generar PNG de Android.

**Fuentes:** Figma Proyecto, `https://www.figma.com/design/pLq0la9g1ew4pFn6OUqUBQ/Proyecto?node-id=176-2`; PDF «Identidad visual.pdf» y los otros PDF incluidos en el ZIP aportado por el usuario.
