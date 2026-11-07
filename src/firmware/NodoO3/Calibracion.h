#pragma once
#include <stdint.h>
#include <math.h>

namespace aeriq {
enum Estado : uint16_t {
  VALIDA = 1, NEGATIVO_LIMITADO = 2, MAXIMO_LIMITADO = 4,
  REFERENCIA_INVALIDA = 8, ADC_SATURADO = 16,
  TEMPERATURA_FUERA_RANGO = 32, CALCULO_INVALIDO = 64,
  // |VGAS - VREF - cero| > 60 mV (= ~1600 ppb): imposible en aire ambiente.
  // Indica cableado/montaje incorrecto, no una concentracion real.
  SENAL_FUERA_RANGO = 128
};
struct Medida {
  uint16_t ozono = 0;
  int16_t temperatura = 0;
  uint16_t estado = 0;
  float ozonoInstantaneo = 0, ozonoFiltrado = 0;
};

// Calibracion de fabrica del sensor concreto (Data Matrix de la pegatina):
//   032824010609 110406 O3 2404 -74.04
//   serie        pieza  gas fecha(AAMM) sensibilidad nA/ppm
// y ganancia TIA de la placa ULPSM (pegatina "GAIN 499 kV/A").
constexpr float SENSIBILIDAD = -74.04f; // nA/ppm
constexpr float GANANCIA_TIA = 499.0f;  // kV/A
// M = nA/ppm * kV/A * 1e-9 * 1e3 = -0.03694596 V/ppm (-36.95 mV/ppm, -0.037 mV/ppb)
constexpr float PENDIENTE = SENSIBILIDAD * GANANCIA_TIA * 1.e-6f;
// Cero por defecto (Vgas0 = Vref + Voffset). Se sustituye por el medido con
// el comando serie CERO y queda guardado en la flash de la placa.
constexpr float OFFSET_V = 0.0f;
constexpr float OFFSET_MAX_V = 0.030f;   // la ficha da +-5 mV; mas de 30 mV = averia/cableado
constexpr float SENAL_MAX_V = 0.060f;    // ~1600 ppb
// Modulo ULPSM: alimentacion nominal admitida 2.7..3.3 V. Margen ADC de 0.1 V.
constexpr float VPLUS_MIN = 2.6f, VPLUS_MAX = 3.4f;

class Calibracion {
  float historial[5] = {};
  uint8_t cantidad = 0, posicion = 0;
  float ema = 0;
  float offset = OFFSET_V;       // V
  float factor = 1.f;            // ajuste de span por comparacion con estacion oficial
  float pendiente = PENDIENTE;   // V/ppm

  static float limitarTemperatura(float t, bool &fuera) {
    if (t < -20.f) { fuera = true; return -20.f; }
    if (t > 50.f) { fuera = true; return 50.f; }
    return t;
  }
  // Deriva tipica del cero del O3 (ficha ULPSM-O3): 0 hasta 30 C, 0.0066 ppm/C despues.
  static float ceroTermico(float tc) { return tc <= 30.f ? 0.f : .0066f * (tc - 30.f); }

 public:
  void reiniciarFiltro() { cantidad = posicion = 0; }
  float cero() const { return offset; }
  float span() const { return factor; }
  float sensibilidad() const { return pendiente / (GANANCIA_TIA * 1.e-6f); }
  bool fijarCero(float voltios) {
    if (!isfinite(voltios) || fabsf(voltios) > OFFSET_MAX_V) return false;
    offset = voltios; reiniciarFiltro(); return true;
  }
  bool fijarSpan(float valor) {
    if (!isfinite(valor) || valor < .3f || valor > 3.f) return false;
    factor = valor; reiniciarFiltro(); return true;
  }
  bool fijarSensibilidad(float nAppm) {
    if (!isfinite(nAppm) || fabsf(nAppm) < 5.f || fabsf(nAppm) > 300.f) return false;
    pendiente = nAppm * GANANCIA_TIA * 1.e-6f; reiniciarFiltro(); return true;
  }

  // Tension de cero de una muestra tomada en aire sin ozono (filtro de carbon activo):
  // VGAS - VREF menos la deriva termica esperada. false si la referencia no es valida.
  bool muestraCero(float gas, float ref, float term, float &voltios) const {
    const float alimentacion = 2.f * ref;
    if (!isfinite(gas) || !isfinite(alimentacion) || alimentacion < VPLUS_MIN || alimentacion > VPLUS_MAX)
      return false;
    bool fuera = false;
    const float tc = limitarTemperatura((87.f / alimentacion) * term - 18.f, fuera);
    voltios = (gas - ref) - pendiente * ceroTermico(tc);
    return isfinite(voltios);
  }

  Medida convertir(float gas, float ref, float term, bool saturado) {
    Medida m;
    if (saturado) m.estado |= ADC_SATURADO;
    const float alimentacion = 2.f * ref;
    if (!isfinite(alimentacion) || alimentacion < VPLUS_MIN || alimentacion > VPLUS_MAX) {
      m.estado |= REFERENCIA_INVALIDA;
      reiniciarFiltro();
      return m;
    }
    const float temperatura = (87.f / alimentacion) * term - 18.f;
    bool fuera = false;
    const float tc = limitarTemperatura(temperatura, fuera);
    if (fuera) m.estado |= TEMPERATURA_FUERA_RANGO;
    const float senal = gas - (ref + offset);
    if (isfinite(senal) && fabsf(senal) > SENAL_MAX_V) {
      m.estado |= SENAL_FUERA_RANGO;
      reiniciarFiltro();
      return m;
    }
    const float span = 1.f + .003f * (tc - 20.f);
    const float ppm = senal / pendiente;
    const float ppb = ((ppm - ceroTermico(tc)) / span) * 1000.f * factor;
    if (!isfinite(ppb) || !isfinite(temperatura)) {
      m.estado |= CALCULO_INVALIDO;
      reiniciarFiltro();
      return m;
    }
    // Una entrada saturada no genera medidas falsas ni contamina el filtro.
    if (saturado) { reiniciarFiltro(); return m; }
    m.ozonoInstantaneo = ppb;
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
    m.ozonoFiltrado = ema;
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
