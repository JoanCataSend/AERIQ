'use strict';
/*
 * Tabla de dispositivos AERIQ con paneles, filtros y orden por columna (T43, T44, T45).
 * La usan «Dispositivos AERIQ» (admin de campaña, fleet.js) y «AERIQ por campaña» (admin principal, portal.js).
 * Todo se calcula en el navegador con los datos de portal.php?action=dashboard: no cambia la API.
 */
const AeriqDeviceTable=(()=>{
  const DAY=86400000,RECENT=15*60000,IDLE=30*DAY;
  const time=v=>v?Date.parse(String(v).replace(' ','T')+'Z'):NaN;
  /** Actividad de un AERIQ: dado de baja, recibiendo ahora (15 min), sin uso en más de 30 días o sin lecturas recientes. */
  function activity(d,now=Date.now()){
    if(!Number(d.activo))return 'baja';
    const received=time(d.ultima_recepcion),measured=time(d.ultima_medicion);
    if(Number.isFinite(received)&&now-received<RECENT)return 'recibiendo';
    if(!Number.isFinite(measured)||now-measured>IDLE)return 'sin_uso';
    return 'sin_datos';
  }
  const ACTIVITY={recibiendo:'Recibiendo ahora',sin_datos:'Sin lecturas recientes',sin_uso:'Sin uso en más de 30 días',baja:'Dado de baja'};
  const RANK={recibiendo:0,sin_datos:1,sin_uso:2,baja:3};
  const COLUMNS={
    nombre:{label:'AERIQ',key:d=>d.nombre.toLocaleLowerCase('es')},
    campana:{label:'Campaña',key:d=>(d.campana||'').toLocaleLowerCase('es')||null},
    actividad:{label:'Actividad',key:d=>RANK[activity(d)]},
    operador:{label:'Operador',key:d=>(d.trabajador||'').toLocaleLowerCase('es')||null},
    ultima:{label:'Última medición',key:d=>Number.isFinite(time(d.ultima_medicion))?time(d.ultima_medicion):null},
    bateria:{label:'Batería',key:d=>d.bateria_pct===null||d.bateria_pct===undefined||d.bateria_pct===''?null:Number(d.bateria_pct)},
  };
  const create=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined&&text!==null)n.textContent=text;if(cls)n.className=cls;return n;};
  /**
   * mount(host, {columns, campaigns:bool, action:(d)=>Node|null, empty})
   * Devuelve {set(devices)}: llamar con la lista de dispositivos cada vez que cambie.
   */
  function mount(host,opts){
    const columns=opts.columns,state={q:'',estado:'',actividad:'',operador:'',campana:'',sort:'nombre',dir:1};
    let devices=[];
    host.replaceChildren();host.classList.add('device-table');
    // Paneles (T45): cuentan sobre todos los AERIQ y, al pulsarlos, filtran la tabla.
    const panels=create('div',null,'stats-grid device-panels');
    const PANELS=[['En servicio','estado','alta'],['Dados de baja','estado','baja'],['Recibiendo ahora','actividad','recibiendo'],['Sin uso en más de 30 días','actividad','sin_uso']];
    const panelButtons=PANELS.map(([label,field,value])=>{const b=create('button',null,'stat stat-button');b.type='button';b.append(create('strong','—'),create('small',label));
      b.onclick=()=>{const on=state[field]===value;state.estado='';state.actividad='';if(!on)state[field]=value;sync();render();};panels.append(b);return {b,field,value};});
    // Filtros (T43): buscador, estado, actividad, operador y (en la vista del admin principal) campaña.
    const controls=create('div',null,'device-filters');
    const search=create('input');search.type='search';search.placeholder='Buscar por nombre o dirección BLE';search.setAttribute('aria-label','Buscar dispositivo');
    const select=(label,name,options)=>{const wrap=create('label',null,'device-filter');wrap.append(create('span',label));const s=create('select');s.name=name;
      for(const [v,t] of options)s.add(new Option(t,v));wrap.append(s);s.onchange=()=>{state[name]=s.value;sync();render();};controls.append(wrap);return s;};
    const searchWrap=create('label',null,'search-control device-search');searchWrap.append(search);controls.append(searchWrap);
    const estado=select('Estado','estado',[['','Todos'],['alta','En servicio'],['baja','Dados de baja']]);
    const actividad=select('Actividad','actividad',[['','Todas'],['recibiendo',ACTIVITY.recibiendo],['sin_datos',ACTIVITY.sin_datos],['sin_uso',ACTIVITY.sin_uso]]);
    const operador=select('Operador','operador',[['','Todos']]);
    const campana=opts.campaigns?select('Campaña','campana',[['','Todas']]):null;
    const clear=create('button','Quitar filtros','text-link');clear.type='button';clear.onclick=()=>{Object.assign(state,{q:'',estado:'',actividad:'',operador:'',campana:''});search.value='';sync();render();};controls.append(clear);
    search.oninput=()=>{state.q=search.value.trim().toLowerCase();render();};
    // Tabla con cabeceras que ordenan (T44): una pulsación ascendente, otra descendente, flecha en la activa.
    const scroll=create('div',null,'table-scroll'),table=create('table',null,'devices-table'),thead=create('thead'),tbody=create('tbody'),bottom=create('div',null,'table-bottom');
    const headRow=create('tr');
    const headers=columns.map(c=>{const th=create('th');const b=create('button',null,'sort-button');b.type='button';b.append(create('span',COLUMNS[c].label),create('span','','sort-arrow'));
      b.onclick=()=>{if(state.sort===c)state.dir*=-1;else{state.sort=c;state.dir=1;}render();};th.append(b);headRow.append(th);return {th,b,c};});
    if(opts.action)headRow.append(create('th','Acciones'));
    thead.append(headRow);table.append(thead,tbody);scroll.append(table);
    host.append(panels,controls,scroll,bottom);
    function sync(){
      estado.value=state.estado;actividad.value=state.actividad;operador.value=state.operador;if(campana)campana.value=state.campana;
      for(const p of panelButtons){const on=state[p.field]===p.value;p.b.classList.toggle('selected',on);p.b.setAttribute('aria-pressed',String(on));}
    }
    function matches(d){
      const act=activity(d);
      if(state.estado==='alta'&&!Number(d.activo))return false;
      if(state.estado==='baja'&&Number(d.activo))return false;
      if(state.actividad&&act!==state.actividad)return false;
      if(state.operador==='__none'&&d.trabajador_id)return false;
      if(state.operador&&state.operador!=='__none'&&String(d.trabajador_id)!==state.operador)return false;
      if(state.campana==='__none'&&d.campana_id)return false;
      if(state.campana&&state.campana!=='__none'&&String(d.campana_id)!==state.campana)return false;
      return !state.q||`${d.nombre} ${d.id} ${d.direccion_ble||''}`.toLowerCase().includes(state.q);
    }
    function cell(d,c){
      const td=create('td');
      if(c==='nombre'){td.className='device-name';td.append(create('strong',d.nombre),create('small',d.direccion_ble||''));}
      else if(c==='campana')td.textContent=d.campana||'Sin asignar';
      else if(c==='actividad'){const a=activity(d);td.append(create('span',ACTIVITY[a],'pill '+({recibiendo:'pill-on',sin_datos:'pill-info',sin_uso:'pill-soon',baja:'pill-off'})[a]));}
      else if(c==='operador')td.textContent=d.trabajador||'Sin operador';
      else if(c==='ultima')td.textContent=d.ultima_medicion?AeriqDomain.when(d.ultima_medicion):'Nunca ha medido';
      else if(c==='bateria')td.textContent=AeriqDomain.battery(d.bateria_pct,d.bateria_en);
      return td;
    }
    function render(){
      const key=COLUMNS[state.sort].key;
      // Los vacíos (sin operador, sin medición…) van siempre al final, en los dos sentidos.
      const shown=devices.filter(matches).sort((a,b)=>{const x=key(a),y=key(b);if(x===null&&y===null)return 0;if(x===null)return 1;if(y===null)return -1;
        return (x<y?-1:x>y?1:0)*state.dir||a.nombre.localeCompare(b.nombre,'es');});
      tbody.replaceChildren();
      for(const d of shown){const tr=create('tr');for(const c of columns)tr.append(cell(d,c));if(opts.action){const td=create('td');const n=opts.action(d);if(n)td.append(n);tr.append(td);}tbody.append(tr);}
      if(!shown.length){const tr=create('tr'),td=create('td',devices.length?'No hay dispositivos que coincidan con los filtros.':(opts.empty||'Todavía no hay dispositivos AERIQ.'));td.colSpan=columns.length+(opts.action?1:0);tr.append(td);tbody.append(tr);}
      for(const h of headers){const on=state.sort===h.c;h.th.setAttribute('aria-sort',on?(state.dir>0?'ascending':'descending'):'none');h.b.querySelector('.sort-arrow').textContent=on?(state.dir>0?'▲':'▼'):'';
        h.b.setAttribute('aria-label',`Ordenar por ${COLUMNS[h.c].label}`+(on?(state.dir>0?', ahora ascendente':', ahora descendente'):''));}
      const counts={alta:devices.filter(d=>Number(d.activo)).length,baja:devices.filter(d=>!Number(d.activo)).length,recibiendo:devices.filter(d=>activity(d)==='recibiendo').length,sin_uso:devices.filter(d=>activity(d)==='sin_uso').length};
      for(const p of panelButtons)p.b.querySelector('strong').textContent=counts[p.value];
      bottom.textContent=`${shown.length} de ${devices.length} AERIQ · «Recibiendo ahora» = ha enviado datos en los últimos 15 minutos.`;
      clear.hidden=!(state.q||state.estado||state.actividad||state.operador||state.campana);
    }
    function set(list){
      devices=list||[];
      const people=[...new Map(devices.filter(d=>d.trabajador_id).map(d=>[String(d.trabajador_id),d.trabajador])).entries()].sort((a,b)=>a[1].localeCompare(b[1],'es'));
      operador.replaceChildren(new Option('Todos',''),new Option('Sin operador','__none'),...people.map(([id,name])=>new Option(name,id)));
      if(campana){const camps=[...new Map(devices.filter(d=>d.campana_id).map(d=>[String(d.campana_id),d.campana])).entries()].sort((a,b)=>a[1].localeCompare(b[1],'es'));
        campana.replaceChildren(new Option('Todas',''),new Option('Sin asignar','__none'),...camps.map(([id,name])=>new Option(name,id)));}
      if(state.operador&&![...operador.options].some(o=>o.value===state.operador))state.operador='';
      if(campana&&state.campana&&![...campana.options].some(o=>o.value===state.campana))state.campana='';
      sync();render();
    }
    return {set,state};
  }
  return {mount,activity,ACTIVITY};
})();
window.AeriqDeviceTable=AeriqDeviceTable;
