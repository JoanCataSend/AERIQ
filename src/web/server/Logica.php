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

    return $conexion;
}

/*
 * --------------------
 * Diseño lógico: probarConexion() --> estado: EstadoBD
 * EstadoBD = (ok:B, database:Text)
 * Descripción: verifica que MariaDB responda y devuelve el nombre de la base activa.
 * --------------------
 */
function probarConexion(): array
{
    $bd = conexionBD();
    $resultado = $bd->query('SELECT DATABASE() AS databaseName');
    $fila = $resultado->fetch_assoc();

    return [
        'ok' => true,
        'database' => (string)($fila['databaseName'] ?? '')
    ];
}

/*
 * --------------------
 * Diseño lógico: datos: MedidaEntrada --> insertarMedida() --> medida: MedidaVista
 * Descripción: valida la entrada e inserta únicamente los campos persistidos en Sprint 0.
 * --------------------
 */
function insertarMedida(array $datos): array
{
    validarMedidaEntrada($datos);

    $bd = conexionBD();

    /*
     * El UUID BLE se reutiliza como identificador natural del dispositivo.
     * Contador y RSSI siguen llegando desde Android, pero no se persisten.
     */
    $dispositivoId = trim((string)$datos['uuid']);
    $tipoMedidaId = (int)$datos['tipoMedidaId'];
    $valor = (int)$datos['valor'];

    $stmt = $bd->prepare(
        'SELECT dispositivoId FROM Dispositivo WHERE dispositivoId = ?'
    );
    $stmt->bind_param('s', $dispositivoId);
    $stmt->execute();

    if (!$stmt->get_result()->fetch_assoc()) {
        throw new DomainException('No existe un dispositivo con ese identificador');
    }

    $stmt = $bd->prepare(
        'SELECT tipoMedidaId FROM TipoMedida WHERE tipoMedidaId = ?'
    );
    $stmt->bind_param('i', $tipoMedidaId);
    $stmt->execute();

    if (!$stmt->get_result()->fetch_assoc()) {
        throw new DomainException('No existe ese tipoMedidaId');
    }

    $stmt = $bd->prepare(
        'INSERT INTO Medida
            (dispositivoId, tipoMedidaId, valor)
         VALUES (?, ?, ?)'
    );
    $stmt->bind_param(
        'sii',
        $dispositivoId,
        $tipoMedidaId,
        $valor
    );
    $stmt->execute();

    return buscarUltimaMedida(
        $dispositivoId,
        $tipoMedidaId
    );
}

/*
 * --------------------
 * Diseño lógico: filtros: FiltrosMedida --> listarMedidas() --> medidas: [MedidaVista]
 * Descripción: lista hasta 500 medidas aplicando filtros opcionales validados.
 * --------------------
 */
function listarMedidas(array $filtros = []): array
{
    $bd = conexionBD();
    $where = [];
    $tipos = '';
    $valores = [];

    if (isset($filtros['dispositivoId']) && $filtros['dispositivoId'] !== '') {
        $dispositivoId = trim((string)$filtros['dispositivoId']);

        if (strlen($dispositivoId) !== 16) {
            throw new InvalidArgumentException(
                'dispositivoId debe contener exactamente 16 caracteres'
            );
        }

        $where[] = 'm.dispositivoId = ?';
        $tipos .= 's';
        $valores[] = $dispositivoId;
    }

    if (isset($filtros['tipoMedidaId']) && $filtros['tipoMedidaId'] !== '') {
        if (filter_var(
            $filtros['tipoMedidaId'],
            FILTER_VALIDATE_INT
        ) === false || (int)$filtros['tipoMedidaId'] <= 0) {
            throw new InvalidArgumentException(
                'tipoMedidaId debe ser un entero positivo'
            );
        }

        $where[] = 'm.tipoMedidaId = ?';
        $tipos .= 'i';
        $valores[] = (int)$filtros['tipoMedidaId'];
    }

    foreach (['desde', 'hasta'] as $campo) {
        if (empty($filtros[$campo])) {
            continue;
        }

        $fecha = (string)$filtros[$campo];

        if (!preg_match(
            '/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/',
            $fecha
        )) {
            throw new InvalidArgumentException(
                $campo . ' debe usar YYYY-MM-DD HH:MM:SS'
            );
        }

        $where[] = 'm.fechaHora '
            . ($campo === 'desde' ? '>=' : '<=')
            . ' ?';

        $tipos .= 's';
        $valores[] = $fecha;
    }

    $sql = consultaMedidaVista();

    if ($where) {
        $sql .= ' WHERE ' . implode(' AND ', $where);
    }

    $sql .= ' ORDER BY m.fechaHora DESC LIMIT 500';

    $stmt = $bd->prepare($sql);

    if ($tipos !== '') {
        $stmt->bind_param($tipos, ...$valores);
    }

    $stmt->execute();

    $resultado = $stmt->get_result();
    $medidas = [];

    while ($fila = $resultado->fetch_assoc()) {
        $medidas[] = normalizarMedidaVista($fila);
    }

    return $medidas;
}

