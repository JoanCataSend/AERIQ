<?php

/*
 * Archivo: Logica.php
 * Descripción: lógica de negocio del servidor PBIO. Valida entradas, accede a
 *              MariaDB y devuelve datos de dominio sin depender de ninguna capa de transporte ni de la GUI.
 * Copyright: 2026 Joan Catala Sendra (uso académico PBIO - UPV)
 * Fecha: 2026-10-13
 * Autor: Joan Catala Sendra
 * Aportación: adaptación de la lógica a la persistencia simplificada de Sprint 0.
 */

declare(strict_types=1);

/*
 * --------------------
 * Diseño lógico: conexionBD() --> conexion: ConexionBD
 * Descripción: crea una única conexión MariaDB usando la configuración local.
 * --------------------
 */
function conexionBD(): mysqli
{
    static $conexion = null;

    if ($conexion instanceof mysqli) {
        return $conexion;
    }

    $rutaConfig = __DIR__ . '/SDBaseDatos.php';

    if (!is_file($rutaConfig)) {
        throw new RuntimeException(
            'Falta SDBaseDatos.php; copie SDBaseDatos.example.php y configure credenciales locales'
        );
    }

    /** @var array{host:string,user:string,password:string,database:string,port:int} $config */
    $config = require $rutaConfig;

    mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);

    $conexion = new mysqli(
        $config['host'],
        $config['user'],
        $config['password'],
        $config['database'],
        (int)$config['port']
    );

    $conexion->set_charset('utf8mb4');
    $conexion->query("SET time_zone = '+00:00'");

    return $conexion;
}
