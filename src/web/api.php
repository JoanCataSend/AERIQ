<?php

/*
 * Archivo: api.php
 * Descripción: adaptador HTTP/JSON entre clientes y la lógica de negocio PBIO.
 * Copyright: 2026 Joan Catala Sendra (uso académico PBIO - UPV)
 * Fecha: 2026-10-13
 * Autor: Joan Catala Sendra
 * Aportación: adapta los filtros al identificador textual simplificado del dispositivo.
 */

declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

/*
 * En el repositorio Logica.php está en ../business_logic/.
 * En Plesk se despliega junto a api.php dentro de ./server/.
 */
$rutasLogica = [
    __DIR__ . '/server/Logica.php',
    dirname(__DIR__) . '/business_logic/Logica.php'
];

$rutaLogica = null;

foreach ($rutasLogica as $candidata) {
    if (is_file($candidata)) {
        $rutaLogica = $candidata;
        break;
    }
}

if ($rutaLogica === null) {
    http_response_code(500);
    echo json_encode([
        'error' => 'Lógica de negocio no disponible'
    ]);
    exit;
}

require_once $rutaLogica;

/*
 * --------------------
 * Diseño lógico: codigo: N, datos: Text --> responder()
 * Descripción: establece el estado HTTP, serializa JSON y finaliza la respuesta.
 * --------------------
 */
function responder(
    int $codigo,
    mixed $datos
): void {
    http_response_code($codigo);

    echo json_encode(
        $datos,
        JSON_UNESCAPED_UNICODE
        | JSON_UNESCAPED_SLASHES
    );

    exit;
}

try {
    $metodo =
        $_SERVER['REQUEST_METHOD']
        ?? 'GET';

    if ($metodo === 'POST') {
        $texto =
            file_get_contents(
                'php://input'
            );

        $datos =
            json_decode(
                $texto ?: '',
                true
            );

        if (!is_array($datos)) {
            throw new InvalidArgumentException(
                'El cuerpo debe ser un objeto JSON'
            );
        }

        responder(
            201,
            insertarMedida($datos)
        );
    }

    if ($metodo !== 'GET') {
        responder(
            405,
            ['error' => 'Método no permitido']
        );
    }

    $accion =
        (string)(
            $_GET['accion']
            ?? 'medidas'
        );

    switch ($accion) {
        case 'mapa-demo':
            responder(200, listarSensoresDemoMapa());

        case 'health':
            responder(
                200,
                probarConexion()
            );

        case 'dispositivos':
            responder(
                200,
                listarDispositivos()
            );

        case 'tipos':
            responder(
                200,
                listarTiposMedida()
            );

        case 'ultima':
            if (!isset(
                $_GET['dispositivoId'],
                $_GET['tipoMedidaId']
            )) {
                throw new InvalidArgumentException(
                    'Faltan dispositivoId y tipoMedidaId'
                );
            }

            if (filter_var(
                $_GET['tipoMedidaId'],
                FILTER_VALIDATE_INT
            ) === false) {
                throw new InvalidArgumentException(
                    'tipoMedidaId debe ser entero'
                );
            }

            responder(
                200,
                buscarUltimaMedida(
                    (string)$_GET['dispositivoId'],
                    (int)$_GET['tipoMedidaId']
                )
            );

        case 'medidas':
            responder(
                200,
                listarMedidas([
                    'dispositivoId' =>
                        $_GET['dispositivoId']
                        ?? '',
                    'tipoMedidaId' =>
                        $_GET['tipoMedidaId']
                        ?? '',
                    'desde' =>
                        $_GET['desde']
                        ?? '',
                    'hasta' =>
                        $_GET['hasta']
                        ?? ''
                ])
            );

        default:
            throw new InvalidArgumentException(
                'Acción REST no soportada'
            );
    }

} catch (InvalidArgumentException $e) {
    responder(
        400,
        ['error' => $e->getMessage()]
    );

} catch (DomainException $e) {
    responder(
        404,
        ['error' => $e->getMessage()]
    );

} catch (Throwable $e) {
    responder(
        500,
        ['error' => 'Error interno del servidor']
    );
}
