# Herramientas AERIQ

Python 3.9+ y biblioteca estándar, salvo la prueba DOM opcional. Se resuelve la raíz desde cada script.

| Script | Función |
| --- | --- |
| `audit-repository.py` | Estructura, referencias, exclusión de secretos y sintaxis JS/PHP disponible |
| `build-plesk-package.py` | Raíz web sin secretos/dependencias instaladas; salida nueva dentro de `.dist/` |
| `build-google-plesk-zip.py` | ZIP de cambios Google/Firebase para extraer sobre `httpdocs/`, con instrucciones |
| `reset-database.py` | Recreación de una base explícita con copia previa, confirmación y `--dry-run` |
| `build-database-scripts.py` | Regenera los `recrear-*.sql` y el esquema de `reinicio/01-instalar.sql` desde `schema.sql` y `seed-demo.sql`; no conecta con ninguna base |
| `migrate-legacy.php` | Inventario/importación a otra base vacía; no borra el origen |
| `bootstrap-organization.php` | Primer administrador/comercial, únicamente por CLI sobre visitante verificado |

El [README de datos](../src/database/README.md) contiene comandos de recreación y migración. No ejecutes los scripts destructivos como paso rutinario de compilación ni desde un endpoint web. Los respaldos y configuraciones temporales están en `.local-backups/` e ignorados por Git.

## Comprobaciones

```powershell
python scripts/audit-repository.py
node scripts/tests/aeriq-domain.test.js
python scripts/tests/integration-aeriq.py --php C:/xampp/php/php.exe --mysql-bin C:/xampp/mysql/bin
php scripts/tests/google-auth-unit.php
python scripts/tests/google-auth-live.py
```

La prueba live de Google solo envía un token inválido y comprueba el despliegue; no crea cuentas. Ver [pruebas-google.md](../doc/pruebas-google.md).

La integración crea instancias aisladas, prueba recreación/importación y apaga sus procesos al terminar. No carga configuración real de Plesk y usa SMTP falso retenido localmente.

La prueba DOM necesita jsdom, instalado solo como herramienta local, no como dependencia de ejecución de la web:

```powershell
npm install --prefix .local-backups/test-tools jsdom --no-audit --no-fund
node scripts/tests/ui-contract.test.js
```

`preview-aeriq.js` sirve la interfaz con respuestas ficticias para una revisión manual local en `127.0.0.1:8766`. No ejecuta PHP ni acredita pruebas de permisos; se detiene con Ctrl+C.
