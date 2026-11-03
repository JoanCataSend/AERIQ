<?php
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    $u=aeriq_web_user();$action=(string)($_GET['action']??'dashboard');$method=$_SERVER['REQUEST_METHOD']??'GET';
    if ($action==='map' && $method==='GET') auth_responder(200,aeriq_map_data(aeriq_ready($u,['admin','analista','trabajador']),$_GET));
    if ($action==='requests' && $method==='GET') {
        aeriq_ready($u,['comercial']);auth_responder(200,['requests'=>aeriq_rows('SELECT * FROM solicitudes ORDER BY recibida_en DESC LIMIT 1000')]);
    }
    if ($action==='request-status' && $method==='POST') {
        aeriq_ready($u,['comercial']);$d=aeriq_json_body();$state=$d['status']??'';
        if (!in_array($state,['pendiente','en revision','respondida','cerrada'],true)) throw new AeriqError('Estado no válido.');
        $id=aeriq_int($d['id']??null);if (!aeriq_one('SELECT id FROM solicitudes WHERE id=?',[$id])) throw new AeriqError('Solicitud no encontrada.',404);
        aeriq_sql('UPDATE solicitudes SET estado=? WHERE id=?',[$state,$id]);auth_responder(200,['ok'=>true]);
    }
    $a=aeriq_ready($u,['admin','analista']);
    if ($method==='GET' && $action==='dashboard') auth_responder(200,aeriq_dashboard($a));
    if ($method==='GET' && $action==='readings') auth_responder(200,['readings'=>aeriq_readings($a,$_GET)]);
    if ($method==='POST') {
        if ($a['rol']!=='admin') throw new AeriqError('Solo el administrador puede gestionar recursos.',403);
        $d=aeriq_json_body();
        if ($action==='worker') auth_responder(201,aeriq_worker($a,$d));
        if ($action==='campaign') auth_responder(200,aeriq_campaign_save($a,$d));
        if ($action==='device') auth_responder(200,aeriq_device_save($a,$d));
    }
    throw new AeriqError('Operación o método no disponible.',405);
} catch (Throwable $e) { aeriq_failure($e); }
