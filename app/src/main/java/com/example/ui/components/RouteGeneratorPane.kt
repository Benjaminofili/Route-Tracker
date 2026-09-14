package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RouteDifficulty
import com.example.model.RouteOption
import com.example.model.RouteType
import com.example.util.GeoUtils
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RouteGeneratorPane(
    targetDistanceKm: Double,
    isGenerating: Boolean,
    generatedRoutes: List<RouteOption>,
    selectedRoute: RouteOption?,
    selectedDifficulty: RouteDifficulty,
    selectedRouteType: RouteType,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onDistanceChange: (Double) -> Unit,
    onDifficultyChange: (RouteDifficulty) -> Unit,
    onRouteTypeChange: (RouteType) -> Unit,
    onGenerateClicked: () -> Unit,
    onRouteSelected: (RouteOption) -> Unit,
    onSaveRoute: (RouteOption) -> Unit,
    onStartRun: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("route_generator_pane"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Distance Selector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target Distance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.1f", targetDistanceKm)} km",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Quick selection chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(3.0, 5.0, 8.0, 10.0, 15.0).forEach { dist ->
                    FilterChip(
                        selected = (targetDistanceKm == dist),
                        onClick = { onDistanceChange(dist) },
                        label = { Text("${dist.toInt()} km") },
                        modifier = Modifier.testTag("chip_distance_${dist.toInt()}")
                    )
                }
            }

            // Fine tuning slider
            Slider(
                value = targetDistanceKm.toFloat(),
                onValueChange = { onDistanceChange((it * 2).roundToInt() / 2.0) },
                valueRange = 1f..25f,
                steps = 47,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("distance_slider")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // ROUTE FILTERS: Difficulty
            Text(
                text = "Difficulty / Elevation",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteDifficulty.values().forEach { diff ->
                    val isDiffSelected = diff == selectedDifficulty
                    FilterChip(
                        selected = isDiffSelected,
                        onClick = { onDifficultyChange(diff) },
                        leadingIcon = {
                            val icon = when (diff) {
                                RouteDifficulty.FLAT -> Icons.Default.Landscape
                                RouteDifficulty.HILLY -> Icons.Default.TrendingUp
                                RouteDifficulty.VERY_HILLY -> Icons.Default.Terrain
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(diff.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("filter_difficulty_${diff.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ROUTE FILTERS: Type (Parks, Urban, Scenic)
            Text(
                text = "Route Type & Scenery",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteType.values().forEach { rType ->
                    val isTypeSelected = rType == selectedRouteType
                    FilterChip(
                        selected = isTypeSelected,
                        onClick = { onRouteTypeChange(rType) },
                        leadingIcon = {
                            val icon = when (rType) {
                                RouteType.PARKS -> Icons.Default.Park
                                RouteType.URBAN -> Icons.Default.LocationCity
                                RouteType.SCENIC -> Icons.Default.PhotoCamera
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = {
                            Text(rType.label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("filter_type_${rType.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Generate Button
            Button(
                onClick = onGenerateClicked,
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("generate_routes_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Calculating ${selectedDifficulty.label} ${selectedRouteType.label} Loop...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (generatedRoutes.isEmpty()) "Generate Filtered Loop Routes" else "Regenerate Route Options")
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Generated Options Carousel
            AnimatedVisibility(visible = generatedRoutes.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = "Generated Loops (${generatedRoutes.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(generatedRoutes) { route ->
                            val isSelected = route.id == selectedRoute?.id
                            val diffPercent = if (targetDistanceKm > 0) {
                                ((route.distanceKm - targetDistanceKm) / targetDistanceKm) * 100.0
                            } else 0.0

                            Card(
                                modifier = Modifier
                                    .width(240.dp)
                                    .clickable { onRouteSelected(route) }
                                    .testTag("route_option_card_${route.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                                border = if (isSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else null
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = route.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { onSaveRoute(route) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (route.isCached) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "Save route offline",
                                                tint = if (route.isCached) MaterialTheme.colorScheme.primary else Color.Gray
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${String.format("%.2f", route.distanceKm)} km",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val diffPrefix = if (diffPercent >= 0) "+" else ""
                                        Text(
                                            text = "$diffPrefix${String.format("%.0f", diffPercent)}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (abs(diffPercent) <= 10.0) Color(0xFF2E7D32) else Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // PATH DURATION AND ELEVATION DISPLAY
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Timer,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = GeoUtils.formatDuration(route.durationSeconds.toLong()),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Terrain,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = Color(0xFF2E7D32)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "+${route.elevationGainMeters} m",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (isSelected) {
                                        Button(
                                            onClick = onStartRun,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                                .testTag("start_run_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Start Run", style = MaterialTheme.typography.labelMedium)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { onRouteSelected(route) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                        ) {
                                            Text("Select", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Selected Route Elevation Profile
                    if (selectedRoute != null && selectedRoute.elevationProfile.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ElevationProfileChart(
                            elevationProfile = selectedRoute.elevationProfile,
                            gainMeters = selectedRoute.elevationGainMeters,
                            lossMeters = selectedRoute.elevationLossMeters
                        )
                    }
                }
            }
        }
    }
}
