'use strict';
document.querySelector('[data-menu-toggle]')?.addEventListener('click',()=>document.querySelector('.main-nav')?.classList.toggle('open'));
if(typeof AeriqAuth!=='undefined')AeriqAuth.session().then(({user})=>{
  const account=document.querySelector('#account-link');if(account&&user){account.href=user.mustChangePassword?'cambiar-clave.html':(['admin','analista','comercial'].includes(user.role)?'portal.html':'perfil.html');account.textContent='Mi cuenta';}
}).catch(()=>{});
