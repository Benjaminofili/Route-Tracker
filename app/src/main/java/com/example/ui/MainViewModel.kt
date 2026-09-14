package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.remote.GeminiGroundingApi
import com.example.data.repository.LocationRepository
import com.example.data.repository.MapsRepository
import com.example.data.repository.RunHistoryRepository
import com.example.model.CompletedRun
import com.example.model.LatLngPoint
import com.example.model.LiveRunState
import com.example.model.PersonalBest
import com.example.model.RouteDifficulty
import com.example.model.RouteOption
import com.example.model.RouteType
import com.example.model.ScoutRecommendation
import com.example.tracker.TrackingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val currentLocation: LatLngPoint = LatLngPoint(37.7694, -122.4862),
    val targetDistanceKm: Double = 5.0,
    val selectedDifficulty: RouteDifficulty = RouteDifficulty.FLAT,
    val selectedRouteType: RouteType = RouteType.PARKS,
    val generatedRoutes: List<RouteOption> = emptyList(),
    val selectedRoute: RouteOption? = null,
    val isGeneratingRoutes: Boolean = false,
    val errorMessage: String? = null,
    val scoutInsights: List<ScoutRecommendation> = emptyList(),
    val isLoadingScout: Boolean = false,
    val showScoutSheet: Boolean = false,
    val showHistorySheet: Boolean = false,
    val showRunHistoryScreen: Boolean = false,
    val isDarkMapEnabled: Boolean = false,
    val lastFinishedRun: CompletedRun? = null,
    val viewingRunRecord: CompletedRun? = null,
    val showBackgroundRationale: Boolean = false
)

