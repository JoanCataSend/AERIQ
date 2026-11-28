<?php
/*
 * Fichero: MailTransport.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/communication.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);

require_once __DIR__.'/../business_logic/DomainError.php';

/**
 * Adaptador de correo. Resuelve la configuración y encapsula SMTP, PHPMailer y mail()
 * para que los servicios solo dependan de la operación de envío.
 */
/**
 * Diseño lógico: ($socket,array $expected) → auth_smtp_read() → string; según doc/web_communication_design.md.
 */
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

/**
 * Gestiona SMTP write.
 * Diseño lógico: ($socket,string $data) → auth_smtp_write() → void; según doc/web_communication_design.md.
 */
function auth_smtp_write($socket,string $data): void {
    $offset=0;$length=strlen($data);
    while ($offset<$length) {
        $written=fwrite($socket,substr($data,$offset));
        if ($written===false || $written===0) throw new RuntimeException('No se pudo escribir en la conexión SMTP.');
        $offset+=$written;
    }
}

/**
 * Gestiona SMTP command.
 * Diseño lógico: ($socket,string $command,array $expected) → auth_smtp_command() → string; según doc/web_communication_design.md.
 */
function auth_smtp_command($socket,string $command,array $expected): string {
    auth_smtp_write($socket,$command."\r\n");
    return auth_smtp_read($socket,$expected);
}

/**
 * Gestiona SMTP send.
 * Diseño lógico: (array $smtp,string $recipient,string $subject,string $body,?string $replyTo=null,?string $html=null) → auth_smtp_send() → void; según doc/web_communication_design.md.
 */
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
        $mime=preg_replace('/(^|\r\n)\./','$1..',$mime);
        $message=implode("\r\n",$headers)."\r\n\r\n".$mime."\r\n.\r\n";
        auth_smtp_write($socket,$message);
        auth_smtp_read($socket,[250]);
        auth_smtp_command($socket,'QUIT',[221]);
    } finally { fclose($socket); }
}

/** Selecciona SMTP configurado o el transporte mail() disponible en el alojamiento. */
/**
 * Diseño lógico: (sin parámetros) → auth_mail_config_ok() → array; según doc/web_communication_design.md.
 */
function auth_mail_config_ok(): array {
    $server=dirname(__DIR__).'/server';
    $smtpFile=$server.'/SMTPConfig.php';
    if (is_file($smtpFile)) {
        $vendor=dirname(__DIR__).'/vendor/autoload.php';
        $smtp=require $smtpFile;
        if (!is_array($smtp)) throw new AeriqError('Revisa la configuración de correo SMTP.','unavailable');
        foreach (['host','port','username','password','from_email'] as $key) {
            if (empty($smtp[$key]) || str_contains((string)$smtp[$key],'tudominio.es') || str_contains((string)$smtp[$key],'CONTRASENA') || str_contains((string)$smtp[$key],'GOOGLE_APP_PASSWORD')) {
                throw new AeriqError('Completa los datos SMTP en SMTPConfig.php.','unavailable');
            }
        }
        if (!filter_var((string)$smtp['from_email'],FILTER_VALIDATE_EMAIL)) throw new AeriqError('Remitente SMTP no válido.','unavailable');
        return ['transport'=>is_file($vendor)?'smtp':'smtp_native','smtp'=>$smtp];
    }

    $opts=[];$mailFile=$server.'/MailConfig.php';
    if (is_file($mailFile)) {
        $opts=require $mailFile;
        if (!is_array($opts)) throw new AeriqError('MailConfig.php debe devolver un array.','unavailable');
    }
    $from=trim((string)($opts['from_email'] ?? getenv('AERIQ_MAIL_FROM') ?: ''));
    if ($from==='') {
        $domain=strtolower(trim((string)($_SERVER['SERVER_NAME']??'')));
        if (preg_match('/^[a-z0-9.-]+$/D',$domain) && str_contains($domain,'.')) $from='no-reply@'.$domain;
    }
    if (!function_exists('mail') || !filter_var($from,FILTER_VALIDATE_EMAIL)) {
        throw new AeriqError('El envío PHP mail() no está disponible o falta un remitente válido. Configura server/MailConfig.php.','unavailable');
    }
    $fromName=str_replace(["\r","\n"],'',trim((string)($opts['from_name']??'AERIQ')));
    return ['transport'=>'php_mail','from_email'=>$from,'from_name'=>$fromName!==''?$fromName:'AERIQ'];
}