/*
 * --------------------
 * Diseño lógico: listarDispositivos() --> dispositivos: [Dispositivo]
 * Descripción: devuelve los dispositivos conocidos por su identificador textual.
 * --------------------
 */
function listarDispositivos(): array
{
    $bd = conexionBD();

    $resultado = $bd->query(
        'SELECT dispositivoId, nombre FROM Dispositivo ORDER BY nombre'
    );

    $dispositivos = [];

    while ($fila = $resultado->fetch_assoc()) {
        $dispositivos[] = $fila;
    }

    return $dispositivos;
}

/*
 * --------------------
 * Diseño lógico: listarTiposMedida() --> tipos: [TipoMedida]
 * Descripción: devuelve los tipos de medida con identificadores normalizados a N.
 * --------------------
 */
function listarTiposMedida(): array
{
    $bd = conexionBD();

    $resultado = $bd->query(
        'SELECT tipoMedidaId, nombre, unidad FROM TipoMedida ORDER BY tipoMedidaId'
    );

    $tipos = [];

    while ($fila = $resultado->fetch_assoc()) {
        $fila['tipoMedidaId'] = (int)$fila['tipoMedidaId'];
        $tipos[] = $fila;
    }

    return $tipos;
}

/*
 * --------------------
 * Diseño lógico:
 * dispositivo_id: Text, tipo_medida_id: N --> buscarUltimaMedida() --> medida: MedidaVista
 * Descripción: devuelve la medida más reciente de un dispositivo y tipo concretos.
 * --------------------
 */
function buscarUltimaMedida(
    string $dispositivoId,
    int $tipoMedidaId
): array {
    $dispositivoId = trim($dispositivoId);

    if (strlen($dispositivoId) !== 16) {
        throw new InvalidArgumentException(
            'dispositivoId debe contener exactamente 16 caracteres'
        );
    }

    if ($tipoMedidaId <= 0) {
        throw new InvalidArgumentException(
            'tipoMedidaId debe ser positivo'
        );
    }

    $bd = conexionBD();

    $sql = consultaMedidaVista()
        . ' WHERE m.dispositivoId = ? AND m.tipoMedidaId = ?'
        . ' ORDER BY m.fechaHora DESC LIMIT 1';

    $stmt = $bd->prepare($sql);
    $stmt->bind_param(
        'si',
        $dispositivoId,
        $tipoMedidaId
    );
    $stmt->execute();

    $fila = $stmt->get_result()->fetch_assoc();

    if (!$fila) {
        throw new DomainException(
            'No hay medidas para esa combinación'
        );
    }

    return normalizarMedidaVista($fila);
}

/*
 * --------------------
 * Diseño lógico: datos: MedidaEntrada --> validarMedidaEntrada()
 * Descripción: comprueba presencia, tipos y límites impuestos por el protocolo BLE.
 * --------------------
 */
