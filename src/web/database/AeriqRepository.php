<?php
/*
 * Fichero: AeriqRepository.php
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/web/database.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
declare(strict_types=1);

require_once __DIR__.'/SqlGateway.php';

/**
 * Diseño lógico: query: Text, params: Text --> execute() --> Text
 * Descripción: Describe la operación realizada y su resultado.
 */
    public static function execute(string $query,array $params=[]): mysqli_stmt { return aeriq_sql($query,$params); }
    /**
     * Diseño lógico: query: Text, params: Text --> rows() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    public static function rows(string $query,array $params=[]): array { return aeriq_rows($query,$params); }
    /**
     * Diseño lógico: query: Text, params: Text --> one() --> Text
     * Descripción: Describe la operación realizada y su resultado.
     */
    public static function one(string $query,array $params=[]): ?array { return aeriq_one($query,$params); }
    /**
     * Diseño lógico: lastInsertId() --> Z
     * Descripción: Procesa last insert id y devuelve el resultado correspondiente.
     */
    public static function lastInsertId(): int { return aeriq_last_insert_id(); }
    /**
     * Diseño lógico: begin()
     * Descripción: Inicializa el componente y los recursos que necesita..
     */
    public static function begin(): void { aeriq_transaction_begin(); }
    /**
     * Diseño lógico: commit()
     * Descripción: Procesa commit y devuelve el resultado correspondiente..
     */
    public static function commit(): void { aeriq_transaction_commit(); }
    /**
     * Diseño lógico: rollback()
     * Descripción: Procesa rollback y devuelve el resultado correspondiente..
     */
    public static function rollback(): void { aeriq_transaction_rollback(); }

    /**
     * Diseño lógico: table: Text, id: Z, organizationId: Z --> ownedResource() --> Text
     * Descripción: Procesa owned resource y devuelve el resultado correspondiente.
     */
    public static function ownedResource(string $table,int $id,int $organizationId): ?array {
        if (!in_array($table,['dispositivos','campanas'],true)) throw new LogicException('Tabla no permitida');
        return self::one(sprintf(AeriqQueryCatalog::OWNED_RESOURCE,$table),[$id,$organizationId]);
    }

    /**
     * Diseño lógico: ids: Text --> requestHistory() --> Text
     * Descripción: Procesa request history y devuelve el resultado correspondiente.
     */
    public static function requestHistory(array $ids): array {
        if (!$ids) return [];
        $query=sprintf(AeriqQueryCatalog::REQUEST_HISTORY,implode(',',array_fill(0,count($ids),'?')));
        return self::rows($query,$ids);
    }

    /**
     * Diseño lógico: ids: Text --> requestObjectives() --> Text
     * Descripción: Procesa request objectives y devuelve el resultado correspondiente.
     */
    public static function requestObjectives(array $ids): array {
        if (!$ids) return [];
        $query=sprintf(AeriqQueryCatalog::REQUEST_OBJECTIVES,implode(',',array_fill(0,count($ids),'?')));
        return self::rows($query,$ids);
    }

    /**
     * Diseño lógico: campaignId: Z, organizationId: Z, workerId: Z --> myCampaign() --> Text
     * Descripción: Procesa my campaign y devuelve el resultado correspondiente.
     */
    public static function myCampaign(int $campaignId,int $organizationId,int $workerId): array {
        return [
            'campaign'=>self::one(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_1.AeriqQueryCatalog::UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_2.AeriqQueryCatalog::ESTADO_CAMPANA.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_3.AeriqQueryCatalog::JOIN_UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_4,[$campaignId,$organizationId]),
            'admins'=>self::rows(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_5,[$campaignId]),
            'orb'=>self::one(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_6.AeriqQueryCatalog::BATERIA.AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_7,[$workerId]),
            'readings'=>self::rows(AeriqQueryCatalog::Q_AERIQ_MY_CAMPAIGN_8,[$workerId,$campaignId]),
        ];
    }

    /**
     * Diseño lógico: organizationId: Z, workerId: Z --> assignments() --> Text
     * Descripción: Procesa assignments y devuelve el resultado correspondiente.
     */
    public static function assignments(int $organizationId,int $workerId): array {
        return self::rows(AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_1.AeriqQueryCatalog::UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_2.AeriqQueryCatalog::BATERIA.AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_3.AeriqQueryCatalog::JOIN_UMBRALES_O3.AeriqQueryCatalog::Q_AERIQ_ASSIGNMENTS_4,[$organizationId,$workerId]);
    }

    /**
     * Diseño lógico: organizationId: Z, workerId: Z --> workerHistory() --> Text
     * Descripción: Procesa worker history y devuelve el resultado correspondiente.
     */
    public static function workerHistory(int $organizationId,int $workerId): array {
        return self::rows(AeriqQueryCatalog::Q_AERIQ_WORKER_HISTORY_1.AeriqQueryCatalog::UMBRALES_LECTURA.AeriqQueryCatalog::Q_AERIQ_WORKER_HISTORY_2,[$organizationId,$workerId]);
    }

    /**
     * Diseño lógico: organizationId: Z, campaignScope: Z --> dashboard() --> Text
     * Descripción: Procesa dashboard y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: where: Text, params: Text, discarded: B --> reportRows() --> Text
     * Descripción: Procesa report rows y devuelve el resultado correspondiente.
     */
    public static function reportRows(array $where,array $params,bool $discarded=false): array {
        array_unshift($where,$discarded?AeriqQueryCatalog::REPORT_INVALID_FILTER:AeriqQueryCatalog::REPORT_VALID_FILTER);
        if ($discarded) {
            return self::rows(AeriqQueryCatalog::Q_AERIQ_DISCARDED_1.implode(AeriqQueryCatalog::Q_AERIQ_DISCARDED_2,$where).AeriqQueryCatalog::Q_AERIQ_DISCARDED_3,$params);
        }
        return self::rows(AeriqQueryCatalog::Q_AERIQ_READINGS_1.AeriqQueryCatalog::UMBRALES_LECTURA.AeriqQueryCatalog::Q_AERIQ_READINGS_2.implode(AeriqQueryCatalog::Q_AERIQ_READINGS_3,$where).AeriqQueryCatalog::Q_AERIQ_READINGS_4,$params);
    }

    /**
     * Diseño lógico: mode: Text, hours: Z, authorization: Text, private: B, limit: Z --> mapMeasurements() --> Text
     * Descripción: Procesa map measurements y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: criteria: Text --> readingFilters() --> Text
     * Descripción: Procesa reading filters y devuelve el resultado correspondiente.
     */
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

    /**
     * Diseño lógico: codes: Text --> objectivesByCode() --> Text
     * Descripción: Procesa objectives by code y devuelve el resultado correspondiente.
     */
    public static function objectivesByCode(array $codes): array {
        if (!$codes) return [];
        $query=AeriqQueryCatalog::Q_AERIQ_REQUEST_3.implode(AeriqQueryCatalog::Q_AERIQ_REQUEST_4,array_fill(0,count($codes),AeriqQueryCatalog::Q_AERIQ_REQUEST_5)).AeriqQueryCatalog::Q_AERIQ_REQUEST_6;
        return self::rows($query,$codes);
    }

    /**
     * Diseño lógico: deviceId: Z, organizationId: Z, campaignId: Z --> deviceBelongsToCampaign() --> B
     * Descripción: Procesa device belongs to campaign y devuelve el resultado correspondiente.
     */
    public static function deviceBelongsToCampaign(int $deviceId,int $organizationId,?int $campaignId): bool {
        $query=AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_1.AeriqQueryCatalog::CAMPANA_ORB.AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_2;
        return self::one($query,[$deviceId,$organizationId,$campaignId])!==null;
    }

    /**
     * Diseño lógico: workerId: Z, organizationId: Z, campaignId: Z --> workerEligibleForCampaign() --> B
     * Descripción: Procesa worker eligible for campaign y devuelve el resultado correspondiente.
     */
    public static function workerEligibleForCampaign(int $workerId,int $organizationId,int $campaignId): bool {
        $query=AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_3.AeriqQueryCatalog::PUEDE_MEDIR.AeriqQueryCatalog::Q_AERIQ_DEVICE_SAVE_4;
        return self::one($query,[$workerId,$organizationId,$campaignId])!==null;
    }
}
