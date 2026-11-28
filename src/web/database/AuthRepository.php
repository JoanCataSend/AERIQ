<?php
/*
 * Fichero: AuthRepository.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/database.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);

require_once __DIR__.'/SqlGateway.php';

/**
 * Diseño lógico: email: Text --> aeriq_auth_user_by_email() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
function aeriq_auth_user_by_email(string $email): ?array {
    return aeriq_one('SELECT u.*,r.codigo AS rol FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.email=? LIMIT 1',[$email]);
}

/**
 * Diseño lógico: id: Z --> aeriq_auth_session_user() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
function aeriq_auth_session_user(int $id): ?array {
    return aeriq_one('SELECT * FROM usuarios WHERE id=? AND (verificado_en IS NOT NULL OR debe_cambiar_clave=1)',[$id]);
}

/**
 * Diseño lógico: key: Text, cutoff: Text --> aeriq_auth_rate_attempts() --> Z
 * Descripción: Procesa aeriq auth rate attempts y devuelve el resultado correspondiente.
 */
function aeriq_auth_rate_attempts(string $key,string $cutoff): int {
    aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES (?,NOW(),1) ON DUPLICATE KEY UPDATE intentos=IF(ventana_en<? ,1,intentos+1), ventana_en=IF(ventana_en<? ,NOW(),ventana_en)',[$key,$cutoff,$cutoff]);
    return (int)(aeriq_one('SELECT intentos FROM limites_acceso WHERE clave_hash=?',[$key])['intentos']??0);
}

/**
 * Diseño lógico: userId: Z, purpose: Text --> aeriq_auth_code_sent_at() --> Text
 * Descripción: Procesa aeriq auth code sent at y devuelve el resultado correspondiente.
 */
