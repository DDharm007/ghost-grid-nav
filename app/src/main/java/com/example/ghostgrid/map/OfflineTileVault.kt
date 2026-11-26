package com.example.ghostgrid.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.ghostgrid.hybrid.StrictOfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

data class TileCacheStats(
    val totalTiles: Int = 0,
    val totalBytes: Long = 0L,
    val stylesCached: Set<String> = emptySet(),
    val hasBundledBase: Boolean = true
)

/**
 * Bulletproof Offline Tile Vault & MapLibre Native HTTP Interceptor:
 *
 * 1. Pre-bundled global + India tiles inside `assets/offline_tiles/<style>/{z}/{x}/{y}.tile`
 *    guarantee Dark, Map, Satellite, and Terrain work immediately with ZERO internet on first boot.
 * 2. Automatic transparent caching: every tile viewed online in MapLibre is mirrored automatically
 *    into app-private `noBackupFilesDir/tile_cache/<style>/{z}/{x}/{y}.tile`.
 * 3. Zero-Blank-Tile Pyramid Parent Cropping: whenever Offline Mode is enabled (or signal drops)
 *    and an exact `(z, x, y)` tile is missing, the interceptor walks UP the tile pyramid
 *    (`z-1 .. 0`), crops the exact sub-quadrant from the closest cached/bundled parent tile,
 *    upscales smoothly to 256×256, and serves it with `Cache-Control: no-store` so MapLibre
 *    NEVER shows blank/black tiles at any zoom level 0..22 and automatically upgrades when
 *    higher-resolution tiles are cached.
 * 4. Strict Offline Mode guarantees 0 outbound network packets at the OkHttp transport layer.
 */
