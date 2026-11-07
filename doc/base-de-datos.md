# Modelo de datos

## Once tablas, una sola fuente de verdad

| Tabla | Qué guarda |
| --- | --- |
| `organizaciones` | Nombre y creación de cada entidad |
| `usuarios` | Identidad, organización, rol, contraseña cifrada, UID de Firebase, activación e invitación |
| `campanas` | Zona, fechas, publicación, umbrales de proyecto y radio de interpolación |
| `dispositivos` | Orb, organización, dirección BLE, alta y clasificación de procedencia |
| `asignaciones` | Historial de relación Orb–trabajador–campaña |
| `magnitudes` | Ozono/temperatura, unidades y límites de formato |
| `mediciones` | Evento único, asignación, magnitud, valor, fecha UTC y GPS opcional |
| `solicitudes` | Datos y estado de cada petición comercial |
| `codigos_email` | Hash de códigos de verificación/recuperación de un solo uso |
| `sesiones_moviles` | Hash del token y caducidad, sin contraseña en texto |
| `limites_acceso` | Contadores de intentos para proteger el acceso |

`vista_mediciones` y `vista_flota` facilitan la lectura con nombres en phpMyAdmin. No son copias de datos ni endpoints públicos. Las API aplican permisos antes de consultar o devolver registros.

## Relaciones

Una organización tiene usuarios, campañas y dispositivos. Cada asignación vincula esas tres entidades dentro de la misma organización mediante claves foráneas compuestas. Solo puede existir una asignación abierta por Orb. Al reasignar, se cierra la anterior y se abre otra; las mediciones conservan su asignación histórica.

Cada medición referencia la magnitud que define su unidad. El evento UUID es único, incluso si se reintenta el envío. Una lectura nueva debe corresponder al trabajador, MAC, asignación y campaña autorizados y a sus fechas. El servidor valida rangos y unidad; las restricciones SQL respaldan la integridad.

El GPS se guarda completo o no se guarda: latitud, longitud y precisión no admiten estados parciales. Datos importados sin contador/RSSI mantienen esos campos nulos. No se inventan coordenadas ni batería.

## Acceso y privacidad

La cuenta integra rol, organización y activación, sin una tabla adicional de permisos redundante. Una invitación crea una cuenta no operativa y una contraseña temporal cuyo hash se guarda; vence a las 24 h por defecto. Sustituirla o recuperar el acceso por correo activa la cuenta. Las organizaciones no pueden asignar trabajadores ni campañas de otra entidad.

`usuarios.firebase_uid` guarda el identificador estable de Firebase cuando el usuario entra con Google. Es único y nulo para las cuentas que solo usan contraseña. En el primer acceso, el correo verificado por Google debe coincidir con una cuenta ya creada por el administrador; después se usa el UID, no el correo mostrado. La migración [anadir-firebase-google.sql](../src/database/anadir-firebase-google.sql) añade la columna y el índice sin borrar datos.

La procedencia real/simulada se toma del Orb al recibir la lectura y queda conservada. Publicar en el mapa es una autorización de campaña distinta de registrar en servidor. Los informes solo incluyen lecturas válidas del ámbito autorizado.

## Crear, recrear o migrar

Utiliza los procedimientos del [README de datos](../src/database/README.md). El esquema se crea desde cero con `schema.sql`; `reset-database.py` es explícitamente destructivo, con copia previa y confirmación del nombre. `migrate-legacy.php` trabaja sobre otra base vacía y conserva el origen. El SQL antiguo se archiva en `doc/historico/database/` y ya no es un requisito del sistema.

Para evolucionar el producto, añade migraciones versionadas al esquema nuevo. `magnitudes` permite extender tipos de medida; la recepción BLE necesitará también el contrato de firmware correspondiente. No se ha validado físicamente batería ni calibración ambiental.
