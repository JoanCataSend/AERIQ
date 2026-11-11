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
│       ├── schema.sql        # Esquema único, nombres en español, relaciones y vistas
│       └── seed-demo.sql     # Organizaciones, cuentas y lecturas ficticias comprensibles
├── scripts/
│   ├── audit-repository.py
│   ├── build-plesk-package.py
│   ├── reset-database.py     # Recreación explícita con copia previa y modo de simulación
│   ├── migrate-legacy.php    # Importación a otra base vacía, sin borrar el origen
│   ├── bootstrap-organization.php
│   └── tests/
└── doc/                      # Diseños, pruebas, despliegue, HU, tareas e histórico
```

## Base de demostración

El esquema nuevo no necesita las tablas antiguas de Plesk. Tiene 22 tablas con nombres claros (los roles, permisos, servicios y estados son catálogos, no `ENUM`) y cuatro vistas: `vista_mediciones`, `vista_flota`, `vista_usuarios` y `vista_solicitudes`. El diseño y sus decisiones están en [Modelo de datos](doc/base-de-datos.md). El [README de datos](src/database/README.md) explica las relaciones y las seis cuentas de ejemplo.

Antes de borrar nada puedes consultar el plan, sin conectar a la base:

```powershell
python scripts/reset-database.py --database aeriq_demo --user root --demo --dry-run
```

El borrado real requiere el nombre exacto y `--confirm RECREAR:nombre_base`; el script crea una copia completa antes de ejecutar DROP. Nunca se ejecuta automáticamente desde la web. Para conservar datos anteriores se utiliza `migrate-legacy.php` hacia una base diferente y vacía.

## Comprobaciones

```powershell
python scripts/audit-repository.py
node scripts/tests/aeriq-domain.test.js
python scripts/tests/integration-aeriq.py
```

Android: abre `src/android/` y ejecuta sus tareas Gradle. Los scripts usan Python 3.9+; la integración aislada necesita PHP y MariaDB locales. La aplicación utiliza el origen `https://ldiamur.upv.edu.es/src/web/` para cuentas, captura y mapas, centralizado en `ApiRoutes.java`.

No se ha borrado, migrado ni publicado nada en Plesk. La batería/LED del Orb necesita hardware y firmware que no están incluidos. Las pruebas de radio/GPS en móvil y la revisión visual final se registran aparte de la compilación y de las pruebas automatizadas.
