/* Prueba de lógica DOM aislada: no controla Chrome ni transmite formularios reales. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {JSDOM,VirtualConsole}=require(path.resolve(process.argv[2]||'.local-backups/test-tools/node_modules/jsdom'));
const root=path.resolve(__dirname,'../../src/web');
const wait=()=>new Promise(r=>setTimeout(r,35));
const user={id:1,name:'Ana',last:'Administradora',role:'admin',roleName:'Administrador',permissions:['informes.ver','mapa.organizacion','mediciones.registrar','organizacion.gestionar'],organization:'Organización de prueba',email:'admin@example.test',mustChangePassword:false};
const worker={id:2,nombre:'Pau',apellidos:'Operador',email:'worker@example.test',rol:'trabajador',rol_nombre:'Trabajador',puede_medir:1,activo:0,cambiar_clave:1};
const campaign={id:1,nombre:'Campaña de prueba',zona:'Campus',inicio:'2026-11-01',fin:'2026-12-01',activa:1,publicar:1,radio_m:30,umbral_medio:60,umbral_alto:100};
const device={id:1,nombre:'Orb original',direccion_ble:'D0:00:00:00:00:01',bateria_pct:57,bateria_en:'2026-11-03 08:00:00.000',trabajador_id:2,trabajador:worker.email,campana_id:1,campana:campaign.nombre,activo:1,simulado:1,ultima_medicion:'2026-11-03 08:00:00'};
// Catálogos del formulario comercial: incluye un servicio nuevo con datos de estudio para comprobar que manda la base de datos.
const catalogs={services:[{codigo:'kits',nombre:'Comprar Orb para mi organización',descripcion:'Sensores propios.',mide_superficie:false},{codigo:'estudio',nombre:'Contratar un estudio de ozono',descripcion:'Medimos nosotros.',mide_superficie:true},{codigo:'auditoria',nombre:'Servicio nuevo de prueba',descripcion:'',mide_superficie:true}],orgTypes:[{codigo:'administracion',nombre:'Administración pública'},{codigo:'empresa',nombre:'Empresa'},{codigo:'otra',nombre:'Otra entidad'}],objectives:[{codigo:'espacios_publicos',nombre:'Calidad del aire en espacios públicos'},{codigo:'otro',nombre:'Otro'}],provinces:['Madrid','Valencia'],durations:['1 semana','2 semanas','A definir con Código Norte']};
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
      else data=u.searchParams.get('action')==='readings'?{readings:[measurement]}:u.searchParams.get('action')==='map'?{measurements:[measurement]}:{workers:[worker],campaigns:[campaign],devices:[device],zones:[{id:1,nombre:'Campus'}],roles:[{codigo:'trabajador',nombre:'Trabajador',descripcion:'Recoge mediciones'},{codigo:'analista',nombre:'Analista',descripcion:'Informes'}],thresholds:{umbral_medio:60,umbral_alto:100}};
    }else if(u.pathname==='/public.php')data=body?{ok:true,id:7,message:'Solicitud registrada.'}:u.searchParams.get('action')==='services'?catalogs:{measurements:[measurement],truncated:false};
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
  // Vista explosionada: entre el hero y «El dispositivo», etiquetas HTML reales y, sin anime.js, estado estático.
  const explode=p.document.querySelector('#por-dentro');assert(explode);assert(explode.previousElementSibling.classList.contains('hero-section'));assert(explode.nextElementSibling.classList.contains('feature-section'));
  assert.equal(explode.querySelectorAll('.explode-labels li h3 + p').length,5);assert.equal(explode.querySelectorAll('.explode-part').length,5);
  assert([...explode.querySelectorAll('img')].every(img=>img.hasAttribute('alt')));assert(!explode.classList.contains('is-animated'));
  assert(!/Plan personal|Premium|3,99/.test(p.document.body.textContent));assert(p.document.querySelectorAll('#planes a[href*="solicitar"]').length===3);assert(!/Ya tengo cuenta/.test(p.document.querySelector('#contacto').textContent));assert(p.document.querySelector('#contacto a[href="solicitar.html"]'));assert.deepEqual(p.errors,[]);p.close();
  p=await page('sensor.html');assert(p.document.querySelector('model-viewer[camera-controls]'));assert(!p.document.querySelector('[data-qty-plus]'));assert.equal(p.document.querySelector('.purchase-actions a[href="solicitar.html?interes=kits"]').textContent,'Solicitar este kit');assert.deepEqual(p.errors,[]);p.close();
  p=await page('perfil.html');assert(p.document.querySelector('#account-link').href.endsWith('/perfil.html'));assert(!p.document.querySelector('[data-logout]'));assert(p.document.querySelector('#logout'));p.document.querySelector('#logout').click();await wait();assert(p.calls.some(c=>c.path==='/auth.php'&&c.action==='logout'));assert.deepEqual(p.errors,[]);p.close();
  p=await page('sensores.html');assert(p.document.querySelector('.devices-table').textContent.includes('Orb original'));assert(p.document.querySelector('.devices-table').textContent.includes('57 % · media'));p.document.querySelector('.devices-table .text-link').click();assert(p.document.querySelector('#device-modal').open);assert(p.document.querySelector('select[name=worker] option[value="2"]'));p.document.querySelector('#device-new-name').value='Orb actualizado';p.document.querySelector('#device-edit-form').dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();assert(p.calls.some(c=>c.action==='device'&&c.body.name==='Orb actualizado'));assert.deepEqual(p.errors,[]);p.close();
  p=await page('portal.html');assert(!p.document.querySelector('[data-view="overview"]').hidden);p.document.querySelector('[data-workspace-view="workers"]').click();assert(!p.document.querySelector('#worker-form').closest('section').hidden);assert.deepEqual([...p.document.querySelectorAll('#worker-form [name=role] option')].map(o=>o.value),['trabajador','analista']);assert(p.document.querySelector('#report-zone option[value="Campus"]'));assert(p.document.querySelector('#zone-options option[value="Campus"]'));p.document.querySelector('[data-workspace-view="reports"]').click();p.document.querySelector('#report-form').dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();assert(p.document.querySelector('#report-results').textContent.includes('68'));assert(!p.document.querySelector('#export-report').disabled);assert.deepEqual(p.errors,[]);p.close();
  // Formulario comercial: catálogos de la BD, preselección por URL, campos de estudio, teléfono y validación en línea.
  p=await page('solicitar.html','?interes=kits&org=empresa');await wait();{
    const d=p.document,f=d.querySelector('#service-form'),radios=()=>[...d.querySelectorAll('[name=interest]')];
    assert.deepEqual(radios().map(r=>r.value),['kits','estudio','auditoria']);assert(radios()[0].checked);
    assert.equal(d.querySelector('#org-type').value,'empresa');assert(!d.querySelector('[name=quantity]'));
    assert.equal(d.querySelectorAll('[name=objectives]').length,2);assert.equal(d.querySelectorAll('#province option').length,3);
    assert(d.querySelector('[data-study]').hidden);assert(d.querySelector('[name=duration]').disabled);
    radios()[2].checked=true;radios()[2].dispatchEvent(new p.w.Event('change',{bubbles:true}));
    assert(!d.querySelector('[data-study]').hidden);assert(!d.querySelector('[name=duration]').disabled);assert(!d.querySelector('[name=surface]').required);
    // Teléfono: las letras se descartan y aparece el aviso; un número de 9 cifras que empieza por 6 es válido.
    const phone=f.elements.phone,typed=new p.w.InputEvent('beforeinput',{data:'a',inputType:'insertText',cancelable:true,bubbles:true});
    phone.dispatchEvent(typed);assert(typed.defaultPrevented);assert.equal(d.querySelector('#phone-help').textContent,'Solo se admiten números.');
    phone.value='60a1';phone.dispatchEvent(new p.w.Event('input',{bubbles:true}));assert.equal(phone.value,'601');
    phone.dispatchEvent(new p.w.FocusEvent('focusout',{bubbles:true}));assert.equal(phone.getAttribute('aria-invalid'),'true');
    phone.value='612345678';phone.dispatchEvent(new p.w.Event('input',{bubbles:true}));assert(!phone.hasAttribute('aria-invalid'));
    // Enviar incompleto: no llama al servidor, marca los campos y lleva el foco al primero con error.
    f.dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
    assert(!p.calls.some(c=>c.path==='/public.php'&&c.body));assert(!d.querySelector('#form-summary').hidden);
    assert.equal(f.elements.entity.getAttribute('aria-invalid'),'true');assert.equal(d.activeElement,f.elements.entity);
    assert.equal(d.querySelector('#objectives-group').getAttribute('aria-invalid'),'true');assert.equal(d.querySelector('#duration-error').textContent,'Elige la duración del estudio.');
    // Completo: envía el contrato nuevo y muestra la referencia.
    radios()[0].checked=true;radios()[0].dispatchEvent(new p.w.Event('change',{bubbles:true}));
    for(const [name,value] of Object.entries({entity:'Ayuntamiento de prueba',contact:'Ana Pérez',email:'ana@example.test',municipality:'Gandia',province:'Valencia',zone:'Parque central',needs:'Medir el ozono en verano.'}))f.elements[name].value=value;
    d.querySelector('[name=objectives][value=otro]').checked=true;f.elements.consent.checked=true;
    f.dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
    const sent=p.calls.find(c=>c.path==='/public.php'&&c.body)?.body;
    assert.deepEqual({interest:sent.interest,orgType:sent.orgType,phone:sent.phone,objectives:sent.objectives,consent:sent.consent},{interest:'kits',orgType:'empresa',phone:'612345678',objectives:['otro'],consent:true});
    assert(!('quantity' in sent)&&!('duration' in sent));
    assert(f.hidden);assert(!d.querySelector('#request-success').hidden);assert(d.querySelector('#success-title').textContent.includes('7'));
    assert.deepEqual(p.errors,[]);p.close();
  }
  // Los enlaces antiguos (?service=estudio) siguen preseleccionando el servicio.
  p=await page('solicitar.html','?service=estudio');await wait();assert(p.document.querySelector('[name=interest][value=estudio]').checked);assert(!p.document.querySelector('[data-study]').hidden);assert.deepEqual(p.errors,[]);p.close();
  p=await page('mapa.html');assert(p.calls.some(c=>c.path==='/public.php'));assert(p.document.querySelector('#map-sensor-list').textContent.includes('68 ppb'));p.document.querySelector('[data-source=mine]').click();await wait();assert(p.calls.some(c=>c.path==='/portal.php'&&c.action==='map'));assert.deepEqual(p.errors,[]);p.close();
  console.log('AERIQ DOM OK: portada/3D, galería, flota, panel por secciones, informes, formularios, presupuesto y mapa público/privado.');
})().catch(e=>{console.error(e);process.exitCode=1;});
