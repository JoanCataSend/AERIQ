<?php
/*
 * Fichero: mobile-avisos.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
/*
 * Avisos entre personas desde Android (HU36), con el token del móvil (X-AERIQ-Token).
 * Es otra puerta a la misma lógica que usa el panel web (portal.php?action=notice…): server/Avisos.php.
 * Acciones: GET notices | notice-unread | notice&id= ; POST notice-create | notice-reply | notice-close. Ver doc/api.md.
 */
try {
    $a=aeriq_ready(aeriq_mobile_user());
    auth_responder(200,aeriq_notice_route($a,$_SERVER['REQUEST_METHOD']??'GET',(string)($_GET['action']??'notices')));
} catch (Throwable $e) { aeriq_failure($e); }
