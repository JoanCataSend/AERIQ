# Estado · 3 de noviembre de 2026

## Cambios de esta revisión

- AERIQ escrito de forma coherente; Código Norte como desarrolladora y Orb como dispositivo.
- Recuperación de diseño, recursos, galería, visor 3D, cuenta y flota anteriores. Las pantallas Android útiles se conservan y sus datos se conectan al servidor.
- Suscripciones/venta ciudadana sustituidas por solicitudes para organizaciones, conforme a la especificación nueva.
<<<<<<< HEAD
- Esquema único con 23 tablas: catálogos de roles y permisos, servicios, estados, zonas, umbrales por magnitud y modelos de Orb en lugar de `ENUM`; claves foráneas, histórico de asignaciones y de solicitudes, y cuatro vistas para leer datos con nombres.
=======
- Esquema único v3 con 25 tablas: catálogos de roles y permisos, servicios, estados, zonas, umbrales por magnitud y modelos de Orb en lugar de `ENUM`; claves foráneas, histórico de asignaciones y de solicitudes, y cuatro vistas para leer datos con nombres.
>>>>>>> origin/rediseno-web
- Recreación explícita con copia previa y modo de simulación; importación hacia otra base sin modificar el origen.
- Invitación temporal con caducidad y cuenta no operativa hasta activación.
- Historial y exportación real en Android; comparación pública sin suscripción.

## Evidencia local

Pruebas del backend con PHP/MariaDB aislados: roles, dos organizaciones, campos, caducidad, activación, correo fallido, idempotencia, publicación/retirada, reasignación conservando histórico, bajas y solicitudes. También se comprueban el reset sobre una base temporal y la importación de lecturas antiguas sin inventar GPS.

Pruebas JS de estadísticas/IDW/CSV y pruebas DOM de portada/3D, galería, flota, secciones, filtros, informes, formulario y mapa. Compilación Android y pruebas JVM del parser, interpolación y CSV. No se presentan las respuestas ficticias de DOM como mediciones reales.

## Pendiente

La aprobación del control de Chrome local agotó su tiempo y no se completó revisión visual. No se ha ejecutado sobre Plesk ni sobre su base actual. Faltan entrega real de correo, prueba visual/dispositivo y extremo a extremo con Orb físico. T22 necesita firmware y circuito/pines de batería/LED. No se ha supuesto ese contrato ni se muestra un porcentaje ficticio.

La [matriz T13–T30](tareas-sprint.md) deja cada dependencia pendiente identificada.
