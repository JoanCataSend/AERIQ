#include <bluefruit.h>
#include <stdio.h>
#include "Medidor.h"
#include "Protocolo.h"
#include "Consola.h"

// Placa: Adafruit nRF52 BSP, SparkFun Pro nRF52840 Mini.
aeriq::Medidor medidor;
aeriq::Consola consola;
aeriq::Medida medida;
uint64_t dispositivo;
uint8_t secuencia = 0;
uint32_t inicioEstado = 0;
enum class Fase { LEER, OZONO, PAUSA, TEMPERATURA, REPOSO, ERROR };
Fase fase = Fase::LEER;
bool radioError = false;
bool medidaDisponible = false;

void diagnosticar() {
  static uint32_t ultimo = 0;
  const uint32_t ahora = millis();
  if (!Serial || !aeriq::transcurrido(ahora, ultimo, 2000)) return;
  char linea[250];
  const float vg = medidor.voltaje(0), vr = medidor.voltaje(1), vt = medidor.voltaje(2);
  // Pista de cableado: si VREF no esta cerca de V+/2 pero otro canal si, los cables estan cruzados.
  const bool refMal = medidaDisponible && (vr < 1.3f || vr > 1.7f);
  const char *pista = !refMal ? "" : (vt > 1.3f && vt < 1.7f) ? " PISTA=cables_cruzados?"
    : (vg > 1.3f && vg < 1.7f && (vr < .2f || vr > 3.f)) ? " PISTA=VREF_suelto?" : " PISTA=VREF_mal";
  const int longitud = snprintf(linea, sizeof(linea),
    "AERIQ id=%08lX%08lX ciclo=%u %s VGAS=%ldmV VREF=%ldmV VTEMP=%ldmV DIF=%lduV O3=%uppb T=%dC O3raw=%ldppb O3filtro=%ldppb estado=0x%04X BLE=%s%s%s\r\n",
    static_cast<unsigned long>(dispositivo >> 32),
    static_cast<unsigned long>(dispositivo), static_cast<unsigned>(secuencia),
    !medidaDisponible ? "SIN_MEDIDA" : ((medida.estado & aeriq::VALIDA) ? "VALIDA" : "INVALIDA"),
    lroundf(vg * 1000.f), lroundf(vr * 1000.f), lroundf(vt * 1000.f),
    lroundf((vg - vr) * 1.e6f),
    static_cast<unsigned>(medida.ozono),
    static_cast<int>(medida.temperatura), lroundf(medida.ozonoInstantaneo),
    lroundf(medida.ozonoFiltrado), static_cast<unsigned>(medida.estado),
    radioError ? "ERROR" : "OK", consola.midiendoCero() ? " MIDIENDO_CERO" : "", pista);
  // No esperar a que el host USB abra el puerto ni a que se vacie el buffer.
  if (longitud > 0 && longitud < static_cast<int>(sizeof(linea)) &&
      Serial.availableForWrite() >= longitud) {
    Serial.write(reinterpret_cast<const uint8_t*>(linea), longitud);
    ultimo = ahora;
  }
}

bool publicar(uint8_t tipo, uint16_t valor) {
  Bluefruit.Advertising.stop();
  Bluefruit.Advertising.clearData();
  Bluefruit.ScanResponse.clearData();
  uint8_t paquete[25], estado[28];
  aeriq::anuncio(tipo, secuencia, valor, paquete);
  aeriq::extension(dispositivo, secuencia, medida.estado, estado);
  const bool datos = Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE)
    && Bluefruit.Advertising.addManufacturerData(paquete, sizeof(paquete))
    && Bluefruit.ScanResponse.addData(0x21, estado, sizeof(estado));
  if (!datos) return false;
  return Bluefruit.Advertising.start(0);
}

void setup() {
  Serial.begin(115200);
  // DEVICEID es el identificador de fabrica, no una direccion BLE ni una clave.
  dispositivo = (static_cast<uint64_t>(NRF_FICR->DEVICEID[1]) << 32)
    | NRF_FICR->DEVICEID[0];
  if (!Bluefruit.begin()) { radioError = true; return; }
  Bluefruit.setName("AERIQ");
  Bluefruit.setTxPower(4);
  Bluefruit.Advertising.setType(BLE_GAP_ADV_TYPE_NONCONNECTABLE_SCANNABLE_UNDIRECTED);
  Bluefruit.Advertising.setInterval(160, 160); // Unidades 0.625 ms: 100 ms.
  medidor.begin();
  consola.begin(medidor.calibrado()); // carga cero/span guardados en flash
  medidor.iniciar();
}

void loop() {
  consola.atender(medidor.calibrado());
  diagnosticar();
  if (radioError) return;
  const uint32_t ahora = millis();
  switch (fase) {
    case Fase::LEER:
      if (medidor.actualizar(medida)) {
        medidaDisponible = true;
        consola.ciclo(medidor.calibrado(), medidor.voltaje(0), medidor.voltaje(1),
                      medidor.voltaje(2), medidor.saturadoUltimoCiclo());
        ++secuencia;
        const bool valida = (medida.estado & aeriq::VALIDA) != 0;
        radioError = !publicar(valida ? aeriq::TIPO_O3 : aeriq::TIPO_ESTADO,
                               valida ? medida.ozono : medida.estado);
        fase = valida ? Fase::OZONO : Fase::ERROR;
        inicioEstado = millis();
      }
      break;
    case Fase::OZONO:
      if (aeriq::transcurrido(ahora, inicioEstado, 1200)) {
        Bluefruit.Advertising.stop(); fase = Fase::PAUSA; inicioEstado = ahora;
      }
      break;
    case Fase::PAUSA:
      if (aeriq::transcurrido(ahora, inicioEstado, 300)) {
        radioError = !publicar(aeriq::TIPO_TEMPERATURA,
                              static_cast<uint16_t>(medida.temperatura));
        fase = Fase::TEMPERATURA; inicioEstado = millis();
      }
      break;
    case Fase::TEMPERATURA:
    case Fase::ERROR:
      if (aeriq::transcurrido(ahora, inicioEstado, 1200)) {
        Bluefruit.Advertising.stop(); fase = Fase::REPOSO; inicioEstado = ahora;
      }
      break;
    case Fase::REPOSO:
      if (aeriq::transcurrido(ahora, inicioEstado, 1500)) {
        medidor.iniciar(); fase = Fase::LEER;
      }
      break;
  }
}
