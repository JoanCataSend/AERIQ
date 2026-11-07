#pragma once
// Calibracion por monitor serie (115200 baudios, fin de linea "Nueva linea").
// Los ajustes se guardan en la flash interna (LittleFS) y se cargan al arrancar.
//
//   AYUDA            lista de comandos
//   CAL              ajustes actuales
//   CERO             mide el cero durante ~10 min (sensor en aire SIN ozono) y lo guarda
//   CERO=<mV>        fija el cero a mano, p. ej. CERO=-2.35
//   SPAN=<factor>    multiplica las lecturas (comparacion con estacion oficial), p. ej. SPAN=1.12
//   SENS=<nA/ppm>    sensibilidad de la pegatina si se cambia de sensor, p. ej. SENS=-74.04
//   CANCELAR         cancela una medicion de CERO en curso
//   BORRAR           vuelve a los valores de fabrica (cero 0, span 1, -74.04 nA/ppm)
#include <Arduino.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>
#include <stdlib.h>
#include <stddef.h>
#include <math.h>
#include <string.h>
#include <ctype.h>
#include "Calibracion.h"

namespace aeriq {
class Consola {
  struct Guardado {
    uint32_t magia;
    float cero, span, sensibilidad;
    uint32_t control;
  };
  static const char *ruta() { return "/aeriq_cal.bin"; }
  static constexpr uint32_t MAGIA = 0x41455131; // "AEQ1"
  static constexpr uint16_t CICLOS_CERO = 130;   // ~4.5 s por ciclo = ~10 min
  static constexpr uint32_t ESTABILIZACION_MS = 60UL * 60UL * 1000UL;

  char linea[40] = {};
  uint8_t largo = 0;
  bool midiendo = false;
  uint16_t ciclos = 0, descartados = 0;
  double suma = 0, sumaCuadrados = 0;

  static uint32_t control(const Guardado &g) {
    const uint8_t *p = reinterpret_cast<const uint8_t*>(&g);
    uint32_t h = 2166136261u; // FNV-1a
    for (size_t i = 0; i < offsetof(Guardado, control); ++i) { h ^= p[i]; h *= 16777619u; }
    return h;
  }

  void imprimir(const char *texto) { if (Serial) Serial.println(texto); }

  void mostrar(Calibracion &c) {
    if (!Serial) return;
    Serial.print(F("CAL cero=")); Serial.print(c.cero() * 1000.f, 3);
    Serial.print(F(" mV  span=")); Serial.print(c.span(), 3);
    Serial.print(F("  sensibilidad=")); Serial.print(c.sensibilidad(), 2);
    Serial.print(F(" nA/ppm  ganancia=")); Serial.print(GANANCIA_TIA, 0);
    Serial.print(F(" kV/A  pendiente=")); Serial.print(c.sensibilidad() * GANANCIA_TIA * 1.e-3f, 3);
    Serial.println(F(" mV/ppm"));
  }

  bool guardar(Calibracion &c) {
    Guardado g{MAGIA, c.cero(), c.span(), c.sensibilidad(), 0};
    g.control = control(g);
    InternalFS.remove(ruta());
    Adafruit_LittleFS_Namespace::File f(InternalFS);
    if (!f.open(ruta(), Adafruit_LittleFS_Namespace::FILE_O_WRITE)) return false;
    const bool ok = f.write(reinterpret_cast<const uint8_t*>(&g), sizeof(g)) == sizeof(g);
    f.close();
    return ok;
  }

