#pragma once
#define isfinite(x) __builtin_isfinite(x)
// Only the bounded rounding operation is supplied by the JS test host.
extern "C" long lroundf(float);
inline float fabsf(float x) { return __builtin_fabsf(x); }
