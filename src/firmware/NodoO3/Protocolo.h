#pragma once
#include <stdint.h>
#include <string.h>

namespace aeriq {
constexpr uint8_t TIPO_O3 = 14, TIPO_TEMPERATURA = 12, TIPO_ESTADO = 15;
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
}
