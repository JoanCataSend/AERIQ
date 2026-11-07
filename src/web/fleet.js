'use strict';
async function initFleet(){
  const form=document.querySelector('#device-edit-form'),modal=document.querySelector('#device-modal'),body=document.querySelector('.devices-table tbody');
  if(!form||!body)return;
  let devices=[],workers=[],campaigns=[],admin=false,current=null,filter='all';
  const create=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text??'—';return n;};
  const name=document.querySelector('#device-new-name');name.name='name';
  const address=create('input');address.name='address';address.required=true;address.pattern='([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}';address.placeholder='AA:BB:CC:DD:EE:FF';
  const worker=create('select');worker.name='worker';const campaign=create('select');campaign.name='campaign';
  const active=create('input');active.name='active';active.type='checkbox';const simulated=create('input');simulated.name='simulated';simulated.type='checkbox';
  for(const [text,field] of [['Dirección BLE',address],['Trabajador',worker],['Campaña',campaign],['Alta administrativa activa',active],['Datos simulados',simulated]]){const label=create('label',text);label.append(field);form.insertBefore(label,form.querySelector('.button-pair'));}
  function selectOptions(select,rows,label){select.replaceChildren(new Option('Sin asignar',''));for(const r of rows)select.add(new Option(label(r),r.id));}
  function edit(d){current=d;form.reset();name.value=d?.nombre??'';address.value=d?.direccion_ble??'';worker.value=d?.trabajador_id??'';campaign.value=d?.campana_id??'';active.checked=d?Boolean(Number(d.activo)):true;simulated.checked=d?Boolean(Number(d.simulado)):false;modal.querySelector('h2').textContent=d?'Editar Orb':'Registrar Orb';modal.showModal();}
  function render(){
    body.replaceChildren();let shown=0;const term=(document.querySelector('#device-search').value||'').toLowerCase();
    for(const d of devices){
      const recent=Number(d.activo)&&d.ultima_recepcion&&Date.now()-new Date(d.ultima_recepcion.replace(' ','T')+'Z').getTime()<900000;
      if((filter==='active'&&!recent)||(filter==='inactive'&&recent)||!(d.nombre+' '+d.id+' '+d.direccion_ble).toLowerCase().includes(term))continue;
      shown++;const tr=create('tr');
      for(const text of [d.nombre+' · Orb #'+d.id,!Number(d.activo)?'Baja administrativa':recent?'Lecturas recientes':'Sin lecturas recientes',d.trabajador||'Sin trabajador',d.campana||'Sin campaña',d.ultima_medicion?d.ultima_medicion+' UTC':'Sin mediciones',Number(d.simulado)?'Simulado':'Real'])tr.append(create('td',text));
      const actions=create('td');if(admin){const b=create('button','Editar / asignar');b.className='text-link';b.onclick=()=>edit(d);actions.append(b);}else actions.textContent='Consulta';tr.append(actions);body.append(tr);
    }
    if(!shown){const tr=create('tr'),td=create('td','No hay dispositivos que coincidan.');td.colSpan=7;tr.append(td);body.append(tr);}
    const summary=document.querySelector('.table-bottom');summary.textContent=`${shown} de ${devices.length} Orb · Actividad basada en recepción durante los últimos 15 minutos.`;
    const stats=document.querySelectorAll('.stat strong');if(stats.length>=3){stats[0].textContent=devices.length;stats[1].textContent=devices.filter(d=>Number(d.activo)).length;stats[2].textContent=devices.filter(d=>!Number(d.activo)).length;}
  }
  async function refresh(){const data=await Aeriq.portal('dashboard');devices=data.devices;workers=data.workers;campaigns=data.campaigns;selectOptions(worker,workers.filter(w=>w.rol==='trabajador'&&(Number(w.activo)||Number(w.cambiar_clave))),w=>w.nombre+' '+w.apellidos);selectOptions(campaign,campaigns,c=>c.nombre);render();}
  document.querySelector('#device-search').oninput=render;
  for(const button of document.querySelectorAll('[data-device-filter]'))button.onclick=()=>{filter=button.dataset.deviceFilter;for(const b of document.querySelectorAll('[data-device-filter]'))b.classList.toggle('selected',b===button);render();};
  document.querySelector('#device-modal-close').onclick=()=>modal.close();
  document.querySelector('#add-device').onclick=()=>edit(null);
  form.onsubmit=async e=>{e.preventDefault();const button=form.querySelector('[type="submit"]');button.disabled=true;try{await Aeriq.portal('device',{...(current?{id:current.id}:{}),name:name.value,address:address.value,worker:worker.value,campaign:campaign.value,active:active.checked,simulated:simulated.checked});modal.close();await refresh();toast('Orb guardado en tu organización.');}catch(error){toast(error.message);}finally{button.disabled=false;}};
  try{const {user}=await AeriqAuth.session();if(!user){location.href='login.html';return;}if(user.mustChangePassword){location.href='cambiar-clave.html';return;}admin=user.role==='admin';document.querySelector('#add-device').hidden=!admin;await refresh();}
  catch(error){body.replaceChildren();const tr=create('tr'),td=create('td',error.message);td.colSpan=7;tr.append(td);body.append(tr);}
}
