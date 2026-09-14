package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.HistoryBottomSheet
import com.example.ui.components.LiveTrackerPane
import com.example.ui.components.MapContainer
import com.example.ui.components.RouteGeneratorPane
import com.example.ui.components.RunSummaryDialog
import com.example.ui.components.ScoutBottomSheet
import com.example.util.GeoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveRunState by viewModel.liveRunState.collectAsStateWithLifecycle()
    val savedRoutes by viewModel.savedRoutes.collectAsStateWithLifecycle()
    val completedRuns by viewModel.completedRuns.collectAsStateWithLifecycle()
    val personalBests by viewModel.personalBests.collectAsStateWithLifecycle()

    // 1. Initial Permission Request: FINE_LOCATION and POST_NOTIFICATIONS
    val initialPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            viewModel.refreshCurrentLocation()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasBg = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasBg) {
                    viewModel.setShowBackgroundRationale(true)
                }
            }
        }
    }

    // 2. Background Location Request
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        viewModel.setShowBackgroundRationale(false)
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        initialPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }

    AnimatedContent(
        targetState = uiState.showRunHistoryScreen,
        label = "main_screen_history_navigation"
    ) { showHistory ->
        if (showHistory) {
            RunHistoryScreen(
                runs = completedRuns,
                personalBests = personalBests,
                onBack = { viewModel.setShowRunHistoryScreen(false) },
                onDeleteRun = { viewModel.deleteCompletedRun(it) }
            )
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = "Morning Run",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { viewModel.refreshCurrentLocation() },
                                modifier = Modifier.testTag("refresh_location_button")
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Refresh GPS Location")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.toggleScoutSheet(true) },
                                modifier = Modifier.testTag("open_scout_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "Local Running Scout")
                            }
                            IconButton(
                                onClick = { viewModel.setShowRunHistoryScreen(true) },
                                modifier = Modifier.testTag("open_history_button")
                            ) {
                                Icon(Icons.Default.History, contentDescription = "Run History & Personal Bests")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        )
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Map Layer
                    MapContainer(
                        currentLocation = uiState.currentLocation,
                        generatedRoutes = uiState.generatedRoutes,
                        selectedRoute = uiState.selectedRoute,
                        liveRunState = liveRunState,
                        viewingRecord = uiState.viewingRunRecord,
                        modifier = Modifier.fillMaxSize(),
                        onRouteSelected = { viewModel.selectRoute(it) }
                    )

                    // Top Status Overlay (e.g. Viewing past record banner)
                    if (uiState.viewingRunRecord != null) {
                        Surface(
                            color = Color(0xFF004D40),
                            contentColor = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(12.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Viewing Past Run Path",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${String.format("%.2f", uiState.viewingRunRecord!!.distanceKm)} km • ${GeoUtils.formatDuration(uiState.viewingRunRecord!!.durationSeconds)}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                IconButton(onClick = { viewModel.clearViewingRecord() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear viewing path", tint = Color.White)
                                }
                            }
                        }
                    }

                    // Bottom Floating / Anchored Controls
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        if (liveRunState.isTracking) {
                            LiveTrackerPane(
                                liveRunState = liveRunState,
                                onPause = { viewModel.pauseLiveRun(context) },
                                onResume = { viewModel.resumeLiveRun(context) },
                                onStop = { viewModel.stopLiveRun(context) },
                                onSimulateStep = { viewModel.simulateNextStep() }
                            )
                        } else {
                            RouteGeneratorPane(
                                targetDistanceKm = uiState.targetDistanceKm,
                                isGenerating = uiState.isGeneratingRoutes,
                                generatedRoutes = uiState.generatedRoutes,
                                selectedRoute = uiState.selectedRoute,
                                selectedDifficulty = uiState.selectedDifficulty,
                                selectedRouteType = uiState.selectedRouteType,
                                errorMessage = uiState.errorMessage,
                                onDistanceChange = { viewModel.setTargetDistance(it) },
                                onDifficultyChange = { viewModel.setDifficulty(it) },
                                onRouteTypeChange = { viewModel.setRouteType(it) },
                                onGenerateClicked = { viewModel.generateRoutes() },
                                onRouteSelected = { viewModel.selectRoute(it) },
                                onSaveRoute = { viewModel.saveRoute(it) },
                                onStartRun = { viewModel.startLiveRun(context) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheets and Dialogs

    if (uiState.showHistorySheet) {
        HistoryBottomSheet(
            savedRoutes = savedRoutes,
            completedRuns = completedRuns,
            onDismiss = { viewModel.toggleHistorySheet(false) },
            onSelectSavedRoute = { route ->
                viewModel.selectRoute(route)
                viewModel.toggleHistorySheet(false)
            },
            onDeleteSavedRoute = { viewModel.deleteSavedRoute(it) },
            onViewCompletedRun = { viewModel.viewRunRecord(it) },
            onDeleteCompletedRun = { viewModel.deleteCompletedRun(it) }
        )
    }

    if (uiState.showScoutSheet) {
        ScoutBottomSheet(
            insights = uiState.scoutInsights,
            isLoading = uiState.isLoadingScout,
            onDismiss = { viewModel.toggleScoutSheet(false) },
            onRefresh = { viewModel.fetchScoutInsights() }
        )
    }

    uiState.lastFinishedRun?.let { finishedRun ->
        RunSummaryDialog(
            run = finishedRun,
            onDismiss = { viewModel.dismissFinishedRunDialog() }
        )
    }

    // Background location rationale dialog
    if (uiState.showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowBackgroundRationale(false) },
            icon = { Icon(Icons.Default.Lock, contentDescription = null) },
            title = { Text("Enable Background Tracking") },
            text = {
                Text(
                    "Morning Run uses background location to record your distance, path, and live pace accurately even when the screen is locked or while running other apps."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            viewModel.setShowBackgroundRationale(false)
                        }
                    }
                ) {
                    Text("Allow All the Time")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowBackgroundRationale(false) }) {
                    Text("Not Now")
                }
            }
        )
    }
}
