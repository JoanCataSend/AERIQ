'use strict';
const kitForm=document.querySelector('#kit-draft');
if(kitForm){
  const count=kitForm.elements.quantity;count.value=Math.max(1,Number(store.get('cart',1))||1);
  kitForm.onsubmit=e=>{e.preventDefault();store.set('cart',Number(count.value));location.href='checkout.html?'+new URLSearchParams({service:kitForm.elements.service.value,quantity:count.value});};
}
