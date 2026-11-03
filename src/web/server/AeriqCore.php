<?php
declare(strict_types=1);

/** Dominio AERIQ. Tablas nuevas aisladas de las tablas heredadas de Sprint 0. */
final class AeriqError extends RuntimeException {
    public function __construct(string $message, public int $status = 422) { parent::__construct($message); }
}
function aeriq_sql(string $sql, array $params = []): mysqli_stmt {
    $s = conexionBD()->prepare($sql);
    if ($params) {
        $types='';foreach ($params as $v) $types.=is_int($v)?'i':(is_float($v)?'d':'s');
        $s->bind_param($types,...$params);
    }
    $s->execute();return $s;
}
function aeriq_rows(string $sql, array $params = []): array { return aeriq_sql($sql,$params)->get_result()->fetch_all(MYSQLI_ASSOC); }
function aeriq_one(string $sql, array $params = []): ?array { return aeriq_rows($sql,$params)[0] ?? null; }
function aeriq_access(int $id): array {
    $a=aeriq_one('SELECT u.id AS usuario_id,u.organizacion_id,o.nombre AS organizacion,u.rol,u.debe_cambiar_clave AS cambiar_clave,u.activo,u.invitacion_expira_en FROM usuarios u LEFT JOIN organizaciones o ON o.id=u.organizacion_id WHERE u.id=?',[$id]);
    if (!$a) throw new AeriqError('Cuenta no disponible.',401); return $a;
}

function aeriq_ready(array $u, array $roles = []): array {
    auth_login_allowed($u); $a=aeriq_access((int)$u['id']);
    if ((int)$a['cambiar_clave']) throw new AeriqError('Activa tu cuenta sustituyendo la contraseña temporal.',428);
    if (!(int)$a['activo']) throw new AeriqError('La cuenta está desactivada.',403);
    if ($roles && !in_array($a['rol'],$roles,true)) throw new AeriqError('Tu función no permite esta operación.',403);
    return $a;
}

function aeriq_int(mixed $v, int $min=1, int $max=PHP_INT_MAX): int {
    if (filter_var($v,FILTER_VALIDATE_INT)===false || (int)$v<$min || (int)$v>$max) throw new AeriqError('Número entero fuera de rango.');
    return (int)$v;
}
function aeriq_text(mixed $v, int $max=200, bool $optional=false): string {
    if (!is_string($v)) throw new AeriqError('Texto no válido.');
    $v=trim($v); if ((!$optional && $v==='') || strlen($v)>$max) throw new AeriqError('Revisa los campos de texto.'); return $v;
}
function aeriq_number(mixed $v,float $min,float $max): float {
    if (!is_numeric($v) || !is_finite((float)$v) || (float)$v<$min || (float)$v>$max) throw new AeriqError('Valor numérico fuera de rango.'); return (float)$v;
}
function aeriq_bool(mixed $v): int { if (!in_array($v,[true,false,0,1,'0','1'],true)) throw new AeriqError('Valor booleano no válido.'); return (int)(bool)$v; }
function aeriq_org(array $a): int { if (!$a['organizacion_id']) throw new AeriqError('La cuenta no pertenece a una organización.',403); return (int)$a['organizacion_id']; }
function aeriq_own(string $table,int $id,int $org): array {
    if (!in_array($table,['dispositivos','campanas'],true)) throw new LogicException('Tabla no permitida');
    $r=aeriq_one("SELECT * FROM $table WHERE id=? AND organizacion_id=?",[$id,$org]);
    if (!$r) throw new AeriqError('El recurso no está disponible en tu organización.',404); return $r;
}
function aeriq_password_changed(int $id): void {
    aeriq_sql('UPDATE usuarios SET debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?',[$id]);
}

