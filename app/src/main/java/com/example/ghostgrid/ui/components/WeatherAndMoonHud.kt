package com.example.ghostgrid.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ghostgrid.data.TemperatureUnit
import com.example.ghostgrid.hybrid.DailyForecastPoint
import com.example.ghostgrid.hybrid.HourlyForecastPoint
import com.example.ghostgrid.hybrid.WeatherIntel
import com.example.ghostgrid.i18n.LocalGhostStrings
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.tools.CelestialNavigatorEngine
import com.example.ghostgrid.tools.MoonPosition
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryLarge
import com.example.ghostgrid.ui.theme.TabularTelemetryMedium
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

private val HudGlassFill = Brush.verticalGradient(listOf(Color(0xD91A1A1D), Color(0xE80C0C0E)))
private val HudGlassRim = Brush.verticalGradient(listOf(Color(0x42FFFFFF), Color(0x14FFFFFF)))
private val HudSheetFill = Brush.verticalGradient(listOf(Color(0xF6161619), Color(0xFC09090B)))

/**
 * Astronomically accurate monochrome Moon Phase Canvas icon.
 * Reflects exact lunar illumination, waxing/waning side, and Northern vs Southern hemisphere
 * orientation as the user moves across the globe.
 */
@Composable
fun MoonPhaseGlyph(
    phaseFraction: Double,
    isNorthernHemisphere: Boolean = true,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2f - 1f
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val circlePath = Path().apply {
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        }

        // Unlit dark lunar face
        drawCircle(color = Color(0xFF1A1A1E), radius = r, center = Offset(cx, cy))

        val p = ((phaseFraction % 1.0) + 1.0) % 1.0
        val waxing = p < 0.5
        val litOnRight = if (isNorthernHemisphere) waxing else !waxing

        // Half-phase cosine factor: +1 at New, 0 at Quarter, -1 at Full
        val cosPhase = cos(2.0 * PI * p).toFloat()
        val terminatorRx = abs(cosPhase) * r

        clipPath(circlePath) {
            if (p > 0.02 && p < 0.98) {
                val litPath = Path()
                val ovalBounds = Rect(cx - r, cy - r, cx + r, cy + r)
                val termBounds = Rect(cx - terminatorRx, cy - r, cx + terminatorRx, cy + r)

                if (litOnRight) {
                    // Outer right semicircle from top (270°) clockwise to bottom (90°)
                    litPath.arcTo(ovalBounds, -90f, 180f, true)
                    // Terminator back from bottom (90°) to top (-90°)
                    val sweep = if (cosPhase < 0f) 180f else -180f
                    if (terminatorRx > 0.5f) {
                        litPath.arcTo(termBounds, 90f, sweep, false)
                    } else {
                        litPath.lineTo(cx, cy - r)
                    }
                    litPath.close()
                } else {
                    // Outer left semicircle from bottom (90°) clockwise to top (270°)
                    litPath.arcTo(ovalBounds, 90f, 180f, true)
                    val sweep = if (cosPhase < 0f) 180f else -180f
                    if (terminatorRx > 0.5f) {
                        litPath.arcTo(termBounds, -90f, sweep, false)
                    } else {
                        litPath.lineTo(cx, cy + r)
                    }
                    litPath.close()
                }
                drawPath(litPath, color = Color(0xFFF4F4F5))
            }
        }

        // Subtle crisp monochrome rim
        drawCircle(
            color = Color(0x70FFFFFF),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1.2f)
        )
    }
}

/**
 * Combined Bottom-Left Smoked-Glass Stack directly above the floating Bottom Dock:
 * 1. Live Weather + Moon Phase pill with 1-tap °C / °F / K switcher & Forecast sheet trigger.
 * 2. Live exact-second World Time pill right below it.
 * Both Weather and Moon Phase dynamically update as the user moves or pans the map.
 */
