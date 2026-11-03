<?php
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    $a=aeriq_ready(aeriq_mobile_user(),['trabajador']);$method=$_SERVER['REQUEST_METHOD']??'GET';
    if ($method==='GET' && ($_GET['action']??'')==='history') auth_responder(200,['readings'=>aeriq_worker_history($a)]);
    if ($method==='GET') auth_responder(200,['assignments'=>aeriq_assignments($a)]);
    if ($method==='POST') auth_responder(200,aeriq_ingest($a,aeriq_json_body()));
    throw new AeriqError('Método no permitido.',405);
} catch (Throwable $e) { aeriq_failure($e); }
