'use strict';
const portalFeedback=document.querySelector('#portal-feedback');
let dashboard={},currentRows=[],administrator=false;
const el=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=String(text??'—');return n;};
function table(host,headers,rows,action){
  host.replaceChildren();const t=el('table');t.className='devices-table';const head=el('tr');for(const h of headers)head.append(el('th',h));
  if(action)head.append(el('th','Acciones'));t.append(head);
  for(const row of rows){const tr=el('tr');for(const v of row.cells)tr.append(el('td',v));if(action){const td=el('td'),b=el('button','Editar');b.className='text-link';b.onclick=()=>action(row.source);td.append(b);tr.append(td);}t.append(tr);}
  if(!rows.length)host.append(el('p','No hay registros.'));else host.append(t);
}
function options(id,rows,label){const select=document.getElementById(id),value=select.value;select.replaceChildren(new Option('Sin selección',''));for(const r of rows)select.add(new Option(label(r),r.id));select.value=value;}
function fill(form,data){for(const [key,value] of Object.entries(data)){const field=form.elements.namedItem(key);if(!field)continue;if(field.type==='checkbox')field.checked=Boolean(Number(value));else field.value=value??'';}form.scrollIntoView({behavior:'smooth',block:'center'});}
function payload(form){const d=Object.fromEntries(new FormData(form));for(const f of form.querySelectorAll('[type="checkbox"]'))d[f.name]=f.checked;if(!d.id)delete d.id;return d;}
async function refresh(){
  dashboard=await Aeriq.portal('dashboard');for(const n of document.querySelectorAll('[data-count]'))n.textContent=dashboard[n.dataset.count].length;
  table(document.querySelector('#workers-list'),['Nombre','Correo','Función','Acceso'],dashboard.workers.map(w=>({cells:[w.nombre+' '+w.apellidos,w.email,w.rol,Number(w.cambiar_clave)?((w.invitacion_expira_en&&new Date(w.invitacion_expira_en.replace(' ','T')+'Z').getTime()<Date.now())?'Invitación caducada':('Activación pendiente · '+(w.invitacion_expira_en||'')+' UTC')):(Number(w.activo)?'Activo':'Desactivado')]})));
  options('device-worker',dashboard.workers.filter(w=>w.rol==='trabajador'&&(Number(w.activo)||Number(w.cambiar_clave))),w=>w.email);
  for(const id of ['device-campaign','report-campaign'])options(id,dashboard.campaigns,c=>c.nombre);
  options('report-device',dashboard.devices,d=>d.nombre);
  table(document.querySelector('#campaigns-list'),['Campaña','Zona','Fechas','Publicación'],dashboard.campaigns.map(c=>({cells:[c.nombre,c.zona,c.inicio+' — '+c.fin,Number(c.publicar)?'Autorizada':'Privada'],source:c})),administrator?c=>fill(document.querySelector('#campaign-form'),{id:c.id,name:c.nombre,zone:c.zona,start:c.inicio,end:c.fin,medium:c.umbral_medio,high:c.umbral_alto,radius:c.radio_m,active:c.activa,public:c.publicar}):null);
  table(document.querySelector('#devices-list'),['Orb','Dirección BLE','Trabajador','Campaña','Alta','Actividad','Última lectura (UTC)'],dashboard.devices.map(d=>({cells:[d.nombre,d.direccion_ble,d.trabajador,d.campana,Number(d.activo)?'Activo':'Baja',d.ultima_recepcion?(Date.now()-new Date(d.ultima_recepcion.replace(' ','T')+'Z').getTime()<900000?'Lecturas en los últimos 15 min':'Sin lecturas recientes'):'Sin lecturas',d.ultima_medicion],source:d})),administrator?d=>fill(document.querySelector('#device-form'),{id:d.id,name:d.nombre,address:d.direccion_ble,worker:d.trabajador_id,campaign:d.campana_id,active:d.activo,simulated:d.simulado}):null);
}
for(const [id,action] of [['worker-form','worker'],['campaign-form','campaign'],['device-form','device']]){
  document.getElementById(id).onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('[type="submit"]');b.disabled=true;
    try{const result=await Aeriq.portal(action,payload(f));f.reset();await refresh();portalFeedback.textContent=result.message||'Cambios guardados.';}
    catch(error){portalFeedback.textContent=error.message;}finally{b.disabled=false;}
  };
}
function renderReport(rows,group){
  const host=document.querySelector('#report-results');host.replaceChildren();
  const sets=new Map();for(const r of rows){const key=`${Number(r.simulado)?'Simulado':'Real'} · ${r.unidad}`;if(!sets.has(key))sets.set(key,[]);sets.get(key).push(r);}
  for(const [name,set] of sets){
    const thermal=set[0].unidad==='°C';const stats=AeriqDomain.statistics(set,group);host.append(el('h3',name));host.append(el('p',`${stats.count} lecturas válidas · mínimo ${stats.min} · máximo ${stats.max} · media ${stats.mean.toFixed(2)}`));
    const canvas=el('canvas');canvas.width=900;canvas.height=220;canvas.style.width='100%';canvas.setAttribute('role','img');canvas.setAttribute('aria-label','Evolución de valores medios; datos disponibles en la tabla siguiente');host.append(canvas);
    const ctx=canvas.getContext('2d'),min=Math.min(0,...stats.series.map(s=>s.mean)),max=Math.max(min+1,...stats.series.map(s=>s.mean));ctx.strokeStyle='#0e6b5e';ctx.lineWidth=3;ctx.beginPath();
    stats.series.forEach((s,i)=>{const x=20+i*850/Math.max(1,stats.series.length-1),y=200-(s.mean-min)/(max-min)*180;if(i)ctx.lineTo(x,y);else ctx.moveTo(x,y);ctx.fillStyle='#0e6b5e';ctx.fillRect(x-2,y-2,4,4);});ctx.stroke();
    const summary=el('div');host.append(summary);table(summary,['Periodo (UTC)','Mínimo','Máximo','Media'],stats.series.map(s=>({cells:[s.date,s.min,s.max,s.mean.toFixed(2)]})));
    host.append(el('p',thermal?'Temperatura del módulo en °C. No se aplican los umbrales de ozono.':`${stats.exceedances.length} lecturas alcanzan o superan el umbral medio de su campaña.`));
    if(stats.exceedances.length){const exceed=el('div');exceed.className='table-scroll';host.append(exceed);table(exceed,['Fecha del suceso (UTC)','Valor','Unidad','Umbral medio','Umbral alto','Nivel'],stats.exceedances.map(r=>({cells:[r.capturada_en,r.valor,r.unidad,r.umbral_medio,r.umbral_alto,AeriqDomain.level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto))]})));}
    const detail=el('div');detail.className='table-scroll';host.append(detail);
    table(detail,['Fecha UTC','Orb','Campaña / zona','Valor','Unidad','Nivel','Coordenadas'],set.map(r=>({cells:[r.capturada_en,r.dispositivo,r.campana+' / '+r.zona,r.valor,r.unidad,thermal?'—':AeriqDomain.level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto)),r.latitud===null?'Sin ubicación':r.latitud+', '+r.longitud]})));
  }
}
document.querySelector('#report-form').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;currentRows=[];document.querySelector('#export-report').disabled=true;document.querySelector('#report-results').replaceChildren();
  try{const filters=payload(f);filters.devices=[...f.querySelector('#report-device').selectedOptions].map(o=>o.value).filter(Boolean).join(',');delete filters.device;const group=filters.group;delete filters.group;currentRows=(await Aeriq.portal('readings',undefined,filters)).readings;renderReport(currentRows,group);document.querySelector('#report-summary').textContent=`${currentRows.length} lecturas válidas autorizadas.`;document.querySelector('#export-report').disabled=!currentRows.length;}
  catch(error){document.querySelector('#report-summary').textContent=error.message;}finally{b.disabled=false;}
};
document.querySelector('#export-report').onclick=()=>{const url=URL.createObjectURL(new Blob([AeriqDomain.csv(currentRows)],{type:'text/csv;charset=utf-8'}));const a=el('a');a.href=url;a.download='AERIQ-mediciones.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
async function commercial(){
  const {requests}=await Aeriq.portal('requests');const host=document.querySelector('#requests-list');host.replaceChildren();
  for(const r of requests){const box=el('article');box.className='portal-section';box.append(el('h3',`#${r.id} · ${r.entidad}`));
    box.append(el('p',`${r.contacto} · ${r.email} · ${r.telefono} · ${r.recibida_en} UTC`));box.append(el('p',`${r.servicio} · Zona: ${r.zona} · Cantidad: ${r.cantidad??'—'} · Superficie m²: ${r.superficie??'—'} · Duración: ${r.duracion}`));box.append(el('p',r.necesidades));
    const select=el('select');select.setAttribute('aria-label',`Estado solicitud ${r.id}`);for(const state of ['pendiente','en revision','respondida','cerrada'])select.add(new Option(state,state));select.value=r.estado;
    const save=el('button','Guardar estado');save.className='btn btn-outline';save.onclick=async()=>{save.disabled=true;try{await Aeriq.portal('request-status',{id:r.id,status:select.value});portalFeedback.textContent='Estado guardado.';}catch(e){portalFeedback.textContent=e.message;}finally{save.disabled=false;}};
    box.append(select,save);host.append(box);
  }
  if(!requests.length)host.append(el('p','Todavía no hay solicitudes.'));
}
(async()=>{try{
  const {user}=await AeriqAuth.session();if(!user){location.href='login.html';return;}if(user.mustChangePassword){location.href='cambiar-clave.html';return;}
  document.querySelector('#portal-title').textContent=user.organization||'Código Norte';administrator=user.role==='admin';
  if(user.role==='comercial'){document.querySelector('#commercial-panel').hidden=false;await commercial();}
  else if(['admin','analista'].includes(user.role)){document.querySelector('#organization-panel').hidden=false;for(const n of document.querySelectorAll('[data-admin]'))n.hidden=!administrator;await refresh();}
  else throw new Error('Esta cuenta no tiene permisos de administración o análisis. Utiliza la aplicación Android para tu Orb.');
  portalFeedback.textContent='Sesión autorizada · '+user.role;
  window.workspaceRole=user.role;showWorkspace(location.hash.slice(1)||'overview');
}catch(e){portalFeedback.textContent=e.message;}})();

function showWorkspace(view){
  const commercial=window.workspaceRole==='comercial';if(commercial)view='requests';
  if(!['overview','workers','campaigns','devices','reports','requests'].includes(view))view='overview';
  if(!commercial&&view==='requests')view='overview';
  for(const n of document.querySelectorAll('[data-view]'))n.hidden=n.dataset.view!==view||(n.hasAttribute('data-admin')&&!administrator);
  for(const a of document.querySelectorAll('[data-workspace-view]')){a.hidden=commercial?a.dataset.workspaceView!=='requests':a.dataset.workspaceView==='requests';a.classList.toggle('active',a.dataset.workspaceView===view);}
}
for(const a of document.querySelectorAll('[data-workspace-view]'))a.addEventListener('click',()=>showWorkspace(a.dataset.workspaceView));
window.addEventListener('hashchange',()=>showWorkspace(location.hash.slice(1)));
