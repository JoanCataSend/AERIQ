<?php
declare(strict_types=1);
require_once __DIR__.'/AeriqMail.php';
require_once __DIR__.'/Avisos.php';

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
    $a=aeriq_one('SELECT u.id AS usuario_id,u.organizacion_id,u.campana_id,cp.nombre AS campana,o.nombre AS organizacion,u.rol_id,r.codigo AS rol,r.nombre AS rol_nombre,r.requiere_organizacion,u.debe_cambiar_clave AS cambiar_clave,u.activo,u.invitacion_expira_en FROM usuarios u JOIN roles r ON r.id=u.rol_id LEFT JOIN organizaciones o ON o.id=u.organizacion_id LEFT JOIN campanas cp ON cp.id=u.campana_id WHERE u.id=?',[$id]);
    if (!$a) throw new AeriqError('Cuenta no disponible.',401);
    $a['permisos']=array_column(aeriq_rows('SELECT p.codigo FROM rol_permisos rp JOIN permisos p ON p.id=rp.permiso_id WHERE rp.rol_id=? ORDER BY p.codigo',[(int)$a['rol_id']]),'codigo');
    // Un rol de organización sin organización no concede permisos (antes era un CHECK sobre el ENUM).
    if ((int)$a['requiere_organizacion'] && !$a['organizacion_id']) $a['permisos']=[];
    // Un admin de campaña sin campaña no concede permisos: si no, vería toda la organización.
    if ($a['rol']==='admin_campana' && !$a['campana_id']) $a['permisos']=[];
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
/** SQL reutilizable: cuentas (alias u) cuyo rol permite registrar mediciones, es decir, asignables a un AERIQ. */
const AERIQ_PUEDE_MEDIR="EXISTS (SELECT 1 FROM rol_permisos rp JOIN permisos p ON p.id=rp.permiso_id WHERE rp.rol_id=u.rol_id AND p.codigo='mediciones.registrar')";
/** Umbral de ozono de la campaña c: el propio o, si no lo tiene, el de referencia de la magnitud. */
const AERIQ_UMBRALES_O3='COALESCE(cu.umbral_medio,mo.umbral_medio_defecto) AS umbral_medio,COALESCE(cu.umbral_alto,mo.umbral_alto_defecto) AS umbral_alto';
const AERIQ_JOIN_UMBRALES_O3='LEFT JOIN campana_umbrales cu ON cu.campana_id=c.id AND cu.magnitud_id=14 JOIN magnitudes mo ON mo.id=14';
/** Umbral de la campaña c para la magnitud t de cada lectura (la temperatura no tiene). */
/** Última batería conocida del AERIQ d (porcentaje y fecha UTC de captura). */
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
/** Campaña a la que se limita la cuenta. null = admin principal: ve toda la organización. */
function aeriq_campaign(array $a): ?int { return $a['campana_id']===null?null:(int)$a['campana_id']; }
/** SQL: campaña de la última asignación del AERIQ d. Un AERIQ pertenece a la campaña donde se asignó por última vez. */
const AERIQ_CAMPANA_ORB='(SELECT h.campana_id FROM asignaciones h WHERE h.dispositivo_id=d.id ORDER BY h.iniciada_en DESC,h.id DESC LIMIT 1)';
/** SQL: estado de la campaña c según sus fechas (UTC): programada, activa o finalizada. */
const AERIQ_ESTADO_CAMPANA="CASE WHEN UTC_DATE()<c.inicio THEN 'programada' WHEN UTC_DATE()>c.fin THEN 'finalizada' ELSE 'activa' END AS estado";
/** SQL: admin de la campaña c (activo o con invitación pendiente), operadores y AERIQ en uso. Para el resumen del admin principal. */
const AERIQ_CAMPANA_RESUMEN="(SELECT TRIM(CONCAT(u.nombre,' ',u.apellidos)) FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.campana_id=c.id AND r.codigo='admin_campana' AND (u.activo=1 OR u.debe_cambiar_clave=1) ORDER BY u.id LIMIT 1) AS admin_nombre,(SELECT COUNT(*) FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.campana_id=c.id AND r.codigo='trabajador' AND (u.activo=1 OR u.debe_cambiar_clave=1)) AS operadores,(SELECT COUNT(*) FROM asignaciones x WHERE x.campana_id=c.id AND x.finalizada_en IS NULL) AS orbs_en_uso";
/** Una campaña tiene como mucho un admin (activo o invitado). Devuelve su nombre si ya lo tiene. */
function aeriq_campaign_admin(int $campaign,int $except=0): ?string {
    $r=aeriq_one("SELECT TRIM(CONCAT(u.nombre,' ',u.apellidos)) AS n FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.campana_id=? AND u.id<>? AND r.codigo='admin_campana' AND (u.activo=1 OR u.debe_cambiar_clave=1) LIMIT 1",[$campaign,$except]);
    return $r?$r['n']:null;
}
function aeriq_password_changed(int $id): void {
    aeriq_sql('UPDATE usuarios SET debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?',[$id]);
}

/**
 * $replyTo (opcional) permite contestar directamente a quien envió el formulario.
 * $html (opcional): versión HTML corporativa (aeriq_mail_compose); se envía junto al texto (multipart/alternative).
 */
