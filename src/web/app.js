/* AERIQ · JavaScript GLOBAL · Interacciones de demostración, sin backend ni cobros. */
'use strict';
// Preferencias locales del navegador (try/catch por si el almacenamiento está bloqueado).
const store={get(k,def=null){try{let v=localStorage.getItem('aeriq_'+k);return v===null?def:JSON.parse(v)}catch{return def}},set(k,v){try{localStorage.setItem('aeriq_'+k,JSON.stringify(v))}catch{}}};
function toast(msg){const n=document.querySelector('#toast');if(!n)return;n.textContent=msg;n.classList.add('show');clearTimeout(toast.t);toast.t=setTimeout(()=>n.classList.remove('show'),3600)}
const $=(s,root=document)=>root.querySelector(s),$$=(s,root=document)=>[...root.querySelectorAll(s)];
function initNavigation(){
  const toggle=$('[data-menu-toggle]');
  toggle?.addEventListener('click',()=>$('.main-nav')?.classList.toggle('open'));
  // Sobre una sección oscura (.dark) la cabecera translúcida pasa a texto claro para que se lea.
  const header=$('.site-header'),darks=$$('.dark');
  if(header&&darks.length){
    let frame=0;
    const update=()=>{frame=0;const mid=header.offsetHeight/2;header.classList.toggle('on-dark',darks.some(s=>{const r=s.getBoundingClientRect();return r.top<=mid&&r.bottom>=mid;}));};
    const schedule=()=>{if(!frame)frame=requestAnimationFrame(update);};
    addEventListener('scroll',schedule,{passive:true});addEventListener('resize',schedule);update();
  }
  if(typeof AeriqAuth==='undefined')return;
  AeriqAuth.session().then(s=>{
    const a=$('#account-link');
    if(a){a.href=s.user?authDestination(s.user):'login.html';a.textContent=s.user?'Mi cuenta':'Iniciar sesión';}
  }).catch(()=>{});
}
function authDestination(user){
  if(!user)return 'login.html';
  if(user.mustChangePassword)return 'cambiar-clave.html';
  const panel=['informes.ver','solicitudes.gestionar'].some(p=>(user.permissions||[]).includes(p));
  return panel?'portal.html':'perfil.html';
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
 const main=$('#product-main-image');
 $$('[data-angle]').forEach(button=>button.addEventListener('click',()=>{
   $$('[data-angle]').forEach(b=>{b.classList.toggle('active',b===button);b.setAttribute('aria-pressed',String(b===button))});
   if(main){main.src=button.dataset.imageSrc;main.alt=button.dataset.imageAlt||'Sensor AERIQ O₃'}
 }));
 $$('[data-angle]').forEach(b=>b.setAttribute('aria-pressed',String(b.classList.contains('active'))));
}
function initMap(){ if(typeof initAeriqMap==='function')initAeriqMap(); }
function initModelViewer(){
 // Cada visor muestra su foto provisional (poster) hasta cargar; el aviso pequeño se apaga al terminar.
 for(const model of document.querySelectorAll('.sensor-model-viewer')){
  const label=model.parentElement.querySelector('.mv-loading');
  const ready=()=>{model.closest('.sensor-3d-shell')?.classList.add('is-ready');label?.classList.add('is-ready');};
  model.addEventListener('load',ready,{once:true});
  model.addEventListener('progress',e=>{if(e.detail?.totalProgress>=1)ready();});
  model.addEventListener('error',()=>{if(label)label.textContent='Modelo 3D no disponible';});
  if(model.loaded)ready();
  if(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches)model.removeAttribute('auto-rotate');
 }
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
    if(role)role.textContent='Función: '+(user.roleName||user.role);
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

function init(){initNavigation();initForms();initSensorProduct();initMap();initModelViewer();initDevices();initProfile();initPbioReadings();}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
