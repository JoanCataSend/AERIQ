# Contratos HTTP observados

Las rutas siguientes se refieren a la carpeta web desplegada en `/AERIQ/`.
Las respuestas son JSON; la validación definitiva requiere el servidor y su base.

## Medidas: `api.php`

| Método | Consulta | Resultado |
| --- | --- | --- |
| GET | `?accion=health` | Estado de conexión y nombre de base |
| GET | `?accion=medidas` | Hasta 500 filas, ordenadas por fecha descendente |
| GET | `?accion=dispositivos` | Dispositivos registrados |
| GET | `?accion=tipos` | Tipos y unidades |
| GET | `?accion=ultima&dispositivoId=…&tipoMedidaId=…` | Última lectura del par |
| GET | `?accion=mapa-demo` | Sensores de `AeriqMapaDemo` con `simulado=true` |
| POST | Sin acción obligatoria | Inserta una medida y devuelve la última lectura |

`medidas` admite `dispositivoId`, `tipoMedidaId`, `desde` y `hasta`. Las fechas
usan `YYYY-MM-DD HH:MM:SS`; los identificadores de dispositivo tienen 16 caracteres.

Cuerpo de ejemplo del POST, correspondiente a valores de prueba PBIO:

```json
{"uuid":"EPSG-GTI-PROY-3A","tipoMedidaId":14,"valor":123,"contador":7,"rssi":-60}
```

El identificador del ejemplo tiene 16 caracteres ASCII y debe existir en la base
para aceptar una escritura. Usa el identificador real registrado en tu entorno.

La API exige los cinco campos, valida rangos y requiere que dispositivo y tipo
existan. Contador y RSSI se reciben pero no se guardan en `Medida`.
La API heredada no exige autenticación de cuenta para insertar; no se ha cambiado
ese contrato en esta reorganización. No ejecutes pruebas de escritura en producción
como comprobación rutinaria.

## Cuenta web: `auth.php?action=…`

`GET session` devuelve `ok`, `csrf` y `user` (o `null`). Las demás operaciones son
POST JSON con cookie de sesión y cabecera `X-CSRF-Token` obtenida en esa consulta.

| Acción | Campos |
| --- | --- |
| `register` | `name`, `last`, `email`, `password` |
| `verify` | `email`, `code` |
| `resend`, `forgot` | `email` |
| `login` | `email`, `password` |
| `reset-password` | `email`, `code`, `password` |
| `profile` | `name`, `last`, `phone` |
| `change-password` | `current`, `password` |
| `logout` | Objeto vacío |

Las contraseñas se validan en PHP entre 12 y 256 bytes. Los códigos son de seis
cifras, caducan a los diez minutos y tienen límite de intentos. `profile` y
`change-password` requieren una cuenta autenticada. Los errores incluyen `error`;
un login pendiente puede incluir `needsVerification`.

## Cuenta móvil: `mobile-auth.php?action=…`

Comparte las acciones de cuenta y sus campos, sin utilizar cookies ni CSRF.
Las peticiones protegidas envían `X-AERIQ-Token`. `GET session` requiere ese token.
Login, verificación y restablecimiento pueden devolver `token` y `user`; cambiar
contraseña también emite un token nuevo. Los tokens se almacenan en MariaDB como
SHA-256 y vencen a los 30 días. Este endpoint exige HTTPS fuera de loopback.

## URLs Android actuales

- Cuentas: `https://jcatsen.upv.edu.es/AERIQ/mobile-auth.php`.
- Escritura PBIO: `https://jcatsen.upv.edu.es/biometria/api.php`.

La web consulta su `api.php` del mismo origen. Confirma la configuración de los
dos despliegues antes de concluir que la web verá las medidas enviadas por Android.
