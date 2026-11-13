<?php
declare(strict_types=1);

/** Dominio AERIQ. Tablas nuevas aisladas de las tablas heredadas de Sprint 0. */
final class AeriqError extends RuntimeException {
    /** $field: nombre del campo del formulario que provoca el error, para marcarlo en la web. */
    public function __construct(string $message, public int $status = 422, public ?string $field = null) { parent::__construct($message); }
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
/** Identidad, organización, rol y permisos de una cuenta, leídos siempre del servidor. */
function aeriq_access(int $id): array {
    $a=aeriq_one('SELECT u.id AS usuario_id,u.organizacion_id,o.nombre AS organizacion,u.rol_id,r.codigo AS rol,r.nombre AS rol_nombre,r.requiere_organizacion,u.debe_cambiar_clave AS cambiar_clave,u.activo,u.invitacion_expira_en FROM usuarios u JOIN roles r ON r.id=u.rol_id LEFT JOIN organizaciones o ON o.id=u.organizacion_id WHERE u.id=?',[$id]);
    if (!$a) throw new AeriqError('Cuenta no disponible.',401);
    $a['permisos']=array_column(aeriq_rows('SELECT p.codigo FROM rol_permisos rp JOIN permisos p ON p.id=rp.permiso_id WHERE rp.rol_id=? ORDER BY p.codigo',[(int)$a['rol_id']]),'codigo');
    // Un rol de organización sin organización no concede permisos (antes era un CHECK sobre el ENUM).
    if ((int)$a['requiere_organizacion'] && !$a['organizacion_id']) $a['permisos']=[];
    return $a;
}
function aeriq_can(array $access, string $permission): bool { return in_array($permission,$access['permisos']??[],true); }

/** Comprueba que la cuenta está activa y, si se indica, que su rol concede el permiso. */
function aeriq_ready(array $u, ?string $permission = null): array {
    auth_login_allowed($u); $a=aeriq_access((int)$u['id']);
    if ((int)$a['cambiar_clave']) throw new AeriqError('Activa tu cuenta sustituyendo la contraseña temporal.',428);
    if (!(int)$a['activo']) throw new AeriqError('La cuenta está desactivada.',403);
    if ($permission!==null && !aeriq_can($a,$permission)) throw new AeriqError('Tu función no permite esta operación.',403);
    return $a;
}
/** SQL reutilizable: cuentas (alias u) cuyo rol permite registrar mediciones, es decir, asignables a un Orb. */
const AERIQ_PUEDE_MEDIR="EXISTS (SELECT 1 FROM rol_permisos rp JOIN permisos p ON p.id=rp.permiso_id WHERE rp.rol_id=u.rol_id AND p.codigo='mediciones.registrar')";
/** Umbral de ozono de la campaña c: el propio o, si no lo tiene, el de referencia de la magnitud. */
const AERIQ_UMBRALES_O3='COALESCE(cu.umbral_medio,mo.umbral_medio_defecto) AS umbral_medio,COALESCE(cu.umbral_alto,mo.umbral_alto_defecto) AS umbral_alto';
const AERIQ_JOIN_UMBRALES_O3='LEFT JOIN campana_umbrales cu ON cu.campana_id=c.id AND cu.magnitud_id=14 JOIN magnitudes mo ON mo.id=14';
/** Umbral de la campaña c para la magnitud t de cada lectura (la temperatura no tiene). */
/** Última batería conocida del Orb d (porcentaje y fecha UTC de captura). */
const AERIQ_BATERIA='(SELECT b.porcentaje FROM lecturas_bateria b WHERE b.dispositivo_id=d.id ORDER BY b.capturada_en DESC,b.id DESC LIMIT 1) AS bateria_pct,(SELECT MAX(b.capturada_en) FROM lecturas_bateria b WHERE b.dispositivo_id=d.id) AS bateria_en';
const AERIQ_UMBRALES_LECTURA='COALESCE(cu.umbral_medio,t.umbral_medio_defecto) AS umbral_medio,COALESCE(cu.umbral_alto,t.umbral_alto_defecto) AS umbral_alto';

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

/** $replyTo (opcional) permite contestar directamente a quien envió el formulario. */
function aeriq_mail(string $recipient,string $subject,string $text,?string $replyTo=null): void {
    $cfg=auth_mail_config_ok();
    if ($replyTo!==null && !filter_var($replyTo,FILTER_VALIDATE_EMAIL)) $replyTo=null;
    if ($cfg['transport']==='smtp_native') {
        try { auth_smtp_send($cfg['smtp'],$recipient,$subject,$text,$replyTo); }
        catch (Throwable $ex) {
            error_log('AERIQ: fallo en SMTP directo (sin registrar credenciales): '.$ex->getMessage());
            throw new AeriqError('No se pudo enviar el correo por SMTP. Comprueba la contraseña de aplicación y el acceso saliente del servidor.',503);
        }
        return;
    }
    if ($cfg['transport']==='smtp') {
        require_once dirname(__DIR__).'/vendor/autoload.php';
        $c=$cfg['smtp']; $m=new PHPMailer\PHPMailer\PHPMailer(true); $m->isSMTP();
        $m->Host=$c['host'];$m->Port=(int)$c['port'];$m->SMTPAuth=true;$m->Username=$c['username'];$m->Password=$c['password'];
        $m->SMTPSecure=($c['encryption']??'tls')==='ssl'?'ssl':'tls';$m->CharSet='UTF-8';
        $m->setFrom($c['from_email'],$c['from_name']??'Código Norte');$m->addAddress($recipient);if($replyTo!==null)$m->addReplyTo($replyTo);$m->Subject=$subject;$m->Body=$text;$m->send();return;
    }
    $headers=['From: '.$cfg['from_name'].' <'.$cfg['from_email'].'>','Content-Type: text/plain; charset=UTF-8'];
    if ($replyTo!==null) $headers[]='Reply-To: '.$replyTo;
    if (!mail($recipient,'=?UTF-8?B?'.base64_encode($subject).'?=',$text,implode("\r\n",$headers))) throw new AeriqError('El servidor no ha aceptado el correo.',503);
}
function aeriq_worker(array $a,array $d): array {
    $org=aeriq_org($a);$email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);$last=auth_string($d['last']??'',120);
    // Solo roles marcados como asignables: un administrador no puede crear otro administrador.
    $role=aeriq_one('SELECT id FROM roles WHERE codigo=? AND asignable=1 AND requiere_organizacion=1',[is_string($d['role']??null)?$d['role']:'trabajador']);
    if (!$role) throw new AeriqError('Función no permitida.');
    $hours=max(1,min(168,(int)(getenv('AERIQ_INVITATION_HOURS')?:24)));
    auth_mail_config_ok();$db=conexionBD();$db->begin_transaction();
    try {
        if (auth_find($email)) throw new AeriqError('Ese correo ya tiene una cuenta. No se cambiará su organización.',409);
        $temporary=bin2hex(random_bytes(10));$hash=password_hash($temporary,PASSWORD_DEFAULT);
        aeriq_sql('INSERT INTO usuarios(organizacion_id,rol_id,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,invitacion_expira_en) VALUES(?,?,?,?,?,?,0,1,DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? HOUR))',[$org,(int)$role['id'],$name,$last,$email,$hash,$hours]);$id=(int)$db->insert_id;
        aeriq_mail($email,'AERIQ · Invitación de tu organización',"Tu organización te invita a AERIQ.\nCorreo: $email\nContraseña temporal: $temporary\n\nAbre AERIQ e inicia sesión con este mismo correo. Puedes entrar con Google (la cuenta se activa en el primer acceso) o con la contraseña temporal (cámbiala para activar la cuenta). El acceso temporal caduca en $hours horas. Si caduca, recupera el acceso desde la web o la app.\nCódigo Norte · Orb");
        $db->commit();return ['ok'=>true,'id'=>$id,'message'=>'Invitación registrada. El servidor ha aceptado el correo; la cuenta se activará al cambiar la contraseña.'];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

function aeriq_campaign_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])?aeriq_int($d['id']):null;
    if ($id) aeriq_own('campanas',$id,$org);
    $start=aeriq_text($d['start']??'',10);$end=aeriq_text($d['end']??'',10);
    foreach ([$start,$end] as $date) { $v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Fecha no válida.'); }
    if ($end<$start) throw new AeriqError('La fecha final debe ser posterior al inicio.');
    $ref=aeriq_one('SELECT umbral_medio_defecto AS medio,umbral_alto_defecto AS alto FROM magnitudes WHERE id=14');
    $mid=aeriq_number($d['medium']??$ref['medio'],0,65535);$high=aeriq_number($d['high']??$ref['alto'],0,65535);if ($high<=$mid) throw new AeriqError('El umbral alto debe superar al medio.');
    $name=aeriq_text($d['name']??'',160);$zoneName=aeriq_text($d['zone']??'');
    $flags=[$start,$end,aeriq_bool($d['active']??true),aeriq_bool($d['public']??false),aeriq_int($d['radius']??30,20,50)];
    $db=conexionBD();$db->begin_transaction();
    try {
        $zone=aeriq_zone($org,$zoneName);
        if ($id) { aeriq_sql('UPDATE campanas SET nombre=?,zona_id=?,inicio=?,fin=?,activa=?,publicar=?,radio_m=? WHERE id=? AND organizacion_id=?',[$name,$zone,...$flags,$id,$org]); }
        else { aeriq_sql('INSERT INTO campanas(nombre,zona_id,inicio,fin,activa,publicar,radio_m,organizacion_id) VALUES(?,?,?,?,?,?,?,?)',[$name,$zone,...$flags,$org]);$id=(int)$db->insert_id; }
        aeriq_sql('INSERT INTO campana_umbrales(campana_id,magnitud_id,umbral_medio,umbral_alto) VALUES(?,14,?,?) ON DUPLICATE KEY UPDATE umbral_medio=VALUES(umbral_medio),umbral_alto=VALUES(umbral_alto)',[$id,$mid,$high]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true,'id'=>$id];
}
/** Devuelve la zona de la organización con ese nombre, creándola si aún no existe. */
function aeriq_zone(int $org,string $name): int {
    aeriq_sql('INSERT IGNORE INTO zonas(organizacion_id,nombre) VALUES(?,?)',[$org,$name]);
    return (int)aeriq_one('SELECT id FROM zonas WHERE organizacion_id=? AND nombre=?',[$org,$name])['id'];
}
function aeriq_device_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])?aeriq_int($d['id']):null;
    if($id)aeriq_own('dispositivos',$id,$org);
    $mac=strtoupper(aeriq_text($d['address']??'',17));if (!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D',$mac)) throw new AeriqError('Dirección BLE no válida.');
    $worker=empty($d['worker'])?null:aeriq_int($d['worker']);$campaign=empty($d['campaign'])?null:aeriq_int($d['campaign']);
    if (($worker===null)!==($campaign===null)) throw new AeriqError('Asigna trabajador y campaña juntos, o deja ambos vacíos.');
    if ($worker && !aeriq_one('SELECT u.id FROM usuarios u WHERE u.id=? AND u.organizacion_id=? AND '.AERIQ_PUEDE_MEDIR.' AND (u.activo=1 OR u.debe_cambiar_clave=1)',[$worker,$org])) throw new AeriqError('Trabajador no autorizado.',403);
    if ($campaign) aeriq_own('campanas',$campaign,$org);
    $active=aeriq_bool($d['active']??true);$simulated=aeriq_bool($d['simulated']??false);$name=aeriq_text($d['name']??'',160);
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
    return aeriq_rows('SELECT d.id,d.nombre,d.direccion_ble,a.id AS asignacion_id,a.campana_id,c.nombre AS campana,z.nombre AS zona,c.publicar,'.AERIQ_UMBRALES_O3.',d.simulado,'.AERIQ_BATERIA.' FROM asignaciones a JOIN dispositivos d ON d.id=a.dispositivo_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.' WHERE a.organizacion_id=? AND a.trabajador_id=? AND a.finalizada_en IS NULL AND d.activo=1 AND c.activa=1 AND UTC_DATE() BETWEEN c.inicio AND c.fin',[aeriq_org($a),(int)$a['usuario_id']]);
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
    $type=aeriq_int($d['type']??null);
    // La magnitud debe existir y ser una de las que mide el modelo de ese Orb.
    $magnitude=aeriq_one('SELECT t.* FROM magnitudes t JOIN modelo_magnitudes mm ON mm.magnitud_id=t.id JOIN dispositivos d ON d.modelo_id=mm.modelo_id WHERE t.id=? AND d.id=?',[$type,$device]);
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
            if ((int)$old['dispositivo_id']!==$device || (int)$old['trabajador_id']!==(int)$a['usuario_id'] || (int)$old['campana_id']!==$campaign || (int)$old['magnitud_id']!==$type || (float)$old['valor']!==$value || $old['capturada_en']!==$captured || ($old['latitud']===null?null:(float)$old['latitud'])!==$lat || ($old['longitud']===null?null:(float)$old['longitud'])!==$lon || ($old['precision_m']===null?null:(float)$old['precision_m'])!==$accuracy || (int)$old['contador']!==$count || (int)$old['rssi']!==$rssi) throw new AeriqError('La clave de lectura ya identifica otros datos.',409);
            $db->commit();return ['ok'=>true,'duplicate'=>true,'id'=>(int)$old['id'],'eventId'=>$event];
        }
        aeriq_sql('INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado) VALUES(?,?,?,?,?,?,?,?,?,?,?)',[$event,(int)$orb['asignacion_id'],$type,$value,$captured,$lat,$lon,$accuracy,$count,$rssi,(int)$orb['simulado']]);
        $id=(int)$db->insert_id;$db->commit();return ['ok'=>true,'duplicate'=>false,'id'=>$id,'eventId'=>$event];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

/**
 * Batería que el Orb anuncia por BLE, enviada por la app de quien lo tiene asignado.
 * Solo Orb reales: un Orb simulado no tiene batería que medir. Para no llenar la tabla,
 * el mismo porcentaje en menos de 10 minutos no crea otra fila.
 */
function aeriq_battery(array $a,array $d): array {
    $device=aeriq_int($d['deviceId']??null);$percent=aeriq_int($d['percent']??null,0,100);
    $captured=aeriq_capture_time($d['capturedAt']??'');
    $orb=aeriq_one('SELECT d.id,d.direccion_ble,d.activo,d.simulado FROM dispositivos d JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL WHERE d.id=? AND d.organizacion_id=? AND a.trabajador_id=?',[$device,aeriq_org($a),(int)$a['usuario_id']]);
    if (!$orb || !(int)$orb['activo']) throw new AeriqError('Orb no asignado o inactivo.',403);
    if ((int)$orb['simulado']) throw new AeriqError('Un Orb simulado no tiene batería que registrar.');
    if (strtoupper((string)($d['address']??''))!==$orb['direccion_ble']) throw new AeriqError('El anuncio no corresponde al Orb asignado.',403);
    $last=aeriq_one('SELECT porcentaje,capturada_en FROM lecturas_bateria WHERE dispositivo_id=? ORDER BY capturada_en DESC,id DESC LIMIT 1',[$device]);
    if ($last && (int)$last['porcentaje']===$percent && strtotime($captured.' UTC')-strtotime($last['capturada_en'].' UTC')<600) {
        return ['ok'=>true,'stored'=>false,'percent'=>$percent];
    }
    aeriq_sql('INSERT INTO lecturas_bateria(dispositivo_id,porcentaje,capturada_en,usuario_id) VALUES(?,?,?,?)',[$device,$percent,$captured,(int)$a['usuario_id']]);
    return ['ok'=>true,'stored'=>true,'percent'=>$percent];
}

function aeriq_worker_history(array $a): array {
    return aeriq_rows('SELECT m.id,a.dispositivo_id,a.campana_id,m.valor,t.unidad,m.magnitud_id AS tipo,m.capturada_en,m.latitud,m.longitud,m.simulado,d.nombre AS dispositivo,c.nombre AS campana,z.nombre AS zona,'.AERIQ_UMBRALES_LECTURA.' FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN dispositivos d ON d.id=a.dispositivo_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id JOIN magnitudes t ON t.id=m.magnitud_id LEFT JOIN campana_umbrales cu ON cu.campana_id=c.id AND cu.magnitud_id=m.magnitud_id WHERE a.organizacion_id=? AND a.trabajador_id=? AND m.valida=1 ORDER BY m.capturada_en DESC,m.id DESC LIMIT 1000',[aeriq_org($a),(int)$a['usuario_id']]);
}

function aeriq_dashboard(array $a): array {
    $org=aeriq_org($a);
    $ref=aeriq_one('SELECT umbral_medio_defecto AS umbral_medio,umbral_alto_defecto AS umbral_alto FROM magnitudes WHERE id=14');
    return [
        'workers'=>aeriq_rows('SELECT u.id,u.nombre,u.apellidos,u.email,r.codigo AS rol,r.nombre AS rol_nombre,'.AERIQ_PUEDE_MEDIR.' AS puede_medir,u.activo,u.debe_cambiar_clave AS cambiar_clave,u.invitacion_expira_en FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.organizacion_id=? ORDER BY u.nombre',[$org]),
        'campaigns'=>aeriq_rows('SELECT c.*,z.nombre AS zona,'.AERIQ_UMBRALES_O3.' FROM campanas c JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.' WHERE c.organizacion_id=? ORDER BY c.inicio DESC',[$org]),
        'zones'=>aeriq_rows('SELECT id,nombre FROM zonas WHERE organizacion_id=? ORDER BY nombre',[$org]),
        'roles'=>aeriq_rows('SELECT codigo,nombre,descripcion FROM roles WHERE asignable=1 AND requiere_organizacion=1 ORDER BY id'),
        'thresholds'=>['umbral_medio'=>(float)$ref['umbral_medio'],'umbral_alto'=>(float)$ref['umbral_alto']],
        'devices'=>aeriq_rows('SELECT d.*,md.nombre AS modelo,'.AERIQ_BATERIA.',a.trabajador_id,a.campana_id,u.email AS trabajador,c.nombre AS campana,(SELECT MAX(m.capturada_en) FROM mediciones m JOIN asignaciones h ON h.id=m.asignacion_id WHERE h.dispositivo_id=d.id) AS ultima_medicion,(SELECT MAX(m.recibida_en) FROM mediciones m JOIN asignaciones h ON h.id=m.asignacion_id WHERE h.dispositivo_id=d.id) AS ultima_recepcion FROM dispositivos d JOIN modelos_dispositivo md ON md.id=d.modelo_id LEFT JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL LEFT JOIN usuarios u ON u.id=a.trabajador_id LEFT JOIN campanas c ON c.id=a.campana_id WHERE d.organizacion_id=? ORDER BY d.nombre',[$org])
    ];
}

function aeriq_readings(array $a,array $filters): array {
    $where=['c.organizacion_id=?','l.valida=1','l.magnitud_id IN (12,14)'];$params=[aeriq_org($a)];
    $type=$filters['type']??14;
    if ($type!=='all') {
        $type=aeriq_int($type);
        if (!in_array($type,[12,14],true)) throw new AeriqError('Magnitud no admitida.');
        $where[]='l.magnitud_id=?';$params[]=$type;
    }
    foreach (['campaign'=>'a.campana_id','device'=>'a.dispositivo_id'] as $key=>$col) {
        if (!empty($filters[$key])) { $where[]="$col=?";$params[]=aeriq_int($filters[$key]); }
    }
    if (!empty($filters['devices'])) {
        $ids=array_values(array_unique(array_map(fn($v)=>aeriq_int($v),explode(',',(string)$filters['devices']))));
        if (count($ids)>100) throw new AeriqError('Selecciona como máximo 100 dispositivos.');
        $where[]='a.dispositivo_id IN ('.implode(',',array_fill(0,count($ids),'?')).')';array_push($params,...$ids);
    }
    if (!empty($filters['from']) && !empty($filters['to']) && $filters['from']>$filters['to']) throw new AeriqError('El intervalo de fechas está invertido.');
    if (!empty($filters['zone'])) { $where[]='z.nombre=?';$params[]=aeriq_text($filters['zone']); }
    foreach (['from'=>'>=','to'=>'<='] as $key=>$op) {
        if (!empty($filters[$key])) {
            $date=aeriq_text($filters[$key],10);$v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);
            if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Filtro de fecha no válido.');
            $where[]="l.capturada_en $op ?";$params[]=$date.($key==='from'?' 00:00:00.000':' 23:59:59.999');
        }
    }
    $rows=aeriq_rows('SELECT l.id,l.evento,a.dispositivo_id,a.campana_id,l.magnitud_id AS tipo,l.valor,t.unidad,l.capturada_en,l.latitud,l.longitud,l.simulado,d.nombre AS dispositivo,c.nombre AS campana,z.nombre AS zona,'.AERIQ_UMBRALES_LECTURA.' FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.magnitud_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id JOIN dispositivos d ON d.id=a.dispositivo_id LEFT JOIN campana_umbrales cu ON cu.campana_id=c.id AND cu.magnitud_id=l.magnitud_id WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en,l.id LIMIT 10001',$params);
    if (count($rows)>10000) throw new AeriqError('Demasiados registros. Reduce el intervalo o selecciona campaña/dispositivo; no se exportarán datos incompletos.',413);
    return $rows;
}
function aeriq_public_map(array $query): array {return aeriq_map_data(null,$query);}
function aeriq_map_data(?array $access,array $query): array {
    $mode=$query['mode']??'real';if (!in_array($mode,['real','simulated'],true)) throw new AeriqError('Selecciona datos reales o simulados.');
    $hours=aeriq_int($query['hours']??24,0,168);if(!in_array($hours,[0,24,168],true))throw new AeriqError('Periodo no válido.');
    $where=['l.valida=1','l.magnitud_id=14','l.latitud IS NOT NULL','l.longitud IS NOT NULL','l.simulado=?'];$params=[$mode==='simulated'?1:0];
    if ($access) {$where[]='a.organizacion_id=?';$params[]=aeriq_org($access);if(!aeriq_can($access,'informes.ver')){$where[]='a.trabajador_id=?';$params[]=(int)$access['usuario_id'];}}
    else $where[]='c.publicar=1';
    if($hours)$where[]='l.capturada_en>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL '.$hours.' HOUR)';
    $rows=aeriq_rows('SELECT l.id,l.valor,t.unidad,l.capturada_en,l.latitud,l.longitud,l.simulado,z.nombre AS zona,c.id AS campaignId,c.radio_m,'.AERIQ_UMBRALES_O3.' FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.magnitud_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.' WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en DESC,l.id DESC LIMIT 2001',$params);
    $truncated=count($rows)>2000;$rows=array_slice($rows,0,2000);
    foreach ($rows as &$r) {foreach (['valor','latitud','longitud','umbral_medio','umbral_alto'] as $k)$r[$k]=(float)$r[$k];$r['simulado']=(bool)$r['simulado'];$r['radio_m']=(int)$r['radio_m'];}
    unset($r);return ['measurements'=>$rows,'truncated'=>$truncated,'mode'=>$mode,'hours'=>$hours];
}
/** Provincias de España: por ahora el formulario comercial solo atiende España. */
const AERIQ_PROVINCIAS=['A Coruña','Álava','Albacete','Alicante','Almería','Asturias','Ávila','Badajoz','Barcelona','Bizkaia','Burgos','Cáceres','Cádiz','Cantabria','Castellón','Ceuta','Ciudad Real','Córdoba','Cuenca','Gipuzkoa','Girona','Granada','Guadalajara','Huelva','Huesca','Illes Balears','Jaén','La Rioja','Las Palmas','León','Lleida','Lugo','Madrid','Málaga','Melilla','Murcia','Navarra','Ourense','Palencia','Pontevedra','Salamanca','Santa Cruz de Tenerife','Segovia','Sevilla','Soria','Tarragona','Teruel','Toledo','Valencia','Valladolid','Zamora','Zaragoza'];
/** Duraciones que se ofrecen para un estudio; se guardan tal cual en solicitudes.duracion. */
/** Buzón comercial de Código Norte que recibe las solicitudes del formulario público. */
const AERIQ_COMMERCIAL_EMAIL_DEFAULT='codigonorte.aeriq@gmail.com';
const AERIQ_DURACIONES=['1 semana','2 semanas','1 mes','3 meses','Más de 3 meses','A definir con Código Norte'];

/** Texto de un campo del formulario público; el error indica qué campo revisar. Longitud en caracteres, como maxlength. */
function aeriq_field_text(array $d,string $key,int $max,string $message,bool $optional=false): string {
    $v=$d[$key]??'';
    if (!is_string($v)) throw new AeriqError($message,422,$key);
    $v=trim($v);$length=function_exists('mb_strlen')?mb_strlen($v,'UTF-8'):strlen($v);
    if ((!$optional && $v==='') || $length>$max) throw new AeriqError($message,422,$key);
    return $v;
}
/** Solicitud comercial del formulario público (solicitar.html). No pide cantidad: la recomienda Código Norte. */
function aeriq_request(array $d): array {
    // Campo trampa: una persona no lo ve; si llega relleno es un bot. Se responde igual para no darle pistas.
    if (is_string($d['website']??null) && trim($d['website'])!=='') return ['ok'=>true,'id'=>0,'message'=>'Solicitud registrada.'];
    $service=aeriq_one('SELECT id,codigo,nombre,mide_superficie FROM servicios WHERE codigo=? AND activo=1',[is_string($d['interest']??null)?$d['interest']:'']);
    if (!$service) throw new AeriqError('Elige qué te interesa.',422,'interest');
    $org=aeriq_one('SELECT id,nombre FROM tipos_organizacion WHERE codigo=? AND activo=1',[is_string($d['orgType']??null)?$d['orgType']:'']);
    if (!$org) throw new AeriqError('Elige el tipo de organización.',422,'orgType');
    $entity=aeriq_field_text($d,'entity',180,'Escribe el nombre de la organización.');
    $contact=aeriq_field_text($d,'contact',160,'Escribe el nombre de la persona de contacto.');
    $email=strtolower(aeriq_field_text($d,'email',254,'Escribe un correo electrónico válido.'));
    if (!filter_var($email,FILTER_VALIDATE_EMAIL)) throw new AeriqError('Escribe un correo electrónico válido.',422,'email');
    $phone=aeriq_field_text($d,'phone',9,'El teléfono debe tener 9 cifras y empezar por 6, 7, 8 o 9.',true);
    if ($phone!=='' && !preg_match('/^[6789][0-9]{8}$/',$phone)) throw new AeriqError('El teléfono debe tener 9 cifras y empezar por 6, 7, 8 o 9.',422,'phone');
    $municipality=aeriq_field_text($d,'municipality',120,'Escribe el municipio.');
    $province=is_string($d['province']??null)?$d['province']:'';
    if (!in_array($province,AERIQ_PROVINCIAS,true)) throw new AeriqError('Elige una provincia.',422,'province');
    $zone=aeriq_field_text($d,'zone',200,'Describe la zona o las instalaciones que quieres medir.');
    $codes=$d['objectives']??null;
    if (!is_array($codes) || count($codes)>20) throw new AeriqError('Marca al menos un objetivo de la medición.',422,'objectives');
    $codes=array_values(array_unique(array_filter($codes,'is_string')));
    $objectives=$codes?aeriq_rows('SELECT id,nombre FROM objetivos_medicion WHERE activo=1 AND codigo IN ('.implode(',',array_fill(0,count($codes),'?')).')',$codes):[];
    if (!$codes || count($objectives)!==count($codes)) throw new AeriqError('Marca al menos un objetivo de la medición.',422,'objectives');
    $needs=aeriq_field_text($d,'needs',6000,'Cuéntanos qué necesitas (máximo 6000 caracteres).');
    if (($d['consent']??false)!==true) throw new AeriqError('Debes autorizar el tratamiento de los datos para gestionar la solicitud.',422,'consent');
    // Superficie, duración e inicio solo se piden en los servicios que se presupuestan por zona (estudios).
    $surface=null;$duration='';$start=null;
    if ((int)$service['mide_superficie']) {
        $raw=$d['surface']??null;
        if ($raw!==null && $raw!=='') {
            if (!is_numeric($raw) || !is_finite((float)$raw) || (float)$raw<=0 || (float)$raw>1e12) throw new AeriqError('La superficie debe ser un número mayor que 0. Si no la sabes, déjala vacía.',422,'surface');
            $surface=(float)$raw;
        }
        $duration=is_string($d['duration']??null)?$d['duration']:'';
        if (!in_array($duration,AERIQ_DURACIONES,true)) throw new AeriqError('Elige la duración del estudio.',422,'duration');
        $month=$d['startMonth']??null;
        if ($month!==null && $month!=='') {
            $m=is_string($month)?DateTimeImmutable::createFromFormat('!Y-m',$month,new DateTimeZone('UTC')):false;
            if (!$m || $m->format('Y-m')!==$month || $month<gmdate('Y-m')) throw new AeriqError('Elige un mes de inicio a partir del actual.',422,'startMonth');
            $start=$m->format('Y-m-d');
        }
    }
    $db=conexionBD();$db->begin_transaction();
    try {
        // Toda solicitud nace en el primer estado del catálogo y queda registrada en el historial.
        aeriq_sql('INSERT INTO solicitudes(servicio_id,tipo_organizacion_id,estado_id,entidad,contacto,email,telefono,municipio,provincia,zona,superficie,duracion,inicio_deseado,necesidades) VALUES(?,?,(SELECT id FROM estados_solicitud ORDER BY orden LIMIT 1),?,?,?,?,?,?,?,?,?,?,?)',[(int)$service['id'],(int)$org['id'],$entity,$contact,$email,$phone,$municipality,$province,$zone,$surface,$duration,$start,$needs]);$id=(int)$db->insert_id;
        foreach ($objectives as $o) aeriq_sql('INSERT INTO solicitud_objetivos(solicitud_id,objetivo_id) VALUES(?,?)',[$id,(int)$o['id']]);
        aeriq_sql('INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id) SELECT id,estado_id,NULL FROM solicitudes WHERE id=?',[$id]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    $service=$service['nombre'];
    // El registro es la fuente de verdad: un fallo del aviso no invalida la solicitud.
    // AERIQ_COMMERCIAL_EMAIL (variable de entorno) sustituye al buzón por defecto de Código Norte.
    $notify=getenv('AERIQ_COMMERCIAL_EMAIL')?:AERIQ_COMMERCIAL_EMAIL_DEFAULT;
    if (filter_var($notify,FILTER_VALIDATE_EMAIL)) {
        $lines=['Nueva solicitud desde el formulario de AERIQ.','',
            'Solicitud: #'.$id,'Servicio: '.$service,'Tipo de organización: '.$org['nombre'],'Organización: '.$entity,
            'Contacto: '.$contact,'Correo: '.$email,'Teléfono: '.($phone!==''?$phone:'—'),
            'Municipio: '.$municipality.' ('.$province.')','Zona: '.$zone,
            'Objetivos: '.implode(', ',array_column($objectives,'nombre'))];
        if ($surface!==null) $lines[]='Superficie: '.rtrim(rtrim(number_format($surface,2,',','.'),'0'),',').' m²';
        if ($duration!=='') $lines[]='Duración: '.$duration;
        if ($start!==null) $lines[]='Inicio deseado: '.substr($start,0,7);
        array_push($lines,'','Necesidades:',$needs,'','Responde a este correo para contestar directamente a '.$contact.'.','La solicitud también está en el panel comercial de AERIQ.');
        try { aeriq_mail($notify,'AERIQ · Nueva solicitud #'.$id.' · '.$entity,implode("\n",$lines),$email); }
        catch (Throwable $e) { error_log('AERIQ: solicitud registrada; aviso comercial no entregado.'); }
    }
    return ['ok'=>true,'id'=>$id,'message'=>'Solicitud registrada. Código Norte la revisará y te propondrá la solución que mejor encaje.'];
}

/** Catálogos del formulario público: servicios, tipos de organización, objetivos, provincias y duraciones. */
function aeriq_request_catalogs(): array {
    $services=aeriq_rows('SELECT codigo,nombre,descripcion,mide_superficie FROM servicios WHERE activo=1 ORDER BY orden,id');
    foreach ($services as &$r) $r['mide_superficie']=(bool)(int)$r['mide_superficie'];
    unset($r);
    return ['services'=>$services,
        'orgTypes'=>aeriq_rows('SELECT codigo,nombre FROM tipos_organizacion WHERE activo=1 ORDER BY orden,id'),
        'objectives'=>aeriq_rows('SELECT codigo,nombre FROM objetivos_medicion WHERE activo=1 ORDER BY orden,id'),
        'provinces'=>AERIQ_PROVINCIAS,'durations'=>AERIQ_DURACIONES];
}
/** Panel comercial: solicitudes con nombres legibles, su historial y los estados posibles. */
function aeriq_requests(): array {
    $rows=aeriq_rows('SELECT s.*,sv.codigo AS servicio,sv.nombre AS servicio_nombre,t.codigo AS tipo_organizacion,t.nombre AS tipo_organizacion_nombre,e.codigo AS estado,e.nombre AS estado_nombre FROM solicitudes s JOIN servicios sv ON sv.id=s.servicio_id LEFT JOIN tipos_organizacion t ON t.id=s.tipo_organizacion_id JOIN estados_solicitud e ON e.id=s.estado_id ORDER BY s.recibida_en DESC,s.id DESC LIMIT 1000');
    $history=[];$objectives=[];
    if ($rows) {
        $ids=array_map(fn($r)=>(int)$r['id'],$rows);
        $sql='SELECT h.solicitud_id,e.codigo AS estado,e.nombre AS estado_nombre,h.cambiado_en,CONCAT(u.nombre,\' \',u.apellidos) AS usuario FROM solicitudes_historial h JOIN estados_solicitud e ON e.id=h.estado_id LEFT JOIN usuarios u ON u.id=h.usuario_id WHERE h.solicitud_id IN ('.implode(',',array_fill(0,count($ids),'?')).') ORDER BY h.cambiado_en,h.id';
        foreach (aeriq_rows($sql,$ids) as $h) {
            $history[(int)$h['solicitud_id']][]=['estado'=>$h['estado'],'estado_nombre'=>$h['estado_nombre'],'cambiado_en'=>$h['cambiado_en'],'usuario'=>$h['usuario']];
        }
        $sql='SELECT so.solicitud_id,o.nombre FROM solicitud_objetivos so JOIN objetivos_medicion o ON o.id=so.objetivo_id WHERE so.solicitud_id IN ('.implode(',',array_fill(0,count($ids),'?')).') ORDER BY o.orden,o.id';
        foreach (aeriq_rows($sql,$ids) as $o) $objectives[(int)$o['solicitud_id']][]=$o['nombre'];
    }
    foreach ($rows as &$r) { unset($r['servicio_id'],$r['tipo_organizacion_id'],$r['estado_id']); $r['objetivos']=$objectives[(int)$r['id']]??[]; $r['historial']=$history[(int)$r['id']]??[]; }
    unset($r);
    return ['requests'=>$rows,'states'=>aeriq_rows('SELECT codigo,nombre,final FROM estados_solicitud ORDER BY orden')];
}
/** Cambia el estado de una solicitud y deja constancia de quién y cuándo. */
function aeriq_request_status(array $a,array $d): array {
    $state=aeriq_one('SELECT id FROM estados_solicitud WHERE codigo=?',[is_string($d['status']??null)?$d['status']:'']);
    if (!$state) throw new AeriqError('Estado no válido.');
    $id=aeriq_int($d['id']??null);
    $db=conexionBD();$db->begin_transaction();
    try {
        $current=aeriq_one('SELECT estado_id FROM solicitudes WHERE id=? FOR UPDATE',[$id]);
        if (!$current) throw new AeriqError('Solicitud no encontrada.',404);
        if ((int)$current['estado_id']!==(int)$state['id']) {
            aeriq_sql('UPDATE solicitudes SET estado_id=? WHERE id=?',[(int)$state['id'],$id]);
            aeriq_sql('INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id) VALUES(?,?,?)',[$id,(int)$state['id'],(int)$a['usuario_id']]);
        }
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true];
}
