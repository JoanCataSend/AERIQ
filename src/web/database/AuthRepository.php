<?php
declare(strict_types=1);

require_once __DIR__.'/SqlGateway.php';

/** Lee una cuenta por su correo y añade el rol lógico requerido por autenticación. */
function aeriq_auth_user_by_email(string $email): ?array {
    return aeriq_one('SELECT u.*,r.codigo AS rol FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.email=? LIMIT 1',[$email]);
}

/** Lee una cuenta de sesión solo si está verificada o pendiente de activar por invitación. */
function aeriq_auth_session_user(int $id): ?array {
    return aeriq_one('SELECT * FROM usuarios WHERE id=? AND (verificado_en IS NOT NULL OR debe_cambiar_clave=1)',[$id]);
}

/** Registra el intento de acceso y devuelve el contador de la ventana vigente. */
function aeriq_auth_rate_attempts(string $key,string $cutoff): int {
    aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES (?,NOW(),1) ON DUPLICATE KEY UPDATE intentos=IF(ventana_en<? ,1,intentos+1), ventana_en=IF(ventana_en<? ,NOW(),ventana_en)',[$key,$cutoff,$cutoff]);
    return (int)(aeriq_one('SELECT intentos FROM limites_acceso WHERE clave_hash=?',[$key])['intentos']??0);
}

/** Lee el instante de envío más reciente de un código de propósito determinado. */
function aeriq_auth_code_sent_at(int $userId,string $purpose): ?array {
    return aeriq_one('SELECT enviado_en FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Persiste el hash de un código después de que el transporte haya aceptado el correo. */
function aeriq_auth_store_code(int $userId,string $purpose,string $hash): void {
    aeriq_sql('INSERT INTO codigos_email(usuario_id,proposito,codigo_hash,vence_en,enviado_en,intentos) VALUES (?,?,?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW(),0) ON DUPLICATE KEY UPDATE codigo_hash=VALUES(codigo_hash),vence_en=VALUES(vence_en),enviado_en=VALUES(enviado_en),intentos=0',[$userId,$purpose,$hash]);
}

/** Lee hash, expiración y contador de intentos para validar un código. */
function aeriq_auth_code(int $userId,string $purpose): ?array {
    return aeriq_one('SELECT codigo_hash,vence_en,intentos FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Registra un intento de validación fallido o consumido según el flujo de autenticación. */
function aeriq_auth_increment_code_attempt(int $userId,string $purpose): void {
    aeriq_sql('UPDATE codigos_email SET intentos=intentos+1 WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Elimina el código cuando la autenticación lo consume correctamente. */
function aeriq_auth_delete_code(int $userId,string $purpose): void {
    aeriq_sql('DELETE FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Recupera una cuenta vinculada a la identidad Firebase indicada. */
function aeriq_auth_firebase_user(string $uid): ?array {
    return aeriq_one("SELECT u.* FROM identidades_externas i JOIN usuarios u ON u.id=i.usuario_id WHERE i.proveedor='firebase' AND i.uid_externo=? LIMIT 1",[$uid]);
}

/** Comprueba si la cuenta ya tiene una identidad Firebase asociada. */
function aeriq_auth_has_firebase_identity(int $userId): bool {
    return aeriq_one("SELECT id FROM identidades_externas WHERE usuario_id=? AND proveedor='firebase'",[$userId])!==null;
}

/** Inserta el vínculo único de proveedor e identidad externa con la cuenta invitada. */
function aeriq_auth_link_firebase_identity(int $userId,string $uid): void {
    aeriq_sql("INSERT INTO identidades_externas(usuario_id,proveedor,uid_externo) VALUES(?,'firebase',?)",[$userId,$uid]);
}

/** Lee la cuenta actual por identificador para completar el inicio de sesión externo. */
function aeriq_auth_user_by_id(int $userId): ?array {
    return aeriq_one('SELECT * FROM usuarios WHERE id=? LIMIT 1',[$userId]);
}

/** Activa una cuenta verificada por Google y reemplaza su contraseña temporal o anterior. */
function aeriq_auth_activate_google(int $userId,string $passwordHash): void {
    aeriq_sql('UPDATE usuarios SET password_hash=?,debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?',[$passwordHash,$userId]);
}

/** Invalida sesiones móviles al completar la activación mediante identidad externa. */
function aeriq_auth_delete_mobile_sessions(int $userId): void {
    aeriq_sql('DELETE FROM sesiones_moviles WHERE usuario_id=?',[$userId]);
}

/** Marca como verificada una cuenta confirmada por el proveedor de identidad. */
function aeriq_auth_mark_verified(int $userId): void {
    aeriq_sql('UPDATE usuarios SET verificado_en=UTC_TIMESTAMP() WHERE id=?',[$userId]);
}

/** Reclama atómicamente el uso de un ID token de proveedor. */
function aeriq_auth_claim_provider_token(string $key,string $expiresAt): void {
    aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES(?,?,1)',[$key,$expiresAt]);
}

/** Libera una reclamación de token cuando la autenticación no pudo completarse. */
function aeriq_auth_release_provider_token(string $key): void {
    aeriq_sql('DELETE FROM limites_acceso WHERE clave_hash=?',[$key]);
}

/** Valida un token móvil y devuelve solo los datos públicos mínimos del usuario. */
function aeriq_auth_mobile_user(string $tokenHash): ?array {
    return aeriq_one('SELECT u.id,u.nombre,u.apellidos,u.email,u.telefono FROM sesiones_moviles s JOIN usuarios u ON u.id=s.usuario_id WHERE s.token_hash=? AND s.vence_en>NOW() AND (u.verificado_en IS NOT NULL OR u.debe_cambiar_clave=1)',[$tokenHash]);
}
