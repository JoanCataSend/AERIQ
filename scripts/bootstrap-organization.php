<?php
/** Solo CLI: bootstrap explícito de una cuenta existente y verificada. */
declare(strict_types=1);
if (PHP_SAPI!=='cli') { http_response_code(404);exit; }
if (!in_array($argc,[4,5],true) || !in_array($argv[1],['admin','comercial'],true)) {
    fwrite(STDERR,"Uso: php bootstrap-organization.php admin|comercial correo nombre-organizacion [raiz-web]\n");exit(1);
}
$web=$argc===5?realpath($argv[4]):dirname(__DIR__).'/src/web';
if (!$web || !is_file($web.'/server/AuthCore.php')) { fwrite(STDERR,"Raíz web no válida.\n");exit(1); }
require_once $web.'/server/AuthCore.php';
$db=conexionBD();$db->begin_transaction();
try {
    $u=auth_find($argv[2]);if (!$u || !$u['verificado_en']) throw new RuntimeException('Se necesita una cuenta existente y verificada.');
    if ($u['rol']!=='visitante') throw new RuntimeException('La cuenta ya tiene una función. No se sobrescribirá.');
    aeriq_sql('INSERT INTO organizaciones(nombre) VALUES(?)',[aeriq_text($argv[3],160)]);$org=(int)$db->insert_id;
    aeriq_sql('UPDATE usuarios SET organizacion_id=?,rol_id=(SELECT id FROM roles WHERE codigo=?) WHERE id=?',[$org,$argv[1],(int)$u['id']]);$db->commit();echo "Organización y función creadas.\n";
} catch (Throwable $e) { $db->rollback();fwrite(STDERR,$e->getMessage()."\n");exit(1); }
