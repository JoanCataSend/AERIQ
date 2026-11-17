/* T25 · Prueba DOM del mapa: zonas estimadas, datos insuficientes, sin datos, radio configurable y consulta de un punto.
   Ejecuta public-map.js en jsdom con un doble de Leaflet que registra lo pintado. No renderiza píxeles. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {JSDOM,VirtualConsole}=require(path.resolve(process.argv[2]||'.local-backups/test-tools/node_modules/jsdom'));
const root=path.resolve(__dirname,'../../src/web'),domain=require(path.join(root,'aeriq-domain.js'));
const wait=()=>new Promise(r=>setTimeout(r,35));
const base={campaignId:1,unidad:'ppb',simulado:true,zona:'Campus',radio_m:30,umbral_medio:60,umbral_alto:100};
const stamp=m=>`2026-11-10 ${String(12+Math.floor(m/60)).padStart(2,'0')}:${String(m%60).padStart(2,'0')}:00.000`;
// Caso denso: la rejilla 5 × 3 de la simulación. Caso disperso: un punto aislado a ~170 m.
const O3=[38,42,47,51,56,61,66,72,78,84,91,97,103,108,114],dense=[];
for(let i=0;i<3;i++)for(let j=0;j<5;j++)dense.push({...base,id:i*5+j+1,latitud:38.996651+i*0.0001,longitud:-0.166009+j*0.00013,valor:O3[i*5+j],capturada_en:stamp((i*5+j)*20)});
const isolated={...base,id:99,latitud:38.9982,longitud:-0.1662,valor:70,capturada_en:stamp(400)};

async function page(measurements){
  const errors=[],groups=[],handlers={};const vc=new VirtualConsole();vc.on('jsdomError',e=>{if(!e.message.includes('navigation'))errors.push(e.message);});
  const dom=new JSDOM(fs.readFileSync(path.join(root,'mapa.html'),'utf8'),{url:'http://localhost/mapa.html',runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
  const w=dom.window;
  w.matchMedia=()=>({matches:false});w.requestAnimationFrame=f=>w.setTimeout(f,0);w.URL.createObjectURL=()=> 'blob:test';w.URL.revokeObjectURL=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};
  const layer=()=>({addTo(){return this;},on(){return this;},setView(){return this;},addLayer(){},fitBounds(){},panTo(){},removeLayer(){},zoomIn(){},zoomOut(){},remove(){},
    getBounds(){return {pad(){return this;},contains(){return true;}};},getCenter(){return {lat:38.99665};}});
  w.L={tileLayer:layer,latLngBounds:layer,circleMarker:(position,options)=>{assert.equal(options.bubblingMouseEvents,false);return layer();},
    map:()=>{const m=layer();m.on=(event,handler)=>{handlers[event]=handler;return m;};return m;},
    layerGroup:()=>{const g=layer();g.items=[];g.clearLayers=()=>{g.items.length=0;};groups.push(g);return g;},
    rectangle:(bounds,style)=>({addTo(group){group.items.push({bounds,style});return this;}})};
  w.fetch=async url=>{
    const u=new URL(url,'http://localhost/');
    const data=u.pathname==='/public.php'?{measurements,truncated:false}:u.pathname==='/auth.php'?{ok:true,csrf:'test',user:null}:[];
    return {ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>data};
  };
  for(const script of w.document.querySelectorAll('script[src]')){
    const src=script.getAttribute('src');if(src.startsWith('http'))continue;
    vm.runInContext(fs.readFileSync(path.join(root,src),'utf8'),dom.getInternalVMContext(),{filename:src});
  }
  await wait();
  const q=s=>w.document.querySelector(s);
  // Segundo layerGroup de public-map.js: las zonas.
  const cells=()=>{const items=groups[1].items;return {colour:items.filter(c=>!c.style.dashArray),grey:items.filter(c=>c.style.dashArray)};};
  const centre=c=>[(c.bounds[0][0]+c.bounds[1][0])/2,(c.bounds[0][1]+c.bounds[1][1])/2];
  const click=(lat,lng)=>{handlers.click({latlng:{lat,lng}});return q('#map-info').textContent;};
  const setRadius=value=>{q('#radius-range').value=String(value);q('#radius-range').dispatchEvent(new w.Event('input',{bubbles:true}));};
  return {w,q,cells,centre,click,setRadius,errors,close:()=>w.close()};
}

(async()=>{
  // Una sola medición: nunca se colorea; solo celdas de «datos insuficientes» alrededor.
  let p=await page([isolated]),c=p.cells();
  assert.equal(c.colour.length,0);assert(c.grey.length>0);
  assert(c.grey.every(cell=>cell.style.interactive===false));
  assert(p.q('#map-loading').textContent.includes('Gris = datos insuficientes; sin color = sin datos.'));
  assert(p.click(isolated.latitud,isolated.longitud).includes('Datos insuficientes'));
  assert(p.click(isolated.latitud,isolated.longitud).includes('1 posición en 30 m; hacen falta 3.'));
  assert.deepEqual(p.errors,[]);p.close();

  // Denso + aislado: color donde hay soporte, gris en el borde y junto al punto aislado, nada más lejos.
  const rows=[...dense,isolated],support=domain.support(rows);
  p=await page(rows);c=p.cells();
  assert(c.colour.length>0&&c.grey.length>0);
  // Cada celda coincide con el cálculo directo en su centro: el valor se estima donde se pinta.
  for(const cell of c.colour)assert(domain.estimate(p.centre(cell),support,30).valor!==null);
  for(const cell of c.grey)assert.equal(domain.estimate(p.centre(cell),support,30).motivo,'insuficiente');
  // Ninguna celda, ni de color ni gris, queda a más del radio de una medición.
  for(const cell of [...c.colour,...c.grey])assert(domain.estimate(p.centre(cell),support,30).cercana<=30);
  // Los colores salen de los umbrales de la campaña.
  assert.deepEqual([...new Set(c.colour.map(cell=>cell.style.fillColor))].sort(),['#219c72','#e35b5d','#eea22d']);
  const at30=c.colour.length;
  assert.equal(p.q('#radius-value').textContent,'30 m');assert(p.q('#radius-reset').hidden);

  // Consulta de un punto: estimación, datos insuficientes o sin datos; no cambia la medición seleccionada.
  let text=p.click(38.9967,-0.1658);
  assert(text.includes('Punto consultado'));assert(text.includes('Estimación: 62 ppb · moderado'));assert(text.includes('Valor interpolado, no medido · datos simulados.'));
  assert.equal(p.q('#selected-id').textContent,'—');
  text=p.click(38.9990,-0.1640);assert(text.includes('Sin datos'));assert(text.includes('Sin datos no significa aire limpio.'));
  text=p.click(isolated.latitud,isolated.longitud);assert(text.includes('Datos insuficientes'));

  // El radio es configurable entre 20 y 50 m: la cobertura crece o se reduce, y se puede volver al de la campaña.
  assert.equal(p.q('#radius-range').min,'20');assert.equal(p.q('#radius-range').max,'50');
  p.setRadius(50);c=p.cells();
  assert(c.colour.length>at30);assert.equal(p.q('#radius-value').textContent,'50 m');assert(!p.q('#radius-reset').hidden);
  assert(p.q('#radius-note').textContent.includes('no se guarda'));
  for(const cell of c.colour)assert(domain.estimate(p.centre(cell),support,50).valor!==null);
  for(const cell of [...c.colour,...c.grey])assert(domain.estimate(p.centre(cell),support,50).cercana<=50);
  // La consulta abierta se recalcula con el radio nuevo.
  assert(p.q('#map-info').textContent.includes('1 posición en 50 m'));
  p.setRadius(20);assert(p.cells().colour.length<at30);
  p.q('#radius-reset').click();assert.equal(p.cells().colour.length,at30);assert(p.q('#radius-reset').hidden);assert.equal(p.q('#radius-value').textContent,'30 m');

  // Capa desactivada: no se pinta nada.
  p.q('#heat-toggle').checked=false;p.q('#heat-toggle').dispatchEvent(new p.w.Event('change'));
  assert.equal(p.cells().colour.length+p.cells().grey.length,0);
  assert.deepEqual(p.errors,[]);p.close();

  // Muchos puntos dispersos: el recorte por rendimiento se avisa en vez de parecer «sin datos».
  const many=[];for(let i=0;i<20;i++)for(let j=0;j<20;j++)many.push({...base,id:i*20+j+1,latitud:38.99+i*0.001,longitud:-0.17+j*0.0013,valor:60,capturada_en:stamp(0)});
  p=await page(many);
  assert.equal(p.cells().colour.length,0);assert(p.q('#map-loading').textContent.includes('Zonas recortadas por rendimiento'));
  assert.deepEqual(p.errors,[]);p.close();

  // Sin mediciones: todo queda sin datos y la consulta lo dice.
  p=await page([]);
  assert.equal(p.cells().colour.length+p.cells().grey.length,0);
  assert(p.click(38.9967,-0.1658).includes('No hay mediciones publicadas.'));
  assert.deepEqual(p.errors,[]);p.close();
  console.log('AERIQ mapa OK: zonas estimadas, datos insuficientes, sin datos, radio 20–50 m, consulta de punto y aviso de recorte.');
})().catch(e=>{console.error(e);process.exit(1);});
