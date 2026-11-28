<?php
/*
 * Fichero: DomainError.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/business_logic.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);

/**
 * Diseño lógico: mensaje: Text, categoria: Text, campo?: Text --> DomainError() --> error: DomainError
 * Descripción: Describe la operación realizada y su resultado.
 */
    /**
     * Diseño lógico: message: Text, category: Text, field: Text --> __construct() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    public function __construct(
        string $message,
        public readonly string $category = 'invalid_input',
        public readonly ?string $field = null
    ) {
        parent::__construct($message);
    }
}

/** Nombre histórico conservado para no romper las rutas existentes. */
class AeriqError extends DomainError {}
