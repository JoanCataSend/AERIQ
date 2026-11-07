import { readFileSync } from 'node:fs';
const path = process.argv[2];
if (!path) throw new Error('Uso: node src/firmware/tests/run.mjs ruta/checks.wasm');
const { instance } = await WebAssembly.instantiate(readFileSync(path), {
  env: { lroundf: x => x < 0 ? -Math.floor(-x + 0.5) : Math.floor(x + 0.5) },
});
const line = instance.exports.run_tests();
if (line) throw new Error(`Fallo en checks.cpp:${line}`);
console.log('OK: conversion, compensacion, filtros, errores, tramas y rollover');
