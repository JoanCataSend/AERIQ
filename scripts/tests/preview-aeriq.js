/* Servidor exclusivamente de revisión visual con respuestas ficticias. No ejecuta PHP. */
'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../src/web');
const campaign={id:1,nombre:'Campaña de revisión visual',zona:'UPV Gandía',inicio:'2026-11-01',fin:'2026-12-01',activa:1,publicar:1,radio_m:30,umbral_medio:60,umbral_alto:100};
const worker={id:2,nombre:'Persona',apellidos:'de prueba',email:'trabajador@example.test',rol:'trabajador',rol_nombre:'Trabajador',puede_medir:1,activo:1,cambiar_clave:1};
const device={id:1,nombre:'Orb de prueba',direccion_ble:'AA:BB:CC:DD:EE:01',trabajador_id:2,trabajador:worker.email,campana_id:1,campana:campaign.nombre,activo:1,simulado:1,ultima_medicion:'2026-11-02 12:00:00'};
// Flota variada para revisar paneles, filtros y orden (T43-T45): uno recibiendo ahora, uno sin uso hace meses y uno de baja.
const utcAgo=ms=>new Date(Date.now()-ms).toISOString().slice(0,19).replace('T',' ');
const fleet=[device,{...device,id:2,nombre:'AERIQ Playa',direccion_ble:'AA:BB:CC:DD:EE:02',trabajador_id:3,trabajador:'Lucía Operadora',bateria_pct:82,bateria_en:utcAgo(60000),ultima_medicion:utcAgo(60000),ultima_recepcion:utcAgo(60000)},
  {...device,id:3,nombre:'AERIQ Almacén',direccion_ble:'AA:BB:CC:DD:EE:03',trabajador_id:null,trabajador:null,ultima_medicion:'2026-08-14 09:30:00',bateria_pct:12,bateria_en:'2026-08-14 09:30:00'},
  {...device,id:4,nombre:'AERIQ Antiguo',direccion_ble:'AA:BB:CC:DD:EE:04',activo:0,trabajador_id:null,trabajador:null,ultima_medicion:null}];
// Informe (T39-T41): 24 lecturas horarias del perfil «malo» de 2-simulacion.sql, para ver la gráfica, los picos y el veredicto.
const MALO=[66,62,60,58,61,65,84,94,102,110,116,121,126,131,134,129,120,112,104,98,92,86,78,72];
const report=Array.from({length:24},(_,k)=>{const d=new Date();d.setMinutes(5,0,0);d.setHours(d.getHours()-k);
  return {id:100+k,campaignId:1,valor:MALO[d.getHours()],unidad:'ppb',capturada_en:d.toISOString().slice(0,19).replace('T',' ')+'.000',latitud:38.996651,longitud:-.166009,simulado:true,zona:campaign.zona,radio_m:30,umbral_medio:60,umbral_alto:100,campana:campaign.nombre,dispositivo:device.nombre};});
// Posiciones ficticias para revisar la interpolación (T25): un grupo denso de 5 × 3 a ~11 m (zona coloreada),
// dos posiciones próximas y una aislada (datos insuficientes, en gris). El resto del mapa queda sin datos.
const fake=(id,latitud,longitud,valor)=>({id,campaignId:1,valor,unidad:'ppb',capturada_en:'2026-11-02 12:00:00.000',latitud,longitud,simulado:true,zona:campaign.zona,radio_m:30,umbral_medio:60,umbral_alto:100,campana:campaign.nombre});
const rows=[38,42,47,51,56,61,66,72,78,84,91,97,103,108,114].map((valor,n)=>fake(n+1,38.996651+Math.floor(n/5)*.0001,-.166009+(n%5)*.00013,valor))
  .concat([fake(16,38.9975,-.1664,58),fake(17,38.9976,-.1664,64),fake(18,38.9962,-.1648,70)]);
