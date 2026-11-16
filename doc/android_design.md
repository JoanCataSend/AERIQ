# Diseño Android AERIQ

## Component Design

La aplicación Android permite iniciar/cerrar sesión, vincular un Orb asignado, recibir anuncios BLE iBeacon, conservar lecturas pendientes sin conexión, adjuntar ubicación cuando se autoriza y consultar historial/mapa. La coordinación de pantalla vive en `MainActivity` y `ui/`; recepción y parsing están en `ble/`; API, sesión y cola local en `data/`. El origen de la API se centraliza en `data/ApiRoutes.java`.

**Flujo de lectura:** anuncio BLE → validar tipo/valor y deduplicar → asociar al Orb/campaña vigentes → añadir fecha y ubicación autorizada → encolar → enviar con token móvil → retirar de la cola al confirmar. Los reintentos conservan el `eventId` para que la API sea idempotente.

## Design Clarifications

- El Orb transmite anuncios iBeacon; no se supone una conexión BLE permanente.
- El consentimiento de ubicación puede denegarse. La lectura sigue siendo válida con coordenadas ausentes.
- Los estados de recepción BLE y envío a Internet se muestran por separado.

## General Rules

- **Programming Language:** Java 11 para la app Android; XML para recursos y layouts.
- Respetar permisos por versión Android, ciclo de vida y accesibilidad; no bloquear el hilo principal con I/O.
- Mantener compatibilidad con el formato iBeacon del firmware y contratos de `doc/api.md`.
- **Function/Method Headers:** documentar el diseño lógico de nuevas funciones/métodos en un bloque entre líneas `--------------------`.
- **Code Readability:** funciones enfocadas, nombres explícitos y comentarios para invariantes no obvias.
- **Automated Testing:** generar pruebas JVM para parser, deduplicación, cola/reintentos, CSV y transformaciones de dominio.
