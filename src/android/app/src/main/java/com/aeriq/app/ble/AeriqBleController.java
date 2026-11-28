package com.aeriq.app.ble;
/*
 * Fichero: AeriqBleController.java
 * Autor: Codigo Norte
 * Descripción: Archivo fuente de src/android/app/src/main/java/com/aeriq/app/ble.
 * Fecha: 2026-09-25
 * Copyright (c) 2026 Codigo Norte
 */
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import java.util.*;
import java.util.function.Consumer;

@SuppressLint("MissingPermission")
public final class AeriqBleController {
    public static final class FoundDevice {
        public final String address, name;
        public final int rssi;
        public final int diagnostic;
        public final long lastSeen;

      /**
       * Diseño lógico: address: Text, name: Text, rssi: Z, lastSeen: Z, diagnostic: Z --> FoundDevice() --> Text
       * Descripción: Procesa found device y devuelve el resultado correspondiente.
       */
        FoundDevice(String address, String name, int rssi, long lastSeen, int diagnostic) {
            this.address = address;
            this.name = name;
            this.rssi = rssi;
            this.lastSeen = lastSeen;
            this.diagnostic = diagnostic;
        }
    }

    public String status = "Pulsa Buscar para detectar el sensor";
    public boolean scanning;
    public String selectedAddress;
    public long lastAdvertisementAt;
    public int sensorDiagnostic = -1;
    /**
     * Diseño lógico: context: Text, onReading: Text --> AeriqBleController() --> Text
     * Descripción: Procesa aeriq ble controller y devuelve el resultado correspondiente.
     */
    public AeriqBleController(Context context, Consumer<LiveMeasurement> onReading) {
        this.context = context;
        this.onReading = onReading;
    }

    /**
     * Diseño lógico: devices() --> [ Text ]
     * Descripción: Procesa devices y devuelve el resultado correspondiente.
     */
    public List<FoundDevice> devices() {
        List<FoundDevice> result = new ArrayList<>(devices.values());
        result.sort(Comparator.comparingLong((FoundDevice device) -> device.lastSeen).reversed());
        return result;
    }

