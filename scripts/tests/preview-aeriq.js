/* Servidor exclusivamente de revisión visual con respuestas ficticias. No ejecuta PHP. */
'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'../../src/web');
const campaign={id:1,nombre:'Campaña de revisión visual',zona:'UPV Gandía',inicio:'2026-11-01',fin:'2026-12-01',activa:1,publicar:1,radio_m:30,umbral_medio:60,umbral_alto:100};
const worker={id:2,nombre:'Persona',apellidos:'de prueba',email:'trabajador@example.test',rol:'trabajador',activo:1,cambiar_clave:1};
const device={id:1,nombre:'Orb de prueba',direccion_ble:'AA:BB:CC:DD:EE:01',trabajador_id:2,trabajador:worker.email,campana_id:1,campana:campaign.nombre,activo:1,simulado:1,ultima_medicion:'2026-11-02 12:00:00'};
const rows=[0,1,2].map(i=>({id:i+1,campaignId:1,valor:50+i*30,unidad:'ppb',capturada_en:'2026-11-02 12:00:00.000',latitud:38.99665+(i===1?.00005:0),longitud:-.166+(i===2?.00005:0),simulado:true,zona:campaign.zona,radio_m:30,umbral_medio:60,umbral_alto:100,dispositivo:device.nombre,campana:campaign.nombre}));
const server=http.createServer((req,res)=>{
  const u=new URL(req.url,'http://localhost');let data;
  if(u.pathname==='/auth.php')data={ok:true,csrf:'visual-test',user:{id:1,name:'Revisión',last:'visual',role:'admin',organization:'Organización de prueba · revisión visual',mustChangePassword:false,email:'admin@example.test'}};
  else if(u.pathname==='/portal.php')data=u.searchParams.get('action')==='readings'?{readings:rows}:{workers:[worker],campaigns:[campaign],devices:[device]};
  else if(u.pathname==='/public.php')data={measurements:u.searchParams.get('mode')==='simulated'?rows:[],mode:u.searchParams.get('mode'),truncated:false};
  if(data){res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(data));return;}
  const file=path.resolve(root,'.'+decodeURIComponent(u.pathname==='/'?'/index.html':u.pathname));
  if(!file.startsWith(root+path.sep)||!['.html','.css','.js','.png','.jpg','.svg','.webp','.glb'].includes(path.extname(file))){res.writeHead(404);res.end();return;}
  fs.stat(file,(error,stat)=>{if(error||!stat.isFile()){res.writeHead(404);res.end();return;}const type={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.svg':'image/svg+xml','.png':'image/png','.webp':'image/webp','.glb':'model/gltf-binary'}[path.extname(file)]||'application/octet-stream';res.writeHead(200,{'Content-Type':type});fs.createReadStream(file).pipe(res);});
});
server.listen(8766,'127.0.0.1',()=>console.log('Revisión visual con datos ficticios: http://127.0.0.1:8766'));
