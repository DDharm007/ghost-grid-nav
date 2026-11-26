package com.example.ghostgrid.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ghostgrid.data.EmergencyContactEntity
import com.example.ghostgrid.data.EncryptedNoteEntity
import com.example.ghostgrid.data.GhostGridSettings
import com.example.ghostgrid.hybrid.CurrencyRatesSnapshot
import com.example.ghostgrid.hybrid.LiveGeoIntelBundle
import com.example.ghostgrid.map.LocationSnapshot
import com.example.ghostgrid.tools.CompassReading
import com.example.ghostgrid.tools.OfflineCalculatorEngine
import com.example.ghostgrid.tools.TorchMode
import com.example.ghostgrid.ui.components.PrecisionCompassPanel
import com.example.ghostgrid.ui.components.PublicGeoIntelPanel
import com.example.ghostgrid.ui.components.StopwatchAndTimerPanel
import com.example.ghostgrid.ui.components.WorldTimeToolPanel
import com.example.ghostgrid.ui.theme.GhostAlertRed
import com.example.ghostgrid.ui.theme.GhostAlertRedContainer
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostOnAccent
import com.example.ghostgrid.ui.theme.GhostSurfaceSolid
import com.example.ghostgrid.ui.theme.GhostSurfaceVariant
import com.example.ghostgrid.ui.theme.GhostTextMuted
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import com.example.ghostgrid.ui.theme.GhostTextSecondary
import com.example.ghostgrid.ui.theme.TabularCoordinateMono
import com.example.ghostgrid.ui.theme.TabularTelemetryLarge
import com.example.ghostgrid.ui.theme.TabularTelemetryMedium

private enum class ToolkitTab(val label: String, val icon: ImageVector) {
    TIME("Time", Icons.Filled.Schedule),
    COMPASS("Compass", Icons.Filled.Explore),
    INTEL("Intel", Icons.Filled.Cloud),
    CALC("Calc", Icons.Filled.Calculate),
    TIMER("Timer", Icons.Filled.Timer),
    NOTES("Notes", Icons.Filled.EditNote),
    SOS("SOS", Icons.Filled.LocalHospital)
}

private val ToolkitGlassBg = Brush.verticalGradient(listOf(Color(0xE617171A), Color(0xF20B0B0D)))
private val ToolkitGlassRim = Brush.verticalGradient(listOf(Color(0x38FFFFFF), Color(0x10FFFFFF)))

