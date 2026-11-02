# Arreglo de compilación (AERIQ)

## Qué se arregla

- `Duplicate resources`: `Theme.AERIQ` aparece en `themes.xml` de Android Studio y en `styles.xml` de la base visual previa. El tema de la app se denomina ahora `Theme.AERIQ.Visual` y el manifiesto lo utiliza.
- `android:windowLightNavigationBar requires API level 27`: el atributo pasa a `res/values-v27/styles.xml`. El tema base sigue válido para minSdk 24.

## Instalación recomendada si tienes la base anterior funcionando

1. Antes de cambiar nada, haz una copia del proyecto.
2. Extrae este ZIP de corrección (PATCH). Copia su carpeta `app/src/main/` encima de la de Android Studio y acepta reemplazar `AndroidManifest.xml` y `res/values/styles.xml`; se añade `res/values-v27/styles.xml`.
3. **No borres** tu `res/values/themes.xml`: puede quedarse porque su tema se llama `Theme.AERIQ` y ya no colisiona.
4. En Android Studio: Build > Rebuild Project.

Este parche sólo modifica el manifiesto y los estilos XML. No modifica Kotlin, BLE, pantallas ni assets.

Si usas el ZIP completo `AERIQ_Android_Visual_Figma_FIX_TEMAS.zip`, éste trae el mismo arreglo integrado; no hace falta aplicarlos ambos.

La compilación completa no se ha podido ejecutar aquí con un SDK Android; debes probar `assembleDebug` en Android Studio.
