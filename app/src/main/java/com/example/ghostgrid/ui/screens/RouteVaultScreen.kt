package com.example.ghostgrid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ghostgrid.data.RecordedTrackEntity
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.tracking.ExportFormat
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostBorder
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostEmerald
import com.example.ghostgrid.ui.theme.GhostPurpleContainer
import com.example.ghostgrid.ui.theme.GhostPurplePrimary
import com.example.ghostgrid.ui.theme.GhostPurpleSoft
import com.example.ghostgrid.ui.theme.GhostSurfaceSolid
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RouteVaultScreen(
    isUnlocked: Boolean,
    tracks: List<RecordedTrackEntity>,
    onRequestBiometricUnlock: () -> Unit,
    onPreviewTrack: (Long) -> Unit,
    onExportTrack: (RecordedTrackEntity, ExportFormat) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        if (!isUnlocked) {
            onRequestBiometricUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GhostCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = GhostTextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.vault_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = GhostTextPrimary
                )
                Text(
                    text = "SQLCipher AES-256 • Keystore-backed • Zero cloud backup",
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostTextSecondary
                )
            }
        }

        if (!isUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostPurplePrimary, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = GhostPurpleSoft,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Biometric Gate Active",
                            style = MaterialTheme.typography.headlineSmall,
                            color = GhostTextPrimary
                        )
                        Text(
                            text = "Your recorded GPS breadcrumbs and GPX/GeoJSON exports are encrypted at rest. Authenticate with biometrics or device screen lock to inspect.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GhostTextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRequestBiometricUnlock,
                            colors = ButtonDefaults.buttonColors(containerColor = GhostPurplePrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_unlock_button")
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Unlock Encrypted Vault")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (tracks.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = GhostSurfaceSolid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No Recorded GPS Tracks Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GhostTextPrimary
                                )
                                Text(
                                    text = "Tap the red Record button on the map screen to log encrypted GPS breadcrumbs (accuracy <= 20m, displacement >= 4m).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(tracks, key = { it.id }) { track ->
                        val dateText = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US)
                            .format(Date(track.startedAt))
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = GhostSurfaceSolid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = GhostTextPrimary
                                        )
                                        Text(
                                            text = dateText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GhostTextSecondary
                                        )
                                    }
                                    if (track.isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = GhostPurpleContainer
                                        ) {
                                            Text(
                                                text = "LIVE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GhostEmerald,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteTrack(track.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete track", tint = GhostAlertRed)
                                    }
                                }

                                Text(
                                    text = "${GeoMath.formatDistance(track.distanceMeters)}  •  ${track.pointCount} encrypted GPS points",
                                    style = TabularCoordinateMono,
                                    color = GhostPurpleSoft
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onPreviewTrack(track.id) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("View")
                                    }
                                    OutlinedButton(
                                        onClick = { onExportTrack(track, ExportFormat.GPX) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("GPX")
                                    }
                                    OutlinedButton(
                                        onClick = { onExportTrack(track, ExportFormat.GEOJSON) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("GeoJSON")
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
