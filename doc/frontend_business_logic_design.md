# Diseño de lógica de cliente AERIQ

## Component Design

**Responsabilidad.** Exponer operaciones orientadas al producto para la interfaz HTML/JavaScript y encapsular HTTP, serialización, CSRF y errores. Los clientes actuales son `src/web/aeriq-client.js`, `auth-client.js`, `google-auth.js` y `pbio-client.js`. Las vistas consumen clientes de dominio como `Aeriq.portal`, `Aeriq.map` y `Aeriq.submit`; no deben construir solicitudes de negocio directamente.

| Método lógico | Entradas | Salida | Adaptador actual |
|---|---|---|---|
| `consultar_panel` | `Accion, Filtros` | `ResultadoPanel` | `Aeriq.portal` |
| `consultar_mapa` | `Ambito, Procedencia, Periodo` | `[Medicion]` | `Aeriq.map` |
| `enviar_solicitud` | `DatosSolicitud` | `Confirmacion` | `Aeriq.submit` |
| `consultar_servicios` | — | `[Servicio]` | `Aeriq.services` |
| `iniciar_sesion` | `Credenciales` | `Sesion` | `AeriqAuth` |

Las operaciones del cliente presentan conceptos de dominio; el adaptador de transporte convierte esos tipos a los parámetros/JSON de `doc/api.md` y traduce errores HTTP a errores de interfaz. `aeriq-domain.js` contiene cálculos puros de CSV, estadísticas e interpolación sin DOM ni red.

## Design Clarifications

- El código ya centraliza buena parte del transporte en clientes compartidos; `google-auth.js` contiene la integración del proveedor externo y no debe ser copiada por pantallas.
- Los datos de autorización del cliente sirven para presentar la interfaz. El servidor vuelve a comprobar toda autorización.
- El diseño del GUI es independiente; su contenido y presentación se documentan en los HTML y documentos de producto existentes.

## General Rules

- **Programming Language:** JavaScript ES2020+ en navegador; HTML y CSS para presentación.
- Las pantallas llaman a interfaces de cliente; encapsular llamadas de red, credenciales y manejo de errores en adaptadores.
- No confiar en filtros, permisos ni identificadores enviados por el navegador; validar en servidor.
- **Function/Method Headers:** cada función o método nuevo debe incluir diseño lógico en bloque delimitado por líneas `--------------------`.
- **Code Readability:** mantener funciones enfocadas, cálculos puros reutilizables y DOM seguro (`textContent`, nodos y valores escapados).
- **Automated Testing:** generar pruebas unitarias de cálculos y pruebas de contrato para adaptadores, errores y permisos visibles.
