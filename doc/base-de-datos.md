# Persistencia MariaDB

## Qué se conoce

La siguiente tabla enumera campos que utiliza el código PHP. No es un esquema
DDL: tipos, índices, relaciones y valores por defecto deben contrastarse con
una exportación real de la base de Plesk.

| Tabla | Campos observados | Uso |
| --- | --- | --- |
| `Dispositivo` | `dispositivoId`, `nombre` | Emisores conocidos |
| `TipoMedida` | `tipoMedidaId`, `nombre`, `unidad` | Magnitudes y unidades |
| `Medida` | `dispositivoId`, `tipoMedidaId`, `valor`, `fechaHora` | Lecturas persistidas |
| `AeriqUsuario` | `id`, `nombre`, `apellidos`, `email`, `telefono`, `password_hash`, `verificado_en` | Cuentas web y móviles |
| `AeriqCodigoEmail` | `usuario_id`, `proposito`, `codigo_hash`, `vence_en`, `enviado_en`, `intentos` | Verificación y recuperación |
| `AeriqAuthRate` | `clave_hash`, `ventana_en`, `intentos` | Limitación de intentos |
| `AeriqSesionMovil` | `token_hash`, `usuario_id`, `vence_en` | Sesiones Android |
| `AeriqMapaDemo` | `id`, `nombre`, `latitud`, `longitud`, `valor_ug_m3`, `nivel`, `simulado`, `actualizado_en` | Puntos simulados web |

Las inserciones de cuentas y medidas omiten algunos campos, por lo que dependen
de los valores por defecto y claves definidos en el servidor. Los `ON DUPLICATE
KEY UPDATE` de códigos e intentos dependen de índices que no pueden reconstruirse
con certeza solo mirando las consultas.

## Material pendiente

El ZIP no contiene SQL. Para una instalación reproducible falta incorporar un
esquema revisado y sus migraciones en `src/database/`. Una exportación privada
completa puede contener cuentas y sesiones; no debe añadirse al repositorio.
Consulta [el procedimiento](../src/database/README.md).

## Conexión

`src/web/server/Logica.php` carga `SDBaseDatos.php`, abre una conexión MySQLi y
configura `utf8mb4`. Los ejemplos incluyen las claves `host`, `user`, `password`,
`database` y `port`. El archivo real está ignorado por Git y no se incluye en
el paquete generado por defecto.

## Límites de los datos

No hay una relación de propiedad de sensores por usuario implementada en las
consultas observadas. La tabla visual de «mis sensores» no acredita esa relación.
Los puntos de `AeriqMapaDemo` son simulados y no deben confundirse con `Medida`.
No se ha exportado ni modificado la base remota durante esta reorganización.
