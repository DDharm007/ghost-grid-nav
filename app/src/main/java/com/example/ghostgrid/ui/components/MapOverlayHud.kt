package com.example.ghostgrid.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ghostgrid.data.GhostGridSettings
import com.example.ghostgrid.map.CarColorPreset
import com.example.ghostgrid.map.GhostCameraMode
import com.example.ghostgrid.map.GhostMapStyle
import com.example.ghostgrid.map.LocationSnapshot
import com.example.ghostgrid.routing.ManeuverDirection
import com.example.ghostgrid.routing.NavigationHudState
import com.example.ghostgrid.search.AmenityCategory
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.PlaceResult
import com.example.ghostgrid.tracking.LiveRecordingState
import com.example.ghostgrid.ui.GhostScreen
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostAlertRedContainer
import com.example.ghostgrid.ui.theme.GhostOnAccent
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryLarge
import com.example.ghostgrid.ui.theme.TabularTelemetryMedium

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import com.example.ghostgrid.map.TileCacheStats
import java.net.URLEncoder

// Smoked Monochrome Glassmorphism Tokens (Black, Grey, White — zero neon/AI glow)
private val GlassFillBrush = Brush.verticalGradient(
    listOf(Color(0xD91A1A1D), Color(0xE80C0C0E))
)
private val GlassRimBrush = Brush.verticalGradient(
    listOf(Color(0x42FFFFFF), Color(0x14FFFFFF))
)
private val GlassSheetBrush = Brush.verticalGradient(
    listOf(Color(0xF5161618), Color(0xFC0A0A0C))
)

