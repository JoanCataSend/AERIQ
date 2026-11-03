# AERIQ — Historias de usuario y tareas de desarrollo

## 1. Contexto general del proyecto

**Empresa:** Código Norte

**Proyecto/producto:** AERIQ

**Dispositivo físico:** Orb

**Objetivo:** desarrollar una plataforma de monitorización ambiental mediante sensores móviles que registran mediciones de ozono (O₃).

Código Norte es la empresa que desarrolla y comercializa AERIQ. El proyecto contempla tres líneas comerciales:

1. Venta de kits de sensores para administraciones públicas, especialmente ayuntamientos.
2. Venta de kits de sensores para empresas privadas.
3. Estudios ambientales realizados por Código Norte, utilizando sus propios sensores, sin que el cliente necesite adquirirlos.

Los ciudadanos pueden consultar gratuitamente la información ambiental pública. Los trabajadores portadores de los sensores utilizan principalmente la aplicación Android, mientras que los administradores y técnicos gestionan usuarios, dispositivos y campañas desde la plataforma web.

**Arquitectura técnica:** sensor Orb → Bluetooth Low Energy (BLE) → aplicación Android → API del servidor → base de datos → plataforma web y mapas.

### Prioridades

- **P0:** funcionalidades esenciales para el flujo técnico y operativo.
- **P1:** funcionalidades complementarias de gestión, informes, mapas y contratación.
- **P2:** mejoras de experiencia y automatizaciones futuras.

Las historias describen funcionalidades previstas y no implican que ya estén desarrolladas.

---

# PARTE I — HISTORIAS DE USUARIO

## HU01 – Registro de trabajadores [P0]

**Historia de usuario**

Como administrador de una organización, quiero registrar a los trabajadores que van a utilizar los sensores Orb, para que puedan acceder a la aplicación Android con una cuenta autorizada.

**Criterios de aceptación**

1. El administrador puede registrar trabajadores desde el panel web de la organización.
2. Al crear una cuenta, el sistema envía un correo electrónico al trabajador con las instrucciones de acceso y una contraseña temporal.
3. Cada trabajador queda vinculado a su organización y dispone únicamente de los permisos correspondientes a su función.

## HU02 – Inicio de sesión y cambio de contraseña [P0]

**Historia de usuario**

Como trabajador registrado, quiero iniciar sesión en la aplicación Android con las credenciales que me ha proporcionado mi organización, para poder utilizar el sensor que tengo asignado.

**Criterios de aceptación**

1. El trabajador puede iniciar sesión mediante su correo electrónico y contraseña.
2. En el primer inicio de sesión debe cambiar obligatoriamente la contraseña temporal por una nueva.
3. Una vez cambiada, puede acceder normalmente sin tener que repetir el proceso.
4. El usuario puede cerrar sesión y recuperar el acceso si olvida su contraseña.

## HU03 – Consulta del mapa público [P1]

**Historia de usuario**

Como ciudadano, quiero consultar un mapa interactivo con las mediciones de ozono disponibles, para conocer la calidad ambiental de las zonas que me interesan.

**Criterios de aceptación**

1. Cualquier persona puede consultar el mapa público sin necesidad de registrarse.
2. El mapa permite desplazarse, ampliar, reducir y consultar diferentes ubicaciones.
3. Las zonas medidas se representan mediante colores verde, amarillo/naranja y rojo, según el nivel de ozono registrado.
4. Al seleccionar una medición, se muestra su valor, unidad, ubicación y fecha, diferenciando los datos reales de los simulados.
5. Solo aparecen las mediciones cuya publicación esté autorizada.

## HU04 – Información del proyecto AERIQ [P1]

**Historia de usuario**

Como visitante de la página web, quiero consultar información sobre el proyecto AERIQ y su funcionamiento, para comprender qué mide, cómo se obtienen los datos y qué servicios ofrece Código Norte.

**Criterios de aceptación**

1. La página principal explica el objetivo del proyecto AERIQ y el funcionamiento general del sensor Orb.
2. Se informa de los servicios de venta de kits y estudios ambientales ofrecidos por Código Norte.
3. El visitante puede acceder al mapa público desde la página principal.
4. Se explican las limitaciones de las mediciones y se aclara que no sustituyen a los sistemas oficiales de vigilancia ambiental.

## HU05 – Recepción Bluetooth y envío de mediciones [P0]

**Historia de usuario**

Como trabajador portador de un Orb, quiero que la aplicación Android reciba las mediciones del dispositivo mediante Bluetooth y las envíe al servidor, para registrar automáticamente los datos de la campaña en la que participo.

