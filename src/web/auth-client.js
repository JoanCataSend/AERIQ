/*
 * Fichero: auth-client.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

/* AERIQ · cliente de sesión. Las contraseñas y códigos nunca se almacenan en localStorage. */
'use strict';
/**
 * Diseño lógico: (: Text --> AeriqAuth() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
const AeriqAuth = (() => {
  let sessionPromise = null;
  /**
   * Diseño lógico: force: Text --> session() --> Text
   * Descripción: Describe la operación realizada y su resultado.
   */
  async function session(force=false){
    if (!sessionPromise || force) {
      sessionPromise = fetch('auth.php?action=session',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}})
        .then(async r => {const d=await r.json();if(!r.ok)throw new Error(d.error||'Servidor no disponible');return d;})
        .catch(e=>{sessionPromise=null;throw e});
    }
    return sessionPromise;
  }
  /**
   * Diseño lógico: action: Text, payload: Text --> post() --> Text
   * Descripción: Procesa post y devuelve el resultado correspondiente.
   */
  async function post(action, payload={}) {
    let state=await session();
    const response=await fetch('auth.php?action='+encodeURIComponent(action),{
      method:'POST',credentials:'same-origin',cache:'no-store',
      headers:{'Content-Type':'application/json','Accept':'application/json','X-CSRF-Token':state.csrf},
      body:JSON.stringify(payload)
    });
    let data;
    try { data=await response.json(); } catch { throw new Error('El servidor no devolvió JSON. Comprueba Plesk/PHP.'); }
    if (!response.ok) {const err=new Error(data.error||`HTTP ${response.status}`);throw err;}
    if (['login','google','logout','reset-password','profile','change-password'].includes(action)) sessionPromise=null;
    if (action==='logout') remember(null);
    else if (data.user) remember(data.user);
    return data;
  }
  /**
   * Diseño lógico: user: Text --> home() --> Text
   * Descripción: Procesa home y devuelve el resultado correspondiente.
   */
  function home(user){
    if(!user)return 'login.html';
    if(user.mustChangePassword)return 'cambiar-clave.html';
    const p=user.permissions||[];
    // Panel: admins (informes o solicitudes) y operadores con campaña («Mi campaña», de consulta).
    return ['informes.ver','solicitudes.gestionar','campanas.gestionar'].some(x=>p.includes(x))||(p.includes('mediciones.registrar')&&user.campaign)?'portal.html':'perfil.html';
  }
  /*
   * Header sin parpadeo. Este script se carga en <head> sin defer: antes de pintar nada mira la
   * última sesión conocida de esta pestaña (sessionStorage: nombre, rol y permisos; nunca claves).
   * Con sesión, el CSS oculta el header hasta pintarlo ya privado; después se confirma con el servidor.
   */
  const KEY='aeriq-user';
  /**
   * Diseño lógico: cached() --> Text
   * Descripción: Procesa cached y devuelve el resultado correspondiente.
   */
  const cached=()=>{try{return JSON.parse(sessionStorage.getItem(KEY)||'null');}catch{return null;}};
  /**
   * Diseño lógico: (KEY,JSON.stringify(user)) → remember() → resultado de la operación; según doc/frontend_business_logic_design.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const remember=user=>{try{user?sessionStorage.setItem(KEY,JSON.stringify(user)):sessionStorage.removeItem(KEY);}catch{}};
  document.documentElement.dataset.session=cached()?'user':'guest';
  let publicNav=null;
  /**
   * Diseño lógico: user: Text --> render() --> Text
   * Descripción: Construye o actualiza la interfaz con los datos actuales.
   */
  function render(user){
    const header=document.querySelector('.site-header'),link=document.querySelector('#account-link'),nav=document.querySelector('.main-nav');
    if(!header)return;
    if(nav&&!publicNav)publicNav=[...nav.children].map(n=>n.cloneNode(true));
    header.querySelector('.account-menu')?.remove();
    const priv=Boolean(user&&!user.mustChangePassword&&link);
    if(nav)nav.replaceChildren(...(priv?[]:publicNav.map(n=>n.cloneNode(true))));
    for(const b of header.querySelectorAll('.header-actions .btn'))b.hidden=priv;
    if(link){link.hidden=priv;link.href=user?.mustChangePassword?'cambiar-clave.html':'login.html';link.textContent=user?.mustChangePassword?'Elegir contraseña':'Iniciar sesión';}
    if(priv){
      /**
       * Diseño lógico: tag: Text, cls: Text, text: Text --> el() --> Text
       * Descripción: Procesa el y devuelve el resultado correspondiente.
       */
      const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
      const panel=home(user)==='portal.html',page=location.pathname.split('/').pop()||'index.html';
      // El admin de campaña administra una sola campaña: su acceso se llama «Mi campaña».
      // Admin principal: «Mi panel». Admin de campaña y operador: «Mi campaña». «Mi cuenta» va solo en la burbuja.
      const perms=user.permissions||[],ownCampaign=perms.includes('equipo.gestionar')||(perms.includes('mediciones.registrar')&&!perms.includes('informes.ver'));
      const panelText=ownCampaign?'Mi campaña':'Mi panel',panelHref=ownCampaign?'portal.html#campaign':'portal.html';
      const items=[['Inicio','index.html'],['Mapa','mapa.html'],...(panel?[[panelText,panelHref]]:[])];
      if(nav)nav.append(...items.map(([text,href])=>{const a=el('a','',text);a.href=href;if(page===href.split('#')[0])a.setAttribute('aria-current','page');return a;}));
      const menu=el('details','account-menu'),summary=el('summary'),list=el('div','account-menu-list');
      summary.append(el('span','account-avatar',((user.name||'')[0]||'')+((user.last||'')[0]||'')),el('span','account-name',user.name||user.email));
      summary.setAttribute('aria-label','Menú de la cuenta');
      const initials=((user.name||'')[0]||'')+((user.last||'')[0]||'');
      const who=el('div','account-menu-who');who.append(el('span','account-avatar',initials),el('strong','',[user.name,user.last].filter(Boolean).join(' ')),el('span','account-role',user.roleName||''));
      /**
       * Diseño lógico: text: Text, href: Text --> go() --> Text
       * Descripción: Procesa go y devuelve el resultado correspondiente.
       */
      const go=(text,href)=>{const a=el('a','',text);a.href=href;if(page===href.split('#')[0])a.setAttribute('aria-current','page');return a;};
      const out=el('button','danger','Cerrar sesión');out.type='button';
      out.onclick=async()=>{out.disabled=true;try{await post('logout');location.href='index.html';}catch(e){out.disabled=false;out.textContent=e.message;}};
      list.append(who,el('div','account-menu-sep'),go('Mi cuenta','perfil.html'),el('div','account-menu-sep'),out);menu.append(summary,list);link.after(menu);
    }
    document.documentElement.dataset.session=priv?'user':'guest';header.dataset.ready='';
  }
  /**
   * Diseño lógico: header() --> Text
   * Descripción: Procesa header y devuelve el resultado correspondiente.
   */
  function header(){
    const start=cached();render(start);
    session().then(({user})=>{remember(user);if(JSON.stringify(user)!==JSON.stringify(start))render(user);}).catch(()=>{});
    document.addEventListener('click',e=>{const m=document.querySelector('.account-menu');if(m?.open&&!m.contains(e.target))m.open=false;});
    document.addEventListener('keydown',e=>{const m=document.querySelector('.account-menu');if(e.key==='Escape'&&m?.open){m.open=false;m.querySelector('summary').focus();}});
  }
  /*
   * Requisitos de la contraseña nueva (T33). Son los mismos que comprueba el servidor (auth_password_missing):
   * aquí solo sirven para avisar mientras se escribe; el servidor vuelve a comprobarlos siempre.
   */
  /**
   * Diseño lógico: (p) → RULES() → resultado de la operación; según doc/frontend_business_logic_design.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const RULES=[['al menos 8 caracteres',p=>[...p].length>=8],['una mayúscula',p=>/\p{Lu}/u.test(p)],['una minúscula',p=>/\p{Ll}/u.test(p)],
    ['un número',p=>/\p{Nd}/u.test(p)],['un símbolo (por ejemplo ! ? # @ . -)',p=>/[^\p{L}\p{Nd}\s]/u.test(p)]];
  /**
   * Diseño lógico: (([,ok])=>!ok(String(p||''))) → passwordMissing() → resultado de la operación; según doc/frontend_business_logic_design.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const passwordMissing=p=>RULES.filter(([,ok])=>!ok(String(p||''))).map(([text])=>text);
  /**
   * Diseño lógico: input: Text --> passwordRules() --> Text
   * Descripción: Procesa password rules y devuelve el resultado correspondiente.
   */
  function passwordRules(input){
    if(!input||input.dataset.rulesReady)return;input.dataset.rulesReady='1';
    const list=document.createElement('ul');list.className='password-rules';list.id=(input.id||input.name)+'-rules';
    const title=document.createElement('li');title.className='password-rules-title';title.textContent='Tu contraseña necesita:';list.append(title);
    /**
     * Diseño lógico: (([text])=>{const li=document.createElement('li');li.textContent=text;list.append(li);return li;}) → items() → resultado de la operación; según doc/frontend_business_logic_design.md.
     * Descripción: Describe la operación realizada y su resultado.
     */
    const items=RULES.map(([text])=>{const li=document.createElement('li');li.textContent=text;list.append(li);return li;});
    input.setAttribute('aria-describedby',list.id);(input.closest('label')||input).after(list);
    /**
     * Diseño lógico: update() --> Text
     * Descripción: Procesa update y devuelve el resultado correspondiente.
     */
    const update=()=>{
      const p=input.value,missing=passwordMissing(p);
      RULES.forEach(([text],i)=>items[i].classList.toggle('is-ok',!missing.includes(text)));
      input.setCustomValidity(p&&missing.length?'La contraseña no es segura. Le falta: '+missing.join(', ')+'.':'');
    };
    input.addEventListener('input',update);update();
  }
  /**
   * Diseño lógico: attachRules() --> Text
   * Descripción: Procesa attach rules y devuelve el resultado correspondiente.
   */
  const attachRules=()=>document.querySelectorAll('input[data-password-rules]').forEach(passwordRules);
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>{header();attachRules();});else{header();attachRules();}
  return {session,post,home,passwordMissing,passwordRules};
})();
// También disponible para módulos ES (por ejemplo, google-auth.js).
window.AeriqAuth = AeriqAuth;
/*
 * Tono de los mensajes: 'error' (rojo, algo ha fallado), 'warn' (naranja, aviso o algo pendiente),
 * 'ok' (verde, guardado o hecho) e 'info' (neutro). Se indica siempre al mostrar el mensaje, no se adivina.
 */
/**
 * Diseño lógico: node: Text, msg: Text, tone: Text --> aeriqStatus() --> Text
 * Descripción: Procesa aeriq status y devuelve el resultado correspondiente.
 */
function aeriqStatus(node,msg,tone='info'){
  if(!node)return;node.textContent=msg||'';
  if(msg&&tone!=='info')node.dataset.tone=tone;else delete node.dataset.tone;
}
window.aeriqStatus=aeriqStatus;
