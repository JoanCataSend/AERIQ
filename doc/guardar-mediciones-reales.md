# Pasar de ejemplos a captura real

## Qué cambia

Al seleccionar un sensor real asignado a tu cuenta, Android activa el guardado.
No es necesario activar manualmente el interruptor. Se puede pausar la captura
desde Mi sensor/Configuración; las pendientes ya capturadas se siguen enviando.
Sólo se guardan lecturas del sensor seleccionado y de su asignación autorizada.
Las tramas de diagnóstico BLE no se publican como mediciones.

Flujo: **sensor → BLE → Android → HTTPS field.php → MariaDB → web/informes**.
La app nunca utiliza usuario/contraseña de MariaDB directamente.

Cada lectura se persiste primero en SQLite privado con UUID, cuenta, sensor,
asignación, valor, unidad, fecha UTC, contador BLE, RSSI y GPS si está autorizado
y es reciente. La captura no espera a las peticiones HTTP. El servidor confirma
el mismo UUID; sólo entonces se retira de la cola. Una respuesta perdida puede
reenviarse sin crear duplicados. La cola sobrevive a reinicios de la app y se
separa por cuenta; no se transfiere a otro usuario al cerrar/iniciar sesión.

Reintentos al recibir nuevas lecturas y cada 30 segundos con la app en primer
plano, hasta 20 lecturas por lote. Errores de sesión/conectividad quedan pendientes;
403/409/422 quedan retenidos para revisión sin atascar las siguientes. El botón
**Reintentar pendientes** vuelve a intentarlos después de corregir el problema.
No cambia UUID, asignación ni fecha para forzar la aceptación. El servidor admite
capturas de hasta 30 días y exige que la asignación siga autorizada: una cola
antigua o un sensor eliminado puede requerir revisión, no se pierde en silencio.

La captura BLE se detiene cuando la app pasa a segundo plano; no se ha añadido
un servicio permanente. Al volver, se reanuda el escaneo si había sensor seleccionado.
Las lecturas ya persistidas permanecen en el móvil hasta sincronizarlas. No
desinstalar ni borrar datos de la app si hay pendientes.

## Limpiar la base existente en Plesk

No usar los scripts `recrear-*` ni los seeds: borrarían cuentas o volverían a
introducir ejemplos. La limpieza nueva **no reconstruye el esquema**.

1. Detener captura y altas durante la limpieza y exportar una copia SQL desde
   phpMyAdmin. Seleccionar la base de AERIQ, no `information_schema`.
2. Ejecutar `src/database/previsualizar-limpieza-sensores.sql`: sólo cuenta filas.
3. Importar `src/database/limpiar-simulados-y-sensores.sql`.
4. Verificar: `dispositivos`, `asignaciones` y `mediciones` quedan vacías; cuentas,
   organizaciones, campañas, sesiones y solicitudes permanecen.

La limpieza borra las lecturas simuladas y retira **todos** los sensores y sus
asignaciones. Si había lecturas reales, antes copia sus filas y sus referencias a
`archivo_sensores_mediciones`, `archivo_sensores_asignaciones` y
`archivo_sensores_dispositivos`. Esas tablas son un archivo, no la lista de sensores
activos ni la fuente de los informes actuales. Nunca se eliminan datos reales sin
conservar esta copia. Las campañas se mantienen, aunque queden sin lecturas.
No se reinician IDs ni se desactivan claves foráneas. El script se ha probado dos
veces consecutivas: no duplica el archivo ni modifica cuentas/organizaciones.

Después, volver a detectar/asignar el sensor desde Android. Esto genera una nueva
asignación; las pendientes antiguas de un sensor retirado no se reasignan a ella.
El nuevo alta utiliza `simulado=0`. Tanto el esquema como los formularios de alta
y la API dejan los sensores nuevos en modo real por defecto.

## Archivos de entrega

- Instalar el APK nuevo actualizando la app existente (misma firma).
- Extraer `actualizar-plesk.zip` en `httpdocs/src/web/` del alojamiento,
  conservando la estructura `server/`. No contiene credenciales ni SQL público.
- Importar los SQL aparte desde phpMyAdmin. **No subirlos a la carpeta pública.**
- La URL confirmada es `https://ldiamur.upv.edu.es/src/web/`.
  `field.php` devuelve JSON 401 sin token: confirma que existe, no es un error
  de configuración cuando se abre a mano sin iniciar sesión.

## Comprobarlo

1. Iniciar sesión en Android, asignar/seleccionar Orb y mantener la app visible.
2. En **Mi sensor → Servidor**, comprobar `lecturas confirmadas`, `pendientes` y
   `requieren revisión`. «Guardada en el móvil» todavía no confirma MariaDB.
3. Sin Internet, las lecturas quedan pendientes. Reconectar y comprobar confirmación.
4. Abrir el panel web con la misma organización: flota con última recepción y,
   en informes, elegir la campaña del sensor y las fechas UTC actuales.
5. Sin GPS las lecturas se guardan y salen en informes/historial, pero no en el
   mapa. Con GPS se ven en **Mi organización → Mediciones reales**. Una campaña
   privada sigue excluida del mapa público; publicar requiere configuración de
   su organización. No se cambia ese permiso automáticamente.

Consulta de comprobación en phpMyAdmin, sin modificar datos:

```sql
SELECT COUNT(*) AS reales, MAX(recibida_en) AS ultima_recepcion
FROM mediciones WHERE simulado=0;
SELECT * FROM vista_mediciones WHERE origen='Real' ORDER BY fecha_utc DESC LIMIT 20;
```

## Pruebas

- Gradle: tests unitarios y compilación del APK.
- Test instrumentado SQLite: persistencia tras reapertura, aislamiento de cuentas,
  bloqueo/reintento y retirada únicamente al confirmar la cuenta/evento correctos.
- Integración PHP/MariaDB temporal: limpieza repetida, conservación de cuentas y
  archivo real; asignación, inserción real, reenvío idempotente, historial e informe;
  mapa privado con GPS y exclusión del mapa público.

Estas pruebas no han borrado la base de Plesk ni han insertado lecturas de prueba
en producción. La recepción física completa se verifica con tu sensor y móvil.
