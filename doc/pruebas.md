# Pruebas de esta revisión

## Resultado local · 3 de noviembre de 2026

Auditoría correcta de 24 documentos y 14 páginas; 75 recursos originales comprobados por SHA-256. Integración PHP/MariaDB y pruebas JavaScript/DOM correctas. Android: `BUILD SUCCESSFUL`, ocho pruebas JVM sin fallos y APK debug generado. La compilación advierte de dos propiedades obsoletas de osmdroid; no impiden el resultado. La revisión visual sigue pendiente.

## Comandos

```powershell
python scripts/audit-repository.py
node scripts/tests/aeriq-domain.test.js
node scripts/tests/ui-contract.test.js
python scripts/tests/integration-aeriq.py --php C:/xampp/php/php.exe --mysql-bin C:/xampp/mysql/bin
```

La prueba DOM utiliza jsdom instalado solo en `.local-backups/test-tools/`; no añade dependencias de ejecución a la web. Lee [scripts](../scripts/README.md) para prepararla. Google Authentication tiene pruebas propias en [pruebas-google.md](pruebas-google.md).

## Integración aislada

Se crea una MariaDB temporal y una copia del backend con configuración propia. No se lee la configuración privada del alojamiento. SMTP se sustituye por un transporte de prueba que conserva mensajes localmente; no se envía correo real. Los procesos se detienen al terminar y los entornos se conservan en `.local-backups/` para inspección.

Se comprueban: esquema y datos demo, roles/CSRF, invitación/rollback/fecha de caducidad/activación, recuperación de un solo uso, propiedad de Orb, campos y unidades, reintentos, informes por organización, publicación y retirada, asignaciones históricas, bajas y solicitudes. El script de recreación se ejecuta exclusivamente sobre otra base temporal, comprobando copia previa y rechazo sin confirmación; el importador conserva valores 123 ppb y −12 °C y el origen, con GPS nulo.

## Interfaz y dominio

La prueba DOM ejecuta scripts de la web en un documento aislado con API falsa: portada/visor y galería presentes, edición de flota con asignaciones, vistas del panel, informes, campos condicionales, presupuesto sin pago y mapa privado/público. No renderiza píxeles ni acredita responsive visual.

Las pruebas de dominio cubren IDW sin muestras, posiciones repetidas, distancias, mezcla de orígenes/unidades, estadísticas y series temporales. CSV cubre comillas/separadores, texto parecido a fórmula y coordenadas negativas como números.

## Android

```powershell
cd src/android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline --console=plain
```

Se compila el flujo completo y se ejecutan pruebas JVM de iBeacon, interpolación y CSV. El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`. La opción offline requiere dependencias ya descargadas; la sincronización inicial utiliza Internet.

## Aceptación pendiente en entorno real

Probar entrega de invitación/código, teléfono con BLE/GPS autorizado/denegado/aproximado, pérdida de anuncios, Internet desconectado, paso a segundo plano, mapa renderizado y adaptación visual móvil/escritorio. Abrir CSV en una hoja de cálculo. Verificar T22 con el hardware real. Ninguna prueba automática local sustituye esos pasos ni la validación del esquema real de Plesk antes de importar.
