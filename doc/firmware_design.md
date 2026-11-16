# Diseño de firmware Orb

## Component Design

El firmware en `src/firmware/NodoO3/` lee ozono y temperatura, valida las lecturas, mantiene el estado de batería disponible y anuncia datos por BLE iBeacon. El bucle utiliza máquina de estados con `millis()`/`micros()` sin esperas bloqueantes. El contrato de tipo/secuencia y valor debe seguir `src/firmware/README.md` y ser interpretado por `IBeaconParser.java`.

## Design Clarifications

- El Orb comunica datos mediante anuncios; no hay protocolo de conexión persistente.
- Los pines, calibración y umbrales físicos dependen de la revisión del hardware y deben documentarse con mediciones reales.
- No presentar lecturas simuladas como medidas físicas ni inventar estados de batería no expuestos por hardware.

## General Rules

- **Programming Language:** C++ Arduino para el firmware nRF52840, con placa/procesador especificados en `src/firmware/README.md`.
- Evitar `delay()`; mantener tareas no bloqueantes y compatibilidad BLE con la app Android.
- Validar unidades, rangos, rollover de contadores y comportamiento ante sensor desconectado.
- **Function/Method Headers:** documentar el diseño lógico de nuevas funciones/métodos en un bloque entre líneas `--------------------`.
- **Code Readability:** separar adquisición, validación y publicación BLE con nombres explícitos.
- **Automated Testing:** generar pruebas lógicas para parser/paquetes, rangos, secuencias y transiciones; completar con pruebas físicas documentadas.
