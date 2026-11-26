<?php
declare(strict_types=1);

require_once __DIR__.'/../server/Logica.php';

/**
 * Adaptador SQL compartido: prepara consultas parametrizadas y devuelve filas asociativas.
 * Las reglas de dominio no dependen de la conexión ni de mysqli.
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

/** Ejecuta una consulta preparada y devuelve todas las filas como mapas asociativos. */
function aeriq_rows(string $sql, array $params = []): array {
    return aeriq_sql($sql,$params)->get_result()->fetch_all(MYSQLI_ASSOC);
}

/** Ejecuta una consulta preparada y devuelve su primera fila, o null si no hay resultado. */
function aeriq_one(string $sql, array $params = []): ?array {
    return aeriq_rows($sql,$params)[0] ?? null;
}

/** Inicia una transacción sobre la conexión compartida del adaptador SQL. */
function aeriq_transaction_begin(): void {
    conexionBD()->begin_transaction();
}

/** Confirma la transacción activa. */
function aeriq_transaction_commit(): void {
    conexionBD()->commit();
}

/** Revierte la transacción activa. */
function aeriq_transaction_rollback(): void {
    conexionBD()->rollback();
}

/** Devuelve el identificador generado por la última inserción en la conexión compartida. */
function aeriq_last_insert_id(): int {
    return (int)conexionBD()->insert_id;
}
