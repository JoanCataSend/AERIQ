'use strict';
const portalFeedback=document.querySelector('#portal-feedback');
// Si algo falla al cargar, se dice en pantalla en vez de quedarse en «Comprobando acceso…».
addEventListener('error',e=>aeriqStatus(portalFeedback,'No se pudo cargar el panel ('+e.message+'). Recarga con Ctrl+F5.','error'));
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
    set('my-campaign-meta',`${mine.zona} · del ${AeriqDomain.when(mine.inicio,{hour:false})} al ${AeriqDomain.when(mine.fin,{hour:false})}`);
    progress(mine,'my-campaign');
    set('my-campaign-workers',dashboard.workers.filter(w=>Number(w.puede_medir)).length);
    set('my-campaign-devices',dashboard.devices.filter(d=>Number(d.activo)&&d.trabajador_id).length);
    set('my-campaign-public',Number(mine.publicar)?'Sí':'No');
    fill(document.querySelector('#campaign-settings-form'),{medium:mine.umbral_medio,high:mine.umbral_alto,radius:mine.radio_m,public:mine.publicar});
  }
  const pick=document.querySelector('#report-device');
  if(pick){const keep=new Set([...pick.querySelectorAll('input:checked')].map(i=>i.value));pick.replaceChildren();
    for(const d of dashboard.devices){const l=el('label');l.className='check-pill';const i=el('input');i.type='checkbox';i.value=d.id;i.checked=keep.has(String(d.id));l.append(i,el('span',d.nombre));pick.append(l);}
    if(!dashboard.devices.length)pick.append(el('small','Todavía no hay AERIQ registrados.'));}
  // Una sola campaña o zona: el selector no aporta nada y se oculta.
  for(const [id,n] of [['report-campaign',dashboard.campaigns.length],['report-zone',(dashboard.zones||[]).length]]){const l=document.getElementById(id)?.closest('label');if(l)l.hidden=n<=1;}
  table(document.querySelector('#campaigns-list'),['Campaña','Zona','Fechas','Estado','Admin','Mapa público'],dashboard.campaigns.map(c=>({cells:[c.nombre,c.zona,c.inicio+' — '+c.fin,statePill(c),c.admin_nombre||'Sin admin',Number(c.publicar)?'Sí':'No'],source:c})),can(me,'campanas.gestionar')?c=>{const f=document.querySelector('#campaign-form');f.reset();fill(f,{id:c.id,name:c.nombre,zone:c.zona,start:c.inicio,end:c.fin});campaignMode(c);f.scrollIntoView({behavior:'smooth',block:'center'});}:null);
}
for(const [id,action] of [['worker-form','worker'],['campaign-form','campaign'],['campaign-settings-form','campaign-settings']]){
  if(!document.getElementById(id))continue;
  document.getElementById(id).onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('[type="submit"]');b.disabled=true;
    try{const result=await Aeriq.portal(action,payload(f));f.reset();await refresh();aeriqStatus(portalFeedback,result.message||'Cambios guardados.','ok');}
    catch(error){aeriqStatus(portalFeedback,error.message,'error');}finally{b.disabled=false;}
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
  for(const h of ['Nombre','Contacto',...(manages==='trabajador'?['AERIQ']:['Campaña']),'Acceso',...(manages?['Acciones']:[])])head.append(el('th',h));t.append(head);
  // El admin de campaña ve solo a sus operadores (no a sí mismo).
  for(const w of dashboard.workers.filter(w=>!manages||w.rol===manages)){
    const tr=el('tr');for(const v of [w.nombre+' '+w.apellidos,contact(w),...(manages==='trabajador'?[w.orb||'Sin AERIQ']:[w.campana||'Sin campaña']),accessText(w)])tr.append(el('td',v));
    if(manages){
      const td=el('td');td.className='row-actions';
      if(w.rol===manages){
        const act=(text,run,cls='text-link')=>{const b=el('button',text);b.type='button';b.className=cls;b.onclick=()=>run(b);td.append(b);};
        const call=async(b,action,body)=>{b.disabled=true;try{const r=await Aeriq.portal(action,body);aeriqStatus(portalFeedback,r.message||'Cambios guardados.','ok');await refresh();}catch(e){aeriqStatus(portalFeedback,e.message,'error');b.disabled=false;}};
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
  try{const r=await Aeriq.portal('worker-edit',payload(f));document.querySelector('#worker-modal').close();aeriqStatus(portalFeedback,r.message,'ok');await refresh();}
  catch(error){aeriqStatus(portalFeedback,error.message,'error');}finally{b.disabled=false;}
});
// Barra de tiempo de una campaña: «Día 8 de 68 · quedan 61 días».
function progress(c,prefix){
  const day=864e5,start=Date.parse(c.inicio+'T00:00:00Z'),end=Date.parse(c.fin+'T00:00:00Z')+day,now=Date.now(),total=Math.max(1,Math.round((end-start)/day));
  const done=Math.min(total,Math.max(0,Math.floor((now-start)/day)+1)),left=Math.max(0,Math.ceil((end-now)/day));
  const bar=document.querySelector(`#${prefix}-progress span`);if(bar)bar.style.width=(now<start?0:Math.round(done/total*100))+'%';
  const text=document.getElementById(prefix+'-days');if(text)text.textContent=now<start?`Empieza en ${Math.ceil((start-now)/day)} días · dura ${total} días`:now>=end?`Terminada · duró ${total} días`:`Día ${done} de ${total} · quedan ${left} días`;
}
async function myCampaign(){
  const d=await Aeriq.portal('my-campaign'),c=d.campaign,set=(id,text)=>{document.getElementById(id).textContent=text;};
  set('op-campaign-name',c.nombre);set('op-campaign-state',stateText(c));
  set('op-campaign-meta',`${c.zona} · del ${AeriqDomain.when(c.inicio,{hour:false})} al ${AeriqDomain.when(c.fin,{hour:false})}`);progress(c,'op-campaign');
  // Tu AERIQ: nombre, si está enviando, batería y fechas.
  const o=d.orb,last=d.readings[0]?.capturada_en,recent=last&&Date.now()-Date.parse(String(last).replace(' ','T')+'Z')<9e5;
  set('op-orb',o?o.nombre:'Sin AERIQ asignado');
  const status=document.getElementById('op-status');status.textContent=!o?'Tu admin de campaña te lo asignará.':recent?'Enviando lecturas':'Sin lecturas en los últimos 15 minutos';status.className='op-status'+(o&&recent?' on':'');
  const pct=o&&o.bateria_pct!=null?Number(o.bateria_pct):null,fill=document.getElementById('op-battery-fill');
  fill.style.width=(pct??0)+'%';fill.className=pct==null?'':pct>60?'high':pct>=20?'mid':'low';
  set('op-battery',o?'Batería: '+AeriqDomain.battery(o.bateria_pct,o.bateria_en):'Batería: —');
  set('op-since',o?.iniciada_en?AeriqDomain.when(o.iniciada_en):'—');set('op-last',last?AeriqDomain.when(last,{seconds:true}):'Todavía ninguna');
  // Escala de niveles con los umbrales de la campaña.
  const mid=Number(c.umbral_medio),high=Number(c.umbral_alto);
  set('op-level-low',`menos de ${mid} ppb`);set('op-level-mid',`${mid} – ${high} ppb`);set('op-level-high',`${high} ppb o más`);
  // Admin: nombre y correo pulsable.
  const box=document.getElementById('op-admins');box.replaceChildren();
  for(const x of d.admins){const row=el('div');row.className='op-admin';const av=el('span',((x.nombre||'')[0]||'')+((x.apellidos||'')[0]||''));av.className='op-avatar';const t=el('div');const m=el('a',x.email);m.href='mailto:'+x.email;t.append(el('strong',`${x.nombre} ${x.apellidos}`),m);row.append(av,t);box.append(row);}
  if(!d.admins.length)box.append(el('p','Tu campaña aún no tiene admin asignado.'));
  // Lecturas: el ozono lleva su nivel con color.
  const lvl=r=>{if(r.unidad!=='ppb')return '—';const k=AeriqDomain.level(Number(r.valor),mid,high),s=el('span',k[0].toUpperCase()+k.slice(1));s.className='level-tag '+k;return s;};
  table(document.querySelector('#op-readings'),['Fecha','Magnitud','Valor','Nivel'],d.readings.map(r=>({cells:[AeriqDomain.when(r.capturada_en,{seconds:true}),r.magnitud,`${r.valor} ${r.unidad}`,lvl(r)]})));
  if(!d.readings.length)document.querySelector('#op-readings').replaceChildren(el('p','Todavía no hay lecturas de tu AERIQ en esta campaña.'));
}
// Admin principal: resumen de campañas y AERIQ por campaña (solo consulta).
function summary(){
  const set=(id,text)=>{const n=document.getElementById(id);if(n)n.textContent=text;};
  set('sum-campaigns',dashboard.campaigns.filter(c=>c.estado==='activa').length);
  set('sum-admins',dashboard.workers.filter(w=>w.rol==='admin_campana'&&Number(w.activo)).length);
  set('sum-orbs',dashboard.campaigns.reduce((n,c)=>n+Number(c.orbs_en_uso||0),0));
  table(document.querySelector('#campaigns-summary'),['Campaña','Admin','Operadores','AERIQ en uso','Estado'],dashboard.campaigns.map(c=>({cells:[c.nombre,c.admin_nombre||'Sin admin',String(c.operadores),String(c.orbs_en_uso),statePill(c)]})));
  const host=document.querySelector('#orbs-by-campaign');if(!host||typeof AeriqDeviceTable==='undefined')return;
  // Una sola tabla con la campaña como columna: se filtra por campaña, estado, actividad y operador y se ordena por columna.
  orbsTable||=AeriqDeviceTable.mount(host,{columns:['nombre','campana','operador','actividad','ultima','bateria'],campaigns:true,empty:'Todavía no hay AERIQ registrados en la organización.'});
  orbsTable.set(dashboard.devices);
}
let orbsTable=null;
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
const originOf=r=>Number(r.simulado)?'Simulado':'Real';
// Gráfica de la evolución (T39): media de cada periodo sobre un eje de tiempo real (los huecos sin datos se ven),
// eje Y con la unidad y eje X con fechas u horas locales. Si el grupo comparte umbrales, sus líneas discontinuas.
// Los picos (T40) se marcan con un triángulo y su valor: rojo si llegan al umbral alto, ámbar si al medio.
function drawChart(canvas,series,limits,opts={}){
  const ctx=canvas.getContext?.('2d');if(!ctx||!series.length)return;
  const unit=opts.unit||'',group=opts.group||'day',peaks=opts.peaks||[];
  const W=canvas.width,H=canvas.height,L=64,R=18,T=22,B=42;
  const values=series.flatMap(s=>[s.mean,s.max]);if(limits)values.push(limits.medium,limits.high);for(const p of peaks)values.push(p.valor);
  const min=Math.min(0,...values),top=Math.max(min+1,...values),max=top+(top-min)*.12;
  const time=d=>Date.parse(String(d).length<=10?d+'T00:00:00Z':String(d).replace(' ','T')+'Z');
  const t0=time(series[0].date),t1=Math.max(time(series.at(-1).date),...peaks.map(p=>time(p.capturada_en)));
  const x=t=>L+(t1>t0?(t-t0)*(W-L-R)/(t1-t0):(W-L-R)/2),y=v=>H-B-(v-min)/(max-min)*(H-T-B);
  ctx.clearRect(0,0,W,H);ctx.font='12px sans-serif';ctx.lineWidth=1;
  // Rejilla y ejes.
  ctx.strokeStyle='#e3e8e6';for(let i=0;i<=4;i++){const v=min+(max-min)*i/4;ctx.beginPath();ctx.moveTo(L,y(v));ctx.lineTo(W-R,y(v));ctx.stroke();}
  ctx.strokeStyle='#8a8f8d';ctx.beginPath();ctx.moveTo(L,T);ctx.lineTo(L,H-B);ctx.lineTo(W-R,H-B);ctx.stroke();
  ctx.fillStyle='#3d4442';ctx.textAlign='right';ctx.textBaseline='middle';
  for(let i=0;i<=4;i++){const v=min+(max-min)*i/4;ctx.fillText(v.toFixed(v>=100?0:1),L-8,y(v));}
  // Unidad del eje Y (vertical) y del eje X.
  ctx.save();ctx.translate(14,(T+H-B)/2);ctx.rotate(-Math.PI/2);ctx.textAlign='center';ctx.font='bold 12px sans-serif';ctx.fillText(unit?`Valor (${unit})`:'Valor',0,0);ctx.restore();
  const sameDay=new Date(t0).toDateString()===new Date(t1).toDateString(),pad=n=>String(n).padStart(2,'0');
  const label=t=>{const d=new Date(t);const day=`${d.getDate()} ${['ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic'][d.getMonth()]}`;
    return group==='hour'?(sameDay?`${pad(d.getHours())}:${pad(d.getMinutes())}`:`${day} ${pad(d.getHours())}:00`):day;};
  const ticks=t1>t0?Math.min(6,series.length):1;ctx.textBaseline='top';
  for(let i=0;i<ticks;i++){const t=ticks===1?t0:t0+(t1-t0)*i/(ticks-1);ctx.textAlign=i===0&&ticks>1?'left':i===ticks-1&&ticks>1?'right':'center';ctx.fillText(label(t),x(t),H-B+8);}
  ctx.textAlign='center';ctx.font='bold 12px sans-serif';ctx.fillText(group==='hour'?'Hora local':group==='week'?'Semana':'Día',(L+W-R)/2,H-16);ctx.font='12px sans-serif';
  if(limits)for(const [v,color,text] of [[limits.medium,'#b26a00','Umbral medio'],[limits.high,'#b3261e','Umbral alto']]){
    ctx.strokeStyle=color;ctx.fillStyle=color;ctx.setLineDash([6,4]);ctx.beginPath();ctx.moveTo(L,y(v));ctx.lineTo(W-R,y(v));ctx.stroke();ctx.setLineDash([]);
    ctx.textAlign='right';ctx.textBaseline='bottom';ctx.fillText(`${text} · ${v} ${unit}`,W-R,y(v)-2);
  }
  ctx.strokeStyle='#0e6b5e';ctx.lineWidth=3;ctx.lineJoin='round';ctx.beginPath();series.forEach((s,i)=>i?ctx.lineTo(x(time(s.date)),y(s.mean)):ctx.moveTo(x(time(s.date)),y(s.mean)));ctx.stroke();
  for(const s of series){const over=limits&&s.max>=limits.medium,r=over?4:3;ctx.fillStyle=over?'#b3261e':'#0e6b5e';ctx.beginPath();ctx.arc(x(time(s.date)),y(s.mean),r,0,Math.PI*2);ctx.fill();}
  // Picos: triángulo en la hora exacta y con el valor de la lectura más alta del tramo.
  for(const p of peaks){
    const px=x(time(p.capturada_en)),py=y(p.valor),color=p.nivel==='alto'?'#b3261e':'#b26a00';
    ctx.fillStyle=color;ctx.strokeStyle='#fff';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(px,py-9);ctx.lineTo(px-7,py+4);ctx.lineTo(px+7,py+4);ctx.closePath();ctx.fill();ctx.stroke();
    ctx.textAlign='center';ctx.textBaseline='bottom';ctx.font='bold 12px sans-serif';ctx.fillText(`${p.valor}`,px,py-11);ctx.font='12px sans-serif';
  }
}
// Aviso de picos (T40): solo aparece si los hay; dice la hora y el valor de cada uno.
function peakAlert(peaks){
  if(!peaks.length)return null;
  const box=el('div');box.className='report-peaks'+(peaks.some(p=>p.nivel==='alto')?' is-high':'');box.setAttribute('role','alert');
  const n=peaks.length;box.append(el('strong',`${n} ${n===1?'pico de ozono':'picos de ozono'} en el periodo`));
  // Los más altos primero; con muchos, se enseñan 8 y el resto está en la tabla de superaciones.
  const top=[...peaks].sort((a,b)=>b.valor-a.valor).slice(0,8);
  const list=el('ul');for(const p of top)list.append(el('li',`${AeriqDomain.when(p.capturada_en)} · ${p.valor} ${p.unidad} · nivel ${p.nivel}${p.dispositivo?' · '+p.dispositivo:''}${p.lecturas>1?` (${p.lecturas} lecturas seguidas sobre el umbral)`:''}`));
  box.append(list);if(peaks.length>top.length)box.append(el('small',`Y ${peaks.length-top.length} picos más: están en la tabla de superaciones.`));return box;
}
// Veredicto por franjas horarias (T41): cuatro fichas de color y un texto que destaca las buenas y las malas.
function verdictBlock(v){
  const box=el('div');box.className='report-verdict';box.append(el('h4','Veredicto de calidad del aire por franja horaria'));
  const grid=el('div');grid.className='report-franjas';
  for(const f of v.franjas){const card=el('div');card.className='report-franja '+(f.calidad?'is-'+f.calidad:'is-empty');
    card.append(el('strong',f.calidad?f.calidad[0].toUpperCase()+f.calidad.slice(1):'Sin datos'),el('span',`${f.nombre} · ${f.horas}`),el('small',f.lecturas?`media ${Math.round(f.media)} ppb · ${f.lecturas} lecturas`:'sin lecturas'));grid.append(card);}
  const text=el('p',v.texto);text.className='report-verdict-text';box.append(grid,text);return box;
}
// Cabecera compacta con los filtros aplicados (el PDF tiene la suya en printReport).
function reportHeader(report){
  const info=report.info,p=reportPeriod(report),box=el('div');box.className='report-header';
  const top=el('div');top.className='report-header-top';
  const title=el('div');title.append(el('h3','Informe ambiental'),el('p',`${me.organization||''}${me.organization?' · ':''}generado el ${AeriqDomain.when(report.generated)}`));
  const count=el('div');count.className='report-count';count.append(el('strong',report.count),el('span',report.count===1?'lectura válida':'lecturas válidas'));
  top.append(title,count);box.append(top);
  const list=el('dl');
  for(const [k,v] of [['Magnitud',info.type],['Campaña',info.campaign],['Zona',info.zone],['Dispositivos',info.devices],['Periodo',`${p.from} – ${p.to}`],['Agrupación',info.group]]){const d=el('div');d.append(el('dt',k),el('dd',v));list.append(d);}
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
// HU35: lecturas descartadas (guardadas como no válidas). Se enseñan aparte y no cuentan en nada del informe.
const discardReason=r=>r.motivo_descarte==='fuera_de_rango'
  ?`Fuera de rango (admitido: ${Number(r.valor_minimo)}–${Number(r.valor_maximo)} ${r.unidad})`
  :`Anómala (se aleja más de ${Number(r.salto_maximo)} ${r.unidad} de la media de las 5 anteriores)`;
function discardedBlock(rows){
  if(!rows?.length)return null;
  const box=el('section');box.className='report-block report-discarded';
  box.append(el('h3',`Mediciones descartadas · ${rows.length}`),el('p','Se guardan como no válidas para dejar constancia. No salen en el mapa, en este informe, en el historial ni en el CSV.'));
  const wrap=el('div');wrap.className='table-scroll';box.append(wrap);
  table(wrap,['Fecha','AERIQ','Valor','Motivo','Origen'],rows.map(r=>({cells:[AeriqDomain.when(r.capturada_en,{seconds:true}),r.dispositivo,`${r.valor} ${r.unidad}`,discardReason(r),originOf(r)]})));
  return box;
}
function renderReport(rows,group,info,discarded=[]){
  const host=document.querySelector('#report-results');host.replaceChildren();
  if(group==='auto'||!group)group=AeriqDomain.autoGroup(rows);
  if(info)info.group={hour:'Horas',day:'Días',week:'Semanas'}[group];
  lastReport={info,count:rows.length,generated:new Date().toISOString().slice(0,16).replace('T',' '),sets:[]};
  const sets=new Map();for(const r of rows){const key=`${originOf(r)} · ${r.unidad}`;if(!sets.has(key))sets.set(key,[]);sets.get(key).push(r);}
  for(const [name,set] of sets){
    const thermal=set[0].unidad==='°C',simulated=Boolean(Number(set[0].simulado));const stats=AeriqDomain.statistics(set,group);
    const peaks=thermal?[]:AeriqDomain.peaks(set),verdict=thermal?null:AeriqDomain.verdict(set);
    const model={name,set,thermal,simulated,stats,limits:null,canvas:null,peaks,verdict,group};lastReport.sets.push(model);
    const block=el('section');block.className='report-block';host.append(block);
    const head=el('h3',name+' ');const badge=el('span',simulated?'Dato simulado':'Dato real');badge.className='report-badge'+(simulated?' is-simulated':'');head.append(badge);block.append(head);
    if(simulated){const warn=el('p','Datos simulados: no representan mediciones reales.');warn.className='report-simulated';block.append(warn);}
    block.append(reportKpis(stats,set[0].unidad,thermal));
    const alert=peakAlert(peaks);if(alert)block.append(alert);
    const limits=thermal?null:AeriqDomain.sharedThresholds(set);
    const canvas=el('canvas');canvas.width=900;canvas.height=240;canvas.style.width='100%';canvas.setAttribute('role','img');
    canvas.setAttribute('aria-label','Evolución de valores medios'+(limits?` con umbral medio ${limits.medium} y alto ${limits.high}`:'')+'; datos disponibles en la tabla siguiente');
    block.append(canvas);drawChart(canvas,stats.series,limits,{unit:set[0].unidad,group,peaks});model.limits=limits;model.canvas=canvas;
    if(!thermal)block.append(el('p',(limits?'Línea discontinua: umbrales de la campaña. Puntos rojos: periodos con alguna lectura igual o superior al umbral medio.':'Las lecturas tienen umbrales distintos según su campaña; consulta el nivel en la tabla de superaciones.')+(peaks.length?' Triángulos: picos (lectura más alta de cada tramo sobre el umbral).':'')));
    if(verdict)block.append(verdictBlock(verdict));
    const summary=el('div');block.append(summary);table(summary,['Periodo','Mínimo','Máximo','Media'],stats.series.map(s=>({cells:[AeriqDomain.when(s.date,{hour:group==='hour'}),s.min,s.max,s.mean.toFixed(2)]})));
    block.append(el('p',thermal?'Temperatura del módulo en °C. No se aplican los umbrales de ozono.':`${stats.exceedances.length} lecturas alcanzan o superan el umbral medio de su campaña.`));
    if(stats.exceedances.length){const exceed=el('div');exceed.className='table-scroll report-exceedances';block.append(exceed);table(exceed,['Fecha del suceso','Origen','Valor','Unidad','Umbral medio','Umbral alto','Nivel'],stats.exceedances.map(r=>({cells:[AeriqDomain.when(r.capturada_en,{seconds:true}),originOf(r),r.valor,r.unidad,r.umbral_medio,r.umbral_alto,levelOf(r)]})));}
    const detail=el('div');detail.className='table-scroll';block.append(detail);
    table(detail,['Fecha','AERIQ','Zona','Valor','Unidad','Nivel','Origen','Coordenadas'],set.map(r=>({cells:[AeriqDomain.when(r.capturada_en,{seconds:true}),r.dispositivo,r.zona,r.valor,r.unidad,thermal?'—':levelOf(r),originOf(r),r.latitud===null?'Sin ubicación':Number(r.latitud).toFixed(5)+', '+Number(r.longitud).toFixed(5)]})));
  }
  if(info)host.prepend(reportHeader(lastReport));
  lastReport.discarded=discarded||[];
  const lost=discardedBlock(discarded);if(lost)host.append(lost);
}
// Textos legibles de los filtros elegidos, para la cabecera del informe.
function reportInfo(f,filters,group){
  const text=name=>{const s=f.elements.namedItem(name);return s.selectedOptions?.[0]?.textContent||'';};
  const devices=[...f.querySelectorAll('#report-device input:checked')].map(i=>i.closest('label').textContent.trim());
  return {type:text('type'),campaign:filters.campaign?text('campaign'):'Todas las autorizadas',zone:filters.zone||'Todas las zonas',devices:devices.length?devices.join(', '):'Todos',
    period:`${filters.from||'desde el inicio'} — ${filters.to||'hasta hoy'}`,group:{auto:'Automática',hour:'Horas',week:'Semanas'}[group]||'Días',from:filters.from||'',to:filters.to||'',campaignSet:Boolean(filters.campaign),zoneSet:Boolean(filters.zone)};
}
// Periodo legible: el del filtro o, si falta un extremo, el de los propios datos.
function reportPeriod(report){
  const all=report.sets.flatMap(s=>s.set.map(r=>String(r.capturada_en).slice(0,10))).sort();
  const from=report.info.from||all[0]||'',to=report.info.to||all.at(-1)||'';
  return {from:AeriqDomain.when(from,{hour:false}),to:AeriqDomain.when(to,{hour:false}),fromData:!report.info.from,toData:!report.info.to};
}
function reportDescription(report){
  const i=report.info,p=reportPeriod(report),simulated=report.sets.some(s=>s.simulated),real=report.sets.some(s=>!s.simulated);
  const what=i.type.split(' · ')[0].replace(/\s*\(.*\)$/,'').toLowerCase();
  return `Este informe recoge las mediciones válidas de ${what} registradas por los dispositivos AERIQ `+
    `${i.campaignSet?`de la campaña «${i.campaign}»`:'de todas las campañas autorizadas'}`+
    `${i.zoneSet?` en la zona «${i.zone}»`:' en todas las zonas'}`+
    `${i.devices!=='Todos'?` (dispositivos: ${i.devices})`:''}, entre el ${p.from} y el ${p.to}, agrupadas por ${i.group.toLowerCase()}. `+
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
td.alto{color:#b3261e;font-weight:700}td.moderado{color:#b26a00;font-weight:700}td.buena{color:#0e6b5e;font-weight:700}td.sim{color:#7a4b00;font-weight:700}
.peaks{border:1px solid #e0b450;background:#fff7e0;color:#7a4b00;border-radius:6px;padding:8px 12px;margin:0 0 10px}.peaks.high{border-color:#e6a5a1;background:#fdeceb;color:#8c1d18}.peaks ul{margin:4px 0 0;padding-left:18px}
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
  body.append(h('div',{class:'cover'},logo,h('div',{class:'org'},me.organization||'',h('br'),`Generado el ${AeriqDomain.when(report.generated)}`)));
  body.append(h('h1',{},title),h('p',{class:'subtitle'},`${i.type} · ${i.campaignSet?i.campaign:'Todas las campañas'} · del ${p.from} al ${p.to}`));
  body.append(h('p',{class:'desc'},reportDescription(report)));
  const filters=h('dl',{class:'filters'});
  for(const [k,v] of [['Magnitud',i.type],['Campaña',i.campaign],['Zona',i.zone],['Dispositivos',i.devices],['Desde',p.from+(p.fromData?' · según los datos':'')],['Hasta',p.to+(p.toData?' · según los datos':'')],['Agrupación',i.group],['Lecturas válidas',report.count],['Series',report.sets.map(s=>s.name).join(' / ')]])filters.append(h('div',{},h('dt',{},k),h('dd',{},v)));
  body.append(filters);
  report.sets.forEach((s,n)=>{
    const st=s.stats,unit=s.set[0].unidad,sec=h('section',{class:n?'':'first'});body.append(sec);
    sec.append(h('h2',{},s.name,h('span',{class:'badge'+(s.simulated?' sim':'')},s.simulated?'Dato simulado':'Dato real')));
    if(s.simulated)sec.append(h('p',{class:'simwarn'},'Datos simulados: no representan mediciones reales y no deben usarse como evidencia ambiental.'));
    const kpis=h('div',{class:'kpis'});
    for(const [k,v,alert] of [['Lecturas',st.count],['Mínimo',`${st.min} ${unit}`],['Máximo',`${st.max} ${unit}`],['Media',`${st.mean.toLocaleString('es-ES',{maximumFractionDigits:2})} ${unit}`],['Superaciones',s.thermal?'—':st.exceedances.length,!s.thermal&&st.exceedances.length]])kpis.append(h('div',{class:'kpi'+(alert?' alert':'')},h('b',{},v),h('small',{},k)));
    sec.append(kpis);
    let src=null;try{src=s.canvas?.toDataURL('image/png');}catch{src=null;}
    if(src&&src.startsWith('data:image'))sec.append(h('figure',{},h('img',{src,alt:'Evolución de valores medios'}),h('figcaption',{},s.thermal?'Media por periodo (°C). No se aplican umbrales de ozono.':s.limits?`Media por periodo. Líneas discontinuas: umbral medio (${s.limits.medium} ${unit}) y alto (${s.limits.high} ${unit}). Puntos rojos: periodos con alguna lectura igual o superior al umbral medio.${s.peaks?.length?' Triángulos: picos.':''}`:'Media por periodo. Las lecturas tienen umbrales distintos según su campaña; ver tabla de superaciones.')));
    if(s.peaks?.length){const box=h('div',{class:'peaks'+(s.peaks.some(p=>p.nivel==='alto')?' high':'')},h('b',{},`${s.peaks.length} ${s.peaks.length===1?'pico de ozono':'picos de ozono'} en el periodo`));
      const ul=h('ul');for(const p of s.peaks)ul.append(h('li',{},`${AeriqDomain.when(p.capturada_en)} · ${p.valor} ${p.unidad} · nivel ${p.nivel}${p.dispositivo?' · '+p.dispositivo:''}`));box.append(ul);sec.append(box);}
    if(s.verdict){sec.append(h('h3',{},'Veredicto por franja horaria'),tableOf(['Franja','Horas','Lecturas',`Media (${unit})`,'Calidad'],s.verdict.franjas.map(f=>[f.nombre,f.horas,f.lecturas,f.media===null?'—':f.media.toFixed(1),f.calidad||'Sin datos']),(c,col)=>col===4?({buena:'buena',regular:'moderado',mala:'alto'})[c]:undefined),h('p',{class:'desc'},s.verdict.texto));}
    sec.append(h('h3',{},'Evolución temporal'),tableOf(['Periodo',`Mínimo (${unit})`,`Máximo (${unit})`,`Media (${unit})`],st.series.map(x=>[AeriqDomain.when(x.date,{hour:s.group==='hour'}),x.min,x.max,x.mean.toFixed(2)])));
    if(!s.thermal){
      sec.append(h('h3',{},'Superaciones de umbral'));
      if(st.exceedances.length)sec.append(tableOf(['Fecha','AERIQ','Origen','Valor','Umbral medio','Umbral alto','Nivel'],st.exceedances.map(r=>[AeriqDomain.when(r.capturada_en,{seconds:true}),r.dispositivo,s.simulated?'Simulado':'Real',`${r.valor} ${r.unidad}`,r.umbral_medio,r.umbral_alto,levelOf(r)]),(c,col)=>col===6?String(c):col===2&&c==='Simulado'?'sim':undefined));
      else sec.append(h('p',{class:'note'},'Ninguna lectura alcanza el umbral medio de su campaña en este periodo.'));
    }
    sec.append(h('h3',{},'Anexo: lecturas'));
    const rows=s.set.slice(0,ANNEX_MAX).map(r=>[AeriqDomain.when(r.capturada_en,{seconds:true}),r.dispositivo,`${r.campana} / ${r.zona}`,`${r.valor} ${r.unidad}`,s.thermal?'—':levelOf(r),s.simulated?'Simulado':'Real']);
    sec.append(tableOf(['Fecha','AERIQ','Campaña / zona','Valor','Nivel','Origen'],rows,(c,col)=>col===4&&['alto','moderado'].includes(c)?c:col===5&&c==='Simulado'?'sim':undefined));
    if(s.set.length>ANNEX_MAX)sec.append(h('p',{class:'note'},`Se muestran las primeras ${ANNEX_MAX} de ${s.set.length} lecturas. El listado completo está en «Descargar CSV».`));
  });
  if(report.discarded?.length){const sec=h('section',{});body.append(sec);sec.append(h('h2',{},`Mediciones descartadas · ${report.discarded.length}`),h('p',{class:'note'},'Guardadas como no válidas: no se usan en ningún cálculo de este informe.'),
    tableOf(['Fecha','AERIQ','Valor','Motivo','Origen'],report.discarded.map(r=>[AeriqDomain.when(r.capturada_en,{seconds:true}),r.dispositivo,`${r.valor} ${r.unidad}`,discardReason(r),originOf(r)])));}
  body.append(h('p',{class:'footer'},'AERIQ · Código Norte. Ozono en ppb y temperatura en °C. Solo se incluyen lecturas válidas autorizadas para esta cuenta; los datos simulados se identifican como tales.'));
  const win=frame.contentWindow,cleanup=()=>setTimeout(()=>frame.remove(),1000);
  const ready=logo.complete?Promise.resolve():new Promise(r=>{logo.onload=logo.onerror=r;setTimeout(r,2000);});
  const old=document.title;
  ready.then(()=>{document.title=file;win.addEventListener('afterprint',()=>{document.title=old;cleanup();},{once:true});win.focus();win.print();setTimeout(()=>{document.title=old;},5000);});
}
document.querySelector('#report-form').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button');b.disabled=true;currentRows=[];document.querySelector('#export-report').disabled=true;document.querySelector('#print-report').disabled=true;document.querySelector('#report-results').replaceChildren();
  try{const filters=payload(f);filters.devices=[...f.querySelectorAll('#report-device input:checked')].map(i=>i.value).join(',');delete filters.device;const group=filters.group;delete filters.group;lastFilters=filters;const result=await Aeriq.portal('readings',undefined,filters);currentRows=result.readings;renderReport(currentRows,group,reportInfo(f,filters,group),result.discarded||[]);document.querySelector('#report-summary').textContent=`${currentRows.length} lecturas válidas autorizadas.`;document.querySelector('#export-report').disabled=!currentRows.length;document.querySelector('#print-report').disabled=!currentRows.length;}
  catch(error){aeriqStatus(document.querySelector('#report-summary'),error.message,'error');}finally{b.disabled=false;}
};
// Con un informe ya generado, cambiar el periodo o la agrupación lo vuelve a calcular (la gráfica se actualiza sola).
document.querySelector('#report-form').addEventListener('change',e=>{if(lastReport&&['from','to','group'].includes(e.target.name)&&e.currentTarget.checkValidity())e.currentTarget.requestSubmit();});
// PDF sin librerías: printReport() maqueta el informe aparte y el navegador ofrece «Guardar como PDF».
document.querySelector('#print-report').onclick=printReport;
// El CSV se vuelve a pedir con export=1: el servidor comprueba el permiso datos.exportar.
document.querySelector('#export-report').onclick=async e=>{const b=e.currentTarget;b.disabled=true;let rows;try{rows=(await Aeriq.portal('readings',undefined,{...lastFilters,export:1})).readings;}catch(error){document.querySelector('#report-summary').textContent=error.message;return;}finally{b.disabled=false;}const url=URL.createObjectURL(new Blob([AeriqDomain.csv(rows)],{type:'text/csv;charset=utf-8'}));const a=el('a');a.href=url;a.download='AERIQ-mediciones.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
// Solicitudes: bandeja. Lista a la izquierda (filtro por estado y buscador) y ficha completa a la derecha.
const REQ_PILL={pendiente:'pill-soon',en_revision:'pill-info',respondida:'pill-on',cerrada:'pill-off'};
const REQ_HELP={pendiente:'Ha llegado y nadie la ha mirado todavía.',en_revision:'La estáis estudiando (presupuesto, disponibilidad…).',respondida:'Ya habéis contestado y esperáis respuesta del cliente.',cerrada:'Terminada: aceptada y convertida en campaña, o descartada.'};
// keep: solicitudes cambiadas aquí; siguen en la lista hasta que cambias de filtro o de búsqueda.
const req={list:[],states:[],filter:'pendiente',q:'',keep:new Set(),sel:null,note:''};
// Fechas del servidor en UTC → hora local, sin la etiqueta UTC.
const when=(s,hour)=>AeriqDomain.when(s,{hour:Boolean(hour)});
const link=(href,text)=>{const a=el('a',text);a.href=href;return a;};
const reqPill=r=>{const p=el('span',r.estado_nombre);p.className='pill '+(REQ_PILL[r.estado]||'pill-off');return p;};
async function commercial(){
  const {requests,states=[]}=await Aeriq.portal('requests');req.list=requests;req.states=states;
  renderRequests();
}
function reqMatches(r){
  const q=req.q.trim().toLowerCase().replace(/^#/,'');
  return (!req.filter||r.estado===req.filter||req.keep.has(r.id))&&(!q||String(r.id)===q||[r.entidad,r.email,r.contacto,r.municipio].some(v=>String(v||'').toLowerCase().includes(q)));
}
function renderRequests(){
  const pending=document.getElementById('sum-requests');if(pending)pending.textContent=req.list.filter(r=>r.estado==='pendiente').length;
  const tabs=document.querySelector('#requests-filter'),host=document.querySelector('#requests-list');if(!host)return;
  tabs.replaceChildren();
  for(const s of [...req.states,{codigo:'',nombre:'Todas'}]){
    const n=s.codigo?req.list.filter(r=>r.estado===s.codigo).length:req.list.length;
    const b=el('button');b.type='button';b.append(s.nombre+' ',el('b',String(n)));b.className=req.filter===s.codigo?'selected':'';b.setAttribute('aria-pressed',String(req.filter===s.codigo));
    if(REQ_HELP[s.codigo])b.title=REQ_HELP[s.codigo];b.onclick=()=>{req.filter=s.codigo;req.keep.clear();renderRequests();};tabs.append(b);
  }
  const shown=req.list.filter(reqMatches);
  if(!shown.some(r=>r.id===req.sel))req.sel=shown[0]?.id??null;
  host.replaceChildren();
  for(const r of shown){
    const row=el('button');row.type='button';row.className='inbox-row'+(r.id===req.sel?' selected':'');row.setAttribute('aria-current',String(r.id===req.sel));
    const bottom=el('span');bottom.className='inbox-row-top';bottom.append(el('small',when(r.recibida_en)),reqPill(r));
    row.title=r.entidad;row.append(el('strong',`#${r.id} · ${r.entidad}`),bottom);row.onclick=()=>{req.sel=r.id;req.note='';renderRequests();};host.append(row);
  }
  if(!shown.length)host.append(el('p',req.list.length?'No hay solicitudes con este filtro.':'Todavía no hay solicitudes.'));
  renderRequestDetail(req.list.find(r=>r.id===req.sel));
}
// Las solicitudes solo avanzan (T38); una cerrada se recupera con «Reabrir». El servidor avisa por correo (T37).
async function setRequestState(r,code,reopen=false){
  try{const res=await Aeriq.portal(reopen?'request-reopen':'request-status',reopen?{id:r.id}:{id:r.id,status:code});
    const name=res.estado_nombre||req.states.find(s=>s.codigo===code)?.nombre||code;
    Object.assign(r,{estado:res.estado||code,estado_nombre:name});
    if(res.changed!==false)(r.historial||=[]).push({estado_nombre:name,reapertura:reopen,cambiado_en:new Date().toISOString().slice(0,19).replace('T',' '),usuario:[me.name,me.last].filter(Boolean).join(' ')});
    req.keep.add(r.id);req.note=(res.message||`Guardado ✓ · ahora está en «${name}»`);req.noteTone=res.mailed===false?'warn':'ok';}
  catch(e){req.note='';aeriqStatus(portalFeedback,e.message,'error');}
  renderRequests();
}
function renderRequestDetail(r){
  const host=document.querySelector('#request-detail');host.replaceChildren();
  if(!r){host.append(el('p','Elige una solicitud de la lista.'));return;}
  // Cabecera: quién, cuándo y responder.
  const head=el('header');head.className='request-detail-head';
  const who=el('div');const h=el('h3',r.entidad);h.append(' ',reqPill(r));who.append(h,el('small',`Solicitud #${r.id} · recibida el ${when(r.recibida_en,true)}`));
  head.append(who,replyMenu(r));host.append(head);
  // Pasos del estado: el actual marcado; solo se puede pulsar un paso posterior (no se retrocede). Botón principal = siguiente paso.
  const steps=el('ol');steps.className='request-steps';steps.setAttribute('aria-label','Estado de la solicitud');
  const at=req.states.findIndex(s=>s.codigo===r.estado);
  req.states.forEach((s,i)=>{const li=el('li');li.className=i<at?'done':i===at?'current':'';
    const closed=Boolean(Number(req.states[at]?.final));
    const b=el('button',s.nombre);b.type='button';b.title=i<at?'Una solicitud no puede volver a un estado anterior.':(REQ_HELP[s.codigo]||'');b.disabled=i<=at||closed;if(i===at)b.setAttribute('aria-current','step');b.onclick=()=>setRequestState(r,s.codigo);li.append(b);steps.append(li);});
  host.append(steps);
  const actions=el('div');actions.className='request-actions';
  const next=req.states[at+1];
  if(next){const b=el('button',next.codigo==='cerrada'?'Cerrar solicitud':`Pasar a ${next.nombre}`);b.type='button';b.className='btn btn-primary';b.onclick=()=>{b.disabled=true;setRequestState(r,next.codigo);};actions.append(b);}
  if(next&&next.codigo!=='cerrada'&&r.estado!=='cerrada'){const c=el('button','Cerrar solicitud');c.type='button';c.className='btn btn-outline';c.onclick=()=>{c.disabled=true;setRequestState(r,'cerrada');};actions.append(c);}
  if(!next||Number(req.states[at]?.final)){const o=el('button','Reabrir');o.type='button';o.className='btn btn-outline';o.title='Vuelve a «En revisión» y queda en el historial';o.onclick=()=>{o.disabled=true;setRequestState(r,'en_revision',true);};actions.append(o);}
  const note=el('small');note.className='request-saved';note.setAttribute('role','status');aeriqStatus(note,req.note,req.noteTone||'ok');actions.append(note);
  host.append(actions,el('p',REQ_HELP[r.estado]||''));host.lastChild.className='note';
  // Ficha: etiqueta a la izquierda, dato a la derecha.
  const info=el('dl');info.className='request-sheet';
  const add=(label,value)=>{if(value==null||value==='')return;const dt=el('dt',label),dd=el('dd');dd.append(value);info.append(dt,dd);};
  const place=[r.municipio,r.provincia&&`(${r.provincia})`].filter(Boolean).join(' ');
  add('Servicio',r.servicio_nombre||r.servicio);add('Organización',r.tipo_organizacion_nombre||'Sin indicar');
  add('Lugar',place);add('Zona',r.zona);add('Contacto',r.contacto);
  add('Correo',link('mailto:'+r.email,r.email));if(r.telefono)add('Teléfono',link('tel:'+r.telefono,r.telefono));
  if(r.objetivos?.length)add('Objetivos',r.objetivos.join(', '));
  if(r.duracion||r.superficie!=null||r.inicio_deseado)add('Estudio',`${r.superficie!=null?r.superficie+' m²':'Superficie por estimar'} · ${r.duracion||'Duración sin indicar'} · Inicio ${r.inicio_deseado?r.inicio_deseado.slice(0,7):'sin indicar'}`);
  const needs=el('p',r.necesidades);needs.className='request-needs';add('Necesita',needs);
  if(r.historial?.length){const log=el('ol');log.className='request-history';log.setAttribute('aria-label',`Historial de la solicitud ${r.id}`);r.historial.forEach((x,i)=>log.append(el('li',[x.reapertura?`Reabierta → ${x.estado_nombre}`:x.estado_nombre,when(x.cambiado_en,true),x.usuario||(i?'':'Formulario público')].filter(Boolean).join(' · '))));add('Historial',log);}
  host.append(info);
}
// Responder: el enlace mailto no hace nada si el ordenador no tiene app de correo, así que se ofrecen Gmail, Outlook y copiar.
function replyMenu(r){
  const subject=`AERIQ · Tu solicitud #${r.id}`,body=`Hola ${r.contacto},\n\n`,enc=encodeURIComponent;
  const menu=el('details');menu.className='reply-menu';const sum=el('summary','Responder');sum.className='btn btn-outline';menu.append(sum);
  const list=el('div');list.className='reply-list';
  const go=(text,href)=>{const a=link(href,text);a.target='_blank';a.rel='noopener';a.onclick=()=>{menu.open=false;};list.append(a);};
  go('Abrir en Gmail',`https://mail.google.com/mail/?view=cm&fs=1&to=${enc(r.email)}&su=${enc(subject)}&body=${enc(body)}`);
  go('Abrir en Outlook',`https://outlook.office.com/mail/deeplink/compose?to=${enc(r.email)}&subject=${enc(subject)}&body=${enc(body)}`);
  const app=link(`mailto:${r.email}?subject=${enc(subject)}&body=${enc(body)}`,'App de correo del ordenador');app.onclick=()=>{menu.open=false;};list.append(app);
  const copy=el('button','Copiar correo');copy.type='button';copy.onclick=async()=>{try{await navigator.clipboard.writeText(r.email);copy.textContent='Copiado ✓';}catch{copy.textContent=r.email;}setTimeout(()=>{copy.textContent='Copiar correo';menu.open=false;},1200);};
  list.append(copy);menu.append(list);return menu;
}
document.querySelector('#requests-search')?.addEventListener('input',e=>{req.q=e.target.value;req.keep.clear();renderRequests();});
document.addEventListener('click',e=>{for(const m of document.querySelectorAll('.reply-menu[open]'))if(!m.contains(e.target))m.open=false;});
(async()=>{try{
  const {user}=await AeriqAuth.session();if(!user){location.href='login.html';return;}if(user.mustChangePassword){location.href='cambiar-clave.html';return;}
  me=user;document.querySelector('#portal-title').textContent=user.organization||'Código Norte';
  // Operador: solo «Mi campaña», de consulta.
  const operator=!can(user,'informes.ver')&&can(user,'mediciones.registrar');
  if(!can(user,'informes.ver')&&!can(user,'campanas.gestionar')&&!operator)throw new Error('Esta cuenta no tiene acceso al panel.');
  document.querySelector('#organization-panel').hidden=false;
  for(const n of document.querySelectorAll('[data-perm]:not([data-view])'))n.hidden=!allowed(n);
  // El operador ve «Mi campaña» (de consulta) y «Avisos»: el resto de enlaces se ocultan por sus permisos.
  if(operator){await myCampaign();}
  else {await refresh();if(can(user,'solicitudes.gestionar'))await commercial();
  // La flota (sección «Dispositivos AERIQ») se carga aparte; al guardar un AERIQ se refrescan los contadores.
  if(typeof initFleet==='function'&&can(user,'equipo.gestionar')){void initFleet();addEventListener('aeriq:fleet-changed',()=>void refresh());}}
  aeriqStatus(portalFeedback,'Sesión autorizada · '+(user.roleName||user.role));
  showWorkspace(location.hash.slice(1));
}catch(e){aeriqStatus(portalFeedback,e.message,'error');}})();

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
