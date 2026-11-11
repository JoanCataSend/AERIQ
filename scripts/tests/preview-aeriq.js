/* Servidor exclusivamente de revisión visual con respuestas ficticias. No ejecuta PHP. */
'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../src/web');
const campaign={id:1,nombre:'Campaña de revisión visual',zona:'UPV Gandía',inicio:'2026-11-01',fin:'2026-12-01',activa:1,publicar:1,radio_m:30,umbral_medio:60,umbral_alto:100};
const worker={id:2,nombre:'Persona',apellidos:'de prueba',email:'trabajador@example.test',rol:'trabajador',rol_nombre:'Trabajador',puede_medir:1,activo:1,cambiar_clave:1};
const device={id:1,nombre:'Orb de prueba',direccion_ble:'AA:BB:CC:DD:EE:01',trabajador_id:2,trabajador:worker.email,campana_id:1,campana:campaign.nombre,activo:1,simulado:1,ultima_medicion:'2026-11-02 12:00:00'};
const rows=[0,1,2].map(i=>({id:i+1,campaignId:1,valor:50+i*30,unidad:'ppb',capturada_en:'2026-11-02 12:00:00.000',latitud:38.99665+(i===1?.00005:0),longitud:-.166+(i===2?.00005:0),simulado:true,zona:campaign.zona,radio_m:30,umbral_medio:60,umbral_alto:100,dispositivo:device.nombre,campana:campaign.nombre}));
// Catálogos del formulario comercial (mismos códigos que schema.sql).
const catalogs={services:[{codigo:'kits',nombre:'Comprar Orb para mi organización',descripcion:'Sensores propios con app y panel. Te recomendamos cuántos necesitas.',mide_superficie:false},{codigo:'estudio',nombre:'Contratar un estudio de ozono',descripcion:'Medimos nosotros la zona durante un tiempo, sin comprar sensores, y entregamos un informe.',mide_superficie:true},{codigo:'asesoramiento',nombre:'No lo tengo claro, aconsejadme',descripcion:'Cuéntanos tu caso y te proponemos la opción que mejor encaja.',mide_superficie:false}],
 orgTypes:[{codigo:'administracion',nombre:'Administración pública'},{codigo:'empresa',nombre:'Empresa'},{codigo:'otra',nombre:'Otra entidad (centro educativo, asociación…)'}],
 objectives:[{codigo:'espacios_publicos',nombre:'Calidad del aire en espacios públicos'},{codigo:'salud_laboral',nombre:'Salud y seguridad laboral'},{codigo:'normativa',nombre:'Informe técnico o normativa'},{codigo:'investigacion',nombre:'Investigación o divulgación'},{codigo:'otro',nombre:'Otro'}],
 provinces:['Alicante','Castellón','Madrid','Valencia'],durations:['1 semana','2 semanas','1 mes','3 meses','Más de 3 meses','A definir con Código Norte']};
const server=http.createServer((req,res)=>{
  const u=new URL(req.url,'http://localhost');let data;
  if(u.pathname==='/auth.php')data={ok:true,csrf:'visual-test',user:{id:1,name:'Revisión',last:'visual',role:'admin',roleName:'Administrador',permissions:['informes.ver','mapa.organizacion','mediciones.registrar','organizacion.gestionar'],organization:'Organización de prueba · revisión visual',mustChangePassword:false,email:'admin@example.test'}};
  else if(u.pathname==='/portal.php')data=u.searchParams.get('action')==='readings'?{readings:rows}:{workers:[worker],campaigns:[campaign],devices:[device],zones:[{id:1,nombre:campaign.zona}],roles:[{codigo:'trabajador',nombre:'Trabajador',descripcion:'Recoge mediciones con los Orb que tiene asignados.'},{codigo:'analista',nombre:'Analista',descripcion:'Consulta informes y exporta los datos de su organización.'}],thresholds:{umbral_medio:60,umbral_alto:100}};
  else if(u.pathname==='/public.php'&&req.method==='POST')data={ok:true,id:42,message:'Solicitud registrada (revisión visual, no se guarda).'};
  else if(u.pathname==='/public.php'&&u.searchParams.get('action')==='services')data=catalogs;
  else if(u.pathname==='/public.php')data={measurements:u.searchParams.get('mode')==='simulated'?rows:[],mode:u.searchParams.get('mode'),truncated:false};
  if(data){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(data));return;}
  const file=path.resolve(root,'.'+decodeURIComponent(u.pathname==='/'?'/index.html':u.pathname));
  if(!file.startsWith(root+path.sep)||!['.html','.css','.js','.png','.jpg','.svg','.webp','.glb'].includes(path.extname(file))){res.writeHead(404);res.end();return;}
  fs.stat(file,(error,stat)=>{if(error||!stat.isFile()){res.writeHead(404);res.end();return;}const type={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.svg':'image/svg+xml','.png':'image/png','.webp':'image/webp','.glb':'model/gltf-binary'}[path.extname(file)]||'application/octet-stream';res.writeHead(200,{'Content-Type':type});fs.createReadStream(file).pipe(res);});
});
server.listen(8766,'127.0.0.1',()=>console.log('Revisión visual con datos ficticios: http://127.0.0.1:8766'));
