package com.example.ghostgrid.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ghostgrid.data.GhostGridDatabase
import com.example.ghostgrid.data.RecordedTrackEntity
import com.example.ghostgrid.data.TrackBreadcrumbEntity
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.GeoPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LiveRecordingState(
    val isRecording: Boolean = false,
    val trackId: Long? = null,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val livePoints: List<GeoPoint> = emptyList()
)

/**
 * Privacy-hardened Foreground Location Service (`foregroundServiceType="location"`).
 *
 * - Uses native Android `LocationManager` (zero Google Play Services).
 * - Filters breadcrumbs (`accuracy <= 20m`, minimum displacement `4m`).
 * - Writes all breadcrumbs directly into the SQLCipher-encrypted Room database.
 */
class RouteRecordingService : Service(), LocationListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private lateinit var database: GhostGridDatabase

    private var activeTrackId: Long? = null
    private var lastAcceptedLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0
    private var totalPoints: Int = 0
    private val inMemoryTrail = ArrayList<GeoPoint>()

    companion object {
        const val ACTION_START = "com.example.ghostgrid.action.START_RECORDING"
        const val ACTION_STOP = "com.example.ghostgrid.action.STOP_RECORDING"
        private const val CHANNEL_ID = "ghostgrid_route_recording"
        private const val NOTIFICATION_ID = 7041

        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 20f
        private const val MIN_DISPLACEMENT_METERS = 4.0

        private val _recordingState = MutableStateFlow(LiveRecordingState())
        val recordingState: StateFlow<LiveRecordingState> = _recordingState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, RouteRecordingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RouteRecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        database = GhostGridDatabase.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecordingAndShutdown()
            ACTION_START, null -> startRecordingSession()
        }
        return START_STICKY
    }

    private fun startRecordingSession() {
        if (activeTrackId != null) return

        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
            stopSelf()
            return
        }

        val notification = buildNotification(0.0, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            val existing = database.trackDao().getActiveTrack()
            val trackId = existing?.id ?: run {
                val stamp = SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date())
                database.trackDao().insertTrack(
                    RecordedTrackEntity(
                        name = "Track $stamp",
                        startedAt = System.currentTimeMillis(),
                        isActive = true
                    )
                )
            }
            activeTrackId = trackId
            val existingPoints = database.trackDao().getBreadcrumbs(trackId)
            inMemoryTrail.clear()
            existingPoints.forEach { inMemoryTrail += GeoPoint(it.lat, it.lon) }
            totalDistanceMeters = existing?.distanceMeters ?: 0.0
            totalPoints = existingPoints.size

            _recordingState.value = LiveRecordingState(
                isRecording = true,
                trackId = trackId,
                distanceMeters = totalDistanceMeters,
                pointCount = totalPoints,
                livePoints = inMemoryTrail.toList()
            )
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    4f,
                    this
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    5000L,
                    8f,
                    this
                )
            }
        } catch (_: SecurityException) {
            stopRecordingAndShutdown()
        }
    }

    override fun onLocationChanged(location: Location) {
        val trackId = activeTrackId ?: return

        // Enforce strict breadcrumb quality filter: accuracy <= 20m, displacement >= 4m
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            return
        }

        val previous = lastAcceptedLocation
        val segmentMeters = if (previous != null) {
            GeoMath.haversineMeters(
                previous.latitude,
                previous.longitude,
                location.latitude,
                location.longitude
            )
        } else {
            0.0
        }

        if (previous != null && segmentMeters < MIN_DISPLACEMENT_METERS) {
            return
        }

        lastAcceptedLocation = location
        totalDistanceMeters += segmentMeters
        totalPoints += 1
        inMemoryTrail += GeoPoint(location.latitude, location.longitude)

        val breadcrumb = TrackBreadcrumbEntity(
            trackId = trackId,
            lat = location.latitude,
            lon = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
            accuracyMeters = location.accuracy,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            timestamp = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )

        _recordingState.value = LiveRecordingState(
            isRecording = true,
            trackId = trackId,
            distanceMeters = totalDistanceMeters,
            pointCount = totalPoints,
            livePoints = inMemoryTrail.toList()
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(totalDistanceMeters, totalPoints))

        serviceScope.launch {
            database.trackDao().insertBreadcrumb(breadcrumb)
            database.trackDao().updateTrackStats(trackId, totalDistanceMeters, totalPoints)
        }
    }

    @Deprecated("Required for older Android API compatibility")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun stopRecordingAndShutdown() {
        runCatching { locationManager.removeUpdates(this) }
        val finishedTrackId = activeTrackId
        activeTrackId = null
        lastAcceptedLocation = null

        _recordingState.value = LiveRecordingState(isRecording = false)

        if (finishedTrackId != null) {
            serviceScope.launch {
                database.trackDao().finishTrack(finishedTrackId, System.currentTimeMillis())
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_tracking_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_tracking_desc)
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(distanceMeters: Double, points: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RouteRecordingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val summary = "${GeoMath.formatDistance(distanceMeters)} • $points encrypted pts"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(getString(R.string.notif_tracking_title))
            .setContentText(summary)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openAppIntent)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(this) }
        serviceScope.cancel()
        super.onDestroy()
    }
}
