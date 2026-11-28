<?php
/*
 * Fichero: sensor-link.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
require_once __DIR__.'/server/SensorLink.php';
try {
    $account = aeriq_ready(aeriq_mobile_user(), 'mediciones.registrar');
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') throw new AeriqError('Método no permitido.','method_not_allowed');
    // Los AERIQ los asigna el admin de campaña desde la web; la app ya no vincula AERIQ por su cuenta.
    throw new AeriqError('Tu admin de campaña es quien te asigna el AERIQ. Pídeselo y aparecerá aquí.','forbidden');
} catch (Throwable $e) { aeriq_failure($e); }
