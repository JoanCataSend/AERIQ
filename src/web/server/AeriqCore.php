<?php
/*
 * Fichero: AeriqCore.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/server.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);
require_once __DIR__.'/AeriqMail.php';
require_once __DIR__.'/Avisos.php';
require_once __DIR__.'/../business_logic/DomainError.php';
require_once __DIR__.'/../communication/MailTransport.php';
require_once __DIR__.'/../database/SqlGateway.php';
require_once __DIR__.'/../database/AeriqRepository.php';

/**
 * Diseño lógico: id: Z --> aeriq_access() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
function aeriq_access(int $id): array {
    $a=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_ACCESS_1,[$id]);
    if (!$a) throw new AeriqError('Cuenta no disponible.','unauthenticated');
    $a['permisos']=array_column(AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_ACCESS_2,[(int)$a['rol_id']]),'codigo');
    // Un rol de organización sin organización no concede permisos (antes era un CHECK sobre el ENUM).
    if ((int)$a['requiere_organizacion'] && !$a['organizacion_id']) $a['permisos']=[];
    // Un admin de campaña sin campaña no concede permisos: si no, vería toda la organización.
    if ($a['rol']==='admin_campana' && !$a['campana_id']) $a['permisos']=[];
    return $a;
}
/**
 * Diseño lógico: access: Text, permission: Text --> aeriq_can() --> B
 * Descripción: Procesa aeriq can y devuelve el resultado correspondiente.
 */
function aeriq_can(array $access, string $permission): bool { return in_array($permission,$access['permisos']??[],true); }

/**
 * Diseño lógico: u: Text, permission: Text --> aeriq_ready() --> Text
 * Descripción: Procesa aeriq ready y devuelve el resultado correspondiente.
 */
function aeriq_ready(array $u, ?string $permission = null): array {
    auth_login_allowed($u); $a=aeriq_access((int)$u['id']);
    if ((int)$a['cambiar_clave']) throw new AeriqError('Activa tu cuenta sustituyendo la contraseña temporal.','activation_required');
    if (!(int)$a['activo']) throw new AeriqError('La cuenta está desactivada.','forbidden');
    if ($permission!==null && !aeriq_can($a,$permission)) throw new AeriqError('Tu función no permite esta operación.','forbidden');
    return $a;
}
/**
 * Diseño lógico: usuario: Usuario, accion: Text, metodo: Text, filtros: FiltrosPanel --> aeriq_portal_access() --> acceso: Acceso
 * Descripción: aplica la política de permisos del portal según la operación solicitada.
 */
/**
 * Diseño lógico: user: Text, action: Text, method: Text, filters: Text --> aeriq_portal_access() --> Text
 * Descripción: Procesa aeriq portal access y devuelve el resultado correspondiente.
 */
function aeriq_portal_access(array $user,string $action,string $method,array $filters=[]): array {
    if ($action==='map' && $method==='GET') return aeriq_ready($user,'mapa.organizacion');
    if (($action==='requests' && $method==='GET') || (in_array($action,['request-status','request-reopen'],true) && $method==='POST')) return aeriq_ready($user,'solicitudes.gestionar');
    if ($action==='my-campaign' && $method==='GET') return aeriq_ready($user,'mediciones.registrar');

    $access=aeriq_ready($user);
    if (str_starts_with($action,'notice')) return $access;

    if ($action==='campaign' && $method==='POST') return aeriq_ready($user,'campanas.gestionar');
    if (($action==='device' || $action==='campaign-settings') && $method==='POST') return aeriq_ready($user,'equipo.gestionar');
    if ($action==='readings' && $method==='GET') {
        $access=aeriq_ready($user,'informes.ver');
        if (!empty($filters['export'])) aeriq_ready($user,'datos.exportar');
        return $access;
    }
    if ($action==='worker' && $method==='POST') {
        if (!aeriq_can($access,'campanas.gestionar') && !aeriq_can($access,'equipo.gestionar')) throw new AeriqError('Tu función no permite crear cuentas.','forbidden');
        return $access;
    }
    if (in_array($action,['dashboard','worker-edit','worker-status','worker-invite'],true)) {
        if (!aeriq_can($access,'informes.ver') && !aeriq_can($access,'campanas.gestionar')) throw new AeriqError('Tu función no permite esta operación.','forbidden');
        return $access;
    }
    if (!aeriq_can($access,'informes.ver') && !aeriq_can($access,'campanas.gestionar')) throw new AeriqError('Tu función no permite esta operación.','forbidden');
    return $access;
}
/**
 * Diseño lógico: v: Text, min: Z, max: Z --> aeriq_int() --> Z
 * Descripción: Procesa aeriq int y devuelve el resultado correspondiente.
 */
function aeriq_int(mixed $v, int $min=1, int $max=PHP_INT_MAX): int {
    if (filter_var($v,FILTER_VALIDATE_INT)===false || (int)$v<$min || (int)$v>$max) throw new AeriqError('Número entero fuera de rango.');
    return (int)$v;
}
/**
 * Diseño lógico: v: Text, max: Z, optional: B --> aeriq_text() --> Text
 * Descripción: Procesa aeriq text y devuelve el resultado correspondiente.
 */
function aeriq_text(mixed $v, int $max=200, bool $optional=false): string {
    if (!is_string($v)) throw new AeriqError('Texto no válido.');
    $v=trim($v); if ((!$optional && $v==='') || strlen($v)>$max) throw new AeriqError('Revisa los campos de texto.'); return $v;
}
/**
 * Diseño lógico: v: Text, min: R, max: R --> aeriq_number() --> R
 * Descripción: Procesa aeriq number y devuelve el resultado correspondiente.
 */
function aeriq_number(mixed $v,float $min,float $max): float {
    if (!is_numeric($v) || !is_finite((float)$v) || (float)$v<$min || (float)$v>$max) throw new AeriqError('Valor numérico fuera de rango.'); return (float)$v;
}
/**
 * Diseño lógico: v: Text --> aeriq_bool() --> Z
 * Descripción: Procesa aeriq bool y devuelve el resultado correspondiente.
 */
