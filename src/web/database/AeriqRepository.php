<?php
declare(strict_types=1);

require_once __DIR__.'/SqlGateway.php';

/** Repositorio SQL de AERIQ: único punto de ejecución para consultas del servicio de aplicación. */
final class AeriqRepository {
    /** Ejecuta una escritura parametrizada del catálogo o del caso de uso. */
    public static function execute(string $query,array $params=[]): mysqli_stmt { return aeriq_sql($query,$params); }
    /** Ejecuta una lectura parametrizada y devuelve todas sus filas. */
    public static function rows(string $query,array $params=[]): array { return aeriq_rows($query,$params); }
    /** Ejecuta una lectura parametrizada y devuelve la primera fila disponible. */
    public static function one(string $query,array $params=[]): ?array { return aeriq_one($query,$params); }
    /** Identificador generado en la conexión por la última escritura. */
    public static function lastInsertId(): int { return aeriq_last_insert_id(); }
    /** Inicia la transacción de un caso de uso atómico. */
    public static function begin(): void { aeriq_transaction_begin(); }
    /** Confirma la transacción de un caso de uso atómico. */
    public static function commit(): void { aeriq_transaction_commit(); }
    /** Revierte la transacción de un caso de uso atómico. */
    public static function rollback(): void { aeriq_transaction_rollback(); }

    /** Lee una campaña o dispositivo de una organización; el nombre de tabla es una lista cerrada. */
    public static function ownedResource(string $table,int $id,int $organizationId): ?array {
        if (!in_array($table,['dispositivos','campanas'],true)) throw new LogicException('Tabla no permitida');
        return self::one(sprintf(AeriqQueryCatalog::OWNED_RESOURCE,$table),[$id,$organizationId]);
    }

    /** Recupera el historial de estados de las solicitudes dadas. */
    public static function requestHistory(array $ids): array {
        if (!$ids) return [];
        $query=sprintf(AeriqQueryCatalog::REQUEST_HISTORY,implode(',',array_fill(0,count($ids),'?')));
        return self::rows($query,$ids);
    }

    /** Recupera los objetivos asociados a las solicitudes dadas. */
    public static function requestObjectives(array $ids): array {
        if (!$ids) return [];
        $query=sprintf(AeriqQueryCatalog::REQUEST_OBJECTIVES,implode(',',array_fill(0,count($ids),'?')));
        return self::rows($query,$ids);
    }

    /** Consultas complementarias del espacio de campaña del operador. */
    public static function myCampaign(int $campaignId,int $organizationId,int $workerId): array {
        return [
            'campaign'=>self::one(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_1.AeriqQueryCatalog::UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_2.AeriqQueryCatalog::ESTADO_CAMPANA.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_3.AeriqQueryCatalog::JOIN_UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_4,[$campaignId,$organizationId]),
            'admins'=>self::rows(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_5,[$campaignId]),
            'orb'=>self::one(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_6.AeriqQueryCatalog::BATERIA.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_7,[$workerId]),
            'readings'=>self::rows(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_8,[$workerId,$campaignId]),
        ];
    }

    /** Lista asignaciones activas del usuario autenticado. */
    public static function assignments(int $organizationId,int $workerId): array {
        return self::rows(AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_1.AeriqQueryCatalog::UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_2.AeriqQueryCatalog::BATERIA.AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_3.AeriqQueryCatalog::JOIN_UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_4,[$organizationId,$workerId]);
    }

    /** Historial de mediciones autorizadas del usuario autenticado. */
    public static function workerHistory(int $organizationId,int $workerId): array {
        return self::rows(AeriqQueryCatalog::Q_AERIQ_WORKER_HISTORY_1.AeriqQueryCatalog::UMBRALES_LECTURA.AeriqQueryCatalog::Q_AERIQ_WORKER_HISTORY_2,[$organizationId,$workerId]);
    }

