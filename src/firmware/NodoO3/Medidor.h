/*
 * Fichero: Medidor.h
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/firmware/NodoO3.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

#pragma once
#include <Arduino.h>
#include "Calibracion.h"
#include "Protocolo.h"

namespace aeriq {
class Medidor {
  const uint8_t pines[3] = {5, 28, 29}; // P0.05 VGAS, P0.28 VREF, P0.29 VTEMP
  float voltios[3] = {};
  float ultimoCiclo[3] = {};
  uint8_t canal = 0, muestras = 0;
  uint32_t suma = 0, ultimoMs = 0, cambioUs = 0;
  bool activo = false, descartar = true, saturado = false, ultimoSaturado = false;
  Calibracion calibracion;
 public:
  /**
   * Diseño lógico: canalAdc: N --> voltaje() --> R
   * Descripción: Describe la operación realizada y su resultado.
   */
  float voltaje(uint8_t canalAdc) const {
    return canalAdc < 3 ? ultimoCiclo[canalAdc] : 0.f;
  }
  /**
   * Diseño lógico: saturadoUltimoCiclo() --> B
   * Descripción: Procesa saturado ultimo ciclo y devuelve el resultado correspondiente.
   */
  bool saturadoUltimoCiclo() const { return ultimoSaturado; }
  Calibracion &calibrado() { return calibracion; }
  /**
   * Diseño lógico: begin()
   * Descripción: Inicializa el componente y los recursos que necesita..
   */
  void begin() {
    for (uint8_t pin : pines) pinMode(pin, INPUT);
    analogReference(AR_DEFAULT); // 0.6 V interna x ganancia inversa 6 = 3.6 V
    analogReadResolution(12);
    analogSampleTime(40);
    analogOversampling(16);
    analogCalibrateOffset();
  }
  /**
   * Diseño lógico: iniciar()
   * Descripción: Procesa iniciar y devuelve el resultado correspondiente..
   */
  void iniciar() {
    canal = muestras = 0; suma = 0;
    activo = descartar = true; saturado = false;
  }
  /**
   * Diseño lógico: resultado: Text --> actualizar() --> B
   * Descripción: Actualiza las medidas de batería cuando corresponde y conserva el último estado válido.
   */
  bool actualizar(Medida &resultado) {
    if (!activo) return false;
    if (descartar) {
      analogRead(pines[canal]); // Primera conversion descartada al cambiar canal.
      cambioUs = micros(); ultimoMs = millis(); descartar = false;
      return false;
    }
    // Una conversion por vuelta: sin delay ni bucles de espera por muestras.
    if (!transcurrido(micros(), cambioUs, 300) ||
        !transcurrido(millis(), ultimoMs, 2)) return false;
    const int lectura = analogRead(pines[canal]);
    ultimoMs = millis();
    if (lectura <= 0 || lectura >= 4095) saturado = true;
    suma += lectura;
    if (++muestras < 50) return false;
    voltios[canal] = (static_cast<float>(suma) / 50.f) * 3.6f / 4095.f;
    if (++canal < 3) {
      suma = 0; muestras = 0; descartar = true;
      return false;
    }
    activo = false;
    for (uint8_t i = 0; i < 3; ++i) ultimoCiclo[i] = voltios[i];
    ultimoSaturado = saturado;
    resultado = calibracion.convertir(voltios[0], voltios[1], voltios[2], saturado);
    return true;
  }
};
}
