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

/** Lee una cuenta por su correo y añade el rol lógico requerido por autenticación. */
/**
 * Diseño lógico: (string $email) → aeriq_auth_user_by_email() → ?array; según doc/database_design.md.
 */
function aeriq_auth_user_by_email(string $email): ?array {
    return aeriq_one('SELECT u.*,r.codigo AS rol FROM usuarios u JOIN roles r ON r.id=u.rol_id WHERE u.email=? LIMIT 1',[$email]);
}

/** Lee una cuenta de sesión solo si está verificada o pendiente de activar por invitación. */
/**
 * Diseño lógico: (int $id) → aeriq_auth_session_user() → ?array; según doc/database_design.md.
 */
function aeriq_auth_session_user(int $id): ?array {
    return aeriq_one('SELECT * FROM usuarios WHERE id=? AND (verificado_en IS NOT NULL OR debe_cambiar_clave=1)',[$id]);
}

/** Registra el intento de acceso y devuelve el contador de la ventana vigente. */
/**
 * Diseño lógico: (string $key,string $cutoff) → aeriq_auth_rate_attempts() → int; según doc/database_design.md.
 */
function aeriq_auth_rate_attempts(string $key,string $cutoff): int {
    aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES (?,NOW(),1) ON DUPLICATE KEY UPDATE intentos=IF(ventana_en<? ,1,intentos+1), ventana_en=IF(ventana_en<? ,NOW(),ventana_en)',[$key,$cutoff,$cutoff]);
    return (int)(aeriq_one('SELECT intentos FROM limites_acceso WHERE clave_hash=?',[$key])['intentos']??0);
}

/** Lee el instante de envío más reciente de un código de propósito determinado. */
/**
 * Diseño lógico: (int $userId,string $purpose) → aeriq_auth_code_sent_at() → ?array; según doc/database_design.md.
 */
