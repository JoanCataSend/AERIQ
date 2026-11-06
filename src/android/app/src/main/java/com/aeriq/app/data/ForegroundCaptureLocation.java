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
        @Override public void onLocationChanged(Location location) { last = location; }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    public ForegroundCaptureLocation(Context context) {
        this.context = context;
        manager = context.getSystemService(LocationManager.class);
    }

    private boolean fine() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean allowed() {
        return fine() || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        stop();
        if (manager == null || !allowed()) return;
        for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER}) {
            if (LocationManager.GPS_PROVIDER.equals(provider) && !fine()) continue;
            try {
                if (!manager.isProviderEnabled(provider)) continue;
                Location cached = manager.getLastKnownLocation(provider);
                if (cached != null && (last == null || cached.getTime() > last.getTime())) last = cached;
                manager.requestLocationUpdates(provider, 3000, 3, listener, Looper.getMainLooper());
            } catch (IllegalArgumentException | SecurityException ignored) {}
        }
    }

    public Location current() {
        if (!allowed() || last == null || !last.hasAccuracy()) return null;
        long age = System.currentTimeMillis() - last.getTime();
        return age >= 0 && age <= 60_000 ? last : null;
    }

    public void stop() {
        if (manager != null) {
            try { manager.removeUpdates(listener); }
            catch (SecurityException ignored) { /* El permiso pudo revocarse durante la captura. */ }
        }
        last = null;
    }
}
