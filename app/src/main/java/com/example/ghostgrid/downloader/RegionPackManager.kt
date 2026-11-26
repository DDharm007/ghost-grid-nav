package com.example.ghostgrid.downloader

import android.content.Context
import android.net.Uri
import com.example.ghostgrid.data.InstalledRegionDao
import com.example.ghostgrid.data.InstalledRegionEntity
import com.example.ghostgrid.hybrid.StrictOfflineInterceptor
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Manages offline region packs (`<region>-pack.zip`):
 * - Imports local `.zip` archives via Android SAF (zero storage permissions).
 * - Downloads remote release manifests and packs over HTTPS (blocked when Strict Offline Mode is ON).
 * - Verifies SHA-256 checksums before installation.
 * - Extracts atomically into app-private `context.noBackupFilesDir/regions/<region_id>/`
 *   with strict Zip-Slip path traversal confinement.
 */
class RegionPackManager(
    private val context: Context,
    private val installedRegionDao: InstalledRegionDao,
    private val isStrictOfflineEnabled: () -> Boolean
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val manifestAdapter = moshi.adapter(RegionPackManifest::class.java)
    private val catalogAdapter = moshi.adapter(RemoteRegionCatalog::class.java)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(StrictOfflineInterceptor(isStrictOfflineEnabled))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _progress = MutableStateFlow<PackInstallProgress>(PackInstallProgress.Idle)
    val progress: StateFlow<PackInstallProgress> = _progress.asStateFlow()

    val regionsRootDir: File
        get() = File(context.noBackupFilesDir, "regions").apply { mkdirs() }

    companion object {
        private const val MAX_ZIP_ENTRIES = 10_000
        private const val MAX_EXTRACTED_BYTES = 4L * 1024L * 1024L * 1024L // 4 GB cap
        private val SAFE_REGION_ID_REGEX = Regex("^[a-z0-9_-]{1,64}$")
    }

    suspend fun fetchRemoteCatalog(manifestUrl: String): Result<RemoteRegionCatalog> = withContext(Dispatchers.IO) {
        runCatching {
            validateSafeHttpsUrl(manifestUrl)
            val req = Request.Builder().url(manifestUrl).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Manifest fetch failed with HTTP ${resp.code}.")
                }
                val json = resp.body?.string().orEmpty()
                catalogAdapter.fromJson(json)
                    ?: throw IllegalStateException("Invalid region catalog JSON.")
            }
        }
    }

    suspend fun downloadAndInstallPack(entry: RemoteRegionEntry): Result<InstalledRegionEntity> =
        withContext(Dispatchers.IO) {
            val tempZip = File(context.cacheDir, "download_${sanitizeRegionId(entry.id)}.zip")
            try {
                validateSafeHttpsUrl(entry.downloadUrl)
                _progress.value = PackInstallProgress.Downloading(entry.name, 0)

                val req = Request.Builder().url(entry.downloadUrl).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Pack download failed (HTTP ${resp.code}).")
                    }
                    val body = resp.body ?: throw IllegalStateException("Empty download body.")
                    val totalBytes = if (body.contentLength() > 0) body.contentLength() else entry.sizeBytes

                    val digest = MessageDigest.getInstance("SHA-256")
                    DigestInputStream(body.byteStream(), digest).use { dis ->
                        FileOutputStream(tempZip).use { fos ->
                            val buffer = ByteArray(32 * 1024)
                            var downloaded = 0L
                            var read: Int
                            while (dis.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0) {
                                    val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    _progress.value = PackInstallProgress.Downloading(entry.name, pct)
                                }
                            }
                        }
                    }

                    _progress.value = PackInstallProgress.Verifying(entry.name)
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (entry.sha256.isNotBlank() && !actualSha.equals(entry.sha256.trim(), ignoreCase = true)) {
                        throw SecurityException("SHA-256 checksum mismatch! Download rejected for safety.")
                    }

                    val installed = extractAndRegisterZip(
                        zipFile = tempZip,
                        fallbackId = entry.id,
                        fallbackName = entry.name,
                        computedSha256 = actualSha
                    )
                    _progress.value = PackInstallProgress.Completed(installed.name)
                    Result.success(installed)
                }
            } catch (e: Exception) {
                _progress.value = PackInstallProgress.Failed(e.message ?: "Pack download failed.")
                Result.failure(e)
            } finally {
                tempZip.delete()
            }
        }

    suspend fun importLocalPackZip(uri: Uri, expectedSha256: String? = null): Result<InstalledRegionEntity> =
        withContext(Dispatchers.IO) {
            val tempZip = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
            try {
                _progress.value = PackInstallProgress.Verifying("Local Region Pack")
                val digest = MessageDigest.getInstance("SHA-256")
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open selected archive.")

                input.use { rawStream ->
                    DigestInputStream(BufferedInputStream(rawStream), digest).use { dis ->
                        FileOutputStream(tempZip).use { fos ->
                            dis.copyTo(fos, bufferSize = 32 * 1024)
                        }
                    }
                }

                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (!expectedSha256.isNullOrBlank() && !actualSha.equals(expectedSha256.trim(), ignoreCase = true)) {
                    throw SecurityException("Local pack SHA-256 verification failed.")
                }

                val fallbackSlug = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.removeSuffix("-pack.zip")
                    ?.removeSuffix(".zip")
                    ?: "region_${System.currentTimeMillis()}"

                val installed = extractAndRegisterZip(
                    zipFile = tempZip,
                    fallbackId = fallbackSlug,
                    fallbackName = fallbackSlug.replace('-', ' ').replace('_', ' ')
                        .replaceFirstChar { it.uppercase() },
                    computedSha256 = actualSha
                )
                _progress.value = PackInstallProgress.Completed(installed.name)
                Result.success(installed)
            } catch (e: Exception) {
                _progress.value = PackInstallProgress.Failed(e.message ?: "Local pack import failed.")
                Result.failure(e)
            } finally {
                tempZip.delete()
            }
        }

    private suspend fun extractAndRegisterZip(
        zipFile: File,
        fallbackId: String,
        fallbackName: String,
        computedSha256: String
    ): InstalledRegionEntity {
        _progress.value = PackInstallProgress.Extracting(fallbackName)

        val stagingDir = File(context.noBackupFilesDir, "staging_${System.currentTimeMillis()}")
        stagingDir.mkdirs()
        val stagingCanonical = stagingDir.canonicalPath

        try {
            var entryCount = 0
            var totalWritten = 0L

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(32 * 1024)
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        throw SecurityException("Archive exceeds maximum safe entry count.")
                    }

                    val targetFile = File(stagingDir, entry.name).canonicalFile
                    if (!targetFile.path.startsWith(stagingCanonical + File.separator)) {
                        throw SecurityException("Blocked path traversal entry in archive: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            var len: Int
                            while (zis.read(buffer).also { len = it } != -1) {
                                totalWritten += len
                                if (totalWritten > MAX_EXTRACTED_BYTES) {
                                    throw SecurityException("Archive exceeds safe uncompressed limit.")
                                }
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Support zips that wrap assets in a single top-level directory
            val contentRoot = unwrapSingleNestedDir(stagingDir)

            val manifestFile = File(contentRoot, "manifest.json")
            val manifest = if (manifestFile.exists()) {
                manifestAdapter.fromJson(manifestFile.readText())
            } else null

            val regionId = sanitizeRegionId(manifest?.id ?: fallbackId)
            val regionName = manifest?.name?.takeIf { it.isNotBlank() } ?: fallbackName

            val pmtilesFile = File(contentRoot, "map.pmtiles")
            val placesDbFile = File(contentRoot, "places.db")
            val graphDir = File(contentRoot, "routing-graph")

            if (!pmtilesFile.exists() && !placesDbFile.exists() && !graphDir.exists()) {
                throw IllegalArgumentException(
                    "Invalid GhostGrid pack: archive must include map.pmtiles, places.db, or routing-graph/."
                )
            }

            val finalDir = File(regionsRootDir, regionId)
            val backupDir = File(regionsRootDir, "${regionId}_old_${System.currentTimeMillis()}")

            if (finalDir.exists()) {
                finalDir.renameTo(backupDir)
            }
            if (!contentRoot.renameTo(finalDir)) {
                contentRoot.copyRecursively(finalDir, overwrite = true)
            }
            backupDir.deleteRecursively()

            val dirSize = calculateDirectorySize(finalDir)
            val entity = InstalledRegionEntity(
                id = regionId,
                name = regionName,
                version = manifest?.version ?: "1.0.0",
                minLon = manifest?.minLon ?: -180.0,
                minLat = manifest?.minLat ?: -85.0,
                maxLon = manifest?.maxLon ?: 180.0,
                maxLat = manifest?.maxLat ?: 85.0,
                sizeBytes = dirSize,
                sha256 = computedSha256,
                hasRoutingGraph = File(finalDir, "routing-graph").isDirectory,
                hasPlacesDb = File(finalDir, "places.db").isFile,
                installedAt = System.currentTimeMillis()
            )
            installedRegionDao.upsert(entity)
            return entity
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    suspend fun installBuiltInRegionPack(
        pack: BuiltInRegionPack,
        liveFetcher: suspend (BuiltInRegionPack) -> List<com.example.ghostgrid.search.PlaceResult>
    ): Result<InstalledRegionEntity> = withContext(Dispatchers.IO) {
        val regionId = sanitizeRegionId(pack.id)
        val stagingDir = File(context.noBackupFilesDir, "staging_${regionId}_${System.currentTimeMillis()}")
        stagingDir.mkdirs()
        try {
            _progress.value = PackInstallProgress.Downloading(pack.name, 20)

            val livePlaces = if (!isStrictOfflineEnabled()) {
                runCatching { liveFetcher(pack) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            _progress.value = PackInstallProgress.Downloading(pack.name, 70)

            val mergedPlaces = buildList {
                pack.seedPlaces.forEachIndexed { idx, seed ->
                    add(
                        com.example.ghostgrid.search.PlaceResult(
                            id = "${regionId}:seed_$idx",
                            name = seed.name,
                            category = seed.category,
                            subcategory = seed.subcategory,
                            address = seed.address,
                            lat = seed.lat,
                            lon = seed.lon,
                            distanceMeters = 0.0,
                            regionId = regionId
                        )
                    )
                }
                addAll(livePlaces)
            }.distinctBy { "${it.name.lowercase().take(24)}_${String.format("%.3f_%.3f", it.lat, it.lon)}" }

            _progress.value = PackInstallProgress.Extracting(pack.name)

            val dbFile = File(stagingDir, "places.db")
            buildOfflinePlacesDb(dbFile, mergedPlaces)

            val manifest = RegionPackManifest(
                id = regionId,
                name = pack.name,
                version = "2026.1",
                minLon = pack.minLon,
                minLat = pack.minLat,
                maxLon = pack.maxLon,
                maxLat = pack.maxLat,
                hasRoutingGraph = false,
                hasPlacesDb = true
            )
            File(stagingDir, "manifest.json").writeText(manifestAdapter.toJson(manifest))

            _progress.value = PackInstallProgress.Verifying(pack.name)
            val sha256 = computeFileSha256(dbFile)

            val finalDir = File(regionsRootDir, regionId)
            val backupDir = File(regionsRootDir, "${regionId}_old_${System.currentTimeMillis()}")
            if (finalDir.exists()) finalDir.renameTo(backupDir)
            if (!stagingDir.renameTo(finalDir)) {
                stagingDir.copyRecursively(finalDir, overwrite = true)
            }
            backupDir.deleteRecursively()

            val dirSize = calculateDirectorySize(finalDir)
            val entity = InstalledRegionEntity(
                id = regionId,
                name = pack.name,
                version = "2026.1 • ${mergedPlaces.size} places",
                minLon = pack.minLon,
                minLat = pack.minLat,
                maxLon = pack.maxLon,
                maxLat = pack.maxLat,
                sizeBytes = dirSize,
                sha256 = sha256,
                hasRoutingGraph = false,
                hasPlacesDb = true,
                installedAt = System.currentTimeMillis()
            )
            installedRegionDao.upsert(entity)
            _progress.value = PackInstallProgress.Completed(pack.name)
            Result.success(entity)
        } catch (e: Exception) {
            _progress.value = PackInstallProgress.Failed(e.message ?: "Could not install ${pack.name}")
            Result.failure(e)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun buildOfflinePlacesDb(
        dbFile: File,
        places: List<com.example.ghostgrid.search.PlaceResult>
    ) {
        val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.beginTransaction()
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS places (
                    id TEXT UNIQUE,
                    name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    subcategory TEXT NOT NULL,
                    address TEXT NOT NULL,
                    lat REAL NOT NULL,
                    lon REAL NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_cat_coords ON places(category, lat, lon)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_name ON places(name)")

            val stmt = db.compileStatement(
                "INSERT OR REPLACE INTO places (id, name, category, subcategory, address, lat, lon) VALUES (?, ?, ?, ?, ?, ?, ?)"
            )
            for (p in places) {
                stmt.clearBindings()
                stmt.bindString(1, p.id)
                stmt.bindString(2, p.name)
                stmt.bindString(3, p.category)
                stmt.bindString(4, p.subcategory)
                stmt.bindString(5, p.address)
                stmt.bindDouble(6, p.lat)
                stmt.bindDouble(7, p.lon)
                stmt.executeInsert()
            }
            stmt.close()

            // Opportunistically build FTS5 + R-Tree virtual tables if device SQLite supports them
            runCatching {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS places_fts USING fts5(name, address, category, subcategory, content='places', content_rowid='rowid')"
                )
                db.execSQL(
                    "INSERT INTO places_fts(rowid, name, address, category, subcategory) SELECT rowid, name, address, category, subcategory FROM places"
                )
            }
            runCatching {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS places_rtree USING rtree(id, min_lon, max_lon, min_lat, max_lat)"
                )
                db.execSQL(
                    "INSERT INTO places_rtree(id, min_lon, max_lon, min_lat, max_lat) SELECT rowid, lon, lon, lat, lat FROM places"
                )
            }

            db.setTransactionSuccessful()
        } finally {
            runCatching { db.endTransaction() }
            runCatching { db.close() }
        }
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16 * 1024)
            var len: Int
            while (fis.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun deleteRegion(regionId: String) = withContext(Dispatchers.IO) {
        val safeId = sanitizeRegionId(regionId)
        val dir = File(regionsRootDir, safeId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        installedRegionDao.deleteById(safeId)
    }

    fun resetProgress() {
        _progress.value = PackInstallProgress.Idle
    }

    private fun unwrapSingleNestedDir(root: File): File {
        val children = root.listFiles()?.filter { !it.name.startsWith("__MACOSX") }.orEmpty()
        return if (children.size == 1 && children[0].isDirectory) children[0] else root
    }

    private fun sanitizeRegionId(raw: String): String {
        val normalized = raw.lowercase().replace(Regex("[^a-z0-9_-]"), "_").take(64)
        return if (SAFE_REGION_ID_REGEX.matches(normalized)) normalized else "region_${System.currentTimeMillis()}"
    }

    private fun calculateDirectorySize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun validateSafeHttpsUrl(rawUrl: String) {
        val uri = URI(rawUrl.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Only HTTPS URLs are permitted for region pack downloads."
        }
        val host = uri.host?.lowercase().orEmpty()
        require(host.isNotBlank()) { "Download URL is missing a valid host." }
        require(host != "localhost" && !host.endsWith(".local")) {
            "Localhost URLs are not allowed."
        }
        val addr = runCatching { InetAddress.getByName(host) }.getOrNull()
        if (addr != null) {
            require(!addr.isLoopbackAddress && !addr.isSiteLocalAddress && !addr.isLinkLocalAddress) {
                "Private or loopback network destinations are blocked."
            }
        }
    }
}
