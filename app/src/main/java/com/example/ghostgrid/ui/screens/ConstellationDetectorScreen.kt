package com.example.ghostgrid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.ghostgrid.data.SavedConstellationEntity
import com.example.ghostgrid.map.LocationSnapshot
import com.example.ghostgrid.tools.CelestialNavigatorEngine
import com.example.ghostgrid.tools.CompassReading
import com.example.ghostgrid.tools.ConstellationDetectionResult
import com.example.ghostgrid.tools.MoonPosition
import com.example.ghostgrid.tools.VisibleConstellation
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryMedium
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val SkyGlassBg = Brush.verticalGradient(listOf(Color(0xE817171A), Color(0xF50B0B0D)))
private val SkyGlassRim = Brush.verticalGradient(listOf(Color(0x3CFFFFFF), Color(0x12FFFFFF)))

private enum class SkyTab(val label: String) {
    SCAN("Sky Scan"),
    SAVED("Saved")
}

/**
 * Minimal Monochrome Constellation Detector & Celestial Compass.
 * - Keeps the live hardware compass ON continuously while scanning the sky.
 * - Captures a real sky photo (or live sensor scan) and fuses star-cluster detection with
 *   exact Local Sidereal Time ephemeris + compass azimuth + sky tilt to identify constellations,
 *   show their direction on the live compass, and track the Moon's exact bearing & phase.
 * - Saves detected constellations + compass bearings + Moon bearing + photo to the encrypted Vault
 *   for offline celestial direction-finding.
 */
