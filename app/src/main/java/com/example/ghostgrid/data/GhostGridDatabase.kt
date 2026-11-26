package com.example.ghostgrid.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ghostgrid.security.KeystoreKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

/**
 * SQLCipher AES-256 encrypted Room database storing all personal GhostGrid data:
 * - Encrypted GPS route history & breadcrumbs
 * - Saved place lists (Favorites, Food Spots, Hospitals, Custom Lists)
 * - Locally cached Hybrid POI enrichments
 * - Installed offline region pack inventory
 * - Saved celestial constellation sightings & Moon bearings
 *
 * Stored exclusively in `noBackupFilesDir` with a 256-bit passphrase wrapped by Android Keystore.
 */
@Database(
    entities = [
        SavedPlaceEntity::class,
        RecordedTrackEntity::class,
        TrackBreadcrumbEntity::class,
        CachedPoiEnrichmentEntity::class,
        InstalledRegionEntity::class,
        EncryptedNoteEntity::class,
        EmergencyContactEntity::class,
        SavedConstellationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class GhostGridDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun trackDao(): TrackDao
    abstract fun cachedPoiDao(): CachedPoiDao
    abstract fun installedRegionDao(): InstalledRegionDao
    abstract fun noteDao(): NoteDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun constellationDao(): ConstellationDao

    companion object {
        private const val DB_FILENAME = "ghostgrid_vault.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `saved_constellations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `constellationName` TEXT NOT NULL,
                        `navigationHint` TEXT NOT NULL,
                        `azimuthDeg` REAL NOT NULL,
                        `cardinalDirection` TEXT NOT NULL,
                        `altitudeDeg` REAL NOT NULL,
                        `starsDetected` INTEGER NOT NULL,
                        `moonAzimuthDeg` REAL NOT NULL,
                        `moonAltitudeDeg` REAL NOT NULL,
                        `moonPhaseLabel` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `photoPath` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_saved_constellations_capturedAt` ON `saved_constellations` (`capturedAt`)"
                )
            }
        }

        @Volatile
        private var instance: GhostGridDatabase? = null

        fun getInstance(context: Context): GhostGridDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildEncryptedDatabase(context.applicationContext).also {
                    instance = it
                }
            }
        }

        private fun buildEncryptedDatabase(appContext: Context): GhostGridDatabase {
            System.loadLibrary("sqlcipher")

            val keyManager = KeystoreKeyManager(appContext)
            val passphrase = keyManager.getOrCreateSqlCipherPassphrase()
            val factory = SupportOpenHelperFactory(passphrase)

            val dbDir = File(appContext.noBackupFilesDir, "vault").apply { mkdirs() }
            val dbFile = File(dbDir, DB_FILENAME)

            return Room.databaseBuilder(
                appContext,
                GhostGridDatabase::class.java,
                dbFile.absolutePath
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }
}
