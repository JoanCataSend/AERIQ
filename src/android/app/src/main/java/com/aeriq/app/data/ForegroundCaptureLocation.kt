package com.aeriq.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/** Solo durante la captura en primer plano y con permiso del sistema. */
class ForegroundCaptureLocation(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var last: Location? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { last = location }
    }
    fun allowed() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        if (!allowed()) return
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { provider ->
            if (provider == LocationManager.GPS_PROVIDER && !fine) return@forEach
            runCatching { if (manager.isProviderEnabled(provider)) manager.requestLocationUpdates(provider, 3000L, 3f, listener, Looper.getMainLooper()) }
        }
    }
    fun current(): Location? = if (allowed()) last?.takeIf { System.currentTimeMillis() - it.time in 0..60000 && it.hasAccuracy() } else null
    fun stop() { runCatching { manager.removeUpdates(listener) }; last = null }
}
