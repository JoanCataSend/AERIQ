package com.aeriq.app.data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

public final class ForegroundCaptureLocation {
    private final Context context;
    private final LocationManager manager;
    private Location last;
    private final LocationListener listener = new LocationListener() {
        /**
         * Atiende el evento ubicación changed.
         * Diseño lógico: (Location location) → onLocationChanged() → void; según doc/android_design.md.
         */
        @Override public void onLocationChanged(Location location) { if (better(location, last)) last = location; }
        /**
         * Atiende el evento provider enabled.
         * Diseño lógico: (String provider) → onProviderEnabled() → void; según doc/android_design.md.
         */
        @Override public void onProviderEnabled(String provider) {}
        /**
         * Atiende el evento provider disabled.
         * Diseño lógico: (String provider) → onProviderDisabled() → void; según doc/android_design.md.
         */
        @Override public void onProviderDisabled(String provider) {}
        /**
         * Atiende el evento estado changed.
         * Diseño lógico: (String provider, int status, Bundle extras) → onStatusChanged() → void; según doc/android_design.md.
         */
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    public ForegroundCaptureLocation(Context context) {
        this.context = context;
        manager = context.getSystemService(LocationManager.class);
    }

    /**
     * Gestiona fine.
     * Diseño lógico: (sin parámetros) → fine() → boolean; según doc/android_design.md.
     */
    private boolean fine() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Gestiona allowed.
     * Diseño lógico: (sin parámetros) → allowed() → boolean; según doc/android_design.md.
     */
    public boolean allowed() {
        return fine() || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Inicia.
     * Diseño lógico: (sin parámetros) → start() → void; según doc/android_design.md.
     */
    @SuppressLint("MissingPermission")
    public void start() {
        stop();
        if (manager == null || !allowed()) return;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            if (LocationManager.GPS_PROVIDER.equals(provider) && !fine()) continue;
            try {
                if (!manager.isProviderEnabled(provider)) continue;
                Location cached = manager.getLastKnownLocation(provider);
                if (cached != null && better(cached, last)) last = cached;
                // Distancia mínima 0: con el móvil quieto también llegan posiciones nuevas. Con 3 m, una
                // persona parada dejaba de recibirlas y, pasado un minuto, las lecturas se enviaban sin GPS.
                // Cada 1 s: el Orb anuncia ozono cada ~1,5 s y cada lectura debe llevar su propia posición.
                manager.requestLocationUpdates(provider, 1000, 0, listener, Looper.getMainLooper());
            } catch (IllegalArgumentException | SecurityException ignored) {}
        }
    }

    /**
     * Red y GPS llegan mezclados: una posición de red (decenas o cientos de metros) no sustituye a una
     * de GPS más precisa y reciente. Pasados 10 s sin nada mejor, gana la más nueva.
     */
    static boolean better(Location candidate, Location current) {
        if (current == null) return true;
        long newer = candidate.getTime() - current.getTime();
        if (newer > 10_000) return true;
        if (newer < -10_000) return false;
        float a = candidate.hasAccuracy() ? candidate.getAccuracy() : Float.MAX_VALUE;
        float b = current.hasAccuracy() ? current.getAccuracy() : Float.MAX_VALUE;
        return newer >= 0 ? a <= b + 5 : a < b;
    }

    /**
     * Gestiona current.
     * Diseño lógico: (sin parámetros) → current() → Location; según doc/android_design.md.
     */
    public Location current() {
        if (!allowed() || last == null || !last.hasAccuracy()) return null;
        long age = System.currentTimeMillis() - last.getTime();
        return age >= 0 && age <= 60_000 ? last : null;
    }

    /**
     * Detiene.
     * Diseño lógico: (sin parámetros) → stop() → void; según doc/android_design.md.
     */
    public void stop() {
        if (manager != null) {
            try { manager.removeUpdates(listener); }
            catch (SecurityException ignored) { /* El permiso pudo revocarse durante la captura. */ }
        }
        last = null;
    }
}
