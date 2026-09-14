package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Routes API Models ---

@JsonClass(generateAdapter = true)
data class ComputeRoutesRequest(
    @Json(name = "origin") val origin: RouteWaypointWrapper,
    @Json(name = "destination") val destination: RouteWaypointWrapper,
    @Json(name = "intermediates") val intermediates: List<RouteWaypointWrapper>,
    @Json(name = "travelMode") val travelMode: String = "WALK",
    @Json(name = "polylineQuality") val polylineQuality: String = "HIGH_QUALITY",
    @Json(name = "polylineEncoding") val polylineEncoding: String = "ENCODED_POLYLINE"
)

@JsonClass(generateAdapter = true)
data class RouteWaypointWrapper(
    @Json(name = "location") val location: LatLngLocationWrapper
)

@JsonClass(generateAdapter = true)
data class LatLngLocationWrapper(
    @Json(name = "latLng") val latLng: ApiLatLng
)

@JsonClass(generateAdapter = true)
data class ApiLatLng(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double
)

@JsonClass(generateAdapter = true)
data class ComputeRoutesResponse(
    @Json(name = "routes") val routes: List<ApiRoute>?
)

@JsonClass(generateAdapter = true)
data class ApiRoute(
    @Json(name = "distanceMeters") val distanceMeters: Int?,
    @Json(name = "duration") val duration: String?,
    @Json(name = "polyline") val polyline: ApiPolyline?
)

@JsonClass(generateAdapter = true)
data class ApiPolyline(
    @Json(name = "encodedPolyline") val encodedPolyline: String?
)

// --- Roads API Models ---

@JsonClass(generateAdapter = true)
data class NearestRoadsResponse(
    @Json(name = "snappedPoints") val snappedPoints: List<SnappedPoint>?
)

@JsonClass(generateAdapter = true)
data class SnappedPoint(
    @Json(name = "location") val location: SnappedLocation?,
    @Json(name = "originalIndex") val originalIndex: Int?,
    @Json(name = "placeId") val placeId: String?
)

@JsonClass(generateAdapter = true)
data class SnappedLocation(
    @Json(name = "latitude") val latitude: Double,
    @Json(name = "longitude") val longitude: Double
)

// --- Retrofit Interfaces ---

interface RoutesApiService {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline",
        @Body request: ComputeRoutesRequest
    ): ComputeRoutesResponse
}

interface RoadsApiService {
    @GET("v1/nearestRoads")
    suspend fun nearestRoads(
        @Query("points") points: String,
        @Query("key") apiKey: String
    ): NearestRoadsResponse
}

object ApiClients {
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    val routesApi: RoutesApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(RoutesApiService::class.java)
    }

    val roadsApi: RoadsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://roads.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(RoadsApiService::class.java)
    }
}
