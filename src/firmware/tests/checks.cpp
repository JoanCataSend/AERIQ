#include "../NodoO3/Calibracion.h"
#include "../NodoO3/Protocolo.h"

// Return the failing line; zero means success. Can also run in a native test host.
#define CHECK(value) do { if (!(value)) return __LINE__; } while (0)

extern "C" int run_tests() {
  using namespace aeriq;
  uint8_t bytes[25];
  anuncio(TIPO_O3, 7, 123, bytes);
  const uint8_t esperado[25] = {
    0x4c,0,2,0x15,0x45,0x50,0x53,0x47,0x2d,0x47,0x54,0x49,
    0x2d,0x50,0x52,0x4f,0x59,0x2d,0x33,0x41,14,7,0,123,0xcb
  };
  for (int i = 0; i < 25; ++i) CHECK(bytes[i] == esperado[i]);
  anuncio(TIPO_TEMPERATURA, 255, static_cast<uint16_t>(-12), bytes);
  CHECK(bytes[20] == 12 && bytes[21] == 255 && bytes[22] == 255 && bytes[23] == 244);
  uint8_t extra[28];
  extension(0x0123456789abcdefULL, 7, VALIDA | NEGATIVO_LIMITADO, extra);
  const uint8_t payload[12] = {1,7,1,0x23,0x45,0x67,0x89,0xab,0xcd,0xef,0,3};
  for (int i = 0; i < 12; ++i) CHECK(extra[16+i] == payload[i]);
  CHECK(extra[0] == 1 && extra[15] == 0xe3);
  CHECK(transcurrido(0x10, 0xfffffff0, 32));
  CHECK(!transcurrido(0x0f, 0xfffffff0, 32));
  CHECK(!transcurrido(1199, 0, 1200));
  CHECK(transcurrido(1200, 0, 1200));

  // T=20 C at Vplus=3.3 V. 3.694596 mV below Vref corresponds to 100 ppb.
  const float ref = 1.65f, term20 = 38.f * 3.3f / 87.f;
  Calibracion c;
  Medida m = c.convertir(ref - .003694596f, ref, term20, false);
  CHECK(m.ozono == 100 && m.temperatura == 20 && m.estado == VALIDA);
  // A single huge outlier is rejected by the median once the window is full.
  for (int i = 0; i < 4; ++i) c.convertir(ref - .003694596f, ref, term20, false);
  m = c.convertir(ref - .3694596f, ref, term20, false);
  CHECK(m.ozono == 100);
  m = c.convertir(ref, 0, term20, false);
  CHECK(!(m.estado & VALIDA) && (m.estado & REFERENCIA_INVALIDA));
  // An invalid cycle resets the filter, so a new zero isn't a stale 100 ppb.
  m = c.convertir(ref, ref, term20, false);
  CHECK(m.ozono == 0 && m.estado == VALIDA);
  m = c.convertir(ref, ref, term20, true);
  CHECK(!(m.estado & VALIDA) && (m.estado & ADC_SATURADO));
  Calibracion negativo;
  m = negativo.convertir(ref + .003694596f, ref, term20, false);
  CHECK(m.ozono == 0 && (m.estado & NEGATIVO_LIMITADO));
  Calibracion alto;
  m = alto.convertir(ref - 1.f, ref, term20, false);
  CHECK(m.ozono == 20000 && (m.estado & MAXIMO_LIMITADO));
  Calibracion caliente;
  // At 40 C: 0.132 ppm raw - 0.066 ppm zero, divided by span 1.06 => 62 ppb.
  m = caliente.convertir(ref - .00487686672f, ref, 58.f * 3.3f / 87.f, false);
  CHECK(m.temperatura == 40 && m.ozono == 62);
  Calibracion extremo;
  m = extremo.convertir(ref, ref, 78.f * 3.3f / 87.f, false);
  CHECK(m.temperatura == 60 && (m.estado & TEMPERATURA_FUERA_RANGO));
  Calibracion noFinito;
  m = noFinito.convertir(__builtin_nanf(""), ref, term20, false);
  CHECK(!(m.estado & VALIDA) && (m.estado & CALCULO_INVALIDO));
  Calibracion frio;
  m = frio.convertir(ref, ref, 6.f * 3.3f / 87.f, false);
  CHECK(m.temperatura == -12);
  Calibracion arranque;
  arranque.convertir(ref - .003694596f, ref, term20, false);
  m = arranque.convertir(ref - .011083788f, ref, term20, false);
  CHECK(m.ozono == 125); // Mediana par 200, EMA 0.25*200 + 0.75*100.
  return 0;
}