function validarMedidaEntrada(array $datos): void
{
    $campos = [
        'uuid',
        'tipoMedidaId',
        'valor',
        'contador',
        'rssi'
    ];

    foreach ($campos as $campo) {
        if (!array_key_exists($campo, $datos)) {
            throw new InvalidArgumentException(
                'Falta el campo ' . $campo
            );
        }
    }

    if (!is_string($datos['uuid'])
        || strlen(trim($datos['uuid'])) !== 16) {
        throw new InvalidArgumentException(
            'uuid debe contener exactamente 16 caracteres'
        );
    }

    foreach (
        ['tipoMedidaId', 'valor', 'contador', 'rssi']
        as $campo
    ) {
        if (filter_var(
            $datos[$campo],
            FILTER_VALIDATE_INT
        ) === false) {
            throw new InvalidArgumentException(
                $campo . ' debe ser entero'
            );
        }
    }

    $tipoMedidaId = (int)$datos['tipoMedidaId'];
    $valor = (int)$datos['valor'];
    $contador = (int)$datos['contador'];

    if ($tipoMedidaId < 0 || $tipoMedidaId > 255) {
        throw new InvalidArgumentException(
            'tipoMedidaId fuera de rango de 8 bits'
        );
    }

    if ($valor < -32768 || $valor > 65535) {
        throw new InvalidArgumentException(
            'valor fuera del rango transportable por Minor'
        );
    }

    if ($tipoMedidaId === 14 && $valor < 0) {
        throw new InvalidArgumentException(
            'El valor de O3 debe estar entre 0 y 65535'
        );
    }

    if ($tipoMedidaId === 12 && $valor > 32767) {
        throw new InvalidArgumentException(
            'La temperatura debe estar entre -32768 y 32767'
        );
    }

    if ($contador < 0 || $contador > 255) {
        throw new InvalidArgumentException(
            'contador fuera de rango'
        );
    }
}

/*
 * --------------------
 * Diseño lógico: consultaMedidaVista() --> consulta: Text
 * Descripción: construye la consulta común que une medida, dispositivo y tipo.
 * --------------------
 */
function consultaMedidaVista(): string
{
    return
        'SELECT
            d.dispositivoId,
            d.nombre AS dispositivo,
            t.tipoMedidaId,
            t.nombre AS tipoMedida,
            t.unidad,
            m.valor,
            DATE_FORMAT(m.fechaHora, "%Y-%m-%dT%H:%i:%s") AS fechaHora
         FROM Medida m
         INNER JOIN Dispositivo d
            ON d.dispositivoId = m.dispositivoId
         INNER JOIN TipoMedida t
            ON t.tipoMedidaId = m.tipoMedidaId';
}

/*
 * --------------------
 * Diseño lógico: fila: MedidaVista --> normalizarMedidaVista() --> medida: MedidaVista
 * Descripción: convierte a N/Z los campos numéricos que MariaDB entrega como texto.
 * --------------------
 */
function normalizarMedidaVista(array $fila): array
{
    foreach (
        ['tipoMedidaId', 'valor']
        as $campo
    ) {
        if (array_key_exists($campo, $fila)) {
            $fila[$campo] = (int)$fila[$campo];
        }
    }

    return $fila;
}

/** Sensores ficticios guardados en MariaDB y GEORREFERENCIADOS. No confundir con Medida real. */
function listarSensoresDemoMapa(): array {
    $res=conexionBD()->query('SELECT id,nombre,latitud,longitud,valor_ug_m3,nivel,simulado,actualizado_en FROM AeriqMapaDemo WHERE simulado=1 ORDER BY id');
    $list=[];
    while ($r=$res->fetch_assoc()) {
        $list[]=[
            'id'=>(string)$r['id'],'name'=>(string)$r['nombre'],
            'coordinates'=>[(float)$r['latitud'],(float)$r['longitud']],
            'value'=>(int)$r['valor_ug_m3'],'unit'=>'µg/m³',
            'quality'=>(string)$r['nivel'],'simulado'=>true,'updated_at'=>$r['actualizado_en'],
        ];
    }
    return $list;
}