@Composable
fun MapWeatherMoonAndTimeHud(
    focusPoint: GeoPoint,
    weather: WeatherIntel?,
    isLoadingWeather: Boolean,
    tempUnit: TemperatureUnit,
    selectedTimeZoneId: String,
    strictOfflineMode: Boolean,
    onCycleTempUnit: () -> Unit,
    onOpenWeatherSheet: () -> Unit,
    onOpenTimeTool: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalGhostStrings.current
    val nowMs = rememberExactSecondTickMillis()
    val timeSnap = resolveWorldTimeSnapshot(selectedTimeZoneId, nowMs)

    // Recompute Moon phase, altitude & azimuth cleanly every 30s or immediately when user moves
    var moonTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            moonTick = System.currentTimeMillis()
        }
    }
    val moon = remember(focusPoint.lat, focusPoint.lon, moonTick) {
        CelestialNavigatorEngine.computeMoonPosition(focusPoint.lat, focusPoint.lon, moonTick)
    }

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 14.dp, bottom = 114.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // 1. Weather + Moon Phase Smoked-Glass Pill (directly above Time)
        Box(
            modifier = Modifier
                .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(HudGlassFill)
                .border(0.85.dp, HudGlassRim, RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenWeatherSheet)
                .padding(horizontal = 11.dp, vertical = 8.dp)
                .testTag("map_weather_moon_pill")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Weather Row: Icon + Temp + Direct °C / °F / K Switcher Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isLoadingWeather && weather == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.6.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = weatherCodeIcon(weather?.weatherCode ?: 0, weather?.isDay ?: true),
                            contentDescription = "Weather",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = when {
                            weather != null -> tempUnit.format(weather.temperatureC)
                            strictOfflineMode -> strings.offlineLabel
                            else -> "--${tempUnit.symbol}"
                        },
                        style = TabularCoordinateMono.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )

                    // Direct 1-tap °C / °F / K switcher right on the map pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x28FFFFFF))
                            .border(0.6.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                            .clickable(onClick = onCycleTempUnit)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("map_temp_unit_cycle")
                    ) {
                        Text(
                            text = tempUnit.symbol,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                }

                // Moon Phase Row: Astronomically accurate Canvas glyph + phase & illumination
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MoonPhaseGlyph(
                        phaseFraction = moon.phaseFraction,
                        isNorthernHemisphere = moon.isNorthernHemisphere,
                        size = 14.dp
                    )
                    Text(
                        text = "${shortMoonLabel(moon.phaseLabel)} ${moon.illuminationPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = GhostTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. World Time Smoked-Glass Pill (directly below Weather & Moon)
        Box(
            modifier = Modifier
                .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(18.dp))
                .background(HudGlassFill)
                .border(0.85.dp, HudGlassRim, RoundedCornerShape(18.dp))
                .clickable(onClick = onOpenTimeTool)
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
                        text = timeSnap.time24,
                        style = TabularCoordinateMono.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
                Text(
                    text = "${timeSnap.cityLabel.take(14)} • ${timeSnap.shortAbbr}",
                    style = MaterialTheme.typography.labelSmall,
                    color = GhostTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Smoked-Glass Monochrome Weather Forecast & Live Moon Phase Bottom Sheet.
 * Supports switching °C / °F / K instantly, viewing hourly + 6-day forecast,
 * and tracking real-time Moon phase, illumination, azimuth & altitude for the active location.
 */
@Composable
fun WeatherForecastSheet(
    focusPoint: GeoPoint,
    weather: WeatherIntel?,
    isLoadingWeather: Boolean,
    tempUnit: TemperatureUnit,
    strictOfflineMode: Boolean,
    onSelectTempUnit: (TemperatureUnit) -> Unit,
    onRefreshWeather: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalGhostStrings.current
    val moon = remember(focusPoint.lat, focusPoint.lon, weather?.fetchedAtMs) {
        CelestialNavigatorEngine.computeMoonPosition(focusPoint.lat, focusPoint.lon)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(HudSheetFill)
            .border(0.85.dp, HudGlassRim, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + °C / °F / K Segmented Switcher
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.weatherAndMoon,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = String.format(Locale.US, "%.3f°, %.3f°", focusPoint.lat, focusPoint.lon),
                            style = TabularCoordinateMono,
                            color = GhostTextMuted
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!strictOfflineMode) {
                            IconButton(
                                onClick = onRefreshWeather,
                                modifier = Modifier.testTag("weather_refresh_button")
                            ) {
                                if (isLoadingWeather) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.White)
                                }
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag("weather_close_button")) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = GhostTextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Instant Celsius / Fahrenheit / Kelvin Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TemperatureUnit.entries.forEach { unit ->
                        val selected = unit == tempUnit
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Color.White else Color(0x16FFFFFF))
                                .border(
                                    0.8.dp,
                                    if (selected) Color.White else Color(0x28FFFFFF),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectTempUnit(unit) }
                                .padding(vertical = 9.dp)
                                .testTag("temp_unit_${unit.id.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${unit.label} (${unit.symbol})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (selected) Color.Black else Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Current Weather Card
            item {
                if (weather != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x12FFFFFF))
                            .border(0.8.dp, Color(0x24FFFFFF), RoundedCornerShape(18.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = weatherCodeIcon(weather.weatherCode, weather.isDay),
                                        contentDescription = weather.weatherLabel,
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Column {
                                        Text(
                                            text = tempUnit.format(weather.temperatureC, decimals = 1),
                                            style = TabularTelemetryLarge,
                                            color = Color.White
                                        )
                                        Text(
                                            text = weather.weatherLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GhostTextSecondary
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "H ${tempUnit.format(weather.tempMaxC)}  L ${tempUnit.format(weather.tempMinC)}",
                                        style = TabularCoordinateMono,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Feels ${tempUnit.format(weather.apparentTempC)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GhostTextMuted
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0x1AFFFFFF))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricMini("Wind", "${weather.windSpeedKmh.roundToInt()} km/h")
                                MetricMini("Humidity", "${weather.humidityPercent}%")
                                MetricMini("Pressure", "${weather.pressureHpa.roundToInt()} hPa")
                                MetricMini("Sun", "${weather.sunriseLocal}/${weather.sunsetLocal}")
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x12FFFFFF))
                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (strictOfflineMode) {
                                "Offline lock is active — Moon phase below updates 100% offline. Go online briefly to refresh live weather & forecast."
                            } else {
                                "Fetching live weather & forecast for your current position…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextSecondary
                        )
                    }
                }
            }

            // Hourly Forecast Strip
            if (weather != null && weather.hourlyForecast.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = strings.hourlyForecast,
                            style = MaterialTheme.typography.labelSmall,
                            color = GhostTextMuted
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(weather.hourlyForecast) { h ->
                                HourlyForecastChip(point = h, unit = tempUnit)
                            }
                        }
                    }
                }
            }

            // 6-Day Forecast List
            if (weather != null && weather.dailyForecast.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = strings.dailyForecast,
                            style = MaterialTheme.typography.labelSmall,
                            color = GhostTextMuted
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x10FFFFFF))
                                .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                weather.dailyForecast.forEachIndexed { idx, day ->
                                    DailyForecastRow(point = day, unit = tempUnit)
                                    if (idx < weather.dailyForecast.lastIndex) {
                                        HorizontalDivider(color = Color(0x14FFFFFF))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Moon Phase Card (100% offline ephemeris, updates as user moves!)
            item {
                MoonDetailCard(moon = moon)
            }
        }
    }
}

