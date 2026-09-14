package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.model.LatLngPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationRepository(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Default runner location (Golden Gate Park / scenic park) if GPS is unavailable initially
    val defaultLocation = LatLngPoint(37.7694, -122.4862)

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LatLngPoint = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(defaultLocation)
            return@suspendCancellableCoroutine
        }

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        continuation.resume(LatLngPoint(loc.latitude, loc.longitude))
                    } else {
                        // Try last location fallback
                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    continuation.resume(LatLngPoint(lastLoc.latitude, lastLoc.longitude))
                                } else {
                                    continuation.resume(defaultLocation)
                                }
                            }
                            .addOnFailureListener {
                                continuation.resume(defaultLocation)
                            }
                    }
                }
                .addOnFailureListener {
                    continuation.resume(defaultLocation)
                }
        } catch (e: Exception) {
            continuation.resume(defaultLocation)
        }
    }
}
