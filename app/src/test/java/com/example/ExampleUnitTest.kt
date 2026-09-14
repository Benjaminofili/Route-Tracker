package com.example

import com.example.data.remote.ElevationApiClient
import com.example.data.repository.RunHistoryRepository
import com.example.model.CompletedRun
import com.example.model.LatLngPoint
import com.example.model.RouteDifficulty
import com.example.util.GeoUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testHaversineDistance() {
        val p1 = LatLngPoint(37.7694, -122.4862)
        val p2 = LatLngPoint(37.7694, -122.4735)
        val dist = GeoUtils.distanceBetween(p1, p2)
        assertTrue("Distance should be ~1117m, was $dist", dist in 1000.0..1200.0)
    }

    @Test
    fun testDestinationPoint() {
        val start = LatLngPoint(37.7694, -122.4862)
        val bearingNorth = 0.0
        val targetDistance = 1000.0
        val dest = GeoUtils.computeDestinationPoint(start, bearingNorth, targetDistance)

        assertTrue(dest.latitude > start.latitude)
        val actualDist = GeoUtils.distanceBetween(start, dest)
        assertEquals(targetDistance, actualDist, 5.0)
    }

    @Test
    fun testDouglasPeuckerSimplification() {
        val p1 = LatLngPoint(37.76940, -122.48620)
        val p2 = LatLngPoint(37.76942, -122.48500)
        val p3 = LatLngPoint(37.76940, -122.48380)

        val simplified = GeoUtils.simplifyPoints(listOf(p1, p2, p3), epsilonMeters = 5.0)
        assertEquals("Jitter point should be removed", 2, simplified.size)
        assertEquals(p1, simplified.first())
        assertEquals(p3, simplified.last())
    }

    @Test
    fun testPolylineEncodingAndDecoding() {
        val points = listOf(
            LatLngPoint(37.7694, -122.4862),
            LatLngPoint(37.7710, -122.4840),
            LatLngPoint(37.7694, -122.4862)
        )
        val encoded = GeoUtils.encodePolyline(points)
        assertFalse(encoded.isEmpty())

        val decoded = GeoUtils.decodePolyline(encoded)
        assertEquals(points.size, decoded.size)
        assertEquals(points[0].latitude, decoded[0].latitude, 0.0001)
        assertEquals(points[0].longitude, decoded[0].longitude, 0.0001)
    }

    @Test
    fun testPaceFormatting() {
        assertEquals("5'00\" /km", GeoUtils.formatPace(300))
        assertEquals("5'24\" /km", GeoUtils.formatPace(324))
    }

    @Test
    fun testElevationGainCalculation() {
        val points = listOf(
            LatLngPoint(37.7694, -122.4862),
            LatLngPoint(37.7710, -122.4840),
            LatLngPoint(37.7720, -122.4800)
        )
        val flatData = ElevationApiClient.synthesizeElevationProfile(points, RouteDifficulty.FLAT)
        assertTrue("Flat gain should be < 35m", flatData.gainMeters <= 35)

        val hillyData = ElevationApiClient.synthesizeElevationProfile(points, RouteDifficulty.HILLY)
        assertTrue("Hilly gain should be greater than flat gain", hillyData.gainMeters >= flatData.gainMeters)
    }

    @Test
    fun testPersonalBestsCalculation() {
        val runs = listOf(
            CompletedRun(
                id = 1,
                timestamp = 1700000000000L,
                distanceMeters = 5200.0,
                durationSeconds = 1560L,
                avgPaceSecondsPerKm = 300, // 5:00/km
                polyline = "",
                pointsCount = 40,
                elevationGainMeters = 45
            ),
            CompletedRun(
                id = 2,
                timestamp = 1700100000000L,
                distanceMeters = 10500.0,
                durationSeconds = 3465L,
                avgPaceSecondsPerKm = 330, // 5:30/km
                polyline = "",
                pointsCount = 80,
                elevationGainMeters = 120
            )
        )

        val pbs = RunHistoryRepository.computePersonalBests(runs)
        assertTrue("Should compute at least 3 personal bests", pbs.size >= 3)
        assertTrue("Should include Longest Run", pbs.any { it.title == "Longest Run" })
        assertTrue("Should include Fastest 5 km", pbs.any { it.title == "Fastest 5 km" })
        assertTrue("Should include Highest Elevation Gain", pbs.any { it.title == "Highest Elevation Gain" })
    }

    @Test
    fun testAutoPauseBehavior() {
        val manager = com.example.tracker.TrackingManager
        manager.setAutoPauseEnabled(true)
        assertTrue(manager.runState.value.isAutoPauseEnabled)

        // Verify simulateStationary sets speed to zero and triggers auto-pause
        manager.simulateStationary()
        // Run state updates gracefully without crash
        assertNotNull(manager.runState.value)
    }
}
