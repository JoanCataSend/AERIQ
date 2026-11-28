<?php
/*
 * Fichero: SqlGateway.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/database.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);

require_once __DIR__.'/../server/Logica.php';
require_once __DIR__.'/AeriqQueryCatalog.php';

/**
 * Adaptador SQL compartido: prepara consultas parametrizadas y devuelve filas asociativas.
 * Las reglas de dominio no dependen de la conexión ni de mysqli.
 */
/**
 * Diseño lógico: sql: Text, params: Text --> aeriq_sql() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
function aeriq_sql(string $sql, array $params = []): mysqli_stmt {
    $statement=conexionBD()->prepare($sql);
    if ($params) {
        $types='';
        foreach ($params as $value) $types.=is_int($value)?'i':(is_float($value)?'d':'s');
        $statement->bind_param($types,...$params);
    }
    $statement->execute();
    return $statement;
}

/**
 * Diseño lógico: sql: Text, params: Text --> aeriq_rows() --> Text
 * Descripción: Procesa aeriq rows y devuelve el resultado correspondiente.
 */
function aeriq_rows(string $sql, array $params = []): array {
    return aeriq_sql($sql,$params)->get_result()->fetch_all(MYSQLI_ASSOC);
}

/**
 * Diseño lógico: sql: Text, params: Text --> aeriq_one() --> Text
 * Descripción: Procesa aeriq one y devuelve el resultado correspondiente.
 */
function aeriq_one(string $sql, array $params = []): ?array {
    return aeriq_rows($sql,$params)[0] ?? null;
}

/**
 * Diseño lógico: aeriq_transaction_begin()
 * Descripción: Procesa aeriq transaction begin y devuelve el resultado correspondiente..
 */
function aeriq_transaction_begin(): void {
    conexionBD()->begin_transaction();
}

/**
 * Diseño lógico: aeriq_transaction_commit()
 * Descripción: Procesa aeriq transaction commit y devuelve el resultado correspondiente..
 */
function aeriq_transaction_commit(): void {
    conexionBD()->commit();
}

/**
 * Diseño lógico: aeriq_transaction_rollback()
 * Descripción: Procesa aeriq transaction rollback y devuelve el resultado correspondiente..
 */
function aeriq_transaction_rollback(): void {
    conexionBD()->rollback();
}

/**
 * Diseño lógico: aeriq_last_insert_id() --> Z
 * Descripción: Procesa aeriq last insert id y devuelve el resultado correspondiente.
 */
function aeriq_last_insert_id(): int {
    return (int)conexionBD()->insert_id;
}