@Composable
fun SurvivalToolkitScreen(
    notes: List<EncryptedNoteEntity>,
    contacts: List<EmergencyContactEntity>,
    settings: GhostGridSettings,
    location: LocationSnapshot,
    torchMode: TorchMode,
    torchAvailable: Boolean,
    compass: CompassReading,
    geoIntel: LiveGeoIntelBundle?,
    isLoadingIntel: Boolean,
    intelError: String?,
    currencyRates: CurrencyRatesSnapshot?,
    isLoadingCurrency: Boolean,
    onSelectTimeZone: (String) -> Unit,
    onToggleTrueNorth: () -> Unit,
    onSetTorchMode: (TorchMode) -> Unit,
    onTransmitMorse: (String) -> Unit,
    onRefreshIntel: () -> Unit,
    onFetchCurrencyRates: (String) -> Unit,
    onSaveNote: (title: String, body: String, tag: String, attachGps: Boolean) -> Unit,
    onTogglePinNote: (EncryptedNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onAddContact: (name: String, relation: String, phone: String) -> Unit,
    onDeleteContact: (Long) -> Unit,
    onSaveMedicalInfo: (bloodType: String, allergies: String) -> Unit,
    onNotify: (String) -> Unit,
    onBack: () -> Unit
) {
    var screenLightColor by remember { mutableStateOf<Color?>(null) }
    BackHandler {
        if (screenLightColor != null) {
            screenLightColor = null
        } else {
            onBack()
        }
    }

    var activeTab by remember { mutableStateOf(ToolkitTab.TIME) }

    Box(modifier = Modifier.fillMaxSize()) {
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
                IconButton(onClick = onBack, modifier = Modifier.testTag("toolkit_back_button")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = GhostTextPrimary
                    )
                }
                Text("Tools", style = MaterialTheme.typography.titleLarge, color = GhostTextPrimary)
            }

            // Minimal Monochrome Frosted Tab Strip
            LazyRow(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ToolkitTab.entries) { tab ->
                    val selected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Color.White else Color(0xFF151518))
                            .border(
                                0.8.dp,
                                if (selected) Color.White else Color(0x28FFFFFF),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { activeTab = tab }
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                            .testTag("toolkit_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (selected) GhostOnAccent else GhostTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) GhostOnAccent else GhostTextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            when (activeTab) {
                ToolkitTab.TIME -> WorldTimeToolPanel(
                    selectedTimeZoneId = settings.selectedTimeZoneId,
                    onSelectTimeZone = onSelectTimeZone
                )

                ToolkitTab.COMPASS -> PrecisionCompassPanel(
                    location = location,
                    torchMode = torchMode,
                    torchAvailable = torchAvailable,
                    compass = compass,
                    onToggleTrueNorth = onToggleTrueNorth,
                    onSetTorchMode = onSetTorchMode,
                    onTransmitMorse = onTransmitMorse,
                    onActivateScreenLight = { screenLightColor = it },
                    onNotify = onNotify
                )

                ToolkitTab.INTEL -> PublicGeoIntelPanel(
                    intel = geoIntel,
                    isLoading = isLoadingIntel,
                    error = intelError,
                    strictOfflineMode = settings.strictOfflineMode,
                    onRefresh = onRefreshIntel
                )

                ToolkitTab.CALC -> CalcUnitsAndCurrencyPanel(
                    currencyRates = currencyRates,
                    isLoadingCurrency = isLoadingCurrency,
                    strictOfflineMode = settings.strictOfflineMode,
                    onFetchCurrencyRates = onFetchCurrencyRates
                )

                ToolkitTab.TIMER -> StopwatchAndTimerPanel()

                ToolkitTab.NOTES -> EncryptedNotesPanel(
                    notes = notes,
                    location = location,
                    onSaveNote = onSaveNote,
                    onTogglePin = onTogglePinNote,
                    onDeleteNote = onDeleteNote
                )

                ToolkitTab.SOS -> EmergencyPanel(
                    contacts = contacts,
                    settings = settings,
                    onAddContact = onAddContact,
                    onDeleteContact = onDeleteContact,
                    onSaveMedicalInfo = onSaveMedicalInfo
                )
            }
        }

        // Full-Screen Tactical White / Red Night-Vision Screen Lamp Overlay
        screenLightColor?.let { lampColor ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(lampColor)
                    .clickable { screenLightColor = null },
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "Tap anywhere to close",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lampColor == Color.White) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun CalcUnitsAndCurrencyPanel(
    currencyRates: CurrencyRatesSnapshot?,
    isLoadingCurrency: Boolean,
    strictOfflineMode: Boolean,
    onFetchCurrencyRates: (String) -> Unit
) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("0") }
    val history = remember { mutableStateListOf<String>() }

    var unitInput by remember { mutableStateOf("100") }
    val numVal = unitInput.toDoubleOrNull() ?: 0.0
    var baseCurrency by remember { mutableStateOf("USD") }

    val keys = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "%", "=")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Minimal Monochrome Calculator
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, ToolkitGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (history.isNotEmpty()) {
                            Text(history.last(), style = TabularCoordinateMono, color = GhostTextMuted)
                        }
                        Text(
                            text = expression.ifEmpty { "0" },
                            style = MaterialTheme.typography.titleMedium,
                            color = GhostTextSecondary,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = result,
                            style = TabularTelemetryLarge,
                            color = Color.White,
                            modifier = Modifier.testTag("calculator_result")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = {
                                if (expression.isNotEmpty()) {
                                    expression = expression.dropLast(1)
                                    result = if (expression.isEmpty()) "0" else OfflineCalculatorEngine.evaluate(expression)
                                }
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("⌫")
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        keys.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { key ->
                                    val isOperator = key in setOf("÷", "×", "-", "+", "%")
                                    val isEquals = key == "="
                                    val isClear = key == "C"
                                    val bg = when {
                                        isEquals -> Color.White
                                        isClear -> GhostAlertRedContainer
                                        isOperator -> Color(0xFF26262A)
                                        else -> Color(0xFF161618)
                                    }
                                    val fg = when {
                                        isEquals -> Color.Black
                                        isClear -> GhostAlertRed
                                        else -> Color.White
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(bg)
                                            .border(0.75.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                                            .clickable {
                                                when (key) {
                                                    "C" -> {
                                                        expression = ""
                                                        result = "0"
                                                    }
                                                    "=" -> {
                                                        val computed = OfflineCalculatorEngine.evaluate(expression)
                                                        if (computed != "Error" && expression.isNotBlank()) {
                                                            history.add("$expression = $computed")
                                                            expression = computed
                                                        }
                                                        result = computed
                                                    }
                                                    else -> {
                                                        expression += key
                                                        val live = OfflineCalculatorEngine.evaluate(expression)
                                                        if (live != "Error") result = live
                                                    }
                                                }
                                            }
                                            .testTag("calc_key_$key"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = key, style = TabularTelemetryMedium, color = fg)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Offline Unit Converter
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, ToolkitGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Units", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    OutlinedTextField(
                        value = unitInput,
                        onValueChange = { unitInput = it },
                        label = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = String.format("%.2f km = %.2f mi  |  %.2f mi = %.2f km", numVal, numVal * 0.621371, numVal, numVal * 1.60934),
                        style = TabularCoordinateMono,
                        color = Color.White
                    )
                    Text(
                        text = String.format("%.1f °C = %.1f °F  |  %.2f L = %.2f gal", numVal, (numVal * 9.0 / 5.0) + 32.0, numVal, numVal * 0.264172),
                        style = TabularCoordinateMono,
                        color = GhostTextSecondary
                    )
                    Text(
                        text = String.format("%.2f kg = %.2f lb  |  %.2f m = %.2f ft", numVal, numVal * 2.20462, numVal, numVal * 3.28084),
                        style = TabularCoordinateMono,
                        color = GhostTextSecondary
                    )
                }
            }
        }

        // Live Currency Converter (Frankfurter API — public-apis)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, ToolkitGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Currency ($baseCurrency)", style = MaterialTheme.typography.titleSmall, color = Color.White)
                        IconButton(
                            onClick = { onFetchCurrencyRates(baseCurrency) },
                            enabled = !strictOfflineMode && !isLoadingCurrency
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Fetch rates", tint = Color.White)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("USD", "EUR", "INR", "GBP", "JPY").forEach { code ->
                            val active = baseCurrency == code
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Color.White else Color(0x18FFFFFF))
                                    .clickable {
                                        baseCurrency = code
                                        if (!strictOfflineMode) onFetchCurrencyRates(code)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(code, style = MaterialTheme.typography.labelSmall, color = if (active) Color.Black else Color.White)
                            }
                        }
                    }

                    if (currencyRates != null) {
                        val targets = listOf("USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD").filter { it != currencyRates.base }
                        targets.forEach { target ->
                            val rate = currencyRates.rates[target] ?: return@forEach
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(target, style = TabularCoordinateMono, color = GhostTextSecondary)
                                Text(
                                    text = String.format("%.2f %s", numVal * rate, target),
                                    style = TabularCoordinateMono,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onFetchCurrencyRates(baseCurrency) },
                            enabled = !strictOfflineMode && !isLoadingCurrency,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (strictOfflineMode) "Offline Lock On" else "Load Live Rates")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EncryptedNotesPanel(
    notes: List<EncryptedNoteEntity>,
    location: LocationSnapshot,
    onSaveNote: (title: String, body: String, tag: String, attachGps: Boolean) -> Unit,
    onTogglePin: (EncryptedNoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf("General") }
    var attachGps by remember { mutableStateOf(true) }
    val tags = listOf("General", "Emergency", "Medical", "Supply", "Location")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, ToolkitGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_title_input")
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Note") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_body_input")
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tags) { tag ->
                            val active = selectedTag == tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (active) Color.White else Color(0x1AFFFFFF))
                                    .clickable { selectedTag = tag }
                                    .padding(horizontal = 11.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Color.Black else GhostTextSecondary
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = attachGps, onCheckedChange = { attachGps = it })
                        Text(
                            text = String.format("Attach GPS (%.4f, %.4f)", location.point.lat, location.point.lon),
                            style = MaterialTheme.typography.bodySmall,
                            color = GhostTextSecondary
                        )
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() || body.isNotBlank()) {
                                onSaveNote(title, body, selectedTag, attachGps)
                                title = ""
                                body = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_note_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Note")
                    }
                }
            }
        }

        items(notes, key = { it.id }) { note ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(GhostSurfaceSolid)
                    .border(
                        0.85.dp,
                        if (note.isPinned) Color.White else Color(0x22FFFFFF),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text(note.tag, style = MaterialTheme.typography.labelSmall, color = GhostTextMuted)
                        }
                        IconButton(onClick = { onTogglePin(note) }) {
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pin",
                                tint = if (note.isPinned) Color.White else GhostTextMuted
                            )
                        }
                        IconButton(onClick = { onDeleteNote(note.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GhostAlertRed)
                        }
                    }
                    if (note.body.isNotBlank()) {
                        Text(note.body, style = MaterialTheme.typography.bodyMedium, color = GhostTextSecondary)
                    }
                    if (note.attachedLat != null && note.attachedLon != null) {
                        Text(
                            text = String.format("%.5f, %.5f", note.attachedLat, note.attachedLon),
                            style = TabularCoordinateMono,
                            color = GhostTextMuted
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EmergencyPanel(
    contacts: List<EmergencyContactEntity>,
    settings: GhostGridSettings,
    onAddContact: (name: String, relation: String, phone: String) -> Unit,
    onDeleteContact: (Long) -> Unit,
    onSaveMedicalInfo: (bloodType: String, allergies: String) -> Unit
) {
    val context = LocalContext.current
    var bloodType by remember(settings.medicalBloodType) { mutableStateOf(settings.medicalBloodType) }
    var allergies by remember(settings.medicalAllergies) { mutableStateOf(settings.medicalAllergies) }

    var contactName by remember { mutableStateOf("") }
    var contactRel by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    val checklistItems = remember {
        listOf(
            "Download local regional offline map pack",
            "Charge power bank",
            "Store 72h water & essential meds",
            "Keep physical ID & cash ready",
            "Confirm family meeting point in Saved"
        )
    }
    val checkedStates = remember { mutableStateListOf(false, false, false, false, false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, GhostAlertRed.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Medical ID", style = MaterialTheme.typography.titleSmall, color = GhostAlertRed)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bloodType,
                            onValueChange = { bloodType = it },
                            label = { Text("Blood") },
                            placeholder = { Text("O+") },
                            singleLine = true,
                            modifier = Modifier.weight(0.4f)
                        )
                        OutlinedTextField(
                            value = allergies,
                            onValueChange = { allergies = it },
                            label = { Text("Allergies / Meds") },
                            singleLine = true,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                    Button(
                        onClick = { onSaveMedicalInfo(bloodType, allergies) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, ToolkitGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Emergency Contacts", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    OutlinedTextField(
                        value = contactName,
                        onValueChange = { contactName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = contactRel,
                            onValueChange = { contactRel = it },
                            label = { Text("Relation") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = contactPhone,
                            onValueChange = { contactPhone = it },
                            label = { Text("Phone") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            if (contactName.isNotBlank() && contactPhone.isNotBlank()) {
                                onAddContact(contactName, contactRel, contactPhone)
                                contactName = ""
                                contactRel = ""
                                contactPhone = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Contact")
                    }

                    contacts.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GhostSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                Text("${c.relation} • ${c.phoneNumber}", style = TabularCoordinateMono, color = GhostTextSecondary)
                            }
                            IconButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(c.phoneNumber)}")))
                                }
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = "Dial", tint = Color.White)
                            }
                            IconButton(onClick = { onDeleteContact(c.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = GhostAlertRed)
                            }
                        }
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ToolkitGlassBg)
                    .border(0.85.dp, ToolkitGlassRim, RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Readiness Checklist", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    checklistItems.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { checkedStates[idx] = !checkedStates[idx] }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (checkedStates[idx]) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (checkedStates[idx]) Color.White else GhostTextMuted,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(item, style = MaterialTheme.typography.bodySmall, color = GhostTextSecondary)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
