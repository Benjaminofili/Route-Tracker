package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import com.google.android.gms.maps.model.LatLng
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
    modifier: Modifier = Modifier,
    onRouteSelected: (RouteOption) -> Unit
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
    val mapProperties = remember {
        MapProperties(isMyLocationEnabled = false)
    }

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
                // If map not yet fully laid out, animate to center
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
            Circle(
                center = currentLocation.toLatLng(),
                radius = 40.0,
                fillColor = Color(0x332196F3),
                strokeColor = Color(0xFF2196F3),
                strokeWidth = 3f
            )
            Marker(
                state = MarkerState(position = currentLocation.toLatLng()),
                title = "Current Location",
                snippet = "Start / Finish Point",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )

            // 2. Unselected Generated Routes (rendered in subtle gray/blue)
            for (route in generatedRoutes) {
                if (route.id != selectedRoute?.id && route.points.isNotEmpty()) {
                    Polyline(
                        points = route.points.map { it.toLatLng() },
                        color = Color(0x77607D8B),
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
                        color = Color(0xFF1976D2),
                        width = 13f,
                        zIndex = 2f
                    )
                }

                // Intermediate Waypoint Markers
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
                    color = Color(0xFFFF5722),
                    width = 14f,
                    zIndex = 5f
                )
            } else if (liveRunState.rawPoints.isNotEmpty()) {
                Polyline(
                    points = liveRunState.rawPoints.map { it.toLatLng() },
                    color = Color(0xFFFF5722),
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
                        color = Color(0xFF00897B),
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

        // Floating Recenter Button
        FloatingActionButton(
            onClick = {
                val target = liveRunState.lastLocation ?: currentLocation
                cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target.toLatLng(), 15f))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .testTag("recenter_button"),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Recenter Map")
        }
    }
}
