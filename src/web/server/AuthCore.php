<?php
declare(strict_types=1);
date_default_timezone_set('UTC');

require_once __DIR__ . '/Logica.php';
require_once __DIR__ . '/AeriqCore.php';

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
    $db=conexionBD();
    $key=hash('sha256',$action.'|'.($_SERVER['REMOTE_ADDR']??'unknown').'|'.$subject);
    $now=time();
    $cutoff=date('Y-m-d H:i:s',$now-$interval);
    $stmt=$db->prepare('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES (?,NOW(),1) ON DUPLICATE KEY UPDATE intentos=IF(ventana_en<? ,1,intentos+1), ventana_en=IF(ventana_en<? ,NOW(),ventana_en)');
    $stmt->bind_param('sss',$key,$cutoff,$cutoff);
    $stmt->execute();
    $stmt=$db->prepare('SELECT intentos FROM limites_acceso WHERE clave_hash=?');$stmt->bind_param('s',$key);$stmt->execute();
    if ((int)($stmt->get_result()->fetch_assoc()['intentos']??0)>$limit) auth_responder(429,['error'=>'Demasiados intentos. Vuelve a intentarlo más tarde.']);
}
function auth_find(string $email): ?array {
    $s=conexionBD()->prepare('SELECT u.*,r.codigo AS rol FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.email=? LIMIT 1');
    $s->bind_param('s',$email);$s->execute(); return $s->get_result()->fetch_assoc()?:null;
}
function auth_login_allowed(array $user): void {
    $a=aeriq_access((int)$user['id']);
    if ((int)$a['cambiar_clave']) {
        if (!$a['invitacion_expira_en'] || strtotime($a['invitacion_expira_en'])<=time()) throw new AeriqError('La invitación ha caducado. Recupera el acceso para activar tu cuenta.',403);
    } elseif (!(int)$a['activo']) throw new AeriqError('Cuenta desactivada. Contacta con tu organización.',403);
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
    $s=conexionBD()->prepare('SELECT * FROM usuarios WHERE id=? AND (verificado_en IS NOT NULL OR debe_cambiar_clave=1)');
    $s->bind_param('i',$id);$s->execute();$user=$s->get_result()->fetch_assoc()?:null;
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
/** Envío SMTP directo sobre TLS, sin librerías Composer. */
function auth_smtp_read($socket,array $expected): string {
    $response='';$code=null;
    do {
        $line=fgets($socket,2048);
        if ($line===false) throw new RuntimeException('El servidor SMTP cerró la conexión.');
        $response.=$line;
        if ($code===null && preg_match('/^(\d{3})[- ]/',$line,$m)) $code=(int)$m[1];
    } while (isset($line[3]) && $line[3]==='-');
    if ($code===null || !in_array($code,$expected,true)) throw new RuntimeException('Respuesta SMTP inesperada ('.($code??'sin código').').');
    return $response;
}
function auth_smtp_write($socket,string $data): void {
    $offset=0;$length=strlen($data);
    while ($offset<$length) {
        $written=fwrite($socket,substr($data,$offset));
        if ($written===false || $written===0) throw new RuntimeException('No se pudo escribir en la conexión SMTP.');
        $offset+=$written;
    }
}
function auth_smtp_command($socket,string $command,array $expected): string {
    auth_smtp_write($socket,$command."\r\n");
    return auth_smtp_read($socket,$expected);
}
function auth_smtp_send(array $smtp,string $recipient,string $subject,string $body,?string $replyTo=null,?string $html=null): void {
    $host=(string)$smtp['host'];$port=(int)$smtp['port'];$encryption=(string)($smtp['encryption']??'tls');
    $endpoint=($encryption==='ssl'?'ssl://':'').$host.':'.$port;
    $socket=@stream_socket_client($endpoint,$errno,$errstr,20,STREAM_CLIENT_CONNECT);
    if (!$socket) throw new RuntimeException('No se pudo conectar con el servidor SMTP ('.$errno.').');
    stream_set_timeout($socket,20);
    try {
        auth_smtp_read($socket,[220]);
        $hello=preg_replace('/[^A-Za-z0-9.-]/','',(string)($_SERVER['SERVER_NAME']??'localhost'))?:'localhost';
        auth_smtp_command($socket,'EHLO '.$hello,[250]);
        if ($encryption==='tls') {
            auth_smtp_command($socket,'STARTTLS',[220]);
            $crypto=stream_socket_enable_crypto($socket,true,STREAM_CRYPTO_METHOD_TLS_CLIENT);
            if ($crypto!==true) throw new RuntimeException('No se pudo negociar TLS con el servidor SMTP.');
            auth_smtp_command($socket,'EHLO '.$hello,[250]);
        }
        auth_smtp_command($socket,'AUTH LOGIN',[334]);
        auth_smtp_command($socket,base64_encode((string)$smtp['username']),[334]);
        $smtpPassword=preg_replace('/\s+/','',(string)$smtp['password']);
        auth_smtp_command($socket,base64_encode($smtpPassword),[235]);
        auth_smtp_command($socket,'MAIL FROM:<'.(string)$smtp['from_email'].'>',[250]);
        auth_smtp_command($socket,'RCPT TO:<'.$recipient.'>',[250,251]);
        auth_smtp_command($socket,'DATA',[354]);
        $fromName=str_replace(["\r","\n"],'',(string)($smtp['from_name']??'AERIQ'));
        $encodedSubject='=?UTF-8?B?'.base64_encode($subject).'?=';
        [$type,$mime]=aeriq_mail_body($body,$html);
        $headers=['From: '.$fromName.' <'.$smtp['from_email'].'>','To: '.$recipient,'Subject: '.$encodedSubject,
            'MIME-Version: 1.0','Content-Type: '.$type,'X-Mailer: AERIQ'];
        if ($html===null) $headers[]='Content-Transfer-Encoding: 8bit';
        if ($replyTo!==null && filter_var($replyTo,FILTER_VALIDATE_EMAIL)) $headers[]='Reply-To: '.$replyTo;
        // Transparencia SMTP: una línea que empieza por «.» se duplica.
        $mime=preg_replace('/(^|\r\n)\./','$1..',$mime);
        $message=implode("\r\n",$headers)."\r\n\r\n".$mime."\r\n.\r\n";
        auth_smtp_write($socket,$message);
        auth_smtp_read($socket,[250]);
        auth_smtp_command($socket,'QUIT',[221]);
    } finally { fclose($socket); }
}
/**
 * Transporte: SMTP cuando hay SMTPConfig.php (nativo si falta PHPMailer);
 * de lo contrario, PHP mail() del alojamiento.
 * MailConfig.php es opcional y solo contiene el remitente (no secretos).
 */
function auth_mail_config_ok(): array {
    $smtpFile=__DIR__.'/SMTPConfig.php';
    if (is_file($smtpFile)) {
        $vendor=dirname(__DIR__).'/vendor/autoload.php';
        $smtp=require $smtpFile;
        if (!is_array($smtp)) throw new AeriqError('Revisa la configuración de correo SMTP.',503);
        foreach (['host','port','username','password','from_email'] as $key) {
            if (empty($smtp[$key]) || str_contains((string)$smtp[$key],'tudominio.es') || str_contains((string)$smtp[$key],'CONTRASENA') || str_contains((string)$smtp[$key],'GOOGLE_APP_PASSWORD')) {
                throw new AeriqError('Completa los datos SMTP en SMTPConfig.php.',503);
            }
        }
        if (!filter_var((string)$smtp['from_email'],FILTER_VALIDATE_EMAIL)) throw new AeriqError('Remitente SMTP no válido.',503);
        return ['transport'=>is_file($vendor)?'smtp':'smtp_native','smtp'=>$smtp];
    }

    $opts=[];
    $mailFile=__DIR__.'/MailConfig.php';
    if (is_file($mailFile)) {
        $opts=require $mailFile;
        if (!is_array($opts)) throw new AeriqError('MailConfig.php debe devolver un array.',503);
    }
    $from=trim((string)($opts['from_email'] ?? getenv('AERIQ_MAIL_FROM') ?: ''));
    if ($from==='') {
        // SERVER_NAME procede de la configuración del virtual host, no de HTTP_HOST.
        $domain=strtolower(trim((string)($_SERVER['SERVER_NAME']??'')));
        if (preg_match('/^[a-z0-9.-]+$/D',$domain) && str_contains($domain,'.')) {
            $from='no-reply@'.$domain;
        }
    }
    if (!function_exists('mail') || !filter_var($from,FILTER_VALIDATE_EMAIL)) {
        throw new AeriqError('El envío PHP mail() no está disponible o falta un remitente válido. Configura server/MailConfig.php.',503);
    }
    $fromName=trim((string)($opts['from_name']??'AERIQ'));
    // Evitar inyección de cabeceras incluso si el fichero de configuración se modifica.
    $fromName=str_replace(["\r","\n"],'',$fromName);
    if ($fromName==='') $fromName='AERIQ';
    return ['transport'=>'php_mail','from_email'=>$from,'from_name'=>$fromName];
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
    $db=conexionBD();
    if ($throttle) {
        $stmt=$db->prepare('SELECT enviado_en FROM codigos_email WHERE usuario_id=? AND proposito=?');
        $stmt->bind_param('is',$userId,$purpose);$stmt->execute();$old=$stmt->get_result()->fetch_assoc();
        if ($old && time()-strtotime($old['enviado_en'])<60) auth_responder(429,['error'=>'Espera un minuto antes de solicitar otro código.']);
    }
    auth_mail_config_ok();
    $code=(string)random_int(100000,999999);
    $hash=password_hash($code,PASSWORD_DEFAULT);
    // No se invalida un código anterior si el servidor rechaza el correo nuevo.
    auth_send_mail($email,$code,$purpose);
    $stmt=$db->prepare('INSERT INTO codigos_email(usuario_id,proposito,codigo_hash,vence_en,enviado_en,intentos) VALUES (?,?,?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW(),0) ON DUPLICATE KEY UPDATE codigo_hash=VALUES(codigo_hash),vence_en=VALUES(vence_en),enviado_en=VALUES(enviado_en),intentos=0');
    $stmt->bind_param('iss',$userId,$purpose,$hash);$stmt->execute();
}
/**
 * Comprueba un código sin gastarlo (para avisar al abrir el enlace de recuperar si ya no sirve).
 * Devuelve 'valido', 'caducado' (vencido, usado o bloqueado) o 'incorrecto'. Un código incorrecto cuenta como
 * intento, igual que al guardar: así no sirve para adivinar códigos.
 */
function auth_check_code(int $id,string $purpose,string $code): string {
    if (!preg_match('/^[0-9]{6}$/',$code)) return 'incorrecto';
    $db=conexionBD();
    $s=$db->prepare('SELECT codigo_hash,vence_en,intentos FROM codigos_email WHERE usuario_id=? AND proposito=?');
    $s->bind_param('is',$id,$purpose);$s->execute();$row=$s->get_result()->fetch_assoc();
    if (!$row || strtotime($row['vence_en'])<time() || (int)$row['intentos']>=5) return 'caducado';
    if (password_verify($code,$row['codigo_hash'])) return 'valido';
    $update=$db->prepare('UPDATE codigos_email SET intentos=intentos+1 WHERE usuario_id=? AND proposito=?');
    $update->bind_param('is',$id,$purpose);$update->execute();
    return 'incorrecto';
}
function auth_verify_code(int $id,string $purpose,string $code): void {
    if (!preg_match('/^[0-9]{6}$/',$code)) auth_responder(422,['error'=>'Introduce el código de seis cifras']);
    $db=conexionBD();
    $s=$db->prepare('SELECT codigo_hash,vence_en,intentos FROM codigos_email WHERE usuario_id=? AND proposito=?');
    $s->bind_param('is',$id,$purpose);$s->execute();$row=$s->get_result()->fetch_assoc();
    if (!$row || strtotime($row['vence_en'])<time() || (int)$row['intentos']>=5) auth_responder(422,['error'=>'Código caducado o bloqueado. Solicita uno nuevo.']);
    $update=$db->prepare('UPDATE codigos_email SET intentos=intentos+1 WHERE usuario_id=? AND proposito=?');
    $update->bind_param('is',$id,$purpose);$update->execute();
    if (!password_verify($code,$row['codigo_hash'])) auth_responder(422,['error'=>'Código incorrecto']);
    $delete=$db->prepare('DELETE FROM codigos_email WHERE usuario_id=? AND proposito=?');
    $delete->bind_param('is',$id,$purpose);$delete->execute();
}
