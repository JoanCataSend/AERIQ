package com.aeriq.app.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.util.GeoPoint

internal val GANDIA_CAMPUS = GeoPoint(38.996651,-0.166009)

internal val darkTileMatrix = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
    -0.55f,-0.34f,-0.11f,0f,305f,
    -0.42f,-0.45f,-0.13f,0f,321f,
    -0.32f,-0.42f,-0.26f,0f,336f,
    0f,0f,0f,1f,0f
)))
