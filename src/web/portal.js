'use strict';
const portalFeedback=document.querySelector('#portal-feedback');
// Si algo falla al cargar, se dice en pantalla en vez de quedarse en «Comprobando acceso…».
addEventListener('error',e=>{if(portalFeedback)portalFeedback.textContent='No se pudo cargar el panel ('+e.message+'). Recarga con Ctrl+F5.';});
let dashboard={},currentRows=[],lastFilters={},me={};
const can=(user,permission)=>(user.permissions||[]).includes(permission);
// data-perm="a b": el elemento se muestra si la cuenta tiene alguno de esos permisos.
const allowed=n=>!n.dataset.perm||n.dataset.perm.split(' ').some(p=>can(me,p));
const levelOf=r=>r.umbral_medio===null||r.umbral_medio===undefined?'—':AeriqDomain.level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto));
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
  renderWorkers();
  catalogs();
  for(const id of ['worker-campaign','report-campaign'])options(id,dashboard.campaigns,c=>c.nombre);
  // «Mi campaña» (admin de campaña): el servidor solo devuelve su campaña.
  const mine=can(me,'equipo.gestionar')?dashboard.campaigns[0]:null;
  if(mine){
    const set=(id,text)=>{document.getElementById(id).textContent=text;};
    set('my-campaign-name',mine.nombre);set('my-campaign-state',Number(mine.activa)?'Campaña activa':'Campaña inactiva');
    set('my-campaign-meta',`${mine.zona} · del ${mine.inicio} al ${mine.fin}`);
    set('my-campaign-workers',dashboard.workers.filter(w=>Number(w.puede_medir)).length);
    set('my-campaign-devices',dashboard.devices.filter(d=>Number(d.activo)&&d.trabajador_id).length);
    set('my-campaign-public',Number(mine.publicar)?'Sí':'No');
    fill(document.querySelector('#campaign-settings-form'),{medium:mine.umbral_medio,high:mine.umbral_alto,radius:mine.radio_m,public:mine.publicar});
  }
  options('report-device',dashboard.devices,d=>d.nombre);
  table(document.querySelector('#campaigns-list'),['Campaña','Zona','Fechas','Publicación'],dashboard.campaigns.map(c=>({cells:[c.nombre,c.zona,c.inicio+' — '+c.fin,Number(c.publicar)?'Autorizada':'Privada'],source:c})),can(me,'campanas.gestionar')?c=>fill(document.querySelector('#campaign-form'),{id:c.id,name:c.nombre,zone:c.zona,start:c.inicio,end:c.fin,medium:c.umbral_medio,high:c.umbral_alto,radius:c.radio_m,active:c.activa,public:c.publicar}):null);
}
for(const [id,action] of [['worker-form','worker'],['campaign-form','campaign'],['campaign-settings-form','campaign-settings']]){
  if(!document.getElementById(id))continue;
  document.getElementById(id).onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('[type="submit"]');b.disabled=true;
    try{const result=await Aeriq.portal(action,payload(f));f.reset();await refresh();portalFeedback.textContent=result.message||'Cambios guardados.';}
    catch(error){portalFeedback.textContent=error.message;}finally{b.disabled=false;}
  };
}
// Funciones, zonas y umbrales de referencia vienen de la base de datos, no del HTML.
// Equipo: el admin principal gestiona a sus admins de campaña; el admin de campaña, a sus operadores.
function accessText(w){
  if(Number(w.cambiar_clave))return (w.invitacion_expira_en&&new Date(w.invitacion_expira_en.replace(' ','T')+'Z').getTime()<Date.now())?'Invitación caducada':'Invitación pendiente';
  return Number(w.activo)?'Activo':(Number(w.verificado)?'Desactivado':'Sin activar');
}
function renderWorkers(){
  const host=document.querySelector('#workers-list');host.replaceChildren();
  const manages=can(me,'campanas.gestionar')?'admin_campana':can(me,'equipo.gestionar')?'trabajador':null;
  document.querySelector('#workers-title').textContent=manages==='trabajador'?'Operadores de tu campaña':'Personas de la organización';
  const t=el('table');t.className='devices-table';const head=el('tr');
  for(const h of ['Nombre','Correo',...(manages==='trabajador'?['Orb']:['Función']),'Acceso',...(manages?['Acciones']:[])])head.append(el('th',h));t.append(head);
  // El admin de campaña ve solo a sus operadores (no a sí mismo).
  for(const w of dashboard.workers.filter(w=>manages!=='trabajador'||w.rol==='trabajador')){
    const tr=el('tr');for(const v of [w.nombre+' '+w.apellidos,w.email,...(manages==='trabajador'?[w.orb||'Sin Orb']:[w.rol_nombre||w.rol]),accessText(w)])tr.append(el('td',v));
    if(manages){
      const td=el('td');td.className='row-actions';
      if(w.rol===manages){
        const act=(text,run,cls='text-link')=>{const b=el('button',text);b.type='button';b.className=cls;b.onclick=()=>run(b);td.append(b);};
        const call=async(b,action,body)=>{b.disabled=true;try{const r=await Aeriq.portal(action,body);portalFeedback.textContent=r.message||'Cambios guardados.';await refresh();}catch(e){portalFeedback.textContent=e.message;b.disabled=false;}};
        act('Editar',()=>editWorker(w));
        if(!Number(w.verificado))act('Reenviar invitación',b=>call(b,'worker-invite',{id:w.id}));
        else if(Number(w.activo))act('Desactivar',b=>{
          // Doble pulsación en lugar de un diálogo del navegador.
          if(b.dataset.confirm){call(b,'worker-status',{id:w.id,active:false});return;}
          b.dataset.confirm='1';b.textContent='¿Seguro? Pulsa otra vez';setTimeout(()=>{delete b.dataset.confirm;b.textContent='Desactivar';},4000);
        },'text-link danger-link');
        else act('Reactivar',b=>call(b,'worker-status',{id:w.id,active:true}));
      }
      tr.append(td);
    }
    t.append(tr);
  }
  host.append(t);
}
function editWorker(w){
  const f=document.querySelector('#worker-edit-form');f.reset();
  fill(f,{id:w.id,name:w.nombre,last:w.apellidos,phone:w.telefono||''});
  document.querySelector('#worker-edit-email').textContent=w.email+' · el correo no se puede cambiar';
  document.querySelector('#worker-modal').showModal();
}
document.querySelector('#worker-modal-close')?.addEventListener('click',()=>document.querySelector('#worker-modal').close());
document.querySelector('#worker-edit-form')?.addEventListener('submit',async e=>{
  e.preventDefault();const f=e.currentTarget,b=f.querySelector('[type="submit"]');b.disabled=true;
  try{const r=await Aeriq.portal('worker-edit',payload(f));document.querySelector('#worker-modal').close();portalFeedback.textContent=r.message;await refresh();}
  catch(error){portalFeedback.textContent=error.message;}finally{b.disabled=false;}
});
function catalogs(){
  // El servidor decide qué rol crea esta cuenta: admin de campaña u operador.
  const title=document.querySelector('#worker-title');if(title&&dashboard.roles?.length)title.textContent='Añadir '+dashboard.roles[0].nombre.toLowerCase();
  const list=document.querySelector('#zone-options');if(list)list.replaceChildren(...(dashboard.zones||[]).map(z=>new Option(z.nombre,z.nombre)));
  const zone=document.querySelector('#report-zone');
  if(zone){const value=zone.value;zone.replaceChildren(new Option('Todas las zonas',''),...(dashboard.zones||[]).map(z=>new Option(z.nombre,z.nombre)));zone.value=[...zone.options].some(o=>o.value===value)?value:'';}
  const form=document.querySelector('#campaign-form');
  if(form&&dashboard.thresholds){form.elements.medium.defaultValue=dashboard.thresholds.umbral_medio;form.elements.high.defaultValue=dashboard.thresholds.umbral_alto;if(!form.elements.id.value){form.elements.medium.value=dashboard.thresholds.umbral_medio;form.elements.high.value=dashboard.thresholds.umbral_alto;}}
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
    if(stats.exceedances.length){const exceed=el('div');exceed.className='table-scroll';host.append(exceed);table(exceed,['Fecha del suceso (UTC)','Valor','Unidad','Umbral medio','Umbral alto','Nivel'],stats.exceedances.map(r=>({cells:[r.capturada_en,r.valor,r.unidad,r.umbral_medio,r.umbral_alto,levelOf(r)]})));}
    const detail=el('div');detail.className='table-scroll';host.append(detail);
    table(detail,['Fecha UTC','Orb','Campaña / zona','Valor','Unidad','Nivel','Coordenadas'],set.map(r=>({cells:[r.capturada_en,r.dispositivo,r.campana+' / '+r.zona,r.valor,r.unidad,thermal?'—':levelOf(r),r.latitud===null?'Sin ubicación':r.latitud+', '+r.longitud]})));
  }
}
document.querySelector('#report-form').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;currentRows=[];document.querySelector('#export-report').disabled=true;document.querySelector('#report-results').replaceChildren();
  try{const filters=payload(f);filters.devices=[...f.querySelector('#report-device').selectedOptions].map(o=>o.value).filter(Boolean).join(',');delete filters.device;const group=filters.group;delete filters.group;lastFilters=filters;currentRows=(await Aeriq.portal('readings',undefined,filters)).readings;renderReport(currentRows,group);document.querySelector('#report-summary').textContent=`${currentRows.length} lecturas válidas autorizadas.`;document.querySelector('#export-report').disabled=!currentRows.length;}
  catch(error){document.querySelector('#report-summary').textContent=error.message;}finally{b.disabled=false;}
};
// El CSV se vuelve a pedir con export=1: el servidor comprueba el permiso datos.exportar.
document.querySelector('#export-report').onclick=async e=>{const b=e.currentTarget;b.disabled=true;let rows;try{rows=(await Aeriq.portal('readings',undefined,{...lastFilters,export:1})).readings;}catch(error){document.querySelector('#report-summary').textContent=error.message;return;}finally{b.disabled=false;}const url=URL.createObjectURL(new Blob([AeriqDomain.csv(rows)],{type:'text/csv;charset=utf-8'}));const a=el('a');a.href=url;a.download='AERIQ-mediciones.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
async function commercial(){
  const {requests,states=[]}=await Aeriq.portal('requests');const host=document.querySelector('#requests-list');host.replaceChildren();
  for(const r of requests){const box=el('article');box.className='portal-section';box.append(el('h3',`#${r.id} · ${r.entidad}`));
    box.append(el('p',`${r.contacto} · ${r.email}${r.telefono?' · '+r.telefono:''} · ${r.recibida_en} UTC`));
    const place=[r.municipio,r.provincia&&`(${r.provincia})`].filter(Boolean).join(' ');
    box.append(el('p',`${r.servicio_nombre||r.servicio} · ${r.tipo_organizacion_nombre||'Tipo de organización sin indicar'}${place?' · '+place:''} · Zona: ${r.zona}`));
    if(r.objetivos?.length)box.append(el('p',`Objetivos: ${r.objetivos.join(', ')}`));
    // Datos de estudio: solo cuando la solicitud los tiene (duración obligatoria en estudios).
    if(r.duracion||r.superficie!=null||r.inicio_deseado)box.append(el('p',`Superficie: ${r.superficie!=null?r.superficie+' m²':'por estimar'} · Duración: ${r.duracion||'—'} · Inicio orientativo: ${r.inicio_deseado?r.inicio_deseado.slice(0,7):'sin indicar'}`));
    box.append(el('p',r.necesidades));
    if(r.historial?.length){const log=el('ol');log.className='request-history';log.setAttribute('aria-label',`Historial de la solicitud ${r.id}`);for(const h of r.historial)log.append(el('li',`${h.estado_nombre} · ${h.cambiado_en} UTC${h.usuario?' · '+h.usuario:' · Formulario público'}`));box.append(log);}
    const select=el('select');select.setAttribute('aria-label',`Estado solicitud ${r.id}`);for(const state of states)select.add(new Option(state.nombre,state.codigo));select.value=r.estado;
    const save=el('button','Guardar estado');save.className='btn btn-outline';save.onclick=async()=>{save.disabled=true;try{await Aeriq.portal('request-status',{id:r.id,status:select.value});portalFeedback.textContent='Estado guardado.';await commercial();}catch(e){portalFeedback.textContent=e.message;}finally{save.disabled=false;}};
    box.append(select,save);host.append(box);
  }
  if(!requests.length)host.append(el('p','Todavía no hay solicitudes.'));
}
(async()=>{try{
  const {user}=await AeriqAuth.session();if(!user){location.href='login.html';return;}if(user.mustChangePassword){location.href='cambiar-clave.html';return;}
  me=user;document.querySelector('#portal-title').textContent=user.organization||'Código Norte';
  if(!can(user,'informes.ver'))throw new Error('Esta cuenta no tiene acceso al panel. Utiliza la aplicación Android para gestionar el Orb que tienes asignado.');
  document.querySelector('#organization-panel').hidden=false;
  for(const n of document.querySelectorAll('[data-perm]:not([data-view])'))n.hidden=!allowed(n);
  await refresh();if(can(user,'solicitudes.gestionar'))await commercial();
  // La flota (sección «Dispositivos Orb») se carga aparte; al guardar un Orb se refrescan los contadores.
  if(typeof initFleet==='function'){void initFleet();addEventListener('aeriq:fleet-changed',()=>void refresh());}
  portalFeedback.textContent='Sesión autorizada · '+(user.roleName||user.role);
  showWorkspace(location.hash.slice(1));
}catch(e){portalFeedback.textContent=e.message;}})();

function showWorkspace(view){
  // Primera sección: «Mi campaña» para el admin de campaña; «Resumen» para el admin principal.
  const first=can(me,'campanas.gestionar')?'overview':'campaign';
  const link=document.querySelector(`.workspace-nav [data-workspace-view="${view}"]`);
  if(!link||!allowed(link))view=first;
  for(const n of document.querySelectorAll('[data-view]'))n.hidden=n.dataset.view!==view||!allowed(n);
  for(const a of document.querySelectorAll('[data-workspace-view]'))a.classList.toggle('active',a.dataset.workspaceView===view);
}
for(const a of document.querySelectorAll('[data-workspace-view]'))a.addEventListener('click',()=>showWorkspace(a.dataset.workspaceView));
window.addEventListener('hashchange',()=>showWorkspace(location.hash.slice(1)));
document.querySelector('#logout').onclick=async e=>{
  const b=e.currentTarget;b.disabled=true;
  try{await AeriqAuth.post('logout');location.href='index.html';}
  catch(err){b.disabled=false;portalFeedback.textContent=err.message;}
};
