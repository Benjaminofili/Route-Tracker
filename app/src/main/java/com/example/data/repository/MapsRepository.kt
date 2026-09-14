package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.SavedRouteEntity
import com.example.data.remote.ApiClients
import com.example.data.remote.ApiLatLng
import com.example.data.remote.ComputeRoutesRequest
import com.example.data.remote.ElevationApiClient
import com.example.data.remote.LatLngLocationWrapper
import com.example.data.remote.RouteWaypointWrapper
import com.example.model.LatLngPoint
import com.example.model.RouteDifficulty
import com.example.model.RouteOption
import com.example.model.RouteType
import com.example.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

class MapsRepository(private val database: AppDatabase) {
    private val TAG = "MapsRepository"
    private val savedRouteDao = database.savedRouteDao()

    val savedRoutesFlow: Flow<List<RouteOption>> = savedRouteDao.getAllRoutes().map { entities ->
        entities.map { entity ->
            val waypoints = parseWaypointsJson(entity.waypointsJson)
            val elevationProfile = parseElevationProfileJson(entity.elevationProfileJson)
            val difficulty = runCatching { RouteDifficulty.valueOf(entity.difficulty) }.getOrDefault(RouteDifficulty.FLAT)
            val routeType = runCatching { RouteType.valueOf(entity.routeType) }.getOrDefault(RouteType.PARKS)

            RouteOption(
                id = entity.id,
                title = entity.title,
                distanceMeters = entity.distanceMeters,
                durationSeconds = entity.durationSeconds,
                polyline = entity.polyline,
                points = GeoUtils.decodePolyline(entity.polyline),
                waypoints = waypoints,
                elevationGainMeters = entity.elevationGainMeters,
                elevationLossMeters = entity.elevationLossMeters,
                elevationProfile = elevationProfile,
                difficulty = difficulty,
                routeType = routeType,
                isCached = true
            )
        }
    }

    /**
     * Generates loop route options tailored to the user's selected difficulty and route type.
     */
    suspend fun generateLoopRoutes(
        startLocation: LatLngPoint,
        targetDistanceMeters: Double,
        difficulty: RouteDifficulty = RouteDifficulty.FLAT,
        routeType: RouteType = RouteType.PARKS,
        routeCount: Int = 3
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MAPS_API_KEY
        val hasValidKey = apiKey.isNotBlank() && apiKey != "YOUR_MAPS_API_KEY"

        val generatedOptions = mutableListOf<RouteOption>()
        val baseRotationStep = 360.0 / routeCount

        // Route type bearing bias:
        // Parks bias towards natural perimeter curves (+20° organic shift)
        // Urban keeps tight grid alignment
        // Scenic opens up broad vista bearings
        val typeAngleOffset = when (routeType) {
            RouteType.PARKS -> 25.0
            RouteType.URBAN -> 0.0
            RouteType.SCENIC -> 45.0
        }

        for (i in 0 until routeCount) {
            val initialAngleOffset = (i * baseRotationStep) + typeAngleOffset + Random.nextDouble(-10.0, 10.0)
            var option: RouteOption? = null

            if (hasValidKey) {
                try {
                    option = generateSingleLoopRouteWithApi(
                        startLocation = startLocation,
                        targetDistanceMeters = targetDistanceMeters,
                        initialBearingOffset = initialAngleOffset,
                        apiKey = apiKey,
                        difficulty = difficulty,
                        routeType = routeType,
                        routeIndex = i + 1
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "API loop generation failed for option ${i + 1}: ${e.message}")
                }
            }

            // Fallback realistic generator if API not configured or failed
            if (option == null) {
                option = generateFallbackLoopOption(
                    startLocation = startLocation,
                    targetDistanceMeters = targetDistanceMeters,
                    rotationDegrees = initialAngleOffset,
                    difficulty = difficulty,
                    routeType = routeType,
                    routeIndex = i + 1
                )
            }

            generatedOptions.add(option)
        }

        generatedOptions
    }

