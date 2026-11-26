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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryLarge
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class WorldTimeSnapshot(
    val zoneId: String,
    val cityLabel: String,
    val shortAbbr: String,
    val offsetLabel: String,
    val time24: String,
    val dateLine: String
)

private val FMT_TIME_24 = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val FMT_DATE_LINE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.US)

fun resolveWorldTimeSnapshot(selectedZoneId: String, nowEpochMs: Long = System.currentTimeMillis()): WorldTimeSnapshot {
    val zone = runCatching {
        if (selectedZoneId.isBlank()) ZoneId.systemDefault() else ZoneId.of(selectedZoneId)
    }.getOrElse { ZoneId.systemDefault() }

    val zdt = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
    val offsetId = zdt.offset.id.let { if (it == "Z") "UTC+00:00" else "UTC$it" }
    val rawId = zone.id
    val city = rawId.substringAfterLast('/').replace('_', ' ')
    val abbr = zone.getDisplayName(TextStyle.SHORT, Locale.US)
        .takeIf { it.isNotBlank() && it.length <= 6 } ?: city.take(4).uppercase()

    return WorldTimeSnapshot(
        zoneId = rawId,
        cityLabel = city,
        shortAbbr = abbr,
        offsetLabel = offsetId,
        time24 = zdt.format(FMT_TIME_24),
        dateLine = zdt.format(FMT_DATE_LINE)
    )
}

@Composable
fun rememberExactSecondTickMillis(): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = System.currentTimeMillis()
            nowMs = current
            val toNextSecond = (1000L - (current % 1000L)).coerceIn(50L, 1000L)
            delay(toNextSecond)
        }
    }
    return nowMs
}

private val TimeGlassFill = Brush.verticalGradient(listOf(Color(0xE817171A), Color(0xF50B0B0D)))
private val TimeGlassRim = Brush.verticalGradient(listOf(Color(0x3CFFFFFF), Color(0x12FFFFFF)))

private data class QuickZone(val label: String, val zoneId: String)

private val QUICK_WORLD_ZONES = listOf(
    QuickZone("Local", ""),
    QuickZone("India (IST)", "Asia/Kolkata"),
    QuickZone("UTC", "UTC"),
    QuickZone("New York", "America/New_York"),
    QuickZone("London", "Europe/London"),
    QuickZone("Dubai", "Asia/Dubai"),
    QuickZone("Tokyo", "Asia/Tokyo"),
    QuickZone("Singapore", "Asia/Singapore"),
    QuickZone("Paris", "Europe/Paris"),
    QuickZone("Sydney", "Australia/Sydney"),
    QuickZone("Los Angeles", "America/Los_Angeles")
)

/**
 * First tool in GhostGrid Tools: World Time Zones.
 * Displays an exact second-accurate digital clock and searchable selector for every IANA
 * time zone in the world. Whichever zone is selected automatically drives the bottom-left
 * clock on the main Map HUD.
 */
@Composable
fun WorldTimeToolPanel(
    selectedTimeZoneId: String,
    onSelectTimeZone: (String) -> Unit
) {
    val nowMs = rememberExactSecondTickMillis()
    val activeSnap = remember(selectedTimeZoneId, nowMs) {
        resolveWorldTimeSnapshot(selectedTimeZoneId, nowMs)
    }

    var searchQuery by remember { mutableStateOf("") }

    val allZoneIds = remember {
        ZoneId.getAvailableZoneIds()
            .filter { it == "UTC" || (it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/")) }
            .sorted()
    }

    val filteredZones = remember(allZoneIds, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            allZoneIds
        } else {
            allZoneIds.filter { id ->
                id.lowercase().contains(q) || id.replace('_', ' ').lowercase().contains(q)
            }
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Active High-Precision Clock Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(TimeGlassFill)
                    .border(0.9.dp, TimeGlassRim, RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = GhostTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = activeSnap.zoneId,
                            style = TabularCoordinateMono,
                            color = GhostTextSecondary
                        )
                    }
                    Text(
                        text = activeSnap.time24,
                        style = TabularTelemetryLarge.copy(fontSize = 44.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.testTag("world_clock_active_time")
                    )
                    Text(
                        text = "${activeSnap.dateLine}  •  ${activeSnap.offsetLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextSecondary
                    )
                    Text(
                        text = "Shown on Map HUD (bottom-left)",
                        style = MaterialTheme.typography.labelSmall,
                        color = GhostTextMuted
                    )
                }
            }
        }

        // Quick World Zone Pills
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QUICK_WORLD_ZONES) { qz ->
                    val isSelected = if (qz.zoneId.isEmpty()) {
                        selectedTimeZoneId.isBlank()
                    } else {
                        activeSnap.zoneId == qz.zoneId
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) Color.White else Color(0x18FFFFFF))
                            .border(
                                0.8.dp,
                                if (isSelected) Color.White else Color(0x2AFFFFFF),
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { onSelectTimeZone(qz.zoneId) }
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                            .testTag("quick_tz_${qz.label.lowercase().replace(' ', '_')}")
                    ) {
                        Text(
                            text = qz.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) Color.Black else Color.White
                        )
                    }
                }
            }
        }

        // Search ALL World Time Zones
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search all ${allZoneIds.size} world time zones…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = GhostTextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = GhostTextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("timezone_search_input")
            )
        }

        // All World Zones List with Live Ticking Times
        items(filteredZones.take(80), key = { it }) { zoneId ->
            val snap = resolveWorldTimeSnapshot(zoneId, nowMs)
            val isSelected = activeSnap.zoneId == zoneId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) Color(0x28FFFFFF) else Color(0x10FFFFFF))
                    .border(
                        0.8.dp,
                        if (isSelected) Color.White else Color(0x1EFFFFFF),
                        RoundedCornerShape(16.dp)
                    )
                    .clickable { onSelectTimeZone(zoneId) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snap.cityLabel,
                            style = MaterialTheme.typography.titleSmall,
                            color = GhostTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${zoneId.replace('_', ' ')} • ${snap.offsetLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = snap.time24,
                        style = TabularCoordinateMono.copy(fontWeight = FontWeight.Medium),
                        color = Color.White
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (filteredZones.size > 80) {
            item {
                Text(
                    text = "Showing 80 of ${filteredZones.size} zones. Type a city or country above to filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostTextMuted,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
