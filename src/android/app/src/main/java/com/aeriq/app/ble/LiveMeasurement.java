package com.aeriq.app.ble;

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
