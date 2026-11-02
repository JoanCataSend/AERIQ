<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
if ((!isset($_SERVER['HTTPS']) || $_SERVER['HTTPS'] !== 'on') && (($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') !== 'https') && ($_SERVER['REMOTE_ADDR']??'')!=='127.0.0.1') {
    http_response_code(403);echo json_encode(['error'=>'Se requiere HTTPS']);exit;
}
ini_set('session.use_strict_mode','1');
ini_set('session.use_only_cookies','1');
session_name('AERIQSESSID');
session_set_cookie_params(['lifetime'=>0,'path'=>'/','secure'=>true,'httponly'=>true,'samesite'=>'Lax']);
session_start();
$_SESSION['csrf'] ??= bin2hex(random_bytes(24));
require_once __DIR__.'/server/AuthCore.php';
try {
    $action=(string)($_GET['action']??'session');
    $method=$_SERVER['REQUEST_METHOD']??'GET';
    if ($method==='GET' && $action==='session') auth_responder(200,['ok'=>true,'csrf'=>$_SESSION['csrf'],'user'=>($u=auth_current())?auth_public($u):null]);
    if ($method!=='POST') auth_responder(405,['error'=>'Método no permitido']);
    if (!hash_equals($_SESSION['csrf'],(string)($_SERVER['HTTP_X_CSRF_TOKEN']??''))) auth_responder(403,['error'=>'Sesión caducada. Recarga la página.']);
    $d=auth_body();
    if ($action==='register') {
        auth_rate('register','',5,3600);
        $email=auth_email($d['email']??'');$name=auth_string($d['name']??'',90);$last=auth_string($d['last']??'',120);$pass=auth_password($d['password']??'');
        auth_mail_config_ok();
        $u=auth_find($email);
        if (!$u) {
            $hash=password_hash($pass,PASSWORD_DEFAULT);$db=conexionBD();
            $s=$db->prepare('INSERT INTO AeriqUsuario(nombre,apellidos,email,password_hash) VALUES (?,?,?,?)');
            $s->bind_param('ssss',$name,$last,$email,$hash);$s->execute();$id=(int)$db->insert_id;
            auth_generate_code($id,'registro',$email,false);
        } elseif (!$u['verificado_en']) {
            // No cambia la contraseña ni los datos al repetir el registro.
            auth_generate_code((int)$u['id'],'registro',$email);
        }
        auth_responder(202,['ok'=>true,'message'=>'Si el correo admite registro, recibirás un código de verificación.']);
    }
    if ($action==='resend') {
        $email=auth_email($d['email']??'');auth_rate('resend',$email,4,3600);
        $u=auth_find($email);
        if ($u && !$u['verificado_en']) auth_generate_code((int)$u['id'],'registro',$email);
        auth_responder(200,['ok'=>true,'message'=>'Si la cuenta está pendiente, recibirás otro código.']);
    }
    if ($action==='verify') {
        $email=auth_email($d['email']??'');$code=trim((string)($d['code']??''));auth_rate('verify',$email,12);
        $u=auth_find($email);
        if (!$u || $u['verificado_en']) auth_responder(422,['error'=>'No hay una verificación pendiente para este correo']);
        auth_verify_code((int)$u['id'],'registro',$code);
        $s=conexionBD()->prepare('UPDATE AeriqUsuario SET verificado_en=NOW() WHERE id=?');$id=(int)$u['id'];$s->bind_param('i',$id);$s->execute();
        auth_login_session($id);auth_responder(200,['ok'=>true,'user'=>auth_public($u)]);
    }
    if ($action==='login') {
        $email=auth_email($d['email']??'');auth_rate('login',$email,12);
        $password=(string)($d['password']??'');$u=auth_find($email);
        if (!$u || !password_verify($password,$u['password_hash'])) auth_responder(401,['error'=>'Correo o contraseña incorrectos']);
        if (!$u['verificado_en']) auth_responder(403,['error'=>'Confirma tu correo antes de iniciar sesión','needsVerification'=>true]);
        auth_login_session((int)$u['id']);auth_responder(200,['ok'=>true,'user'=>auth_public($u)]);
    }
    if ($action==='logout') {
        $_SESSION=[];session_regenerate_id(true);auth_responder(200,['ok'=>true]);
    }
    if ($action==='profile') {
        $u=auth_require_user();$name=auth_string($d['name']??'',90);$last=auth_string($d['last']??'',120);
        $phone=trim((string)($d['phone']??''));if (mb_strlen($phone)>40) auth_responder(422,['error'=>'Teléfono demasiado largo']);
        $id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE AeriqUsuario SET nombre=?,apellidos=?,telefono=? WHERE id=?');$s->bind_param('sssi',$name,$last,$phone,$id);$s->execute();
        auth_responder(200,['ok'=>true,'user'=>auth_public(auth_current())]);
    }
    if ($action==='change-password') {
        $u=auth_require_user();auth_rate('password-change',(string)$u['id'],6);
        $db=conexionBD();$s=$db->prepare('SELECT password_hash FROM AeriqUsuario WHERE id=?');$id=(int)$u['id'];$s->bind_param('i',$id);$s->execute();$row=$s->get_result()->fetch_assoc();
        if (!$row || !password_verify((string)($d['current']??''),$row['password_hash'])) auth_responder(422,['error'=>'Contraseña actual incorrecta']);
        $pass=auth_password($d['password']??'');$hash=password_hash($pass,PASSWORD_DEFAULT);
        $s=$db->prepare('UPDATE AeriqUsuario SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();auth_responder(200,['ok'=>true]);
    }
    if ($action==='forgot') {
        $email=auth_email($d['email']??'');auth_rate('forgot',$email,4,3600);
        auth_mail_config_ok();$u=auth_find($email);
        if ($u && $u['verificado_en']) auth_generate_code((int)$u['id'],'recuperar',$email);
        auth_responder(200,['ok'=>true,'message'=>'Si existe una cuenta verificada, recibirás un código para restablecerla.']);
    }
    if ($action==='reset-password') {
        $email=auth_email($d['email']??'');auth_rate('reset',$email,12);$code=trim((string)($d['code']??''));$pass=auth_password($d['password']??'');
        $u=auth_find($email);if (!$u || !$u['verificado_en']) auth_responder(422,['error'=>'Código incorrecto o caducado']);
        auth_verify_code((int)$u['id'],'recuperar',$code);
        $hash=password_hash($pass,PASSWORD_DEFAULT);$id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE AeriqUsuario SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();
        auth_login_session($id);auth_responder(200,['ok'=>true,'user'=>auth_public($u)]);
    }
    auth_responder(404,['error'=>'Acción no disponible']);
} catch (mysqli_sql_exception $e) {
    error_log('AERIQ base de datos: '.$e->getMessage());
    auth_responder($e->getCode()===1062?409:500,['error'=>$e->getCode()===1062?'La cuenta ya existe o hay un conflicto en el registro':'Error de base de datos. Comprueba la migración y la configuración.']);
} catch (Throwable $e) {
    error_log('AERIQ auth error: '.$e->getMessage());
    auth_responder(500,['error'=>'Error interno de autenticación']);
}
