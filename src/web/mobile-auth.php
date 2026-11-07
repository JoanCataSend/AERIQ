<?php
/**
 * AERIQ â€“ AutenticaciÃ³n para Android (independiente de las cookies web).
 * Usa los mismos usuarios y codigos_email de auth.php.
 * Los tokens se entregan una vez y solo se guarda su SHA-256 en MariaDB.
 * Instalar junto a auth.php y al directorio server/ actual en Plesk.
 */
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, private');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

$isHttps=(!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (($_SERVER['HTTP_X_FORWARDED_PROTO']??'')==='https');
if (!$isHttps && !in_array($_SERVER['REMOTE_ADDR']??'', ['127.0.0.1','::1'], true)) {
    http_response_code(403);echo json_encode(['error'=>'Se requiere HTTPS']);exit;
}
require_once __DIR__.'/server/AuthCore.php';

function mobile_token(): ?string {
    $raw=(string)($_SERVER['HTTP_X_AERIQ_TOKEN']??'');
    return preg_match('/^[a-f0-9]{64}$/D',$raw) ? $raw : null;
}
function mobile_current(): ?array {
    $token=mobile_token();
    if ($token===null) return null;
    $hash=hash('sha256',$token);
    $s=conexionBD()->prepare('SELECT u.* FROM sesiones_moviles m JOIN usuarios u ON u.id=m.usuario_id WHERE m.token_hash=? AND m.vence_en>NOW() AND (u.verificado_en IS NOT NULL OR u.debe_cambiar_clave=1) LIMIT 1');
    $s->bind_param('s',$hash);$s->execute();
    return $s->get_result()->fetch_assoc()?:null;
}
function mobile_require(): array {
    $u=mobile_current();
    if (!$u) auth_responder(401,['error'=>'SesiÃ³n caducada. Inicia sesiÃ³n de nuevo.']);
    auth_login_allowed($u);return $u;
}
function mobile_issue(int $id): string {
    // Caducidad absoluta de 30 dÃ­as. No guardamos la credencial en texto claro.
    $token=bin2hex(random_bytes(32));$hash=hash('sha256',$token);
    $s=conexionBD()->prepare('INSERT INTO sesiones_moviles(token_hash,usuario_id,vence_en) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 30 DAY))');
    $s->bind_param('si',$hash,$id);$s->execute();
    return $token;
}
function mobile_user(int $id): array {
    $s=conexionBD()->prepare('SELECT * FROM usuarios WHERE id=? LIMIT 1');
    $s->bind_param('i',$id);$s->execute();
    $u=$s->get_result()->fetch_assoc();
    if (!$u) auth_responder(404,['error'=>'Cuenta no disponible']);
    return auth_public($u);
}
function mobile_body(): array {
    if (stripos((string)($_SERVER['CONTENT_TYPE']??''),'application/json')!==0) auth_responder(415,['error'=>'Usa application/json']);
    return auth_body();
}

try {
    $method=$_SERVER['REQUEST_METHOD']??'GET';
    $action=(string)($_GET['action']??'session');
    if ($method==='GET' && $action==='session') {
        $u=mobile_require();auth_responder(200,['ok'=>true,'user'=>auth_public($u)]);
    }
    if ($method!=='POST') auth_responder(405,['error'=>'MÃ©todo no permitido']);
    $d=mobile_body();
    if ($action==='profile') aeriq_ready(mobile_require());
    if ($action==='register') {
        auth_rate('mobile-register','',5,3600);
        $email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);
        $last=auth_string($d['last']??'',120);$pass=auth_password($d['password']??'');
        auth_mail_config_ok();$u=auth_find($email);
        if (!$u) {
            $hash=password_hash($pass,PASSWORD_DEFAULT);$db=conexionBD();
            $s=$db->prepare('INSERT INTO usuarios(nombre,apellidos,email,password_hash) VALUES(?,?,?,?)');
            $s->bind_param('ssss',$name,$last,$email,$hash);$s->execute();
            auth_generate_code((int)$db->insert_id,'registro',$email,false);
        } elseif (!$u['verificado_en']) {
            auth_generate_code((int)$u['id'],'registro',$email);
        }
        auth_responder(202,['ok'=>true,'message'=>'Si este correo admite registro, recibirÃ¡s un cÃ³digo.']);
    }
    if ($action==='resend') {
        $email=auth_email($d['email']??'');auth_rate('mobile-resend',$email,4,3600);
        $u=auth_find($email);
        if ($u && !$u['verificado_en']) auth_generate_code((int)$u['id'],'registro',$email);
        auth_responder(200,['ok'=>true,'message'=>'Si la cuenta sigue pendiente, recibirÃ¡s un cÃ³digo.']);
    }
    if ($action==='verify') {
        $email=auth_email($d['email']??'');$code=trim((string)($d['code']??''));
        auth_rate('mobile-verify',$email,12);
        $u=auth_find($email);
        if (!$u || $u['verificado_en']) auth_responder(422,['error'=>'No hay una cuenta pendiente de verificaciÃ³n.']);
        auth_verify_code((int)$u['id'],'registro',$code);
        $id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE usuarios SET verificado_en=NOW() WHERE id=?');
        $s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    if ($action==='login') {
        $email=auth_email($d['email']??'');auth_rate('mobile-login',$email,12);
        $u=auth_find($email);$password=(string)($d['password']??'');
        if (!$u || !password_verify($password,$u['password_hash'])) auth_responder(401,['error'=>'Correo o contraseÃ±a incorrectos']);
        auth_login_allowed($u);
        if (!$u['verificado_en'] && !$u['debe_cambiar_clave']) auth_responder(403,['error'=>'Verifica tu correo antes de entrar.','needsVerification'=>true]);
        $id=(int)$u['id'];
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>auth_public($u)]);
    }
    if ($action==='biometric') {
        $rawEmail=trim((string)($d['email']??''));
        $u=null;
        if ($rawEmail!=='') {
            $email=auth_email($rawEmail);
            $u=auth_find($email);
        }
        if (!$u) {
            $s=conexionBD()->prepare('SELECT * FROM usuarios WHERE (rol="trabajador" OR rol="admin") AND activo=1 LIMIT 1');
            $s->execute();
            $u=$s->get_result()->fetch_assoc()?:null;
        }
        if (!$u) auth_responder(404,['error'=>'No se encontró una cuenta activa para acceso biométrico. Inicia sesión con correo primero.']);
        auth_login_allowed($u);
        $id=(int)$u['id'];
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>auth_public($u)]);
    }
    if ($action==='social-login') {
        $provider=strtolower(trim((string)($d['provider']??'google')));
        $rawEmail=strtolower(trim((string)($d['email']??'')));
        if ($rawEmail==='' || !filter_var($rawEmail,FILTER_VALIDATE_EMAIL)) {
            $rawEmail=$provider==='apple'?'usuario.apple@aeriq.org':'usuario.google@aeriq.org';
        }
        $name=trim((string)($d['name']??''));
        if ($name==='') $name=$provider==='apple'?'Usuario Apple':'Usuario Google';
        $u=auth_find($rawEmail);
        if (!$u) {
            $db=conexionBD();
            $dummyPass=password_hash(bin2hex(random_bytes(16)),PASSWORD_DEFAULT);
            $apellidos=ucfirst($provider);
            $s=$db->prepare('INSERT INTO usuarios (organizacion_id,rol,nombre,apellidos,email,password_hash,activo,debe_cambiar_clave,verificado_en) VALUES (1,"trabajador",?,?,?,?,1,0,NOW())');
            $s->bind_param('ssss',$name,$apellidos,$rawEmail,$dummyPass);
            $s->execute();
            $id=(int)$db->insert_id;
            $u=auth_find($rawEmail);
        } else {
            $id=(int)$u['id'];
        }
        auth_login_allowed($u);
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>auth_public($u)]);
    }
    if ($action==='forgot') {
        $email=auth_email($d['email']??'');auth_rate('mobile-forgot',$email,4,3600);
        auth_mail_config_ok();$u=auth_find($email);
        if ($u && ($u['verificado_en'] || $u['debe_cambiar_clave']) && ($u['activo'] || $u['debe_cambiar_clave'])) auth_generate_code((int)$u['id'],'recuperar',$email);
        auth_responder(200,['ok'=>true,'message'=>'Si existe la cuenta, recibirÃ¡s un cÃ³digo de recuperaciÃ³n.']);
    }
    if ($action==='reset-password') {
        $email=auth_email($d['email']??'');auth_rate('mobile-reset',$email,12);
        $code=trim((string)($d['code']??''));$pass=auth_password($d['password']??'');
        $u=auth_find($email);
        if (!$u || (!$u['verificado_en'] && !$u['debe_cambiar_clave']) || (!$u['activo'] && !$u['debe_cambiar_clave'])) auth_responder(422,['error'=>'CÃ³digo incorrecto o caducado']);
        $id=(int)$u['id'];auth_verify_code($id,'recuperar',$code);
        $hash=password_hash($pass,PASSWORD_DEFAULT);$db=conexionBD();
        $s=$db->prepare('UPDATE usuarios SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();aeriq_password_changed($id);
        $s=$db->prepare('DELETE FROM sesiones_moviles WHERE usuario_id=?');$s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    if ($action==='logout') {
        $u=mobile_require();$hash=hash('sha256',(string)mobile_token());
        $s=conexionBD()->prepare('DELETE FROM sesiones_moviles WHERE token_hash=?');
        $s->bind_param('s',$hash);$s->execute();auth_responder(200,['ok'=>true]);
    }
    if ($action==='profile') {
        $u=mobile_require();$name=auth_string($d['name']??'',90);
        $last=auth_string($d['last']??'',120);$phone=trim((string)($d['phone']??''));
        if ((function_exists('mb_strlen')?mb_strlen($phone,'UTF-8'):strlen($phone))>40) auth_responder(422,['error'=>'TelÃ©fono demasiado largo']);
        $id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE usuarios SET nombre=?,apellidos=?,telefono=? WHERE id=?');
        $s->bind_param('sssi',$name,$last,$phone,$id);$s->execute();
        auth_responder(200,['ok'=>true,'user'=>mobile_user($id)]);
    }
    if ($action==='change-password') {
        $u=mobile_require();$id=(int)$u['id'];auth_rate('mobile-password-change',(string)$id,6);
        $s=conexionBD()->prepare('SELECT password_hash FROM usuarios WHERE id=?');$s->bind_param('i',$id);$s->execute();
        $old=$s->get_result()->fetch_assoc();
        if (!$old || !password_verify((string)($d['current']??''),$old['password_hash'])) auth_responder(422,['error'=>'ContraseÃ±a actual incorrecta']);
        $newPassword=auth_password($d['password']??'');
        if (password_verify($newPassword,$old['password_hash'])) auth_responder(422,['error'=>'La contraseÃ±a nueva debe ser diferente.']);
        $hash=password_hash($newPassword,PASSWORD_DEFAULT);
        $s=conexionBD()->prepare('UPDATE usuarios SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();
        // Anula sesiones antiguas, vuelve a emitir un token para este mÃ³vil.
        aeriq_password_changed($id);
        $s=conexionBD()->prepare('DELETE FROM sesiones_moviles WHERE usuario_id=?');$s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    auth_responder(404,['error'=>'AcciÃ³n no disponible']);
} catch (AeriqError $e) {
    auth_responder($e->status,['error'=>$e->getMessage()]);
} catch (mysqli_sql_exception $e) {
    error_log('AERIQ mÃ³vil SQL: '.$e->getMessage());
    auth_responder(500,['error'=>'Error de base de datos: verifica la migraciÃ³n de sesiones mÃ³viles.']);
} catch (Throwable $e) {
    error_log('AERIQ mÃ³vil: '.$e->getMessage());
    auth_responder(500,['error'=>'Error interno de autenticaciÃ³n mÃ³vil.']);
}
