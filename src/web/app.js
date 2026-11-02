/* AERIQ · JavaScript GLOBAL · Interacciones de demostración, sin backend ni cobros. */
'use strict';
const AERIQ_PRICE = 249;
const money = (amount) => new Intl.NumberFormat('es-ES', {style:'currency',currency:'EUR'}).format(amount);
const store={get(k,def=null){try{let v=localStorage.getItem('aeriq_'+k);return v===null?def:JSON.parse(v)}catch{return def}},set(k,v){try{localStorage.setItem('aeriq_'+k,JSON.stringify(v))}catch{}}};
function toast(msg){const n=document.querySelector('#toast');if(!n)return;n.textContent=msg;n.classList.add('show');clearTimeout(toast.t);toast.t=setTimeout(()=>n.classList.remove('show'),3600)}
const $=(s,root=document)=>root.querySelector(s),$$=(s,root=document)=>[...root.querySelectorAll(s)];
function cartCount(){return Math.max(0,Math.min(99,Number(store.get('cart',0))||0))}
function setCart(n){store.set('cart',Math.max(0,Math.min(99,n)));updateCart();}
function updateCart(){let n=cartCount(),badge=$('#cart-badge');if(badge){badge.textContent=n;badge.hidden=n===0;}let count=$('#cart-product-count');if(count)count.textContent=`${n} ${n===1?'producto':'productos'}`;let qty=$('#cart-qty');if(qty)qty.textContent=n;let summary=$('#summary-quantity');if(summary)summary.textContent=n;let sub=$('#summary-subtotal');if(sub)sub.textContent=money(n*AERIQ_PRICE);let line=$('#cart-line-price');if(line)line.textContent=money(n*AERIQ_PRICE);let cart=$('#cart-contents');if(cart){const old=$('.cart-product',cart);if(n===0){cart.innerHTML='<div class="cart-empty"><strong>Tu carrito está vacío.</strong><p>Descubre el sensor AERIQ O₃ para añadirlo a tu carrito.</p><a href="sensor.html" class="btn btn-primary">Ver sensor ↗</a></div>';}else if(!old){location.reload()}}let go=$('#go-checkout');if(go)go.classList.toggle('hidden',n===0);let cq=$('#checkout-qty');if(cq)cq.textContent=n;let cp=$('#checkout-product-total');if(cp)cp.textContent=money(n*AERIQ_PRICE);let cs=$('#checkout-subtotal');if(cs)cs.textContent=money(n*AERIQ_PRICE);refreshCheckout();}
function refreshCheckout(){let total=$('#checkout-total');if(!total)return;let shipping=document.querySelector('input[name="delivery"]:checked')?.value==='pickup'?0:6.9;$('#checkout-shipping').textContent=money(shipping);total.textContent=money(cartCount()*AERIQ_PRICE+shipping)}
function initNavigation(){
  const toggle=$('[data-menu-toggle]');
  toggle?.addEventListener('click',()=>$('.main-nav')?.classList.toggle('open'));
  if(typeof AeriqAuth==='undefined')return;
  AeriqAuth.session().then(s=>{
    const a=$('#account-link');
    if(a){a.href=s.user?'perfil.html':'login.html';a.textContent=s.user?'Mi cuenta':'Entrar';}
  }).catch(()=>{});
}
function authFeedback(error){
  const msg=error instanceof Error?error.message:String(error);
  toast(msg);
}
function authSubmitButton(form, busy){
  const button=form?.querySelector('button[type="submit"]');
  if(button)button.disabled=Boolean(busy);
}
function initForms(){
  $$('[data-demo-form]').forEach(f=>f.addEventListener('submit',e=>{e.preventDefault();if(f.reportValidity())toast(f.dataset.success||'Esta función sigue en demostración.')}));
  $$('[data-toggle-password]').forEach(b=>b.addEventListener('click',()=>{
    const input=document.getElementById(b.dataset.togglePassword);if(!input)return;
    input.type=input.type==='password'?'text':'password';b.textContent=input.type==='password'?'Mostrar':'Ocultar';
  }));
  $$('[data-social]').forEach(b=>b.addEventListener('click',()=>toast(`Google/Apple requiere configurar OAuth oficial; el botón todavía no inicia sesión.`)));
  $('#login-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;authSubmitButton(f,true);
    try{
      const email=f.elements.namedItem('email').value;
      await AeriqAuth.post('login',{email,password:f.elements.namedItem('password').value});
      location.href='perfil.html';
    }catch(error){
      if(error.needsVerification)location.href='verificar.html?email='+encodeURIComponent(f.elements.namedItem('email').value);
      else authFeedback(error);
    }finally{authSubmitButton(f,false)}
  });
  $('#register-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;
    const password=f.elements.namedItem('password').value;
    if(password!==f.elements.namedItem('confirm').value){toast('Las contraseñas no coinciden.');return}
    authSubmitButton(f,true);
    try{
      const email=f.elements.namedItem('email').value.trim();
      await AeriqAuth.post('register',{
        name:f.elements.namedItem('name').value,last:f.elements.namedItem('last').value,
        email,password
      });
      location.href='verificar.html?email='+encodeURIComponent(email);
    }catch(error){authFeedback(error)}finally{authSubmitButton(f,false)}
  });
  $('#verify-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;authSubmitButton(f,true);
    try{
      await AeriqAuth.post('verify',{email:f.elements.namedItem('email').value,code:f.elements.namedItem('code').value});
      location.href='perfil.html';
    }catch(error){authFeedback(error)}finally{authSubmitButton(f,false)}
  });
  const verifyEmail=$('#verify-email');
  if(verifyEmail){verifyEmail.value=new URLSearchParams(location.search).get('email')||'';}
  $('#resend-code')?.addEventListener('click',async()=>{
    const email=verifyEmail?.value;if(!email){toast('Introduce un correo válido.');return}
    try{const data=await AeriqAuth.post('resend',{email});toast(data.message||'Si la cuenta está pendiente, recibirás otro código.')}catch(error){authFeedback(error)}
  });
  $('#forgot-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;authSubmitButton(f,true);
    try{
      const email=f.elements.namedItem('email').value;
      const data=await AeriqAuth.post('forgot',{email});
      $('#reset-email').value=email;
      $('#reset-form').hidden=false;
      toast(data.message||'Revisa tu correo.');
    }catch(error){authFeedback(error)}finally{authSubmitButton(f,false)}
  });
  $('#reset-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;
    const password=f.elements.namedItem('password').value;
    if(password!==f.elements.namedItem('confirm').value){toast('Las contraseñas no coinciden.');return}
    authSubmitButton(f,true);
    try{
      await AeriqAuth.post('reset-password',{email:f.elements.namedItem('email').value,code:f.elements.namedItem('code').value,password});
      location.href='perfil.html';
    }catch(error){authFeedback(error)}finally{authSubmitButton(f,false)}
  });
}
function initSensorProduct(){
 let n=1;
 const main=$('#product-main-image');
 function show(){const el=$('#product-qty');if(el)el.textContent=n}
 $('[data-qty-minus]')?.addEventListener('click',()=>{n=Math.max(1,n-1);show()});
 $('[data-qty-plus]')?.addEventListener('click',()=>{n=Math.min(99,n+1);show()});
 $('#add-to-cart')?.addEventListener('click',()=>{setCart(cartCount()+n);toast(`${n} sensor(es) añadidos al carrito.`)});
 $$('[data-angle]').forEach(button=>button.addEventListener('click',()=>{
   $$('[data-angle]').forEach(b=>{b.classList.toggle('active',b===button);b.setAttribute('aria-pressed',String(b===button))});
   if(main){main.src=button.dataset.imageSrc;main.alt=button.dataset.imageAlt||'Sensor AERIQ O₃'}
 }));
 $$('[data-angle]').forEach(b=>b.setAttribute('aria-pressed',String(b.classList.contains('active'))));
 show();
}
function initCart(){ $('[data-cart-minus]')?.addEventListener('click',()=>setCart(cartCount()-1));$('[data-cart-plus]')?.addEventListener('click',()=>setCart(cartCount()+1));$('#remove-cart')?.addEventListener('click',()=>{setCart(0);toast('Producto eliminado.')});$('#clear-cart')?.addEventListener('click',()=>{setCart(0);toast('Carrito vaciado.')});updateCart()}
function initCheckout(){if(!$('input[name="delivery"]'))return;if(!cartCount()){toast('Añade un sensor al carrito para continuar.');setTimeout(()=>location.href='carrito.html',800);return}
 $$('input[name="delivery"]').forEach(x=>x.addEventListener('change',()=>{let home=x.checked&&x.value==='home';$('#address-fields').hidden=!home;$('#pickup-fields').hidden=home;$$('.choice-grid .choice').forEach(c=>c.classList.toggle('selected',$('input',c).checked));refreshCheckout()}));
 $$('input[name="payment"]').forEach(x=>x.addEventListener('change',()=>$$('.payment-methods .choice').forEach(c=>c.classList.toggle('selected',$('input',c).checked))));
 $$('input[name="address-source"]').forEach(x=>x.addEventListener('change',()=>{if(x.checked&&x.value==='saved'){const address=$('input[name="address"]');if(address)address.value='C/ del Jardín, 12';$('input[name="zip"]').value='46001';$('input[name="city"]').value='Valencia';$('input[name="province"]').value='Valencia'}}));
 $('#checkout-form')?.addEventListener('submit',e=>{e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;if($('input[name="delivery"]:checked').value==='pickup'){toast('La recogida no está disponible: faltan puntos confirmados. No se ha realizado ningún pago.');return}toast('Compra de demostración: no se ha realizado ningún pedido ni cobro.')});refreshCheckout();}
