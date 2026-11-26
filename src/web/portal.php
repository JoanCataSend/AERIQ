<?php
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    $u=aeriq_web_user();$action=(string)($_GET['action']??'dashboard');$method=$_SERVER['REQUEST_METHOD']??'GET';
    if ($action==='map' && $method==='GET') auth_responder(200,aeriq_consultar_mapa(aeriq_portal_access($u,$action,$method,$_GET),'organizacion',(string)($_GET['mode']??'real'),aeriq_map_hours($_GET['hours']??24)));
    if ($action==='requests' && $method==='GET') {
        $access=aeriq_portal_access($u,$action,$method);auth_responder(200,aeriq_consultar_panel($access,'solicitudes'));
    }
    if ($action==='request-status' && $method==='POST') {
        $c=aeriq_portal_access($u,$action,$method);auth_responder(200,aeriq_request_status($c,aeriq_json_body()));
    }
    if ($action==='request-reopen' && $method==='POST') {
        $c=aeriq_portal_access($u,$action,$method);auth_responder(200,aeriq_request_reopen($c,aeriq_json_body()));
    }
    // El operador consulta su campaña (solo lectura); el resto del panel exige informes.
    if ($action==='my-campaign' && $method==='GET') auth_responder(200,aeriq_my_campaign(aeriq_portal_access($u,$action,$method)));
    $a=aeriq_portal_access($u,$action,$method,$_GET);
    // HU36: avisos entre personas. Para todas las cuentas de la organización (también operadores).
    if (str_starts_with($action,'notice')) auth_responder(200,aeriq_notice_route($a,$method,$action));
    if ($method==='GET' && $action==='dashboard') auth_responder(200,aeriq_consultar_panel($a,'resumen'));
    if ($method==='GET' && $action==='readings') {
        // El servicio compone lecturas y descartadas; el CSV omite siempre las descartadas.
        auth_responder(200,aeriq_consultar_panel($a,'lecturas',$_GET));
    }
    if ($method==='POST') {
        $d=aeriq_json_body();
        if ($action==='worker') {
            auth_responder(201,aeriq_worker($a,$d));
        }
        if ($action==='campaign') auth_responder(200,aeriq_campaign_save($a,$d));
        if ($action==='device') auth_responder(200,aeriq_device_save($a,$d));
        // Gestión de cuentas: el admin principal, sus admins de campaña; el admin de campaña, sus operadores.
        if ($action==='worker-edit') auth_responder(200,aeriq_worker_edit($a,$d));
        if ($action==='worker-status') auth_responder(200,aeriq_worker_status($a,$d));
        if ($action==='worker-invite') auth_responder(200,aeriq_worker_invite($a,$d));
        if ($action==='campaign-settings') auth_responder(200,aeriq_campaign_settings($a,$d));
    }
    throw new AeriqError('Operación o método no disponible.','method_not_allowed');
} catch (Throwable $e) { aeriq_failure($e); }
