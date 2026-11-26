<?php
declare(strict_types=1);

/** Error de dominio sin estado ni dependencia del protocolo HTTP. */
class DomainError extends RuntimeException {
    /*
     * --------------------
     * Diseño lógico: mensaje: Text, categoria: Text, campo?: Text --> DomainError() --> error: DomainError
     * Descripción: crea un error de negocio sin depender de HTTP ni de la capa de comunicación.
     * --------------------
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
