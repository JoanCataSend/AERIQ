'use strict';
/* Flota de AERIQ dentro del panel (portal.html, sección «Dispositivos AERIQ»). */
async function initFleet(){
  const root=document.querySelector('#fleet');if(!root)return;
  const $f=sel=>root.querySelector(sel);
  const form=$f('#device-edit-form'),modal=$f('#device-modal'),body=$f('.devices-table tbody');
  if(!form||!body)return;
  const say=msg=>{if(typeof toast==='function')toast(msg);else{const n=document.querySelector('#portal-feedback');if(n)n.textContent=msg;}};
  let devices=[],workers=[],campaigns=[],admin=false,current=null,filter='all';
  const create=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text??'—';return n;};
  const name=$f('#device-new-name');name.name='name';
  const address=create('input');address.name='address';address.required=true;address.pattern='([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}';address.placeholder='AA:BB:CC:DD:EE:FF';
  const worker=create('select');worker.name='worker';
  for(const [text,field] of [['Dirección BLE',address],['Operador',worker]]){const label=create('label',text);label.append(field);form.insertBefore(label,form.querySelector('.button-pair'));}
  // «En servicio»: solo al editar. Apagado = dado de baja (deja de aceptar lecturas; las que tiene se conservan).
  const active=create('input');active.name='active';active.type='checkbox';active.setAttribute('role','switch');
  const service=create('label');service.className='switch-row';const knob=create('span');knob.className='switch';knob.setAttribute('aria-hidden','true');
  const txt=create('span');txt.append(create('strong','En servicio'),create('small','Si lo apagas, el AERIQ queda dado de baja: deja de aceptar lecturas y conserva las que ya tiene.'));
  service.append(active,knob,txt);form.insertBefore(service,form.querySelector('.button-pair'));
  const when=v=>AeriqDomain.when(v,{seconds:true});
  function selectOptions(select,rows,label){select.replaceChildren(new Option('Sin asignar',''));for(const r of rows)select.add(new Option(label(r),r.id));}
  function edit(d){current=d;form.reset();name.value=d?.nombre??'';address.value=d?.direccion_ble??'';worker.value=d?.trabajador_id??'';active.checked=d?Boolean(Number(d.activo)):true;service.hidden=!d;modal.querySelector('h2').textContent=d?'Editar AERIQ':'Registrar AERIQ';modal.showModal();}
  function render(){
    body.replaceChildren();let shown=0;const term=($f('#device-search').value||'').toLowerCase();
    for(const d of devices){
      const recent=Number(d.activo)&&d.ultima_recepcion&&Date.now()-new Date(d.ultima_recepcion.replace(' ','T')+'Z').getTime()<900000;
      if((filter==='active'&&!recent)||(filter==='inactive'&&recent)||!(d.nombre+' '+d.id+' '+d.direccion_ble).toLowerCase().includes(term))continue;
      shown++;const tr=create('tr');
      const who=create('td');who.append(create('strong',d.nombre),create('small',d.direccion_ble||''));who.className='device-name';tr.append(who);
      for(const text of [!Number(d.activo)?'Dado de baja':recent?'Lecturas recientes':'Sin lecturas recientes',d.trabajador||'Sin operador',d.ultima_medicion?when(d.ultima_medicion):'Sin mediciones',AeriqDomain.battery(d.bateria_pct,d.bateria_en)])tr.append(create('td',text));
      const actions=create('td');if(admin){const b=create('button','Editar / asignar');b.className='text-link';b.onclick=()=>edit(d);actions.append(b);}else actions.textContent='Consulta';tr.append(actions);body.append(tr);
    }
    if(!shown){const tr=create('tr'),td=create('td','No hay dispositivos que coincidan.');td.colSpan=6;tr.append(td);body.append(tr);}
    const summary=$f('.table-bottom');summary.textContent=`${shown} de ${devices.length} AERIQ · «Lecturas recientes» = ha enviado datos en los últimos 15 minutos.`;
    const stats=root.querySelectorAll('.stat strong');if(stats.length>=3){stats[0].textContent=devices.length;stats[1].textContent=devices.filter(d=>Number(d.activo)).length;stats[2].textContent=devices.filter(d=>!Number(d.activo)).length;}
  }
  async function refresh(){const data=await Aeriq.portal('dashboard');devices=data.devices;workers=data.workers;campaigns=data.campaigns;selectOptions(worker,workers.filter(w=>Number(w.puede_medir)&&(Number(w.activo)||Number(w.cambiar_clave))),w=>w.nombre+' '+w.apellidos);render();}
  $f('#device-search').oninput=render;
  for(const button of root.querySelectorAll('[data-device-filter]'))button.onclick=()=>{filter=button.dataset.deviceFilter;for(const b of root.querySelectorAll('[data-device-filter]'))b.classList.toggle('selected',b===button);render();};
  $f('#device-modal-close').onclick=()=>modal.close();
  $f('#add-device').onclick=()=>edit(null);
  form.onsubmit=async e=>{e.preventDefault();const button=form.querySelector('[type="submit"]');button.disabled=true;try{await Aeriq.portal('device',{...(current?{id:current.id}:{}),name:name.value,address:address.value,worker:worker.value,active:current?active.checked:true,simulated:current?Boolean(Number(current.simulado)):false});modal.close();await refresh();window.dispatchEvent(new Event('aeriq:fleet-changed'));say('AERIQ guardado en tu campaña.');}catch(error){say(error.message);}finally{button.disabled=false;}};
  try{const {user}=await AeriqAuth.session();if(!user){location.href='login.html';return;}if(user.mustChangePassword){location.href='cambiar-clave.html';return;}admin=(user.permissions||[]).includes('equipo.gestionar');$f('#add-device').hidden=!admin;await refresh();}
  catch(error){body.replaceChildren();const tr=create('tr'),td=create('td',error.message);td.colSpan=6;tr.append(td);body.append(tr);}
}
