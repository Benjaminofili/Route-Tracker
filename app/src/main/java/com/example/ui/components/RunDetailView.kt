package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompletedRun
import com.example.util.GeoUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunDetailView(
    run: CompletedRun,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d, yyyy • h:mm a", Locale.getDefault()) }
    val runPoints = remember(run.polyline) { GeoUtils.decodePolyline(run.polyline) }

    val initialPos = remember(runPoints) {
        val first = runPoints.firstOrNull()?.toLatLng() ?: com.google.android.gms.maps.model.LatLng(37.7694, -122.4862)
        CameraPosition.fromLatLngZoom(first, 14f)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = initialPos
    }

    LaunchedEffect(runPoints) {
        if (runPoints.size > 1) {
            val builder = LatLngBounds.builder()
            for (p in runPoints) {
                builder.include(p.toLatLng())
            }
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 80),
                    durationMs = 600
                )
            } catch (e: Exception) {
                // Map layout fallback
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag("run_detail_screen"),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Run Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("run_detail_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onDelete(run.id)
                            onBack()
                        },
                        modifier = Modifier.testTag("delete_run_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete run", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Interactive Map Visualizer
            var isDarkMap by remember { mutableStateOf(false) }
            val mapProperties = remember(isDarkMap) {
                MapProperties(
                    isMyLocationEnabled = false,
                    mapStyleOptions = if (isDarkMap) MapStyles.darkStyleOptions else null
                )
            }
            val polylineColor = if (isDarkMap) Color(0xFF00E676) else Color(0xFF00897B)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = mapProperties,
                    uiSettings = remember {
                        MapUiSettings(
                            zoomControlsEnabled = false,
                            myLocationButtonEnabled = false,
                            compassEnabled = true
                        )
                    }
                ) {
                    if (runPoints.isNotEmpty()) {
                        Polyline(
                            points = runPoints.map { it.toLatLng() },
                            color = polylineColor,
                            width = 14f
                        )

                        Marker(
                            state = MarkerState(position = runPoints.first().toLatLng()),
                            title = "Start",
                            snippet = "Run start point",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )

                        Marker(
                            state = MarkerState(position = runPoints.last().toLatLng()),
                            title = "Finish",
                            snippet = "Run finish point",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { isDarkMap = !isDarkMap },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(40.dp)
                        .testTag("detail_toggle_dark_map"),
                    containerColor = if (isDarkMap) Color(0xFF2C3848) else MaterialTheme.colorScheme.surface,
                    contentColor = if (isDarkMap) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Icon(
                        if (isDarkMap) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Dark Map",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Stats and Details Body
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = dateFormat.format(Date(run.timestamp)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.2f km", run.distanceKm),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text(run.difficulty) }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(run.routeType) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4 Grid Metric Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        icon = Icons.Default.Timer,
                        label = "Duration",
                        value = GeoUtils.formatDuration(run.durationSeconds),
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        icon = Icons.Default.Speed,
                        label = "Avg Pace",
                        value = GeoUtils.formatPace(run.avgPaceSecondsPerKm),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        icon = Icons.Default.Landscape,
                        label = "Elevation Gain",
                        value = "+${run.elevationGainMeters} m",
                        modifier = Modifier.weight(1f)
                    )
                    // Estimated Calories (approx 65 kcal per km for 70kg runner)
                    val estCalories = (run.distanceKm * 65).toInt()
                    MetricCard(
                        icon = Icons.Default.FitnessCenter,
                        label = "Est. Calories",
                        value = "$estCalories kcal",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Path Elevation Profile
                if (run.elevationProfile.isNotEmpty()) {
                    ElevationProfileChart(
                        elevationProfile = run.elevationProfile,
                        gainMeters = run.elevationGainMeters,
                        lossMeters = run.elevationLossMeters
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // GPS Data Points info
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filtered GPS Coordinates:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${run.pointsCount} points",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
