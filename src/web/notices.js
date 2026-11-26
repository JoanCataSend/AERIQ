'use strict';
/*
 * Avisos entre personas (HU36), sección «Avisos» del panel (portal.html). Como una solicitud: asunto, mensajes,
 * estado e historial. Quién puede escribir a quién lo decide el servidor (server/Avisos.php): el admin principal con
 * admins y operadores; el admin de campaña con sus operadores y el admin principal; el operador con su admin y el
 * admin principal. Los avisos con algo nuevo llevan un punto y el enlace de la navegación muestra cuántos hay.
 */
(()=>{
  const panel=document.querySelector('#notices-panel');if(!panel)return;
  const $=s=>document.querySelector(s);
  const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined&&text!==null)n.textContent=String(text);if(cls)n.className=cls;return n;};
  const when=s=>AeriqDomain.when(s);
  const PILL={abierto:'pill-soon',respondido:'pill-info',cerrado:'pill-off'};
  const state={list:[],contacts:[],filter:'open',selected:null,unread:0,loaded:false};
  const status=(msg,tone)=>aeriqStatus($('#notices-status'),msg,tone);

  function badge(n){
    state.unread=n;const b=$('#notices-badge');if(!b)return;
    b.hidden=!n;b.textContent=n;b.setAttribute('aria-label',`${n} ${n===1?'aviso nuevo':'avisos nuevos'}`);
  }
  async function load(keep){
    try{
      const d=await Aeriq.portal('notices');state.list=d.notices;state.contacts=d.contacts;state.loaded=true;badge(d.unread);
      $('#notice-new').disabled=!d.contacts.length;$('#notice-new').title=d.contacts.length?'':'No hay nadie a quien puedas escribir todavía.';
      renderList();
      if(keep&&state.selected)await open(state.selected,false);else if(!state.selected)renderEmpty();
    }catch(e){status(e.message,'error');}
  }
  function renderEmpty(){
    const host=$('#notice-detail');host.replaceChildren();
    host.append(node('p',state.list.length?'Elige un aviso de la lista.':'Todavía no tienes avisos. Pulsa «Nuevo aviso» para escribir a alguien de tu equipo.'));
  }
  function renderList(){
    const pending=state.list.filter(n=>n.estado!=='cerrado'),shown=state.filter==='open'?pending:state.list;
    const filter=$('#notices-filter');filter.replaceChildren();
    for(const [code,label,count] of [['open','Abiertos',pending.length],['all','Todos',state.list.length]]){
      const b=node('button');b.type='button';b.append(label+' ',node('b',count));b.classList.toggle('selected',state.filter===code);b.setAttribute('aria-pressed',String(state.filter===code));
      b.onclick=()=>{state.filter=code;renderList();};filter.append(b);
    }
    const host=$('#notices-list');host.replaceChildren();
    if(!shown.length){host.append(node('p',state.filter==='open'?'No tienes avisos abiertos.':'No tienes avisos.','note'));return;}
    for(const n of shown){
      const row=node('button',null,'inbox-row notice-row'+(n.nuevo?' is-new':'')+(state.selected===n.id?' selected':''));row.type='button';
      const top=node('span',null,'inbox-row-top');top.append(node('strong',n.con_nombre),node('small',when(n.actualizado_en)));
      const pill=node('span',n.estado_nombre,'pill '+(PILL[n.estado]||'pill-off'));
      row.append(top,node('span',n.asunto,'notice-subject'),node('small',`${n.con_rol}${n.ultimo?' · '+n.ultimo:''}`,'notice-preview'),pill);
      row.setAttribute('aria-label',`${n.nuevo?'Nuevo. ':''}${n.asunto}, con ${n.con_nombre}, ${n.estado_nombre}`);
      row.onclick=()=>open(n.id,true);host.append(row);
    }
  }
  async function open(id,refreshList){
    state.selected=id;renderList();
    const host=$('#notice-detail');
    try{
      const d=await Aeriq.portal('notice',undefined,{id}),n=d.notice;host.replaceChildren();
      const head=node('header',null,'request-detail-head');const who=node('div');const h=node('h3',n.asunto);h.append(' ',node('span',n.estado_nombre,'pill '+(PILL[n.estado]||'pill-off')));
      who.append(h,node('small',`Con ${n.con_nombre} · ${n.con_rol} · desde el ${when(n.creado_en)}`));head.append(who);host.append(head);
      const thread=node('ol',null,'notice-thread');thread.setAttribute('aria-label','Mensajes del aviso');
      for(const m of d.messages){const li=node('li',null,'notice-message'+(m.mio?' is-mine':''));li.append(node('strong',m.mio?'Tú':m.autor),node('p',m.texto),node('small',when(m.creado_en)));thread.append(li);}
      host.append(thread);
      const form=node('form',null,'notice-reply');const label=node('label','Responder');const area=node('textarea');area.name='message';area.rows=3;area.maxLength=4000;area.required=true;
      area.placeholder=n.cerrado?'Escribe para volver a abrir el aviso…':'Escribe tu respuesta…';label.append(area);
      const actions=node('div',null,'button-row');const send=node('button','Enviar respuesta','btn btn-primary');send.type='submit';actions.append(send);
      if(!n.cerrado){const close=node('button','Cerrar aviso','btn btn-outline');close.type='button';close.onclick=async()=>{close.disabled=true;try{const r=await Aeriq.portal('notice-close',{id});status(r.message,'ok');await load(true);}catch(e){status(e.message,'error');close.disabled=false;}};actions.append(close);}
      form.append(label,actions);host.append(form);
      form.onsubmit=async e=>{e.preventDefault();if(!area.value.trim()){status('Escribe el mensaje antes de enviarlo.','warn');return;}send.disabled=true;
        try{const r=await Aeriq.portal('notice-reply',{id,message:area.value});status(r.message,'ok');await load(true);}catch(err){status(err.message,'error');send.disabled=false;}};
      thread.lastElementChild?.scrollIntoView({block:'nearest'});
      // Abrirlo lo marca como visto: se actualizan el punto y el contador.
      if(refreshList){const item=state.list.find(x=>x.id===id);if(item?.nuevo){item.nuevo=false;badge(Math.max(0,state.unread-1));renderList();}}
    }catch(e){status(e.message,'error');}
  }
  // Nuevo aviso: el destinatario se elige entre las personas a las que la cuenta puede escribir.
  const modal=$('#notice-modal'),form=$('#notice-form');
  $('#notice-new').onclick=()=>{
    form.reset();const to=form.elements.namedItem('to');
    to.replaceChildren(new Option('Elige a quién escribir',''),...state.contacts.map(c=>new Option(`${c.nombre} · ${c.rol}${c.campana?' · '+c.campana:''}`,c.id)));
    modal.showModal();
  };
  $('#notice-modal-close').onclick=()=>modal.close();
  form.onsubmit=async e=>{e.preventDefault();if(!form.reportValidity())return;const b=form.querySelector('[type=submit]');b.disabled=true;
    try{const d=Object.fromEntries(new FormData(form));const r=await Aeriq.portal('notice-create',{to:Number(d.to),subject:d.subject,message:d.message});
      modal.close();status(r.message,'ok');state.selected=r.id;state.filter='open';await load(true);}
    catch(err){status(err.message,'error');}finally{b.disabled=false;}};

  // Arranque: solo cuentas de una organización. El contador se refresca cada minuto mientras el panel está abierto.
  AeriqAuth.session().then(({user})=>{
    if(!user?.organizationId||user.mustChangePassword)return;
    load(false);
    setInterval(async()=>{if(document.hidden)return;try{const {unread}=await Aeriq.portal('notice-unread');if(unread!==state.unread){badge(unread);if(!panel.hidden)load(true);}}catch{}},60000);
  }).catch(()=>{});
})();
