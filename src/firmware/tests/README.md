# Pruebas de lógica del firmware

`checks.cpp` ejecuta el C++ real de Calibracion y Protocolo. No simula hardware
ni valida la temporización del ADC o la radio. Se puede llamar `run_tests()` desde
un host C++ o una placa: devuelve cero o la línea de la comprobación fallida.

Para ejecutarlo sin placa, se usa Clang con destino WebAssembly y Node.js:

```powershell
# Desde la raíz del proyecto; crear primero una carpeta de salida ignorada:
New-Item -ItemType Directory -Force .local-backups/sensor-tests
clang++ --target=wasm32 -O1 -nostdlib -nostdinc -fno-builtin -Isrc/firmware/tests/wasm src/firmware/tests/checks.cpp '-Wl,--no-entry' '-Wl,--export=run_tests' '-Wl,--allow-undefined' -o .local-backups/sensor-tests/checks.wasm
node src/firmware/tests/run.mjs .local-backups/sensor-tests/checks.wasm
```

`wasm/` aporta únicamente los tipos básicos, copia de bytes y declaraciones
necesarias en el host sin libc. Node proporciona `lroundf` con redondeo al entero
más próximo, empates alejándose de cero. Estos headers **no** se incluyen en la
compilación Arduino, que utiliza su biblioteca estándar real.

Casos: vector de bytes completo O3, temperatura negativa, ID/estado, rollover de
reloj, conversión de 100 ppb a 20 °C, compensación a 40 °C, rechazo de un pico,
reinicio del filtro tras error, límites de salida, saturación y temperatura fuera
del intervalo de compensación.
