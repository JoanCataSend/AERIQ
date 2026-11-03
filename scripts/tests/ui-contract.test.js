/* Prueba de lógica DOM aislada: no controla Chrome ni transmite formularios reales. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {JSDOM,VirtualConsole}=require(path.resolve(process.argv[2]||'.local-backups/test-tools/node_modules/jsdom'));
const root=path.resolve(__dirname,'../../src/web');
const wait=()=>new Promise(r=>setTimeout(r,35));
const user={id:1,name:'Ana',last:'Administradora',role:'admin',organization:'Organización de prueba',email:'admin@example.test',mustChangePassword:false};
const worker={id:2,nombre:'Pau',apellidos:'Operador',email:'worker@example.test',rol:'trabajador',activo:0,cambiar_clave:1};
const campaign={id:1,nombre:'Campaña de prueba',zona:'Campus',inicio:'2026-11-01',fin:'2026-12-01',activa:1,publicar:1,radio_m:30,umbral_medio:60,umbral_alto:100};
const device={id:1,nombre:'Orb original',direccion_ble:'D0:00:00:00:00:01',trabajador_id:2,trabajador:worker.email,campana_id:1,campana:campaign.nombre,activo:1,simulado:1,ultima_medicion:'2026-11-03 08:00:00'};
const measurement={id:1,campaignId:1,valor:68,unidad:'ppb',capturada_en:'2026-11-03 08:00:00.000',latitud:38.99665,longitud:-.166,simulado:true,zona:'Campus',radio_m:30,umbral_medio:60,umbral_alto:100,dispositivo:'Orb original',campana:'Campaña de prueba'};
async function page(name,search=''){
  const errors=[],calls=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>{if(!e.message.includes('navigation'))errors.push(e.message);});
  const dom=new JSDOM(fs.readFileSync(path.join(root,name),'utf8'),{url:'http://localhost/'+name+search,runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
  const w=dom.window;
  w.matchMedia=()=>({matches:false});w.requestAnimationFrame=f=>w.setTimeout(f,0);w.URL.createObjectURL=()=> 'blob:test';w.URL.revokeObjectURL=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;};
  w.HTMLCanvasElement.prototype.getContext=()=>({beginPath(){},moveTo(){},lineTo(){},fillRect(){},stroke(){}});
  const chain=()=>({addTo(){return this;},on(){return this;},setView(){return this;},setCenter(){},clearLayers(){},addLayer(){},getBounds(){return {pad(){return this;},contains(){return true;}};},getCenter(){return {lat:38.99665};},fitBounds(){},panTo(){},removeLayer(){},zoomIn(){},zoomOut(){},remove(){}});
  w.L={map:chain,layerGroup:chain,tileLayer:chain,circleMarker:chain,rectangle:chain,latLngBounds:chain};
  w.fetch=async(url,options={})=>{
    const u=new URL(url,'http://localhost/');const body=options.body?JSON.parse(options.body):null;calls.push({path:u.pathname,action:u.searchParams.get('action'),body});
    let data;
    if(u.pathname==='/auth.php')data={ok:true,csrf:'test',user};
    else if(u.pathname==='/portal.php'){
      if(body){if(u.searchParams.get('action')==='device')device.nombre=body.name;data={ok:true,id:1};}
      else data=u.searchParams.get('action')==='readings'?{readings:[measurement]}:u.searchParams.get('action')==='map'?{measurements:[measurement]}:{workers:[worker],campaigns:[campaign],devices:[device]};
    }else if(u.pathname==='/public.php')data=body?{ok:true,id:7,message:'Solicitud registrada.'}:{measurements:[measurement],truncated:false};
    else data=[];
    return {ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>data};
  };
  for(const script of w.document.querySelectorAll('script[src]')){
    const src=script.getAttribute('src');if(src.startsWith('http'))continue;
    vm.runInContext(fs.readFileSync(path.join(root,src),'utf8'),dom.getInternalVMContext(),{filename:src});
  }
  await wait();return {dom,w,document:w.document,calls,errors,close:()=>w.close()};
}
(async()=>{
  let p=await page('index.html');
  assert(p.document.querySelector('model-viewer'));assert(p.document.querySelector('.desktop-mock img'));assert(p.document.querySelector('.phone-mock img'));
  assert(!/Plan personal|Premium|3,99/.test(p.document.body.textContent));assert(p.document.querySelectorAll('#planes a[href*="solicitar"]').length===3);assert.deepEqual(p.errors,[]);p.close();
  p=await page('sensor.html');assert.equal(p.document.querySelectorAll('[data-angle]').length,4);p.document.querySelector('[data-angle="back"]').click();assert(p.document.querySelector('#product-main-image').src.includes('trasera'));assert.equal(p.document.querySelector('#add-to-cart').textContent,'Solicitar este kit ↗');assert.deepEqual(p.errors,[]);p.close();
  p=await page('sensores.html');assert(p.document.querySelector('.devices-table').textContent.includes('Orb original'));p.document.querySelector('.devices-table .text-link').click();assert(p.document.querySelector('#device-modal').open);assert(p.document.querySelector('select[name=worker] option[value="2"]'));p.document.querySelector('#device-new-name').value='Orb actualizado';p.document.querySelector('#device-edit-form').dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();assert(p.calls.some(c=>c.action==='device'&&c.body.name==='Orb actualizado'));assert.deepEqual(p.errors,[]);p.close();
  p=await page('portal.html');assert(!p.document.querySelector('[data-view="overview"]').hidden);p.document.querySelector('[data-workspace-view="workers"]').click();assert(!p.document.querySelector('#worker-form').closest('section').hidden);p.document.querySelector('[data-workspace-view="reports"]').click();p.document.querySelector('#report-form').dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();assert(p.document.querySelector('#report-results').textContent.includes('68'));assert(!p.document.querySelector('#export-report').disabled);assert.deepEqual(p.errors,[]);p.close();
  p=await page('solicitar.html');const kind=p.document.querySelector('#service-kind');kind.value='estudio';kind.dispatchEvent(new p.w.Event('change'));assert(p.document.querySelector('[name=surface]').required);assert(!p.document.querySelector('[name=quantity]').required);assert.deepEqual(p.errors,[]);p.close();
  p=await page('checkout.html','?service=empresa&quantity=4');assert.equal(p.document.querySelector('[name=quantity]').value,'4');assert(!p.document.querySelector('[name=payment]'));assert.deepEqual(p.errors,[]);p.close();
  p=await page('mapa.html');assert(p.calls.some(c=>c.path==='/public.php'));assert(p.document.querySelector('#map-sensor-list').textContent.includes('68 ppb'));p.document.querySelector('[data-source=mine]').click();await wait();assert(p.calls.some(c=>c.path==='/portal.php'&&c.action==='map'));assert.deepEqual(p.errors,[]);p.close();
  console.log('AERIQ DOM OK: portada/3D, galería, flota, panel por secciones, informes, formularios, presupuesto y mapa público/privado.');
})().catch(e=>{console.error(e);process.exitCode=1;});
