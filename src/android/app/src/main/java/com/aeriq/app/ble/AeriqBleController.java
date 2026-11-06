package com.aeriq.app.ble;

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
import java.util.*;
import java.util.function.Consumer;

@SuppressLint("MissingPermission")
public final class AeriqBleController {
    public static final class FoundDevice {
        public final String address, name;
        public final int rssi;
        public final int diagnostic;
        public final long lastSeen;

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
    public LiveMeasurement lastO3, lastTemperature;
    private final Context context;
    private final Consumer<LiveMeasurement> onReading;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, FoundDevice> devices = new HashMap<>();
    private final Map<String, Integer> counters = new HashMap<>();
    private BluetoothLeScanner scanner;
    private ScanCallback callback;

    public AeriqBleController(Context context, Consumer<LiveMeasurement> onReading) {
        this.context = context;
        this.onReading = onReading;
    }

    public List<FoundDevice> devices() {
        List<FoundDevice> result = new ArrayList<>(devices.values());
        result.sort(Comparator.comparingLong((FoundDevice device) -> device.lastSeen).reversed());
        return result;
    }

    public String[] requiredScanPermissions() {
        return Build.VERSION.SDK_INT >= 31
            ? new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
            : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public boolean hasScanPermissions() {
        for (String permission : requiredScanPermissions()) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private BluetoothAdapter adapter() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }

    public boolean bluetoothEnabled() {
        try {
            BluetoothAdapter adapter = adapter();
            return adapter != null && adapter.isEnabled();
        } catch (SecurityException error) {
            return false;
        }
    }

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
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    ScanRecord record = result.getScanRecord();
                    if (record == null || result.getDevice() == null) return;
                    byte[] data = record.getManufacturerSpecificData(IBeaconParser.APPLE_MANUFACTURER_ID);
                    BeaconReading measurement = IBeaconParser.parse(data);
                    BeaconReading reading = measurement != null ? measurement : IBeaconParser.parseStatus(data);
                    if (reading == null) return;
                    String address = result.getDevice().getAddress();
                    long received = System.currentTimeMillis();
                    main.post(() -> {
                        if (callback == this) accept(record, address, result.getRssi(), reading, received);
                    });
                }

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

    private void accept(ScanRecord record, String address, int rssi, BeaconReading reading, long received) {
        String name = record.getDeviceName();
        devices.put(address, new FoundDevice(address, name == null ? "Sensor AERIQ / PBIO" : name, rssi, received,
            reading.typeId == IBeaconParser.STATUS_ID ? reading.value : -1));
        if (selectedAddress == null) {
            status = reading.typeId == IBeaconParser.STATUS_ID
                ? "Sensor detectado, con error de lectura (estado 0x" + Integer.toHexString(reading.value) + "). Selecciónalo para diagnosticar."
                : "Sensor detectado. Selecciónalo para recibir mediciones.";
        }
        if (!address.equalsIgnoreCase(selectedAddress)) return;
        lastAdvertisementAt = received;
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

    public void stopScan() {
        if (callback != null && scanner != null) {
            try { scanner.stopScan(callback); } catch (SecurityException | IllegalStateException ignored) {}
        }
        callback = null;
        scanning = false;
        status = "Escaneo detenido";
    }

    public void select(String address) {
        if (!devices.containsKey(address)) return;
        if (address.equalsIgnoreCase(selectedAddress)) return;
        unlink();
        selectedAddress = address;
        status = "Sensor seleccionado. Esperando nueva medición…";
    }

    public void unlink() {
        selectedAddress = null;
        lastAdvertisementAt = 0;
        lastO3 = null;
        lastTemperature = null;
        counters.clear();
        status = "Sensor desvinculado de esta sesión";
    }
}
