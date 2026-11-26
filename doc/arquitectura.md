# Arquitectura AERIQ

```text
Orb → anuncios BLE iBeacon → Android / AeriqFieldClient → field.php
                                                        ↓
                                                    AeriqCore
                                                        ↓
                        usuarios · asignaciones · mediciones (MariaDB)
                                                        ↓
          Mapa público web / Android ← public.php (solo publicación autorizada)
          Panel web y flota ← portal.php (rol + organización + CSRF)
          Formularios → solicitudes → panel comercial de Código Norte
```

Las cuentas web (`auth.php`, cookie/CSRF) y móviles (`mobile-auth.php`, token cifrado en Android) comparten `usuarios`, sin duplicar identidades. La nueva base es autosuficiente y no necesita tablas heredadas de Sprint 0.

## Código y responsabilidades

- `src/android/`: aplicación Java con Android Views y layouts XML, BLE, captura autorizada, historial, exportación y ubicación en primer plano. `ApiRoutes.java` centraliza el origen del servidor en `https://jcatsen.upv.edu.es/AERIQ/`.
- `src/web/`: HTML y CSS compartidos; conserva galería, visor 3D, mockups y navegación originales. Los scripts especializados conectan flota, informes, mapas y solicitudes.
- `aeriq-client.js`: clientes nuevos; `auth-client.js`: sesión existente; `aeriq-domain.js`: cálculos de informes, CSV e IDW sin DOM.
- `server/AuthCore.php`: reglas de autenticación y sesiones. `business_logic/DomainError.php`: errores semánticos. `database/SqlGateway.php` y `database/AuthRepository.php`: ejecución SQL y persistencia de autenticación. `communication/MailTransport.php`: SMTP/PHPMailer/mail() y su configuración. `server/AeriqCore.php` concentra los casos de uso y transacciones; sus consultas de dominio todavía deben migrar a repositorios específicos. `AeriqHttp.php` y las rutas manejan transporte y traducción HTTP.
- `src/database/1-instalar.sql`: entidades, claves, índices y vistas de lectura.
- `scripts/`: empaquetado, auditoría, recreación respaldada, importación no destructiva y pruebas aisladas.

## Conservar lo que funciona

Los recursos gráficos no se sustituyen. «Mi sensor», vinculación, historial, configuración, mapa, Aprende y Cuenta permanecen; sus datos y permisos se actualizan. La comparación se mantiene gratuita. Las suscripciones y compras ciudadanas se adaptan a soluciones para organizaciones, porque el backlog actual las excluye.

La API `api.php` conserva consultas de compatibilidad sobre el esquema nuevo y exige permisos para lecturas privadas. Las escrituras operativas utilizan `field.php`; no hay un segundo camino anónimo de inserción. El firmware iBeacon mantiene su contrato 12/14 y no depende de las tablas internas del servidor.

## Datos

Ozono en ppb, temperatura en °C, fechas UTC. No se convierten unidades sin base física. Datos reales y simulados se separan; IDW limita cercanía y soporte. Enviar al servidor no equivale a publicar en el mapa. Los límites ambientales configurados son parámetros del proyecto, no umbrales oficiales.
