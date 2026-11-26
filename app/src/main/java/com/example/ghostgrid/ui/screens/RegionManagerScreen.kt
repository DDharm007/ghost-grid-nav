package com.example.ghostgrid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ghostgrid.data.InstalledRegionEntity
import com.example.ghostgrid.downloader.BuiltInRegionCatalog
import com.example.ghostgrid.downloader.BuiltInRegionPack
import com.example.ghostgrid.downloader.PackInstallProgress
import com.example.ghostgrid.downloader.RegionGroup
import com.example.ghostgrid.downloader.RemoteRegionEntry
import com.example.ghostgrid.map.TileCacheStats
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono

private val RegionGlassBg = Brush.verticalGradient(listOf(Color(0xE817171A), Color(0xF50B0B0D)))
private val RegionGlassRim = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x12FFFFFF)))

@Composable
fun RegionManagerScreen(
    installedRegions: List<InstalledRegionEntity>,
    remoteCatalog: List<RemoteRegionEntry>,
    savedManifestUrl: String,
    strictOfflineMode: Boolean,
    packProgress: PackInstallProgress,
    isFetchingCatalog: Boolean,
    hasWorldBasePmtiles: Boolean,
    tileCacheStats: TileCacheStats,
    isCachingAreaTiles: Boolean,
    tileCacheProgressPct: Int,
    onInstallBuiltInPack: (BuiltInRegionPack) -> Unit,
    onOpenRegionOnMap: (GeoPoint) -> Unit,
    onCacheCurrentAreaOffline: () -> Unit,
    onImportLocalZip: () -> Unit,
    onFetchManifest: (String) -> Unit,
    onDownloadRemote: (RemoteRegionEntry) -> Unit,
    onDeleteRegion: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var manifestUrl by remember(savedManifestUrl) { mutableStateOf(savedManifestUrl) }
    var selectedGroup by remember { mutableStateOf(RegionGroup.INDIA) }
    var showCustomManifest by remember { mutableStateOf(false) }

    val installedIds = remember(installedRegions) { installedRegions.map { it.id }.toSet() }
    val builtInList = remember(selectedGroup) { BuiltInRegionCatalog.byGroup(selectedGroup) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GhostCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Minimal Monochrome Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("regions_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = GhostTextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.regions_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = GhostTextPrimary
                )
                Text(
                    text = "${installedRegions.size} installed • ${tileCacheStats.totalTiles} offline map tiles cached",
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostTextMuted
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Offline Map Vault Status + Cache Current Map Area (Dark / Map / Satellite / Terrain)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(RegionGlassBg)
                        .border(0.85.dp, RegionGlassRim, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Global Offline Vault",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GhostTextPrimary
                                )
                                Text(
                                    text = if (tileCacheStats.totalTiles > 0) {
                                        "${tileCacheStats.totalTiles} tiles (${formatBytes(tileCacheStats.totalBytes)}) • Dark, Map, Satellite & Terrain ready offline"
                                    } else {
                                        "Offline world map active. Install a region below or save your current view for offline Satellite, Map, Dark & Terrain."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                            }
                        }

                        if (isCachingAreaTiles) {
                            LinearProgressIndicator(
                                progress = { tileCacheProgressPct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.White,
                                trackColor = Color(0x22FFFFFF)
                            )
                            Text(
                                text = "Caching Dark, Map, Satellite & Terrain tiles… $tileCacheProgressPct%",
                                style = TabularCoordinateMono,
                                color = GhostTextSecondary
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onCacheCurrentAreaOffline,
                                    enabled = !strictOfflineMode,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("cache_area_offline_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save Map Area")
                                }
                                OutlinedButton(
                                    onClick = onImportLocalZip,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("import_local_zip_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Import .zip")
                                }
                            }
                        }
                    }
                }
            }

            // Active Install Progress Banner
            if (packProgress !is PackInstallProgress.Idle) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x20FFFFFF))
                            .border(0.9.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (packProgress) {
                                is PackInstallProgress.Downloading -> {
                                    Text(
                                        text = "Downloading ${packProgress.regionName} (${packProgress.percent}%)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    LinearProgressIndicator(
                                        progress = { packProgress.percent / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.White,
                                        trackColor = Color(0x24FFFFFF)
                                    )
                                }
                                is PackInstallProgress.Verifying -> {
                                    Text(
                                        text = "Verifying ${packProgress.regionName}…",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
                                }
                                is PackInstallProgress.Extracting -> {
                                    Text(
                                        text = "Building offline database for ${packProgress.regionName}…",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
                                }
                                is PackInstallProgress.Completed -> {
                                    Text(
                                        text = "Installed offline pack: ${packProgress.regionName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White
                                    )
                                }
                                is PackInstallProgress.Failed -> {
                                    Text(
                                        text = packProgress.message,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = GhostAlertRed
                                    )
                                }
                                PackInstallProgress.Idle -> Unit
                            }
                        }
                    }
                }
            }

            // Installed Regional Packs
            if (installedRegions.isNotEmpty()) {
                item {
                    Text(
                        text = "Installed (${installedRegions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = GhostTextPrimary
                    )
                }

                items(installedRegions, key = { "inst_${it.id}" }) { region ->
                    val centerLat = (region.minLat + region.maxLat) / 2.0
                    val centerLon = (region.minLon + region.maxLon) / 2.0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RegionGlassBg)
                            .border(0.85.dp, Color(0x36FFFFFF), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(region.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                    Icon(
                                        Icons.Filled.VerifiedUser,
                                        contentDescription = "Verified",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "${region.version} • ${formatBytes(region.sizeBytes)}",
                                    style = TabularCoordinateMono,
                                    color = GhostTextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onOpenRegionOnMap(GeoPoint(centerLat, centerLon)) }
                            ) {
                                Icon(Icons.Filled.MyLocation, contentDescription = "View on map", tint = Color.White)
                            }
                            IconButton(onClick = { onDeleteRegion(region.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete pack", tint = GhostAlertRed)
                            }
                        }
                    }
                }
            }

            // Ready-to-Install India & World Packs Header + Segmented Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "India & World Offline Packs",
                        style = MaterialTheme.typography.titleMedium,
                        color = GhostTextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RegionGroup.entries.forEach { group ->
                            val selected = selectedGroup == group
                            val count = BuiltInRegionCatalog.byGroup(group).size
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) Color.White else Color(0x14FFFFFF))
                                    .border(
                                        0.8.dp,
                                        if (selected) Color.White else Color(0x28FFFFFF),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedGroup = group }
                                    .padding(vertical = 10.dp)
                                    .testTag("region_group_${group.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${group.label} ($count)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }

            // India or World Pack Items
            items(builtInList, key = { it.id }) { pack ->
                val isInstalled = pack.id in installedIds
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(RegionGlassBg)
                        .border(0.85.dp, RegionGlassRim, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            tint = Color(0xFFD4D4D8),
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pack.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(pack.subtitle, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                        }
                        if (isInstalled) {
                            OutlinedButton(
                                onClick = { onOpenRegionOnMap(GeoPoint(pack.centerLat, pack.centerLon)) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Open")
                            }
                        } else {
                            Button(
                                onClick = { onInstallBuiltInPack(pack) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("install_pack_${pack.id}")
                            ) {
                                Text("Install")
                            }
                        }
                    }
                }
            }

            // Collapsible Custom GitHub Release Manifest (for advanced custom packs)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x10FFFFFF))
                        .border(0.8.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCustomManifest = !showCustomManifest },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Release Manifest URL",
                                style = MaterialTheme.typography.titleSmall,
                                color = GhostTextPrimary
                            )
                            Text(
                                text = if (showCustomManifest) "Hide" else "Expand",
                                style = MaterialTheme.typography.labelSmall,
                                color = GhostTextSecondary
                            )
                        }

                        if (showCustomManifest) {
                            OutlinedTextField(
                                value = manifestUrl,
                                onValueChange = { manifestUrl = it },
                                placeholder = { Text("https://.../catalog.json") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manifest_url_input")
                            )
                            OutlinedButton(
                                onClick = { onFetchManifest(manifestUrl) },
                                enabled = !strictOfflineMode && !isFetchingCatalog && manifestUrl.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("fetch_manifest_button")
                            ) {
                                if (isFetchingCatalog) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Fetching…")
                                } else {
                                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.fetch_manifest))
                                }
                            }
                        }
                    }
                }
            }

            // Custom Remote Catalog Entries if fetched
            if (remoteCatalog.isNotEmpty()) {
                items(remoteCatalog, key = { "rem_${it.id}" }) { entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(RegionGlassBg)
                            .border(0.85.dp, RegionGlassRim, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                                Text(
                                    text = "v${entry.version} • ${formatBytes(entry.sizeBytes)}",
                                    style = TabularCoordinateMono,
                                    color = GhostTextSecondary
                                )
                            }
                            Button(
                                onClick = { onDownloadRemote(entry) },
                                enabled = !strictOfflineMode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("Install")
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.0f KB", kb)
    val mb = kb / 1024.0
    return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
}
