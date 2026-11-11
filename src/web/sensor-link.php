<?php
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
require_once __DIR__.'/server/SensorLink.php';
try {
    $account = aeriq_ready(aeriq_mobile_user(), 'mediciones.registrar');
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') throw new AeriqError('Método no permitido.', 405);
    auth_responder(200, aeriq_link_sensor($account, aeriq_json_body()));
} catch (Throwable $e) { aeriq_failure($e); }