**Criterios de aceptación**

1. La aplicación identifica el Orb asignado y recibe las mediciones que transmite mediante BLE.
2. Cada medición se asocia al dispositivo, la fecha, la hora y la campaña correspondiente.
3. Cuando el usuario autoriza la ubicación, se incorporan las coordenadas GPS del móvil.
4. Las mediciones recibidas se envían al servidor para almacenarse en la base de datos.
5. El sistema evita almacenar mediciones duplicadas y permite identificar los errores de envío.

## HU06 – Estado de batería del Orb [P1]

**Historia de usuario**

Como trabajador portador de un Orb, quiero conocer el nivel de batería del dispositivo, para saber cuándo necesita cargarse y evitar que deje de registrar mediciones durante la jornada.

**Criterios de aceptación**

1. El dispositivo dispone de tres indicadores LED para representar su nivel de batería: bajo, medio y alto.
2. Los indicadores permiten consultar el nivel de batería sin necesidad de utilizar la aplicación.
3. Si el firmware transmite el estado de la batería, la aplicación también muestra dicha información.

## HU09 – Informes de mediciones ambientales [P1]

**Historia de usuario**

Como responsable de una organización, quiero generar informes de las mediciones obtenidas durante una campaña, para analizar la evolución del ozono y evaluar la calidad ambiental de las zonas estudiadas.

**Criterios de aceptación**

1. El usuario puede seleccionar una campaña, una zona y un intervalo de fechas.
2. El informe incluye los valores de ozono registrados, su fecha, su hora y su localización.
3. Se muestran estadísticas como el valor mínimo, máximo y medio de las mediciones válidas.
4. Se incluyen tablas y gráficas que permiten visualizar la evolución de los niveles de ozono por días o semanas.
5. El informe clasifica los resultados según los niveles ambientales definidos para el proyecto.
6. Se identifican los momentos en los que las mediciones superaron los umbrales establecidos, indicando el valor y la fecha del suceso.

## HU10 – Gestión de dispositivos de una organización [P0]

**Historia de usuario**

Como administrador de una organización, quiero gestionar los dispositivos Orb asignados a mi entidad, para controlar su distribución, actividad y utilización en las campañas de medición.

**Criterios de aceptación**

1. El administrador puede consultar todos los dispositivos asignados a su organización.
2. Puede dar de alta, dar de baja administrativamente y modificar el nombre de los dispositivos.
3. Puede asignar dispositivos a trabajadores y campañas de medición.
4. Puede consultar el estado de actividad y la fecha de la última medición recibida.
5. Únicamente puede gestionar los dispositivos sobre los que su organización tenga autorización.

## HU15 – Exportación de mediciones [P1]

**Historia de usuario**

Como administrador o analista de una organización, quiero exportar las mediciones de mis campañas, para poder analizarlas utilizando herramientas externas.

**Criterios de aceptación**

1. El usuario puede seleccionar los dispositivos, zonas y periodos de los que desea obtener información.
2. El sistema permite descargar los registros en formato CSV.
3. Los archivos incluyen fecha, hora, valor, unidad, dispositivo y coordenadas cuando su consulta esté autorizada.
4. Únicamente se pueden exportar los datos de las campañas a las que el usuario tenga acceso.

## HU18 – Solicitud de kits para administraciones públicas [P1]

**Historia de usuario**

Como responsable de una administración pública, quiero solicitar información y un presupuesto para adquirir kits de sensores AERIQ, para organizar estudios ambientales en las zonas de mi municipio o territorio.

**Criterios de aceptación**

1. El formulario permite identificar la administración y proporcionar los datos de contacto.
2. Se puede indicar la cantidad aproximada de sensores necesarios y la finalidad del proyecto.
3. La solicitud se guarda en la base de datos.
4. El solicitante recibe una confirmación de que su petición se ha registrado correctamente.

## HU19 – Solicitud de kits para empresas privadas [P1]

**Historia de usuario**

Como responsable de una empresa, quiero solicitar información y un presupuesto para adquirir kits de sensores AERIQ, para realizar estudios ambientales en mis instalaciones o zonas de actividad.

**Criterios de aceptación**

1. El formulario permite introducir los datos de la empresa y de la persona de contacto.
2. Se puede indicar la cantidad aproximada de sensores y las instalaciones o zonas que se desean estudiar.
3. La solicitud se almacena en la base de datos.
4. El solicitante recibe una confirmación de que su petición ha sido registrada.