@Composable
private fun HourlyForecastChip(point: HourlyForecastPoint, unit: TemperatureUnit) {
    Column(
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x14FFFFFF))
            .border(0.7.dp, Color(0x24FFFFFF), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = point.timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = GhostTextSecondary,
            maxLines = 1
        )
        Icon(
            imageVector = weatherCodeIcon(point.weatherCode, point.isDay),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = unit.format(point.temperatureC),
            style = TabularCoordinateMono.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1
        )
        if (point.precipProbPct > 0) {
            Text(
                text = "${point.precipProbPct}%",
                style = MaterialTheme.typography.labelSmall,
                color = GhostTextMuted
            )
        }
    }
}

@Composable
private fun DailyForecastRow(point: DailyForecastPoint, unit: TemperatureUnit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = point.dayLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.width(52.dp)
        )
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = weatherCodeIcon(point.weatherCode, isDay = true),
                contentDescription = point.weatherLabel,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = point.weatherLabel,
                style = MaterialTheme.typography.bodySmall,
                color = GhostTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (point.precipProbMaxPct > 0) {
            Text(
                text = "${point.precipProbMaxPct}%",
                style = MaterialTheme.typography.labelSmall,
                color = GhostTextMuted,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        Text(
            text = "${unit.format(point.tempMinC)} / ${unit.format(point.tempMaxC)}",
            style = TabularCoordinateMono,
            color = Color.White
        )
    }
}

@Composable
private fun MoonDetailCard(moon: MoonPosition) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x12FFFFFF))
            .border(0.8.dp, Color(0x28FFFFFF), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A0A0C)),
                contentAlignment = Alignment.Center
            ) {
                MoonPhaseGlyph(
                    phaseFraction = moon.phaseFraction,
                    isNorthernHemisphere = moon.isNorthernHemisphere,
                    size = 38.dp
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${moon.phaseLabel} • ${moon.illuminationPercent}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    text = "Bearing ${moon.azimuthDeg.roundToInt()}° ${moon.cardinalDirection} • Alt ${moon.altitudeDeg.roundToInt()}°",
                    style = TabularCoordinateMono,
                    color = GhostTextSecondary
                )
                Text(
                    text = "${if (moon.isAboveHorizon) "Visible above horizon" else "Below horizon"} • ${if (moon.isNorthernHemisphere) "Northern" else "Southern"} sky view",
                    style = MaterialTheme.typography.labelSmall,
                    color = GhostTextMuted
                )
            }
        }
    }
}

@Composable
private fun MetricMini(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
        Text(value, style = TabularCoordinateMono, color = GhostTextPrimary)
    }
}

private fun shortMoonLabel(phaseLabel: String): String = when (phaseLabel) {
    "Waxing Crescent" -> "Wax Cres"
    "Waxing Gibbous" -> "Wax Gibb"
    "Waning Gibbous" -> "Wan Gibb"
    "Waning Crescent" -> "Wan Cres"
    "First Quarter" -> "1st Qtr"
    "Last Quarter" -> "3rd Qtr"
    else -> phaseLabel
}

fun weatherCodeIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0 -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.NightsStay
    1, 2 -> Icons.Filled.WbCloudy
    3, 45, 48 -> Icons.Filled.Cloud
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Filled.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Filled.AcUnit
    95, 96, 99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Grain
}
