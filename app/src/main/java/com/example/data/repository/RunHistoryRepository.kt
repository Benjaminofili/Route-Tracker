package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.RunRecordEntity
import com.example.model.CompletedRun
import com.example.model.PersonalBest
import com.example.util.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

class RunHistoryRepository(database: AppDatabase) {
    private val runRecordDao = database.runRecordDao()

    val allRuns: Flow<List<CompletedRun>> = runRecordDao.getAllRuns().map { entities ->
        entities.map { entity ->
            val profile = parseElevationProfileJson(entity.elevationProfileJson)
            CompletedRun(
                id = entity.id,
                timestamp = entity.timestamp,
                distanceMeters = entity.distanceMeters,
                durationSeconds = entity.durationSeconds,
                avgPaceSecondsPerKm = entity.avgPaceSecondsPerKm,
                polyline = entity.polyline,
                pointsCount = entity.pointsCount,
                elevationGainMeters = entity.elevationGainMeters,
                elevationLossMeters = entity.elevationLossMeters,
                elevationProfile = profile,
                difficulty = entity.difficulty,
                routeType = entity.routeType
            )
        }
    }

    suspend fun saveRun(run: CompletedRun): Long = withContext(Dispatchers.IO) {
        val entity = RunRecordEntity(
            timestamp = run.timestamp,
            distanceMeters = run.distanceMeters,
            durationSeconds = run.durationSeconds,
            avgPaceSecondsPerKm = run.avgPaceSecondsPerKm,
            polyline = run.polyline,
            pointsCount = run.pointsCount,
            elevationGainMeters = run.elevationGainMeters,
            elevationLossMeters = run.elevationLossMeters,
            elevationProfileJson = serializeElevationProfileJson(run.elevationProfile),
            difficulty = run.difficulty,
            routeType = run.routeType
        )
        runRecordDao.insertRun(entity)
    }

    suspend fun getRunById(id: Long): CompletedRun? = withContext(Dispatchers.IO) {
        val entity = runRecordDao.getRunById(id) ?: return@withContext null
        CompletedRun(
            id = entity.id,
            timestamp = entity.timestamp,
            distanceMeters = entity.distanceMeters,
            durationSeconds = entity.durationSeconds,
            avgPaceSecondsPerKm = entity.avgPaceSecondsPerKm,
            polyline = entity.polyline,
            pointsCount = entity.pointsCount,
            elevationGainMeters = entity.elevationGainMeters,
            elevationLossMeters = entity.elevationLossMeters,
            elevationProfile = parseElevationProfileJson(entity.elevationProfileJson),
            difficulty = entity.difficulty,
            routeType = entity.routeType
        )
    }

    suspend fun deleteRun(id: Long) = withContext(Dispatchers.IO) {
        runRecordDao.deleteRunById(id)
    }

    fun computePersonalBests(runs: List<CompletedRun>): List<PersonalBest> =
        Companion.computePersonalBests(runs)