    private suspend fun generateSingleLoopRouteWithApi(
        startLocation: LatLngPoint,
        targetDistanceMeters: Double,
        initialBearingOffset: Double,
        apiKey: String,
        difficulty: RouteDifficulty,
        routeType: RouteType,
        routeIndex: Int
    ): RouteOption? {
        var currentRadius = targetDistanceMeters / (2.0 * PI)
        var closestRoute: RouteOption? = null
        var minDistanceDiff = Double.MAX_VALUE

        // Configure waypoint spread according to route type:
        // Parks: 5 organic waypoints avoiding highways
        // Urban: 4 cardinal waypoints aligned to city blocks
        // Scenic: 4-5 wide arc waypoints
        val bearings = when (routeType) {
            RouteType.URBAN -> listOf(0.0, 90.0, 180.0, 270.0)
            RouteType.PARKS -> listOf(0.0, 72.0, 144.0, 216.0, 288.0)
            RouteType.SCENIC -> listOf(20.0, 110.0, 200.0, 290.0)
        }

        for (attempt in 1..3) {
            val candidateWaypoints = mutableListOf<LatLngPoint>()
            for (baseBearing in bearings) {
                var bearing = (baseBearing + initialBearingOffset) % 360.0
                var snappedPoint: LatLngPoint? = null

                // Try candidate point, nudge bearing if snapping fails
                for (nudgeAttempt in 0..2) {
                    val candidate = GeoUtils.computeDestinationPoint(
                        start = startLocation,
                        bearingDegrees = bearing,
                        distanceMeters = currentRadius
                    )

                    snappedPoint = snapToRoad(candidate, apiKey)
                    if (snappedPoint != null) break
                    bearing = (bearing + 15.0) % 360.0
                }

                candidateWaypoints.add(snappedPoint ?: GeoUtils.computeDestinationPoint(
                    start = startLocation,
                    bearingDegrees = bearing,
                    distanceMeters = currentRadius
                ))
            }

            val apiRoute = computeWalkRoute(
                start = startLocation,
                waypoints = candidateWaypoints,
                apiKey = apiKey
            )

            if (apiRoute != null && apiRoute.distanceMeters != null && !apiRoute.polyline?.encodedPolyline.isNullOrEmpty()) {
                val actualDistance = apiRoute.distanceMeters.toDouble()
                val durationSecs = parseDuration(apiRoute.duration)
                val diff = abs(actualDistance - targetDistanceMeters)
                val encodedPolyline = apiRoute.polyline!!.encodedPolyline!!
                val points = GeoUtils.decodePolyline(encodedPolyline)

                // Fetch real elevation data for path
                val elevationData = ElevationApiClient.fetchElevationForPath(
                    encodedPolyline = encodedPolyline,
                    points = points,
                    difficulty = difficulty
                )

                val title = createRouteTitle(routeType, routeIndex)

                val option = RouteOption(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    distanceMeters = actualDistance.toInt(),
                    durationSeconds = durationSecs,
                    polyline = encodedPolyline,
                    points = points,
                    waypoints = candidateWaypoints,
                    elevationGainMeters = elevationData.gainMeters,
                    elevationLossMeters = elevationData.lossMeters,
                    elevationProfile = elevationData.profile,
                    difficulty = difficulty,
                    routeType = routeType
                )

                if (diff < minDistanceDiff) {
                    minDistanceDiff = diff
                    closestRoute = option
                }

                val tolerance = targetDistanceMeters * 0.10
                if (diff <= tolerance) {
                    return option
                }

                if (actualDistance > 0) {
                    val scaleFactor = (targetDistanceMeters / actualDistance).coerceIn(0.5, 2.0)
                    currentRadius *= scaleFactor
                }
            } else {
                break
            }
        }

        return closestRoute
    }

