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
    if(a){a.href=s.user?authDestination(s.user):'login.html';a.textContent=s.user?'Mi cuenta':'Entrar';}
  }).catch(()=>{});
}
function authDestination(user){
  if(!user)return 'login.html';
  if(user.mustChangePassword)return 'cambiar-clave.html';
  return ['admin','analista','comercial'].includes(user.role)?'portal.html':'perfil.html';
}
window.authDestination=authDestination;
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
  $$('[data-toggle-password]').forEach(b=>{
  const input=document.getElementById(b.dataset.togglePassword);if(!input)return;
  const open=b.querySelector('.eye-open'),closed=b.querySelector('.eye-closed');
  // Estado inicial: contraseña oculta -> icono "ojo" (como en la app).
  if(open){open.hidden=false;open.style.display='';}
  if(closed){closed.hidden=true;closed.style.display='none';}
  b.addEventListener('click',e=>{
    e.preventDefault();
    e.stopPropagation();
    const show=input.type==='password';
    input.type=show?'text':'password';
    if(open){open.hidden=show;open.style.display=show?'none':'';}
    if(closed){closed.hidden=!show;closed.style.display=show?'':'none';}
    b.setAttribute('aria-pressed',String(show));
    b.setAttribute('aria-label',show?'Ocultar contraseña':'Mostrar contraseña');
  });
});
  $$('[data-social]').forEach(b=>b.addEventListener('click',()=>toast(`El acceso con ${b.dataset.social||'ese proveedor'} todavía no está disponible. Usa Google o tu correo.`)));
  $('#login-form')?.addEventListener('submit',async e=>{
    e.preventDefault();const f=e.currentTarget;if(!f.reportValidity())return;authSubmitButton(f,true);
    try{
      const email=f.elements.namedItem('email').value;
      const result=await AeriqAuth.post('login',{email,password:f.elements.namedItem('password').value});
      location.href=authDestination(result.user);
    }catch(error){
      authFeedback(error);
    }finally{authSubmitButton(f,false)}
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
 $('#add-to-cart')?.addEventListener('click',()=>{setCart(n);location.href='carrito.html'});
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
function initMap(){ if(typeof initAeriqMap==='function')initAeriqMap(); }
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
function initDevices(){if(typeof initFleet==='function')void initFleet();}
async function initProfile(){
  const form=$('#profile-form');if(!form)return;
  try{
    const session=await AeriqAuth.session();
    if(!session.user){location.href='login.html';return;}
    const user=session.user;
    const organization=document.querySelector('#profile-organization'),role=document.querySelector('#profile-role');
    if(organization)organization.textContent=user.organization||'Cuenta de consulta pública, sin organización asignada';
    if(role)role.textContent='Función: '+user.role;
    const avatar=document.querySelector('.avatar');if(avatar)avatar.textContent=(user.name?.[0]||'')+(user.last?.[0]||'');
    if(user.mustChangePassword){location.href="cambiar-clave.html";return;}
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
  $$('#logout').forEach(button=>button.addEventListener('click',async()=>{
    button.disabled=true;
    try{await AeriqAuth.post('logout');location.href='login.html'}catch(error){button.disabled=false;authFeedback(error)}
  }));
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
