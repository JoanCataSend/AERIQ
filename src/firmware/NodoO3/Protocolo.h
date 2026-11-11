#pragma once
#include <stdint.h>
#include <string.h>

namespace aeriq {
constexpr uint8_t TIPO_O3 = 14, TIPO_TEMPERATURA = 12, TIPO_ESTADO = 15, TIPO_BATERIA = 16;
// e3b44c20-3f02-4d76-9cb2-bdcb3105a001, orden BLE (LSB primero).
constexpr uint8_t UUID_ESTADO[16] = {
  0x01,0xa0,0x05,0x31,0xcb,0xbd,0xb2,0x9c,
  0x76,0x4d,0x02,0x3f,0x20,0x4c,0xb4,0xe3
};
// Incluye los dos bytes del fabricante; Android los separa del payload.
inline void anuncio(uint8_t tipo, uint8_t secuencia, uint16_t valor, uint8_t out[25]) {
  out[0] = 0x4c; out[1] = 0; out[2] = 2; out[3] = 0x15;
  memcpy(out + 4, "EPSG-GTI-PROY-3A", 16);
  out[20] = tipo; out[21] = secuencia;
  out[22] = valor >> 8; out[23] = valor & 255;
  out[24] = static_cast<uint8_t>(-53); // RSSI de referencia; potencia radio +4 dBm.
}
inline void extension(uint64_t id, uint8_t secuencia, uint16_t estado, uint8_t out[28]) {
  memcpy(out, UUID_ESTADO, 16);
  out[16] = 1; // Version del protocolo de estado.
  out[17] = secuencia;
  for (uint8_t i = 0; i < 8; ++i) out[18+i] = id >> (56-8*i);
  out[26] = estado >> 8; out[27] = estado & 255;
}
inline bool transcurrido(uint32_t ahora, uint32_t inicio, uint32_t intervalo) {
  return static_cast<uint32_t>(ahora - inicio) >= intervalo;
}

// ---- Bateria: medidor MAX17048 por I2C (Qwiic) ----
// Registro SOC: porcentaje en 1/256 %. Se redondea y se limita a 0..100.
inline uint8_t porcentajeMax17048(uint16_t soc) {
  const uint32_t redondeado = (static_cast<uint32_t>(soc) + 128) >> 8;
  return redondeado > 100 ? 100 : static_cast<uint8_t>(redondeado);
}
// Registro VCELL: 78.125 uV por unidad = 5/64 mV.
inline uint16_t milivoltiosMax17048(uint16_t vcell) {
  return static_cast<uint16_t>((static_cast<uint32_t>(vcell) * 5) / 64);
}
// Registro VERSION: la familia MAX17048/49 responde 0x001X.
inline bool esMax1704x(uint16_t version) { return (version & 0xfff0) == 0x0010; }

// Color del LED segun la bateria: verde > 60 %, azul 20..60 %, rojo < 20 %.
// Sin medidor o sin lectura (porcentaje < 0) no se enciende ninguno: no se inventa el nivel.
enum class Color : uint8_t { NINGUNO, ROJO, AZUL, VERDE };
inline Color colorBateria(int16_t porcentaje) {
  if (porcentaje < 0) return Color::NINGUNO;
  if (porcentaje > 60) return Color::VERDE;
  if (porcentaje >= 20) return Color::AZUL;
  return Color::ROJO;
}
// Destello corto cada periodo: casi no gasta bateria y se ve igual.
constexpr uint32_t DESTELLO_PERIODO_MS = 5000, DESTELLO_DURACION_MS = 80;
inline bool destelloEncendido(uint32_t ahora, uint32_t inicio) {
  return static_cast<uint32_t>(ahora - inicio) % DESTELLO_PERIODO_MS < DESTELLO_DURACION_MS;
}
}
