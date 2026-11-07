<?php
declare(strict_types=1);

/** Alta/asignación propia desde Android; nunca reasigna sensores de otra persona. */
function aeriq_link_sensor(array $a, array $data): array {
    $org = aeriq_org($a);
    $user = (int)$a['usuario_id'];
    $address = strtoupper(aeriq_text($data['address'] ?? '', 17));
    if (!preg_match('/^([0-9A-F]{2}:){5}[0-9A-F]{2}$/D', $address)) {
        throw new AeriqError('Dirección del sensor no válida.');
    }
    $name = aeriq_text($data['name'] ?? 'Mi sensor Orb', 160);
    $db = conexionBD();
    $db->begin_transaction();
    try {
        // Serializa altas propias de la organización y la creación de campaña personal.
        aeriq_one('SELECT id FROM organizaciones WHERE id=? FOR UPDATE', [$org]);
        $device = aeriq_one('SELECT * FROM dispositivos WHERE direccion_ble=? FOR UPDATE', [$address]);
        if ($device && ((int)$device['organizacion_id'] !== $org || !(int)$device['activo'] || (int)$device['simulado'])) {
            throw new AeriqError('Este sensor no está disponible para tu cuenta. Consulta con tu organización.', 409);
        }
        if (!$device) {
            aeriq_sql('INSERT INTO dispositivos(organizacion_id,nombre,direccion_ble,activo,simulado) VALUES(?,?,?,1,0)', [$org,$name,$address]);
            $device = ['id'=>(int)$db->insert_id];
        }
        $id = (int)$device['id'];
        $old = aeriq_one('SELECT * FROM asignaciones WHERE dispositivo_id=? AND finalizada_en IS NULL FOR UPDATE', [$id]);
        if ($old && (int)$old['trabajador_id'] !== $user) {
            throw new AeriqError('Este sensor ya está asignado a otra persona. Tu organización debe liberarlo.', 409);
        }
        if ($old) {
            $active = aeriq_one('SELECT id FROM campanas WHERE id=? AND activa=1 AND UTC_DATE() BETWEEN inicio AND fin', [(int)$old['campana_id']]);
            if (!$active) throw new AeriqError('La campaña de este sensor ha terminado o está inactiva. Consulta con tu organización.', 409);
        } else {
            // Privado por defecto; no cambia las campañas existentes ni su publicación.
            $campaignName = 'Mis mediciones · '.$user;
            $campaign = aeriq_one('SELECT id FROM campanas WHERE organizacion_id=? AND nombre=? AND publicar=0 AND activa=1 AND UTC_DATE() BETWEEN inicio AND fin ORDER BY id LIMIT 1 FOR UPDATE', [$org,$campaignName]);
            if (!$campaign) {
                aeriq_sql("INSERT INTO campanas(organizacion_id,nombre,zona,inicio,fin,activa,publicar) VALUES(?,?,?,UTC_DATE(),DATE_ADD(UTC_DATE(),INTERVAL 1 YEAR),1,0)", [$org,$campaignName,'Captura personal']);
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
