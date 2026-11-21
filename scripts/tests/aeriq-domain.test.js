'use strict';
process.env.TZ='UTC'; // fechas deterministas: la web muestra la hora local
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
assert.deepEqual([P.radioMin,P.radioMax,P.radio,P.minPosiciones,P.separacionMin,P.potencia,P.distanciaMin,P.celda,P.zonaPropia,P.zonaPropiaMax],[20,50,30,3,5,3,1,10,.5,15]);
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
// Denso: la rejilla 5 × 3 de la simulación (src/database/2-simulacion.sql), posiciones a ~11 m.
const O3=[38,42,47,51,56,61,66,72,78,84,91,97,103,108,114],dense=[];
for(let i=0;i<3;i++)for(let j=0;j<5;j++)dense.push({...base,latitud:38.996651+i*0.0001,longitud:-0.166009+j*0.00013,valor:O3[i*5+j],capturada_en:stamp((i*5+j)*20)});
assert.equal(domain.support(dense).length,15);
let d=sweep(dense,30);
assert.deepEqual([d.valor,d.insuficiente,d.lejos,d.mezcla,d.radio],[53,23,302,0,0]);
near(d.sum,3932.380126722633);near(d.min,38);near(d.max,114);
assert(d.min>=38&&d.max<=114,'IDW no sale del rango de las muestras');
// El radio es configurable: la cobertura crece con él y nunca se extiende más allá.
assert.deepEqual([20,30,40,50].map(r=>sweep(dense,r).valor),[30,53,87,126]);
const support=domain.support(dense);
let e=domain.estimate([38.9967,-0.1658],support,30);near(e.valor,59.6380563517204);assert.equal(e.posiciones,14);assert.equal(e.motivo,null);
e=domain.estimate([38.997,-0.16575],support,30);near(e.valor,97.18262845667287);assert.equal(e.posiciones,8);
e=domain.estimate([38.9972,-0.16575],support,30);assert.equal(e.valor,null);assert.equal(e.motivo,'lejos');near(e.cercana,38.80712562013137);
// Sobre una muestra (y en su zona propia, T46) manda el valor medido en ella.
e=domain.estimate([38.996651,-0.166009],support,30);near(e.valor,38);assert.equal(e.propia,true);assert.equal(e.posiciones,8);
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
near(sweep(simulated,30).sum,3932.380126722633);
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
for(const rows of [rep,[rep[1],rep[3],rep[0],rep[2]],[...rep].reverse()])near(domain.interpolate(query,rows,30),99);
// Consulta de un punto: explica el valor estimado o por qué no hay nivel (mismos textos que en Android).
const info={unidad:'ppb',umbral_medio:60,umbral_alto:100};
assert.deepEqual(domain.describe(domain.estimate([38.9967,-0.1658],support,30),30,{...info,simulado:true}),{titulo:'Estimación: 59,6 ppb · bajo',detalle:'14 posiciones en 30 m · la más cercana a 7 m. Valor interpolado, no medido · datos simulados.'});
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
assert.deepEqual(tally(g),[302,23,21,21,11]);
for(let i=0;i<ROWS;i++)for(let j=0;j<COLS;j++){
  const e=domain.estimate([lats[i],lngs[j]],domain.support(dense),30),s=g.estados[i*COLS+j];
  assert.equal(s,e.valor!==null?Z[domain.level(e.valor,60,100)]:e.motivo==='insuficiente'?Z.insuficiente:Z.sinDatos);
}
// Solo se evalúan los puntos cercanos a alguna posición, no toda la rejilla.
assert(g.evaluadas<ROWS*COLS);
assert.deepEqual([20,30,40,50].map(r=>{const t=tally(domain.raster([group(dense,r)],lats,lngs));return t[2]+t[3]+t[4];}),[30,53,87,126]);
assert.deepEqual([20,30,40,50].map(r=>{const t=tally(domain.raster([group(tri,r)],lats,lngs));return t[2]+t[3]+t[4];}),[0,3,11,27]);
// La rejilla puede ir de arriba abajo (latitud decreciente, como la pantalla): mismo resultado.
assert.deepEqual(tally(domain.raster([group(dense,30)],[...lats].reverse(),lngs)),[302,23,21,21,11]);
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
assert.equal(domain.battery(20),'20 % · media');assert.equal(domain.battery(19,'2026-11-11 10:05:00.000'),'19 % · baja · 11 nov 2026, 10:05');assert.equal(domain.battery(0),'0 % · baja');
// ---- T46: el color junto a cada punto coincide con su medición.
{
  const fine=(rows,step=0.000009)=>{ // rejilla de ~1 m alrededor de las posiciones
    const la=rows.map(r=>r.latitud),lo=rows.map(r=>r.longitud);
    const lats=[],lngs=[];for(let v=Math.min(...la)-0.0004;v<=Math.max(...la)+0.0004;v+=step)lats.push(v);for(let v=Math.min(...lo)-0.0005;v<=Math.max(...lo)+0.0005;v+=step*1.28)lngs.push(v);
    return {lats,lngs};
  };
  const levelAt=(grid,lats,lngs,lat,lng)=>{let i=0,j=0;for(let k=1;k<lats.length;k++)if(Math.abs(lats[k]-lat)<Math.abs(lats[i]-lat))i=k;for(let k=1;k<lngs.length;k++)if(Math.abs(lngs[k]-lng)<Math.abs(lngs[j]-lng))j=k;return grid.estados[i*lngs.length+j];};
  const zoneOf=v=>v>=100?Z.alto:v>=60?Z.moderado:Z.bajo;
  // Punto rojo (110) rodeado de seis vecinos amarillos (70) a ~11 m: alrededor del rojo no queda zona amarilla.
  const center={...base,latitud:38.9966,longitud:-0.1662,valor:110,capturada_en:stamp(0)};
  const ring=[center];for(let k=0;k<6;k++){const a=k*Math.PI/3;ring.push({...base,latitud:38.9966+0.0001*Math.sin(a),longitud:-0.1662+0.000128*Math.cos(a),valor:70,capturada_en:stamp(k+1)});}
  const own=domain.ownRadii(domain.support(ring));
  const r110=own.get(domain.support(ring).find(r=>r.valor===110));assert(r110>5&&r110<6,'zona propia = mitad de la distancia a la vecina (~11 m)');
  const {lats,lngs}=fine(ring),grid=domain.raster([group(ring,30)],lats,lngs);
  for(const [dlat,dlng] of [[0,0],[0.00003,0],[-0.00003,0],[0,0.00004],[0,-0.00004],[0.00002,0.00003]])
    assert.equal(levelAt(grid,lats,lngs,center.latitud+dlat,center.longitud+dlng),Z.alto,'zona roja alrededor del punto rojo');
  for(const r of ring)assert.equal(levelAt(grid,lats,lngs,r.latitud,r.longitud),zoneOf(r.valor),'cada punto conserva su color');
  // Con los tres perfiles simulados de 2-simulacion.sql (bueno, regular y malo), en cada punto la zona tiene su nivel.
  const PERFILES={bueno:[24,22,21,20,20,22,26,30,34,38,41,44,47,49,51,52,50,46,41,36,32,29,27,25],
    regular:[38,36,35,34,35,37,45,52,60,66,70,74,79,84,87,86,82,76,70,66,62,58,54,48],
    malo:[66,62,60,58,61,65,84,94,102,110,116,121,126,131,134,129,120,112,104,98,92,86,78,72]};
  for(const [name,values] of Object.entries(PERFILES)){
    const rows=values.map((v,k)=>({...base,latitud:38.9966+Math.floor(k/6)*0.0001,longitud:-0.1662+(k%6)*0.00013,valor:v,capturada_en:stamp(k)}));
    const box=fine(rows),g=domain.raster([group(rows,30)],box.lats,box.lngs);
    for(const r of rows)assert.equal(levelAt(g,box.lats,box.lngs,r.latitud,r.longitud),zoneOf(r.valor),`perfil ${name}: punto de ${r.valor} ppb`);
    if(name==='bueno')assert(!g.estados.some(x=>x===Z.moderado||x===Z.alto),'el perfil bueno no tiene zonas amarillas ni rojas');
    if(name==='malo')assert(g.estados.some(x=>x===Z.alto),'el perfil malo tiene zonas rojas');
  }
}
// ---- T47: donde se solapan dos campañas no gana la primera: la zona común se interpola con las dos.
{
  const left=[],right=[];
  for(let k=0;k<4;k++){left.push({...base,campaignId:1,latitud:38.9966+(k%2)*0.0001,longitud:-0.1662+Math.floor(k/2)*0.00013,valor:40,capturada_en:stamp(k)});
    right.push({...base,campaignId:2,latitud:38.9966+(k%2)*0.0001,longitud:-0.1662+0.00026+Math.floor(k/2)*0.00013,valor:105,capturada_en:stamp(k)});}
  const lats=[],lngs=[];for(let i=0;i<40;i++)lats.push(38.99645+i*0.00001);for(let j=0;j<80;j++)lngs.push(-0.16635+j*0.0000128);
  const ab=domain.raster([group(left,30),group(right,30)],lats,lngs),ba=domain.raster([group(right,30),group(left,30)],lats,lngs);
  assert.deepEqual([...ab.estados],[...ba.estados],'el resultado no depende del orden de las campañas');
  // Hay puntos de la zona común con nivel moderado: ni el bajo de una ni el alto de la otra (antes ganaba la primera).
  assert(ab.estados.some(x=>x===Z.moderado),'la zona común muestra un valor intermedio');
  assert(ab.estados.some(x=>x===Z.bajo)&&ab.estados.some(x=>x===Z.alto));
}
console.log('AERIQ: interpolación acotada (casos densos, dispersos, simulados, límites de radio y deriva GPS), estadísticas, series, CSV y batería correctos.');
assert.deepEqual(domain.sharedThresholds([{umbral_medio:'60',umbral_alto:'100'},{umbral_medio:60,umbral_alto:100}]),{medium:60,high:100});
assert.equal(domain.sharedThresholds([{umbral_medio:60,umbral_alto:100},{umbral_medio:70,umbral_alto:100}]),null);
assert.equal(domain.sharedThresholds([{umbral_medio:null,umbral_alto:null}]),null);assert.equal(domain.sharedThresholds([]),null);
console.log('AERIQ: umbrales comunes del informe correctos.');
// ---- Informe (T39-T41): agrupación automática, picos y veredicto por franjas.
{
  const at=(h,m=5)=>`2026-11-20 ${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:00.000`;
  const MALO=[66,62,60,58,61,65,84,94,102,110,116,121,126,131,134,129,120,112,104,98,92,86,78,72];
  const hourly=MALO.map((v,h)=>({id:h,valor:v,unidad:'ppb',capturada_en:at(h),umbral_medio:60,umbral_alto:100,dispositivo:'AERIQ'}));
  // T39: dos días o menos se agrupan por horas (la gráfica no sale con un solo punto); hasta 60 días, por días; más, por semanas.
  assert.equal(domain.autoGroup(hourly),'hour');assert.equal(domain.statistics(hourly,'hour').series.length,24);
  assert.equal(domain.statistics(hourly,'hour').series[13].date,'2026-11-20 13:00:00');
  assert.equal(domain.autoGroup([{capturada_en:'2026-11-01 10:00:00'},{capturada_en:'2026-11-20 10:00:00'}]),'day');
  assert.equal(domain.autoGroup([{capturada_en:'2026-01-01 10:00:00'},{capturada_en:'2026-11-20 10:00:00'}]),'week');
  // T40: con una lectura por hora, cada tramo sobre el umbral es un pico (no uno por lectura). Aquí hay dos:
  // 66 ppb de madrugada (moderado) y, tras bajar a 58, el de la tarde: 134 ppb a las 14 h (alto).
  const p=domain.peaks(hourly);
  assert.deepEqual(p.map(x=>[x.capturada_en,x.valor,x.nivel,x.lecturas]),[[at(0),66,'moderado',3],[at(14),134,'alto',20]]);
  // Lecturas seguidas cada pocos segundos: un hueco de más de 30 min corta el tramo.
  const dense=[[0,70],[1,80],[2,75],[40,90],[41,50]].map(([m,v],i)=>({id:i,valor:v,unidad:'ppb',capturada_en:at(10,m),umbral_medio:60,umbral_alto:100}));
  assert.deepEqual(domain.peaks(dense).map(x=>[x.valor,x.lecturas]),[[80,3],[90,1]]);
  // Sin lecturas sobre el umbral no hay picos; la temperatura nunca tiene.
  assert.deepEqual(domain.peaks(hourly.map(r=>({...r,valor:30}))),[]);
  assert.deepEqual(domain.peaks([{valor:40,unidad:'°C',capturada_en:at(1),umbral_medio:null,umbral_alto:null}]),[]);
  // T41: franjas de 6 h con la media frente a los umbrales de la campaña.
  const v=domain.verdict(hourly,{hour:r=>Number(r.capturada_en.slice(11,13))});
  assert.deepEqual(v.franjas.map(f=>[f.codigo,f.lecturas,Math.round(f.media),f.calidad]),[['madrugada',6,62,'regular'],['manana',6,105,'mala'],['tarde',6,125,'mala'],['noche',6,88,'regular']]);
  assert.equal(v.texto,'La calidad del aire es regular de madrugada y por la noche (media 62 / 88 ppb) y mala por la mañana y por la tarde (media 105 / 125 ppb). Evita la actividad física intensa al aire libre en las franjas malas.');
  const good=domain.verdict(hourly.slice(0,12).map(r=>({...r,valor:30})),{hour:r=>Number(r.capturada_en.slice(11,13))});
  assert.deepEqual(good.franjas.map(f=>f.calidad),['buena','buena',null,null]);assert(good.texto.includes('Sin lecturas: tarde y noche.'));
  // Umbrales distintos en una franja (varias campañas): manda el nivel más repetido.
  const mixed=domain.verdict([{valor:70,unidad:'ppb',capturada_en:at(1),umbral_medio:60,umbral_alto:100},{valor:70,unidad:'ppb',capturada_en:at(2),umbral_medio:80,umbral_alto:120},{valor:90,unidad:'ppb',capturada_en:at(3),umbral_medio:80,umbral_alto:120}],{hour:r=>Number(r.capturada_en.slice(11,13))});
  assert.equal(mixed.franjas[0].calidad,'regular');
  assert.equal(domain.verdict([{valor:20,unidad:'°C',capturada_en:at(1),umbral_medio:null,umbral_alto:null}]).franjas.every(f=>f.calidad===null),true);
}
console.log('AERIQ: informe por horas, picos y veredicto por franjas correctos.');
