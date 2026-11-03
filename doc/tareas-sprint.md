# T13–T30: implementación y comprobación

Revisión del 3 de noviembre de 2026. La [especificación recibida](especificacion-sprint.md) es la referencia del alcance. «Probado localmente» no significa desplegado en Plesk ni probado con el Orb físico.

| Tarea | Resultado de esta revisión | Comprobación y pendiente |
| --- | --- | --- |
| T13 | Esquema único, claves compuestas, histórico de asignaciones y vistas legibles. Scripts para crear/recrear e importar origen anterior. | Pruebas sobre bases temporales: conserva 123 ppb y −12 °C sin GPS inventado, origen intacto y recreación con copia. Pendiente contrastar volcado real. |
| T14 | Alta de trabajadores dentro del diseño web original/panel por secciones, validación y roles en servidor. | API y lógica DOM; roles ajenos, duplicados y errores. Revisión visual responsive pendiente. |
| T15 | Invitación aleatoria, hash, cuenta no operativa, plazo 24 h configurable y activación obligatoria. | Caducidad, bloqueo previo y fallo de transporte probados con SMTP falso. Falta entrega real del alojamiento. |
| T16 | Login, cambio inicial, token, cierre y recuperación reutilizados/adaptados en la app. | Servidor, compilación y pruebas JVM. Recorrido visual en móvil pendiente. |
| T17 | Contrato iBeacon original, permisos, parser y deduplicación; se conserva la pantalla de vinculación. | Pruebas del parser. Recepción/radio física pendiente. |
| T18 | Orb, asignación, operador, campaña, fecha UTC, unidad y GPS opcional en el envío autenticado. | API y compilación. GPS real/denegación en dispositivo pendiente. |
| T19 | Validación de formato, unidad, rangos, fechas, asignación e idempotencia; trazabilidad por evento. | Integración: rechazo de campos/roles incorrectos y reintentos sin duplicar. |
| T20 | Diagnóstico BLE y servidor separados dentro de «Mi sensor»; ausencia de anuncios y error de red diferenciados. | Compilación. Pérdida de radio/Internet real pendiente; no se borra el Orb cacheado por un fallo de actualización. |
| T21 | Tabla de flota restaurada, búsqueda/filtros, altas/bajas, nombres y asignaciones históricas. | API, DOM y aislamiento entre organizaciones; las lecturas mantienen su operador anterior al reasignar. |
| T22 | Estado «batería no transmitida» coherente con el contrato disponible. | **Pendiente:** falta firmware/esquema eléctrico/GPIO para programar y validar LED. No se han inventado niveles ni pines. |
| T23 | Marca AERIQ y recursos originales; portada/3D/galerías conservados, tres servicios y crédito a Código Norte. | DOM, enlaces y comparación de recursos. No hay suscripciones personales ni venta ciudadana. |
| T24 | Mapas web/Android, ámbito público/organización en web, periodo, origen, valor/unidad/fecha/coordenadas. | Permisos y lógica DOM; mapa Android compila. Navegación física/render visual pendiente. |
| T25 | IDW local por campaña, tres posiciones distintas, radio 20–50 m, sin extrapolación a distancia arbitraria. | Pruebas JS/Kotlin de muestras densas, dispersas, repetidas y procedencias mezcladas. No equivale a validación científica. |
| T26 | Formulario con tres servicios y campos condicionales, consentimiento, confirmación y recorrido de cantidad conservado. | DOM y API. Revisión visual responsive pendiente. |
| T27 | Solicitudes persistentes, panel comercial privado, estados y aviso opcional. | Permisos, cambio de estado y persistencia aun fallando el correo. |
| T28 | Filtros, estadísticas, gráficas, tablas y sucesos de umbral con real/simulado separado. | Cálculos JS y consultas autorizadas. Los umbrales son de demostración/proyecto, no legales. |
| T29 | CSV de filtros autorizados y CSV privado Android; fechas, unidades, coordenadas y escape correcto. | JS y JVM; coordenadas negativas y neutralización de fórmulas textuales. Apertura manual en hoja de cálculo pendiente. |
| T30 | Recorrido de servidor con varios roles, datos demo, negativos y trazabilidad documentados. | Automatización local superada; extremo a extremo con radio, correo y entorno Plesk pendiente. |

Las estimaciones originales de 122 h son planificación, no una declaración de horas ejecutadas aquí. No se han marcado como terminadas las comprobaciones físicas o de producción que no se han realizado.
