<?php
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    $method=$_SERVER['REQUEST_METHOD']??'GET';$action=$_GET['action']??'map';
    if ($method==='GET' && $action==='map') auth_responder(200,aeriq_public_map($_GET));
    if ($method==='POST' && $action==='request') {
        aeriq_https();auth_rate('service-request','',8,3600);auth_responder(201,aeriq_request(aeriq_json_body()));
    }
    throw new AeriqError('Operación no disponible.',405);
} catch (Throwable $e) { aeriq_failure($e); }