/** Puerto de prevalidación para confirmar que hay un transporte de correo disponible. */
/**
 * Diseño lógico: (sin parámetros) → aeriq_mailer_ready() → void; según doc/web_communication_design.md.
 */
function aeriq_mailer_ready(): void {
    auth_mail_config_ok();
}

/** Envía texto plano y, si existe, su alternativa HTML usando el transporte configurado. */
/**
 * Diseño lógico: (string $recipient,string $subject,string $text,?string $replyTo=null,?string $html=null) → aeriq_mail() → void; según doc/web_communication_design.md.
 */
function aeriq_mail(string $recipient,string $subject,string $text,?string $replyTo=null,?string $html=null): void {
    $cfg=auth_mail_config_ok();
    if ($replyTo!==null && !filter_var($replyTo,FILTER_VALIDATE_EMAIL)) $replyTo=null;
    if ($cfg['transport']==='smtp_native') {
        try { auth_smtp_send($cfg['smtp'],$recipient,$subject,$text,$replyTo,$html); }
        catch (Throwable $ex) {
            error_log('AERIQ: fallo en SMTP directo (sin registrar credenciales): '.$ex->getMessage());
            throw new AeriqError('No se pudo enviar el correo por SMTP. Comprueba la contraseña de aplicación y el acceso saliente del servidor.','unavailable');
        }
        return;
    }
    if ($cfg['transport']==='smtp') {
        require_once dirname(__DIR__).'/vendor/autoload.php';
        $c=$cfg['smtp'];$m=new PHPMailer\PHPMailer\PHPMailer(true);$m->isSMTP();
        $m->Host=$c['host'];$m->Port=(int)$c['port'];$m->SMTPAuth=true;$m->Username=$c['username'];$m->Password=$c['password'];
        $m->SMTPSecure=($c['encryption']??'tls')==='ssl'?'ssl':'tls';$m->CharSet='UTF-8';
        $m->setFrom($c['from_email'],$c['from_name']??'Código Norte');$m->addAddress($recipient);if($replyTo!==null)$m->addReplyTo($replyTo);$m->Subject=$subject;
        if ($html!==null) {$m->isHTML(true);$m->Body=$html;$m->AltBody=$text;} else $m->Body=$text;
        try {$m->send();}
        catch (Throwable $ex) {
            error_log('AERIQ: fallo en transporte SMTP (sin registrar credenciales): '.$ex->getMessage());
            throw new AeriqError('No se ha podido enviar el correo. Revisa la configuración del servidor.','unavailable');
        }
        return;
    }
    [$type,$body]=aeriq_mail_body($text,$html);
    $headers=['From: '.$cfg['from_name'].' <'.$cfg['from_email'].'>','MIME-Version: 1.0','Content-Type: '.$type,'X-Mailer: AERIQ'];
    if ($replyTo!==null) $headers[]='Reply-To: '.$replyTo;
    if (!@mail($recipient,'=?UTF-8?B?'.base64_encode($subject).'?=',$body,implode("\r\n",$headers))) throw new AeriqError('El servidor no ha aceptado el correo. Comprueba que PHP mail() esté habilitado en Plesk.','unavailable');
}

/** Construye el cuerpo MIME de texto o la alternativa multiparte texto/HTML. */
/**
 * Diseño lógico: (string $text,?string $html) → aeriq_mail_body() → array; según doc/web_communication_design.md.
 */
function aeriq_mail_body(string $text,?string $html): array {
    $text=str_replace(["\r\n","\r"],"\n",$text);
    if ($html===null) return ['text/plain; charset=UTF-8',str_replace("\n","\r\n",$text)];
    $boundary='aeriq_'.bin2hex(random_bytes(12));
    $parts=['--'.$boundary,'Content-Type: text/plain; charset=UTF-8','Content-Transfer-Encoding: base64','',chunk_split(base64_encode($text),76,"\r\n"),
        '--'.$boundary,'Content-Type: text/html; charset=UTF-8','Content-Transfer-Encoding: base64','',chunk_split(base64_encode($html),76,"\r\n"),
        '--'.$boundary.'--',''];
    return ['multipart/alternative; boundary="'.$boundary.'"',implode("\r\n",$parts)];
}