// «Mi organización»: las mismas posiciones con su Orb y una hora por lectura, para revisar la ruta.
// El grupo denso se recorre en zigzag cada minuto; las tres sueltas llegan tras un hueco de 30 min (otro tramo).
const zigzag=[0,1,2,3,4,9,8,7,6,5,10,11,12,13,14];
const at=minutes=>`2026-11-02 ${String(12+Math.floor(minutes/60)).padStart(2,'0')}:${String(minutes%60).padStart(2,'0')}:00.000`;
const mine=rows.map((r,n)=>({...r,dispositivo_id:device.id,dispositivo:device.nombre,capturada_en:at(n<15?zigzag.indexOf(n):45+n-15)}));
// Catálogos del formulario comercial (mismos códigos que src/database/1-instalar.sql).
const catalogs={services:[{codigo:'kits',nombre:'Comprar Orb para mi organización',descripcion:'Sensores propios con app y panel. Te recomendamos cuántos necesitas.',mide_superficie:false},{codigo:'estudio',nombre:'Contratar un estudio de ozono',descripcion:'Medimos nosotros la zona durante un tiempo, sin comprar sensores, y entregamos un informe.',mide_superficie:true},{codigo:'asesoramiento',nombre:'No lo tengo claro, aconsejadme',descripcion:'Cuéntanos tu caso y te proponemos la opción que mejor encaja.',mide_superficie:false}],
 orgTypes:[{codigo:'administracion',nombre:'Administración pública'},{codigo:'empresa',nombre:'Empresa'},{codigo:'otra',nombre:'Otra entidad (centro educativo, asociación…)'}],
 objectives:[{codigo:'espacios_publicos',nombre:'Calidad del aire en espacios públicos'},{codigo:'salud_laboral',nombre:'Salud y seguridad laboral'},{codigo:'normativa',nombre:'Informe técnico o normativa'},{codigo:'investigacion',nombre:'Investigación o divulgación'},{codigo:'otro',nombre:'Otro'}],
 provinces:['Alicante','Castellón','Madrid','Valencia'],durations:['1 semana','2 semanas','1 mes','3 meses','Más de 3 meses','A definir con Código Norte']};
const server=http.createServer((req,res)=>{
  const u=new URL(req.url,'http://localhost');let data;
  if(u.pathname==='/auth.php')data={ok:true,csrf:'visual-test',user:{id:1,name:'Revisión',last:'visual',role:'admin',roleName:'Administrador',permissions:['informes.ver','mapa.organizacion','equipo.gestionar','datos.exportar'],organization:'Organización de prueba · revisión visual',organizationId:1,mustChangePassword:false,email:'admin@example.test'}};
  else if(u.pathname==='/portal.php'&&u.searchParams.get('action')==='map')data={measurements:u.searchParams.get('mode')==='simulated'?mine:[],mode:u.searchParams.get('mode'),truncated:false};
  else if(u.pathname==='/portal.php')data=u.searchParams.get('action')==='readings'?{readings:report}:{workers:[worker],campaigns:[campaign],devices:fleet,zones:[{id:1,nombre:campaign.zona}],roles:[{codigo:'trabajador',nombre:'Trabajador',descripcion:'Recoge mediciones con los Orb que tiene asignados.'},{codigo:'analista',nombre:'Analista',descripcion:'Consulta informes y exporta los datos de su organización.'}],thresholds:{umbral_medio:60,umbral_alto:100}};
  else if(u.pathname==='/public.php'&&req.method==='POST')data={ok:true,id:42,message:'Solicitud registrada (revisión visual, no se guarda).'};
  else if(u.pathname==='/public.php'&&u.searchParams.get('action')==='services')data=catalogs;
  else if(u.pathname==='/public.php')data={measurements:u.searchParams.get('mode')==='simulated'?rows:[],mode:u.searchParams.get('mode'),truncated:false};
  if(data){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(data));return;}
  const file=path.resolve(root,'.'+decodeURIComponent(u.pathname==='/'?'/index.html':u.pathname));
  if(!file.startsWith(root+path.sep)||!['.html','.css','.js','.png','.jpg','.svg','.webp','.glb'].includes(path.extname(file))){res.writeHead(404);res.end();return;}
  fs.stat(file,(error,stat)=>{if(error||!stat.isFile()){res.writeHead(404);res.end();return;}const type={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.svg':'image/svg+xml','.png':'image/png','.webp':'image/webp','.glb':'model/gltf-binary'}[path.extname(file)]||'application/octet-stream';res.writeHead(200,{'Content-Type':type});fs.createReadStream(file).pipe(res);});
});
server.listen(8766,'127.0.0.1',()=>console.log('Revisión visual con datos ficticios: http://127.0.0.1:8766'));