function aeriq_bool(mixed $v): int { if (!in_array($v,[true,false,0,1,'0','1'],true)) throw new AeriqError('Valor booleano no válido.'); return (int)(bool)$v; }
/**
 * Diseño lógico: a: Text --> aeriq_org() --> Z
 * Descripción: Procesa aeriq org y devuelve el resultado correspondiente.
 */
function aeriq_org(array $a): int { if (!$a['organizacion_id']) throw new AeriqError('La cuenta no pertenece a una organización.','forbidden'); return (int)$a['organizacion_id']; }
/**
 * Diseño lógico: table: Text, id: Z, org: Z --> aeriq_own() --> Text
 * Descripción: Procesa aeriq own y devuelve el resultado correspondiente.
 */
function aeriq_own(string $table,int $id,int $org): array {
    if (!in_array($table,['dispositivos','campanas'],true)) throw new LogicException('Tabla no permitida');
    $r=AeriqRepository::ownedResource($table,$id,$org);
    if (!$r) throw new AeriqError('El recurso no está disponible en tu organización.','not_found'); return $r;
}
/**
 * Diseño lógico: a: Text --> aeriq_campaign() --> Z
 * Descripción: Procesa aeriq campaign y devuelve el resultado correspondiente.
 */
function aeriq_campaign(array $a): ?int { return $a['campana_id']===null?null:(int)$a['campana_id']; }
/**
 * Diseño lógico: campaign: Z, except: Z --> aeriq_campaign_admin() --> Text
 * Descripción: Procesa aeriq campaign admin y devuelve el resultado correspondiente.
 */
function aeriq_campaign_admin(int $campaign,int $except=0): ?string {
    $r=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_CAMPAIGN_ADMIN_1,[$campaign,$except]);
    return $r?$r['n']:null;
}
/**
 * Diseño lógico: id: Z --> aeriq_password_changed()
 * Descripción: Procesa aeriq password changed y devuelve el resultado correspondiente..
 */
function aeriq_password_changed(int $id): void {
    AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_PASSWORD_CHANGED_1,[$id]);
}

/**
 * Diseño lógico: a: Text, d: Text --> aeriq_worker() --> Text
 * Descripción: Procesa aeriq worker y devuelve el resultado correspondiente.
 */
function aeriq_worker(array $a,array $d): array {
    $org=aeriq_org($a);$email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);$last=auth_string($d['last']??'',120);$phone=aeriq_text($d['phone']??'',40,true);
    // El admin principal crea el admin de una campaña; el admin de campaña crea operadores de la suya.
    // El rol y la campaña los decide el servidor, nunca el formulario.
    if (aeriq_can($a,'campanas.gestionar')) {
        if (empty($d['campaign'])) throw new AeriqError('Elige la campaña que va a gestionar.','invalid_input','campaign');
        $campaign=aeriq_int($d['campaign']);aeriq_own('campanas',$campaign,$org);$code='admin_campana';
        if ($who=aeriq_campaign_admin($campaign)) throw new AeriqError("Esta campaña ya tiene admin: $who. Desactívalo antes de asignar otro.",'conflict','campaign');
    } else {
        $campaign=aeriq_campaign($a);$code='trabajador';
        if (!$campaign) throw new AeriqError('Tu cuenta no tiene campaña asignada.','forbidden');
    }
    $role=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_WORKER_1,[$code]);
    if (!$role) throw new AeriqError('Función no permitida.');
    aeriq_mailer_ready();AeriqRepository::begin();
    try {
        if (auth_find($email)) throw new AeriqError('Ese correo ya tiene una cuenta. No se cambiará su organización.','conflict');
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_WORKER_2,[$org,(int)$role['id'],$campaign,$name,$last,$email,$phone,'!']);$id=(int)AeriqRepository::lastInsertId();
        aeriq_invite($id,$email);
        AeriqRepository::commit();return ['ok'=>true,'id'=>$id,'message'=>'Invitación registrada. El servidor ha aceptado el correo; la cuenta se activará al cambiar la contraseña.'];
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
}

/**
 * Diseño lógico: id: Z, email: Text --> aeriq_invite()
 * Descripción: Procesa aeriq invite y devuelve el resultado correspondiente..
 */
function aeriq_invite(int $id,string $email): void {
    $hours=max(1,min(168,(int)(getenv('AERIQ_INVITATION_HOURS')?:24)));
    $temporary=bin2hex(random_bytes(16));
    AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_INVITE_1,[password_hash($temporary,PASSWORD_DEFAULT),$hours,$id]);
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
/**
 * Diseño lógico: a: Text, id: Z --> aeriq_managed() --> Text
 * Descripción: Procesa aeriq managed y devuelve el resultado correspondiente.
 */
function aeriq_managed(array $a,int $id): array {
    $org=aeriq_org($a);
    if (aeriq_can($a,'campanas.gestionar')) $u=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_MANAGED_1,[$id,$org]);
    elseif (aeriq_can($a,'equipo.gestionar') && aeriq_campaign($a)) $u=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_MANAGED_2,[$id,$org,aeriq_campaign($a)]);
    else throw new AeriqError('Tu función no permite gestionar cuentas.','forbidden');
    if (!$u) throw new AeriqError('Esa cuenta no está entre las que gestionas.','not_found');
    return $u;
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_worker_edit() --> Text
 * Descripción: Procesa aeriq worker edit y devuelve el resultado correspondiente.
 */
function aeriq_worker_edit(array $a,array $d): array {
    $u=aeriq_managed($a,aeriq_int($d['id']??null));
    $phone=trim(is_string($d['phone']??null)?$d['phone']:'');if (mb_strlen($phone)>40) throw new AeriqError('Teléfono demasiado largo.','invalid_input','phone');
    AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_WORKER_EDIT_1,[auth_string($d['name']??'',90),auth_string($d['last']??'',120),$phone,(int)$u['id']]);
    return ['ok'=>true,'message'=>'Datos guardados.'];
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_worker_status() --> Text
 * Descripción: Procesa aeriq worker status y devuelve el resultado correspondiente.
 */