@Composable
fun ConstellationDetectorScreen(
    location: LocationSnapshot,
    compass: CompassReading,
    savedConstellations: List<SavedConstellationEntity>,
    latestDetection: ConstellationDetectionResult?,
    isAnalyzingSky: Boolean,
    onToggleTrueNorth: () -> Unit,
    onAnalyzeSkyPhoto: (File, Float, Float) -> Unit,
    onRunLiveSkyScan: (Float, Float) -> Unit,
    onSaveDetection: (ConstellationDetectionResult) -> Unit,
    onDeleteSaved: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(SkyTab.SCAN) }
    var lockedBearingDeg by remember { mutableStateOf<Float?>(null) }
    var lockedLabel by remember { mutableStateOf<String?>(null) }
    var manualAzimuthOffsetDeg by remember { mutableStateOf(0f) }
    var domeZoom by remember { mutableStateOf(1f) }
    var selectedConstellationId by remember { mutableStateOf<String?>(null) }
    var selectedMoonOnDome by remember { mutableStateOf(false) }
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    var capturedAzimuth by remember { mutableStateOf(0f) }
    var capturedPitch by remember { mutableStateOf(0f) }
    var nowEpochMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(5_000L)
        }
    }

    val skyAltitude = remember(compass.pitchDeg) {
        CelestialNavigatorEngine.estimateSkyAltitudeFromPitch(compass.pitchDeg)
    }

    val moonPos = remember(location.point, nowEpochMs) {
        CelestialNavigatorEngine.computeMoonPosition(
            latDeg = location.point.lat,
            lonDeg = location.point.lon,
            epochMillis = nowEpochMs
        )
    }

    val liveSkyConstellations = remember(location.point, compass.activeAzimuthDeg, skyAltitude, nowEpochMs) {
        CelestialNavigatorEngine.computeSkyConstellations(
            latDeg = location.point.lat,
            lonDeg = location.point.lon,
            lookAzimuthDeg = compass.activeAzimuthDeg.toDouble(),
            lookAltitudeDeg = skyAltitude.toDouble(),
            epochMillis = nowEpochMs
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        if (success && file != null && file.exists() && file.length() > 0L) {
            onAnalyzeSkyPhoto(file, capturedAzimuth, capturedPitch)
        }
        pendingCaptureFile = null
    }

    fun launchSkyCamera() {
        val dir = File(context.cacheDir, "sky_captures").apply { mkdirs() }
        val captureFile = File(dir, "sky_${System.currentTimeMillis()}.jpg")
        pendingCaptureFile = captureFile
        capturedAzimuth = compass.activeAzimuthDeg
        capturedPitch = compass.pitchDeg
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            captureFile
        )
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSkyCamera()
        } else {
            // Even without camera permission, live sensor + ephemeris sky detection still works
            onRunLiveSkyScan(compass.activeAzimuthDeg, compass.pitchDeg)
        }
    }

    fun requestSkyPhotoScan() {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            launchSkyCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GhostCanvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Minimal Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("sky_back_button")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = GhostTextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Constellation Detector", style = MaterialTheme.typography.titleLarge, color = GhostTextPrimary)
                Text(
                    text = "Live compass • Moon & star bearing",
                    style = MaterialTheme.typography.bodySmall,
                    color = GhostTextMuted
                )
            }
            // True / Mag North Toggle
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1EFFFFFF))
                    .border(0.8.dp, Color(0x32FFFFFF), RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleTrueNorth)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (compass.useTrueNorth) "TRUE N" else "MAG N",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Segmented Tabs: Sky Scan / Saved
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SkyTab.entries.forEach { tab ->
                val selected = activeTab == tab
                val label = if (tab == SkyTab.SAVED) "Saved (${savedConstellations.size})" else tab.label
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
                        .clickable { activeTab = tab }
                        .padding(vertical = 10.dp)
                        .testTag("sky_tab_${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.Black else Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (activeTab == SkyTab.SCAN) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Celestial Sky Dome + Active Compass Rose
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(SkyGlassBg)
                            .border(0.9.dp, SkyGlassRim, RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Live Compass Header Strip (Always ON while detecting)
                            val effectiveDomeAzimuth = ((compass.activeAzimuthDeg + manualAzimuthOffsetDeg) % 360f + 360f) % 360f
                            val effectiveCardinal = CelestialNavigatorEngine.azimuthToCardinal(effectiveDomeAzimuth)
                            val visibleDomeConstellations = remember(liveSkyConstellations) {
                                liveSkyConstellations.filter { it.centerAltitudeDeg > 0.0 }
                            }
                            val highlightedConstellationId = selectedConstellationId
                                ?: latestDetection?.constellation?.def?.id
                                ?: visibleDomeConstellations.firstOrNull()?.def?.id
                            val selectedDomeConstellation = remember(selectedConstellationId, visibleDomeConstellations) {
                                visibleDomeConstellations.firstOrNull { it.def.id == selectedConstellationId }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Explore,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "${effectiveDomeAzimuth.roundToInt()}° $effectiveCardinal",
                                        style = TabularTelemetryMedium,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = if (abs(manualAzimuthOffsetDeg) > 1f) {
                                        "EXPLORE • ${String.format(Locale.US, "%.1fx", domeZoom)}"
                                    } else {
                                        "ALT ${skyAltitude.roundToInt()}° • ${if (compass.hasHardwareSensor) "LIVE" else "GPS"}"
                                    },
                                    style = TabularCoordinateMono,
                                    color = GhostTextSecondary
                                )
                            }

                            // Interactive Live Sky Dome + Compass Dial (Drag to spin, Tap stars or Moon!)
                            LiveSkyCompassDome(
                                azimuthDeg = effectiveDomeAzimuth,
                                domeZoom = domeZoom,
                                constellations = visibleDomeConstellations,
                                highlightedId = if (selectedMoonOnDome) null else highlightedConstellationId,
                                moon = moonPos,
                                moonSelected = selectedMoonOnDome,
                                lockedBearingDeg = lockedBearingDeg,
                                onRotateDeltaDeg = { delta ->
                                    manualAzimuthOffsetDeg = (manualAzimuthOffsetDeg + delta) % 360f
                                },
                                onTapConstellation = { vc ->
                                    selectedMoonOnDome = false
                                    selectedConstellationId = vc.def.id
                                    lockedBearingDeg = vc.centerAzimuthDeg.toFloat()
                                    lockedLabel = vc.def.name
                                },
                                onTapMoon = {
                                    selectedMoonOnDome = true
                                    selectedConstellationId = null
                                    lockedBearingDeg = moonPos.azimuthDeg.toFloat()
                                    lockedLabel = "Moon"
                                },
                                modifier = Modifier
                                    .size(258.dp)
                                    .testTag("interactive_constellation_dome")
                            )

                            // Interactive Dome Controls: Zoom -, Zoom +, Reset Live Compass
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Drag to spin • Tap stars or Moon",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GhostTextMuted
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    DomeChipButton(
                                        label = "-",
                                        onClick = { domeZoom = (domeZoom - 0.3f).coerceAtLeast(1.0f) }
                                    )
                                    DomeChipButton(
                                        label = "+",
                                        onClick = { domeZoom = (domeZoom + 0.3f).coerceAtMost(2.2f) }
                                    )
                                    if (abs(manualAzimuthOffsetDeg) > 1f || domeZoom > 1.05f) {
                                        DomeChipButton(
                                            label = "Live",
                                            highlighted = true,
                                            onClick = {
                                                manualAzimuthOffsetDeg = 0f
                                                domeZoom = 1.0f
                                            }
                                        )
                                    }
                                }
                            }

                            // Interactive Selected Constellation / Moon Inspector Pill
                            if (selectedMoonOnDome) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x1EFFFFFF))
                                        .border(0.8.dp, Color(0x3CFFFFFF), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Moon • ${moonPos.phaseLabel} (${moonPos.illuminationPercent}%)",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Bearing ${moonPos.azimuthDeg.roundToInt()}° ${moonPos.cardinalDirection} • Alt ${moonPos.altitudeDeg.roundToInt()}°",
                                                style = TabularCoordinateMono,
                                                color = GhostTextSecondary
                                            )
                                        }
                                        Text(
                                            text = "Clear",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GhostTextSecondary,
                                            modifier = Modifier.clickable {
                                                selectedMoonOnDome = false
                                                lockedBearingDeg = null
                                                lockedLabel = null
                                            }
                                        )
                                    }
                                }
                            } else if (selectedDomeConstellation != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x1EFFFFFF))
                                        .border(0.8.dp, Color(0x3CFFFFFF), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = selectedDomeConstellation.def.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "${selectedDomeConstellation.centerAzimuthDeg.roundToInt()}° ${selectedDomeConstellation.cardinalDirection} • Alt ${selectedDomeConstellation.centerAltitudeDeg.roundToInt()}° • ${selectedDomeConstellation.projectedStars.size} stars",
                                                    style = TabularCoordinateMono,
                                                    color = GhostTextSecondary
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Text(
                                                    text = "Save",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White,
                                                    modifier = Modifier.clickable {
                                                        onSaveDetection(
                                                            ConstellationDetectionResult(
                                                                constellation = selectedDomeConstellation,
                                                                starsDetectedInPhoto = selectedDomeConstellation.projectedStars.size,
                                                                confidencePercent = 96,
                                                                compassAzimuthDeg = selectedDomeConstellation.centerAzimuthDeg.toFloat(),
                                                                compassCardinal = selectedDomeConstellation.cardinalDirection,
                                                                skyAltitudeDeg = selectedDomeConstellation.centerAltitudeDeg.toFloat(),
                                                                moon = moonPos
                                                            )
                                                        )
                                                    }
                                                )
                                                Text(
                                                    text = "Clear",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GhostTextSecondary,
                                                    modifier = Modifier.clickable {
                                                        selectedConstellationId = null
                                                        lockedBearingDeg = null
                                                        lockedLabel = null
                                                    }
                                                )
                                            }
                                        }
                                        Text(
                                            text = selectedDomeConstellation.def.navigationHint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GhostTextSecondary
                                        )
                                    }
                                }
                            } else if (lockedBearingDeg != null && lockedLabel != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x20FFFFFF))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Locked: $lockedLabel (${lockedBearingDeg!!.roundToInt()}°)",
                                        style = TabularCoordinateMono,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Clear",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GhostTextSecondary,
                                        modifier = Modifier.clickable {
                                            lockedBearingDeg = null
                                            lockedLabel = null
                                        }
                                    )
                                }
                            }

                            // Action Buttons: Capture Sky Photo + Instant Live Scan
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { requestSkyPhotoScan() },
                                    enabled = !isAnalyzingSky,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1.25f)
                                        .height(48.dp)
                                        .testTag("capture_sky_photo_button")
                                ) {
                                    if (isAnalyzingSky) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.Black
                                        )
                                    } else {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Photo Sky Scan")
                                }

                                OutlinedButton(
                                    onClick = { onRunLiveSkyScan(compass.activeAzimuthDeg, compass.pitchDeg) },
                                    enabled = !isAnalyzingSky,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("live_sky_scan_button")
                                ) {
                                    Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Live Scan")
                                }
                            }
                        }
                    }
                }

                // Detected Constellation Result Card (with Compass Direction + Photo + Save)
                if (latestDetection != null) {
                    item {
                        DetectedConstellationCard(
                            detection = latestDetection,
                            onLockDirection = {
                                lockedBearingDeg = latestDetection.constellation.centerAzimuthDeg.toFloat()
                                lockedLabel = latestDetection.constellation.def.name
                            },
                            onLockMoon = {
                                lockedBearingDeg = latestDetection.moon.azimuthDeg.toFloat()
                                lockedLabel = "Moon"
                            },
                            onSave = { onSaveDetection(latestDetection) }
                        )
                    }
                }

                // Live Moon Tracker Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(SkyGlassBg)
                            .border(0.85.dp, SkyGlassRim, RoundedCornerShape(18.dp))
                            .clickable {
                                lockedBearingDeg = moonPos.azimuthDeg.toFloat()
                                lockedLabel = "Moon"
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NightsStay,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Moon • ${moonPos.phaseLabel} (${moonPos.illuminationPercent}%)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = GhostTextPrimary
                                )
                                Text(
                                    text = if (moonPos.isAboveHorizon) {
                                        "Direction: ${moonPos.azimuthDeg.roundToInt()}° ${moonPos.cardinalDirection} • Alt ${moonPos.altitudeDeg.roundToInt()}°"
                                    } else {
                                        "Below horizon (${moonPos.azimuthDeg.roundToInt()}° ${moonPos.cardinalDirection} • ${moonPos.altitudeDeg.roundToInt()}°)"
                                    },
                                    style = TabularCoordinateMono,
                                    color = GhostTextSecondary
                                )
                            }
                            Text(
                                text = "Track",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                // Constellations Overhead Right Now
                item {
                    Text(
                        text = "Visible Sky Constellations",
                        style = MaterialTheme.typography.titleSmall,
                        color = GhostTextPrimary
                    )
                }

                val visibleNow = liveSkyConstellations.filter { it.centerAltitudeDeg > 0.0 }.take(8)
                items(visibleNow, key = { it.def.id }) { vc ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x12FFFFFF))
                            .border(0.8.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                            .clickable {
                                lockedBearingDeg = vc.centerAzimuthDeg.toFloat()
                                lockedLabel = vc.def.name
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(vc.def.name, style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                                Text(
                                    text = vc.def.navigationHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${vc.centerAzimuthDeg.roundToInt()}° ${vc.cardinalDirection}",
                                    style = TabularCoordinateMono,
                                    color = Color.White
                                )
                                Text(
                                    text = "Alt ${vc.centerAltitudeDeg.roundToInt()}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GhostTextMuted
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(20.dp)) }
            }
        } else {
            // Saved Constellations Tab (Encrypted Vault Sightings)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (savedConstellations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SkyGlassBg)
                                .border(0.85.dp, SkyGlassRim, RoundedCornerShape(18.dp))
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("No saved constellations yet", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                                Text(
                                    text = "Scan the sky with Photo Sky Scan or Live Scan, then save constellations to keep their compass & Moon directions offline.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GhostTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(savedConstellations, key = { it.id }) { entry ->
                        SavedConstellationCard(
                            entry = entry,
                            onUseForCompass = {
                                lockedBearingDeg = entry.azimuthDeg
                                lockedLabel = entry.constellationName
                                activeTab = SkyTab.SCAN
                            },
                            onUseMoonBearing = {
                                lockedBearingDeg = entry.moonAzimuthDeg
                                lockedLabel = "Moon (${entry.constellationName})"
                                activeTab = SkyTab.SCAN
                            },
                            onDelete = { onDeleteSaved(entry.id) }
                        )
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun DetectedConstellationCard(
    detection: ConstellationDetectionResult,
    onLockDirection: () -> Unit,
    onLockMoon: () -> Unit,
    onSave: () -> Unit
) {
    val photoBitmap = remember(detection.photoPath) {
        if (detection.photoPath.isNotBlank() && File(detection.photoPath).exists()) {
            runCatching { BitmapFactory.decodeFile(detection.photoPath) }.getOrNull()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SkyGlassBg)
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detection.constellation.def.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Compass ${detection.constellation.centerAzimuthDeg.roundToInt()}° ${detection.constellation.cardinalDirection} • Alt ${detection.constellation.centerAltitudeDeg.roundToInt()}° • ${detection.starsDetectedInPhoto} stars",
                        style = TabularCoordinateMono,
                        color = GhostTextSecondary
                    )
                }
                Text(
                    text = "${detection.confidencePercent}%",
                    style = TabularCoordinateMono.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White
                )
            }

            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap.asImageBitmap(),
                    contentDescription = "Captured sky photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(0.8.dp, Color(0x30FFFFFF), RoundedCornerShape(14.dp))
                )
            }

            Text(
                text = detection.constellation.def.navigationHint,
                style = MaterialTheme.typography.bodySmall,
                color = GhostTextPrimary
            )

            HorizontalDivider(color = Color(0x1EFFFFFF))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Scan Heading: ${detection.compassAzimuthDeg.roundToInt()}° ${detection.compassCardinal}",
                    style = TabularCoordinateMono,
                    color = GhostTextSecondary
                )
                Text(
                    text = "Moon: ${detection.moon.azimuthDeg.roundToInt()}° ${detection.moon.cardinalDirection}",
                    style = TabularCoordinateMono,
                    color = GhostTextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_constellation_button")
                ) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Save")
                }
                OutlinedButton(
                    onClick = onLockDirection,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lock Bearing")
                }
                OutlinedButton(
                    onClick = onLockMoon,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(0.85f)
                ) {
                    Text("Moon")
                }
            }
        }
    }
}

