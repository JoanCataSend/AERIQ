"""Recrear una base AERIQ explícita con copia previa; nunca se ejecuta al arrancar la web."""
import argparse
import datetime as dt
import getpass
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
SYSTEM={'mysql','sys','performance_schema','information_schema'}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--database',required=True,help='Nombre exacto de la base que se recreará')
    p.add_argument('--host',default='127.0.0.1');p.add_argument('--port',type=int,default=3306)
    p.add_argument('--user',required=True);p.add_argument('--mysql-bin',default='')
    p.add_argument('--confirm',help='Debe ser exactamente RECREAR:nombre_base')
    p.add_argument('--demo',action='store_true',help='Añadir cuentas y datos ficticios documentados')
    p.add_argument('--dry-run',action='store_true',help='Mostrar el plan sin conectar ni borrar')
    args=p.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_]{1,64}',args.database) or args.database.lower() in SYSTEM:p.error('Nombre de base no permitido')
    if not 1<=args.port<=65535:p.error('Puerto no válido')
    print(f'Destino explícito: {args.host}:{args.port}/{args.database}')
    print('Plan: copia de seguridad si existe -> DROP de esa base -> CREATE -> schema.sql'+(' -> seed-demo.sql' if args.demo else ''))
    if args.dry_run:return
    if args.confirm!='RECREAR:'+args.database:p.error('No se ha borrado nada. Repite con --confirm RECREAR:'+args.database)
    def binary(name):
        value=str(Path(args.mysql_bin)/(name+('.exe' if os.name=='nt' else ''))) if args.mysql_bin else shutil.which(name)
        if not value or not Path(value).is_file():p.error('No se encuentra '+name+'; configura --mysql-bin')
        return value
    mysql,dump=binary('mysql'),binary('mysqldump')
    # No pasar contraseñas por la línea de comandos ni registrarlas en consola.
    password=os.environ['AERIQ_DB_PASSWORD'] if 'AERIQ_DB_PASSWORD' in os.environ else getpass.getpass('Contraseña de MariaDB: ')
    if any(c in args.host+args.user for c in '\r\n'):p.error('Host/usuario no válidos')
    backups=ROOT/'.local-backups';backups.mkdir(exist_ok=True)
    def quote(v):return '"'+str(v).replace('\\','\\\\').replace('"','\\"').replace('\n','\\n').replace('\r','\\r')+'"'
    fd,config=tempfile.mkstemp(prefix='mysql-',suffix='.cnf',dir=backups)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:f.write('[client]\nhost='+quote(args.host)+'\nport='+str(args.port)+'\nuser='+quote(args.user)+'\npassword='+quote(password)+'\n')
        connection=['--defaults-extra-file='+config,'--default-character-set=utf8mb4']
        def run(sql):
            r=subprocess.run([mysql,*connection,'--batch','--skip-column-names'],input=sql,encoding='utf-8',capture_output=True)
            if r.returncode:raise RuntimeError(r.stderr.strip())
            return r.stdout.strip()
        exists=run("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='"+args.database+"';")
        if exists:
            backup=backups/(dt.datetime.now(dt.timezone.utc).strftime('%Y%m%d-%H%M%S-%f')+'-'+args.database+'.sql')
            with backup.open('xb') as f:
                result=subprocess.run([dump,*connection,'--single-transaction','--routines','--triggers','--events','--databases',args.database],stdout=f,stderr=subprocess.PIPE)
            if result.returncode or not backup.stat().st_size:raise RuntimeError('Copia incompleta. No se ejecutará DROP: '+result.stderr.decode(errors='replace'))
            print('Copia previa:',backup)
        run(f'DROP DATABASE IF EXISTS `{args.database}`; CREATE DATABASE `{args.database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        sql=(ROOT/'src/database/schema.sql').read_text(encoding='utf-8')
        if args.demo:sql+='\n'+(ROOT/'src/database/seed-demo.sql').read_text(encoding='utf-8')
        run('USE `'+args.database+'`;\n'+sql)
        print('Base recreada. '+('Datos exclusivamente simulados; consulta src/database/README.md.' if args.demo else 'Esquema vacío, sin usuarios.'))
    finally:
        # Solo se elimina el archivo temporal de credenciales creado por este proceso.
        path=Path(config).resolve()
        if path.parent==backups.resolve():path.unlink(missing_ok=True)

if __name__=='__main__':main()
