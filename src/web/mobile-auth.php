<?php
/**
 * AERIQ – Autenticación para Android (independiente de las cookies web).
 * Usa los mismos AeriqUsuario y AeriqCodigoEmail de auth.php.
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
    $s=conexionBD()->prepare('SELECT u.id,u.nombre,u.apellidos,u.email,u.telefono,u.verificado_en FROM AeriqSesionMovil m JOIN AeriqUsuario u ON u.id=m.usuario_id WHERE m.token_hash=? AND m.vence_en>NOW() AND u.verificado_en IS NOT NULL LIMIT 1');
    $s->bind_param('s',$hash);$s->execute();
    return $s->get_result()->fetch_assoc()?:null;
}
function mobile_require(): array {
    $u=mobile_current();
    if (!$u) auth_responder(401,['error'=>'Sesión caducada. Inicia sesión de nuevo.']);
    return $u;
}
function mobile_issue(int $id): string {
    // Caducidad absoluta de 30 días. No guardamos la credencial en texto claro.
    $token=bin2hex(random_bytes(32));$hash=hash('sha256',$token);
    $s=conexionBD()->prepare('INSERT INTO AeriqSesionMovil(token_hash,usuario_id,vence_en) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 30 DAY))');
    $s->bind_param('si',$hash,$id);$s->execute();
    return $token;
}
function mobile_user(int $id): array {
    $s=conexionBD()->prepare('SELECT id,nombre,apellidos,email,telefono,verificado_en FROM AeriqUsuario WHERE id=? LIMIT 1');
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
    if ($method!=='POST') auth_responder(405,['error'=>'Método no permitido']);
    $d=mobile_body();
    if ($action==='register') {
        auth_rate('mobile-register','',5,3600);
        $email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);
        $last=auth_string($d['last']??'',120);$pass=auth_password($d['password']??'');
        auth_mail_config_ok();$u=auth_find($email);
        if (!$u) {
            $hash=password_hash($pass,PASSWORD_DEFAULT);$db=conexionBD();
            $s=$db->prepare('INSERT INTO AeriqUsuario(nombre,apellidos,email,password_hash) VALUES(?,?,?,?)');
            $s->bind_param('ssss',$name,$last,$email,$hash);$s->execute();
            auth_generate_code((int)$db->insert_id,'registro',$email,false);
        } elseif (!$u['verificado_en']) {
            auth_generate_code((int)$u['id'],'registro',$email);
        }
        auth_responder(202,['ok'=>true,'message'=>'Si este correo admite registro, recibirás un código.']);
    }
    if ($action==='resend') {
        $email=auth_email($d['email']??'');auth_rate('mobile-resend',$email,4,3600);
        $u=auth_find($email);
        if ($u && !$u['verificado_en']) auth_generate_code((int)$u['id'],'registro',$email);
        auth_responder(200,['ok'=>true,'message'=>'Si la cuenta sigue pendiente, recibirás un código.']);
    }
    if ($action==='verify') {
        $email=auth_email($d['email']??'');$code=trim((string)($d['code']??''));
        auth_rate('mobile-verify',$email,12);
        $u=auth_find($email);
        if (!$u || $u['verificado_en']) auth_responder(422,['error'=>'No hay una cuenta pendiente de verificación.']);
        auth_verify_code((int)$u['id'],'registro',$code);
        $id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE AeriqUsuario SET verificado_en=NOW() WHERE id=?');
        $s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    if ($action==='login') {
        $email=auth_email($d['email']??'');auth_rate('mobile-login',$email,12);
        $u=auth_find($email);$password=(string)($d['password']??'');
        if (!$u || !password_verify($password,$u['password_hash'])) auth_responder(401,['error'=>'Correo o contraseña incorrectos']);
        if (!$u['verificado_en']) auth_responder(403,['error'=>'Verifica tu correo antes de entrar.','needsVerification'=>true]);
        $id=(int)$u['id'];
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>auth_public($u)]);
    }
    if ($action==='forgot') {
        $email=auth_email($d['email']??'');auth_rate('mobile-forgot',$email,4,3600);
        auth_mail_config_ok();$u=auth_find($email);
        if ($u && $u['verificado_en']) auth_generate_code((int)$u['id'],'recuperar',$email);
        auth_responder(200,['ok'=>true,'message'=>'Si existe la cuenta, recibirás un código de recuperación.']);
    }
    if ($action==='reset-password') {
        $email=auth_email($d['email']??'');auth_rate('mobile-reset',$email,12);
        $code=trim((string)($d['code']??''));$pass=auth_password($d['password']??'');
        $u=auth_find($email);
        if (!$u || !$u['verificado_en']) auth_responder(422,['error'=>'Código incorrecto o caducado']);
        $id=(int)$u['id'];auth_verify_code($id,'recuperar',$code);
        $hash=password_hash($pass,PASSWORD_DEFAULT);$db=conexionBD();
        $s=$db->prepare('UPDATE AeriqUsuario SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();
        $s=$db->prepare('DELETE FROM AeriqSesionMovil WHERE usuario_id=?');$s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    if ($action==='logout') {
        $u=mobile_require();$hash=hash('sha256',(string)mobile_token());
        $s=conexionBD()->prepare('DELETE FROM AeriqSesionMovil WHERE token_hash=?');
        $s->bind_param('s',$hash);$s->execute();auth_responder(200,['ok'=>true]);
    }
    if ($action==='profile') {
        $u=mobile_require();$name=auth_string($d['name']??'',90);
        $last=auth_string($d['last']??'',120);$phone=trim((string)($d['phone']??''));
        if ((function_exists('mb_strlen')?mb_strlen($phone,'UTF-8'):strlen($phone))>40) auth_responder(422,['error'=>'Teléfono demasiado largo']);
        $id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE AeriqUsuario SET nombre=?,apellidos=?,telefono=? WHERE id=?');
        $s->bind_param('sssi',$name,$last,$phone,$id);$s->execute();
        auth_responder(200,['ok'=>true,'user'=>mobile_user($id)]);
    }
    if ($action==='change-password') {
        $u=mobile_require();$id=(int)$u['id'];auth_rate('mobile-password-change',(string)$id,6);
        $s=conexionBD()->prepare('SELECT password_hash FROM AeriqUsuario WHERE id=?');$s->bind_param('i',$id);$s->execute();
        $old=$s->get_result()->fetch_assoc();
        if (!$old || !password_verify((string)($d['current']??''),$old['password_hash'])) auth_responder(422,['error'=>'Contraseña actual incorrecta']);
        $hash=password_hash(auth_password($d['password']??''),PASSWORD_DEFAULT);
        $s=conexionBD()->prepare('UPDATE AeriqUsuario SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();
        // Anula sesiones antiguas, vuelve a emitir un token para este móvil.
        $s=conexionBD()->prepare('DELETE FROM AeriqSesionMovil WHERE usuario_id=?');$s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    auth_responder(404,['error'=>'Acción no disponible']);
} catch (mysqli_sql_exception $e) {
    error_log('AERIQ móvil SQL: '.$e->getMessage());
    auth_responder(500,['error'=>'Error de base de datos: verifica la migración de sesiones móviles.']);
} catch (Throwable $e) {
    error_log('AERIQ móvil: '.$e->getMessage());
    auth_responder(500,['error'=>'Error interno de autenticación móvil.']);
}
