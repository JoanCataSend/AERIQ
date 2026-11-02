# AERIQ · Sprint 1

Proyecto universitario de Aplicaciones de Biometría y Medio Ambiente: una
aplicación Android recibe anuncios Bluetooth de un nodo PBIO y puede enviar sus
lecturas a un servidor PHP/MariaDB. La web permite explorar un mapa, consultar
mediciones almacenadas y gestionar una cuenta compartida con Android.

La web procede de la copia de Plesk facilitada en `tal.zip`. El repositorio
organiza esa implementación y la aplicación móvil para que cada parte tenga una
entrada clara, documentación y pasos de comprobación.

## Por dónde empezar

| Necesito… | Ir a… |
| --- | --- |
| Entender cómo se conecta todo | [Arquitectura](doc/arquitectura.md) |
| Abrir la app en Android Studio | [Android](src/android/README.md) |
| Trabajar en las pantallas web | [Web](src/web/README.md) |
| Entender las API y el servidor | [Backend](src/web/server/README.md) y [contratos](doc/api.md) |
| Preparar la base de datos | [Base de datos](src/database/README.md) |
| Publicar una carpeta compatible con Plesk | [Despliegue](doc/despliegue.md) |
| Revisar qué funciona y qué falta | [Estado del proyecto](doc/estado.md) |
| Comprobar una entrega | [Pruebas](doc/pruebas.md) |

## Estructura

```text
AERIQ/
├── README.md                 # Presentación y puntos de entrada
├── .gitignore                # Secretos, cachés y resultados generados
├── src/
│   ├── README.md
│   ├── android/              # Proyecto Android Studio: Kotlin y Compose
│   │   ├── README.md
│   │   ├── app/src/          # Aplicación, recursos y pruebas
│   │   ├── design_assets/   # SVG de referencia visual
│   │   └── gradle/          # Wrapper y catálogo de versiones
│   ├── web/                  # Raíz web desplegable en /AERIQ/
│   │   ├── README.md
│   │   ├── *.html           # Pantallas web
│   │   ├── app.js           # Interacciones de la interfaz
│   │   ├── styles.css       # Estilos compartidos
│   │   ├── *-client.js      # Clientes de autenticación y medidas
│   │   ├── *.php            # Entradas HTTP de las API
│   │   ├── server/          # Lógica PHP y configuración privada
│   │   ├── assets/          # Imágenes, iconos y modelo 3D
│   │   └── vendor/          # Dependencias de Composer cuando se instalen
│   └── database/             # Requisitos del esquema y restauración
├── doc/
│   ├── README.md            # Índice de documentación
│   ├── arquitectura.md
│   ├── web.md
│   ├── android.md
│   ├── api.md
│   ├── base-de-datos.md
│   ├── despliegue.md
│   ├── pruebas.md
│   ├── estado.md
│   ├── contribucion.md
│   └── historico/           # Notas de entregas anteriores
└── scripts/
    ├── README.md
    ├── audit-repository.py   # Enlaces, estructura y sintaxis disponible
    └── build-plesk-package.py # Genera .dist/plesk/AERIQ/
```

## Arranque rápido

Desde la raíz, con Python 3.9 o superior:

```powershell
python scripts/audit-repository.py
python scripts/build-plesk-package.py
```

Para Android, abre `src/android/` en Android Studio y sincroniza Gradle. Para
el sitio completo necesitas PHP 8.1+, MariaDB y configuración de correo;
[la guía](doc/despliegue.md) explica los requisitos. Abrir un HTML o usar un
servidor estático no ejecuta las API PHP.

## Alcance actual

Hay código para cuentas reales con verificación por correo, sesión web, tokens
móviles y consulta de medidas. Los sensores coloreados del mapa son simulados;
las lecturas BLE del firmware de referencia también pueden ser valores de prueba.
Cartografía real no significa medición ambiental validada.

Pagos, OAuth de Google/Apple, avisos, recorridos y varias funciones comerciales
siguen pendientes. El ZIP no incluía el volcado SQL ni las dependencias SMTP
instaladas. Consulta [el estado detallado](doc/estado.md).

## Relación con Sprint 0

La organización `src/`, `doc/` y `scripts/` toma como referencia el
[repositorio PBIO de Sprint 0](https://github.com/JoanCataSend/PBIO-O3-GTI3A-2026).
Aquí se conserva la estructura interna desplegable de la web para mantener sus
rutas relativas. El firmware no está incluido en esta entrega; su contrato BLE
se describe en [Android](doc/android.md).

Las antiguas carpetas `AERIQ_Android/` y `AERIQ_Web/` están ahora en
`src/android/` y `src/web/`. Las notas anteriores se conservan en
[el histórico](doc/historico/README.md). La reorganización no publica cambios
en Plesk ni modifica los endpoints configurados en la app.
