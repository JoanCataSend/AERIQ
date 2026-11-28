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

  /**
   * Diseño lógico: uuid: Text, typeId: Z, counter: Z, value: Z, txPower: Z --> BeaconReading() --> Text
   * Descripción: Describe la operación realizada y su resultado.
   */
    BeaconReading(String uuid, int typeId, int counter, int value, int txPower) {
        this.uuid = uuid;
        this.typeId = typeId;
        this.counter = counter;
        this.value = value;
        this.txPower = txPower;
    }

    /**
     * Diseño lógico: unit() --> Text
     * Descripción: Procesa unit y devuelve el resultado correspondiente.
     */
    public String unit() { return typeId == IBeaconParser.O3_ID ? "ppb" : "°C"; }
    /**
     * Diseño lógico: label() --> Text
     * Descripción: Procesa label y devuelve el resultado correspondiente.
     */
    public String label() { return typeId == IBeaconParser.O3_ID ? "Ozono" : "Temperatura"; }
}
