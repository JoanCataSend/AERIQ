<?php
/**
 * AERIQ – Autenticación para Android (independiente de las cookies web).
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
require_once __DIR__.'/server/FirebaseAuth.php';
require_once __DIR__.'/server/AeriqCore.php';
require_once __DIR__.'/communication/HttpErrorMapper.php';

/**
 * Gestiona mobile token.
 * Diseño lógico: (sin parámetros) → mobile_token() → ?string; según doc/frontend_business_logic_design.md.
 */
function mobile_token(): ?string {
    $raw=(string)($_SERVER['HTTP_X_AERIQ_TOKEN']??'');
    return preg_match('/^[a-f0-9]{64}$/D',$raw) ? $raw : null;
}
/**
 * Gestiona mobile current.
 * Diseño lógico: (sin parámetros) → mobile_current() → ?array; según doc/frontend_business_logic_design.md.
 */
function mobile_current(): ?array {
    $token=mobile_token();
    if ($token===null) return null;
    $hash=hash('sha256',$token);
    $s=conexionBD()->prepare('SELECT u.* FROM sesiones_moviles m JOIN usuarios u ON u.id=m.usuario_id WHERE m.token_hash=? AND m.vence_en>NOW() AND (u.verificado_en IS NOT NULL OR u.debe_cambiar_clave=1) LIMIT 1');
    $s->bind_param('s',$hash);$s->execute();
    return $s->get_result()->fetch_assoc()?:null;
}
/**
 * Gestiona mobile require.
 * Diseño lógico: (sin parámetros) → mobile_require() → array; según doc/frontend_business_logic_design.md.
 */
function mobile_require(): array {
    $u=mobile_current();
    if (!$u) auth_responder(401,['error'=>'Sesión caducada. Inicia sesión de nuevo.']);
    auth_login_allowed($u);return $u;
}
/**
 * Gestiona mobile issue.
 * Diseño lógico: (int $id) → mobile_issue() → string; según doc/frontend_business_logic_design.md.
 */
function mobile_issue(int $id): string {
    // Caducidad absoluta de 30 días. No guardamos la credencial en texto claro.
    $token=bin2hex(random_bytes(32));$hash=hash('sha256',$token);
    $s=conexionBD()->prepare('INSERT INTO sesiones_moviles(token_hash,usuario_id,vence_en) VALUES(?,?,DATE_ADD(NOW(),INTERVAL 30 DAY))');
    $s->bind_param('si',$hash,$id);$s->execute();
    return $token;
}
/**
 * Gestiona mobile usuario.
 * Diseño lógico: (int $id) → mobile_user() → array; según doc/frontend_business_logic_design.md.
 */
