package com.example.ghostgrid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ghostgrid.data.SavedPlaceEntity
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.search.PlaceResult
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostBorder
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostPurpleContainer
import com.example.ghostgrid.ui.theme.GhostPurplePrimary
import com.example.ghostgrid.ui.theme.GhostPurpleSoft
import com.example.ghostgrid.ui.theme.GhostSurfaceSolid
import com.example.ghostgrid.ui.theme.GhostSurfaceVariant
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono

@Composable
fun SavedPlacesScreen(
    savedPlaces: List<SavedPlaceEntity>,
    currentLocation: GeoPoint,
    onSaveCurrentLocation: (name: String, listName: String, notes: String) -> Unit,
    onNavigateToPlace: (PlaceResult) -> Unit,
    onDeletePlace: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedList by remember { mutableStateOf("All") }
    var newName by remember { mutableStateOf("") }
    var newList by remember { mutableStateOf("Favorites") }
    var newNotes by remember { mutableStateOf("") }

    val availableLists = remember(savedPlaces) {
        (listOf("All", "Favorites", "Food Spots", "Hospitals", "Safe Zones") +
            savedPlaces.map { it.listName }).distinct()
    }
    val filtered = remember(savedPlaces, selectedList) {
        if (selectedList == "All") savedPlaces else savedPlaces.filter { it.listName == selectedList }
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
            IconButton(onClick = onBack, modifier = Modifier.testTag("saved_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = GhostTextPrimary
                )
            }
            Column {
                Text("Encrypted Saved Places", style = MaterialTheme.typography.titleLarge, color = GhostTextPrimary)
                Text("Stored offline inside SQLCipher vault", style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
            }
        }

        // List Filter Chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableLists) { listName ->
                val active = selectedList == listName
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) GhostPurplePrimary else GhostSurfaceSolid,
                    modifier = Modifier
                        .border(1.dp, if (active) GhostPurpleSoft else GhostBorder, RoundedCornerShape(999.dp))
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { selectedList = listName }
                ) {
                    Text(
                        text = listName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) com.example.ghostgrid.ui.theme.GhostOnAccent else GhostTextSecondary,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quick Bookmark Current GPS Location Card
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = GhostSurfaceSolid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GhostBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Save Current GPS Position", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                        Text(
                            text = String.format("Lat %.5f, Lon %.5f", currentLocation.lat, currentLocation.lon),
                            style = TabularCoordinateMono,
                            color = GhostPurpleSoft
                        )
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Place Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newList,
                                onValueChange = { newList = it },
                                label = { Text("List") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newNotes,
                                onValueChange = { newNotes = it },
                                label = { Text("Note (optional)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onSaveCurrentLocation(newName, newList, newNotes)
                                    newName = ""
                                    newNotes = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GhostPurplePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_current_gps_button")
                        ) {
                            Icon(Icons.Filled.AddLocationAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Encrypt & Save Waypoint")
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GhostSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No places saved in '$selectedList' yet. Tap any point on the map or save your current GPS location above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { sp ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = GhostSurfaceSolid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GhostBorder, RoundedCornerShape(16.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sp.name, style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                                Text(
                                    text = "${sp.listName} • ${sp.address}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                                if (sp.notes.isNotBlank()) {
                                    Text(sp.notes, style = MaterialTheme.typography.bodySmall, color = GhostPurpleSoft)
                                }
                            }
                            IconButton(
                                onClick = {
                                    onNavigateToPlace(
                                        PlaceResult(
                                            id = "saved:${sp.id}",
                                            name = sp.name,
                                            category = sp.category,
                                            subcategory = sp.listName,
                                            address = sp.address,
                                            lat = sp.lat,
                                            lon = sp.lon,
                                            distanceMeters = 0.0,
                                            regionId = "vault",
                                            isSaved = true,
                                            savedListName = sp.listName
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Navigation, contentDescription = "Navigate", tint = GhostPurpleSoft)
                            }
                            IconButton(onClick = { onDeletePlace(sp.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GhostAlertRed)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
