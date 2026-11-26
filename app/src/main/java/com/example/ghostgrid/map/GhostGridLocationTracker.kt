package com.example.ghostgrid.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.ghostgrid.search.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationSnapshot(
    val point: GeoPoint = GeoPoint(23.0225, 72.5714), // Default center (Gujarat) until GPS locks
    val speedMps: Float = 0f,
    val bearing: Float = 0f,
    val accuracyMeters: Float = 0f,
    val hasGpsFix: Boolean = false,
    val provider: String = "none",
    val rawLocation: Location? = null
)

/**
 * Pure Android native `LocationManager` tracker (zero Google Play Services dependencies).
 * Feeds live GPS coordinates, speed, bearing, and accuracy into MapLibre & the Navigation Engine.
 */
class GhostGridLocationTracker(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _location = MutableStateFlow(LocationSnapshot())
    val location: StateFlow<LocationSnapshot> = _location.asStateFlow()

    private var isListening = false

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun startUpdates() {
        if (isListening || !hasLocationPermission()) return
        try {
            // Seed immediately from last known provider fix if available
            val lastGps = runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            val lastNet = runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            val bestLast = chooseBetterLocation(lastGps, lastNet)
            if (bestLast != null) {
                emitLocation(bestLast)
            }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    this
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    5f,
                    this
                )
            }
            isListening = true
        } catch (_: SecurityException) {
            isListening = false
        }
    }

    fun stopUpdates() {
        if (!isListening) return
        runCatching { locationManager.removeUpdates(this) }
        isListening = false
    }

    override fun onLocationChanged(location: Location) {
        emitLocation(location)
    }

    @Deprecated("Required for compatibility")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun emitLocation(loc: Location) {
        _location.value = LocationSnapshot(
            point = GeoPoint(loc.latitude, loc.longitude),
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            bearing = if (loc.hasBearing()) loc.bearing else _location.value.bearing,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 0f,
            hasGpsFix = true,
            provider = loc.provider ?: "gps",
            rawLocation = loc
        )
    }

    private fun chooseBetterLocation(a: Location?, b: Location?): Location? = when {
        a == null -> b
        b == null -> a
        a.time >= b.time -> a
        else -> b
    }
}
