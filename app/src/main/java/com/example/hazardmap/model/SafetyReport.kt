package com.example.hazardmap.model

import com.google.android.gms.maps.model.LatLng

data class SafetyReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ReportType,
    val description: String = "",
    val fuzzedLatLng: LatLng,
    val timestampMs: Long = System.currentTimeMillis(),
    val expiryMs: Long = when (type) {
        ReportType.UNSAFE_AREA -> Long.MAX_VALUE
        else -> System.currentTimeMillis() + 3_600_000L
    },
    val isResolved: Boolean = false
)
