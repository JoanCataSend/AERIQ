# Comunicación con el trabajador desde el dispositivo (T48)

Investigación sobre cómo podría AERIQ comunicarse con el operador que lleva el Orb, para decidir si se implementa y cómo. Es solo un estudio: no cambia el código.

## Estado del alcance

> **Pendiente de confirmar con el profesor.** En las notas de la revisión del Sprint 2 la frase quedó cortada («comunicarse con el trabajador desde el dispositivo…»). Antes de implementar nada hay que confirmar:
>
> 1. **Quién habla con quién.** ¿El sistema avisa al trabajador, el admin de campaña le escribe, o el trabajador avisa al admin?
> 2. **Por dónde.** ¿Por la app del móvil o por el propio Orb (luz, vibración, sonido)?
> 3. **Para qué.** ¿Alertas de ozono alto, instrucciones de la campaña o incidencias del Orb?

Este documento deja preparadas las opciones para que la decisión sea rápida.

## De qué partimos

- **El Orb solo emite.** Envía anuncios iBeacon *no conectables* cada 100 ms (`src/firmware/README.md`). No acepta conexiones ni recibe datos, y no tiene pantalla, altavoz ni motor de vibración. Solo tiene los tres LED de batería (T22).
- **La app ya está en contacto con el servidor.** Envía lecturas a `field.php` con el token móvil y recibe la asignación del Orb.
- **El admin de campaña ya gestiona a sus operadores** desde el panel web y, desde la app, puede asignarles un Orb (`mobile-team.php`).
- **La app ya sabe el nivel de ozono en directo.** Calcula si es bajo, moderado o alto con los umbrales de la campaña.

## Opciones

| # | Opción | Qué haría | Esfuerzo | Ventajas | Inconvenientes |
|---|---|---|---|---|---|
| A | **Avisos locales en la app** | Si la lectura en directo supera el umbral alto de la campaña, la app muestra una notificación y vibra el móvil | Bajo (solo Android) | Sin servidor nuevo ni cuentas externas. Funciona sin cobertura, porque el cálculo ya está en el móvil | Solo cubre alertas automáticas, no mensajes del admin |
| B | **Mensajes del admin por la API (consulta periódica)** | El admin escribe un aviso a un operador o a toda la campaña en el panel. La app lo recoge al consultar `field.php` (ya lo hace al abrirse y al enviar lecturas) y lo muestra | Medio: tabla `mensajes`, acción en `portal.php`, campo en `field.php` y pantalla en la app | Reutiliza el token y los permisos actuales. Queda registro de quién envió qué | Llega con retraso (cuando la app vuelve a consultar). Si la app está cerrada no hay aviso inmediato |
| C | **Notificaciones push (Firebase Cloud Messaging)** | Igual que B, pero el servidor avisa al móvil al momento | Medio-alto: guardar el token FCM de cada móvil, credenciales de servidor de Firebase y envío desde PHP | Inmediato aunque la app esté cerrada. El proyecto ya usa Firebase para el acceso con Google | Credenciales de servidor de Firebase en Plesk. Depende de los servicios de Google en el móvil. Más piezas que mantener |
| D | **Aviso en el propio Orb (LED)** | El Orb parpadea en un patrón distinto cuando el ozono que mide supera un umbral | Bajo en firmware, pero los umbrales tendrían que ir fijos en el Orb o llegarle de algún modo | Lo ve el trabajador aunque no mire el móvil | El Orb no recibe datos: no conoce los umbrales de cada campaña. Se confunde con los LED de batería. Recibir datos obligaría a hacerlo conectable (cambia el contrato BLE y el consumo) |
| E | **Trabajador → admin** | Botón «Avisar a mi admin» en la app (incidencia: Orb roto, zona inaccesible…) | Medio, comparte la mayoría de piezas con B | Cierra el ciclo de comunicación | Necesita que el admin revise el panel o reciba un correo |

## Recomendación

1. **Primero A (avisos locales).** Es lo que más valor da con menos riesgo. Encaja con «comunicarse con el trabajador desde el dispositivo»: el móvil, a partir de lo que mide el Orb, avisa al trabajador en el momento. No toca el servidor ni el firmware.
2. **Después B, si el profesor se refería a mensajes del admin.** La consulta periódica reutiliza todo lo existente. Si hace falta inmediatez, se pasaría a C, con B como respaldo.
3. **No hacer D ahora.** Que el Orb reciba datos cambiaría el contrato BLE (anuncios no conectables) y la batería. Usar los LED para el ozono, además, se confundiría con su uso actual para la batería.

## Si se aprueba A: criterios de validación propuestos

- Con una lectura en directo igual o superior al umbral alto de la campaña, el móvil muestra una notificación y vibra una vez.
- No se repite la alerta mientras el nivel siga alto. Se vuelve a avisar solo después de bajar del umbral medio y volver a subir.
- La notificación dice el valor, la unidad y el nivel. Con datos simulados no se avisa como si fueran reales.
- Se puede desactivar en Cuenta. Si el usuario no concede el permiso de notificaciones (Android 13 o superior), la app sigue funcionando sin avisos.

## Decisión del equipo

> **Pendiente.** Rellenar tras hablar con el profesor: opción elegida, alcance y en qué sprint se hace.
