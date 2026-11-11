#pragma once
#include <Arduino.h>
#include <Wire.h>
#include "Protocolo.h"

namespace aeriq {
// Medidor de bateria LiPo MAX17048 conectado al Qwiic de la placa (SDA 8, SCL 11).
// Si no esta conectado o no responde, la bateria queda desconocida (-1): no se
// enciende ningun LED de nivel ni se publica el anuncio de bateria.
class Bateria {
  static constexpr uint8_t DIRECCION = 0x36;
  static constexpr uint8_t REG_VCELL = 0x02, REG_SOC = 0x04, REG_VERSION = 0x08;
  static constexpr uint32_t PERIODO_MS = 30000; // La carga cambia despacio.
  uint32_t ultima = 0;
  bool pendiente = true;
  int16_t porcentajeActual = -1;
  uint16_t milivoltiosActual = 0;

  // Una lectura I2C de 2 bytes: llamada sincronica acotada (< 1 ms a 100 kHz).
  bool leer(uint8_t registro, uint16_t &valor) {
    Wire.beginTransmission(DIRECCION);
    Wire.write(registro);
    if (Wire.endTransmission(false) != 0) return false;
    if (Wire.requestFrom(DIRECCION, static_cast<uint8_t>(2)) != 2) return false;
    const uint8_t alto = Wire.read(), bajo = Wire.read();
    valor = static_cast<uint16_t>(alto) << 8 | bajo;
    return true;
  }

 public:
  void begin() {
    Wire.begin();
    Wire.setClock(100000);
  }
  // Lee al arrancar y despues cada 30 s. Se llama en cada vuelta de loop().
  void actualizar(uint32_t ahora) {
    if (!pendiente && !transcurrido(ahora, ultima, PERIODO_MS)) return;
    pendiente = false;
    ultima = ahora;
    uint16_t version = 0, soc = 0, vcell = 0;
    if (!leer(REG_VERSION, version) || !esMax1704x(version) ||
        !leer(REG_SOC, soc) || !leer(REG_VCELL, vcell)) {
      porcentajeActual = -1;
      return;
    }
    porcentajeActual = porcentajeMax17048(soc);
    milivoltiosActual = milivoltiosMax17048(vcell);
  }
  bool conocida() const { return porcentajeActual >= 0; }
  int16_t porcentaje() const { return porcentajeActual; }
  uint16_t milivoltios() const { return conocida() ? milivoltiosActual : 0; }
};
}
