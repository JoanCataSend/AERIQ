<?php
declare(strict_types=1);
date_default_timezone_set('UTC');

require_once __DIR__ . '/../business_logic/DomainError.php';
require_once __DIR__ . '/../communication/MailTransport.php';
require_once __DIR__ . '/../database/AuthRepository.php';

function auth_responder(int $status, array $payload): never {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}
function auth_body(): array {
    if ((int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 16384) auth_responder(413, ['error'=>'Petición demasiado grande']);
    $obj=json_decode(file_get_contents('php://input') ?: '');
    if (!$obj instanceof stdClass) auth_responder(400,['error'=>'Se esperaba un objeto JSON']);
    return get_object_vars($obj);
}
function auth_email(mixed $value): string {
    if (!is_string($value)) auth_responder(422,['error'=>'Correo electrónico no válido']);
    $email=strtolower(trim((string)$value));
    if (strlen($email)>254 || !filter_var($email, FILTER_VALIDATE_EMAIL)) auth_responder(422,['error'=>'Correo electrónico no válido']);
    return $email;
}
function auth_string(mixed $value, int $limit): string {
    if (!is_string($value)) auth_responder(422,['error'=>'Texto no válido']);
    $s=trim((string)$value);
    if ($s==='' || (function_exists('mb_strlen') ? mb_strlen($s,'UTF-8') : strlen($s))>$limit) auth_responder(422,['error'=>'Revisa los datos introducidos']);
    return $s;
}
/**
 * Requisitos de la contraseña nueva (T33): al menos 8 caracteres, una mayúscula, una minúscula, un número y un
 * símbolo (máximo 256). Devuelve lo que falta, vacío si cumple. Los mismos requisitos están en auth-client.js
 * (web) y en PasswordRules.java (Android), pero el que decide es el servidor.
 */
function auth_password_missing(string $p): array {
    $missing=[];
    $length=function_exists('mb_strlen')?mb_strlen($p,'UTF-8'):strlen($p);
    if ($length<8) $missing[]='al menos 8 caracteres';
    if (!preg_match('/\p{Lu}/u',$p)) $missing[]='una mayúscula';
    if (!preg_match('/\p{Ll}/u',$p)) $missing[]='una minúscula';
    if (!preg_match('/\p{Nd}/u',$p)) $missing[]='un número';
    if (!preg_match('/[^\p{L}\p{Nd}\s]/u',$p)) $missing[]='un símbolo (por ejemplo ! ? # @ . -)';
    return $missing;
}
function auth_password(mixed $value): string {
    if (!is_string($value)) auth_responder(422,['error'=>'Contraseña no válida','field'=>'password']);
    $p=(string)$value;
    if (strlen($p)>256) auth_responder(422,['error'=>'La contraseña puede tener como máximo 256 caracteres.','field'=>'password']);
    if (!preg_match('//u',$p)) auth_responder(422,['error'=>'Contraseña no válida','field'=>'password']);
    $missing=auth_password_missing($p);
    if ($missing) {
        $last=array_pop($missing);
        $list=$missing?implode(', ',$missing).' y '.$last:$last;
        auth_responder(422,['error'=>'La contraseña no es segura. Le falta: '.$list.'.','field'=>'password','missing'=>array_merge($missing,[$last])]);
    }
    return $p;
}
function auth_rate(string $action, string $subject, int $limit, int $interval=900): void {
    $key=hash('sha256',$action.'|'.($_SERVER['REMOTE_ADDR']??'unknown').'|'.$subject);
    $cutoff=date('Y-m-d H:i:s',time()-$interval);
    if (aeriq_auth_rate_attempts($key,$cutoff)>$limit) auth_responder(429,['error'=>'Demasiados intentos. Vuelve a intentarlo más tarde.']);
}
function auth_find(string $email): ?array {
    return aeriq_auth_user_by_email($email);
}
function auth_login_allowed(array $user): void {
    $a=aeriq_access((int)$user['id']);
    if ((int)$a['cambiar_clave']) {
        if (!$a['invitacion_expira_en'] || strtotime($a['invitacion_expira_en'])<=time()) throw new AeriqError('La invitación ha caducado. Recupera el acceso para activar tu cuenta.','forbidden');
    } elseif (!(int)$a['activo']) throw new AeriqError('Cuenta desactivada. Contacta con tu organización.','forbidden');
}
function auth_public(array $user): array {
    $a=aeriq_access((int)$user['id']);
    auth_login_allowed($user);
    return ['id'=>(int)$user['id'],'name'=>$user['nombre'],'last'=>$user['apellidos'],'email'=>$user['email'],'phone'=>$user['telefono']??'',
        'role'=>$a['rol'],'roleName'=>$a['rol_nombre'],'permissions'=>$a['permisos'],
        'organizationId'=>$a['organizacion_id'],'organization'=>$a['organizacion'],'campaign'=>$a['campana'],
        'mustChangePassword'=>(bool)$a['cambiar_clave']];
}
function auth_current(): ?array {
    if (empty($_SESSION['user_id'])) return null;
    $id=(int)$_SESSION['user_id'];
    if (!empty($_SESSION['last_seen']) && time()-(int)$_SESSION['last_seen']>3600) {
        unset($_SESSION['user_id'],$_SESSION['last_seen']);return null;
    }
    $user=aeriq_auth_session_user($id);
    if (!$user) {unset($_SESSION['user_id']);return null;}
    $_SESSION['last_seen']=time();return $user;
}
function auth_require_user(): array {
    $u=auth_current(); if (!$u) auth_responder(401,['error'=>'Debes iniciar sesión']);auth_login_allowed($u);return $u;
}
function auth_login_session(int $id): void {
    session_regenerate_id(true);
    $_SESSION['user_id']=$id;
    $_SESSION['last_seen']=time();
    $_SESSION['csrf']=bin2hex(random_bytes(24));
}
/**
 * Correo de recuperación (T34, T35): código de seis cifras y enlace que abre recuperar.html con el correo y el
 * código ya puestos. El código caduca a los 10 minutos y se borra al usarse, así que el enlace también.
 * Que mail() acepte el mensaje no garantiza que llegue a la bandeja de entrada.
 */
function auth_send_mail(string $recipient,string $code,string $purpose): void {
    if (!filter_var($recipient,FILTER_VALIDATE_EMAIL) || !preg_match('/^[0-9]{6}$/',$code)) {
        auth_responder(422,['error'=>'Datos de correo no válidos.']);
    }
    $link=aeriq_public_url('recuperar.html').'#'.http_build_query(['email'=>$recipient,'code'=>$code],'','&',PHP_QUERY_RFC3986);
    $mail=aeriq_mail_compose('Recupera tu contraseña',[
        ['p','Hemos recibido una petición para cambiar la contraseña de tu cuenta de AERIQ.'],
        ['button','Elegir una contraseña nueva',$link],
        ['p','También puedes escribir este código en la web o en la app de AERIQ:'],
        ['code',$code],
        ['note','El enlace y el código caducan en 10 minutos y solo sirven una vez. Si no lo has pedido tú, ignora este mensaje: tu contraseña no cambia. No compartas el código con nadie.'],
    ],'Tu código para recuperar el acceso a AERIQ');
    aeriq_mail($recipient,'AERIQ · Recupera tu contraseña',$mail['text'],null,$mail['html']);
}
function auth_generate_code(int $userId,string $purpose,string $email,bool $throttle=true): void {
    if ($throttle) {
        $old=aeriq_auth_code_sent_at($userId,$purpose);
        if ($old && time()-strtotime($old['enviado_en'])<60) auth_responder(429,['error'=>'Espera un minuto antes de solicitar otro código.']);
    }
    aeriq_mailer_ready();
    $code=(string)random_int(100000,999999);
    $hash=password_hash($code,PASSWORD_DEFAULT);
    // No se invalida un código anterior si el servidor rechaza el correo nuevo.
    auth_send_mail($email,$code,$purpose);
    aeriq_auth_store_code($userId,$purpose,$hash);
}
/**
 * Comprueba un código sin gastarlo (para avisar al abrir el enlace de recuperar si ya no sirve).
 * Devuelve 'valido', 'caducado' (vencido, usado o bloqueado) o 'incorrecto'. Un código incorrecto cuenta como
 * intento, igual que al guardar: así no sirve para adivinar códigos.
 */
function auth_check_code(int $id,string $purpose,string $code): string {
    if (!preg_match('/^[0-9]{6}$/',$code)) return 'incorrecto';
    $row=aeriq_auth_code($id,$purpose);
    if (!$row || strtotime($row['vence_en'])<time() || (int)$row['intentos']>=5) return 'caducado';
    if (password_verify($code,$row['codigo_hash'])) return 'valido';
    aeriq_auth_increment_code_attempt($id,$purpose);
    return 'incorrecto';
}
function auth_verify_code(int $id,string $purpose,string $code): void {
    if (!preg_match('/^[0-9]{6}$/',$code)) auth_responder(422,['error'=>'Introduce el código de seis cifras']);
    $row=aeriq_auth_code($id,$purpose);
    if (!$row || strtotime($row['vence_en'])<time() || (int)$row['intentos']>=5) auth_responder(422,['error'=>'Código caducado o bloqueado. Solicita uno nuevo.']);
    aeriq_auth_increment_code_attempt($id,$purpose);
    if (!password_verify($code,$row['codigo_hash'])) auth_responder(422,['error'=>'Código incorrecto']);
    aeriq_auth_delete_code($id,$purpose);
}