    /** Lecturas del panel con el ámbito de campaña aplicado en la consulta de persistencia. */
    public static function dashboard(int $organizationId,?int $campaignScope): array {
        $only=static fn(string $column)=>$campaignScope? ' AND '.$column.'='.$campaignScope:'';
        $ref=self::one(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_1);
        return [
            'workers'=>self::rows(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_2.AeriqQueryCatalog::PUEDE_MEDIR.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_3.$only(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_4).AeriqQueryCatalog::Q_AERIQ_DASHBOARD_5,[$organizationId]),
            'campaigns'=>self::rows(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_6.AeriqQueryCatalog::UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_7.AeriqQueryCatalog::CAMPANA_RESUMEN.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_8.AeriqQueryCatalog::ESTADO_CAMPANA.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_9.AeriqQueryCatalog::JOIN_UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_10.$only(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_11).AeriqQueryCatalog::Q_AERIQ_DASHBOARD_12,[$organizationId]),
            'zones'=>self::rows(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_13.($campaignScope?AeriqQueryCatalog::Q_AERIQ_DASHBOARD_14.$campaignScope.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_15:AeriqQueryCatalog::Q_AERIQ_DASHBOARD_16).AeriqQueryCatalog::Q_AERIQ_DASHBOARD_17,[$organizationId]),
            'roles'=>self::rows(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_18,[$campaignScope?'trabajador':'admin_campana']),
            'thresholds'=>['umbral_medio'=>(float)$ref['umbral_medio'],'umbral_alto'=>(float)$ref['umbral_alto']],
            'devices'=>self::rows(AeriqQueryCatalog::Q_AERIQ_DASHBOARD_19.AeriqQueryCatalog::BATERIA.AeriqQueryCatalog::Q_AERIQ_DASHBOARD_20.$only(AeriqQueryCatalog::CAMPANA_ORB).AeriqQueryCatalog::Q_AERIQ_DASHBOARD_21,[$organizationId]),
        ];
    }

    /** Ejecuta la consulta de lecturas válidas o descartadas con filtros ya validados. */
    public static function reportRows(array $where,array $params,bool $discarded=false): array {
        array_unshift($where,$discarded?AeriqQueryCatalog::REPORT_INVALID_FILTER:AeriqQueryCatalog::REPORT_VALID_FILTER);
        if ($discarded) {
            return self::rows(AeriqQueryCatalog::Q_AERIQ_DISCARDED_1.implode(AeriqQueryCatalog::Q_AERIQ_DISCARDED_2,$where).AeriqQueryCatalog::Q_AERIQ_DISCARDED_3,$params);
        }
        return self::rows(AeriqQueryCatalog::Q_AERIQ_READINGS_1.AeriqQueryCatalog::UMBRALES_LECTURA.AeriqQueryCatalog::Q_AERIQ_READINGS_2.implode(AeriqQueryCatalog::Q_AERIQ_READINGS_3,$where).AeriqQueryCatalog::Q_AERIQ_READINGS_4,$params);
    }

    /** Consulta el mapa aplicando filtros de publicación, organización, campaña, usuario y periodo. */
    public static function mapMeasurements(string $mode,int $hours,array $authorization,bool $private,int $limit): array {
        $where=[AeriqQueryCatalog::MAP_VALID_FILTER,AeriqQueryCatalog::MAP_OZONE_FILTER,AeriqQueryCatalog::MAP_LATITUDE_FILTER,AeriqQueryCatalog::MAP_LONGITUDE_FILTER,AeriqQueryCatalog::MAP_SOURCE_FILTER];
        $params=[$mode==='simulated'?1:0];
        if (!empty($authorization['organizationId'])) {
            $where[]=AeriqQueryCatalog::MAP_ORG_FILTER;$params[]=(int)$authorization['organizationId'];
            if (!empty($authorization['campaignId'])) {$where[]=AeriqQueryCatalog::MAP_CAMPAIGN_FILTER;$params[]=(int)$authorization['campaignId'];}
            if (!empty($authorization['workerId'])) {$where[]=AeriqQueryCatalog::MAP_WORKER_FILTER;$params[]=(int)$authorization['workerId'];}
        } else $where[]=AeriqQueryCatalog::MAP_PUBLISHED_FILTER;
        if ($hours) $where[]=sprintf(AeriqQueryCatalog::MAP_HOURS_FILTER,$hours);
        $fields=$private?AeriqQueryCatalog::MAP_PRIVATE_FIELDS:'';
        $join=$private?AeriqQueryCatalog::MAP_PRIVATE_JOIN:'';
        $query=AeriqQueryCatalog::Q_AERIQ_MAP_DATA_1.AeriqQueryCatalog::UMBRALES_O3.$fields.AeriqQueryCatalog::Q_AERIQ_MAP_DATA_2.AeriqQueryCatalog::JOIN_UMBRALES_O3.$join.AeriqQueryCatalog::Q_AERIQ_MAP_DATA_3.implode(AeriqQueryCatalog::Q_AERIQ_MAP_DATA_4,$where).AeriqQueryCatalog::Q_AERIQ_MAP_DATA_5.$limit;
        return self::rows($query,$params);
    }