## HU20 – Solicitud de estudios ambientales [P1]

**Historia de usuario**

Como cliente interesado, quiero solicitar un estudio ambiental realizado por Código Norte utilizando AERIQ, para obtener información sobre la calidad del aire de una zona sin necesidad de comprar ni gestionar los sensores.

**Criterios de aceptación**

1. El formulario permite indicar la zona que se desea estudiar y su superficie aproximada.
2. Se puede especificar la duración, las necesidades de medición y los resultados o informes solicitados.
3. Se recogen los datos de contacto necesarios para preparar el presupuesto.
4. La solicitud se almacena en la base de datos.
5. El sistema confirma la recepción e informa de que el presupuesto definitivo se realizará tras revisar las necesidades del estudio.

## HU28 – Representación e interpolación de mediciones [P1]

**Historia de usuario**

Como usuario del mapa, quiero visualizar una representación de las zonas medidas a partir de los datos geolocalizados, para identificar las áreas con diferentes niveles de ozono sin confundirlas con zonas donde no existen mediciones.

**Criterios de aceptación**

1. Las mediciones se representan sobre un mapa real utilizando sus coordenadas geográficas.
2. El sistema utiliza interpolación espacial para representar los niveles de ozono entre mediciones cercanas.
3. La distancia máxima de interpolación es configurable, tomando inicialmente como referencia un rango de 20 a 50 metros.
4. Cuando no existen suficientes mediciones próximas, la zona aparece sin datos y no se le asigna un nivel de contaminación.
5. La representación diferencia los datos reales de los simulados.

## HU29 – Verificación del funcionamiento del dispositivo [P0]

**Historia de usuario**

Como trabajador portador de un Orb, quiero comprobar desde la aplicación Android si el dispositivo está transmitiendo mediciones y si estas se están enviando correctamente al servidor, para saber si el sistema funciona correctamente durante mi jornada.

**Criterios de aceptación**

1. La aplicación muestra si está recibiendo anuncios Bluetooth del Orb asignado.
2. Se muestra de forma independiente el estado del envío de datos al servidor.
3. La aplicación distingue entre un dispositivo del que no se reciben mediciones y un dispositivo que transmite correctamente, pero cuyos datos no pueden enviarse por falta de conexión a Internet.
4. El trabajador recibe una indicación clara cuando se produce un error de recepción o envío.

## HU30 – Seguimiento de solicitudes de contratación [P1]

**Historia de usuario**

Como responsable comercial de Código Norte, quiero consultar y gestionar las solicitudes de kits y estudios recibidas desde la página web, para realizar su seguimiento y responder a los clientes interesados.

**Criterios de aceptación**

1. Todas las solicitudes enviadas desde los formularios se almacenan en la base de datos.
2. El personal autorizado de Código Norte puede consultar las solicitudes desde un panel interno.
3. Cada solicitud muestra el cliente, los datos de contacto, el servicio solicitado y la fecha de recepción.
4. Se puede modificar su estado, por ejemplo: pendiente, en revisión, respondida o cerrada.
5. El sistema puede notificar por correo electrónico la llegada de nuevas solicitudes, sin depender del correo como único registro.

---

# PARTE II — TAREAS DEL SPRINT BACKLOG

Las siguientes tareas están registradas en Trello, desde T13 hasta T30. Cada tarea incluye las horas aproximadas, prioridad, relación con las historias de usuario, descripción y subtareas.

Todas deben validarse funcionalmente antes de considerarse terminadas.

## T13 – Adaptar la base de datos para organizaciones, campañas y sensores (6 h)

**Prioridad:** P0

**Historias relacionadas:** HU01, HU05, HU10.

**Descripción:**

Definir y preparar las tablas y relaciones necesarias para que cada organización gestione sus trabajadores, campañas y dispositivos con datos separados. Adaptar la base de datos existente sin perder las mediciones de prueba.

**Subtareas:**

- [ ] Revisar esquema y datos de la BBDD actual.
- [ ] Crear entidades organización, campaña y asignaciones de Orb.
- [ ] Definir claves y separación de permisos por organización.
- [ ] Ejecutar migración y comprobar que se conservan mediciones previas.

## T14 – Diseñar e implementar el alta web de trabajadores (8 h)

**Prioridad:** P0

**Historia relacionada:** HU01.

**Descripción:**

Crear el formulario web para que un administrador dé de alta a los operadores y la API que vincula cada cuenta con su organización y rol. Solo el administrador autorizado puede realizar altas.

