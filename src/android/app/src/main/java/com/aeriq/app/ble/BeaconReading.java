package com.aeriq.app.ble;

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

    public String unit() { return typeId == IBeaconParser.O3_ID ? "ppb" : "°C"; }
    public String label() { return typeId == IBeaconParser.O3_ID ? "Ozono" : "Temperatura"; }
}