function aeriq_worker_status(array $a,array $d): array {
    $u=aeriq_managed($a,aeriq_int($d['id']??null));$id=(int)$u['id'];$active=aeriq_bool($d['active']??false);
    if ($active) {
        if (!$u['verificado_en']) throw new AeriqError('Esta cuenta aún no se ha activado: reenvía la invitación.','conflict');
        // Solo al reactivar a un admin de campaña (lo hace el admin principal): una campaña, un admin.
        if (aeriq_can($a,'campanas.gestionar') && $u['campana_id'] && ($who=aeriq_campaign_admin((int)$u['campana_id'],$id))) throw new AeriqError("Su campaña ya tiene otro admin: $who. Desactívalo antes.",'conflict');
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_WORKER_STATUS_1,[$id]);return ['ok'=>true,'message'=>'Cuenta reactivada.'];
    }
    AeriqRepository::begin();
    try {
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_WORKER_STATUS_2,[$id]);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_WORKER_STATUS_3,[$id]);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_WORKER_STATUS_4,[$id]);
        AeriqRepository::commit();
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
    return ['ok'=>true,'message'=>'Cuenta desactivada. Su AERIQ queda libre para asignarlo a otra persona.'];
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_worker_invite() --> Text
 * Descripción: Procesa aeriq worker invite y devuelve el resultado correspondiente.
 */
function aeriq_worker_invite(array $a,array $d): array {
    $u=aeriq_managed($a,aeriq_int($d['id']??null));
    if ($u['verificado_en']) throw new AeriqError('Esta cuenta ya está activa: si olvidó la contraseña, puede recuperarla desde el inicio de sesión.','conflict');
    aeriq_mailer_ready();AeriqRepository::begin();
    try {aeriq_invite((int)$u['id'],$u['email']);AeriqRepository::commit();} catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
    return ['ok'=>true,'message'=>'Invitación reenviada a '.$u['email'].'.'];
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_campaign_save() --> Text
 * Descripción: Procesa aeriq campaign save y devuelve el resultado correspondiente.
 */
function aeriq_campaign_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])&&$d['id']!==''?aeriq_int($d['id']):null;
    if ($id) aeriq_own('campanas',$id,$org);
    $start=aeriq_text($d['start']??'',10);$end=aeriq_text($d['end']??'',10);
    foreach ([$start,$end] as $date) { $v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Fecha no válida.'); }
    if ($end<$start) throw new AeriqError('La fecha final debe ser posterior al inicio.');
    $name=aeriq_text($d['name']??'',160);$zoneName=aeriq_text($d['zone']??'');
    AeriqRepository::begin();
    try {
        $zone=aeriq_zone($org,$zoneName);
        if ($id) AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_CAMPAIGN_SAVE_1,[$name,$zone,$start,$end,$id,$org]);
        // Nueva: privada, distancia por defecto y umbrales de referencia hasta que su admin los ajuste.
        else { AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_CAMPAIGN_SAVE_2,[$name,$zone,$start,$end,$org]);$new=(int)AeriqRepository::lastInsertId(); }
        AeriqRepository::commit();
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
    return $id?['ok'=>true,'id'=>$id,'message'=>'Cambios de la campaña guardados.']:['ok'=>true,'id'=>$new,'message'=>'Campaña creada. Ahora asígnale un admin en «Admins de campaña».'];
}
/**
 * Diseño lógico: a: Text --> aeriq_my_campaign() --> Text
 * Descripción: Procesa aeriq my campaign y devuelve el resultado correspondiente.
 */
function aeriq_my_campaign(array $a): array {
    $org=aeriq_org($a);$id=aeriq_campaign($a);$uid=(int)$a['usuario_id'];
    if (!$id) throw new AeriqError('Todavía no perteneces a ninguna campaña: tu admin de campaña te añadirá.','not_found');
    return AeriqRepository::myCampaign($id,$org,$uid);
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_campaign_settings() --> Text
 * Descripción: Procesa aeriq campaign settings y devuelve el resultado correspondiente.
 */
function aeriq_campaign_settings(array $a,array $d): array {
    $id=aeriq_campaign($a);if (!$id) throw new AeriqError('Tu cuenta no tiene campaña asignada.','forbidden');
    $mid=aeriq_number($d['medium']??null,0,65535);$high=aeriq_number($d['high']??null,0,65535);
    if ($high<=$mid) throw new AeriqError('El umbral alto debe superar al moderado.','invalid_input','high');
    $radius=aeriq_int($d['radius']??30,20,50);$public=aeriq_bool($d['public']??false);
    AeriqRepository::begin();
    try {
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_CAMPAIGN_SETTINGS_1,[$public,$radius,$id,aeriq_org($a)]);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_CAMPAIGN_SETTINGS_2,[$id,$mid,$high]);
        AeriqRepository::commit();
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
    return ['ok'=>true,'id'=>$id,'message'=>'Ajustes de la campaña guardados.'];
}
/**
 * Diseño lógico: org: Z, name: Text --> aeriq_zone() --> Z
 * Descripción: Procesa aeriq zone y devuelve el resultado correspondiente.
 */
function aeriq_zone(int $org,string $name): int {
    AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_ZONE_1,[$org,$name]);
    return (int)AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_ZONE_2,[$org,$name])['id'];
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_device_save() --> Text
 * Descripción: Procesa aeriq device save y devuelve el resultado correspondiente.
 */
