'use strict';
const portalFeedback=document.querySelector('#portal-feedback');
// Si algo falla al cargar, se dice en pantalla en vez de quedarse en «Comprobando acceso…».
addEventListener('error',e=>{if(portalFeedback)portalFeedback.textContent='No se pudo cargar el panel ('+e.message+'). Recarga con Ctrl+F5.';});
let dashboard={},currentRows=[],lastFilters={},me={};
const can=(user,permission)=>(user.permissions||[]).includes(permission);
// data-perm="a b": el elemento se muestra si la cuenta tiene alguno de esos permisos.
const allowed=n=>!n.dataset.perm||n.dataset.perm.split(' ').some(p=>can(me,p));
const levelOf=r=>r.umbral_medio===null||r.umbral_medio===undefined?'—':AeriqDomain.level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto));
const el=(tag,text)=>{const n=document.createElement(tag);if(text instanceof Node)n.append(text);else if(text!==undefined)n.textContent=String(text??'—');return n;};
// Estado de una campaña según sus fechas (lo calcula el servidor).
const STATE={activa:['Activa','pill-on'],programada:['Programada','pill-soon'],finalizada:['Finalizada','pill-off']};
const statePill=c=>{const [t,k]=STATE[c.estado]||STATE.activa;const s=document.createElement('span');s.className='pill '+k;s.textContent=t;return s;};
const stateText=c=>c.estado==='programada'?'Programada · empieza el '+c.inicio:c.estado==='finalizada'?'Campaña finalizada':'Campaña activa';
const pill=(text,on)=>{const s=document.createElement('span');s.className='pill '+(on?'pill-on':'pill-off');s.textContent=text;return s;};
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
  options('report-campaign',dashboard.campaigns,c=>c.nombre);
  // Un admin por campaña: solo se ofrecen las campañas que aún no tienen.
  const free=dashboard.campaigns.filter(c=>!c.admin_nombre),sel=document.querySelector('#worker-campaign');
  if(sel){options('worker-campaign',free,c=>c.nombre);if(!free.length)sel.replaceChildren(new Option('Todas las campañas tienen admin',''));}
  if(can(me,'campanas.gestionar'))summary();
  // «Mi campaña» (admin de campaña): el servidor solo devuelve su campaña.
  const mine=can(me,'equipo.gestionar')?dashboard.campaigns[0]:null;
  if(mine){
    const set=(id,text)=>{document.getElementById(id).textContent=text;};
    set('my-campaign-name',mine.nombre);set('my-campaign-state',stateText(mine));
    set('my-campaign-meta',`${mine.zona} · del ${mine.inicio} al ${mine.fin}`);
    set('my-campaign-workers',dashboard.workers.filter(w=>Number(w.puede_medir)).length);
    set('my-campaign-devices',dashboard.devices.filter(d=>Number(d.activo)&&d.trabajador_id).length);
    set('my-campaign-public',Number(mine.publicar)?'Sí':'No');
    fill(document.querySelector('#campaign-settings-form'),{medium:mine.umbral_medio,high:mine.umbral_alto,radius:mine.radio_m,public:mine.publicar});
  }
  options('report-device',dashboard.devices,d=>d.nombre);
  table(document.querySelector('#campaigns-list'),['Campaña','Zona','Fechas','Estado','Admin','Mapa público'],dashboard.campaigns.map(c=>({cells:[c.nombre,c.zona,c.inicio+' — '+c.fin,statePill(c),c.admin_nombre||'Sin admin',Number(c.publicar)?'Sí':'No'],source:c})),can(me,'campanas.gestionar')?c=>{const f=document.querySelector('#campaign-form');f.reset();fill(f,{id:c.id,name:c.nombre,zone:c.zona,start:c.inicio,end:c.fin});campaignMode(c);f.scrollIntoView({behavior:'smooth',block:'center'});}:null);
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
function contact(w){const box=document.createElement('div');box.append(w.email);if(w.telefono){const t=document.createElement('small');t.className='cell-sub';t.textContent=w.telefono;box.append(t);}return box;}
function renderWorkers(){
  const host=document.querySelector('#workers-list');host.replaceChildren();
  const manages=can(me,'campanas.gestionar')?'admin_campana':can(me,'equipo.gestionar')?'trabajador':null;
  document.querySelector('#workers-title').textContent=manages==='trabajador'?'Operadores de tu campaña':'Admins de campaña';
  const t=el('table');t.className='devices-table';const head=el('tr');
  for(const h of ['Nombre','Contacto',...(manages==='trabajador'?['Orb']:['Campaña']),'Acceso',...(manages?['Acciones']:[])])head.append(el('th',h));t.append(head);
  // El admin de campaña ve solo a sus operadores (no a sí mismo).
  for(const w of dashboard.workers.filter(w=>!manages||w.rol===manages)){
    const tr=el('tr');for(const v of [w.nombre+' '+w.apellidos,contact(w),...(manages==='trabajador'?[w.orb||'Sin Orb']:[w.campana||'Sin campaña']),accessText(w)])tr.append(el('td',v));
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
  document.querySelector('#worker-edit-email').value=w.email;document.querySelector('#worker-edit-title').textContent='Editar datos de '+w.nombre;
  document.querySelector('#worker-modal').showModal();
}
document.querySelector('#worker-modal-close')?.addEventListener('click',()=>document.querySelector('#worker-modal').close());
document.querySelector('#worker-edit-form')?.addEventListener('submit',async e=>{
  e.preventDefault();const f=e.currentTarget,b=f.querySelector('[type="submit"]');b.disabled=true;
  try{const r=await Aeriq.portal('worker-edit',payload(f));document.querySelector('#worker-modal').close();portalFeedback.textContent=r.message;await refresh();}
  catch(error){portalFeedback.textContent=error.message;}finally{b.disabled=false;}
});
async function myCampaign(){
  const d=await Aeriq.portal('my-campaign'),c=d.campaign,set=(id,text)=>{document.getElementById(id).textContent=text;};
  set('op-campaign-name',c.nombre);set('op-campaign-state',stateText(c));
  set('op-campaign-meta',`${c.zona} · del ${c.inicio} al ${c.fin}`);
  set('op-orb',d.orb?d.orb.nombre:'Sin Orb');set('op-battery',d.orb&&d.orb.bateria_pct!=null?d.orb.bateria_pct+' %':'—');
  set('op-thresholds',`${Number(c.umbral_medio)} / ${Number(c.umbral_alto)}`);
  set('op-admins',d.admins.length?'Tu admin de campaña: '+d.admins.map(x=>`${x.nombre} ${x.apellidos} (${x.email})`).join(', '):'Tu campaña aún no tiene admin asignado.');
  table(document.querySelector('#op-readings'),['Fecha (UTC)','Magnitud','Valor'],d.readings.map(r=>({cells:[String(r.capturada_en).slice(0,16),r.magnitud,`${r.valor} ${r.unidad}`]})));
}
// Admin principal: resumen de campañas y AERIQ por campaña (solo consulta).
function summary(){
  const set=(id,text)=>{const n=document.getElementById(id);if(n)n.textContent=text;};
  set('sum-campaigns',dashboard.campaigns.filter(c=>c.estado==='activa').length);
  set('sum-admins',dashboard.workers.filter(w=>w.rol==='admin_campana'&&Number(w.activo)).length);
  set('sum-orbs',dashboard.campaigns.reduce((n,c)=>n+Number(c.orbs_en_uso||0),0));
  table(document.querySelector('#campaigns-summary'),['Campaña','Admin','Operadores','AERIQ en uso','Estado'],dashboard.campaigns.map(c=>({cells:[c.nombre,c.admin_nombre||'Sin admin',String(c.operadores),String(c.orbs_en_uso),statePill(c)]})));
  const host=document.querySelector('#orbs-by-campaign');if(!host)return;host.replaceChildren();
  const groups=[...dashboard.campaigns.map(c=>({title:c.nombre,sub:'Admin: '+(c.admin_nombre||'sin asignar'),devices:dashboard.devices.filter(d=>Number(d.campana_id)===Number(c.id))})),
    {title:'Sin asignar',sub:'AERIQ registrados que ahora no lleva nadie',devices:dashboard.devices.filter(d=>!d.campana_id)}];
  for(const g of groups){
    if(g.title==='Sin asignar'&&!g.devices.length)continue;
    const box=el('div');box.className='orb-group';box.append(el('h3',`${g.title} · ${g.devices.length} AERIQ`),el('p',g.sub));
    const list=el('div');list.className='table-scroll';box.append(list);host.append(box);
    table(list,['AERIQ','Operador','Estado','Batería','Última medición'],g.devices.map(d=>({cells:[d.nombre,d.trabajador||'—',Number(d.activo)?'Alta':'Baja',d.bateria_pct!=null?d.bateria_pct+' %':'—',d.ultima_medicion?String(d.ultima_medicion).slice(0,16):'Sin datos']})));
  }
}
// Formulario de campaña: «Crear campaña» o «Editar campaña: …» con su botón de cancelar.
function campaignMode(c){
  const title=document.querySelector('#campaign-form-title'),submit=document.querySelector('#campaign-submit'),cancel=document.querySelector('#campaign-cancel');
  if(!title)return;title.textContent=c?'Editar campaña: '+c.nombre:'Crear campaña';submit.textContent=c?'Editar campaña':'Crear campaña';cancel.hidden=!c;
}
// Un input oculto no se vacía con reset(): sin esto, «Crear campaña» tras editar sobrescribiría la editada.
document.querySelector('#campaign-form')?.addEventListener('reset',e=>{e.currentTarget.elements.id.value='';setTimeout(()=>campaignMode(null));});
document.querySelector('#campaign-cancel')?.addEventListener('click',()=>document.querySelector('#campaign-form').reset());
function catalogs(){
  // El servidor decide qué rol crea esta cuenta: admin de campaña u operador.
  const title=document.querySelector('#worker-title');if(title)title.textContent=can(me,'campanas.gestionar')?'Asignar admin de campaña':'Añadir operador';
  const link=document.querySelector('#workers-link');if(link&&can(me,'campanas.gestionar'))link.textContent='Admins de campaña';
  const list=document.querySelector('#zone-options');if(list)list.replaceChildren(...(dashboard.zones||[]).map(z=>new Option(z.nombre,z.nombre)));
  const zone=document.querySelector('#report-zone');
  if(zone){const value=zone.value;zone.replaceChildren(new Option('Todas las zonas',''),...(dashboard.zones||[]).map(z=>new Option(z.nombre,z.nombre)));zone.value=[...zone.options].some(o=>o.value===value)?value:'';}
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
  const {requests,states=[]}=await Aeriq.portal('requests');
  const pending=document.getElementById('sum-requests');if(pending)pending.textContent=requests.filter(r=>r.estado==='pendiente').length;const host=document.querySelector('#requests-list');host.replaceChildren();
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
  // Operador: solo «Mi campaña», de consulta.
  const operator=!can(user,'informes.ver')&&can(user,'mediciones.registrar');
  if(!can(user,'informes.ver')&&!can(user,'campanas.gestionar')&&!operator)throw new Error('Esta cuenta no tiene acceso al panel.');
  document.querySelector('#organization-panel').hidden=false;
  for(const n of document.querySelectorAll('[data-perm]:not([data-view])'))n.hidden=!allowed(n);
  if(operator){document.querySelector('.workspace-nav').hidden=true;await myCampaign();}
  else {await refresh();if(can(user,'solicitudes.gestionar'))await commercial();
  // La flota (sección «Dispositivos Orb») se carga aparte; al guardar un Orb se refrescan los contadores.
  if(typeof initFleet==='function'&&can(user,'equipo.gestionar')){void initFleet();addEventListener('aeriq:fleet-changed',()=>void refresh());}}
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
