package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ghostgrid.map.CarColorPreset
import com.example.ghostgrid.map.GhostCameraMode
import com.example.ghostgrid.map.GhostGridMapController
import com.example.ghostgrid.map.GhostMapStyle
import com.example.ghostgrid.routing.RoutingProfile
import com.example.ghostgrid.security.BiometricVaultGate
import com.example.ghostgrid.ui.GhostGridViewModel
import com.example.ghostgrid.ui.GhostScreen
import com.example.ghostgrid.ui.components.BottomCommandDock
import com.example.ghostgrid.ui.components.MapLayersAndVehicleSheet
import com.example.ghostgrid.ui.components.MapTacticalActionColumn
import com.example.ghostgrid.ui.components.MapWorldClockPill
import com.example.ghostgrid.ui.components.PlaceDetailSheet
import com.example.ghostgrid.ui.components.TopSearchAndPrivacyHud
import com.example.ghostgrid.ui.components.TurnByTurnNavigationBanner
import com.example.ghostgrid.ui.screens.ConstellationDetectorScreen
import com.example.ghostgrid.ui.screens.RegionManagerScreen
import com.example.ghostgrid.ui.screens.RouteVaultScreen
import com.example.ghostgrid.ui.screens.SavedPlacesScreen
import com.example.ghostgrid.ui.screens.SettingsScreen
import com.example.ghostgrid.ui.screens.SurvivalToolkitScreen
import com.example.ghostgrid.ui.theme.GhostCanvas
import com.example.ghostgrid.ui.theme.GhostGridTheme
import com.example.ghostgrid.ui.theme.GhostSurfaceSolid
import com.example.ghostgrid.ui.theme.GhostTextPrimary
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

class MainActivity : FragmentActivity() {

    private val viewModel: GhostGridViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(applicationContext)

