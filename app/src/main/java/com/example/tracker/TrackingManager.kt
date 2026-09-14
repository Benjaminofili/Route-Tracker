package com.example.tracker

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.core.content.ContextCompat
import com.example.model.CompletedRun
import com.example.model.LatLngPoint
import com.example.model.LiveRunState
import com.example.util.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object TrackingManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    private val _runState = MutableStateFlow(LiveRunState())
    val runState: StateFlow<LiveRunState> = _runState.asStateFlow()

    private var runStartTimeMillis: Long = 0L

    fun startTracking(context: Context) {
        if (_runState.value.isTracking) return

        runStartTimeMillis = System.currentTimeMillis()
        _runState.value = LiveRunState(
            isTracking = true,
            isPaused = false,
            elapsedTimeSeconds = 0L,
            distanceMeters = 0.0,
            currentPaceSecondsPerKm = 0,
            rawPoints = emptyList(),
            simplifiedPoints = emptyList()
        )

        startTimer()

        val intent = Intent(context, RunTrackingService::class.java).apply {
            action = RunTrackingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseTracking(context: Context) {
        if (!_runState.value.isTracking || _runState.value.isPaused) return
        _runState.value = _runState.value.copy(isPaused = true)
        stopTimer()

        val intent = Intent(context, RunTrackingService::class.java).apply {
            action = RunTrackingService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeTracking(context: Context) {
        if (!_runState.value.isTracking || !_runState.value.isPaused) return
        _runState.value = _runState.value.copy(isPaused = false)
        startTimer()

        val intent = Intent(context, RunTrackingService::class.java).apply {
            action = RunTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun stopTracking(
        context: Context,
        difficulty: String = "Flat",
        routeType: String = "Parks"
    ): CompletedRun? {
        val currentState = _runState.value
        if (!currentState.isTracking) return null

        stopTimer()

        val intent = Intent(context, RunTrackingService::class.java).apply {
            action = RunTrackingService.ACTION_STOP
        }
        context.startService(intent)

        val completedRun = if (currentState.simplifiedPoints.isNotEmpty() || currentState.rawPoints.isNotEmpty()) {
            val finalPoints = if (currentState.simplifiedPoints.isNotEmpty()) {
                currentState.simplifiedPoints
            } else {
                currentState.rawPoints
            }
            val polyline = GeoUtils.encodePolyline(finalPoints)
            val diffEnum = when (difficulty.lowercase()) {
                "hilly" -> com.example.model.RouteDifficulty.HILLY
                "very hilly" -> com.example.model.RouteDifficulty.VERY_HILLY
                else -> com.example.model.RouteDifficulty.FLAT
            }
            val elevationData = com.example.data.remote.ElevationApiClient.synthesizeElevationProfile(
                finalPoints,
                diffEnum
            )

            CompletedRun(
                timestamp = if (runStartTimeMillis > 0) runStartTimeMillis else System.currentTimeMillis(),
                distanceMeters = currentState.distanceMeters,
                durationSeconds = currentState.elapsedTimeSeconds,
                avgPaceSecondsPerKm = currentState.currentPaceSecondsPerKm,
                polyline = polyline,
                pointsCount = finalPoints.size,
                elevationGainMeters = elevationData.gainMeters,
                elevationLossMeters = elevationData.lossMeters,
                elevationProfile = elevationData.profile,
                difficulty = difficulty,
                routeType = routeType
            )
        } else null

        _runState.value = LiveRunState(isTracking = false)
        return completedRun
    }

    /**
     * Process new location from GPS.
     * Brief requirements:
     * - Discard any reading where location.accuracy > 20 meters.
     * - Do NOT snap to roads; record raw points.
     * - Run Douglas-Peucker simplification pass (~5m tolerance) to strip jitter.
     * - Sum distances between remaining points.
     * - Stationary protection: stationary for 60s does not inflate distance.
     */
    fun onNewLocation(location: Location) {
        val currentState = _runState.value
        if (!currentState.isTracking || currentState.isPaused) return

        // 1. Accuracy filter (< 20m)
        if (location.accuracy > 20.0f) {
            return
        }

        val newPoint = LatLngPoint(location.latitude, location.longitude)
        val lastPoint = currentState.rawPoints.lastOrNull()

        // 2. Stationary jitter check:
        // If speed is practically zero (< 0.3 m/s) and distance from last point is < 2.5m,
        // it's stationary noise; ignore to avoid distance inflation.
        if (lastPoint != null) {
            val distFromLast = GeoUtils.distanceBetween(lastPoint, newPoint)
            if (distFromLast < 2.5 && (location.hasSpeed() && location.speed < 0.4f)) {
                return
            }
        }

        val updatedRaw = currentState.rawPoints + newPoint

        // 3. Douglas-Peucker simplification (~5m tolerance)
        val simplified = GeoUtils.simplifyPoints(updatedRaw, epsilonMeters = 5.0)

        // 4. Sum distances between simplified points
        val totalDistance = GeoUtils.calculateTotalDistance(simplified)

        // 5. Pace calculation (elapsed time / distance so far in km)
        val paceSecondsPerKm = if (totalDistance > 30.0 && currentState.elapsedTimeSeconds > 5) {
            val km = totalDistance / 1000.0
            (currentState.elapsedTimeSeconds / km).toInt()
        } else {
            0
        }

        _runState.value = currentState.copy(
            distanceMeters = totalDistance,
            currentPaceSecondsPerKm = paceSecondsPerKm,
            rawPoints = updatedRaw,
            simplifiedPoints = simplified,
            lastLocation = newPoint,
            currentAccuracy = location.accuracy,
            currentSpeedMps = location.speed
        )
    }

    /**
     * Simulation support for testing or emulator usage:
     * Advances runner along a route or simulated steps.
     */
    fun simulatePoint(latLng: LatLngPoint) {
        val currentState = _runState.value
        if (!currentState.isTracking || currentState.isPaused) return

        val loc = Location("simulation").apply {
            latitude = latLng.latitude
            longitude = latLng.longitude
            accuracy = 4.0f
            speed = 2.8f // ~10 km/h jogging speed
            time = System.currentTimeMillis()
        }
        onNewLocation(loc)
    }

    private fun startTimer() {
        stopTimer()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _runState.value
                if (current.isTracking && !current.isPaused) {
                    val updatedElapsed = current.elapsedTimeSeconds + 1
                    val updatedPace = if (current.distanceMeters > 30.0) {
                        (updatedElapsed / (current.distanceMeters / 1000.0)).toInt()
                    } else {
                        current.currentPaceSecondsPerKm
                    }
                    _runState.value = current.copy(
                        elapsedTimeSeconds = updatedElapsed,
                        currentPaceSecondsPerKm = updatedPace
                    )
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
