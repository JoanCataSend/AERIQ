<?php
declare(strict_types=1);
require_once __DIR__.'/AuthCore.php';
require_once __DIR__.'/AeriqCore.php';
require_once __DIR__.'/../communication/HttpErrorMapper.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');
date_default_timezone_set('UTC');
/**
 * Gestiona https.
 * Diseño lógico: (sin parámetros) → aeriq_https() → void; según doc/aeriq_business_logic_design.md.
 */
function aeriq_https(): void {
    if (($_SERVER['HTTPS']??'off')==='off' && ($_SERVER['HTTP_X_FORWARDED_PROTO']??'')!=='https' && !in_array($_SERVER['REMOTE_ADDR']??'',['127.0.0.1','::1'],true)) throw new AeriqError('Se requiere HTTPS.','forbidden');
}
/**
 * Gestiona web usuario.
 * Diseño lógico: (sin parámetros) → aeriq_web_user() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_web_user(): array {
    aeriq_https();ini_set('session.use_strict_mode','1');session_name('AERIQSESSID');
    session_set_cookie_params(['lifetime'=>0,'path'=>'/','secure'=>true,'httponly'=>true,'samesite'=>'Lax']);session_start();
    $u=auth_current();if (!$u) throw new AeriqError('Inicia sesión.','unauthenticated');
    if (($_SERVER['REQUEST_METHOD']??'GET')!=='GET' && (!isset($_SESSION['csrf']) || !hash_equals($_SESSION['csrf'],(string)($_SERVER['HTTP_X_CSRF_TOKEN']??'')))) throw new AeriqError('Sesión caducada. Recarga la página.','forbidden');
    return $u;
}
/**
 * Gestiona mobile usuario.
 * Diseño lógico: (sin parámetros) → aeriq_mobile_user() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_mobile_user(): array {
    aeriq_https();$token=(string)($_SERVER['HTTP_X_AERIQ_TOKEN']??'');
    if (!preg_match('/^[a-f0-9]{64}$/D',$token)) throw new AeriqError('Token de sesión no válido.','unauthenticated');
    $u=aeriq_auth_mobile_user(hash('sha256',$token));
    if (!$u) throw new AeriqError('Sesión caducada. Inicia sesión.','unauthenticated');return $u;
}
/**
 * Gestiona JSON cuerpo.
 * Diseño lógico: (sin parámetros) → aeriq_json_body() → array; según doc/aeriq_business_logic_design.md.
 */
function aeriq_json_body(): array {
    if (stripos($_SERVER['CONTENT_TYPE']??'','application/json')!==0) throw new AeriqError('Se esperaba application/json.','unsupported_media');return auth_body();
}
/**
 * Gestiona failure.
 * Diseño lógico: (Throwable $e) → aeriq_failure() → never; según doc/aeriq_business_logic_design.md.
 */
function aeriq_failure(Throwable $e): never {
    if ($e instanceof DomainError) auth_responder(aeriq_http_status($e),['error'=>$e->getMessage()]+($e->field!==null?['field'=>$e->field]:[]));
    if ($e instanceof mysqli_sql_exception && $e->getCode()===1062) auth_responder(409,['error'=>'El recurso ya existe; no se han sobrescrito datos.']);
    error_log('AERIQ: '.$e->getMessage());auth_responder(500,['error'=>'No se ha podido completar la operación. Comprueba la migración y la configuración del servidor.']);
}
