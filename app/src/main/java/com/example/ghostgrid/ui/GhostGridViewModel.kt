package com.example.ghostgrid.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ghostgrid.data.EmergencyContactEntity
import com.example.ghostgrid.data.EncryptedNoteEntity
import com.example.ghostgrid.data.GhostGridDatabase
import com.example.ghostgrid.data.GhostGridPreferences
import com.example.ghostgrid.data.GhostGridSettings
import com.example.ghostgrid.data.InstalledRegionEntity
import com.example.ghostgrid.data.RecordedTrackEntity
import com.example.ghostgrid.data.SavedConstellationEntity
import com.example.ghostgrid.data.SavedPlaceEntity
import com.example.ghostgrid.downloader.BuiltInRegionPack
import com.example.ghostgrid.downloader.PackInstallProgress
import com.example.ghostgrid.downloader.RegionPackManager
import com.example.ghostgrid.downloader.RemoteRegionEntry
import com.example.ghostgrid.hybrid.CurrencyRatesSnapshot
import com.example.ghostgrid.hybrid.LiveGeoIntelBundle
import com.example.ghostgrid.hybrid.PlacesEnrichmentService
import com.example.ghostgrid.hybrid.PoiEnrichment
import com.example.ghostgrid.hybrid.PublicApisClient
import com.example.ghostgrid.map.GhostCameraMode
import com.example.ghostgrid.map.GhostGridLocationTracker
import com.example.ghostgrid.map.LocationSnapshot
import com.example.ghostgrid.map.MapAssetManager
import com.example.ghostgrid.map.OfflineTileVault
import com.example.ghostgrid.map.TileCacheStats
import com.example.ghostgrid.routing.ActiveRoute
import com.example.ghostgrid.routing.AvoidanceZone
import com.example.ghostgrid.routing.NavigationHudState
import com.example.ghostgrid.routing.NoRegionGraphException
import com.example.ghostgrid.routing.OfflineRoutingEngine
import com.example.ghostgrid.routing.OfflineVoiceGuide
import com.example.ghostgrid.routing.RoutingProfile
import com.example.ghostgrid.search.AmenityCategory
import com.example.ghostgrid.search.GeoMath
import com.example.ghostgrid.search.GeoPoint
import com.example.ghostgrid.search.OfflinePlacesSearchEngine
import com.example.ghostgrid.search.PlaceResult
import com.example.ghostgrid.security.KeystoreKeyManager
import com.example.ghostgrid.tools.CelestialNavigatorEngine
import com.example.ghostgrid.tools.CompassReading
import com.example.ghostgrid.tools.ConstellationDetectionResult
import com.example.ghostgrid.tools.ProfileSyncService
import com.example.ghostgrid.tools.TorchAndCompassController
import com.example.ghostgrid.tools.TorchMode
import com.example.ghostgrid.tracking.ExportFormat
import com.example.ghostgrid.tracking.LiveRecordingState
import com.example.ghostgrid.tracking.RouteRecordingService
import com.example.ghostgrid.tracking.TrackExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class GhostScreen {
    MAP,
    CONSTELLATIONS,
    REGIONS,
    VAULT,
    SAVED_PLACES,
    TOOLKIT,
    SETTINGS
}

data class GhostGridUiState(
    val currentScreen: GhostScreen = GhostScreen.MAP,
    val searchQuery: String = "",
    val activeCategory: AmenityCategory? = null,
    val searchResults: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedPlace: PlaceResult? = null,
    val selectedEnrichment: PoiEnrichment? = null,
    val isLoadingEnrichment: Boolean = false,
    val enrichmentError: String? = null,
    val cameraMode: GhostCameraMode = GhostCameraMode.FREE,
    val navHud: NavigationHudState = NavigationHudState(),
    val pendingCompassFallbackDest: PlaceResult? = null,
    val remoteCatalog: List<RemoteRegionEntry> = emptyList(),
    val isFetchingCatalog: Boolean = false,
    val isVaultUnlocked: Boolean = false,
    val previewTrackPoints: List<GeoPoint> = emptyList(),
    val snackbarMessage: String? = null,
    val mapReloadCounter: Int = 0,
    val worldBaseFile: File? = null,
    val activePlacesDbCount: Int = 0,
    val activeRoutingGraphCount: Int = 0,
    val keystoreActive: Boolean = false,
    val isSyncingProfile: Boolean = false,
    val showLayersSheet: Boolean = false,
    // Offline Tile Cache (Dark / Street / Satellite / Terrain)
    val tileCacheStats: TileCacheStats = TileCacheStats(),
    val isCachingAreaTiles: Boolean = false,
    val tileCacheProgressPct: Int = 0,
    // Constellation Detector
    val latestSkyDetection: ConstellationDetectionResult? = null,
    val isAnalyzingSky: Boolean = false,
    // Public APIs (Open-Meteo, USGS, REST Countries, Frankfurter)
    val geoIntel: LiveGeoIntelBundle? = null,
    val isLoadingIntel: Boolean = false,
    val intelError: String? = null,
    val currencyRates: CurrencyRatesSnapshot? = null,
    val isLoadingCurrency: Boolean = false
)

class GhostGridViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val database = GhostGridDatabase.getInstance(appContext)
    val preferences = GhostGridPreferences(appContext)
    val mapAssetManager = MapAssetManager(appContext)
    val locationTracker = GhostGridLocationTracker(appContext)

    private val searchEngine = OfflinePlacesSearchEngine(appContext, database.savedPlaceDao())
    private val routingEngine = OfflineRoutingEngine(appContext)
    private val voiceGuide = OfflineVoiceGuide(appContext)
    private val keyManager = KeystoreKeyManager(appContext)

    val settings: StateFlow<GhostGridSettings> = preferences.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = GhostGridSettings()
    )

    private val strictOfflineSupplier: () -> Boolean = { settings.value.strictOfflineMode }

    val offlineTileVault = OfflineTileVault(appContext, strictOfflineSupplier)

    private val publicApis = PublicApisClient(isStrictOfflineEnabled = strictOfflineSupplier)

    val regionPackManager = RegionPackManager(
        context = appContext,
        installedRegionDao = database.installedRegionDao(),
        isStrictOfflineEnabled = strictOfflineSupplier
    )

    private val enrichmentService = PlacesEnrichmentService(
        context = appContext,
        cachedPoiDao = database.cachedPoiDao(),
        preferences = preferences,
        isStrictOfflineEnabled = strictOfflineSupplier
    )

    private val profileSyncService = ProfileSyncService(
        context = appContext,
        isStrictOfflineEnabled = strictOfflineSupplier
    )

    val torchAndCompass = TorchAndCompassController(appContext, viewModelScope)

    val installedRegions: StateFlow<List<InstalledRegionEntity>> = database.installedRegionDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = database.savedPlaceDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordedTracks: StateFlow<List<RecordedTrackEntity>> = database.trackDao()
        .observeAllTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val encryptedNotes: StateFlow<List<EncryptedNoteEntity>> = database.noteDao()
        .observeAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emergencyContacts: StateFlow<List<EmergencyContactEntity>> = database.emergencyContactDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedConstellations: StateFlow<List<SavedConstellationEntity>> = database.constellationDao()
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveLocation: StateFlow<LocationSnapshot> = locationTracker.location
    val recordingState: StateFlow<LiveRecordingState> = RouteRecordingService.recordingState
    val packProgress: StateFlow<PackInstallProgress> = regionPackManager.progress
    val torchMode: StateFlow<TorchMode> = torchAndCompass.torchMode
    val torchAvailable: StateFlow<Boolean> = torchAndCompass.torchAvailable
    val compassReading: StateFlow<CompassReading> = torchAndCompass.compass

    private val _uiState = MutableStateFlow(GhostGridUiState())
    val uiState: StateFlow<GhostGridUiState> = _uiState.asStateFlow()

    private var currentMapCenter: GeoPoint? = null
    private var searchJob: Job? = null
    private var lastRerouteElapsedMs: Long = 0L

    init {
        viewModelScope.launch {
            val worldFile = mapAssetManager.ensureWorldBasePmtiles()
            val ksOk = keyManager.isHardwareKeystoreAvailable()
            val stats = offlineTileVault.getCacheStats()
            _uiState.update { it.copy(worldBaseFile = worldFile, keystoreActive = ksOk, tileCacheStats = stats) }
            reloadOfflineEngines()
        }

        viewModelScope.launch {
            settings.collectLatest { s ->
                voiceGuide.setEnabled(s.voiceGuidanceEnabled)
            }
        }

        viewModelScope.launch {
            liveLocation.collectLatest { loc ->
                torchAndCompass.updateGeoLocationForDeclination(loc.point.lat, loc.point.lon)
                torchAndCompass.updateGpsFallbackHeading(loc.bearing, loc.speedMps)
                if (loc.hasGpsFix) {
                    handleLiveNavigationTick(loc.point)
                }
            }
        }
    }

    fun navigateTo(screen: GhostScreen) {
        if (screen == GhostScreen.TOOLKIT ||
            screen == GhostScreen.CONSTELLATIONS ||
            _uiState.value.cameraMode == GhostCameraMode.COMPASS
        ) {
            torchAndCompass.startCompass()
        } else {
            torchAndCompass.stopCompass()
        }
        _uiState.update {
            it.copy(
                currentScreen = screen,
                isVaultUnlocked = if (screen == GhostScreen.VAULT) it.isVaultUnlocked else false
            )
        }
        if (screen == GhostScreen.TOOLKIT && _uiState.value.geoIntel == null && !settings.value.strictOfflineMode) {
            refreshLiveGeoIntel()
        }
    }

    fun navigateBackToMap() {
        if (_uiState.value.cameraMode != GhostCameraMode.COMPASS) {
            torchAndCompass.stopCompass()
        }
        _uiState.update { it.copy(currentScreen = GhostScreen.MAP, isVaultUnlocked = false) }
    }

    fun markVaultUnlocked() {
        _uiState.update { it.copy(isVaultUnlocked = true) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun toggleStrictOfflineMode() {
        viewModelScope.launch {
            val next = !settings.value.strictOfflineMode
            preferences.setStrictOfflineMode(next)
            showMessage(
                if (next) "Offline lock enabled"
                else "Open map active"
            )
        }
    }

    fun setShowLayersSheet(show: Boolean) {
        _uiState.update { it.copy(showLayersSheet = show) }
    }

    fun selectMapStyle(styleId: String) {
        viewModelScope.launch {
            preferences.setMapStyleId(styleId)
            // Keep Offline Mode intact so Dark, Map, Satellite & Terrain are ALL accessible while Offline!
            if (styleId == "pure_offline" && !settings.value.strictOfflineMode) {
                preferences.setStrictOfflineMode(true)
            }
        }
    }

    fun updateMapCenter(point: GeoPoint) {
        currentMapCenter = point
    }

    fun selectCarColor(colorId: String) {
        viewModelScope.launch {
            preferences.setCarColorId(colorId)
        }
    }

    fun toggleAlwaysShow3dCar() {
        viewModelScope.launch {
            val next = !settings.value.alwaysShow3dCar
            preferences.setAlwaysShow3dCar(next)
        }
    }

    fun setRoutingProfile(profile: RoutingProfile) {
        viewModelScope.launch {
            preferences.setRoutingProfileId(profile.id)
            val currentRoute = _uiState.value.navHud.activeRoute
            if (currentRoute != null && !currentRoute.isDirectCompassFallback) {
                startNavigationTo(
                    PlaceResult(
                        id = "dest",
                        name = currentRoute.destinationName,
                        category = "destination",
                        subcategory = "",
                        address = "",
                        lat = currentRoute.destination.lat,
                        lon = currentRoute.destination.lon,
                        distanceMeters = 0.0,
                        regionId = currentRoute.regionId
                    ),
                    overrideProfile = profile
                )
            }
        }
    }

    fun toggleVoiceGuidance() {
        viewModelScope.launch {
            val next = !settings.value.voiceGuidanceEnabled
            preferences.setVoiceGuidanceEnabled(next)
        }
    }

    fun cycleCameraMode() {
        val next = when (_uiState.value.cameraMode) {
            GhostCameraMode.FREE -> GhostCameraMode.TRACKING
            GhostCameraMode.TRACKING -> GhostCameraMode.COMPASS
            GhostCameraMode.COMPASS -> GhostCameraMode.NAVIGATION_3D
            GhostCameraMode.NAVIGATION_3D -> GhostCameraMode.FREE
        }
        if (next == GhostCameraMode.COMPASS) {
            torchAndCompass.startCompass()
        } else if (_uiState.value.currentScreen != GhostScreen.TOOLKIT &&
            _uiState.value.currentScreen != GhostScreen.CONSTELLATIONS
        ) {
            torchAndCompass.stopCompass()
        }
        _uiState.update { it.copy(cameraMode = next) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        triggerSearch(immediate = false)
    }

    fun submitSearchNow() {
        triggerSearch(immediate = true)
    }

    fun toggleCategoryFilter(category: AmenityCategory) {
        val next = if (_uiState.value.activeCategory == category) null else category
        _uiState.update { it.copy(activeCategory = next) }
        triggerSearch(immediate = true)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchQuery = "", activeCategory = null, searchResults = emptyList(), isSearching = false)
        }
    }

    private fun triggerSearch(immediate: Boolean = false) {
        searchJob?.cancel()
        val q = _uiState.value.searchQuery
        val cat = _uiState.value.activeCategory
        if (q.isBlank() && cat == null) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(240)
            _uiState.update { it.copy(isSearching = true) }
            val pos = currentMapCenter ?: liveLocation.value.point

            // 1. Instant local offline SQLite + encrypted saved places search
            val offlineHits = searchEngine.search(
                query = q,
                categoryFilter = cat,
                userLat = pos.lat,
                userLon = pos.lon
            )
            if (offlineHits.isNotEmpty()) {
                _uiState.update { it.copy(searchResults = offlineHits) }
            }

            // 2. When online, run multi-engine live search (Google Places + Bing Maps + ArcGIS + Photon + OSM)
            if (!settings.value.strictOfflineMode && cat != AmenityCategory.SAVED) {
                val googleKey = preferences.getDecryptedPlacesApiKey()
                val bingKey = preferences.getDecryptedBingApiKey()
                val onlineHits = publicApis.searchLivePlaces(
                    query = q,
                    category = cat,
                    centerLat = pos.lat,
                    centerLon = pos.lon,
                    googleApiKey = googleKey,
                    bingApiKey = bingKey
                )

                val merged = (offlineHits + onlineHits)
                    .distinctBy {
                        "${it.name.lowercase().trim()}_${String.format(java.util.Locale.US, "%.4f", it.lat)}"
                    }
                    .sortedWith(compareByDescending<PlaceResult> { it.isSaved }.thenBy { it.distanceMeters })
                    .take(35)
                _uiState.update { it.copy(searchResults = merged, isSearching = false) }
            } else {
                _uiState.update { it.copy(searchResults = offlineHits, isSearching = false) }
            }
        }
    }

    fun selectPlace(place: PlaceResult?) {
        _uiState.update {
            it.copy(
                selectedPlace = place,
                selectedEnrichment = null,
                enrichmentError = null,
                pendingCompassFallbackDest = null
            )
        }
        if (place != null) {
            viewModelScope.launch {
                val cached = enrichmentService.getCachedEnrichment(place)
                if (cached != null) {
                    _uiState.update { it.copy(selectedEnrichment = cached) }
                }
            }
        }
    }

    fun onMapTapped(point: GeoPoint) {
        if (_uiState.value.navHud.isNavigating) return
        val dist = GeoMath.haversineMeters(
            liveLocation.value.point.lat,
            liveLocation.value.point.lon,
            point.lat,
            point.lon
        )
        val pinId = "pin:${String.format("%.5f,%.5f", point.lat, point.lon)}"
        val pin = PlaceResult(
            id = pinId,
            name = "Dropped Pin",
            category = "pin",
            subcategory = "Location",
            address = String.format("%.5f, %.5f", point.lat, point.lon),
            lat = point.lat,
            lon = point.lon,
            distanceMeters = dist,
            regionId = "pin"
        )
        selectPlace(pin)

        // Resolve human-readable OpenStreetMap address via Nominatim when online
        if (!settings.value.strictOfflineMode) {
            viewModelScope.launch {
                publicApis.reverseGeocode(point.lat, point.lon).onSuccess { (display, _) ->
                    val current = _uiState.value.selectedPlace
                    if (current?.id == pinId) {
                        val shortTitle = display.substringBefore(",").trim().ifBlank { "Dropped Pin" }
                        _uiState.update {
                            it.copy(
                                selectedPlace = current.copy(
                                    name = shortTitle,
                                    address = display
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun fetchLiveEnrichmentForSelected() {
        val place = _uiState.value.selectedPlace ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEnrichment = true, enrichmentError = null) }
            val result = enrichmentService.fetchLiveEnrichment(place)
            result.onSuccess { enriched ->
                _uiState.update {
                    it.copy(
                        selectedEnrichment = enriched,
                        isLoadingEnrichment = false,
                        enrichmentError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingEnrichment = false,
                        enrichmentError = err.message ?: "Unavailable"
                    )
                }
            }
        }
    }

    fun startNavigationTo(place: PlaceResult, overrideProfile: RoutingProfile? = null) {
        val profile = overrideProfile ?: RoutingProfile.fromId(settings.value.routingProfileId)
        val origin = liveLocation.value.point
        val dest = GeoPoint(place.lat, place.lon)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    navHud = it.navHud.copy(isRecalculating = true, lastAlertBanner = null),
                    pendingCompassFallbackDest = null
                )
            }
            val offlineResult = routingEngine.calculateRoute(
                origin = origin,
                destination = dest,
                destinationName = place.name,
                profile = profile
            )

            if (offlineResult.isSuccess) {
                val route = offlineResult.getOrThrow()
                applyActiveRoute(route)
                voiceGuide.announceAlert("Route ready to ${place.name}. ${route.maneuvers.firstOrNull()?.instructionText.orEmpty()}")
                return@launch
            }

            // Fallback to open OSRM routing when online and no local GraphHopper graph is installed
            if (!settings.value.strictOfflineMode) {
                val osrmResult = publicApis.calculateOsrmRoute(origin, dest, place.name, profile)
                if (osrmResult.isSuccess) {
                    val route = osrmResult.getOrThrow()
                    applyActiveRoute(route)
                    voiceGuide.announceAlert("Route ready to ${place.name}. ${route.maneuvers.firstOrNull()?.instructionText.orEmpty()}")
                    return@launch
                }
            }

            val err = offlineResult.exceptionOrNull()
            _uiState.update {
                it.copy(
                    navHud = it.navHud.copy(isRecalculating = false),
                    pendingCompassFallbackDest = if (err is NoRegionGraphException) place else null,
                    snackbarMessage = err?.message ?: "Could not compute route."
                )
            }
        }
    }

    fun startDirectCompassBearing(place: PlaceResult) {
        val profile = RoutingProfile.fromId(settings.value.routingProfileId)
        val route = routingEngine.buildDirectCompassBearing(
            origin = liveLocation.value.point,
            destination = GeoPoint(place.lat, place.lon),
            destinationName = place.name,
            profile = profile
        )
        _uiState.update { it.copy(pendingCompassFallbackDest = null) }
        applyActiveRoute(route)
        voiceGuide.announceAlert("Direct bearing active to ${place.name}")
    }

    private fun applyActiveRoute(route: ActiveRoute) {
        val firstManeuver = route.maneuvers.firstOrNull()
        _uiState.update {
            it.copy(
                selectedPlace = null,
                searchResults = emptyList(),
                cameraMode = GhostCameraMode.NAVIGATION_3D,
                navHud = NavigationHudState(
                    isNavigating = true,
                    activeRoute = route,
                    currentManeuverIndex = 0,
                    distanceToNextTurnMeters = firstManeuver?.distanceMeters ?: route.totalDistanceMeters,
                    remainingDistanceMeters = route.totalDistanceMeters,
                    remainingDurationMillis = route.totalDurationMillis,
                    isRecalculating = false,
                    lastAlertBanner = null
                )
            )
        }
    }

    fun triggerRoadBlockedAhead() {
        val route = _uiState.value.navHud.activeRoute ?: return
        if (route.isDirectCompassFallback) {
            showMessage("Install a regional routing pack for road-block detours.")
            return
        }

        val currentPos = liveLocation.value.point
        val blockPoly = GeoMath.buildForwardBlockPolygon(
            origin = currentPos,
            polyline = route.points,
            lengthMeters = 200.0
        )
        val newZones = route.avoidanceZones + AvoidanceZone(
            id = "block_${System.currentTimeMillis()}",
            polygon = blockPoly
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(navHud = it.navHud.copy(isRecalculating = true, lastAlertBanner = "Rerouting…"))
            }
            val result = routingEngine.calculateRoute(
                origin = currentPos,
                destination = route.destination,
                destinationName = route.destinationName,
                profile = route.profile,
                avoidanceZones = newZones
            )
            result.onSuccess { rerouted ->
                applyActiveRoute(rerouted)
                _uiState.update {
                    it.copy(navHud = it.navHud.copy(lastAlertBanner = "Detour active"))
                }
                voiceGuide.announceAlert("Road block avoided. Detour ready.")
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        navHud = it.navHud.copy(
                            isRecalculating = false,
                            lastAlertBanner = err.message ?: "No alternate route found"
                        )
                    )
                }
            }
        }
    }

    fun stopNavigation() {
        _uiState.update {
            it.copy(
                navHud = NavigationHudState(),
                cameraMode = GhostCameraMode.FREE
            )
        }
    }

    private fun handleLiveNavigationTick(currentPos: GeoPoint) {
        val hud = _uiState.value.navHud
        val route = hud.activeRoute ?: return
        if (!hud.isNavigating || hud.isRecalculating) return

        val distToDest = GeoMath.haversineMeters(
            currentPos.lat, currentPos.lon,
            route.destination.lat, route.destination.lon
        )
        if (distToDest < 20.0) {
            voiceGuide.announceAlert("You have arrived at ${route.destinationName}.")
            showMessage("Arrived at ${route.destinationName}")
            stopNavigation()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (routingEngine.isOffRoute(currentPos, route) && (now - lastRerouteElapsedMs) > 4000L) {
            lastRerouteElapsedMs = now
            voiceGuide.announceAlert("Off route. Recalculating.")
            viewModelScope.launch {
                _uiState.update {
                    it.copy(navHud = it.navHud.copy(isRecalculating = true, lastAlertBanner = "Recalculating…"))
                }
                val result = routingEngine.calculateRoute(
                    origin = currentPos,
                    destination = route.destination,
                    destinationName = route.destinationName,
                    profile = route.profile,
                    avoidanceZones = route.avoidanceZones
                )
                if (result.isSuccess) {
                    applyActiveRoute(result.getOrThrow())
                } else if (!settings.value.strictOfflineMode) {
                    publicApis.calculateOsrmRoute(currentPos, route.destination, route.destinationName, route.profile)
                        .onSuccess { applyActiveRoute(it) }
                        .onFailure {
                            _uiState.update { st -> st.copy(navHud = st.navHud.copy(isRecalculating = false)) }
                        }
                } else {
                    _uiState.update { st -> st.copy(navHud = st.navHud.copy(isRecalculating = false)) }
                }
            }
            return
        }

        var nextIdx = hud.currentManeuverIndex.coerceIn(0, (route.maneuvers.size - 1).coerceAtLeast(0))
        while (nextIdx < route.maneuvers.lastIndex) {
            val m = route.maneuvers[nextIdx]
            val d = GeoMath.haversineMeters(currentPos.lat, currentPos.lon, m.location.lat, m.location.lon)
            if (d < 22.0) nextIdx++ else break
        }
        val activeManeuver = route.maneuvers.getOrNull(nextIdx)
        val distToManeuver = if (activeManeuver != null) {
            GeoMath.haversineMeters(
                currentPos.lat, currentPos.lon,
                activeManeuver.location.lat, activeManeuver.location.lon
            )
        } else distToDest

        if (activeManeuver != null) {
            voiceGuide.announceManeuver(activeManeuver, distToManeuver)
        }

        val speed = liveLocation.value.speedMps.coerceAtLeast(4f)
        val etaMillis = ((distToDest / speed) * 1000.0).toLong()
        _uiState.update {
            it.copy(
                navHud = it.navHud.copy(
                    currentManeuverIndex = nextIdx,
                    distanceToNextTurnMeters = distToManeuver,
                    remainingDistanceMeters = distToDest,
                    remainingDurationMillis = etaMillis
                )
            )
        }
    }

    // --- Public APIs Intel & Currency (Open-Meteo, USGS, REST Countries, Frankfurter) ---
    fun refreshLiveGeoIntel() {
        val pos = liveLocation.value.point
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingIntel = true, intelError = null) }
            publicApis.fetchLiveGeoIntel(pos.lat, pos.lon)
                .onSuccess { bundle ->
                    _uiState.update { it.copy(geoIntel = bundle, isLoadingIntel = false, intelError = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoadingIntel = false,
                            intelError = err.message ?: "Offline or unreachable"
                        )
                    }
                }
        }
    }

    fun fetchCurrencyRates(base: String = "USD") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCurrency = true) }
            publicApis.fetchCurrencyRates(base)
                .onSuccess { snap ->
                    _uiState.update { it.copy(currencyRates = snap, isLoadingCurrency = false) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(isLoadingCurrency = false) }
                    showMessage(err.message ?: "Rates unavailable offline")
                }
        }
    }

    // --- Compass & Torch ---
    fun toggleCompassTrueNorth() {
        torchAndCompass.toggleTrueNorth()
    }

    fun setTorchMode(mode: TorchMode) {
        torchAndCompass.setTorchMode(mode)
    }

    fun transmitMorseText(text: String) {
        torchAndCompass.transmitCustomMorse(text)
    }

    // --- Saved Places ---
    fun savePlaceToList(place: PlaceResult, listName: String, notes: String = "") {
        viewModelScope.launch {
            database.savedPlaceDao().insert(
                SavedPlaceEntity(
                    name = place.name,
                    category = place.category,
                    listName = listName.trim().ifBlank { "Favorites" },
                    address = place.address,
                    lat = place.lat,
                    lon = place.lon,
                    notes = notes.trim()
                )
            )
            showMessage("Saved to $listName")
        }
    }

    fun saveCurrentLocationToList(name: String, listName: String, notes: String) {
        val pos = liveLocation.value.point
        viewModelScope.launch {
            database.savedPlaceDao().insert(
                SavedPlaceEntity(
                    name = name.trim().ifBlank { "Marked Waypoint" },
                    category = "saved",
                    listName = listName.trim().ifBlank { "Favorites" },
                    address = String.format("%.5f, %.5f", pos.lat, pos.lon),
                    lat = pos.lat,
                    lon = pos.lon,
                    notes = notes.trim()
                )
            )
            showMessage("Saved waypoint")
        }
    }

    fun deleteSavedPlace(id: Long) {
        viewModelScope.launch { database.savedPlaceDao().deleteById(id) }
    }

    // --- Encrypted Route Recording & Export ---
    fun toggleTrackRecording() {
        if (recordingState.value.isRecording) {
            RouteRecordingService.stop(appContext)
            showMessage("Track saved to Vault")
        } else {
            RouteRecordingService.start(appContext)
            showMessage("Recording track")
        }
    }

    fun previewRecordedTrack(trackId: Long) {
        viewModelScope.launch {
            val pts = database.trackDao().getBreadcrumbs(trackId).map { GeoPoint(it.lat, it.lon) }
            _uiState.update {
                it.copy(
                    previewTrackPoints = pts,
                    currentScreen = GhostScreen.MAP,
                    isVaultUnlocked = false
                )
            }
        }
    }

    fun deleteRecordedTrack(trackId: Long) {
        viewModelScope.launch {
            database.trackDao().deleteTrack(trackId)
            showMessage("Track erased")
        }
    }

    fun exportTrack(track: RecordedTrackEntity, format: ExportFormat, onIntentReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val pts = database.trackDao().getBreadcrumbs(track.id)
            if (pts.isEmpty()) {
                showMessage("No GPS points to export")
                return@launch
            }
            val intent = TrackExporter.exportAndCreateShareIntent(appContext, track, pts, format)
            onIntentReady(intent)
        }
    }

    // --- Offline Region Packs & Hot Reload ---
    fun importLocalPackZip(uri: Uri) {
        viewModelScope.launch {
            val res = regionPackManager.importLocalPackZip(uri)
            res.onSuccess { region ->
                reloadOfflineEngines()
                showMessage("Installed ${region.name}")
            }.onFailure { err ->
                showMessage(err.message ?: "Pack import failed")
            }
        }
    }

    fun fetchRemoteRegionCatalog(url: String) {
        viewModelScope.launch {
            preferences.setManifestFeedUrl(url)
            _uiState.update { it.copy(isFetchingCatalog = true) }
            val res = regionPackManager.fetchRemoteCatalog(url)
            res.onSuccess { catalog ->
                _uiState.update { it.copy(remoteCatalog = catalog.regions, isFetchingCatalog = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isFetchingCatalog = false) }
                showMessage(err.message ?: "Could not fetch catalog")
            }
        }
    }

    fun downloadRemoteRegion(entry: RemoteRegionEntry) {
        viewModelScope.launch {
            val res = regionPackManager.downloadAndInstallPack(entry)
            res.onSuccess { region ->
                reloadOfflineEngines()
                showMessage("Installed ${region.name}")
            }.onFailure { err ->
                showMessage(err.message ?: "Download failed")
            }
        }
    }

    fun deleteInstalledRegion(regionId: String) {
        viewModelScope.launch {
            regionPackManager.deleteRegion(regionId)
            reloadOfflineEngines()
        }
    }

    fun installBuiltInRegionPack(pack: BuiltInRegionPack) {
        viewModelScope.launch {
            val isOffline = settings.value.strictOfflineMode
            val res = regionPackManager.installBuiltInRegionPack(
                pack = pack,
                liveFetcher = { p ->
                    if (isOffline) emptyList() else publicApis.fetchRegionPlacesForPack(p)
                }
            )
            res.onSuccess { region ->
                reloadOfflineEngines()
                if (!isOffline) {
                    _uiState.update { it.copy(isCachingAreaTiles = true, tileCacheProgressPct = 5) }
                    val tileRes = offlineTileVault.cacheRegionTiles(
                        centerLat = pack.centerLat,
                        centerLon = pack.centerLon,
                        onProgress = { pct -> _uiState.update { it.copy(tileCacheProgressPct = pct) } }
                    )
                    val stats = offlineTileVault.getCacheStats()
                    _uiState.update {
                        it.copy(
                            isCachingAreaTiles = false,
                            tileCacheProgressPct = 100,
                            tileCacheStats = stats,
                            mapReloadCounter = it.mapReloadCounter + 1
                        )
                    }
                    val savedTiles = tileRes.getOrDefault(0)
                    showMessage("Installed ${region.name} + $savedTiles offline tiles")
                } else {
                    showMessage("Installed ${region.name} offline places pack")
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Could not install ${pack.name}")
            }
        }
    }

    fun cacheCurrentAreaOffline() {
        if (settings.value.strictOfflineMode) {
            showMessage("Disable offline lock briefly to save map tiles")
            return
        }
        val center = currentMapCenter ?: liveLocation.value.point
        viewModelScope.launch {
            _uiState.update { it.copy(isCachingAreaTiles = true, tileCacheProgressPct = 3) }
            val res = offlineTileVault.cacheRegionTiles(
                centerLat = center.lat,
                centerLon = center.lon,
                onProgress = { pct -> _uiState.update { it.copy(tileCacheProgressPct = pct) } }
            )
            val stats = offlineTileVault.getCacheStats()
            _uiState.update {
                it.copy(
                    isCachingAreaTiles = false,
                    tileCacheProgressPct = 100,
                    tileCacheStats = stats,
                    mapReloadCounter = it.mapReloadCounter + 1
                )
            }
            res.onSuccess { count ->
                showMessage("Saved $count tiles (Dark, Map, Satellite & Terrain) for offline use")
            }.onFailure { err ->
                showMessage(err.message ?: "Could not cache offline tiles")
            }
        }
    }

    // --- Constellation Detector & Celestial Compass ---
    fun analyzeSkyPhoto(photoFile: File, azimuthDeg: Float, pitchDeg: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingSky = true) }
            val pos = liveLocation.value.point
            val result = withContext(Dispatchers.Default) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = runCatching { android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath, opts) }.getOrNull()
                val detection = CelestialNavigatorEngine.detectConstellation(
                    bitmap = bmp,
                    compassAzimuthDeg = azimuthDeg,
                    devicePitchDeg = pitchDeg,
                    latDeg = pos.lat,
                    lonDeg = pos.lon,
                    photoPath = photoFile.absolutePath
                )
                bmp?.recycle()
                detection
            }
            _uiState.update { it.copy(latestSkyDetection = result, isAnalyzingSky = false) }
            showMessage("Detected ${result.constellation.def.name} (${result.compassCardinal} ${result.compassAzimuthDeg.toInt()}°)")
        }
    }

    fun runLiveSkyScan(azimuthDeg: Float, pitchDeg: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzingSky = true) }
            val pos = liveLocation.value.point
            val result = withContext(Dispatchers.Default) {
                CelestialNavigatorEngine.detectConstellation(
                    bitmap = null,
                    compassAzimuthDeg = azimuthDeg,
                    devicePitchDeg = pitchDeg,
                    latDeg = pos.lat,
                    lonDeg = pos.lon,
                    photoPath = ""
                )
            }
            _uiState.update { it.copy(latestSkyDetection = result, isAnalyzingSky = false) }
            showMessage("Locked ${result.constellation.def.name} • ${result.compassCardinal}")
        }
    }

    fun saveConstellationDetection(detection: ConstellationDetectionResult) {
        viewModelScope.launch {
            val pos = liveLocation.value.point
            val savedPhotoPath = withContext(Dispatchers.IO) {
                if (detection.photoPath.isNotBlank()) {
                    val src = File(detection.photoPath)
                    if (src.exists()) {
                        val destDir = File(appContext.noBackupFilesDir, "constellations").apply { mkdirs() }
                        val dest = File(destDir, "sky_${System.currentTimeMillis()}.jpg")
                        runCatching { src.copyTo(dest, overwrite = true) }
                        dest.absolutePath
                    } else ""
                } else ""
            }
            database.constellationDao().insert(
                SavedConstellationEntity(
                    constellationName = detection.constellation.def.name,
                    navigationHint = detection.constellation.def.navigationHint,
                    azimuthDeg = detection.compassAzimuthDeg,
                    cardinalDirection = detection.compassCardinal,
                    altitudeDeg = detection.skyAltitudeDeg,
                    starsDetected = detection.starsDetectedInPhoto,
                    moonAzimuthDeg = detection.moon.azimuthDeg.toFloat(),
                    moonAltitudeDeg = detection.moon.altitudeDeg.toFloat(),
                    moonPhaseLabel = "${detection.moon.phaseLabel} (${detection.moon.illuminationPercent}%)",
                    lat = pos.lat,
                    lon = pos.lon,
                    photoPath = savedPhotoPath,
                    capturedAt = detection.capturedAtMs
                )
            )
            showMessage("Saved ${detection.constellation.def.name} for Moon & direction tracking")
        }
    }

    fun deleteSavedConstellation(id: Long) {
        viewModelScope.launch {
            database.constellationDao().deleteById(id)
            showMessage("Constellation removed")
        }
    }

    private suspend fun reloadOfflineEngines() {
        searchEngine.reloadRegionPools()
        routingEngine.reloadGraphs()
        val dbCount = searchEngine.getActiveRegionDbCount()
        val graphCount = routingEngine.getLoadedGraphCount()
        val stats = offlineTileVault.getCacheStats()
        _uiState.update {
            it.copy(
                activePlacesDbCount = dbCount,
                activeRoutingGraphCount = graphCount,
                tileCacheStats = stats,
                mapReloadCounter = it.mapReloadCounter + 1
            )
        }
    }

    // --- Notes & Emergency ---
    fun saveEncryptedNote(id: Long = 0, title: String, body: String, tag: String, attachCurrentGps: Boolean) {
        if (title.isBlank() && body.isBlank()) return
        val pos = if (attachCurrentGps) liveLocation.value.point else null
        viewModelScope.launch {
            database.noteDao().upsert(
                EncryptedNoteEntity(
                    id = id,
                    title = title.trim().ifBlank { "Note" },
                    body = body.trim(),
                    tag = tag,
                    attachedLat = pos?.lat,
                    attachedLon = pos?.lon,
                    updatedAt = System.currentTimeMillis()
                )
            )
            showMessage("Note saved")
        }
    }

    fun toggleNotePin(note: EncryptedNoteEntity) {
        viewModelScope.launch { database.noteDao().setPinned(note.id, !note.isPinned) }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch { database.noteDao().deleteById(noteId) }
    }

    fun addEmergencyContact(name: String, relation: String, phone: String) {
        if (name.isBlank() || phone.isBlank()) return
        viewModelScope.launch {
            database.emergencyContactDao().insert(
                EmergencyContactEntity(
                    name = name.trim(),
                    relation = relation.trim().ifBlank { "Emergency" },
                    phoneNumber = phone.trim()
                )
            )
        }
    }

    fun deleteEmergencyContact(id: Long) {
        viewModelScope.launch { database.emergencyContactDao().deleteById(id) }
    }

    fun saveMedicalInfo(bloodType: String, allergies: String) {
        viewModelScope.launch {
            preferences.setMedicalInfo(bloodType, allergies)
            showMessage("Medical ID saved")
        }
    }

    // --- No-Login Local Profile & User Server Sync ---
    fun updateProfileName(name: String) {
        viewModelScope.launch {
            preferences.setProfileName(name)
            showMessage("Name updated")
        }
    }

    fun updateProfilePhoto(uri: Uri) {
        viewModelScope.launch {
            val res = profileSyncService.savePickedProfilePhoto(uri)
            res.onSuccess { path ->
                preferences.setProfilePhotoPath(path)
                showMessage("Photo saved")
            }.onFailure { err ->
                showMessage(err.message ?: "Could not save photo")
            }
        }
    }

    fun removeProfilePhoto() {
        viewModelScope.launch {
            profileSyncService.removeProfilePhoto()
            preferences.setProfilePhotoPath("")
        }
    }

    fun updateUserServerUrl(url: String) {
        viewModelScope.launch { preferences.setUserServerUrl(url) }
    }

    fun syncProfileToUserServer() {
        val current = settings.value
        if (current.userServerUrl.isBlank()) {
            showMessage("Enter your HTTPS server URL first")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingProfile = true) }
            val res = profileSyncService.syncProfileToUserServer(
                serverUrl = current.userServerUrl,
                displayName = current.profileName,
                photoPath = current.profilePhotoPath
            )
            res.onSuccess { msg ->
                preferences.markProfileSyncedNow()
                _uiState.update { it.copy(isSyncingProfile = false) }
                showMessage(msg)
            }.onFailure { err ->
                _uiState.update { it.copy(isSyncingProfile = false) }
                showMessage(err.message ?: "Sync failed")
            }
        }
    }

    fun savePlacesApiKey(key: String) {
        viewModelScope.launch {
            preferences.setPlacesApiKey(key)
            showMessage(if (key.isBlank()) "Google Places key cleared" else "Google Places key encrypted & saved")
        }
    }

    fun saveBingApiKey(key: String) {
        viewModelScope.launch {
            preferences.setBingApiKey(key)
            showMessage(if (key.isBlank()) "Bing Maps key cleared" else "Bing Maps key encrypted & saved")
        }
    }

    fun setSelectedTimeZone(zoneId: String) {
        viewModelScope.launch {
            preferences.setSelectedTimeZoneId(zoneId)
            showMessage("Clock set to $zoneId")
        }
    }

    fun clearEnrichmentCache() {
        viewModelScope.launch { database.cachedPoiDao().clearAll() }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopUpdates()
        torchAndCompass.shutdown()
        voiceGuide.shutdown()
        viewModelScope.launch {
            searchEngine.closeAll()
            routingEngine.closeAll()
        }
    }
}
