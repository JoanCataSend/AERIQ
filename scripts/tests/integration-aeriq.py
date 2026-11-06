"""Integración aislada de PHP/MariaDB. No usa configuraciones ni correo de Plesk."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT=Path(__file__).resolve().parents[2]

def port():
    with socket.socket() as s:
        s.bind(('127.0.0.1',0));return s.getsockname()[1]

def main():
    args=argparse.ArgumentParser(description=__doc__)
    args.add_argument('--php',default='C:/xampp/php/php.exe')
    args.add_argument('--mysql-bin',default='C:/xampp/mysql/bin')
    opt=args.parse_args()
    folder=ROOT/'.local-backups'/('integration-'+uuid.uuid4().hex[:10]);folder.mkdir(parents=True)
    web=folder/'web';(web/'server').mkdir(parents=True);(web/'vendor').mkdir()
    for p in (ROOT/'src/web').glob('*.php'):shutil.copy2(p,web/p.name)
    for p in (ROOT/'src/web/server').glob('*.php'):
        if p.name not in {'SDBaseDatos.php','SMTPConfig.php','MailConfig.php'}:shutil.copy2(p,web/'server'/p.name)
    dbport,httpport=port(),port();binary=Path(opt.mysql_bin)
    flags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0
    subprocess.run([str(binary/'mysql_install_db.exe'),f'--datadir={folder / "db"}',f'--port={dbport}','--silent'],check=True,capture_output=True,creationflags=flags)
    mysql=[str(binary/'mysql.exe'),'--no-defaults','--default-character-set=utf8mb4','--host=127.0.0.1',f'--port={dbport}','--user=root','--batch','--skip-column-names']
    def sql(text):
        r=subprocess.run(mysql,input=text,text=True,encoding='utf-8',errors='replace',capture_output=True,creationflags=flags)
        if r.returncode:raise RuntimeError(r.stderr)
        return r.stdout.strip()
    log=(folder/'database.log').open('w')
    db=subprocess.Popen([str(binary/'mysqld.exe'),'--no-defaults',f'--basedir={binary.parent}',f'--datadir={folder / "db"}',f'--port={dbport}','--bind-address=127.0.0.1','--skip-name-resolve','--innodb-buffer-pool-size=32M',f'--tmpdir={folder}'],stdout=log,stderr=log,creationflags=flags)
    php=None
    try:
        deadline=time.monotonic()+25
        while True:
            try:sql('SELECT 1');break
            except RuntimeError:
                if time.monotonic()>deadline:raise RuntimeError('MariaDB aislada no ha arrancado')
                time.sleep(.2)
        sql('CREATE DATABASE aeriq_test CHARACTER SET utf8mb4; USE aeriq_test;'+(ROOT/'src/database/schema.sql').read_text(encoding='utf-8'))
        password='Integration-Only-2026!'
        hashed=subprocess.check_output([opt.php,'-r',f'echo password_hash("{password}", PASSWORD_DEFAULT);'],text=True).strip()
        sql(f"USE aeriq_test; INSERT INTO organizaciones(nombre) VALUES('Org A'),('Org B'); INSERT INTO usuarios(nombre,apellidos,email,password_hash,verificado_en) VALUES('Admin','A','admin-a@example.test','{hashed}',NOW()),('Admin','B','admin-b@example.test','{hashed}',NOW()),('Comercial','CN','sales@example.test','{hashed}',NOW()); UPDATE usuarios SET organizacion_id=1,rol='admin' WHERE id=1; UPDATE usuarios SET organizacion_id=2,rol='admin' WHERE id=2; UPDATE usuarios SET rol='comercial' WHERE id=3;")
        # Una instalación nueva puede recrearse sin depender de las tablas de Plesk.
        sql('CREATE DATABASE aeriq_demo_test; USE aeriq_demo_test;'+(ROOT/'src/database/schema.sql').read_text()+(ROOT/'src/database/seed-demo.sql').read_text())
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM usuarios')=='6'
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones')=='9'
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones WHERE simulado=0')=='0'
        # SQL completo de phpMyAdmin: elimina incluso tablas/vistas heredadas y se puede repetir.
        sql('USE aeriq_demo_test; CREATE TABLE tabla_antigua(id INT); CREATE VIEW vista_antigua AS SELECT * FROM tabla_antigua;')
        for _ in range(2):
            sql('USE aeriq_demo_test;'+(ROOT/'src/database/recrear-demo-plesk.sql').read_text(encoding='utf-8'))
            assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM usuarios')=='6'
            assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones')=='9'
            assert sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='aeriq_demo_test' AND TABLE_NAME IN ('tabla_antigua','vista_antigua')")=='0'
        reset=[os.sys.executable,'-X','utf8',str(ROOT/'scripts/reset-database.py'),'--database','aeriq_demo_test','--host','127.0.0.1','--port',str(dbport),'--user','root','--mysql-bin',str(binary),'--demo']
        assert subprocess.run(reset+['--dry-run'],capture_output=True).returncode==0
        assert subprocess.run(reset,capture_output=True).returncode!=0
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones')=='9'
        recreated=subprocess.run(reset+['--confirm','RECREAR:aeriq_demo_test'],env={**os.environ,'AERIQ_DB_PASSWORD':''},capture_output=True,text=True)
        assert recreated.returncode==0,recreated.stderr
        assert 'Copia previa:' in recreated.stdout
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones')=='9'
        # Migración de un Sprint 0 mínimo: conserva valores y no inventa GPS ni MAC.
        sql('CREATE DATABASE aeriq_cleanup_test; USE aeriq_cleanup_test;'+(ROOT/'src/database/schema.sql').read_text()+(ROOT/'src/database/seed-demo.sql').read_text())
        sql(f"USE aeriq_cleanup_test; INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,simulado) SELECT '{uuid.uuid4()}',id,14,73,UTC_TIMESTAMP(3),0 FROM asignaciones ORDER BY id LIMIT 1")
        accounts_before=sql('USE aeriq_cleanup_test; SELECT id,email,password_hash,organizacion_id FROM usuarios ORDER BY id')
        orgs_before=sql('USE aeriq_cleanup_test; SELECT * FROM organizaciones ORDER BY id')
        for _ in range(2):
            sql('USE aeriq_cleanup_test;'+(ROOT/'src/database/limpiar-simulados-y-sensores.sql').read_text(encoding='utf-8'))
            assert sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM mediciones')=='0'
            assert sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM dispositivos')=='0'
            assert sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM asignaciones')=='0'
            assert sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM archivo_sensores_mediciones WHERE simulado=0 AND valor=73')=='1'
            assert accounts_before==sql('USE aeriq_cleanup_test; SELECT id,email,password_hash,organizacion_id FROM usuarios ORDER BY id')
            assert orgs_before==sql('USE aeriq_cleanup_test; SELECT * FROM organizaciones ORDER BY id')
        sql('CREATE DATABASE jcatsen_pbio; USE jcatsen_pbio; CREATE TABLE AeriqUsuario(id INT PRIMARY KEY); CREATE TABLE AeriqCodigoEmail(id INT, FOREIGN KEY(id) REFERENCES AeriqUsuario(id)); CREATE TABLE AeriqSesionMovil(id INT, FOREIGN KEY(id) REFERENCES AeriqUsuario(id)); CREATE TABLE Dispositivo(id INT PRIMARY KEY); CREATE TABLE TipoMedida(id INT PRIMARY KEY); CREATE TABLE Medida(id INT, dispositivo_id INT, tipo INT, FOREIGN KEY(dispositivo_id) REFERENCES Dispositivo(id), FOREIGN KEY(tipo) REFERENCES TipoMedida(id)); INSERT INTO Medida(id) VALUES(1);')
        for _ in range(2):
            sql((ROOT/'src/database/recrear-jcatsen-pbio.sql').read_text(encoding='utf-8'))
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.usuarios')=='6'
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.mediciones')=='9'
            assert sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='jcatsen_pbio' AND TABLE_NAME='Medida'")=='0'
        for _ in range(2):
            sql((ROOT/'src/database/anadir-mediciones-publicas-privadas.sql').read_text(encoding='utf-8'))
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.mediciones')=='45'
            assert sql("USE jcatsen_pbio; SELECT COUNT(*) FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN campanas c ON c.id=a.campana_id WHERE m.evento LIKE 'ae100000-%' AND c.publicar=1")=='12'
            assert sql("USE jcatsen_pbio; SELECT COUNT(*) FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN campanas c ON c.id=a.campana_id WHERE m.evento LIKE 'ae100000-%' AND c.publicar=0")=='24'
        for _ in range(2):
            sql((ROOT/'src/database/anadir-mediciones-playa-gandia.sql').read_text(encoding='utf-8'))
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.mediciones')=='99'
            assert sql("USE jcatsen_pbio; SELECT SUM(valor<60),SUM(valor>=60 AND valor<100),SUM(valor>=100) FROM mediciones WHERE evento LIKE 'ae200000-%'")=='18\t18\t18'
            assert sql("USE jcatsen_pbio; SELECT COUNT(*) FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN campanas c ON c.id=a.campana_id WHERE m.evento LIKE 'ae200000-%' AND c.publicar=1 AND m.simulado=1")=='54'
        sql("CREATE DATABASE aeriq_old_test; USE aeriq_old_test; CREATE TABLE AeriqUsuario(id BIGINT PRIMARY KEY,nombre VARCHAR(90),apellidos VARCHAR(120),email VARCHAR(254),telefono VARCHAR(40),password_hash VARCHAR(255),verificado_en DATETIME); CREATE TABLE Dispositivo(dispositivoId VARCHAR(16),nombre VARCHAR(100)); CREATE TABLE TipoMedida(tipoMedidaId INT,nombre VARCHAR(30),unidad VARCHAR(16)); CREATE TABLE Medida(id INT,dispositivoId VARCHAR(16),tipoMedidaId INT,valor INT,fechaHora DATETIME); INSERT INTO Dispositivo VALUES('EPSG-GTI-PROY-3A','Orb de Sprint 0'); INSERT INTO TipoMedida VALUES(14,'Ozono','ppb'),(12,'Temperatura','°C'); INSERT INTO Medida VALUES(1,'EPSG-GTI-PROY-3A',14,123,UTC_TIMESTAMP()),(2,'EPSG-GTI-PROY-3A',12,-12,UTC_TIMESTAMP());")
        sql('CREATE DATABASE aeriq_import_test; USE aeriq_import_test;'+(ROOT/'src/database/schema.sql').read_text())
        configs=[]
        for database in ['aeriq_old_test','aeriq_import_test']:
            cfg=folder/(database+'.php');cfg.write_text(f"<?php return ['host'=>'127.0.0.1','port'=>{dbport},'user'=>'root','password'=>'','database'=>'{database}'];")
            configs.append(str(cfg))
        migrate=[opt.php,str(ROOT/'scripts/migrate-legacy.php'),'--source='+configs[0],'--target='+configs[1]]
        assert subprocess.run(migrate,capture_output=True).returncode==0
        assert sql('USE aeriq_import_test; SELECT COUNT(*) FROM mediciones')=='0'
        imported=subprocess.run(migrate+['--apply'],capture_output=True,text=True)
        assert imported.returncode==0,imported.stderr
        assert sql('USE aeriq_import_test; SELECT GROUP_CONCAT(valor ORDER BY tipo) FROM mediciones')=='-12,123'
        assert sql('USE aeriq_import_test; SELECT COUNT(*) FROM mediciones WHERE latitud IS NOT NULL')=='0'
        assert sql('USE aeriq_old_test; SELECT COUNT(*) FROM Medida')=='2'
        assert subprocess.run(migrate+['--apply'],capture_output=True).returncode!=0
        (web/'server/SDBaseDatos.php').write_text(f"<?php return ['host'=>'127.0.0.1','port'=>{dbport},'user'=>'root','password'=>'','database'=>'aeriq_test'];",encoding='utf-8')
        (web/'server/SMTPConfig.php').write_text("<?php return ['host'=>'test','port'=>2525,'username'=>'test','password'=>'test','from_email'=>'airq@example.test'];",encoding='utf-8')
        existing=subprocess.run([opt.php,str(ROOT/'scripts/bootstrap-organization.php'),'admin','admin-a@example.test','No override',str(web)],capture_output=True,text=True)
        assert existing.returncode==1 and 'sobrescrib' in existing.stderr
        (web/'vendor/autoload.php').write_text('''<?php namespace PHPMailer\\PHPMailer;
class PHPMailer {
 const ENCRYPTION_SMTPS='ssl';const ENCRYPTION_STARTTLS='tls';
 public function __construct(...$v){} public function isSMTP(){} public function setFrom(...$v){}
 public function addAddress($recipient){$this->recipient=$recipient;}
 public function send(){if(file_exists(__DIR__.'/fail-mail'))throw new \\RuntimeException('Test SMTP failure');file_put_contents(__DIR__.'/mail.jsonl',json_encode(['to'=>$this->recipient,'body'=>$this->Body])."\\n",FILE_APPEND);}
}''',encoding='utf-8')
        env={**os.environ,'AERIQ_COMMERCIAL_EMAIL':'sales@example.test'}
        httplog=(folder/'http.log').open('w')
        php=subprocess.Popen([opt.php,'-S',f'127.0.0.1:{httpport}','-t',str(web)],stdout=httplog,stderr=httplog,env=env,creationflags=flags)
        base=f'http://127.0.0.1:{httpport}/';cookies={};csrf={}
        def request(path,body=None,identity=None,token=None):
            headers={'Accept':'application/json'}
            if identity and identity in cookies:headers['Cookie']=cookies[identity]
            if body is not None:
                headers['Content-Type']='application/json'
                if identity:headers['X-CSRF-Token']=csrf.get(identity,'')
            if token:headers['X-AERIQ-Token']=token
            req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
            try:r=urllib.request.urlopen(req,timeout=15)
            except urllib.error.HTTPError as e:r=e
            raw=r.read().decode('utf-8-sig')
            try:data=json.loads(raw)
            except ValueError:raise AssertionError(f'No JSON ({r.status}) {path}: {raw[:1000]}')
            if identity:
                cookie=r.headers.get('Set-Cookie')
                if cookie:cookies[identity]=cookie.split(';')[0]
                if 'csrf' in data:csrf[identity]=data['csrf']
            return r.status,data
        def expect(code,path,body=None,identity=None,token=None):
            status,data=request(path,body,identity,token)
            assert status==code,(path,status,code,data)
            return data
        deadline=time.monotonic()+10
        while True:
            try:expect(200,'auth.php?action=session',identity='a');break
            except OSError:
                if time.monotonic()>deadline:raise
                time.sleep(.2)
        for identity,email in [('a','admin-a@example.test'),('b','admin-b@example.test'),('sales','sales@example.test')]:
            expect(200,'auth.php?action=session',identity=identity)
            expect(200,'auth.php?action=login',{'email':email,'password':password},identity)
            expect(200,'auth.php?action=session',identity=identity)
        worker=expect(201,'portal.php?action=worker',{'name':'Worker','last':'One','email':'worker@example.test','role':'trabajador'},'a')
        mail=json.loads((web/'vendor/mail.jsonl').read_text().splitlines()[-1]);temporary=re.search(r'Contraseña temporal: (\w+)',mail['body']).group(1)
        expect(200,'auth.php?action=session',identity='worker')
        webworker=expect(200,'auth.php?action=login',{'email':'worker@example.test','password':temporary},'worker')
        assert webworker['user']['mustChangePassword'] is True
        expect(200,'auth.php?action=session',identity='worker')
        expect(428,'portal.php?action=dashboard',identity='worker')
        original_csrf=csrf['a'];csrf['a']='incorrect-csrf'
        expect(403,'portal.php?action=worker',{'name':'No','last':'CSRF','email':'no@example.test'},'a');csrf['a']=original_csrf
        expect(422,'portal.php?action=worker',{'name':'No','last':'Escalation','email':'no@example.test','role':'admin'},'a')
        (web/'vendor/fail-mail').touch()
        expect(500,'portal.php?action=worker',{'name':'Failed','last':'Mail','email':'failed@example.test'},'a')
        assert sql("USE aeriq_test; SELECT COUNT(*) FROM usuarios WHERE email='failed@example.test'")=='0'
        mobile=expect(200,'mobile-auth.php?action=login',{'email':'worker@example.test','password':temporary})
        assert mobile['user']['mustChangePassword'] is True;token=mobile['token']
        assert sql("USE aeriq_test; SELECT activo FROM usuarios WHERE email='worker@example.test'")=='0'
        sql("USE aeriq_test; UPDATE usuarios SET invitacion_expira_en=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 MINUTE) WHERE email='worker@example.test'")
        expect(403,'mobile-auth.php?action=login',{'email':'worker@example.test','password':temporary})
        expect(403,'field.php',token=token)
        sql("USE aeriq_test; UPDATE usuarios SET invitacion_expira_en=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 24 HOUR) WHERE email='worker@example.test'")
        expect(428,'field.php',token=token)
        expect(422,'mobile-auth.php?action=change-password',{'current':temporary,'password':temporary},token=token)
        changed=expect(200,'mobile-auth.php?action=change-password',{'current':temporary,'password':password},token=token)
        assert changed['user']['mustChangePassword'] is False
        expect(401,'field.php',token=token);token=changed['token']
        today=dt.datetime.now(dt.timezone.utc).date();campaign={'name':'Campaign A','zone':'UPV','start':str(today-dt.timedelta(days=1)),'end':str(today+dt.timedelta(days=1)),'public':False}
        cid=expect(200,'portal.php?action=campaign',campaign,'a')['id']
        device={'name':'Orb A','address':'AA:BB:CC:DD:EE:01','worker':worker['id'],'campaign':cid,'active':True,'simulated':True}
        did=expect(200,'portal.php?action=device',device,'a')['id']
        expect(404,'portal.php?action=device',{**device,'id':did},'b')
        expect(403,'portal.php?action=device',{**device,'address':'AA:BB:CC:DD:EE:02'},'b')
        assignments=expect(200,'field.php',token=token);assert len(assignments['assignments'])==1
        captured=dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z')
        reading={'eventId':str(uuid.uuid4()),'deviceId':did,'campaignId':cid,'address':device['address'],'type':14,'value':123,'capturedAt':captured,'counter':7,'rssi':-60,'latitude':38.99665,'longitude':-.166,'accuracy':5}
        expect(403,'field.php',{**reading,'address':'AA:BB:CC:DD:EE:99'},token=token)
        first=expect(200,'field.php',reading,token=token);again=expect(200,'field.php',reading,token=token)
        assert first['id']==again['id'] and again['duplicate'] is True
        expect(409,'field.php',{**reading,'value':124},token=token)
        expect(409,'field.php',{**reading,'latitude':38.997},token=token)
        expect(409,'field.php',{**reading,'campaignId':cid+1},token=token)
        expect(422,'field.php',{**reading,'eventId':str(uuid.uuid4()),'latitude':91},token=token)
        expect(422,'field.php',{**reading,'eventId':str(uuid.uuid4()),'unit':'µg/m³'},token=token)
        expect(422,'field.php',{**reading,'eventId':str(uuid.uuid4()),'value':-1},token=token)
        expect(422,'field.php',{**reading,'eventId':str(uuid.uuid4()),'counter':256},token=token)
        expect(422,'field.php',{**reading,'eventId':str(uuid.uuid4()),'capturedAt':'2026-02-30T12:00:00.000Z'},token=token)
        for offset in [1,2]:expect(200,'field.php',{**reading,'eventId':str(uuid.uuid4()),'latitude':38.99665+offset*.00005,'value':60+offset*20},token=token)
        assert expect(200,'portal.php?action=readings',identity='b')['readings']==[]
        assert len(expect(200,'portal.php?action=readings',identity='a')['readings'])==3
        assert len(expect(200,'field.php?action=history',token=token)['readings'])==3
        assert len(expect(200,f'portal.php?action=readings&devices={did}',identity='a')['readings'])==3
        expect(422,'portal.php?action=readings&from=2026-12-01&to=2026-01-01',identity='a')
        assert expect(200,'public.php?action=map&mode=simulated')['measurements']==[]
        private=expect(200,'portal.php?action=map&mode=simulated',identity='a')
        assert len(private['measurements'])==3
        sql(f"USE aeriq_test; INSERT INTO usuarios(id,organizacion_id,rol,nombre,apellidos,email,password_hash,verificado_en) VALUES(50,1,'trabajador','Segundo','Operador','second@example.test','{hashed}',UTC_TIMESTAMP());")
        expect(200,'portal.php?action=device',{**device,'id':did,'worker':50},'a')
        assert len(expect(200,'field.php?action=history',token=token)['readings'])==3
        assert expect(200,'field.php',token=token)['assignments']==[]
        assert sql('USE aeriq_test; SELECT COUNT(*) FROM asignaciones WHERE finalizada_en IS NOT NULL')=='1'
        expect(200,'portal.php?action=device',{**device,'id':did},'a')
        expect(200,'portal.php?action=campaign',{**campaign,'id':cid,'public':True},'a')
        assert len(expect(200,'public.php?action=map&mode=simulated')['measurements'])==3
        assert expect(200,'public.php?action=map&mode=real')['measurements']==[]
        expect(200,'portal.php?action=campaign',{**campaign,'id':cid,'public':False},'a')
        assert expect(200,'public.php?action=map&mode=simulated')['measurements']==[]
        expect(200,'portal.php?action=device',{**device,'id':did,'active':False},'a')
        expect(403,'field.php',{**reading,'eventId':str(uuid.uuid4())},token=token)
        expect(403,'portal.php?action=requests',identity='a')
        req={'service':'estudio','entity':'City','contact':'Contact','email':'client@example.test','phone':'','zone':'UPV','surface':100,'duration':'2 weeks','needs':'Daily report','consent':True}
        expect(422,'public.php?action=request',{**req,'consent':False})
        registered=expect(201,'public.php?action=request',req)
        assert registered['id']>0 # SMTP failure does not lose the persisted request.
        requests=expect(200,'portal.php?action=requests',identity='sales')['requests'];assert len(requests)==1
        expect(200,'portal.php?action=request-status',{'id':registered['id'],'status':'respondida'},'sales')
        assert expect(200,'portal.php?action=requests',identity='sales')['requests'][0]['estado']=='respondida'
        for service in ['administracion','empresa']:
            expect(422,'public.php?action=request',{**req,'service':service,'quantity':0})
            expect(201,'public.php?action=request',{**req,'service':service,'quantity':4})
        assert len(expect(200,'portal.php?action=requests',identity='sales')['requests'])==3
        # Autoasignación real desde Android: persistencia, idempotencia y aislamiento.
        expect(401,'sensor-link.php',{'address':'AA:BB:CC:DD:EE:71','name':'Mi Orb'})
        expect(422,'sensor-link.php',{'address':'incorrecta','name':'Mi Orb'},token=token)
        linked=expect(200,'sensor-link.php',{'address':'AA:BB:CC:DD:EE:71','name':'Mi Orb real'},token=token)
        own=next(row for row in linked['assignments'] if row['direccion_ble']=='AA:BB:CC:DD:EE:71')
        assert int(own['simulado'])==0 and int(own['publicar'])==0
        repeated=expect(200,'sensor-link.php',{'address':'AA:BB:CC:DD:EE:71','name':'Otro nombre'},token=token)
        assert next(row for row in repeated['assignments'] if row['id']==own['id'])['asignacion_id']==own['asignacion_id']
        assert any(row['id']==own['id'] for row in expect(200,'field.php',token=token)['assignments'])
        # Sensor BLE real -> API -> BBDD -> historial/informe, incluso sin GPS.
        actual={**reading,'eventId':str(uuid.uuid4()),'deviceId':own['id'],'assignmentId':own['asignacion_id'],
                'campaignId':own['campana_id'],'address':own['direccion_ble'],'capturedAt':dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z')}
        for key in ['latitude','longitude','accuracy']:actual.pop(key,None)
        expect(200,'field.php',actual,token=token)
        assert expect(200,'field.php',actual,token=token)['duplicate'] is True
        saved_real=next(row for row in expect(200,'field.php?action=history',token=token)['readings'] if row['dispositivo_id']==own['id'])
        assert int(saved_real['simulado'])==0 and saved_real['latitud'] is None
        report=expect(200,f"portal.php?action=readings&campaign={own['campana_id']}",identity='a')['readings']
        assert any(row['evento']==actual['eventId'] and int(row['simulado'])==0 for row in report)
        # GPS real: aparece en el mapa privado y sigue excluido del mapa público.
        gps={**actual,'eventId':str(uuid.uuid4()),'latitude':38.99665,'longitude':-0.166,'accuracy':8}
        expect(200,'field.php',gps,token=token)
        private_real=expect(200,'portal.php?action=map&mode=real',identity='a')['measurements']
        assert any(int(row['campaignId'])==int(own['campana_id']) for row in private_real)
        assert not any(int(row['campaignId'])==int(own['campana_id']) for row in expect(200,'public.php?action=map&mode=real')['measurements'])
        sql("USE aeriq_test; INSERT INTO dispositivos(organizacion_id,nombre,direccion_ble,simulado) VALUES(2,'Otra organización','AA:BB:CC:DD:EE:72',0),(1,'Ocupado','AA:BB:CC:DD:EE:73',0);")
        sql(f"USE aeriq_test; INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id) SELECT 1,id,50,{own['campana_id']} FROM dispositivos WHERE direccion_ble='AA:BB:CC:DD:EE:73'")
        for address in ['AA:BB:CC:DD:EE:72','AA:BB:CC:DD:EE:73']:
            expect(409,'sensor-link.php',{'address':address,'name':'No robar'},token=token)
        assert sql("USE aeriq_test; SELECT COUNT(*) FROM dispositivos WHERE direccion_ble='incorrecta'")=='0'
        admin_session=expect(200,'mobile-auth.php?action=login',{'email':'admin-a@example.test','password':password})
        admin_link=expect(200,'sensor-link.php',{'address':'AA:BB:CC:DD:EE:74','name':'Orb administrador'},token=admin_session['token'])
        assert any(row['direccion_ble']=='AA:BB:CC:DD:EE:74' for row in admin_link['assignments'])
        assert len(expect(200,'field.php',token=admin_session['token'])['assignments'])==1
        sql("USE aeriq_test; UPDATE usuarios SET activo=0 WHERE email='worker@example.test'")
        expect(403,'auth.php?action=change-password',{'current':password,'password':'No-Reactivation-2026!'},'worker')
        assert sql("USE aeriq_test; SELECT activo FROM usuarios WHERE email='worker@example.test'")=='0'
        sql("USE aeriq_test; UPDATE usuarios SET activo=1 WHERE email='worker@example.test'")
        # Recuperación real del flujo, con correo retenido en el transporte falso.
        (web/'vendor/fail-mail').unlink()
        expect(200,'mobile-auth.php?action=forgot',{'email':'worker@example.test'})
        recovery=json.loads((web/'vendor/mail.jsonl').read_text().splitlines()[-1])
        code=re.search(r'código AERIQ es: (\d{6})',recovery['body']).group(1)
        reset=expect(200,'mobile-auth.php?action=reset-password',{'email':'worker@example.test','code':code,'password':'Recovered-Only-2026!'})
        assert reset['user']['mustChangePassword'] is False
        expect(401,'mobile-auth.php?action=session',token=token)
        expect(422,'mobile-auth.php?action=reset-password',{'email':'worker@example.test','code':code,'password':'Recovered-Only-2026!'})
        print('AERIQ integración OK: esquema único, roles/organizaciones, invitación y rollback de correo, contraseña inicial, asignaciones, deduplicación, publicación, baja y solicitudes.')
        print('Entorno aislado conservado en',folder)
    finally:
        for process in [php,db]:
            if process and process.poll() is None:
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait()
        log.close()

if __name__=='__main__':main()