  void ejecutar(Calibracion &c) {
    for (uint8_t i = 0; i < largo; ++i) linea[i] = toupper(static_cast<unsigned char>(linea[i]));
    const char *valor = strchr(linea, '=');
    const float numero = valor ? static_cast<float>(atof(valor + 1)) : 0.f;
    if (!strcmp(linea, "AYUDA") || !strcmp(linea, "?")) {
      imprimir("Comandos: CAL | CERO | CERO=<mV> | SPAN=<factor> | SENS=<nA/ppm> | CANCELAR | BORRAR");
      imprimir("CERO: sensor encendido >24 h, dentro de una bolsa/bote con carbon activo 30-60 min, luego CERO.");
    } else if (!strcmp(linea, "CAL")) {
      mostrar(c);
    } else if (!strcmp(linea, "CERO")) {
      midiendo = true; ciclos = descartados = 0; suma = sumaCuadrados = 0;
      if (millis() < ESTABILIZACION_MS)
        imprimir("AVISO: la placa lleva <60 min encendida; el cero puede no ser estable.");
      imprimir("CERO: midiendo ~10 min. No muevas el sensor ni abras el filtro. CANCELAR para parar.");
    } else if (!strcmp(linea, "CANCELAR")) {
      midiendo = false; imprimir("CERO cancelado; se mantiene el cero anterior.");
    } else if (!strncmp(linea, "CERO=", 5)) {
      if (c.fijarCero(numero / 1000.f) && guardar(c)) { imprimir("Cero guardado."); mostrar(c); }
      else imprimir("ERROR: cero fuera de +-30 mV o no se pudo guardar.");
    } else if (!strncmp(linea, "SPAN=", 5)) {
      if (c.fijarSpan(numero) && guardar(c)) { imprimir("Span guardado."); mostrar(c); }
      else imprimir("ERROR: span debe estar entre 0.3 y 3.");
    } else if (!strncmp(linea, "SENS=", 5)) {
      if (c.fijarSensibilidad(numero) && guardar(c)) { imprimir("Sensibilidad guardada."); mostrar(c); }
      else imprimir("ERROR: sensibilidad no valida (usa el numero de la pegatina, p. ej. -74.04).");
    } else if (!strcmp(linea, "BORRAR")) {
      c.fijarCero(OFFSET_V); c.fijarSpan(1.f); c.fijarSensibilidad(SENSIBILIDAD);
      InternalFS.remove(ruta());
      imprimir("Valores de fabrica restaurados."); mostrar(c);
    } else if (largo) {
      imprimir("Comando desconocido. Escribe AYUDA.");
    }
  }

 public:
  bool midiendoCero() const { return midiendo; }

  void begin(Calibracion &c) {
    if (!InternalFS.begin()) return;
    Adafruit_LittleFS_Namespace::File f(InternalFS);
    if (!f.open(ruta(), Adafruit_LittleFS_Namespace::FILE_O_READ)) return;
    Guardado g{};
    const bool leido = f.read(&g, sizeof(g)) == static_cast<int>(sizeof(g));
    f.close();
    if (!leido || g.magia != MAGIA || g.control != control(g)) return;
    c.fijarSensibilidad(g.sensibilidad);
    c.fijarCero(g.cero);
    c.fijarSpan(g.span);
  }

  // Lee caracteres sin bloquear; ejecuta al recibir fin de linea.
  void atender(Calibracion &c) {
    while (Serial && Serial.available() > 0) {
      const int ch = Serial.read();
      if (ch == '\r' || ch == '\n') {
        linea[largo] = 0;
        if (largo) ejecutar(c);
        largo = 0;
      } else if (largo < sizeof(linea) - 1 && ch != ' ') {
        linea[largo++] = static_cast<char>(ch);
      }
    }
  }

  // Llamar con las tensiones de cada ciclo completo mientras se mide el cero.
  void ciclo(Calibracion &c, float gas, float ref, float term, bool saturado) {
    if (!midiendo) return;
    float v;
    if (saturado || !c.muestraCero(gas, ref, term, v)) {
      if (++descartados > 20) {
        midiendo = false;
        imprimir("ERROR CERO: demasiados ciclos invalidos. Revisa cableado/VREF antes de calibrar.");
      }
      return;
    }
    suma += v; sumaCuadrados += static_cast<double>(v) * v; ++ciclos;
    if (ciclos % 13 == 0 && Serial) {
      Serial.print(F("CERO ")); Serial.print(ciclos * 100 / CICLOS_CERO);
      Serial.print(F("%  media=")); Serial.print(suma / ciclos * 1000.0, 3); Serial.println(F(" mV"));
    }
    if (ciclos < CICLOS_CERO) return;
    midiendo = false;
    const double media = suma / ciclos;
    const double varianza = sumaCuadrados / ciclos - media * media;
    const double desviacion = varianza > 0 ? sqrt(varianza) : 0;
    if (Serial) {
      Serial.print(F("CERO medido=")); Serial.print(media * 1000.0, 3);
      Serial.print(F(" mV  ruido(1 sigma)=")); Serial.print(desviacion * 1000.0, 3);
      Serial.print(F(" mV = ")); Serial.print(fabs(desviacion / c.sensibilidad() / GANANCIA_TIA * 1.e9), 0);
      Serial.println(F(" ppb"));
    }
    if (desviacion > 0.002) {
      imprimir("ERROR CERO: la senal varia demasiado (>2 mV). Espera a que se estabilice y repite.");
    } else if (!c.fijarCero(static_cast<float>(media))) {
      imprimir("ERROR CERO: fuera de +-30 mV. No es un problema de calibracion: revisa cableado/buffer.");
    } else if (!guardar(c)) {
      imprimir("ERROR: cero aplicado pero no guardado en flash.");
    } else {
      imprimir("Cero guardado en flash."); mostrar(c);
    }
  }
};
}
