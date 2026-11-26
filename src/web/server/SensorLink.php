<?php
declare(strict_types=1);

/** Nombre de la campaña personal que crea aeriq_link_sensor para cada persona. */
function aeriq_personal_campaign(int $user): string { return 'Mis mediciones · '.$user; }

/**
 * «Compartir con la red» en Android. En la campaña personal de quien lo pulsa, publica o retira sus
 * mediciones del mapa público. Una campaña creada por la organización no cambia: su publicación la
 * decide quien la administra. Devuelve si la campaña queda publicada y si es la personal.
 */
function aeriq_share(array $a, array $data): array {
    $device = aeriq_int($data['deviceId'] ?? null);
    $share = aeriq_bool($data['share'] ?? null);
    $user = (int)$a['usuario_id'];
    $db = conexionBD();
    $db->begin_transaction();
    try {
        $c = aeriq_one('SELECT c.id,c.nombre,c.publicar FROM asignaciones a JOIN campanas c ON c.id=a.campana_id JOIN dispositivos d ON d.id=a.dispositivo_id WHERE a.dispositivo_id=? AND a.organizacion_id=? AND a.trabajador_id=? AND a.finalizada_en IS NULL AND d.activo=1 FOR UPDATE', [$device,aeriq_org($a),$user]);
        if (!$c) throw new AeriqError('AERIQ no asignado o inactivo.','forbidden');
        $personal = $c['nombre'] === aeriq_personal_campaign($user);
        if ($personal && (int)$c['publicar'] !== $share) aeriq_sql('UPDATE campanas SET publicar=? WHERE id=?', [$share,(int)$c['id']]);
        $db->commit();
    } catch (Throwable $e) {
        $db->rollback();
        throw $e;
    }
    $published = $personal ? (bool)$share : (bool)(int)$c['publicar'];
    return ['ok'=>true,'personal'=>$personal,'published'=>$published,
        'message'=>$personal ? ($published ? 'Tus mediciones salen en el mapa público.' : 'Tus mediciones ya no salen en el mapa público.')
            : ($published ? 'Esta campaña de tu organización ya es pública.' : 'Tus lecturas llegan a tu organización; ella decide si se publican.')];
}

/** Alta/asignación propia desde Android; nunca reasigna sensores de otra persona. */
function aeriq_link_sensor(array $a, array $data): array {
    $org = aeriq_org($a);
    $user = (int)$a['usuario_id'];
    $address = strtoupper(aeriq_text($data['address'] ?? '', 17));
    if (!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D', $address)) {
        throw new AeriqError('Dirección del sensor no válida.');
    }
    $name = aeriq_text($data['name'] ?? 'Mi sensor AERIQ', 160);
    $db = conexionBD();
    $db->begin_transaction();
    try {
        // Serializa altas propias de la organización y la creación de campaña personal.
        aeriq_one('SELECT id FROM organizaciones WHERE id=? FOR UPDATE', [$org]);
        $device = aeriq_one('SELECT * FROM dispositivos WHERE direccion_ble=? FOR UPDATE', [$address]);
        if ($device && ((int)$device['organizacion_id'] !== $org || !(int)$device['activo'] || (int)$device['simulado'])) {
            throw new AeriqError('Este sensor no está disponible para tu cuenta. Consulta con tu organización.','conflict');
        }
        if (!$device) {
            aeriq_sql('INSERT INTO dispositivos(organizacion_id,nombre,direccion_ble,activo,simulado) VALUES(?,?,?,1,0)', [$org,$name,$address]);
            $device = ['id'=>(int)$db->insert_id];
        }
        $id = (int)$device['id'];
        $old = aeriq_one('SELECT * FROM asignaciones WHERE dispositivo_id=? AND finalizada_en IS NULL FOR UPDATE', [$id]);
        if ($old && (int)$old['trabajador_id'] !== $user) {
            throw new AeriqError('Este sensor ya está asignado a otra persona. Tu organización debe liberarlo.','conflict');
        }
        if ($old) {
            $active = aeriq_one('SELECT id FROM campanas WHERE id=? AND activa=1 AND UTC_DATE() BETWEEN inicio AND fin', [(int)$old['campana_id']]);
            if (!$active) throw new AeriqError('La campaña de este sensor ha terminado o está inactiva. Consulta con tu organización.','conflict');
        } else {
            // Nace privada; la publica «Compartir con la red» (aeriq_share). Se reutiliza esté publicada o no.
            $campaignName = aeriq_personal_campaign($user);
            $campaign = aeriq_one('SELECT id FROM campanas WHERE organizacion_id=? AND nombre=? AND activa=1 AND UTC_DATE() BETWEEN inicio AND fin ORDER BY id LIMIT 1 FOR UPDATE', [$org,$campaignName]);
            if (!$campaign) {
                // Sin fila en campana_umbrales: usa los umbrales de referencia del ozono.
                aeriq_sql("INSERT INTO campanas(organizacion_id,zona_id,nombre,inicio,fin,activa,publicar) VALUES(?,?,?,UTC_DATE(),DATE_ADD(UTC_DATE(),INTERVAL 1 YEAR),1,0)", [$org,aeriq_zone($org,'Captura personal'),$campaignName]);
                $campaign = ['id'=>(int)$db->insert_id];
            }
            aeriq_sql('INSERT INTO asignaciones(organizacion_id,dispositivo_id,trabajador_id,campana_id) VALUES(?,?,?,?)', [$org,$id,$user,(int)$campaign['id']]);
        }
        $rows = aeriq_assignments($a);
        $db->commit();
        return ['ok'=>true,'message'=>'Sensor asignado a tu cuenta.','assignments'=>$rows];
    } catch (Throwable $e) {
        $db->rollback();
        throw $e;
    }
}
