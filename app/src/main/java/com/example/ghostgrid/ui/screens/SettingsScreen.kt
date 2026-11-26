package com.example.ghostgrid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ghostgrid.data.GhostGridSettings
import com.example.ghostgrid.ui.components.ProfileAvatar
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostBorder
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostEmerald
import com.example.ghostgrid.ui.theme.GhostEmeraldContainer
import com.example.ghostgrid.ui.theme.GhostPurplePrimary
import com.example.ghostgrid.ui.theme.GhostPurpleSoft
import com.example.ghostgrid.ui.theme.GhostSurfaceSolid
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: GhostGridSettings,
    keystoreActive: Boolean,
    activePlacesDbCount: Int,
    activeRoutingGraphCount: Int,
    isSyncingProfile: Boolean,
    onToggleStrictOffline: () -> Unit,
    onToggleVoice: () -> Unit,
    onUpdateProfileName: (String) -> Unit,
    onPickProfilePhoto: () -> Unit,
    onRemoveProfilePhoto: () -> Unit,
    onUpdateUserServerUrl: (String) -> Unit,
    onSyncProfileToUserServer: () -> Unit,
    onSavePlacesApiKey: (String) -> Unit,
    onSaveBingApiKey: (String) -> Unit,
    onClearEnrichmentCache: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var displayName by remember(settings.profileName) { mutableStateOf(settings.profileName) }
    var userServerUrl by remember(settings.userServerUrl) { mutableStateOf(settings.userServerUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var bingKeyInput by remember { mutableStateOf("") }

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
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = GhostTextPrimary
                )
            }
            Column {
                Text("Identity, Privacy & Settings", style = MaterialTheme.typography.titleLarge, color = GhostTextPrimary)
                Text(stringResource(R.string.tagline), style = MaterialTheme.typography.bodySmall, color = GhostPurpleSoft)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // No-Login Local Identity Card (Name + Profile Photo + User Server Sync)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Local Profile", style = MaterialTheme.typography.titleMedium, color = GhostTextPrimary)
                            Surface(shape = RoundedCornerShape(999.dp), color = GhostEmeraldContainer) {
                                Text(
                                    text = stringResource(R.string.profile_no_login_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GhostEmerald,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfileAvatar(
                                photoPath = settings.profilePhotoPath,
                                displayName = displayName,
                                size = 64.dp,
                                onClick = onPickProfilePhoto,
                                modifier = Modifier.testTag("settings_profile_avatar")
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPickProfilePhoto,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pick_profile_photo_button")
                                ) {
                                    Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (settings.profilePhotoPath.isBlank()) "Add Profile Photo" else "Change Photo")
                                }
                                if (settings.profilePhotoPath.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = onRemoveProfilePhoto,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = GhostAlertRed, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Remove Photo", color = GhostAlertRed)
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Your Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_name_input")
                            )
                            Button(
                                onClick = { onUpdateProfileName(displayName) },
                                colors = ButtonDefaults.buttonColors(containerColor = GhostPurplePrimary)
                            ) {
                                Text("Save")
                            }
                        }

                        HorizontalDivider(color = GhostBorder)

                        Text(
                            text = "Personal User Server Storage (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            color = GhostTextPrimary
                        )
                        Text(
                            text = "Store your name and profile photo on your own HTTPS server when Strict Offline Mode is OFF. No account or login required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextSecondary
                        )
                        OutlinedTextField(
                            value = userServerUrl,
                            onValueChange = { userServerUrl = it },
                            label = { Text("User Server Endpoint (HTTPS)") },
                            placeholder = { Text("https://my-server.example/api/ghostgrid/profile") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_server_url_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onUpdateUserServerUrl(userServerUrl) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save URL")
                            }
                            Button(
                                onClick = {
                                    onUpdateUserServerUrl(userServerUrl)
                                    onSyncProfileToUserServer()
                                },
                                enabled = !settings.strictOfflineMode && !isSyncingProfile && userServerUrl.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = GhostPurplePrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_profile_server_button")
                            ) {
                                if (isSyncingProfile) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync Now")
                                }
                            }
                        }
                        if (settings.lastProfileSyncedAt > 0L) {
                            val syncedText = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
                                .format(Date(settings.lastProfileSyncedAt))
                            Text(
                                text = "Last synced to user server: $syncedText",
                                style = TabularCoordinateMono,
                                color = GhostEmerald
                            )
                        }
                    }
                }
            }

            // Master Privacy & Voice Toggles
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Strict Offline Mode", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                                Text(
                                    text = "Hard-blocks all outbound HTTPS calls at the OkHttp transport layer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                            }
                            Switch(
                                checked = settings.strictOfflineMode,
                                onCheckedChange = { onToggleStrictOffline() }
                            )
                        }
                        HorizontalDivider(color = GhostBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Offline Voice Guidance (TTS)", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                                Text(
                                    text = "Uses local on-device TextToSpeech with network synthesis disabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                            }
                            Switch(
                                checked = settings.voiceGuidanceEnabled,
                                onCheckedChange = { onToggleVoice() }
                            )
                        }
                    }
                }
            }

            // Hybrid Places API Key Configuration (Keystore-encrypted)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Key, contentDescription = null, tint = GhostPurpleSoft, modifier = Modifier.size(18.dp))
                            Text("Google & Bing Search Keys (Optional)", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                        }
                        Text(
                            text = "Live search works out of the box using open global geocoders. Add optional Google Places or Bing Maps keys (Keystore-encrypted) to include live Google & Bing results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextSecondary
                        )
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text(if (settings.hasPlacesApiKey) "Replace Google Places Key" else "Google Places API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onSavePlacesApiKey(apiKeyInput)
                                    apiKeyInput = ""
                                },
                                enabled = apiKeyInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = GhostPurplePrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Google Key")
                            }
                            if (settings.hasPlacesApiKey) {
                                OutlinedButton(
                                    onClick = { onSavePlacesApiKey("") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clear", color = GhostAlertRed)
                                }
                            }
                        }

                        HorizontalDivider(color = GhostBorder)

                        OutlinedTextField(
                            value = bingKeyInput,
                            onValueChange = { bingKeyInput = it },
                            label = { Text(if (settings.hasBingApiKey) "Replace Bing Maps Key" else "Bing Maps API Key") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onSaveBingApiKey(bingKeyInput)
                                    bingKeyInput = ""
                                },
                                enabled = bingKeyInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = GhostPurplePrimary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Bing Key")
                            }
                            if (settings.hasBingApiKey) {
                                OutlinedButton(
                                    onClick = { onSaveBingApiKey("") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clear", color = GhostAlertRed)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onClearEnrichmentCache,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear Cached Live Reviews & Hours")
                        }
                    }
                }
            }

            // Security & Privacy Hardening Verification Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostEmerald.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = GhostEmerald, modifier = Modifier.size(18.dp))
                            Text("Privacy & Security Telemetry", style = MaterialTheme.typography.titleSmall, color = GhostEmerald)
                        }
                        SecurityStatusRow("SQLCipher AES-256 Vault", "Active in noBackupFilesDir")
                        SecurityStatusRow("Android Keystore AES-GCM Key", if (keystoreActive) "Verified" else "Fallback")
                        SecurityStatusRow("Cloud Backup & Transfer", "Disabled (allowBackup=false)")
                        SecurityStatusRow("Active Offline Search DBs", "$activePlacesDbCount installed")
                        SecurityStatusRow("Active GraphHopper Graphs", "$activeRoutingGraphCount loaded")
                    }
                }
            }

            // Mandatory Open Geospatial Attribution (ODbL 1.0 & CDLA-Permissive-2.0)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = GhostPurpleSoft, modifier = Modifier.size(18.dp))
                            Text("Open Data Attribution", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                        }
                        Text(stringResource(R.string.attribution_osm), style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                        Text(stringResource(R.string.attribution_overture), style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                        Text(stringResource(R.string.attribution_engines), style = MaterialTheme.typography.bodySmall, color = GhostTextMuted)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SecurityStatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
        Text(value, style = TabularCoordinateMono, color = GhostTextPrimary)
    }
}
