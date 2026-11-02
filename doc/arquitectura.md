# Arquitectura

## Vista general

```text
Emisor PBIO (firmware externo a esta carpeta)
    │ anuncios BLE iBeacon
    ▼
Android: ble/ → MainActivity → data/PbioApiClient
    │ HTTPS JSON, si el usuario activa compartir
    ▼
/biometria/api.php (destino configurado en Android)
    │
    ▼
MariaDB ← lógica PHP ← src/web/api.php ← pbio-client.js ← interfaz web

Interfaz web → auth-client.js → auth.php → AuthCore.php → MariaDB / correo
Android → AeriqAuthManager → mobile-auth.php → AuthCore.php → MariaDB / correo
```

El destino de envío de medidas Android pertenece al despliegue de Sprint 0.
El endpoint que incluye este repositorio se publica bajo `/AERIQ/api.php`.
El código local no demuestra que ambos despliegues usen la misma base: hay que
confirmar sus configuraciones en el servidor.

## Responsabilidades

| Capa | Ubicación | Función |
| --- | --- | --- |
| Interfaz móvil | `src/android/app/src/main/java/com/aeriq/app/ui/` | Pantallas Compose |
| Recepción BLE | `src/android/app/src/main/java/com/aeriq/app/ble/` | Escaneo y parser |
| Clientes móviles | `src/android/app/src/main/java/com/aeriq/app/data/` | HTTP y token cifrado |
| Interfaz web | `src/web/*.html`, `app.js`, `styles.css` | Presentación e interacción |
| Clientes web | `src/web/auth-client.js`, `pbio-client.js` | Encapsulan fetch y JSON |
| Comunicación servidor | `src/web/api.php`, `auth.php`, `mobile-auth.php` | Despacho HTTP |
| Lógica servidor | `src/web/server/` | Validación, sesiones, correo y persistencia |
| Persistencia | MariaDB externa | Medidas, cuentas, códigos y sensores simulados |

Las capas web se mantienen dentro de la raíz desplegable para conservar los
`require` PHP y los enlaces relativos. No se han duplicado en carpetas paralelas.

## Dos clases de datos ambientales

`Medida` conserva lecturas recibidas del firmware. Una fila persistida puede
contener un valor de prueba emitido por ese firmware; estar en MariaDB no valida
la precisión del sensor. `AeriqMapaDemo` alimenta los puntos simulados del mapa web.
El mapa Android mantiene ejemplos definidos en código y no consulta esa tabla.

Las lecturas BLE de ozono se expresan en ppb. Los ejemplos del mapa usan µg/m³.
No existe una conversión entre ambos flujos en esta implementación.

## Identidad y privacidad

La web usa cookie de sesión y CSRF. Android usa tokens enviados por cabecera,
guardados cifrados mediante Android Keystore. Ambos clientes utilizan las tablas
de cuentas a través de endpoints distintos. La ubicación del teléfono se usa en
el mapa Android; el envío PBIO actual no añade coordenadas.

Más información: [API](api.md), [Android](android.md), [web](web.md).