function aeriq_device_save(array $a,array $d): array {
    $org=aeriq_org($a);$id=isset($d['id'])?aeriq_int($d['id']):null;$scope=aeriq_campaign($a);
    if (!$scope) throw new AeriqError('Solo el admin de una campaña registra AERIQ.','forbidden');
    if ($id && !AeriqRepository::deviceBelongsToCampaign($id,$org,$scope)) throw new AeriqError('El AERIQ no pertenece a tu campaña.','not_found');
    $mac=strtoupper(aeriq_text($d['address']??'',17));if (!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D',$mac)) throw new AeriqError('Dirección BLE no válida.');
    // La campaña es siempre la del admin; el AERIQ se asigna a un operador de esa campaña.
    if (empty($d['worker'])) throw new AeriqError('Asigna el AERIQ a un operador de tu campaña.','invalid_input','worker');
    $worker=aeriq_int($d['worker']);$campaign=$scope;
    if (!AeriqRepository::workerEligibleForCampaign($worker,$org,$scope)) throw new AeriqError('Ese operador no es de tu campaña.','forbidden');
    $active=aeriq_bool($d['active']??true);
    if (!$id && !$active) throw new AeriqError('Un AERIQ nuevo se registra activo.');$simulated=aeriq_bool($d['simulated']??false);$name=aeriq_text($d['name']??'',160);
    AeriqRepository::begin();
    try {
        if ($id) {
            $previous=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_5,[$id,$org]);
            if (!$previous) throw new AeriqError('Dispositivo no disponible en tu organización.','not_found');
            AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_6,[$name,$mac,$active,$simulated,$id]);
        } else {AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_7,[$org,$name,$mac,$active,$simulated]);$id=(int)AeriqRepository::lastInsertId();}
        $old=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_8,[$id]);
        $same=$old && $active && $worker && (int)$old['trabajador_id']===$worker && (int)$old['campana_id']===$campaign;
        if (!$same) {
            AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_9,[$id]);
            if ($active && $worker) {
                // Un AERIQ por operador: si ya llevaba otro, ese queda libre.
                AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_10,[$worker]);
                AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_11,[$org,$id,$worker,$campaign]);
            }
        }
        AeriqRepository::commit();return ['ok'=>true,'id'=>$id];
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
}

/**
 * Diseño lógico: a: Text --> aeriq_assignments() --> Text
 * Descripción: Procesa aeriq assignments y devuelve el resultado correspondiente.
 */
function aeriq_assignments(array $a): array {
    // Rangos de ozono y temperatura (HU35): la app los comprueba antes de enviar; el servidor vuelve a hacerlo.
    return AeriqRepository::assignments(aeriq_org($a),(int)$a['usuario_id']);
}

/**
 * Diseño lógico: v: Text --> aeriq_capture_time() --> Text
 * Descripción: Procesa aeriq capture time y devuelve el resultado correspondiente.
 */
function aeriq_capture_time(mixed $v): string {
    $text=aeriq_text($v,30);if (!preg_match('/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?Z$/D',$text)) throw new AeriqError('Fecha de captura UTC no válida.');
    $time=new DateTimeImmutable($text);
    if ($time->format(str_contains($text,'.')?'Y-m-d\TH:i:s.v\Z':'Y-m-d\TH:i:s\Z')!==$text) throw new AeriqError('Fecha de captura inexistente.');
    if ($time->getTimestamp()>time()+300 || $time->getTimestamp()<time()-86400*30) throw new AeriqError('Fecha de captura fuera de rango.');
    return $time->setTimezone(new DateTimeZone('UTC'))->format('Y-m-d H:i:s.v');
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_ingest() --> Text
 * Descripción: Procesa aeriq ingest y devuelve el resultado correspondiente.
 */
function aeriq_ingest(array $a,array $d): array {
    $device=aeriq_int($d['deviceId']??null);$campaign=aeriq_int($d['campaignId']??null);$event=aeriq_text($d['eventId']??'',36);
    if (!preg_match('/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/Di',$event)) throw new AeriqError('Identificador de lectura no válido.');
    $type=aeriq_int($d['type']??null);
    // La magnitud debe existir y ser una de las que mide el modelo de ese AERIQ.
    $magnitude=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_INGEST_1,[$type,$device]);
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
    AeriqRepository::begin();
    try {
        $orb=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_INGEST_2,[$device,aeriq_org($a),(int)$a['usuario_id']]);
        if (!$orb || !(int)$orb['activo'] || !(int)$orb['campana_activa']) throw new AeriqError('AERIQ o campaña no asignados o inactivos.','forbidden');
        if ((int)$orb['campana_id']!==$campaign || (isset($d['assignmentId']) && (int)$d['assignmentId']!==(int)$orb['asignacion_id'])) throw new AeriqError('La asignación ha cambiado. Actualiza tus asignaciones.','conflict');
        if (strtoupper((string)($d['address']??''))!==$orb['direccion_ble']) throw new AeriqError('El anuncio no corresponde al AERIQ asignado.','forbidden');
        $date=substr($captured,0,10);if ($date<$orb['inicio'] || $date>$orb['fin'] || $captured<$orb['iniciada_en']) throw new AeriqError('Lectura fuera de las fechas de la asignación/campaña.');
        $old=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_INGEST_3,[$event]);
        if ($old) {
            if ((int)$old['dispositivo_id']!==$device || (int)$old['trabajador_id']!==(int)$a['usuario_id'] || (int)$old['campana_id']!==$campaign || (int)$old['magnitud_id']!==$type || (float)$old['valor']!==$value || $old['capturada_en']!==$captured || ($old['latitud']===null?null:(float)$old['latitud'])!==$lat || ($old['longitud']===null?null:(float)$old['longitud'])!==$lon || ($old['precision_m']===null?null:(float)$old['precision_m'])!==$accuracy || (int)$old['contador']!==$count || (int)$old['rssi']!==$rssi) throw new AeriqError('La clave de lectura ya identifica otros datos.','conflict');
            AeriqRepository::commit();return ['ok'=>true,'duplicate'=>true,'id'=>(int)$old['id'],'eventId'=>$event,'valid'=>(bool)(int)$old['valida'],'reason'=>$old['motivo_descarte']];
        }
        $reason=aeriq_discard_reason($magnitude,(int)$orb['asignacion_id'],$type,$value,$captured);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_INGEST_4,[$event,(int)$orb['asignacion_id'],$type,$value,$captured,$lat,$lon,$accuracy,$count,$rssi,(int)$orb['simulado'],$reason===null?1:0,$reason]);
        $id=(int)AeriqRepository::lastInsertId();AeriqRepository::commit();return ['ok'=>true,'duplicate'=>false,'id'=>$id,'eventId'=>$event,'valid'=>$reason===null,'reason'=>$reason];
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
}