function aeriq_mail(string $recipient,string $subject,string $text,?string $replyTo=null,?string $html=null): void {
    $cfg=auth_mail_config_ok();
    if ($replyTo!==null && !filter_var($replyTo,FILTER_VALIDATE_EMAIL)) $replyTo=null;
    if ($cfg['transport']==='smtp_native') {
        try { auth_smtp_send($cfg['smtp'],$recipient,$subject,$text,$replyTo,$html); }
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
        $m->setFrom($c['from_email'],$c['from_name']??'Código Norte');$m->addAddress($recipient);if($replyTo!==null)$m->addReplyTo($replyTo);$m->Subject=$subject;
        if ($html!==null) {$m->isHTML(true);$m->Body=$html;$m->AltBody=$text;} else $m->Body=$text;
        try {$m->send();}
        catch (Throwable $ex) {
            error_log('AERIQ: fallo en transporte SMTP (sin registrar credenciales): '.$ex->getMessage());
            throw new AeriqError('No se ha podido enviar el correo. Revisa la configuración del servidor.',503);
        }
        return;
    }
    [$type,$body]=aeriq_mail_body($text,$html);
    $headers=['From: '.$cfg['from_name'].' <'.$cfg['from_email'].'>','MIME-Version: 1.0','Content-Type: '.$type,'X-Mailer: AERIQ'];
    if ($replyTo!==null) $headers[]='Reply-To: '.$replyTo;
    if (!@mail($recipient,'=?UTF-8?B?'.base64_encode($subject).'?=',$body,implode("\r\n",$headers))) throw new AeriqError('El servidor no ha aceptado el correo. Comprueba que PHP mail() esté habilitado en Plesk.',503);
}
/** Cuerpo MIME: texto solo, o texto + HTML como alternativas. Devuelve [Content-Type, cuerpo con saltos CRLF]. */
function aeriq_mail_body(string $text,?string $html): array {
    $text=str_replace(["\r\n","\r"],"\n",$text);
    if ($html===null) return ['text/plain; charset=UTF-8',str_replace("\n","\r\n",$text)];
    $boundary='aeriq_'.bin2hex(random_bytes(12));
    $parts=['--'.$boundary,'Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: base64','',chunk_split(base64_encode($text),76,"\r\n"),
        '--'.$boundary,'Content-Type: text/html; charset=UTF-8','Content-Transfer-Encoding: base64','',chunk_split(base64_encode($html),76,"\r\n"),
        '--'.$boundary.'--',''];
    return ['multipart/alternative; boundary="'.$boundary.'"',implode("\r\n",$parts)];
}
function aeriq_worker(array $a,array $d): array {
    $org=aeriq_org($a);$email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);$last=auth_string($d['last']??'',120);$phone=aeriq_text($d['phone']??'',40,true);
    // El admin principal crea el admin de una campaña; el admin de campaña crea operadores de la suya.
    // El rol y la campaña los decide el servidor, nunca el formulario.
    if (aeriq_can($a,'campanas.gestionar')) {
        if (empty($d['campaign'])) throw new AeriqError('Elige la campaña que va a gestionar.',422,'campaign');
        $campaign=aeriq_int($d['campaign']);aeriq_own('campanas',$campaign,$org);$code='admin_campana';
        if ($who=aeriq_campaign_admin($campaign)) throw new AeriqError("Esta campaña ya tiene admin: $who. Desactívalo antes de asignar otro.",409,'campaign');
    } else {
        $campaign=aeriq_campaign($a);$code='trabajador';
        if (!$campaign) throw new AeriqError('Tu cuenta no tiene campaña asignada.',403);
    }
    $role=aeriq_one('SELECT id FROM roles WHERE codigo=?',[$code]);
    if (!$role) throw new AeriqError('Función no permitida.');
    auth_mail_config_ok();$db=conexionBD();$db->begin_transaction();
    try {
        if (auth_find($email)) throw new AeriqError('Ese correo ya tiene una cuenta. No se cambiará su organización.',409);
        aeriq_sql('INSERT INTO usuarios(organizacion_id,rol_id,campana_id,nombre,apellidos,email,telefono,password_hash,activo,debe_cambiar_clave) VALUES(?,?,?,?,?,?,?,?,0,1)',[$org,(int)$role['id'],$campaign,$name,$last,$email,$phone,'!']);$id=(int)$db->insert_id;
        aeriq_invite($id,$email);
        $db->commit();return ['ok'=>true,'id'=>$id,'message'=>'Invitación registrada. El servidor ha aceptado el correo; la cuenta se activará al cambiar la contraseña.'];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

/** Invitación: clave temporal impredecible de un solo uso (solo se guarda su hash) y correo al destinatario. Va dentro de una transacción. */
function aeriq_invite(int $id,string $email): void {
    $hours=max(1,min(168,(int)(getenv('AERIQ_INVITATION_HOURS')?:24)));
    $temporary=bin2hex(random_bytes(16));
    aeriq_sql('UPDATE usuarios SET password_hash=?,activo=0,debe_cambiar_clave=1,invitacion_expira_en=DATE_ADD(UTC_TIMESTAMP(),INTERVAL ? HOUR) WHERE id=?',[password_hash($temporary,PASSWORD_DEFAULT),$hours,$id]);
    // T32: el botón abre directamente la página de elegir contraseña con el correo y la clave temporal ya puestos.
    // Van tras «#»: el navegador no los envía al servidor ni aparecen en sus registros.
    $link=aeriq_public_url('cambiar-clave.html').'#'.http_build_query(['email'=>$email,'temporal'=>$temporary],'','&',PHP_QUERY_RFC3986);
    $mail=aeriq_mail_compose('Te damos la bienvenida a AERIQ',[
        ['p',"Tu organización te ha creado una cuenta en AERIQ con el correo $email."],
        ['p','Pulsa el botón para elegir tu contraseña. Después podrás entrar en la web y en la app de AERIQ con tu correo y esa contraseña.'],
        ['button','Establecer mi contraseña',$link],
        ['facts',['Correo'=>$email,'Contraseña temporal'=>$temporary,'Caduca en'=>$hours.' horas']],
        ['note','Si prefieres hacerlo a mano, inicia sesión con la contraseña temporal y te pedirá cambiarla. También puedes entrar con Google usando este mismo correo. Si el acceso caduca, recupéralo desde «¿Has olvidado tu contraseña?».'],
    ],'Elige tu contraseña para empezar a usar AERIQ');
    aeriq_mail($email,'AERIQ · Invitación de tu organización',$mail['text'],null,$mail['html']);
}
/** Cuenta que esta cuenta puede gestionar: el admin principal, los admins de campaña de su organización; el admin de campaña, los operadores de su campaña. */
function aeriq_managed(array $a,int $id): array {
    $org=aeriq_org($a);
    if (aeriq_can($a,'campanas.gestionar')) $u=aeriq_one("SELECT u.* FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.id=? AND u.organizacion_id=? AND r.codigo='admin_campana'",[$id,$org]);
    elseif (aeriq_can($a,'equipo.gestionar') && aeriq_campaign($a)) $u=aeriq_one("SELECT u.* FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.id=? AND u.organizacion_id=? AND u.campana_id=? AND r.codigo='trabajador'",[$id,$org,aeriq_campaign($a)]);
    else throw new AeriqError('Tu función no permite gestionar cuentas.',403);
    if (!$u) throw new AeriqError('Esa cuenta no está entre las que gestionas.',404);
    return $u;
}
/** Editar nombre, apellidos y teléfono. El correo no se cambia: es la identidad de la cuenta. */
function aeriq_worker_edit(array $a,array $d): array {
    $u=aeriq_managed($a,aeriq_int($d['id']??null));
    $phone=trim(is_string($d['phone']??null)?$d['phone']:'');if (mb_strlen($phone)>40) throw new AeriqError('Teléfono demasiado largo.',422,'phone');
    aeriq_sql('UPDATE usuarios SET nombre=?,apellidos=?,telefono=? WHERE id=?',[auth_string($d['name']??'',90),auth_string($d['last']??'',120),$phone,(int)$u['id']]);
    return ['ok'=>true,'message'=>'Datos guardados.'];
}
/** Desactivar: no puede entrar ni enviar lecturas, se cierran sus sesiones y queda libre su AERIQ. Sus mediciones se conservan. */
function aeriq_worker_status(array $a,array $d): array {
    $u=aeriq_managed($a,aeriq_int($d['id']??null));$id=(int)$u['id'];$active=aeriq_bool($d['active']??false);
    if ($active) {
        if (!$u['verificado_en']) throw new AeriqError('Esta cuenta aún no se ha activado: reenvía la invitación.',409);
        // Solo al reactivar a un admin de campaña (lo hace el admin principal): una campaña, un admin.
        if (aeriq_can($a,'campanas.gestionar') && $u['campana_id'] && ($who=aeriq_campaign_admin((int)$u['campana_id'],$id))) throw new AeriqError("Su campaña ya tiene otro admin: $who. Desactívalo antes.",409);
        aeriq_sql('UPDATE usuarios SET activo=1 WHERE id=?',[$id]);return ['ok'=>true,'message'=>'Cuenta reactivada.'];
    }
    $db=conexionBD();$db->begin_transaction();
    try {
        aeriq_sql('UPDATE usuarios SET activo=0,debe_cambiar_clave=0,invitacion_expira_en=NULL WHERE id=?',[$id]);
        aeriq_sql('DELETE FROM sesiones_moviles WHERE usuario_id=?',[$id]);
        aeriq_sql('UPDATE asignaciones SET finalizada_en=UTC_TIMESTAMP(3) WHERE trabajador_id=? AND finalizada_en IS NULL',[$id]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true,'message'=>'Cuenta desactivada. Su AERIQ queda libre para asignarlo a otra persona.'];
}
/** Reenviar la invitación a una cuenta que todavía no se ha activado (por ejemplo, porque caducó). */
function aeriq_worker_invite(array $a,array $d): array {
    $u=aeriq_managed($a,aeriq_int($d['id']??null));
    if ($u['verificado_en']) throw new AeriqError('Esta cuenta ya está activa: si olvidó la contraseña, puede recuperarla desde el inicio de sesión.',409);
    auth_mail_config_ok();$db=conexionBD();$db->begin_transaction();
    try {aeriq_invite((int)$u['id'],$u['email']);$db->commit();} catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true,'message'=>'Invitación reenviada a '.$u['email'].'.'];
}
/** Admin principal: nombre, zona y fechas; el estado (programada, activa, finalizada) sale de las fechas. Umbrales, distancia y publicación los ajusta el admin de la campaña (aeriq_campaign_settings). */
function aeriq_campaign_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])&&$d['id']!==''?aeriq_int($d['id']):null;
    if ($id) aeriq_own('campanas',$id,$org);
    $start=aeriq_text($d['start']??'',10);$end=aeriq_text($d['end']??'',10);
    foreach ([$start,$end] as $date) { $v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Fecha no válida.'); }
    if ($end<$start) throw new AeriqError('La fecha final debe ser posterior al inicio.');
    $name=aeriq_text($d['name']??'',160);$zoneName=aeriq_text($d['zone']??'');
    $db=conexionBD();$db->begin_transaction();
    try {
        $zone=aeriq_zone($org,$zoneName);
        if ($id) aeriq_sql('UPDATE campanas SET nombre=?,zona_id=?,inicio=?,fin=?,activa=1 WHERE id=? AND organizacion_id=?',[$name,$zone,$start,$end,$id,$org]);
        // Nueva: privada, distancia por defecto y umbrales de referencia hasta que su admin los ajuste.
        else { aeriq_sql('INSERT INTO campanas(nombre,zona_id,inicio,fin,activa,publicar,radio_m,organizacion_id) VALUES(?,?,?,?,1,0,30,?)',[$name,$zone,$start,$end,$org]);$new=(int)$db->insert_id; }
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return $id?['ok'=>true,'id'=>$id,'message'=>'Cambios de la campaña guardados.']:['ok'=>true,'id'=>$new,'message'=>'Campaña creada. Ahora asígnale un admin en «Admins de campaña».'];
}
/** «Mi campaña» del operador: su campaña, su admin, su AERIQ y sus últimas lecturas. Solo lectura y sin descarga. */
function aeriq_my_campaign(array $a): array {
    $org=aeriq_org($a);$id=aeriq_campaign($a);$uid=(int)$a['usuario_id'];
    if (!$id) throw new AeriqError('Todavía no perteneces a ninguna campaña: tu admin de campaña te añadirá.',404);
    return [
        'campaign'=>aeriq_one('SELECT c.id,c.nombre,z.nombre AS zona,c.inicio,c.fin,c.activa,c.publicar,c.radio_m,'.AERIQ_UMBRALES_O3.','.AERIQ_ESTADO_CAMPANA.' FROM campanas c JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.' WHERE c.id=? AND c.organizacion_id=?',[$id,$org]),
        'admins'=>aeriq_rows("SELECT u.nombre,u.apellidos,u.email FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.campana_id=? AND r.codigo='admin_campana' AND u.activo=1 ORDER BY u.nombre",[$id]),
        'orb'=>aeriq_one('SELECT d.id,d.nombre,d.simulado,a.iniciada_en,'.AERIQ_BATERIA.' FROM asignaciones a JOIN dispositivos d ON d.id=a.dispositivo_id WHERE a.trabajador_id=? AND a.finalizada_en IS NULL LIMIT 1',[$uid]),
        'readings'=>aeriq_rows('SELECT m.capturada_en,t.nombre AS magnitud,m.valor,t.unidad FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id JOIN magnitudes t ON t.id=m.magnitud_id WHERE a.trabajador_id=? AND a.campana_id=? AND m.valida=1 ORDER BY m.capturada_en DESC LIMIT 20',[$uid,$id]),
    ];
}
/** El admin de campaña ajusta SU campaña: umbrales de ozono, distancia de interpolación y si sale en el mapa público. */
function aeriq_campaign_settings(array $a,array $d): array {
    $id=aeriq_campaign($a);if (!$id) throw new AeriqError('Tu cuenta no tiene campaña asignada.',403);
    $mid=aeriq_number($d['medium']??null,0,65535);$high=aeriq_number($d['high']??null,0,65535);
    if ($high<=$mid) throw new AeriqError('El umbral alto debe superar al moderado.',422,'high');
    $radius=aeriq_int($d['radius']??30,20,50);$public=aeriq_bool($d['public']??false);
    $db=conexionBD();$db->begin_transaction();
    try {
        aeriq_sql('UPDATE campanas SET publicar=?,radio_m=? WHERE id=? AND organizacion_id=?',[$public,$radius,$id,aeriq_org($a)]);
        aeriq_sql('INSERT INTO campana_umbrales(campana_id,magnitud_id,umbral_medio,umbral_alto) VALUES(?,14,?,?) ON DUPLICATE KEY UPDATE umbral_medio=VALUES(umbral_medio),umbral_alto=VALUES(umbral_alto)',[$id,$mid,$high]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true,'id'=>$id,'message'=>'Ajustes de la campaña guardados.'];
}
/** Devuelve la zona de la organización con ese nombre, creándola si aún no existe. */
function aeriq_zone(int $org,string $name): int {
    aeriq_sql('INSERT IGNORE INTO zonas(organizacion_id,nombre) VALUES(?,?)',[$org,$name]);
    return (int)aeriq_one('SELECT id FROM zonas WHERE organizacion_id=? AND nombre=?',[$org,$name])['id'];
}
function aeriq_device_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])?aeriq_int($d['id']):null;$scope=aeriq_campaign($a);
    if (!$scope) throw new AeriqError('Solo el admin de una campaña registra AERIQ.',403);
    if ($id && !aeriq_one('SELECT d.id FROM dispositivos d WHERE d.id=? AND d.organizacion_id=? AND '.AERIQ_CAMPANA_ORB.'=?',[$id,$org,$scope])) throw new AeriqError('El AERIQ no pertenece a tu campaña.',404);
    $mac=strtoupper(aeriq_text($d['address']??'',17));if (!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D',$mac)) throw new AeriqError('Dirección BLE no válida.');
    // La campaña es siempre la del admin; el AERIQ se asigna a un operador de esa campaña.
    if (empty($d['worker'])) throw new AeriqError('Asigna el AERIQ a un operador de tu campaña.',422,'worker');
    $worker=aeriq_int($d['worker']);$campaign=$scope;
    if (!aeriq_one('SELECT u.id FROM usuarios u WHERE u.id=? AND u.organizacion_id=? AND u.campana_id=? AND '.AERIQ_PUEDE_MEDIR.' AND (u.activo=1 OR u.debe_cambiar_clave=1)',[$worker,$org,$scope])) throw new AeriqError('Ese operador no es de tu campaña.',403);
    $active=aeriq_bool($d['active']??true);
    if (!$id && !$active) throw new AeriqError('Un AERIQ nuevo se registra activo.');$simulated=aeriq_bool($d['simulated']??false);$name=aeriq_text($d['name']??'',160);
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
            if ($active && $worker) {
                // Un AERIQ por operador: si ya llevaba otro, ese queda libre.
                aeriq_sql('UPDATE asignaciones SET finalizada_en=UTC_TIMESTAMP(3) WHERE trabajador_id=? AND finalizada_en IS NULL',[$worker]);
                aeriq_sql('INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id) VALUES(?,?,?,?)',[$org,$id,$worker,$campaign]);
            }
        }
        $db->commit();return ['ok'=>true,'id'=>$id];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

function aeriq_assignments(array $a): array {
    // Rangos de ozono y temperatura (HU35): la app los comprueba antes de enviar; el servidor vuelve a hacerlo.
    return aeriq_rows('SELECT d.id,d.nombre,d.direccion_ble,a.id AS asignacion_id,a.campana_id,c.nombre AS campana,z.nombre AS zona,c.publicar,'.AERIQ_UMBRALES_O3.',mo.valor_minimo AS o3_minimo,mo.valor_maximo AS o3_maximo,(SELECT valor_minimo FROM magnitudes WHERE id=12) AS temperatura_minima,(SELECT valor_maximo FROM magnitudes WHERE id=12) AS temperatura_maxima,d.simulado,'.AERIQ_BATERIA.' FROM asignaciones a JOIN dispositivos d ON d.id=a.dispositivo_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.' WHERE a.organizacion_id=? AND a.trabajador_id=? AND a.finalizada_en IS NULL AND d.activo=1 AND c.activa=1 AND UTC_DATE() BETWEEN c.inicio AND c.fin',[aeriq_org($a),(int)$a['usuario_id']]);
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
    // La magnitud debe existir y ser una de las que mide el modelo de ese AERIQ.
    $magnitude=aeriq_one('SELECT t.* FROM magnitudes t JOIN modelo_magnitudes mm ON mm.magnitud_id=t.id JOIN dispositivos d ON d.modelo_id=mm.modelo_id WHERE t.id=? AND d.id=?',[$type,$device]);
    if (!$magnitude) throw new AeriqError('Magnitud no admitida.');
    if (isset($d['unit']) && $d['unit']!==$magnitude['unidad']) throw new AeriqError('La unidad no corresponde a esta magnitud.');
    // Solo se rechaza lo que no puede venir del anuncio BLE (uint16/int16). Fuera del rango de la magnitud
    // la lectura se guarda como no válida (HU35): queda constancia, pero no entra en mapas ni informes.
    $value=aeriq_number($d['value']??null,-32768,65535);
    $captured=aeriq_capture_time($d['capturedAt']??'');$count=aeriq_int($d['counter']??null,0,255);$rssi=aeriq_int($d['rssi']??null,-127,20);
    $lat=$lon=$accuracy=null;
    if (isset($d['latitude']) || isset($d['longitude']) || isset($d['accuracy'])) {
        $lat=aeriq_number($d['latitude']??null,-90,90);$lon=aeriq_number($d['longitude']??null,-180,180);$accuracy=aeriq_number($d['accuracy']??null,0,10000);
    }
    $db=conexionBD();$db->begin_transaction();
    try {
        $orb=aeriq_one('SELECT d.*,a.id AS asignacion_id,a.trabajador_id,a.campana_id,a.iniciada_en,c.inicio,c.fin,c.activa AS campana_activa FROM dispositivos d JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL JOIN campanas c ON c.id=a.campana_id WHERE d.id=? AND d.organizacion_id=? AND a.trabajador_id=? FOR UPDATE',[$device,aeriq_org($a),(int)$a['usuario_id']]);
        if (!$orb || !(int)$orb['activo'] || !(int)$orb['campana_activa']) throw new AeriqError('AERIQ o campaña no asignados o inactivos.',403);
        if ((int)$orb['campana_id']!==$campaign || (isset($d['assignmentId']) && (int)$d['assignmentId']!==(int)$orb['asignacion_id'])) throw new AeriqError('La asignación ha cambiado. Actualiza tus asignaciones.',409);
        if (strtoupper((string)($d['address']??''))!==$orb['direccion_ble']) throw new AeriqError('El anuncio no corresponde al AERIQ asignado.',403);
        $date=substr($captured,0,10);if ($date<$orb['inicio'] || $date>$orb['fin'] || $captured<$orb['iniciada_en']) throw new AeriqError('Lectura fuera de las fechas de la asignación/campaña.');
        $old=aeriq_one('SELECT m.*,a.trabajador_id,a.dispositivo_id,a.campana_id FROM mediciones m JOIN asignaciones a ON a.id=m.asignacion_id WHERE m.evento=?',[$event]);
        if ($old) {
            if ((int)$old['dispositivo_id']!==$device || (int)$old['trabajador_id']!==(int)$a['usuario_id'] || (int)$old['campana_id']!==$campaign || (int)$old['magnitud_id']!==$type || (float)$old['valor']!==$value || $old['capturada_en']!==$captured || ($old['latitud']===null?null:(float)$old['latitud'])!==$lat || ($old['longitud']===null?null:(float)$old['longitud'])!==$lon || ($old['precision_m']===null?null:(float)$old['precision_m'])!==$accuracy || (int)$old['contador']!==$count || (int)$old['rssi']!==$rssi) throw new AeriqError('La clave de lectura ya identifica otros datos.',409);
            $db->commit();return ['ok'=>true,'duplicate'=>true,'id'=>(int)$old['id'],'eventId'=>$event,'valid'=>(bool)(int)$old['valida'],'reason'=>$old['motivo_descarte']];
        }
        $reason=aeriq_discard_reason($magnitude,(int)$orb['asignacion_id'],$type,$value,$captured);
        aeriq_sql('INSERT INTO mediciones(evento,asignacion_id,magnitud_id,valor,capturada_en,latitud,longitud,precision_m,contador,rssi,simulado,valida,motivo_descarte) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)',[$event,(int)$orb['asignacion_id'],$type,$value,$captured,$lat,$lon,$accuracy,$count,$rssi,(int)$orb['simulado'],$reason===null?1:0,$reason]);
        $id=(int)$db->insert_id;$db->commit();return ['ok'=>true,'duplicate'=>false,'id'=>$id,'eventId'=>$event,'valid'=>$reason===null,'reason'=>$reason];
    } catch (Throwable $e) {$db->rollback();throw $e;}
}

/**
 * Motivo para descartar una lectura (HU35), o null si es válida.
 * - 'fuera_de_rango': fuera de [valor_minimo, valor_maximo] de su magnitud.
 * - 'anomala': se aleja más de salto_maximo de la media de las 5 lecturas anteriores de la misma asignación y
 *   magnitud (de los 10 minutos previos, al menos 3). La media incluye las anómalas pero no las fuera de rango:
 *   un pico suelto se descarta, pero si el nivel cambia de verdad, la media lo alcanza en dos o tres lecturas.
 * Los límites están en la tabla magnitudes: se ajustan sin tocar código.
 */
function aeriq_discard_reason(array $magnitude,int $assignment,int $type,float $value,string $captured): ?string {
    if ($value<(float)$magnitude['valor_minimo'] || $value>(float)$magnitude['valor_maximo']) return 'fuera_de_rango';
    if ($magnitude['salto_maximo']===null) return null;
    $since=(new DateTimeImmutable($captured,new DateTimeZone('UTC')))->modify('-10 minutes')->format('Y-m-d H:i:s.v');
    $previous=aeriq_rows("SELECT valor FROM mediciones WHERE asignacion_id=? AND magnitud_id=? AND capturada_en<? AND capturada_en>=? AND (motivo_descarte IS NULL OR motivo_descarte<>'fuera_de_rango') ORDER BY capturada_en DESC,id DESC LIMIT 5",[$assignment,$type,$captured,$since]);
    if (count($previous)<3) return null;
    $mean=array_sum(array_map(fn($r)=>(float)$r['valor'],$previous))/count($previous);
    return abs($value-$mean)>(float)$magnitude['salto_maximo']?'anomala':null;
}

/**
 * Batería que el AERIQ anuncia por BLE, enviada por la app de quien lo tiene asignado.
 * Solo AERIQ reales: un AERIQ simulado no tiene batería que medir. Para no llenar la tabla,
 * el mismo porcentaje en menos de 10 minutos no crea otra fila.
 */
function aeriq_battery(array $a,array $d): array {
    $device=aeriq_int($d['deviceId']??null);$percent=aeriq_int($d['percent']??null,0,100);
    $captured=aeriq_capture_time($d['capturedAt']??'');
    $orb=aeriq_one('SELECT d.id,d.direccion_ble,d.activo,d.simulado FROM dispositivos d JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL WHERE d.id=? AND d.organizacion_id=? AND a.trabajador_id=?',[$device,aeriq_org($a),(int)$a['usuario_id']]);
    if (!$orb || !(int)$orb['activo']) throw new AeriqError('AERIQ no asignado o inactivo.',403);
    if ((int)$orb['simulado']) throw new AeriqError('Un AERIQ simulado no tiene batería que registrar.');
    if (strtoupper((string)($d['address']??''))!==$orb['direccion_ble']) throw new AeriqError('El anuncio no corresponde al AERIQ asignado.',403);
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
    $org=aeriq_org($a);$scope=aeriq_campaign($a);
    // Admin de campaña: solo su campaña. Admin principal ($scope null): toda la organización.
    $only=fn(string $col)=>$scope?" AND $col=".$scope:'';
    $ref=aeriq_one('SELECT umbral_medio_defecto AS umbral_medio,umbral_alto_defecto AS umbral_alto FROM magnitudes WHERE id=14');
    return [
        'workers'=>aeriq_rows('SELECT u.id,u.nombre,u.apellidos,u.email,u.telefono,u.campana_id,(SELECT nombre FROM campanas WHERE id=u.campana_id) AS campana,(u.verificado_en IS NOT NULL) AS verificado,(SELECT d.nombre FROM asignaciones x JOIN dispositivos d ON d.id=x.dispositivo_id WHERE x.trabajador_id=u.id AND x.finalizada_en IS NULL LIMIT 1) AS orb,r.codigo AS rol,r.nombre AS rol_nombre,'.AERIQ_PUEDE_MEDIR.' AS puede_medir,u.activo,u.debe_cambiar_clave AS cambiar_clave,u.invitacion_expira_en FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.organizacion_id=?'.$only('u.campana_id').' ORDER BY u.nombre',[$org]),
        'campaigns'=>aeriq_rows('SELECT c.*,z.nombre AS zona,'.AERIQ_UMBRALES_O3.','.AERIQ_CAMPANA_RESUMEN.','.AERIQ_ESTADO_CAMPANA.' FROM campanas c JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.' WHERE c.organizacion_id=?'.$only('c.id').' ORDER BY c.inicio DESC',[$org]),
        'zones'=>aeriq_rows('SELECT id,nombre FROM zonas WHERE organizacion_id=?'.($scope?' AND id=(SELECT zona_id FROM campanas WHERE id='.$scope.')':'').' ORDER BY nombre',[$org]),
        'roles'=>aeriq_rows('SELECT codigo,nombre,descripcion FROM roles WHERE codigo=?',[$scope?'trabajador':'admin_campana']),
        'thresholds'=>['umbral_medio'=>(float)$ref['umbral_medio'],'umbral_alto'=>(float)$ref['umbral_alto']],
        'devices'=>aeriq_rows('SELECT d.*,md.nombre AS modelo,'.AERIQ_BATERIA.',a.trabajador_id,a.campana_id,TRIM(CONCAT(u.nombre,\' \',u.apellidos)) AS trabajador,c.nombre AS campana,(SELECT MAX(m.capturada_en) FROM mediciones m JOIN asignaciones h ON h.id=m.asignacion_id WHERE h.dispositivo_id=d.id) AS ultima_medicion,(SELECT MAX(m.recibida_en) FROM mediciones m JOIN asignaciones h ON h.id=m.asignacion_id WHERE h.dispositivo_id=d.id) AS ultima_recepcion FROM dispositivos d JOIN modelos_dispositivo md ON md.id=d.modelo_id LEFT JOIN asignaciones a ON a.dispositivo_id=d.id AND a.finalizada_en IS NULL LEFT JOIN usuarios u ON u.id=a.trabajador_id LEFT JOIN campanas c ON c.id=a.campana_id WHERE d.organizacion_id=?'.$only(AERIQ_CAMPANA_ORB).' ORDER BY d.nombre',[$org])
    ];
}

function aeriq_readings(array $a,array $filters): array {
    [$where,$params]=aeriq_readings_filter($a,$filters);
    $where[]='l.valida=1';
    $rows=aeriq_rows('SELECT l.id,l.evento,a.dispositivo_id,a.campana_id,l.magnitud_id AS tipo,l.valor,t.unidad,l.capturada_en,l.latitud,l.longitud,l.simulado,d.nombre AS dispositivo,c.nombre AS campana,z.nombre AS zona,'.AERIQ_UMBRALES_LECTURA.' FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.magnitud_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id JOIN dispositivos d ON d.id=a.dispositivo_id LEFT JOIN campana_umbrales cu ON cu.campana_id=c.id AND cu.magnitud_id=l.magnitud_id WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en,l.id LIMIT 10001',$params);
    if (count($rows)>10000) throw new AeriqError('Demasiados registros. Reduce el intervalo o selecciona campaña/dispositivo; no se exportarán datos incompletos.',413);
    return $rows;
}
/** HU35: lecturas descartadas (no válidas) con los mismos filtros del informe, para enseñarlas aparte. Máximo 500. */
function aeriq_discarded(array $a,array $filters): array {
    [$where,$params]=aeriq_readings_filter($a,$filters);
    $where[]='l.valida=0';
    return aeriq_rows('SELECT l.id,l.magnitud_id AS tipo,l.valor,t.unidad,t.valor_minimo,t.valor_maximo,t.salto_maximo,l.capturada_en,l.simulado,l.motivo_descarte,d.nombre AS dispositivo,c.nombre AS campana FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.magnitud_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id JOIN dispositivos d ON d.id=a.dispositivo_id WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en DESC,l.id DESC LIMIT 500',$params);
}
/** Condiciones comunes del informe: organización, campaña del admin, magnitud, campaña, AERIQ, zona y fechas. */
function aeriq_readings_filter(array $a,array $filters): array {
    $where=['c.organizacion_id=?','l.magnitud_id IN (12,14)'];$params=[aeriq_org($a)];
    if ($scope=aeriq_campaign($a)) { $where[]='a.campana_id=?';$params[]=$scope; }
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
    return [$where,$params];
}
function aeriq_public_map(array $query): array {return aeriq_map_data(null,$query);}
function aeriq_map_data(?array $access,array $query): array {
    $mode=$query['mode']??'real';if (!in_array($mode,['real','simulated'],true)) throw new AeriqError('Selecciona datos reales o simulados.');
    $hours=aeriq_int($query['hours']??24,0,168);if(!in_array($hours,[0,24,168],true))throw new AeriqError('Periodo no válido.');
    $where=['l.valida=1','l.magnitud_id=14','l.latitud IS NOT NULL','l.longitud IS NOT NULL','l.simulado=?'];$params=[$mode==='simulated'?1:0];
    if ($access) {$where[]='a.organizacion_id=?';$params[]=aeriq_org($access);if($scope=aeriq_campaign($access)){$where[]='a.campana_id=?';$params[]=$scope;}if(!aeriq_can($access,'informes.ver')){$where[]='a.trabajador_id=?';$params[]=(int)$access['usuario_id'];}}
    else $where[]='c.publicar=1';
    if($hours)$where[]='l.capturada_en>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL '.$hours.' HOUR)';
    // Solo el mapa de la organización identifica el AERIQ (para dibujar su ruta); el público no lo expone.
    $orb=$access?',a.dispositivo_id,d.nombre AS dispositivo':'';$orbJoin=$access?' JOIN dispositivos d ON d.id=a.dispositivo_id':'';
    $rows=aeriq_rows('SELECT l.id,l.valor,t.unidad,l.capturada_en,l.latitud,l.longitud,l.simulado,z.nombre AS zona,c.id AS campaignId,c.radio_m,'.AERIQ_UMBRALES_O3.$orb.' FROM mediciones l JOIN asignaciones a ON a.id=l.asignacion_id JOIN magnitudes t ON t.id=l.magnitud_id JOIN campanas c ON c.id=a.campana_id JOIN zonas z ON z.id=c.zona_id '.AERIQ_JOIN_UMBRALES_O3.$orbJoin.' WHERE '.implode(' AND ',$where).' ORDER BY l.capturada_en DESC,l.id DESC LIMIT '.(AERIQ_MAPA_MAX+1),$params);
    $truncated=count($rows)>AERIQ_MAPA_MAX;$rows=array_slice($rows,0,AERIQ_MAPA_MAX);
    foreach ($rows as &$r) {foreach (['valor','latitud','longitud','umbral_medio','umbral_alto'] as $k)$r[$k]=(float)$r[$k];$r['simulado']=(bool)$r['simulado'];$r['radio_m']=(int)$r['radio_m'];if($access)$r['dispositivo_id']=(int)$r['dispositivo_id'];}
    unset($r);return ['measurements'=>$rows,'truncated'=>$truncated,'mode'=>$mode,'hours'=>$hours];
}
/** Lecturas del mapa como máximo (las más recientes del filtro). El Orb anuncia ozono cada ~1,5 s: 10.000 son unas 4 h de un Orb. */
const AERIQ_MAPA_MAX=10000;
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
    // T36: acuse al solicitante con el número y el resumen de lo que ha pedido. Si falla, la solicitud sigue guardada.
    $facts=['Número de solicitud'=>'#'.$id,'Servicio'=>$service,'Organización'=>$entity.' ('.$org['nombre'].')','Contacto'=>$contact,
        'Teléfono'=>$phone,'Lugar'=>$municipality.' ('.$province.')','Zona'=>$zone,'Objetivos'=>implode(', ',array_column($objectives,'nombre'))];
    if ($surface!==null) $facts['Superficie']=rtrim(rtrim(number_format($surface,2,',','.'),'0'),',').' m²';
    if ($duration!=='') $facts['Duración']=$duration;
    if ($start!==null) $facts['Inicio deseado']=substr($start,0,7);
    $facts['Lo que necesitas']=mb_strlen($needs)>600?mb_substr($needs,0,600).'…':$needs;
    $sent=true;
    try {
        $mail=aeriq_mail_compose('Hemos recibido tu solicitud',[
            ['p',"Hola, $contact:"],
            ['p','Gracias por contactar con Código Norte. Tu solicitud ha llegado correctamente y la revisaremos en los próximos días laborables. Te escribiremos a este correo cada vez que cambie de estado.'],
            ['facts',$facts],
            ['note','Guarda el número de solicitud: te servirá si necesitas escribirnos. Puedes responder a este correo para añadir información.'],
        ],'Solicitud #'.$id.' recibida');
        aeriq_mail($email,'AERIQ · Hemos recibido tu solicitud #'.$id,$mail['text'],(getenv('AERIQ_COMMERCIAL_EMAIL')?:AERIQ_COMMERCIAL_EMAIL_DEFAULT),$mail['html']);
    } catch (Throwable $e) { $sent=false;error_log('AERIQ: solicitud registrada; confirmación al solicitante no entregada.'); }
    return ['ok'=>true,'id'=>$id,'mailed'=>$sent,'message'=>'Solicitud registrada. '.($sent?'Te hemos enviado un correo de confirmación. ':'').'Código Norte la revisará y te propondrá la solución que mejor encaje.'];
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
        $sql='SELECT h.solicitud_id,e.codigo AS estado,e.nombre AS estado_nombre,e.final,h.cambiado_en,CONCAT(u.nombre,\' \',u.apellidos) AS usuario FROM solicitudes_historial h JOIN estados_solicitud e ON e.id=h.estado_id LEFT JOIN usuarios u ON u.id=h.usuario_id WHERE h.solicitud_id IN ('.implode(',',array_fill(0,count($ids),'?')).') ORDER BY h.cambiado_en,h.id';
        $wasFinal=[];
        foreach (aeriq_rows($sql,$ids) as $h) {
            $sid=(int)$h['solicitud_id'];
            // Una entrada que sale de un estado final es una reapertura (T38): se marca para enseñarla así.
            $history[$sid][]=['estado'=>$h['estado'],'estado_nombre'=>$h['estado_nombre'],'cambiado_en'=>$h['cambiado_en'],'usuario'=>$h['usuario'],
                'reapertura'=>!empty($wasFinal[$sid]) && !(int)$h['final']];
            $wasFinal[$sid]=(bool)(int)$h['final'];
        }
        $sql='SELECT so.solicitud_id,o.nombre FROM solicitud_objetivos so JOIN objetivos_medicion o ON o.id=so.objetivo_id WHERE so.solicitud_id IN ('.implode(',',array_fill(0,count($ids),'?')).') ORDER BY o.orden,o.id';
        foreach (aeriq_rows($sql,$ids) as $o) $objectives[(int)$o['solicitud_id']][]=$o['nombre'];
    }
    foreach ($rows as &$r) { unset($r['servicio_id'],$r['tipo_organizacion_id'],$r['estado_id']); $r['objetivos']=$objectives[(int)$r['id']]??[]; $r['historial']=$history[(int)$r['id']]??[]; }
    unset($r);
    return ['requests'=>$rows,'states'=>aeriq_rows('SELECT codigo,nombre,final FROM estados_solicitud ORDER BY orden')];
}
/**
 * Cambia el estado de una solicitud (T38): solo hacia delante en el orden del catálogo. Volver atrás no se
 * permite; una solicitud cerrada se recupera con aeriq_request_reopen(). Cada cambio queda en el historial con
 * quién y cuándo, y se avisa por correo al solicitante (T37). Un fallo del correo no deshace el cambio.
 */
function aeriq_request_status(array $a,array $d): array {
    $state=aeriq_one('SELECT id,codigo,nombre,orden,final FROM estados_solicitud WHERE codigo=?',[is_string($d['status']??null)?$d['status']:'']);
    if (!$state) throw new AeriqError('Estado no válido.');
    $id=aeriq_int($d['id']??null);
    $db=conexionBD();$db->begin_transaction();
    try {
        $current=aeriq_one('SELECT s.estado_id,e.orden,e.final,e.nombre FROM solicitudes s JOIN estados_solicitud e ON e.id=s.estado_id WHERE s.id=? FOR UPDATE',[$id]);
        if (!$current) throw new AeriqError('Solicitud no encontrada.',404);
        if ((int)$current['estado_id']===(int)$state['id']) {$db->commit();return ['ok'=>true,'changed'=>false,'message'=>'La solicitud ya estaba en «'.$state['nombre'].'».'];}
        if ((int)$current['final']) throw new AeriqError('La solicitud está cerrada. Usa «Reabrir» para volver a trabajar en ella.',409);
        if ((int)$state['orden']<(int)$current['orden']) throw new AeriqError('Una solicitud solo puede avanzar de estado: no puede volver de «'.$current['nombre'].'» a «'.$state['nombre'].'».',409);
        aeriq_sql('UPDATE solicitudes SET estado_id=? WHERE id=?',[(int)$state['id'],$id]);
        aeriq_sql('INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id) VALUES(?,?,?)',[$id,(int)$state['id'],(int)$a['usuario_id']]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return aeriq_request_notify($id,(int)$state['final']?'cerrada':'avance');
}
/** Reabre una solicitud cerrada (T38): vuelve a «En revisión» y queda en el historial como reapertura. */
function aeriq_request_reopen(array $a,array $d): array {
    $id=aeriq_int($d['id']??null);
    $target=aeriq_one("SELECT id FROM estados_solicitud WHERE codigo='en_revision'")
        ?:aeriq_one('SELECT id FROM estados_solicitud WHERE final=0 ORDER BY orden DESC LIMIT 1');
    if (!$target) throw new AeriqError('No hay un estado al que reabrir.',500);
    $db=conexionBD();$db->begin_transaction();
    try {
        $current=aeriq_one('SELECT e.final FROM solicitudes s JOIN estados_solicitud e ON e.id=s.estado_id WHERE s.id=? FOR UPDATE',[$id]);
        if (!$current) throw new AeriqError('Solicitud no encontrada.',404);
        if (!(int)$current['final']) throw new AeriqError('Solo se puede reabrir una solicitud cerrada.',409);
        aeriq_sql('UPDATE solicitudes SET estado_id=? WHERE id=?',[(int)$target['id'],$id]);
        aeriq_sql('INSERT INTO solicitudes_historial(solicitud_id,estado_id,usuario_id) VALUES(?,?,?)',[$id,(int)$target['id'],(int)$a['usuario_id']]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return aeriq_request_notify($id,'reabierta');
}
/** Correo al solicitante tras un cambio de estado (T37): avance, cierre o reapertura. */
function aeriq_request_notify(int $id,string $kind): array {
    $r=aeriq_one('SELECT s.id,s.email,s.contacto,s.entidad,sv.nombre AS servicio,e.codigo,e.nombre AS estado FROM solicitudes s JOIN servicios sv ON sv.id=s.servicio_id JOIN estados_solicitud e ON e.id=s.estado_id WHERE s.id=?',[$id]);
    $explain=['pendiente'=>'Tu solicitud está registrada y pendiente de revisión.',
        'en_revision'=>'Estamos estudiando tu solicitud: qué solución encaja, cuántos AERIQ hacen falta y la disponibilidad.',
        'respondida'=>'Te hemos enviado nuestra propuesta. Si tienes dudas, responde a este correo y te ayudamos.'];
    [$title,$subject,$intro,$detail]=match ($kind) {
        'cerrada'=>['Tu solicitud se ha cerrado','AERIQ · Solicitud #'.$id.' cerrada','Hemos cerrado tu solicitud #'.$id.'.',
            'Gracias por confiar en Código Norte. Si quieres retomarla o tienes cualquier duda, responde a este correo y la reabriremos.'],
        'reabierta'=>['Hemos reabierto tu solicitud','AERIQ · Solicitud #'.$id.' reabierta','Hemos vuelto a abrir tu solicitud #'.$id.' y está otra vez en revisión.',
            'Te escribiremos en cuanto haya novedades.'],
        default=>['Tu solicitud avanza','AERIQ · Solicitud #'.$id.': '.$r['estado'],'Tu solicitud #'.$id.' ha pasado a «'.$r['estado'].'».',
            $explain[$r['codigo']]??'Te avisaremos en cada cambio.'],
    };
    $sent=true;
    try {
        $mail=aeriq_mail_compose($title,[['p','Hola, '.$r['contacto'].':'],['p',$intro],
            ['facts',['Número de solicitud'=>'#'.$id,'Servicio'=>$r['servicio'],'Organización'=>$r['entidad'],'Estado actual'=>$r['estado']]],
            ['p',$detail]],$intro);
        aeriq_mail($r['email'],$subject,$mail['text'],(getenv('AERIQ_COMMERCIAL_EMAIL')?:AERIQ_COMMERCIAL_EMAIL_DEFAULT),$mail['html']);
    } catch (Throwable $e) { $sent=false;error_log('AERIQ: estado de la solicitud guardado; correo al solicitante no entregado.'); }
    return ['ok'=>true,'changed'=>true,'estado'=>$r['codigo'],'estado_nombre'=>$r['estado'],'mailed'=>$sent,
        'message'=>'Guardado: ahora está en «'.$r['estado'].'».'.($sent?' Se ha avisado por correo a '.$r['email'].'.':' No se pudo enviar el correo al solicitante; avísale tú.')];
}
