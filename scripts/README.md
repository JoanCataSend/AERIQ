# Herramientas del repositorio

Requieren Python 3.9 o superior y solo usan la biblioteca estándar. Puedes ejecutarlas desde
cualquier carpeta: resuelven la raíz a partir de su propio archivo.

## Auditoría

```powershell
python scripts/audit-repository.py
```

Comprueba rutas documentadas, referencias HTML y disponibilidad de clientes,
JSON de Composer y exclusión Git de configuraciones privadas. Si están disponibles,
ejecuta Node para revisar sintaxis JS y PHP para revisar sintaxis del backend.
Devuelve código 1 ante un fallo y 0 si pasan las comprobaciones disponibles;
informa por separado de las omitidas por falta de herramientas.

## Paquete Plesk

```powershell
python scripts/build-plesk-package.py
```

Genera `.dist/plesk/AERIQ/` desde `src/web/`. Incluye archivos de ejecución,
recursos, ejemplos de configuración y protecciones Apache. Omite archivos privados,
documentación y dependencias instaladas, que se configuran en el alojamiento.
No conecta con Plesk ni con MariaDB.

Si el destino existe, no lo sustituye. Usa una ruta nueva dentro de `.dist/`:

```powershell
python scripts/build-plesk-package.py --output .dist/revision-02/AERIQ
```

Consulta [despliegue](../doc/despliegue.md) para completar la instalación.
