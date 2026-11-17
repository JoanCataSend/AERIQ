'use strict';
function initAeriqMap(){
  const host=document.querySelector('#real-gandia-map');if(!host)return;
  const q=s=>document.querySelector(s),status=q('#map-loading'),list=q('#map-sensor-list'),directory=q('#sensor-directory');
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
  // Interpolación acotada (T25): parámetros y cálculo en aeriq-domain.js; aquí solo se agrupa, se pinta y se consulta.
  const P=AeriqDomain.INTERPOLACION,METROS_GRADO=111320,grey='#686d6b';
  let groups=[],radiusOverride=null,queried=null,baseStatus='';
  // El control del mapa sustituye al radio de la campaña solo en esta vista; no se guarda ni se envía.
  const radiusOf=g=>radiusOverride??g.radius;
  function buildGroups(){
    const byKey=new Map();for(const r of rows){const k=AeriqDomain.groupKey(r);if(!byKey.has(k))byKey.set(k,[]);byKey.get(k).push(r);}
    groups=[...byKey.values()].map(list=>({samples:AeriqDomain.support(list),radius:Number(list[0].radio_m),unidad:list[0].unidad,umbral_medio:list[0].umbral_medio,umbral_alto:list[0].umbral_alto,simulado:Boolean(list[0].simulado)}));
  }
  function showRadius(){
    const radii=[...new Set(groups.map(g=>g.radius))].sort((a,b)=>a-b),first=radii[0]??P.radio;
    if(radiusOverride===null)q('#radius-range').value=first;
    q('#radius-value').textContent=radiusOverride!==null?`${radiusOverride} m`:radii.length>1?`${first}–${radii[radii.length-1]} m`:`${first} m`;
    q('#radius-note').textContent=radiusOverride!==null?'Radio de prueba: solo cambia esta vista y no se guarda.':'Se aplica el radio configurado en cada campaña.';
    q('#radius-reset').hidden=radiusOverride===null;
  }
  // Pulsar el mapa explica ese punto: valor estimado, datos insuficientes o sin datos. No cambia la medición seleccionada.
  function query(latlng){
    queried=latlng;const point=[latlng.lat,latlng.lng];let best=null;
    for(const g of groups){
      const radius=radiusOf(g),e=AeriqDomain.estimate(point,g.samples,radius),rank=e.valor!==null?2:e.motivo==='insuficiente'?1:0;
      if(!best||rank>best.rank||(rank===best.rank&&(e.cercana??Infinity)<(best.e.cercana??Infinity)))best={e,g,radius,rank};
    }
    if(!best)best={e:AeriqDomain.estimate(point,[],P.radio),g:{},radius:P.radio};
    const text=AeriqDomain.describe(best.e,best.radius,best.g),info=q('#map-info');info.hidden=false;info.replaceChildren();
    for(const [tag,value] of [['strong','Punto consultado'],['p',text.titulo],['p',`${coord(point[0])}, ${coord(point[1])}`],['p',text.detalle]]){const n=document.createElement(tag);n.textContent=value;info.append(n);}
  }
  function choose(r,pan=true){selected=r;queried=null;const info=q('#map-info');info.hidden=false;info.replaceChildren();
    for(const [tag,text] of [['strong',r.zona],['p',`${r.valor} ${r.unidad} · ${level(r)}`],['p',utcLabel(r.capturada_en)],['p',`${coord(r.latitud)}, ${coord(r.longitud)}`],['p',r.simulado?'Datos simulados':'Medición real · no sustituye a estaciones oficiales']]){const n=document.createElement(tag);n.textContent=text;info.append(n);}
    if(pan)map.panTo([r.latitud,r.longitud]);q('#selected-id').textContent=r.id;
  }
  function drawZones(){
    zones.clearLayers();let clipped=false;
    if(q('#heat-toggle').checked&&rows.length){
      // Color: tres o más posiciones dentro del radio. Gris: hay mediciones cerca pero no bastan. Sin pintar: sin datos.
      const bounds=map.getBounds().pad(.1),latStep=P.celda/METROS_GRADO,bucketLat=P.radioMax*1.1/METROS_GRADO;
      let count=0,examined=0;
      scan:for(const g of groups){
        if(!g.samples.length)continue;
        // Latitud de referencia fija por grupo: la rejilla no se desplaza al mover el mapa.
        const cos=Math.max(.01,Math.cos(Math.round(g.samples[0].latitud)*Math.PI/180)),lonStep=latStep/cos,bucketLon=bucketLat/cos;
        const radius=radiusOf(g),reach=Math.ceil(radius/P.celda)+1,buckets=new Map();
        for(const r of g.samples){const key=`${Math.floor(r.latitud/bucketLat)}:${Math.floor(r.longitud/bucketLon)}`;if(!buckets.has(key))buckets.set(key,[]);buckets.get(key).push(r);}
        // Muestrear solo alrededor de mediciones visibles, hasta donde alcanza el radio, sin recorrer el mapa completo.
        const cells=new Map();for(const r of g.samples){if(!bounds.contains([r.latitud,r.longitud]))continue;const a=Math.floor(r.latitud/latStep),b=Math.floor(r.longitud/lonStep);for(let i=-reach;i<=reach;i++)for(let j=-reach;j<=reach;j++)cells.set(`${a+i}:${b+j}`,[a+i,b+j]);}
        for(const [a,b] of cells.values()){
          // El tope de rendimiento se avisa: una zona recortada no debe confundirse con una zona sin datos.
          if(count>=2500||examined++>=8000){clipped=true;break scan;}
          const south=a*latStep,west=b*lonStep,centre=[south+latStep/2,west+lonStep/2];
          const x=Math.floor(centre[0]/bucketLat),y=Math.floor(centre[1]/bucketLon),near=[];
          for(let i=-1;i<=1;i++)for(let j=-1;j<=1;j++)near.push(...(buckets.get(`${x+i}:${y+j}`)||[]));
          // El valor se calcula en el centro de la celda que se pinta.
          const e=AeriqDomain.estimate(centre,near,radius);if(e.valor===null&&e.motivo!=='insuficiente')continue;
          const style=e.valor===null?{stroke:true,color:grey,weight:1,opacity:.55,dashArray:'3 3',fillColor:grey,fillOpacity:.13,interactive:false}
            :{stroke:false,fillColor:colors[AeriqDomain.level(e.valor,g.umbral_medio,g.umbral_alto)],fillOpacity:.25,interactive:false};
          L.rectangle([[south,west],[south+latStep,west+lonStep]],style).addTo(zones);count++;
        }
      }
    }
    if(baseStatus)status.textContent=baseStatus+(clipped?' Zonas recortadas por rendimiento: acerca el mapa para verlas completas.':'');
  }
  const redraw=()=>{drawZones();if(queried)query(queried);};
  async function refresh(){
    const id=++requestId,previousId=selected?.id,pending=queried;status.hidden=false;baseStatus='';status.textContent='Consultando mediciones autorizadas…';selected=null;q('#map-info').hidden=true;q('#selected-id').textContent='—';pins.clearLayers();zones.clearLayers();list.replaceChildren();directory.hidden=true;
    try{
      // Reales y simuladas nunca se mezclan: la casilla elige uno u otro origen.
      const mode=q('#simulated-toggle').checked?'simulated':'real';
      const result=await Aeriq.map(mode,source,Number(q('#public-period')?.value||24));if(id!==requestId)return;rows=result.measurements;directory.hidden=!rows.length;buildGroups();showRadius();
      for(const r of rows){
        const marker=L.circleMarker([r.latitud,r.longitud],{radius:6,color:'#fff',weight:2,fillColor:colors[level(r)],fillOpacity:1,bubblingMouseEvents:false}).on('click',()=>choose(r));pins.addLayer(marker);
        const button=document.createElement('button');button.className='sensor-list-item';button.textContent=`${r.zona} · ${r.valor} ${r.unidad} · ${r.simulado?'Simulado':'Real'}`;button.onclick=()=>choose(r);list.append(button);
      }
      if(!q('#marker-toggle').checked)map.removeLayer(pins);else pins.addTo(map);
      baseStatus=rows.length?`${rows.length} mediciones autorizadas. Gris = datos insuficientes; sin color = sin datos.${result.truncated?' Se muestran las 2.000 más recientes.':''}`:'No hay mediciones publicadas de este origen. Las áreas permanecen sin datos.';status.textContent=baseStatus;
      if(rows.length){if(!hasFit){map.fitBounds(L.latLngBounds(rows.map(r=>[r.latitud,r.longitud])),{maxZoom:18});hasFit=true;}drawZones();const previous=rows.find(r=>r.id===previousId);if(previous)choose(previous,false);}
      // La consulta de un punto se mantiene tras recargar los datos.
      if(pending)query(pending);
    }catch(e){if(id===requestId){rows=[];groups=[];queried=null;showRadius();status.textContent=e.message;}}
  }
  const range=q('#radius-range');range.min=P.radioMin;range.max=P.radioMax;
  range.oninput=()=>{radiusOverride=Math.min(P.radioMax,Math.max(P.radioMin,Number(range.value)));showRadius();redraw();};
  q('#radius-reset').onclick=()=>{radiusOverride=null;showRadius();redraw();};
  map.on('click',e=>query(e.latlng));
  q('#simulated-toggle').onchange=()=>{hasFit=false;refresh();};q('#public-period').onchange=refresh;
  // «Mi organización» solo tiene sentido con una cuenta vinculada a una organización.
  if(typeof AeriqAuth!=='undefined')AeriqAuth.session().then(s=>{q('#source-switch').hidden=!s?.user?.organizationId;}).catch(()=>{});
  for(const button of document.querySelectorAll('[data-source]'))button.onclick=()=>{source=button.dataset.source;hasFit=false;for(const b of document.querySelectorAll('[data-source]'))b.classList.toggle('selected',b===button);refresh();};q('#heat-toggle').onchange=drawZones;
  q('#marker-toggle').onchange=e=>{if(e.target.checked)pins.addTo(map);else map.removeLayer(pins);};
  q('#zoomin-map').onclick=()=>map.zoomIn();q('#zoomout-map').onclick=()=>map.zoomOut();
  q('#map-search').onkeydown=e=>{if(e.key!=='Enter')return;e.preventDefault();const term=e.target.value.toLowerCase();if(term.includes('playa'))map.setView([38.999,-.1589],16);else if(term.includes('grau')||term.includes('grao'))map.setView([38.9922,-.1656],15);else if(term.includes('upv')||term.includes('gand'))map.setView(campus,16);else toast('Búsqueda local: UPV, playa o Grau de Gandía.');};
  q('#export-sensor').onclick=()=>{if(!selected){toast('Selecciona una medición pública.');return;}const r=selected;const csv=AeriqDomain.csv([{...r,dispositivo:'Medición pública #'+r.id,campana:r.campaignId}]);const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='AERIQ-medicion-publica.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
  map.on('moveend',()=>{clearTimeout(controllerTimer);controllerTimer=setTimeout(drawZones,150);});
  refresh();
  const timer=setInterval(()=>{if(!document.hidden)refresh();},30000);window.addEventListener('pagehide',()=>{clearInterval(timer);clearTimeout(controllerTimer);map.remove();},{once:true});
}
