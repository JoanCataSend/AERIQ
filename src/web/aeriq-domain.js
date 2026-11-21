/* Cálculos compartidos sin DOM: informes, CSV e interpolación local IDW acotada. */
(function(root){
  'use strict';
  const level=(v,medium,high)=>v>=high?'alto':v>=medium?'moderado':'bajo';
  function csvCell(v){const text=String(v??'');return '"'+(typeof v!=='number'&&/^[\s]*[=+@-]/.test(text)?"'"+text:text).replaceAll('"','""')+'"';}
  function csv(rows){
    const keys=['capturada_en','valor','unidad','dispositivo','campana','zona','latitud','longitud','simulado'];
    return '\ufeff'+keys.join(';')+'\r\n'+rows.map(r=>keys.map(k=>{let v=r[k];if(['valor','latitud','longitud','simulado'].includes(k)&&v!==null&&v!==undefined&&v!==''&&Number.isFinite(Number(v)))v=Number(v);return csvCell(v);}).join(';')).join('\r\n');
  }
  const utcTime=v=>Date.parse(String(v??'').replace(' ','T')+'Z');
  /**
   * Agrupación del informe cuando se elige «Automática» (T39): con datos de dos días o menos, por horas (si no,
   * la gráfica saldría con un solo punto); hasta 60 días, por días; más, por semanas.
   */
  function autoGroup(rows){
    const times=rows.map(r=>utcTime(r.capturada_en)).filter(Number.isFinite);
    if(!times.length)return 'day';
    const span=Math.max(...times)-Math.min(...times);
    return span<=48*3600000?'hour':span<=60*86400000?'day':'week';
  }
  /** group: 'hour' (clave «AAAA-MM-DD HH:00:00» UTC), 'day' («AAAA-MM-DD») o 'week' (lunes de la semana). */
  function statistics(rows,group='day'){
    if(!rows.length)return {count:0,min:null,max:null,mean:null,series:[],exceedances:[]};
    const buckets=new Map(),values=rows.map(r=>Number(r.valor));
    for(const r of rows){
      const date=new Date(r.capturada_en.replace(' ','T')+'Z');
      if(group==='week')date.setUTCDate(date.getUTCDate()-((date.getUTCDay()+6)%7));
      const iso=date.toISOString(),key=group==='hour'?iso.slice(0,10)+' '+iso.slice(11,13)+':00:00':iso.slice(0,10);
      if(!buckets.has(key))buckets.set(key,[]);buckets.get(key).push(Number(r.valor));
    }
    return {count:rows.length,min:Math.min(...values),max:Math.max(...values),mean:values.reduce((a,b)=>a+b,0)/values.length,
      series:[...buckets].sort(([a],[b])=>a.localeCompare(b)).map(([date,v])=>({date,mean:v.reduce((a,b)=>a+b,0)/v.length,min:Math.min(...v),max:Math.max(...v)})),
      exceedances:rows.filter(r=>r.unidad!=='°C'&&r.umbral_medio!==null&&r.umbral_medio!==undefined&&Number(r.valor)>=Number(r.umbral_medio))};
  }
  // Umbrales comunes de un grupo de lecturas: solo si todas tienen el mismo umbral medio y alto; si no, null.
  function sharedThresholds(rows){
    if(!rows.length||rows.some(r=>r.umbral_medio===null||r.umbral_medio===undefined||r.umbral_alto===null||r.umbral_alto===undefined))return null;
    const medium=Number(rows[0].umbral_medio),high=Number(rows[0].umbral_alto);
    return rows.every(r=>Number(r.umbral_medio)===medium&&Number(r.umbral_alto)===high)?{medium,high}:null;
  }
  /**
   * Picos de ozono (T40): tramos seguidos de lecturas que alcanzan el umbral medio de su campaña. Un tramo se corta
   * cuando una lectura baja del umbral o hay un hueco sin lecturas mayor que maxHueco minutos o que 1,5 veces el
   * intervalo típico entre lecturas (así, con una lectura por hora, una tarde entera sobre el umbral es un solo pico). De cada tramo se da la lectura
   * más alta: hora (capturada_en, UTC), valor, nivel ('alto' si llega al umbral alto, si no 'moderado'), cuántas
   * lecturas tiene el tramo y los umbrales. Sin umbrales (temperatura) no hay picos. Ordenados por hora.
   */
  function peaks(rows,maxHueco=30){
    const valid=rows.filter(r=>r.unidad!=='°C'&&r.umbral_medio!==null&&r.umbral_medio!==undefined&&r.umbral_medio!==''&&Number.isFinite(utcTime(r.capturada_en)));
    const sorted=[...valid].sort((a,b)=>utcTime(a.capturada_en)-utcTime(b.capturada_en)||Number(a.id??0)-Number(b.id??0)),out=[];
    const gaps=sorted.slice(1).map((r,i)=>utcTime(r.capturada_en)-utcTime(sorted[i].capturada_en)).filter(g=>g>0).sort((a,b)=>a-b);
    const limit=Math.max(maxHueco*60000,gaps.length?1.5*gaps[gaps.length>>1]:0);
    let episode=null,last=null;
    const close=()=>{if(episode){const r=episode.top;out.push({capturada_en:r.capturada_en,valor:Number(r.valor),unidad:r.unidad,dispositivo:r.dispositivo,
      nivel:Number(r.valor)>=Number(r.umbral_alto)?'alto':'moderado',lecturas:episode.count,umbral_medio:Number(r.umbral_medio),umbral_alto:Number(r.umbral_alto)});}episode=null;};
    for(const r of sorted){
      const over=Number(r.valor)>=Number(r.umbral_medio);
      if(episode&&(!over||utcTime(r.capturada_en)-utcTime(last.capturada_en)>limit))close();
      if(over){if(!episode)episode={top:r,count:0};episode.count++;if(Number(r.valor)>Number(episode.top.valor))episode.top=r;}
      last=r;
    }
    close();return out;
  }
  /** Franjas horarias del veredicto (T41), en hora local de quien mira el informe. */
  const FRANJAS=Object.freeze([{codigo:'madrugada',nombre:'Madrugada',horas:'0–6 h',desde:0,hasta:6},{codigo:'manana',nombre:'Mañana',horas:'6–12 h',desde:6,hasta:12},
    {codigo:'tarde',nombre:'Tarde',horas:'12–18 h',desde:12,hasta:18},{codigo:'noche',nombre:'Noche',horas:'18–24 h',desde:18,hasta:24}]);
  const CALIDAD={bajo:'buena',moderado:'regular',alto:'mala'};
  /**
   * Veredicto de calidad del aire por franja horaria (T41). Cada franja se califica con la media de sus lecturas
   * y los umbrales de la campaña: por debajo del umbral medio, buena; hasta el alto, regular; desde el alto, mala.
   * Si las lecturas de una franja tienen umbrales distintos (varias campañas), se usa el nivel más repetido.
   * options.hour(r) devuelve la hora local (0-23) de una lectura; por defecto, la del navegador.
   * Devuelve {franjas:[{…FRANJA, lecturas, media, calidad|null}], texto}.
   */
  function verdict(rows,options={}){
    const hourOf=options.hour||(r=>new Date(utcTime(r.capturada_en)).getHours());
    const valid=rows.filter(r=>r.unidad!=='°C'&&r.umbral_medio!==null&&r.umbral_medio!==undefined&&r.umbral_medio!==''&&Number.isFinite(utcTime(r.capturada_en)));
    const unit=valid[0]?.unidad||'ppb';
    const franjas=FRANJAS.map(f=>{
      const set=valid.filter(r=>{const h=hourOf(r);return h>=f.desde&&h<f.hasta;});
      if(!set.length)return {...f,lecturas:0,media:null,calidad:null};
      const media=set.reduce((a,r)=>a+Number(r.valor),0)/set.length,limits=sharedThresholds(set);
      let nivel;
      if(limits)nivel=level(media,limits.medium,limits.high);
      else{const count={bajo:0,moderado:0,alto:0};for(const r of set)count[level(Number(r.valor),Number(r.umbral_medio),Number(r.umbral_alto))]++;nivel=['alto','moderado','bajo'].reduce((a,b)=>count[b]>count[a]?b:a,'alto');}
      return {...f,lecturas:set.length,media,calidad:CALIDAD[nivel]};
    });
    const rated=franjas.filter(f=>f.calidad);
    if(!rated.length)return {franjas,texto:'No hay lecturas de ozono con umbrales para calificar las franjas horarias.'};
    const name=f=>({madrugada:'de madrugada',manana:'por la mañana',tarde:'por la tarde',noche:'por la noche'})[f.codigo];
    const join=list=>list.length>1?list.slice(0,-1).join(', ')+' y '+list.at(-1):list[0];
    const parts=['buena','regular','mala'].map(q=>{const fs=rated.filter(f=>f.calidad===q);if(!fs.length)return null;
      return `${q} ${join(fs.map(name))}`+(q==='buena'?'':` (media ${fs.map(f=>Math.round(f.media)).join(' / ')} ${unit})`);}).filter(Boolean);
    const worst=rated.some(f=>f.calidad==='mala')?'mala':rated.some(f=>f.calidad==='regular')?'regular':'buena';
    const advice=worst==='mala'?' Evita la actividad física intensa al aire libre en las franjas malas.':worst==='regular'?' En las franjas regulares, mejor evitar el ejercicio intenso.':' No se ha alcanzado el umbral medio de ozono en ninguna franja.';
    const empty=franjas.filter(f=>!f.calidad).map(f=>f.nombre.toLowerCase());
    return {franjas,peor:worst,texto:`La calidad del aire es ${join(parts)}.${advice}`+(empty.length?` Sin lecturas: ${join(empty)}.`:'')};
  }
  function distance(a,b){const rad=Math.PI/180;const dlat=(a[0]-b[0])*rad,dlon=(a[1]-b[1])*rad;const h=Math.sin(dlat/2)**2+Math.cos(a[0]*rad)*Math.cos(b[0]*rad)*Math.sin(dlon/2)**2;return 6371000*2*Math.asin(Math.min(1,Math.sqrt(h)));}
  // Interpolación IDW local acotada (T25/HU28). Mismos parámetros y pasos que PublicReadings.java; ver doc/interpolacion.md.
  const INTERPOLACION=Object.freeze({
    radioMin:20,radioMax:50, // rango admitido del radio (m); fuera de él no se estima
    radio:30,                // radio por defecto; cada campaña guarda el suyo en campanas.radio_m
    minPosiciones:3,         // posiciones de soporte distintas exigidas dentro del radio
    separacionMin:5,         // lecturas más juntas (m) cuentan como una sola posición: deriva del GPS
    potencia:3,              // exponente del peso 1/d^p: 3 (antes 2) para que mande la medición más cercana (T46)
    zonaPropia:.5,           // alrededor de cada posición, hasta esta fracción de la distancia a su vecina más cercana...
    zonaPropiaMax:15,        // ...y como mucho estos metros, la zona toma el valor medido en ese punto (T46)
    distanciaMin:1,          // suelo de distancia (m) para no dividir entre cero
    celda:10,                // lado (m) de la rejilla de validación de las pruebas
    maxEvaluaciones:250000   // puntos que raster() evalúa como máximo por pintado; si se alcanza, se avisa
  });
  const METROS_GRADO=111320;
  // Solo se interpolan lecturas de la misma campaña, unidad y origen (real/simulado).
  const groupKey=r=>`${r.campaignId}|${r.unidad}|${r.simulado?1:0}`;
  const order=(a,b)=>a<b?-1:a>b?1:0;
  /** Posiciones de soporte: una lectura (la más reciente) por cada posición separada al menos separacionMin de las demás de su grupo. */
  function support(rows,options){
    const o={...INTERPOLACION,...options},out=[];
    const valid=rows.filter(r=>r.latitud!==null&&r.longitud!==null&&r.latitud!==''&&r.longitud!==''&&[r.latitud,r.longitud,r.valor].every(v=>Number.isFinite(Number(v))));
    if(!valid.length)return out;
    // Orden propio (fecha descendente y desempates fijos): el resultado no depende del orden de entrada.
    const sorted=[...valid].sort((a,b)=>order(String(b.capturada_en??''),String(a.capturada_en??''))||order(Number(a.latitud),Number(b.latitud))||order(Number(a.longitud),Number(b.longitud))||order(Number(a.valor),Number(b.valor)));
    // Rejilla de separacionMin para comparar solo con vecinas; ±2 celdas cubre el estrechamiento en longitud.
    const latStep=o.separacionMin/METROS_GRADO,lonStep=latStep/Math.max(.01,Math.cos(Number(sorted[0].latitud)*Math.PI/180)),cells=new Map();
    for(const r of sorted){
      const p=[Number(r.latitud),Number(r.longitud)],x=Math.floor(p[0]/latStep),y=Math.floor(p[1]/lonStep),key=groupKey(r);
      let repeated=false;
      for(let i=-2;i<=2&&!repeated;i++)for(let j=-2;j<=2&&!repeated;j++)for(const s of cells.get(`${key}|${x+i}|${y+j}`)||[])if(distance(p,[Number(s.latitud),Number(s.longitud)])<o.separacionMin){repeated=true;break;}
      if(repeated)continue;
      const k=`${key}|${x}|${y}`;if(!cells.has(k))cells.set(k,[]);cells.get(k).push(r);out.push(r);
    }
    return out;
  }
  /**
   * Zona propia de cada posición de soporte (T46): radio (m) alrededor del punto en el que manda su propio valor,
   * la mitad de la distancia a la posición vecina más cercana de su grupo, como mucho zonaPropiaMax. Así el color
   * junto a un punto rojo es rojo aunque sus vecinos sean amarillos. Devuelve un Map lectura → radio (m).
   * Se guarda por lista de posiciones: llamar dos veces con la misma lista no repite el cálculo.
   */
  const ownCache=new WeakMap();
  function ownRadii(samples,options){
    const o={...INTERPOLACION,...options};
    if(ownCache.has(samples))return ownCache.get(samples);
    const out=new Map(),reach=o.zonaPropiaMax/o.zonaPropia;
    if(samples.length){
      const latStep=reach/METROS_GRADO,lonStep=latStep/Math.max(.01,Math.cos(Number(samples[0].latitud)*Math.PI/180)),cells=new Map();
      const cellOf=r=>[Math.floor(Number(r.latitud)/latStep),Math.floor(Number(r.longitud)/lonStep)];
      for(const r of samples){const [x,y]=cellOf(r),k=`${x}:${y}`;if(!cells.has(k))cells.set(k,[]);cells.get(k).push(r);}
      for(const r of samples){
        const [x,y]=cellOf(r),p=[Number(r.latitud),Number(r.longitud)];let nn=Infinity;
        for(let a=-1;a<=1;a++)for(let b=-1;b<=1;b++)for(const s of cells.get(`${x+a}:${y+b}`)||[]){
          if(s===r||groupKey(s)!==groupKey(r))continue;const d=distance(p,[Number(s.latitud),Number(s.longitud)]);if(d<nn)nn=d;}
        out.set(r,Math.min(o.zonaPropiaMax,nn*o.zonaPropia));
      }
    }
    ownCache.set(samples,out);return out;
  }
  /**
   * Estimación en un punto a partir de posiciones de soporte (resultado de support).
   * motivo: null (hay valor) · 'radio' (fuera de 20–50 m) · 'lejos' (ninguna posición en el radio: sin datos)
   * · 'insuficiente' (menos de minPosiciones) · 'mezcla' (campañas, unidades u orígenes distintos).
   */
  function estimate(point,samples,radius,options){
    const o={...INTERPOLACION,...options};
    if(!Number.isFinite(radius)||radius<o.radioMin||radius>o.radioMax)return {valor:null,posiciones:0,cercana:null,motivo:'radio'};
    let first=null,mixed=false,sum=0,weight=0,count=0,nearest=Infinity,closest=null;
    for(const r of samples){
      const d=distance(point,[Number(r.latitud),Number(r.longitud)]);if(d<nearest){nearest=d;closest=r;}if(d>radius)continue;
      if(!first)first=r;else if(groupKey(r)!==groupKey(first))mixed=true;
      const w=1/Math.max(o.distanciaMin,d)**o.potencia;sum+=Number(r.valor)*w;weight+=w;count++;
    }
    const base={posiciones:count,cercana:Number.isFinite(nearest)?nearest:null,peso:weight};
    if(!count)return {valor:null,...base,motivo:'lejos'};
    if(mixed)return {valor:null,...base,motivo:'mezcla'};
    if(count<o.minPosiciones)return {valor:null,...base,motivo:'insuficiente'};
    // Dentro de la zona propia de la posición más cercana manda su valor medido (T46).
    const own=(o.propio||ownRadii(samples,o)).get(closest);
    if(own!==undefined&&nearest<=own)return {valor:Number(closest.valor),...base,motivo:null,propia:true};
    return {valor:sum/weight,...base,motivo:null};
  }
  /**
   * Texto de la consulta de un punto del mapa. Con valor indica que es interpolado; sin él, por qué no se asigna nivel.
   * info: {unidad, umbral_medio, umbral_alto, simulado} del grupo. Mismos textos que PublicReadings.describe.
   */
  /** Valor redondeado para el texto; con un decimal si al redondear cruzaría un umbral (59,6 no es «60 · bajo»). */
  function shown(v,info){const m=Number(info.umbral_medio),h=Number(info.umbral_alto),r=Math.round(v);
    return level(r,m,h)===level(v,m,h)?String(r):v.toFixed(1).replace('.',',');}
  function describe(e,radius,info={},options){
    const o={...INTERPOLACION,...options},positions=`${e.posiciones} ${e.posiciones===1?'posición':'posiciones'} en ${radius} m`;
    if(e.valor!==null)return {titulo:`Estimación: ${shown(e.valor,info)} ${info.unidad} · ${level(e.valor,Number(info.umbral_medio),Number(info.umbral_alto))}`,
      detalle:`${positions} · la más cercana a ${Math.round(e.cercana)} m. `+(e.propia?'Junto a esa medición se muestra su valor medido':'Valor interpolado, no medido')+(info.simulado?' · datos simulados.':'.')};
    if(e.motivo==='insuficiente')return {titulo:'Datos insuficientes',detalle:`${positions}; hacen falta ${o.minPosiciones}. No se asigna nivel de ozono.`};
    return {titulo:'Sin datos',detalle:(e.cercana===null?'No hay mediciones publicadas.':`La medición más cercana está a ${Math.round(e.cercana)} m (radio de ${radius} m).`)+' Sin datos no significa aire limpio.'};
  }
  /** Valor interpolado o null si el punto queda sin datos. Acepta lecturas en bruto. */
  function interpolate(point,rows,radius,options){return estimate(point,support(rows,options),radius,options).valor;}
  /** Estado de cada punto de raster(): 0 sin datos, 1 datos insuficientes, 2-4 nivel bajo, moderado o alto. */
  const ZONA=Object.freeze({sinDatos:0,insuficiente:1,bajo:2,moderado:3,alto:4});
  /** Índices [desde, hasta] de los valores de una lista ordenada (creciente o decreciente) que caen en [min, max]. */
  function span(values,min,max){
    const n=values.length,asc=n<2||values[0]<=values[n-1],at=i=>asc?values[i]:values[n-1-i];
    let lo=0,hi=n;while(lo<hi){const m=(lo+hi)>>1;if(at(m)<min)lo=m+1;else hi=m;}
    let a=lo;hi=n;while(lo<hi){const m=(lo+hi)>>1;if(at(m)<=max)lo=m+1;else hi=m;}
    const b=lo-1;return asc?[a,b]:[n-1-b,n-1-a];
  }
  /**
   * Rejilla para pintar las zonas sin celdas cuadradas: evalúa estimate() en el centro de cada punto de una
   * rejilla fina (la pantalla, cada pocos píxeles), así el borde de cada zona sigue el radio y sale redondo.
   * lats: latitud de cada fila; lngs: longitud de cada columna (ordenadas). groups: [{samples, radius,
   * umbral_medio, umbral_alto}], con samples ya reducidas por support(). Solo se evalúan los puntos cercanos
   * a alguna posición. Si varios grupos alcanzan un punto, sus estimaciones se combinan (T47) y un valor estimado
   * gana a «datos insuficientes».
   * Devuelve {filas, columnas, estados (Uint8Array con ZONA, fila a fila), evaluadas, recortado}.
   */
  function raster(groups,lats,lngs,options){
    const o={...INTERPOLACION,...options},R=lats.length,C=lngs.length,states=new Uint8Array(R*C),seen=new Uint32Array(R*C);
    // Acumulados por punto para combinar campañas que se solapan (T47): en vez de quedarse con la primera, la zona
    // común se interpola con las lecturas de todas (media de cada estimación ponderada por su peso IDW) y el nivel
    // se juzga con los umbrales de la campaña que más pesa en ese punto. Dentro de la zona propia de una posición
    // manda su valor medido, el de la posición más cercana si hay varias.
    const sumV=new Float64Array(R*C),sumW=new Float64Array(R*C),topW=new Float64Array(R*C),owner=new Int32Array(R*C).fill(-1);
    const exactV=new Float64Array(R*C),exactD=new Float64Array(R*C).fill(Infinity),exactG=new Int32Array(R*C).fill(-1),insufficient=new Uint8Array(R*C);
    let evaluated=0,clipped=false,pass=0;
    const bucketLat=o.radioMax*1.1/METROS_GRADO;
    scan:for(const [gi,g] of groups.entries()){
      const radius=Number(g.radius);pass++;
      if(!g.samples?.length||!Number.isFinite(radius)||radius<o.radioMin||radius>o.radioMax)continue;
      const propio=ownRadii(g.samples,o);
      // Cubos de ~radio máximo para comparar cada punto solo con las posiciones vecinas.
      const cos=Math.max(.01,Math.cos(Number(g.samples[0].latitud)*Math.PI/180)),bucketLon=bucketLat/cos,buckets=new Map();
      for(const s of g.samples){const k=`${Math.floor(s.latitud/bucketLat)}:${Math.floor(s.longitud/bucketLon)}`;if(!buckets.has(k))buckets.set(k,[]);buckets.get(k).push(s);}
      const dLat=radius/METROS_GRADO;
      for(const s of g.samples){
        const lat=Number(s.latitud),lng=Number(s.longitud),dLng=dLat/Math.max(.01,Math.cos(lat*Math.PI/180));
        const [r0,r1]=span(lats,lat-dLat,lat+dLat),[c0,c1]=span(lngs,lng-dLng,lng+dLng);
        for(let i=r0;i<=r1;i++)for(let j=c0;j<=c1;j++){
          const k=i*C+j;if(seen[k]===pass)continue;seen[k]=pass;
          // El tope de rendimiento se avisa: una zona recortada no debe confundirse con una zona sin datos.
          if(evaluated>=o.maxEvaluaciones){clipped=true;break scan;}
          evaluated++;
          const x=Math.floor(lats[i]/bucketLat),y=Math.floor(lngs[j]/bucketLon),near=[];
          for(let a=-1;a<=1;a++)for(let b=-1;b<=1;b++){const list=buckets.get(`${x+a}:${y+b}`);if(list)near.push(...list);}
          const e=estimate([lats[i],lngs[j]],near,radius,{...o,propio});
          if(e.valor!==null){
            if(e.propia){if(e.cercana<exactD[k]){exactD[k]=e.cercana;exactV[k]=e.valor;exactG[k]=gi;}}
            else{sumV[k]+=e.valor*e.peso;sumW[k]+=e.peso;if(e.peso>topW[k]){topW[k]=e.peso;owner[k]=gi;}}
          }
          else if(e.motivo==='insuficiente')insufficient[k]=1;
        }
      }
    }
    for(let k=0;k<states.length;k++){
      let value=null,g=null;
      if(exactG[k]>=0){value=exactV[k];g=groups[exactG[k]];}
      else if(sumW[k]>0){value=sumV[k]/sumW[k];g=groups[owner[k]];}
      if(value!==null)states[k]=ZONA[level(value,Number(g.umbral_medio),Number(g.umbral_alto))];
      else if(insufficient[k])states[k]=ZONA.insuficiente;
    }
    return {filas:R,columnas:C,estados:states,evaluadas:evaluated,recortado:clipped};
  }
  // Batería del AERIQ (mismos tramos que sus LED): alta > 60 %, media 20–60 %, baja < 20 %.
  // Sin dato no se inventa nada: «Sin datos» hasta que el AERIQ con medidor la envíe.
  // Fechas del servidor (UTC, «YYYY-MM-DD HH:MM:SS[.mmm]») → hora local legible, sin milisegundos ni «UTC».
  // hour:false → solo el día; seconds:true → hasta el segundo.
  const MESES=['ene','feb','mar','abr','may','jun','jul','ago','sep','oct','nov','dic'];
  function when(v,{hour=true,seconds=false}={}){
    if(!v)return '';const text=String(v).trim().replace(' ','T');
    const d=new Date(/^\d{4}-\d{2}-\d{2}$/.test(text)?text+'T12:00:00Z':/(Z|[+-]\d{2}:?\d{2})$/.test(text)?text:text+'Z');
    if(Number.isNaN(d.getTime()))return String(v);
    const p=n=>String(n).padStart(2,'0');let out=`${d.getDate()} ${MESES[d.getMonth()]} ${d.getFullYear()}`;
    if(hour&&!/^\d{4}-\d{2}-\d{2}$/.test(text))out+=`, ${p(d.getHours())}:${p(d.getMinutes())}`+(seconds?':'+p(d.getSeconds()):'');
    return out;
  }
  function battery(percent,at){
    if(percent===null||percent===undefined||percent===''||!Number.isFinite(Number(percent)))return 'Sin datos';
    const p=Number(percent),tier=p>60?'alta':p>=20?'media':'baja';
    return `${p} % · ${tier}`+(at?` · ${when(at)}`:'');
  }
  /**
   * Ruta de un AERIQ: sus posiciones en orden de captura, partida en tramos cuando pasan más de
   * maxHueco minutos entre dos lecturas (un descanso o un día distinto no se une con una línea recta).
   * rows: lecturas con latitud, longitud y capturada_en (UTC). Devuelve una lista de tramos de lecturas.
   */
  function route(rows,maxHueco=10){
    const time=r=>Date.parse(String(r.capturada_en??'').replace(' ','T')+'Z');
    const valid=rows.filter(r=>[r.latitud,r.longitud].every(v=>v!==null&&v!==''&&Number.isFinite(Number(v)))&&Number.isFinite(time(r)));
    const sorted=[...valid].sort((a,b)=>time(a)-time(b)||Number(a.id??0)-Number(b.id??0)),out=[];
    let last=null;
    for(const r of sorted){
      if(!last||time(r)-time(last)>maxHueco*60000)out.push([]);
      out[out.length-1].push(r);last=r;
    }
    return out;
  }
  const api={level,csv,statistics,autoGroup,peaks,verdict,FRANJAS,sharedThresholds,distance,INTERPOLACION,groupKey,support,ownRadii,estimate,describe,interpolate,ZONA,raster,route,battery,when};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.AeriqDomain=api;
})(typeof globalThis!=='undefined'?globalThis:this);
