package com.example.ghostgrid.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_places",
    indices = [Index("listName"), Index("category")]
)
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val listName: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recorded_tracks")
data class RecordedTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
    val pointCount: Int = 0,
    val isActive: Boolean = true
)

@Entity(
    tableName = "track_breadcrumbs",
    foreignKeys = [
        ForeignKey(
            entity = RecordedTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackId"), Index("timestamp")]
)
data class TrackBreadcrumbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val accuracyMeters: Float = 0f,
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_poi_enrichment")
data class CachedPoiEnrichmentEntity(
    @PrimaryKey val placeKey: String,
    val placeName: String,
    val rating: Double?,
    val userRatingCount: Int?,
    val isOpenNow: Boolean?,
    val weekdayHours: String,
    val topReviews: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "installed_regions")
data class InstalledRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val sizeBytes: Long,
    val sha256: String,
    val hasRoutingGraph: Boolean,
    val hasPlacesDb: Boolean,
    val installedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "encrypted_notes",
    indices = [Index("tag"), Index("updatedAt")]
)
data class EncryptedNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val tag: String = "General",
    val isPinned: Boolean = false,
    val attachedLat: Double? = null,
    val attachedLon: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relation: String,
    val phoneNumber: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "saved_constellations",
    indices = [Index("capturedAt")]
)
data class SavedConstellationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val constellationName: String,
    val navigationHint: String,
    val azimuthDeg: Float,
    val cardinalDirection: String,
    val altitudeDeg: Float,
    val starsDetected: Int,
    val moonAzimuthDeg: Float,
    val moonAltitudeDeg: Float,
    val moonPhaseLabel: String,
    val lat: Double,
    val lon: Double,
    val photoPath: String = "",
    val capturedAt: Long = System.currentTimeMillis()
)
