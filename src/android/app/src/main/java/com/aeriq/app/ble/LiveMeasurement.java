package com.aeriq.app.ble;
/*
 * Fichero: LiveMeasurement.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ble.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
public final class LiveMeasurement {
    public final String address;
    public final BeaconReading reading;
    public final int rssi;
    public final long receivedAtMillis;



    LiveMeasurement(String address, BeaconReading reading, int rssi, long receivedAtMillis) {
        this.address = address;
        this.reading = reading;
        this.rssi = rssi;
        this.receivedAtMillis = receivedAtMillis;
    }
}
