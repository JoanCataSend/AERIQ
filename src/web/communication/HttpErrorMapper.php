<?php
declare(strict_types=1);

/** Traduce categorías del dominio al contrato de estados HTTP de la API. */
/*
 * --------------------
 * Diseño lógico: error: DomainError --> aeriq_http_status() --> estado: N
 * Descripción: asigna el estado HTTP que corresponde a la categoría semántica.
 * --------------------
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