    /** Construye predicados de lectura desde filtros de dominio ya validados. */
    public static function readingFilters(array $criteria): array {
        $where=[AeriqQueryCatalog::REPORT_ORG_FILTER,AeriqQueryCatalog::REPORT_MAGNITUDE_FILTER];
        $params=[(int)$criteria['organizationId']];
        if (!empty($criteria['campaignScope'])) {$where[]=AeriqQueryCatalog::REPORT_CAMPAIGN_FILTER;$params[]=(int)$criteria['campaignScope'];}
        if ($criteria['type']!=='all') {$where[]=AeriqQueryCatalog::REPORT_TYPE_FILTER;$params[]=(int)$criteria['type'];}
        if (!empty($criteria['campaign'])) {$where[]=AeriqQueryCatalog::REPORT_CAMPAIGN_FILTER;$params[]=(int)$criteria['campaign'];}
        if (!empty($criteria['device'])) {$where[]=AeriqQueryCatalog::REPORT_DEVICE_FILTER;$params[]=(int)$criteria['device'];}
        if (!empty($criteria['devices'])) {
            $ids=$criteria['devices'];
            $where[]=sprintf(AeriqQueryCatalog::REPORT_DEVICE_IN_FILTER,implode(',',array_fill(0,count($ids),'?')));
            array_push($params,...$ids);
        }
        if (!empty($criteria['zone'])) {$where[]=AeriqQueryCatalog::REPORT_ZONE_FILTER;$params[]=$criteria['zone'];}
        foreach (['from'=>'>=','to'=>'<='] as $key=>$operator) {
            if (!empty($criteria[$key])) {
                $where[]=sprintf(AeriqQueryCatalog::REPORT_DATE_FILTER,$operator);
                $params[]=$criteria[$key].($key==='from'?' 00:00:00.000':' 23:59:59.999');
            }
        }
        return [$where,$params];
    }

    /** Busca objetivos activos por los códigos seleccionados en el formulario comercial. */
    public static function objectivesByCode(array $codes): array {
        if (!$codes) return [];
        $query=AeriqQueryCatalog::Q_AERIQ_REQUEST_3.implode(AeriqQueryCatalog::Q_AERIQ_REQUEST_4,array_fill(0,count($codes),AeriqQueryCatalog::Q_AERIQ_REQUEST_5)).AeriqQueryCatalog::Q_AERIQ_REQUEST_6;
        return self::rows($query,$codes);
    }

    /** Comprueba que el dispositivo pertenece a la organización y campaña permitidas. */
    public static function deviceBelongsToCampaign(int $deviceId,int $organizationId,?int $campaignId): bool {
        $query=AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_1.AeriqQueryCatalog::CAMPANA_ORB.AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_2;
        return self::one($query,[$deviceId,$organizationId,$campaignId])!==null;
    }

    /** Comprueba que el trabajador es asignable a la campaña y tiene permiso de medición. */
    public static function workerEligibleForCampaign(int $workerId,int $organizationId,int $campaignId): bool {
        $query=AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_3.AeriqQueryCatalog::PUEDE_MEDIR.AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_4;
        return self::one($query,[$workerId,$organizationId,$campaignId])!==null;
    }
}