function aeriq_mail(string $recipient,string $subject,string $text): void {
    $cfg=auth_mail_config_ok();
    if ($cfg['transport']==='smtp') {
        require_once dirname(__DIR__).'/vendor/autoload.php';
        $c=$cfg['smtp']; $m=new PHPMailer\PHPMailer\PHPMailer(true); $m->isSMTP();
        $m->Host=$c['host'];$m->Port=(int)$c['port'];$m->SMTPAuth=true;$m->Username=$c['username'];$m->Password=$c['password'];
        $m->SMTPSecure=($c['encryption']??'tls')==='ssl'?'ssl':'tls';$m->CharSet='UTF-8';
        $m->setFrom($c['from_email'],$c['from_name']??'Código Norte');$m->addAddress($recipient);$m->Subject=$subject;$m->Body=$text;$m->send();return;
    }
    $headers=['From: '.$cfg['from_name'].' <'.$cfg['from_email'].'>','Content-Type: text/plain; charset=UTF-8'];
    if (!mail($recipient,'=?UTF-8?B?'.base64_encode($subject).'?=',$text,implode("\r\n",$headers))) throw new AeriqError('El servidor no ha aceptado el correo.',503);
}
function aeriq_worker(array $a,array $d): array {
    $org=aeriq_org($a);$email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);$last=auth_string($d['last']??'',120);
    $role=$d['role']??'trabajador';if (!in_array($role,['trabajador','analista'],true)) throw new AeriqError('Función no permitida.');
    $hours=max(1,min(168,(int)(getenv('AERIQ_INVITATION_HOURS')?:24)));
    auth_mail_config_ok();$db=conexionBD();$db->begin_transaction();
    try {
        if (auth_find($email)) throw new AeriqError('Ese correo ya tiene una cuenta. No se cambiará su organización.',409);
        $temporary=bin2hex(random_bytes(10));$hash=password_hash($temporary,PASSWORD_DEFAULT);
        aeriq_sql('INSERT INTO usuarios(organizacion_id,rol,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,invitacion_expira_en) VALUES(?,?,?,?,?,?,0,1,DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? HOUR))',[$org,$role,$name,$last,$email,$hash,$hours]);$id=(int)$db->insert_id;
        aeriq_mail($email,'AERIQ · Invitación de tu organización',"Tu organización te invita a AERIQ.\nCorreo: $email\nContraseña temporal: $temporary\n\nAbre la app AERIQ e inicia sesión. Sustituye la contraseña para activar tu cuenta. El acceso temporal caduca en $hours horas. Si caduca, recupera el acceso desde la app o la web.\nCódigo Norte · Orb");
        $db->commit();return ['ok'=>true,'id'=>$id,'message'=>'Invitación registrada. El servidor ha aceptado el correo; la cuenta se activará al cambiar la contraseña.'];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

function aeriq_campaign_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])?aeriq_int($d['id']):null;
    if ($id) aeriq_own('campanas',$id,$org);
    $start=aeriq_text($d['start']??'',10);$end=aeriq_text($d['end']??'',10);
    foreach ([$start,$end] as $date) { $v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Fecha no válida.'); }
    if ($end<$start) throw new AeriqError('La fecha final debe ser posterior al inicio.');
    $mid=aeriq_number($d['medium']??60,0,65535);$high=aeriq_number($d['high']??100,0,65535);if ($high<=$mid) throw new AeriqError('El umbral alto debe superar al medio.');
    $params=[aeriq_text($d['name']??'',160),aeriq_text($d['zone']??''),$start,$end,aeriq_bool($d['active']??true),aeriq_bool($d['public']??false),aeriq_int($d['radius']??30,20,50),$mid,$high];
    if ($id) { aeriq_sql('UPDATE campanas SET nombre=?,zona=?,inicio=?,fin=?,activa=?,publicar=?,radio_m=?,umbral_medio=?,umbral_alto=? WHERE id=? AND organizacion_id=?',[...$params,$id,$org]); }
    else { aeriq_sql('INSERT INTO campanas(nombre,zona,inicio,fin,activa,publicar,radio_m,umbral_medio,umbral_alto,organizacion_id) VALUES(?,?,?,?,?,?,?,?,?,?)',[...$params,$org]);$id=(int)conexionBD()->insert_id; }
    return ['ok'=>true,'id'=>$id];
}
function aeriq_device_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])?aeriq_int($d['id']):null;
    if($id)aeriq_own('dispositivos',$id,$org);
    $mac=strtoupper(aeriq_text($d['address']??'',17));if (!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D',$mac)) throw new AeriqError('Dirección BLE no válida.');
    $worker=empty($d['worker'])?null:aeriq_int($d['worker']);$campaign=empty($d['campaign'])?null:aeriq_int($d['campaign']);
    if (($worker===null)!==($campaign===null)) throw new AeriqError('Asigna trabajador y campaña juntos, o deja ambos vacíos.');
    if ($worker && !aeriq_one("SELECT id FROM usuarios WHERE id=? AND organizacion_id=? AND rol='trabajador' AND (activo=1 OR debe_cambiar_clave=1)",[$worker,$org])) throw new AeriqError('Trabajador no autorizado.',403);
    if ($campaign) aeriq_own('campanas',$campaign,$org);
    $active=aeriq_bool($d['active']??true);$simulated=aeriq_bool($d['simulated']??true);$name=aeriq_text($d['name']??'',160);
    $db=conexionBD();$db->begin_transaction();
    try {
        if ($id) {
            $previous=aeriq_one('SELECT id FROM dispositivos WHERE id=? AND organizacion_id=? FOR UPDATE',[$id,$org]);
            if (!$previous) throw new AeriqError('Dispositivo no disponible en tu organización.',404);
            aeriq_sql('UPDATE dispositivos SET nombre=?,direccion_ble=?,activo=?,simulado=? WHERE id=?',[$name,$mac,$active,$simulated,$id]);
        } else {aeriq_sql('INSERT INTO dispositivos(organizacion_id,nombre,direccion_ble,activo,simulado) VALUES(?,?,?,?,?)',[$org,$name,$mac,$active,$simulated]);$id=(int)$db->insert_id;}
        $old=aeriq_one('SELECT * FROM asignaciones WHERE dispositivo_id=? AND finalizada_en IS NULL FOR UPDATE',[$id]);
        $same=$old && $active && $worker && (int)$old['trabajador_id']===$worker && (int)$old['campana_id']===$campaign;
        if (!$same) {
            aeriq_sql('UPDATE asignaciones SET finalizada_en=UTC_TIMESTAMP(3) WHERE dispositivo_id=? AND finalizada_en IS NULL',[$id]);
            if ($active && $worker) aeriq_sql('INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id) VALUES(?,?,?,?)',[$org,$id,$worker,$campaign]);
        }
        $db->commit();return ['ok'=>true,'id'=>$id];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

function aeriq_assignments(array $a): array {
    return aeriq_rows('SELECT d.id,d.nombre,d.direccion_ble,a.id AS asignacion_id,a.campana_id,c.nombre AS campana,c.zona,c.publicar,c.umbral_medio,c.umbral_alto,d.simulado FROM asignaciones a JOIN dispositivos d ON d.id=a.dispositivo_id JOIN campanas c ON c.id=a.campana_id WHERE a.organizacion_id=? AND a.trabajador_id=? AND a.finalizada_en IS NULL AND d.activo=1 AND c.activa=1 AND UTC_DATE() BETWEEN c.inicio AND c.fin',[aeriq_org($a),(int)$a['usuario_id']]);
}

function aeriq_capture_time(mixed $v): string {
    $text=aeriq_text($v,30);if (!preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/D',$text)) throw new AeriqError('Fecha de captura UTC no válida.');
    $time=new DateTimeImmutable($text);
    if ($time->format(str_contains($text,'.')?'Y-m-d\TH:i:s.v\Z':'Y-m-d\TH:i:s\Z')!==$text) throw new AeriqError('Fecha de captura inexistente.');
    if ($time->getTimestamp()>time()+300 || $time->getTimestamp()<time()-86400*30) throw new AeriqError('Fecha de captura fuera de rango.');
    return $time->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.v');
}
function aeriq_ingest(array $a,array $d): array {
    $device=aeriq_int($d['deviceId']??null);$campaign=aeriq_int($d['campaignId']??null);$event=aeriq_text($d['eventId']??'',36);
    if (!preg_match('/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/Di',$event)) throw new AeriqError('Identificador de lectura no válido.');
    $type=aeriq_int($d['type']??null);$magnitude=aeriq_one('SELECT * FROM magnitudes WHERE id=?',[$type]);
    if (!$magnitude) throw new AeriqError('Magnitud no admitida.');
    if (isset($d['unit']) && $d['unit']!==$magnitude['unidad']) throw new AeriqError('La unidad no corresponde a esta magnitud.');
    $value=aeriq_number($d['value']??null,(float)$magnitude['valor_minimo'],(float)$magnitude['valor_maximo']);
    $captured=aeriq_capture_time($d['capturedAt']??'');$count=aeriq_int($d['counter']??null,0,255);$rssi=aeriq_int($d['rssi']??null,-127,20);
    $lat=$lon=$accuracy=null;
    if (isset($d['latitude']) || isset($d['longitude']) || isset($d['accuracy'])) {
        $lat=aeriq_number($d['latitude']??null,-90,90);$lon=aeriq_number($d['longitude']??null,-180,180);$accuracy=aeriq_number($d['accuracy']??null,0,10000);
    }
    $db=conexionBD();$db->begin_transaction();
    try {
        $orb=aeriq_one('SELECT d.*,a.id AS asignacion_id,a.trabajador_id,a.campana_id,a.iniciada_en,c.inicio,c.fin,c.activa AS campana_activa FROM dispositivos d JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL JOIN campanas c ON c.id=a.campana_id WHERE d.id=? AND d.organizacion_id=? AND a.trabajador_id=? FOR UPDATE',[$device,aeriq_org($a),(int)$a['usuario_id']]);
        if (!$orb || !(int)$orb['activo'] || !(int)$orb['campana_activa']) throw new AeriqError('Orb o campaña no asignados o inactivos.',403);
        if ((int)$orb['campana_id']!==$campaign || (isset($d['assignmentId']) && (int)$d['assignmentId']!==(int)$orb['asignacion_id'])) throw new AeriqError('La asignación ha cambiado. Actualiza tus asignaciones.',409);
        if (strtoupper((string)($d['address']??''))!==$orb['direccion_ble']) throw new AeriqError('El anuncio no corresponde al Orb asignado.',403);
        $date=substr($captured,0,10);if ($date<$orb['inicio'] || $date>$orb['fin'] || $captured<$orb['iniciada_en']) throw new AeriqError('Lectura fuera de las fechas de la asignación/campaña.');
        $old=aeriq_one('SELECT m.*,a.trabajador_id,a.dispositivo_id,a.campana_id FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id WHERE m.evento=?',[$event]);
        if ($old) {
            if ((int)$old['dispositivo_id']!==$device || (int)$old['trabajador_id']!==(int)$a['usuario_id'] || (int)$old['campana_id']!==$campaign || (int)$old['tipo']!==$type || (float)$old['valor']!==$value || $old['capturada_en']!==$captured || ($old['latitud']===null?null:(float)$old['latitud'])!==$lat || ($old['longitud']===null?null:(float)$old['longitud'])!==$lon || ($old['precision_m']===null?null:(float)$old['precision_m'])!==$accuracy || (int)$old['contador']!==$count || (int)$old['rssi']!==$rssi) throw new AeriqError('La clave de lectura ya identifica otros datos.',409);
            $db->commit();return ['ok'=>true,'duplicate'=>true,'id'=>(int)$old['id'],'eventId'=>$event];
        }
        aeriq_sql('INSERT INTO mediciones(evento,asignacion_id,tipo,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado) VALUES(?,?,?,?,?,?,?,?,?,?,?)',[$event,(int)$orb['asignacion_id'],$type,$value,$captured,$lat,$lon,$accuracy,$count,$rssi,(int)$orb['simulado']]);
        $id=(int)$db->insert_id;$db->commit();return ['ok'=>true,'duplicate'=>false,'id'=>$id,'eventId'=>$event];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

function aeriq_worker_history(array $a): array {
    return aeriq_rows('SELECT m.id,a.dispositivo_id,a.campana_id,m.valor,t.unidad,m.tipo,m.capturada_en,m.latitud,m.longitud,m.simulado,d.nombre AS dispositivo,c.nombre AS campana,c.zona,c.umbral_medio,c.umbral_alto FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN dispositivos d ON d.id=a.dispositivo_id JOIN campanas c ON c.id=a.campana_id JOIN magnitudes t ON t.id=m.tipo WHERE a.organizacion_id=? AND a.trabajador_id=? AND m.valida=1 ORDER BY m.capturada_en DESC,m.id DESC LIMIT 1000',[aeriq_org($a),(int)$a['usuario_id']]);
}

function aeriq_dashboard(array $a): array {
    $org=aeriq_org($a);
    return [
        'workers'=>aeriq_rows('SELECT id,nombre,apellidos,email,rol,activo,debe_cambiar_clave AS cambiar_clave,invitacion_expira_en FROM usuarios WHERE organizacion_id=? ORDER BY nombre',[$org]),
        'campaigns'=>aeriq_rows('SELECT * FROM campanas WHERE organizacion_id=? ORDER BY inicio DESC',[$org]),
        'devices'=>aeriq_rows('SELECT d.*,a.trabajador_id,a.campana_id,u.email AS trabajador,c.nombre AS campana,(SELECT MAX(m.capturada_en) FROM mediciones m JOIN asignaciones h ON h.id=m.asignacion_id WHERE h.dispositivo_id=d.id) AS ultima_medicion,(SELECT MAX(m.recibida_en) FROM mediciones m JOIN asignaciones h ON h.id=m.asignacion_id WHERE h.dispositivo_id=d.id) AS ultima_recepcion FROM dispositivos d LEFT JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL LEFT JOIN usuarios u ON u.id=a.trabajador_id LEFT JOIN campanas c ON c.id=a.campana_id WHERE d.organizacion_id=? ORDER BY d.nombre',[$org])
    ];
}

function aeriq_readings(array $a,array $filters): array {
    $where=['c.organizacion_id=?','l.tipo=14','l.valida=1'];$params=[aeriq_org($a)];
    foreach (['campaign'=>'a.campana_id','device'=>'a.dispositivo_id'] as $key=>$col) {
        if (!empty($filters[$key])) { $where[]="$col=?";$params[]=aeriq_int($filters[$key]); }
    }
    if (!empty($filters['devices'])) {
        $ids=array_values(array_unique(array_map(fn($v)=>aeriq_int($v),explode(',',(string)$filters['devices']))));
        if (count($ids)>100) throw new AeriqError('Selecciona como máximo 100 dispositivos.');
        $where[]='a.dispositivo_id IN ('.implode(',',array_fill(0,count($ids),'?')).')';array_push($params,...$ids);
    }
    if (!empty($filters['from']) && !empty($filters['to']) && $filters['from']>$filters['to']) throw new AeriqError('El intervalo de fechas está invertido.');
    if (!empty($filters['zone'])) { $where[]='c.zona=?';$params[]=aeriq_text($filters['zone']); }
    foreach (['from'=>'>=','to'=>'<='] as $key=>$op) {
        if (!empty($filters[$key])) {
            $date=aeriq_text($filters[$key],10);$v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);
            if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Filtro de fecha no válido.');
            $where[]="l.capturada_en $op ?";$params[]=$date.($key==='from'?' 00:00:00.000':' 23:59:59.999');
        }
    }
    $rows=aeriq_rows('SELECT l.id,l.evento,a.dispositivo_id,a.campana_id,l.valor,t.unidad,l.capturada_en,l.latitud,l.longitud,l.simulado,d.nombre AS dispositivo,c.nombre AS campana,c.zona,c.umbral_medio,c.umbral_alto FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.tipo JOIN campanas c ON c.id=a.campana_id JOIN dispositivos d ON d.id=a.dispositivo_id WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en,l.id LIMIT 10001',$params);
    if (count($rows)>10000) throw new AeriqError('Demasiados registros. Reduce el intervalo o selecciona campaña/dispositivo; no se exportarán datos incompletos.',413);
    return $rows;
}
function aeriq_public_map(array $query): array {return aeriq_map_data(null,$query);}
function aeriq_map_data(?array $access,array $query): array {
    $mode=$query['mode']??'real';if (!in_array($mode,['real','simulated'],true)) throw new AeriqError('Selecciona datos reales o simulados.');
    $hours=aeriq_int($query['hours']??24,0,168);if(!in_array($hours,[0,24,168],true))throw new AeriqError('Periodo no válido.');
    $where=['l.valida=1','l.tipo=14','l.latitud IS NOT NULL','l.longitud IS NOT NULL','l.simulado=?'];$params=[$mode==='simulated'?1:0];
    if ($access) {$where[]='a.organizacion_id=?';$params[]=aeriq_org($access);if($access['rol']==='trabajador'){$where[]='a.trabajador_id=?';$params[]=(int)$access['usuario_id'];}}
    else $where[]='c.publicar=1';
    if($hours)$where[]='l.capturada_en>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL '.$hours.' HOUR)';
    $rows=aeriq_rows('SELECT l.id,l.valor,t.unidad,l.capturada_en,l.latitud,l.longitud,l.simulado,c.zona,c.id AS campaignId,c.radio_m,c.umbral_medio,c.umbral_alto FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.tipo JOIN campanas c ON c.id=a.campana_id WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en DESC,l.id DESC LIMIT 2001',$params);
    $truncated=count($rows)>2000;$rows=array_slice($rows,0,2000);
    foreach ($rows as &$r) {foreach (['valor','latitud','longitud','umbral_medio','umbral_alto'] as $k)$r[$k]=(float)$r[$k];$r['simulado']=(bool)$r['simulado'];$r['radio_m']=(int)$r['radio_m'];}
    unset($r);return ['measurements'=>$rows,'truncated'=>$truncated,'mode'=>$mode,'hours'=>$hours];
}
function aeriq_request(array $d): array {
    if (($d['consent']??false)!==true) throw new AeriqError('Debes autorizar el tratamiento de los datos para gestionar la solicitud.');
    $service=$d['service']??'';if (!in_array($service,['administracion','empresa','estudio'],true)) throw new AeriqError('Servicio no válido.');
    $entity=aeriq_text($d['entity']??'',180);$contact=aeriq_text($d['contact']??'',160);$email=auth_email($d['email']??'');
    $phone=aeriq_text($d['phone']??'',40,true);$zone=aeriq_text($d['zone']??'');$needs=aeriq_text($d['needs']??'',6000);
    $count=$service==='estudio'?null:aeriq_int($d['quantity']??null,1,100000);
    $surface=$service==='estudio'?aeriq_number($d['surface']??null,0.01,1e12):null;
    $duration=aeriq_text($d['duration']??'',100,$service!=='estudio');
    aeriq_sql('INSERT INTO solicitudes(servicio,entidad,contacto,email,telefono,cantidad,zona,superficie,duracion,necesidades) VALUES(?,?,?,?,?,?,?,?,?,?)',[$service,$entity,$contact,$email,$phone,$count,$zone,$surface,$duration,$needs]);$id=(int)conexionBD()->insert_id;
    // El registro es la fuente de verdad: un fallo del aviso no invalida la solicitud.
    $notify=getenv('AERIQ_COMMERCIAL_EMAIL');
    if ($notify && filter_var($notify,FILTER_VALIDATE_EMAIL)) {
        try { aeriq_mail($notify,'AERIQ · Nueva solicitud #'.$id,"Consulta el panel comercial. Servicio: $service. Solicitud: $id."); }
        catch (Throwable $e) { error_log('AERIQ: solicitud registrada; aviso comercial no entregado.'); }
    }
    return ['ok'=>true,'id'=>$id,'message'=>'Solicitud registrada. Código Norte revisará tus necesidades antes de preparar el presupuesto definitivo.'];
}