/**
 * Motivo para descartar una lectura (HU35), o null si es válida.
 * - 'fuera_de_rango': fuera de [valor_minimo, valor_maximo] de su magnitud.
 * - 'anomala': se aleja más de salto_maximo de la media de las 5 lecturas anteriores de la misma asignación y
 *   magnitud (de los 10 minutos previos, al menos 3). La media incluye las anómalas pero no las fuera de rango:
 *   un pico suelto se descarta, pero si el nivel cambia de verdad, la media lo alcanza en dos o tres lecturas.
 * Los límites están en la tabla magnitudes: se ajustan sin tocar código.
 */
/**
 * Diseño lógico: magnitude: Text, assignment: Z, type: Z, value: R, captured: Text --> aeriq_discard_reason() --> Text
 * Descripción: Procesa aeriq discard reason y devuelve el resultado correspondiente.
 */
function aeriq_discard_reason(array $magnitude,int $assignment,int $type,float $value,string $captured): ?string {
    if ($value<(float)$magnitude['valor_minimo'] || $value>(float)$magnitude['valor_maximo']) return 'fuera_de_rango';
    if ($magnitude['salto_maximo']===null) return null;
    $since=(new DateTimeImmutable($captured,new DateTimeZone('UTC')))->modify('-10 minutes')->format('Y-m-d H:i:s.v');
    $previous=AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_DISCARD_REASON_1,[$assignment,$type,$captured,$since]);
    if (count($previous)<3) return null;
    $mean=array_sum(array_map(fn($r)=>(float)$r['valor'],$previous))/count($previous);
    return abs($value-$mean)>(float)$magnitude['salto_maximo']?'anomala':null;
}

/**
 * Batería que el AERIQ anuncia por BLE, enviada por la app de quien lo tiene asignado.
 * Solo AERIQ reales: un AERIQ simulado no tiene batería que medir. Para no llenar la tabla,
 * el mismo porcentaje en menos de 10 minutos no crea otra fila.
 */
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_battery() --> Text
 * Descripción: Procesa aeriq battery y devuelve el resultado correspondiente.
 */
function aeriq_battery(array $a,array $d): array {
    $device=aeriq_int($d['deviceId']??null);$percent=aeriq_int($d['percent']??null,0,100);
    $captured=aeriq_capture_time($d['capturedAt']??'');
    $orb=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_BATTERY_1,[$device,aeriq_org($a),(int)$a['usuario_id']]);
    if (!$orb || !(int)$orb['activo']) throw new AeriqError('AERIQ no asignado o inactivo.','forbidden');
    if ((int)$orb['simulado']) throw new AeriqError('Un AERIQ simulado no tiene batería que registrar.');
    if (strtoupper((string)($d['address']??''))!==$orb['direccion_ble']) throw new AeriqError('El anuncio no corresponde al AERIQ asignado.','forbidden');
    $last=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_BATTERY_2,[$device]);
    if ($last && (int)$last['porcentaje']===$percent && strtotime($captured.' UTC')-strtotime($last['capturada_en'].' UTC')<600) {
        return ['ok'=>true,'stored'=>false,'percent'=>$percent];
    }
    AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_BATTERY_3,[$device,$percent,$captured,(int)$a['usuario_id']]);
    return ['ok'=>true,'stored'=>true,'percent'=>$percent];
}

/**
 * Diseño lógico: a: Text --> aeriq_worker_history() --> Text
 * Descripción: Procesa aeriq worker history y devuelve el resultado correspondiente.
 */
function aeriq_worker_history(array $a): array {
    return AeriqRepository::workerHistory(aeriq_org($a),(int)$a['usuario_id']);
}

/**
 * Diseño lógico: a: Text --> aeriq_dashboard() --> Text
 * Descripción: Procesa aeriq dashboard y devuelve el resultado correspondiente.
 */
function aeriq_dashboard(array $a): array {
    $org=aeriq_org($a);$scope=aeriq_campaign($a);
    return AeriqRepository::dashboard($org,$scope);
}
/**
 * Diseño lógico: accion: AccionPanel, filtros: FiltrosPanel --> aeriq_consultar_panel() --> resultado: ResultadoPanel
 * Descripción: resuelve las consultas de solo lectura del panel mediante las reglas de dominio.
 */
/**
 * Diseño lógico: access: Text, action: Text, filters: Text --> aeriq_consultar_panel() --> Text
 * Descripción: Procesa aeriq consultar panel y devuelve el resultado correspondiente.
 */
function aeriq_consultar_panel(array $access,string $action,array $filters=[]): array {
    return match ($action) {
        'resumen' => aeriq_dashboard($access),
        'lecturas' => !empty($filters['export'])
            ? ['readings'=>aeriq_readings($access,$filters)]
            : ['readings'=>aeriq_readings($access,$filters),'discarded'=>aeriq_discarded($access,$filters)],
        'solicitudes' => aeriq_requests(),
        default => throw new AeriqError('Consulta de panel no disponible.','not_found'),
    };
}

/**
 * Diseño lógico: a: Text, filters: Text --> aeriq_readings() --> Text
 * Descripción: Procesa aeriq readings y devuelve el resultado correspondiente.
 */
function aeriq_readings(array $a,array $filters): array {
    [$where,$params]=aeriq_readings_filter($a,$filters);
    $rows=AeriqRepository::reportRows($where,$params);
    if (count($rows)>10000) throw new AeriqError('Demasiados registros. Reduce el intervalo o selecciona campaña/dispositivo; no se exportarán datos incompletos.','too_large');
    return $rows;
}
/**
 * Diseño lógico: a: Text, filters: Text --> aeriq_discarded() --> Text
 * Descripción: Procesa aeriq discarded y devuelve el resultado correspondiente.
 */
function aeriq_discarded(array $a,array $filters): array {
    [$where,$params]=aeriq_readings_filter($a,$filters);
    return AeriqRepository::reportRows($where,$params,true);
}
/**
 * Diseño lógico: --> aeriq_health() --> disponible: B
 * Descripción: comprueba que la conexión al esquema AERIQ responde.
 */
/**
 * Diseño lógico: aeriq_health() --> B
 * Descripción: Procesa aeriq health y devuelve el resultado correspondiente.
 */
function aeriq_health(): bool {
    AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_HEALTH_1);
    return true;
}
/**
 * Diseño lógico: --> aeriq_magnitudes() --> magnitudes: [Magnitud]
 * Descripción: devuelve el catálogo de magnitudes en orden estable.
 */
