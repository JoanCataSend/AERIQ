# Estado del proyecto

Revisión documental: 2 de noviembre de 2026. «Implementado» indica que existe
código para esa función, no que se haya validado en producción en esta revisión.

| Parte | Implementado | Demostración o pendiente |
| --- | --- | --- |
| Web | Once páginas, navegación, carrito, galería y visor 3D | Contacto, pedidos y pagos sin servicio real |
| Cuenta | Registro, códigos, login, perfil y recuperación PHP | Requiere SQL y transporte de correo; OAuth ausente |
| Móvil | Tokens y almacenamiento cifrado | Validación de servidor y dispositivo pendiente |
| BLE | Escaneo iBeacon, selección, parser y deduplicación | Firmware externo; sus valores pueden ser ficticios |
| Medidas | API PHP y cliente Android con envío voluntario | Sin GPS, propiedad por usuario ni idempotencia |
| Mapa web | Calles OSM y puntos de MariaDB | Puntos simulados, sin estaciones oficiales |
| Mapa Android | Calles OSM y ubicación del teléfono | Puntos de ozono definidos como ejemplos locales |
| Sensores propios | Interfaz y selección del emisor | Tabla web, privacidad y asociación a cuenta pendientes |
| Historial/Premium | Pantallas y gráficos | Análisis real, suscripción, avisos y exportación móvil pendientes |
| Base de datos | Consultas existentes documentadas | Falta exportación SQL, esquema y migraciones en el repositorio |
| Despliegue | Generador local y guía | No se ha publicado ni alterado Plesk |

## Procedencia y límites

La web se sincronizó desde `tal.zip`: 12 archivos actualizados, 11 incorporados y
35 ya idénticos. Se excluyó `aa.zip`, una copia redundante de `server/`. La app
Android procede del proyecto local existente. Las notas de entregas anteriores
se guardan en [histórico](historico/README.md).

La reorganización traslada `AERIQ_Web/` a `src/web/` y `AERIQ_Android/` a
`src/android/`, sin cambiar la implementación ni las URLs remotas. Los README
y documentos vigentes sustituyen las instrucciones antiguas de ubicación.

El firmware, los PDF originales de identidad, un volcado SQL y una licencia global
del proyecto no se han aportado en esta carpeta. No se han fabricado para rellenar
la estructura. La referencia de Sprint 0 se enlaza desde el README principal.
