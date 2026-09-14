package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
fun ElevationProfileChart(
    elevationProfile: List<Double>,
    gainMeters: Int,
    lossMeters: Int,
    modifier: Modifier = Modifier
) {
    if (elevationProfile.isEmpty()) return

    val minElevation = remember(elevationProfile) { elevationProfile.minOrNull() ?: 0.0 }
    val maxElevation = remember(elevationProfile) { elevationProfile.maxOrNull() ?: 100.0 }
    val range = remember(minElevation, maxElevation) { max(10.0, maxElevation - minElevation) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("elevation_profile_chart"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Landscape,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Elevation Profile",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.NorthEast,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "+$gainMeters m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SouthEast,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "-$lossMeters m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
            ) {
                val lineColor = MaterialTheme.colorScheme.primary
                val gradientStart = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                val gradientEnd = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)

                Canvas(modifier = Modifier.matchParentSize()) {
                    val width = size.width
                    val height = size.height
                    val paddingBottom = 12f
                    val paddingTop = 12f
                    val chartHeight = height - paddingTop - paddingBottom

                    val path = Path()
                    val fillPath = Path()

                    val stepX = width / (elevationProfile.size - 1).coerceAtLeast(1)

                    elevationProfile.forEachIndexed { index, alt ->
                        val x = index * stepX
                        val normalizedY = ((alt - minElevation) / range).toFloat()
                        val y = paddingTop + (chartHeight * (1f - normalizedY))

                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    fillPath.lineTo(width, height)
                    fillPath.close()

                    // Draw gradient fill under curve
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(gradientStart, gradientEnd),
                            startY = 0f,
                            endY = height
                        )
                    )

                    // Draw stroke line
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(
                            width = 3.5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw start and end milestone dots
                    if (elevationProfile.isNotEmpty()) {
                        val firstY = paddingTop + (chartHeight * (1f - ((elevationProfile.first() - minElevation) / range).toFloat()))
                        val lastY = paddingTop + (chartHeight * (1f - ((elevationProfile.last() - minElevation) / range).toFloat()))

                        drawCircle(color = lineColor, radius = 5f, center = Offset(0f, firstY))
                        drawCircle(color = Color.White, radius = 2.5f, center = Offset(0f, firstY))

                        drawCircle(color = lineColor, radius = 5f, center = Offset(width, lastY))
                        drawCircle(color = Color.White, radius = 2.5f, center = Offset(width, lastY))
                    }
                }
            }

            // Min and Max Axis Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Low: ${minElevation.toInt()} m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "High: ${maxElevation.toInt()} m",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
