<?php
/*
 * Fichero: public.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    $method=$_SERVER['REQUEST_METHOD']??'GET';$action=$_GET['action']??'map';
    if ($method==='GET' && $action==='map') auth_responder(200,aeriq_public_map($_GET));
    if ($method==='GET' && $action==='services') auth_responder(200,aeriq_consultar_servicios());
    if ($method==='POST' && $action==='request') {
        aeriq_https();auth_rate('service-request','',8,3600);auth_responder(201,aeriq_enviar_solicitud(aeriq_json_body()));
    }
    throw new AeriqError('Operación no disponible.','method_not_allowed');
} catch (Throwable $e) { aeriq_failure($e); }
