# Orb: dependencia de firmware y hardware

El firmware fuente y el circuito de batería/LED no se han aportado en esta carpeta. Se mantiene el contrato iBeacon existente: fabricante 0x004C, prefijo 02 15, identificador EPSG-GTI-PROY-3A, tipos 14 ozono ppb y 12 temperatura °C. No hay transmisión de batería acordada en ese contrato.

T22 necesita comprobar batería, divisor/ADC, GPIO y tres LED en el equipo real, fijar niveles a partir de mediciones y validar carga/descarga. No se asignan pines ni tensiones por suposición. Si se añade batería a BLE, debe definirse el paquete y actualizar parser/app y pruebas conjuntamente.

La app indica que batería no está transmitida y mantiene diagnóstico real de anuncios y envío; no confunde una recepción BLE correcta con una conexión GATT permanente.
