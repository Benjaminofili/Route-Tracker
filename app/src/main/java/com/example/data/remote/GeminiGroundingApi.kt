package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.model.LatLngPoint
import com.example.model.ScoutRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiGroundingApi {
    private const val TAG = "GeminiGroundingApi"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getLocalRunningInsights(
        center: LatLngPoint,
        distanceKm: Double
    ): List<ScoutRecommendation> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext getLocalFallbackInsights(center, distanceKm)
        }

        try {
            val prompt = """
                You are a running route coach and local scout. The user is planning a ${String.format("%.1f", distanceKm)} km morning run starting near coordinates (${center.latitude}, ${center.longitude}).
                Using Google Maps data, identify 3 top features for runners near this area:
                1. A scenic park, trail segment, or waterfront path.
                2. Availability of public water fountains, hydration stations, or restrooms.
                3. Running safety, pedestrian pavement quality, and morning elevation tips.
                Respond with concise, structured, practical points.
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)

                // Maps grounding tool
                val tools = JSONArray().apply {
                    put(JSONObject().put("googleMaps", JSONObject()))
                }
                put("tools", tools)
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                Log.w(TAG, "Gemini API request failed: code ${response.code}, falling back")
                return@withContext getLocalFallbackInsights(center, distanceKm)
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (!text.isNullOrBlank()) {
                parseInsightsText(text)
            } else {
                getLocalFallbackInsights(center, distanceKm)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Gemini insights", e)
            getLocalFallbackInsights(center, distanceKm)
        }
    }

    private fun parseInsightsText(rawText: String): List<ScoutRecommendation> {
        val lines = rawText.split("\n").filter { it.isNotBlank() }
        val recommendations = mutableListOf<ScoutRecommendation>()
        var currentTitle = "Local Running Scout"
        val currentBullets = mutableListOf<String>()

        for (line in lines) {
            val clean = line.trim().trimStart('*', '-', '#').trim()
            if (line.startsWith("#") || line.contains(":") && clean.length < 50 && !clean.startsWith("•")) {
                if (currentBullets.isNotEmpty()) {
                    recommendations.add(ScoutRecommendation(currentTitle, currentBullets.joinToString("\n")))
                    currentBullets.clear()
                }
                currentTitle = clean
            } else {
                currentBullets.add(clean)
            }
        }
        if (currentBullets.isNotEmpty()) {
            recommendations.add(ScoutRecommendation(currentTitle, currentBullets.joinToString("\n")))
        }

        return if (recommendations.isNotEmpty()) recommendations else getLocalFallbackInsights(null, 5.0)
    }

    private fun getLocalFallbackInsights(center: LatLngPoint?, distanceKm: Double): List<ScoutRecommendation> {
        return listOf(
            ScoutRecommendation(
                title = "Hydration & Restrooms",
                details = "Public park pavilions and trailheads typically offer public drinking fountains and accessible restrooms along morning loops.",
                amenities = listOf("Water Fountains", "Restrooms", "Benches")
            ),
            ScoutRecommendation(
                title = "Morning Foot Traffic & Surface",
                details = "Recommended for ${String.format("%.1f", distanceKm)} km loop: Wide sidewalks, asphalt multi-use paths, and pedestrian-separated crossings.",
                amenities = listOf("Paved Path", "Street Lights", "Low Traffic")
            ),
            ScoutRecommendation(
                title = "Pacing & Elevation Advice",
                details = "Keep steady cadence during the opening kilometer. Utilize loop cross streets to pace heart rate smoothly before returning.",
                amenities = listOf("Gentle Grade", "Scenic Loop")
            )
        )
    }
}