@Composable
private fun SavedConstellationCard(
    entry: SavedConstellationEntity,
    onUseForCompass: () -> Unit,
    onUseMoonBearing: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(entry.capturedAt) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(entry.capturedAt))
    }
    val thumb = remember(entry.photoPath) {
        if (entry.photoPath.isNotBlank() && File(entry.photoPath).exists()) {
            runCatching { BitmapFactory.decodeFile(entry.photoPath) }.getOrNull()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SkyGlassBg)
            .border(0.85.dp, SkyGlassRim, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.constellationName, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(
                        text = "${entry.azimuthDeg.roundToInt()}° ${entry.cardinalDirection} • Alt ${entry.altitudeDeg.roundToInt()}° • $dateLabel",
                        style = TabularCoordinateMono,
                        color = GhostTextSecondary
                    )
                    Text(
                        text = "Moon: ${entry.moonAzimuthDeg.roundToInt()}° (${entry.moonPhaseLabel})",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextMuted
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GhostAlertRed, modifier = Modifier.size(18.dp))
                }
            }

            Text(entry.navigationHint, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUseForCompass, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text("Compass Bearing")
                }
                OutlinedButton(onClick = onUseMoonBearing, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                    Text("Moon Direction")
                }
            }
        }
    }
}

@Composable
private fun DomeChipButton(
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) Color.White else Color(0x1AFFFFFF))
            .border(0.7.dp, if (highlighted) Color.White else Color(0x30FFFFFF), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (highlighted) Color.Black else Color.White
        )
    }
}

