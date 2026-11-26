package com.example.ghostgrid.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.ghostgrid.data.SavedPlaceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-region Offline Search Engine querying:
 * 1. All installed regional `places.db` files inside `context.noBackupFilesDir/regions/<id>/places.db`
 *    using parameterized SQLite FTS5 prefix queries combined with R-Tree spatial filtering
 *    and Haversine proximity sorting.
 * 2. The SQLCipher-encrypted Saved Places Room table.
 * 3. Direct geographic coordinate queries (e.g. "23.0225, 72.5714").
 */
class OfflinePlacesSearchEngine(
    private val context: Context,
    private val savedPlaceDao: SavedPlaceDao
) {
    private val poolMutex = Mutex()
    private val openDatabases = LinkedHashMap<String, SQLiteDatabase>()

    suspend fun reloadRegionPools() = withContext(Dispatchers.IO) {
        poolMutex.withLock {
            openDatabases.values.forEach { db -> runCatching { db.close() } }
            openDatabases.clear()

            val regionsRoot = File(context.noBackupFilesDir, "regions")
            if (!regionsRoot.exists() || !regionsRoot.isDirectory) return@withLock

            regionsRoot.listFiles()?.forEach { regionDir ->
                if (!regionDir.isDirectory) return@forEach
                val placesDbFile = File(regionDir, "places.db")
                if (placesDbFile.exists() && placesDbFile.isFile && placesDbFile.length() > 0L) {
                    runCatching {
                        val db = SQLiteDatabase.openDatabase(
                            placesDbFile.absolutePath,
                            null,
                            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                        )
                        openDatabases[regionDir.name] = db
                    }
                }
            }
        }
    }

    suspend fun getActiveRegionDbCount(): Int = poolMutex.withLock { openDatabases.size }

    suspend fun search(
        query: String,
        categoryFilter: AmenityCategory?,
        userLat: Double,
        userLon: Double,
        radiusKm: Double = 35.0,
        limit: Int = 40
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val results = mutableListOf<PlaceResult>()

        // 1. Direct coordinate jump (always works globally offline)
        val parsedCoord = GeoMath.parseCoordinateQuery(trimmed)
        if (parsedCoord != null) {
            val dist = GeoMath.haversineMeters(userLat, userLon, parsedCoord.lat, parsedCoord.lon)
            results += PlaceResult(
                id = "coord:${parsedCoord.lat},${parsedCoord.lon}",
                name = String.format("%.5f, %.5f", parsedCoord.lat, parsedCoord.lon),
                category = "coordinate",
                subcategory = "GPS Coordinate",
                address = "Direct offline coordinate pin",
                lat = parsedCoord.lat,
                lon = parsedCoord.lon,
                distanceMeters = dist,
                regionId = "global"
            )
        }

        // 2. Encrypted Saved Places search (always works offline)
        val savedPlaces = savedPlaceDao.getAllSnapshot()
        val matchingSaved = savedPlaces.filter { sp ->
            val catOk = categoryFilter == null ||
                categoryFilter == AmenityCategory.SAVED ||
                sp.category.equals(categoryFilter.dbKey, ignoreCase = true)
            val textOk = trimmed.isEmpty() ||
                sp.name.contains(trimmed, ignoreCase = true) ||
                sp.address.contains(trimmed, ignoreCase = true) ||
                sp.listName.contains(trimmed, ignoreCase = true)
            catOk && textOk
        }.map { sp ->
            PlaceResult(
                id = "saved:${sp.id}",
                name = sp.name,
                category = sp.category,
                subcategory = sp.listName,
                address = sp.address,
                lat = sp.lat,
                lon = sp.lon,
                distanceMeters = GeoMath.haversineMeters(userLat, userLon, sp.lat, sp.lon),
                regionId = "vault",
                isSaved = true,
                savedListName = sp.listName
            )
        }
        results += matchingSaved

        if (categoryFilter == AmenityCategory.SAVED) {
            return@withContext results.sortedBy { it.distanceMeters }.take(limit)
        }

        // 3. Query all active regional SQLite pools
        val ftsQuery = buildSafeFtsPrefixQuery(trimmed)
        val bbox = GeoMath.boundingBoxAround(userLat, userLon, radiusKm)

        val dbsSnapshot: List<Pair<String, SQLiteDatabase>> = poolMutex.withLock {
            openDatabases.entries.map { it.key to it.value }
        }

        for ((regionId, db) in dbsSnapshot) {
            if (!db.isOpen) continue
            runCatching {
                results += querySingleRegionDb(
                    regionId = regionId,
                    db = db,
                    rawQuery = trimmed,
                    ftsQuery = ftsQuery,
                    categoryKey = categoryFilter?.dbKey,
                    bbox = bbox,
                    userLat = userLat,
                    userLon = userLon,
                    limit = limit
                )
            }
        }

        results
            .distinctBy { it.id }
            .sortedBy { it.distanceMeters }
            .take(limit)
    }

    private fun hasTable(db: SQLiteDatabase, tableName: String): Boolean {
        return runCatching {
            db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE name = ? LIMIT 1",
                arrayOf(tableName)
            ).use { it.moveToFirst() }
        }.getOrDefault(false)
    }

    private fun querySingleRegionDb(
        regionId: String,
        db: SQLiteDatabase,
        rawQuery: String,
        ftsQuery: String?,
        categoryKey: String?,
        bbox: DoubleArray,
        userLat: Double,
        userLon: Double,
        limit: Int
    ): List<PlaceResult> {
        val (minLat, maxLat, minLon, maxLon) = bbox
        val useFts = hasTable(db, "places_fts")
        val useRtree = hasTable(db, "places_rtree")

        val primary = if (useFts && useRtree) {
            runCatching {
                queryWithFtsAndRtree(regionId, db, ftsQuery, categoryKey, minLat, maxLat, minLon, maxLon, userLat, userLon, limit)
            }.getOrNull()
        } else null

        if (primary != null && primary.isNotEmpty()) return primary

        // Reliable standard indexed query across all Android SQLite builds
        return queryStandardPlacesTable(regionId, db, rawQuery, categoryKey, minLat, maxLat, minLon, maxLon, userLat, userLon, limit)
    }

    private fun queryWithFtsAndRtree(
        regionId: String,
        db: SQLiteDatabase,
        ftsQuery: String?,
        categoryKey: String?,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        userLat: Double,
        userLon: Double,
        limit: Int
    ): List<PlaceResult> {
        val sql: String
        val args: Array<String>

        when {
            ftsQuery != null && categoryKey != null -> {
                sql = """
                    SELECT p.id, p.name, p.category, p.subcategory, p.address, p.lat, p.lon
                    FROM places_fts f
                    JOIN places p ON p.rowid = f.rowid
                    JOIN places_rtree r ON r.id = p.rowid
                    WHERE places_fts MATCH ?
                      AND p.category = ?
                      AND r.max_lon >= ? AND r.min_lon <= ?
                      AND r.max_lat >= ? AND r.min_lat <= ?
                    LIMIT ?
                """.trimIndent()
                args = arrayOf(
                    ftsQuery,
                    categoryKey,
                    minLon.toString(),
                    maxLon.toString(),
                    minLat.toString(),
                    maxLat.toString(),
                    (limit * 2).toString()
                )
            }

            ftsQuery != null -> {
                sql = """
                    SELECT p.id, p.name, p.category, p.subcategory, p.address, p.lat, p.lon
                    FROM places_fts f
                    JOIN places p ON p.rowid = f.rowid
                    WHERE places_fts MATCH ?
                    LIMIT ?
                """.trimIndent()
                args = arrayOf(ftsQuery, (limit * 3).toString())
            }

            categoryKey != null -> {
                sql = """
                    SELECT p.id, p.name, p.category, p.subcategory, p.address, p.lat, p.lon
                    FROM places_rtree r
                    JOIN places p ON p.rowid = r.id
                    WHERE p.category = ?
                      AND r.max_lon >= ? AND r.min_lon <= ?
                      AND r.max_lat >= ? AND r.min_lat <= ?
                    LIMIT ?
                """.trimIndent()
                args = arrayOf(
                    categoryKey,
                    minLon.toString(),
                    maxLon.toString(),
                    minLat.toString(),
                    maxLat.toString(),
                    (limit * 3).toString()
                )
            }

            else -> return emptyList()
        }

        return readCursor(db, sql, args, regionId, userLat, userLon)
    }

    private fun queryStandardPlacesTable(
        regionId: String,
        db: SQLiteDatabase,
        rawQuery: String,
        categoryKey: String?,
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        userLat: Double,
        userLon: Double,
        limit: Int
    ): List<PlaceResult> {
        val likePattern = "%${rawQuery.lowercase()}%"
        val sql: String
        val args: Array<String>

        when {
            rawQuery.isNotBlank() && categoryKey != null -> {
                sql = """
                    SELECT id, name, category, subcategory, address, lat, lon
                    FROM places
                    WHERE category = ?
                      AND (LOWER(name) LIKE ? OR LOWER(address) LIKE ? OR LOWER(subcategory) LIKE ?)
                    LIMIT ?
                """.trimIndent()
                args = arrayOf(categoryKey, likePattern, likePattern, likePattern, (limit * 2).toString())
            }

            rawQuery.isNotBlank() -> {
                sql = """
                    SELECT id, name, category, subcategory, address, lat, lon
                    FROM places
                    WHERE LOWER(name) LIKE ? OR LOWER(address) LIKE ? OR LOWER(subcategory) LIKE ? OR LOWER(category) LIKE ?
                    LIMIT ?
                """.trimIndent()
                args = arrayOf(likePattern, likePattern, likePattern, likePattern, (limit * 3).toString())
            }

            categoryKey != null -> {
                // First try nearby bounding box, then broaden across installed region if sparse
                val nearSql = """
                    SELECT id, name, category, subcategory, address, lat, lon
                    FROM places
                    WHERE category = ?
                      AND lat BETWEEN ? AND ?
                      AND lon BETWEEN ? AND ?
                    LIMIT ?
                """.trimIndent()
                val nearArgs = arrayOf(
                    categoryKey,
                    minLat.toString(),
                    maxLat.toString(),
                    minLon.toString(),
                    maxLon.toString(),
                    (limit * 3).toString()
                )
                val near = readCursor(db, nearSql, nearArgs, regionId, userLat, userLon)
                if (near.isNotEmpty()) return near

                sql = """
                    SELECT id, name, category, subcategory, address, lat, lon
                    FROM places
                    WHERE category = ?
                    LIMIT ?
                """.trimIndent()
                args = arrayOf(categoryKey, (limit * 2).toString())
            }

            else -> return emptyList()
        }

        return readCursor(db, sql, args, regionId, userLat, userLon)
    }

    private fun readCursor(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>,
        regionId: String,
        userLat: Double,
        userLon: Double
    ): List<PlaceResult> {
        val out = mutableListOf<PlaceResult>()
        db.rawQuery(sql, args).use { cursor ->
            while (cursor.moveToNext()) {
                val lat = cursor.getDouble(5)
                val lon = cursor.getDouble(6)
                out += PlaceResult(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    category = cursor.getString(2),
                    subcategory = cursor.getString(3),
                    address = cursor.getString(4),
                    lat = lat,
                    lon = lon,
                    distanceMeters = GeoMath.haversineMeters(userLat, userLon, lat, lon),
                    regionId = regionId
                )
            }
        }
        return out
    }

    /**
     * Sanitizes user input into a safe SQLite FTS5 prefix expression (`token1* token2*`)
     * preventing FTS5 operator injection or syntax crashes.
     */
    private fun buildSafeFtsPrefixQuery(rawQuery: String): String? {
        val tokens = rawQuery
            .lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .map { it.trim() }
            .filter { it.length >= 2 }
            .take(6)

        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { "$it*" }
    }

    suspend fun closeAll() = withContext(Dispatchers.IO) {
        poolMutex.withLock {
            openDatabases.values.forEach { runCatching { it.close() } }
            openDatabases.clear()
        }
    }
}