async function initMap(){
  const host=$('#real-gandia-map');if(!host)return;
  const campus=[38.996651,-0.166009];
  let sensors=[],selected=null,source='all';
  const list=$('#map-sensor-list');const status=$('#map-loading');
  const colors={green:'#219c72',orange:'#eea22d',red:'#e35b5d'};
  if(typeof L==='undefined'){
    if(status)status.textContent='No se ha podido cargar Leaflet. Comprueba la conexión a Internet.';
    return;
  }
  const mapVisual=host.closest('.map-visual');
  function setMapStyle(style){
    const mode=style==='light'?'light':'dark';
    host.classList.toggle('map-theme-dark',mode==='dark');
    mapVisual?.classList.toggle('is-dark-map',mode==='dark');
    $$('[data-map-style]').forEach(b=>{const active=b.dataset.mapStyle===mode;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active))});
    store.set('mapStyle',mode);
  }
  $$('[data-map-style]').forEach(b=>b.addEventListener('click',()=>setMapStyle(b.dataset.mapStyle)));
  setMapStyle(store.get('mapStyle','dark'));
  const map=L.map(host,{zoomControl:false,scrollWheelZoom:true,preferCanvas:true}).setView([38.9974,-0.1633],16);
  const tileLayer=L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
    maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'
  }).addTo(map);
  tileLayer.on('load',()=>{if(status)status.hidden=true});
  const circles=L.layerGroup().addTo(map),pins=L.layerGroup().addTo(map),layers=new Map();
  const esc=(v)=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function choose(id,pan){
    const s=sensors.find(x=>x.id===id);if(!s)return;
    selected=s;
    $('#selected-id').textContent=s.id;
    $('#map-id').textContent='#'+s.id;
    $('#map-value').textContent=String(s.value);
    $('#map-location').textContent=s.name;
    $('#map-quality').textContent=s.quality;
    $$('.sensor-list-item').forEach(b=>b.classList.toggle('chosen',b.dataset.sensorId===id));
    layers.forEach(({marker,sensor})=>{
      marker.getElement()?.querySelector('.aeriq-map-marker')?.classList.toggle('active',sensor.id===id);
    });
    if(pan)map.flyTo(s.coordinates,Math.max(map.getZoom(),16),{duration:.45});
  }
  function renderList(){
    if(!list)return;
    if(source==='mine'){
      list.innerHTML='<p class="map-empty-sensors">No hay sensores propios conectados. Selecciona «Todos» para ver los simulados de MariaDB.</p>';
      return;
    }
    if(!sensors.length){list.innerHTML='<p class="map-empty-sensors">No hay datos simulados disponibles en MariaDB.</p>';return;}
    list.innerHTML=sensors.map(s=>`<button class="sensor-list-item${s.id===selected?.id?' chosen':''}" type="button" data-sensor-id="${esc(s.id)}"><strong>${esc(s.name)}</strong><span>#${esc(s.id)} · ${s.value} µg/m³ · ${esc(s.quality)} (SIMULADO)</span><i class="sensor-state ${s.color}"></i></button>`).join('');
  }
  function toggleSource(){
    if(source==='all'){
      pins.addTo(map);circles.addTo(map);
      if(!$('#marker-toggle')?.checked)map.removeLayer(pins);
      if(!$('#heat-toggle')?.checked)map.removeLayer(circles);
    }else{map.removeLayer(pins);map.removeLayer(circles)}
    renderList();
  }
  async function refreshSensors(){
    try {
      if(typeof AeriqPbio==='undefined')throw new Error('Cliente PBIO no cargado');
      const result=await AeriqPbio.listarMapaDemo();
      if(!Array.isArray(result))throw new Error('Respuesta incorrecta del servidor');
      const parsed=result.filter(s=>s&&s.simulado===true&&Array.isArray(s.coordinates)&&s.coordinates.length===2&&Number.isFinite(Number(s.coordinates[0]))&&Number.isFinite(Number(s.coordinates[1]))&&Number.isFinite(Number(s.value))&&['bajo','moderado','alto'].includes(s.quality))
        .map(s=>({id:String(s.id),name:String(s.name),coordinates:s.coordinates.map(Number),value:Number(s.value),quality:s.quality,color:({bajo:'green',moderado:'orange',alto:'red'})[s.quality]}));
      sensors=parsed;
      const selectedId=selected?.id;
      pins.clearLayers();circles.clearLayers();layers.clear();
      if(sensors.length) {
        selected=sensors.find(x=>x.id===selectedId)||sensors.find(x=>x.id==='017')||sensors[0];
        for(const s of sensors){
          const circle=L.circle(s.coordinates,{radius:125,color:colors[s.color],weight:0,fillColor:colors[s.color],fillOpacity:.20,interactive:false}).addTo(circles);
          const icon=L.divIcon({className:'aeriq-marker-container',html:`<div class="aeriq-map-marker ${s.color}${s.id===selected.id?' active':''}"><span class="marker-center"></span></div>`,iconSize:[32,32],iconAnchor:[16,16]});
          const marker=L.marker(s.coordinates,{icon,keyboard:true,title:`Sensor SIMULADO #${s.id} · ${s.name}`}).addTo(pins);
          marker.on('click',()=>choose(s.id,true));layers.set(s.id,{marker,circle,sensor:s});
        }
        choose(selected.id,false);
      }else{
        selected=null;
        $('#map-location').textContent='Sin sensores simulados en la base de datos';
        $('#map-value').textContent='—';$('#map-id').textContent='—';$('#selected-id').textContent='—';
      }
      toggleSource();
      if(status && status.textContent?.startsWith('Error'))status.hidden=true;
    }catch(e){
      console.warn('AERIQ mapa-demo',e);
      if(list)list.textContent='No se pueden consultar los sensores simulados de MariaDB. Revisa api.php, la migración SQL y la conexión.';
      if(!sensors.length){$('#map-location').textContent='Servidor no disponible';$('#map-value').textContent='—';}
      // El mapa de calles sigue utilizable, pero no se inventan resultados locales.
    }
  }
  list?.addEventListener('click',e=>{const b=e.target.closest('[data-sensor-id]');if(b)choose(b.dataset.sensorId,true)});
  $$('[data-source]').forEach(b=>b.addEventListener('click',()=>{
    source=b.dataset.source;$$('[data-source]').forEach(v=>v.classList.toggle('selected',v===b));toggleSource();
  }));
  $('#toggle-map-layers')?.addEventListener('click',()=>{const l=$('#map-layer-options');if(l)l.hidden=!l.hidden});
  $('#heat-toggle')?.addEventListener('change',e=>{if(source==='all'&&e.target.checked)circles.addTo(map);else map.removeLayer(circles)});
  $('#marker-toggle')?.addEventListener('change',e=>{if(source==='all'&&e.target.checked)pins.addTo(map);else map.removeLayer(pins)});
  $('#map-reset')?.addEventListener('click',()=>map.flyTo(campus,16,{duration:.6}));
  ['#map-zoom-in','#zoomin-map'].forEach(x=>$(x)?.addEventListener('click',()=>map.zoomIn()));
  $('#zoomout-map')?.addEventListener('click',()=>map.zoomOut());
  const search=$('#map-search');
  function searchPlace(){
    const q=(search?.value||'').trim().toLowerCase();
    if(!q)return;
    if(q.includes('upv')||q.includes('campus')||q.includes('universidad')){map.flyTo(campus,17,{duration:.6});choose('011',false)}
    else if(q.includes('playa')||q.includes('platja')){map.flyTo([38.9990,-0.1589],16,{duration:.6});choose('017',false)}
    else if(q.includes('grau')||q.includes('grao'))map.flyTo([38.9922,-0.1656],15,{duration:.6});
    else toast('Búsqueda de prueba: UPV, playa de Gandía o Grau de Gandía.');
  }
  search?.addEventListener('change',searchPlace);
  search?.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();searchPlace();search.blur()}});
  $('#export-sensor')?.addEventListener('click',()=>{
    if(!selected){toast('Selecciona un sensor de MariaDB.');return;}
    const s=selected;
    const csv='id_sensor;zona_aproximada;latitud;longitud;ozono_ug_m3;calidad;tipo_de_dato\n'+
      `${s.id};"${s.name.replaceAll('"','""')}";${s.coordinates[0]};${s.coordinates[1]};${s.value};${s.quality};SIMULADO\n`;
    const blob=new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'});
    const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`aeriq_gandia_${s.id}_SIMULADO.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),2500);
    toast('CSV simulado exportado desde los datos de MariaDB.');
  });
  await refreshSensors();
  // Recargar datos cuando se editen coordenadas en phpMyAdmin.
  setInterval(()=>{if(!document.hidden)void refreshSensors()},30000);
  requestAnimationFrame(()=>map.invalidateSize());
  window.addEventListener('resize',()=>map.invalidateSize());
}
function initModelViewer(){
 const model=$('.sensor-model-viewer');if(!model)return;
 const shell=model.closest('.sensor-3d-shell');
 function ready(){shell?.classList.add('is-ready')}
 model.addEventListener('load',ready,{once:true});
 model.addEventListener('error',()=>{
   const label=$('.studio-loading');if(label)label.textContent='No se ha podido cargar el modelo 3D. Comprueba la conexión y el archivo GLB.';
 });
 if(model.loaded)ready();
 if(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)model.removeAttribute('auto-rotate');
}
function initDevices(){const rows=$$('.device-row');let filter='all';function apply(){let q=($('#device-search')?.value||'').toLowerCase().trim();let count=0;rows.forEach(r=>{let ok=(filter==='all'||(filter==='active')===(r.dataset.active==='true'))&&(!q||r.dataset.name.includes(q)||r.dataset.id.toLowerCase().includes(q));r.classList.toggle('hidden',!ok);if(ok)count++});let tb=$('.table-bottom');if(tb)tb.firstChild.textContent=`${count} de ${rows.length} sensores `}
 $('#device-search')?.addEventListener('input',apply);$$('[data-device-filter]').forEach(b=>b.addEventListener('click',()=>{filter=b.dataset.deviceFilter;$$('[data-device-filter]').forEach(a=>a.classList.toggle('selected',a===b));apply()}));
 $$('[data-private]').forEach(x=>x.addEventListener('change',()=>toast('Preferencia guardada solo visualmente (sin servidor).')));
 const modal=$('#device-modal'),edit=$('#device-new-name');let current=null;$$('[data-edit-device]').forEach(b=>b.addEventListener('click',()=>{current=b.closest('tr');edit.value=$('.device-title',current).textContent;modal.showModal()}));$('#device-modal-close')?.addEventListener('click',()=>modal.close());$('#device-edit-form')?.addEventListener('submit',e=>{e.preventDefault();if(current&&edit.value.trim()){$('.device-title',current).textContent=edit.value.trim();current.dataset.name=edit.value.trim().toLowerCase();toast('Nombre actualizado en esta demostración.');apply()}modal.close()});$('#add-device')?.addEventListener('click',()=>toast('Para registrar un sensor real será necesario conectar el backend.'))}
async function initProfile(){
  const form=$('#profile-form');if(!form)return;
  try{
    const session=await AeriqAuth.session();
    if(!session.user){location.href='login.html';return;}
    const user=session.user;
    form.elements.namedItem('name').value=user.name||'';
    form.elements.namedItem('last').value=user.last||'';
    form.elements.namedItem('email').value=user.email||'';
    form.elements.namedItem('phone').value=user.phone||'';
    $('#sidebar-user').textContent=[user.name,user.last].join(' ');
    $('#sidebar-email').textContent=user.email;
    form.hidden=false;
  }catch(error){toast('No se pudo comprobar la sesión. Revisa Plesk.');return;}
  form.addEventListener('submit',async e=>{
    e.preventDefault();if(!form.reportValidity())return;authSubmitButton(form,true);
    try{
      const u=(await AeriqAuth.post('profile',{
        name:form.elements.namedItem('name').value,last:form.elements.namedItem('last').value,
        phone:form.elements.namedItem('phone').value
      })).user;
      $('#sidebar-user').textContent=u.name+' '+u.last;$('#sidebar-email').textContent=u.email;
      toast('Perfil guardado en MariaDB.');
    }catch(error){authFeedback(error)}finally{authSubmitButton(form,false)}
  });
  $('#password-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;
    const pass=f.elements.namedItem('password').value;
    if(pass!==f.elements.namedItem('confirm').value){toast('Las contraseñas no coinciden.');return}
    authSubmitButton(f,true);
    try{
      await AeriqAuth.post('change-password',{current:f.elements.namedItem('current').value,password:pass});
      f.reset();toast('Contraseña actualizada.');
    }catch(error){authFeedback(error)}finally{authSubmitButton(f,false)}
  });
  $('#logout')?.addEventListener('click',async()=>{
    try{await AeriqAuth.post('logout');location.href='login.html'}catch(error){authFeedback(error)}
  });
  $('#delete-address')?.addEventListener('click',()=>toast('Las direcciones siguen siendo una maqueta; no se guardan en servidor.'));
  $('#edit-address')?.addEventListener('click',()=>toast('Gestión de direcciones pendiente de backend.'));
  $('#add-address')?.addEventListener('click',()=>toast('Gestión de direcciones pendiente de backend.'));
}
/* Persisted records from Sprint 0: genuine DB rows, test values emitted by firmware. No geo/conversion. */
function initPbioReadings() {
  const panel = $('#pbio-live-panel');
  if (!panel) return;
  const label = $('#pbio-api-status');
  const rows = $('#pbio-live-readings');
  let activeRequest = false;
  function cell(parent, tag, cls, value) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    n.textContent = String(value);
    parent.appendChild(n);
    return n;
  }
  async function refresh() {
    if (activeRequest || document.hidden) return;
    activeRequest = true;
    label.textContent = 'Consultando la base de datos…';
    try {
      if (typeof AeriqPbio === 'undefined') throw new Error('Cliente API no cargado');
      // Una única petición, limitada a 500 filas por la propia lógica del backend.
      const measurements = await AeriqPbio.listarMedidas();
      if (!Array.isArray(measurements)) throw new Error('Respuesta inesperada del servidor');
      rows.replaceChildren();
      // API orders rows newest first; first reading for each sensor + type is the latest.
      const latest = new Map();
      for (const m of measurements) {
        const type = Number(m.tipoMedidaId);
        if (type !== 12 && type !== 14) continue;
        const key = String(m.dispositivoId || '') + ':' + type;
        if (!latest.has(key)) latest.set(key, m);
      }
      if (!latest.size) {
        label.textContent = 'Conexión con PHP correcta. No hay mediciones guardadas.';
        cell(rows,'p','pbio-empty','Activa «Compartir con la red» en Android y selecciona el sensor BLE.');
        return;
      }
      label.textContent = `${latest.size} tipo(s) de lectura encontrados · ${new Date().toLocaleTimeString('es-ES')}`;
      for (const m of [...latest.values()].slice(0,20)) {
        const type = Number(m.tipoMedidaId);
        const card = document.createElement('article'); card.className = 'pbio-reading';
        const top = document.createElement('div'); top.className = 'pbio-reading-top';
        cell(top,'strong','',type === 14 ? 'Ozono O₃' : 'Temperatura');
        cell(top,'span','pbio-real-tag','BD');
        card.appendChild(top);
        const val = Number(m.valor);
        cell(card,'div','pbio-measurement',`${Number.isFinite(val) ? val : '—'} ${type === 14 ? 'ppb' : '°C'}`);
        cell(card,'small','pbio-device-name',`${m.dispositivo || 'Sensor'} · ${m.dispositivoId || ''}`);
        cell(card,'small','pbio-datetime',m.fechaHora ? `Registrado: ${m.fechaHora.replace('T',' ')}` : 'Sin fecha');
        rows.appendChild(card);
      }
    } catch (error) {
      rows.replaceChildren();
      label.textContent = `Servidor no disponible: ${error.message || 'Error de conexión'}`;
      cell(rows,'p','pbio-empty','No se muestran datos ficticios como alternativa a un fallo de conexión.');
    } finally { activeRequest = false; }
  }
  $('#pbio-refresh')?.addEventListener('click',refresh);
  refresh();
  // Detener consultas cuando la pestaña está oculta.
  const timer = window.setInterval(refresh, 15000);
  window.addEventListener('pagehide', () => clearInterval(timer), {once:true});
  document.addEventListener('visibilitychange', () => {if (!document.hidden) refresh()});
}

function init(){initNavigation();initForms();initSensorProduct();initCart();initCheckout();initMap();initModelViewer();initDevices();initProfile();initPbioReadings();updateCart();}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