function aeriq_auth_code_sent_at(int $userId,string $purpose): ?array {
    return aeriq_one('SELECT enviado_en FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Persiste el hash de un código después de que el transporte haya aceptado el correo. */
/**
 * Diseño lógico: (int $userId,string $purpose,string $hash) → aeriq_auth_store_code() → void; según doc/database_design.md.
 */
function aeriq_auth_store_code(int $userId,string $purpose,string $hash): void {
    aeriq_sql('INSERT INTO codigos_email(usuario_id,proposito,codigo_hash,vence_en,enviado_en,intentos) VALUES (?,?,?,DATE_ADD(NOW(),INTERVAL 10 MINUTE),NOW(),0) ON DUPLICATE KEY UPDATE codigo_hash=VALUES(codigo_hash),vence_en=VALUES(vence_en),enviado_en=VALUES(enviado_en),intentos=0',[$userId,$purpose,$hash]);
}

/** Lee hash, expiración y contador de intentos para validar un código. */
/**
 * Diseño lógico: (int $userId,string $purpose) → aeriq_auth_code() → ?array; según doc/database_design.md.
 */
function aeriq_auth_code(int $userId,string $purpose): ?array {
    return aeriq_one('SELECT codigo_hash,vence_en,intentos FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Registra un intento de validación fallido o consumido según el flujo de autenticación. */
/**
 * Diseño lógico: (int $userId,string $purpose) → aeriq_auth_increment_code_attempt() → void; según doc/database_design.md.
 */
function aeriq_auth_increment_code_attempt(int $userId,string $purpose): void {
    aeriq_sql('UPDATE codigos_email SET intentos=intentos+1 WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Elimina el código cuando la autenticación lo consume correctamente. */
/**
 * Diseño lógico: (int $userId,string $purpose) → aeriq_auth_delete_code() → void; según doc/database_design.md.
 */
function aeriq_auth_delete_code(int $userId,string $purpose): void {
    aeriq_sql('DELETE FROM codigos_email WHERE usuario_id=? AND proposito=?',[$userId,$purpose]);
}

/** Recupera una cuenta vinculada a la identidad Firebase indicada. */
/**
 * Diseño lógico: (string $uid) → aeriq_auth_firebase_user() → ?array; según doc/database_design.md.
 */
function aeriq_auth_firebase_user(string $uid): ?array {
    return aeriq_one("SELECT u.* FROM identidades_externas i JOIN usuarios u ON u.id=i.usuario_id WHERE i.proveedor='firebase' AND i.uid_externo=? LIMIT 1",[$uid]);
}

/** Comprueba si la cuenta ya tiene una identidad Firebase asociada. */
/**
 * Diseño lógico: (int $userId) → aeriq_auth_has_firebase_identity() → bool; según doc/database_design.md.
 */
function aeriq_auth_has_firebase_identity(int $userId): bool {
    return aeriq_one("SELECT id FROM identidades_externas WHERE usuario_id=? AND proveedor='firebase'",[$userId])!==null;
}

/** Inserta el vínculo único de proveedor e identidad externa con la cuenta invitada. */
/**
 * Diseño lógico: (int $userId,string $uid) → aeriq_auth_link_firebase_identity() → void; según doc/database_design.md.
 */
function aeriq_auth_link_firebase_identity(int $userId,string $uid): void {
    aeriq_sql("INSERT INTO identidades_externas(usuario_id,proveedor,uid_externo) VALUES(?,'firebase',?)",[$userId,$uid]);
}

/** Lee la cuenta actual por identificador para completar el inicio de sesión externo. */
/**
 * Diseño lógico: (int $userId) → aeriq_auth_user_by_id() → ?array; según doc/database_design.md.
 */
function aeriq_auth_user_by_id(int $userId): ?array {
    return aeriq_one('SELECT * FROM usuarios WHERE id=? LIMIT 1',[$userId]);
}

/** Activa una cuenta verificada por Google y reemplaza su contraseña temporal o anterior. */
/**
 * Diseño lógico: (int $userId,string $passwordHash) → aeriq_auth_activate_google() → void; según doc/database_design.md.
 */
function aeriq_auth_activate_google(int $userId,string $passwordHash): void {
    aeriq_sql('UPDATE usuarios SET password_hash=?,debe_cambiar_clave=0,activo=1,verificado_en=COALESCE(verificado_en,UTC_TIMESTAMP()),invitacion_expira_en=NULL WHERE id=?',[$passwordHash,$userId]);
}

/** Invalida sesiones móviles al completar la activación mediante identidad externa. */
/**
 * Diseño lógico: (int $userId) → aeriq_auth_delete_mobile_sessions() → void; según doc/database_design.md.
 */
function aeriq_auth_delete_mobile_sessions(int $userId): void {
    aeriq_sql('DELETE FROM sesiones_moviles WHERE usuario_id=?',[$userId]);
}

/** Marca como verificada una cuenta confirmada por el proveedor de identidad. */
/**
 * Diseño lógico: (int $userId) → aeriq_auth_mark_verified() → void; según doc/database_design.md.
 */
function aeriq_auth_mark_verified(int $userId): void {
    aeriq_sql('UPDATE usuarios SET verificado_en=UTC_TIMESTAMP() WHERE id=?',[$userId]);
}

/** Reclama atómicamente el uso de un ID token de proveedor. */
/**
 * Diseño lógico: (string $key,string $expiresAt) → aeriq_auth_claim_provider_token() → void; según doc/database_design.md.
 */
function aeriq_auth_claim_provider_token(string $key,string $expiresAt): void {
    aeriq_sql('INSERT INTO limites_acceso(clave_hash,ventana_en,intentos) VALUES(?,?,1)',[$key,$expiresAt]);
}

/** Libera una reclamación de token cuando la autenticación no pudo completarse. */
/**
 * Diseño lógico: (string $key) → aeriq_auth_release_provider_token() → void; según doc/database_design.md.
 */
function aeriq_auth_release_provider_token(string $key): void {
    aeriq_sql('DELETE FROM limites_acceso WHERE clave_hash=?',[$key]);
}

/** Valida un token móvil y devuelve solo los datos públicos mínimos del usuario. */
/**
 * Diseño lógico: (string $tokenHash) → aeriq_auth_mobile_user() → ?array; según doc/database_design.md.
 */
function aeriq_auth_mobile_user(string $tokenHash): ?array {
    return aeriq_one('SELECT u.id,u.nombre,u.apellidos,u.email,u.telefono FROM sesiones_moviles s JOIN usuarios u ON u.id=s.usuario_id WHERE s.token_hash=? AND s.vence_en>NOW() AND (u.verificado_en IS NOT NULL OR u.debe_cambiar_clave=1)',[$tokenHash]);
}
