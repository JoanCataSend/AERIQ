'use strict';
function initAeriqMap(){
  const host=document.querySelector('#real-gandia-map');if(!host)return;
  const q=s=>document.querySelector(s),status=q('#map-loading'),list=q('#map-sensor-list'),directory=q('#sensor-directory');
  if(typeof L==='undefined'){status.hidden=false;status.textContent='No se ha podido cargar la cartografía. Comprueba tu conexión.';return;}
  const campus=[38.996651,-0.166009],map=L.map(host,{zoomControl:false}).setView(campus,16),pins=L.layerGroup().addTo(map),trail=L.layerGroup().addTo(map);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
  // Zonas en un lienzo propio, por debajo de la ruta y de los puntos; no recibe toques (la consulta de punto los necesita).
  const zonePane=map.createPane('aeriq-zonas'),routePane=map.createPane('aeriq-ruta');
  zonePane.style.zIndex='350';zonePane.style.pointerEvents='none';routePane.style.zIndex='390';routePane.style.pointerEvents='none';
  const canvas=document.createElement('canvas'),buffer=document.createElement('canvas');
  canvas.className='map-zones leaflet-zoom-hide';zonePane.append(canvas);
  const ctx=canvas.getContext('2d'),bufferCtx=buffer.getContext('2d');
  let rows=[],selected=null,requestId=0,source='all',hasFit=false,truncated=false;
  const colors={bajo:'#219c72',moderado:'#eea22d',alto:'#e35b5d'};
  const level=r=>AeriqDomain.level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto));
  // Solo formato de presentación: hora local y coordenadas con el redondeo visible.
  const utcLabel=v=>AeriqDomain.when(v);
  const coord=v=>Number.isFinite(Number(v))?Number(v).toFixed(5):String(v);
  // Interpolación acotada (T25): parámetros y cálculo en aeriq-domain.js; aquí solo se agrupa, se pinta y se consulta.
  const P=AeriqDomain.INTERPOLACION,Z=AeriqDomain.ZONA;
  // Rejilla de pantalla: se evalúa un punto cada PASO píxeles y la imagen se amplía suavizada,
  // así el borde de cada zona sigue el radio (redondo) en vez de formar cuadrados.
  // Misma transparencia para los tres niveles (T47): ningún color «pesa» más que otro donde se juntan zonas.
  const PASO=4,ALFA=92,RGBA={[Z.insuficiente]:[104,109,107,48],[Z.bajo]:[33,156,114,ALFA],[Z.moderado]:[238,162,45,ALFA],[Z.alto]:[227,91,93,ALFA]};
  const routeColor=getComputedStyle(document.documentElement).getPropertyValue('--route').trim()||'#24508f';
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
    const lines=[['strong',r.zona],['p',`${r.valor} ${r.unidad} · ${level(r)}`],['p',utcLabel(r.capturada_en)],['p',`${coord(r.latitud)}, ${coord(r.longitud)}`],['p',r.simulado?'Datos simulados':'Medición real · no sustituye a estaciones oficiales']];
    // El nombre del AERIQ solo llega en el mapa de la organización, nunca en el público.
    if(r.dispositivo)lines.splice(1,0,['p',r.dispositivo]);
    for(const [tag,text] of lines){const n=document.createElement(tag);n.textContent=text;info.append(n);}
    if(pan)map.panTo([r.latitud,r.longitud]);q('#selected-id').textContent=r.id;
  }
  function drawZones(){
    let clipped=false;const size=map.getSize();
    // El lienzo cubre la vista actual; al arrastrar se mueve con el mapa y al terminar se vuelve a calcular.
    canvas.width=size.x;canvas.height=size.y;canvas.style.width=`${size.x}px`;canvas.style.height=`${size.y}px`;
    L.DomUtil.setPosition(canvas,map.containerPointToLayerPoint([0,0]));
    if(ctx){ctx.clearRect(0,0,size.x,size.y);}
    if(ctx&&bufferCtx&&q('#heat-toggle').checked&&rows.length&&size.x>0&&size.y>0){
      // Color: tres o más posiciones dentro del radio. Gris: hay mediciones cerca pero no bastan. Sin pintar: sin datos.
      // En Web Mercator la latitud solo depende de la fila y la longitud de la columna.
      const cols=Math.ceil(size.x/PASO),lines=Math.ceil(size.y/PASO);
      const lats=Array.from({length:lines},(_,i)=>map.containerPointToLatLng([0,(i+.5)*PASO]).lat);
      const lngs=Array.from({length:cols},(_,j)=>map.containerPointToLatLng([(j+.5)*PASO,0]).lng);
      const grid=AeriqDomain.raster(groups.map(g=>({...g,radius:radiusOf(g)})),lats,lngs);clipped=grid.recortado;
      if(grid.evaluadas){
        buffer.width=cols;buffer.height=lines;const image=bufferCtx.createImageData(cols,lines);
        for(let k=0;k<grid.estados.length;k++){const c=RGBA[grid.estados[k]];if(c)image.data.set(c,k*4);}
        bufferCtx.putImageData(image,0,0);
        ctx.imageSmoothingEnabled=true;ctx.drawImage(buffer,0,0,cols*PASO,lines*PASO);
      }
    }
    if(baseStatus)status.textContent=baseStatus+(clipped?' Zonas recortadas por rendimiento: acerca el mapa para verlas completas.':'');
  }
  // Ruta del AERIQ (solo «Mi organización»): sus posiciones en orden de captura, partidas cuando hay un hueco largo.
  function drawRoute(fit=false){
    trail.clearLayers();
    const devices=new Map();
    if(source==='mine')for(const r of rows)if(r.dispositivo_id!==undefined&&r.dispositivo_id!==null&&!devices.has(String(r.dispositivo_id)))devices.set(String(r.dispositivo_id),r.dispositivo||`AERIQ #${r.dispositivo_id}`);
    const box=q('#route-control'),toggle=q('#route-toggle'),select=q('#route-device'),note=q('#route-note'),current=select.value;
    box.hidden=!devices.size;q('#legend-route').hidden=true;
    select.replaceChildren(...[...devices].sort((a,b)=>a[1].localeCompare(b[1],'es')).map(([id,name])=>new Option(name,id)));
    if(devices.has(current))select.value=current;
    select.disabled=!toggle.checked;
    if(!devices.size||!toggle.checked){note.textContent='Une las posiciones de un AERIQ en orden de captura, dentro del periodo elegido.';q('#route-distance').textContent='';return;}
    const segments=AeriqDomain.route(rows.filter(r=>String(r.dispositivo_id)===select.value)),points=segments.flat();
    for(const segment of segments)if(segment.length>1)L.polyline(segment.map(r=>[r.latitud,r.longitud]),{pane:'aeriq-ruta',color:routeColor,weight:3.5,opacity:.9,lineJoin:'round',lineCap:'round',interactive:false}).addTo(trail);
    if(points.length){
      const first=points[0],last=points[points.length-1];
      L.circleMarker([first.latitud,first.longitud],{radius:6,color:routeColor,weight:3,fillColor:'#fff',fillOpacity:1,bubblingMouseEvents:false}).bindTooltip(`Inicio · ${utcLabel(first.capturada_en)}`).addTo(trail);
      if(points.length>1)L.circleMarker([last.latitud,last.longitud],{radius:6,color:'#fff',weight:2,fillColor:routeColor,fillOpacity:1,bubblingMouseEvents:false}).bindTooltip(`Última posición · ${utcLabel(last.capturada_en)}`).addTo(trail);
      q('#legend-route').hidden=false;
      if(fit)map.fitBounds(L.latLngBounds(points.map(r=>[r.latitud,r.longitud])),{maxZoom:18,padding:[40,40]});
    }
    // HU39: distancia recorrida en el periodo (sin contar los huecos entre tramos ni los saltos del GPS).
    const meters=AeriqDomain.routeDistance(segments);q('#route-distance').textContent=points.length>1?`Distancia recorrida: ${AeriqDomain.distanceText(meters)}`:'';
    note.textContent=`${points.length} ${points.length===1?'posición':'posiciones'} en ${segments.length} ${segments.length===1?'tramo':'tramos'}. Un hueco de más de 10 minutos corta la línea.${truncated?' Solo entran las 10.000 mediciones más recientes del periodo.':''}`;
  }
  const redraw=()=>{drawZones();if(queried)query(queried);};
  async function refresh(){
    const id=++requestId,previousId=selected?.id,pending=queried;status.hidden=false;baseStatus='';status.textContent='Consultando mediciones autorizadas…';selected=null;q('#map-info').hidden=true;q('#selected-id').textContent='—';pins.clearLayers();trail.clearLayers();if(ctx)ctx.clearRect(0,0,canvas.width,canvas.height);list.replaceChildren();directory.hidden=true;
    try{
      // Reales y simuladas nunca se mezclan: la casilla elige uno u otro origen.
      const mode=q('#simulated-toggle').checked?'simulated':'real';
      const result=await Aeriq.consultar_mapa(source==='mine'?'organizacion':'publico',mode==='simulated'?'simulada':'real',Number(q('#public-period')?.value||24));if(id!==requestId)return;rows=result.measurements;truncated=Boolean(result.truncated);directory.hidden=!rows.length;buildGroups();showRadius();
      // Un punto por posición: la lectura más reciente de cada sitio, la misma con la que se pinta la zona (T46).
      // Las anteriores del mismo sitio siguen en la lista de mediciones, pero no se apilan con otros colores en el mapa.
      for(const g of groups)for(const r of g.samples){
        const marker=L.circleMarker([r.latitud,r.longitud],{radius:6,color:'#fff',weight:2,fillColor:colors[level(r)],fillOpacity:1,bubblingMouseEvents:false}).on('click',()=>choose(r));pins.addLayer(marker);
      }
      for(const r of rows){
        const button=document.createElement('button');button.className='sensor-list-item';button.textContent=`${r.zona} · ${r.valor} ${r.unidad} · ${r.simulado?'Simulado':'Real'}`;button.onclick=()=>choose(r);list.append(button);
      }
      if(!q('#marker-toggle').checked)map.removeLayer(pins);else pins.addTo(map);
      baseStatus=rows.length?`${rows.length} mediciones autorizadas. Gris = datos insuficientes; sin color = sin datos.${result.truncated?' Se muestran las 10.000 más recientes.':''}`:'No hay mediciones publicadas de este origen. Las áreas permanecen sin datos.';status.textContent=baseStatus;
      if(rows.length){if(!hasFit){map.fitBounds(L.latLngBounds(rows.map(r=>[r.latitud,r.longitud])),{maxZoom:18});hasFit=true;}drawZones();const previous=rows.find(r=>r.id===previousId);if(previous)choose(previous,false);}
      drawRoute();
      // La consulta de un punto se mantiene tras recargar los datos.
      if(pending)query(pending);
    }catch(e){if(id===requestId){rows=[];groups=[];queried=null;truncated=false;showRadius();drawRoute();aeriqStatus(status,e.message,'error');}}
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
  q('#route-toggle').onchange=()=>drawRoute(true);q('#route-device').onchange=()=>drawRoute(true);
  q('#zoomin-map').onclick=()=>map.zoomIn();q('#zoomout-map').onclick=()=>map.zoomOut();
  q('#map-search').onkeydown=e=>{if(e.key!=='Enter')return;e.preventDefault();const term=e.target.value.toLowerCase();if(term.includes('playa'))map.setView([38.999,-.1589],16);else if(term.includes('grau')||term.includes('grao'))map.setView([38.9922,-.1656],15);else if(term.includes('upv')||term.includes('gand'))map.setView(campus,16);else toast('Búsqueda local: UPV, playa o Grau de Gandía.','warn');};
  q('#export-sensor').onclick=()=>{if(!selected){toast('Selecciona una medición pública.','warn');return;}const r=selected;const csv=AeriqDomain.csv([{...r,dispositivo:'Medición pública #'+r.id,campana:r.campaignId}]);const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='AERIQ-medicion-publica.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
  // Sin espera: tras un zoom el lienzo anterior quedaría a otra escala. Calcular la vista cuesta pocos milisegundos.
  map.on('moveend',drawZones);map.on('resize',drawZones);
  refresh();
  const timer=setInterval(()=>{if(!document.hidden)refresh();},30000);window.addEventListener('pagehide',()=>{clearInterval(timer);map.remove();},{once:true});
}
