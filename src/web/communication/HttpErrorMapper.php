<?php
/*
 * Fichero: HttpErrorMapper.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/communication.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);

/**
 * Diseño lógico: error: DomainError --> aeriq_http_status() --> estado: N
 * Descripción: Describe la operación realizada y su resultado.
 */
/**
 * Diseño lógico: error: Text --> aeriq_http_status() --> Z
 * Descripción: Describe la operación realizada y su resultado.
 */
function aeriq_http_status(Throwable $error): int {
    if (!$error instanceof DomainError) return 500;
    return match ($error->category) {
        'unauthenticated' => 401,
        'forbidden' => 403,
        'not_found' => 404,
        'method_not_allowed' => 405,
        'conflict' => 409,
        'too_large' => 413,
        'unsupported_media' => 415,
        'activation_required' => 428,
        'unavailable' => 503,
        'internal' => 500,
        default => 422,
    };
}
