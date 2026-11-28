/*
 * Fichero: math.h
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/firmware/tests/wasm.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */


#pragma once
#define isfinite(x) __builtin_isfinite(x)
// Only the bounded rounding operation is supplied by the JS test host.
extern "C" long lroundf(float);
/**
 * Gestiona fabsf.
 * Diseño lógico: (float x) → fabsf() → resultado de la operación; según doc/firmware_design.md.
 */
inline float fabsf(float x) { return __builtin_fabsf(x); }
