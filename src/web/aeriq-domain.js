/* Cálculos compartidos sin DOM: informes, CSV e interpolación local IDW. */
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
  function interpolate(point,rows,radius){
    if(!Number.isFinite(radius)||radius<20||radius>50)return null;
    const near=rows.map(r=>({r,d:distance(point,[Number(r.latitud),Number(r.longitud)])})).filter(x=>x.d<=radius);
    // Tres ubicaciones distintas; anuncios repetidos en un punto no crean cobertura.
    const unique=new Map();for(const x of near){const k=`${x.r.latitud},${x.r.longitud}`;if(!unique.has(k))unique.set(k,x);}
    const points=[...unique.values()];if(points.length<3)return null;
    const first=points[0].r;
    if(points.some(x=>x.r.unidad!==first.unidad||String(x.r.campaignId)!==String(first.campaignId)||Boolean(x.r.simulado)!==Boolean(first.simulado)))return null;
    let sum=0,weight=0;for(const x of points){const w=1/Math.max(1,x.d)**2;sum+=Number(x.r.valor)*w;weight+=w;}
    return sum/weight;
  }
  const api={level,csv,statistics,distance,interpolate};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.AeriqDomain=api;
})(typeof globalThis!=='undefined'?globalThis:this);
