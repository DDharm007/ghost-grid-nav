package com.example.ghostgrid.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages offline PMTiles map archives stored exclusively inside app-private `noBackupFilesDir`.
 *
 * - Extracts bundled `assets/world-base.pmtiles` (if compiled and bundled into APK assets)
 *   into `noBackupFilesDir/maps/world-base.pmtiles` for native `pmtiles://file://...` reading.
 * - Enumerates all installed regional `map.pmtiles` files inside `noBackupFilesDir/regions/<id>/`.
 */
class MapAssetManager(private val context: Context) {

    private val mapsDir: File
        get() = File(context.noBackupFilesDir, "maps").apply { mkdirs() }

    private val regionsDir: File
        get() = File(context.noBackupFilesDir, "regions").apply { mkdirs() }

    suspend fun ensureWorldBasePmtiles(): File? = withContext(Dispatchers.IO) {
        val target = File(mapsDir, "world-base.pmtiles")
        if (target.exists() && target.length() > 0L) {
            return@withContext target
        }

        val assetNames = runCatching { context.assets.list("").orEmpty() }.getOrDefault(emptyArray())
        if ("world-base.pmtiles" !in assetNames) {
            return@withContext null
        }

        val tmp = File(mapsDir, "world-base.pmtiles.tmp")
        runCatching {
            context.assets.open("world-base.pmtiles").use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            target
        }.getOrElse {
            tmp.delete()
            null
        }
    }

    fun getInstalledRegionalPmtiles(): List<Pair<String, File>> {
        if (!regionsDir.exists()) return emptyList()
        return regionsDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val pmtiles = File(dir, "map.pmtiles")
                if (pmtiles.isFile && pmtiles.length() > 0L) dir.name to pmtiles else null
            }
            .orEmpty()
    }
}
