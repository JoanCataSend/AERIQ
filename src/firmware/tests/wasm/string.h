#pragma once
typedef __SIZE_TYPE__ size_t;
inline void* memcpy(void* dest, const void* src, size_t count) {
  auto d = static_cast<unsigned char*>(dest);
  auto s = static_cast<const unsigned char*>(src);
  for (size_t i = 0; i < count; ++i) d[i] = s[i];
  return dest;
}
