# Temperatura y diagnóstico de la referencia

Android muestra una tarjeta de temperatura del módulo, selector de magnitud en
historial y CSV en °C. La web permite Ozono, Temperatura o ambas series en informes.
Las estadísticas se separan por unidad; los umbrales de ozono no se aplican a °C.
El mapa ambiental sigue siendo de ozono. El protocolo conserva tipo 12 int16 °C
y tipo 14 uint16 ppb; no requiere migración SQL.

## Lectura aportada

```text
VGAS=1315mV VREF=453mV VTEMP=1641mV
O3=0ppb T=140C O3raw=-21523ppb O3filtro=-29080ppb estado=0x0023
```

La fórmula deduce Vplus=0.906 V desde VREF, y de ahí T≈140 °C. No es una
temperatura fiable. El ozono calculado es negativo y se limita a cero. El estado
0x23 combina cálculo aritmético válido (1), límite negativo (2) y compensación
térmica fuera de rango (32). No demuestra que la instalación esté bien.

El firmware nuevo rechaza Vplus inferido fuera de 2.6..3.4 V: margen software
de 0.1 V alrededor de la alimentación especificada 2.7..3.3 V. Con la lectura
aportada emite diagnóstico de referencia inválida, no 0 ppb/140 °C como lecturas
válidas. No corrige artificialmente señales ni calibra el cero automáticamente.
La señal serie muestra los tres voltajes del mismo ciclo y O3 antes del límite.

## Cableado y comprobación física

Para **ULPSM-O3 968-046**, el pinout es 1 VGAS, 2 VREF, 3 VTEMP, 6 GND,
7 V+; 4/5 sin conexión. En la SparkFun Pro nRF52840 Mini, las entradas utilizadas
son P0.05/A3, P0.28/A4 y P0.29/A5 respectivamente. Comprobar orientación del pin 1;
el número del conector no es la posición contada desde cualquier extremo.

La [ficha SPEC](https://media.digikey.com/pdf/Data%20Sheets/SPEC%20Sensors%20PDFs/968-046_8-25-17.pdf)
exige buffers de ganancia unidad para VREF y VTEMP (alta impedancia). No basta
con afirmar que los cables están soldados: la conexión directa al ADC no cumple
esa recomendación. La ficha recomienda 60 minutos de estabilización. Con 3.3 V
de alimentación, VREF debería estar cerca de 1.65 V en una medición correctamente
acondicionada. El fabricante no indica un valor fijo de temperatura ambiente.

Comprobar alimentación entre 7 y 6, continuidad/orientación con alimentación
retirada y el buffer/circuito de entrada. No intercambiar señales a prueba y error
ni modificar OFFSET para ocultar el problema. La causa concreta requiere revisión
eléctrica; los números solos no distinguen soldadura, cable, carga ADC o módulo.

## Instalar

1. Actualizar APK.
2. Subir a `httpdocs/src/web/` el ZIP de web manteniendo carpetas.
3. Abrir `src/firmware/NodoO3/NodoO3.ino` y volver a cargar el firmware.
4. Ver monitor a 115200 y elegir Temperatura en historial/informes.

El firmware antiguo puede haber guardado valores incorrectos: esta actualización
no borra ni reescribe el histórico. Se corrige la recepción de ciclos nuevos.
La comprobación real de temperatura debe realizarse después de corregir VREF.
