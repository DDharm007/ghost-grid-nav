package com.example.ghostgrid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_places ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_places WHERE listName = :listName ORDER BY createdAt DESC")
    fun observeByList(listName: String): Flow<List<SavedPlaceEntity>>

    @Query("SELECT DISTINCT listName FROM saved_places ORDER BY listName ASC")
    fun observeListNames(): Flow<List<String>>

    @Query("SELECT * FROM saved_places")
    suspend fun getAllSnapshot(): List<SavedPlaceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: SavedPlaceEntity): Long

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM recorded_tracks ORDER BY startedAt DESC")
    fun observeAllTracks(): Flow<List<RecordedTrackEntity>>

    @Query("SELECT * FROM recorded_tracks WHERE isActive = 1 ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveTrack(): Flow<RecordedTrackEntity?>

    @Query("SELECT * FROM recorded_tracks WHERE isActive = 1 ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveTrack(): RecordedTrackEntity?

    @Query("SELECT * FROM recorded_tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): RecordedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: RecordedTrackEntity): Long

    @Query(
        "UPDATE recorded_tracks SET distanceMeters = :distanceMeters, pointCount = :pointCount WHERE id = :trackId"
    )
    suspend fun updateTrackStats(trackId: Long, distanceMeters: Double, pointCount: Int)

    @Query(
        "UPDATE recorded_tracks SET isActive = 0, endedAt = :endedAt WHERE id = :trackId"
    )
    suspend fun finishTrack(trackId: Long, endedAt: Long)

    @Query("DELETE FROM recorded_tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBreadcrumb(point: TrackBreadcrumbEntity): Long

    @Query("SELECT * FROM track_breadcrumbs WHERE trackId = :trackId ORDER BY timestamp ASC")
    fun observeBreadcrumbs(trackId: Long): Flow<List<TrackBreadcrumbEntity>>

    @Query("SELECT * FROM track_breadcrumbs WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun getBreadcrumbs(trackId: Long): List<TrackBreadcrumbEntity>
}

@Dao
interface CachedPoiDao {
    @Query("SELECT * FROM cached_poi_enrichment WHERE placeKey = :placeKey LIMIT 1")
    suspend fun getByKey(placeKey: String): CachedPoiEnrichmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(enrichment: CachedPoiEnrichmentEntity)

    @Query("DELETE FROM cached_poi_enrichment")
    suspend fun clearAll()
}

@Dao
interface InstalledRegionDao {
    @Query("SELECT * FROM installed_regions ORDER BY installedAt DESC")
    fun observeAll(): Flow<List<InstalledRegionEntity>>

    @Query("SELECT * FROM installed_regions")
    suspend fun getAllSnapshot(): List<InstalledRegionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(region: InstalledRegionEntity)

    @Query("DELETE FROM installed_regions WHERE id = :regionId")
    suspend fun deleteById(regionId: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM encrypted_notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllNotes(): Flow<List<EncryptedNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: EncryptedNoteEntity): Long

    @Query("UPDATE encrypted_notes SET isPinned = :pinned, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM encrypted_notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContactEntity): Long

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ConstellationDao {
    @Query("SELECT * FROM saved_constellations ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<SavedConstellationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SavedConstellationEntity): Long

    @Query("DELETE FROM saved_constellations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
