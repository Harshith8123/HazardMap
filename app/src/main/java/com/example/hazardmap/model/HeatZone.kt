package com.example.hazardmap.model

import com.google.android.gms.maps.model.LatLng

data class HeatZone(
    val center: LatLng,
    val weight: Float,
    val radius: Double = 80.0
)
