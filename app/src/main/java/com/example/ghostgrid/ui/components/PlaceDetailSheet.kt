package com.example.ghostgrid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ghostgrid.hybrid.PoiEnrichment
import com.example.ghostgrid.routing.RoutingProfile
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.PlaceResult
import com.example.ghostgrid.ui.theme.GhostOnAccent
import com.example.ghostgrid.ui.theme.GhostSurfaceVariant
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono

@Composable
fun PlaceDetailSheet(
    place: PlaceResult,
    activeProfile: RoutingProfile,
    strictOfflineMode: Boolean,
    enrichment: PoiEnrichment?,
    isLoadingEnrichment: Boolean,
    enrichmentError: String?,
    showCompassFallbackOption: Boolean,
    onSelectProfile: (RoutingProfile) -> Unit,
    onNavigate: () -> Unit,
    onCompassBearing: () -> Unit,
    onSaveToList: (String) -> Unit,
    onFetchLiveEnrichment: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showListPicker by remember { mutableStateOf(false) }
    var customListName by remember { mutableStateOf("") }
    val presetLists = listOf("Favorites", "Food Spots", "Hospitals", "Safe Zones")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF5161618), Color(0xFC0A0A0C))))
            .border(
                0.85.dp,
                Brush.verticalGradient(listOf(Color(0x42FFFFFF), Color(0x14FFFFFF))),
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = GhostTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = place.address.ifBlank { place.subcategory },
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = GeoMath.formatDistance(place.distanceMeters),
                            style = TabularCoordinateMono,
                            color = Color.White
                        )
                        Text(
                            text = String.format("%.5f, %.5f", place.lat, place.lon),
                            style = TabularCoordinateMono,
                            color = GhostTextMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = GhostTextSecondary)
                }
            }

            // Minimal Monochrome Routing Profile Pills
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RoutingProfile.entries) { profile ->
                    val selected = profile == activeProfile
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Color.White else Color(0x18FFFFFF))
                            .border(
                                0.8.dp,
                                if (selected) Color.White else Color(0x26FFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectProfile(profile) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = profile.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) GhostOnAccent else GhostTextSecondary
                        )
                    }
                }
            }

            // Primary Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("place_navigate_button")
                ) {
                    Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Directions")
                }

                OutlinedButton(
                    onClick = { showListPicker = !showListPicker },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("place_save_button")
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }

            if (showCompassFallbackOption) {
                OutlinedButton(
                    onClick = onCompassBearing,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compass_fallback_button")
                ) {
                    Icon(Icons.Filled.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Direct Bearing", color = Color.White)
                }
            }

            if (showListPicker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GhostSurfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetLists) { list ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable {
                                        onSaveToList(list)
                                        showListPicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(text = list, style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customListName,
                            onValueChange = { customListName = it },
                            placeholder = { Text("Custom list") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (customListName.isNotBlank()) {
                                    onSaveToList(customListName.trim())
                                    customListName = ""
                                    showListPicker = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Add")
                        }
                    }
                }
            }

            if (enrichment != null) {
                HorizontalDivider(color = Color(0x1EFFFFFF))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    enrichment.rating?.let { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = String.format("%.1f (%d)", r, enrichment.userRatingCount ?: 0),
                                style = TabularCoordinateMono,
                                color = Color.White
                            )
                        }
                    }
                    enrichment.isOpenNow?.let { open ->
                        Text(
                            text = if (open) "Open" else "Closed",
                            style = MaterialTheme.typography.labelSmall,
                            color = GhostTextSecondary
                        )
                    }
                }
                enrichment.weekdayHours.take(2).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                }
            }
        }
    }
}
