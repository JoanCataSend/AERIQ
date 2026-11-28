/*
 * Fichero: ui-contract.test.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de scripts/tests.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

process.env.TZ='UTC'; // fechas deterministas: la web muestra la hora local
/* Prueba de lógica DOM aislada: no controla Chrome ni transmite formularios reales. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {JSDOM,VirtualConsole}=require(path.resolve(process.argv[2]||'.local-backups/test-tools/node_modules/jsdom'));
const root=path.resolve(__dirname,'../../src/web');
/**
 * Diseño lógico: wait() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
const wait=()=>new Promise(r=>setTimeout(r,35));
// Admin principal por defecto; las pruebas del panel cambian user.permissions para el admin de campaña.
const ADMIN=['campanas.gestionar','solicitudes.gestionar'],ADMIN_CAMPANA=['datos.exportar','equipo.gestionar','informes.ver','mapa.organizacion'];
const user={id:1,name:'Ana',last:'Administradora',role:'admin',roleName:'Administrador principal',permissions:[...ADMIN],organizationId:1,organization:'Organización de prueba',campaign:'Campaña de prueba',email:'admin@example.test',mustChangePassword:false};
const worker={id:2,nombre:'Pau',apellidos:'Operador',email:'worker@example.test',rol:'trabajador',rol_nombre:'Trabajador',puede_medir:1,activo:0,cambiar_clave:1};
const campaign={id:1,nombre:'Campaña de prueba',zona:'Campus',inicio:'2026-11-01',fin:'2026-12-01',activa:1,estado:'activa',publicar:1,radio_m:30,umbral_medio:60,umbral_alto:100};
const device={id:1,nombre:'Orb original',direccion_ble:'D0:00:00:00:00:01',bateria_pct:57,bateria_en:'2026-11-03 08:00:00.000',trabajador_id:2,trabajador:worker.email,campana_id:1,campana:campaign.nombre,activo:1,simulado:1,ultima_medicion:'2026-11-03 08:00:00'};
// Catálogos del formulario comercial: incluye un servicio nuevo con datos de estudio para comprobar que manda la base de datos.
const catalogs={services:[{codigo:'kits',nombre:'Comprar Orb para mi organización',descripcion:'Sensores propios.',mide_superficie:false},{codigo:'estudio',nombre:'Contratar un estudio de ozono',descripcion:'Medimos nosotros.',mide_superficie:true},{codigo:'auditoria',nombre:'Servicio nuevo de prueba',descripcion:'',mide_superficie:true}],orgTypes:[{codigo:'administracion',nombre:'Administración pública'},{codigo:'empresa',nombre:'Empresa'},{codigo:'otra',nombre:'Otra entidad'}],objectives:[{codigo:'espacios_publicos',nombre:'Calidad del aire en espacios públicos'},{codigo:'otro',nombre:'Otro'}],provinces:['Madrid','Valencia'],durations:['1 semana','2 semanas','A definir con Código Norte']};
const measurement={id:1,campaignId:1,valor:68,unidad:'ppb',capturada_en:'2026-11-03 08:00:00.000',latitud:38.99665,longitud:-.166,simulado:true,zona:'Campus',radio_m:30,umbral_medio:60,umbral_alto:100,dispositivo:'Orb original',campana:'Campaña de prueba'};
/**
 * Diseño lógico: name: Text, search: Text --> page() --> Text
 * Descripción: Procesa page y devuelve el resultado correspondiente.
 */
