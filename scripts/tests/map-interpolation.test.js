process.env.TZ='UTC'; // fechas deterministas: la web muestra la hora local
/* T25 · Prueba DOM del mapa: zonas estimadas (círculos del radio, no cuadrados), datos insuficientes, sin datos,
   radio configurable, consulta de un punto y ruta del Orb en «Mi organización».
   Ejecuta public-map.js en jsdom con un doble de Leaflet (proyección lineal de 1 m por píxel) y un lienzo falso
   que guarda la imagen pintada. No renderiza píxeles reales. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {JSDOM,VirtualConsole}=require(path.resolve(process.argv[2]||'.local-backups/test-tools/node_modules/jsdom'));
const root=path.resolve(__dirname,'../../src/web'),domain=require(path.join(root,'aeriq-domain.js'));
const wait=()=>new Promise(r=>setTimeout(r,35));
// Los arrays creados dentro de jsdom tienen otro prototipo: se comparan como JSON.
const plain=v=>JSON.parse(JSON.stringify(v));
const base={campaignId:1,unidad:'ppb',simulado:true,zona:'Campus',radio_m:30,umbral_medio:60,umbral_alto:100};
const stamp=m=>`2026-11-10 ${String(12+Math.floor(m/60)).padStart(2,'0')}:${String(m%60).padStart(2,'0')}:00.000`;
// Caso denso: la rejilla 5 × 3 de la simulación. Caso disperso: un punto aislado a ~170 m.
const O3=[38,42,47,51,56,61,66,72,78,84,91,97,103,108,114],dense=[];
for(let i=0;i<3;i++)for(let j=0;j<5;j++)dense.push({...base,id:i*5+j+1,latitud:38.996651+i*0.0001,longitud:-0.166009+j*0.00013,valor:O3[i*5+j],capturada_en:stamp((i*5+j)*20)});
const isolated={...base,id:99,latitud:38.9982,longitud:-0.1662,valor:70,capturada_en:stamp(400)};
// Vista falsa de 800 × 600 px a 1 m por píxel, centrada en el campus.
const W=800,H=600,DLAT=1/111320,DLNG=DLAT/Math.cos(38.99675*Math.PI/180),TOP=38.99675+H/2*DLAT,LEFT=-0.16575-W/2*DLNG;
const toLatLng=([x,y])=>({lat:TOP-y*DLAT,lng:LEFT+x*DLNG});
// Colores RGB del lienzo → estado y color de la leyenda.
const STATE={'104,109,107':'insuficiente','33,156,114':'#219c72','238,162,45':'#eea22d','227,91,93':'#e35b5d'};

async function page(measurements,{organization=false,mine=measurements}={}){
  const errors=[],handlers={},paint={image:null},routes=[],ends=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>{if(!e.message.includes('navigation'))errors.push(e.message);});
  const dom=new JSDOM(fs.readFileSync(path.join(root,'mapa.html'),'utf8'),{url:'http://localhost/mapa.html',runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
  const w=dom.window;
  w.matchMedia=()=>({matches:false});w.requestAnimationFrame=f=>w.setTimeout(f,0);w.URL.createObjectURL=()=> 'blob:test';w.URL.revokeObjectURL=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};
  // Lienzo falso: borrar descarta lo pintado; putImageData guarda la imagen de la rejilla.
  w.HTMLCanvasElement.prototype.getContext=function(){return {clearRect(){paint.image=null;},drawImage(){},putImageData(image){paint.image=image;},
    createImageData(width,height){return {width,height,data:new Uint8ClampedArray(width*height*4)};}};};
  const layer=()=>({addTo(){return this;},on(){return this;},setView(){return this;},addLayer(){},fitBounds(){},panTo(){},removeLayer(){},zoomIn(){},zoomOut(){},remove(){},bindTooltip(text){this.tooltip=text;return this;}});
  w.L={tileLayer:layer,latLngBounds:layer,DomUtil:{setPosition(){}},
    circleMarker:(position,options)=>{assert.equal(options.bubblingMouseEvents,false);const m=layer();m.position=position;m.addTo=function(g){if(g&&g.route)ends.push(this);return this;};return m;},
    polyline:(points,options)=>({addTo(){routes.push({points,options});return this;}}),
    map:()=>{const m=layer();m.on=(event,handler)=>{handlers[event]=handler;return m;};
      m.getSize=()=>({x:W,y:H});m.containerPointToLatLng=toLatLng;m.containerPointToLayerPoint=()=>({x:0,y:0});
      m.createPane=()=>{const d=w.document.createElement('div');w.document.body.append(d);return d;};return m;},
    layerGroup:(()=>{let n=0;return ()=>{const g=layer();g.route=n++===1;g.clearLayers=()=>{if(g.route){routes.length=0;ends.length=0;}};return g;};})()};
  w.fetch=async url=>{
    const u=new URL(url,'http://localhost/');
    const data=u.pathname==='/public.php'?{measurements,truncated:false}:u.pathname==='/portal.php'?{measurements:mine,truncated:false}
      :u.pathname==='/auth.php'?{ok:true,csrf:'test',user:organization?{organizationId:1}:null}:[];
    return {ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>data};
  };
  for(const script of w.document.querySelectorAll('script[src]')){
    const src=script.getAttribute('src');if(src.startsWith('http'))continue;
    vm.runInContext(fs.readFileSync(path.join(root,src),'utf8'),dom.getInternalVMContext(),{filename:src});
  }
  await wait();
  const q=s=>w.document.querySelector(s);
  // Puntos pintados de la rejilla, con su centro en coordenadas y su color.
  const cells=()=>{
    const out={colour:[],grey:[]},img=paint.image;if(!img)return out;
    const step=W/img.width;
    for(let k=0;k<img.width*img.height;k++){
      const d=img.data;if(!d[k*4+3])continue;
      const state=STATE[`${d[k*4]},${d[k*4+1]},${d[k*4+2]}`];assert(state,'color desconocido en el lienzo');
      const c=toLatLng([(k%img.width+.5)*step,(Math.floor(k/img.width)+.5)*step]),cell={centre:[c.lat,c.lng],colour:state};
      (state==='insuficiente'?out.grey:out.colour).push(cell);
    }
    return out;
  };
  const click=(lat,lng)=>{handlers.click({latlng:{lat,lng}});return q('#map-info').textContent;};
  const setRadius=value=>{q('#radius-range').value=String(value);q('#radius-range').dispatchEvent(new w.Event('input',{bubbles:true}));};
  const change=(sel,checked)=>{const el=q(sel);if(checked!==undefined)el.checked=checked;el.dispatchEvent(new w.Event('change'));};
  return {w,q,cells,click,setRadius,change,routes,ends,errors,close:()=>w.close()};
}

(async()=>{
  // Una sola medición: nunca se colorea; solo «datos insuficientes» alrededor.
  let p=await page([isolated]),c=p.cells();
  assert.equal(c.colour.length,0);assert(c.grey.length>0);
  assert(p.q('#map-loading').textContent.includes('Gris = datos insuficientes; sin color = sin datos.'));
  assert(p.click(isolated.latitud,isolated.longitud).includes('Datos insuficientes'));
  assert(p.click(isolated.latitud,isolated.longitud).includes('1 posición en 30 m; hacen falta 3.'));
  // La zona es un círculo del radio, no un cuadrado: todo lo pintado está a ≤ 30 m y el disco está lleno.
  // Un cuadrado de 60 m de lado llegaría a ~42 m en las esquinas.
  const far=c.grey.map(cell=>domain.distance(cell.centre,[isolated.latitud,isolated.longitud]));
  assert(Math.max(...far)<=30);assert(Math.max(...far)>28);
  const disc=Math.PI*30*30/16;assert(Math.abs(c.grey.length-disc)/disc<.05,`${c.grey.length} puntos para un disco de ~${Math.round(disc)}`);
  // El lienzo de las zonas no recibe toques.
  assert(p.q('canvas.map-zones'));assert.equal(p.q('canvas.map-zones').parentElement.style.pointerEvents,'none');
  // Mapa público: sin Orb identificado no hay control de ruta.
  assert(p.q('#route-control').hidden);
  assert.deepEqual(p.errors,[]);p.close();

  // Denso + aislado: color donde hay soporte, gris en el borde y junto al punto aislado, nada más lejos.
  const rows=[...dense,isolated],support=domain.support(rows);
  p=await page(rows);c=p.cells();
  assert(c.colour.length>0&&c.grey.length>0);
  // Cada punto coincide con el cálculo directo en su centro: el valor se estima donde se pinta.
  for(const cell of c.colour)assert(domain.estimate(cell.centre,support,30).valor!==null);
  for(const cell of c.grey)assert.equal(domain.estimate(cell.centre,support,30).motivo,'insuficiente');
  // Nada pintado, ni de color ni gris, queda a más del radio de una medición.
  for(const cell of [...c.colour,...c.grey])assert(domain.estimate(cell.centre,support,30).cercana<=30);
  // Los colores salen de los umbrales de la campaña.
  assert.deepEqual([...new Set(c.colour.map(cell=>cell.colour))].sort(),['#219c72','#e35b5d','#eea22d']);
  const at30=c.colour.length;
  assert.equal(p.q('#radius-value').textContent,'30 m');assert(p.q('#radius-reset').hidden);

  // Consulta de un punto: estimación, datos insuficientes o sin datos; no cambia la medición seleccionada.
  let text=p.click(38.9967,-0.1658);
  assert(text.includes('Punto consultado'));assert(text.includes('Estimación: 59,6 ppb · bajo'));assert(text.includes('Valor interpolado, no medido · datos simulados.'));
  assert.equal(p.q('#selected-id').textContent,'—');
  text=p.click(38.9990,-0.1640);assert(text.includes('Sin datos'));assert(text.includes('Sin datos no significa aire limpio.'));
  text=p.click(isolated.latitud,isolated.longitud);assert(text.includes('Datos insuficientes'));

  // El radio es configurable entre 20 y 50 m: la cobertura crece o se reduce, y se puede volver al de la campaña.
  assert.equal(p.q('#radius-range').min,'20');assert.equal(p.q('#radius-range').max,'50');
  p.setRadius(50);c=p.cells();
  assert(c.colour.length>at30);assert.equal(p.q('#radius-value').textContent,'50 m');assert(!p.q('#radius-reset').hidden);
  assert(p.q('#radius-note').textContent.includes('no se guarda'));
  for(const cell of c.colour)assert(domain.estimate(cell.centre,support,50).valor!==null);
  for(const cell of [...c.colour,...c.grey])assert(domain.estimate(cell.centre,support,50).cercana<=50);
  // La consulta abierta se recalcula con el radio nuevo.
  assert(p.q('#map-info').textContent.includes('1 posición en 50 m'));
  p.setRadius(20);assert(p.cells().colour.length<at30);
  p.q('#radius-reset').click();assert.equal(p.cells().colour.length,at30);assert(p.q('#radius-reset').hidden);assert.equal(p.q('#radius-value').textContent,'30 m');

  // Capa desactivada: no se pinta nada.
  p.change('#heat-toggle',false);
  assert.equal(p.cells().colour.length+p.cells().grey.length,0);
  assert.deepEqual(p.errors,[]);p.close();

  // Muchos puntos dispersos (~100 m entre sí): cada uno con su círculo gris, sin recorte ni color.
  const many=[];for(let i=0;i<20;i++)for(let j=0;j<20;j++)many.push({...base,id:i*20+j+1,latitud:38.995+i*0.001,longitud:-0.17+j*0.0013,valor:60,capturada_en:stamp(0)});
  p=await page(many);c=p.cells();
  assert.equal(c.colour.length,0);assert(c.grey.length>0);assert(!p.q('#map-loading').textContent.includes('Zonas recortadas'));
  assert.deepEqual(p.errors,[]);p.close();

  // Sin mediciones: todo queda sin datos y la consulta lo dice.
  p=await page([]);
  assert.equal(p.cells().colour.length+p.cells().grey.length,0);
  assert(p.click(38.9967,-0.1658).includes('No hay mediciones publicadas.'));
  assert.deepEqual(p.errors,[]);p.close();

  // Ruta del Orb en «Mi organización»: posiciones en orden de captura, tramos partidos por huecos > 10 min.
  const orb=(id,name)=>({dispositivo_id:id,dispositivo:name});
  const walk=[
    {...dense[2],...orb(7,'Orb Norte'),id:201,capturada_en:'2026-11-10 12:02:00.000'},
    {...dense[0],...orb(7,'Orb Norte'),id:200,capturada_en:'2026-11-10 12:00:00.000'},
    {...dense[4],...orb(7,'Orb Norte'),id:202,capturada_en:'2026-11-10 12:05:00.000'},
    {...dense[14],...orb(7,'Orb Norte'),id:203,capturada_en:'2026-11-10 12:40:00.000'},
    {...dense[13],...orb(7,'Orb Norte'),id:204,capturada_en:'2026-11-10 12:41:00.000'},
    {...dense[5],...orb(3,'Orb Albufera'),id:300,capturada_en:'2026-11-10 12:00:00.000'}];
  p=await page(dense,{organization:true,mine:walk});
  assert(p.q('#route-control').hidden,'en «Públicos» no hay ruta');
  p.q('[data-source="mine"]').click();await wait();
  assert(!p.q('#route-control').hidden);
  assert.deepEqual([...p.q('#route-device').options].map(o=>o.textContent),['Orb Albufera','Orb Norte']);
  assert(p.q('#route-device').disabled);assert.equal(p.routes.length,0);
  p.q('#route-device').value='7';p.change('#route-toggle',true);
  assert(!p.q('#route-device').disabled);
  assert.equal(p.routes.length,2,'el hueco de 35 minutos parte la ruta en dos tramos');
  assert.deepEqual(plain(p.routes[0].points),[[dense[0].latitud,dense[0].longitud],[dense[2].latitud,dense[2].longitud],[dense[4].latitud,dense[4].longitud]]);
  assert.deepEqual(plain(p.routes[1].points),[[dense[14].latitud,dense[14].longitud],[dense[13].latitud,dense[13].longitud]]);
  assert(p.routes.every(r=>r.options.pane==='aeriq-ruta'&&r.options.interactive===false));
  assert.equal(p.ends.length,2);assert(p.ends[0].tooltip.startsWith('Inicio · 10 nov 2026, 12:00'));assert(p.ends[1].tooltip.startsWith('Última posición · 10 nov 2026, 12:41'));
  assert(p.q('#route-note').textContent.startsWith('5 posiciones en 2 tramos.'));assert(/^Distancia recorrida: \d/.test(p.q('#route-distance').textContent),'HU39: se muestra la distancia recorrida');assert(!p.q('#legend-route').hidden);
  // Otro Orb: su ruta sustituye a la anterior. Con una sola posición no hay línea, solo el punto de inicio.
  p.q('#route-device').value='3';p.change('#route-device');
  assert.equal(p.routes.length,0);assert.equal(p.ends.length,1);assert(p.q('#route-note').textContent.startsWith('1 posición en 1 tramo.'));assert.equal(p.q('#route-distance').textContent,'','con una sola posición no hay distancia');
  // La ficha de una medición de la organización nombra su Orb.
  p.change('#route-toggle',false);assert.equal(p.ends.length,0);assert.equal(p.q('#route-distance').textContent,'');assert(p.q('#legend-route').hidden);
  p.q('#map-sensor-list button').click();assert(p.q('#map-info').textContent.includes('Orb Norte'));
  // Volver a «Públicos» oculta el control.
  p.q('[data-source="all"]').click();await wait();assert(p.q('#route-control').hidden);
  assert.deepEqual(p.errors,[]);p.close();
  console.log('AERIQ mapa OK: zonas circulares del radio, datos insuficientes, sin datos, radio 20–50 m, consulta de punto y ruta del Orb.');
})().catch(e=>{console.error(e);process.exit(1);});
