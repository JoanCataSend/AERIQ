package com.aeriq.app.ble;
/*
 * Fichero: BeaconReading.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ble.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
public final class BeaconReading {
    public final String uuid;
    public final int typeId, counter, value, txPower;



    BeaconReading(String uuid, int typeId, int counter, int value, int txPower) {
        this.uuid = uuid;
        this.typeId = typeId;
        this.counter = counter;
        this.value = value;
        this.txPower = txPower;
    }

    /**
     * Gestiona unit.
     * Diseño lógico: (sin parámetros) → unit() → String; según doc/android_design.md.
     */
    public String unit() { return typeId == IBeaconParser.O3_ID ? "ppb" : "°C"; }
    /**
     * Gestiona label.
     * Diseño lógico: (sin parámetros) → label() → String; según doc/android_design.md.
     */
    public String label() { return typeId == IBeaconParser.O3_ID ? "Ozono" : "Temperatura"; }
}