**Subtareas:**

- [ ] Definir campos y permisos de alta de trabajadores.
- [ ] Implementar formulario responsive en panel web.
- [ ] Crear endpoint de alta con validaciones.
- [ ] Probar alta autorizada, duplicados y errores.

## T15 – Configurar el correo de invitación y la contraseña temporal (5 h)

**Prioridad:** P0

**Historias relacionadas:** HU01, HU02.

**Descripción:**

Tras el alta de un trabajador, enviar el correo de acceso con credencial temporal y forzar su sustitución. Probar caducidad, envío fallido y ausencia de acceso antes de activar la cuenta.

**Subtareas:**

- [ ] Diseñar correo de invitación y generación segura de acceso temporal.
- [ ] Configurar servicio de envío de correo.
- [ ] Forzar cambio de contraseña en primer acceso.

**Comprobación adicional incluida en la descripción:** probar la entrega, la caducidad y los fallos de correo.

## T16 – Implementar el login Android y el cambio inicial de contraseña (8 h)

**Prioridad:** P0

**Historia relacionada:** HU02.

**Descripción:**

Adaptar el acceso a la app Android para los trabajadores dados de alta por su organización. Exigir cambio de contraseña en el primer acceso, controlar sesión y ofrecer recuperación de acceso.

**Subtareas:**

- [ ] Conectar pantalla de login Android con backend.
- [ ] Implementar pantalla y API de cambio de contraseña inicial.
- [ ] Gestionar sesión, cierre y recuperación de acceso.

**Comprobación adicional:** probar credenciales incorrectas y permisos de usuario.

## T17 – Integrar la recepción BLE del sensor Orb en Android (6 h)

**Prioridad:** P0

**Historia relacionada:** HU05.

**Descripción:**

Detectar los anuncios BLE del Orb y extraer la medición e identificador. Evitar hablar de una conexión permanente si el dispositivo se comunica mediante publicidad iBeacon.

**Subtareas:**

- [ ] Verificar publicidad BLE e identificadores del Orb.
- [ ] Implementar recepción BLE con permisos Android.
- [ ] Interpretar y validar paquete de medición.
- [ ] Probar recepción y pérdida de anuncios.

## T18 – Asociar ubicación, fecha y campaña y enviar las mediciones (7 h)

**Prioridad:** P0

**Historia relacionada:** HU05.

**Descripción:**

Relacionar cada lectura recibida por la app con operador, Orb, campaña, fecha/hora y coordenadas cuando haya permiso. Enviar la medición a la API y gestionar errores de red.

**Subtareas:**

- [ ] Vincular lectura con Orb, operador y campaña.
- [ ] Solicitar permisos de ubicación y guardar coordenadas si están autorizadas.
- [ ] Enviar lecturas a la API con fecha y unidad.
- [ ] Gestionar errores y confirmar envío correcto.

## T19 – Validar y guardar lecturas en la API sin duplicados (7 h)

**Prioridad:** P0

**Historia relacionada:** HU05.

**Descripción:**

Completar el endpoint del servidor que registra mediciones verificando autorización, formato, unidad, campaña y duplicaciones. Conservar trazabilidad y respuesta de error comprensible.

**Subtareas:**

- [ ] Comprobar campos, unidad y rangos de lectura.
- [ ] Validar credenciales y asociación a campaña.
- [ ] Evitar duplicados mediante identificador de lectura.
- [ ] Guardar datos con trazabilidad y probar respuestas de error.

## T20 – Mostrar el estado de Bluetooth y del envío en Android (4 h)

**Prioridad:** P0

**Historia relacionada:** HU29.

**Descripción:**

Añadir una sección de estado que distinga la recepción BLE del Orb del envío de datos a Internet y muestre al portador si hay errores.

**Subtareas:**

- [ ] Mostrar estado de recepción de anuncios BLE.
- [ ] Mostrar estado de transmisión al servidor por separado.
- [ ] Mostrar avisos claros para errores BLE o sin Internet.
- [ ] Probar ambos estados en dispositivo Android.

## T21 – Implementar la gestión de dispositivos por organización (9 h)

**Prioridad:** P0

**Historia relacionada:** HU10.

**Descripción:**

Preparar la API y el panel web para consultar, renombrar, dar de alta/baja administrativamente y asignar Orb a operadores o campañas, respetando siempre los permisos de cada organización.

**Subtareas:**

