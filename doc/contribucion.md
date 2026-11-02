# Trabajar en AERIQ

## Dónde hacer cada cambio

- Pantallas web y estilos: `src/web/`.
- Comunicación web: `auth-client.js` o `pbio-client.js`.
- Contrato HTTP: `api.php`, `auth.php` o `mobile-auth.php`.
- Validación/persistencia/correo: `src/web/server/`.
- Pantallas móviles: `src/android/app/src/main/java/com/aeriq/app/ui/`.
- BLE y clientes móviles: `ble/` y `data/`, bajo el mismo paquete.
- SQL revisado cuando esté disponible: `src/database/`.
- Explicaciones y procedimientos: `doc/`.

## Flujo de una modificación

1. Lee el README del componente y su documento en `doc/`.
2. Cambia la implementación de esa responsabilidad.
3. Si afecta a un contrato, actualiza `doc/api.md` y los clientes implicados.
4. Si afecta a esquema, documenta la migración y su compatibilidad antes de aplicarla.
5. Ejecuta la auditoría y las pruebas relevantes de `doc/pruebas.md`.
6. Describe qué cambió, cómo se comprobó y qué no se pudo comprobar.

## Archivos locales

No añadas configuraciones privadas, cachés Android, `.dist/`, APK ni copias de
seguridad a Git. El wrapper Gradle y los recursos originales sí forman parte
del proyecto. La carpeta `vendor/` se rellena con Composer cuando se usa SMTP.

No mantengas una segunda copia del código dentro de la documentación. Las notas
históricas conservan el contexto de entregas pasadas; las instrucciones vigentes
se editan en los documentos actuales y sus README.
