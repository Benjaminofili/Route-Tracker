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
    private var stationaryCounter: Int = 0

    fun startTracking(context: Context) {
        if (_runState.value.isTracking) return

        runStartTimeMillis = System.currentTimeMillis()
        stationaryCounter = 0

        _runState.value = LiveRunState(
            isTracking = true,
            isPaused = false,
            isAutoPaused = false,
            isAutoPauseEnabled = _runState.value.isAutoPauseEnabled,
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
        stationaryCounter = 0
        _runState.value = _runState.value.copy(isPaused = false, isAutoPaused = false)
        startTimer()

        val intent = Intent(context, RunTrackingService::class.java).apply {
            action = RunTrackingService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        _runState.value = _runState.value.copy(
            isAutoPauseEnabled = enabled,
            isAutoPaused = if (!enabled) false else _runState.value.isAutoPaused
        )
        if (!enabled) {
            stationaryCounter = 0
        }
    }

    fun stopTracking(
        context: Context,
        difficulty: String = "Flat",
        routeType: String = "Parks"
    ): CompletedRun? {
        val currentState = _runState.value
        if (!currentState.isTracking) return null

        stopTimer()
        stationaryCounter = 0

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

        _runState.value = LiveRunState(
            isTracking = false,
            isAutoPauseEnabled = currentState.isAutoPauseEnabled
        )
        return completedRun
    }

    /**
     * Process new location from GPS.
     * Includes Auto-Pause detection:
     * - Threshold for stationary: speed < 0.6 m/s (~2.1 km/h).
     * - If stationary for consecutive updates: auto-pause the run clock and distance accumulation.
     * - If speed resumes > 0.8 m/s: auto-resumes tracking.
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
        val distFromLast = if (lastPoint != null) GeoUtils.distanceBetween(lastPoint, newPoint) else 0.0

        // 2. Auto-pause logic
        var newAutoPaused = currentState.isAutoPaused
        if (currentState.isAutoPauseEnabled) {
            val isSlowOrStopped = if (location.hasSpeed()) {
                location.speed < 0.6f
            } else {
                distFromLast < 1.5
            }

            if (isSlowOrStopped) {
                stationaryCounter++
                if (stationaryCounter >= 2) {
                    newAutoPaused = true
                }
            } else {
                // Moving (> 0.7 m/s or significant displacement)
                stationaryCounter = 0
                if (newAutoPaused) {
                    newAutoPaused = false
                }
            }
        }

        // If auto-paused, do not accumulate distance from slight GPS jitter
        if (newAutoPaused) {
            _runState.value = currentState.copy(
                isAutoPaused = true,
                currentAccuracy = location.accuracy,
                currentSpeedMps = location.speed
            )
            return
        }

        // 3. Stationary jitter suppression:
        // If speed is practically zero (< 0.3 m/s) and distance from last point is < 2.5m,
        // it's stationary noise; ignore to avoid distance inflation.
        if (lastPoint != null && distFromLast < 2.5 && (location.hasSpeed() && location.speed < 0.4f)) {
            return
        }

        val updatedRaw = currentState.rawPoints + newPoint

        // 4. Douglas-Peucker simplification (~5m tolerance)
        val simplified = GeoUtils.simplifyPoints(updatedRaw, epsilonMeters = 5.0)

        // 5. Sum distances between simplified points
        val totalDistance = GeoUtils.calculateTotalDistance(simplified)

        // 6. Pace calculation (elapsed moving time / distance so far in km)
        val paceSecondsPerKm = if (totalDistance > 30.0 && currentState.elapsedTimeSeconds > 5) {
            val km = totalDistance / 1000.0
            (currentState.elapsedTimeSeconds / km).toInt()
        } else {
            0
        }

        _runState.value = currentState.copy(
            isAutoPaused = false,
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

    /**
     * Simulates stopping at a red light / crosswalk to test Auto-Pause.
     */
    fun simulateStationary() {
        val currentState = _runState.value
        if (!currentState.isTracking || currentState.isPaused) return

        val last = currentState.lastLocation ?: LatLngPoint(37.7694, -122.4862)
        val loc = Location("simulation_stop").apply {
            latitude = last.latitude
            longitude = last.longitude
            accuracy = 4.0f
            speed = 0.0f
            time = System.currentTimeMillis()
        }
        // Send two consecutive stops to trigger stationary counter
        onNewLocation(loc)
        onNewLocation(loc)
    }

    private fun startTimer() {
        stopTimer()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _runState.value
                // Only increment moving time when tracking, not manually paused, and NOT auto-paused
                if (current.isTracking && !current.isPaused && !current.isAutoPaused) {
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