function mobile_user(int $id): array {
    $s=conexionBD()->prepare('SELECT * FROM usuarios WHERE id=? LIMIT 1');
    $s->bind_param('i',$id);$s->execute();
    $u=$s->get_result()->fetch_assoc();
    if (!$u) auth_responder(404,['error'=>'Cuenta no disponible']);
    return auth_public($u);
}
/**
 * Gestiona mobile cuerpo.
 * Diseño lógico: (sin parámetros) → mobile_body() → array; según doc/frontend_business_logic_design.md.
 */
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
    if ($method==='GET' && $action==='firebase-config') {
        auth_responder(200,['ok'=>true,'configured'=>firebase_web_configured(),'config'=>firebase_public_config()]);
    }
    if ($method!=='POST') auth_responder(405,['error'=>'Método no permitido']);
    $d=mobile_body();
    if ($action==='profile') aeriq_ready(mobile_require());
    // No hay autoregistro: las cuentas las crea un administrador desde la web.
    if ($action==='login') {
        $email=auth_email($d['email']??'');auth_rate('mobile-login',$email,12);
        $u=auth_find($email);$password=(string)($d['password']??'');
        if (!$u || !password_verify($password,$u['password_hash'])) auth_responder(401,['error'=>'Correo o contraseña incorrectos']);
        auth_login_allowed($u);
        if (!$u['verificado_en'] && !$u['debe_cambiar_clave']) auth_responder(403,['error'=>'La cuenta no está activada. Contacta con un administrador.']);
        $id=(int)$u['id'];
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>auth_public($u)]);
    }
    if ($action==='biometric') auth_responder(410,['error'=>'Usa el token de sesión guardado en este dispositivo tras verificar la biometría.']);
    if ($action==='google') {
        $idToken=is_string($d['idToken']??null)?trim($d['idToken']):'';
        if ($idToken==='') auth_responder(422,['error'=>'Falta el token de Google.']);
        auth_rate('mobile-google',(string)($_SERVER['REMOTE_ADDR']??'unknown'),20);
        $claims=firebase_verify_id_token($idToken);
        $replayKey=firebase_replay_claim($idToken,(int)($claims['exp']??time()+3600));
        try {
            $result=firebase_link_account($claims);
            $id=(int)$result['user']['id'];
            auth_responder(200,['ok'=>true,'linked'=>(bool)$result['linked'],'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
        } catch (Throwable $e) {
            firebase_replay_release($replayKey);
            throw $e;
        }
    }
    if ($action==='social-login') {
        // El antiguo acceso social aceptaba cualquier correo sin comprobarlo.
        // Se rechaza hasta que Android envíe un idToken real de Firebase.
        auth_responder(410,['error'=>'El acceso social móvil requiere un token de Firebase. Actualiza la app.']);
    }
    if ($action==='forgot') {
        $email=auth_email($d['email']??'');auth_rate('mobile-forgot',$email,4,3600);
        aeriq_mailer_ready();$u=auth_find($email);
        if (!$u) auth_responder(404,['error'=>'No hay ninguna cuenta de AERIQ con ese correo. Revísalo o pide el acceso a tu organización.','field'=>'email']);
        if (!$u['verificado_en'] && !$u['debe_cambiar_clave']) auth_responder(404,['error'=>'No hay ninguna cuenta de AERIQ con ese correo. Revísalo o pide el acceso a tu organización.','field'=>'email']);
        if (!$u['activo'] && !$u['debe_cambiar_clave']) auth_responder(403,['error'=>'Esta cuenta está desactivada. Contacta con tu organización.','field'=>'email']);
        auth_generate_code((int)$u['id'],'recuperar',$email);
        auth_responder(200,['ok'=>true,'message'=>'Te hemos enviado un correo con un enlace y un código para elegir una contraseña nueva. Caducan en 10 minutos.']);
    }
    if ($action==='reset-password') {
        $email=auth_email($d['email']??'');auth_rate('mobile-reset',$email,12);
        $code=trim((string)($d['code']??''));$pass=auth_password($d['password']??'');
        $u=auth_find($email);
        if (!$u || (!$u['verificado_en'] && !$u['debe_cambiar_clave']) || (!$u['activo'] && !$u['debe_cambiar_clave'])) auth_responder(422,['error'=>'Código incorrecto o caducado']);
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
        if ((function_exists('mb_strlen')?mb_strlen($phone,'UTF-8'):strlen($phone))>40) auth_responder(422,['error'=>'Teléfono demasiado largo']);
        $id=(int)$u['id'];$s=conexionBD()->prepare('UPDATE usuarios SET nombre=?,apellidos=?,telefono=? WHERE id=?');
        $s->bind_param('sssi',$name,$last,$phone,$id);$s->execute();
        auth_responder(200,['ok'=>true,'user'=>mobile_user($id)]);
    }
    if ($action==='change-password') {
        $u=mobile_require();$id=(int)$u['id'];auth_rate('mobile-password-change',(string)$id,6);
        $s=conexionBD()->prepare('SELECT password_hash FROM usuarios WHERE id=?');$s->bind_param('i',$id);$s->execute();
        $old=$s->get_result()->fetch_assoc();
        if (!$old || !password_verify((string)($d['current']??''),$old['password_hash'])) auth_responder(422,['error'=>'Contraseña actual incorrecta']);
        $newPassword=auth_password($d['password']??'');
        if (password_verify($newPassword,$old['password_hash'])) auth_responder(422,['error'=>'La contraseña nueva debe ser diferente.']);
        $hash=password_hash($newPassword,PASSWORD_DEFAULT);
        $s=conexionBD()->prepare('UPDATE usuarios SET password_hash=? WHERE id=?');$s->bind_param('si',$hash,$id);$s->execute();
        // Anula sesiones antiguas, vuelve a emitir un token para este móvil.
        aeriq_password_changed($id);
        $s=conexionBD()->prepare('DELETE FROM sesiones_moviles WHERE usuario_id=?');$s->bind_param('i',$id);$s->execute();
        auth_responder(200,['ok'=>true,'token'=>mobile_issue($id),'user'=>mobile_user($id)]);
    }
    auth_responder(404,['error'=>'Acción no disponible']);
} catch (AeriqError $e) {
    auth_responder(aeriq_http_status($e),['error'=>$e->getMessage()]+($e->field!==null?['field'=>$e->field]:[]));
} catch (mysqli_sql_exception $e) {
    error_log('AERIQ móvil SQL: '.$e->getMessage());
    auth_responder(500,['error'=>'Error de base de datos: verifica la migración de sesiones móviles.']);
} catch (Throwable $e) {
    error_log('AERIQ móvil: '.$e->getMessage());
    auth_responder(500,['error'=>'Error interno de autenticación móvil.']);
}
