package com.example.model

import com.google.android.gms.maps.model.LatLng

enum class RouteDifficulty(val label: String, val description: String, val targetGainMeters: IntRange) {
    FLAT("Flat", "Gentle, level grades (< 30m gain)", 5..30),
    HILLY("Hilly", "Moderate climbs & rolling terrain (30-90m gain)", 31..90),
    VERY_HILLY("Very Hilly", "Steep ascents & intense hill repeats (> 90m gain)", 91..220)
}

enum class RouteType(val label: String, val subtitle: String) {
    PARKS("Parks", "Greenways, trails, and waterside loops"),
    URBAN("Urban", "Paved city streets, wide sidewalks, and architecture"),
    SCENIC("Scenic", "Viewpoints, ridge paths, and panoramic vistas")
}

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun toLatLng(): LatLng {
        return LatLng(latitude, longitude)
    }
}

data class RouteOption(
    val id: String,
    val title: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val polyline: String,
    val points: List<LatLngPoint>,
    val waypoints: List<LatLngPoint>,
    val elevationGainMeters: Int = 20,
    val elevationLossMeters: Int = 20,
    val elevationProfile: List<Double> = emptyList(),
    val difficulty: RouteDifficulty = RouteDifficulty.FLAT,
    val routeType: RouteType = RouteType.PARKS,
    val isCached: Boolean = false
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
}

data class LiveRunState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedTimeSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val currentPaceSecondsPerKm: Int = 0,
    val rawPoints: List<LatLngPoint> = emptyList(),
    val simplifiedPoints: List<LatLngPoint> = emptyList(),
    val lastLocation: LatLngPoint? = null,
    val currentAccuracy: Float = 0f,
    val currentSpeedMps: Float = 0f,
    val elevationGainMeters: Int = 0,
    val elevationProfile: List<Double> = emptyList()
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
}

data class CompletedRun(
    val id: Long = 0,
    val timestamp: Long,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val avgPaceSecondsPerKm: Int,
    val polyline: String,
    val pointsCount: Int,
    val elevationGainMeters: Int = 0,
    val elevationLossMeters: Int = 0,
    val elevationProfile: List<Double> = emptyList(),
    val difficulty: String = "Flat",
    val routeType: String = "Parks"
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
}

data class PersonalBest(
    val title: String,
    val value: String,
    val subtext: String,
    val iconType: String,
    val runId: Long,
    val runDate: Long
)

data class ScoutRecommendation(
    val title: String,
    val details: String,
    val amenities: List<String> = emptyList()
)
