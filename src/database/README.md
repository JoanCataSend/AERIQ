# Base de datos de AERIQ

Para instalar la base hacen falta dos archivos, y sirven en cualquier ordenador o servidor (Plesk, XAMPP, MariaDB local…):

| Archivo | Qué hace | ¿Obligatorio? |
|---|---|---|
| [1-instalar.sql](1-instalar.sql) | **Borra** todo lo que haya en la base e instala las 29 tablas, las 4 vistas, los catálogos (roles, permisos, magnitudes, servicios…), la organización **AERIQ Gandia** y el admin principal. | Sí |
| [3-actualizar.sql](3-actualizar.sql) | Pone al día una base **con datos** sin borrar nada: rangos y motivo de descarte de las mediciones (HU35) y tablas de avisos (HU36). Solo si la base se instaló antes del 26 de noviembre de 2026. Se puede repetir. | Solo en bases antiguas |
| [2-simulacion.sql](2-simulacion.sql) | Añade datos de prueba: 3 campañas con un perfil de calidad del aire cada una (bueno, regular y malo), 3 AERIQ simulados con 90 lecturas de las últimas 24 h (y 2 descartadas de ejemplo, guardadas como no válidas), 4 cuentas de prueba y 3 solicitudes. No borra nada y se puede repetir. | No |

Los archivos no llevan escrito el nombre de la base: se instalan en la que tengas seleccionada (`mcalgar2_pbio`, `jcatsen_pbio`…).

## Instalar en phpMyAdmin

1. Entra en phpMyAdmin y pulsa en tu base en la columna de la izquierda.
2. Abre la pestaña **Importar**, elige `1-instalar.sql` y pulsa **Importar**.
3. Si quieres datos de prueba, repite el paso 2 con `2-simulacion.sql`.
4. Comprueba que `server/SDBaseDatos.php` del servidor apunta a esa base con su usuario y contraseña. Ese archivo no se sube al repositorio.
5. Si tenías la web abierta, cierra sesión y vuelve a entrar.

## Cuentas

Todas tienen la contraseña `1234`. Cámbiala si la web es pública.

| Correo | Rol | Campaña | Lo crea |
|---|---|---|---|
| `gandia@gmail.com` | Admin principal (Marina) | Todas | `1-instalar.sql` |
| `analista.simulacion@aeriq.example` | Admin de campaña (Marta) | Paseo por el campus | `2-simulacion.sql` |
| `operador.simulacion@aeriq.example` | Operador (Pau) | Paseo por el campus | `2-simulacion.sql` |
| `operadora.simulacion@aeriq.example` | Operadora (Lucía) | Playa de Gandia | `2-simulacion.sql` |
| `operador3.simulacion@aeriq.example` | Operador (Álex) | Recinto municipal (privada) | `2-simulacion.sql` |

Perfiles de los datos simulados (sirven para enseñar el informe y el mapa en los tres escenarios):

| Campaña | Perfil | Qué se ve |
|---|---|---|
| Paseo por el campus | Bueno | Todo por debajo del umbral medio (60 ppb). Sin picos. |
| Playa de Gandia | Regular | Buena de madrugada, regular el resto del día (umbrales propios 55/90). Picos moderados. |
| Recinto municipal (privada) | Malo | Mala por la mañana y por la tarde, con picos por encima de 100 ppb. |

El valor de cada lectura depende de su hora local, así que el veredicto por franjas sale igual a cualquier hora. Para volver a ponerlas en las últimas 24 h, importa otra vez `2-simulacion.sql`.

Cada operador lleva un solo AERIQ. Las campañas de la playa y del recinto aún no tienen admin: el admin principal se lo puede asignar desde **Mi panel**.

## Pasar la base de un ordenador a otro

Hay dos casos:

- **Instalación nueva** (sin datos que conservar): importa `1-instalar.sql` y, si quieres, `2-simulacion.sql` en el otro ordenador.
- **Con los datos reales** (cuentas, campañas y mediciones):
  1. En el ordenador de origen, selecciona la base y abre **Exportar**. Elige el método *Rápido* y el formato *SQL*, y guarda el archivo.
  2. En el destino, crea una base vacía (o selecciona una existente) e importa ese archivo en **Importar**.

  Ese archivo contiene correos y hashes de contraseñas: no lo subas a Git.

## Roles y permisos

| Rol | Código | Permisos |
|---|---|---|
| Admin principal | `admin` | `campanas.gestionar`, `solicitudes.gestionar` |
| Admin de campaña | `admin_campana` | `mapa.organizacion`, `informes.ver`, `equipo.gestionar`, `datos.exportar` |
| Operador | `trabajador` | `mapa.organizacion`, `mediciones.registrar` |
| Visitante | `visitante` | Solo el mapa público |

Cada cuenta, salvo el admin principal, pertenece a una sola campaña (`usuarios.campana_id`). Una campaña está *programada*, *activa* o *finalizada* según sus fechas de inicio y fin. La web lo calcula, así que no hay que tocar nada a mano.

## Cambiar la estructura

Edita `1-instalar.sql`, que es la única definición de la base. Si cambias la estructura de una base que ya tiene datos que quieres conservar, escribe el `ALTER TABLE` a mano y apúntalo en el mensaje del commit.
