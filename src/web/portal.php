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
    // El operador consulta su campaña (solo lectura); el resto del panel exige informes.
    if ($action==='my-campaign' && $method==='GET') auth_responder(200,aeriq_my_campaign(aeriq_ready($u,'mediciones.registrar')));
    $a=aeriq_ready($u,'informes.ver');
    if ($method==='GET' && $action==='dashboard') auth_responder(200,aeriq_dashboard($a));
    if ($method==='GET' && $action==='readings') {
        // El CSV se pide con export=1: solo el admin de campaña puede exportar.
        if (!empty($_GET['export'])) aeriq_ready($u,'datos.exportar');
        auth_responder(200,['readings'=>aeriq_readings($a,$_GET)]);
    }
    if ($method==='POST') {
        $d=aeriq_json_body();
        if ($action==='worker') {
            if (!aeriq_can($a,'campanas.gestionar') && !aeriq_can($a,'equipo.gestionar')) throw new AeriqError('Tu función no permite crear cuentas.',403);
            auth_responder(201,aeriq_worker($a,$d));
        }
        if ($action==='campaign') auth_responder(200,aeriq_campaign_save(aeriq_ready($u,'campanas.gestionar'),$d));
        if ($action==='device') auth_responder(200,aeriq_device_save(aeriq_ready($u,'equipo.gestionar'),$d));
        // Gestión de cuentas: el admin principal, sus admins de campaña; el admin de campaña, sus operadores.
        if ($action==='worker-edit') auth_responder(200,aeriq_worker_edit($a,$d));
        if ($action==='worker-status') auth_responder(200,aeriq_worker_status($a,$d));
        if ($action==='worker-invite') auth_responder(200,aeriq_worker_invite($a,$d));
        if ($action==='campaign-settings') auth_responder(200,aeriq_campaign_settings(aeriq_ready($u,'equipo.gestionar'),$d));
    }
    throw new AeriqError('Operación o método no disponible.',405);
} catch (Throwable $e) { aeriq_failure($e); }