- [ ] Crear endpoints para alta, baja administrativa y edición de Orb.
- [ ] Diseñar tabla web de flota con último dato y estado.
- [ ] Permitir asignar Orb a usuario y campaña.
- [ ] Probar aislamiento de datos entre organizaciones.

## T22 – Programar los tres LED indicadores de batería del Orb (7 h)

**Prioridad:** P1

**Historia relacionada:** HU06.

**Descripción:**

Probar la medición del nivel de batería con el hardware disponible y configurar tres LED para mostrar niveles bajo, medio y alto. Si el firmware expone batería por BLE, reflejar el dato en la app.

**Subtareas:**

- [ ] Comprobar conexión de batería y GPIO disponibles.
- [ ] Definir tres niveles de batería y comportamiento LED.
- [ ] Programar lectura de tensión y LEDs en firmware.
- [ ] Medir y validar físicamente los niveles y documentar limitaciones.

## T23 – Actualizar la portada y la información pública de AERIQ (4 h)

**Prioridad:** P1

**Historia relacionada:** HU04.

**Descripción:**

Reutilizar los diseños web existentes y adaptar portada, textos y navegación: AERIQ es el producto, Código Norte es la empresa y Orb es el sensor. Mostrar información ambiental, soluciones y acceso al mapa.

**Subtareas:**

- [ ] Actualizar nombre AERIQ, producto Orb y crédito a Código Norte.
- [ ] Revisar contenido de portada y navegación responsive.
- [ ] Añadir información sobre O₃, servicios y limitaciones.
- [ ] Verificar enlaces a mapa y contacto.

## T24 – Integrar el mapa público con mediciones geolocalizadas (9 h)

**Prioridad:** P1

**Historia relacionada:** HU03.

**Descripción:**

Conectar el mapa interactivo de web y la vista de mapa Android con mediciones autorizadas de la API. Mostrar color, fecha, unidad, valor y distinción entre datos reales y simulados.

**Subtareas:**

- [ ] Conectar datos autorizados de la API con mapa web.
- [ ] Mostrar marcadores o capas con colores por nivel.
- [ ] Presentar valor, unidad, fecha y origen real/simulado.
- [ ] Probar navegación, filtros básicos y vista Android.

## T25 – Implementar la interpolación limitada y las zonas sin datos (8 h)

**Prioridad:** P1

**Historia relacionada:** HU28.

**Descripción:**

Representar áreas medidas sin extenderlas a distancias arbitrarias. Probar un umbral de 20–50 metros configurable y mostrar zonas sin datos cuando no existan muestras próximas.

**Subtareas:**

- [ ] Definir algoritmo de interpolación y parámetros configurables.
- [ ] Aplicar límite de proximidad inicial de 20–50 m.
- [ ] Evitar rellenar zonas con insuficientes datos.
- [ ] Validar con casos densos, dispersos y simulados.

## T26 – Crear el formulario de solicitud de kits y estudios (7 h)

**Prioridad:** P1

**Historias relacionadas:** HU18, HU19, HU20.

**Descripción:**

Crear un formulario responsive que permita solicitar kits para ayuntamientos o empresas, o un estudio puntual de AERIQ gestionado por Código Norte, con campos adaptados al servicio seleccionado.

**Subtareas:**

- [ ] Diseñar formulario con selección de kits o estudios.
- [ ] Definir campos específicos de ayuntamiento, empresa y estudio.
- [ ] Validar datos y proporcionar confirmación visible.
- [ ] Probar formulario responsive y errores.

## T27 – Registrar y gestionar las solicitudes recibidas (8 h)

**Prioridad:** P1

**Historias relacionadas:** HU18, HU19, HU20, HU30.

**Descripción:**

Guardar solicitudes de contratación en la base de datos, confirmar su recepción y permitir al equipo de Código Norte consultarlas y cambiar su estado desde un panel privado.

**Subtareas:**

- [ ] Crear tabla y endpoint de solicitudes comerciales.
- [ ] Guardar los formularios de forma persistente.
- [ ] Crear listado privado de solicitudes con estados.
- [ ] Verificar acceso de Código Norte y notificación opcional.

## T28 – Generar informes ambientales con gráficos y alertas (9 h)

**Prioridad:** P1

**Historia relacionada:** HU09.

**Descripción:**

Construir la consulta por campaña, zona y fechas, calcular mínimo/máximo/media de registros válidos y mostrar tablas y gráficas con señalización de umbrales definidos para la demo.

**Subtareas:**

- [ ] Implementar filtros de campaña, zona y periodo.
- [ ] Calcular estadísticas a partir de medidas válidas.
- [ ] Generar gráficos y tabla temporal.
- [ ] Mostrar superaciones de umbral con etiqueta de dato simulado.

