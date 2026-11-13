'use strict';
(async()=>{
  const feedback=document.querySelector('#password-feedback');
  try { if (!(await AeriqAuth.session()).user) { location.href='login.html';return; } }
  catch(e) { feedback.textContent=e.message;return; }
  document.querySelector('#first-logout').onclick=async()=>{try{await AeriqAuth.post('logout');location.href='login.html';}catch(e){feedback.textContent=e.message;}};
  document.querySelector('#first-password').onsubmit=async e=>{
    e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');
    if(f.elements.namedItem('password').value!==f.elements.namedItem('confirm').value){feedback.textContent='Las contraseñas no coinciden.';return;}
    b.disabled=true;
    try{
      await AeriqAuth.post('change-password',{current:f.elements.namedItem('current').value,password:f.elements.namedItem('password').value});
      f.reset();
      await AeriqAuth.post('logout');
      location.href='login.html';
    }
    catch(error){feedback.textContent=error.message;}finally{b.disabled=false;}
  };
})();
