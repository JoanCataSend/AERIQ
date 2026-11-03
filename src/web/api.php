<?php
/** Adaptador de consulta PBIO sobre el esquema único AERIQ. Escrituras: field.php. */
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
try {
    if (($_SERVER['REQUEST_METHOD']??'GET')!=='GET') throw new AeriqError('Las lecturas se envían autenticadas mediante field.php.',405);
    $action=$_GET['accion']??'medidas';
    if ($action==='health') {aeriq_sql('SELECT id FROM organizaciones LIMIT 1');auth_responder(200,['ok'=>true,'schema'=>'aeriq-unificado-v1']);}
    if ($action==='tipos') auth_responder(200,aeriq_rows('SELECT id AS tipoMedidaId,nombre,unidad FROM magnitudes ORDER BY id'));
    $u=aeriq_web_user();$access=aeriq_ready($u,['admin','analista']);
    if ($action==='dispositivos') auth_responder(200,aeriq_rows('SELECT id AS dispositivoId,nombre FROM dispositivos WHERE organizacion_id=?',[aeriq_org($access)]));
    if ($action==='medidas' || $action==='ultima') {
        $filters=[];
        if (!empty($_GET['dispositivoId']))$filters['device']=$_GET['dispositivoId'];
        if (!empty($_GET['desde']))$filters['from']=substr($_GET['desde'],0,10);
        if (!empty($_GET['hasta']))$filters['to']=substr($_GET['hasta'],0,10);
        $rows=aeriq_readings($access,$filters);
        foreach ($rows as &$r) {$r['dispositivoId']=$r['dispositivo_id'];$r['tipoMedidaId']=14;$r['fechaHora']=str_replace(' ','T',$r['capturada_en']);}
        unset($r);auth_responder(200,$action==='ultima'?($rows?end($rows):null):array_reverse($rows));
    }
    throw new AeriqError('Consulta no disponible. Usa public.php para el mapa público.',404);
} catch (Throwable $e) {aeriq_failure($e);}
