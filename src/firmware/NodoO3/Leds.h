/*
 * Fichero: Leds.h
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/firmware/NodoO3.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */


#pragma once
#include <Arduino.h>
#include "Protocolo.h"

namespace aeriq {
// Tres LED de nivel de bateria. Conexion: pin -> resistencia -> LED (pata larga) -> GND.
// Resistencias orientativas a 3.3 V: rojo 1 kOhm, verde y azul 220 Ohm (1-2 mA).
// Cambia aqui los pines si los conectas en otros.
class Leds {
  static constexpr uint8_t PIN_ROJO = 2, PIN_VERDE = 3, PIN_AZUL = 4;
  uint32_t inicio = 0;
  Color encendido = Color::NINGUNO;

  /**
   * Gestiona mostrar.
   * Diseño lógico: (Color color) → mostrar() → resultado de la operación; según doc/firmware_design.md.
   */
  void mostrar(Color color) {
    if (color == encendido) return;
    digitalWrite(PIN_ROJO, color == Color::ROJO ? HIGH : LOW);
    digitalWrite(PIN_VERDE, color == Color::VERDE ? HIGH : LOW);
    digitalWrite(PIN_AZUL, color == Color::AZUL ? HIGH : LOW);
    encendido = color;
  }

 public:
  /**
   * Gestiona begin.
   * Diseño lógico: (uint32_t ahora) → begin() → resultado de la operación; según doc/firmware_design.md.
   */
  void begin(uint32_t ahora) {
    const uint8_t pines[3] = {PIN_ROJO, PIN_VERDE, PIN_AZUL};
    for (uint8_t pin : pines) {
      pinMode(pin, OUTPUT);
      digitalWrite(pin, LOW);
    }
    inicio = ahora;
  }
  // Destello de 80 ms cada 5 s con el color del nivel; apagado si no se conoce.
  /**
   * Diseño lógico: (uint32_t ahora, int16_t porcentaje) → actualizar() → resultado de la operación; según doc/firmware_design.md.
   */
  void actualizar(uint32_t ahora, int16_t porcentaje) {
    mostrar(destelloEncendido(ahora, inicio) ? colorBateria(porcentaje) : Color::NINGUNO);
  }
};
}
