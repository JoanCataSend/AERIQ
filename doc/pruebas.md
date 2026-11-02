# Comprobaciones y aceptación

## Revisión automática local

Desde la raíz:

```powershell
python scripts/audit-repository.py
```

Comprueba la estructura documentada, enlaces Markdown vigentes, referencias
HTML locales, orden de carga de clientes JavaScript, JSON de Composer y
que los archivos privados estén ignorados por Git. Ejecuta `node --check`
y `php -l` si encuentra esos intérpretes. Si falta uno, lo indica como omitido.
Los textos históricos quedan fuera de la comprobación de enlaces porque
conservan rutas de entregas antiguas.

Estas comprobaciones detectan errores de organización y sintaxis; no prueban
sesiones, correo, SQL ni comportamiento visual.

## Android

```powershell
cd src/android
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

La última orden necesita un dispositivo o emulador. El parser dispone de pruebas
para ozono, temperatura negativa y rechazo de identificador o trama inválidos.
Las pruebas de ejemplo generadas por Android Studio no cubren los flujos de cuenta.

## Matriz manual

| Área | Acción | Resultado esperado |
| --- | --- | --- |
| Web | Abrir las once páginas en escritorio y móvil | Navegación sin recursos locales perdidos |
| Producto | Cambiar vista y cantidad | Imagen y cantidad coherentes |
| Carrito | Añadir, restar y vaciar | Totales correctos; vacío sin checkout |
| Compra | Confirmar formulario de prueba | Mensaje de demostración, sin cobro ni pedido |
| API | Consultar `accion=health` y `accion=medidas` | JSON válido desde la base configurada |
| Mapa web | Consultar los sensores y cambiar capas | Puntos simulados de MariaDB y cartografía OSM |
| Cuenta web | Registrar y confirmar código con correo de prueba | Cuenta verificada y sesión establecida |
| Cuenta web | Perfil, cambio de contraseña y logout | Persistencia y sesión coherentes |
| Recuperación | Solicitar código y restablecer | Código válido una vez; acceso con contraseña nueva |
| Android | Acceder y restaurar sesión | Token móvil válido sin almacenar contraseña |
| BLE | Seleccionar emisor PBIO | Lecturas con tipos/unidades correctos |
| Compartir | Desactivado y después activado | Sin envíos antes; POST de nuevas lecturas después |
| Ubicación | Permitir, denegar y dar aproximada | Mapa disponible y posición solo si se autoriza |
| Ciclo Android | Salir de mapa, poner app en segundo plano y volver | Revisar liberación de recursos y reanudación |
| Extremo a extremo | Emitir valor de prueba y compartir voluntariamente | Mismo valor en Android y consulta web, si comparten base |

Usa una cuenta y un entorno de prueba para operaciones que escriben o envían correo.
Registra fecha, versión, entorno y resultado; no adjuntes contraseñas ni tokens.

## Validación de esta reorganización

Se comprueba la integridad de los archivos de implementación contra sus hashes
anteriores al traslado, además de la auditoría y el paquete generado. No se da por
superada una compilación Android ni una integración PHP/MariaDB sin ejecutarlas.

Resultado del 2 de noviembre de 2026: los 171 archivos originales respaldados
conservaron su contenido tras el traslado; la auditoría de 20 documentos y once
páginas pasó, incluida la revisión de sintaxis JavaScript con Node. El paquete
generado contiene 55 archivos verificados contra el origen, sin configuraciones
privadas. PHP no está instalado en este entorno y no se ejecutaron la compilación
Android ni las pruebas que requieren servidor, correo o dispositivo.