    companion object {
        /**
         * Computes Personal Bests for common distances and milestones:
         * - Fastest 1k pace
         * - Fastest 3k pace (on runs >= 3k)
         * - Fastest 5k pace (on runs >= 5k)
         * - Fastest 10k pace (on runs >= 10k)
         * - Longest Run Distance
         * - Most Elevation Gained
         */
        fun computePersonalBests(runs: List<CompletedRun>): List<PersonalBest> {
            if (runs.isEmpty()) return emptyList()

            val personalBests = mutableListOf<PersonalBest>()

            // 1. Longest Distance
            val longestRun = runs.maxByOrNull { it.distanceMeters }
            if (longestRun != null && longestRun.distanceMeters >= 500) {
                personalBests.add(
                    PersonalBest(
                        title = "Longest Run",
                        value = String.format("%.2f km", longestRun.distanceKm),
                        subtext = "${GeoUtils.formatDuration(longestRun.durationSeconds)} • ${GeoUtils.formatPace(longestRun.avgPaceSecondsPerKm)}",
                        iconType = "distance",
                        runId = longestRun.id,
                        runDate = longestRun.timestamp
                    )
                )
            }

            // 2. Fastest 5 km (runs >= 4.8 km)
            val valid5kRuns = runs.filter { it.distanceMeters >= 4800 }
            val fastest5k = valid5kRuns.minByOrNull { it.avgPaceSecondsPerKm }
            if (fastest5k != null && fastest5k.avgPaceSecondsPerKm > 0) {
                val estimated5kTime = (fastest5k.avgPaceSecondsPerKm * 5.0).toLong()
                personalBests.add(
                    PersonalBest(
                        title = "Fastest 5 km",
                        value = GeoUtils.formatDuration(estimated5kTime),
                        subtext = "Pace: ${GeoUtils.formatPace(fastest5k.avgPaceSecondsPerKm)}",
                        iconType = "speed",
                        runId = fastest5k.id,
                        runDate = fastest5k.timestamp
                    )
                )
            }

            // 3. Fastest 3 km (runs >= 2.9 km)
            val valid3kRuns = runs.filter { it.distanceMeters >= 2900 }
            val fastest3k = valid3kRuns.minByOrNull { it.avgPaceSecondsPerKm }
            if (fastest3k != null && fastest3k.avgPaceSecondsPerKm > 0) {
                val estimated3kTime = (fastest3k.avgPaceSecondsPerKm * 3.0).toLong()
                personalBests.add(
                    PersonalBest(
                        title = "Fastest 3 km",
                        value = GeoUtils.formatDuration(estimated3kTime),
                        subtext = "Pace: ${GeoUtils.formatPace(fastest3k.avgPaceSecondsPerKm)}",
                        iconType = "speed",
                        runId = fastest3k.id,
                        runDate = fastest3k.timestamp
                    )
                )
            }

            // 4. Fastest 10 km (runs >= 9.8 km)
            val valid10kRuns = runs.filter { it.distanceMeters >= 9800 }
            val fastest10k = valid10kRuns.minByOrNull { it.avgPaceSecondsPerKm }
            if (fastest10k != null && fastest10k.avgPaceSecondsPerKm > 0) {
                val estimated10kTime = (fastest10k.avgPaceSecondsPerKm * 10.0).toLong()
                personalBests.add(
                    PersonalBest(
                        title = "Fastest 10 km",
                        value = GeoUtils.formatDuration(estimated10kTime),
                        subtext = "Pace: ${GeoUtils.formatPace(fastest10k.avgPaceSecondsPerKm)}",
                        iconType = "speed",
                        runId = fastest10k.id,
                        runDate = fastest10k.timestamp
                    )
                )
            }

            // 5. Fastest 1 km (runs >= 1000m)
            val valid1kRuns = runs.filter { it.distanceMeters >= 1000 }
            val fastest1k = valid1kRuns.minByOrNull { it.avgPaceSecondsPerKm }
            if (fastest1k != null && fastest1k.avgPaceSecondsPerKm > 0) {
                personalBests.add(
                    PersonalBest(
                        title = "Fastest 1 km Pace",
                        value = GeoUtils.formatPace(fastest1k.avgPaceSecondsPerKm),
                        subtext = "On ${String.format("%.1f", fastest1k.distanceKm)} km run",
                        iconType = "pace",
                        runId = fastest1k.id,
                        runDate = fastest1k.timestamp
                    )
                )
            }

            // 6. Max Elevation Gained
            val maxElevationRun = runs.maxByOrNull { it.elevationGainMeters }
            if (maxElevationRun != null && maxElevationRun.elevationGainMeters > 10) {
                personalBests.add(
                    PersonalBest(
                        title = "Highest Elevation Gain",
                        value = "+${maxElevationRun.elevationGainMeters} m",
                        subtext = "${String.format("%.1f", maxElevationRun.distanceKm)} km (${maxElevationRun.difficulty})",
                        iconType = "elevation",
                        runId = maxElevationRun.id,
                        runDate = maxElevationRun.timestamp
                    )
                )
            }

            return personalBests
        }
    }

    private fun serializeElevationProfileJson(profile: List<Double>): String {
        val arr = JSONArray()
        for (h in profile) {
            arr.put(h)
        }
        return arr.toString()
    }

    private fun parseElevationProfileJson(json: String?): List<Double> {
        if (json.isNullOrBlank()) return emptyList()
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