        setContent {
            GhostGridTheme {
                GhostGridRoot(
                    activity = this,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.locationTracker.hasLocationPermission()) {
            viewModel.locationTracker.startUpdates()
        }
        if (viewModel.uiState.value.currentScreen == GhostScreen.TOOLKIT ||
            viewModel.uiState.value.currentScreen == GhostScreen.CONSTELLATIONS ||
            viewModel.uiState.value.cameraMode == GhostCameraMode.COMPASS
        ) {
            viewModel.torchAndCompass.startCompass()
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.locationTracker.stopUpdates()
        viewModel.torchAndCompass.stopCompass()
    }
}

@Composable
private fun GhostGridRoot(
    activity: FragmentActivity,
    viewModel: GhostGridViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val location by viewModel.liveLocation.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val installedRegions by viewModel.installedRegions.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val recordedTracks by viewModel.recordedTracks.collectAsStateWithLifecycle()
    val encryptedNotes by viewModel.encryptedNotes.collectAsStateWithLifecycle()
    val emergencyContacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
    val savedConstellations by viewModel.savedConstellations.collectAsStateWithLifecycle()
    val packProgress by viewModel.packProgress.collectAsStateWithLifecycle()
    val torchMode by viewModel.torchMode.collectAsStateWithLifecycle()
    val torchAvailable by viewModel.torchAvailable.collectAsStateWithLifecycle()
    val compass by viewModel.compassReading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val mapController = remember {
        GhostGridMapController(context, viewModel.mapAssetManager, viewModel.offlineTileVault)
    }

    val activeStyle = remember(settings.mapStyleId) { GhostMapStyle.fromId(settings.mapStyleId) }
    val activeCarColor = remember(settings.carColorId) { CarColorPreset.fromId(settings.carColorId) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val locGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locGranted) {
            viewModel.locationTracker.startUpdates()
        }
    }

    val zipImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importLocalPackZip(uri)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.updateProfilePhoto(uri)
    }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        if (!viewModel.locationTracker.hasLocationPermission()) {
            permissionLauncher.launch(perms)
        } else {
            viewModel.locationTracker.startUpdates()
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    LaunchedEffect(activeStyle, settings.strictOfflineMode) {
        mapController.loadMapStyle(activeStyle, settings.strictOfflineMode)
    }
    LaunchedEffect(uiState.mapReloadCounter) {
        mapController.hotReloadRegionalPacks()
        if (settings.strictOfflineMode && uiState.mapReloadCounter > 0) {
            mapController.loadMapStyle(activeStyle, strictOfflineMode = true, forceReload = true)
        }
    }
    LaunchedEffect(location, uiState.navHud.isNavigating, activeCarColor, settings.alwaysShow3dCar) {
        mapController.updateUserVehicleMarker(
            snapshot = location,
            isNavigating = uiState.navHud.isNavigating,
            carColor = activeCarColor,
            alwaysShowCar = settings.alwaysShow3dCar
        )
    }
    LaunchedEffect(uiState.cameraMode) {
        val heading = if (uiState.cameraMode == GhostCameraMode.COMPASS && compass.hasHardwareSensor) {
            compass.activeAzimuthDeg
        } else {
            location.bearing
        }
        mapController.applyCameraMode(uiState.cameraMode, location.point, heading)
    }
    LaunchedEffect(uiState.navHud.activeRoute) {
        val route = uiState.navHud.activeRoute
        mapController.updateActiveRoute(
            routePoints = route?.points.orEmpty(),
            avoidanceZones = route?.avoidanceZones.orEmpty()
        )
    }
    LaunchedEffect(recordingState.livePoints, uiState.previewTrackPoints) {
        val pts = if (recordingState.isRecording) recordingState.livePoints else uiState.previewTrackPoints
        mapController.updateLiveBreadcrumbs(pts)
    }
    LaunchedEffect(uiState.searchResults, uiState.selectedPlace) {
        mapController.updatePlaceMarkers(uiState.searchResults, uiState.selectedPlace)
    }

    Scaffold(
        containerColor = GhostCanvas,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = GhostSurfaceSolid,
                    contentColor = GhostTextPrimary,
                    actionColor = Color.White,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GhostCanvas)
        ) {
            GhostGridMapSurface(
                mapController = mapController,
                worldBaseFile = uiState.worldBaseFile,
                initialStyle = activeStyle,
                strictOfflineMode = settings.strictOfflineMode,
                onMapTapped = { pt ->
                    viewModel.setShowLayersSheet(false)
                    viewModel.onMapTapped(pt)
                },
                onCameraIdle = { center ->
                    viewModel.updateMapCenter(center)
                },
                onStyleReady = {
                    val startZoom = if (settings.strictOfflineMode && uiState.tileCacheStats.totalTiles == 0) 3.4 else 14.5
                    mapController.animateTo(location.point, zoom = startZoom, tilt = 18.0)
                    mapController.updateUserVehicleMarker(
                        snapshot = location,
                        isNavigating = uiState.navHud.isNavigating,
                        carColor = activeCarColor,
                        alwaysShowCar = settings.alwaysShow3dCar
                    )
                }
            )

            if (uiState.currentScreen == GhostScreen.MAP) {
                if (uiState.navHud.isNavigating) {
                    TurnByTurnNavigationBanner(
                        navHud = uiState.navHud,
                        voiceEnabled = settings.voiceGuidanceEnabled,
                        onRoadBlockedAhead = { viewModel.triggerRoadBlockedAhead() },
                        onToggleVoice = { viewModel.toggleVoiceGuidance() },
                        onStopNavigation = { viewModel.stopNavigation() },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else {
                    TopSearchAndPrivacyHud(
                        query = uiState.searchQuery,
                        activeCategory = uiState.activeCategory,
                        results = uiState.searchResults,
                        isSearching = uiState.isSearching,
                        settings = settings,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSubmitSearch = { viewModel.submitSearchNow() },
                        onClearSearch = { viewModel.clearSearch() },
                        onToggleCategory = { viewModel.toggleCategoryFilter(it) },
                        onSelectResult = { place ->
                            viewModel.selectPlace(place)
                            viewModel.clearSearch()
                            mapController.animateTo(
                                com.example.ghostgrid.search.GeoPoint(place.lat, place.lon),
                                zoom = 15.5,
                                tilt = 24.0
                            )
                        },
                        onToggleStrictOffline = { viewModel.toggleStrictOfflineMode() },
                        onOpenLayersSheet = { viewModel.setShowLayersSheet(!uiState.showLayersSheet) },
                        onOpenScreen = { viewModel.navigateTo(it) },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                if (uiState.selectedPlace == null && !uiState.showLayersSheet) {
                    if (!uiState.navHud.isNavigating) {
                        MapWorldClockPill(
                            selectedTimeZoneId = settings.selectedTimeZoneId,
                            onClick = { viewModel.navigateTo(GhostScreen.TOOLKIT) },
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }

                    MapTacticalActionColumn(
                        cameraMode = uiState.cameraMode,
                        recordingState = recordingState,
                        location = location,
                        onCycleCameraMode = { viewModel.cycleCameraMode() },
                        onRecenter = {
                            val targetZoom = if (settings.strictOfflineMode && installedRegions.isEmpty() && uiState.tileCacheStats.totalTiles == 0) 4.0 else 15.8
                            mapController.animateTo(
                                location.point,
                                zoom = targetZoom,
                                tilt = if (settings.alwaysShow3dCar) 50.0 else 20.0
                            )
                        },
                        onToggleRecording = {
                            if (!viewModel.locationTracker.hasLocationPermission()) {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                viewModel.toggleTrackRecording()
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }

                val selected = uiState.selectedPlace
                when {
                    uiState.showLayersSheet -> {
                        MapLayersAndVehicleSheet(
                            currentStyle = activeStyle,
                            strictOfflineMode = settings.strictOfflineMode,
                            tileCacheStats = uiState.tileCacheStats,
                            isCachingAreaTiles = uiState.isCachingAreaTiles,
                            tileCacheProgressPct = uiState.tileCacheProgressPct,
                            currentCarColor = activeCarColor,
                            alwaysShow3dCar = settings.alwaysShow3dCar,
                            onSelectStyle = { style ->
                                viewModel.selectMapStyle(style.id)
                                val zoom = if (style == GhostMapStyle.PURE_OFFLINE && installedRegions.isEmpty()) 3.4 else 14.2
                                mapController.animateTo(
                                    location.point,
                                    zoom = zoom,
                                    tilt = if (style == GhostMapStyle.OPENFREEMAP_3D) 48.0 else 18.0
                                )
                            },
                            onCacheCurrentAreaOffline = { viewModel.cacheCurrentAreaOffline() },
                            onSelectCarColor = { preset -> viewModel.selectCarColor(preset.id) },
                            onToggleAlways3dCar = {
                                viewModel.toggleAlwaysShow3dCar()
                                mapController.animateTo(location.point, zoom = 16.5, tilt = 52.0)
                            },
                            onDismiss = { viewModel.setShowLayersSheet(false) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    selected != null -> {
                        PlaceDetailSheet(
                            place = selected,
                            activeProfile = RoutingProfile.fromId(settings.routingProfileId),
                            strictOfflineMode = settings.strictOfflineMode,
                            enrichment = uiState.selectedEnrichment,
                            isLoadingEnrichment = uiState.isLoadingEnrichment,
                            enrichmentError = uiState.enrichmentError,
                            showCompassFallbackOption = uiState.pendingCompassFallbackDest?.id == selected.id,
                            onSelectProfile = { viewModel.setRoutingProfile(it) },
                            onNavigate = { viewModel.startNavigationTo(selected) },
                            onCompassBearing = { viewModel.startDirectCompassBearing(selected) },
                            onSaveToList = { listName -> viewModel.savePlaceToList(selected, listName) },
                            onFetchLiveEnrichment = { viewModel.fetchLiveEnrichmentForSelected() },
                            onDismiss = { viewModel.selectPlace(null) },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    else -> {
                        AnimatedVisibility(
                            visible = !uiState.navHud.isNavigating,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            BottomCommandDock(
                                activeScreen = uiState.currentScreen,
                                onOpenScreen = { viewModel.navigateTo(it) }
                            )
                        }
                    }
                }
            }

            when (uiState.currentScreen) {
                GhostScreen.CONSTELLATIONS -> ConstellationDetectorScreen(
                    location = location,
                    compass = compass,
                    savedConstellations = savedConstellations,
                    latestDetection = uiState.latestSkyDetection,
                    isAnalyzingSky = uiState.isAnalyzingSky,
                    onToggleTrueNorth = { viewModel.toggleCompassTrueNorth() },
                    onAnalyzeSkyPhoto = { file, az, pitch -> viewModel.analyzeSkyPhoto(file, az, pitch) },
                    onRunLiveSkyScan = { az, pitch -> viewModel.runLiveSkyScan(az, pitch) },
                    onSaveDetection = { det -> viewModel.saveConstellationDetection(det) },
                    onDeleteSaved = { id -> viewModel.deleteSavedConstellation(id) },
                    onBack = { viewModel.navigateBackToMap() }
                )

                GhostScreen.REGIONS -> RegionManagerScreen(
                    installedRegions = installedRegions,
                    remoteCatalog = uiState.remoteCatalog,
                    savedManifestUrl = settings.manifestFeedUrl,
                    strictOfflineMode = settings.strictOfflineMode,
                    packProgress = packProgress,
                    isFetchingCatalog = uiState.isFetchingCatalog,
                    hasWorldBasePmtiles = uiState.worldBaseFile != null,
                    tileCacheStats = uiState.tileCacheStats,
                    isCachingAreaTiles = uiState.isCachingAreaTiles,
                    tileCacheProgressPct = uiState.tileCacheProgressPct,
                    onInstallBuiltInPack = { pack -> viewModel.installBuiltInRegionPack(pack) },
                    onOpenRegionOnMap = { center ->
                        viewModel.navigateBackToMap()
                        mapController.animateTo(center, zoom = 11.5, tilt = 18.0)
                    },
                    onCacheCurrentAreaOffline = { viewModel.cacheCurrentAreaOffline() },
                    onImportLocalZip = {
                        zipImportLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onFetchManifest = { url -> viewModel.fetchRemoteRegionCatalog(url) },
                    onDownloadRemote = { entry -> viewModel.downloadRemoteRegion(entry) },
                    onDeleteRegion = { id -> viewModel.deleteInstalledRegion(id) },
                    onBack = { viewModel.navigateBackToMap() }
                )

                GhostScreen.VAULT -> RouteVaultScreen(
                    isUnlocked = uiState.isVaultUnlocked,
                    tracks = recordedTracks,
                    onRequestBiometricUnlock = {
                        BiometricVaultGate.authenticate(
                            activity = activity,
                            title = context.getString(R.string.vault_biometric_title),
                            subtitle = context.getString(R.string.vault_biometric_subtitle),
                            onSuccess = { viewModel.markVaultUnlocked() },
                            onError = { msg -> viewModel.showMessage(msg) }
                        )
                    },
                    onPreviewTrack = { trackId -> viewModel.previewRecordedTrack(trackId) },
                    onExportTrack = { track, format ->
                        viewModel.exportTrack(track, format) { shareIntent ->
                            context.startActivity(Intent.createChooser(shareIntent, "Export Track"))
                        }
                    },
                    onDeleteTrack = { id -> viewModel.deleteRecordedTrack(id) },
                    onBack = { viewModel.navigateBackToMap() }
                )

                GhostScreen.SAVED_PLACES -> SavedPlacesScreen(
                    savedPlaces = savedPlaces,
                    currentLocation = location.point,
                    onSaveCurrentLocation = { name, list, notes ->
                        viewModel.saveCurrentLocationToList(name, list, notes)
                    },
                    onNavigateToPlace = { place ->
                        viewModel.navigateBackToMap()
                        viewModel.selectPlace(place)
                        mapController.animateTo(
                            com.example.ghostgrid.search.GeoPoint(place.lat, place.lon),
                            zoom = 15.5,
                            tilt = 24.0
                        )
                    },
                    onDeletePlace = { id -> viewModel.deleteSavedPlace(id) },
                    onBack = { viewModel.navigateBackToMap() }
                )

                GhostScreen.TOOLKIT -> SurvivalToolkitScreen(
                    notes = encryptedNotes,
                    contacts = emergencyContacts,
                    settings = settings,
                    location = location,
                    torchMode = torchMode,
                    torchAvailable = torchAvailable,
                    compass = compass,
                    geoIntel = uiState.geoIntel,
                    isLoadingIntel = uiState.isLoadingIntel,
                    intelError = uiState.intelError,
                    currencyRates = uiState.currencyRates,
                    isLoadingCurrency = uiState.isLoadingCurrency,
                    onSelectTimeZone = { zoneId -> viewModel.setSelectedTimeZone(zoneId) },
                    onToggleTrueNorth = { viewModel.toggleCompassTrueNorth() },
                    onSetTorchMode = { viewModel.setTorchMode(it) },
                    onTransmitMorse = { viewModel.transmitMorseText(it) },
                    onRefreshIntel = { viewModel.refreshLiveGeoIntel() },
                    onFetchCurrencyRates = { viewModel.fetchCurrencyRates(it) },
                    onSaveNote = { title, body, tag, gps ->
                        viewModel.saveEncryptedNote(title = title, body = body, tag = tag, attachCurrentGps = gps)
                    },
                    onTogglePinNote = { viewModel.toggleNotePin(it) },
                    onDeleteNote = { viewModel.deleteNote(it) },
                    onAddContact = { name, rel, phone -> viewModel.addEmergencyContact(name, rel, phone) },
                    onDeleteContact = { viewModel.deleteEmergencyContact(it) },
                    onSaveMedicalInfo = { blood, allergies -> viewModel.saveMedicalInfo(blood, allergies) },
                    onNotify = { viewModel.showMessage(it) },
                    onBack = { viewModel.navigateBackToMap() }
                )

                GhostScreen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    keystoreActive = uiState.keystoreActive,
                    activePlacesDbCount = uiState.activePlacesDbCount,
                    activeRoutingGraphCount = uiState.activeRoutingGraphCount,
                    isSyncingProfile = uiState.isSyncingProfile,
                    onToggleStrictOffline = { viewModel.toggleStrictOfflineMode() },
                    onToggleVoice = { viewModel.toggleVoiceGuidance() },
                    onUpdateProfileName = { viewModel.updateProfileName(it) },
                    onPickProfilePhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveProfilePhoto = { viewModel.removeProfilePhoto() },
                    onUpdateUserServerUrl = { viewModel.updateUserServerUrl(it) },
                    onSyncProfileToUserServer = { viewModel.syncProfileToUserServer() },
                    onSavePlacesApiKey = { viewModel.savePlacesApiKey(it) },
                    onSaveBingApiKey = { viewModel.saveBingApiKey(it) },
                    onClearEnrichmentCache = { viewModel.clearEnrichmentCache() },
                    onBack = { viewModel.navigateBackToMap() }
                )

                GhostScreen.MAP -> Unit
            }
        }
    }
}

@Composable
private fun GhostGridMapSurface(
    mapController: GhostGridMapController,
    worldBaseFile: java.io.File?,
    initialStyle: GhostMapStyle,
    strictOfflineMode: Boolean,
    onMapTapped: (com.example.ghostgrid.search.GeoPoint) -> Unit,
    onCameraIdle: (com.example.ghostgrid.search.GeoPoint) -> Unit,
    onStyleReady: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { mapLibreMap ->
                mapController.attachMap(
                    mapView = this,
                    mapLibreMap = mapLibreMap,
                    worldBase = worldBaseFile,
                    initialStyle = initialStyle,
                    strictOfflineMode = strictOfflineMode,
                    onMapClick = onMapTapped,
                    onCameraIdle = onCameraIdle,
                    onStyleReady = onStyleReady
                )
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}
