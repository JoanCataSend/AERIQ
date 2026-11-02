/* AERIQ · JavaScript GLOBAL · Interacciones de demostración, sin backend ni cobros. */
'use strict';
const AERIQ_PRICE = 249;
const money = (amount) => new Intl.NumberFormat('es-ES', {style:'currency',currency:'EUR'}).format(amount);
const store={get(k,def=null){try{let v=localStorage.getItem('aeriq_'+k);return v===null?def:JSON.parse(v)}catch{return def}},set(k,v){try{localStorage.setItem('aeriq_'+k,JSON.stringify(v))}catch{}}};
function toast(msg){const n=document.querySelector('#toast');if(!n)return;n.textContent=msg;n.classList.add('show');clearTimeout(toast.t);toast.t=setTimeout(()=>n.classList.remove('show'),3600)}
const $=(s,root=document)=>root.querySelector(s),$$=(s,root=document)=>[...root.querySelectorAll(s)];
function cartCount(){return Math.max(0,Math.min(99,Number(store.get('cart',1))||0))}
function setCart(n){store.set('cart',Math.max(0,Math.min(99,n)));updateCart();}
function updateCart(){let n=cartCount(),badge=$('#cart-badge');if(badge)badge.textContent=n;let count=$('#cart-product-count');if(count)count.textContent=`${n} ${n===1?'producto':'productos'}`;let qty=$('#cart-qty');if(qty)qty.textContent=n;let summary=$('#summary-quantity');if(summary)summary.textContent=n;let sub=$('#summary-subtotal');if(sub)sub.textContent=money(n*AERIQ_PRICE);let line=$('#cart-line-price');if(line)line.textContent=money(n*AERIQ_PRICE);let cart=$('#cart-contents');if(cart){const old=$('.cart-product',cart);if(n===0){cart.innerHTML='<div class="cart-empty"><strong>Tu carrito está vacío.</strong><p>Descubre el sensor AERIQ O₃ para añadirlo a tu carrito.</p><a href="sensor.html" class="btn btn-primary">Ver sensor ↗</a></div>';}else if(!old){location.reload()}}let go=$('#go-checkout');if(go)go.classList.toggle('hidden',n===0);let cq=$('#checkout-qty');if(cq)cq.textContent=n;let cp=$('#checkout-product-total');if(cp)cp.textContent=money(n*AERIQ_PRICE);let cs=$('#checkout-subtotal');if(cs)cs.textContent=money(n*AERIQ_PRICE);refreshCheckout();}
function refreshCheckout(){let total=$('#checkout-total');if(!total)return;let shipping=document.querySelector('input[name="delivery"]:checked')?.value==='pickup'?0:6.9;$('#checkout-shipping').textContent=money(shipping);total.textContent=money(cartCount()*AERIQ_PRICE+shipping)}
function initNavigation(){const toggle=$('[data-menu-toggle]');toggle?.addEventListener('click',()=>$('.main-nav')?.classList.toggle('open'));let user=store.get('user');if(user){let link=$('#account-link');if(link){link.href='perfil.html';link.textContent='Mi cuenta'}}}
function initForms(){
 $$('[data-demo-form]').forEach(f=>f.addEventListener('submit',e=>{e.preventDefault();if(f.reportValidity())toast(f.dataset.success||'Acción simulada: no se han enviado datos.')}));
 $$('[data-toggle-password]').forEach(b=>b.addEventListener('click',()=>{const inp=document.getElementById(b.dataset.togglePassword);if(!inp)return;inp.type=inp.type==='password'?'text':'password';b.textContent=inp.type==='password'?'Mostrar':'Ocultar'}));
 $$('[data-social]').forEach(b=>b.addEventListener('click',()=>toast(`Inicio de sesión con ${b.dataset.social} no conectado en este prototipo.`)));
 $('#forgot-password')?.addEventListener('click',e=>{e.preventDefault();toast('Recuperación de contraseña: pendiente de conectar al servidor.')});
 $('#login-form')?.addEventListener('submit',e=>{e.preventDefault();let f=e.currentTarget;if(!f.reportValidity())return;store.set('user',{name:'Marina',last:'López',email:f.elements.namedItem('email').value});toast('Has entrado en el perfil de demostración.');location.href='perfil.html'});
 $('#register-form')?.addEventListener('submit',e=>{e.preventDefault();let f=e.currentTarget;if(!f.reportValidity())return;if(f.elements.namedItem('password').value!==f.elements.namedItem('confirm').value){toast('Las contraseñas no coinciden.');return}store.set('user',{name:f.elements.namedItem('name').value,last:f.elements.namedItem('last').value,email:f.elements.namedItem('email').value});toast('Cuenta de demostración creada (sin servidor).');location.href='perfil.html'});
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
function initMap(){
 const host=$('#real-gandia-map');if(!host)return;
 // Posicion oficial del Campus de Gandia: UPV, 38.996651, -0.166009.
 // Todas las lecturas son deliberadamente ficticias; ubicaciones aproximadas alrededor del campus.
 const campus=[38.996651,-0.166009];
 const sensors=[
  {id:'011',name:'Campus UPV Gandia',coordinates:[38.996651,-0.166009],value:58,quality:'bajo',color:'green'},
  {id:'017',name:'Playa de Gandia · entorno universitario',coordinates:[38.99802,-0.16250],value:68,quality:'moderado',color:'orange'},
  {id:'024',name:'Camino hacia la playa',coordinates:[39.00012,-0.16055],value:82,quality:'moderado',color:'orange'},
  {id:'028',name:'Entorno norte del campus',coordinates:[38.99935,-0.16742],value:121,quality:'alto',color:'red'},
  {id:'035',name:'Entorno sur del campus',coordinates:[38.99472,-0.16487],value:45,quality:'bajo',color:'green'}
 ];
 const colors={green:'#219c72',orange:'#eea22d',red:'#e35b5d'};
 let selected=sensors[1],source='all';
 const list=$('#map-sensor-list');const status=$('#map-loading');
 if(typeof L==='undefined'){
   if(status)status.innerHTML='No se pudo cargar el mapa de calles. Revisa tu conexión a Internet y actualiza la página. <a href="https://www.openstreetmap.org/?mlat=38.996651&mlon=-0.166009#map=16/38.996651/-0.166009" target="_blank" rel="noopener noreferrer">Abrir mapa de la UPV</a>';
   return;
 }
 // Una sola cartografía sin clave: las dos apariencias usan las mismas teselas OSM.
 // Modo oscuro = filtro visual CSS aplicado SOLO al panel de teselas.
 const mapVisual=host.closest('.map-visual');
 function setMapStyle(style){
   const mode=style==='light'?'light':'dark';
   host.classList.toggle('map-theme-dark',mode==='dark');
   mapVisual?.classList.toggle('is-dark-map',mode==='dark');
   $$('[data-map-style]').forEach(btn=>{
     const active=btn.dataset.mapStyle===mode;
     btn.classList.toggle('active',active);
     btn.setAttribute('aria-pressed',String(active));
   });
   store.set('mapStyle',mode);
 }
 $$('[data-map-style]').forEach(btn=>btn.addEventListener('click',()=>setMapStyle(btn.dataset.mapStyle)));
 setMapStyle(store.get('mapStyle','dark'));
 const map=L.map(host,{zoomControl:false,scrollWheelZoom:true,preferCanvas:true}).setView([38.9974,-0.1633],16);
 const tileLayer=L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
   maxZoom:19,attribution:'&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener noreferrer">OpenStreetMap</a> contributors'
 }).addTo(map);
 tileLayer.on('load',()=>{if(status)status.hidden=true});
 const circles=L.layerGroup().addTo(map),pins=L.layerGroup().addTo(map);
 // Círculos a escala geográfica; son áreas meramente ilustrativas, no una interpolación validada.
 const layers=new Map();
 sensors.forEach(s=>{
   const circle=L.circle(s.coordinates,{radius:125,color:colors[s.color],weight:0,fillColor:colors[s.color],fillOpacity:.20,interactive:false}).addTo(circles);
   const icon=L.divIcon({className:'aeriq-marker-container',html:`<div class="aeriq-map-marker ${s.color}${s.id===selected.id?' active':''}"><span class="marker-center"></span></div>`,iconSize:[32,32],iconAnchor:[16,16]});
   const marker=L.marker(s.coordinates,{icon,keyboard:true,title:`Sensor de ejemplo #${s.id} · ${s.name}`}).addTo(pins);
   marker.on('click',()=>choose(s.id,true));
   layers.set(s.id,{marker,circle,sensor:s});
 });
 function choose(id,pan){
   const s=sensors.find(x=>x.id===id);if(!s)return;selected=s;
   $('#selected-id').textContent=s.id;
   $('#map-id').textContent='#'+s.id;
   $('#map-value').textContent=String(s.value);
   $('#map-location').textContent=s.name;
   $('#map-quality').textContent=s.quality;
   $$('.sensor-list-item').forEach(b=>b.classList.toggle('chosen',b.dataset.sensorId===id));
   layers.forEach(({marker,sensor})=>{
     const c=marker.getElement()?.querySelector('.aeriq-map-marker');
     if(c)c.classList.toggle('active',sensor.id===id);
   });
   if(pan)map.flyTo(s.coordinates,Math.max(map.getZoom(),16),{duration:.45});
 }
 function renderList(){
   if(!list)return;
   if(source==='mine'){
     list.innerHTML='<p class="map-empty-sensors">No hay sensores propios conectados en este prototipo. Selecciona «Todos» para ver los de demostración.</p>';
     return;
   }
   list.innerHTML=sensors.map(s=>`<button class="sensor-list-item${s.id===selected.id?' chosen':''}" type="button" data-sensor-id="${s.id}"><strong>${s.name}</strong><span>#${s.id} · ${s.value} µg/m³ · ${s.quality} (demo)</span><i class="sensor-state ${s.color}"></i></button>`).join('');
 }
 renderList();choose(selected.id,false);
 list?.addEventListener('click',e=>{
   const b=e.target.closest('[data-sensor-id]');if(b)choose(b.dataset.sensorId,true);
 });
 function toggleSource(){
   if(source==='all'){
     pins.addTo(map);circles.addTo(map);
     if(!$('#marker-toggle')?.checked)map.removeLayer(pins);
     if(!$('#heat-toggle')?.checked)map.removeLayer(circles);
   }else{map.removeLayer(pins);map.removeLayer(circles)}
   renderList();
 }
 $$('[data-source]').forEach(b=>b.addEventListener('click',()=>{
   source=b.dataset.source;
   $$('[data-source]').forEach(v=>v.classList.toggle('selected',v===b));
   toggleSource();
 }));
 $('#toggle-map-layers')?.addEventListener('click',()=>{const l=$('#map-layer-options');l.hidden=!l.hidden});
 $('#heat-toggle')?.addEventListener('change',e=>{
   if(source==='all'&&e.target.checked)circles.addTo(map);else map.removeLayer(circles);
 });
 $('#marker-toggle')?.addEventListener('change',e=>{
   if(source==='all'&&e.target.checked)pins.addTo(map);else map.removeLayer(pins);
 });
 function goCampus(){map.flyTo(campus,16,{duration:.6})}
 $('#map-reset')?.addEventListener('click',goCampus);
 ['#map-zoom-in','#zoomin-map'].forEach(x=>$(x)?.addEventListener('click',()=>map.zoomIn()));
 $('#zoomout-map')?.addEventListener('click',()=>map.zoomOut());
 const search=$('#map-search');
 function searchPlace(){
   const q=(search?.value||'').trim().toLowerCase();
   if(!q)return;
   if(q.includes('upv')||q.includes('campus')||q.includes('universidad')||q.includes('universitat')){
     map.flyTo(campus,17,{duration:.6});choose('011',false);
   }else if(q.includes('playa')||q.includes('platja')){
     map.flyTo([38.9990,-0.1589],16,{duration:.6});choose('017',false);
   }else if(q.includes('grau')||q.includes('grao')){
     map.flyTo([38.9922,-0.1656],15,{duration:.6});
   }else toast('En esta demostración puedes buscar UPV, playa de Gandia o Grau de Gandia.');
 }
 search?.addEventListener('change',searchPlace);
 search?.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();searchPlace();search.blur()}});
 $('#export-sensor')?.addEventListener('click',()=>{
   const csv='id_sensor;zona_aproximada;latitud;longitud;ozono_ug_m3;calidad;tipo_de_dato\n'+
     `${selected.id};"${selected.name}";${selected.coordinates[0]};${selected.coordinates[1]};${selected.value};${selected.quality};FICTICIO\n`;
   const blob=new Blob(['\ufeff'+csv],{type:'text/csv;charset=utf-8'});
   const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=`aeriq_gandia_${selected.id}_DEMO.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),2500);
   toast('CSV de ejemplo exportado; no contiene mediciones reales.');
 });
 // Asegura que las teselas se redibujan después de aplicar el layout responsive.
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
function initProfile(){const user=store.get('user');const f=$('#profile-form');if(f&&user){f.elements.namedItem('name').value=user.name||'';f.elements.namedItem('last').value=user.last||'';f.elements.namedItem('email').value=user.email||'';$('#sidebar-user').textContent=[user.name,user.last].join(' ');$('#sidebar-email').textContent=user.email}
 f?.addEventListener('submit',e=>{e.preventDefault();if(!f.reportValidity())return;const user={name:f.elements.namedItem('name').value,last:f.elements.namedItem('last').value,email:f.elements.namedItem('email').value};store.set('user',user);$('#sidebar-user').textContent=user.name+' '+user.last;$('#sidebar-email').textContent=user.email;toast('Perfil actualizado localmente.')});$('#logout')?.addEventListener('click',()=>{store.set('user',null);toast('Sesión de demostración cerrada.');location.href='login.html'});
 $('#delete-address')?.addEventListener('click',()=>{let a=$('#profile-address');if(a)a.innerHTML='<p>Sin direcciones guardadas.</p>';toast('Dirección eliminada en esta demostración.')});$('#edit-address')?.addEventListener('click',()=>{let a=prompt('Nueva dirección de ejemplo','C/ del Jardín, 12');if(a){$('#profile-address').querySelector('p').innerHTML=a.replaceAll('<','&lt;')+'<br>46001 Valencia, Valencia';toast('Dirección actualizada localmente.')}});$('#add-address')?.addEventListener('click',()=>toast('Añadir direcciones reales requiere conectar una cuenta al servidor.'))}
function init(){initNavigation();initForms();initSensorProduct();initCart();initCheckout();initMap();initModelViewer();initDevices();initProfile();updateCart();}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
