package com.example.hazardmap.model

import com.google.android.gms.maps.model.LatLng

data class RouteResult(
    val polylinePoints: List<LatLng>,
    val riskScore: Float,       // 0.0 = safest, 1.0 = most dangerous
    val distanceMeters: Int,
    val durationSeconds: Int
)
