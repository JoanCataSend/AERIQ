'use strict';
const menuToggle=document.querySelector('[data-menu-toggle]');
const mainNavigation=menuToggle?.getAttribute('aria-controls')?document.getElementById(menuToggle.getAttribute('aria-controls')):document.querySelector('.main-nav');
if(menuToggle&&mainNavigation){
  /**
   * Establece menú.
   * Diseño lógico: ('open',open) → setMenu() → resultado de la operación; según doc/frontend_business_logic_design.md.
   */
  const setMenu=open=>{mainNavigation.classList.toggle('open',open);menuToggle.setAttribute('aria-expanded',String(open));menuToggle.setAttribute('aria-label',open?'Cerrar menú':'Abrir menú');};
  menuToggle.addEventListener('click',()=>setMenu(!mainNavigation.classList.contains('open')));
  mainNavigation.addEventListener('click',event=>{if(event.target.closest('a'))setMenu(false);});
  document.addEventListener('keydown',event=>{if(event.key==='Escape'&&mainNavigation.classList.contains('open')){setMenu(false);menuToggle.focus();}});
  addEventListener('resize',()=>{if(matchMedia('(min-width: 861px)').matches)setMenu(false);});
}
