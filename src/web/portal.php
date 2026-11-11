<?php
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    $u=aeriq_web_user();$action=(string)($_GET['action']??'dashboard');$method=$_SERVER['REQUEST_METHOD']??'GET';
    if ($action==='map' && $method==='GET') auth_responder(200,aeriq_map_data(aeriq_ready($u,'mapa.organizacion'),$_GET));
    if ($action==='requests' && $method==='GET') {
        aeriq_ready($u,'solicitudes.gestionar');auth_responder(200,aeriq_requests());
    }
    if ($action==='request-status' && $method==='POST') {
        $c=aeriq_ready($u,'solicitudes.gestionar');auth_responder(200,aeriq_request_status($c,aeriq_json_body()));
    }
    $a=aeriq_ready($u,'informes.ver');
    if ($method==='GET' && $action==='dashboard') auth_responder(200,aeriq_dashboard($a));
    if ($method==='GET' && $action==='readings') auth_responder(200,['readings'=>aeriq_readings($a,$_GET)]);
    if ($method==='POST') {
        if (!aeriq_can($a,'organizacion.gestionar')) throw new AeriqError('Solo el administrador puede gestionar recursos.',403);
        $d=aeriq_json_body();
        if ($action==='worker') auth_responder(201,aeriq_worker($a,$d));
        if ($action==='campaign') auth_responder(200,aeriq_campaign_save($a,$d));
        if ($action==='device') auth_responder(200,aeriq_device_save($a,$d));
    }
    throw new AeriqError('Operación o método no disponible.',405);
} catch (Throwable $e) { aeriq_failure($e); }
