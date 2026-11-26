package com.example.ghostgrid.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ghostgrid.map.LocationSnapshot
import com.example.ghostgrid.tools.CompassAccuracy
import com.example.ghostgrid.tools.CompassReading
import com.example.ghostgrid.tools.TorchAndCompassController
import com.example.ghostgrid.tools.TorchMode
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostAlertRedContainer
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryLarge
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val MonoGlassBg = Brush.verticalGradient(listOf(Color(0xE617171A), Color(0xF20B0B0D)))
private val MonoGlassRim = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x10FFFFFF)))

@Composable
fun PrecisionCompassPanel(
    location: LocationSnapshot,
    torchMode: TorchMode,
    torchAvailable: Boolean,
    compass: CompassReading,
    onToggleTrueNorth: () -> Unit,
    onSetTorchMode: (TorchMode) -> Unit,
    onTransmitMorse: (String) -> Unit,
    onActivateScreenLight: (Color?) -> Unit,
    onNotify: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var lockedTargetDeg by remember { mutableStateOf<Float?>(null) }
    var morseInput by remember { mutableStateOf("") }

    // Shortest-path unwrapped rotation angle (eliminates 359° -> 0° jump)
    var unwrappedRotation by remember { mutableFloatStateOf(-compass.activeAzimuthDeg) }
    LaunchedEffect(compass.activeAzimuthDeg) {
        val target = -compass.activeAzimuthDeg
        val delta = ((target - unwrappedRotation + 540f) % 360f + 360f) % 360f - 180f
        unwrappedRotation += delta
    }
    val smoothDialAngle by animateFloatAsState(
        targetValue = unwrappedRotation,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "compass_dial_rotation"
    )

    val headingInt = compass.activeAzimuthDeg.roundToInt() % 360
    val isLevel = abs(compass.pitchDeg) <= 2.5f && abs(compass.rollDeg) <= 2.5f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Aviation-Grade Minimal Monochrome Compass + Spirit Level
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MonoGlassBg)
                    .border(0.85.dp, MonoGlassRim, RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Heading & Mode Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = String.format("%03d°", headingInt),
                                style = TabularTelemetryLarge.copy(fontSize = 36.sp),
                                color = Color.White,
                                modifier = Modifier.testTag("compass_heading_text")
                            )
                            Text(
                                text = compass.cardinal,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = GhostTextSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // True North / Magnetic North Toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x1EFFFFFF))
                                .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                                .clickable(onClick = onToggleTrueNorth)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("compass_north_mode_toggle")
                        ) {
                            Text(
                                text = if (compass.useTrueNorth) "TRUE N" else "MAG N",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    // Rotating Watch-Grade Compass Bezel + Built-in 2-Axis Bubble Level
                    Box(
                        modifier = Modifier
                            .size(264.dp)
                            .clip(CircleShape)
                            .clickable {
                                lockedTargetDeg = if (lockedTargetDeg == null) {
                                    compass.activeAzimuthDeg
                                } else {
                                    null
                                }
                            }
                            .testTag("compass_dial_canvas"),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val outerR = size.minDimension / 2f - 10f

                            // Outer smoked bezel fill
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFF141416), Color(0xFF09090B)),
                                    center = Offset(cx, cy),
                                    radius = outerR
                                ),
                                radius = outerR,
                                center = Offset(cx, cy)
                            )

                            // Hairline outer bevel ring
                            drawCircle(
                                color = Color(0xFF3A3A3F),
                                radius = outerR,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.2f)
                            )
                            drawCircle(
                                color = Color(0xFF232327),
                                radius = outerR - 34f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.2f)
                            )

                            // Rotating Bezel Ticks & Cardinals
                            rotate(degrees = smoothDialAngle, pivot = Offset(cx, cy)) {
                                for (deg in 0 until 360 step 5) {
                                    val rad = Math.toRadians(deg.toDouble() - 90.0)
                                    val isCardinal = deg % 90 == 0
                                    val isMajor = deg % 30 == 0
                                    val isMedium = deg % 15 == 0
                                    val tickLen = when {
                                        isCardinal -> 20f
                                        isMajor -> 15f
                                        isMedium -> 10f
                                        else -> 6f
                                    }
                                    val startR = outerR - 6f
                                    val endR = startR - tickLen
                                    val tickColor = when {
                                        deg == 0 -> GhostAlertRed
                                        isCardinal -> Color.White
                                        isMajor -> Color(0xFFD4D4D8)
                                        else -> Color(0xFF52525B)
                                    }
                                    drawLine(
                                        color = tickColor,
                                        start = Offset(
                                            cx + (startR * cos(rad)).toFloat(),
                                            cy + (startR * sin(rad)).toFloat()
                                        ),
                                        end = Offset(
                                            cx + (endR * cos(rad)).toFloat(),
                                            cy + (endR * sin(rad)).toFloat()
                                        ),
                                        strokeWidth = when {
                                            isCardinal -> 3.4f
                                            isMajor -> 2.2f
                                            else -> 1.2f
                                        },
                                        cap = StrokeCap.Round
                                    )
                                }

                                // Cardinal & Degree Labels
                                val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    textAlign = Paint.Align.CENTER
                                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                                }
                                val degreePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    textAlign = Paint.Align.CENTER
                                    textSize = 24f
                                    color = android.graphics.Color.parseColor("#71717A")
                                }

                                val cardinals = listOf(
                                    0 to "N", 45 to "NE", 90 to "E", 135 to "SE",
                                    180 to "S", 225 to "SW", 270 to "W", 315 to "NW"
                                )
                                for ((deg, label) in cardinals) {
                                    val rad = Math.toRadians(deg.toDouble() - 90.0)
                                    val isMain = deg % 90 == 0
                                    val labelR = if (isMain) outerR - 50f else outerR - 46f
                                    val lx = cx + (labelR * cos(rad)).toFloat()
                                    val ly = cy + (labelR * sin(rad)).toFloat()
                                    cardinalPaint.textSize = if (isMain) 38f else 22f
                                    cardinalPaint.color = when {
                                        deg == 0 -> android.graphics.Color.parseColor("#E5484D")
                                        isMain -> android.graphics.Color.WHITE
                                        else -> android.graphics.Color.parseColor("#71717A")
                                    }
                                    drawContext.canvas.nativeCanvas.save()
                                    drawContext.canvas.nativeCanvas.rotate(-smoothDialAngle, lx, ly)
                                    drawContext.canvas.nativeCanvas.drawText(
                                        label,
                                        lx,
                                        ly - (cardinalPaint.ascent() + cardinalPaint.descent()) / 2f,
                                        cardinalPaint
                                    )
                                    drawContext.canvas.nativeCanvas.restore()
                                }

                                // Minor numeric degree callouts (30, 60, 120, 150, 210, 240, 300, 330)
                                listOf(30, 60, 120, 150, 210, 240, 300, 330).forEach { deg ->
                                    val rad = Math.toRadians(deg.toDouble() - 90.0)
                                    val rNum = outerR - 29f
                                    val nx = cx + (rNum * cos(rad)).toFloat()
                                    val ny = cy + (rNum * sin(rad)).toFloat()
                                    drawContext.canvas.nativeCanvas.save()
                                    drawContext.canvas.nativeCanvas.rotate(-smoothDialAngle, nx, ny)
                                    drawContext.canvas.nativeCanvas.drawText(
                                        deg.toString(),
                                        nx,
                                        ny - (degreePaint.ascent() + degreePaint.descent()) / 2f,
                                        degreePaint
                                    )
                                    drawContext.canvas.nativeCanvas.restore()
                                }

                                // Locked target bearing marker on bezel (if user locked a heading)
                                lockedTargetDeg?.let { target ->
                                    val rad = Math.toRadians(target.toDouble() - 90.0)
                                    val tx = cx + ((outerR - 14f) * cos(rad)).toFloat()
                                    val ty = cy + ((outerR - 14f) * sin(rad)).toFloat()
                                    drawCircle(
                                        color = Color.White,
                                        radius = 7.5f,
                                        center = Offset(tx, ty)
                                    )
                                }
                            }

                            // Fixed Top White Lubber Index Triangle (12 o'clock)
                            val lubber = Path().apply {
                                moveTo(cx, 4f)
                                lineTo(cx - 10f, 20f)
                                lineTo(cx + 10f, 20f)
                                close()
                            }
                            drawPath(lubber, color = Color.White)

                            // Center Spirit Level Reticle & Crosshair
                            val levelR = 46f
                            drawCircle(
                                color = if (isLevel) Color.White else Color(0xFF3F3F46),
                                radius = levelR,
                                center = Offset(cx, cy),
                                style = Stroke(width = if (isLevel) 2.2f else 1.4f)
                            )
                            drawLine(
                                color = Color(0xFF3F3F46),
                                start = Offset(cx - 22f, cy),
                                end = Offset(cx + 22f, cy),
                                strokeWidth = 1.4f
                            )
                            drawLine(
                                color = Color(0xFF3F3F46),
                                start = Offset(cx, cy - 22f),
                                end = Offset(cx, cy + 22f),
                                strokeWidth = 1.4f
                            )

                            // Live 2-Axis Spirit Level Bubble
                            val maxOffset = levelR - 11f
                            val bx = (cx + (compass.rollDeg.coerceIn(-45f, 45f) / 45f) * maxOffset)
                            val by = (cy - (compass.pitchDeg.coerceIn(-45f, 45f) / 45f) * maxOffset)
                            drawCircle(
                                color = if (isLevel) Color.White else Color(0x99D4D4D8),
                                radius = 10f,
                                center = Offset(bx, by)
                            )
                            drawCircle(
                                color = Color(0xFF0A0A0C),
                                radius = 3f,
                                center = Offset(bx, by)
                            )
                        }
                    }

                    // Locked Bearing Deviation Bar (only when locked)
                    lockedTargetDeg?.let { target ->
                        val diff = (((target - compass.activeAzimuthDeg) + 540f) % 360f) - 180f
                        val dirStr = when {
                            abs(diff) <= 3f -> "On Course"
                            diff > 0f -> "Turn ${diff.roundToInt()}° Right"
                            else -> "Turn ${abs(diff).roundToInt()}° Left"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Target ${target.roundToInt() % 360}°",
                                style = TabularCoordinateMono,
                                color = GhostTextSecondary
                            )
                            Text(
                                text = dirStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }

                    // Minimal Telemetry Metrics Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TelemetryCell(
                            label = "LEVEL",
                            value = "${compass.pitchDeg.roundToInt()}° / ${compass.rollDeg.roundToInt()}°"
                        )
                        TelemetryCell(
                            label = "FLUX",
                            value = if (compass.magneticFluxMicroTesla > 0f) {
                                String.format("%.0f µT", compass.magneticFluxMicroTesla)
                            } else "—"
                        )
                        TelemetryCell(
                            label = "DECL",
                            value = String.format("%+.1f°", compass.declinationDeg)
                        )
                    }

                    // Lock Bearing Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                lockedTargetDeg = if (lockedTargetDeg == null) compass.activeAzimuthDeg else null
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (lockedTargetDeg != null) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (lockedTargetDeg != null) "Unlock Bearing" else "Lock Bearing")
                        }
                    }

                    if (compass.accuracy == CompassAccuracy.LOW || compass.accuracy == CompassAccuracy.UNRELIABLE) {
                        Text(
                            text = "Move device in a figure-8 to calibrate sensor",
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextMuted
                        )
                    }
                }
            }
        }

        // Light, Screen Lamp & Morse Code Transmitter
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MonoGlassBg)
                    .border(0.85.dp, MonoGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Light & Morse Signal", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onSetTorchMode(if (torchMode == TorchMode.STEADY) TorchMode.OFF else TorchMode.STEADY)
                            },
                            enabled = torchAvailable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (torchMode == TorchMode.STEADY) Color.White else Color(0xFF232326),
                                contentColor = if (torchMode == TorchMode.STEADY) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("torch_steady_button")
                        ) {
                            Icon(Icons.Filled.FlashlightOn, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (torchMode == TorchMode.STEADY) "LED On" else "Torch")
                        }

                        Button(
                            onClick = {
                                onSetTorchMode(if (torchMode == TorchMode.SOS) TorchMode.OFF else TorchMode.SOS)
                            },
                            enabled = torchAvailable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (torchMode == TorchMode.SOS) GhostAlertRed else GhostAlertRedContainer,
                                contentColor = if (torchMode == TorchMode.SOS) Color.White else GhostAlertRed
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("torch_sos_button")
                        ) {
                            Icon(Icons.Filled.Sos, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (torchMode == TorchMode.SOS) "Stop SOS" else "SOS")
                        }
                    }

                    // Screen-Based White & Red Night-Vision Light (works on every device)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onActivateScreenLight(Color.White) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Screen Lamp")
                        }
                        OutlinedButton(
                            onClick = { onActivateScreenLight(Color(0xFFB91C1C)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Night Red", color = GhostAlertRed)
                        }
                    }

                    // Custom Morse Encoder & LED Transmitter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = morseInput,
                            onValueChange = { morseInput = it.take(32) },
                            label = { Text("Morse message") },
                            placeholder = { Text("HELP") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onTransmitMorse(morseInput) },
                            enabled = morseInput.isNotBlank() && torchAvailable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = "Flash Morse", modifier = Modifier.size(17.dp))
                        }
                    }
                    if (morseInput.isNotBlank()) {
                        Text(
                            text = TorchAndCompassController.encodeToMorse(morseInput),
                            style = TabularCoordinateMono,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // GPS Coordinates Card
        item {
            val coordString = String.format("%.6f, %.6f", location.point.lat, location.point.lon)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MonoGlassBg)
                    .border(0.85.dp, MonoGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Coordinates", style = MaterialTheme.typography.titleSmall, color = GhostTextPrimary)
                    Text(coordString, style = TabularCoordinateMono, color = Color.White)
                    Text(
                        text = "±${location.accuracyMeters.toInt()} m • ${location.provider}",
                        style = MaterialTheme.typography.bodySmall,
                        color = GhostTextMuted
                    )
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(coordString))
                            onNotify("Coordinates copied")
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TelemetryCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
        Text(value, style = TabularCoordinateMono, color = Color.White)
    }
}
