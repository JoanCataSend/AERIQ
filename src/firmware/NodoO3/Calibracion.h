#pragma once
#include <stdint.h>
#include <math.h>

namespace aeriq {
enum Estado : uint16_t {
  VALIDA = 1, NEGATIVO_LIMITADO = 2, MAXIMO_LIMITADO = 4,
  REFERENCIA_INVALIDA = 8, ADC_SATURADO = 16,
  TEMPERATURA_FUERA_RANGO = 32, CALCULO_INVALIDO = 64
};
struct Medida {
  uint16_t ozono = 0;
  int16_t temperatura = 0;
  uint16_t estado = 0;
};

// Calibracion individual de sensor-real-final, NO universal para otros sensores.
constexpr float SENSIBILIDAD = -74.04f; // nA/ppm
constexpr float GANANCIA_TIA = 499.0f; // kV/A
constexpr float PENDIENTE = SENSIBILIDAD * GANANCIA_TIA * 1.e-6f;
constexpr float OFFSET_V = 0.0f; // No se ha aportado una calibracion de cero distinta.

class Calibracion {
  float historial[5] = {};
  uint8_t cantidad = 0, posicion = 0;
  float ema = 0;
 public:
  Medida convertir(float gas, float ref, float term, bool saturado) {
    Medida m;
    if (saturado) m.estado |= ADC_SATURADO;
    const float alimentacion = 2.f * ref;
    if (!isfinite(alimentacion) || alimentacion <= .1f) {
      m.estado |= REFERENCIA_INVALIDA;
      cantidad = posicion = 0;
      return m;
    }
    const float temperatura = (87.f / alimentacion) * term - 18.f;
    float tc = temperatura;
    if (tc < -20.f) { tc = -20.f; m.estado |= TEMPERATURA_FUERA_RANGO; }
    if (tc > 50.f) { tc = 50.f; m.estado |= TEMPERATURA_FUERA_RANGO; }
    const float cero = tc <= 30.f ? 0.f : .0066f * (tc - 30.f);
    const float span = 1.f + .003f * (tc - 20.f);
    const float ppm = (gas - (ref + OFFSET_V)) / PENDIENTE;
    const float ppb = ((ppm - cero) / span) * 1000.f;
    if (!isfinite(ppb) || !isfinite(temperatura)) {
      m.estado |= CALCULO_INVALIDO;
      cantidad = posicion = 0;
      return m;
    }
    // Una entrada saturada no genera medidas falsas ni contamina el filtro.
    if (saturado) { cantidad = posicion = 0; return m; }
    historial[posicion] = ppb;
    posicion = (posicion + 1) % 5;
    if (cantidad < 5) ++cantidad;
    float orden[5];
    for (uint8_t i = 0; i < cantidad; ++i) orden[i] = historial[i];
    for (uint8_t i = 1; i < cantidad; ++i)
      for (uint8_t j = i; j > 0 && orden[j] < orden[j-1]; --j) {
        float aux = orden[j]; orden[j] = orden[j-1]; orden[j-1] = aux;
      }
    const float mediana = cantidad % 2 ? orden[cantidad/2]
      : (orden[cantidad/2-1] + orden[cantidad/2]) / 2.f;
    ema = cantidad == 1 ? mediana : .25f * mediana + .75f * ema;
    float salida = ema;
    if (salida < 0) { salida = 0; m.estado |= NEGATIVO_LIMITADO; }
    if (salida > 20000) { salida = 20000; m.estado |= MAXIMO_LIMITADO; }
    m.ozono = static_cast<uint16_t>(lroundf(salida));
    float t = temperatura;
    if (t < -32768.f) t = -32768.f;
    if (t > 32767.f) t = 32767.f;
    m.temperatura = static_cast<int16_t>(lroundf(t));
    m.estado |= VALIDA;
    return m;
  }
};
}
