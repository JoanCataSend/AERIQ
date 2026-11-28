/*
 * Fichero: clave.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

'use strict';
/*
 * Elegir contraseña (primer acceso). Dos formas de llegar:
 * - Desde el botón del correo de invitación (T32): el enlace trae el correo y la clave temporal tras «#»
 *   (no viajan al servidor en la URL). Se entra con ellas y se cambia la contraseña en un solo paso.
 * - Con la sesión ya iniciada con la clave temporal: se pide la temporal y la nueva.
 */
(async()=>{
  const feedback=document.querySelector('#password-feedback'),form=document.querySelector('#first-password');
  const hash=new URLSearchParams(location.hash.slice(1)),invite={email:hash.get('email')||'',temporary:hash.get('temporal')||''};
  const fromMail=Boolean(invite.email&&invite.temporary);
  if(fromMail){
    // Se borra de la barra de direcciones para que no quede en el historial ni al compartir la pantalla.
    history.replaceState(null,'',location.pathname+location.search);
    form.elements.namedItem('email').value=invite.email;form.elements.namedItem('current').value=invite.temporary;
    document.querySelector('#invite-email').hidden=false;document.querySelector('#temporary-field').hidden=true;
    document.querySelector('#password-intro').textContent='Elige la contraseña con la que entrarás en la web y en la app de AERIQ.';
    document.querySelector('#first-logout').hidden=true;
  } else {
    try { if (!(await AeriqAuth.session()).user) { location.href='login.html';return; } }
    catch(e) { aeriqStatus(feedback,e.message,'error');return; }
  }
  document.querySelector('#first-logout').onclick=async()=>{try{await AeriqAuth.post('logout');location.href='index.html';}catch(e){aeriqStatus(feedback,e.message,'error');}};
  form.onsubmit=async e=>{
    e.preventDefault();const f=e.currentTarget,b=f.querySelector('button[type="submit"]');aeriqStatus(feedback,'');
    if(!f.reportValidity())return;
    if(f.elements.namedItem('password').value!==f.elements.namedItem('confirm').value){aeriqStatus(feedback,'Las contraseñas no coinciden.','error');return;}
    b.disabled=true;
    try{
      const current=f.elements.namedItem('current').value;
      if(fromMail)await AeriqAuth.post('login',{email:invite.email,password:current});
      const result=await AeriqAuth.post('change-password',{current,password:f.elements.namedItem('password').value});
      f.reset();aeriqStatus(feedback,'Contraseña guardada. Entrando…','ok');
      location.href=AeriqAuth.home(result.user||(await AeriqAuth.session(true)).user);
    }
    catch(error){
      aeriqStatus(feedback,fromMail&&/incorrect|caducad/i.test(error.message)
        ?'Este enlace ya no sirve: la invitación ha caducado o ya se usó. Recupera el acceso desde «¿Has olvidado tu contraseña?».'
        :error.message,'error');
    }finally{b.disabled=false;}
  };
})();
