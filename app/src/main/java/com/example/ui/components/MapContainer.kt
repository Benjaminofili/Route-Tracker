package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.CompletedRun
import com.example.model.LatLngPoint
import com.example.model.LiveRunState
import com.example.model.RouteOption
import com.example.util.GeoUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapContainer(
    currentLocation: LatLngPoint,
    generatedRoutes: List<RouteOption>,
    selectedRoute: RouteOption?,
    liveRunState: LiveRunState,
    viewingRecord: CompletedRun?,
    isDarkMap: Boolean = false,
    modifier: Modifier = Modifier,
    onRouteSelected: (RouteOption) -> Unit,
    onToggleDarkMap: () -> Unit = {}
) {
    val initialCameraPosition = remember(currentLocation) {
        CameraPosition.fromLatLngZoom(currentLocation.toLatLng(), 14.5f)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = initialCameraPosition
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = true
        )
    }

    val mapProperties = remember(isDarkMap) {
        MapProperties(
            isMyLocationEnabled = false,
            mapStyleOptions = if (isDarkMap) MapStyles.darkStyleOptions else null
        )
    }

    // High-contrast color palette adaptively paired with the map theme
    val selectedRouteColor = if (isDarkMap) Color(0xFF00E5FF) else Color(0xFF1976D2)
    val unselectedRouteColor = if (isDarkMap) Color(0x99546E7A) else Color(0x77607D8B)
    val livePathColor = if (isDarkMap) Color(0xFFFF5722) else Color(0xFFFF5722)
    val completedPathColor = if (isDarkMap) Color(0xFF00E676) else Color(0xFF00897B)

    // Auto-fit camera when a route is selected or when viewing record changes
    LaunchedEffect(selectedRoute, viewingRecord) {
        val pointsToFit = when {
            viewingRecord != null -> GeoUtils.decodePolyline(viewingRecord.polyline)
            selectedRoute != null && selectedRoute.points.isNotEmpty() -> selectedRoute.points
            else -> emptyList()
        }

        if (pointsToFit.size > 1) {
            val builder = LatLngBounds.builder()
            for (p in pointsToFit) {
                builder.include(p.toLatLng())
            }
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(builder.build(), 120),
                    durationMs = 800
                )
            } catch (e: Exception) {
                val center = pointsToFit[pointsToFit.size / 2]
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(center.toLatLng(), 14f),
                    durationMs = 600
                )
            }
        }
    }

    // Follow live runner if tracking
    LaunchedEffect(liveRunState.lastLocation) {
        if (liveRunState.isTracking && liveRunState.lastLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(liveRunState.lastLocation.toLatLng()),
                durationMs = 400
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_view"),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings
        ) {
            // 1. Current Location Marker & Pulse Circle
            val circleColor = if (isDarkMap) Color(0x4400E5FF) else Color(0x332196F3)
            val strokeColor = if (isDarkMap) Color(0xFF00E5FF) else Color(0xFF2196F3)
            Circle(
                center = currentLocation.toLatLng(),
                radius = 40.0,
                fillColor = circleColor,
                strokeColor = strokeColor,
                strokeWidth = 3f
            )
            Marker(
                state = MarkerState(position = currentLocation.toLatLng()),
                title = "Current Location",
                snippet = "Start / Finish Point",
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (isDarkMap) BitmapDescriptorFactory.HUE_CYAN else BitmapDescriptorFactory.HUE_AZURE
                )
            )

            // 2. Unselected Generated Routes
            for (route in generatedRoutes) {
                if (route.id != selectedRoute?.id && route.points.isNotEmpty()) {
                    Polyline(
                        points = route.points.map { it.toLatLng() },
                        color = unselectedRouteColor,
                        width = 8f,
                        clickable = true,
                        onClick = { onRouteSelected(route) }
                    )
                }
            }

            // 3. Selected Route (Bold Primary Line) & Waypoint Pins
            if (selectedRoute != null && viewingRecord == null) {
                if (selectedRoute.points.isNotEmpty()) {
                    Polyline(
                        points = selectedRoute.points.map { it.toLatLng() },
                        color = selectedRouteColor,
                        width = 13f,
                        zIndex = 2f
                    )
                }

                selectedRoute.waypoints.forEachIndexed { index, wp ->
                    Marker(
                        state = MarkerState(position = wp.toLatLng()),
                        title = "Waypoint ${index + 1}",
                        snippet = "${selectedRoute.title} turn point",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                    )
                }
            }

            // 4. Live Tracked Run Path (Bright Coral / Orange Line)
            if (liveRunState.simplifiedPoints.isNotEmpty()) {
                Polyline(
                    points = liveRunState.simplifiedPoints.map { it.toLatLng() },
                    color = livePathColor,
                    width = 14f,
                    zIndex = 5f
                )
            } else if (liveRunState.rawPoints.isNotEmpty()) {
                Polyline(
                    points = liveRunState.rawPoints.map { it.toLatLng() },
                    color = livePathColor,
                    width = 14f,
                    zIndex = 5f
                )
            }

            // Active Runner Marker
            if (liveRunState.isTracking && liveRunState.lastLocation != null) {
                Marker(
                    state = MarkerState(position = liveRunState.lastLocation.toLatLng()),
                    title = "Runner",
                    snippet = "${GeoUtils.formatDistance(liveRunState.distanceMeters)} • ${GeoUtils.formatPace(liveRunState.currentPaceSecondsPerKm)}",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                    zIndex = 6f
                )
            }

            // 5. Viewing Completed Run Record Path
            if (viewingRecord != null) {
                val recordPoints = remember(viewingRecord.polyline) {
                    GeoUtils.decodePolyline(viewingRecord.polyline)
                }
                if (recordPoints.isNotEmpty()) {
                    Polyline(
                        points = recordPoints.map { it.toLatLng() },
                        color = completedPathColor,
                        width = 14f,
                        zIndex = 4f
                    )
                    Marker(
                        state = MarkerState(position = recordPoints.first().toLatLng()),
                        title = "Recorded Run Start",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }
            }
        }

        // Floating Map Controls (Recenter & Dark/Light Theme Toggle)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    val target = liveRunState.lastLocation ?: currentLocation
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), 15f))
                },
                modifier = Modifier.testTag("recenter_button"),
                containerColor = if (isDarkMap) Color(0xFF2C3848) else MaterialTheme.colorScheme.surface,
                contentColor = if (isDarkMap) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter Map")
            }

            FloatingActionButton(
                onClick = onToggleDarkMap,
                modifier = Modifier.testTag("toggle_dark_map_button"),
                containerColor = if (isDarkMap) Color(0xFF2C3848) else MaterialTheme.colorScheme.surface,
                contentColor = if (isDarkMap) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    if (isDarkMap) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkMap) "Switch to Light Map Theme" else "Switch to Dark Map Theme"
                )
            }
        }
    }
}