class MainViewModel(
    application: Application,
    private val mapsRepository: MapsRepository,
    private val locationRepository: LocationRepository,
    private val runHistoryRepository: RunHistoryRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val liveRunState: StateFlow<LiveRunState> = TrackingManager.runState

    val savedRoutes: StateFlow<List<RouteOption>> = mapsRepository.savedRoutesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val completedRuns: StateFlow<List<CompletedRun>> = runHistoryRepository.allRuns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val personalBests: StateFlow<List<PersonalBest>> = completedRuns.map { runs ->
        runHistoryRepository.computePersonalBests(runs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshCurrentLocation()
    }

    fun refreshCurrentLocation() {
        viewModelScope.launch {
            val loc = locationRepository.getCurrentLocation()
            _uiState.value = _uiState.value.copy(currentLocation = loc)
        }
    }

    fun setTargetDistance(distanceKm: Double) {
        _uiState.value = _uiState.value.copy(targetDistanceKm = distanceKm)
    }

    fun setDifficulty(difficulty: RouteDifficulty) {
        _uiState.value = _uiState.value.copy(selectedDifficulty = difficulty)
    }

    fun setRouteType(routeType: RouteType) {
        _uiState.value = _uiState.value.copy(selectedRouteType = routeType)
    }

    fun setShowRunHistoryScreen(show: Boolean) {
        _uiState.value = _uiState.value.copy(showRunHistoryScreen = show)
    }

    fun generateRoutes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGeneratingRoutes = true,
                errorMessage = null,
                viewingRunRecord = null
            )
            try {
                val center = _uiState.value.currentLocation
                val targetMeters = _uiState.value.targetDistanceKm * 1000.0
                val difficulty = _uiState.value.selectedDifficulty
                val routeType = _uiState.value.selectedRouteType

                val routes = mapsRepository.generateLoopRoutes(
                    startLocation = center,
                    targetDistanceMeters = targetMeters,
                    difficulty = difficulty,
                    routeType = routeType,
                    routeCount = 3
                )

                _uiState.value = _uiState.value.copy(
                    isGeneratingRoutes = false,
                    generatedRoutes = routes,
                    selectedRoute = routes.firstOrNull()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingRoutes = false,
                    errorMessage = "Error generating routes: ${e.message}"
                )
            }
        }
    }

    fun selectRoute(route: RouteOption) {
        _uiState.value = _uiState.value.copy(
            selectedRoute = route,
            viewingRunRecord = null
        )
    }

    fun saveRoute(route: RouteOption) {
        viewModelScope.launch {
            mapsRepository.saveRoute(route)
            val updated = _uiState.value.generatedRoutes.map {
                if (it.id == route.id) it.copy(isCached = true) else it
            }
            _uiState.value = _uiState.value.copy(
                generatedRoutes = updated,
                selectedRoute = _uiState.value.selectedRoute?.takeIf { it.id != route.id } ?: route.copy(isCached = true)
            )
        }
    }

    fun deleteSavedRoute(id: String) {
        viewModelScope.launch {
            mapsRepository.deleteSavedRoute(id)
        }
    }

    fun startLiveRun(context: Context) {
        TrackingManager.startTracking(context)
    }

    fun pauseLiveRun(context: Context) {
        TrackingManager.pauseTracking(context)
    }

    fun resumeLiveRun(context: Context) {
        TrackingManager.resumeTracking(context)
    }

    fun stopLiveRun(context: Context) {
        viewModelScope.launch {
            val currentDiff = _uiState.value.selectedRoute?.difficulty?.label ?: _uiState.value.selectedDifficulty.label
            val currentType = _uiState.value.selectedRoute?.routeType?.label ?: _uiState.value.selectedRouteType.label

            val completed = TrackingManager.stopTracking(
                context = context,
                difficulty = currentDiff,
                routeType = currentType
            )
            if (completed != null && completed.distanceMeters > 5.0) {
                val id = runHistoryRepository.saveRun(completed)
                _uiState.value = _uiState.value.copy(
                    lastFinishedRun = completed.copy(id = id)
                )
            }
        }
    }

    fun dismissFinishedRunDialog() {
        _uiState.value = _uiState.value.copy(lastFinishedRun = null)
    }

    fun viewRunRecord(run: CompletedRun) {
        _uiState.value = _uiState.value.copy(
            viewingRunRecord = run
        )
    }

    fun clearViewingRecord() {
        _uiState.value = _uiState.value.copy(viewingRunRecord = null)
    }

    fun deleteCompletedRun(id: Long) {
        viewModelScope.launch {
            runHistoryRepository.deleteRun(id)
        }
    }

    fun fetchScoutInsights() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingScout = true, showScoutSheet = true)
            val insights = GeminiGroundingApi.getLocalRunningInsights(
                center = _uiState.value.currentLocation,
                distanceKm = _uiState.value.targetDistanceKm
            )
            _uiState.value = _uiState.value.copy(
                scoutInsights = insights,
                isLoadingScout = false
            )
        }
    }

    fun toggleScoutSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showScoutSheet = show)
        if (show && _uiState.value.scoutInsights.isEmpty()) {
            fetchScoutInsights()
        }
    }

    fun toggleHistorySheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHistorySheet = show)
    }

    fun setShowBackgroundRationale(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBackgroundRationale = show)
    }

    fun toggleDarkMapTheme() {
        _uiState.value = _uiState.value.copy(isDarkMapEnabled = !_uiState.value.isDarkMapEnabled)
    }

    fun setDarkMapTheme(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMapEnabled = enabled)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        TrackingManager.setAutoPauseEnabled(enabled)
    }

    fun simulateStationary() {
        TrackingManager.simulateStationary()
    }

    fun simulateNextStep() {
        val selected = _uiState.value.selectedRoute
        val state = liveRunState.value
        if (!state.isTracking) return

        if (selected != null && selected.points.isNotEmpty()) {
            val currentIndex = state.rawPoints.size % selected.points.size
            val nextPoint = selected.points[currentIndex]
            TrackingManager.simulatePoint(nextPoint)
        } else {
            val last = state.lastLocation ?: _uiState.value.currentLocation
            val next = LatLngPoint(last.latitude + 0.0002, last.longitude + 0.0002)
            TrackingManager.simulatePoint(next)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = Room.databaseBuilder(
                application,
                AppDatabase::class.java,
                "morning_run_db"
            ).fallbackToDestructiveMigration(true).build()

            val mapsRepo = MapsRepository(database)
            val locationRepo = LocationRepository(application)
            val runHistoryRepo = RunHistoryRepository(database)

            return MainViewModel(application, mapsRepo, locationRepo, runHistoryRepo) as T
        }
    }
}
