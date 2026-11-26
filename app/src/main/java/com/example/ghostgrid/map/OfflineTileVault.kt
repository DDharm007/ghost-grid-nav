package com.example.ghostgrid.map

import android.content.Context
import com.example.ghostgrid.hybrid.StrictOfflineInterceptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class TileCacheStats(
    val totalTiles: Int = 0,
    val totalBytes: Long = 0L,
    val stylesCached: Set<String> = emptySet()
)

/**
 * Private on-device raster tile cache stored in `context.noBackupFilesDir/tile_cache/<styleId>/{z}/{x}/{y}.tile`.
 * Allows Dark, Street Map, Satellite Imagery, and Terrain views to render cleanly
 * with 100% zero network access when Strict Offline Mode is enabled.
 */
class OfflineTileVault(
    private val context: Context,
    isStrictOfflineEnabled: () -> Boolean
) {
    private val httpClient = OkHttpClient.Builder()
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

    fun localBaseTileUriTemplate(style: GhostMapStyle): String {
        val dir = File(rootDir, style.id).apply { mkdirs() }
        return "file://${dir.absolutePath}/{z}/{x}/{y}.tile"
    }

    fun localOverlayTileUriTemplate(style: GhostMapStyle): String? {
        if (overlayUrlTemplateFor(style) == null) return null
        val dir = File(rootDir, "${style.id}_overlay").apply { mkdirs() }
        return "file://${dir.absolutePath}/{z}/{x}/{y}.tile"
    }

    fun hasCachedTiles(style: GhostMapStyle): Boolean {
        val dir = File(rootDir, style.id)
        if (!dir.exists()) return false
        return dir.walkTopDown().any { it.isFile && it.length() > 100L }
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
        TileCacheStats(totalTiles = count, totalBytes = bytes, stylesCached = styles)
    }

    /**
     * Downloads real offline raster tiles around (centerLat, centerLon) for all 4 core map modes
     * (Dark, Street Map, Satellite, Terrain) + global z=1..2 overview tiles so switching between
     * Dark / Map / Satellite / Terrain works seamlessly with zero internet in Offline Mode.
     */
    private data class TileTask(val url: String, val destFile: File)

    suspend fun cacheRegionTiles(
        centerLat: Double,
        centerLon: Double,
        styles: List<GhostMapStyle> = CACHEABLE_STYLES,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val tasks = mutableListOf<TileTask>()

            for (style in styles) {
                val baseTemplate = baseUrlTemplateFor(style) ?: continue
                val overlayTemplate = overlayUrlTemplateFor(style)
                val baseDir = File(rootDir, style.id)
                val overlayDir = File(rootDir, "${style.id}_overlay")

                // 1. Global overview tiles (z=1..2) once per style so full Earth renders offline
                for (z in 1..2) {
                    val maxIdx = (1 shl z) - 1
                    for (x in 0..maxIdx) {
                        for (y in 0..maxIdx) {
                            addTaskIfMissing(tasks, baseTemplate, baseDir, z, x, y)
                        }
                    }
                }

                // 2. Regional metro & street/satellite detail around center (z=8, 10, 11, 12, 13)
                val zoomRadii = listOf(8 to 1, 10 to 1, 11 to 1, 12 to 2, 13 to 2)
                for ((z, radius) in zoomRadii) {
                    val (cx, cy) = latLonToTile(centerLat, centerLon, z)
                    val maxTile = (1 shl z) - 1
                    for (dx in -radius..radius) {
                        for (dy in -radius..radius) {
                            val tx = (cx + dx).coerceIn(0, maxTile)
                            val ty = (cy + dy).coerceIn(0, maxTile)
                            addTaskIfMissing(tasks, baseTemplate, baseDir, z, tx, ty)
                            if (overlayTemplate != null && z in 10..13) {
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
            val sem = Semaphore(10)

            coroutineScope {
                tasks.map { task ->
                    async {
                        sem.withPermit {
                            runCatching {
                                val req = Request.Builder().url(task.url).get().build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val bytes = resp.body?.bytes()
                                        if (bytes != null && bytes.size > 100) {
                                            task.destFile.parentFile?.mkdirs()
                                            val tmp = File(task.destFile.parentFile, "${task.destFile.name}.tmp")
                                            FileOutputStream(tmp).use { it.write(bytes) }
                                            tmp.renameTo(task.destFile)
                                            saved.incrementAndGet()
                                        }
                                    }
                                }
                            }
                            val completed = done.incrementAndGet()
                            if (completed % 6 == 0 || completed == total) {
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
