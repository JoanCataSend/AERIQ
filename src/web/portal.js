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
async function myCampaign(){
  const d=await Aeriq.portal('my-campaign'),c=d.campaign,set=(id,text)=>{document.getElementById(id).textContent=text;};
  set('op-campaign-name',c.nombre);set('op-campaign-state',Number(c.activa)?'Campaña activa':'Campaña inactiva');
  set('op-campaign-meta',`${c.zona} · del ${c.inicio} al ${c.fin}`);
  set('op-orb',d.orb?d.orb.nombre:'Sin Orb');set('op-battery',d.orb&&d.orb.bateria_pct!=null?d.orb.bateria_pct+' %':'—');
  set('op-thresholds',`${Number(c.umbral_medio)} / ${Number(c.umbral_alto)}`);
  set('op-admins',d.admins.length?'Tu admin de campaña: '+d.admins.map(x=>`${x.nombre} ${x.apellidos} (${x.email})`).join(', '):'Tu campaña aún no tiene admin asignado.');
  table(document.querySelector('#op-readings'),['Fecha (UTC)','Magnitud','Valor'],d.readings.map(r=>({cells:[String(r.capturada_en).slice(0,16),r.magnitud,`${r.valor} ${r.unidad}`]})));
}
function catalogs(){
  // El servidor decide qué rol crea esta cuenta: admin de campaña u operador.
  const title=document.querySelector('#worker-title');if(title&&dashboard.roles?.length)title.textContent='Añadir '+dashboard.roles[0].nombre.toLowerCase();
  const list=document.querySelector('#zone-options');if(list)list.replaceChildren(...(dashboard.zones||[]).map(z=>new Option(z.nombre,z.nombre)));
  const zone=document.querySelector('#report-zone');
  if(zone){const value=zone.value;zone.replaceChildren(new Option('Todas las zonas',''),...(dashboard.zones||[]).map(z=>new Option(z.nombre,z.nombre)));zone.value=[...zone.options].some(o=>o.value===value)?value:'';}
  const form=document.querySelector('#campaign-form');
  if(form&&dashboard.thresholds){form.elements.medium.defaultValue=dashboard.thresholds.umbral_medio;form.elements.high.defaultValue=dashboard.thresholds.umbral_alto;if(!form.elements.id.value){form.elements.medium.value=dashboard.thresholds.umbral_medio;form.elements.high.value=dashboard.thresholds.umbral_alto;}}
}
const originOf=r=>Number(r.simulado)?'Simulado':'Real';
// Gráfica de medias por periodo con ejes, fechas extremas y, si el grupo comparte umbrales, sus líneas.
// Los puntos en rojo son periodos con alguna lectura que alcanza el umbral medio.
function drawChart(canvas,series,limits){
  const ctx=canvas.getContext?.('2d');if(!ctx||!series.length)return;
  const W=canvas.width,H=canvas.height,L=56,R=16,T=18,B=30;
  const values=series.flatMap(s=>[s.mean,s.max]);if(limits)values.push(limits.medium,limits.high);
  const min=Math.min(0,...values),top=Math.max(min+1,...values),max=top+(top-min)*.08;
  const x=i=>L+(series.length>1?i*(W-L-R)/(series.length-1):(W-L-R)/2),y=v=>H-B-(v-min)/(max-min)*(H-T-B);
  ctx.clearRect(0,0,W,H);ctx.font='12px sans-serif';ctx.lineWidth=1;ctx.strokeStyle='#8a8f8d';ctx.fillStyle='#3d4442';
  ctx.beginPath();ctx.moveTo(L,T);ctx.lineTo(L,H-B);ctx.lineTo(W-R,H-B);ctx.stroke();
  ctx.textAlign='right';ctx.textBaseline='middle';for(const v of [min,(min+max)/2,max])ctx.fillText(v.toFixed(1),L-6,y(v));
  ctx.textBaseline='top';ctx.textAlign='left';ctx.fillText(series[0].date,L,H-B+8);
  if(series.length>1){ctx.textAlign='right';ctx.fillText(series.at(-1).date,W-R,H-B+8);}
  if(limits)for(const [v,color,label] of [[limits.medium,'#b26a00','Umbral medio'],[limits.high,'#b3261e','Umbral alto']]){
    ctx.strokeStyle=color;ctx.fillStyle=color;ctx.setLineDash([6,4]);ctx.beginPath();ctx.moveTo(L,y(v));ctx.lineTo(W-R,y(v));ctx.stroke();ctx.setLineDash([]);
    ctx.textAlign='right';ctx.textBaseline='bottom';ctx.fillText(`${label} · ${v}`,W-R,y(v)-2);
  }
  ctx.strokeStyle='#0e6b5e';ctx.lineWidth=3;ctx.beginPath();series.forEach((s,i)=>i?ctx.lineTo(x(i),y(s.mean)):ctx.moveTo(x(i),y(s.mean)));ctx.stroke();
  for(const [i,s] of series.entries()){const over=limits&&s.max>=limits.medium,r=over?4:2;ctx.fillStyle=over?'#b3261e':'#0e6b5e';ctx.fillRect(x(i)-r,y(s.mean)-r,2*r,2*r);}
}
// Cabecera compacta con los filtros aplicados (el PDF tiene la suya en printReport).
function reportHeader(report){
  const info=report.info,p=reportPeriod(report),box=el('div');box.className='report-header';
  const top=el('div');top.className='report-header-top';
  const title=el('div');title.append(el('h3','Informe ambiental'),el('p',`${me.organization||''}${me.organization?' · ':''}generado el ${fmtDate(report.generated.slice(0,10))} a las ${report.generated.slice(11)} UTC`));
  const count=el('div');count.className='report-count';count.append(el('strong',report.count),el('span',report.count===1?'lectura válida':'lecturas válidas'));
  top.append(title,count);box.append(top);
  const list=el('dl');
  for(const [k,v] of [['Magnitud',info.type],['Campaña',info.campaign],['Zona',info.zone],['Dispositivos',info.devices],['Periodo (UTC)',`${p.from} – ${p.to}`],['Agrupación',info.group]]){const d=el('div');d.append(el('dt',k),el('dd',v));list.append(d);}
  box.append(list);return box;
}
// Cifras clave de una serie en fichas pequeñas.
function reportKpis(stats,unit,thermal){
  const row=el('div');row.className='report-kpis';const n=v=>Number(v).toLocaleString('es-ES',{maximumFractionDigits:2});
  for(const [k,v,alert] of [['Lecturas',stats.count],['Mínimo',`${n(stats.min)} ${unit}`],['Máximo',`${n(stats.max)} ${unit}`],['Media',`${n(stats.mean)} ${unit}`],['Superaciones',thermal?'—':stats.exceedances.length,!thermal&&stats.exceedances.length>0]]){
    const d=el('div');d.className='report-kpi'+(alert?' is-alert':'');d.append(el('strong',v),el('span',k));row.append(d);
  }
  return row;
}
let lastReport=null;
function renderReport(rows,group,info){
  const host=document.querySelector('#report-results');host.replaceChildren();
  lastReport={info,count:rows.length,generated:new Date().toISOString().slice(0,16).replace('T',' '),sets:[]};
  const sets=new Map();for(const r of rows){const key=`${originOf(r)} · ${r.unidad}`;if(!sets.has(key))sets.set(key,[]);sets.get(key).push(r);}
  for(const [name,set] of sets){
    const thermal=set[0].unidad==='°C',simulated=Boolean(Number(set[0].simulado));const stats=AeriqDomain.statistics(set,group);
    const model={name,set,thermal,simulated,stats,limits:null,canvas:null};lastReport.sets.push(model);
    const block=el('section');block.className='report-block';host.append(block);
    const head=el('h3',name+' ');const badge=el('span',simulated?'Dato simulado':'Dato real');badge.className='report-badge'+(simulated?' is-simulated':'');head.append(badge);block.append(head);
    if(simulated){const warn=el('p','Datos simulados: no representan mediciones reales.');warn.className='report-simulated';block.append(warn);}
    block.append(reportKpis(stats,set[0].unidad,thermal));
    const limits=thermal?null:AeriqDomain.sharedThresholds(set);
    const canvas=el('canvas');canvas.width=900;canvas.height=240;canvas.style.width='100%';canvas.setAttribute('role','img');
    canvas.setAttribute('aria-label','Evolución de valores medios'+(limits?` con umbral medio ${limits.medium} y alto ${limits.high}`:'')+'; datos disponibles en la tabla siguiente');
    block.append(canvas);drawChart(canvas,stats.series,limits);model.limits=limits;model.canvas=canvas;
    if(!thermal)block.append(el('p',limits?'Línea discontinua: umbrales de la campaña. Puntos rojos: periodos con alguna lectura igual o superior al umbral medio.':'Las lecturas tienen umbrales distintos según su campaña; consulta el nivel en la tabla de superaciones.'));
    const summary=el('div');block.append(summary);table(summary,['Periodo (UTC)','Mínimo','Máximo','Media'],stats.series.map(s=>({cells:[s.date,s.min,s.max,s.mean.toFixed(2)]})));
    block.append(el('p',thermal?'Temperatura del módulo en °C. No se aplican los umbrales de ozono.':`${stats.exceedances.length} lecturas alcanzan o superan el umbral medio de su campaña.`));
    if(stats.exceedances.length){const exceed=el('div');exceed.className='table-scroll report-exceedances';block.append(exceed);table(exceed,['Fecha del suceso (UTC)','Origen','Valor','Unidad','Umbral medio','Umbral alto','Nivel'],stats.exceedances.map(r=>({cells:[String(r.capturada_en).slice(0,19),originOf(r),r.valor,r.unidad,r.umbral_medio,r.umbral_alto,levelOf(r)]})));}
    const detail=el('div');detail.className='table-scroll';block.append(detail);
    table(detail,['Fecha UTC','Orb','Campaña / zona','Valor','Unidad','Nivel','Origen','Coordenadas'],set.map(r=>({cells:[String(r.capturada_en).slice(0,19),r.dispositivo,r.campana+' / '+r.zona,r.valor,r.unidad,thermal?'—':levelOf(r),originOf(r),r.latitud===null?'Sin ubicación':Number(r.latitud).toFixed(5)+', '+Number(r.longitud).toFixed(5)]})));
  }
  if(info)host.prepend(reportHeader(lastReport));
}
// Textos legibles de los filtros elegidos, para la cabecera del informe.
function reportInfo(f,filters,group){
  const text=name=>{const s=f.elements.namedItem(name);return s.selectedOptions?.[0]?.textContent||'';};
  const devices=[...f.querySelector('#report-device').selectedOptions].filter(o=>o.value).map(o=>o.textContent);
  return {type:text('type'),campaign:filters.campaign?text('campaign'):'Todas las autorizadas',zone:filters.zone||'Todas las zonas',devices:devices.length?devices.join(', '):'Todos',
    period:`${filters.from||'desde el inicio'} — ${filters.to||'hasta hoy'}`,group:group==='week'?'Semanas':'Días',from:filters.from||'',to:filters.to||'',campaignSet:Boolean(filters.campaign),zoneSet:Boolean(filters.zone)};
}
const fmtDate=d=>d?`${d.slice(8,10)}/${d.slice(5,7)}/${d.slice(0,4)}`:'';
// Periodo legible: el del filtro o, si falta un extremo, el de los propios datos.
function reportPeriod(report){
  const all=report.sets.flatMap(s=>s.set.map(r=>String(r.capturada_en).slice(0,10))).sort();
  const from=report.info.from||all[0]||'',to=report.info.to||all.at(-1)||'';
  return {from:fmtDate(from),to:fmtDate(to),fromData:!report.info.from,toData:!report.info.to};
}
function reportDescription(report){
  const i=report.info,p=reportPeriod(report),simulated=report.sets.some(s=>s.simulated),real=report.sets.some(s=>!s.simulated);
  const what=i.type.split(' · ')[0].replace(/\s*\(.*\)$/,'').toLowerCase();
  return `Este informe recoge las mediciones válidas de ${what} registradas por los dispositivos Orb `+
    `${i.campaignSet?`de la campaña «${i.campaign}»`:'de todas las campañas autorizadas'}`+
    `${i.zoneSet?` en la zona «${i.zone}»`:' en todas las zonas'}`+
    `${i.devices!=='Todos'?` (dispositivos: ${i.devices})`:''}, entre el ${p.from} y el ${p.to} (UTC), agrupadas por ${i.group.toLowerCase()}. `+
    `Incluye ${report.count} ${report.count===1?"lectura válida":"lecturas válidas"}. Las lecturas no válidas se descartan antes de calcular mínimos, máximos y medias, y `+
    `las superaciones se evalúan respecto a los umbrales de ozono definidos para cada campaña. `+
    (simulated&&real?'Los datos reales y los simulados se presentan y calculan por separado.':simulated?'Todos los datos de este informe son simulados y no representan mediciones reales.':'Todos los datos de este informe proceden de mediciones reales.');
}
const PRINT_CSS=`@page{size:A4;margin:16mm 14mm}*{box-sizing:border-box}
body{margin:0;font:10pt/1.45 "Segoe UI",Roboto,Arial,sans-serif;color:#1d2522;-webkit-print-color-adjust:exact;print-color-adjust:exact}
.cover{display:flex;justify-content:space-between;align-items:flex-end;border-bottom:3px solid #0e6b5e;padding-bottom:10px;margin-bottom:16px}
.cover img{height:34px}.cover .org{text-align:right;color:#56635e;font-size:9pt}
h1{font-size:22pt;margin:0 0 4px;color:#0e3d36}.subtitle{margin:0 0 14px;color:#56635e;font-size:11pt}
.desc{background:#f2f7f5;border-left:4px solid #0e6b5e;padding:10px 14px;margin:0 0 16px;text-align:justify}
.filters{display:grid;grid-template-columns:repeat(3,1fr);gap:0;border:1px solid #d5ddda;border-radius:6px;margin-bottom:20px;overflow:hidden}
.filters div{padding:7px 10px;border-right:1px solid #d5ddda;border-bottom:1px solid #d5ddda}.filters div:nth-child(3n){border-right:0}
.filters dt{font-size:7pt;font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:#6b7a74}.filters dd{margin:2px 0 0;font-weight:600}
section{break-before:page}section.first{break-before:auto}
h2{font-size:14pt;margin:0 0 8px;color:#0e3d36;display:flex;align-items:center;gap:10px}
.badge{font-size:8pt;font-weight:700;padding:3px 8px;border-radius:10px;background:#e3f1ec;color:#0e6b5e}.badge.sim{background:#fff1cc;color:#7a4b00;border:1px solid #e0b450}
.simwarn{background:#fff7e0;border:1px solid #e0b450;color:#7a4b00;padding:6px 10px;border-radius:6px;font-weight:600;margin:0 0 10px}
.kpis{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin:0 0 12px}.kpi{border:1px solid #d5ddda;border-radius:6px;padding:8px;text-align:center}
.kpi b{display:block;font-size:15pt;color:#0e3d36}.kpi small{font-size:7.5pt;color:#6b7a74;text-transform:uppercase;letter-spacing:.06em}.kpi.alert b{color:#b3261e}
figure{margin:0 0 6px;break-inside:avoid}figure img{width:100%;border:1px solid #e3e8e6;border-radius:6px}figcaption{font-size:8pt;color:#6b7a74;margin-top:4px}
h3{font-size:11pt;margin:16px 0 6px;color:#0e3d36}
table{width:100%;border-collapse:collapse;font-size:8.5pt}thead{display:table-header-group}tr{break-inside:avoid}
th{background:#0e6b5e;color:#fff;text-align:left;padding:5px 7px;font-weight:600}td{padding:4px 7px;border-bottom:1px solid #e3e8e6}tbody tr:nth-child(even) td{background:#f7faf9}
td.alto{color:#b3261e;font-weight:700}td.moderado{color:#b26a00;font-weight:700}td.sim{color:#7a4b00;font-weight:700}
.note{font-size:8pt;color:#6b7a74}.footer{margin-top:24px;padding-top:8px;border-top:1px solid #d5ddda;font-size:8pt;color:#6b7a74}`;
const ANNEX_MAX=500;
// Documento de informe independiente en un iframe oculto: se imprime solo el informe, sin la página del portal.
function printReport(){
  const report=lastReport;if(!report)return;
  const frame=document.createElement('iframe');frame.setAttribute('aria-hidden','true');frame.tabIndex=-1;
  Object.assign(frame.style,{position:'fixed',right:'0',bottom:'0',width:'0',height:'0',border:'0',visibility:'hidden'});document.body.append(frame);
  const doc=frame.contentDocument,title='Informe ambiental AERIQ',file='AERIQ-informe-'+report.generated.slice(0,10);
  doc.open();doc.write('<!doctype html><html lang="es"><head><meta charset="utf-8"><title></title><style></style></head><body></body></html>');doc.close();
  doc.title=file;doc.querySelector('style').textContent=PRINT_CSS;
  const h=(tag,props={},...kids)=>{const n=doc.createElement(tag);for(const [k,v] of Object.entries(props))if(v!==undefined&&v!==null)(k==='class'?n.className=v:n.setAttribute(k,v));for(const k of kids)n.append(k instanceof doc.defaultView.Node?k:String(k??'—'));return n;};
  const tableOf=(headers,rows,cls)=>h('table',{},h('thead',{},h('tr',{},...headers.map(x=>h('th',{},x)))),h('tbody',{},...rows.map(r=>h('tr',{},...r.map((c,i)=>h('td',{class:cls?.(c,i)},c))))));
  const p=reportPeriod(report),i=report.info,body=doc.body;
  const logo=h('img',{src:new URL('assets/aeriq_logo.png',location.href).href,alt:'AERIQ'});
  body.append(h('div',{class:'cover'},logo,h('div',{class:'org'},me.organization||'',h('br'),`Generado el ${fmtDate(report.generated.slice(0,10))} a las ${report.generated.slice(11)} UTC`)));
  body.append(h('h1',{},title),h('p',{class:'subtitle'},`${i.type} · ${i.campaignSet?i.campaign:'Todas las campañas'} · del ${p.from} al ${p.to}`));
  body.append(h('p',{class:'desc'},reportDescription(report)));
  const filters=h('dl',{class:'filters'});
  for(const [k,v] of [['Magnitud',i.type],['Campaña',i.campaign],['Zona',i.zone],['Dispositivos',i.devices],['Desde (UTC)',p.from+(p.fromData?' · según los datos':'')],['Hasta (UTC)',p.to+(p.toData?' · según los datos':'')],['Agrupación',i.group],['Lecturas válidas',report.count],['Series',report.sets.map(s=>s.name).join(' / ')]])filters.append(h('div',{},h('dt',{},k),h('dd',{},v)));
  body.append(filters);
  report.sets.forEach((s,n)=>{
    const st=s.stats,unit=s.set[0].unidad,sec=h('section',{class:n?'':'first'});body.append(sec);
    sec.append(h('h2',{},s.name,h('span',{class:'badge'+(s.simulated?' sim':'')},s.simulated?'Dato simulado':'Dato real')));
    if(s.simulated)sec.append(h('p',{class:'simwarn'},'Datos simulados: no representan mediciones reales y no deben usarse como evidencia ambiental.'));
    const kpis=h('div',{class:'kpis'});
    for(const [k,v,alert] of [['Lecturas',st.count],['Mínimo',`${st.min} ${unit}`],['Máximo',`${st.max} ${unit}`],['Media',`${st.mean.toLocaleString('es-ES',{maximumFractionDigits:2})} ${unit}`],['Superaciones',s.thermal?'—':st.exceedances.length,!s.thermal&&st.exceedances.length]])kpis.append(h('div',{class:'kpi'+(alert?' alert':'')},h('b',{},v),h('small',{},k)));
    sec.append(kpis);
    let src=null;try{src=s.canvas?.toDataURL('image/png');}catch{src=null;}
    if(src&&src.startsWith('data:image'))sec.append(h('figure',{},h('img',{src,alt:'Evolución de valores medios'}),h('figcaption',{},s.thermal?'Media por periodo (°C). No se aplican umbrales de ozono.':s.limits?`Media por periodo. Líneas discontinuas: umbral medio (${s.limits.medium} ${unit}) y alto (${s.limits.high} ${unit}). Puntos rojos: periodos con alguna lectura igual o superior al umbral medio.`:'Media por periodo. Las lecturas tienen umbrales distintos según su campaña; ver tabla de superaciones.')));
    sec.append(h('h3',{},'Evolución temporal'),tableOf(['Periodo (UTC)',`Mínimo (${unit})`,`Máximo (${unit})`,`Media (${unit})`],st.series.map(x=>[x.date,x.min,x.max,x.mean.toFixed(2)])));
    if(!s.thermal){
      sec.append(h('h3',{},'Superaciones de umbral'));
      if(st.exceedances.length)sec.append(tableOf(['Fecha (UTC)','Orb','Origen','Valor','Umbral medio','Umbral alto','Nivel'],st.exceedances.map(r=>[String(r.capturada_en).slice(0,19),r.dispositivo,s.simulated?'Simulado':'Real',`${r.valor} ${r.unidad}`,r.umbral_medio,r.umbral_alto,levelOf(r)]),(c,col)=>col===6?String(c):col===2&&c==='Simulado'?'sim':undefined));
      else sec.append(h('p',{class:'note'},'Ninguna lectura alcanza el umbral medio de su campaña en este periodo.'));
    }
    sec.append(h('h3',{},'Anexo: lecturas'));
    const rows=s.set.slice(0,ANNEX_MAX).map(r=>[String(r.capturada_en).slice(0,19),r.dispositivo,`${r.campana} / ${r.zona}`,`${r.valor} ${r.unidad}`,s.thermal?'—':levelOf(r),s.simulated?'Simulado':'Real']);
    sec.append(tableOf(['Fecha (UTC)','Orb','Campaña / zona','Valor','Nivel','Origen'],rows,(c,col)=>col===4&&['alto','moderado'].includes(c)?c:col===5&&c==='Simulado'?'sim':undefined));
    if(s.set.length>ANNEX_MAX)sec.append(h('p',{class:'note'},`Se muestran las primeras ${ANNEX_MAX} de ${s.set.length} lecturas. El listado completo está en «Descargar CSV».`));
  });
  body.append(h('p',{class:'footer'},'AERIQ · Código Norte. Ozono en ppb, temperatura en °C y fechas en UTC. Solo se incluyen lecturas válidas autorizadas para esta cuenta; los datos simulados se identifican como tales.'));
  const win=frame.contentWindow,cleanup=()=>setTimeout(()=>frame.remove(),1000);
  const ready=logo.complete?Promise.resolve():new Promise(r=>{logo.onload=logo.onerror=r;setTimeout(r,2000);});
  const old=document.title;
  ready.then(()=>{document.title=file;win.addEventListener('afterprint',()=>{document.title=old;cleanup();},{once:true});win.focus();win.print();setTimeout(()=>{document.title=old;},5000);});
}
document.querySelector('#report-form').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;currentRows=[];document.querySelector('#export-report').disabled=true;document.querySelector('#print-report').disabled=true;document.querySelector('#report-results').replaceChildren();
  try{const filters=payload(f);filters.devices=[...f.querySelector('#report-device').selectedOptions].map(o=>o.value).filter(Boolean).join(',');delete filters.device;const group=filters.group;delete filters.group;lastFilters=filters;currentRows=(await Aeriq.portal('readings',undefined,filters)).readings;renderReport(currentRows,group,reportInfo(f,filters,group));document.querySelector('#report-summary').textContent=`${currentRows.length} lecturas válidas autorizadas.`;document.querySelector('#export-report').disabled=!currentRows.length;document.querySelector('#print-report').disabled=!currentRows.length;}
  catch(error){document.querySelector('#report-summary').textContent=error.message;}finally{b.disabled=false;}
};
// PDF sin librerías: printReport() maqueta el informe aparte y el navegador ofrece «Guardar como PDF».
document.querySelector('#print-report').onclick=printReport;
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
  // Operador: solo «Mi campaña», de consulta.
  const operator=!can(user,'informes.ver')&&can(user,'mediciones.registrar');
  if(!can(user,'informes.ver')&&!operator)throw new Error('Esta cuenta no tiene acceso al panel.');
  document.querySelector('#organization-panel').hidden=false;
  for(const n of document.querySelectorAll('[data-perm]:not([data-view])'))n.hidden=!allowed(n);
  if(operator){document.querySelector('.workspace-nav').hidden=true;await myCampaign();}
  else {await refresh();if(can(user,'solicitudes.gestionar'))await commercial();
  // La flota (sección «Dispositivos Orb») se carga aparte; al guardar un Orb se refrescan los contadores.
  if(typeof initFleet==='function'){void initFleet();addEventListener('aeriq:fleet-changed',()=>void refresh());}}
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
