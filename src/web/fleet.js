/*
 * Fichero: fleet.js
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */

'use strict';
/* Flota de AERIQ dentro del panel (portal.html, sección «Dispositivos AERIQ»). */
/**
 * Diseño lógico: initFleet() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
async function initFleet(){
  const root=document.querySelector('#fleet');if(!root)return;
  /**
   * Diseño lógico: (sel) → $f() → resultado de la operación; según doc/frontend_business_logic_design.md.
   * Descripción: Describe la operación realizada y su resultado.
   */
  const $f=sel=>root.querySelector(sel);
  const form=$f('#device-edit-form'),modal=$f('#device-modal'),host=$f('#fleet-table');
  if(!form||!host)return;
  /**
   * Diseño lógico: msg: Text, tone: Text --> say() --> Text
   * Descripción: Describe la operación realizada y su resultado.
   */
  const say=(msg,tone)=>{if(typeof toast==='function')toast(msg,tone);else aeriqStatus(document.querySelector('#portal-feedback'),msg,tone);};
  let devices=[],workers=[],campaigns=[],admin=false,current=null,view=null;
  /**
   * Diseño lógico: tag: Text, text: Text --> create() --> Text
   * Descripción: Procesa create y devuelve el resultado correspondiente.
   */
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
  /**
   * Diseño lógico: (v,{seconds:true}) → when() → resultado de la operación; según doc/frontend_business_logic_design.md.
   * Descripción: Formatea una fecha como hora local relativa al día actual.
   */
  const when=v=>AeriqDomain.when(v,{seconds:true});
  /**
   * Diseño lógico: select: Text, rows: Text, label: Text --> selectOptions() --> Text
   * Descripción: Procesa select options y devuelve el resultado correspondiente.
   */
  function selectOptions(select,rows,label){select.replaceChildren(new Option('Sin asignar',''));for(const r of rows)select.add(new Option(label(r),r.id));}
  /**
   * Diseño lógico: d: Text --> edit() --> Text
   * Descripción: Procesa edit y devuelve el resultado correspondiente.
   */
  function edit(d){current=d;form.reset();name.value=d?.nombre??'';address.value=d?.direccion_ble??'';worker.value=d?.trabajador_id??'';active.checked=d?Boolean(Number(d.activo)):true;service.hidden=!d;modal.querySelector('h2').textContent=d?'Editar AERIQ':'Registrar AERIQ';modal.showModal();}
  // Tabla con paneles, filtros y orden por columna (device-table.js).
  /**
   * Diseño lógico: mountTable() --> Text
   * Descripción: Procesa mount table y devuelve el resultado correspondiente.
   */
  const mountTable=()=>{view=AeriqDeviceTable.mount(host,{columns:['nombre','actividad','operador','ultima','bateria'],
    action:d=>{if(!admin)return document.createTextNode('Consulta');const b=create('button','Editar / asignar');b.type='button';b.className='text-link';b.onclick=()=>edit(d);return b;},
    empty:'Todavía no hay AERIQ registrados en tu campaña.'});};
  /**
   * Diseño lógico: refresh() --> Text
   * Descripción: Procesa refresh y devuelve el resultado correspondiente.
   */
  async function refresh(){const data=await Aeriq.consultar_panel('resumen');devices=data.devices;workers=data.workers;campaigns=data.campaigns;selectOptions(worker,workers.filter(w=>Number(w.puede_medir)&&(Number(w.activo)||Number(w.cambiar_clave))),w=>w.nombre+' '+w.apellidos);if(!view)mountTable();view.set(devices);}
  $f('#device-modal-close').onclick=()=>modal.close();
  $f('#add-device').onclick=()=>edit(null);
  form.onsubmit=async e=>{e.preventDefault();const button=form.querySelector('[type="submit"]');button.disabled=true;try{await Aeriq.portal('device',{...(current?{id:current.id}:{}),name:name.value,address:address.value,worker:worker.value,active:current?active.checked:true,simulated:current?Boolean(Number(current.simulado)):false});modal.close();await refresh();window.dispatchEvent(new Event('aeriq:fleet-changed'));say('AERIQ guardado en tu campaña.','ok');}catch(error){say(error.message,'error');}finally{button.disabled=false;}};
  try{const {user}=await AeriqAuth.session();if(!user){location.href='login.html';return;}if(user.mustChangePassword){location.href='cambiar-clave.html';return;}admin=(user.permissions||[]).includes('equipo.gestionar');$f('#add-device').hidden=!admin;await refresh();}
  catch(error){host.replaceChildren(create('p',error.message));}
}
