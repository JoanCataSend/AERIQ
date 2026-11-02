package com.aeriq.app.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** Coordenadas del campus UPV Gandía, solo como referencia de la maqueta. */
internal val GANDIA_CAMPUS = GeoPoint(38.996651, -0.166009)

/** Los sensores y valores siguientes son FICTICIOS. La ubicación del móvil sí es real si se autoriza. */
internal data class OzoneDemoSensor(
    val id: String,
    val name: String,
    val position: GeoPoint,
    val value: Int,
    val level: String,
    val argb: Int
)

internal val ozoneDemoSensors = listOf(
    OzoneDemoSensor("011", "Campus UPV Gandía", GeoPoint(38.996651, -0.166009), 58, "Bajo", AndroidColor.rgb(34,157,114)),
    OzoneDemoSensor("017", "Playa de Gandía · entorno universitario", GeoPoint(38.99802, -0.16250), 68, "Moderado", AndroidColor.rgb(239,166,45)),
    OzoneDemoSensor("024", "Camino hacia la playa", GeoPoint(39.00012, -0.16055), 82, "Moderado", AndroidColor.rgb(239,166,45)),
    OzoneDemoSensor("028", "Entorno norte del campus", GeoPoint(38.99935, -0.16742), 121, "Alto", AndroidColor.rgb(228,92,91)),
    OzoneDemoSensor("035", "Entorno sur del campus", GeoPoint(38.99472, -0.16487), 45, "Bajo", AndroidColor.rgb(34,157,114))
)

private class AeriqMapParts(
    val map: MapView,
    val circles: FolderOverlay,
    val pins: FolderOverlay,
    val sensorMarkers: Map<String, Marker>,
    val me: Marker
)

private fun dotDrawable(context: Context, color: Int, selected: Boolean): GradientDrawable {
    val density = context.resources.displayMetrics.density
    val dimension = ((if (selected) 22f else 17f) * density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke((3f * density).toInt(), AndroidColor.WHITE)
        setSize(dimension, dimension)
    }
}

private fun makeMap(context: Context, onSelect: (String) -> Unit): AeriqMapParts {
    // Una identificación específica es necesaria para el uso responsable de teselas de OSM.
    Configuration.getInstance().userAgentValue = "AERIQ-Android-academic-prototype/1.0 (${context.packageName})"
    val map = MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setMinZoomLevel(3.0)
        setMaxZoomLevel(19.0)
        controller.setZoom(16.0)
        controller.setCenter(GANDIA_CAMPUS)
        // El gesto de arrastrar corresponde al mapa y no al scroll vertical de Compose.
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }
    val circles = FolderOverlay()
    val pins = FolderOverlay()
    val markers = mutableMapOf<String, Marker>()

    ozoneDemoSensors.forEach { sensor ->
        val area = Polygon(map).apply {
            points = Polygon.pointsAsCircle(sensor.position, 130.0)
            fillColor = AndroidColor.argb(44, AndroidColor.red(sensor.argb), AndroidColor.green(sensor.argb), AndroidColor.blue(sensor.argb))
            strokeColor = AndroidColor.TRANSPARENT
            strokeWidth = 0f
        }
        circles.add(area)
        val marker = Marker(map).apply {
            position = sensor.position
            title = sensor.name
            snippet = "${sensor.value} µg/m³ · dato de demostración"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setIcon(dotDrawable(context, sensor.argb, false))
            setOnMarkerClickListener { _, _ -> onSelect(sensor.id); true }
        }
        pins.add(marker)
        markers[sensor.id] = marker
    }
    // Azul = ubicación del dispositivo, nunca un punto de medición de ozono.
    val myMarker = Marker(map).apply {
        position = GANDIA_CAMPUS
        title = "Mi ubicación actual"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setIcon(dotDrawable(context, AndroidColor.rgb(30,121,223), true))
    }
    return AeriqMapParts(map, circles, pins, markers, myMarker)
}

private val darkTileMatrix = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
    -0.55f,-0.34f,-0.11f,0f,305f,
    -0.42f,-0.45f,-0.13f,0f,321f,
    -0.32f,-0.42f,-0.26f,0f,336f,
    0f,0f,0f,1f,0f
)))

/** Mapa nativo, sin Google Maps ni API key. Los puntos coloreados son de ejemplo. */
@Composable
internal fun RealOzoneMap(
    selectedId: String,
    visibleSensors: Boolean,
    visibleZones: Boolean,
    darkMode: Boolean,
    location: GeoPoint?,
    centerMyLocation: Boolean,
    focusPoint: GeoPoint,
    focusNonce: Int,
    onSelectSensor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestSelect = rememberUpdatedState(onSelectSensor)
    val parts = remember(context) { makeMap(context) { id -> latestSelect.value(id) } }
    val map = parts.map
    val mapIsNearMe = remember(map) { booleanArrayOf(false) }
    val lastFocusNonce = remember(map) { intArrayOf(0) }

    DisposableEffect(map, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) map.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        modifier = modifier.fillMaxSize(),
        update = { currentMap ->
            // Mantenemos la pila ordenada: zonas debajo, puntos encima y ubicación al frente.
            currentMap.overlays.remove(parts.circles)
            currentMap.overlays.remove(parts.pins)
            currentMap.overlays.remove(parts.me)
            if (visibleZones) currentMap.overlays.add(parts.circles)
            if (visibleSensors) currentMap.overlays.add(parts.pins)
            if (location != null) {
                parts.me.position = location
                currentMap.overlays.add(parts.me)
                if (centerMyLocation && !mapIsNearMe[0]) {
                    currentMap.controller.animateTo(location)
                    currentMap.controller.setZoom(16.0)
                    mapIsNearMe[0] = true
                }
            }
            if (focusNonce != lastFocusNonce[0]) {
                lastFocusNonce[0] = focusNonce
                currentMap.controller.animateTo(focusPoint)
                currentMap.controller.setZoom(16.0)
            }
            ozoneDemoSensors.forEach { sensor ->
                parts.sensorMarkers[sensor.id]?.setIcon(
                    dotDrawable(context, sensor.argb, selectedId == sensor.id)
                )
            }
            currentMap.overlayManager.tilesOverlay.setColorFilter(if (darkMode) darkTileMatrix else null)
            currentMap.invalidate()
        }
    )
}