/**
 * Diseño lógico: aeriq_magnitudes() --> Text
 * Descripción: Procesa aeriq magnitudes y devuelve el resultado correspondiente.
 */
function aeriq_magnitudes(): array {
    return AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_MAGNITUDES_1);
}
/**
 * Diseño lógico: acceso: Acceso --> aeriq_devices() --> dispositivos: [Dispositivo]
 * Descripción: lista los dispositivos de la organización autorizada.
 */
/**
 * Diseño lógico: access: Text --> aeriq_devices() --> Text
 * Descripción: Procesa aeriq devices y devuelve el resultado correspondiente.
 */
function aeriq_devices(array $access): array {
    return AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_DEVICES_1,[aeriq_org($access)]);
}
/**
 * Diseño lógico: a: Text, filters: Text --> aeriq_readings_filter() --> Text
 * Descripción: Procesa aeriq readings filter y devuelve el resultado correspondiente.
 */
function aeriq_readings_filter(array $a,array $filters): array {
    $type=$filters['type']??14;
    if ($type!=='all') {
        $type=aeriq_int($type);
        if (!in_array($type,[12,14],true)) throw new AeriqError('Magnitud no admitida.');
    }
    if (!empty($filters['from']) && !empty($filters['to']) && $filters['from']>$filters['to']) throw new AeriqError('El intervalo de fechas está invertido.');
    $criteria=['organizationId'=>aeriq_org($a),'campaignScope'=>aeriq_campaign($a),'type'=>$type,
        'campaign'=>!empty($filters['campaign'])?aeriq_int($filters['campaign']):null,
        'device'=>!empty($filters['device'])?aeriq_int($filters['device']):null,
        'devices'=>[],'zone'=>!empty($filters['zone'])?aeriq_text($filters['zone']):null,'from'=>null,'to'=>null];
    if (!empty($filters['devices'])) {
        $criteria['devices']=array_values(array_unique(array_map(fn($v)=>aeriq_int($v),explode(',',(string)$filters['devices']))));
        if (count($criteria['devices'])>100) throw new AeriqError('Selecciona como máximo 100 dispositivos.');
    }
    foreach (['from','to'] as $key) {
        if (!empty($filters[$key])) {
            $date=aeriq_text($filters[$key],10);$v=DateTimeImmutable::createFromFormat('!Y-m-d',$date);
            if (!$v || $v->format('Y-m-d')!==$date) throw new AeriqError('Filtro de fecha no válido.');
            $criteria[$key]=$date;
        }
    }
    return AeriqRepository::readingFilters($criteria);
}
/**
 * Diseño lógico: acceso?: Acceso, ambito: AmbitoMapa, procedencia: Procedencia, horas: N --> aeriq_consultar_mapa() --> resultado: ResultadoMapa
 * Descripción: consulta el mapa público o el mapa autorizado de una organización.
 */
/**
 * Diseño lógico: access: Text, scope: Text, source: Text, hours: Z --> aeriq_consultar_mapa() --> Text
 * Descripción: Procesa aeriq consultar mapa y devuelve el resultado correspondiente.
 */
function aeriq_consultar_mapa(?array $access,string $scope,string $source,int $hours): array {
    if (!in_array($scope,['publico','organizacion','propio'],true)) throw new AeriqError('Ámbito del mapa no válido.');
    if (!in_array($source,['real','simulated','simulada'],true)) throw new AeriqError('Procedencia del mapa no válida.');
    if (!in_array($hours,[0,24,168],true)) throw new AeriqError('Periodo del mapa no válido.');
    $query=['mode'=>$source==='simulada'?'simulated':$source,'hours'=>$hours];
    if ($scope==='publico') return aeriq_map_data(null,$query);
    if ($access===null) throw new AeriqError('Inicia sesión para consultar este mapa.','unauthenticated');
    return aeriq_map_data($access,$query);
}
/**
 * Diseño lógico: valor: Z --> aeriq_map_hours() --> horas: N
 * Descripción: valida y convierte los periodos admitidos por el mapa público.
 */
/**
 * Diseño lógico: value: Text --> aeriq_map_hours() --> Z
 * Descripción: Procesa aeriq map hours y devuelve el resultado correspondiente.
 */
function aeriq_map_hours(mixed $value): int {
    $hours=aeriq_int($value,0,168);
    if (!in_array($hours,[0,24,168],true)) throw new AeriqError('Periodo del mapa no válido.');
    return $hours;
}
/**
 * Diseño lógico: query: Text --> aeriq_public_map() --> Text
 * Descripción: Procesa aeriq public map y devuelve el resultado correspondiente.
 */
function aeriq_public_map(array $query): array {
    return aeriq_consultar_mapa(null,'publico',(string)($query['mode']??'real'),aeriq_map_hours($query['hours']??24));
}
/**
 * Diseño lógico: access: Text, query: Text --> aeriq_map_data() --> Text
 * Descripción: Procesa aeriq map data y devuelve el resultado correspondiente.
 */
function aeriq_map_data(?array $access,array $query): array {
    $mode=$query['mode']??'real';if (!in_array($mode,['real','simulated'],true)) throw new AeriqError('Selecciona datos reales o simulados.');
    $hours=aeriq_int($query['hours']??24,0,168);if(!in_array($hours,[0,24,168],true))throw new AeriqError('Periodo no válido.');
    $authorization=[];
    if ($access) {
        $authorization=['organizationId'=>aeriq_org($access),'campaignId'=>aeriq_campaign($access)];
        if (!aeriq_can($access,'informes.ver')) $authorization['workerId']=(int)$access['usuario_id'];
    }
    // Solo el mapa de la organización identifica el AERIQ (para dibujar su ruta); el público no lo expone.
    $rows=AeriqRepository::mapMeasurements($mode,$hours,$authorization,(bool)$access,AERIQ_MAPA_MAX+1);
    $truncated=count($rows)>AERIQ_MAPA_MAX;$rows=array_slice($rows,0,AERIQ_MAPA_MAX);
    foreach ($rows as &$r) {foreach (['valor','latitud','longitud','umbral_medio','umbral_alto'] as $k)$r[$k]=(float)$r[$k];$r['simulado']=(bool)$r['simulado'];$r['radio_m']=(int)$r['radio_m'];if($access)$r['dispositivo_id']=(int)$r['dispositivo_id'];}
    unset($r);return ['measurements'=>$rows,'truncated'=>$truncated,'mode'=>$mode,'hours'=>$hours];
}
/**
 * Diseño lógico: d: Text, key: Text, max: Z, message: Text, optional: B --> aeriq_field_text() --> Text
 * Descripción: Procesa aeriq field text y devuelve el resultado correspondiente.
 */
