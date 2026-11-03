<?php
declare(strict_types=1);
require_once __DIR__.'/AuthCore.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');header('X-Content-Type-Options: nosniff');
date_default_timezone_set('UTC');
function aeriq_https(): void {
    if (($_SERVER['HTTPS']??'off')==='off' && ($_SERVER['HTTP_X_FORWARDED_PROTO']??'')!=='https' && !in_array($_SERVER['REMOTE_ADDR']??'',['127.0.0.1','::1'],true)) throw new AeriqError('Se requiere HTTPS.',403);
}
function aeriq_web_user(): array {
    aeriq_https();ini_set('session.use_strict_mode','1');session_name('AERIQSESSID');
    session_set_cookie_params(['lifetime'=>0,'path'=>'/','secure'=>true,'httponly'=>true,'samesite'=>'Lax']);session_start();
    $u=auth_current();if (!$u) throw new AeriqError('Inicia sesión.',401);
    if (($_SERVER['REQUEST_METHOD']??'GET')!=='GET' && (!isset($_SESSION['csrf']) || !hash_equals($_SESSION['csrf'],(string)($_SERVER['HTTP_X_CSRF_TOKEN']??'')))) throw new AeriqError('Sesión caducada. Recarga la página.',403);
    return $u;
}
function aeriq_mobile_user(): array {
    aeriq_https();$token=(string)($_SERVER['HTTP_X_AERIQ_TOKEN']??'');
    if (!preg_match('/^[a-f0-9]{64}$/D',$token)) throw new AeriqError('Token de sesión no válido.',401);
    $u=aeriq_one('SELECT u.id,u.nombre,u.apellidos,u.email,u.telefono FROM sesiones_moviles s JOIN usuarios u ON u.id=s.usuario_id WHERE s.token_hash=? AND s.vence_en>NOW() AND (u.verificado_en IS NOT NULL OR u.debe_cambiar_clave=1)',[hash('sha256',$token)]);
    if (!$u) throw new AeriqError('Sesión caducada. Inicia sesión.',401);return $u;
}
function aeriq_json_body(): array {
    if (stripos($_SERVER['CONTENT_TYPE']??'','application/json')!==0) throw new AeriqError('Se esperaba application/json.',415);return auth_body();
}
function aeriq_failure(Throwable $e): never {
    if ($e instanceof AeriqError) auth_responder($e->status,['error'=>$e->getMessage()]);
    if ($e instanceof mysqli_sql_exception && $e->getCode()===1062) auth_responder(409,['error'=>'El recurso ya existe; no se han sobrescrito datos.']);
    error_log('AERIQ: '.$e->getMessage());auth_responder(500,['error'=>'No se ha podido completar la operación. Comprueba la migración y la configuración del servidor.']);
}