function aeriq_auth_code_sent_at(int $userId,string $purpose): ?array {
    return aeriq_one('SELECT enviado_en FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/**
 * Diseño lógico: userId: Z, purpose: Text, hash: Text --> aeriq_auth_store_code()
 * Descripción: Procesa aeriq auth store code y devuelve el resultado correspondiente..
 */
function aeriq_auth_store_code(int $userId,string $purpose,string $hash): void {
    aeriq_sql('INSERT INTO codigos_email(usuario_id,proposito,codigo_hash,vence_en,enviado_en,intentos) VALUES (?,?,?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW(),0) ON DUPLICATE KEY UPDATE codigo_hash=VALUES(codigo_hash),vence_en=VALUES(vence_en),enviado_en=VALUES(enviado_en),intentos=0',[$userId,$purpose,$hash]);
}

/**
 * Diseño lógico: userId: Z, purpose: Text --> aeriq_auth_code() --> Text
 * Descripción: Procesa aeriq auth code y devuelve el resultado correspondiente.
 */
function aeriq_auth_code(int $userId,string $purpose): ?array {
    return aeriq_one('SELECT codigo_hash,vence_en,intentos FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/**
 * Diseño lógico: userId: Z, purpose: Text --> aeriq_auth_increment_code_attempt()
 * Descripción: Procesa aeriq auth increment code attempt y devuelve el resultado correspondiente..
 */
function aeriq_auth_increment_code_attempt(int $userId,string $purpose): void {
    aeriq_sql('UPDATE codigos_email SET intentos=intentos+1 WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/**
 * Diseño lógico: userId: Z, purpose: Text --> aeriq_auth_delete_code()
 * Descripción: Procesa aeriq auth delete code y devuelve el resultado correspondiente..
 */
function aeriq_auth_delete_code(int $userId,string $purpose): void {
    aeriq_sql('DELETE FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/**
 * Diseño lógico: uid: Text --> aeriq_auth_firebase_user() --> Text
 * Descripción: Procesa aeriq auth firebase user y devuelve el resultado correspondiente.
 */
function aeriq_auth_firebase_user(string $uid): ?array {
    return aeriq_one("SELECT u.* FROM identidades_externas i JOIN usuarios u ON u.id=i.usuario_id WHERE i.proveedor='firebase' AND i.uid_externo=? LIMIT 1",[$uid]);
}

/**
 * Diseño lógico: userId: Z --> aeriq_auth_has_firebase_identity() --> B
 * Descripción: Procesa aeriq auth has firebase identity y devuelve el resultado correspondiente.
 */
function aeriq_auth_has_firebase_identity(int $userId): bool {
    return aeriq_one("SELECT id FROM identidades_externas WHERE usuario_id=? AND proveedor='firebase'",[$userId])!==null;
}

/**
 * Diseño lógico: userId: Z, uid: Text --> aeriq_auth_link_firebase_identity()
 * Descripción: Procesa aeriq auth link firebase identity y devuelve el resultado correspondiente..
 */
function aeriq_auth_link_firebase_identity(int $userId,string $uid): void {
    aeriq_sql("INSERT INTO identidades_externas(usuario_id,proveedor,uid_externo) VALUES(?,'firebase',?)",[$userId,$uid]);
}

/**
 * Diseño lógico: userId: Z --> aeriq_auth_user_by_id() --> Text
 * Descripción: Procesa aeriq auth user by id y devuelve el resultado correspondiente.
 */
function aeriq_auth_user_by_id(int $userId): ?array {
    return aeriq_one('SELECT * FROM usuarios WHERE id=? LIMIT 1',[$userId]);
}

/**
 * Diseño lógico: userId: Z, passwordHash: Text --> aeriq_auth_activate_google()
 * Descripción: Procesa aeriq auth activate google y devuelve el resultado correspondiente..
 */
function aeriq_auth_activate_google(int $userId,string $passwordHash): void {
    aeriq_sql('UPDATE usuarios SET password_hash=?,debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?',[$passwordHash,$userId]);
}

/**
 * Diseño lógico: userId: Z --> aeriq_auth_delete_mobile_sessions()
 * Descripción: Procesa aeriq auth delete mobile sessions y devuelve el resultado correspondiente..
 */
function aeriq_auth_delete_mobile_sessions(int $userId): void {
    aeriq_sql('DELETE FROM sesiones_moviles WHERE usuario_id=?',[$userId]);
}

/**
 * Diseño lógico: userId: Z --> aeriq_auth_mark_verified()
 * Descripción: Procesa aeriq auth mark verified y devuelve el resultado correspondiente..
 */
function aeriq_auth_mark_verified(int $userId): void {
    aeriq_sql('UPDATE usuarios SET verificado_en=UTC_TIMESTAMP() WHERE id=?',[$userId]);
}

/**
 * Diseño lógico: key: Text, expiresAt: Text --> aeriq_auth_claim_provider_token()
 * Descripción: Procesa aeriq auth claim provider token y devuelve el resultado correspondiente..
 */
function aeriq_auth_claim_provider_token(string $key,string $expiresAt): void {
    aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES(?,?,1)',[$key,$expiresAt]);
}

/**
 * Diseño lógico: key: Text --> aeriq_auth_release_provider_token()
 * Descripción: Procesa aeriq auth release provider token y devuelve el resultado correspondiente..
 */
function aeriq_auth_release_provider_token(string $key): void {
    aeriq_sql('DELETE FROM limites_acceso WHERE clave_hash=?',[$key]);
}

/**
 * Diseño lógico: tokenHash: Text --> aeriq_auth_mobile_user() --> Text
 * Descripción: Procesa aeriq auth mobile user y devuelve el resultado correspondiente.
 */
function aeriq_auth_mobile_user(string $tokenHash): ?array {
    return aeriq_one('SELECT u.id,u.nombre,u.apellidos,u.email,u.telefono FROM sesiones_moviles s JOIN usuarios u ON u.id=s.usuario_id WHERE s.token_hash=? AND s.vence_en>NOW() AND (u.verificado_en IS NOT NULL OR u.debe_cambiar_clave=1)',[$tokenHash]);
}