    private suspend fun snapToRoad(point: LatLngPoint, apiKey: String): LatLngPoint? {
        return try {
            val response = ApiClients.roadsApi.nearestRoads(
                points = "${point.latitude},${point.longitude}",
                apiKey = apiKey
            )
            val snapped = response.snappedPoints?.firstOrNull()?.location
            if (snapped != null) {
                LatLngPoint(snapped.latitude, snapped.longitude)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun computeWalkRoute(
        start: LatLngPoint,
        waypoints: List<LatLngPoint>,
        apiKey: String
    ): com.example.data.remote.ApiRoute? {
        val originWrapper = RouteWaypointWrapper(
            location = LatLngLocationWrapper(latLng = ApiLatLng(start.latitude, start.longitude))
        )
        val destinationWrapper = RouteWaypointWrapper(
            location = LatLngLocationWrapper(latLng = ApiLatLng(start.latitude, start.longitude))
        )
        val intermediateWrappers = waypoints.map { wp ->
            RouteWaypointWrapper(
                location = LatLngLocationWrapper(latLng = ApiLatLng(wp.latitude, wp.longitude))
            )
        }

        val request = ComputeRoutesRequest(
            origin = originWrapper,
            destination = destinationWrapper,
            intermediates = intermediateWrappers,
            travelMode = "WALK"
        )

        val response = ApiClients.routesApi.computeRoutes(apiKey = apiKey, request = request)
        return response.routes?.firstOrNull()
    }

    private fun parseDuration(durationStr: String?): Int {
        if (durationStr == null) return 0
        return durationStr.removeSuffix("s").toIntOrNull() ?: 0
    }

    private fun createRouteTitle(type: RouteType, index: Int): String {
        return when (type) {
            RouteType.PARKS -> when (index) {
                1 -> "Emerald Greenway Loop"
                2 -> "Parkland Perimeter Circuit"
                else -> "Riverbank Trail Loop"
            }
            RouteType.URBAN -> when (index) {
                1 -> "Metro Avenue Circuit"
                2 -> "Downtown Promenade Loop"
                else -> "Civic Center Loop"
            }
            RouteType.SCENIC -> when (index) {
                1 -> "Sunrise Vista Loop"
                2 -> "Panorama Overlook Circuit"
                else -> "Waterfront Ridge Loop"
            }
        }
    }

    private fun generateFallbackLoopOption(
        startLocation: LatLngPoint,
        targetDistanceMeters: Double,
        rotationDegrees: Double,
        difficulty: RouteDifficulty,
        routeType: RouteType,
        routeIndex: Int
    ): RouteOption {
        val title = createRouteTitle(routeType, routeIndex)

        val sides = when (routeType) {
            RouteType.URBAN -> 8
            RouteType.PARKS -> 10
            RouteType.SCENIC -> 8
        }
        val effectiveRadius = (targetDistanceMeters / (2.0 * PI)) * 0.96
        val waypoints = mutableListOf<LatLngPoint>()
        val fullPathPoints = mutableListOf<LatLngPoint>()

        fullPathPoints.add(startLocation)

        for (i in 0 until sides) {
            val angle = rotationDegrees + (i * 360.0 / sides)
            // Adjust geometry for route type:
            val rVariance = when (routeType) {
                RouteType.URBAN -> 0.04 * kotlin.math.sin(i * 2.0)
                RouteType.PARKS -> 0.12 * kotlin.math.sin(i * 1.6)
                RouteType.SCENIC -> 0.16 * kotlin.math.cos(i * 1.4)
            }
            val r = effectiveRadius * (1.0 + rVariance)
            val wp = GeoUtils.computeDestinationPoint(startLocation, angle, r)
            if (i % 2 == 1) {
                waypoints.add(wp)
            }
            fullPathPoints.add(wp)
        }
        fullPathPoints.add(startLocation)

        val densePoints = mutableListOf<LatLngPoint>()
        for (i in 0 until fullPathPoints.size - 1) {
            val p1 = fullPathPoints[i]
            val p2 = fullPathPoints[i + 1]
            val segDist = GeoUtils.distanceBetween(p1, p2)
            val steps = (segDist / 45.0).toInt().coerceAtLeast(1)
            for (step in 0 until steps) {
                val f = step.toDouble() / steps
                densePoints.add(
                    LatLngPoint(
                        p1.latitude + (p2.latitude - p1.latitude) * f,
                        p1.longitude + (p2.longitude - p1.longitude) * f
                    )
                )
            }
        }
        densePoints.add(startLocation)

        val totalDist = GeoUtils.calculateTotalDistance(densePoints)
        // Pace accounts for terrain difficulty:
        val basePaceSecPerKm = when (difficulty) {
            RouteDifficulty.FLAT -> 340 // ~5:40 /km
            RouteDifficulty.HILLY -> 375 // ~6:15 /km
            RouteDifficulty.VERY_HILLY -> 420 // ~7:00 /km
        }
        val durationSeconds = ((totalDist / 1000.0) * basePaceSecPerKm).toInt()

        val encodedPolyline = GeoUtils.encodePolyline(densePoints)
        val elevationData = ElevationApiClient.synthesizeElevationProfile(densePoints, difficulty)

        return RouteOption(
            id = UUID.randomUUID().toString(),
            title = title,
            distanceMeters = totalDist.toInt(),
            durationSeconds = durationSeconds,
            polyline = encodedPolyline,
            points = densePoints,
            waypoints = waypoints,
            elevationGainMeters = elevationData.gainMeters,
            elevationLossMeters = elevationData.lossMeters,
            elevationProfile = elevationData.profile,
            difficulty = difficulty,
            routeType = routeType,
            isCached = false
        )
    }

    suspend fun saveRoute(route: RouteOption) = withContext(Dispatchers.IO) {
        val entity = SavedRouteEntity(
            id = route.id,
            title = route.title,
            distanceMeters = route.distanceMeters,
            durationSeconds = route.durationSeconds,
            polyline = route.polyline,
            waypointsJson = serializeWaypointsJson(route.waypoints),
            elevationGainMeters = route.elevationGainMeters,
            elevationLossMeters = route.elevationLossMeters,
            elevationProfileJson = serializeElevationProfileJson(route.elevationProfile),
            difficulty = route.difficulty.name,
            routeType = route.routeType.name
        )
        savedRouteDao.insertRoute(entity)
    }

    suspend fun deleteSavedRoute(id: String) = withContext(Dispatchers.IO) {
        savedRouteDao.deleteRouteById(id)
    }

    private fun serializeWaypointsJson(waypoints: List<LatLngPoint>): String {
        val arr = JSONArray()
        for (wp in waypoints) {
            val obj = org.json.JSONObject()
            obj.put("lat", wp.latitude)
            obj.put("lng", wp.longitude)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseWaypointsJson(json: String): List<LatLngPoint> {
        val list = mutableListOf<LatLngPoint>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(LatLngPoint(obj.getDouble("lat"), obj.getDouble("lng")))
            }
        } catch (e: Exception) {
            // Ignore
        }
        return list
    }

    private fun serializeElevationProfileJson(profile: List<Double>): String {
        val arr = JSONArray()
        for (h in profile) {
            arr.put(h)
        }
        return arr.toString()
    }

    private fun parseElevationProfileJson(json: String): List<Double> {
        val list = mutableListOf<Double>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                list.add(arr.getDouble(i))
            }
        } catch (e: Exception) {
            // Ignore
        }
        return list
    }
}
