package com.example.ghostgrid.downloader

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RegionPackManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val minLon: Double = -180.0,
    val minLat: Double = -85.0,
    val maxLon: Double = 180.0,
    val maxLat: Double = 85.0,
    val minZoom: Int = 6,
    val maxZoom: Int = 16,
    val hasRoutingGraph: Boolean = false,
    val hasPlacesDb: Boolean = false,
    val attribution: String = "Map data © OpenStreetMap contributors (ODbL 1.0); Buildings & POIs © Overture Maps Foundation (CDLA-Permissive-2.0)"
)

@JsonClass(generateAdapter = true)
data class RemoteRegionCatalog(
    val updatedAt: String = "",
    val regions: List<RemoteRegionEntry> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RemoteRegionEntry(
    val id: String,
    val name: String,
    val version: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double
)

sealed interface PackInstallProgress {
    data object Idle : PackInstallProgress
    data class Downloading(val regionName: String, val percent: Int) : PackInstallProgress
    data class Verifying(val regionName: String) : PackInstallProgress
    data class Extracting(val regionName: String) : PackInstallProgress
    data class Completed(val regionName: String) : PackInstallProgress
    data class Failed(val message: String) : PackInstallProgress
}
