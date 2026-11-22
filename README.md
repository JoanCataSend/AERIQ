# AERIQ · Orb · Código Norte

Plataforma de monitorización ambiental: **AERIQ** es el producto, **Orb** el sensor y **Código Norte** la empresa desarrolladora. El flujo es Orb → BLE/iBeacon → Android → API PHP → MariaDB → web y mapas.

Esta revisión conserva los diseños, recursos, galería y visor 3D anteriores. Completa las historias HU01–HU30 aportadas y las tareas T13–T30, manteniendo la separación entre datos reales y simulados y entre organizaciones. Las funciones antiguas de suscripción/venta ciudadana se sustituyen por presupuestos para organizaciones, como pide el backlog.

## Entradas

| Necesito… | Documento |
| --- | --- |
| Entender las partes | [Arquitectura](doc/arquitectura.md) |
| Trabajar en la web | [README web](src/web/README.md) |
| Abrir la app | [README Android](src/android/README.md) |
| Entender o recrear la base | [README base de datos](src/database/README.md) |
| Preparar Plesk | [Despliegue](doc/despliegue.md) |
| Revisar las 15 historias | [Historias](doc/historias-usuario.md) |
| Revisar las 18 tareas | [Sprint backlog](doc/tareas-sprint.md) |
| Ejecutar comprobaciones | [Pruebas](doc/pruebas.md) |
| Conocer límites pendientes | [Estado](doc/estado.md) |

```text
httpdocs/
├── src/
│   ├── android/              # Proyecto Android Studio, pruebas y recursos originales
│   ├── web/                  # Raíz desplegable: HTML/CSS/JS, API PHP y server/
│   └── database/
│       ├── 1-instalar.sql    # Instala la base completa (estructura, catálogos y admin principal)
│       └── 2-simulacion.sql  # Opcional: campañas, AERIQ, lecturas y cuentas de prueba
├── scripts/
│   ├── audit-repository.py
│   ├── build-plesk-package.py
│   ├── migrate-legacy.php    # Importación a otra base vacía, sin borrar el origen
│   ├── bootstrap-organization.php
│   └── tests/
└── doc/                      # Diseños, pruebas, despliegue, HU, tareas e histórico
```

## Base de datos

Se instala con dos archivos de [src/database](src/database/README.md): `1-instalar.sql` (obligatorio) y `2-simulacion.sql` (datos de prueba, opcional). Se importan desde phpMyAdmin en la base que tengas seleccionada, sea cual sea su nombre. Tiene 26 tablas y cuatro vistas. El diseño y sus decisiones están en [Modelo de datos](doc/base-de-datos.md).

Para llevar una base con datos reales a otro ordenador, usa **Exportar** e **Importar** de phpMyAdmin (ver el [README de datos](src/database/README.md)).

## Comprobaciones

```powershell
python scripts/audit-repository.py
node scripts/tests/aeriq-domain.test.js
python scripts/tests/integration-aeriq.py
```

Android: abre `src/android/` y ejecuta sus tareas Gradle. Los scripts usan Python 3.9+; la integración aislada necesita PHP y MariaDB locales. La aplicación utiliza el origen `https://jcatsen.upv.edu.es/AERIQ/` para cuentas, captura y mapas, centralizado en `ApiRoutes.java`.

No se ha borrado, migrado ni publicado nada en Plesk. La batería/LED del Orb necesita hardware y firmware que no están incluidos. Las pruebas de radio/GPS en móvil y la revisión visual final se registran aparte de la compilación y de las pruebas automatizadas.
