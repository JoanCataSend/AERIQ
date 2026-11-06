# Asignar un sensor desde Android

En **Cuenta → Buscar y asignar sensor**, activar búsqueda y encender Orb.
Se muestran todos los emisores con tramas válidas del proyecto, incluidos los que
informan de errores físicos. Cada tarjeta muestra señal, MAC y diagnóstico.
Pulsar **Asignar a mi cuenta**, escribir nombre y confirmar. Un sensor que ya
pertenece a la cuenta muestra **Usar este sensor**. La cuenta muestra sus sensores
guardados incluso si no están cerca; el escáner sólo muestra emisores recientes.

La recepción BLE y la asignación son distintas: seleccionar permite recibir en
este móvil; asignar guarda el vínculo en MariaDB. El envío de lecturas sigue
requiriendo activar su interruptor. No se cambia el diseño de la web.

## Publicación en Plesk

Instalar el APK nuevo y publicar estos archivos manteniendo sus rutas dentro de
la carpeta donde están `mobile-auth.php` y `field.php`:

```text
sensor-link.php
field.php
server/SensorLink.php
```

El ZIP de actualización contiene únicamente esos tres archivos; requiere que el
resto de la API actual ya esté desplegado. No incluye claves ni reemplaza la
configuración de base de datos. No necesita ejecutar SQL ni borrar datos.
Hasta publicar la API, la app podrá descubrir el sensor pero la asignación online
fallará con HTTP 404. El origen se configura en `data/ApiRoutes.java` y debe
coincidir con el despliegue real.

## Contrato

`POST sensor-link.php`, HTTPS, `X-AERIQ-Token` de sesión y JSON:

```json
{"address":"AA:BB:CC:DD:EE:01","name":"Mi Orb"}
```

Devuelve `ok`, `message` y `assignments`, con el mismo formato que `GET field.php`.
Sólo cuentas activadas de trabajador o administrador con organización. El usuario
se toma del token, nunca del cuerpo. Las asignaciones y el historial de captura
en `field.php` también admiten administradores y se limitan a su propio usuario.

Si el dispositivo no existe, se registra como real, activo y de la organización
de la cuenta. Si ya existe, debe ser real, activo y de esa organización. No se
reasigna un dispositivo ocupado por otra persona ni se altera uno simulado.
Las repeticiones conservan el ID de asignación y la campaña. Si su campaña
ha caducado, el servidor devuelve conflicto para que la organización la revise.

Para un dispositivo libre se crea o reutiliza una campaña privada vigente
`Mis mediciones · <idUsuario>`, con zona `Captura personal`, desde hoy hasta un
año después. `publicar=0`; privado significa excluido del mapa público y acceso
del usuario a sus asignaciones, conservando la gestión de su organización.
No cambia la publicación de campañas existentes. El vínculo y la campaña se
guardan juntos en una transacción, con exclusión de asignaciones simultáneas.

Errores: 401 sesión, 403 función/organización, 409 ocupado/no disponible, 422 campos.
El nombre enviado sólo se usa al dar de alta el sensor; no renombra dispositivos
existentes. El MAC BLE no es una prueba criptográfica de propiedad del hardware.

## Verificación

`gradlew.bat testDebugUnitTest assembleDebug` verifica Java y genera el APK.
`python -X utf8 scripts/tests/integration-aeriq.py` usa una BBDD temporal y prueba
alta real privada, persistencia, idempotencia, formato y aislamiento entre usuarios
y organizaciones, además de los flujos existentes. No opera sobre Plesk.
