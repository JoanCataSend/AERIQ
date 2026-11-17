/* Cálculos compartidos sin DOM: informes, CSV e interpolación local IDW acotada. */
(function(root){
  'use strict';
  const level=(v,medium,high)=>v>=high?'alto':v>=medium?'moderado':'bajo';
  function csvCell(v){const text=String(v??'');return '"'+(typeof v!=='number'&&/^[\s]*[=+@-]/.test(text)?"'"+text:text).replaceAll('"','""')+'"';}
  function csv(rows){
    const keys=['capturada_en','valor','unidad','dispositivo','campana','zona','latitud','longitud','simulado'];
    return '\ufeff'+keys.join(';')+'\r\n'+rows.map(r=>keys.map(k=>{let v=r[k];if(['valor','latitud','longitud','simulado'].includes(k)&&v!==null&&v!==undefined&&v!==''&&Number.isFinite(Number(v)))v=Number(v);return csvCell(v);}).join(';')).join('\r\n');
  }
  function statistics(rows,group='day'){
    if(!rows.length)return {count:0,min:null,max:null,mean:null,series:[],exceedances:[]};
    const buckets=new Map(),values=rows.map(r=>Number(r.valor));
    for(const r of rows){
      const date=new Date(r.capturada_en.replace(' ','T')+'Z');
      if(group==='week')date.setUTCDate(date.getUTCDate()-((date.getUTCDay()+6)%7));
      const key=date.toISOString().slice(0,10);if(!buckets.has(key))buckets.set(key,[]);buckets.get(key).push(Number(r.valor));
    }
    return {count:rows.length,min:Math.min(...values),max:Math.max(...values),mean:values.reduce((a,b)=>a+b,0)/values.length,
      series:[...buckets].sort(([a],[b])=>a.localeCompare(b)).map(([date,v])=>({date,mean:v.reduce((a,b)=>a+b,0)/v.length,min:Math.min(...v),max:Math.max(...v)})),
      exceedances:rows.filter(r=>r.unidad!=='°C'&&r.umbral_medio!==null&&r.umbral_medio!==undefined&&Number(r.valor)>=Number(r.umbral_medio))};
  }
  function distance(a,b){const rad=Math.PI/180;const dlat=(a[0]-b[0])*rad,dlon=(a[1]-b[1])*rad;const h=Math.sin(dlat/2)**2+Math.cos(a[0]*rad)*Math.cos(b[0]*rad)*Math.sin(dlon/2)**2;return 6371000*2*Math.asin(Math.min(1,Math.sqrt(h)));}
  // Interpolación IDW local acotada (T25/HU28). Mismos parámetros y pasos que PublicReadings.java; ver doc/interpolacion.md.
  const INTERPOLACION=Object.freeze({
    radioMin:20,radioMax:50, // rango admitido del radio (m); fuera de él no se estima
    radio:30,                // radio por defecto; cada campaña guarda el suyo en campanas.radio_m
    minPosiciones:3,         // posiciones de soporte distintas exigidas dentro del radio
    separacionMin:5,         // lecturas más juntas (m) cuentan como una sola posición: deriva del GPS
    potencia:2,              // exponente del peso 1/d^p
    distanciaMin:1,          // suelo de distancia (m) para no dividir entre cero
    celda:10                 // lado de la celda de pintado (m)
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
   * Estimación en un punto a partir de posiciones de soporte (resultado de support).
   * motivo: null (hay valor) · 'radio' (fuera de 20–50 m) · 'lejos' (ninguna posición en el radio: sin datos)
   * · 'insuficiente' (menos de minPosiciones) · 'mezcla' (campañas, unidades u orígenes distintos).
   */
  function estimate(point,samples,radius,options){
    const o={...INTERPOLACION,...options};
    if(!Number.isFinite(radius)||radius<o.radioMin||radius>o.radioMax)return {valor:null,posiciones:0,cercana:null,motivo:'radio'};
    let first=null,mixed=false,sum=0,weight=0,count=0,nearest=Infinity;
    for(const r of samples){
      const d=distance(point,[Number(r.latitud),Number(r.longitud)]);if(d<nearest)nearest=d;if(d>radius)continue;
      if(!first)first=r;else if(groupKey(r)!==groupKey(first))mixed=true;
      const w=1/Math.max(o.distanciaMin,d)**o.potencia;sum+=Number(r.valor)*w;weight+=w;count++;
    }
    const base={posiciones:count,cercana:Number.isFinite(nearest)?nearest:null};
    if(!count)return {valor:null,...base,motivo:'lejos'};
    if(mixed)return {valor:null,...base,motivo:'mezcla'};
    if(count<o.minPosiciones)return {valor:null,...base,motivo:'insuficiente'};
    return {valor:sum/weight,...base,motivo:null};
  }
  /**
   * Texto de la consulta de un punto del mapa. Con valor indica que es interpolado; sin él, por qué no se asigna nivel.
   * info: {unidad, umbral_medio, umbral_alto, simulado} del grupo. Mismos textos que PublicReadings.describe.
   */
  function describe(e,radius,info={},options){
    const o={...INTERPOLACION,...options},positions=`${e.posiciones} ${e.posiciones===1?'posición':'posiciones'} en ${radius} m`;
    if(e.valor!==null)return {titulo:`Estimación: ${Math.round(e.valor)} ${info.unidad} · ${level(e.valor,Number(info.umbral_medio),Number(info.umbral_alto))}`,
      detalle:`${positions} · la más cercana a ${Math.round(e.cercana)} m. Valor interpolado, no medido${info.simulado?' · datos simulados.':'.'}`};
    if(e.motivo==='insuficiente')return {titulo:'Datos insuficientes',detalle:`${positions}; hacen falta ${o.minPosiciones}. No se asigna nivel de ozono.`};
    return {titulo:'Sin datos',detalle:(e.cercana===null?'No hay mediciones publicadas.':`La medición más cercana está a ${Math.round(e.cercana)} m (radio de ${radius} m).`)+' Sin datos no significa aire limpio.'};
  }
  /** Valor interpolado o null si el punto queda sin datos. Acepta lecturas en bruto. */
  function interpolate(point,rows,radius,options){return estimate(point,support(rows,options),radius,options).valor;}
  // Batería del Orb (mismos tramos que sus LED): alta > 60 %, media 20–60 %, baja < 20 %.
  // Sin dato no se inventa nada: «Sin datos» hasta que el Orb con medidor la envíe.
  function battery(percent,at){
    if(percent===null||percent===undefined||percent===''||!Number.isFinite(Number(percent)))return 'Sin datos';
    const p=Number(percent),tier=p>60?'alta':p>=20?'media':'baja';
    return `${p} % · ${tier}`+(at?` · ${String(at).slice(0,16)} UTC`:'');
  }
  const api={level,csv,statistics,distance,INTERPOLACION,groupKey,support,estimate,describe,interpolate,battery};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.AeriqDomain=api;
})(typeof globalThis!=='undefined'?globalThis:this);
