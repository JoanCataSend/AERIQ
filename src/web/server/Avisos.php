<?php
declare(strict_types=1);
/**
 * Avisos entre personas de una organización (HU36), como una solicitud: asunto, mensajes, estado e historial.
 *
 * Quién puede escribir a quién (lo decide siempre el servidor, nunca el formulario):
 *   - el admin principal (permiso campanas.gestionar), con cualquier admin de campaña u operador de su organización;
 *   - el admin de campaña (equipo.gestionar), con los operadores de su campaña y con el admin principal;
 *   - el operador, con el admin de su campaña y con el admin principal.
 * Dos operadores no se escriben entre sí. Solo las dos personas de un aviso pueden leerlo y responderlo.
 *
 * Estados (tabla estados_aviso): abierto al crearlo o si responde quien lo creó; respondido si responde la otra
 * persona; cerrado cuando cualquiera de las dos lo cierra. Responder a un aviso cerrado lo vuelve a abrir.
 * «Nuevo»: hay mensajes de la otra persona posteriores a la última vez que lo abriste (visto_*_en).
 */

const AERIQ_AVISO_MAX=4000;

/** SQL: la cuenta del alias $u tiene el permiso $p (los permisos van por rol, nunca por nombre de rol). */
/**
 * Diseño lógico: (string $u,string $p) → aeriq_notice_perm() → string; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_perm(string $u,string $p): string {
    return "EXISTS (SELECT 1 FROM rol_permisos rp JOIN permisos pp ON pp.id=rp.permiso_id WHERE rp.rol_id=$u.rol_id AND pp.codigo='$p')";
}

/** Personas a las que la cuenta puede escribir: id, nombre, función y campaña. */
/**
 * Diseño lógico: (array $a,?int $only=null) → aeriq_notice_contacts() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_contacts(array $a,?int $only=null): array {
    $org=aeriq_org($a);
    if (aeriq_can($a,'campanas.gestionar')) {
        $rule='1=1';$params=[$org,(int)$a['usuario_id']];
    } else {
        $campaign=(int)($a['campana_id']??0);
        // Con el admin principal siempre; dentro de la campaña, si una de las dos personas gestiona el equipo.
        $rule=aeriq_notice_perm('u','campanas.gestionar').' OR (u.campana_id=? AND ('.(aeriq_can($a,'equipo.gestionar')?'1=1':aeriq_notice_perm('u','equipo.gestionar')).'))';
        $params=[$org,(int)$a['usuario_id'],$campaign];
    }
    $sql='SELECT u.id,TRIM(CONCAT(u.nombre,\' \',u.apellidos)) AS nombre,r.nombre AS rol_nombre,c.nombre AS campana FROM usuarios u JOIN roles r ON r.id=u.rol_id LEFT JOIN campanas c ON c.id=u.campana_id'
        .' WHERE u.organizacion_id=? AND u.id<>? AND (u.activo=1 OR u.debe_cambiar_clave=1) AND r.requiere_organizacion=1 AND ('.$rule.')';
    if ($only!==null) {$sql.=' AND u.id=?';$params[]=$only;}
    return aeriq_rows($sql.' ORDER BY r.id DESC,u.nombre,u.apellidos',$params);
}

/** Aviso del que la cuenta es una de las dos partes, con bloqueo opcional; si no, 404. */
/**
 * Diseño lógico: (array $a,int $id,bool $lock=false) → aeriq_notice_own() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_own(array $a,int $id,bool $lock=false): array {
    $n=aeriq_one('SELECT * FROM avisos WHERE id=? AND organizacion_id=? AND (creado_por=? OR destinatario_id=?)'.($lock?' FOR UPDATE':''),[$id,aeriq_org($a),(int)$a['usuario_id'],(int)$a['usuario_id']]);
    if (!$n) throw new AeriqError('Aviso no encontrado.','not_found');
    return $n;
}

/**
 * Gestiona aviso texto.
 * Diseño lógico: (array $d,string $key,int $max,string $message) → aeriq_notice_text() → string; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_text(array $d,string $key,int $max,string $message): string {
    $v=is_string($d[$key]??null)?trim($d[$key]):'';
    $length=function_exists('mb_strlen')?mb_strlen($v,'UTF-8'):strlen($v);
    if ($v==='' || $length>$max) throw new AeriqError($message,'invalid_input',$key);
    return $v;
}

/** Bandeja: los avisos de la cuenta (más recientes primero), cuántos tienen algo nuevo y a quién puede escribir. */
/**
 * Diseño lógico: (array $a) → aeriq_notices() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notices(array $a): array {
    $me=(int)$a['usuario_id'];
    $rows=aeriq_rows("SELECT n.id,n.asunto,n.creado_en,n.actualizado_en,e.codigo AS estado,e.nombre AS estado_nombre,(n.creado_por=?) AS mio,"
        ."o.id AS con_id,TRIM(CONCAT(o.nombre,' ',o.apellidos)) AS con_nombre,r.nombre AS con_rol,"
        ."(SELECT m.texto FROM aviso_mensajes m WHERE m.aviso_id=n.id ORDER BY m.creado_en DESC,m.id DESC LIMIT 1) AS ultimo,"
        ."EXISTS(SELECT 1 FROM aviso_mensajes m WHERE m.aviso_id=n.id AND m.autor_id<>? AND m.creado_en>COALESCE(IF(n.creado_por=?,n.visto_creador_en,n.visto_destinatario_en),'1970-01-01')) AS nuevo"
        ." FROM avisos n JOIN estados_aviso e ON e.id=n.estado_id JOIN usuarios o ON o.id=IF(n.creado_por=?,n.destinatario_id,n.creado_por) JOIN roles r ON r.id=o.rol_id"
        ." WHERE n.organizacion_id=? AND (n.creado_por=? OR n.destinatario_id=?) ORDER BY n.actualizado_en DESC,n.id DESC LIMIT 300",
        [$me,$me,$me,$me,aeriq_org($a),$me,$me]);
    $unread=0;
    foreach ($rows as &$r) {
        $r['id']=(int)$r['id'];$r['con_id']=(int)$r['con_id'];$r['mio']=(bool)(int)$r['mio'];$r['nuevo']=(bool)(int)$r['nuevo'];
        if ($r['nuevo']) $unread++;
        if ($r['ultimo']!==null && mb_strlen($r['ultimo'])>140) $r['ultimo']=mb_substr($r['ultimo'],0,140).'…';
    }
    unset($r);
    $contacts=array_map(fn($c)=>['id'=>(int)$c['id'],'nombre'=>$c['nombre'],'rol'=>$c['rol_nombre'],'campana'=>$c['campana']],aeriq_notice_contacts($a));
    return ['notices'=>$rows,'unread'=>$unread,'contacts'=>$contacts,'states'=>aeriq_rows('SELECT codigo,nombre,final FROM estados_aviso ORDER BY orden')];
}

/** Solo el número de avisos con algo nuevo (para el aviso de la navegación). */
/**
 * Diseño lógico: (array $a) → aeriq_notices_unread() → int; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notices_unread(array $a): int {
    $me=(int)$a['usuario_id'];
    return (int)(aeriq_one("SELECT COUNT(*) AS n FROM avisos n WHERE n.organizacion_id=? AND (n.creado_por=? OR n.destinatario_id=?) AND EXISTS(SELECT 1 FROM aviso_mensajes m WHERE m.aviso_id=n.id AND m.autor_id<>? AND m.creado_en>COALESCE(IF(n.creado_por=?,n.visto_creador_en,n.visto_destinatario_en),'1970-01-01'))",
        [aeriq_org($a),$me,$me,$me,$me])['n']??0);
}

/** Un aviso con todos sus mensajes. Abrirlo lo marca como visto para quien lo abre. */
/**
 * Diseño lógico: (array $a,int $id) → aeriq_notice() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice(array $a,int $id): array {
    $me=(int)$a['usuario_id'];$n=aeriq_notice_own($a,$id);
    aeriq_sql('UPDATE avisos SET '.((int)$n['creado_por']===$me?'visto_creador_en':'visto_destinatario_en').'=UTC_TIMESTAMP(3) WHERE id=?',[$id]);
    $other=(int)$n['creado_por']===$me?(int)$n['destinatario_id']:(int)$n['creado_por'];
    $who=aeriq_one("SELECT TRIM(CONCAT(u.nombre,' ',u.apellidos)) AS nombre,r.nombre AS rol FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.id=?",[$other]);
    $state=aeriq_one('SELECT codigo,nombre,final FROM estados_aviso WHERE id=?',[(int)$n['estado_id']]);
    $messages=aeriq_rows("SELECT m.id,m.texto,m.creado_en,(m.autor_id=?) AS mio,TRIM(CONCAT(u.nombre,' ',u.apellidos)) AS autor FROM aviso_mensajes m JOIN usuarios u ON u.id=m.autor_id WHERE m.aviso_id=? ORDER BY m.creado_en,m.id",[$me,$id]);
    foreach ($messages as &$m) {$m['id']=(int)$m['id'];$m['mio']=(bool)(int)$m['mio'];}
    unset($m);
    return ['notice'=>['id'=>(int)$n['id'],'asunto'=>$n['asunto'],'estado'=>$state['codigo'],'estado_nombre'=>$state['nombre'],'cerrado'=>(bool)(int)$state['final'],
        'creado_en'=>$n['creado_en'],'mio'=>(int)$n['creado_por']===$me,'con_id'=>$other,'con_nombre'=>$who['nombre']??'','con_rol'=>$who['rol']??''],'messages'=>$messages];
}

/** Crea un aviso para una persona a la que la cuenta puede escribir. */
/**
 * Diseño lógico: (array $a,array $d) → aeriq_notice_create() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_create(array $a,array $d): array {
    $to=aeriq_int($d['to']??null);
    $subject=aeriq_notice_text($d,'subject',160,'Escribe el asunto (máximo 160 caracteres).');
    $text=aeriq_notice_text($d,'message',AERIQ_AVISO_MAX,'Escribe el mensaje (máximo '.AERIQ_AVISO_MAX.' caracteres).');
    if (!aeriq_notice_contacts($a,$to)) throw new AeriqError('No puedes enviar avisos a esa persona.','forbidden','to');
    $db=conexionBD();$db->begin_transaction();
    try {
        aeriq_sql("INSERT INTO avisos(organizacion_id,creado_por,destinatario_id,asunto,estado_id,creado_en,actualizado_en,visto_creador_en) VALUES(?,?,?,?,(SELECT id FROM estados_aviso WHERE codigo='abierto'),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",[aeriq_org($a),(int)$a['usuario_id'],$to,$subject]);
        $id=(int)$db->insert_id;
        aeriq_sql('INSERT INTO aviso_mensajes(aviso_id,autor_id,texto,creado_en) VALUES(?,?,?,UTC_TIMESTAMP(3))',[$id,(int)$a['usuario_id'],$text]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true,'id'=>$id,'message'=>'Aviso enviado.'];
}

/** Responde a un aviso. Si responde la otra persona queda «respondido»; si responde quien lo creó, «abierto». */
/**
 * Diseño lógico: (array $a,array $d) → aeriq_notice_reply() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_reply(array $a,array $d): array {
    $id=aeriq_int($d['id']??null);$me=(int)$a['usuario_id'];
    $text=aeriq_notice_text($d,'message',AERIQ_AVISO_MAX,'Escribe el mensaje (máximo '.AERIQ_AVISO_MAX.' caracteres).');
    $db=conexionBD();$db->begin_transaction();
    try {
        $n=aeriq_notice_own($a,$id,true);$creator=(int)$n['creado_por']===$me;
        aeriq_sql('INSERT INTO aviso_mensajes(aviso_id,autor_id,texto,creado_en) VALUES(?,?,?,UTC_TIMESTAMP(3))',[$id,$me,$text]);
        aeriq_sql('UPDATE avisos SET estado_id=(SELECT id FROM estados_aviso WHERE codigo=?),actualizado_en=UTC_TIMESTAMP(3),'.($creator?'visto_creador_en':'visto_destinatario_en').'=UTC_TIMESTAMP(3) WHERE id=?',[$creator?'abierto':'respondido',$id]);
        $db->commit();
    } catch (Throwable $e) {$db->rollback();throw $e;}
    return ['ok'=>true,'id'=>$id,'message'=>'Respuesta enviada.'];
}

/** Cierra un aviso (cualquiera de las dos personas). Se puede reabrir respondiendo. */
/**
 * Diseño lógico: (array $a,array $d) → aeriq_notice_close() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_close(array $a,array $d): array {
    $id=aeriq_int($d['id']??null);aeriq_notice_own($a,$id);
    aeriq_sql("UPDATE avisos SET estado_id=(SELECT id FROM estados_aviso WHERE codigo='cerrado'),actualizado_en=UTC_TIMESTAMP(3) WHERE id=?",[$id]);
    return ['ok'=>true,'id'=>$id,'message'=>'Aviso cerrado. Si respondes, se vuelve a abrir.'];
}

/** Rutas comunes de portal.php (sesión web) y mobile-avisos.php (token del móvil). */
/**
 * Diseño lógico: (array $a,string $method,string $action) → aeriq_notice_route() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_notice_route(array $a,string $method,string $action): array {
    if (!aeriq_org($a)) throw new AeriqError('Los avisos son para cuentas de una organización.','forbidden');
    if ($method==='GET' && $action==='notices') return aeriq_notices($a);
    if ($method==='GET' && $action==='notice-unread') return ['unread'=>aeriq_notices_unread($a)];
    if ($method==='GET' && $action==='notice') return aeriq_notice($a,aeriq_int($_GET['id']??null));
    if ($method==='POST' && $action==='notice-create') return aeriq_notice_create($a,aeriq_json_body());
    if ($method==='POST' && $action==='notice-reply') return aeriq_notice_reply($a,aeriq_json_body());
    if ($method==='POST' && $action==='notice-close') return aeriq_notice_close($a,aeriq_json_body());
    throw new AeriqError('Operación o método no disponible.','method_not_allowed');
}
