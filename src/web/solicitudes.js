'use strict';
// Formulario comercial (solicitar.html). Los catálogos (servicios, tipos de organización,
// objetivos, provincias y duraciones) llegan de public.php?action=services: la base de datos
// decide qué se ofrece y qué servicios piden datos de estudio (servicios.mide_superficie).
(()=>{
  const form=document.querySelector('#service-form');
  if(!form)return;
  const $=selector=>document.querySelector(selector);
  const summary=$('#form-summary'),feedback=$('#service-feedback'),submit=$('#submit-request');
  const DRAFT_KEY='aeriq-solicitud-borrador';
  const FIELDS=['interest','orgType','entity','contact','email','phone','municipality','province','zone','objectives','surface','duration','startMonth','needs','consent'];
  const GROUPS=new Set(['interest','objectives']);
  const MESSAGES={
    interest:'Elige qué te interesa.',
    orgType:'Elige el tipo de organización.',
    entity:'Escribe el nombre de la organización.',
    contact:'Escribe el nombre de la persona de contacto.',
    emailMissing:'Escribe tu correo electrónico.',
    email:'Escribe un correo válido, por ejemplo nombre@ayuntamiento.es.',
    phone:'El teléfono debe tener 9 cifras y empezar por 6, 7, 8 o 9.',
    municipality:'Escribe el municipio.',
    province:'Elige una provincia.',
    zone:'Describe la zona o las instalaciones que quieres medir.',
    objectives:'Marca al menos un objetivo.',
    surface:'La superficie debe ser un número mayor que 0. Si no la sabes, déjala vacía.',
    duration:'Elige la duración del estudio.',
    startMonth:'Elige un mes a partir del actual.',
    needs:'Cuéntanos qué necesitas.',
    consent:'Necesitamos tu autorización para gestionar la solicitud.'
  };
  const EMAIL_TYPOS={'gmial.com':'gmail.com','gmai.com':'gmail.com','gamil.com':'gmail.com','gmail.co':'gmail.com','gmail.es':'gmail.com','gmal.com':'gmail.com','hotmial.com':'hotmail.com','hotmal.com':'hotmail.com','hotmail.co':'hotmail.com','hotmai.com':'hotmail.com','outlok.com':'outlook.com','outlook.co':'outlook.com','outloo.com':'outlook.com','yahooo.com':'yahoo.com','yaho.com':'yahoo.com','yaho.es':'yahoo.es'};
  const touched=new Set();
  let studyServices=new Set(),catalogs=null;

  const store={
    get(){try{return JSON.parse(sessionStorage.getItem(DRAFT_KEY)||'null');}catch{return null;}},
    set(value){try{sessionStorage.setItem(DRAFT_KEY,JSON.stringify(value));}catch{/* Sin almacenamiento: no se guarda el borrador. */}},
    clear(){try{sessionStorage.removeItem(DRAFT_KEY);}catch{/* Nada que borrar. */}}
  };
  const el=(tag,props={},children=[])=>{const node=Object.assign(document.createElement(tag),props);node.append(...children);return node;};
  const thisMonth=()=>{const d=new Date();return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}`;};
  const chosenInterest=()=>form.querySelector('[name=interest]:checked')?.value||'';
  const isStudy=()=>studyServices.has(chosenInterest());

  // ------------------------------------------------------------ Preselección desde la URL
  // ?interes=kits|estudio|asesoramiento&org=administracion|empresa|otra. Los enlaces antiguos
  // (?service=administracion|empresa|estudio) siguen funcionando.
  const params=new URLSearchParams(location.search);
  let wantedInterest=params.get('interes'),wantedOrg=params.get('org');
  const legacy=params.get('service');
  if(!wantedInterest&&legacy==='estudio')wantedInterest='estudio';
  if(!wantedInterest&&['administracion','empresa'].includes(legacy)){wantedInterest='kits';wantedOrg=wantedOrg||legacy;}

  // ------------------------------------------------------------ Catálogos
  function fillSelect(select,items,value,label){
    const placeholder=select.options[0];
    select.replaceChildren(placeholder,...items.map(item=>new Option(label(item),value(item))));
  }
  function renderCatalogs(data){
    catalogs=data;
    studyServices=new Set(data.services.filter(s=>s.mide_superficie).map(s=>s.codigo));
    const interest=$('#interest-options');interest.removeAttribute('data-loading');
    interest.replaceChildren(...data.services.map(s=>el('label',{className:'choice-card'},[
      el('input',{type:'radio',name:'interest',value:s.codigo,required:true}),
      el('span',{className:'choice-title',textContent:s.nombre}),
      el('span',{className:'choice-desc',textContent:s.descripcion||''})
    ])));
    $('#objective-options').replaceChildren(...data.objectives.map(o=>el('label',{},[
      el('input',{type:'checkbox',name:'objectives',value:o.codigo}),document.createTextNode(' '+o.nombre)
    ])));
    fillSelect($('#org-type'),data.orgTypes,o=>o.codigo,o=>o.nombre);
    fillSelect($('#province'),data.provinces,p=>p,p=>p);
    fillSelect($('#duration'),data.durations,d=>d,d=>d);
    form.elements.startMonth.min=thisMonth();
  }

  // ------------------------------------------------------------ Validación
  function check(name){
    if(name==='interest')return chosenInterest()?'':MESSAGES.interest;
    if(name==='objectives')return form.querySelector('[name=objectives]:checked')?'':MESSAGES.objectives;
    const field=form.elements.namedItem(name);
    if(!field||field.disabled)return '';
    if(field.type==='checkbox')return field.checked?'':MESSAGES[name];
    const value=field.value.trim();
    if(name==='phone')return value===''||/^[6789]\d{8}$/.test(value)?'':MESSAGES.phone;
    if(name==='surface')return field.validity.badInput||(value!==''&&!(Number(value)>0))?MESSAGES.surface:'';
    if(name==='startMonth')return value===''||(/^\d{4}-\d{2}$/.test(value)&&value>=thisMonth())?'':MESSAGES.startMonth;
    if(name==='email'){
      if(value==='')return MESSAGES.emailMissing;
      return field.validity.typeMismatch||!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value)?MESSAGES.email:'';
    }
    if(field.required&&value==='')return MESSAGES[name];
    return '';
  }
  function show(name,message){
    const error=document.getElementById(`${name}-error`);
    if(error)error.textContent=message;
    const target=GROUPS.has(name)?document.getElementById(`${name}-group`):form.elements.namedItem(name);
    if(!target)return;
    if(message)target.setAttribute('aria-invalid','true');else target.removeAttribute('aria-invalid');
  }
  function validate(name){const message=check(name);show(name,message);return message;}
  function focusField(name){
    const target=GROUPS.has(name)?form.querySelector(`[name=${name}]`):form.elements.namedItem(name);
    target?.focus();
  }
  function fieldName(target){return target?.name&&FIELDS.includes(target.name)?target.name:'';}

  form.addEventListener('focusout',e=>{
    const name=fieldName(e.target);if(!name)return;
    // En los grupos se valida al salir del grupo entero, no al pasar de una opción a otra.
    if(GROUPS.has(name)&&e.relatedTarget?.name===name)return;
    touched.add(name);validate(name);
    if(name==='email')suggestEmail();
  });
  form.addEventListener('input',e=>{
    const name=fieldName(e.target);
    if(name&&touched.has(name))validate(name);
    if(name==='needs')updateCount();
    if(name==='email')$('#email-hint').hidden=true;
    saveDraft();
  });
  form.addEventListener('change',e=>{
    const name=fieldName(e.target);if(!name)return;
    if(GROUPS.has(name)||e.target.type==='checkbox'||e.target.tagName==='SELECT'){touched.add(name);validate(name);}
    if(name==='interest')syncStudy();
    saveDraft();
  });

  // ------------------------------------------------------------ Teléfono: solo cifras
  const phone=form.elements.phone,phoneHelp=$('#phone-help'),phoneHelpText=phoneHelp.textContent;
  let phoneTimer;
  phoneHelp.setAttribute('aria-live','polite');
  function phoneWarning(){
    phoneHelp.textContent='Solo se admiten números.';phoneHelp.classList.add('is-warning');
    clearTimeout(phoneTimer);
    phoneTimer=setTimeout(()=>{phoneHelp.textContent=phoneHelpText;phoneHelp.classList.remove('is-warning');},3000);
  }
  phone.addEventListener('beforeinput',e=>{
    if(e.inputType==='insertText'&&e.data&&/\D/.test(e.data)){e.preventDefault();phoneWarning();}
  });
  phone.addEventListener('paste',e=>{
    const text=e.clipboardData?.getData('text');if(text==null)return;
    e.preventDefault();
    let digits=text.replace(/\D/g,'');
    if(digits.length===11&&digits.startsWith('34'))digits=digits.slice(2);
    if(/[^\d\s+().-]/.test(text))phoneWarning();
    const start=phone.selectionStart??phone.value.length,end=phone.selectionEnd??phone.value.length;
    const room=9-(phone.value.length-(end-start));
    phone.setRangeText(digits.slice(0,Math.max(0,room)),start,end,'end');
    phone.dispatchEvent(new Event('input',{bubbles:true}));
  });
  // Red de seguridad para autocompletado, arrastrar y soltar o teclados que no lanzan beforeinput.
  phone.addEventListener('input',()=>{
    const clean=phone.value.replace(/\D/g,'').slice(0,9);
    if(clean!==phone.value){if(/[^\d\s]/.test(phone.value))phoneWarning();phone.value=clean;}
  });

  // ------------------------------------------------------------ Correo: aviso de errores de tecleo
  function suggestEmail(){
    const hint=$('#email-hint'),value=form.elements.email.value.trim().toLowerCase();
    const [user,domain]=value.split('@');const fix=domain&&EMAIL_TYPOS[domain];
    if(!fix||check('email')){hint.hidden=true;return;}
    const button=el('button',{type:'button',className:'link-button',textContent:`${user}@${fix}`});
    button.onclick=()=>{form.elements.email.value=`${user}@${fix}`;hint.hidden=true;validate('email');saveDraft();form.elements.email.focus();};
    hint.replaceChildren(document.createTextNode('¿Quisiste decir '),button,document.createTextNode('?'));
    hint.hidden=false;
  }

  // ------------------------------------------------------------ Campos de estudio y contador
  function syncStudy(){
    const study=isStudy(),box=form.querySelector('[data-study]');
    box.hidden=!study;
    for(const field of box.querySelectorAll('input,select'))field.disabled=!study;
    if(!study)for(const name of ['surface','duration','startMonth']){show(name,'');touched.delete(name);}
  }
  function updateCount(){
    const needs=form.elements.needs,count=$('#needs-count'),max=Number(needs.maxLength)||6000;
    count.textContent=`${needs.value.length} / ${max}`;
    count.classList.toggle('is-near-limit',needs.value.length>max*.9);
  }

  // ------------------------------------------------------------ Borrador (sessionStorage)
  let draftTimer;
  function snapshot(){
    const data={};
    for(const name of ['orgType','entity','contact','email','phone','municipality','province','zone','surface','duration','startMonth','needs'])data[name]=form.elements.namedItem(name).value;
    data.interest=chosenInterest();
    data.objectives=[...form.querySelectorAll('[name=objectives]:checked')].map(i=>i.value);
    return data;
  }
  function saveDraft(){clearTimeout(draftTimer);draftTimer=setTimeout(()=>store.set(snapshot()),300);}
  function setInterest(code){const radio=[...form.querySelectorAll('[name=interest]')].find(r=>r.value===code);if(radio)radio.checked=true;}
  function setSelect(select,value){if(value&&[...select.options].some(o=>o.value===value))select.value=value;}
  function restore(){
    const draft=store.get();
    if(draft&&typeof draft==='object'){
      for(const name of ['entity','contact','email','phone','municipality','zone','surface','startMonth','needs'])if(typeof draft[name]==='string')form.elements.namedItem(name).value=draft[name];
      for(const name of ['orgType','province','duration'])setSelect(form.elements.namedItem(name),draft[name]);
      setInterest(draft.interest);
      if(Array.isArray(draft.objectives))for(const box of form.querySelectorAll('[name=objectives]'))box.checked=draft.objectives.includes(box.value);
    }
    // Un enlace con ?interes= o ?org= expresa lo que la persona acaba de elegir: tiene prioridad.
    if(wantedInterest)setInterest(wantedInterest);
    if(wantedOrg)setSelect(form.elements.orgType,wantedOrg);
  }

  // ------------------------------------------------------------ Envío
  function collect(){
    const value=name=>form.elements.namedItem(name).value.trim();
    const data={interest:chosenInterest(),orgType:value('orgType'),entity:value('entity'),contact:value('contact'),
      email:value('email'),phone:value('phone'),municipality:value('municipality'),province:value('province'),zone:value('zone'),
      objectives:[...form.querySelectorAll('[name=objectives]:checked')].map(i=>i.value),
      needs:value('needs'),consent:form.elements.consent.checked,website:form.elements.website.value};
    if(isStudy())Object.assign(data,{surface:value('surface'),duration:value('duration'),startMonth:value('startMonth')});
    return data;
  }
  function showSummary(count){
    summary.textContent=count===1?'Revisa el campo marcado antes de enviar.':`Revisa los ${count} campos marcados antes de enviar.`;
    summary.hidden=false;
  }
  function setBusy(busy){
    submit.disabled=busy;submit.setAttribute('aria-busy',String(busy));
    submit.textContent=busy?'Enviando…':'Enviar solicitud';
  }
  function showSuccess(result,data){
    const service=catalogs?.services.find(s=>s.codigo===data.interest)?.nombre||data.interest;
    const org=catalogs?.orgTypes.find(o=>o.codigo===data.orgType)?.nombre||'';
    const goals=(catalogs?.objectives||[]).filter(o=>data.objectives.includes(o.codigo)).map(o=>o.nombre).join(', ');
    $('#success-title').textContent=result.id?`Solicitud n.º ${result.id} recibida`:'Solicitud recibida';
    $('#success-text').textContent=`Gracias, ${data.contact}. Código Norte la revisará y te escribirá a ${data.email}. Guarda el número de referencia por si necesitas consultarla.`;
    const rows=[['Me interesa',service],['Organización',`${data.entity}${org?` · ${org}`:''}`],['Ubicación',`${data.municipality} (${data.province})`],['Objetivos',goals]];
    if(data.duration)rows.push(['Duración del estudio',data.duration]);
    $('#success-summary').replaceChildren(...rows.flatMap(([term,detail])=>[el('dt',{textContent:term}),el('dd',{textContent:detail})]));
    form.hidden=true;
    const box=$('#request-success');box.hidden=false;box.focus();
  }
  form.addEventListener('submit',async e=>{
    e.preventDefault();
    if(submit.disabled)return;
    feedback.textContent='';
    const invalid=FIELDS.filter(name=>{touched.add(name);return validate(name);});
    if(invalid.length){showSummary(invalid.length);focusField(invalid[0]);return;}
    summary.hidden=true;setBusy(true);
    const data=collect();
    try{
      const result=await Aeriq.submit(data);
      clearTimeout(draftTimer);store.clear();showSuccess(result,data);
    }catch(error){
      if(error.field&&FIELDS.includes(error.field)){show(error.field,error.message);showSummary(1);focusField(error.field);}
      else feedback.textContent=error.message||'No se ha podido enviar la solicitud. Inténtalo de nuevo.';
    }finally{setBusy(false);}
  });
  $('#new-request').addEventListener('click',()=>{
    form.reset();touched.clear();
    for(const name of FIELDS)show(name,'');
    summary.hidden=true;feedback.textContent='';$('#email-hint').hidden=true;
    $('#request-success').hidden=true;form.hidden=false;
    syncStudy();updateCount();form.querySelector('[name=interest]')?.focus();
  });

  // ------------------------------------------------------------ Arranque
  updateCount();
  Aeriq.services().then(data=>{
    if(!data?.services?.length)throw new Error('Sin servicios');
    renderCatalogs(data);restore();syncStudy();updateCount();
  }).catch(()=>{
    $('#interest-options').replaceChildren(el('p',{className:'field-help',textContent:'No se han podido cargar las opciones.'}));
    summary.textContent='No se han podido cargar las opciones del formulario. Revisa tu conexión y recarga la página.';
    summary.hidden=false;submit.disabled=true;
  });
})();
