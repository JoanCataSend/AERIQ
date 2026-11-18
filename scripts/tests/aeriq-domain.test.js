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

// ---- T25: validación con casos densos, dispersos y simulados ----
// Los mismos casos y valores esperados están en PublicInterpolationTest.java: si cambias uno, cambia el otro.
const near=(a,b)=>assert(Math.abs(a-b)<1e-6,`${a} ≠ ${b}`);
const P=domain.INTERPOLACION;
assert.deepEqual([P.radioMin,P.radioMax,P.radio,P.minPosiciones,P.separacionMin,P.potencia,P.distanciaMin,P.celda],[20,50,30,3,5,2,1,10]);
assert(Object.isFrozen(P));
const stamp=m=>`2026-11-10 ${String(12+Math.floor(m/60)).padStart(2,'0')}:${String(m%60).padStart(2,'0')}:00.000`;
// Rejilla de evaluación de 18 × 21 celdas de ~10 m alrededor del campus (centros de celda).
const LAT0=38.9960,LON0=-0.1670,LAT_STEP=0.00009,LON_STEP=0.0001156,ROWS=18,COLS=21;
function sweep(rows,radius,options){
  const s=domain.support(rows,options),c={valor:0,insuficiente:0,lejos:0,mezcla:0,radio:0,min:Infinity,max:-Infinity,sum:0};
  for(let i=0;i<ROWS;i++)for(let j=0;j<COLS;j++){
    const e=domain.estimate([LAT0+(i+.5)*LAT_STEP,LON0+(j+.5)*LON_STEP],s,radius,options);
    if(e.motivo){c[e.motivo]++;assert.equal(e.valor,null);continue;}
    // Nunca se colorea sin tres posiciones dentro del radio.
    assert(e.posiciones>=(options?.minPosiciones??3)&&e.cercana<=radius);
    c.valor++;c.min=Math.min(c.min,e.valor);c.max=Math.max(c.max,e.valor);c.sum+=e.valor;
  }
  return c;
}
// Denso: la rejilla 5 × 3 de la simulación (src/database/reinicio/03-simulacion.sql), posiciones a ~11 m.
const O3=[38,42,47,51,56,61,66,72,78,84,91,97,103,108,114],dense=[];
for(let i=0;i<3;i++)for(let j=0;j<5;j++)dense.push({...base,latitud:38.996651+i*0.0001,longitud:-0.166009+j*0.00013,valor:O3[i*5+j],capturada_en:stamp((i*5+j)*20)});
assert.equal(domain.support(dense).length,15);
let d=sweep(dense,30);
assert.deepEqual([d.valor,d.insuficiente,d.lejos,d.mezcla,d.radio],[53,23,302,0,0]);
near(d.sum,3957.222753625807);near(d.min,42.876886387974906);near(d.max,109.04794135160219);
assert(d.min>=38&&d.max<=114,'IDW no sale del rango de las muestras');
// El radio es configurable: la cobertura crece con él y nunca se extiende más allá.
assert.deepEqual([20,30,40,50].map(r=>sweep(dense,r).valor),[30,53,87,126]);
const support=domain.support(dense);
let e=domain.estimate([38.9967,-0.1658],support,30);near(e.valor,62.25689582591126);assert.equal(e.posiciones,14);assert.equal(e.motivo,null);
e=domain.estimate([38.997,-0.16575],support,30);near(e.valor,95.27058686717591);assert.equal(e.posiciones,8);
e=domain.estimate([38.9972,-0.16575],support,30);assert.equal(e.valor,null);assert.equal(e.motivo,'lejos');near(e.cercana,38.80712562013137);
// Sobre una muestra pesa sobre todo esa muestra, sin exigir igualdad.
e=domain.estimate([38.996651,-0.166009],support,30);near(e.valor,38.58790393896943);assert.equal(e.posiciones,8);
// Disperso: tres puntos a ~90 m no se unen con ningún radio admitido.
const A=[38.9966,-0.1662];
const sparse=[{...base,latitud:A[0],longitud:A[1],valor:50},{...base,latitud:A[0]+0.00081,longitud:A[1],valor:70},{...base,latitud:A[0],longitud:A[1]+0.00104,valor:90}];
for(const r of [20,30,40,50]){d=sweep(sparse,r);assert.equal(d.valor,0);assert(d.insuficiente>0&&d.lejos>0);}
assert.equal(domain.estimate(A,domain.support(sparse),50).motivo,'insuficiente');
// Dos posiciones próximas tampoco bastan.
const pair=[sparse[0],{...base,latitud:A[0]+0.0001,longitud:A[1],valor:70}];
for(const r of [30,50]){d=sweep(pair,r);assert.equal(d.valor,0);assert(d.insuficiente>0);}
// Deriva del GPS: tres lecturas a menos de 5 m son una sola posición (la más reciente).
const jitter=[{...base,latitud:A[0],longitud:A[1],valor:50,capturada_en:stamp(0)},{...base,latitud:A[0]+0.00001,longitud:A[1],valor:70,capturada_en:stamp(1)},{...base,latitud:A[0],longitud:A[1]+0.00002,valor:90,capturada_en:stamp(2)}];
assert.deepEqual(domain.support(jitter).map(r=>r.valor),[90]);
for(const r of [30,50])assert.equal(sweep(jitter,r).valor,0);
assert.equal(domain.interpolate(A,jitter,50),null);
// separacionMin es un parámetro: con 1 m esas tres lecturas sí cuentan como posiciones distintas.
assert(domain.interpolate(A,jitter,30,{separacionMin:1})!==null);
// Tres puntos a ~40 m: sin datos con 20 m; la zona estimada aparece y crece al ampliar el radio.
const tri=[{...base,latitud:A[0],longitud:A[1],valor:50},{...base,latitud:A[0]+0.00036,longitud:A[1],valor:70},{...base,latitud:A[0]+0.00018,longitud:A[1]+0.0004,valor:90}];
assert.deepEqual([20,30,40,50].map(r=>sweep(tri,r).valor),[0,3,11,27]);
// Simulado: se interpola igual que lo real, pero nunca mezclado con ello.
const simulated=dense.map(r=>({...r,simulado:true}));
near(sweep(simulated,30).sum,3957.222753625807);
const mixed=[...simulated.slice(0,14),{...simulated[14],simulado:false}];
assert.equal(domain.estimate([38.99682,-0.16552],domain.support(mixed),30).motivo,'mezcla');
assert.equal(domain.estimate([38.99682,-0.16552],domain.support([...dense.slice(0,14),{...dense[14],unidad:'µg/m³'}]),30).motivo,'mezcla');
assert.equal(domain.estimate([38.99682,-0.16552],domain.support([...dense.slice(0,14),{...dense[14],campaignId:2}]),30).motivo,'mezcla');
// Límites del radio: 20 y 50 valen; 19, 51 y valores no numéricos no estiman nada.
for(const r of [19,51,NaN,undefined,0])assert.equal(domain.estimate([38.9967,-0.1658],support,r).motivo,'radio');
for(const r of [20,50])assert.equal(domain.estimate([38.9967,-0.1658],support,r).motivo,null);
// Lecturas repetidas en una posición: cuenta la más reciente, sea cual sea el orden de entrada.
const rep=[{...base,latitud:A[0],longitud:A[1],valor:10,capturada_en:stamp(0)},{...base,latitud:A[0],longitud:A[1],valor:99,capturada_en:stamp(30)},{...base,latitud:A[0]+0.0001,longitud:A[1],valor:70,capturada_en:stamp(5)},{...base,latitud:A[0],longitud:A[1]+0.00013,valor:90,capturada_en:stamp(6)}];
const query=[A[0]+0.00003,A[1]+0.00003];
for(const rows of [rep,[rep[1],rep[3],rep[0],rep[2]],[...rep].reverse()])near(domain.interpolate(query,rows,30),92.50890267193577);
// Consulta de un punto: explica el valor estimado o por qué no hay nivel (mismos textos que en Android).
const info={unidad:'ppb',umbral_medio:60,umbral_alto:100};
assert.deepEqual(domain.describe(domain.estimate([38.9967,-0.1658],support,30),30,{...info,simulado:true}),{titulo:'Estimación: 62 ppb · moderado',detalle:'14 posiciones en 30 m · la más cercana a 7 m. Valor interpolado, no medido · datos simulados.'});
assert.deepEqual(domain.describe(domain.estimate([38.9972,-0.16575],support,30),30,info),{titulo:'Sin datos',detalle:'La medición más cercana está a 39 m (radio de 30 m). Sin datos no significa aire limpio.'});
assert.deepEqual(domain.describe(domain.estimate(A,domain.support(sparse.slice(0,1)),30),30,info),{titulo:'Datos insuficientes',detalle:'1 posición en 30 m; hacen falta 3. No se asigna nivel de ozono.'});
assert.equal(domain.describe(domain.estimate(A,[],30),30,info).detalle,'No hay mediciones publicadas. Sin datos no significa aire limpio.');
// Sin coordenadas no hay posición; sin lecturas no hay nada que estimar.
assert.equal(domain.support([{...base,latitud:null,longitud:null,valor:50}]).length,0);
assert.equal(domain.estimate(A,[],30).motivo,'lejos');assert.equal(domain.estimate(A,[],30).cercana,null);
// ---- Rejilla de pintado (zonas redondas): raster() da en cada punto lo mismo que estimate() ----
// Los mismos casos y recuentos están en PublicInterpolationTest.java.
const lats=Array.from({length:ROWS},(_,i)=>LAT0+(i+.5)*LAT_STEP),lngs=Array.from({length:COLS},(_,j)=>LON0+(j+.5)*LON_STEP);
const Z=domain.ZONA,group=(rows,radius)=>({samples:domain.support(rows),radius,umbral_medio:60,umbral_alto:100});
const tally=g=>{const t=[0,0,0,0,0];for(const s of g.estados)t[s]++;return t;};
let g=domain.raster([group(dense,30)],lats,lngs);
assert.deepEqual([g.filas,g.columnas,g.recortado],[ROWS,COLS,false]);
// 53 con valor (bajo + moderado + alto) y 23 insuficientes, como el barrido punto a punto.
assert.deepEqual(tally(g),[302,23,17,28,8]);
for(let i=0;i<ROWS;i++)for(let j=0;j<COLS;j++){
  const e=domain.estimate([lats[i],lngs[j]],domain.support(dense),30),s=g.estados[i*COLS+j];
  assert.equal(s,e.valor!==null?Z[domain.level(e.valor,60,100)]:e.motivo==='insuficiente'?Z.insuficiente:Z.sinDatos);
}
// Solo se evalúan los puntos cercanos a alguna posición, no toda la rejilla.
assert(g.evaluadas<ROWS*COLS);
assert.deepEqual([20,30,40,50].map(r=>{const t=tally(domain.raster([group(dense,r)],lats,lngs));return t[2]+t[3]+t[4];}),[30,53,87,126]);
assert.deepEqual([20,30,40,50].map(r=>{const t=tally(domain.raster([group(tri,r)],lats,lngs));return t[2]+t[3]+t[4];}),[0,3,11,27]);
// La rejilla puede ir de arriba abajo (latitud decreciente, como la pantalla): mismo resultado.
assert.deepEqual(tally(domain.raster([group(dense,30)],[...lats].reverse(),lngs)),[302,23,17,28,8]);
// Un radio fuera de 20–50 m no pinta nada; sin grupos tampoco.
assert.deepEqual(tally(domain.raster([group(dense,51)],lats,lngs)),[ROWS*COLS,0,0,0,0]);
assert.equal(domain.raster([],lats,lngs).evaluadas,0);
// Dos grupos que se tocan: donde uno estima y el otro no basta, gana el valor estimado.
const other=dense.slice(0,1).map(r=>({...r,campaignId:2}));
assert.deepEqual(tally(domain.raster([group(other,30),group(dense,30)],lats,lngs)),tally(g));
// Tope de evaluaciones: se marca recortado para avisarlo en el mapa.
g=domain.raster([group(dense,30)],lats,lngs,{maxEvaluaciones:10});assert.deepEqual([g.evaluadas,g.recortado],[10,true]);
// ---- Ruta de un Orb: orden de captura y tramos partidos por huecos de más de 10 minutos ----
const at=(id,t,lat=A[0])=>({id,latitud:lat,longitud:A[1],capturada_en:`2026-11-10 ${t}:00.000`});
const legs=domain.route([at(3,'12:09'),at(1,'12:00'),at(2,'12:05'),at(4,'12:30'),{...at(5,'12:31'),latitud:null},at(6,'12:40')]);
assert.deepEqual(legs.map(l=>l.map(r=>r.id)),[[1,2,3],[4,6]]);
assert.deepEqual(domain.route([at(1,'12:00'),at(2,'12:30')],60).map(l=>l.length),[2]);
assert.deepEqual(domain.route([]),[]);
const rows=[{...base,valor:50,capturada_en:'2026-11-02 12:00:00.000'}, {...base,valor:100,capturada_en:'2026-11-03 12:00:00.000'}];
const stats=domain.statistics(rows);assert.equal(stats.min,50);assert.equal(stats.max,100);assert.equal(stats.mean,75);assert.equal(stats.exceedances.length,1);assert.equal(stats.series.length,2);
const thermal=domain.statistics(rows.map((row,i)=>({...row,unidad:'°C',valor:i?100:-12})));
assert.equal(thermal.min,-12);assert.equal(thermal.mean,44);assert.equal(thermal.exceedances.length,0);
assert.equal(domain.statistics(rows,'week').series.length,1);
const csv=domain.csv([{...rows[0],dispositivo:'=HYPERLINK("bad")',zona:'Zona; "norte"'}]);
assert(csv.includes('"\'=HYPERLINK(""bad"")"'));assert(csv.includes('"Zona; ""norte"""'));
assert(domain.csv([{longitud:'-0.166'}]).includes('"-0.166"'));
assert.equal(domain.level(100,60,100),'alto');assert.equal(domain.level(60,60,100),'moderado');
assert.equal(domain.battery(null),'Sin datos');assert.equal(domain.battery(61),'61 % · alta');assert.equal(domain.battery(60),'60 % · media');
assert.equal(domain.battery(20),'20 % · media');assert.equal(domain.battery(19,'2026-11-11 10:05:00.000'),'19 % · baja · 2026-11-11 10:05 UTC');assert.equal(domain.battery(0),'0 % · baja');
console.log('AERIQ: interpolación acotada (casos densos, dispersos, simulados, límites de radio y deriva GPS), estadísticas, series, CSV y batería correctos.');
