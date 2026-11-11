# Despliegue AERIQ

## Elegir el camino de datos

No mezcles el esquema nuevo con la ampliación anterior. Configura una base AERIQ vacía y utiliza uno de estos caminos:

1. **Demostración nueva:** importar `src/database/schema.sql` y después `seed-demo.sql`, o utilizar el script de recreación con `--demo`.
2. **Conservar datos:** importar solo `schema.sql` en una base distinta y vacía y ejecutar `migrate-legacy.php` desde la base anterior. El origen no se modifica.
3. **Reinstalar en Plesk:** usar [los SQL de reinicio](../src/database/reinicio/LEEME.md) para recrear `jcatsen_pbio` y dar de alta el primer administrador.

El [README de base de datos](../src/database/README.md) contiene comandos, cuentas de demostración y límites de la importación. La antigua `001_airq.sql` se conserva solo en el histórico. No debe aplicarse a esta versión.

## Requisitos

PHP 8.1+ recomendado según Composer, MySQLi con `get_result`, mbstring, MariaDB 10.4+, HTTPS y Apache con `.htaccess` o protecciones equivalentes. La sintaxis y pruebas locales usan PHP de XAMPP y MariaDB aislada. Para SMTP se necesita instalar la dependencia de Composer; para `mail()` se necesita el servicio del alojamiento.

## Preparar archivos

```powershell
python scripts/audit-repository.py
node scripts/tests/aeriq-domain.test.js
python scripts/build-plesk-package.py --output .dist/aeriq-plesk
```

Publica **solo el contenido de `src/web/`** en `httpdocs/src/web/`. En este alojamiento la web real es `https://ldiamur.upv.edu.es/src/web/login.html`, sin una carpeta adicional en la raíz. Los SQL, scripts CLI y respaldos se mantienen fuera de la raíz pública. El paquete no incluye credenciales ni dependencias instaladas. El generador rechaza sobrescribir una salida existente.

Para el parche concreto de Google/Firebase:

```powershell
python scripts/build-google-plesk-zip.py
```

El resultado es `.dist/airiq-google-plesk.zip`, que se extrae sobre `httpdocs/` (contiene `src/web/...` y `INSTRUCCIONES-PLESK.txt`). No incluye `FirebaseConfig.php` ni las configuraciones privadas. La migración queda en `.dist/anadir-firebase-google.sql` para importarla desde phpMyAdmin.

En `server/SDBaseDatos.php` configura el nombre de la base nueva y las credenciales del entorno. Conserva la configuración privada y `.htaccess`; no publiques la raíz del repositorio. Mantén la base anterior respaldada hasta validar datos y funcionamiento. El cambio de configuración/deployment en Plesk no se ha realizado durante esta revisión.

## Primer administrador

La reinstalación de `jcatsen_pbio` incluye [02-admin.sql](../src/database/reinicio/02-admin.sql) con la cuenta inicial. Los datos demo ya incluyen las seis cuentas documentadas. Para promover una cuenta visitante existente y verificada, está disponible el comando CLI:

```text
php scripts/bootstrap-organization.php admin CORREO_VERIFICADO "NOMBRE ORGANIZACIÓN"
php scripts/bootstrap-organization.php comercial CORREO_VERIFICADO "Código Norte"
```

El comando solo eleva una cuenta visitante ya verificada y rechaza sobrescribir roles. Si se ejecuta fuera del repositorio, pasa como cuarto argumento la ruta real a la raíz web instalada. No hay un botón público para convertirse en administrador.

## Correo e invitación

Configura `MailConfig.php` para `mail()` o `SMTPConfig.php` más `composer install --no-dev`. Parte de los ejemplos sin versionar secretos. `AERIQ_COMMERCIAL_EMAIL` es opcional para avisos; el registro persistente es la fuente de verdad. `AERIQ_INVITATION_HOURS` configura la caducidad temporal, 24 h por defecto.

Una invitación crea la cuenta pendiente de activación. El cambio inicial o la recuperación por correo verifican el acceso y activan la cuenta. Si falla el transporte de invitación, el alta se revierte. La aceptación del transporte no garantiza llegada a bandeja: comprobarlo en el alojamiento sigue siendo necesario.

## Acceso con Google (Firebase)

1. En Firebase Console, proyecto `airiq-e9d70`, habilita `Authentication > Sign-in method > Google`.
2. En `Authentication > Settings > Authorized domains`, añade `ldiamur.upv.edu.es` (sin ruta) y `localhost` para pruebas. Si la API key tiene restricciones de referrer, autoriza `https://ldiamur.upv.edu.es/*`.
3. La base debe tener el esquema v2 (`schema.sql` o `migracion-v2-catalogos.sql`): los vínculos con Google se guardan en `identidades_externas`. `anadir-firebase-google.sql` solo es para bases anteriores y no hace nada en una base v2.
4. La configuración pública del proyecto ya está en `src/web/server/FirebaseConfig.example.php` y se usa por defecto. Si el servidor necesita otro proyecto, crea `src/web/server/FirebaseConfig.php` (ignorado por Git) a partir del ejemplo y cambia `api_key`, `app_id` y `messaging_sender_id`. El SDK web se carga desde el CDN de Firebase: no hace falta `npm install` ni compilar. No publiques claves de servicio.
5. Invita al operador con el correo exacto de su cuenta de Google. El primer acceso con Google guarda el UID en `identidades_externas`, activa una invitación vigente y anula la contraseña temporal. Un correo que no exista en `usuarios` se rechaza: Google no crea cuentas.
6. Comprueba `https://ldiamur.upv.edu.es/src/web/auth.php?action=firebase-config` y que `configured` sea `true`.

La verificación del token se hace en PHP con los certificados públicos de Google; no se necesita clave de servicio ni Firebase Admin. El servidor debe permitir conexiones HTTPS salientes y tener OpenSSL. Si Google rota sus certificados, se refrescan automáticamente.

## App y demostración

La app mantiene `com.aeriq.app` y la identidad AERIQ. `data/ApiRoutes.java` centraliza el servidor en `https://ldiamur.upv.edu.es/src/web/`. Compila el APK después de fijar ese origen si usas otro alojamiento.

1. Administrador: crear/consultar campaña, registrar Orb y asignar trabajador.
2. Trabajador: entrar, sustituir temporal, actualizar asignaciones y seleccionar anuncios del Orb autorizado.
3. Activar captura/envío; GPS es opcional. Comprobar recepción y servidor por separado.
4. Consultar historial/informe y CSV. Autorizar publicación para ver las lecturas geolocalizadas.
5. Probar solicitudes y seguimiento con comercial y aislar otra organización.

Las MAC demo son ficticias: sustituirlas por las reales para BLE. La base de ejemplo solo contiene datos simulados. Los LED y la transmisión de batería siguen necesitando el firmware/circuito físico.