function aeriq_field_text(array $d,string $key,int $max,string $message,bool $optional=false): string {
    $v=$d[$key]??'';
    if (!is_string($v)) throw new AeriqError($message,'invalid_input',$key);
    $v=trim($v);$length=function_exists('mb_strlen')?mb_strlen($v,'UTF-8'):strlen($v);
    if ((!$optional && $v==='') || $length>$max) throw new AeriqError($message,'invalid_input',$key);
    return $v;
}
/**
 * Diseño lógico: d: Text --> aeriq_request() --> Text
 * Descripción: Procesa aeriq request y devuelve el resultado correspondiente.
 */
function aeriq_request(array $d): array {
    // Campo trampa: una persona no lo ve; si llega relleno es un bot. Se responde igual para no darle pistas.
    if (is_string($d['website']??null) && trim($d['website'])!=='') return ['ok'=>true,'id'=>0,'message'=>'Solicitud registrada.'];
    $service=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_1,[is_string($d['interest']??null)?$d['interest']:'']);
    if (!$service) throw new AeriqError('Elige qué te interesa.','invalid_input','interest');
    $org=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_2,[is_string($d['orgType']??null)?$d['orgType']:'']);
    if (!$org) throw new AeriqError('Elige el tipo de organización.','invalid_input','orgType');
    $entity=aeriq_field_text($d,'entity',180,'Escribe el nombre de la organización.');
    $contact=aeriq_field_text($d,'contact',160,'Escribe el nombre de la persona de contacto.');
    $email=strtolower(aeriq_field_text($d,'email',254,'Escribe un correo electrónico válido.'));
    if (!filter_var($email,FILTER_VALIDATE_EMAIL)) throw new AeriqError('Escribe un correo electrónico válido.','invalid_input','email');
    $phone=aeriq_field_text($d,'phone',9,'El teléfono debe tener 9 cifras y empezar por 6, 7, 8 o 9.',true);
    if ($phone!=='' && !preg_match('/^[6789][0-9]{8}$/',$phone)) throw new AeriqError('El teléfono debe tener 9 cifras y empezar por 6, 7, 8 o 9.','invalid_input','phone');
    $municipality=aeriq_field_text($d,'municipality',120,'Escribe el municipio.');
    $province=is_string($d['province']??null)?$d['province']:'';
    if (!in_array($province,AERIQ_PROVINCIAS,true)) throw new AeriqError('Elige una provincia.','invalid_input','province');
    $zone=aeriq_field_text($d,'zone',200,'Describe la zona o las instalaciones que quieres medir.');
    $codes=$d['objectives']??null;
    if (!is_array($codes) || count($codes)>20) throw new AeriqError('Marca al menos un objetivo de la medición.','invalid_input','objectives');
    $codes=array_values(array_unique(array_filter($codes,'is_string')));
    $objectives=AeriqRepository::objectivesByCode($codes);
    if (!$codes || count($objectives)!==count($codes)) throw new AeriqError('Marca al menos un objetivo de la medición.','invalid_input','objectives');
    $needs=aeriq_field_text($d,'needs',6000,'Cuéntanos qué necesitas (máximo 6000 caracteres).');
    if (($d['consent']??false)!==true) throw new AeriqError('Debes autorizar el tratamiento de los datos para gestionar la solicitud.','invalid_input','consent');
    // Superficie, duración e inicio solo se piden en los servicios que se presupuestan por zona (estudios).
    $surface=null;$duration='';$start=null;
    if ((int)$service['mide_superficie']) {
        $raw=$d['surface']??null;
        if ($raw!==null && $raw!=='') {
            if (!is_numeric($raw) || !is_finite((float)$raw) || (float)$raw<=0 || (float)$raw>1e12) throw new AeriqError('La superficie debe ser un número mayor que 0. Si no la sabes, déjala vacía.','invalid_input','surface');
            $surface=(float)$raw;
        }
        $duration=is_string($d['duration']??null)?$d['duration']:'';
        if (!in_array($duration,AERIQ_DURACIONES,true)) throw new AeriqError('Elige la duración del estudio.','invalid_input','duration');
        $month=$d['startMonth']??null;
        if ($month!==null && $month!=='') {
            $m=is_string($month)?DateTimeImmutable::createFromFormat('!Y-m',$month,new DateTimeZone('UTC')):false;
            if (!$m || $m->format('Y-m')!==$month || $month<gmdate('Y-m')) throw new AeriqError('Elige un mes de inicio a partir del actual.','invalid_input','startMonth');
            $start=$m->format('Y-m-d');
        }
    }
    AeriqRepository::begin();
    try {
        // Toda solicitud nace en el primer estado del catálogo y queda registrada en el historial.
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_7,[(int)$service['id'],(int)$org['id'],$entity,$contact,$email,$phone,$municipality,$province,$zone,$surface,$duration,$start,$needs]);$id=(int)AeriqRepository::lastInsertId();
        foreach ($objectives as $o) AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_8,[$id,(int)$o['id']]);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_9,[$id]);
        AeriqRepository::commit();
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
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
/**
 * Diseño lógico: solicitud: DatosSolicitud --> aeriq_enviar_solicitud() --> confirmacion: ConfirmacionSolicitud
 * Descripción: valida y registra una solicitud pública con sus objetivos e historial.
 */
/**
 * Diseño lógico: data: Text --> aeriq_enviar_solicitud() --> Text
 * Descripción: Procesa aeriq enviar solicitud y devuelve el resultado correspondiente.
 */
function aeriq_enviar_solicitud(array $data): array { return aeriq_request($data); }