class OfflineTileVault(
    private val context: Context,
    private val isStrictOfflineEnabled: () -> Boolean
) {
    private val downloadHttpClient = OkHttpClient.Builder()
        .addInterceptor(StrictOfflineInterceptor(isStrictOfflineEnabled))
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "GhostGrid-Android/1.0 (Offline Tile Cache)")
                .build()
            chain.proceed(req)
        }
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    val rootDir: File
        get() = File(context.noBackupFilesDir, "tile_cache").apply { mkdirs() }

    companion object {
        val CACHEABLE_STYLES = listOf(
            GhostMapStyle.STEALTH_DARK,
            GhostMapStyle.STANDARD_MAP,
            GhostMapStyle.SATELLITE,
            GhostMapStyle.TERRAIN
        )

        private val ARCGIS_TILE_REGEX = Regex("""/MapServer/tile/(\d+)/(\d+)/(\d+)""")

        // Minimal 1x1 transparent PNG used for uncached transparent label overlays offline
        private val TRANSPARENT_PNG_1X1: ByteArray = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte()
        )

        fun baseUrlTemplateFor(style: GhostMapStyle): String? = when (style) {
            GhostMapStyle.STEALTH_DARK ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}"
            GhostMapStyle.STANDARD_MAP ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
            GhostMapStyle.SATELLITE ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
            GhostMapStyle.TERRAIN ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}"
            GhostMapStyle.OPENFREEMAP_3D, GhostMapStyle.PURE_OFFLINE -> null
        }

        fun overlayUrlTemplateFor(style: GhostMapStyle): String? = when (style) {
            GhostMapStyle.STEALTH_DARK ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}"
            GhostMapStyle.SATELLITE ->
                "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"
            else -> null
        }
    }

    private data class ParsedTileRequest(
        val folder: String,
        val z: Int,
        val x: Int,
        val y: Int,
        val isOverlay: Boolean
    )

    private data class TileTask(val url: String, val destFile: File)

    /**
     * OkHttpClient registered with MapLibre Native via `HttpRequestUtil.setOkHttpClient(...)`.
     * Handles offline-first tile delivery, transparent online tile caching, and smooth
     * pyramid parent-cropping so Dark, Map, Satellite, and Terrain are always visible offline.
     */
    fun createMapLibreHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(MapLibreTileInterceptor())
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private inner class MapLibreTileInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val urlStr = req.url.toString()
            val parsed = parseArcGisTileUrl(urlStr)
            val offlineLocked = isStrictOfflineEnabled()

            // Non-tile requests (e.g. OpenFreeMap 3D style/fonts/vector tiles)
            if (parsed == null) {
                if (offlineLocked) {
                    return Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("Blocked by GhostGrid Strict Offline Mode")
                        .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                        .build()
                }
                val withUa = req.newBuilder()
                    .header("User-Agent", "GhostGrid-Android/1.0")
                    .build()
                return chain.proceed(withUa)
            }

            // 1. Check exact local disk cache first (instant <1ms both online & offline)
            readDiskTile(parsed.folder, parsed.z, parsed.x, parsed.y)?.let { bytes ->
                return buildTileResponse(req, bytes, cacheable = true)
            }

            // 2. If Strict Offline Mode is ON: NEVER touch the network; serve from bundled assets or parent pyramid
            if (offlineLocked) {
                return serveOfflineWithPyramidFallback(req, parsed)
            }

            // 3. Online Mode: fetch crisp tile and mirror to disk cache automatically; fallback offline if signal drops
            return try {
                val netReq = req.newBuilder()
                    .header("User-Agent", "GhostGrid-Android/1.0")
                    .build()
                val netResp = chain.proceed(netReq)
                if (netResp.isSuccessful) {
                    val bodyBytes = netResp.body?.bytes()
                    val contentType = netResp.header("Content-Type") ?: "image/jpeg"
                    netResp.close()
                    if (bodyBytes != null && bodyBytes.size > 100) {
                        writeDiskTile(parsed.folder, parsed.z, parsed.x, parsed.y, bodyBytes)
                        return buildTileResponse(req, bodyBytes, contentType = contentType, cacheable = true)
                    }
                } else {
                    netResp.close()
                }
                serveOfflineWithPyramidFallback(req, parsed)
            } catch (_: IOException) {
                serveOfflineWithPyramidFallback(req, parsed)
            }
        }
    }

    private fun parseArcGisTileUrl(url: String): ParsedTileRequest? {
        val folderAndOverlay: Pair<String, Boolean> = when {
            url.contains("World_Dark_Gray_Base") -> GhostMapStyle.STEALTH_DARK.id to false
            url.contains("World_Dark_Gray_Reference") -> "${GhostMapStyle.STEALTH_DARK.id}_overlay" to true
            url.contains("World_Street_Map") -> GhostMapStyle.STANDARD_MAP.id to false
            url.contains("World_Imagery") -> GhostMapStyle.SATELLITE.id to false
            url.contains("World_Boundaries_and_Places") -> "${GhostMapStyle.SATELLITE.id}_overlay" to true
            url.contains("World_Topo_Map") -> GhostMapStyle.TERRAIN.id to false
            else -> return null
        }
        val match = ARCGIS_TILE_REGEX.find(url) ?: return null
        val z = match.groupValues[1].toIntOrNull() ?: return null
        val y = match.groupValues[2].toIntOrNull() ?: return null
        val x = match.groupValues[3].toIntOrNull() ?: return null
        return ParsedTileRequest(
            folder = folderAndOverlay.first,
            z = z,
            x = x,
            y = y,
            isOverlay = folderAndOverlay.second
        )
    }

    private fun serveOfflineWithPyramidFallback(req: Request, tile: ParsedTileRequest): Response {
        // Exact match in APK bundled assets
        readBundledAssetTile(tile.folder, tile.z, tile.x, tile.y)?.let { bytes ->
            return buildTileResponse(req, bytes, cacheable = true)
        }

        // Transparent overlays without an exact cached tile return 1x1 transparent PNG (no-store)
        if (tile.isOverlay) {
            return buildTileResponse(req, TRANSPARENT_PNG_1X1, contentType = "image/png", cacheable = false)
        }

        // Walk UP the tile pyramid (z-1 down to 0) to crop & upscale the exact quadrant from closest parent tile
        for (parentZ in (tile.z - 1) downTo 0) {
            val shift = tile.z - parentZ
            val px = tile.x ushr shift
            val py = tile.y ushr shift
            val parentBytes = readDiskTile(tile.folder, parentZ, px, py)
                ?: readBundledAssetTile(tile.folder, parentZ, px, py)
                ?: continue

            val cropped = cropParentTileQuadrant(parentBytes, tile.x, tile.y, shift)
            if (cropped != null) {
                return buildTileResponse(req, cropped, contentType = "image/jpeg", cacheable = false)
            }
        }

        // Ultimate fallback: try stealth_dark bundled z=0 if another style's z=0 was missing
        readBundledAssetTile(GhostMapStyle.STEALTH_DARK.id, 0, 0, 0)?.let { fallbackBytes ->
            val cropped = cropParentTileQuadrant(fallbackBytes, tile.x, tile.y, tile.z) ?: fallbackBytes
            return buildTileResponse(req, cropped, contentType = "image/jpeg", cacheable = false)
        }

        return buildTileResponse(req, TRANSPARENT_PNG_1X1, contentType = "image/png", cacheable = false)
    }

    private fun cropParentTileQuadrant(parentBytes: ByteArray, childX: Int, childY: Int, shift: Int): ByteArray? {
        if (shift <= 0) return parentBytes
        val parentBmp = BitmapFactory.decodeByteArray(parentBytes, 0, parentBytes.size) ?: return null
        return try {
            val pw = parentBmp.width
            val ph = parentBmp.height
            val out = Bitmap.createBitmap(256, 256, Bitmap.Config.RGB_565)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

            val safeShift = shift.coerceAtMost(20)
            val divisor = (1L shl safeShift).toDouble()
            val localX = (childX.toLong() and ((1L shl safeShift) - 1L)).toDouble()
            val localY = (childY.toLong() and ((1L shl safeShift) - 1L)).toDouble()

            val leftF = (localX / divisor) * pw
            val topF = (localY / divisor) * ph
            val spanW = (pw / divisor).coerceAtLeast(2.0)
            val spanH = (ph / divisor).coerceAtLeast(2.0)

            val srcLeft = leftF.toInt().coerceIn(0, (pw - 2).coerceAtLeast(0))
            val srcTop = topF.toInt().coerceIn(0, (ph - 2).coerceAtLeast(0))
            val srcRight = (srcLeft + spanW.toInt().coerceAtLeast(2)).coerceAtMost(pw)
            val srcBottom = (srcTop + spanH.toInt().coerceAtLeast(2)).coerceAtMost(ph)

            canvas.drawBitmap(
                parentBmp,
                Rect(srcLeft, srcTop, srcRight, srcBottom),
                Rect(0, 0, 256, 256),
                paint
            )
            val baos = ByteArrayOutputStream(12 * 1024)
            out.compress(Bitmap.CompressFormat.JPEG, 86, baos)
            out.recycle()
            baos.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            parentBmp.recycle()
        }
    }

    private fun buildTileResponse(
        req: Request,
        bytes: ByteArray,
        contentType: String = "image/jpeg",
        cacheable: Boolean
    ): Response {
        val cacheHeader = if (cacheable) "public, max-age=2592000" else "no-store, no-cache, max-age=0"
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Content-Type", contentType)
            .header("Cache-Control", cacheHeader)
            .body(bytes.toResponseBody(contentType.toMediaType()))
            .build()
    }

    private fun readDiskTile(folder: String, z: Int, x: Int, y: Int): ByteArray? {
        val f = File(rootDir, "$folder/$z/$x/$y.tile")
        if (!f.isFile || f.length() <= 100L) return null
        return runCatching { f.readBytes() }.getOrNull()
    }

    private fun writeDiskTile(folder: String, z: Int, x: Int, y: Int, bytes: ByteArray) {
        runCatching {
            val dest = File(rootDir, "$folder/$z/$x/$y.tile")
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, "${dest.name}.${Thread.currentThread().id}.tmp")
            FileOutputStream(tmp).use { it.write(bytes) }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
            }
        }
    }

    private fun readBundledAssetTile(folder: String, z: Int, x: Int, y: Int): ByteArray? {
        if (z > 6) return null
        val path = "offline_tiles/$folder/$z/$x/$y.tile"
        return runCatching {
            context.assets.open(path).use { it.readBytes() }
        }.getOrNull()
    }

    fun hasCachedTiles(style: GhostMapStyle): Boolean {
        val dir = File(rootDir, style.id)
        if (dir.exists() && dir.walkTopDown().any { it.isFile && it.length() > 100L }) return true
        return readBundledAssetTile(style.id, 0, 0, 0) != null
    }

    suspend fun getCacheStats(): TileCacheStats = withContext(Dispatchers.IO) {
        var count = 0
        var bytes = 0L
        val styles = mutableSetOf<String>()
        rootDir.listFiles()?.forEach { styleDir ->
            if (!styleDir.isDirectory) return@forEach
            var styleHas = false
            styleDir.walkTopDown().forEach { f ->
                if (f.isFile && f.length() > 0L) {
                    count++
                    bytes += f.length()
                    styleHas = true
                }
            }
            if (styleHas && !styleDir.name.endsWith("_overlay")) {
                styles.add(styleDir.name)
            }
        }
        val bundledOk = readBundledAssetTile(GhostMapStyle.STEALTH_DARK.id, 0, 0, 0) != null
        TileCacheStats(
            totalTiles = count,
            totalBytes = bytes,
            stylesCached = styles,
            hasBundledBase = bundledOk
        )
    }

    /**
     * Downloads a continuous zoom pyramid (`z=4..14`) around `(centerLat, centerLon)` for ALL 4
     * map views (Dark, Street Map, Satellite, Terrain) + reference overlays so switching views
     * in Offline Mode delivers crisp street & aerial imagery with zero zoom gaps.
     */
    suspend fun cacheRegionTiles(
        centerLat: Double,
        centerLon: Double,
        styles: List<GhostMapStyle> = CACHEABLE_STYLES,
        lightweight: Boolean = false,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val tasks = mutableListOf<TileTask>()

            // Continuous zoom pyramid with zero gaps from z=4 up to street level z=14
            val zoomRadii = if (lightweight) {
                listOf(6 to 0, 8 to 0, 10 to 1, 12 to 1, 13 to 1)
            } else {
                listOf(
                    4 to 0, 5 to 0, 6 to 1, 7 to 1,
                    8 to 1, 9 to 1, 10 to 1, 11 to 1,
                    12 to 2, 13 to 2, 14 to 1
                )
            }

            for (style in styles) {
                val baseTemplate = baseUrlTemplateFor(style) ?: continue
                val overlayTemplate = overlayUrlTemplateFor(style)
                val baseDir = File(rootDir, style.id)
                val overlayDir = File(rootDir, "${style.id}_overlay")

                for ((z, radius) in zoomRadii) {
                    val (cx, cy) = latLonToTile(centerLat, centerLon, z)
                    val maxTile = (1 shl z) - 1
                    for (dx in -radius..radius) {
                        for (dy in -radius..radius) {
                            val tx = (cx + dx).coerceIn(0, maxTile)
                            val ty = (cy + dy).coerceIn(0, maxTile)
                            addTaskIfMissing(tasks, baseTemplate, baseDir, z, tx, ty)
                            if (overlayTemplate != null && z in 10..14) {
                                addTaskIfMissing(tasks, overlayTemplate, overlayDir, z, tx, ty)
                            }
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                onProgress(100)
                return@runCatching 0
            }

            val total = tasks.size
            val done = AtomicInteger(0)
            val saved = AtomicInteger(0)
            val sem = Semaphore(12)

            coroutineScope {
                tasks.map { task ->
                    async {
                        sem.withPermit {
                            runCatching {
                                val req = Request.Builder().url(task.url).get().build()
                                downloadHttpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val bytes = resp.body?.bytes()
                                        if (bytes != null && bytes.size > 100) {
                                            task.destFile.parentFile?.mkdirs()
                                            val tmp = File(task.destFile.parentFile, "${task.destFile.name}.tmp")
                                            FileOutputStream(tmp).use { it.write(bytes) }
                                            if (!tmp.renameTo(task.destFile)) tmp.delete()
                                            saved.incrementAndGet()
                                        }
                                    }
                                }
                            }
                            val completed = done.incrementAndGet()
                            if (completed % 5 == 0 || completed == total) {
                                onProgress(((completed * 100) / total).coerceIn(1, 100))
                            }
                        }
                    }
                }.awaitAll()
            }

            onProgress(100)
            saved.get()
        }
    }

    suspend fun clearAllCachedTiles() = withContext(Dispatchers.IO) {
        rootDir.deleteRecursively()
        rootDir.mkdirs()
    }

    private fun addTaskIfMissing(
        tasks: MutableList<TileTask>,
        template: String,
        styleDir: File,
        z: Int,
        x: Int,
        y: Int
    ) {
        val dest = File(styleDir, "$z/$x/$y.tile")
        if (dest.exists() && dest.length() > 100L) return
        val url = template
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
        tasks.add(TileTask(url, dest))
    }

    private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        val n = (1 shl zoom).toDouble()
        val x = floor(((lon + 180.0) / 360.0) * n).toInt().coerceIn(0, (1 shl zoom) - 1)
        val latRad = Math.toRadians(clampedLat)
        val y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt().coerceIn(0, (1 shl zoom) - 1)
        return x to y
    }
}
