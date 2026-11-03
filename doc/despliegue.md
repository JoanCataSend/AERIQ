# Despliegue AERIQ

## Elegir el camino de datos

No mezcles el esquema nuevo con la ampliación anterior. Configura una base AERIQ vacía y utiliza uno de estos caminos:

1. **Demostración nueva:** importar `src/database/schema.sql` y después `seed-demo.sql`, o utilizar el script de recreación con `--demo`.
2. **Conservar datos:** importar solo `schema.sql` en una base distinta y vacía y ejecutar `migrate-legacy.php` desde la base anterior. El origen no se modifica.

El [README de base de datos](../src/database/README.md) contiene comandos, cuentas de demostración y límites de la importación. La antigua `001_airq.sql` se conserva solo en el histórico. No debe aplicarse a esta versión.

## Requisitos

PHP 8.1+ recomendado según Composer, MySQLi con `get_result`, mbstring, MariaDB 10.4+, HTTPS y Apache con `.htaccess` o protecciones equivalentes. La sintaxis y pruebas locales usan PHP de XAMPP y MariaDB aislada. Para SMTP se necesita instalar la dependencia de Composer; para `mail()` se necesita el servicio del alojamiento.

## Preparar archivos

```powershell
python scripts/audit-repository.py
node scripts/tests/aeriq-domain.test.js
python scripts/build-plesk-package.py --output .dist/aeriq-revision/AERIQ
```

Publica **solo el contenido de la carpeta web generada** en `/AERIQ/`. Los SQL, scripts CLI y respaldos se mantienen fuera de la raíz pública. El paquete no incluye credenciales ni dependencias instaladas. El generador rechaza sobrescribir una salida existente.

En `server/SDBaseDatos.php` configura el nombre de la base nueva y las credenciales del entorno. Conserva la configuración privada y `.htaccess`; no publiques la raíz del repositorio. Mantén la base anterior respaldada hasta validar datos y funcionamiento. El cambio de configuración/deployment en Plesk no se ha realizado durante esta revisión.

## Primer administrador

Con los datos demo ya existen las seis cuentas documentadas. Para una base real vacía, registra y verifica una cuenta y utiliza CLI:

```text
php scripts/bootstrap-organization.php admin CORREO_VERIFICADO "NOMBRE ORGANIZACIÓN"
php scripts/bootstrap-organization.php comercial CORREO_VERIFICADO "Código Norte"
```

El comando solo eleva una cuenta visitante ya verificada y rechaza sobrescribir roles. Si se ejecuta fuera del repositorio, pasa como cuarto argumento la ruta real a la raíz web instalada. No hay un botón público para convertirse en administrador.

## Correo e invitación

Configura `MailConfig.php` para `mail()` o `SMTPConfig.php` más `composer install --no-dev`. Parte de los ejemplos sin versionar secretos. `AERIQ_COMMERCIAL_EMAIL` es opcional para avisos; el registro persistente es la fuente de verdad. `AERIQ_INVITATION_HOURS` configura la caducidad temporal, 24 h por defecto.

Una invitación crea la cuenta pendiente de activación. El cambio inicial o la recuperación por correo verifican el acceso y activan la cuenta. Si falla el transporte de invitación, el alta se revierte. La aceptación del transporte no garantiza llegada a bandeja: comprobarlo en el alojamiento sigue siendo necesario.

## App y demostración

La app mantiene `com.aeriq.app` y la identidad AERIQ. `data/ApiRoutes.kt` centraliza el servidor. Compila el APK después de fijar ese origen si usas otro alojamiento.

1. Administrador: crear/consultar campaña, registrar Orb y asignar trabajador.
2. Trabajador: entrar, sustituir temporal, actualizar asignaciones y seleccionar anuncios del Orb autorizado.
3. Activar captura/envío; GPS es opcional. Comprobar recepción y servidor por separado.
4. Consultar historial/informe y CSV. Autorizar publicación para ver las lecturas geolocalizadas.
5. Probar solicitudes y seguimiento con comercial y aislar otra organización.

Las MAC demo son ficticias: sustituirlas por las reales para BLE. La base de ejemplo solo contiene datos simulados. Los LED y la transmisión de batería siguen necesitando el firmware/circuito físico.