/**
 * Diseño lógico: aeriq_request_catalogs() --> Text
 * Descripción: Procesa aeriq request catalogs y devuelve el resultado correspondiente.
 */
function aeriq_request_catalogs(): array {
    $services=AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_REQUEST_CATALOGS_1);
    foreach ($services as &$r) $r['mide_superficie']=(bool)(int)$r['mide_superficie'];
    unset($r);
    return ['services'=>$services,
        'orgTypes'=>AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_REQUEST_CATALOGS_2),
        'objectives'=>AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_REQUEST_CATALOGS_3),
        'provinces'=>AERIQ_PROVINCIAS,'durations'=>AERIQ_DURACIONES];
}
/**
 * Diseño lógico: --> aeriq_consultar_servicios() --> servicios: [Servicio]
 * Descripción: presenta los catálogos activos usados por el formulario público.
 */
/**
 * Diseño lógico: --> aeriq_consultar_servicios() --> catalogos: CatalogosSolicitud
 * Descripción: expone los catálogos activos para el formulario comercial.
 */
/**
 * Diseño lógico: aeriq_consultar_servicios() --> Text
 * Descripción: Procesa aeriq consultar servicios y devuelve el resultado correspondiente.
 */
function aeriq_consultar_servicios(): array { return aeriq_request_catalogs(); }
/**
 * Diseño lógico: aeriq_requests() --> Text
 * Descripción: Procesa aeriq requests y devuelve el resultado correspondiente.
 */
function aeriq_requests(): array {
    $rows=AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_REQUESTS_1);
    $history=[];$objectives=[];
    if ($rows) {
        $ids=array_map(fn($r)=>(int)$r['id'],$rows);
        $wasFinal=[];
        foreach (AeriqRepository::requestHistory($ids) as $h) {
            $sid=(int)$h['solicitud_id'];
            // Una entrada que sale de un estado final es una reapertura (T38): se marca para enseñarla así.
            $history[$sid][]=['estado'=>$h['estado'],'estado_nombre'=>$h['estado_nombre'],'cambiado_en'=>$h['cambiado_en'],'usuario'=>$h['usuario'],
                'reapertura'=>!empty($wasFinal[$sid]) && !(int)$h['final']];
            $wasFinal[$sid]=(bool)(int)$h['final'];
        }
        foreach (AeriqRepository::requestObjectives($ids) as $o) $objectives[(int)$o['solicitud_id']][]=$o['nombre'];
    }
    foreach ($rows as &$r) { unset($r['servicio_id'],$r['tipo_organizacion_id'],$r['estado_id']); $r['objetivos']=$objectives[(int)$r['id']]??[]; $r['historial']=$history[(int)$r['id']]??[]; }
    unset($r);
    return ['requests'=>$rows,'states'=>AeriqRepository::rows(AeriqQueryCatalog::Q_AERIQ_REQUESTS_2)];
}
/**
 * Cambia el estado de una solicitud (T38): solo hacia delante en el orden del catálogo. Volver atrás no se
 * permite; una solicitud cerrada se recupera con aeriq_request_reopen(). Cada cambio queda en el historial con
 * quién y cuándo, y se avisa por correo al solicitante (T37). Un fallo del correo no deshace el cambio.
 */
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_request_status() --> Text
 * Descripción: Procesa aeriq request status y devuelve el resultado correspondiente.
 */
function aeriq_request_status(array $a,array $d): array {
    $state=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_STATUS_1,[is_string($d['status']??null)?$d['status']:'']);
    if (!$state) throw new AeriqError('Estado no válido.');
    $id=aeriq_int($d['id']??null);
    AeriqRepository::begin();
    try {
        $current=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_STATUS_2,[$id]);
        if (!$current) throw new AeriqError('Solicitud no encontrada.','not_found');
        if ((int)$current['estado_id']===(int)$state['id']) {AeriqRepository::commit();return ['ok'=>true,'changed'=>false,'message'=>'La solicitud ya estaba en «'.$state['nombre'].'».'];}
        if ((int)$current['final']) throw new AeriqError('La solicitud está cerrada. Usa «Reabrir» para volver a trabajar en ella.','conflict');
        if ((int)$state['orden']<(int)$current['orden']) throw new AeriqError('Una solicitud solo puede avanzar de estado: no puede volver de «'.$current['nombre'].'» a «'.$state['nombre'].'».','conflict');
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_STATUS_3,[(int)$state['id'],$id]);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_STATUS_4,[$id,(int)$state['id'],(int)$a['usuario_id']]);
        AeriqRepository::commit();
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
    return aeriq_request_notify($id,(int)$state['final']?'cerrada':'avance');
}
/**
 * Diseño lógico: a: Text, d: Text --> aeriq_request_reopen() --> Text
 * Descripción: Procesa aeriq request reopen y devuelve el resultado correspondiente.
 */
function aeriq_request_reopen(array $a,array $d): array {
    $id=aeriq_int($d['id']??null);
    $target=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_REOPEN_1)
        ?:AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_REOPEN_2);
    if (!$target) throw new AeriqError('No hay un estado al que reabrir.','internal');
    AeriqRepository::begin();
    try {
        $current=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_REOPEN_3,[$id]);
        if (!$current) throw new AeriqError('Solicitud no encontrada.','not_found');
        if (!(int)$current['final']) throw new AeriqError('Solo se puede reabrir una solicitud cerrada.','conflict');
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_REOPEN_4,[(int)$target['id'],$id]);
        AeriqRepository::execute(AeriqQueryCatalog::Q_AERIQ_REQUEST_REOPEN_5,[$id,(int)$target['id'],(int)$a['usuario_id']]);
        AeriqRepository::commit();
    } catch (Throwable $e) {AeriqRepository::rollback();throw $e;}
    return aeriq_request_notify($id,'reabierta');
}
/**
 * Diseño lógico: id: Z, kind: Text --> aeriq_request_notify() --> Text
 * Descripción: Procesa aeriq request notify y devuelve el resultado correspondiente.
 */
function aeriq_request_notify(int $id,string $kind): array {
    $r=AeriqRepository::one(AeriqQueryCatalog::Q_AERIQ_REQUEST_NOTIFY_1,[$id]);
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