/**
 * Interactive Watch-Grade Monochrome Sky Dome + Live Rotating Compass Dial.
 * - Drag circularly to spin the 360° star dome manually.
 * - Tap any constellation or the Moon directly on the dome to highlight, lock bearing, and inspect.
 * - Supports live zoom (1.0x – 2.2x) with upright N/E/S/W cardinal & constellation labels.
 */
@Composable
private fun LiveSkyCompassDome(
    azimuthDeg: Float,
    domeZoom: Float,
    constellations: List<VisibleConstellation>,
    highlightedId: String?,
    moon: MoonPosition,
    moonSelected: Boolean,
    lockedBearingDeg: Float?,
    onRotateDeltaDeg: (Float) -> Unit,
    onTapConstellation: (VisibleConstellation) -> Unit,
    onTapMoon: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }

    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(azimuthDeg, domeZoom, constellations, moon) {
                detectTapGestures { tapOffset ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = (size.width.coerceAtMost(size.height) / 2f) - 10f

                    // Invert dome zoom and -azimuthDeg rotation into unrotated sky-dome coordinates
                    val zx = (tapOffset.x - cx) / domeZoom
                    val zy = (tapOffset.y - cy) / domeZoom
                    val rotRad = Math.toRadians(azimuthDeg.toDouble())
                    val cosR = cos(rotRad).toFloat()
                    val sinR = sin(rotRad).toFloat()
                    val skyX = cx + (zx * cosR - zy * sinR)
                    val skyY = cy + (zx * sinR + zy * cosR)

                    val maxHitPx = 42f / domeZoom

                    // 1. Check Moon hit first
                    val moonR = ((90.0 - moon.altitudeDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * (radius - 20f)
                    val moonRad = Math.toRadians(moon.azimuthDeg - 90.0)
                    val moonX = cx + (moonR * cos(moonRad)).toFloat()
                    val moonY = cy + (moonR * sin(moonRad)).toFloat()
                    if (hypot((skyX - moonX).toDouble(), (skyY - moonY).toDouble()) <= maxHitPx) {
                        onTapMoon()
                        return@detectTapGestures
                    }

                    // 2. Find closest constellation by center or any of its stars
                    var bestMatch: VisibleConstellation? = null
                    var bestDist = Double.MAX_VALUE
                    for (vc in constellations) {
                        val cR = ((90.0 - vc.centerAltitudeDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * (radius - 20f)
                        val cRad = Math.toRadians(vc.centerAzimuthDeg - 90.0)
                        val cX = cx + (cR * cos(cRad)).toFloat()
                        val cY = cy + (cR * sin(cRad)).toFloat()
                        var d = hypot((skyX - cX).toDouble(), (skyY - cY).toDouble())

                        for (st in vc.projectedStars) {
                            val sR = ((90.0 - st.altitudeDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * (radius - 20f)
                            val sRad = Math.toRadians(st.azimuthDeg - 90.0)
                            val sX = cx + (sR * cos(sRad)).toFloat()
                            val sY = cy + (sR * sin(sRad)).toFloat()
                            val sd = hypot((skyX - sX).toDouble(), (skyY - sY).toDouble())
                            if (sd < d) d = sd
                        }
                        if (d < bestDist) {
                            bestDist = d
                            bestMatch = vc
                        }
                    }

                    if (bestMatch != null && bestDist <= maxHitPx * 1.65) {
                        onTapConstellation(bestMatch)
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val curr = change.position
                    val prev = curr - dragAmount
                    val a1 = atan2((prev.y - cy).toDouble(), (prev.x - cx).toDouble())
                    val a2 = atan2((curr.y - cy).toDouble(), (curr.x - cx).toDouble())
                    var dDeg = Math.toDegrees(a2 - a1).toFloat()
                    if (dDeg > 180f) dDeg -= 360f
                    if (dDeg < -180f) dDeg += 360f
                    // Negative because dome rotates by -azimuthDeg
                    onRotateDeltaDeg(-dDeg)
                    change.consume()
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f - 10f

        // Outer ring + altitude circles (30° and 60°)
        drawCircle(color = Color(0xFF0A0A0C), radius = radius, center = Offset(cx, cy))
        drawCircle(color = Color(0x48FFFFFF), radius = radius, center = Offset(cx, cy), style = Stroke(1.6f))
        drawCircle(
            color = Color(0x1EFFFFFF),
            radius = radius * 0.66f,
            center = Offset(cx, cy),
            style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        )
        drawCircle(
            color = Color(0x1AFFFFFF),
            radius = radius * 0.33f,
            center = Offset(cx, cy),
            style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        )

        scale(scale = domeZoom, pivot = Offset(cx, cy)) {
            // Rotate sky dome opposite effective heading so "ahead" is always straight up (12 o'clock)
            rotate(degrees = -azimuthDeg, pivot = Offset(cx, cy)) {
                // 36 degree bezel ticks + upright N / E / S / W markers
                for (deg in 0 until 360 step 10) {
                    val rad = Math.toRadians(deg.toDouble() - 90.0)
                    val isMajor = deg % 90 == 0
                    val isMid = deg % 30 == 0
                    val inner = radius - when {
                        isMajor -> 12f
                        isMid -> 8f
                        else -> 4f
                    }
                    drawLine(
                        color = when {
                            deg == 0 -> Color(0xFFE5484D)
                            isMajor -> Color.White
                            else -> Color(0x55FFFFFF)
                        },
                        start = Offset(cx + (inner * cos(rad)).toFloat(), cy + (inner * sin(rad)).toFloat()),
                        end = Offset(cx + (radius * cos(rad)).toFloat(), cy + (radius * sin(rad)).toFloat()),
                        strokeWidth = if (isMajor) 2.6f else 1.2f,
                        cap = StrokeCap.Round
                    )
                }

                // Cardinal labels (N, E, S, W) drawn upright inside bezel
                val cardinals = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
                for ((deg, lbl) in cardinals) {
                    val rad = Math.toRadians(deg.toDouble() - 90.0)
                    val lx = cx + ((radius - 21f) * cos(rad)).toFloat()
                    val ly = cy + ((radius - 21f) * sin(rad)).toFloat()
                    rotate(degrees = azimuthDeg, pivot = Offset(lx, ly)) {
                        labelPaint.color = if (deg == 0) android.graphics.Color.parseColor("#E5484D") else android.graphics.Color.argb(210, 255, 255, 255)
                        labelPaint.textSize = 24f / domeZoom.coerceAtLeast(1f)
                        labelPaint.isFakeBoldText = deg == 0
                        drawContext.canvas.nativeCanvas.drawText(lbl, lx, ly + 8f / domeZoom, labelPaint)
                    }
                }

                // Visible constellations projected onto stereographic dome
                for (vc in constellations) {
                    val isHi = vc.def.id == highlightedId
                    val starPts = vc.projectedStars.map { st ->
                        val rNorm = ((90.0 - st.altitudeDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * (radius - 20f)
                        val rad = Math.toRadians(st.azimuthDeg - 90.0)
                        Offset(cx + (rNorm * cos(rad)).toFloat(), cy + (rNorm * sin(rad)).toFloat())
                    }
                    for ((a, b) in vc.def.edges) {
                        if (a in starPts.indices && b in starPts.indices) {
                            drawLine(
                                color = if (isHi) Color.White.copy(alpha = 0.92f) else Color(0x45FFFFFF),
                                start = starPts[a],
                                end = starPts[b],
                                strokeWidth = (if (isHi) 2.2f else 1.1f) / domeZoom
                            )
                        }
                    }
                    starPts.forEachIndexed { idx, pt ->
                        val mag = vc.projectedStars[idx].mag
                        val starR = ((4.2f - mag * 0.55f).coerceIn(1.6f, 4.5f)) / domeZoom.coerceAtMost(1.5f)
                        if (isHi) {
                            drawCircle(color = Color(0x38FFFFFF), radius = starR * 2.1f, center = pt)
                        }
                        drawCircle(
                            color = if (isHi) Color.White else Color(0xB8FFFFFF),
                            radius = starR,
                            center = pt
                        )
                    }

                    // Highlighted constellation target reticle + upright short name label
                    if (isHi) {
                        val cR = ((90.0 - vc.centerAltitudeDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * (radius - 20f)
                        val cRad = Math.toRadians(vc.centerAzimuthDeg - 90.0)
                        val cPt = Offset(cx + (cR * cos(cRad)).toFloat(), cy + (cR * sin(cRad)).toFloat())
                        drawCircle(
                            color = Color(0x55FFFFFF),
                            radius = 18f / domeZoom,
                            center = cPt,
                            style = Stroke(1.2f / domeZoom, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
                        )
                        val shortName = vc.def.name.substringBefore(" (")
                        val tx = cPt.x
                        val ty = cPt.y + 26f / domeZoom
                        rotate(degrees = azimuthDeg, pivot = Offset(tx, ty)) {
                            labelPaint.color = android.graphics.Color.WHITE
                            labelPaint.textSize = 23f / domeZoom
                            labelPaint.isFakeBoldText = true
                            drawContext.canvas.nativeCanvas.drawText(shortName, tx, ty, labelPaint)
                        }
                    }
                }

                // Moon Marker (Interactive)
                val moonR = ((90.0 - moon.altitudeDeg.coerceIn(0.0, 90.0)) / 90.0).toFloat() * (radius - 20f)
                val moonRad = Math.toRadians(moon.azimuthDeg - 90.0)
                val moonPt = Offset(cx + (moonR * cos(moonRad)).toFloat(), cy + (moonR * sin(moonRad)).toFloat())
                if (moonSelected) {
                    drawCircle(
                        color = Color.White,
                        radius = 16f / domeZoom,
                        center = moonPt,
                        style = Stroke(1.6f / domeZoom)
                    )
                }
                drawCircle(color = Color(0x40FFFFFF), radius = 12f / domeZoom.coerceAtMost(1.4f), center = moonPt)
                drawCircle(color = Color.White, radius = 6.5f / domeZoom.coerceAtMost(1.4f), center = moonPt)
                if (moon.illuminationPercent < 85) {
                    drawCircle(
                        color = Color(0xFF0A0A0C),
                        radius = 5f / domeZoom.coerceAtMost(1.4f),
                        center = Offset(moonPt.x + 2.5f / domeZoom, moonPt.y - 1f / domeZoom)
                    )
                }

                // Locked Target Bearing Ray
                if (lockedBearingDeg != null) {
                    val bRad = Math.toRadians(lockedBearingDeg.toDouble() - 90.0)
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, cy),
                        end = Offset(cx + ((radius - 4f) * cos(bRad)).toFloat(), cy + ((radius - 4f) * sin(bRad)).toFloat()),
                        strokeWidth = 2.0f / domeZoom,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                    )
                }
            }
        }

        // Fixed Lubber Line (Phone Forward Direction at 12 o'clock)
        val tri = Path().apply {
            moveTo(cx, cy - radius - 2f)
            lineTo(cx - 6f, cy - radius - 11f)
            lineTo(cx + 6f, cy - radius - 11f)
            close()
        }
        drawPath(tri, color = Color.White)
        drawCircle(color = Color(0x66FFFFFF), radius = 3f, center = Offset(cx, cy))
    }
}
