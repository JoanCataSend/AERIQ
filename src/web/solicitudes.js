'use strict';
const serviceForm=document.querySelector('#service-form');
const kind=document.querySelector('#service-kind');
if(['administracion','empresa','estudio'].includes(new URLSearchParams(location.search).get('service')))kind.value=new URLSearchParams(location.search).get('service');
// Qué servicios miden superficie lo decide el catálogo de la base de datos (servicios.mide_superficie).
let surfaceServices=new Set(['estudio']);
function serviceFields(){const study=surfaceServices.has(kind.value);for(const n of document.querySelectorAll('[data-study]')){n.hidden=!study;n.querySelector('input').required=study;}for(const n of document.querySelectorAll('[data-kit]')){n.hidden=study;n.querySelector('input').required=!study;}}
const quantity=new URLSearchParams(location.search).get('quantity');if(quantity&&Number(quantity)>0)serviceForm.elements.quantity.value=Math.min(100000,Number(quantity));
kind.onchange=serviceFields;serviceFields();
Aeriq.services().then(({services})=>{
  if(!services?.length)return;const chosen=kind.value;
  kind.replaceChildren(...services.map(s=>new Option(s.nombre,s.codigo)));
  surfaceServices=new Set(services.filter(s=>s.mide_superficie).map(s=>s.codigo));
  if(services.some(s=>s.codigo===chosen))kind.value=chosen;serviceFields();
}).catch(()=>{/* Sin conexión: se mantienen las opciones del HTML. */});
serviceForm.onsubmit=async e=>{e.preventDefault();const button=serviceForm.querySelector('button'),feedback=document.querySelector('#service-feedback');button.disabled=true;
  try{const d=Object.fromEntries(new FormData(serviceForm));d.consent=serviceForm.elements.namedItem('consent').checked;const result=await Aeriq.submit(d);feedback.textContent=`Solicitud #${result.id}. ${result.message}`;serviceForm.reset();serviceFields();}
  catch(error){feedback.textContent=error.message;}finally{button.disabled=false;}
};
