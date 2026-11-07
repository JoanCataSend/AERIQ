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
    return data;
  }
  return {session,post};
})();
// También disponible para módulos ES (por ejemplo, google-auth.js).
window.AeriqAuth = AeriqAuth;
