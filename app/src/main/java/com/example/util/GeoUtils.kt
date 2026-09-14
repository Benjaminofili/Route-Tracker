package com.example.util

import com.example.model.LatLngPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object GeoUtils {
    const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Compute Haversine distance in meters between two lat/lng points.
     */
    fun distanceBetween(p1: LatLngPoint, p2: LatLngPoint): Double {
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Destination point formula:
     * Given a start point, bearing in degrees, and distance in meters,
     * compute destination lat/lng.
     */
    fun computeDestinationPoint(
        start: LatLngPoint,
        bearingDegrees: Double,
        distanceMeters: Double
    ): LatLngPoint {
        val δ = distanceMeters / EARTH_RADIUS_METERS
        val θ = Math.toRadians(bearingDegrees)
        val φ1 = Math.toRadians(start.latitude)
        val λ1 = Math.toRadians(start.longitude)

        val sinφ1 = sin(φ1)
        val cosφ1 = cos(φ1)
        val sinδ = sin(δ)
        val cosδ = cos(δ)

        val φ2 = asin(sinφ1 * cosδ + cosφ1 * sinδ * cos(θ))
        val y = sin(θ) * sinδ * cosφ1
        val x = cosδ - sinφ1 * sin(φ2)
        val λ2 = λ1 + atan2(y, x)

        // Normalize longitude between -180 and +180
        val normalizedLon = (Math.toDegrees(λ2) + 540.0) % 360.0 - 180.0
        return LatLngPoint(Math.toDegrees(φ2), normalizedLon)
    }

    /**
     * Douglas-Peucker line simplification algorithm.
     * Removes jitter within tolerance epsilonMeters (e.g. 5.0m).
     */
    fun simplifyPoints(
        points: List<LatLngPoint>,
        epsilonMeters: Double = 5.0
    ): List<LatLngPoint> {
        if (points.size < 3) return points

        var maxDistance = 0.0
        var maxIndex = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], start, end)
            if (dist > maxDistance) {
                maxDistance = dist
                maxIndex = i
            }
        }

        return if (maxDistance > epsilonMeters) {
            val left = simplifyPoints(points.subList(0, maxIndex + 1), epsilonMeters)
            val right = simplifyPoints(points.subList(maxIndex, points.size), epsilonMeters)
            left.dropLast(1) + right
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistance(
        point: LatLngPoint,
        lineStart: LatLngPoint,
        lineEnd: LatLngPoint
    ): Double {
        val length = distanceBetween(lineStart, lineEnd)
        if (length == 0.0) return distanceBetween(point, lineStart)

        // Using spherical triangle projection for accuracy
        val d13 = distanceBetween(lineStart, point)
        val d12 = length
        val d23 = distanceBetween(lineEnd, point)

        // Cross-track distance estimation using semiperimeter formula
        val s = (d12 + d13 + d23) / 2.0
        val area = sqrt((s * (s - d12) * (s - d13) * (s - d23)).coerceAtLeast(0.0))
        return (2.0 * area) / d12
    }

    /**
     * Sum distances between sequential points in meters.
     */
    fun calculateTotalDistance(points: List<LatLngPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += distanceBetween(points[i], points[i + 1])
        }
        return total
    }

    fun formatDistance(distanceMeters: Double): String {
        return if (distanceMeters >= 1000) {
            String.format("%.2f km", distanceMeters / 1000.0)
        } else {
            String.format("%.0f m", distanceMeters)
        }
    }

    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    fun formatPace(paceSecondsPerKm: Int): String {
        if (paceSecondsPerKm <= 0 || paceSecondsPerKm > 3600) return "--'--\" /km"
        val minutes = paceSecondsPerKm / 60
        val seconds = paceSecondsPerKm % 60
        return String.format("%d'%02d\" /km", minutes, seconds)
    }

    /**
     * Decode Google polyline string to List<LatLngPoint>
     */
    fun decodePolyline(encoded: String): List<LatLngPoint> {
        val poly = ArrayList<LatLngPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            val p = LatLngPoint((lat.toDouble() / 1E5), (lng.toDouble() / 1E5))
            poly.add(p)
        }
        return poly
    }

    /**
     * Encode List<LatLngPoint> to standard Google polyline string.
     */
    fun encodePolyline(points: List<LatLngPoint>): String {
        val result = StringBuilder()
        var lastLat = 0
        var lastLng = 0

        for (point in points) {
            val lat = (point.latitude * 1e5).toInt()
            val lng = (point.longitude * 1e5).toInt()

            val dLat = lat - lastLat
            val dLng = lng - lastLng

            encodeValue(dLat, result)
            encodeValue(dLng, result)

            lastLat = lat
            lastLng = lng
        }

        return result.toString()
    }

    private fun encodeValue(value: Int, result: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            result.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        result.append((v + 63).toChar())
    }
}
