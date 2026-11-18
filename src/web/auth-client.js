/* AERIQ · cliente de sesión. Las contraseñas y códigos nunca se almacenan en localStorage. */
'use strict';
const AeriqAuth = (() => {
  let sessionPromise = null;
  async function session(force=false){
    if (!sessionPromise || force) {
      sessionPromise = fetch('auth.php?action=session',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}})
        .then(async r => {const d=await r.json();if(!r.ok)throw new Error(d.error||'Servidor no disponible');return d;})
        .catch(e=>{sessionPromise=null;throw e});
    }
    return sessionPromise;
  }
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
  /** Página de inicio de cada cuenta: el panel si tiene permisos de gestión; si no, Mi cuenta. */
  function home(user){
    if(!user)return 'login.html';
    if(user.mustChangePassword)return 'cambiar-clave.html';
    return ['informes.ver','solicitudes.gestionar'].some(p=>(user.permissions||[]).includes(p))?'portal.html':'perfil.html';
  }
  /*
   * Header sin parpadeo. Este script se carga en <head> sin defer: antes de pintar nada mira la
   * última sesión conocida de esta pestaña (sessionStorage: nombre, rol y permisos; nunca claves).
   * Con sesión, el CSS oculta el header hasta pintarlo ya privado; después se confirma con el servidor.
   */
  const KEY='aeriq-user';
  const cached=()=>{try{return JSON.parse(sessionStorage.getItem(KEY)||'null');}catch{return null;}};
  const remember=user=>{try{user?sessionStorage.setItem(KEY,JSON.stringify(user)):sessionStorage.removeItem(KEY);}catch{}};
  document.documentElement.dataset.session=cached()?'user':'guest';
  let publicNav=null;
  /** Pinta el header: sin sesión, menú público e «Iniciar sesión»; con sesión, Inicio · Mapa · Mi panel · Mi cuenta y menú con tu nombre. */
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
      const el=(tag,cls,text)=>{const n=document.createElement(tag);if(cls)n.className=cls;if(text!==undefined)n.textContent=text;return n;};
      const panel=home(user)==='portal.html',page=location.pathname.split('/').pop()||'index.html';
      // El admin de campaña administra una sola campaña: su acceso se llama «Mi campaña».
      const campaignAdmin=(user.permissions||[]).includes('equipo.gestionar'),panelText=campaignAdmin?'Mi campaña':'Mi panel',panelHref=campaignAdmin?'portal.html#campaign':'portal.html';
      const items=[['Inicio','index.html'],['Mapa','mapa.html'],...(panel?[[panelText,panelHref]]:[]),['Mi cuenta','perfil.html']];
      if(nav)nav.append(...items.map(([text,href])=>{const a=el('a','',text);a.href=href;if(page===href.split('#')[0])a.setAttribute('aria-current','page');return a;}));
      const menu=el('details','account-menu'),summary=el('summary'),list=el('div','account-menu-list');
      summary.append(el('span','account-avatar',((user.name||'')[0]||'')+((user.last||'')[0]||'')),el('span','account-name',user.name||user.email));
      summary.setAttribute('aria-label','Menú de la cuenta');
      const initials=((user.name||'')[0]||'')+((user.last||'')[0]||'');
      const who=el('div','account-menu-who');who.append(el('span','account-avatar',initials),el('strong','',[user.name,user.last].filter(Boolean).join(' ')),el('span','account-role',user.roleName||''));
      const go=(text,href)=>{const a=el('a','',text);a.href=href;if(page===href.split('#')[0])a.setAttribute('aria-current','page');return a;};
      const out=el('button','danger','Cerrar sesión');out.type='button';
      out.onclick=async()=>{out.disabled=true;try{await post('logout');location.href='index.html';}catch(e){out.disabled=false;out.textContent=e.message;}};
      list.append(who,el('div','account-menu-sep'),...(panel?[go(panelText,panelHref)]:[]),go('Mi cuenta','perfil.html'),el('div','account-menu-sep'),out);menu.append(summary,list);link.after(menu);
    }
    document.documentElement.dataset.session=priv?'user':'guest';header.dataset.ready='';
  }
  function header(){
    const start=cached();render(start);
    session().then(({user})=>{remember(user);if(JSON.stringify(user)!==JSON.stringify(start))render(user);}).catch(()=>{});
    document.addEventListener('click',e=>{const m=document.querySelector('.account-menu');if(m?.open&&!m.contains(e.target))m.open=false;});
    document.addEventListener('keydown',e=>{const m=document.querySelector('.account-menu');if(e.key==='Escape'&&m?.open){m.open=false;m.querySelector('summary').focus();}});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',header);else header();
  return {session,post,home};
})();
// También disponible para módulos ES (por ejemplo, google-auth.js).
window.AeriqAuth = AeriqAuth;