async function page(name,search=''){
  /**
   * Diseño lógico: errors() --> Text
   * Descripción: Describe la operación realizada y su resultado.
   */
  const errors=[],calls=[];const vc=new VirtualConsole();vc.on('jsdomError',e=>{if(!e.message.includes('navigation'))errors.push(e.message);});
  const dom=new JSDOM(fs.readFileSync(path.join(root,name),'utf8'),{url:'http://localhost/'+name+search,runScripts:'outside-only',pretendToBeVisual:true,virtualConsole:vc});
  const w=dom.window;
  w.matchMedia=()=>({matches:false});w.requestAnimationFrame=f=>w.setTimeout(f,0);w.URL.createObjectURL=()=> 'blob:test';w.URL.revokeObjectURL=()=>{};
  w.HTMLElement.prototype.scrollIntoView=()=>{};w.HTMLDialogElement.prototype.showModal=function(){this.open=true;};w.HTMLDialogElement.prototype.close=function(){this.open=false;};
  w.HTMLCanvasElement.prototype.getContext=()=>({beginPath(){},moveTo(){},lineTo(){},fillRect(){},fillText(){},setLineDash(){},stroke(){},fill(){},arc(){},closePath(){},save(){},restore(){},translate(){},rotate(){},clearRect(){},drawImage(){},putImageData(){},createImageData(width,height){return {width,height,data:new Uint8ClampedArray(width*height*4)};}});
  // El mapa pinta las zonas en un lienzo propio (public-map.js): proyección lineal de ~1 m por píxel en el campus.
  /**
   * Diseño lógico: chain() --> Text
   * Descripción: Procesa chain y devuelve el resultado correspondiente.
   */
  const chain=()=>({addTo(){return this;},on(){return this;},setView(){return this;},setCenter(){},clearLayers(){},addLayer(){},getBounds(){return {pad(){return this;},contains(){return true;}};},getCenter(){return {lat:38.99665};},fitBounds(){},panTo(){},removeLayer(){},zoomIn(){},zoomOut(){},remove(){},bindTooltip(){return this;},
    /**
     * Diseño lógico: getSize() --> Text
     * Descripción: Procesa get size y devuelve el resultado correspondiente.
     */
    getSize(){return {x:800,y:600};},containerPointToLatLng([x,y]){return {lat:38.9984-y*9e-6,lng:-0.1704+x*11.6e-6};},containerPointToLayerPoint(){return {x:0,y:0};},createPane(){return w.document.createElement('div');}});
  w.L={map:chain,layerGroup:chain,tileLayer:chain,circleMarker:chain,rectangle:chain,polyline:chain,latLngBounds:chain,DomUtil:{setPosition(){}}};
  w.fetch=async(url,options={})=>{
    const u=new URL(url,'http://localhost/');const body=options.body?JSON.parse(options.body):null;calls.push({path:u.pathname,action:u.searchParams.get('action'),body});
    let data;
    if(u.pathname==='/auth.php'&&u.searchParams.get('action')==='check-reset')data=globalThis.resetExpired?{ok:true,valid:false,state:'caducado',message:'El enlace ha caducado. Pide otro.'}:{ok:true,valid:true,state:'valido',message:''};
    else if(u.pathname==='/auth.php')data={ok:true,csrf:'test',user:globalThis.noSession?null:user};
    else if(u.pathname==='/portal.php'&&String(u.searchParams.get('action')).startsWith('notice'))data=body?{ok:true,id:5,message:'Aviso enviado.'}:u.searchParams.get('action')==='notice-unread'?{unread:1}
      :u.searchParams.get('action')==='notice'?{notice:{id:5,asunto:'Orb sin batería',estado:'abierto',estado_nombre:'Abierto',cerrado:false,creado_en:'2026-11-20 09:00:00',mio:false,con_id:2,con_nombre:'Pau Operador',con_rol:'Operador'},messages:[{id:1,texto:'El Orb no carga.',creado_en:'2026-11-20 09:00:00',mio:false,autor:'Pau Operador'}]}
      :{notices:[{id:5,asunto:'Orb sin batería',creado_en:'2026-11-20 09:00:00',actualizado_en:'2026-11-20 09:00:00',estado:'abierto',estado_nombre:'Abierto',mio:false,con_id:2,con_nombre:'Pau Operador',con_rol:'Operador',ultimo:'El Orb no carga.',nuevo:true},{id:4,asunto:'Cambio de zona',creado_en:'2026-11-10 09:00:00',actualizado_en:'2026-11-11 09:00:00',estado:'cerrado',estado_nombre:'Cerrado',mio:true,con_id:2,con_nombre:'Pau Operador',con_rol:'Operador',ultimo:'Hecho.',nuevo:false}],unread:1,contacts:[{id:2,nombre:'Pau Operador',rol:'Operador',campana:'Campaña de prueba'}],states:[]};
    else if(u.pathname==='/portal.php'&&u.searchParams.get('action')==='requests')data={requests:[{id:7,entidad:'Ayto. Prueba',contacto:'Ana',email:'ana@x.es',telefono:'600000000',recibida_en:'2026-11-18 10:00:00',estado:'pendiente',estado_nombre:'Pendiente',servicio_nombre:'Estudio',municipio:'Gandia',zona:'Centro',necesidades:'Medir',objetivos:[],historial:[]},{id:8,entidad:'Colegio',contacto:'Bea',email:'bea@x.es',recibida_en:'2026-11-17 10:00:00',estado:'cerrada',estado_nombre:'Cerrada',zona:'Playa',necesidades:'—',objetivos:[],historial:[]}],states:[{codigo:'pendiente',nombre:'Pendiente'},{codigo:'cerrada',nombre:'Cerrada'}]};
    else if(u.pathname==='/portal.php'&&u.searchParams.get('action')==='my-campaign')data={campaign:{nombre:'Campaña de prueba',zona:'Gandia',inicio:'2026-01-01',fin:'2026-12-31',activa:1,publicar:1,umbral_medio:60,umbral_alto:100},admins:[{nombre:'Marta',apellidos:'Admin',email:'marta@example.test'}],orb:{nombre:'Orb original',bateria_pct:57},readings:[{capturada_en:'2026-05-15 10:00:00',magnitud:'Ozono',valor:42,unidad:'ppb'}]};
    else if(u.pathname==='/portal.php'){
      if(body){if(u.searchParams.get('action')==='device')device.nombre=body.name;data={ok:true,id:1};}
      else data=u.searchParams.get('action')==='readings'?{readings:[measurement],discarded:[{...measurement,id:9,valor:742,valida:0,motivo_descarte:'fuera_de_rango'}]}:u.searchParams.get('action')==='map'?{measurements:[measurement]}:{workers:[worker],campaigns:[campaign],devices:[device],zones:[{id:1,nombre:'Campus'}],roles:user.permissions.includes('campanas.gestionar')?[{codigo:'admin_campana',nombre:'Admin de campaña'}]:[{codigo:'trabajador',nombre:'Operador'}],thresholds:{umbral_medio:60,umbral_alto:100}};
    }else if(u.pathname==='/public.php')data=body?{ok:true,id:7,message:'Solicitud registrada.'}:u.searchParams.get('action')==='services'?catalogs:{measurements:[measurement],truncated:false};
    else data=[];
    return {ok:true,status:200,headers:{get:()=> 'application/json'},json:async()=>data};
  };
  for(const script of w.document.querySelectorAll('script[src]')){
    const src=script.getAttribute('src');if(src.startsWith('http')||script.type==='module')continue; // los módulos (visor 3D) no corren en vm
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
  p=await page('perfil.html');assert(p.document.querySelector('.main-nav a[href="portal.html"]'));assert(!p.document.querySelector('[data-logout]'));assert(!p.document.querySelector('#logout'),'Cerrar sesión solo en la burbuja');assert.deepEqual(p.errors,[]);p.close();
  p=await page('portal.html');assert(!p.document.querySelector('.workspace-nav #logout'),'el panel no tiene su propio Cerrar sesión');assert.deepEqual(p.errors,[]);p.close();
  user.permissions=[...ADMIN_CAMPANA];// La flota la edita el admin de campaña.
  // Flota de Orb dentro del panel («Dispositivos Orb»): tabla, filtros y edición; el Orb va a un operador de la campaña.
  p=await page('portal.html');p.document.querySelector('[data-workspace-view="devices"]').click();await wait();const fleet=p.document.querySelector('#fleet');assert(!fleet.hidden);
  assert(fleet.querySelector('.devices-table').textContent.includes('Orb original'));assert(fleet.querySelector('.devices-table').textContent.includes('57 % · media'));assert.equal(fleet.querySelector('.stat strong').textContent,'1');
  // Paneles con su número (T45), filtros combinables (T43) y orden al pulsar la cabecera con flecha (T44).
  {const panels=[...fleet.querySelectorAll('.device-panels .stat-button')];assert.deepEqual(panels.map(b=>b.querySelector('small').textContent),['En servicio','Dados de baja','Recibiendo ahora','Sin uso en más de 30 días']);
  assert.deepEqual(panels.map(b=>b.querySelector('strong').textContent).slice(0,2),['1','0']);
  panels[1].click();assert(fleet.querySelector('.devices-table').textContent.includes('No hay dispositivos que coincidan'));assert.equal(panels[1].getAttribute('aria-pressed'),'true');
  panels[1].click();assert(fleet.querySelector('.devices-table').textContent.includes('Orb original'));
  const estado=fleet.querySelector('select[name=estado]');estado.value='baja';estado.dispatchEvent(new p.w.Event('change'));assert(fleet.querySelector('.devices-table').textContent.includes('No hay dispositivos que coincidan'));
  fleet.querySelector('.device-filters .text-link').click();assert.equal(estado.value,'');assert(fleet.querySelector('.devices-table').textContent.includes('Orb original'));
  assert(fleet.querySelector('select[name=operador] option[value="2"]'));assert(fleet.querySelector('select[name=actividad] option[value="sin_uso"]'));
  /**
   * Diseño lógico: ('.devices-table th') → th() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const th=[...fleet.querySelectorAll('.devices-table th')].find(t=>t.textContent.startsWith('Última medición'));th.querySelector('button').click();assert.equal(th.getAttribute('aria-sort'),'ascending');assert.equal(th.querySelector('.sort-arrow').textContent,'▲');
  th.querySelector('button').click();assert.equal(th.getAttribute('aria-sort'),'descending');assert.equal(th.querySelector('.sort-arrow').textContent,'▼');}
  assert(!p.document.querySelector('#add-device').hidden);fleet.querySelector('.devices-table .text-link').click();assert(p.document.querySelector('#device-modal').open);assert(fleet.querySelector('select[name=worker] option[value="2"]'));assert(!fleet.querySelector('select[name=campaign]'));
  p.document.querySelector('#device-new-name').value='Orb actualizado';p.document.querySelector('#device-edit-form').dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
  /**
   * Diseño lógico: (c=>c.action==='device'&&c.body) → sent() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const sent=p.calls.find(c=>c.action==='device'&&c.body);assert.equal(sent.body.name,'Orb actualizado');assert(!('campaign' in sent.body));assert.deepEqual(p.errors,[]);p.close();user.permissions=[...ADMIN];
  // El admin principal consulta la flota pero no registra ni edita Orb.
  p=await page('portal.html');p.document.querySelector('[data-workspace-view="devices"]').click();await wait();assert(p.document.querySelector('#add-device').hidden);assert(!p.document.querySelector('#fleet .devices-table .text-link'));assert.deepEqual(p.errors,[]);p.close();
  p=await page('portal.html');assert(!p.document.querySelector('[data-view="overview"]').hidden);p.document.querySelector('[data-workspace-view="workers"]').click();assert(!p.document.querySelector('#worker-form').closest('section').hidden);const shown=sel=>!p.document.querySelector(sel).hidden&&!p.document.querySelector(sel).closest('[hidden]');
  // Admin principal: Resumen, Campañas, Admins de campaña, AERIQ por campaña y Solicitudes. Sin informes, CSV ni registro de Orb.
  /**
   * Diseño lógico: ('.workspace-nav a') → navShown() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const navShown=[...p.document.querySelectorAll('.workspace-nav a')].filter(a=>!a.hidden).map(a=>a.firstChild.textContent.trim());
  assert.deepEqual(navShown,['Resumen','Campañas','Admins de campaña','AERIQ por campaña','Solicitudes','Avisos']);
  assert.equal(p.document.querySelector('#notices-badge').textContent,'1');assert(!p.document.querySelector('#notices-badge').hidden);
  // Avisos (HU36): bandeja con filtro, punto en los nuevos, conversación, respuesta y aviso nuevo con destinatario del servidor.
  p.document.querySelector('[data-workspace-view="notices"]').click();await wait();assert(!p.document.querySelector('#notices-panel').hidden);
  {const rows=p.document.querySelectorAll('#notices-list .notice-row');assert.equal(rows.length,1,'por defecto solo abiertos');assert(rows[0].classList.contains('is-new'));
  p.document.querySelectorAll('#notices-filter button')[1].click();assert.equal(p.document.querySelectorAll('#notices-list .notice-row').length,2);
  p.document.querySelector('#notices-list .notice-row').click();await wait();const detail=p.document.querySelector('#notice-detail');
  assert(detail.textContent.includes('El Orb no carga.'));assert.equal(p.document.querySelector('#notices-badge').hidden,true,'abrirlo lo marca como visto');
  detail.querySelector('textarea').value='Te mando otro cable.';detail.querySelector('form').dispatchEvent(new p.w.Event('submit',{cancelable:true}));await wait();
  assert.deepEqual(p.calls.find(c=>c.action==='notice-reply').body,{id:5,message:'Te mando otro cable.'});
  [...detail.querySelectorAll('button')].find(b=>b.textContent==='Cerrar aviso').click();await wait();assert.deepEqual(p.calls.find(c=>c.action==='notice-close').body,{id:5});
  p.document.querySelector('#notice-new').click();const nf=p.document.querySelector('#notice-form');assert.deepEqual([...nf.elements.to.options].map(o=>o.value),['','2']);
  nf.elements.to.value='2';nf.elements.subject.value='Recogida';nf.elements.message.value='Pasa por el laboratorio.';nf.dispatchEvent(new p.w.Event('submit',{cancelable:true}));await wait();
  assert.deepEqual(p.calls.find(c=>c.action==='notice-create').body,{to:2,subject:'Recogida',message:'Pasa por el laboratorio.'});assert(!p.document.querySelector('#notice-modal').open);p.document.querySelector('[data-workspace-view="workers"]').click();}
  assert.equal(p.document.querySelector('#worker-title').textContent,'Asignar admin de campaña');assert(shown('#worker-campaign'));assert(p.document.querySelector('#worker-campaign option[value="1"]'));
  assert.equal(p.document.querySelector('#worker-form').firstElementChild.querySelector('select').name,'campaign','la campaña va primero');
  p.document.querySelector('[data-workspace-view="overview"]').click();assert(p.document.querySelector('#campaigns-summary').textContent.includes('Campaña de prueba'));assert.equal(p.document.querySelector('#sum-campaigns').textContent,'1');
  p.document.querySelector('[data-workspace-view="orbs"]').click();assert(!p.document.querySelector('[data-view="orbs"]').hidden);{const o=p.document.querySelector('#orbs-by-campaign');assert(o.querySelector('.devices-table').textContent.includes('Campaña de prueba'));assert(o.querySelector('.devices-table').textContent.includes('Orb actualizado'));
  assert(o.querySelector('select[name=campana] option[value="1"]'));assert.equal(o.querySelectorAll('.device-panels .stat-button').length,4);}
  p.document.querySelector('[data-workspace-view="reports"]').click();assert(p.document.querySelector('[data-view="reports"]').hidden,'sin informes');assert(!p.calls.some(c=>c.action==='readings'));
  p.document.querySelector('[data-workspace-view="campaigns"]').click();assert(shown('#campaign-form'));
  // Crear campaña: solo nombre, zona (texto libre), fechas y activa. Umbrales, distancia y publicación son del admin de campaña.
  /**
   * Diseño lógico: ('#campaign-form') → cf() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const cf=p.document.querySelector('#campaign-form');assert.deepEqual([...cf.elements].filter(e=>e.name).map(e=>e.name),['id','name','zone','start','end']);assert(!cf.elements.zone.getAttribute('list'));
  assert.equal(p.document.querySelector('#campaign-submit').textContent,'Crear campaña');assert(p.document.querySelector('#campaign-cancel').hidden);
  p.document.querySelector('#campaigns-list .text-link').click();assert.equal(p.document.querySelector('#campaign-form-title').textContent,'Editar campaña: Campaña de prueba');assert.equal(p.document.querySelector('#campaign-submit').textContent,'Editar campaña');
  assert.equal(cf.elements.id.value,'1');p.document.querySelector('#campaign-cancel').click();await wait();assert.equal(cf.elements.id.value,'');assert.equal(p.document.querySelector('#campaign-form-title').textContent,'Crear campaña');
  p.document.querySelector('#campaigns-list .text-link').click();cf.elements.name.value='Campaña renombrada';cf.dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
  assert.deepEqual(p.calls.find(c=>c.action==='campaign').body,{id:'1',name:'Campaña renombrada',zone:'Campus',start:'2026-11-01',end:'2026-12-01'});
  // Resumen: 3 números y el estado como pastilla.
  p.document.querySelector('[data-workspace-view="overview"]').click();assert.equal(p.document.querySelectorAll('[data-view="overview"] .stat').length,3);assert(p.document.querySelector('#campaigns-summary .pill-on'));
  // Solicitudes: bandeja con filtro por estado (pendientes por defecto), buscador (también por nº), ficha y pasos de estado.
  {const d=p.document,rows=()=>d.querySelectorAll('#requests-list .inbox-row');d.querySelector('[data-workspace-view="requests"]').click();
  assert.deepEqual([...d.querySelectorAll('#requests-filter button')].map(b=>b.textContent),['Pendiente 1','Cerrada 1','Todas 2']);
  assert.equal(rows().length,1);assert(d.querySelector('#request-detail').textContent.includes('Ayto. Prueba'));assert(!d.querySelector('#request-detail').textContent.includes('UTC'));
  /**
   * Diseño lógico: ('#request-detail .reply-list a') → replies() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const replies=[...d.querySelectorAll('#request-detail .reply-list a')].map(a=>a.href);assert(replies[0].startsWith('https://mail.google.com/'));assert(replies.some(h=>h.startsWith('mailto:ana@x.es?subject=')));
  d.querySelectorAll('#requests-filter button')[2].click();assert.equal(rows().length,2);
  const q=d.querySelector('#requests-search');q.value='#8';q.dispatchEvent(new p.w.Event('input'));assert.equal(rows().length,1);assert(d.querySelector('#request-detail').textContent.includes('Colegio'));
  q.value='colegio';q.dispatchEvent(new p.w.Event('input'));assert.equal(rows().length,1);
  q.value='';q.dispatchEvent(new p.w.Event('input'));d.querySelectorAll('#requests-filter button')[0].click();
  // «Cerrar solicitud» guarda, la solicitud sigue seleccionada en la lista y el contador baja.
  d.querySelector('#request-detail .btn-primary').click();await new Promise(r=>setTimeout(r,20));
  assert.deepEqual(p.calls.filter(c=>c.action==='request-status').map(c=>c.body),[{id:7,status:'cerrada'}]);
  assert.equal(rows().length,1);assert(d.querySelector('#request-detail .request-saved').textContent.includes('Cerrada'));
  assert.equal(d.querySelector('#requests-filter button').textContent,'Pendiente 0');
  // Una solicitud no vuelve atrás (T38): los pasos anteriores no se pueden pulsar y «Reabrir» usa su propia acción.
  assert([...d.querySelectorAll('#request-detail .request-steps button')].every(b=>b.disabled));
  /**
   * Diseño lógico: ('#request-detail .request-actions button') → reopen() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const reopen=[...d.querySelectorAll('#request-detail .request-actions button')].find(b=>b.textContent==='Reabrir');assert(reopen);reopen.click();await new Promise(r=>setTimeout(r,20));
  assert.deepEqual(p.calls.filter(c=>c.action==='request-reopen').map(c=>c.body),[{id:7}]);assert.equal(p.calls.filter(c=>c.action==='request-status').length,1);
  assert(d.querySelector('#request-detail .request-history').textContent.includes('Reabierta'));}
  assert.deepEqual(p.errors,[]);p.close();
  // Admin de campaña: crea operadores de su campaña, registra Orb y exporta CSV; sin campañas nuevas ni solicitudes.
  user.permissions=[...ADMIN_CAMPANA];p=await page('portal.html');
  assert.equal(p.document.querySelector('#worker-title').textContent,'Añadir operador');assert(p.document.querySelector('#worker-campaign').closest('label').hidden);
  assert(p.document.querySelector('[data-workspace-view="requests"]').hidden);
  // «Mi campaña»: primera sección del admin de campaña, con su ficha y sus ajustes; sin Resumen ni Campañas.
  assert(!p.document.querySelector('[data-view="campaign"]').hidden);assert(p.document.querySelector('[data-view="overview"]').hidden);
  assert(p.document.querySelector('[data-workspace-view="campaigns"]').hidden);assert(p.document.querySelector('[data-workspace-view="overview"]').hidden);
  assert.equal(p.document.querySelector('#my-campaign-name').textContent,'Campaña de prueba');assert.equal(p.document.querySelector('#my-campaign-public').textContent,'Sí');
  const settings=p.document.querySelector('#campaign-settings-form');assert.equal(settings.elements.medium.value,'60');assert.equal(settings.elements.radius.value,'30');assert(settings.elements.public.checked);
  settings.elements.high.value='120';settings.dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
  assert.deepEqual(p.calls.find(c=>c.action==='campaign-settings').body,{medium:'60',high:'120',radius:'30',public:true});
  p.document.querySelector('[data-workspace-view="campaigns"]').click();assert(!p.document.querySelector('[data-view="campaign"]').hidden);
  // Equipo del admin de campaña: sus operadores con su Orb y acciones (editar, reenviar invitación).
  p.document.querySelector('[data-workspace-view="workers"]').click();const team=p.document.querySelector('#workers-list');
  assert.equal(p.document.querySelector('#workers-title').textContent,'Operadores de tu campaña');assert([...team.querySelectorAll('th')].some(th=>th.textContent==='AERIQ'));
  /**
   * Diseño lógico: ('.row-actions button') → buttons() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const buttons=[...team.querySelectorAll('.row-actions button')].map(b=>b.textContent);assert.deepEqual(buttons,['Editar','Reenviar invitación']);
  team.querySelectorAll('.row-actions button')[1].click();await wait();assert.deepEqual(p.calls.find(c=>c.action==='worker-invite').body,{id:2});
  team.querySelector('.row-actions button').click();assert(p.document.querySelector('#worker-modal').open);
  const wf=p.document.querySelector('#worker-edit-form');wf.elements.phone.value='600111222';wf.dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
  assert.deepEqual(p.calls.find(c=>c.action==='worker-edit').body,{id:'2',name:'Pau',last:'Operador',phone:'600111222'});
  p.document.querySelector('[data-workspace-view="devices"]').click();await wait();assert(!p.document.querySelector('#add-device').hidden);
  p.document.querySelector('[data-workspace-view="reports"]').click();p.document.querySelector('#report-form').dispatchEvent(new p.w.Event('submit',{cancelable:true,bubbles:true}));await wait();
  // Informe: cabecera con filtros, cifras, etiqueta de simulado y PDF como documento aparte (solo el informe).
  {const d=p.document,r=d.querySelector('#report-results'),pdf=d.querySelector('#print-report');assert(r.firstElementChild.classList.contains('report-header'));assert.equal(r.querySelectorAll('.report-kpi').length,5);assert.equal(r.querySelector('.report-badge').textContent,'Dato simulado');
  // Lectura de 68 ppb con umbral medio 60: un pico moderado con su hora y valor, y la franja de esa hora regular.
  assert(r.querySelector('.report-peaks').textContent.includes('1 pico de ozono'));
  // HU35: las descartadas se listan aparte con su motivo y no cuentan en el informe.
  assert(r.querySelector('.report-discarded').textContent.includes('742 ppb'));assert(r.querySelector('.report-discarded').textContent.includes('Fuera de rango'));assert(r.querySelector('.report-peaks li').textContent.includes('68 ppb · nivel moderado'));
  assert.equal(r.querySelectorAll('.report-franja').length,4);assert.equal(r.querySelectorAll('.report-franja.is-regular').length,1);assert(r.querySelector('.report-verdict-text').textContent.startsWith('La calidad del aire es regular'));
  const ex=r.querySelector('.report-exceedances table');assert.equal(ex.rows[0].cells[1].textContent,'Origen');assert.equal(ex.rows[1].cells[1].textContent,'Simulado');
  assert(!pdf.hidden&&!pdf.disabled);p.w.HTMLCanvasElement.prototype.toDataURL=()=>'data:image/png;base64,AAAA';pdf.click();await wait();const doc=d.querySelector('iframe').contentDocument;
  assert.equal(doc.querySelector('h1').textContent,'Informe ambiental AERIQ');assert(doc.querySelector('.desc').textContent.includes('simulados'));assert(doc.querySelector('.simwarn'));assert(doc.querySelector('.peaks'));assert(doc.body.textContent.includes('Veredicto por franja horaria'));assert(!doc.querySelector('form,nav'));}
  const csv=p.document.querySelector('#export-report');assert(!csv.hidden&&!csv.disabled);csv.click();await wait();
  assert(p.calls.some(c=>c.action==='readings'&&c.body===null)&&p.calls.filter(c=>c.action==='readings').length===2);assert.deepEqual(p.errors,[]);p.close();user.permissions=[...ADMIN];
  // Formulario comercial: catálogos de la BD, preselección por URL, campos de estudio, teléfono y validación en línea.
  p=await page('solicitar.html','?interes=kits&org=empresa');await wait();{
    /**
     * Diseño lógico: ('#service-form') → d() → resultado de la operación; según scripts/README.md.
     * Descripción: Describe la operación realizada y su resultado.
     */
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
    /**
     * Diseño lógico: (c=>c.path==='/public.php'&&c.body) → sent() → resultado de la operación; según scripts/README.md.
     * Descripción: Describe la operación realizada y su resultado.
     */
    const sent=p.calls.find(c=>c.path==='/public.php'&&c.body)?.body;
    assert.deepEqual({interest:sent.interest,orgType:sent.orgType,phone:sent.phone,objectives:sent.objectives,consent:sent.consent},{interest:'kits',orgType:'empresa',phone:'612345678',objectives:['otro'],consent:true});
    assert(!('quantity' in sent)&&!('duration' in sent));
    assert(f.hidden);assert(!d.querySelector('#request-success').hidden);assert(d.querySelector('#success-title').textContent.includes('7'));
    // Tras enviar solo queda volver a la portada.
    const exits=[...d.querySelectorAll('#request-success a,#request-success button')];
    assert.equal(exits.length,1);assert.equal(exits[0].textContent,'Volver al menú');assert(exits[0].href.endsWith('/index.html'));assert(!d.querySelector('#new-request'));
    assert.deepEqual(p.errors,[]);p.close();
  }
  // Los enlaces antiguos (?service=estudio) siguen preseleccionando el servicio.
  p=await page('solicitar.html','?service=estudio');await wait();assert(p.document.querySelector('[name=interest][value=estudio]').checked);assert(!p.document.querySelector('[data-study]').hidden);assert.deepEqual(p.errors,[]);p.close();
  p=await page('mapa.html');assert(p.calls.some(c=>c.path==='/public.php'));assert(p.document.querySelector('#map-sensor-list').textContent.includes('68 ppb'));p.document.querySelector('[data-source=mine]').click();await wait();assert(p.calls.some(c=>c.path==='/portal.php'&&c.action==='map'));assert.deepEqual(p.errors,[]);p.close();
  // Header: sin sesión «Iniciar sesión»; con sesión «Mi panel»/«Mi cuenta» según el rol y menú con tu nombre.
  globalThis.noSession=true;p=await page('index.html');let link=p.document.querySelector('#account-link');
  assert(p.document.querySelector('.site-header').hasAttribute('data-ready'));assert.equal(link.textContent,'Iniciar sesión');assert(!p.document.querySelector('.account-menu'));
  // Sin sesión: el menú empieza por «El AERIQ» (sin «Mapa»); al mapa se va con el botón «Ver el mapa».
  assert.deepEqual([...p.document.querySelectorAll('.main-nav a')].map(a=>a.textContent),['El AERIQ','Organizaciones','Nosotros','Contacto']);assert(!p.document.querySelector('.header-actions .btn').hidden);p.close();globalThis.noSession=false;
  /**
   * Diseño lógico: ('.main-nav a') → navText() → resultado de la operación; según scripts/README.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const navText=p=>[...p.document.querySelectorAll('.main-nav a')].map(a=>a.textContent);
  for(const [perms,page_,items] of [[ADMIN,'index.html',['Inicio','Mapa','Mi panel']],[ADMIN_CAMPANA,'solicitar.html',['Inicio','Mapa','Mi campaña']],[['mapa.organizacion','mediciones.registrar'],'mapa.html',['Inicio','Mapa','Mi campaña']]]){
    user.permissions=[...perms];p=await page(page_);link=p.document.querySelector('#account-link');
    // Con sesión: menú central privado, sin el enlace de acceso ni el botón «Ver el mapa».
    assert.deepEqual(navText(p),items,page_);assert(link.hidden);assert([...p.document.querySelectorAll('.header-actions .btn')].every(b=>b.hidden));
    assert.equal(p.document.documentElement.dataset.session,'user');
    if(items.includes('Mi panel'))assert(p.document.querySelector('.main-nav a[href="portal.html"]'));
    if(items.includes('Mi campaña'))assert(p.document.querySelector('.main-nav a[href="portal.html#campaign"]'));
    const menu=p.document.querySelector('#account-link + .account-menu');assert(menu,page_);assert.equal(menu.querySelector('.account-avatar').textContent,'AA');
    assert(menu.querySelector('a[href="perfil.html"]'));assert.deepEqual([...menu.querySelectorAll('.account-menu-list a,.account-menu-list button')].map(n=>n.textContent),['Mi cuenta','Cerrar sesión'],page_);menu.querySelector('button.danger').click();await wait();
    assert(p.calls.some(c=>c.path==='/auth.php'&&c.action==='logout'),page_);assert.deepEqual(p.errors,[]);p.close();
  }
  // Operador: «Mi campaña» de solo consulta (su campaña, su Orb y sus últimas lecturas) y «Avisos» con su admin.
  user.permissions=['mapa.organizacion','mediciones.registrar'];p=await page('portal.html');
  assert(!p.document.querySelector('.workspace-nav').hidden);assert.deepEqual([...p.document.querySelectorAll('.workspace-nav a')].filter(a=>!a.hidden).map(a=>a.firstChild.textContent.trim()),['Mi campaña','Avisos']);assert(!p.document.querySelector('#operator-campaign').hidden);assert(p.document.querySelector('[data-view="campaign"][data-perm="equipo.gestionar"]').hidden);
  assert.equal(p.document.querySelector('#op-campaign-name').textContent,'Campaña de prueba');assert.equal(p.document.querySelector('#op-orb').textContent,'Orb original');assert(p.document.querySelector('#op-battery').textContent.startsWith('Batería: 57 % · media'));assert.equal(p.document.querySelector('#op-level-mid').textContent,'60 – 100 ppb');assert(p.document.querySelector('#op-admins a').href.startsWith('mailto:marta@'));
  assert(p.document.querySelector('#op-readings').textContent.includes('42 ppb'));assert(!p.document.querySelector('#operator-campaign form,#operator-campaign button'));
  assert(!p.calls.some(c=>c.action==='dashboard'));assert.deepEqual(p.errors,[]);p.close();user.permissions=[...ADMIN];
  // Enlace de recuperación: si ha caducado, se avisa al abrirlo (en rojo) y vuelve el formulario para pedir otro.
  globalThis.noSession=true;p=await page('recuperar.html','#email=ana@example.test&code=123456');assert(p.calls.some(c=>c.action==='check-reset'));assert(!p.document.querySelector('#reset-form').hidden);p.close();
  globalThis.resetExpired=true;p=await page('recuperar.html','#email=ana@example.test&code=123456');assert(p.document.querySelector('#reset-form').hidden);assert(!p.document.querySelector('#forgot-form').hidden);
  assert.equal(p.document.querySelector('#forgot-form').elements.email.value,'ana@example.test');assert.equal(p.document.querySelector('.auth-card > p').dataset.tone,'error');assert(p.document.querySelector('.auth-card > p').textContent.includes('caducado'));
  assert.deepEqual(p.errors,[]);p.close();globalThis.resetExpired=false;globalThis.noSession=false;
  // El operador no ve en Mi cuenta enlaces al panel ni a la flota (le darían error).
  // Mi cuenta es solo tu cuenta: sin accesos al panel ni a la flota dentro de la página (eso va en el header).
  p=await page('perfil.html');assert(!p.document.querySelector('#pedidos'));assert(!p.document.querySelector('main a[href^="portal.html"],main a[href="sensores.html"]'));p.close();
  user.permissions=[...ADMIN];p=await page('perfil.html');assert(!p.document.querySelector('main a[href^="portal.html"]'));p.close();
  console.log('AERIQ DOM OK: portada/3D, galería, flota, panel por secciones, informes, formularios, presupuesto y mapa público/privado.');
})().then(()=>process.exit(0),e=>{console.error(e);process.exit(1);}); // salida explícita: el contador de avisos usa setInterval
