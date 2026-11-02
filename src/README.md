# Código del proyecto

| Carpeta | Responsabilidad | Punto de entrada |
| --- | --- | --- |
| [android](android/README.md) | App móvil, BLE, mapa y clientes HTTP | `app/src/main/java/com/aeriq/app/MainActivity.kt` |
| [web](web/README.md) | Interfaz web y backend PHP desplegable | `index.html`, `api.php`, `auth.php`, `mobile-auth.php` |
| [database](database/README.md) | Requisitos para restaurar MariaDB | Documentación del esquema; falta la exportación SQL |

No hay copias paralelas del código. `scripts/build-plesk-package.py` genera
una carpeta de publicación a partir de `web/`. Las dependencias y resultados
de compilación se regeneran en cada entorno.

Consulta la [arquitectura](../doc/arquitectura.md) para el recorrido de datos.
