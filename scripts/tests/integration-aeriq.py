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
        sql(f"USE aeriq_test; INSERT INTO organizaciones(nombre) VALUES('Org A'),('Org B'); INSERT INTO usuarios(nombre,apellidos,email,password_hash,verificado_en) VALUES('Admin','A','admin-a@example.test','{hashed}',NOW()),('Admin','B','admin-b@example.test','{hashed}',NOW()),('Comercial','CN','sales@example.test','{hashed}',NOW()); UPDATE usuarios SET organizacion_id=1,rol_id=(SELECT id FROM roles WHERE codigo='admin') WHERE id=1; UPDATE usuarios SET organizacion_id=2,rol_id=(SELECT id FROM roles WHERE codigo='admin') WHERE id=2; UPDATE usuarios SET rol_id=(SELECT id FROM roles WHERE codigo='comercial') WHERE id=3;")
        # Una instalación nueva puede recrearse sin depender de las tablas de Plesk.
        sql('CREATE DATABASE aeriq_demo_test; USE aeriq_demo_test;'+(ROOT/'src/database/schema.sql').read_text(encoding='utf-8')+(ROOT/'src/database/seed-demo.sql').read_text(encoding='utf-8'))
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM usuarios')=='6'
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones')=='9'
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones WHERE simulado=0')=='0'
        # Migración v1 -> v2: conserva los datos, es repetible y deja la misma estructura que schema.sql.
        sql('CREATE DATABASE aeriq_v1_test CHARACTER SET utf8mb4; USE aeriq_v1_test;'+(ROOT/'scripts/tests/fixtures/esquema-v1-con-demo.sql').read_text(encoding='utf-8'))
        sql("USE aeriq_v1_test; INSERT INTO organizaciones(id,nombre) VALUES(9,'Empresa Verde · demostración'); UPDATE usuarios SET firebase_uid='uid-demo' WHERE id=1; UPDATE campanas SET umbral_medio=55 WHERE id=2; UPDATE solicitudes SET estado='en revision' WHERE id=2;")
        for _ in range(2):
            sql('USE aeriq_v1_test;'+(ROOT/'src/database/migracion-v2-catalogos.sql').read_text(encoding='utf-8'))
            sql('USE aeriq_v1_test;'+(ROOT/'src/database/migracion-v3-bateria.sql').read_text(encoding='utf-8'))
            assert sql('USE aeriq_v1_test; SELECT COUNT(*) FROM mediciones')=='9'
            assert sql("USE aeriq_v1_test; SELECT r.codigo FROM usuarios u JOIN roles r ON r.id=u.rol_id ORDER BY u.id")=='admin\ntrabajador\nanalista\ncomercial\nadmin\ntrabajador'
            assert sql("USE aeriq_v1_test; SELECT nombre FROM organizaciones WHERE id=9")=='Empresa Verde · demostración (#9)'
            assert sql("USE aeriq_v1_test; SELECT usuario_id,proveedor,uid_externo FROM identidades_externas")=='1\tfirebase\tuid-demo'
            assert sql("USE aeriq_v1_test; SELECT umbral_medio FROM campana_umbrales WHERE campana_id=2 AND magnitud_id=14")=='55'
            assert sql("USE aeriq_v1_test; SELECT e.codigo FROM solicitudes s JOIN estados_solicitud e ON e.id=s.estado_id WHERE s.id=2")=='en_revision'
            assert sql("USE aeriq_v1_test; SELECT COUNT(*) FROM solicitudes_historial")=='3'
            assert sql("USE aeriq_v1_test; SELECT COUNT(*) FROM vista_mediciones")=='9'
        structure="SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'-') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='{}' ORDER BY 1,2"
        foreign="SELECT TABLE_NAME,REFERENCED_TABLE_NAME,GROUP_CONCAT(COLUMN_NAME ORDER BY ORDINAL_POSITION) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='{}' AND REFERENCED_TABLE_NAME IS NOT NULL GROUP BY CONSTRAINT_NAME,TABLE_NAME,REFERENCED_TABLE_NAME ORDER BY 1,2,3"
        checks="SELECT TABLE_NAME,CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='{}' ORDER BY 1,2"
        for query in [structure,foreign,checks]:
            assert sql(query.format('aeriq_v1_test'))==sql(query.format('aeriq_demo_test')),query
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
        assert 'Copia previa:' in recreated.stdout,(recreated.stdout,recreated.stderr)
        assert sql('USE aeriq_demo_test; SELECT COUNT(*) FROM mediciones')=='9'
        # Migración de un Sprint 0 mínimo: conserva valores y no inventa GPS ni MAC.
        sql('CREATE DATABASE aeriq_cleanup_test; USE aeriq_cleanup_test;'+(ROOT/'src/database/schema.sql').read_text(encoding='utf-8')+(ROOT/'src/database/seed-demo.sql').read_text(encoding='utf-8'))
        sql(f"USE aeriq_cleanup_test; INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,simulado) SELECT '{uuid.uuid4()}',id,14,73,UTC_TIMESTAMP(3),0 FROM asignaciones ORDER BY id LIMIT 1")
        accounts_before=sql('USE aeriq_cleanup_test; SELECT id,email,password_hash,organizacion_id FROM usuarios ORDER BY id')
        orgs_before=sql('USE aeriq_cleanup_test; SELECT * FROM organizaciones ORDER BY id')
        for _ in range(2):
            cleanup_out=sql('USE aeriq_cleanup_test;'+(ROOT/'src/database/limpiar-simulados-y-sensores.sql').read_text(encoding='utf-8'))
            n=sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM mediciones');assert n=='0',('mediciones',n,cleanup_out)
            n=sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM dispositivos');assert n=='0',('dispositivos',n,cleanup_out)
            n=sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM asignaciones');assert n=='0',('asignaciones',n,cleanup_out)
            assert sql('USE aeriq_cleanup_test; SELECT COUNT(*) FROM archivo_sensores_mediciones WHERE simulado=0 AND valor=73')=='1'
            after=sql('USE aeriq_cleanup_test; SELECT id,email,password_hash,organizacion_id FROM usuarios ORDER BY id')
            assert accounts_before==after,(accounts_before,after)
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
        reset_dir=ROOT/'src/database/reinicio'
        format_sql=(reset_dir/'00-formatear.sql').read_text(encoding='utf-8')
        install_sql=(reset_dir/'01-instalar.sql').read_text(encoding='utf-8')
        unqualified_install=re.sub(r'`jcatsen_pbio`\.`(\w+)`',r'\1',install_sql)
        assert (ROOT/'src/database/schema.sql').read_text(encoding='utf-8') in unqualified_install
        # Simula el cambio de base activa de phpMyAdmin al consultar metadatos.
        format_sql=format_sql.replace("-- Este marcador vacío", "USE information_schema;\n-- Este marcador vacío")
        install_sql=install_sql.replace("-- AERIQ · Esquema único", "USE information_schema;\n-- AERIQ · Esquema único")
        admin_seed=(reset_dir/'02-admin.sql').read_text(encoding='utf-8')
        sql('USE jcatsen_pbio; CREATE TABLE tokens_consumidos(id INT PRIMARY KEY); CREATE VIEW vista_anterior AS SELECT id FROM tokens_consumidos;')
        sql('ALTER TABLE jcatsen_pbio.usuarios AUTO_INCREMENT=2000000000')
        previous_admin=1999999999
        for _ in range(2):
            sql('USE aeriq_test;'+format_sql)
            assert sql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='jcatsen_pbio'")=='_reinicio_aeriq'
            sql(install_sql)
            assert sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='jcatsen_pbio' AND TABLE_TYPE='BASE TABLE'")=='23'
            assert sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='jcatsen_pbio' AND TABLE_TYPE='VIEW'")=='4'
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.mediciones')=='0'
            assert sql('SELECT id FROM jcatsen_pbio.magnitudes ORDER BY id')=='12\n14'
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.usuarios')=='0'
            if previous_admin == 1999999999:
                sql("INSERT INTO jcatsen_pbio.organizaciones(nombre) VALUES ('AERIQ Gandia')")
            # phpMyAdmin puede ejecutar consultas intermedias entre sentencias del importador.
            sql(admin_seed.replace("INSERT INTO `jcatsen_pbio`.`usuarios`", "SELECT 1;\nINSERT INTO `jcatsen_pbio`.`usuarios`"))
            sql(admin_seed)
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.organizaciones')=='1'
            admin_id,role=sql('SELECT u.id,r.codigo FROM jcatsen_pbio.usuarios u JOIN jcatsen_pbio.roles r ON r.id=u.rol_id').split('\t')
            assert role=='admin' and int(admin_id)>previous_admin
            previous_admin=int(admin_id)
        # Datos simulados opcionales sobre la instalación por fases: repetibles y sin duplicados.
        for _ in range(2):
            sql((reset_dir/'03-simulacion.sql').read_text(encoding='utf-8'))
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.mediciones WHERE simulado=1')=='54'
            assert sql("SELECT COUNT(*) FROM jcatsen_pbio.mediciones m JOIN jcatsen_pbio.asignaciones a ON a.id=m.asignacion_id JOIN jcatsen_pbio.campanas c ON c.id=a.campana_id WHERE c.publicar=1 AND m.magnitud_id=14")=='30'
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.usuarios')=='5'
            assert sql('SELECT COUNT(*) FROM jcatsen_pbio.solicitudes_historial')=='6'
        sql("CREATE DATABASE aeriq_old_test; USE aeriq_old_test; CREATE TABLE AeriqUsuario(id BIGINT PRIMARY KEY,nombre VARCHAR(90),apellidos VARCHAR(120),email VARCHAR(254),telefono VARCHAR(40),password_hash VARCHAR(255),verificado_en DATETIME); CREATE TABLE Dispositivo(dispositivoId VARCHAR(16),nombre VARCHAR(100)); CREATE TABLE TipoMedida(tipoMedidaId INT,nombre VARCHAR(30),unidad VARCHAR(16)); CREATE TABLE Medida(id INT,dispositivoId VARCHAR(16),tipoMedidaId INT,valor INT,fechaHora DATETIME); INSERT INTO Dispositivo VALUES('EPSG-GTI-PROY-3A','Orb de Sprint 0'); INSERT INTO TipoMedida VALUES(14,'Ozono','ppb'),(12,'Temperatura','°C'); INSERT INTO Medida VALUES(1,'EPSG-GTI-PROY-3A',14,123,UTC_TIMESTAMP()),(2,'EPSG-GTI-PROY-3A',12,-12,UTC_TIMESTAMP());")
        sql('CREATE DATABASE aeriq_import_test; USE aeriq_import_test;'+(ROOT/'src/database/schema.sql').read_text(encoding='utf-8'))
        configs=[]
        for database in ['aeriq_old_test','aeriq_import_test']:
            cfg=folder/(database+'.php');cfg.write_text(f"<?php return ['host'=>'127.0.0.1','port'=>{dbport},'user'=>'root','password'=>'','database'=>'{database}'];")
            configs.append(str(cfg))
        migrate=[opt.php,str(ROOT/'scripts/migrate-legacy.php'),'--source='+configs[0],'--target='+configs[1]]
        assert subprocess.run(migrate,capture_output=True).returncode==0
        assert sql('USE aeriq_import_test; SELECT COUNT(*) FROM mediciones')=='0'
        imported=subprocess.run(migrate+['--apply'],capture_output=True,text=True)
        assert imported.returncode==0,imported.stderr
        assert sql('USE aeriq_import_test; SELECT GROUP_CONCAT(valor ORDER BY magnitud_id) FROM mediciones')=='-12,123'
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
        admin_user=expect(200,'auth.php?action=session',identity='a')['user']
        assert admin_user['role']=='admin' and 'organizacion.gestionar' in admin_user['permissions'] and admin_user['roleName']=='Administrador'
        assert 'solicitudes.gestionar' in expect(200,'auth.php?action=session',identity='sales')['user']['permissions']
        board=expect(200,'portal.php?action=dashboard',identity='a')
        assert [r['codigo'] for r in board['roles']]==['trabajador','analista'] and board['thresholds']=={'umbral_medio':60.0,'umbral_alto':100.0}
        expect(422,'portal.php?action=worker',{'name':'No','last':'Comercial','email':'no2@example.test','role':'comercial'},'a')
        expect(422,'portal.php?action=worker',{'name':'No','last':'Visitante','email':'no3@example.test','role':'visitante'},'a')
        services=expect(200,'public.php?action=services')['services']
        assert [s['codigo'] for s in services]==['administracion','empresa','estudio'] and services[2]['mide_superficie'] is True
        worker=expect(201,'portal.php?action=worker',{'name':'Worker','last':'One','email':'worker@example.test','role':'trabajador'},'a')
        mail=json.loads((web/'vendor/mail.jsonl').read_text(encoding='utf-8').splitlines()[-1]);temporary=re.search(r'Contraseña temporal: (\w+)',mail['body']).group(1)
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
        # La zona se reutiliza por nombre y los umbrales se guardan por magnitud.
        board=expect(200,'portal.php?action=dashboard',identity='a')
        assert [z['nombre'] for z in board['zones']]==['UPV']
        saved=next(c for c in board['campaigns'] if c['id']==cid)
        assert saved['zona']=='UPV' and float(saved['umbral_medio'])==60 and float(saved['umbral_alto'])==100
        expect(422,'portal.php?action=campaign',{**campaign,'id':cid,'medium':90,'high':80},'a')
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
        assert len(expect(200,'portal.php?action=readings&zone=UPV',identity='a')['readings'])==3
        assert expect(200,'portal.php?action=readings&zone=Otra',identity='a')['readings']==[]
        expect(422,'portal.php?action=readings&from=2026-12-01&to=2026-01-01',identity='a')
        assert expect(200,'public.php?action=map&mode=simulated')['measurements']==[]
        private=expect(200,'portal.php?action=map&mode=simulated',identity='a')
        assert len(private['measurements'])==3
        sql(f"USE aeriq_test; INSERT INTO usuarios(id,organizacion_id,rol_id,nombre,apellidos,email,password_hash,verificado_en) VALUES(50,1,(SELECT id FROM roles WHERE codigo='trabajador'),'Segundo','Operador','second@example.test','{hashed}',UTC_TIMESTAMP());")
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
        listing=expect(200,'portal.php?action=requests',identity='sales');requests=listing['requests'];assert len(requests)==1
        assert [s['codigo'] for s in listing['states']]==['pendiente','en_revision','respondida','cerrada']
        assert requests[0]['servicio']=='estudio' and requests[0]['estado']=='pendiente' and [h['estado'] for h in requests[0]['historial']]==['pendiente']
        expect(422,'portal.php?action=request-status',{'id':registered['id'],'status':'en revision'},'sales')
        expect(200,'portal.php?action=request-status',{'id':registered['id'],'status':'respondida'},'sales')
        answered=expect(200,'portal.php?action=requests',identity='sales')['requests'][0]
        assert answered['estado']=='respondida' and answered['estado_nombre']=='Respondida'
        assert [h['estado'] for h in answered['historial']]==['pendiente','respondida'] and answered['historial'][1]['usuario']=='Comercial CN'
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
        thermal={**actual,'eventId':str(uuid.uuid4()),'type':12,'value':-12,'unit':'°C'}
        expect(200,'field.php',thermal,token=token)
        # El modelo del Orb decide qué magnitudes acepta la ingesta.
        sql("USE aeriq_test; INSERT INTO modelos_dispositivo(codigo,nombre) VALUES('solo-o3','Prueba solo ozono'); INSERT INTO modelo_magnitudes(modelo_id,magnitud_id) SELECT id,14 FROM modelos_dispositivo WHERE codigo='solo-o3';")
        sql(f"USE aeriq_test; UPDATE dispositivos SET modelo_id=(SELECT id FROM modelos_dispositivo WHERE codigo='solo-o3') WHERE id={own['id']}")
        expect(422,'field.php',{**thermal,'eventId':str(uuid.uuid4())},token=token)
        sql(f"USE aeriq_test; UPDATE dispositivos SET modelo_id=1 WHERE id={own['id']}")
        temperatures=expect(200,f"portal.php?action=readings&campaign={own['campana_id']}&type=12",identity='a')['readings']
        assert len(temperatures)==1 and temperatures[0]['unidad']=='°C' and float(temperatures[0]['valor'])==-12
        assert temperatures[0]['umbral_medio'] is None and int(temperatures[0]['tipo'])==12
        both=expect(200,f"portal.php?action=readings&campaign={own['campana_id']}&type=all",identity='a')['readings']
        assert {row['unidad'] for row in both}=={'ppb','°C'}
        expect(422,'portal.php?action=readings&type=99',identity='a')
        # Batería anunciada por el Orb real: se guarda, no se duplica y aparece en app y panel.
        now=dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds').replace('+00:00','Z')
        battery={'deviceId':own['id'],'address':own['direccion_ble'],'percent':57,'capturedAt':now}
        assert expect(200,'field.php?action=battery',battery,token=token)['stored'] is True
        assert expect(200,'field.php?action=battery',battery,token=token)['stored'] is False
        expect(422,'field.php?action=battery',{**battery,'percent':101},token=token)
        expect(403,'field.php?action=battery',{**battery,'address':'AA:BB:CC:DD:EE:99'},token=token)
        assert expect(200,'field.php?action=battery',{**battery,'percent':55},token=token)['stored'] is True
        mine=next(row for row in expect(200,'field.php',token=token)['assignments'] if row['id']==own['id'])
        assert int(mine['bateria_pct'])==55 and mine['bateria_en']
        board_device=next(d for d in expect(200,'portal.php?action=dashboard',identity='a')['devices'] if d['id']==own['id'])
        assert int(board_device['bateria_pct'])==55
        assert sql(f"USE aeriq_test; SELECT COUNT(*) FROM lecturas_bateria WHERE dispositivo_id={own['id']}")=='2'
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
        recovery=json.loads((web/'vendor/mail.jsonl').read_text(encoding='utf-8').splitlines()[-1])
        code=re.search(r'código AERIQ es: (\d{6})',recovery['body']).group(1)
        reset=expect(200,'mobile-auth.php?action=reset-password',{'email':'worker@example.test','code':code,'password':'Recovered-Only-2026!'})
        assert reset['user']['mustChangePassword'] is False
        expect(401,'mobile-auth.php?action=session',token=token)
        expect(422,'mobile-auth.php?action=reset-password',{'email':'worker@example.test','code':code,'password':'Recovered-Only-2026!'})
        config=web/'server/SDBaseDatos.php'
        config.write_text(config.read_text(encoding='utf-8').replace("'database'=>'aeriq_test'", "'database'=>'jcatsen_pbio'"),encoding='utf-8')
        initial_login={'email':'gandia@gmail.com','password':'1234'}
        expect(200,'auth.php?action=session',identity='initial-admin')
        assert expect(200,'auth.php?action=login',initial_login,'initial-admin')['user']['role']=='admin'
        assert expect(200,'mobile-auth.php?action=login',initial_login)['user']['role']=='admin'
        # Cuentas de 03-simulacion.sql: misma contraseña 1234 que la administradora inicial.
        assert expect(200,'mobile-auth.php?action=login',{'email':'operador.simulacion@aeriq.example','password':'1234'})['user']['role']=='trabajador'
        expect(200,'auth.php?action=session',identity='sim-sales')
        assert expect(200,'auth.php?action=login',{'email':'comercial.simulacion@codigonorte.example','password':'1234'},'sim-sales')['user']['role']=='comercial'
        print('AERIQ integración OK: migración v1->v2, reinstalación por fases, acceso inicial web y Android, roles, invitaciones, asignaciones, deduplicación, publicación y solicitudes.')
        print('Entorno aislado conservado en',folder)
    finally:
        for process in [php,db]:
            if process and process.poll() is None:
                process.terminate()
                try:process.wait(timeout=10)
                except subprocess.TimeoutExpired:process.kill();process.wait()
        log.close()

if __name__=='__main__':main()
