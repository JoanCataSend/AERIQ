<?php
/*
 * Fichero: api.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
/** Adaptador de consulta PBIO sobre el esquema único AERIQ. Escrituras: field.php. */
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    if (($_SERVER['REQUEST_METHOD']??'GET')!=='GET') throw new AeriqError('Las lecturas se envían autenticadas mediante field.php.','method_not_allowed');
    $action=$_GET['accion']??'medidas';
    if ($action==='health') {aeriq_health();auth_responder(200,['ok'=>true,'schema'=>'aeriq-unificado-v2']);}
    if ($action==='tipos') auth_responder(200,aeriq_magnitudes());
    $u=aeriq_web_user();$access=aeriq_ready($u,'informes.ver');
    if ($action==='dispositivos') auth_responder(200,aeriq_devices($access));
    if ($action==='medidas' || $action==='ultima') {
        $filters=[];
        if (!empty($_GET['dispositivoId']))$filters['device']=$_GET['dispositivoId'];
        if (!empty($_GET['desde']))$filters['from']=substr($_GET['desde'],0,10);
        if (!empty($_GET['hasta']))$filters['to']=substr($_GET['hasta'],0,10);
        $rows=aeriq_readings($access,$filters);
        foreach ($rows as &$r) {$r['dispositivoId']=$r['dispositivo_id'];$r['tipoMedidaId']=(int)$r['tipo'];$r['fechaHora']=str_replace(' ','T',$r['capturada_en']);}
        unset($r);auth_responder(200,$action==='ultima'?($rows?end($rows):null):array_reverse($rows));
    }
    throw new AeriqError('Consulta no disponible. Usa public.php para el mapa público.','not_found');
} catch (Throwable $e) {aeriq_failure($e);}
