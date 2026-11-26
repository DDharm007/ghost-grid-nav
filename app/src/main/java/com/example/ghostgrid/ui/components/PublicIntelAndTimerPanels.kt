package com.example.ghostgrid.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghostgrid.hybrid.LiveGeoIntelBundle
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryLarge
import kotlinx.coroutines.delay

private val GlassCardBg = Brush.verticalGradient(listOf(Color(0xE617171A), Color(0xF20B0B0D)))
private val GlassCardRim = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x10FFFFFF)))

/**
 * Live Geo Intel panel backed by free zero-key APIs from public-apis/public-apis:
 * Open-Meteo (Weather, UV, Sun, Air Quality, Elevation), Nominatim + REST Countries, USGS Earthquakes.
 */
@Composable
fun PublicGeoIntelPanel(
    intel: LiveGeoIntelBundle?,
    isLoading: Boolean,
    error: String?,
    strictOfflineMode: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Environment & Area", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading && !strictOfflineMode,
                    modifier = Modifier.testTag("refresh_intel_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            }
        }

        if (strictOfflineMode) {
            item {
                GlassCard {
                    Text("Offline lock enabled", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        text = "Turn off offline lock on the map to fetch live weather, air quality, elevation, and seismic alerts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextSecondary
                    )
                }
            }
        } else if (error != null && intel == null) {
            item {
                GlassCard {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                    OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                        Text("Retry")
                    }
                }
            }
        }

        // Weather, Air Quality & Elevation (Open-Meteo)
        intel?.weather?.let { w ->
            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = String.format("%.1f°C", w.temperatureC),
                                style = TabularTelemetryLarge.copy(fontSize = 36.sp),
                                color = Color.White
                            )
                            Text(
                                text = "${w.weatherLabel} • Feels ${String.format("%.0f°", w.apparentTempC)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = GhostTextSecondary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "H ${String.format("%.0f°", w.tempMaxC)}  L ${String.format("%.0f°", w.tempMinC)}",
                                style = TabularCoordinateMono,
                                color = Color.White
                            )
                            w.elevationMeters?.let { elev ->
                                Text(
                                    text = "Elev ${elev.toInt()} m",
                                    style = TabularCoordinateMono,
                                    color = GhostTextMuted
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x1EFFFFFF))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPair("WIND", "${String.format("%.0f", w.windSpeedKmh)} km/h")
                        MetricPair("HUMID", "${w.humidityPercent}%")
                        MetricPair("PRESS", "${w.pressureHpa.toInt()} hPa")
                        MetricPair("UV", String.format("%.1f", w.uvIndexMax))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPair("SUNRISE", w.sunriseLocal.ifBlank { "—" })
                        MetricPair("SUNSET", w.sunsetLocal.ifBlank { "—" })
                        MetricPair("US AQI", w.usAqi?.toString() ?: "—")
                        MetricPair("PM2.5", w.pm25?.let { String.format("%.1f", it) } ?: "—")
                    }
                }
            }
        }

        // Country & Area Info (Nominatim + REST Countries)
        intel?.country?.let { c ->
            item {
                GlassCard {
                    Text(c.commonName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(c.addressSummary, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                    HorizontalDivider(color = Color(0x1EFFFFFF))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPair("CAPITAL", c.capital)
                        MetricPair("DIAL", c.callingCode)
                        MetricPair("DRIVE", c.drivingSide)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricPair("CURRENCY", c.currencySummary)
                        MetricPair("TZ", c.timezone)
                    }
                }
            }
        }

        // Seismic Feed (USGS Earthquake API)
        if (intel != null) {
            item {
                GlassCard {
                    Text("Seismic Activity (600 km)", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    if (intel.quakes.isEmpty()) {
                        Text("No recent earthquakes recorded nearby.", style = MaterialTheme.typography.bodySmall, color = GhostTextMuted)
                    } else {
                        intel.quakes.forEach { q ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(q.place, style = MaterialTheme.typography.bodySmall, color = Color.White)
                                    Text(
                                        text = "${q.distanceKm.toInt()} km away • Depth ${q.depthKm.toInt()} km",
                                        style = TabularCoordinateMono,
                                        color = GhostTextMuted
                                    )
                                }
                                Text(
                                    text = String.format("M %.1f", q.magnitude),
                                    style = TabularCoordinateMono,
                                    color = if (q.magnitude >= 4.5) GhostAlertRed else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Offline Precision Stopwatch (with laps) & Countdown Timer.
 */
@Composable
fun StopwatchAndTimerPanel() {
    // Stopwatch state
    var swRunning by remember { mutableStateOf(false) }
    var swElapsedMs by remember { mutableLongStateOf(0L) }
    val laps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(swRunning) {
        if (!swRunning) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (swRunning) {
            delay(35)
            val now = System.currentTimeMillis()
            swElapsedMs += (now - last)
            last = now
        }
    }

    // Countdown state
    var cdTotalMs by remember { mutableLongStateOf(5 * 60_000L) }
    var cdRemainingMs by remember { mutableLongStateOf(5 * 60_000L) }
    var cdRunning by remember { mutableStateOf(false) }

    LaunchedEffect(cdRunning) {
        if (!cdRunning) return@LaunchedEffect
        var last = System.currentTimeMillis()
        while (cdRunning && cdRemainingMs > 0L) {
            delay(100)
            val now = System.currentTimeMillis()
            cdRemainingMs = (cdRemainingMs - (now - last)).coerceAtLeast(0L)
            last = now
            if (cdRemainingMs == 0L) cdRunning = false
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Stopwatch
        item {
            GlassCard {
                Text("Stopwatch", style = MaterialTheme.typography.titleSmall, color = GhostTextSecondary)
                Text(
                    text = formatStopwatch(swElapsedMs),
                    style = TabularTelemetryLarge.copy(fontSize = 38.sp),
                    color = Color.White,
                    modifier = Modifier.testTag("stopwatch_display")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { swRunning = !swRunning },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (swRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (swRunning) "Pause" else "Start")
                    }
                    OutlinedButton(
                        onClick = { if (swElapsedMs > 0L) laps.add(0, swElapsedMs) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Flag, contentDescription = "Lap", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lap")
                    }
                    OutlinedButton(
                        onClick = {
                            swRunning = false
                            swElapsedMs = 0L
                            laps.clear()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        if (laps.isNotEmpty()) {
            itemsIndexed(laps) { idx, lapMs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Lap ${laps.size - idx}", style = MaterialTheme.typography.bodySmall, color = GhostTextMuted)
                    Text(formatStopwatch(lapMs), style = TabularCoordinateMono, color = Color.White)
                }
            }
        }

        // Countdown Timer
        item {
            GlassCard {
                Text("Countdown", style = MaterialTheme.typography.titleSmall, color = GhostTextSecondary)
                Text(
                    text = formatCountdown(cdRemainingMs),
                    style = TabularTelemetryLarge.copy(fontSize = 36.sp),
                    color = if (cdRemainingMs == 0L) GhostAlertRed else Color.White
                )
                LinearProgressIndicator(
                    progress = {
                        if (cdTotalMs > 0L) (cdRemainingMs.toFloat() / cdTotalMs.toFloat()).coerceIn(0f, 1f) else 0f
                    },
                    color = Color.White,
                    trackColor = Color(0xFF27272A),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "1m", 5 to "5m", 15 to "15m", 30 to "30m").forEach { (mins, lbl) ->
                        val ms = mins * 60_000L
                        val active = cdTotalMs == ms
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Color.White else Color(0x18FFFFFF))
                                .clickable {
                                    cdRunning = false
                                    cdTotalMs = ms
                                    cdRemainingMs = ms
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(lbl, style = MaterialTheme.typography.labelMedium, color = if (active) Color.Black else Color.White)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (cdRemainingMs == 0L) cdRemainingMs = cdTotalMs
                            cdRunning = !cdRunning
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (cdRunning) "Pause" else "Start")
                    }
                    OutlinedButton(
                        onClick = {
                            cdRunning = false
                            cdRemainingMs = cdTotalMs
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GlassCardBg)
            .border(0.85.dp, GlassCardRim, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
private fun MetricPair(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
        Text(value, style = TabularCoordinateMono, color = Color.White)
    }
}

private fun formatStopwatch(ms: Long): String {
    val mins = (ms / 60_000L)
    val secs = (ms / 1_000L) % 60L
    val cs = (ms / 10L) % 100L
    return String.format("%02d:%02d.%02d", mins, secs, cs)
}

private fun formatCountdown(ms: Long): String {
    val totalSec = (ms + 999L) / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%02d:%02d", mins, secs)
}