@Composable
fun TopSearchAndPrivacyHud(
    query: String,
    activeCategory: AmenityCategory?,
    results: List<PlaceResult>,
    isSearching: Boolean,
    settings: GhostGridSettings,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onToggleCategory: (AmenityCategory) -> Unit,
    onSelectResult: (PlaceResult) -> Unit,
    onToggleStrictOffline: () -> Unit,
    onOpenLayersSheet: () -> Unit,
    onOpenScreen: (GhostScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Minimal Smoked-Glass Floating Search Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(28.dp))
                .background(GlassFillBrush)
                .border(0.85.dp, GlassRimBrush, RoundedCornerShape(28.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = Color(0xFFD4D4D8),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search places",
                            style = MaterialTheme.typography.bodyLarge,
                            color = GhostTextMuted,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch() }),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = GhostTextPrimary),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input")
                    )
                }

                if (query.isNotEmpty() || activeCategory != null) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 2.dp),
                            strokeWidth = 1.8.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("clear_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_clear_search),
                            tint = GhostTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (settings.strictOfflineMode) Color.White else Color(0x26FFFFFF))
                            .clickable(onClick = onToggleStrictOffline)
                            .testTag("strict_offline_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (settings.strictOfflineMode) Icons.Filled.CloudOff else Icons.Filled.Public,
                            contentDescription = stringResource(R.string.cd_strict_offline_toggle),
                            tint = if (settings.strictOfflineMode) Color.Black else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Spacer(Modifier.width(7.dp))

                    ProfileAvatar(
                        photoPath = settings.profilePhotoPath,
                        displayName = settings.profileName,
                        size = 34.dp,
                        onClick = { onOpenScreen(GhostScreen.SETTINGS) },
                        modifier = Modifier.testTag("hud_profile_avatar")
                    )
                }
            }
        }

        // Minimal Monochrome Frosted Category Pills
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 6.dp)
        ) {
            items(AmenityCategory.entries) { category ->
                val selected = activeCategory == category
                Box(
                    modifier = Modifier
                        .shadow(8.dp, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) SolidColor(Color.White) else GlassFillBrush
                        )
                        .border(
                            0.8.dp,
                            if (selected) SolidColor(Color.White) else GlassRimBrush,
                            RoundedCornerShape(999.dp)
                        )
                        .clickable { onToggleCategory(category) }
                        .testTag("chip_${category.dbKey}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = categoryIcon(category),
                            contentDescription = null,
                            tint = if (selected) GhostOnAccent else Color(0xFFD4D4D8),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = shortCategoryLabel(category),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (selected) GhostOnAccent else GhostTextPrimary
                        )
                    }
                }
            }
        }

        // Minimal Single Circular Map Layers Glass Button
        if (query.isBlank() && activeCategory == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(GlassFillBrush)
                        .border(0.85.dp, GlassRimBrush, CircleShape)
                        .clickable(onClick = onOpenLayersSheet)
                        .testTag("map_layers_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = "Map layers",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Smoked-Glass Search Results Sheet
        AnimatedVisibility(visible = query.isNotBlank() || activeCategory != null) {
            val webTerm = query.trim().ifBlank { activeCategory?.label.orEmpty() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .shadow(18.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(GlassSheetBrush)
                    .border(0.85.dp, GlassRimBrush, RoundedCornerShape(22.dp))
            ) {
                Column {
                    when {
                        isSearching && results.isEmpty() -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Searching…", style = MaterialTheme.typography.bodyMedium, color = GhostTextSecondary)
                            }
                        }

                        results.isEmpty() -> {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "No places found",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GhostTextPrimary
                                )
                                Text(
                                    text = if (settings.strictOfflineMode) {
                                        "Offline lock is on. Install an India/World pack in Maps or tap the globe icon."
                                    } else {
                                        "Try another name or enter lat, lon coordinates."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                            }
                        }

                        else -> {
                            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                                items(results, key = { it.id }) { place ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSelectResult(place) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0x1EFFFFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (place.isSaved) Icons.Filled.Bookmark else Icons.Filled.Place,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = place.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = GhostTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = place.address.ifBlank { place.subcategory },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GhostTextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = GeoMath.formatDistance(place.distanceMeters),
                                            style = TabularCoordinateMono,
                                            color = GhostTextSecondary
                                        )
                                    }
                                    HorizontalDivider(color = Color(0x18FFFFFF))
                                }
                            }
                        }
                    }

                    // Minimal Google & Bing Web Search Row (only when online and query/category active)
                    if (!settings.strictOfflineMode && webTerm.isNotBlank()) {
                        HorizontalDivider(color = Color(0x22FFFFFF))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val enc = URLEncoder.encode(webTerm, "UTF-8")
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x18FFFFFF))
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$enc"))
                                            )
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text("Google", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = GhostTextSecondary, modifier = Modifier.size(13.dp))
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x18FFFFFF))
                                    .clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com/maps?q=$enc"))
                                            )
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text("Bing", style = MaterialTheme.typography.labelMedium, color = Color.White)
                                    Icon(Icons.Filled.OpenInNew, contentDescription = null, tint = GhostTextSecondary, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TurnByTurnNavigationBanner(
    navHud: NavigationHudState,
    voiceEnabled: Boolean,
    onRoadBlockedAhead: () -> Unit,
    onToggleVoice: () -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val route = navHud.activeRoute ?: return
    val maneuver = route.maneuvers.getOrNull(navHud.currentManeuverIndex) ?: route.maneuvers.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(GlassSheetBrush)
                .border(0.9.dp, GlassRimBrush, RoundedCornerShape(24.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = maneuverIcon(maneuver?.direction ?: ManeuverDirection.STRAIGHT),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = GeoMath.formatDistance(navHud.distanceToNextTurnMeters),
                            style = TabularTelemetryLarge,
                            color = Color.White
                        )
                        Text(
                            text = maneuver?.instructionText ?: "Continue to ${route.destinationName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color(0xFFD4D4D8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onToggleVoice,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("nav_voice_toggle")
                    ) {
                        Icon(
                            imageVector = if (voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = stringResource(R.string.cd_voice_toggle),
                            tint = if (voiceEnabled) Color.White else GhostTextMuted
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color(0x20FFFFFF))
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column {
                            Text("ETA", style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
                            Text(
                                text = GeoMath.formatDuration(navHud.remainingDurationMillis),
                                style = TabularTelemetryMedium,
                                color = Color.White
                            )
                        }
                        Column {
                            Text("LEFT", style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
                            Text(
                                text = GeoMath.formatDistance(navHud.remainingDistanceMeters),
                                style = TabularTelemetryMedium,
                                color = Color(0xFFD4D4D8)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(GhostAlertRedContainer)
                                .border(0.8.dp, GhostAlertRed.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .clickable(onClick = onRoadBlockedAhead)
                                .padding(horizontal = 11.dp, vertical = 8.dp)
                                .testTag("road_blocked_ahead_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = "Blocked",
                                    tint = GhostAlertRed,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text("Blocked", style = MaterialTheme.typography.labelMedium, color = GhostAlertRed)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .clickable(onClick = onStopNavigation)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("end_navigation_button")
                        ) {
                            Text(
                                text = "End",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom-Left Smoked-Glass Live World Time Pill on the main Map HUD.
 * Displays exact second-accurate time for whichever world time zone is selected in Tools -> Time.
 */
@Composable
fun MapWorldClockPill(
    selectedTimeZoneId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMs = rememberExactSecondTickMillis()
    val snap = resolveWorldTimeSnapshot(selectedTimeZoneId, nowMs)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, bottom = 114.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(18.dp))
            .background(GlassFillBrush)
            .border(0.85.dp, GlassRimBrush, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag("map_world_clock_pill")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = "World time",
                    tint = GhostTextSecondary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = snap.time24,
                    style = TabularCoordinateMono.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }
            Text(
                text = "${snap.cityLabel.take(14)} • ${snap.shortAbbr}",
                style = MaterialTheme.typography.labelSmall,
                color = GhostTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Minimal Smoked-Glass Right-Side Control Stack lifted cleanly above the floating bottom bar.
 */
@Composable
fun MapTacticalActionColumn(
    cameraMode: GhostCameraMode,
    recordingState: LiveRecordingState,
    location: LocationSnapshot,
    onCycleCameraMode: () -> Unit,
    onRecenter: () -> Unit,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 14.dp, bottom = 114.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val speedKmh = (location.speedMps * 3.6f).toInt().coerceAtLeast(0)
        if (speedKmh > 0) {
            Box(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(GlassFillBrush)
                    .border(0.8.dp, GlassRimBrush, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$speedKmh", style = TabularTelemetryMedium, color = Color.White)
                    Text(text = "km/h", style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
                }
            }
        }

        // Camera Mode Glass Circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(if (cameraMode != GhostCameraMode.FREE) SolidColor(Color.White) else GlassFillBrush)
                .border(0.85.dp, GlassRimBrush, CircleShape)
                .clickable(onClick = onCycleCameraMode)
                .testTag("camera_mode_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (cameraMode) {
                    GhostCameraMode.FREE -> Icons.Filled.Explore
                    GhostCameraMode.TRACKING -> Icons.Filled.MyLocation
                    GhostCameraMode.COMPASS -> Icons.Filled.Navigation
                    GhostCameraMode.NAVIGATION_3D -> Icons.Filled.DirectionsCar
                },
                contentDescription = stringResource(R.string.cd_camera_mode),
                tint = if (cameraMode != GhostCameraMode.FREE) Color.Black else Color.White,
                modifier = Modifier.size(21.dp)
            )
        }

        // Recenter GPS Glass Circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(GlassFillBrush)
                .border(0.85.dp, GlassRimBrush, CircleShape)
                .clickable(onClick = onRecenter)
                .testTag("recenter_gps_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.GpsFixed,
                contentDescription = stringResource(R.string.action_recenter),
                tint = if (location.hasGpsFix) Color.White else GhostTextMuted,
                modifier = Modifier.size(21.dp)
            )
        }

        // Crisp White Squircle Primary Action (Track Recorder)
        val isRec = recordingState.isRecording
        Box(
            modifier = Modifier
                .shadow(14.dp, RoundedCornerShape(17.dp))
                .clip(RoundedCornerShape(17.dp))
                .background(if (isRec) GhostAlertRed else Color.White)
                .clickable(onClick = onToggleRecording)
                .minimumInteractiveComponentSize()
                .padding(horizontal = 14.dp, vertical = 13.dp)
                .testTag("record_track_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isRec) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = stringResource(R.string.cd_record_track),
                    tint = if (isRec) Color.White else Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                if (isRec) {
                    Text(
                        text = GeoMath.formatDistance(recordingState.distanceMeters),
                        style = TabularCoordinateMono,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Minimal Smoked-Glass Floating Bottom Navigation Bar (Black, Grey, White)
 * including Constellation Detector ("Sky").
 */
@Composable
fun BottomCommandDock(
    activeScreen: GhostScreen,
    onOpenScreen: (GhostScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(28.dp))
            .background(GlassFillBrush)
            .border(0.85.dp, GlassRimBrush, RoundedCornerShape(28.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab(
                icon = Icons.Filled.Explore,
                label = "Explore",
                selected = activeScreen == GhostScreen.MAP,
                contentDescription = "Explore map",
                onClick = { onOpenScreen(GhostScreen.MAP) },
                testTag = "dock_explore"
            )
            BottomNavTab(
                icon = Icons.Filled.AutoAwesome,
                label = "Sky",
                selected = activeScreen == GhostScreen.CONSTELLATIONS,
                contentDescription = "Constellation Detector",
                onClick = { onOpenScreen(GhostScreen.CONSTELLATIONS) },
                testTag = "dock_constellations"
            )
            BottomNavTab(
                icon = Icons.Filled.Bookmark,
                label = "Saved",
                selected = activeScreen == GhostScreen.SAVED_PLACES,
                contentDescription = stringResource(R.string.cd_saved_lists),
                onClick = { onOpenScreen(GhostScreen.SAVED_PLACES) },
                testTag = "dock_saved"
            )
            BottomNavTab(
                icon = Icons.Filled.Map,
                label = "Maps",
                selected = activeScreen == GhostScreen.REGIONS,
                contentDescription = stringResource(R.string.cd_region_manager),
                onClick = { onOpenScreen(GhostScreen.REGIONS) },
                testTag = "dock_regions"
            )
            BottomNavTab(
                icon = Icons.Filled.Build,
                label = "Tools",
                selected = activeScreen == GhostScreen.TOOLKIT,
                contentDescription = stringResource(R.string.cd_toolkit),
                onClick = { onOpenScreen(GhostScreen.TOOLKIT) },
                testTag = "dock_toolkit"
            )
            BottomNavTab(
                icon = Icons.Filled.Lock,
                label = "Vault",
                selected = activeScreen == GhostScreen.VAULT,
                contentDescription = stringResource(R.string.cd_route_vault),
                onClick = { onOpenScreen(GhostScreen.VAULT) },
                testTag = "dock_vault"
            )
        }
    }
}

@Composable
private fun BottomNavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) Color.White else Color.Transparent)
                .padding(horizontal = 11.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) Color.Black else GhostTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) Color.White else GhostTextMuted,
            maxLines = 1
        )
    }
}

/**
 * Minimal Smoked-Glass Sheet for Map Styles (all accessible in Offline Mode too!),
 * Offline Area Caching (Dark, Map, Satellite, Terrain), and Monochrome 3D Car Avatar.
 */
@Composable
fun MapLayersAndVehicleSheet(
    currentStyle: GhostMapStyle,
    strictOfflineMode: Boolean,
    tileCacheStats: TileCacheStats,
    isCachingAreaTiles: Boolean,
    tileCacheProgressPct: Int,
    currentCarColor: CarColorPreset,
    alwaysShow3dCar: Boolean,
    onSelectStyle: (GhostMapStyle) -> Unit,
    onCacheCurrentAreaOffline: () -> Unit,
    onSelectCarColor: (CarColorPreset) -> Unit,
    onToggleAlways3dCar: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(GlassSheetBrush)
            .border(0.85.dp, GlassRimBrush, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Map & Vehicle", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        text = if (strictOfflineMode) {
                            "Offline mode • ${tileCacheStats.totalTiles} cached tiles ready"
                        } else {
                            "Switch layers anytime — works online & offline"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextMuted
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = GhostTextSecondary)
                }
            }

            // Map Style Selector (Dark, Map, Satellite, Terrain, 3D, Offline — ALL selectable even when Offline Mode is ON!)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(GhostMapStyle.entries) { style ->
                    val effectiveSelected = style == currentStyle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (effectiveSelected) Color.White else Color(0x16FFFFFF))
                            .border(
                                0.8.dp,
                                if (effectiveSelected) Color.White else Color(0x28FFFFFF),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectStyle(style) }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                            .testTag("style_${style.id}")
                    ) {
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (effectiveSelected) Color.Black else Color.White
                        )
                    }
                }
            }

            // One-Tap Save Current Area Offline (Dark + Map + Satellite + Terrain)
            if (isCachingAreaTiles) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { tileCacheProgressPct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                        trackColor = Color(0x22FFFFFF)
                    )
                    Text(
                        text = "Saving Dark, Map, Satellite & Terrain offline… $tileCacheProgressPct%",
                        style = TabularCoordinateMono,
                        color = GhostTextSecondary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (strictOfflineMode) Color(0x10FFFFFF) else Color(0x1EFFFFFF))
                        .border(0.8.dp, Color(0x2EFFFFFF), RoundedCornerShape(14.dp))
                        .clickable(enabled = !strictOfflineMode, onClick = onCacheCurrentAreaOffline)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                        .testTag("sheet_save_area_offline")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            tint = if (strictOfflineMode) GhostTextMuted else Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = if (strictOfflineMode) {
                                "Disable offline lock briefly to save new area tiles"
                            } else {
                                "Save Current Area Offline (Dark, Map, Satellite, Terrain)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (strictOfflineMode) GhostTextMuted else Color.White
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0x1EFFFFFF))

            // 3D Car Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("3D Car Model", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        text = "Always visible or auto while moving",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextMuted
                    )
                }
                Switch(
                    checked = alwaysShow3dCar,
                    onCheckedChange = { onToggleAlways3dCar() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color.White,
                        uncheckedThumbColor = Color(0xFFA1A1AA),
                        uncheckedTrackColor = Color(0xFF27272A)
                    ),
                    modifier = Modifier.testTag("always_show_3d_car_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CarColorPreset.entries.forEach { preset ->
                    val selected = currentCarColor == preset
                    val swatch = Color(android.graphics.Color.parseColor(preset.primaryHex))
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
                            .clickable { onSelectCarColor(preset) }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("car_color_${preset.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(0.8.dp, Color(0xFF71717A), CircleShape)
                            )
                            Text(
                                text = preset.label.substringBefore(" "),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.Black else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shortCategoryLabel(category: AmenityCategory): String = when (category) {
    AmenityCategory.FOOD -> "Food"
    AmenityCategory.HOSPITAL -> "Hospitals"
    AmenityCategory.PHARMACY -> "Pharmacy"
    AmenityCategory.FUEL -> "Fuel"
    AmenityCategory.TRANSIT -> "Transit"
    AmenityCategory.SAVED -> "Saved"
}

private fun categoryIcon(category: AmenityCategory): ImageVector = when (category) {
    AmenityCategory.FOOD -> Icons.Filled.Restaurant
    AmenityCategory.HOSPITAL -> Icons.Filled.LocalHospital
    AmenityCategory.PHARMACY -> Icons.Filled.LocalPharmacy
    AmenityCategory.FUEL -> Icons.Filled.LocalGasStation
    AmenityCategory.TRANSIT -> Icons.Filled.DirectionsBus
    AmenityCategory.SAVED -> Icons.Filled.Bookmark
}

private fun maneuverIcon(direction: ManeuverDirection): ImageVector = when (direction) {
    ManeuverDirection.LEFT, ManeuverDirection.SLIGHT_LEFT, ManeuverDirection.SHARP_LEFT -> Icons.Filled.TurnLeft
    ManeuverDirection.RIGHT, ManeuverDirection.SLIGHT_RIGHT, ManeuverDirection.SHARP_RIGHT -> Icons.Filled.TurnRight
    ManeuverDirection.UTURN -> Icons.Filled.UTurnLeft
    ManeuverDirection.ROUNDABOUT -> Icons.Filled.RoundaboutRight
    ManeuverDirection.ARRIVE -> Icons.Filled.Flag
    ManeuverDirection.STRAIGHT -> Icons.Filled.Straight
}
