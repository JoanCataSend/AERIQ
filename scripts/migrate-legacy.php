<?php
/** Importación no destructiva a una base AERIQ NUEVA y VACÍA. Solo CLI. */
declare(strict_types=1);
if (PHP_SAPI!=='cli') {http_response_code(404);exit;}
$opt=getopt('',['source:','target:','apply']);
if (!isset($opt['source'],$opt['target'])) {fwrite(STDERR,"Uso: php migrate-legacy.php --source=CONFIG_ORIGEN.php --target=CONFIG_DESTINO.php [--apply]\nSin --apply solo muestra el inventario.\n");exit(1);}
mysqli_report(MYSQLI_REPORT_ERROR|MYSQLI_REPORT_STRICT);
function open_database(string $file): array {
    $cfg=require $file;
    $db=new mysqli($cfg['host'],$cfg['user'],$cfg['password'],$cfg['database'],(int)$cfg['port']);
    $db->set_charset('utf8mb4');$db->query("SET time_zone='+00:00'");return [$db,$cfg];
}
function command(mysqli $db,string $sql,array $params=[]): mysqli_stmt {
    $s=$db->prepare($sql);if ($params) {$types='';foreach ($params as $v)$types.=is_int($v)?'i':(is_float($v)?'d':'s');$s->bind_param($types,...$params);}$s->execute();return $s;
}
function rows(mysqli $db,string $sql,array $p=[]): array {return command($db,$sql,$p)->get_result()->fetch_all(MYSQLI_ASSOC);}
function event_id(string $key): string {$h=md5($key);return substr($h,0,8).'-'.substr($h,8,4).'-4'.substr($h,13,3).'-8'.substr($h,17,3).'-'.substr($h,20,12);}
try {
    [$source,$s]=open_database($opt['source']);[$target,$t]=open_database($opt['target']);
    if (strcasecmp($s['database'],$t['database'])===0) throw new RuntimeException('El destino debe tener un nombre de base distinto del origen.');
    $source->query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ');
    $source->begin_transaction(MYSQLI_TRANS_START_READ_ONLY|MYSQLI_TRANS_START_WITH_CONSISTENT_SNAPSHOT);
    $available=[];foreach(rows($source,'SHOW TABLES') as $row){$actual=(string)array_values($row)[0];$available[strtolower($actual)]=$actual;}
    $names=['AeriqUsuario','AirqAcceso','AirqOrganizacion','AirqCampana','AirqDispositivo','AirqLectura','AirqSolicitud','Medida','Dispositivo','TipoMedida'];$data=[];
    foreach ($names as $name) {
        $actual=$available[strtolower($name)]??null;
        $data[$name]=$actual?rows($source,'SELECT * FROM `'.str_replace('`','``',$actual).'`'):[];
        echo $name.': '.count($data[$name])." registro(s)\n";
    }
    if (!array_intersect(array_map('strtolower',$names),array_keys($available))) throw new RuntimeException('No se reconoce un esquema de origen antiguo compatible. No se importará nada.');
    if (!isset($opt['apply'])) {echo "Inventario de solo lectura. Prepara schema.sql en el destino vacío y repite con --apply para importar.\n";exit;}
    foreach (['usuarios','organizaciones','mediciones','solicitudes'] as $table) if ((int)$target->query("SELECT COUNT(*) n FROM `$table`")->fetch_assoc()['n']) throw new RuntimeException('El destino debe estar vacío, sin seed-demo. No se sobrescribirán datos.');
    $target->begin_transaction();$total=0;
    try {
        foreach ($data['AirqOrganizacion'] as $r)command($target,'INSERT INTO organizaciones(id,nombre) VALUES(?,?)',[(int)$r['id'],$r['nombre']]);
        $access=[];foreach ($data['AirqAcceso'] as $r)$access[$r['usuario_id']]=$r;
        foreach ($data['AeriqUsuario'] as $u) {
            $a=$access[$u['id']]??[];$pending=(int)($a['cambiar_clave']??0);
            command($target,'INSERT INTO usuarios(id,organizacion_id,rol_id,nombre,apellidos,email,telefono,password_hash,activo,debe_cambiar_clave,verificado_en,invitacion_expira_en) VALUES(?,?,(SELECT id FROM roles WHERE codigo=?),?,?,?,?,?,?,?,?,?)',[(int)$u['id'],$a['organizacion_id']??null,$a['rol']??'visitante',$u['nombre'],$u['apellidos'],$u['email'],$u['telefono']??'',$u['password_hash'],$pending?0:(int)($a['activo']??1),$pending,$u['verificado_en']??null,$pending?gmdate('Y-m-d H:i:s',time()+86400):null]);
        }
        foreach ($data['AirqCampana'] as $c) {
            command($target,'INSERT IGNORE INTO zonas(organizacion_id,nombre) VALUES(?,?)',[(int)$c['organizacion_id'],trim($c['zona'])]);
            command($target,'INSERT INTO campanas(id,organizacion_id,zona_id,nombre,inicio,fin,activa,publicar,radio_m) VALUES(?,?,(SELECT id FROM zonas WHERE organizacion_id=? AND nombre=?),?,?,?,?,?,?)',[(int)$c['id'],(int)$c['organizacion_id'],(int)$c['organizacion_id'],trim($c['zona']),$c['nombre'],$c['inicio'],$c['fin'],(int)$c['activa'],(int)$c['publicar'],(int)$c['radio_m']]);
            command($target,'INSERT INTO campana_umbrales(campana_id,magnitud_id,umbral_medio,umbral_alto) VALUES(?,14,?,?)',[(int)$c['id'],(float)$c['umbral_medio'],(float)$c['umbral_alto']]);
        }
        $devices=[];foreach ($data['AirqDispositivo'] as $d) {$devices[$d['id']]=$d;command($target,'INSERT INTO dispositivos(id,organizacion_id,nombre,direccion_ble,activo,simulado) VALUES(?,?,?,?,?,?)',[(int)$d['id'],(int)$d['organizacion_id'],$d['nombre'],$d['direccion_ble'],(int)$d['activo'],(int)$d['simulado']]);}
        $assignments=[];
        $assignment=function(int $device,int $worker,int $campaign,string $date) use (&$assignments,$target,$devices): int {
            $key="$device/$worker/$campaign";if(isset($assignments[$key]))return $assignments[$key];
            $d=$devices[$device]??null;if(!$d)throw new RuntimeException('Una lectura referencia un dispositivo inexistente.');
            // Se conservan identidades históricas reconstruidas; no se inventa una asignación operativa.
            command($target,'INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en,finalizada_en) VALUES(?,?,?,?,?,UTC_TIMESTAMP(3))',[(int)$d['organizacion_id'],$device,$worker,$campaign,$date]);
            return $assignments[$key]=(int)$target->insert_id;
        };
        usort($data['AirqLectura'],fn($a,$b)=>strcmp($a['capturada_en'],$b['capturada_en']));
        foreach ($data['AirqLectura'] as $r) {
            if (!in_array((int)$r['tipo'],[12,14],true) || $r['unidad']!==((int)$r['tipo']===14?'ppb':'°C')) throw new RuntimeException('Unidad no compatible: importación revertida; no se convierten unidades automáticamente.');
            $a=$assignment((int)$r['dispositivo_id'],(int)$r['usuario_id'],(int)$r['campana_id'],$r['capturada_en']);
            command($target,'INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,recibida_en,latitud,longitud,precision_m,contador,rssi,simulado,valida) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',[$r['evento'],$a,(int)$r['tipo'],(float)$r['valor'],$r['capturada_en'],$r['recibida_en'],$r['latitud'],$r['longitud'],$r['precision_m'],$r['contador'],$r['rssi'],(int)$r['simulado'],(int)$r['valida']]);$total++;
        }
        foreach ($data['AirqDispositivo'] as $d) {
            if (!$d['trabajador_id'] || !$d['campana_id'] || !(int)$d['activo'])continue;
            command($target,'INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id) VALUES(?,?,?,?)',[(int)$d['organizacion_id'],(int)$d['id'],(int)$d['trabajador_id'],(int)$d['campana_id']]);
        }
        foreach ($data['AirqSolicitud'] as $r) {
            $service=$target->query("SELECT id FROM servicios WHERE codigo='".$target->real_escape_string((string)$r['servicio'])."'")->fetch_assoc();
            $state=$target->query("SELECT id FROM estados_solicitud WHERE codigo='".$target->real_escape_string(str_replace(' ','_',(string)$r['estado']))."'")->fetch_assoc();
            if (!$service || !$state) throw new RuntimeException('Servicio o estado de solicitud desconocido: importación revertida.');
            command($target,'INSERT INTO solicitudes(id,servicio_id,estado_id,entidad,contacto,email,telefono,cantidad,zona,superficie,duracion,necesidades,recibida_en,consentimiento_en) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)',[(int)$r['id'],(int)$service['id'],(int)$state['id'],$r['entidad'],$r['contacto'],$r['email'],$r['telefono'],$r['cantidad'],$r['zona'],$r['superficie'],$r['duracion'],$r['necesidades'],$r['recibida_en'],null]);
            // Solo se conoce la recepción; los cambios de estado antiguos no tenían fecha.
            command($target,'INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id,cambiado_en) VALUES(?,1,NULL,?)',[(int)$r['id'],$r['recibida_en']]);
        }
        if ($data['Medida']) {
            foreach($data['TipoMedida'] as $type) {
                if(in_array((int)$type['tipoMedidaId'],[12,14],true) && $type['unidad']!==((int)$type['tipoMedidaId']===14?'ppb':'°C'))throw new RuntimeException('Unidad histórica incompatible: no se realizará una conversión automática.');
            }
            command($target,"INSERT INTO organizaciones(nombre) VALUES('Archivo de mediciones Sprint 0')");$org=(int)$target->insert_id;
            command($target,"INSERT INTO usuarios(organizacion_id,rol_id,nombre,apellidos,email,password_hash,activo) VALUES(?,(SELECT id FROM roles WHERE codigo='trabajador'),'Importación','Histórica','archivo@legacy.invalid',?,0)",[$org,password_hash(bin2hex(random_bytes(32)),PASSWORD_DEFAULT)]);$operator=(int)$target->insert_id;
            $dates=array_column($data['Medida'],'fechaHora');sort($dates);$start=substr($dates[0],0,10);$end=substr(end($dates),0,10);
            command($target,"INSERT INTO zonas(organizacion_id,nombre) VALUES(?,'Sin coordenadas originales')",[$org]);$zone=(int)$target->insert_id;
            command($target,"INSERT INTO campanas(organizacion_id,zona_id,nombre,inicio,fin,activa,publicar) VALUES(?,?,'Mediciones conservadas Sprint 0',?,?,0,0)",[$org,$zone,$start,$end]);$campaign=(int)$target->insert_id;
            $legacy=[];foreach ($data['Dispositivo'] as $d)$legacy[$d['dispositivoId']]=$d['nombre'];$mapped=[];
            foreach ($data['Medida'] as $i=>$r) {
                $id=$r['dispositivoId'];
                if (!isset($mapped[$id])) {
                    command($target,'INSERT INTO dispositivos(organizacion_id,nombre,activo,simulado) VALUES(?,?,0,1)',[$org,$legacy[$id]??('Orb importado '.$id)]);$orb=(int)$target->insert_id;
                    command($target,'INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id,iniciada_en,finalizada_en) VALUES(?,?,?,?,?,?)',[$org,$orb,$operator,$campaign,$start.' 00:00:00',$end.' 23:59:59']);$mapped[$id]=(int)$target->insert_id;
                }
                $type=(int)$r['tipoMedidaId'];if(!in_array($type,[12,14],true))throw new RuntimeException('Magnitud antigua desconocida: importación revertida.');
                $key='Medida/'.($r['id']??$r['medidaId']??$i);
                command($target,'INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,simulado) VALUES(?,?,?,?,?,1)',[event_id($key),$mapped[$id],$type,(float)$r['valor'],$r['fechaHora']]);$total++;
            }
        }
        $target->commit();echo "Importación completada: $total mediciones conservadas; origen sin cambios. Las sesiones y códigos temporales no se migran.\n";
    } catch (Throwable $e) {$target->rollback();throw $e;}
} catch (Throwable $e) {fwrite(STDERR,$e->getMessage()."\n");exit(1);}