## T29 – Implementar la exportación de mediciones a CSV (5 h)

**Prioridad:** P1

**Historia relacionada:** HU15.

**Descripción:**

Permitir descargar mediciones filtradas por campaña, fechas, zona o dispositivo, incluyendo unidad, fecha y coordenadas cuando estén autorizadas. Asegurar permisos organizativos.

**Subtareas:**

- [ ] Definir columnas y filtros del CSV.
- [ ] Implementar exportación desde API o web.
- [ ] Aplicar permisos de organización y coordenadas.
- [ ] Verificar fechas, unidades y apertura en hoja de cálculo.

## T30 – Probar la integración completa y preparar la demostración (5 h)

**Prioridad:** P0

**Historias relacionadas:** HU01, HU02, HU03, HU05, HU10, HU29.

**Descripción:**

Verificar los flujos de alta y acceso, captación BLE, ubicación, recepción en API, gestión de Orb y representación en el mapa. Registrar errores y preparar una demo con datos reales o simulados correctamente marcados.

**Subtareas:**

- [ ] Probar alta y acceso con dos roles distintos.
- [ ] Probar flujo BLE → Android → API → BBDD.
- [ ] Probar mapa, gestión de Orb y errores de envío.
- [ ] Preparar demo y documentar problemas pendientes.

---

# PARTE III — RESUMEN DE PLANIFICACIÓN

## Resumen de tareas

| Tarea | Horas | Prioridad |
|---|---:|---|
| T13 – Base de datos organizativa | 6 h | P0 |
| T14 – Alta web de trabajadores | 8 h | P0 |
| T15 – Invitaciones y contraseña temporal | 5 h | P0 |
| T16 – Login Android | 8 h | P0 |
| T17 – Recepción BLE | 6 h | P0 |
| T18 – Ubicación y envío de mediciones | 7 h | P0 |
| T19 – Validación de lecturas API | 7 h | P0 |
| T20 – Estado BLE y envío | 4 h | P0 |
| T21 – Gestión de dispositivos | 9 h | P0 |
| T22 – Indicadores LED de batería | 7 h | P1 |
| T23 – Actualizar portada AERIQ | 4 h | P1 |
| T24 – Mapa público | 9 h | P1 |
| T25 – Interpolación espacial | 8 h | P1 |
| T26 – Formulario de contratación | 7 h | P1 |
| T27 – Gestión de solicitudes | 8 h | P1 |
| T28 – Informes ambientales | 9 h | P1 |
| T29 – Exportación CSV | 5 h | P1 |
| T30 – Pruebas e integración | 5 h | P0 |
| **TOTAL** | **122 h** | |

**Tareas P0:** 10 tareas, 65 horas.

**Tareas P1:** 8 tareas, 57 horas.

**Total:** 18 tareas y 70 subtareas registradas en Trello.

Las estimaciones son orientativas y deben ajustarse a la capacidad y experiencia del equipo.

## Instrucciones para desarrollar este proyecto

Al utilizar esta documentación para implementar AERIQ, se deben respetar las siguientes condiciones:

1. Mantener Código Norte como empresa desarrolladora y comercializadora, AERIQ como producto y Orb como dispositivo físico.
2. Respetar las historias de usuario y sus criterios de aceptación.
3. Desarrollar las tareas por orden de dependencias técnicas, priorizando el flujo completo de mediciones.
4. Aprovechar el código y los diseños que ya existan, evitando rehacer funcionalidades que estén implementadas y probadas.
5. Mantener el aislamiento de datos privados entre las organizaciones.
6. Diferenciar las mediciones reales de las simuladas.
7. Evitar las antiguas funcionalidades Premium, las suscripciones personales y la venta de sensores a ciudadanos.
8. Mantener la compatibilidad entre Android, el firmware del Orb, la API, la base de datos y la plataforma web.
9. No considerar una tarea terminada hasta comprobar que funciona y cumple los criterios asociados.
10. Registrar cualquier limitación de hardware, error de integración o funcionalidad que quede pendiente.

**Nota sobre el alcance:** este documento recoge las 15 historias de usuario de la última revisión y las 18 tareas creadas a partir de ellas. No constituye necesariamente el Product Backlog completo de AERIQ: existen otras historias antiguas y funcionalidades que todavía deben revisarse o planificarse.

**Tablero de Trello:** https://trello.com/b/JsrnGLyO/pb-2026-2027-06
