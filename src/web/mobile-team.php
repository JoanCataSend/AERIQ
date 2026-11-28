<?php
/*
 * Fichero: mobile-team.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);
require_once __DIR__.'/server/AeriqHttp.php';
/*
 * Orb de la campaña desde Android, para el admin de campaña (permiso equipo.gestionar).
 * Es solo otra puerta, con el token del móvil, a la misma lógica que usa el panel web en portal.php:
 * aeriq_dashboard() para listar y aeriq_device_save() para registrar o reasignar un Orb, con sus mismas reglas
 * (solo operadores de su campaña, un Orb por operador, la asignación anterior se cierra). Ver doc/api.md.
 */
try {
    $a=aeriq_ready(aeriq_mobile_user(),'equipo.gestionar');$method=$_SERVER['REQUEST_METHOD']??'GET';
    if ($method==='GET') {
        $d=aeriq_dashboard($a);
        // Solo lo que la app necesita: quién puede medir (sin correo ni teléfono) y los Orb de la campaña.
        $operators=[];
        foreach ($d['workers'] as $w) {
            if (!(int)$w['puede_medir']) continue;
            $operators[]=['id'=>(int)$w['id'],'nombre'=>trim($w['nombre'].' '.$w['apellidos']),'orb'=>$w['orb'],
                'activo'=>(bool)(int)$w['activo'],'pendiente'=>(bool)(int)$w['cambiar_clave']];
        }
        $devices=[];
        foreach ($d['devices'] as $o) {
            $devices[]=['id'=>(int)$o['id'],'nombre'=>$o['nombre'],'direccion_ble'=>$o['direccion_ble'],
                'trabajador_id'=>$o['trabajador_id']===null?null:(int)$o['trabajador_id'],'trabajador'=>$o['trabajador'],
                'activo'=>(bool)(int)$o['activo'],'simulado'=>(bool)(int)$o['simulado'],'ultima_medicion'=>$o['ultima_medicion']];
        }
        $c=$d['campaigns'][0]??null;
        auth_responder(200,['campaign'=>$c?['id'=>(int)$c['id'],'nombre'=>$c['nombre']]:null,'operators'=>$operators,'devices'=>$devices]);
    }
    if ($method==='POST') auth_responder(200,aeriq_device_save($a,aeriq_json_body()));
    throw new AeriqError('Método no permitido.','method_not_allowed');
} catch (Throwable $e) { aeriq_failure($e); }