    /**
     * Diseño lógico: requiredScanPermissions() --> Text
     * Descripción: Procesa required scan permissions y devuelve el resultado correspondiente.
     */
    public String[] requiredScanPermissions() {
        return Build.VERSION.SDK_INT >= 31
            ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
            : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    /**
     * Diseño lógico: hasScanPermissions() --> B
     * Descripción: Procesa has scan permissions y devuelve el resultado correspondiente.
     */
    public boolean hasScanPermissions() {
        for (String permission : requiredScanPermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /**
     * Diseño lógico: adapter() --> Text
     * Descripción: Procesa adapter y devuelve el resultado correspondiente.
     */
    private BluetoothAdapter adapter() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }

    /**
     * Diseño lógico: bluetoothEnabled() --> B
     * Descripción: Procesa bluetooth enabled y devuelve el resultado correspondiente.
     */
    public boolean bluetoothEnabled() {
        try {
            BluetoothAdapter adapter = adapter();
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException error) {
            return false;
        }
    }

    /**
     * Diseño lógico: startScan()
     * Descripción: Procesa start scan y devuelve el resultado correspondiente..
     */
    public void startScan() {
        if (scanning) return;
        if (!hasScanPermissions() || !bluetoothEnabled()) {
            status = "Activa Bluetooth y concede los permisos";
            return;
        }
        try {
            scanner = adapter().getBluetoothLeScanner();
            if (scanner == null) {
                status = "Escáner BLE no disponible";
                return;
            }
            ScanCallback scan = new ScanCallback() {
                /**
                 * Diseño lógico: (int callbackType, ScanResult result) --> onScanResult()
                 * Descripción: Describe la operación realizada y su resultado.
                 */
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    ScanRecord record = result.getScanRecord();
                    if (record == null || result.getDevice() == null) return;
                    byte[] data = record.getManufacturerSpecificData(IBeaconParser.APPLE_MANUFACTURER_ID);
                    BeaconReading measurement = IBeaconParser.parse(data);
                    BeaconReading status = measurement != null ? null : IBeaconParser.parseStatus(data);
                    BeaconReading reading = measurement != null ? measurement
                        : status != null ? status : IBeaconParser.parseBattery(data);
                    if (reading == null) return;
                    String address = result.getDevice().getAddress();
                    long received = System.currentTimeMillis();
                    main.post(() -> {
                        if (callback == this) accept(record, address, result.getRssi(), reading, received);
                    });
                }

                /**
                 * Diseño lógico: (int code) --> onScanFailed()
                 * Descripción: Describe la operación realizada y su resultado.
                 */
                @Override public void onScanFailed(int code) {
                    main.post(() -> {
                        if (callback != this) return;
                        callback = null;
                        scanning = false;
                        status = "Error al buscar sensores BLE: " + code;
                    });
                }
            };
            ScanFilter filter = new ScanFilter.Builder()
                .setManufacturerData(IBeaconParser.APPLE_MANUFACTURER_ID, new byte[]{2, 21}).build();
            ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            scanner.startScan(Collections.singletonList(filter), settings, scan);
            callback = scan;
            scanning = true;
            status = "Buscando sensores por BLE…";
        } catch (SecurityException | IllegalStateException error) {
            status = "No se puede iniciar el escaneo: " + error.getMessage();
        }
    }

    /**
     * Diseño lógico: record: Text, address: Text, rssi: Z, reading: Text, received: Z --> accept()
     * Descripción: Procesa accept y devuelve el resultado correspondiente..
     */
    private void accept(ScanRecord record, String address, int rssi, BeaconReading reading, long received) {
        String name = record.getDeviceName();
        int diagnostic = reading.typeId == IBeaconParser.STATUS_ID ? reading.value
            : IBeaconParser.parseDiagnostic(record.getServiceData(ParcelUuid.fromString(IBeaconParser.STATUS_UUID)), reading.counter);
        devices.put(address, new FoundDevice(address, name == null ? "Sensor AERIQ / PBIO" : name, rssi, received, diagnostic));
        if (selectedAddress == null && address.equalsIgnoreCase(preferredAddress)) {
            select(address);
            if (onAutoSelect != null) onAutoSelect.run();
        }
        if (selectedAddress == null) {
            status = reading.typeId == IBeaconParser.STATUS_ID
                ? "Sensor detectado, con error de lectura (estado 0x" + Integer.toHexString(reading.value) + "). Selecciónalo para diagnosticar."
                : "Sensor detectado. Selecciónalo para recibir mediciones.";
        }
        if (!address.equalsIgnoreCase(selectedAddress)) return;
        lastAdvertisementAt = received;
        sensorDiagnostic = diagnostic;
        if (reading.typeId == IBeaconParser.BATTERY_ID) {
            // La batería es estado del Orb: se muestra y se envía aparte, nunca como medición.
            batteryPercent = reading.value;
            batteryAt = received;
            if (onBattery != null) onBattery.accept(address, reading.value);
            return;
        }
        if (reading.typeId == IBeaconParser.STATUS_ID) {
            lastO3 = null;
            lastTemperature = null;
            counters.clear();
            status = "Sensor detectado, pero sin medición válida. Estado 0x"
                + Integer.toHexString(reading.value) + ". Revisa el monitor serie y el cableado.";
            return; // Nunca enviar banderas de diagnóstico a la API como temperatura/O3.
        }
        String key = address + "/" + reading.typeId;
        if (Objects.equals(counters.put(key, reading.counter), reading.counter)) return;
        LiveMeasurement live = new LiveMeasurement(address, reading, rssi, received);
        if (reading.typeId == IBeaconParser.O3_ID) lastO3 = live;
        else lastTemperature = live;
        status = "Recibiendo mediciones del sensor seleccionado";
        onReading.accept(live);
    }

    /**
     * Diseño lógico: stopScan()
     * Descripción: Procesa stop scan y devuelve el resultado correspondiente..
     */
    public void stopScan() {
        if (callback != null && scanner != null) {
            try { scanner.stopScan(callback); } catch (SecurityException | IllegalStateException ignored) {}
        }
        callback = null;
        scanning = false;
        status = "Escaneo detenido";
    }

    /**
     * Diseño lógico: address: Text --> select()
     * Descripción: Procesa select y devuelve el resultado correspondiente..
     */
    public void select(String address) {
        if (!devices.containsKey(address)) return;
        if (address.equalsIgnoreCase(selectedAddress)) return;
        unlink();
        selectedAddress = address;
        status = "Sensor seleccionado. Esperando nueva medición…";
    }

    /**
     * Diseño lógico: unlink()
     * Descripción: Procesa unlink y devuelve el resultado correspondiente..
     */
    public void unlink() {
        selectedAddress = null;
        lastAdvertisementAt = 0;
        sensorDiagnostic = -1;
        batteryPercent = -1;
        batteryAt = 0;
        lastO3 = null;
        lastTemperature = null;
        counters.clear();
        status = "Sensor desvinculado de esta sesión";
    }
}
