#pragma once
#define isfinite(x) __builtin_isfinite(x)
// Only the bounded rounding operation is supplied by the JS test host.
extern "C" long lroundf(float);
