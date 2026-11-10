'use strict';
function initAeriqMap(){
  const host=document.querySelector('#real-gandia-map');if(!host)return;
  const q=s=>document.querySelector(s),status=q('#map-loading'),list=q('#map-sensor-list');
  if(typeof L==='undefined'){status.hidden=false;status.textContent='No se ha podido cargar la cartografía. Comprueba tu conexión.';return;}
  const campus=[38.996651,-0.166009],map=L.map(host,{zoomControl:false}).setView(campus,16),pins=L.layerGroup().addTo(map),zones=L.layerGroup().addTo(map);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
  let rows=[],selected=null,requestId=0,controllerTimer,source='all',hasFit=false;
  const colors={bajo:'#219c72',moderado:'#eea22d',alto:'#e35b5d'};
  const level=r=>AeriqDomain.level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto));
  // Solo formato de presentación: la fecha sigue en UTC y las coordenadas no se alteran salvo el redondeo visible.
  const months=['ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic'];
  const utcLabel=v=>{const m=/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/.exec(String(v||''));return m?`${Number(m[3])} ${months[Number(m[2])-1]} ${m[1]}, ${m[4]}:${m[5]} UTC`:`${v} UTC`;};
  const coord=v=>Number.isFinite(Number(v))?Number(v).toFixed(5):String(v);
  function choose(r,pan=true){selected=r;const info=q('#map-info');info.hidden=false;info.replaceChildren();
    for(const [tag,text] of [['strong',r.zona],['p',`${r.valor} ${r.unidad} · ${level(r)}`],['p',utcLabel(r.capturada_en)],['p',`${coord(r.latitud)}, ${coord(r.longitud)}`],['p',r.simulado?'Datos simulados':'Medición real · no sustituye a estaciones oficiales']]){const n=document.createElement(tag);n.textContent=text;info.append(n);}
    if(pan)map.panTo([r.latitud,r.longitud]);q('#selected-id').textContent=r.id;
  }
  function drawZones(){
    zones.clearLayers();if(!q('#heat-toggle').checked||!rows.length)return;
    // Solo las celdas visibles con al menos tres posiciones próximas reciben color.
    const bounds=map.getBounds(),center=map.getCenter(),latStep=10/111320,lonStep=10/(111320*Math.cos(center.lat*Math.PI/180));
    const groups=new Map();for(const r of rows){const k=String(r.campaignId);if(!groups.has(k))groups.set(k,[]);groups.get(k).push(r);}
    let count=0,examined=0;
    for(const group of groups.values()){
      const radius=group[0].radio_m;
      const bucketLat=50/111320,bucketLon=50/(111320*Math.cos(center.lat*Math.PI/180)),buckets=new Map();
      for(const r of group){const key=`${Math.floor(r.latitud/bucketLat)}:${Math.floor(r.longitud/bucketLon)}`;if(!buckets.has(key))buckets.set(key,[]);buckets.get(key).push(r);}
      // Muestrear solo alrededor de mediciones, sin recorrer el mapa completo.
      const cells=new Map();for(const r of group){if(!bounds.pad(.1).contains([r.latitud,r.longitud]))continue;const a=Math.round(r.latitud/latStep),b=Math.round(r.longitud/lonStep);for(let i=-5;i<=5;i++)for(let j=-5;j<=5;j++)cells.set(`${a+i}:${b+j}`,[(a+i)*latStep,(b+j)*lonStep]);}
      for(const point of cells.values()){
        if(count>=2500||examined++>=5000)return;
        const x=Math.floor(point[0]/bucketLat),y=Math.floor(point[1]/bucketLon),near=[];
        for(let i=-1;i<=1;i++)for(let j=-1;j<=1;j++)near.push(...(buckets.get(`${x+i}:${y+j}`)||[]));
        const v=AeriqDomain.interpolate(point,near,radius);if(v===null)continue;
        const color=colors[AeriqDomain.level(v,group[0].umbral_medio,group[0].umbral_alto)];
        L.rectangle([point,[point[0]+latStep,point[1]+lonStep]],{stroke:false,fillColor:color,fillOpacity:.25,interactive:false}).addTo(zones);count++;
      }
    }
  }
  async function refresh(){
    const id=++requestId,previousId=selected?.id;status.hidden=false;status.textContent='Consultando mediciones autorizadas…';selected=null;q('#map-info').hidden=true;q('#selected-id').textContent='—';pins.clearLayers();zones.clearLayers();list.replaceChildren();
    try{
      const result=await Aeriq.map(q('#public-mode').value,source,Number(q('#public-period')?.value||24));if(id!==requestId)return;rows=result.measurements;
      for(const r of rows){
        const marker=L.circleMarker([r.latitud,r.longitud],{radius:6,color:'#fff',weight:2,fillColor:colors[level(r)],fillOpacity:1}).on('click',()=>choose(r));pins.addLayer(marker);
        const button=document.createElement('button');button.className='sensor-list-item';button.textContent=`${r.zona} · ${r.valor} ${r.unidad} · ${r.simulado?'Simulado':'Real'}`;button.onclick=()=>choose(r);list.append(button);
      }
      if(!q('#marker-toggle').checked)map.removeLayer(pins);else pins.addTo(map);
      status.textContent=rows.length?`${rows.length} mediciones autorizadas. Sin color = sin cobertura suficiente.${result.truncated?' Se muestran las 2.000 más recientes.':''}`:'No hay mediciones publicadas de este origen. Las áreas permanecen sin datos.';
      if(rows.length){if(!hasFit){map.fitBounds(L.latLngBounds(rows.map(r=>[r.latitud,r.longitud])),{maxZoom:18});hasFit=true;}drawZones();const previous=rows.find(r=>r.id===previousId);if(previous)choose(previous,false);}
    }catch(e){if(id===requestId){rows=[];status.textContent=e.message;}}
  }
  q('#public-mode').onchange=()=>{hasFit=false;refresh();};q('#public-period').onchange=refresh;
  for(const button of document.querySelectorAll('[data-source]'))button.onclick=()=>{source=button.dataset.source;hasFit=false;for(const b of document.querySelectorAll('[data-source]'))b.classList.toggle('selected',b===button);refresh();};q('#heat-toggle').onchange=drawZones;
  q('#marker-toggle').onchange=e=>{if(e.target.checked)pins.addTo(map);else map.removeLayer(pins);};
  q('#toggle-map-layers').onclick=()=>{q('#map-layer-options').hidden=!q('#map-layer-options').hidden;};
  q('#map-reset').onclick=()=>map.setView(campus,16);
  for(const id of ['#zoomin-map','#map-zoom-in'])q(id)?.addEventListener('click',()=>map.zoomIn());q('#zoomout-map').onclick=()=>map.zoomOut();
  q('#map-search').onkeydown=e=>{if(e.key!=='Enter')return;e.preventDefault();const term=e.target.value.toLowerCase();if(term.includes('playa'))map.setView([38.999,-.1589],16);else if(term.includes('grau')||term.includes('grao'))map.setView([38.9922,-.1656],15);else if(term.includes('upv')||term.includes('gand'))map.setView(campus,16);else toast('Búsqueda local: UPV, playa o Grau de Gandía.');};
  for(const button of document.querySelectorAll('[data-map-style]'))button.onclick=()=>{const dark=button.dataset.mapStyle==='dark';store.set('mapStyle',dark?'dark':'light');host.classList.toggle('map-theme-dark',dark);host.closest('.map-visual').classList.toggle('is-dark-map',dark);for(const b of document.querySelectorAll('[data-map-style]')){const on=b===button;b.setAttribute('aria-pressed',String(on));b.classList.toggle('active',on);}};
  q('#export-sensor').onclick=()=>{if(!selected){toast('Selecciona una medición pública.');return;}const r=selected;const csv=AeriqDomain.csv([{...r,dispositivo:'Medición pública #'+r.id,campana:r.campaignId}]);const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='AERIQ-medicion-publica.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
  map.on('moveend',()=>{clearTimeout(controllerTimer);controllerTimer=setTimeout(drawZones,150);});
  q(store.get('mapStyle','dark')==='light'?'[data-map-style="light"]':'[data-map-style="dark"]')?.click();refresh();
  const timer=setInterval(()=>{if(!document.hidden)refresh();},30000);window.addEventListener('pagehide',()=>{clearInterval(timer);clearTimeout(controllerTimer);map.remove();},{once:true});
}
