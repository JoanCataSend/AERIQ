'use strict';
const assert=require('node:assert/strict');
const domain=require('../../src/web/aeriq-domain.js');
const base={campaignId:1,unidad:'ppb',simulado:false,umbral_medio:60,umbral_alto:100};
const points=[
  {...base,latitud:38.99665,longitud:-.16600,valor:50},
  {...base,latitud:38.99670,longitud:-.16600,valor:70},
  {...base,latitud:38.99665,longitud:-.16594,valor:90}
];
assert.equal(domain.interpolate([38.99666,-.16599],points.slice(0,2),30),null);
assert.equal(domain.interpolate([39,-.16],points,30),null);
assert.equal(domain.interpolate([38.99666,-.16599],[points[0],points[0],points[0]],30),null);
assert.equal(domain.interpolate([38.99666,-.16599],[...points.slice(0,2),{...points[2],simulado:true}],30),null);
assert.equal(domain.interpolate([38.99666,-.16599],[...points.slice(0,2),{...points[2],unidad:'µg/m³'}],30),null);
assert.equal(domain.interpolate([38.99666,-.16599],points,51),null);
const v=domain.interpolate([38.99666,-.16599],points,30);assert(v>=50&&v<=90);
const rows=[{...base,valor:50,capturada_en:'2026-11-02 12:00:00.000'}, {...base,valor:100,capturada_en:'2026-11-03 12:00:00.000'}];
const stats=domain.statistics(rows);assert.equal(stats.min,50);assert.equal(stats.max,100);assert.equal(stats.mean,75);assert.equal(stats.exceedances.length,1);assert.equal(stats.series.length,2);
assert.equal(domain.statistics(rows,'week').series.length,1);
const csv=domain.csv([{...rows[0],dispositivo:'=HYPERLINK("bad")',zona:'Zona; "norte"'}]);
assert(csv.includes('"\'=HYPERLINK(""bad"")"'));assert(csv.includes('"Zona; ""norte"""'));
assert(domain.csv([{longitud:'-0.166'}]).includes('"-0.166"'));
assert.equal(domain.level(100,60,100),'alto');assert.equal(domain.level(60,60,100),'moderado');
console.log('AERIQ: interpolación sin cobertura/muestras mixtas, estadísticas, series y CSV correctos.');
