# Cobertura de las historias AERIQ

La [especificación completa](especificacion-sprint.md) contiene los criterios de las 15 HU. Esta matriz sitúa su implementación; el [backlog de tareas](tareas-sprint.md) distingue pruebas locales de pendientes físicos.

| HU | Qué se conserva y completa |
| --- | --- |
| HU01 | Alta web de trabajadores, organización/rol, invitación por correo y activación; no hay elevación por registro público. |
| HU02 | Login/recuperación originales, cambio inicial obligatorio, caducidad temporal y sesiones. |
| HU03 | Diseño/controles de mapa conservados, con lectura autorizada y valor/unidad/fecha/posición/origen. |
| HU04 | Portada y Orb 3D originales; AERIQ como producto, Código Norte como desarrolladora, tres servicios y límites de medición. |
| HU05 | BLE original, permisos y parser; asignación histórica, unidad, fecha UTC, GPS opcional y envío autenticado idempotente. |
| HU06 | No se inventa batería; **LED/firmware pendientes de hardware y código no aportados**. |
| HU09 | Informes por campaña/zona/fechas, estadísticas, series y superaciones. |
| HU10 | Flota original convertida en gestión persistente y aislada; asignar/renombrar/alta/baja sin borrar histórico. |
| HU15 | CSV filtrado y autorizado; además se conserva y completa exportación privada desde Android. |
| HU18 | Kits para administraciones: entidad, contacto, cantidad/finalidad y registro confirmado. |
| HU19 | Kits para empresas: contacto, instalaciones, cantidad y registro confirmado. |
| HU20 | Estudios: superficie, duración, necesidades/resultados y confirmación de revisión de presupuesto. |
| HU28 | IDW local acotado 20–50 m con soporte suficiente, modos separados y zonas sin color cuando falta soporte. |
| HU29 | Diagnóstico de recepción independiente del envío; pérdida de anuncios y problemas de red no se confunden. |
| HU30 | Panel comercial privado con solicitudes, contacto, fechas, estados y aviso opcional sin perder el registro. |

## Base de demostración

El [README de datos](../src/database/README.md) describe las cuentas y valores ficticios comprensibles. Ningún reset/migración se ejecuta automáticamente sobre Plesk. Para conservar datos previos se importa a otra base vacía, manteniendo el origen.

## Límite de aceptación

Se han podido probar contratos de servidor, lógica DOM, cálculos y compilación/JVM. Las pruebas físicas BLE/GPS/LED, entrega real de correo, producción y revisión visual permanecen explícitas. Las historias adicionales del backlog que no figuran en este documento no se consideran especificadas por inferencia.
