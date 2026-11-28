/*
 * Fichero: string.h
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/firmware/tests/wasm.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

#pragma once
typedef __SIZE_TYPE__ size_t;
/**
 * Diseño lógico: dest: , src: , count: N --> memcpy()
 * Descripción: Describe la operación realizada y su resultado.
 */
inline void* memcpy(void* dest, const void* src, size_t count) {
  auto d = static_cast<unsigned char*>(dest);
  auto s = static_cast<const unsigned char*>(src);
  for (size_t i = 0; i < count; ++i) d[i] = s[i];
  return dest;
}
